# Overview

CNSim Bitcoin is a research simulator of the Nakamoto consensus protocol. It
models the behavior of individual Bitcoin nodes — honest ones that follow the
standard protocol, and attacker variants that run a hidden-chain double-spend
strategy — on top of the generic discrete-event CNSim engine. The simulator is
used to study block propagation, fork resolution, confirmation dynamics, and
the probability of successful double-spend attacks under varying network and
adversary parameters.

This document pulls together the pieces described in the other five pages:

- [Blockchain Structure](structure.md) — `Block`, `Blockchain`, `BlockHeightComparator`.
- [Node and Honest Behavior](honest.md) — `BitcoinNode`, `BitcoinNodeFactory`,
  `NodeBehaviorStrategy`, `DefaultNodeBehavior`, `HonestNodeBehavior`,
  `HonestNodeBehaviorLimited`.
- [Hidden Chain Attack Behavior](attack.md) — `HiddenChainAttackBehavior`.
- [Reporter](reporter.md) — `BitcoinReporter`.
- [Driver and Factory](driver.md) — `BitcoinMainDriver`, `BitcoinSimulatorFactory`.

---

## Instantiation of the CNSim engine

The Bitcoin simulator plugs into the CNSim engine through a short chain of
factory calls. Every run starts from `BitcoinMainDriver.main`, reads its
configuration, and flows down to concrete node instances:

```
BitcoinMainDriver.main(args)
 └─ BitcoinMainDriver.run(args)
     ├─ ConfigInitializer.initialize(args)          [engine]
     ├─ BitcoinReporter.initialize()                [bitcoin]
     └─ for each simID in sim.numSimulations range:
         └─ runSingleSimulation(simID)
             ├─ new BitcoinSimulatorFactory()       [bitcoin]
             ├─ sf.createSimulation(simID)
             │    ├─ SimulatorFactory#createSimulation(simID) [engine]
             │    │    wires: network, workload, reporters,
             │    │           termination, event scheduler
             │    └─ BitcoinSimulatorFactory#createNodeSet(s) [bitcoin]
             │         ├─ BitcoinNodeFactory("Honest", s)            × honest count
             │         └─ BitcoinNodeFactory("Hidden Chain Attack", s, ns) × attacker count
             │              └─ BitcoinNode + NodeBehaviorStrategy
             │                   (HonestNodeBehavior or HiddenChainAttackBehavior)
             ├─ s.run()                             [engine event loop]
             ├─ s.closeNodes()                      → BitcoinNode#close writes StructureLog
             └─ reset static IDs: PoWNode, Transaction, Block
         (loop)
     └─ BitcoinReporter.flushAll()                   writes BlockLog/StructureLog/AttackLog
                                                     plus engine-level reports
```

The engine owns the event loop, the configuration system, the sampler (which
supplies per-node hash power, electric power, and electricity cost), the
network topology, and the workload generator. The Bitcoin layer owns the
ledger data types, the node behavior, and the Bitcoin-specific reports.

---

## Extended from `cnsim-engine`

Classes in the engine extended by Bitcoin-specific subclasses:

| Engine class | Bitcoin subclass | What is added |
|--------------|------------------|----------------|
| `ca.yorku.cmg.cnsim.engine.reporter.Reporter` | `BitcoinReporter` | `BlockLog`, `StructureLog`, `AttackLog` plus the four `reporter.report*` flags. |
| `ca.yorku.cmg.cnsim.engine.SimulatorFactory` | `BitcoinSimulatorFactory` | `createNodeSet` split into honest vs. attacker populations. |
| `ca.yorku.cmg.cnsim.engine.node.AbstractNodeFactory` | `BitcoinNodeFactory` | Mode-string dispatch between `"Honest"` and `"Hidden Chain Attack"`. Reads attack parameters from config. |
| `ca.yorku.cmg.cnsim.engine.node.PoWNode` | `BitcoinNode` | Owns a `Blockchain`, a mining pool, difficulty/threshold config, and a `NodeBehaviorStrategy`. |
| `ca.yorku.cmg.cnsim.engine.transaction.TransactionGroup` | `Block` | Parent pointer, height, validation metadata, static block-ID counter. |

---

## Reused unchanged

The following engine types are used as-is — imported, referenced, and handed
around, but not subclassed:

- `Simulation` — event loop, clock, simulation-wide references.
- `Config`, `ConfigInitializer` — properties-file configuration.
- `PoWNodeSet` — engine's node collection for proof-of-work simulations.
- `IMiner`, `INode` — engine's mining and node interfaces.
- `IStructure` — the contract `Blockchain` implements so the engine can query
  transaction belief and confirmations.
- `Transaction`, `ITxContainer` — transaction and container types from the
  engine's transaction package.
- `Event_ContainerValidation`, `Event_HashPowerChange` — engine event types
  the Bitcoin layer schedules.
- The `Sampler` accessors used by `BitcoinNodeFactory` to read hash power,
  electric power, and electricity cost per node.

---

## New, Bitcoin-specific

Classes introduced by this module that have no direct engine counterpart:

**Structure**
- `Blockchain` — main chain, orphans, tips, chain queries, CSV printers.
- `BlockHeightComparator` — sort order used by tip selection and report
  printing.

**Node behavior**
- `NodeBehaviorStrategy` — the four-event strategy contract.
- `DefaultNodeBehavior` — abstract base with mining control, pool
  management, block-integration helpers, and validators.
- `HonestNodeBehavior` — canonical Bitcoin protocol.
- `HonestNodeBehaviorLimited` — testing-only variant, reused by the attacker.
- `HiddenChainAttackBehavior` — the hidden-chain double-spend strategy and
  its `State` / `ReleaseStrategy` enums.

**Analysis and utilities**
- `ca.yorku.cmg.cnsim.bitcoin.analysis.AttackMetricsCollector` —
  singleton that aggregates attack outcomes across simulations.
- `ca.yorku.cmg.cnsim.bitcoin.analysis.AttackerSuccessProbabilityAnalyzer` —
  implements the Bitcoin whitepaper Section 11 probability formula for
  comparison against simulation results.
- `ca.yorku.cmg.cnsim.bitcoin.utils.BitcoinDifficultyUtility` — conversions
  between difficulty representations.

**Entry point and factories**
- `BitcoinMainDriver` — CLI entry point, simulation loop, cross-run ID reset.
- `BitcoinSimulatorFactory` — honest/attacker node-set assembly.
- `BitcoinNodeFactory` — per-node creation with strategy selection.

<claude> After you have done all that, create an overview.md that offers and overview and also lists the steps took to instantiate the cnsim-engine, including what has been extended and reused from cnsim</claude>
