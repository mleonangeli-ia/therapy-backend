-- ============================================================
-- V1: Initial schema
-- ============================================================

-- Enable pgcrypto for UUID generation and column encryption
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- PACK TYPES (catalog)
-- ============================================================
CREATE TABLE pack_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    session_count   INT NOT NULL DEFAULT 10,
    price_amount    DECIMAL(10, 2) NOT NULL,
    price_currency  VARCHAR(3) NOT NULL DEFAULT 'ARS',
    validity_days   INT NOT NULL DEFAULT 90,
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- PATIENTS
-- ============================================================
CREATE TABLE patients (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(255) NOT NULL,
    date_of_birth       DATE,
    phone               VARCHAR(50),
    country_code        CHAR(2) NOT NULL DEFAULT 'AR',
    timezone            VARCHAR(100) NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    language            VARCHAR(10) NOT NULL DEFAULT 'es',
    consent_signed_at   TIMESTAMPTZ,
    consent_version     VARCHAR(20),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    email_verified      BOOLEAN NOT NULL DEFAULT false,
    email_verify_token  VARCHAR(255),
    reset_password_token        VARCHAR(255),
    reset_password_token_expiry TIMESTAMPTZ,
    failed_login_attempts       INT NOT NULL DEFAULT 0,
    locked_until                TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_email ON patients(email);
CREATE INDEX idx_patients_is_active ON patients(is_active);

-- ============================================================
-- PATIENT CONSENT HISTORY
-- ============================================================
CREATE TABLE patient_consent_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    consent_version VARCHAR(20) NOT NULL,
    signed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address      INET,
    user_agent      TEXT,
    content_hash    VARCHAR(64)
);

CREATE INDEX idx_consent_history_patient ON patient_consent_history(patient_id);

-- ============================================================
-- PACKS
-- ============================================================
CREATE TYPE pack_status AS ENUM (
    'PENDING_PAYMENT',
    'ACTIVE',
    'COMPLETED',
    'EXPIRED',
    'REFUNDED',
    'CANCELLED'
);

CREATE TABLE packs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    pack_type_id        UUID NOT NULL REFERENCES pack_types(id),
    status              pack_status NOT NULL DEFAULT 'PENDING_PAYMENT',
    sessions_used       INT NOT NULL DEFAULT 0,
    sessions_total      INT NOT NULL DEFAULT 10,
    purchased_at        TIMESTAMPTZ,
    activated_at        TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    mp_preference_id    VARCHAR(255),
    mp_payment_id       VARCHAR(255),
    mp_payment_status   VARCHAR(50),
    mp_external_ref     VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_packs_patient_id ON packs(patient_id);
CREATE INDEX idx_packs_status ON packs(patient_id, status);
CREATE INDEX idx_packs_mp_payment_id ON packs(mp_payment_id);
CREATE INDEX idx_packs_mp_external_ref ON packs(mp_external_ref);

-- ============================================================
-- PAYMENT EVENTS
-- ============================================================
CREATE TYPE payment_event_type AS ENUM (
    'PREFERENCE_CREATED',
    'PAYMENT_APPROVED',
    'PAYMENT_REJECTED',
    'PAYMENT_PENDING',
    'PAYMENT_CANCELLED',
    'REFUND_REQUESTED',
    'REFUND_COMPLETED'
);

CREATE TABLE payment_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_id         UUID NOT NULL REFERENCES packs(id) ON DELETE RESTRICT,
    patient_id      UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    event_type      payment_event_type NOT NULL,
    mp_payment_id   VARCHAR(255),
    mp_status       VARCHAR(50),
    amount          DECIMAL(10, 2),
    currency        VARCHAR(3),
    raw_payload     JSONB,
    idempotency_key VARCHAR(255) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_events_pack ON payment_events(pack_id);
CREATE INDEX idx_payment_events_patient ON payment_events(patient_id, created_at DESC);

-- ============================================================
-- SESSIONS
-- ============================================================
CREATE TYPE session_status AS ENUM (
    'IN_PROGRESS',
    'COMPLETED',
    'ABANDONED'
);

CREATE TYPE session_modality AS ENUM (
    'TEXT',
    'AUDIO',
    'MIXED'
);

