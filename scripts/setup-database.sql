-- Creates the LedgerMate database user and database in a local PostgreSQL server.
-- Run it ONCE as the PostgreSQL superuser, passing your chosen password on the command line
-- (the password is never stored in this file):
--
--   psql -U postgres -v db_password='<your-password>' -f scripts/setup-database.sql
--
-- Use the same password for DB_PASSWORD in your .env file. The tables themselves are created
-- by the application (Flyway migrations) the first time the backend starts.
\set ON_ERROR_STOP on

CREATE ROLE ledgermate LOGIN PASSWORD :'db_password';
CREATE DATABASE ledgermate OWNER ledgermate ENCODING 'UTF8';
