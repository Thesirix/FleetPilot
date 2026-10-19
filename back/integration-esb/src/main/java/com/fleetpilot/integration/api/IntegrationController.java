package com.fleetpilot.integration.api;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.integration.journal.ExchangeJournal;
import com.fleetpilot.integration.journal.ExchangeStatus;
import com.fleetpilot.integration.journal.FileIntake;
import com.fleetpilot.integration.journal.Flow;
import com.fleetpilot.integration.journal.JournalViews.ExchangeDetail;
import com.fleetpilot.integration.journal.JournalViews.ExchangeSummary;
import com.fleetpilot.integration.journal.JournalViews.FlowActivity;
import com.fleetpilot.integration.journal.ProcessedFiles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The integration console: the flows with the partners and the state of their Camel routes, the journal of the
 * exchanges with the issues found line by line, the replay of a failed or rejected file, and the drop of a file as a
 * partner would do it.
 */
@RestController
@RequestMapping("/api/v1/integration")
@PreAuthorize(Roles.ANY_STAFF)
@Tag(name = "Integration", description = "Partner flows, journal of the exchanges, replay")
public class IntegrationController {

    private static final Map<Flow, List<String>> ROUTES = Map.of(
            Flow.GARAGE_QUOTES, List.of("garages"),
            Flow.LESSOR_INVOICES, List.of("lessors"),
            Flow.FUEL_CARDS, List.of("fuelcards"),
            Flow.INSURER_CLAIMS, List.of("insurer-events", "insurer-declare", "insurer-follow-up"));
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ExchangeJournal journal;
    private final ProcessedFiles processed;
    private final CamelContext camel;
    private final Path inbox;
    private final Clock clock;

    public IntegrationController(ExchangeJournal journal, ProcessedFiles processed, CamelContext camel,
            @Value("${fleetpilot.integration.inbox}") String inbox, Clock clock) {
        this.journal = journal;
        this.processed = processed;
        this.camel = camel;
        this.inbox = Path.of(inbox);
        this.clock = clock;
    }

    /** A Camel route of a flow and its state. */
    public record RouteView(String id, String description, String status, String endpoint) {
    }

    public record FlowView(Flow flow, String folder, List<RouteView> routes, FlowActivity activity) {
    }

    public record Accepted(String fileName, String message) {
    }

    @GetMapping("/flows")
    @Operation(summary = "The partner flows", description = "Camel routes and their state, activity of 30 days")
    public List<FlowView> flows() {
        List<FlowActivity> activity = journal.activity();
        return activity.stream().map(a -> new FlowView(a.flow(), a.flow().folder(),
                ROUTES.get(a.flow()).stream().map(this::route).toList(), a)).toList();
    }

    private RouteView route(String id) {
        Route route = camel.getRoute(id);
        ServiceStatus status = camel.getRouteController().getRouteStatus(id);
        return new RouteView(id, route == null ? null : route.getDescription(),
                status == null ? "Unknown" : status.name(),
                route == null ? null : route.getEndpoint().getEndpointBaseUri());
    }

    @GetMapping("/exchanges")
    @Operation(summary = "Journal of the exchanges", description = "Most recent first")
    public PageResponse<ExchangeSummary> exchanges(@RequestParam(required = false) Flow flow,
            @RequestParam(required = false) ExchangeStatus status,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(journal.search(flow, status, pageable));
    }

    @GetMapping("/exchanges/{id}")
    @Operation(summary = "An exchange", description = "With its content as received and the issues found")
    public ExchangeDetail exchange(@PathVariable UUID id) {
        return journal.detail(id).orElseThrow(() -> new ResourceNotFoundException("Exchange", id));
    }

    @GetMapping("/exchanges/{id}/content")
    @Operation(summary = "The file as the partner sent it")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        ExchangeDetail detail = exchange(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(detail.contentType() + ";charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + detail.summary().source() + "\"")
                .body(detail.payload() == null ? new byte[0] : detail.payload().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/exchanges/{id}/replay")
    @Operation(summary = "Replay a file", description = "The file goes back into the inbox of its flow, once the cause "
            + "of the failure or of the rejection is fixed (a service back up, a garage registered...)")
    public ResponseEntity<Accepted> replay(@PathVariable UUID id) throws IOException {
        ExchangeDetail detail = exchange(id);
        ExchangeSummary exchange = detail.summary();
        if (!exchange.flow().receivesFiles() || detail.payload() == null) {
            throw new BusinessRuleException("NOT_REPLAYABLE", "Only the files received from a partner can be replayed");
        }
        if (exchange.status() != ExchangeStatus.FAILED && exchange.status() != ExchangeStatus.REJECTED
                && exchange.status() != ExchangeStatus.PARTIAL) {
            throw new BusinessRuleException("NOT_REPLAYABLE", "Only a failed, rejected or partially accepted file can "
                    + "be replayed");
        }
        // the content was handled before: forget it, so that the idempotent consumer lets it through again
        processed.remove(ProcessedFiles.key(exchange.flow(), FileIntake.sha256(detail.payload())));
        String name = "replay-" + id + "__" + exchange.source();
        write(exchange.flow(), name, detail.payload().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.accepted().body(new Accepted(name, "Fichier remis dans la file " + exchange.flow().folder()));
    }

    @PostMapping(path = "/inbox/{flow}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Drop a file", description = "As a partner would on the SFTP server: the route of the flow "
            + "picks it up within a few seconds")
    public ResponseEntity<Accepted> drop(@PathVariable Flow flow, @RequestPart("file") MultipartFile file)
            throws IOException {
        if (!flow.receivesFiles()) {
            throw new BusinessRuleException("NO_FILE_FLOW", "The flow " + flow + " receives no file");
        }
        String original = file.getOriginalFilename() == null ? "fichier" : Path.of(file.getOriginalFilename())
                .getFileName().toString();
        String base = original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!base.toLowerCase(Locale.ROOT).endsWith(flow.extension())) {
            throw new BusinessRuleException("WRONG_EXTENSION", "The flow " + flow + " expects " + flow.extension()
                    + " files");
        }
        String name = LocalDateTime.now(clock).format(STAMP) + "_" + base;
        write(flow, name, file.getBytes());
        return ResponseEntity.created(URI.create("/api/v1/integration/exchanges?flow=" + flow))
                .body(new Accepted(name, "Fichier déposé dans la file " + flow.folder()));
    }

    /** Written under a temporary name then renamed, so that the route never reads a half-written file. */
    private void write(Flow flow, String name, byte[] content) throws IOException {
        Path folder = inbox.resolve(flow.folder());
        Files.createDirectories(folder);
        Path temporary = folder.resolve("." + name + ".part");
        Files.write(temporary, content);
        Files.move(temporary, folder.resolve(name));
    }
}
