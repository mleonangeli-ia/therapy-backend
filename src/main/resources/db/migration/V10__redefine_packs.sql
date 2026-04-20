-- Deactivate all previous pack types
UPDATE pack_types SET is_active = false;

-- Pack 1: Acompañamiento IA — 10 sesiones IA con revisión de psicólogo, renovable
INSERT INTO pack_types (id, name, session_count, price_amount, price_currency, validity_days, description, is_active)
VALUES (gen_random_uuid(), 'Pack Acompañamiento', 10, 44999.00, 'ARS', 90,
    '10 sesiones con IA, cada una revisada por un psicólogo profesional. Incluye reporte por sesión. Renovable al finalizar.',
    true);

-- Pack 2: Integral — 10 sesiones IA + 1 entrevista de cierre con psicólogo
INSERT INTO pack_types (id, name, session_count, price_amount, price_currency, validity_days, description, is_active)
VALUES (gen_random_uuid(), 'Pack Integral', 10, 64999.00, 'ARS', 120,
    '10 sesiones con IA + una entrevista final de cierre con un psicólogo profesional. Incluye reporte integrador.',
    true);

-- Pack 3: Profesional — entrevista de admisión + sesiones con psicólogo
INSERT INTO pack_types (id, name, session_count, price_amount, price_currency, validity_days, description, is_active)
VALUES (gen_random_uuid(), 'Pack Profesional', 10, 119999.00, 'ARS', 120,
    'Entrevista de admisión + 10 sesiones individuales con un psicólogo profesional. Atención 100% humana con soporte de la plataforma.',
    true);
