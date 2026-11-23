# Delivery service image (ADR-002): Go 1.26, static binary on a minimal non-root base.
# Images are pinned by the digest of their multi-architecture index (linux/amd64 and linux/arm64).
ARG GO_IMAGE=golang:1.26.11-trixie@sha256:cff05dd6b81c901f2e8249eb6f91c69a60451d0f12c26bfef08e476a85b32d4d
ARG RUNTIME_IMAGE=gcr.io/distroless/static-debian13:nonroot@sha256:8ce6dd87e9823c5b48dcfb2c8604714ccb351ce59607327bcc4957448da279b5

# ---- source: module files first, so the dependency layer is cached until go.mod/go.sum change ----
FROM --platform=$BUILDPLATFORM ${GO_IMAGE} AS source
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .

# ---- test: runs go test through gotestsum and writes a JUnit report to /reports ----
# Compose service delivery-test (profile "test") mounts /reports; GO_TEST_ARGS selects the tests,
# for example "-short ./..." or "-run AuthzMatrix ./...".
FROM source AS test
# gotestsum 1.13.0 (ADR-026), built from source through the Go module proxy and checksum database.
RUN go install gotest.tools/gotestsum@v1.13.0 \
    && mkdir -p /reports
ENV GO_TEST_ARGS=./...
CMD ["sh", "-c", "exec gotestsum --junitfile /reports/junit.xml --format testname -- ${GO_TEST_ARGS}"]

# ---- build: static binary for the target architecture ----
FROM source AS build
ARG TARGETOS
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/delivery ./cmd/delivery

# ---- runtime: no shell; the Compose health check calls "/delivery -healthcheck" ----
FROM ${RUNTIME_IMAGE} AS runtime
COPY --from=build /out/delivery /delivery
USER nonroot:nonroot
ENV DELIVERY_HTTP_ADDR=:8081
EXPOSE 8081
ENTRYPOINT ["/delivery"]
