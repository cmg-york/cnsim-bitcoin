# Hidden Chain Attack Behavior

`HiddenChainAttackBehavior` is the attacker-side `NodeBehaviorStrategy`. It
extends `DefaultNodeBehavior` like the honest strategy does, but composes two
honest behaviors internally and dispatches to them based on a three-state
machine. The goal is a **selfish / hidden-chain double-spend attack**: the
node waits for a target transaction to appear in a public block, then
secretly mines a private chain that excludes the target, and later releases
the private chain if and when it is long enough to replace the public one.

See [honest.md](honest.md) for the strategy pattern, `DefaultNodeBehavior`
helpers, and `BitcoinNodeFactory` wiring.

---

## States

Three states, managed by the inner enum `State`:

| State | Meaning |
|-------|---------|
| `IDLE` | Normal honest operation; no attack in progress. |
| `MONITORING` | Honest on the surface; waiting for a block containing the target transaction. The attack is armed but has not started. |
| `ATTACKING` | Secretly mining a private chain rooted at the parent of the target-transaction block. Public-chain blocks are still received to keep the advantage calculation current. |

The factory arms a newly-created attacker by calling `goToMonitoringState()`
immediately after construction. The attack therefore begins in `MONITORING`,
not `IDLE`, from the simulation's point of view.

---

## Configuration parameters

Set via `BitcoinNodeFactory` from `hiddenChainAttack.*` properties.

| Field | Type | Role |
|-------|------|------|
| `attackPower` | `float` | Hash power applied during `ATTACKING` (trials per time unit). Configured via `hiddenChainAttack.maliciousPowerRatio` relative to total honest hash power. |
| `honestPower` | `float` | The node's pre-attack hash power. Stored by `switchToAttackPower()` and restored on `completeAttack`. |
| `networkHonestPower` | `float` | Total honest-network hash power excluding this node, used by analysis code. |
| `targetTransaction` | `long` | The transaction ID to double-spend / orphan. `-1` = unset. |
| `startAdvantage` | `Integer` | Attack begins when `getCurrentAdvantage() <= startAdvantage`. Typically zero or negative (e.g. `-2` = "start when 2 blocks behind"). |
| `releaseAdvantage` | `Integer` | Chain released when `getCurrentAdvantage() >= releaseAdvantage`. Typically zero or positive. Zero reproduces Nakamoto probabilities. |
| `releaseConfirmations` | `Integer` | Alternative release trigger: release when the target has this many confirmations **and** advantage ≥ 1. Mutually exclusive with `releaseAdvantage`. |
| `attackTimeOut` | `long` | Simulation time at which an ongoing MONITORING or ATTACKING phase is forcibly terminated. `-1` = disabled. |

Runtime state:

| Field | Role |
|-------|------|
| `currentState` (`State`) | Current phase (IDLE / MONITORING / ATTACKING). |
| `hiddenChain` (`ArrayList<Block>`) | Blocks mined privately during ATTACKING. Cleared on `cancelAttack` / `completeAttack`. |
| `hiddenChainTip` (`Block`) | Latest hidden block; `null` when no chain is in progress. |

### `ReleaseStrategy` enum

`getReleaseStrategy()` infers which of two exclusive strategies is active
based on which release parameter was configured:

- **`ADVANTAGE`** — fires `completeAttack(time)` when
  `getCurrentAdvantage() >= releaseAdvantage`.
- **`CONFIRMATIONS`** — fires `completeAttack(time)` when the target
  transaction has reached `releaseConfirmations` confirmations **and** the
  attacker has at least 1 block of advantage.

The factory refuses construction if both (or neither) are configured.

---

## Wrapped honest behaviors

The attacker composes two honest instances instead of re-implementing protocol
logic:

| Field | Type | Used when |
|-------|------|-----------|
| `honestBehavior` | `HonestNodeBehavior` | `IDLE` and `MONITORING` — full honest protocol. Also used by `MONITORING`'s propagated-block handler to maintain an accurate public-chain view. |
| `altHonestBehavior` | `HonestNodeBehaviorLimited` | `ATTACKING` — public-chain events must be recorded (so advantage stays accurate) but must **not** clear the attacker's mining pool. `HonestNodeBehaviorLimited` skips the pool-extraction and mining-reconsideration side-effects for exactly this reason. |

See [honest.md](honest.md) for what `HonestNodeBehaviorLimited` loosens.

---

## Event handlers (per state)

