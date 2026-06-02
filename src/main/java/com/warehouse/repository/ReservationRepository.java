package com.warehouse.repository;

import com.warehouse.domain.model.Reservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Reservation> findWithItemsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findWithItemsByIdForUpdate(@Param("id") UUID id);

    boolean existsByOrderId(String orderId);
}
