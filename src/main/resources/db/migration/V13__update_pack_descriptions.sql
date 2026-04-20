-- V13: Update pack names and descriptions

UPDATE pack_types SET
  name = 'Pack Acompañamiento Supervisado',
  description = '10 sesiones de 45 minutos con IA, cada una revisada por un profesional en psicología. Renovable al finalizar.'
WHERE name = 'Pack Acompañamiento';

UPDATE pack_types SET
  name = 'Pack Integral',
  description = '10 sesiones de 45 minutos con IA + una entrevista final de cierre con un psicólogo.'
WHERE name = 'Pack Integral';

UPDATE pack_types SET
  name = 'Pack Personalizado',
  description = 'Entrevista de admisión + 10 sesiones de 45 minutos con un Licenciado en psicología.'
WHERE name = 'Pack Profesional';
