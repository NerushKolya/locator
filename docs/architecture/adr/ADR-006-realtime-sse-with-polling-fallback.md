# ADR-006: Realtime transport — SSE with a polling fallback

- Status: Accepted
- Date: 2026-11-15

## Context

A viewer with the map open should see markers move within seconds (US-030). Data flows one way: server to viewer. Commands from the app are ordinary REST calls. The sharing phone must not hold a persistent connection, because a radio that never sleeps drains the battery. The transport has to pass through proxies and tunnels used in development and work with the Android HTTP client.

## Options

1. **Polling.** Trivial and works everywhere. Delay up to the polling interval, wasted requests.
2. **Server-Sent Events (SSE).** Plain HTTP, one direction, built-in reconnection with `Last-Event-ID`, works over HTTP/2. Server to client only.
3. **WebSocket.** Two-way, low latency. Own protocol on top, ping/pong, more proxy trouble; two-way is not needed.
4. **Push messages for every update.** No connection. Quotas, throttling and delays; wrong tool.

## Decision

SSE for the map screen, served by `delivery`, opened only while the map is visible. The app first loads a REST snapshot, then applies stream events. If the stream cannot be opened or breaks, the app polls the snapshot every 30 seconds and keeps retrying the stream. The first walking-skeleton version uses polling only. The protocol is specified in [realtime-protocol.md](../../api/realtime-protocol.md).

## Consequences

- The simplest transport that meets the latency goal; the fallback needs no extra server code.
- The edge proxy must not buffer the stream route; keep-alive comments are sent every 20 seconds.
- The stream is never kept open in the background: background updates are the job of push.
- WebSocket is introduced only if a two-way feature (chat) is decided, through a new ADR.
