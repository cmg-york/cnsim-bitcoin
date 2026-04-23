# Reporter

`BitcoinReporter` extends the engine's `ca.yorku.cmg.cnsim.engine.reporter.Reporter`
and adds three Bitcoin-specific CSV reports on top of the general simulation
reports produced by the base class. It is accessed entirely through static
methods: the reporter is not instantiated per node, and all logs are
accumulated in static buffers over the lifetime of the JVM, then flushed once
at the end of the driver run.

---

## Configuration flags

`BitcoinReporter.initialize()` reads four properties and stores them as
enable-flags that gate every log write:

| Property | Enables |
|----------|---------|
| `reporter.reportBlockEvents` | Per-block events (validations, appends, orphan moves, discards). |
| `reporter.reportStructureEvents` | End-of-simulation dump of each node's blockchain and orphan list. |
| `reporter.reportAttackEvents` | Attack lifecycle markers (entering MONITORING, starting attack, releasing chain). |
| `reporter.reportAttackDetails` | More verbose attack output (per-malicious-block validation lines). |

The last two use `Config.getOptionalPropertyBoolean`, so omitting them from the
properties file leaves the feature off.

---

## CSV outputs

File names incorporate `Reporter.runId`, a timestamp set by the engine, so a
single driver run produces one set of files per report type.

### `BlockLog - [runId].csv`

Written by `reportBlockEvent(...)` and gated by `reporter.reportBlockEvents`.

| # | Column | Source |
|---|--------|--------|
| 1 | `SimID` | simulation index |
| 2 | `SimTime` | simulation time at the event |
| 3 | `SysTime` | wall-clock delta from `Simulation.sysStartTime` |
| 4 | `NodeID` | node associated with the event |
| 5 | `BlockID` | block the event concerns |
| 6 | `ParentID` | parent block ID, or `-1` |
| 7 | `Height` | block height at the time |
| 8 | `BlockContent` | `;`-separated transaction IDs |
| 9 | `EventType` | e.g. `Node Completes Validation`, `Appended On Chain (w/ parent)`, `Added to Orphans`, `Discarded due to overlap with parent's chain` |
| 10 | `Difficulty` | validation difficulty (or `-1.0` for propagation receipts) |
| 11 | `Cycles` | cycles spent validating (or `-1.0` for propagation) |

### `StructureLog - [runId].csv`

Written by `reportBlockChainState(blockchain, orphans)`, called from
`BitcoinNode.close()` so each node dumps its final view. Gated by
`reporter.reportStructureEvents`.

| # | Column |
|---|--------|
| 1 | `SimID` |
| 2 | `SimTime` |
| 3 | `SysTime` |
| 4 | `NodeID` |
| 5 | `BlockID` |
| 6 | `ParentBlockID` |
| 7 | `Height` |
| 8 | `Content` |
| 9 | `Place` — either `blockchain` or `orphans` |

The rows themselves are built by `Blockchain.printStructureReport(nodeID)` and
`Blockchain.printOrphansReport(nodeID)`.

### `AttackLog - [runId].csv`

Written by `reportAttackEvent(...)`. Gated by `reporter.reportAttackEvents`.

| # | Column |
|---|--------|
| 1 | `SimID` |
| 2 | `SimTime` |
| 3 | `SysTime` |
| 4 | `NodeID` |
| 5 | `EventType` |
| 6 | `TransactionID` |
| 7 | `BlockID` |
| 8 | `BlockHeight` |
| 9 | `HiddenChainLength` |
| 10 | `PublicChainLength` |
| 11 | `Description` |

Event types include `Target TX Arrival`, `Attack Start`, `Chain Reveal`, and
attack-state transitions emitted by `HiddenChainAttackBehavior` (e.g. `Going
into Monitoring`, `Attack is Starting`, `Releasing chain`).

The inherited `addEvent(...)` method from the base `Reporter` is also used for
free-form attack breadcrumbs that don't fit the structured `AttackLog` schema;
those land in the engine's generic event log.

---

## Key public methods

| Method | Role |
|--------|------|
| `initialize()` | Read the four reporter flags from `Config` at driver startup. |
| `reportBlockEvent(...)` | Append a row to `blockLog` if `reportBlockEvents`. |
| `reportBlockChainState(blockchain, orphans)` | Append per-node chain + orphan rows to `structureLog` if `reportStructureEvents`. |
| `reportAttackEvent(...)` | Append a row to `attackLog` if `reportAttackEvents`. |
| `reportsBlockEvents()` / `reportsStructureEvents()` / `reportsAttackEvents()` / `reportsAttackDetails()` | Flag getters used by producers to skip expensive formatting when a report is off. |
| `flushBlockReport()` / `flushStructReport()` / `flushAttackReport()` | Write one buffer to its CSV. |
| `flushCustomReports()` | Flush the three Bitcoin-specific logs. |
| `flushAll()` | Call the base `Reporter.flushAll()` then `flushCustomReports()`. The driver calls this once after all simulations. |

All buffers (`blockLog`, `structureLog`, `attackLog`) are `static` and are
pre-seeded with CSV header rows in a static initializer, so headers appear at
the top of each file without an explicit call.

---

## Lifecycle

1. `BitcoinMainDriver.run(...)` calls `ConfigInitializer.initialize(args)` to
   load the properties file, then calls `BitcoinReporter.initialize()` to
   snapshot the four flags.
2. During each simulation, node behaviors and `Blockchain` call
   `reportBlockEvent`, `addEvent`, and `reportAttackEvent` as events occur.
   Every `add*` method checks its flag first, so a disabled report costs only
   the flag check.
3. When the driver has run all simulations it calls `BitcoinReporter.flushAll()`
   exactly once. That writes the engine's standard reports via the parent
   class and the three Bitcoin-specific reports via `flushCustomReports()`.
4. Because the buffers are static, per-simulation state is **not** cleared
   between runs; every simulation contributes to the same output files. Rows
   are disambiguated by the `SimID` column.


