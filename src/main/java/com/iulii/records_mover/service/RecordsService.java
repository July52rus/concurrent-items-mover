package com.iulii.records_mover.service;

import com.iulii.records_mover.models.MovableRecord;
import com.iulii.records_mover.repository.RecordsRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordsService {

    private static final double MIN_GAP = 0.0000000001;
    private static final int BATCH_SIZE = 500;
    private final RecordsRepository repository;

    public Page<MovableRecord> getRecords(int page, int size) {
        int offset = page * size;
        List<MovableRecord> content = repository.findWithPagination(offset, size);
        long total = repository.countAllRecords();

        return new PageImpl<>(
                content,
                PageRequest.of(page, size),
                total
        );
    }

    // Перемещение элемента между двумя другими или перед элементом выше когда сверху нет элемента и после элемента ниже, когда внизу нет границы
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 50), retryFor = ConcurrentModificationException.class)
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void moveItemBetween(UUID itemId, UUID afterId, UUID beforeId) {
        // Блокируем только перемещаемый элемент
        MovableRecord itemToMove = repository.findByIdWithLock(itemId).orElseThrow(() -> new EntityNotFoundException("Item not found"));

        NeighborPositions neighborPositions = getNeighborPositions(afterId, beforeId);

        Double newPosition = findAvailablePosition(neighborPositions.afterPos, neighborPositions.beforePos);

        //  Проверяем, что позиция свободна (дополнительная защита)
        if (repository.existsByPosition(newPosition)) {
            throw new ConcurrentModificationException("Position was occupied after calculation");
        }

        //  Атомарное обновление с проверкой
        repository.updatePosition(itemId, newPosition);
    }

    // Рекурсивный поиск доступной позиции
    private Double findAvailablePosition(Double afterPos, Double beforePos) {
        if (beforePos - afterPos < MIN_GAP) {
            return createGap(afterPos, beforePos);
        }

        Double middle = (afterPos + beforePos) / 2;

        if (!repository.existsByPosition(middle)) {
            return middle;
        }

        // Рекурсивно находим новую позицию до середины и после середины
        Double result = findAvailablePosition(afterPos, middle);
        if (result == null) {
            result = findAvailablePosition(middle, beforePos);
        }

        return result;
    }

    // Похожу операцию можно осуществлять по расписанию для перебалансировки записей по позициям
    private Double createGap(Double afterPos, Double beforePos) {

        double requiredGap = MIN_GAP * 10; // Буфер в 3 раза больше минимума
        double existingGap = beforePos - afterPos;
        double shiftAmount = requiredGap - existingGap;

        // Пакетный сдвиг с блокировками
        shift500ItemsInBatches(beforePos, shiftAmount);

        return afterPos + (requiredGap / 2);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void shift500ItemsInBatches(Double fromPosition, Double shiftAmount) {
        List<MovableRecord> batch = repository.findAndLockNextItemsById(fromPosition, null, PageRequest.of(0, BATCH_SIZE));
        batch.forEach(item -> item.setPosition(item.getPosition() + shiftAmount));
        repository.saveAll(batch);
    }

    private NeighborPositions getNeighborPositions(UUID afterId, UUID beforeId) {
        Double afterPos = afterId != null ? repository.findByIdWithLock(afterId).orElseThrow(() -> new EntityNotFoundException("After item not found")).getPosition() : 0;

        Double beforePos = beforeId != null ? repository.findByIdWithLock(beforeId).orElseThrow(() -> new EntityNotFoundException("Before item not found")).getPosition() : repository.findMaxPosition() + 100;

        return new NeighborPositions(afterPos, beforePos);
    }


    //Рекорд для хранения позиций между которых перемещаем для удобства
    private record NeighborPositions(Double afterPos, Double beforePos) {
    }
}
