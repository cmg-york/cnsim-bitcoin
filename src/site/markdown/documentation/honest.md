# Node and Honest Behavior

The `ca.yorku.cmg.cnsim.bitcoin.node` package houses the simulated Bitcoin
node plus the plug-in behavior strategies it delegates to. This page covers
the honest side of that package. For the hidden-chain attacker, see
[attack.md](attack.md).

The key idea is the **strategy pattern**: a `BitcoinNode` does almost no
protocol logic of its own. Instead it holds a `NodeBehaviorStrategy` and
forwards every event to it. Swapping the strategy turns the same node into an
honest miner, an attacker, or a testing stub.

```
NodeBehaviorStrategy (interface)
  └── DefaultNodeBehavior (abstract — mining, pool management, validators)
        ├── HonestNodeBehavior          (canonical Bitcoin protocol)
        │     └── HonestNodeBehaviorLimited (testing-only variant)
        └── HiddenChainAttackBehavior   (see attack.md)
```

Creation of the node and wiring of the strategy are handled by
`BitcoinNodeFactory`, selected at runtime via a mode string (`"Honest"` vs
`"Hidden Chain Attack"`).

---

## `NodeBehaviorStrategy`

The interface every node-behavior class implements. Four event methods, each
called by `BitcoinNode` when the corresponding simulation event fires:

| Method | Triggered by |
|--------|--------------|
| `event_NodeReceivesClientTransaction(Transaction t, long time)` | A client delivers a transaction directly to this node. |
| `event_NodeReceivesPropagatedTransaction(Transaction t, long time)` | A peer propagates a transaction to this node. |
| `event_NodeReceivesPropagatedContainer(ITxContainer t)` | A peer propagates a block (container) to this node. |
| `event_NodeCompletesValidation(ITxContainer t, long time)` | This node finishes mining a block. |

Concrete strategies decide how to validate, propagate, mine, or (in the
attacker case) secretly divert each of these events.

---

## `DefaultNodeBehavior` (abstract)

A shared base class that provides the boilerplate every concrete strategy
needs: mining control, pool maintenance, validator helpers, and a common
new-block integration path. It holds a back-reference to the `BitcoinNode`
(`protected BitcoinNode node`) so the helpers can read and mutate node state.

The four `event_*` methods are declared abstract here — each concrete strategy
fills them in.

### Mining control

- **`considerMining(long time)`** — the mining state machine. If
  `isWorthMining()` returns `true` and the node is not already mining, it
  schedules a new `Event_ContainerValidation` for a block built from the
  current mining pool and calls `node.startMining(itr)`. If mining is no
  longer worthwhile, it flags the pending validation event as ignored and
  calls `node.stopMining()`. The method also carries defensive assertions —
  it throws `IllegalStateException` if it finds the node in an inconsistent
  combination of "mining flag" and "pending validation event".
- **`isWorthMining()`** — returns `true` when the mining pool's value exceeds
  `BitcoinNode.getMinValueToMine()`. `HiddenChainAttackBehavior` overrides this
  to return `true` as long as the attacker has any transactions to mine.

### Pool management

- **`reconstructMiningPool()`** — rebuilds the mining pool by picking the top
  N transactions from the regular pool, sorted by value-per-size
  (`TxValuePerSizeComparator`). N is `bitcoin.maxBlockSize` from config.
- **`transactionReceipt(Transaction t, long time)`** — the standard "I accept
  this transaction" path: `node.addTransactionToPool(t)` →
  `reconstructMiningPool()` → `considerMining(time)`.

### Block integration and post-validation cleanup

- **`handleNewBlockReception(Block b)`** — invoked when a received/propagated
  block passes validation. Calls `node.getStructure().addToStructure(b)`,
  extracts the block's transactions from the pool, reconstructs the mining
  pool, and calls `considerMining(Simulation.currTime)`.
- **`processPostValidationActivities(long time)`** — invoked after this node
  finishes its own validation. Stops mining, resets the next validation
  event, removes the validated transactions from the pool, reconstructs the
  mining pool, and reconsiders mining.

### Validator helpers

- **`conflictFree(Transaction t)`** — looks up the transaction's conflict
  partner in the simulation's conflict registry. Returns `true` unless that
  partner is already in the pool, mining pool, or structure. Throws
  `IllegalStateException` if the registry has no entry for `t`.
- **`dependenciesPresent(Transaction t)`** — delegates to
  `node.getPool().satisfiesDependenciesOf_Incl_3rdGroup(...)`, passing the
  full blockchain structure as the third dependency group.
- **`transactionContainedInPool(Transaction t)`** /
  **`transactionContainedInStructure(Transaction t)`** — presence checks.
  The latter is overridden in `HonestNodeBehaviorLimited` to always return
  `false`.
