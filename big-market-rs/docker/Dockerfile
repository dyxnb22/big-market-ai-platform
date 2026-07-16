# syntax=docker/dockerfile:1
ARG BIN=bm-app
FROM rust:1.85-bookworm AS builder
WORKDIR /src
COPY . .
ARG BIN
RUN cargo build --release -p ${BIN}

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
  && rm -rf /var/lib/apt/lists/*
ARG BIN
COPY --from=builder /src/target/release/${BIN} /usr/local/bin/app
EXPOSE 8080 8083 8085
CMD ["/usr/local/bin/app"]
