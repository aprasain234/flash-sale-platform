# Flash Sale — High-Concurrency Ticket Booking System

A portfolio project demonstrating concurrency-safe seat reservation under load,
built as Spring Boot microservices on Kafka, Redis, and Postgres, deployed to EKS.

## What this scaffold is (and isn't)

This is a **compiling, structurally complete skeleton** — real Kafka event
contracts, a real atomic Redis reservation script, a real idempotent order
write path, real Dockerfiles and k8s manifests. It is **not** a finished
product: there's no auth, no seat-map UI, no real payment gateway, and the
load test is not included. Those are next steps, not oversights — see TODOs
below.

## Architecture

```
                     ┌──────────────┐
   client ────────▶  │  api-gateway │  (rate limiting, routing)
                     └──────┬───────┘
                            │
        ┌───────────────────┼────────────────────┐
        ▼                   ▼                     ▼
┌───────────────┐   ┌───────────────┐   ┌──────────────────┐
│ inventory-svc  │   │  order-svc    │   │  payment-svc      │
│ Redis (holds,  │   │ Postgres      │   │ mock payment      │
│ TTL, atomic    │   │ (source of    │   │ ~90% success rate │
│ Lua reserve)   │   │  truth)       │   │                    │
└───────┬────────┘   └───────┬───────┘   └─────────┬─────────┘
        │                    │                      │
        │   seat-reserved    │    order-created      │
        └───────────────────▶│◀──────────────────────┘
                             │  payment-completed
                             │◀──────────────┐
                             ▼               │
                    ┌─────────────────┐      │
                    │notification-svc │◀─────┘
                    │  (mock, logs)   │
                    └─────────────────┘
```

## Flow

1. Client calls `POST /api/inventory/reserve` (via api-gateway).
2. inventory-service runs an atomic Lua script against Redis: checks
   remaining count, decrements it, sets a per-seat hold key with a 300s TTL.
   No read-then-write race — it's one script execution.
3. On success, inventory-service publishes `SeatReservedEvent` to Kafka.
4. order-service consumes it, writes a `PENDING` order row to Postgres.
   The DB unique constraint on `idempotency_key` (= reservationId) is the
   real duplicate guard — Kafka is at-least-once, so duplicates *will*
   arrive, and the in-memory check alone would not survive a restart or
   multiple replicas.
5. order-service publishes `OrderCreatedEvent`.
6. payment-service consumes it, simulates authorization, publishes
   `PaymentCompletedEvent`.
7. order-service consumes that and marks the order `CONFIRMED` or `FAILED`.
8. notification-service consumes the same event and logs a mock notification.

**On payment failure:** the Redis hold is *not* explicitly released by any
service in this scaffold yet — see TODOs. Either wire `POST /api/inventory/release`
into a payment-completed listener in inventory-service, or rely on the TTL
alone and accept seats stay "soft-held but unusable" until it expires. Decide
this explicitly; don't let it be an accident.

## Local development

```bash
docker compose up -d          # Kafka (KRaft), Redis, Postgres
mvn clean install             # build all modules, run from repo root

# in separate terminals:
cd inventory-service    && mvn spring-boot:run
cd order-service        && mvn spring-boot:run
cd payment-service      && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd api-gateway           && mvn spring-boot:run
```

Then:
```bash
curl -X POST localhost:8080/api/inventory/reserve \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"evt-1","seatId":"A1","userId":"user-42"}'
```

You'll need to seed `seats:available:evt-1` in Redis first:
```bash
redis-cli SET seats:available:evt-1 100
```

## Deploying to EKS

1. Build and push images to ECR:
   ```bash
   mvn clean package -DskipTests
   for svc in api-gateway inventory-service order-service payment-service notification-service; do
     docker build -t <account>.dkr.ecr.<region>.amazonaws.com/$svc:latest ./$svc
     docker push <account>.dkr.ecr.<region>.amazonaws.com/$svc:latest
   done
   ```
2. Point real infra: swap the ConfigMap values in `k8s/configmap.yaml` for
   ElastiCache (Redis), MSK or your Kafka deployment, and RDS (Postgres).
   Running Kafka/Redis/Postgres as plain pods on EKS is fine for a demo but
   is not how this should run for real — say that plainly if asked.
3. `kubectl apply -f k8s/namespace.yaml`
4. `kubectl apply -f k8s/configmap.yaml`
5. `kubectl apply -f k8s/*.yaml` (deployments + services)
6. `kubectl apply -f k8s/api-gateway-ingress.yaml` (requires AWS Load Balancer
   Controller installed on the cluster)

## TODOs (the honest list — these are what make the "high-concurrency" claim credible)

- [ ] **Load test.** Nothing here proves the concurrency story without a
      k6/Gatling/JMeter run showing correct behavior (no overselling) under
      concurrent hits on the same seat and same event's inventory counter.
- [ ] **Wire the failure-path release.** inventory-service needs to consume
      `PaymentCompletedEvent` (success=false) and call its own release logic,
      or you're relying entirely on TTL expiry for failed payments.
- [ ] **Redis keyspace notifications** for TTL-expired holds, so
      inventory-service can reactively track state rather than only updating
      the counter on explicit reserve/release calls.
- [ ] **Outbox pattern** (or at least document the gap) — right now Kafka
      publishes happen after the Redis/Postgres write but aren't in the same
      transaction as either; a crash between them is a real gap.
- [ ] **Auth** — there's no authn/authz on any endpoint yet.
- [ ] **Idempotency store for payment-service** — currently an in-memory
      `Set`, explicitly noted in code as not production-safe.
- [ ] Redis/Postgres/Kafka on EKS as StatefulSets or managed services —
      picking one is a real architectural decision, not filled in here.
