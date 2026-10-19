package com.fleetpilot.quote.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.quote.domain.model.Garage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface GarageRepository extends JpaRepository<Garage, UUID>, JpaSpecificationExecutor<Garage> {

    Optional<Garage> findByCode(String code);

    Optional<Garage> findBySiret(String siret);

    /** A garage and its distance as the crow flies from a point, in kilometres. */
    interface GarageDistance {
        UUID getId();

        double getDistanceKm();
    }

    /**
     * Approved garages within a radius, nearest first (haversine formula on a sphere of 6,371 km). The bounding box on
     * latitude and longitude discards most rows before the trigonometry.
     */
    @Query(nativeQuery = true, value = """
            select id, distance_km as distanceKm from (
                select g.id, 6371 * 2 * asin(sqrt(
                        power(sin(radians(g.latitude - :lat) / 2), 2)
                        + cos(radians(:lat)) * cos(radians(g.latitude))
                          * power(sin(radians(g.longitude - :lon) / 2), 2))) as distance_km
                from garage g
                where g.approved
                  and g.latitude between :lat - :radiusKm / 111.0 and :lat + :radiusKm / 111.0
                  and g.longitude between :lon - :radiusKm / (111.0 * cos(radians(:lat)))
                                      and :lon + :radiusKm / (111.0 * cos(radians(:lat)))
            ) nearby
            where distance_km <= :radiusKm
            order by distance_km, id
            limit :limit""")
    List<GarageDistance> approvedNear(double lat, double lon, double radiusKm, int limit);
}
