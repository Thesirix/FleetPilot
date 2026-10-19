package com.fleetpilot.energy.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.energy.application.EnergyViews.AssessmentView;
import com.fleetpilot.energy.domain.Transition.Assessment;
import com.fleetpilot.energy.domain.Transition.Eligibility;
import com.fleetpilot.energy.domain.Transition.EvModel;
import com.fleetpilot.energy.domain.Transition.ZfeRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** The catalogue, the zones and the assessments of the vehicles. */
public interface AssessmentRepository {

    List<EvModel> catalog();

    List<ZfeRule> zones();

    /** Adds the model, or replaces the one with the same code. */
    void saveModel(EvModel model);

    /** Removes the model; the assessments that proposed it no longer do. */
    void deleteModel(String code);

    void saveZone(ZfeRule zone);

    void deleteZone(String city);

    void replaceAll(List<Assessment> assessments, Instant at);

    Page<AssessmentView> search(UUID companyId, Eligibility status, String category, String q, String sort,
            Pageable pageable);

    Optional<AssessmentView> find(UUID vehicleId);

    List<AssessmentView> all(UUID companyId);
}
