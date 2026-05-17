-- Migración: asociar contextual_weight_config con usuarios (1 registro por usuario)

-- 1. Añadir columna user_id (nullable inicialmente)
ALTER TABLE contextual_weight_config ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- 2. Asignar usuario por defecto (id=3) a todos los registros existentes
UPDATE contextual_weight_config SET user_id = 3 WHERE user_id IS NULL;

-- 3. Hacer la columna NOT NULL
ALTER TABLE contextual_weight_config ALTER COLUMN user_id SET NOT NULL;

-- 4. Añadir unique sobre user_id (un registro por usuario)
ALTER TABLE contextual_weight_config
    ADD CONSTRAINT uq_cwc_user UNIQUE (user_id);
