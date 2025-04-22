CREATE TABLE IF NOT EXISTS credit_requests (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES organization_users(id),
    amount	DECIMAL,
    status VARCHAR NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITHOUT TIME ZONE
);

