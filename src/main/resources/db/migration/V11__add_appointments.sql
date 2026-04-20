-- ============================================================
-- V11: Appointments — patients can schedule sessions with therapists
-- ============================================================

CREATE TABLE appointments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_id         UUID        NOT NULL REFERENCES packs(id),
    patient_id      UUID        NOT NULL REFERENCES patients(id),
    therapist_id    UUID        NOT NULL REFERENCES therapists(id),
    scheduled_at    TIMESTAMPTZ NOT NULL,
    duration_minutes INT        NOT NULL DEFAULT 50,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_appointments_patient   ON appointments(patient_id);
CREATE INDEX idx_appointments_therapist ON appointments(therapist_id);
CREATE INDEX idx_appointments_scheduled ON appointments(scheduled_at);
CREATE INDEX idx_appointments_pack      ON appointments(pack_id);
