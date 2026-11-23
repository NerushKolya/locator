# Core service image (ADR-010): FrankenPHP in classic mode on PHP 8.5, Debian variant.
# Base image pinned by digest of the multi-architecture index (linux/amd64 and linux/arm64): dunglas/frankenphp 1.14.0.
ARG FRANKENPHP_IMAGE=dunglas/frankenphp:1.14.0-php8.5-bookworm@sha256:ef775349d856cf5146aa5e569f62e50ebefd294546b57c77951f0d750959d194

# ---- base: PHP extensions and Composer; used to scaffold the application and as the parent of every target ----
FROM ${FRANKENPHP_IMAGE} AS base

RUN install-php-extensions pdo_pgsql intl opcache zip

# Composer 2.11.2, pinned by digest.
COPY --from=composer:2.11.2@sha256:50ea4d33fdcde1adb0f045547295faa1548b33872e5f789dae5b02ce2fb5de93 /usr/bin/composer /usr/local/bin/composer

ENV SERVER_NAME=:8080
WORKDIR /app

# ---- deps-dev / deps-prod: Composer dependencies in their own layers (cached until composer.lock changes) ----
FROM base AS deps-dev
COPY composer.json composer.lock symfony.lock ./
RUN composer install --no-interaction --no-scripts --no-autoloader --prefer-dist

FROM base AS deps-prod
COPY composer.json composer.lock symfony.lock ./
RUN composer install --no-interaction --no-scripts --no-autoloader --prefer-dist --no-dev

# ---- test: dev dependencies; applies migrations to the test database, then runs PHPUnit ----
# Compose service core-test (profile "test") supplies the database settings and mounts /reports and /contract.
FROM base AS test
COPY --from=deps-dev /app/vendor ./vendor
COPY composer.json composer.lock symfony.lock phpunit.dist.xml .env .env.test ./
COPY bin ./bin
COPY config ./config
COPY migrations ./migrations
COPY public ./public
COPY src ./src
COPY tests ./tests
RUN composer dump-autoload --no-interaction \
    && mkdir -p /reports var
ENV APP_ENV=test
# PHPUNIT_ARGS lets scripts select a test suite or group, for example "--testsuite unit".
CMD ["sh", "-c", "php bin/console doctrine:migrations:migrate --no-interaction --allow-no-migration && exec php vendor/bin/phpunit --log-junit /reports/junit.xml ${PHPUNIT_ARGS}"]

# ---- runtime: no dev dependencies, no .env file; every setting comes from the environment ----
# The cache warm-up below needs placeholder values only to compile the container; the real values come from the environment at start.
FROM base AS runtime
COPY --from=deps-prod /app/vendor ./vendor
COPY composer.json composer.lock symfony.lock ./
COPY bin ./bin
COPY config ./config
COPY migrations ./migrations
COPY public ./public
COPY src ./src
COPY Caddyfile /etc/frankenphp/Caddyfile
COPY php.ini /usr/local/etc/php/conf.d/zz-locator.ini
ENV APP_ENV=prod \
    APP_DEBUG=0 \
    APP_RUNTIME_OPTIONS='{"disable_dotenv":true}' \
    APP_SHARE_DIR=var/share \
    DEFAULT_URI=http://localhost
RUN composer dump-autoload --no-interaction --classmap-authoritative --no-dev \
    && env APP_SECRET=build-time-only SERVICE_NAME=core LOG_LEVEL=info DB_HOST=build DB_PORT=5432 DB_NAME=build CORE_APP_PASSWORD=build CORE_MIGRATOR_PASSWORD=build LOCATOR_PUBLIC_BASE_URL=http://localhost OPERATOR_MODE=off \
       php bin/console cache:warmup --no-interaction \
    && mkdir -p var/share
# Run as an unprivileged user: port 8080 needs no capability, and only var/ (cache, logs) and the Caddy state directories are writable.
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin locator \
    && mkdir -p /data/caddy /config/caddy \
    && chown -R locator:locator /app/var /data/caddy /config/caddy
USER locator
EXPOSE 8080
