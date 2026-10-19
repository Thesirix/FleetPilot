package com.fleetpilot.quote.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.quote.domain.model.Quote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface QuoteRepository extends JpaRepository<Quote, UUID>, JpaSpecificationExecutor<Quote> {

    @Query(value = "select nextval('quote_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    Optional<Quote> findByGarageIdAndGarageReference(UUID garageId, String garageReference);

    Optional<Quote> findByReference(String reference);

    @Override
    @EntityGraph(attributePaths = {"garage", "vehicle"})
    Page<Quote> findAll(Specification<Quote> spec, Pageable pageable);
}
