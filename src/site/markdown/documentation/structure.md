# Blockchain Structure

The `ca.yorku.cmg.cnsim.bitcoin.structure` package holds the data types that
represent the simulated Bitcoin ledger inside each node: blocks, the local
blockchain (with its main chain, orphans, and tips), and an ordering helper used
when multiple tips exist. The package integrates with the simulation engine by
implementing the engine's `IStructure` interface, so a `BitcoinNode` can ask its
structure whether a transaction is known, confirmed, or missing.

The three classes in the package are:

* `Block` — a single block: transactions plus parent link, height, and
validation metadata.
* `Blockchain` — the node's local ledger view: a main chain, an orphan pool,
and a tip list.
* `BlockHeightComparator` — a sort order (tallest first, then highest-ID first)
used by the tip-selection logic and by report printing.

---

## `Block`

Extends `TransactionGroup` (from the engine) so a block *is* a group of
transactions with a unique ID. On top of that, `Block` tracks where it sits in
the chain and how it was produced.

### Fields

|Field|Purpose|
|-|-|
|`parent` (`TransactionGroup`)|Reference to the parent block. `null` for genesis or for a locally-validated block before it is placed.|
|`height` (`int`)|Position in the chain; genesis is height 1.|
|`simTime\_validation`, `sysTime\_validation`|Simulation and wall-clock timestamps captured at validation.|
|`validationNodeID`|Node that produced the block.|
|`currentNodeID`|Node currently holding the block (updated on propagation).|
|`validationDifficulty`, `validationCycles`|Proof-of-work metrics recorded at validation.|
|`lastBlockEvent` (`String`)|Short label of the most recent event for logging.|

### Static ID counter

`Block` assigns IDs from a private static counter `currID`:

* `getNextID()` — returns the next ID and increments the counter.
* `getCurrID()` / `setCurrID(int)` — inspection and manual override.
* `resetCurrID()` — resets to 1. Called by `BitcoinMainDriver` between
simulations so each run starts from ID 1.

### Main methods

* `validateBlock(newTransList, simTime, sysTime, nodeID, eventType, difficulty, cycles)` —
invoked when a node finishes validating a block. Updates the underlying
`TransactionGroup`, records validation timestamps, the producing node, the
difficulty, and the computational cycles spent.
* `hasParent()`, `getParent()`, `setParent(TransactionGroup)` — parent-link
accessors used during chain insertion and traversal.
* `clone()`, `equals()`, `hashCode()` — standard object operations. `equals`
compares ID, height, validation metadata, parent, and the last event label.

---

## `Blockchain`

Implements `IStructure`. Each `BitcoinNode` owns one. It stores three parallel
views of the same ledger:

|Field|Purpose|
|-|-|
|`blockchain` (`ArrayList<Block>`)|The main chain; every block in it either is genesis or has its `parent` pointing at another block in this list.|
|`orphans` (`ArrayList<Block>`)|Blocks whose parent has not (yet) been seen locally. Revisited after every chain change.|
|`tips` (`ArrayList<Block>`)|Leaf blocks — blocks that no other block in the chain currently points at as a parent. A fork produces two tips.|
|`allTx` (`TransactionGroup`)|Aggregate of every transaction in the chain. Reserved for future transaction-containment optimizations.|

### Adding a block

`addToStructure(Block b)` is the single entry point. It routes based on whether
the block already has a parent reference:

* `b.hasParent() == true` → `placeBlockInChain(b)`. Used for blocks that
arrived via propagation (they carry their producer's parent reference) or
orphans being re-examined. The parent is looked up by ID in the local chain.
If it is not found, the block goes into `orphans`. If it is found, the chain
is traversed to detect transaction overlaps; overlap-free blocks are appended
and the tip list is updated, otherwise the block is discarded as corrupt.
* `b.hasParent() == false` → `pushBlockToChain(b)`. Used when the same node
just validated the block. The block is attached under the **tallest
non-overlapping tip**, selected by `getNonOverlappingTip(b)`. If no such tip
exists (the chain is empty, or only a conflicting path leads to genesis), the
block becomes a genesis-child.

Every successful addition triggers `processOrphans()`, which re-tries each
orphan against the grown chain and recurses while any orphan manages to be
placed.

### Overlap and conflict detection

* `hasChainOverlap(Block b, Block tip)` — walks from `tip` to genesis and
returns `true` if any ancestor shares a transaction ID with `b`. Used to
prevent double-inclusion across forks.
* `getNonOverlappingTip(Block b)` — sorts tips with `BlockHeightComparator`
(tallest first) and returns the first one whose chain to genesis does not
overlap with `b`. Returns `null` if none qualifies.

### Orphan management

* `processOrphans()` — copies `orphans`, clears the live list, and retries each
entry through `placeBlockInChain`. Recurses while the retry resolved at least
one orphan and others remain. This is what allows a chain delivered
out-of-order to eventually settle.
* `addToOrphans(Block b)` — quarantines `b` and emits an "Added to Orphans"
block event.

### Transaction queries

|Method|Meaning|
|-|-|
|`contains(Transaction t)` / `contains(long txID)`|Is the transaction anywhere in main chain or orphans?|
|`contains(Block block)`|Is any transaction of `block` already in the chain between `block.parent` and genesis? Used as a sanity check; logs an error if triggered.|
|`getTransactionDepth(Block tip, long txID)`|Number of blocks from `tip` back to (and including) the block that contains `txID`. `null` if not found.|
|`getConfirmations(long txID)`|Depth from the current longest tip — the standard "confirmations" metric.|
|`transactionBelief(long txID)`|Returns `1.0f` if the transaction is in the longest chain, `0.0f` otherwise.|
|`transactionBelieved(long txID)`|Boolean convenience wrapper around `transactionBelief`.|
|`satisfiesDependencies(Transaction t, TxDependencyRegistry)`|Delegates to the aggregate `allTx` group.|

### Chain inspection

* `getBlockchainHeight()` — the maximum height across all blocks.
* `getLongestTip()` — the tip with the greatest height (ties broken by
first-encountered in the tips list).
* `getBlockByID(int id)` — linear lookup.

### CSV reporting

Each of these returns `String\[]` rows for the reporter to append to a log:

* `printStructure()` — `BlockID,ParentID,BlockHeight,Transactions`.
* `printStructureReport(int nodeID)` — same plus `SimID,SimTime,SysTime,NodeID`
and a trailing `blockchain` tag; feeds the `StructureLog` CSV.
* `printOrphans()` / `printOrphansReport(int nodeID)` — orphan-list dumps,
analogous format, tagged `orphans`.
* `printTips(sep)`, `printTipsHash(sep)`, `printLongestChain()` — human-readable
summaries used in traces.

---

## `BlockHeightComparator`

A `Comparator<Block>` that orders blocks by **descending** height, breaking
ties by **descending** block ID. Used wherever tallest-first ordering matters:

* `Blockchain.getNonOverlappingTip(b)` — to prefer extending the longest chain
first.
* `Blockchain.printStructure()` / `printStructureReport(nodeID)` — so the
generated CSV lists the tip first and genesis last.

---



