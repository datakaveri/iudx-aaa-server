-- Adding apd_policies table

CREATE TABLE apd_policies (
    id uuid DEFAULT public.gen_random_uuid() NOT NULL,
    apd_id uuid NOT NULL,
    user_class character varying NOT NULL,
    item_id uuid NOT NULL,
    item_type item_enum NOT NULL,
    owner_id uuid NOT NULL,
    status policy_status_enum NOT NULL,
    expiry_time timestamp without time zone NOT NULL,
    constraints jsonb NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE apd_policies OWNER TO ${flyway:user};

ALTER TABLE ONLY apd_policies
    ADD CONSTRAINT apd_policies_pkey PRIMARY KEY (id);

ALTER TABLE ONLY apd_policies
    ADD CONSTRAINT apd_policies_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id);

ALTER TABLE ONLY apd_policies
    ADD CONSTRAINT apd_policies_apd_id_fkey FOREIGN KEY (apd_id) REFERENCES apds(id);

GRANT SELECT,INSERT,UPDATE ON TABLE apd_policies TO ${authUser};
