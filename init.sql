CREATE TABLE IF NOT EXISTS movable_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    value TEXT NOT NULL,
    position float8 NOT NULL,
    CONSTRAINT unique_position UNIQUE (position)
);

-- Индекс для быстрого поиска по позиции
CREATE INDEX idx_movable_records_position ON movable_records(position);

-- Функция для генерации 10 млн тестовых записей
INSERT INTO movable_records (value, position)
SELECT
    'Item ' || n,
    n * 100.0 -- Начальный шаг 100.0 для запаса
FROM generate_series(1, 10000000) AS n;