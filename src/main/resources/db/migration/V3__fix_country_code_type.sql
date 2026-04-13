-- Fix country_code column type from CHAR(2) to VARCHAR(2) to match Hibernate mapping
ALTER TABLE patients ALTER COLUMN country_code TYPE VARCHAR(2);
