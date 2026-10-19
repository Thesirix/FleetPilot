package com.fleetpilot.quote.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ConflictException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.common.util.RegistrationNumbers;
import com.fleetpilot.messaging.OutboxPublisher;
import com.fleetpilot.quote.application.command.QuoteCommands.AnswerCounterOffer;
import com.fleetpilot.quote.application.command.QuoteCommands.Completion;
import com.fleetpilot.quote.application.command.QuoteCommands.CounterOffer;
import com.fleetpilot.quote.application.command.QuoteCommands.Decision;
import com.fleetpilot.quote.application.command.QuoteCommands.IdentifyVehicle;
import com.fleetpilot.quote.application.command.QuoteCommands.InvoiceQuote;
import com.fleetpilot.quote.application.command.QuoteCommands.Rejection;
import com.fleetpilot.quote.application.command.QuoteCommands.ReviseQuote;
import com.fleetpilot.quote.application.command.QuoteCommands.SubmitQuote;
import com.fleetpilot.quote.application.view.QuoteViews.QuoteAction;
import com.fleetpilot.quote.application.view.QuoteViews.QuoteDetail;
import com.fleetpilot.quote.application.view.QuoteViews.QuoteSummary;
import com.fleetpilot.quote.domain.analysis.QuoteAnalysis;
import com.fleetpilot.quote.domain.analysis.QuoteAnalyzer;
import com.fleetpilot.quote.domain.event.QuoteEvents;
import com.fleetpilot.quote.domain.model.AnalysisRules;
import com.fleetpilot.quote.domain.model.Garage;
import com.fleetpilot.quote.domain.model.Quote;
import com.fleetpilot.quote.domain.model.QuoteChannel;
import com.fleetpilot.quote.domain.model.QuoteStatus;
import com.fleetpilot.quote.domain.model.VehicleRef;
import com.fleetpilot.quote.domain.repository.GarageRepository;
import com.fleetpilot.quote.domain.repository.QuoteRepository;
import com.fleetpilot.quote.domain.repository.QuoteSpecifications;
import com.fleetpilot.quote.domain.repository.QuoteSpecifications.QuoteCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garage quotes, from submission to invoice. Every quote is compared with the reference tariff as soon as its vehicle
 * is known; compliant small quotes are approved at once, the others wait for a fleet manager.
 *
 * <p>Approval authority: a fleet manager approves up to the approval limit of the analysis rules excluding
 * tax; above, only a platform administrator may approve (double validation of large repairs).
 */
@Service
@Transactional
public class QuoteService {

    private static final String AGGREGATE = "quote";

    private final QuoteRepository quotes;
    private final GarageRepository garages;
    private final VehicleLookup vehicleLookup;
    private final QuoteAnalyzer analyzer;
    private final OutboxPublisher outbox;
    private final QuoteAccess access;
    private final Clock clock;
    private final AnalysisRulesService rules;

