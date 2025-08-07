package com.iulii.records_mover.service;

import com.iulii.records_mover.models.MovableRecord;
import com.iulii.records_mover.repository.RecordsRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Retryable;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordsServiceTest {

    @Mock
    private RecordsRepository repository;

    @InjectMocks
    private RecordsService recordsService;

    private UUID itemId;
    private UUID afterId;
    private UUID beforeId;
    private MovableRecord movableRecord;
    private MovableRecord afterRecord;
    private MovableRecord beforeRecord;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        afterId = UUID.randomUUID();
        beforeId = UUID.randomUUID();

        movableRecord = new MovableRecord();
        movableRecord.setId(itemId);
        movableRecord.setPosition(1.5);

        afterRecord = new MovableRecord();
        afterRecord.setId(afterId);
        afterRecord.setPosition(1.0);

        beforeRecord = new MovableRecord();
        beforeRecord.setId(beforeId);
        beforeRecord.setPosition(2.0);
    }

    @Test
    void getRecords_shouldReturnPageOfRecords() {
        // Arrange
        int page = 0;
        int size = 10;
        Pageable pageable = PageRequest.of(page, size);
        List<MovableRecord> records = List.of(movableRecord);
        when(repository.findWithPagination(0, size)).thenReturn(records);
        when(repository.countAllRecords()).thenReturn(1L);

        // Act
        Page<MovableRecord> result = recordsService.getRecords(page, size);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(records, result.getContent());
        verify(repository).findWithPagination(0, size);
        verify(repository).countAllRecords();
    }

    @Test
    void moveItemBetween_shouldUpdatePositionWhenGapExists() {
        // Arrange
        when(repository.findByIdWithLock(itemId)).thenReturn(Optional.of(movableRecord));
        when(repository.findByIdWithLock(afterId)).thenReturn(Optional.of(afterRecord));
        when(repository.findByIdWithLock(beforeId)).thenReturn(Optional.of(beforeRecord));
        when(repository.existsByPosition(1.5)).thenReturn(false);

        // Act
        recordsService.moveItemBetween(itemId, afterId, beforeId);

        // Assert
        verify(repository).updatePosition(itemId, 1.5);
    }

    @Test
    void moveItemBetween_shouldThrowExceptionWhenItemNotFound() {
        // Arrange
        when(repository.findByIdWithLock(itemId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> recordsService.moveItemBetween(itemId, afterId, beforeId));
    }

    @Test
    void moveItemBetween_shouldThrowExceptionWhenAfterItemNotFound() {
        // Arrange
        when(repository.findByIdWithLock(itemId)).thenReturn(Optional.of(movableRecord));
        when(repository.findByIdWithLock(afterId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> recordsService.moveItemBetween(itemId, afterId, beforeId));
    }

    @Test
    void moveItemBetween_shouldThrowExceptionWhenBeforeItemNotFound() {
        // Arrange
        when(repository.findByIdWithLock(itemId)).thenReturn(Optional.of(movableRecord));
        when(repository.findByIdWithLock(afterId)).thenReturn(Optional.of(afterRecord));
        when(repository.findByIdWithLock(beforeId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> recordsService.moveItemBetween(itemId, afterId, beforeId));
    }

    @Test
    void moveItemBetween_shouldCreateGapWhenNoSpace() {
        // Arrange
        MovableRecord tightBeforeRecord = new MovableRecord();
        tightBeforeRecord.setId(beforeId);
        tightBeforeRecord.setPosition(1.00000000001);

        when(repository.findByIdWithLock(itemId)).thenReturn(Optional.of(movableRecord));
        when(repository.findByIdWithLock(afterId)).thenReturn(Optional.of(afterRecord));
        when(repository.findByIdWithLock(beforeId)).thenReturn(Optional.of(tightBeforeRecord));
        when(repository.existsByPosition(1.0000000005)).thenReturn(false);

        // Act
        recordsService.moveItemBetween(itemId, afterId, beforeId);

        // Assert
        verify(repository).findAndLockNextItemsById(any(), any(), any());
    }

    @Test
    void moveItemBetween_shouldRetryWhenConcurrentModificationOccurs() throws NoSuchMethodException {
        // Arrange
        Retryable retryable = RecordsService.class.getMethod("moveItemBetween", UUID.class, UUID.class, UUID.class).getAnnotation(Retryable.class);

        // Assert
        assertEquals(3, retryable.maxAttempts());
        assertEquals(50, retryable.backoff().delay());
        assertEquals(ConcurrentModificationException.class, retryable.retryFor()[0]);
    }
}