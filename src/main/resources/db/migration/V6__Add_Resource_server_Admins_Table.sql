CREATE TABLE resource_server_admins (
    id uuid DEFAULT public.gen_random_uuid() NOT NULL,
    admin_id uuid NOT NULL,
    resource_server_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE resource_server_admins OWNER TO ${flyway:user};

ALTER TABLE ONLY resource_server_admins
    ADD CONSTRAINT resource_server_admins_pkey PRIMARY KEY (id);

ALTER TABLE ONLY resource_server_admins
    ADD CONSTRAINT r_s_admins_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id);

ALTER TABLE ONLY resource_server_admins
    ADD CONSTRAINT r_s_admins_resource_server_id_fkey FOREIGN KEY (resource_server_id) REFERENCES resource_server(id);

ALTER TABLE ONLY resource_server_admins
    ADD CONSTRAINT unique_server_to_admin UNIQUE (owner_id, resource_server_id);

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE resource_server_admins TO ${authUser};