CREATE TABLE sessions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_id                 UUID NOT NULL REFERENCES packs(id) ON DELETE RESTRICT,
    patient_id              UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    session_number          INT NOT NULL,
    status                  session_status NOT NULL DEFAULT 'IN_PROGRESS',
    modality                session_modality NOT NULL DEFAULT 'TEXT',
    started_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at                TIMESTAMPTZ,
    duration_seconds        INT,
    turn_count              INT NOT NULL DEFAULT 0,
    ai_model_used           VARCHAR(100),
    therapeutic_focus       TEXT,
    mood_start              SMALLINT CHECK (mood_start BETWEEN 1 AND 10),
    mood_end                SMALLINT CHECK (mood_end BETWEEN 1 AND 10),
    crisis_flag             BOOLEAN NOT NULL DEFAULT false,
    crisis_details_enc      TEXT,   -- encrypted
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (pack_id, session_number)
);

CREATE INDEX idx_sessions_patient_status ON sessions(patient_id, status);
CREATE INDEX idx_sessions_pack ON sessions(pack_id, session_number);

-- ============================================================
-- SESSION MESSAGES
-- ============================================================
CREATE TYPE message_role AS ENUM ('PATIENT', 'ASSISTANT', 'SYSTEM');
CREATE TYPE message_content_type AS ENUM ('TEXT', 'AUDIO_TRANSCRIPT', 'AUDIO_RESPONSE');

CREATE TABLE session_messages (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id                  UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    role                        message_role NOT NULL,
    content_type                message_content_type NOT NULL DEFAULT 'TEXT',
    content_text_enc            TEXT,       -- encrypted; null for audio-only
    audio_s3_key                VARCHAR(500),
    audio_duration_ms           INT,
    transcription_confidence    FLOAT,
    sequence_number             INT NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_session ON session_messages(session_id, sequence_number);

-- ============================================================
-- SESSION CONTEXTS (cross-session AI memory)
-- ============================================================
CREATE TABLE session_contexts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    pack_id             UUID NOT NULL REFERENCES packs(id) ON DELETE CASCADE,
    session_number      INT NOT NULL,
    summary_enc         TEXT,       -- encrypted
    key_themes_enc      TEXT,       -- encrypted JSON array
    emotional_state_enc TEXT,       -- encrypted
    progress_notes_enc  TEXT,       -- encrypted
    therapeutic_goals_enc TEXT,     -- encrypted JSON array
    patient_vocabulary_enc TEXT,    -- encrypted JSON
    risk_factors_enc    TEXT,       -- encrypted JSON array
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (patient_id, session_number)
);

CREATE INDEX idx_contexts_patient ON session_contexts(patient_id, session_number);

-- ============================================================
-- SESSION REPORTS
-- ============================================================
CREATE TYPE report_status AS ENUM ('PENDING', 'GENERATING', 'COMPLETED', 'FAILED');

CREATE TABLE session_reports (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES sessions(id) ON DELETE RESTRICT,
    patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    status              report_status NOT NULL DEFAULT 'PENDING',
    generated_at        TIMESTAMPTZ,
    s3_key              VARCHAR(500),
    download_count      INT NOT NULL DEFAULT 0,
    report_data_enc     TEXT,   -- encrypted JSON with Claude output
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reports_patient ON session_reports(patient_id, created_at DESC);
CREATE INDEX idx_reports_session ON session_reports(session_id);

-- ============================================================
-- REFRESH TOKENS
-- ============================================================
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    device_info     VARCHAR(500),
    ip_address      INET,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_patient ON refresh_tokens(patient_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);

-- ============================================================
-- AUDIT LOGS
-- ============================================================
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,
    actor_id        UUID,
    patient_id      UUID,
    resource_type   VARCHAR(50),
    resource_id     UUID,
    ip_address      INET,
    user_agent      TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_patient ON audit_logs(patient_id, created_at DESC);
CREATE INDEX idx_audit_logs_event ON audit_logs(event_type, created_at DESC);

-- ============================================================
-- UPDATED_AT triggers
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_patients_updated_at
    BEFORE UPDATE ON patients
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_packs_updated_at
    BEFORE UPDATE ON packs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_sessions_updated_at
    BEFORE UPDATE ON sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_contexts_updated_at
    BEFORE UPDATE ON session_contexts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_reports_updated_at
    BEFORE UPDATE ON session_reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_pack_types_updated_at
    BEFORE UPDATE ON pack_types
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
