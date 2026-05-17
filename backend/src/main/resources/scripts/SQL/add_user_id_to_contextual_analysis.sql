-- Migración: asociar fixture_contextual_analysis con usuarios
-- Ejecutar en orden

-- 1. Añadir columna user_id (nullable inicialmente para poder migrar datos)
ALTER TABLE fixture_contextual_analysis ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- 2. Asignar usuario por defecto (id=3) a todos los análisis existentes
UPDATE fixture_contextual_analysis SET user_id = 3 WHERE user_id IS NULL;

-- 3. Hacer la columna NOT NULL
ALTER TABLE fixture_contextual_analysis ALTER COLUMN user_id SET NOT NULL;

-- 4. Eliminar el unique constraint antiguo sobre fixture_id
ALTER TABLE fixture_contextual_analysis DROP CONSTRAINT IF EXISTS fixture_contextual_analysis_fixture_id_key;

-- 5. Añadir unique compuesto sobre (fixture_id, user_id)
ALTER TABLE fixture_contextual_analysis
    ADD CONSTRAINT uq_fca_fixture_user UNIQUE (fixture_id, user_id);
