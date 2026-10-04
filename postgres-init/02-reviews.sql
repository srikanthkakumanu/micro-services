-- Additive provisioning: safe for fresh instances and existing development volumes.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'theuser') THEN
        CREATE ROLE theuser LOGIN PASSWORD 'theuser';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'reviewsadmin') THEN
        CREATE ROLE reviewsadmin LOGIN PASSWORD 'reviewsadmin';
    END IF;
END $$;
SELECT 'CREATE DATABASE reviewsdb'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'reviewsdb') \gexec
GRANT ALL PRIVILEGES ON DATABASE reviewsdb TO reviewsadmin;
GRANT CONNECT ON DATABASE reviewsdb TO theuser;
\connect reviewsdb
GRANT USAGE, CREATE ON SCHEMA public TO reviewsadmin;
GRANT USAGE ON SCHEMA public TO theuser;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO theuser;
ALTER DEFAULT PRIVILEGES FOR ROLE reviewsadmin IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO theuser;
