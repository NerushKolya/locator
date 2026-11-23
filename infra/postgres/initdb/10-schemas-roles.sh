#!/usr/bin/env bash
# Database baseline: PostGIS, schemas, roles and default privileges.
# Runs once, on an empty data directory, as the superuser of the image.
set -euo pipefail

for name in CORE_MIGRATOR_PASSWORD CORE_APP_PASSWORD DELIVERY_APP_PASSWORD; do
    if [ -z "${!name:-}" ]; then
        echo "init: $name is not set" >&2
        exit 1
    fi
done

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v migrator_password="$CORE_MIGRATOR_PASSWORD" \
    -v core_app_password="$CORE_APP_PASSWORD" \
    -v delivery_app_password="$DELIVERY_APP_PASSWORD" <<'SQL'
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE ROLE core_migrator LOGIN PASSWORD :'migrator_password';
CREATE ROLE core_app      LOGIN PASSWORD :'core_app_password';
CREATE ROLE delivery_app  LOGIN PASSWORD :'delivery_app_password';

CREATE SCHEMA core AUTHORIZATION core_migrator;
GRANT USAGE ON SCHEMA core TO core_app;

CREATE SCHEMA delivery AUTHORIZATION delivery_app;

ALTER ROLE core_migrator SET search_path = core, public;
ALTER ROLE core_app      SET search_path = core, public;
ALTER ROLE delivery_app  SET search_path = delivery, public;

-- Tables and sequences that core_migrator creates later are usable by core_app.
ALTER DEFAULT PRIVILEGES FOR ROLE core_migrator IN SCHEMA core
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO core_app;
ALTER DEFAULT PRIVILEGES FOR ROLE core_migrator IN SCHEMA core
    GRANT USAGE, SELECT ON SEQUENCES TO core_app;
SQL
