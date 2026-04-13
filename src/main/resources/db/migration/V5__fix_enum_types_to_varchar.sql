-- Convert all custom PostgreSQL enum types to VARCHAR for Hibernate compatibility
-- Must drop defaults first since they reference the enum types

-- packs.status
ALTER TABLE packs ALTER COLUMN status DROP DEFAULT;
ALTER TABLE packs ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
ALTER TABLE packs ALTER COLUMN status SET DEFAULT 'PENDING_PAYMENT';

-- payment_events.event_type
ALTER TABLE payment_events ALTER COLUMN event_type TYPE VARCHAR(50) USING event_type::text;

-- sessions.status
ALTER TABLE sessions ALTER COLUMN status DROP DEFAULT;
ALTER TABLE sessions ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
ALTER TABLE sessions ALTER COLUMN status SET DEFAULT 'SCHEDULED';

-- sessions.modality
ALTER TABLE sessions ALTER COLUMN modality DROP DEFAULT;
ALTER TABLE sessions ALTER COLUMN modality TYPE VARCHAR(50) USING modality::text;

-- session_messages.role
ALTER TABLE session_messages ALTER COLUMN role TYPE VARCHAR(50) USING role::text;

-- session_messages.content_type
ALTER TABLE session_messages ALTER COLUMN content_type TYPE VARCHAR(50) USING content_type::text;

-- session_reports.status
ALTER TABLE session_reports ALTER COLUMN status DROP DEFAULT;
ALTER TABLE session_reports ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
ALTER TABLE session_reports ALTER COLUMN status SET DEFAULT 'PENDING';

-- Drop now-unused custom types
DROP TYPE IF EXISTS pack_status CASCADE;
DROP TYPE IF EXISTS payment_event_type CASCADE;
DROP TYPE IF EXISTS session_status CASCADE;
DROP TYPE IF EXISTS session_modality CASCADE;
DROP TYPE IF EXISTS message_role CASCADE;
DROP TYPE IF EXISTS message_content_type CASCADE;
DROP TYPE IF EXISTS report_status CASCADE;
