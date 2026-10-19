package com.fleetpilot.vehicle.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A photo of a claim or of an inspection, taken with the phone and resized before it is sent. */
@Entity
@Table(name = "photo")
public class Photo {

    public enum Owner {
        INCIDENT,
        INSPECTION
    }

    public static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    /** The app sends about 300 kB; a raw 12 megapixel photo is refused. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_PER_OWNER = 10;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false)
    private Owner ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Photo() {
    }

    public Photo(UUID id, Owner ownerType, UUID ownerId, UUID vehicleId, String contentType, byte[] content,
            Instant now) {
        if (contentType == null || !CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException("PHOTO_FORMAT", "A photo is a JPEG, PNG or WebP image");
        }
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new BusinessRuleException("PHOTO_SIZE", "A photo weighs at most 2 MB");
        }
        this.id = Objects.requireNonNull(id);
        this.ownerType = Objects.requireNonNull(ownerType);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.vehicleId = Objects.requireNonNull(vehicleId);
        this.contentType = contentType;
        this.sizeBytes = content.length;
        this.content = content;
        this.createdAt = Objects.requireNonNull(now);
    }

    public UUID getId() {
        return id;
    }

    public Owner getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
