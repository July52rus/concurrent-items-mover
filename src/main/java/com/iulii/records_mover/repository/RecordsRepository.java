package com.iulii.records_mover.repository;

import com.iulii.records_mover.models.MovableRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordsRepository extends JpaRepository<MovableRecord, UUID> {

    @Query(value = """
        SELECT * FROM movable_records
        ORDER BY position
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """, nativeQuery = true)
    List<MovableRecord> findWithPagination(
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Query(value = """
        SELECT COUNT(*) FROM movable_records
        """, nativeQuery = true)
    long countAllRecords();

    // Находит элемент с максимальной позицией
    @Query("SELECT COALESCE(MAX(m.position), 0.0) FROM MovableRecord m")
    Double findMaxPosition();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MovableRecord m WHERE m.id = :id")
    Optional<MovableRecord> findByIdWithLock(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT m FROM MovableRecord m
    WHERE m.position >= :position
    AND (:lastId IS NULL OR m.id > :lastId)
    ORDER BY m.position ASC, m.id ASC
    """)
    List<MovableRecord> findAndLockNextItemsById(
            @Param("position") Double position,
            @Param("lastId") UUID lastId,
            Pageable pageable);

    // Атомарное обновление с проверкой позиций не вышло
    @Modifying
    @Query(nativeQuery = true, value = """
        UPDATE movable_records
        SET position = :newPosition
        WHERE id = :id
        AND NOT EXISTS (
            SELECT 1 FROM movable_records
            WHERE position = :newPosition
        )
        AND (
            :afterId IS NULL OR
            EXISTS (SELECT 1 FROM movable_records WHERE id = :afterId AND position = :afterPos)
        )
        AND (
            :beforeId IS NULL OR
            EXISTS (SELECT 1 FROM movable_records WHERE id = :beforeId AND position = :beforePos)
        )
        """)
    int updatePositionSafely(
            @Param("id") UUID id,
            @Param("newPosition") Double newPosition,
            @Param("afterId") UUID afterId,
            @Param("afterPos") Double afterPos,
            @Param("beforeId") UUID beforeId,
            @Param("beforePos") Double beforePos);

    @Query("SELECT m.position FROM MovableRecord m WHERE m.id = :id")
    Optional<Double> findPositionById(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE MovableRecord m SET m.position = :newPosition WHERE m.id = :id")
    void updatePosition(@Param("id") UUID id, @Param("newPosition") Double newPosition);

    // Проверяет существование элемента на позиции
    boolean existsByPosition(Double position);
}
