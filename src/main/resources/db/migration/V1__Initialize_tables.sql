SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

-- for gen_random_uuid
CREATE EXTENSION pgcrypto WITH SCHEMA ${flyway:defaultSchema};

ALTER SCHEMA ${flyway:defaultSchema} OWNER TO ${flyway:user};

--
-- Enum creation
--

CREATE TYPE acc_reqs_status_enum AS ENUM (
    'REJECTED',
    'PENDING',
    'APPROVED'
);

ALTER TYPE acc_reqs_status_enum OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TYPE item_enum AS ENUM (
    'RESOURCE_SERVER',
    'RESOURCE_GROUP',
    'RESOURCE'
);

ALTER TYPE item_enum OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TYPE policy_status_enum AS ENUM (
    'ACTIVE',
    'DELETED'
);

ALTER TYPE policy_status_enum OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TYPE role_enum AS ENUM (
    'CONSUMER',
    'DELEGATE',
    'PROVIDER',
    'ADMIN'
);

ALTER TYPE role_enum OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TYPE role_status_enum AS ENUM (
    'REJECTED',
    'PENDING',
    'APPROVED'
);

ALTER TYPE role_status_enum OWNER TO ${flyway:user};

--------------------------------------------------

--
-- Table creation
--

SET default_tablespace = '';

SET default_table_access_method = heap;

CREATE TABLE access_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    item_id uuid NOT NULL,
    item_type item_enum NOT NULL,
    owner_id uuid NOT NULL,
    status acc_reqs_status_enum NOT NULL,
    expiry_duration interval NOT NULL,
    constraints jsonb NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE access_requests OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE approved_access_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_id uuid NOT NULL,
    policy_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE approved_access_requests OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE delegations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_id uuid NOT NULL,
    user_id uuid NOT NULL,
    resource_server_id uuid NOT NULL,
    status policy_status_enum NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE delegations OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE organizations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying NOT NULL,
    url character varying NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE organizations OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE policies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    item_id uuid NOT NULL,
    item_type item_enum NOT NULL,
    owner_id uuid NOT NULL,
    status policy_status_enum NOT NULL,
    expiry_time timestamp without time zone NOT NULL,
    constraints jsonb NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE policies OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE resource (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    cat_id character varying NOT NULL,
    provider_id uuid NOT NULL,
    resource_group_id uuid,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    resource_server_id uuid NOT NULL
);

ALTER TABLE resource OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE resource_group (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    cat_id character varying NOT NULL,
    provider_id uuid NOT NULL,
    resource_server_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE resource_group OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE resource_server (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying NOT NULL,
    owner_id uuid NOT NULL,
    url character varying NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE resource_server OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    role role_enum NOT NULL,
    status role_status_enum NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE roles OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE user_clients (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    client_id uuid NOT NULL,
    client_secret character varying NOT NULL,
    client_name character varying NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE user_clients OWNER TO ${flyway:user};

--------------------------------------------------

CREATE TABLE users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    phone character varying(10) NOT NULL,
    organization_id uuid,
    email_hash character varying NOT NULL,
    keycloak_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE users OWNER TO ${flyway:user};

--------------------------------------------------

--
-- Primary Key, Unique and Foreign key constraints
--

ALTER TABLE ONLY access_requests
    ADD CONSTRAINT access_requests_pkey PRIMARY KEY (id);

ALTER TABLE ONLY approved_access_requests
    ADD CONSTRAINT approved_access_requests_pkey PRIMARY KEY (id);

ALTER TABLE ONLY organizations
    ADD CONSTRAINT idx_organizations_url UNIQUE (url);

ALTER TABLE ONLY organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY policies
    ADD CONSTRAINT policies_pkey PRIMARY KEY (id);

ALTER TABLE ONLY resource_group
    ADD CONSTRAINT resource_groups_pkey PRIMARY KEY (id);

ALTER TABLE ONLY resource_server
    ADD CONSTRAINT resource_servers_pkey PRIMARY KEY (id);

ALTER TABLE ONLY resource
    ADD CONSTRAINT resources_pkey PRIMARY KEY (id);

ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY resource
    ADD CONSTRAINT unique_res UNIQUE (cat_id, provider_id, resource_group_id);

ALTER TABLE ONLY resource_group
    ADD CONSTRAINT unique_rsg UNIQUE (cat_id, provider_id, resource_server_id);

ALTER TABLE ONLY user_clients
    ADD CONSTRAINT user_clients_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY access_requests
    ADD CONSTRAINT access_requests_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY access_requests
    ADD CONSTRAINT access_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY approved_access_requests
    ADD CONSTRAINT approved_access_requests_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES policies(id) ON DELETE CASCADE;

ALTER TABLE ONLY approved_access_requests
    ADD CONSTRAINT approved_access_requests_request_id_fkey FOREIGN KEY (request_id) REFERENCES access_requests(id) ON DELETE CASCADE;

ALTER TABLE ONLY delegations
    ADD CONSTRAINT delegations_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY delegations
    ADD CONSTRAINT delegations_resource_server_id_fkey FOREIGN KEY (resource_server_id) REFERENCES resource_server(id) ON DELETE CASCADE;

ALTER TABLE ONLY delegations
    ADD CONSTRAINT delegations_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY policies
    ADD CONSTRAINT policies_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id);

ALTER TABLE ONLY policies
    ADD CONSTRAINT policies_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY resource_group
    ADD CONSTRAINT resource_groups_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES users(id);

ALTER TABLE ONLY resource_group
    ADD CONSTRAINT resource_groups_resource_server_id_fkey FOREIGN KEY (resource_server_id) REFERENCES resource_server(id);

ALTER TABLE ONLY resource
    ADD CONSTRAINT resource_resource_server_id_fkey FOREIGN KEY (resource_server_id) REFERENCES resource_server(id);

ALTER TABLE ONLY resource_server
    ADD CONSTRAINT resource_servers_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id);

ALTER TABLE ONLY resource
    ADD CONSTRAINT resources_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES users(id);

ALTER TABLE ONLY resource
    ADD CONSTRAINT resources_resource_group_fkey FOREIGN KEY (resource_group_id) REFERENCES resource_group(id);

ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY user_clients
    ADD CONSTRAINT user_clients_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY users
    ADD CONSTRAINT users_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES organizations(id);

--
-- Access grants
--

GRANT USAGE ON SCHEMA test TO ${authUser};

GRANT SELECT,INSERT,UPDATE ON TABLE access_requests TO ${authUser};
GRANT SELECT,INSERT,UPDATE ON TABLE approved_access_requests TO ${authUser};
GRANT SELECT,INSERT,UPDATE ON TABLE delegations TO ${authUser};
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE organizations TO ${authUser};
GRANT SELECT,INSERT,UPDATE ON TABLE policies TO ${authUser};
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE resource TO ${authUser};
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE resource_group TO ${authUser};
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE resource_server TO ${authUser};
GRANT SELECT,INSERT,UPDATE ON TABLE roles TO ${authUser};
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE user_clients TO ${authUser};
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE users TO ${authUser};
