package com.fleetpilot.quote.api.rest;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.quote.application.QuoteAccess;
import com.fleetpilot.quote.application.QuoteService;
import com.fleetpilot.quote.application.SavingsQuery;
import com.fleetpilot.quote.application.command.QuoteCommands.AnswerCounterOffer;
import com.fleetpilot.quote.application.command.QuoteCommands.Completion;
import com.fleetpilot.quote.application.command.QuoteCommands.CounterOffer;
import com.fleetpilot.quote.application.command.QuoteCommands.Decision;
import com.fleetpilot.quote.application.command.QuoteCommands.IdentifyVehicle;
import com.fleetpilot.quote.application.command.QuoteCommands.InvoiceQuote;
import com.fleetpilot.quote.application.command.QuoteCommands.Rejection;
import com.fleetpilot.quote.application.command.QuoteCommands.ReviseQuote;
import com.fleetpilot.quote.application.command.QuoteCommands.SubmitQuote;
import com.fleetpilot.quote.application.view.QuoteViews.QuoteDetail;
import com.fleetpilot.quote.application.view.QuoteViews.QuoteSummary;
import com.fleetpilot.quote.application.view.QuoteViews.SavingsSummary;
import com.fleetpilot.quote.domain.model.QuoteChannel;
import com.fleetpilot.quote.domain.model.QuoteStatus;
import com.fleetpilot.quote.domain.repository.QuoteSpecifications.QuoteCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
@PreAuthorize(Roles.ANY_MANAGER_OR_GARAGE)
@Tag(name = "Quotes", description = "Garage quotes, analysis against the reference tariff, negotiation, invoices")
public class QuoteController {

    /** OAuth2 client of the integration bus: the quotes it sends come from the garages' files. */
    static final String INTEGRATION_BUS = "fleetpilot-services";

    private final QuoteService quotes;
    private final SavingsQuery savings;
    private final QuoteAccess access;

    public QuoteController(QuoteService quotes, SavingsQuery savings, QuoteAccess access) {
        this.quotes = quotes;
        this.savings = savings;
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "Search quotes", description = "A garage sees its own quotes, a client manager those of its "
            + "company. Sort by submittedAt, amount, overcharge, reference, status...")
    public PageResponse<QuoteSummary> search(@ParameterObject QuoteFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(quotes.search(filter.toCriteria(), pageable));
    }

    /**
     * Filters of the quote list, all optional.
     *
     * @param open         true: waiting for a decision, false: decided
     * @param withFindings true: only quotes with findings
     * @param q            reference of the quote, of the garage, or registration
     */
    public record QuoteFilter(UUID companyId, UUID garageId, UUID vehicleId, QuoteStatus status,
            @Parameter(description = "true: waiting for a decision, false: decided") Boolean open,
            @Parameter(description = "true: only quotes with findings") Boolean withFindings,
            @Parameter(description = "Reference of the quote, of the garage, or registration") String q,
            Instant from, Instant to) {

        QuoteCriteria toCriteria() {
            return new QuoteCriteria(companyId, garageId, vehicleId, status, open, withFindings, q, from, to);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Quote file", description = "Lines with their analysis, timeline, and the actions the "
            + "caller may take")
    public QuoteDetail get(@PathVariable UUID id) {
        return quotes.get(id);
    }

    @GetMapping("/by-reference/{reference}")
    @Operation(summary = "Find a quote by its FleetPilot reference (DEV-2026-000123)")
    public QuoteDetail getByReference(@PathVariable String reference) {
        return quotes.getByReference(reference);
    }

    @GetMapping("/savings")
    @PreAuthorize(Roles.ANY_MANAGER)
    @Operation(summary = "Savings indicators", description = "Quotes by status, savings obtained, overcharges left "
            + "to negotiate, findings by type, garages to watch, last twelve months")
    public SavingsSummary savings(@RequestParam(required = false) UUID companyId) {
        return savings.summary(access.restrictedCompanyId().orElse(companyId));
    }

    @PostMapping
    @PreAuthorize(Roles.GARAGE_OR_STAFF)
    @Operation(summary = "Submit a quote", description = "The vehicle is identified from its registration and the "
            + "quote is analysed at once. A garage submits for itself; the integration bus sends the quotes it received")
    public ResponseEntity<QuoteDetail> submit(@Valid @RequestBody SubmitQuote command) {
        QuoteChannel channel = INTEGRATION_BUS.equals(CurrentUser.clientId().orElse(null)) ? QuoteChannel.ESB
                : CurrentUser.hasAnyRole(Roles.API_SUPPLIER) ? QuoteChannel.API : QuoteChannel.PORTAL;
        QuoteDetail created = quotes.submit(command, channel);
        return ResponseEntity.created(URI.create("/api/v1/quotes/" + created.id())).body(created);
    }

    @PostMapping("/{id}/vehicle")
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Identify the vehicle of a received quote", description = "Then the quote is analysed")
    public QuoteDetail identifyVehicle(@PathVariable UUID id, @Valid @RequestBody IdentifyVehicle command) {
        return quotes.identifyVehicle(id, command);
    }

    @PostMapping("/{id}/counter-offer")
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Propose a lower amount to the garage")
    public QuoteDetail counterOffer(@PathVariable UUID id, @Valid @RequestBody CounterOffer command) {
        return quotes.counterOffer(id, command);
    }

    @PostMapping("/{id}/counter-offer/answer")
    @PreAuthorize(Roles.GARAGE_OR_STAFF)
    @Operation(summary = "Answer a counter-offer", description = "Accepting it accepts the quote at that amount")
    public QuoteDetail answerCounterOffer(@PathVariable UUID id, @Valid @RequestBody AnswerCounterOffer command) {
        return quotes.answerCounterOffer(id, command);
    }

    @PostMapping("/{id}/revisions")
    @PreAuthorize(Roles.GARAGE_OR_STAFF)
    @Operation(summary = "Send a new version of the quote", description = "It replaces the lines and is analysed again")
    public QuoteDetail revise(@PathVariable UUID id, @Valid @RequestBody ReviseQuote command) {
        return quotes.revise(id, command);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Accept the quote", description = "A justification is required when it has overcharges. "
            + "Above the approval limit of fleet managers, only an administrator may accept")
    public QuoteDetail accept(@PathVariable UUID id, @Valid @RequestBody Decision command) {
        return quotes.accept(id, command);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Reject the quote")
    public QuoteDetail reject(@PathVariable UUID id, @Valid @RequestBody Rejection command) {
        return quotes.reject(id, command);
    }

    @PostMapping("/{id}/completion")
    @PreAuthorize(Roles.GARAGE_OR_STAFF)
    @Operation(summary = "Report the work as done")
    public QuoteDetail complete(@PathVariable UUID id, @Valid @RequestBody Completion command) {
        return quotes.complete(id, command);
    }

    @PostMapping("/{id}/invoice")
    @PreAuthorize(Roles.GARAGE_OR_STAFF)
    @Operation(summary = "Send the invoice", description = "Reconciled with the accepted amount; an invoice above it "
            + "beyond 2 % (min 10 EUR) is disputed")
    public QuoteDetail invoice(@PathVariable UUID id, @Valid @RequestBody InvoiceQuote command) {
        return quotes.invoice(id, command);
    }
}
