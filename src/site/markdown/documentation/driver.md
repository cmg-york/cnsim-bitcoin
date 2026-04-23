# Driver and Factory

This page covers the two classes that wire the simulation together at startup:

* `BitcoinMainDriver` — the `main` entry point. Reads configuration, drives
the simulation loop, and resets global state between runs.
* `BitcoinSimulatorFactory` — concrete subclass of the engine's
`SimulatorFactory`. Supplies the Bitcoin-specific rule for how the node set
is populated (honest vs. attacker mix).

\---

## `BitcoinMainDriver`

A plain entry-point class — no inheritance, no strategy, no state. Its job is
to orchestrate: read config, run N simulations, flush reports.

### Entry point

```java
public static void main(String\[] args) {
    BitcoinMainDriver b = new BitcoinMainDriver();
    b.run(args);
}
```

`run(args)` performs a fixed sequence:

1. **Announce the engine version** — prints
`Config.class.getPackage().getImplementationVersion()` and the current
working directory.
2. **Initialize configuration** — `ConfigInitializer.initialize(args)` parses
the `-c path/to/file.properties` argument. If parsing throws `IOException`,
the driver prints the stack trace and calls `System.exit(1)`.
3. **Initialize the reporter** — `BitcoinReporter.initialize()` snapshots the
reporter flags from `Config`.
4. **Resolve the simulation range** — reads three properties:

   * `sim.numSimulations`
   * `sim.numSimulations.From`
   * `sim.numSimulations.To`

   If either `From` or `To` is `-1`, the loop runs `1..numSimulations`.
Otherwise it runs the explicit `\[From, To]` range. This is how a scripted
batch can resume or sharded-run a subset of simulation IDs without
re-running the whole sweep.

5. **Run each simulation** — `runSingleSimulation(simID)` for each ID.
6. **Flush reports** — `BitcoinReporter.flushAll()` after the last simulation.
Because the reporter accumulates statically, this is deferred to the very
end so every simulation contributes to the same set of CSV files.

   ### `runSingleSimulation(int simID)`

   ```
1. sf = new BitcoinSimulatorFactory()
2. s  = sf.createSimulation(simID)      // engine-wide setup + Bitcoin node set
3. s.run()                              // run the event loop
4. print s.getStatistics()
5. s.closeNodes()                       // triggers BitcoinNode.close() per node,
                                        // which in turn calls reportBlockChainState()
6. reset static ID counters:
   - PoWNode.resetCurrID()
   - Transaction.resetCurrID()
   - Block.resetCurrID()
```

   The three counter resets at the end are critical. `PoWNode`, `Transaction`,
and `Block` all mint IDs from static counters; without resetting them between
simulations, the second run would start with `BlockID=30000` instead of `1`,
making report diffing and test reproducibility impossible. The reporter
buffers are **not** reset — they are expected to span the whole batch.

   \---

   ## `BitcoinSimulatorFactory`

   Extends `ca.yorku.cmg.cnsim.engine.SimulatorFactory`. Almost all of the
simulation wiring (network topology, transaction workload, termination
conditions, reporting hooks, event scheduler) is handled by the parent's
`createSimulation(int)` — see the engine documentation for details. This
subclass overrides only what is Bitcoin-specific.

   ### `createSimulation(int simID)`

   Currently a thin passthrough:

   ```java
Simulation s = super.createSimulation(simID);
return s;
```

   It exists as an override point for Bitcoin-specific scheduling (for example,
hashpower-change events configured via `node.hashPowerChanges`). The class
imports `Event\_HashPowerChange`, so that wiring is intended to live here when
enabled.

   ### `createNodeSet(Simulation s)` — the Bitcoin-specific logic

   The engine calls this to produce the `PoWNodeSet` used in the simulation.
This override splits the node count into honest and attacker buckets:

   ```
attackingNodes = Config.hasProperty("hiddenChainAttack.attackingNodes")
                    ? Config.getPropertyInt("hiddenChainAttack.attackingNodes")
                    : 0
honestNodes    = Config.getPropertyInt("net.numOfNodes") - attackingNodes

ns = new PoWNodeSet()

ns.setNodeFactory(new BitcoinNodeFactory("Honest", s))
ns.addNodes(honestNodes)

ns.setNodeFactory(new BitcoinNodeFactory("Hidden Chain Attack", s, ns))
ns.addNodes(attackingNodes)
```

   Two details worth noting:

* **Honest nodes are added first.** The attacker factory needs a reference to
the already-populated `PoWNodeSet` so it can compute `networkHonestPower`
from the honest nodes' total hash power. If no attack is configured, only
the honest pass runs.
* **The attacker factory constructor takes three arguments** —
`BitcoinNodeFactory(String mode, Simulation s, PoWNodeSet honestPeers)` —
where the third is used to size the attacker's mining power relative to the
honest network.

  ### Configuration properties read

|Property|Used for|
|-|-|
|`net.numOfNodes`|Total number of nodes in the simulation.|
|`hiddenChainAttack.attackingNodes`|Count of nodes that run `HiddenChainAttackBehavior`. Optional; defaults to 0.|
|`node.hashPowerChanges`|Dynamic hashpower changes during the run, format `{nodeID:newHashPower:time,...}`. Placeholder for future scheduling in `createSimulation`.|

\---

## How they fit together

```
BitcoinMainDriver.main
  └─ run(args)
       ├─ ConfigInitializer.initialize(args)
       ├─ BitcoinReporter.initialize()
       └─ for each simID:
            └─ runSingleSimulation(simID)
                 ├─ new BitcoinSimulatorFactory()
                 ├─ sf.createSimulation(simID)
                 │    ├─ super.createSimulation (engine)
                 │    └─ createNodeSet
                 │         └─ BitcoinNodeFactory → BitcoinNode × N
                 ├─ s.run()
                 ├─ s.closeNodes()
                 └─ reset PoWNode / Transaction / Block IDs
       └─ BitcoinReporter.flushAll()
```