    public QuoteService(QuoteRepository quotes, GarageRepository garages, VehicleLookup vehicleLookup,
            QuoteAnalyzer analyzer, OutboxPublisher outbox, QuoteAccess access, Clock clock, AnalysisRulesService rules) {
        this.quotes = quotes;
        this.garages = garages;
        this.vehicleLookup = vehicleLookup;
        this.analyzer = analyzer;
        this.outbox = outbox;
        this.access = access;
        this.clock = clock;
        this.rules = rules;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<QuoteSummary> search(QuoteCriteria criteria, Pageable pageable) {
        QuoteCriteria scoped = criteria.scoped(access.restrictedCompanyId().orElse(criteria.companyId()),
                access.restrictedGarageId().orElse(criteria.garageId()));
        return quotes.findAll(QuoteSpecifications.quotes(scoped), pageable).map(QuoteSummary::of);
    }

    @Transactional(readOnly = true)
    public QuoteDetail get(UUID id) {
        return detail(load(id));
    }

    @Transactional(readOnly = true)
    public QuoteDetail getByReference(String reference) {
        Quote quote = quotes.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", reference));
        access.checkAccess(quote);
        return detail(quote);
    }

    // ---------------------------------------------------------------- garage side

    /** A garage submits a quote. The vehicle is looked up from the registration, then the quote is analysed. */
    public QuoteDetail submit(SubmitQuote cmd, QuoteChannel channel) {
        Garage garage = garageOf(cmd);
        UUID garageId = garage.getId();
        if (cmd.garageReference() != null && !cmd.garageReference().isBlank()
                && quotes.findByGarageIdAndGarageReference(garageId, cmd.garageReference().trim()).isPresent()) {
            throw new ConflictException("QUOTE_ALREADY_RECEIVED",
                    "Quote " + cmd.garageReference() + " of this garage was already received");
        }
        VehicleRef vehicle = vehicleLookup.resolve(cmd.registration()).orElse(null);
        String registration = vehicle != null ? vehicle.getRegistration()
                : RegistrationNumbers.normalize(cmd.registration()).orElse(cmd.registration().trim());
        String reference = "DEV-%d-%06d".formatted(Year.now(clock).getValue(), quotes.nextReferenceNumber());
        Quote quote = new Quote(UUID.randomUUID(), reference, garage, vehicle,
                new Quote.Submission(registration, blankToNull(cmd.garageReference()), cmd.mileage(), cmd.description(),
                        channel, cmd.lineInputs()),
                access.author(), clock.instant());
        quotes.save(quote);
        outbox.publish(EventTypes.TOPIC_QUOTES, EventTypes.QUOTE_RECEIVED, quote.getCompanyId(), AGGREGATE,
                quote.getId(), QuoteEvents.QuoteReceived.of(quote));
        if (vehicle != null) {
            analyse(quote);
        }
        return detail(quote);
    }

    /** The garage of a submitted quote: the signed-in garage, or the one named by id or SIRET. */
    private Garage garageOf(SubmitQuote cmd) {
        UUID garageId = access.restrictedGarageId().orElse(cmd.garageId());
        if (garageId != null) {
            return garages.findById(garageId).orElseThrow(() -> new ResourceNotFoundException("Garage", garageId));
        }
        if (cmd.garageSiret() != null) {
            return garages.findBySiret(cmd.garageSiret()).orElseThrow(() -> new BusinessRuleException("UNKNOWN_GARAGE",
                    "No garage of the network has the SIRET " + cmd.garageSiret()));
        }
        throw new BusinessRuleException("GARAGE_REQUIRED", "The garage of the quote is required");
    }

    public QuoteDetail answerCounterOffer(UUID id, AnswerCounterOffer cmd) {
        Quote quote = loadForGarage(id);
        if (Boolean.TRUE.equals(cmd.accept())) {
            quote.acceptCounterOffer(access.author(), clock.instant());
            publishDecision(quote);
        } else {
            quote.refuseCounterOffer(cmd.message(), access.author(), clock.instant());
        }
        return detail(quote);
    }

    public QuoteDetail revise(UUID id, ReviseQuote cmd) {
        Quote quote = loadForGarage(id);
        if (cmd.lineInputs() == null || cmd.lineInputs().isEmpty()) {
            throw new BusinessRuleException("QUOTE_WITHOUT_LINES", "A quote needs at least one line");
        }
        // The new lines reuse the positions of the old ones (unique per quote): delete the old ones first
        quote.discardLinesForRevision();
        quotes.flush();
        quote.revise(cmd.lineInputs(), cmd.message(), access.author(), clock.instant());
        if (quote.getVehicle() != null) {
            analyse(quote);
        }
        return detail(quote);
    }

    public QuoteDetail complete(UUID id, Completion cmd) {
        Quote quote = loadForGarage(id);
        LocalDate on = cmd.completedOn() != null ? cmd.completedOn() : LocalDate.now(clock);
        quote.complete(on, access.author(), clock.instant());
        return detail(quote);
    }

    public QuoteDetail invoice(UUID id, InvoiceQuote cmd) {
        Quote quote = loadForGarage(id);
        LocalDate on = cmd.invoicedOn() != null ? cmd.invoicedOn() : LocalDate.now(clock);
        quote.invoice(cmd.number(), cmd.amount(), on, access.author(), clock.instant());
        outbox.publish(EventTypes.TOPIC_QUOTES, EventTypes.QUOTE_INVOICED, quote.getCompanyId(), AGGREGATE,
                quote.getId(), QuoteEvents.QuoteInvoiced.of(quote));
        return detail(quote);
    }

    // ---------------------------------------------------------------- manager side

    public QuoteDetail identifyVehicle(UUID id, IdentifyVehicle cmd) {
        Quote quote = loadForManager(id);
        VehicleRef vehicle = vehicleLookup.resolve(cmd.registration()).orElseThrow(() -> new BusinessRuleException(
                "VEHICLE_UNKNOWN", "No vehicle of the fleet has the registration " + cmd.registration()));
        quote.identifyVehicle(vehicle, access.author(), clock.instant());
        analyse(quote);
        return detail(quote);
    }

    public QuoteDetail counterOffer(UUID id, CounterOffer cmd) {
        Quote quote = loadForManager(id);
        quote.proposeCounterOffer(cmd.amount(), cmd.message(), access.author(), clock.instant());
        outbox.publish(EventTypes.TOPIC_QUOTES, EventTypes.QUOTE_COUNTER_OFFER, quote.getCompanyId(), AGGREGATE,
                quote.getId(), QuoteEvents.CounterOffered.of(quote, access.author()));
        return detail(quote);
    }

    public QuoteDetail accept(UUID id, Decision cmd) {
        Quote quote = loadForManager(id);
        BigDecimal managerApprovalLimit = rules.forAnalysis().getManagerApprovalLimit();
        if (quote.getAmount().compareTo(managerApprovalLimit) > 0 && !access.canApproveAnyAmount()) {
            throw new BusinessRuleException("APPROVAL_LIMIT_EXCEEDED", "Quotes above " + managerApprovalLimit
                    + " EUR excluding tax must be approved by a platform administrator");
        }
        quote.accept(cmd.comment(), access.author(), clock.instant());
        publishDecision(quote);
        return detail(quote);
    }

    public QuoteDetail reject(UUID id, Rejection cmd) {
        Quote quote = loadForManager(id);
        quote.reject(cmd.reason(), access.author(), clock.instant());
        publishDecision(quote);
        return detail(quote);
    }

    // ---------------------------------------------------------------- internals

    private void analyse(Quote quote) {
        AnalysisRules current = rules.forAnalysis();
        QuoteAnalysis analysis = analyzer.withPolicy(current.policy())
                .analyze(quote.lineInputs(), quote.tariffClass(), quote.getGarage().getRegion());
        quote.applyAnalysis(analysis, current.getAutoApprovalLimit(), clock.instant());
        outbox.publish(EventTypes.TOPIC_QUOTES, EventTypes.QUOTE_ANALYSED, quote.getCompanyId(), AGGREGATE,
                quote.getId(), QuoteEvents.QuoteAnalysed.of(quote));
        if (quote.isAutoApproved()) {
            publishDecision(quote);
        }
    }

    private void publishDecision(Quote quote) {
        String type = quote.getStatus() == QuoteStatus.REJECTED ? EventTypes.QUOTE_REJECTED : EventTypes.QUOTE_ACCEPTED;
        outbox.publish(EventTypes.TOPIC_QUOTES, type, quote.getCompanyId(), AGGREGATE, quote.getId(),
                QuoteEvents.QuoteDecided.of(quote));
    }

    private Quote load(UUID id) {
        Quote quote = quotes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Quote", id));
        access.checkAccess(quote);
        return quote;
    }

    /** Garage actions: the garage itself, or staff acting on its behalf (a call, an e-mail). */
    private Quote loadForGarage(UUID id) {
        Quote quote = load(id);
        if (access.restrictedCompanyId().isPresent()) {
            throw new ResourceNotFoundException("Quote", id);
        }
        return quote;
    }

    /** Manager actions: never a garage. */
    private Quote loadForManager(UUID id) {
        Quote quote = load(id);
        if (access.restrictedGarageId().isPresent()) {
            throw new ResourceNotFoundException("Quote", id);
        }
        return quote;
    }

    private QuoteDetail detail(Quote quote) {
        return QuoteDetail.of(quote, actions(quote));
    }

    /** Buttons the portal shows for this quote and this user. */
    Set<QuoteAction> actions(Quote quote) {
        boolean garage = access.restrictedGarageId().isPresent();
        boolean client = access.restrictedCompanyId().isPresent();
        Set<QuoteAction> actions = EnumSet.noneOf(QuoteAction.class);
        QuoteStatus status = quote.getStatus();
        if (!garage && !client) {
            if (status == QuoteStatus.RECEIVED) {
                actions.add(QuoteAction.IDENTIFY_VEHICLE);
            }
            if (status == QuoteStatus.IN_REVIEW || status == QuoteStatus.TO_NEGOTIATE) {
                actions.add(QuoteAction.COUNTER_OFFER);
                if (quote.getAmount().compareTo(rules.forAnalysis().getManagerApprovalLimit()) <= 0
                        || access.canApproveAnyAmount()) {
                    actions.add(QuoteAction.ACCEPT);
                }
            }
            if (status.isOpen()) {
                actions.add(QuoteAction.REJECT);
            }
        }
        if (!client) {
            if (status.isOpen() && quote.getCounterOffer() != null) {
                actions.add(QuoteAction.ANSWER_COUNTER_OFFER);
            }
            if (garage && status.isOpen()) {
                actions.add(QuoteAction.REVISE);
            }
            if (status == QuoteStatus.ACCEPTED) {
                actions.add(QuoteAction.COMPLETE);
            }
            if (status == QuoteStatus.ACCEPTED || status == QuoteStatus.COMPLETED) {
                actions.add(QuoteAction.INVOICE);
            }
        }
        return actions;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
