package com.fleetpilot.reporting.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.reporting.application.ReportContents.Built;
import com.fleetpilot.reporting.application.Sources.FleetSource;
import com.fleetpilot.reporting.application.Sources.Incident;
import com.fleetpilot.reporting.application.Sources.QuoteSource;
import com.fleetpilot.reporting.application.Sources.Vehicle;
import com.fleetpilot.reporting.application.TcoCalculator.Indicators;
import com.fleetpilot.reporting.application.TcoCalculator.VehicleCost;
import com.fleetpilot.reporting.domain.Report;
import com.fleetpilot.reporting.domain.ReportFile;
import com.fleetpilot.reporting.domain.ReportFileRepository;
import com.fleetpilot.reporting.domain.ReportRepository;
import com.fleetpilot.reporting.domain.ReportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reports requested from the portal. The request is recorded and put on the ActiveMQ queue; a consumer builds the file
 * in the background (it reads thousands of records from the other services), then the requester is told by e-mail.
 * Each user sees their own reports; the data of a client manager is always restricted to their company.
 */
@Service
public class ReportService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportService.class);
    private static final Duration INDICATORS_CACHE = Duration.ofMinutes(10);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** Puts the id of a report to build on the queue. */
    public interface ReportQueue {
        void enqueue(UUID reportId);
    }

    /** Sends an e-mail request to the notification service. */
    public interface Notifier {
        void notify(EmailRequest request);
    }

    public record ReportTypeView(ReportType type, String title, String format, boolean periodic) {
    }

    private record CachedIndicators(Indicators indicators, Instant at) {
    }

    private final ReportRepository reports;
    private final ReportFileRepository files;
    private final ReportContents contents;
    private final FleetSource fleet;
    private final QuoteSource quotes;
    private final ReportQueue queue;
    private final Notifier notifier;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Map<String, CachedIndicators> indicatorsCache = new ConcurrentHashMap<>();

    public ReportService(ReportRepository reports, ReportFileRepository files, ReportContents contents, FleetSource fleet,
            QuoteSource quotes, ReportQueue queue, Notifier notifier, TransactionTemplate transactions, Clock clock) {
        this.reports = reports;
        this.files = files;
        this.contents = contents;
        this.fleet = fleet;
        this.quotes = quotes;
        this.queue = queue;
        this.notifier = notifier;
        this.transactions = transactions;
        this.clock = clock;
    }

    public List<ReportTypeView> catalog() {
        return Arrays.stream(ReportType.values())
                .map(t -> new ReportTypeView(t, t.title(), t.format().name(), t.periodic())).toList();
    }

    /** Records the request and queues it; the file is built in the background. */
    public ReportView request(ReportType type, UUID companyId, LocalDate from, LocalDate to) {
        UUID company = restrictedCompany().orElse(companyId);
        String companyName = company == null ? null : fleet.companyName(company);
        String username = CurrentUser.username().orElse("FleetPilot");
        Report.Requester requester = new Report.Requester(username,
                CurrentUser.token().map(t -> t.getToken().getClaimAsString("email")).orElse(null),
                CurrentUser.token().map(t -> t.getToken().getClaimAsString("name")).orElse(null));
        Report report = transactions.execute(status -> reports.save(new Report(UUID.randomUUID(), type, companyName,
                company, from, to, requester, clock.instant())));
        queue.enqueue(report.getId());
        return ReportView.of(report);
    }

    /** Builds a queued report (called by the queue consumer). */
    public void generate(UUID id) {
        Report report = transactions.execute(status -> {
            Report r = load(id);
            if (r.getStatus() != Report.Status.PENDING) {
                return null;
            }
            r.start(clock.instant());
            return r;
        });
        if (report == null) {
            LOG.debug("Report {} already handled", id);
            return;
        }
        long start = System.nanoTime();
        try {
            Built built = contents.build(report);
            Report ready = transactions.execute(status -> {
                files.save(new ReportFile(id, built.file()));
                Report r = load(id);
                r.ready(built.file().length, built.rows(), clock.instant());
                return r;
            });
            LOG.info("Report {} ({}) built in {} ms: {} rows", ready.getType(), id, (System.nanoTime() - start) / 1_000_000,
                    built.rows());
            tellRequester(ready);
        } catch (RuntimeException e) {
            LOG.error("Report {} ({}) failed", report.getType(), id, e);
            transactions.executeWithoutResult(status -> load(id).failed(e.getMessage(), clock.instant()));
        }
    }

    public Page<ReportView> mine(Pageable pageable) {
        String username = CurrentUser.username().orElse("");
        return reports.findByRequestedByOrderByRequestedAtDesc(username, pageable).map(ReportView::of);
    }

    public ReportView get(UUID id) {
        return ReportView.of(owned(id));
    }

    /** The file of a ready report, with its name and type. */
    public ReportDownload download(UUID id) {
        Report report = owned(id);
        if (report.getStatus() != Report.Status.READY) {
            throw new ResourceNotFoundException("Report file", id);
        }
        ReportFile file = files.findById(id).orElseThrow(() -> new ResourceNotFoundException("Report file", id));
        return new ReportDownload(report.getFileName(), report.getContentType(), file.getContent());
    }

    public record ReportDownload(String fileName, String contentType, byte[] content) {
    }

    /**
     * Indicators of the fleet over twelve months (TCO, cost per kilometre, claims, immobilisation), kept ten minutes:
     * computing them reads the whole fleet from the other services.
     */
    public Indicators indicators(UUID companyId) {
        UUID company = restrictedCompany().orElse(companyId);
        String key = company == null ? "all" : company.toString();
        CachedIndicators cached = indicatorsCache.get(key);
        Instant now = clock.instant();
        if (cached != null && cached.at().plus(INDICATORS_CACHE).isAfter(now)) {
            return cached.indicators();
        }
        TcoCalculator calculator = new TcoCalculator(now, PARIS);
        List<Vehicle> vehicles = fleet.vehicles(company);
        List<Incident> incidents = fleet.incidents(company);
        List<VehicleCost> costs = calculator.costs(vehicles, fleet.activeContracts(company), quotes.quotes(company, null,
                null), incidents);
        Indicators indicators = calculator.indicators(vehicles, costs, incidents);
        indicatorsCache.put(key, new CachedIndicators(indicators, now));
        return indicators;
    }

    private void tellRequester(Report report) {
        if (report.getRequesterEmail() == null) {
            return;
        }
        Map<String, Object> model = new HashMap<>();
        model.put("title", report.getTitle());
        model.put("fileName", report.getFileName());
        model.put("rows", report.getRowCount());
        try {
            notifier.notify(new EmailRequest("report-ready:" + report.getId(), "report-ready", report.getRequesterEmail(),
                    report.getRequesterName(), report.getCompanyId(), "report", report.getId().toString(), model,
                    List.of()));
        } catch (RuntimeException e) {
            // The report is ready whatever happens to the e-mail: the portal shows it anyway
            LOG.warn("Could not ask for the e-mail of report {}: {}", report.getId(), e.getMessage());
        }
    }

    private Report owned(UUID id) {
        Report report = load(id);
        boolean staff = CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER);
        if (!staff && !report.getRequestedBy().equals(CurrentUser.username().orElse(""))) {
            throw new ResourceNotFoundException("Report", id);
        }
        return report;
    }

    private Report load(UUID id) {
        return reports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Report", id));
    }

    /** A client manager only sees their own company. */
    private static Optional<UUID> restrictedCompany() {
        if (!CurrentUser.isAuthenticated() || CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER)) {
            return Optional.empty();
        }
        return Optional.of(CurrentUser.companyId().orElse(new UUID(0, 0)));
    }
}
