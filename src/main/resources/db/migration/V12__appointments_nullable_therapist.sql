-- V12: Allow appointments without therapist (assigned from portal)
ALTER TABLE appointments ALTER COLUMN therapist_id DROP NOT NULL;
