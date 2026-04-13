-- Convert INET columns to VARCHAR(45) to avoid Hibernate type mismatch
ALTER TABLE patient_consent_history
    ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;

ALTER TABLE refresh_tokens
    ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;

ALTER TABLE audit_logs
    ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;
