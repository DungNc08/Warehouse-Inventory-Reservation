package com.warehouse.domain.model;

import com.warehouse.domain.enums.ReservationStatus;
import com.warehouse.domain.state.CancelledReservationState;
import com.warehouse.domain.state.ConfirmedReservationState;
import com.warehouse.domain.state.PendingReservationState;
import com.warehouse.domain.state.ReservationState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationItem> items = new ArrayList<>();

    protected Reservation() {
    }

    public Reservation(UUID id, String orderId) {
        this.id = id;
        this.orderId = orderId;
        this.status = ReservationStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ReservationItem> getItems() {
        return items;
    }

    public void addItem(ReservationItem item) {
        items.add(item);
        item.setReservation(this);
    }

    public void confirm() {
        resolveState().confirm(this);
    }

    public void cancel() {
        resolveState().cancel(this);
    }

    public void applyStatus(ReservationStatus newStatus) {
        this.status = newStatus;
    }

    private ReservationState resolveState() {
        return switch (status) {
            case PENDING -> new PendingReservationState();
            case CONFIRMED -> new ConfirmedReservationState();
            case CANCELLED -> new CancelledReservationState();
        };
    }
}
