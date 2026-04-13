-- ============================================================
-- V2: Seed initial pack types
-- ============================================================

INSERT INTO pack_types (id, name, session_count, price_amount, price_currency, validity_days, description, is_active)
VALUES
    (gen_random_uuid(), 'Pack Inicial', 10, 29999.00, 'ARS', 90,
     'Pack de 10 sesiones terapéuticas con IA. Incluye seguimiento de progreso y reporte profesional por sesión. Válido por 90 días.',
     true),
    (gen_random_uuid(), 'Pack Continuidad', 10, 27999.00, 'ARS', 90,
     'Pack de renovación para pacientes que ya completaron su primer pack. Mantené tu continuidad terapéutica.',
     true);