- **`getConflictBlock(Block b)`** — builds a synthetic block populated with
  stand-in `Transaction` objects that share IDs with each real transaction's
  conflict partner. Used by the block-reception path to detect whether any
  conflict counterpart is already buried in the chain.

---

## `BitcoinNode`

Extends the engine's `PoWNode`. Responsibilities:

- Hold the node's local ledger (`Blockchain blockchain`, exposed via
  `getStructure()` as the engine's `IStructure`).
- Hold the node's mining pool (`TransactionGroup miningPool`).
- Hold proof-of-work parameters (`operatingDifficulty`, `minValueToMine`,
  `minSizeToMine`) read from config (`pow.difficulty`, `bitcoin.minValueToMine`,
  `bitcoin.minSizeToMine`).
- Hold the active `NodeBehaviorStrategy` and forward every event to it.

### Event forwarding

Every `event_*` override on `BitcoinNode` is a one-liner that delegates:

```java
public void event_NodeReceivesClientTransaction(Transaction t, long time) {
    behaviorStrategy.event_NodeReceivesClientTransaction(t, time);
}
```

The node class itself does not encode any protocol decisions — it is a
container for state plus the strategy hook.

### Reporting

- **`beliefReport(long[] sample, long time)`** — for each transaction ID in
  `sample`, reads `blockchain.transactionBelief(...)` and writes a row via
  `Reporter.addBeliefEntry(...)`.
- **`close(INode n)`** — called by `Simulation.closeNodes()`. Dumps the final
  per-node chain and orphan state by passing
  `blockchain.printStructureReport(id)` and `printOrphansReport(id)` to
  `BitcoinReporter.reportBlockChainState(...)`.

The other `Reporter` hooks (`periodicReport`, `timeAdvancementReport`,
`nodeStatusReport`, `structureReport`) are currently no-ops.

### Getters and setters

Typical accessors: `getMiningPool`/`setMiningPool`, `getMinValueToMine`/`set…`,
`getMinSizeToMine`/`set…`, `getOperatingDifficulty`, `getStructure`,
`setStructure`, `getProspectiveCycles`, `belief(Transaction)` and
`belief(long)`.

---

## `BitcoinNodeFactory`

Extends the engine's `AbstractNodeFactory`. One instance is bound to one
behavior mode string (chosen when constructed) and produces nodes of that
flavor when `createNewNode()` is called.

### Constructors

```java
BitcoinNodeFactory(String defaultNodeBehavior, Simulation sim)
BitcoinNodeFactory(String defaultNodeBehavior, Simulation sim, PoWNodeSet nodes)
```

The three-argument form is required when creating attackers, because the
attacker needs to see the already-populated honest `PoWNodeSet` to compute its
mining power.

### `createNewNode()`

Fixed sequence regardless of mode:

1. `new BitcoinNode(sim)`.
2. Draw per-node properties from the engine's sampler: `HashPower`,
   `ElectricPower`, `ElectricityCost`.
3. Build the strategy based on the mode string:

   - **`"Honest"`** → `new HonestNodeBehavior(node)`. Done.
   - **`"Hidden Chain Attack"`** → `new HiddenChainAttackBehavior(node,
     new HonestNodeBehavior(node), new HonestNodeBehaviorLimited(node))`, then
     read attack parameters from config:
     `hiddenChainAttack.targetTransaction`, `.startAdvantage`,
     `.releaseAdvantage` or `.releaseConfirmations` (exactly one of the two),
     optional `.attackTimeOut`, and compute `attackPower` from
     `hiddenChainAttack.maliciousPowerRatio` and the honest network's total
     hash power. Finally call `goToMonitoringState()` to arm the attack. See
     [attack.md](attack.md).
   - Any other string → `ConfigurationException`.
4. `node.setBehaviorStrategy(strategy)` and return the node.

---

## `HonestNodeBehavior`

The canonical Bitcoin protocol, implemented as four event handlers on top of
`DefaultNodeBehavior`'s helpers. The call hierarchies below mirror the
existing [`NodeBehavior.md`](NodeBehavior.md) style, with prose above each
event explaining why the steps are arranged this way.

### event_NodeReceivesClientTransaction(Transaction t, long time)

A client hands the node a fresh transaction. The node must both accept it
into its own pool **and** gossip it onward, but only if it passes the basic
conflict and dependency checks. Unlike the propagated case, we do not check
for prior presence in the structure — clients are considered authoritative
for their own submissions.

- \[check dependencies and conflicts\]
- \[DEFAULT\].transactionReceipt(t, time)
    - \[NODE\].addTransactionToPool(t)
    - \[DEFAULT\].reconstructMiningPool()
    - \[DEFAULT\].considerMining()
        - \[DEFAULT\].isWorthMining();
        - \[NODE\].scheduleValidationEvent(\[mining pool\])
        - \[NODE\].startMining(interval);
