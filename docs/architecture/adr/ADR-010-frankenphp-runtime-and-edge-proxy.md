# ADR-010: FrankenPHP as PHP runtime and edge proxy

- Status: Accepted
- Date: 2026-11-15

## Context

The core needs a PHP runtime, and the system needs an entry point that terminates TLS with automatically renewed certificates, serves static pages and routes the stream path to `delivery` and the back-office host to `backoffice`. Fewer containers mean less memory on a free host and less to operate.

## Options

1. **FrankenPHP** — a PHP application server built on Caddy: runtime and web server in one container, automatic HTTPS, HTTP/2 and HTTP/3, optional worker mode.
2. **nginx + PHP-FPM + a certificate client.** The classic, well-known setup. Two or three containers and manual certificate handling.
3. **Separate Caddy or Traefik in front of PHP-FPM.** Clear separation of proxy and runtime. One more container.

## Decision

FrankenPHP runs the core in **classic mode** and its embedded Caddy is the edge proxy for the whole system: certificates from Let's Encrypt, routing of `/v1/stream` to `delivery` with response buffering disabled, routing of the back-office host name to `backoffice`, and blocking of `/internal/**` and `/admin/**` from outside. Worker mode is an optimization for later; it requires care with state kept between requests.

## Consequences

- One container fewer and no manual certificate work.
- The core container is also the single entry point: its restart interrupts streams briefly (clients reconnect by design).
- Proxy configuration lives in the repository next to the Compose files and is tested in the end-to-end suite (including the blocked internal paths).
- If the edge must be scaled or isolated later, a standalone Caddy takes the same configuration.
