CREATE TABLE IF NOT EXISTS organization_users (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL UNIQUE,
    role VARCHAR NOT NULL CHECK (role IN ('admin','member')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
