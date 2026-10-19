package com.fleetpilot.vehicle.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ConflictException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.IdentityDirectory.Account;
import com.fleetpilot.vehicle.application.IdentityDirectory.AccountData;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.repository.CompanyRepository;
import com.fleetpilot.vehicle.domain.repository.DriverRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration of the user accounts of the client companies, the drivers, the garages and the lessors. The FleetPilot
 * staff come from the corporate LDAP directory: their accounts are shown but managed in the directory. Each role needs
 * what it is attached to: a company for a client manager, a driver of that company for a driver, a garage for a
 * garage, a leasing company for a lessor.
 */
@Service
@Transactional
public class UserAdminService {

    /** Roles an administrator gives in FleetPilot; the staff roles come from LDAP groups. */
    public static final Set<String> MANAGED_ROLES = Set.of(Roles.CLIENT_MANAGER, Roles.DRIVER, Roles.GARAGE,
            Roles.API_SUPPLIER);

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Schema(description = "A user account and what it is attached to")
    public record UserView(String id, String username, String email, String firstName, String lastName, String fullName,
            boolean enabled, String role, UUID companyId, String companyName, UUID driverId, String driverName,
            UUID garageId, String lessor, boolean federated, Instant createdAt) {
    }

    /** The created account and its temporary password, shown once to the administrator. */
    public record CreatedUser(UserView user, String temporaryPassword) {
    }

    @Schema(description = "The account of a client manager, a driver, a garage or a lessor")
    public record SaveUser(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Schema(example = "client-manager") String role,
            UUID companyId,
            UUID driverId,
            UUID garageId,
            @Size(max = 40) String lessor,
            @NotNull Boolean enabled) {
    }

    public record UserCriteria(String q, String role, UUID companyId, Boolean enabled) {
    }

    private final IdentityDirectory directory;
    private final CompanyRepository companies;
    private final DriverRepository drivers;
    private final JdbcClient jdbc;
    private final AuditLog audit;