- \[NODE\].broadcastTransaction(t, time)

If either check fails, the transaction is discarded and an error-log entry
records the reason ("dependencies not satisfied", "conflicts present", or
both).

### event_NodeReceivesPropagatedTransaction(Transaction t, long time)

A peer forwarded a transaction. The node must decide whether this is the
first time it's seeing it — if so, accept it; if it's already in the pool or
the chain, drop it. No rebroadcast: propagation in this simulator is
single-hop from the point of view of the behavior, and the engine's gossip
is responsible for multi-hop spread.

- \[check dependencies and conflicts\]
- \[check not already in pool or structure\]
- \[DEFAULT\].transactionReceipt(t, time)
    - \[NODE\].addTransactionToPool(t)
    - \[DEFAULT\].reconstructMiningPool()
    - \[DEFAULT\].considerMining()
        - \[DEFAULT\].isWorthMining();
        - \[NODE\].scheduleValidationEvent(\[mining pool\])
        - \[NODE\].startMining(interval);

If the transaction already lives in the pool or the structure, the event is
recorded as a duplicate and silently dropped. If conflict or dependency
checks fail, the rejection reason is logged.

### event_NodeReceivesPropagatedContainer(ITxContainer t)

A peer forwarded a block. Before integrating it, the node must verify three
properties against its current view of the chain:

1. None of the block's transactions are already in the structure on the path
   it would join (overlap with own chain).
2. None of the block's transactions conflict with something already in the
   structure (double-spend check via the synthetic conflict block).
3. All of the block's transaction dependencies are satisfied by the structure.

Only if all three hold does the block flow through
`handleNewBlockReception`. Otherwise the block is discarded with a logged
reason.

- \[NODE\].setCurrentNodeID, setLastBlockEvent (stamp receipt metadata)
- \[REPORT\] reportBlockEvent("Node Receives Propagated Block")
- \[check\] !structure.contains(b)                    — no overlap with chain
- \[check\] !structure.contains(getConflictBlock(b))  — no conflicting tx in chain
- \[check\] structure.satisfiesDependencies(b, ...)   — all deps present
- \[DEFAULT\].handleNewBlockReception(b)
    - \[NODE\].getStructure().addToStructure(b)
    - \[NODE\].getPool().extractGroup(b)
    - \[DEFAULT\].reconstructMiningPool()
    - \[DEFAULT\].considerMining()
        - \[DEFAULT\].isWorthMining();
        - \[NODE\].scheduleValidationEvent(\[mining pool\])
        - \[NODE\].startMining(interval);

### event_NodeCompletesValidation(ITxContainer t, long time)

This node just mined a block. Three things must happen: stamp validation
metadata onto the block, broadcast a copy to peers, and reset the node's
mining pipeline for the next round. A sanity check runs in the middle to
verify the miner did not produce a block that overlaps with its own chain
(which would indicate a bug upstream — the mining pool is supposed to always
be conflict-free).

- \[BLOCK\].validateBlock(miningPool, simTime, sysTime, nodeID, "Node Completes Validation", difficulty, cycles)
- \[NODE\].completeValidation(miningPool, time)
- \[REPORT\] reportBlockEvent("Node Completes Validation")
- \[sanity check\] setParent(longestTip); if structure.contains(b): log ERROR
- \[set parent to null\] — let Structure resolve the parent on insertion
- \[NODE\].getStructure().addToStructure(b)
- \[NODE\].broadcastContainer(b.clone(), time)
- \[DEFAULT\].processPostValidationActivities(time)
    - \[NODE\].stopMining()
    - \[NODE\].resetNextValidationEvent()
    - \[NODE\].removeFromPool(miningPool)
    - \[DEFAULT\].reconstructMiningPool()
    - \[DEFAULT\].considerMining()

The clone before broadcast is essential: `Block` carries mutable per-node
state (current holder ID, last event label), so peers must receive their own
copies rather than references to the miner's live object.

---

## `HonestNodeBehaviorLimited`

A testing-only subclass of `HonestNodeBehavior` that loosens two of the base
class's safety checks so unit tests can drive predictable flows through the
pool without blockchain-consistency logic getting in the way:

- `transactionContainedInStructure(Transaction t)` always returns `false`, so
  the "already in chain" guard in
  `event_NodeReceivesPropagatedTransaction` never fires.
- `handleNewBlockReception(Block b)` only calls
  `node.getStructure().addToStructure(b)`. No pool extraction, no mining-pool
  reconstruction, no reconsideration of mining. Test state therefore stays
  deterministic across block arrivals.

This class is not intended for production simulations. It is also used by
`HiddenChainAttackBehavior` as its `altHonestBehavior` during the `ATTACKING`
state — see [attack.md](attack.md) — because the attacker needs public-chain
blocks to be recorded without clearing its own pool.

