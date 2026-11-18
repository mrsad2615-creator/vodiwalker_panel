-- Isolated upstream-style GoTrue role and schema. The password comes from the test container environment.
\getenv test_password POSTGRES_PASSWORD
CREATE ROLE supabase_admin NOLOGIN;
CREATE ROLE supabase_auth_admin NOINHERIT CREATEROLE LOGIN NOREPLICATION PASSWORD :'test_password';
CREATE SCHEMA IF NOT EXISTS auth AUTHORIZATION supabase_auth_admin;
GRANT CREATE ON DATABASE postgres TO supabase_auth_admin;
ALTER ROLE supabase_auth_admin SET search_path TO auth;
