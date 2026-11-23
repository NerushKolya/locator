# Project database image (ADR-020): PostgreSQL 18 + PostGIS 3 from the PostgreSQL apt repository.
# The base image is pinned by digest of the multi-architecture index (linux/amd64 and linux/arm64).
FROM postgres:18-bookworm@sha256:194e55feb2597b1465dbee81cc9fd8d204566cd2eb42c8d22fd6c931a38781b1

# The PostgreSQL apt repository is already configured in the base image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-18-postgis-3 \
    && rm -rf /var/lib/apt/lists/*

# Runs once, on an empty data directory only.
COPY --chmod=755 initdb/ /docker-entrypoint-initdb.d/