The four strategy events from `NodeBehaviorStrategy` are each dispatched
differently in each of the three states.

### event_NodeReceivesClientTransaction(Transaction t, long time)

- **IDLE**:
    - \[HONEST\].event_NodeReceivesClientTransaction(t, time)
- **MONITORING**:
    - \[HONEST\].event_NodeReceivesClientTransaction(t, time) — target
      transactions are *not* censored; they flow through like any other. The
      attack triggers on the **block** that contains the target, not on the
      transaction itself.
    - \[REPORT\] "Attacker Receiving Target Transaction (Tx kept)" (when
      `reporter.reportAttackEvents` is on)
- **ATTACKING**:
    - \[check\] !inHiddenChain(t) && t.id != targetTransaction
    - \[ALT-HONEST\].event_NodeReceivesClientTransaction(t, time)
    - \[assert\] target transaction must not arrive in this state — throws
      `IllegalStateException` if it does.
- \[post\] if `attackTimedOut(time)` → `handleTimeOut(time)`

### event_NodeReceivesPropagatedTransaction(Transaction t, long time)

- **IDLE**:
    - \[HONEST\].event_NodeReceivesPropagatedTransaction(t, time)
- **MONITORING**:
    - \[check\] t.id != targetTransaction
    - \[HONEST\].event_NodeReceivesPropagatedTransaction(t, time) — target
      transaction IS ignored at gossip time, unlike the client-path case.
- **ATTACKING**:
    - \[check\] !inHiddenChain(t) && t.id != targetTransaction
    - \[ALT-HONEST\].event_NodeReceivesPropagatedTransaction(t, time)
- \[post\] if `attackTimedOut(time)` → `handleTimeOut(time)`

### event_NodeReceivesPropagatedContainer(ITxContainer c)

- **IDLE**:
    - \[HONEST\].event_NodeReceivesPropagatedContainer(c)
- **MONITORING**:
    - \[HONEST\].event_NodeReceivesPropagatedContainer(c)
    - \[check\] c.contains(targetTransaction) — if true:
        - \[assert\] hiddenChainTip == null && hiddenChain.isEmpty()
        - \[state\] hiddenChainTip = (Block) c.getParent()
        - \[private\] considerAttacking()
            - if getCurrentAdvantage() <= startAdvantage → startAttack()
- **ATTACKING**:
    - \[ALT-HONEST\].event_NodeReceivesPropagatedContainer(c) — public-chain
      update without pool disturbance
    - \[private\] evaluateAttackState(Simulation.currTime)

### event_NodeCompletesValidation(ITxContainer c, long time)

- **IDLE**:
    - \[HONEST\].event_NodeCompletesValidation(c, time)
- **MONITORING**:
    - \[HONEST\].event_NodeCompletesValidation(c, time)
- **ATTACKING**:
    - \[REPORT\] "Validating Malicious block" (when
      `reporter.reportAttackDetails` is on)
    - \[private\] nodeCompletesMaliciousValidation(c, time)
        - \[BLOCK\].validateBlock(miningPool, ..., "Node Completes Malicious Validation", difficulty, cycles)
        - \[NODE\].completeValidation(miningPool, time)
        - \[REPORT\] reportBlockEvent("Node Completes Malicious Validation")
        - \[state\] block.setParent(hiddenChainTip); block.setHeight(...)
        - \[state\] hiddenChainTip = block; hiddenChain.add(block)
        - \[post-validation\] stopMining → resetNextValidationEvent → removeFromPool(miningPool) → honestBehavior.reconstructMiningPool() → considerMining(time)
    - \[private\] evaluateAttackState(time)
        - ADVANTAGE strategy: advantage >= releaseAdvantage → completeAttack(time)
        - CONFIRMATIONS strategy: confirmations >= releaseConfirmations && advantage >= 1 → completeAttack(time)
- \[post\] if `attackTimedOut(time)` → `handleTimeOut(time)`

Note that `isWorthMining()` is also overridden: in `ATTACKING` the attacker
returns `true` whenever the mining pool is non-empty, dropping the
value-threshold heuristic so the attacker never voluntarily idles while
racing the public chain.

---

## Attack flow

Step-by-step from factory construction to chain release:

1. **Factory construction** — `BitcoinNodeFactory("Hidden Chain Attack", sim,
   honestNodeSet).createNewNode()` builds the node, wires
   `HiddenChainAttackBehavior` with its two honest delegates, and sets all
   `hiddenChainAttack.*` parameters. It ends with
   `strategy.goToMonitoringState()` so the attack is armed as soon as the
   simulation starts.
