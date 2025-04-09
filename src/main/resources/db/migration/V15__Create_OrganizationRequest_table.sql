CREATE TABLE IF NOT EXISTS organization_create_requests (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    requested_by UUID NOT NULL,
    name VARCHAR NOT NULL UNIQUE,
    description TEXT,
    document_path TEXT,
    status VARCHAR NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