    public UserAdminService(IdentityDirectory directory, CompanyRepository companies, DriverRepository drivers,
            JdbcClient jdbc, AuditLog audit) {
        this.directory = directory;
        this.companies = companies;
        this.drivers = drivers;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<UserView> search(UserCriteria criteria, Pageable pageable) {
        Map<UUID, String> companyNames = companies.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));
        String q = criteria.q() == null ? "" : criteria.q().trim().toLowerCase(Locale.ROOT);
        List<UserView> all = directory.accounts().stream()
                .filter(a -> criteria.role() == null || criteria.role().isBlank() || criteria.role().equals(a.role()))
                .filter(a -> criteria.enabled() == null || criteria.enabled() == a.enabled())
                .filter(a -> criteria.companyId() == null
                        || a.attribute("company_id").map(criteria.companyId().toString()::equals).orElse(false))
                .filter(a -> q.isEmpty() || (a.username() + " " + a.email() + " " + a.firstName() + " " + a.lastName())
                        .toLowerCase(Locale.ROOT).contains(q))
                .map(a -> view(a, companyNames, Map.of()))
                .sorted(Comparator.comparing((UserView u) -> rank(u.role())).thenComparing(UserView::lastName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        Map<UUID, String> driverNames = driverNames(all.stream()
                .skip(pageable.getOffset()).limit(pageable.getPageSize()).map(UserView::driverId).toList());
        List<UserView> page = all.stream().skip(pageable.getOffset()).limit(pageable.getPageSize())
                .map(u -> u.driverId() == null ? u : withDriver(u, driverNames.get(u.driverId())))
                .toList();
        return new PageImpl<>(page, pageable, all.size());
    }

    @Transactional(readOnly = true)
    public UserView get(String id) {
        Account account = load(id);
        Map<UUID, String> companyNames = companies.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));
        UserView user = view(account, companyNames, Map.of());
        return user.driverId() == null ? user : withDriver(user, driverNames(List.of(user.driverId())).get(user.driverId()));
    }

    /** Lessors with contracts in the fleet, for the form of a lessor account. */
    @Transactional(readOnly = true)
    public List<String> lessors() {
        return jdbc.sql("select distinct lessor from lease_contract order by lessor").query(String.class).list();
    }

    public CreatedUser create(SaveUser cmd) {
        String email = cmd.email().trim().toLowerCase(Locale.ROOT);
        if (directory.accounts().stream().anyMatch(a -> email.equalsIgnoreCase(a.email()) || email.equals(a.username()))) {
            throw new ConflictException("USER_EMAIL_TAKEN", "An account already uses " + email);
        }
        Map<String, String> attributes = attributesFor(cmd, null);
        String password = temporaryPassword();
        String id = directory.create(new AccountData(email, email, cmd.firstName().trim(), cmd.lastName().trim(),
                cmd.enabled(), cmd.role(), attributes), password);
        audit.record("USER_CREATED", "USER", id, email, companyOf(attributes), cmd.role());
        return new CreatedUser(get(id), password);
    }

    public UserView update(String id, SaveUser cmd) {
        Account account = editable(id);
        String email = cmd.email().trim().toLowerCase(Locale.ROOT);
        if (directory.accounts().stream().anyMatch(a -> !a.id().equals(id) && email.equalsIgnoreCase(a.email()))) {
            throw new ConflictException("USER_EMAIL_TAKEN", "An account already uses " + email);
        }
        Map<String, String> attributes = attributesFor(cmd, id);
        directory.update(id, new AccountData(account.username(), email, cmd.firstName().trim(), cmd.lastName().trim(),
                cmd.enabled(), cmd.role(), attributes));
        audit.record(account.enabled() && !cmd.enabled() ? "USER_DISABLED"
                        : !account.enabled() && cmd.enabled() ? "USER_ENABLED" : "USER_UPDATED",
                "USER", id, account.username(), companyOf(attributes), cmd.role());
        return get(id);
    }

    public String resetPassword(String id) {
        Account account = editable(id);
        String password = temporaryPassword();
        directory.resetPassword(id, password);
        audit.record("USER_PASSWORD_RESET", "USER", id, account.username(),
                account.attribute("company_id").map(UUID::fromString).orElse(null), null);
        return password;
    }

    // ---------------------------------------------------------------- rules

    private Map<String, String> attributesFor(SaveUser cmd, String self) {
        if (!MANAGED_ROLES.contains(cmd.role())) {
            throw new BusinessRuleException("ROLE_NOT_MANAGED",
                    "FleetPilot staff accounts are managed in the corporate directory (LDAP), not in FleetPilot");
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        switch (cmd.role()) {
            case Roles.CLIENT_MANAGER -> attributes.put("company_id", activeCompany(cmd.companyId()).getId().toString());
            case Roles.DRIVER -> {
                Company company = activeCompany(cmd.companyId());
                if (cmd.driverId() == null) {
                    throw new BusinessRuleException("USER_DRIVER_REQUIRED", "A driver account is attached to a driver");
                }
                Driver driver = drivers.findById(cmd.driverId())
                        .orElseThrow(() -> new ResourceNotFoundException("Driver", cmd.driverId()));
                if (!driver.getCompanyId().equals(company.getId())) {
                    throw new BusinessRuleException("DRIVER_OF_ANOTHER_COMPANY",
                            driver.getFullName() + " does not work for " + company.getName());
                }
                directory.accountsWith("driver_id", driver.getId().toString()).stream()
                        .filter(other -> !other.id().equals(self))
                        .findAny()
                        .ifPresent(other -> {
                            throw new ConflictException("DRIVER_ALREADY_HAS_ACCOUNT",
                                    driver.getFullName() + " already signs in as " + other.username());
                        });
                attributes.put("company_id", company.getId().toString());
                attributes.put("driver_id", driver.getId().toString());
            }
            case Roles.GARAGE -> {
                if (cmd.garageId() == null) {
                    throw new BusinessRuleException("USER_GARAGE_REQUIRED", "A garage account is attached to a garage");
                }
                attributes.put("garage_id", cmd.garageId().toString());
            }
            case Roles.API_SUPPLIER -> {
                String lessor = cmd.lessor() == null ? "" : cmd.lessor().trim();
                if (!lessors().contains(lessor)) {
                    throw new BusinessRuleException("USER_LESSOR_REQUIRED",
                            "A lessor account is attached to a leasing company of the fleet");
                }
                attributes.put("lessor", lessor);
            }
            default -> throw new IllegalStateException(cmd.role());
        }
        return attributes;
    }

    private Company activeCompany(UUID id) {
        if (id == null) {
            throw new BusinessRuleException("USER_COMPANY_REQUIRED", "The account must be attached to a client company");
        }
        Company company = companies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company", id));
        if (!company.isActive()) {
            throw new BusinessRuleException("COMPANY_INACTIVE", company.getName() + " is no longer a client");
        }
        return company;
    }

    private Account load(String id) {
        return directory.account(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /** The staff accounts belong to LDAP, and nobody changes their own account here. */
    private Account editable(String id) {
        Account account = load(id);
        if (account.federated()) {
            throw new BusinessRuleException("USER_FROM_DIRECTORY",
                    account.username() + " comes from the corporate directory (LDAP) and is managed there");
        }
        if (CurrentUser.username().map(account.username()::equalsIgnoreCase).orElse(false)) {
            throw new BusinessRuleException("USER_IS_YOURSELF", "You cannot change your own account here");
        }
        return account;
    }

    // ---------------------------------------------------------------- views

    private static UserView view(Account a, Map<UUID, String> companyNames, Map<UUID, String> driverNames) {
        UUID companyId = a.attribute("company_id").map(UserAdminService::uuid).orElse(null);
        UUID driverId = a.attribute("driver_id").map(UserAdminService::uuid).orElse(null);
        String fullName = (Objects.toString(a.firstName(), "") + " " + Objects.toString(a.lastName(), "")).trim();
        return new UserView(a.id(), a.username(), a.email(), a.firstName(), a.lastName(),
                fullName.isEmpty() ? a.username() : fullName, a.enabled(), a.role(), companyId,
                companyId == null ? null : companyNames.get(companyId), driverId,
                driverId == null ? null : driverNames.get(driverId),
                a.attribute("garage_id").map(UserAdminService::uuid).orElse(null), a.attribute("lessor").orElse(null),
                a.federated(), a.createdAt());
    }

    private static UserView withDriver(UserView u, String driverName) {
        return new UserView(u.id(), u.username(), u.email(), u.firstName(), u.lastName(), u.fullName(), u.enabled(),
                u.role(), u.companyId(), u.companyName(), u.driverId(), driverName, u.garageId(), u.lessor(),
                u.federated(), u.createdAt());
    }

    private Map<UUID, String> driverNames(List<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).toList();
        return wanted.isEmpty() ? Map.of() : drivers.findAllById(wanted).stream()
                .collect(Collectors.toMap(Driver::getId, Driver::getFullName, (a, b) -> a, LinkedHashMap::new));
    }

    private static UUID companyOf(Map<String, String> attributes) {
        return attributes.containsKey("company_id") ? UUID.fromString(attributes.get("company_id")) : null;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Administrators first, then the staff, the client managers, the drivers, the garages and the lessors. */
    private static int rank(String role) {
        List<String> order = List.of(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER, Roles.CLIENT_MANAGER, Roles.DRIVER,
                Roles.GARAGE, Roles.API_SUPPLIER);
        int index = order.indexOf(role);
        return index < 0 ? order.size() : index;
    }

    /** Twelve characters from an alphabet without look-alikes (no 0/O, 1/l/I), plus a digit and a symbol. */
    static String temporaryPassword() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.append(2 + RANDOM.nextInt(8)).append('!').toString();
    }
}
