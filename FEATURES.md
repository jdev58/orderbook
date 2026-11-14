# Feature list — HFT Order Book

Legend: ✅ done · 🚧 planned · ❌ not planned (v0.x)

---

## Core order book

| Feature | Status | Notes |
|---------|--------|--------|
| Limit orders (GTC-style rest) | ✅ | Implicit GTC: remainder rests on book |
| Buy / sell sides | ✅ | `OrderSide` |
| Price–time priority | ✅ | `TreeMap` levels + FIFO `PriceLevel` |
| Maker price on trade | ✅ | Taker matches at resting price |
| Partial fills | ✅ | `PARTIALLY_FILLED` → `FILLED` |
| Order lifecycle | ✅ | NEW → PARTIALLY_FILLED / FILLED / CANCELLED |
| Register + open-order index | ✅ | `allOrdersById` + `openOrdersById` (Agrona) |
| Cancel resting order | ✅ | Removes from level; prunes empty level |
| Remove fully filled maker | ✅ | `removeFilledOrder` |
| Book snapshot (depth N) | ✅ | Best bid/ask, spread, mid, level aggregates |
| Replace (cancel + new submit) | ✅ | `MatchingEngine.replace` |

---

## Matching engine

| Feature | Status | Notes |
|---------|--------|--------|
| Continuous matching on submit | ✅ | While crossed with contra best |
| Multi-level sweep (small qty) | ✅ | Covered by tests + JMH |
| Market orders | 🚧 | Not implemented |
| IOC (immediate-or-cancel) | 🚧 | Not implemented |
| FOK (fill-or-kill) | 🚧 | Not implemented |
| Stop / stop-limit | ❌ | Future |
| Self-trade prevention (STP) | ❌ | Future |
| Post-only / maker-only | ❌ | Future |

---

## Data structures & performance

| Feature | Status | Notes |
|---------|--------|--------|
| `TreeMap` bid/ask levels | ✅ | Current production path in code |
| Intrusive doubly-linked FIFO per level | ✅ | `Order.prev` / `next` |
| Agrona `Long2ObjectHashMap` | ✅ | Order id lookup |
| Cached best bid/ask | 🚧 | Still uses `firstEntry()` on `TreeMap` |
| Level aggregates (qty/count O(1)) | 🚧 | Snapshot walks FIFO today |
| Allocation-free / pooled hot path | 🚧 | New `ArrayList`, `Trade`, `Order` per submit |
| `LongAdaptiveRadixTreeMap` / ART price index | 🚧 | Evaluated; not integrated |
| Tick-indexed array book | 🚧 | Optional for fixed tick symbols |

---

## Engine & concurrency

| Feature | Status | Notes |
|---------|--------|--------|
| Dedicated matching thread / event queue | 🚧 | `engine/` package placeholder only |
| Single-writer book (matching thread) | 🚧 | Demo uses `synchronized` on HTTP handlers |
| Multi-symbol books | ❌ | Single in-memory book |
| Persistence / WAL / replay | ❌ | In-memory only |

---

## Demo & tooling

| Feature | Status | Notes |
|---------|--------|--------|
| HTTP demo server (port 8765) | ✅ | JDK `HttpServer` + virtual threads |
| RTL web UI — depth ladder | ✅ | `src/main/resources/front/` |
| REST: book, order, cancel, replace | ✅ | JSON |
| Order status lookup | ✅ | `/api/order/status` |
| Trade log API | ✅ | `/api/trades` |
| Seed sample book | ✅ | `/api/seed` |
| Docker / compose | ❌ | Not yet |
| OpenAPI spec | ❌ | Not yet |

---

## Quality & benchmarks

| Feature | Status | Notes |
|---------|--------|--------|
| JUnit 5 unit tests | ✅ | Book, level, engine, lifecycle |
| Maven Enforcer (Java 25) | ✅ | |
| JMH benchmark profile (`-Pjmh`) | ✅ | `benchmarks.jar` + `benchmarks.bat` |
| Benchmarks: rest, match, cancel, snapshot | ✅ | `OrderBookBenchmarks` |
| CI (GitHub Actions) | ✅ | `.github/workflows/ci.yml` — `mvn test` on push/PR |
| Mutation / property tests | ❌ | Future |

---

## Validation & exchange rules

| Feature | Status | Notes |
|---------|--------|--------|
| Positive id / price / quantity | ✅ | Constructor checks |
| Duplicate order id on register | ✅ | Throws |
| Terminal order on book | ✅ | Rejected |
| Tick size / lot size enforcement | ❌ | Future |
| Min / max price bands | ❌ | Future |
| Max open orders per user | ❌ | Future |

---

## Baseline numbers (reference only)

Example JMH run (JDK 25, `-wi 5 -i 10 -r 1s -f 3`, **not** a guarantee):

| Benchmark | ~Throughput |
|-----------|-------------|
| `cancelOpenOrder` | ~37M ops/s |
| `singleLevelMatch` | ~5.2M ops/s |
| `snapshotDepth15` | ~4.6M ops/s |
| `multiLevelMatch` | ~4.0M ops/s |
| `restingSubmit` | ~1.9M ops/s (book grows over trial) |

Re-run locally after any change:

```bash
mvn -Pjmh package -DskipTests
java -jar target/benchmarks.jar -wi 5 -i 10 -r 1s -f 3 com.hft.orderbook.bench.OrderBookBenchmarks
```

---

## Suggested milestones for “v0.2”

1. **Product:** IOC + FOK + market + `TimeInForce` on `Order`  
2. **Engine:** `MatchingEngine` behind a small command queue API  
3. **Perf:** best bid/ask cache + level aggregates; JMH `restingSubmit` with fixed depth  
4. **Docs:** expand API docs; optional OpenAPI  
5. **Optional:** price-level map upgrade (ART or `Long2ObjectHashMap` + sorted level list)

---

## What this project is / isn’t

**Is:** a clear Java reference for price–time books, teaching, and perf baselines.  
**Isn’t:** a licensed exchange, colocated HFT stack, or audited trading system.