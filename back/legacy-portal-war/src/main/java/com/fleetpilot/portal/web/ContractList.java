package com.fleetpilot.portal.web;

import java.util.Comparator;
import java.util.List;

import com.fleetpilot.portal.api.Contract;

/**
 * The home page of the extranet: the active contracts of the lessor with three figures (contracts, returns within 90
 * days, contracts heading for a mileage overrun), a filter, a search by registration or contract number, and pages of
 * 25 lines.
 */
public record ContractList(int active, int returnsSoon, int overrunRisk, int projectedOverrunKm, String filter,
        String search, List<ContractRow> rows, int page, int pages, int matching) {

    public static final int PAGE_SIZE = 25;
    /** A return is prepared three months ahead: inspection, buy-back offer, next vehicle. */
    public static final int RETURN_HORIZON_DAYS = 90;

    public static final String ALL = "all";
    public static final String RETURNS = "returns";
    public static final String OVERRUN = "overrun";

    public static ContractList of(List<Contract> contracts, String filter, String search, int requestedPage) {
        String chosen = filter(filter);
        List<Contract> matching = select(contracts, chosen, search);
        int pages = Math.max(1, (matching.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(Math.max(requestedPage, 1), pages);
        List<ContractRow> rows = matching.stream()
                .skip((long) (page - 1) * PAGE_SIZE)
                .limit(PAGE_SIZE)
                .map(ContractRow::of)
                .toList();
        int overrunKm = contracts.stream()
                .filter(Contract::overrunRisk)
                .mapToInt(c -> c.projectedOverrunKm() == null ? 0 : c.projectedOverrunKm())
                .sum();
        return new ContractList(contracts.size(),
                (int) contracts.stream().filter(c -> c.endsWithin(RETURN_HORIZON_DAYS)).count(),
                (int) contracts.stream().filter(Contract::overrunRisk).count(), overrunKm, chosen,
                search == null ? "" : search.trim(), rows, page, pages, matching.size());
    }

    /**
     * The contracts of the filter that match the search: the returns by end date, the overruns largest first, all
     * of them by end date. The CSV export takes the same lines.
     */
    public static List<Contract> select(List<Contract> contracts, String filter, String search) {
        String chosen = filter(filter);
        Comparator<Contract> order = OVERRUN.equals(chosen)
                ? Comparator.comparing((Contract c) -> c.projectedOverrunKm() == null ? 0 : c.projectedOverrunKm())
                        .reversed()
                : Comparator.comparing(Contract::endDate).thenComparing(Contract::contractNumber);
        return contracts.stream()
                .filter(c -> switch (chosen) {
                    case RETURNS -> c.endsWithin(RETURN_HORIZON_DAYS);
                    case OVERRUN -> c.overrunRisk();
                    default -> true;
                })
                .filter(c -> c.matches(search))
                .sorted(order)
                .toList();
    }

    private static String filter(String requested) {
        return RETURNS.equals(requested) || OVERRUN.equals(requested) ? requested : ALL;
    }

    public String projectedOverrun() {
        return Formats.km(projectedOverrunKm);
    }
}
