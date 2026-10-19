package com.fleetpilot.vehicle.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    /** What a list of photos shows, without loading the images. */
    interface PhotoSummary {
        UUID getId();

        String getContentType();

        int getSizeBytes();

        Instant getCreatedAt();
    }

    @Query("select p.id as id, p.contentType as contentType, p.sizeBytes as sizeBytes, p.createdAt as createdAt "
            + "from Photo p where p.ownerType = :owner and p.ownerId = :ownerId order by p.createdAt")
    List<PhotoSummary> summaries(Photo.Owner owner, UUID ownerId);

    long countByOwnerTypeAndOwnerId(Photo.Owner owner, UUID ownerId);
}
