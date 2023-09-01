-- removing PENDING status from apd_status_enum
-- first we need to delete any APDs in PENDING state

DELETE FROM apds WHERE status = 'PENDING';

CREATE TYPE new_apd_status_enum AS ENUM (
    'ACTIVE',
    'INACTIVE'
);

ALTER TABLE apds ALTER COLUMN status TYPE new_apd_status_enum USING status::text::new_apd_status_enum;

DROP TYPE apd_status_enum;

ALTER TYPE new_apd_status_enum RENAME TO apd_status_enum;
