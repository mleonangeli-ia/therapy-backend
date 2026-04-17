INSERT INTO pack_types (id, name, session_count, price_amount, price_currency, validity_days, description, is_active)
VALUES
    (gen_random_uuid(), 'Pack Premium', 10, 69999.00, 'ARS', 120,
     '10 sesiones con IA + sesión de cierre con un psicólogo profesional. Incluye reporte integrador final elaborado por el profesional. Válido por 120 días.',
     true);
