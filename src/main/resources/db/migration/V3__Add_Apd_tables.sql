-- Adding APD status enum and APD details table

CREATE TYPE apd_status_enum AS ENUM (
    'PENDING',
    'ACTIVE',
    'INACTIVE'
);

ALTER TYPE apd_status_enum OWNER TO ${flyway:user};

CREATE TABLE apds (
    id uuid DEFAULT public.gen_random_uuid() NOT NULL,
    name character varying NOT NULL,
    url character varying NOT NULL,
    owner_id uuid NOT NULL,
    status apd_status_enum NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE apds OWNER TO ${flyway:user};

ALTER TABLE ONLY apds
    ADD CONSTRAINT apds_pkey PRIMARY KEY (id);

ALTER TABLE ONLY apds
    ADD CONSTRAINT unique_apd_url UNIQUE (url);

ALTER TABLE ONLY apds
    ADD CONSTRAINT apds_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

GRANT SELECT,INSERT,UPDATE ON TABLE apds TO ${authUser};
