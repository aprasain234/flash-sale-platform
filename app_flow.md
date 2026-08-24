# Flash Sale — High-Concurrency Ticket Booking System

**Prepared by:** Engineering
**Audience:** Product & Business stakeholders
**Status:** Working prototype, core flow verified end-to-end. Not production-hardened yet — see [Maturity & Known Gaps](#maturity--known-gaps) for an unvarnished list.

---

## 1. The problem this solves

Any time we sell a limited number of things at a fixed moment — concert tickets, flash-sale inventory, limited-edition drops, appointment slots — we run into the same failure mode if the system isn't built for it: **overselling**. Two customers both see "available," both click buy, and only one seat actually exists. That's a refund, an angry customer, and a support ticket, multiplied by however many people hit "buy" in the same second.

The naive fix — "just check the database before selling" — doesn't hold up under real concurrency. If a thousand people hit the same seat in the same 200-millisecond window, a plain check-then-write pattern will let more than one of them through. This system exists to prove out, concretely, an architecture that doesn't have that failure mode — not as a theoretical claim, but as something we can load-test and demonstrate.

**This is currently a prototype/portfolio project**, not a production system serving real traffic. It's built to demonstrate the pattern correctly, end to end, so the approach can be evaluated, load-tested, and — if it holds up — adapted for a real product surface.

---

## 2. What it does, in one paragraph

A user requests a specific seat. The system checks availability and places a temporary hold atomically — no two users can claim the same seat, and no request can oversell total inventory, even under heavy concurrent load. That hold is recorded, an order is created, a payment is simulated, and depending on the outcome the order is confirmed or the seat is released. Every step happens through independent services reacting to events, not a single monolithic transaction — which is what lets each piece scale, fail, and be understood independently.

---

## 3. Why this architecture, not something simpler

A reasonable question from a business stakeholder: **"couldn't we just use one application and one database?"** Yes, for low traffic. This architecture exists specifically because flash-sale traffic is bursty and adversarial by nature — everyone hits the system in the same few seconds, which is exactly the condition under which simpler designs fail.

Three deliberate decisions worth knowing about:

| Decision | Why |
|---|---|
| **Redis for the "is it available" check, not the database** | Redis can do an atomic check-and-claim in microseconds. A relational database can too, but under thousands of concurrent requests for the same row, it becomes a lock-contention bottleneck. Redis is the right tool for "fast, temporary, high-contention" state. |
| **A temporary hold with an auto-expiring timer (TTL), not a manual cleanup job** | If a user reserves a seat and abandons checkout, we don't want that seat gone forever. Instead of running a background job to find and release stale holds, we let Redis expire the hold automatically after 5 minutes. Simpler, and there's no cleanup process that can fail or fall behind. |
| **Services communicate through events (Kafka), not direct calls** | If the payment step directly called the order step, which directly called the notification step, a slowdown anywhere cascades into a slowdown everywhere — and an outage in notifications could block people from buying tickets, which makes no sense. Event-driven design means each service does its job and moves on; a hiccup in notification-service doesn't stop anyone from checking out. |

The tradeoff, stated plainly: this is **more moving parts** than a single application, and that has a real cost — more services to deploy, monitor, and debug, and correctness has to be reasoned about across asynchronous boundaries instead of within one transaction. We took on that complexity deliberately, because it's the same complexity a real flash-sale product would face, and the point of this project is to prove the pattern works, not to take a shortcut around the hard part.

---

## 4. How a request actually flows

```
                     ┌──────────────┐
   Customer ───────▶ │  api-gateway │  (single entry point, rate-limited)
                     └──────┬───────┘
                            │ POST /api/inventory/reserve
                            ▼
                  ┌────────────────────┐
                  │  inventory-service  │
                  │  Redis: atomic      │
                  │  check + claim +    │
                  │  5-min hold         │
                  └─────────┬───────────┘
                            │ event: seat-reserved
                            ▼
                  ┌────────────────────┐
                  │   order-service     │
                  │   Postgres: durable │
                  │   record created    │
                  └─────────┬───────────┘
                            │ event: order-created
                            ▼
                  ┌────────────────────┐
                  │  payment-service    │
                  │  authorizes payment │
                  └─────────┬───────────┘
                            │ event: payment-completed
                 ┌──────────┴───────────┐
                 ▼                      ▼
      ┌────────────────────┐  ┌─────────────────────┐
      │   order-service      │  │ notification-service │
      │   marks CONFIRMED/    │  │ notifies customer     │
      │   FAILED               │  │ (mocked for now)      │
      └────────────────────┘  └─────────────────────┘
```

**The only step a customer waits on is the first one** — the reserve call. Everything after that happens in the background, independently, without the customer's browser sitting on an open connection waiting for payment and confirmation to finish. This matters for perceived performance: the customer gets an immediate "your seat is held" response, not a multi-second wait for the entire pipeline.

---

## 5. What each service is responsible for

### `api-gateway` — the front door
The single point of entry for customer traffic. Applies rate limiting so a traffic spike can't overwhelm the services behind it — this is the layer that protects everything else during the exact moment a flash sale traffic surge hits.

### `inventory-service` — the bouncer
Owns one job: answer "can this specific person have this specific seat, right now" — correctly, even if ten thousand people ask at once. This is where the overselling-prevention guarantee actually lives, enforced through an atomic operation in Redis. Nothing else in the system can claim a seat without going through here first.

### `order-service` — the record of truth
The only service with a permanent database. Once inventory-service says "held," order-service is what makes that fact durable — survives a restart, a deployment, a service crash. If a customer asks "did I actually get a seat," this is the service (indirectly) responsible for that answer, because it's the only one keeping a permanent record.

### `payment-service` — the money check
Currently simulates payment authorization (a placeholder — no real payment provider integrated yet). In a production version, this is where a real gateway (Stripe, Braintree, etc.) would plug in, without requiring changes to any other service, because the rest of the system only cares about the *event* it publishes (approved or declined), not how that decision was made.

### `notification-service` — keeping the customer informed
Currently logs a message rather than sending a real email/SMS — again, a placeholder. The architecture point being demonstrated: this can be wired to a real provider independently, without touching payment or order logic, because it only listens for events, it doesn't sit in the critical path of the sale itself.

---

## 6. Where the data lives, and why that split matters

| Question | Answered by | Why here, not elsewhere |
|---|---|---|
| "Is this seat available right now, this second?" | **Redis** | Needs to be answered in microseconds under heavy concurrent load. Also self-cleans via TTL — no risk of a seat staying "held" forever if a customer walks away. |
| "Did this order actually happen, permanently?" | **Postgres** | This is the record that has to survive restarts, deployments, and time. Redis is intentionally disposable; nothing that must be permanent lives there. |

This is the central design principle of the whole system: **fast, temporary decisions happen in Redis; permanent facts get written to Postgres.** Nothing downstream should ever treat a Redis key's continued existence as a guarantee — the moment payment succeeds, the durable Postgres write is what counts, not whether the Redis hold happens to still be there.

---

## 7. Verified so far

- ✅ End-to-end flow confirmed working: a reservation correctly flows through every service and lands as a `CONFIRMED` (or `FAILED`) row in Postgres.
- ✅ Atomic reservation logic is implemented via a single Redis script — no window exists where two requests can both succeed for the same seat.
- ✅ Duplicate event delivery (an inherent property of the messaging system used) is handled correctly at the database level for order creation, not just in application code that could be bypassed by a restart or multiple running copies of the service.
- ⏳ **Not yet load-tested.** The "high concurrency" claim is architecturally sound but hasn't yet been proven under simulated concurrent load (e.g. 100 simultaneous requests for the same seat). This is the next concrete milestone — see below.

---

## 8. Maturity & known gaps

Being direct about what this is and isn't, since that's more useful to a business stakeholder than a polished-sounding claim that doesn't hold up under questioning:

- **No real payment integration.** Payment success/failure is currently simulated (~90% success rate), not connected to an actual payment processor.
- **No real notifications.** Confirmation/failure messages are logged, not sent to an actual customer via email or SMS.
- **No authentication.** Any caller can currently reserve a seat as any user ID — there's no login or identity verification layer yet.
- **Failure-path cleanup is incomplete.** If payment fails, the seat currently stays "held" until its 5-minute timer naturally expires, rather than being released immediately. Functionally correct, but not optimal — a customer whose payment just failed can't have someone else grab that seat until the timer runs out.
- **Not load-tested yet.** The concurrency-safety claim is real at the code level (verified by reading the logic) but hasn't been proven with an actual concurrent-traffic test. This is the single most important next step before this claim can be stated with full confidence.
- **Local development only.** Currently runs on a developer's machine via Docker Compose. Cloud deployment (AWS EKS) is scaffolded — deployment manifests exist — but hasn't been executed against a live cluster yet.

None of these are architectural flaws — they're intentionally deferred scope, typical of a prototype proving out a pattern before investment in the surrounding production concerns (real payments, real notifications, auth, live infrastructure).

---

## 9. Recommended next steps, in order

1. **Load test the concurrency guarantee.** Fire concurrent requests at the same seat and confirm exactly one succeeds. This is the test that actually validates the core value proposition of the whole project.
2. **Close the failure-path gap.** Release the seat immediately on payment failure instead of waiting for the hold to expire.
3. **Add an order-status lookup** so a customer (or our own tooling) can check "did my reservation go through" without needing to query the database directly.
4. **Deploy to a live environment** to validate the Kubernetes/EKS manifests actually work outside local Docker Compose.
5. **Only after 1–4:** real payment integration, real notifications, authentication — the production-readiness work that depends on the core pattern already being proven.

---

## 10. Tech stack, for reference

Java 21 · Spring Boot 3 · Apache Kafka · Redis · PostgreSQL · Docker Compose (local) · Kubernetes/EKS (scaffolded, not yet deployed)