2. **Monitoring** — while in `MONITORING`, all events delegate to
   `honestBehavior` (with the target transaction filtered out of the gossip
   path only). The node looks like any honest peer.
3. **Target detected** — when a propagated block contains the target
   transaction, the handler sets `hiddenChainTip = block.getParent()` and
   calls `considerAttacking()`. If `getCurrentAdvantage() <= startAdvantage`,
   `startAttack()` runs.
4. **startAttack** — validates parameters (`validateAttackParameters()`),
   clears any residual `hiddenChain`, calls `switchToAttackPower()` (store
   `honestPower`, set `node.setHashPower(attackPower)`, reschedule any
   pending validation event), and sets `currentState = ATTACKING`.
5. **Private mining** — in `ATTACKING`, each `event_NodeCompletesValidation`
   is intercepted by `nodeCompletesMaliciousValidation(c, time)`: the block
   is parented onto `hiddenChainTip`, its height set to tip+1, appended to
   `hiddenChain`, and `hiddenChainTip` advanced. The post-validation cleanup
   runs so the attacker immediately starts working on the next hidden block.
6. **Public chain tracking** — in parallel, every propagated public block is
   still accepted via `altHonestBehavior` so the attacker's view of
   `structure.getLongestTip()` is current and `getCurrentAdvantage()` stays
   meaningful.
7. **Evaluate release** — after each hidden block is added,
   `evaluateAttackState(time)` checks the active `ReleaseStrategy`. If the
   threshold is met, `completeAttack(time)` runs.
8. **completeAttack** — transitions to `IDLE` **before** broadcasting so
   incoming replies are processed honestly; then iterates over `hiddenChain`
   and for each block calls `event_NodeReceivesPropagatedContainer(block)`
   locally (so the node records its own chain as received) and
   `node.broadcastContainer(block.clone(), time)` to push a copy to peers.
   Clears `hiddenChain` and `hiddenChainTip`, then `switchToNormalPower()`
   restores `honestPower`.
9. **Alternative endings**:
   - `cancelAttack()` — discards the hidden chain without broadcasting,
     resets state to `IDLE`. Used when continuing is no longer favorable.
   - `handleTimeOut(time)` — invoked whenever `attackTimedOut(time)` becomes
     true after any event; in `MONITORING` it calls `goToIdleState()`, in
     `ATTACKING` it calls `completeAttack(time)`.

---

## State transition methods

| Method | From | To | Notes |
|--------|------|----|-------|
| `goToMonitoringState()` | `IDLE` | `MONITORING` | Called by the factory to arm the attack. Throws if called from `ATTACKING` or already in `MONITORING`. |
| `goToIdleState()` | `MONITORING` | `IDLE` | Abandon monitoring without engaging. Throws if called from `ATTACKING` or already in `IDLE`. |
| `startAttack()` (private) | `MONITORING` | `ATTACKING` | Triggered by `considerAttacking()` when `advantage <= startAdvantage`. |
| `cancelAttack()` | `ATTACKING` | `IDLE` | Hidden chain discarded silently. |
| `completeAttack(long time)` | `ATTACKING` | `IDLE` | Broadcasts every hidden block before clearing. Restores honest hash power. |
| `handleTimeOut(long time)` (private) | any | `IDLE` | Dispatches to `goToIdleState()` or `completeAttack()` as appropriate. |

---

## Key inspection methods

| Method | Returns |
|--------|---------|
| `getCurrentAdvantage()` | `hiddenChainTip.height - structure.getLongestTip().height` (0 if no hidden tip). Throws if the node/structure/longest tip is null. |
| `getCurrentConfirmations()` | `structure.getConfirmations(targetTransaction)`, or `-1` if the target is not in the structure. Throws if `targetTransaction` is unset. |
| `inHiddenChain(Transaction t)` | Walks from `hiddenChainTip` to root; returns `true` if any block contains `t`. `false` when no attack is in progress. |
| `getReleaseStrategy()` | `ADVANTAGE` or `CONFIRMATIONS`; throws if both or neither are configured. |
| `getAttackState()` | The current `State`. |
| `printHiddenChain(String sep)` / `printHiddenChainAndContent(String sep)` | Debug-printers listing the hidden chain from tip to root. |


