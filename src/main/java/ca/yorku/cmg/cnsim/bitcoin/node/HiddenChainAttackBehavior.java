package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Debug;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import java.util.ArrayList;
import java.util.Objects;


/**
 * Implements a hidden chain attack strategy for Bitcoin network simulation.
 * <p>
 * A {@code HiddenChainAttackBehavior} wraps the honest node behavior and allows
 * an attacker to secretly mine on a private chain while potentially appearing honest
 * to the network. The attacker can then strategically release the hidden chain when
 * advantageous (e.g., to double-spend transactions or reorganize the blockchain).
 * </p>
 * <p>
 * This class manages three distinct operational states:
 * <ul>
 *   <li>{@link State#IDLE}: Normal honest operation, no attack in progress.</li>
 *   <li>{@link State#MONITORING}: Honest operation while observing network for
 *       attack opportunities.</li>
 *   <li>{@link State#ATTACKING}: Private chain mining in progress, blocks not
 *       yet released to network.</li>
 * </ul>
 * </p>
 * <p>
 * Attack initiation and chain release are controlled by configurable parameters:
 * {@code attackPower} (mining capability in trials per time unit),
 * {@code targetTransaction} (transaction ID to trigger attack onset),
 * {@code startAdvantage} (height disadvantage threshold that triggers attack),
 * and {@code releaseAdvantage} (height advantage threshold that triggers chain release).
 * </p>
 *
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 * @see HonestNodeBehavior
 * @see BitcoinNode
 * @see NodeBehaviorStrategy
 */
public class HiddenChainAttackBehavior extends DefaultNodeBehavior {


    // ================================
    // FIELDS
    // ================================

    /**
     * The wrapped honest node behavior strategy.
     * Delegates all honest operations while the attack is not in progress.
     */
    private HonestNodeBehavior honestBehavior;

    /**
     * The current operational state of the attack.
     * Controls whether and how the node participates in hidden chain mining.
     */
    private State currentState;

    /**
     * Attacker's mining power (in trials per unit of time). See unit documentation.
     * Value of -1 indicates uninitialized; must be set before attack initiation.
     */
    private float attackPower = -1;

    /**
     * Node's normal (pre-attack) mining power (in trials per unit of time).
     * Value of -1 indicates uninitialized. Stored when attack begins so the node
     * can revert to honest hash power after the attack completes or is cancelled.
     */
    private float honestPower = -1;

    /**
     * Target transaction ID for the attack (double-spend or orphan attempt).
     * In MONITORING state, the node waits for a block containing this transaction;
     * upon reception, the attack is triggered. Value of -1 indicates no target set.
     */
    private long targetTransaction = -1;

    /**
     * Advantage threshold that triggers attack start.
     * Typically zero or negative. For example, -2 means "start when 2 blocks behind".
     * The attack starts when {@code getAdvantage() <= startAdvantage}.
     * Must be set via {@link #setStartAdvantage(Integer)} before entering MONITORING state.
     */
    private Integer startAdvantage;

    /**
     * Advantage threshold that triggers hidden chain release.
     * Typically zero or positive. Zero reproduces Nakamoto attack probabilities.
     * The chain is released when {@code getAdvantage() >= releaseAdvantage}.
     * Must be set via {@link #setReleaseAdvantage(Integer)} before entering MONITORING state.
     */
    private Integer releaseAdvantage;

    /**
     * Private blockchain maintained by the attacker during ATTACKING state.
     * Contains blocks mined secretly that have not yet been released to the network.
     * Cleared when the attack completes or is cancelled.
     */
    private ArrayList<Block> hiddenChain;

    /**
     * Reference to the tip (latest block) of the hidden chain.
     * Null when no attack is in progress. Set to the parent of the target transaction's
     * block when the attack starts, then updated as each new hidden block is added via
     * {@link #nodeCompletesMaliciousValidation(ITxContainer, long)}.
     * Used to calculate the attacker's current advantage.
     */
    private Block hiddenChainTip = null;


    // ================================
    // CONSTRUCTORS
    // ================================

    /**
     * Constructs a hidden chain attack behavior and binds it to a specific
     * {@linkplain BitcoinNode} instance.
     * <p>
     * Initializes the behavior in IDLE state with a wrapped {@linkplain HonestNodeBehavior}
     * and empty hidden chain. Attack parameters ({@code startAdvantage}, {@code releaseAdvantage},
     * {@code attackPower}) must be configured via setters before entering MONITORING state.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires node != null;
     *   //@ requires beh != null;
     *   //@ ensures this.node == node;
     *   //@ ensures currentState == State.IDLE;
     *   //@ ensures hiddenChain != null && hiddenChain.isEmpty();
     *   //@ ensures honestBehavior != null;
     * }</pre>
     *
     * @param node the Bitcoin node to which this behavior is attached (must not be null)
     * @param beh  the honest behavior to wrap and delegate to (must not be null)
     * @throws NullPointerException if {@code node} is null
     */
    public HiddenChainAttackBehavior(BitcoinNode node, HonestNodeBehavior beh) {
        Objects.requireNonNull(node, "BitcoinNode cannot be null");
        this.node = node;
        this.honestBehavior = beh;
        this.currentState = State.IDLE;
        this.hiddenChain = new ArrayList<>();
    }


    // ================================
    // EVENT HANDLERS
    // ================================


    /**
     * Handles transactions received directly from clients.
     * <p>
     * Behavior depends on current attack state:
     * <ul>
     *   <li><b>IDLE:</b> Delegates to honest behavior; normal operation.</li>
     *   <li><b>MONITORING:</b> Accepts all transactions except the target transaction
     *       (target must arrive in a block to trigger attack).</li>
     *   <li><b>ATTACKING:</b> Rejects the target transaction (should not arrive directly);
     *       accepts others to maintain honest appearance.</li>
     * </ul>
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires t != null;
     *   //@ requires time >= 0;
     *   //@ requires currentState != null;
     * }</pre>
     *
     * @param t    the received transaction (must not be null)
     * @param time the current simulation time (must be non-negative)
     * @throws NullPointerException     if {@code t} is null
     * @throws IllegalArgumentException if {@code time} is negative
     * @throws IllegalStateException    if target transaction arrives during ATTACKING state
     */
    @Override
    public void event_NodeReceivesClientTransaction(Transaction t, long time) {
        Objects.requireNonNull(t, "Transaction cannot be null");
        if (time < 0) throw new IllegalArgumentException("Time cannot be negative: " + time);

        switch (currentState) {
        case IDLE:
            // In IDLE, accept all transactions normally
            honestBehavior.event_NodeReceivesClientTransaction(t, time);
            break;

        case MONITORING:
            // In MONITORING, ignore the target transaction (wait for it in a block)
            if (t.getID() != targetTransaction) {
                honestBehavior.event_NodeReceivesClientTransaction(t, time);
            }
            break;

        case ATTACKING:
            // In ATTACKING, target transaction should not arrive; other transactions accepted
            if (t.getID() == targetTransaction) {
                throw new IllegalStateException(
                    "Target transaction received during attack (should not occur): " + t.getID());
            }
            // Accept non-target transactions to maintain honest appearance
            honestBehavior.event_NodeReceivesClientTransaction(t, time);
            break;
        }
    }

    /**
     * Handles transactions propagated from other nodes in the network.
     * <p>
     * Behavior depends on current attack state:
     * <ul>
     *   <li><b>IDLE:</b> Delegates to honest behavior; normal propagation handling.</li>
     *   <li><b>MONITORING:</b> Ignores target transaction; forwards others normally.</li>
     *   <li><b>ATTACKING:</b> Maintains transaction pool on public chain while privately
     *       mining hidden chain; accepts all propagated transactions.</li>
     * </ul>
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires t != null;
     *   //@ requires time >= 0;
     *   //@ requires currentState != null;
     * }</pre>
     *
     * @param t    the propagated transaction (must not be null)
     * @param time the current simulation time (must be non-negative)
     * @throws NullPointerException     if {@code t} is null
     * @throws IllegalArgumentException if {@code time} is negative
     */
    @Override
    public void event_NodeReceivesPropagatedTransaction(Transaction t, long time) {
        Objects.requireNonNull(t, "Transaction cannot be null");
        if (time < 0) throw new IllegalArgumentException("Time cannot be negative: " + time);

        switch (currentState) {
        case IDLE:
            // In IDLE, handle all propagated transactions normally
            honestBehavior.event_NodeReceivesPropagatedTransaction(t, time);
            break;

        case MONITORING:
            // In MONITORING, ignore the target transaction; process others
            if (t.getID() != targetTransaction) {
                honestBehavior.event_NodeReceivesPropagatedTransaction(t, time);
            }
            break;

        case ATTACKING:
            // In ATTACKING, maintain honest pool for public chain mining
            /* if (t.getID() == targetTransaction) {
                throw new IllegalStateException(
                    "Target transaction propagated during attack (should not occur): " + t.getID());
            }*/
            honestBehavior.event_NodeReceivesPropagatedTransaction(t, time);
            break;
        }
    }

    /**
     * Handles reception of a propagated block from the network.
     * <p>
     * Behavior depends on current attack state:
     * <ul>
     *   <li><b>IDLE:</b> Delegates to honest behavior; normal block handling.</li>
     *   <li><b>MONITORING:</b> Delegates to honest behavior; if the block contains the
     *       target transaction, sets the hidden chain tip to the block's parent and
     *       considers initiating the attack via {@link #considerAttacking()}.</li>
     *   <li><b>ATTACKING:</b> Delegates to honest behavior to update the public chain;
     *       advantage is re-evaluated in {@link #event_NodeCompletesValidation(ITxContainer, long)}.</li>
     * </ul>
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires c != null;
     *   //@ requires c instanceof Block;
     *   //@ requires currentState != null;
     * }</pre>
     *
     * @param c the propagated block container (must not be null, must be a {@linkplain Block})
     * @throws NullPointerException     if {@code c} is null
     * @throws IllegalArgumentException if {@code c} is not a Block instance
     */
    @Override
    public void event_NodeReceivesPropagatedContainer(ITxContainer c) {
        Objects.requireNonNull(c, "Container cannot be null");
        if (!(c instanceof Block)) {
            throw new IllegalArgumentException(
                "Expected Block instance, got: " + c.getClass().getSimpleName());
        }

        switch (currentState) {
        case IDLE:
            // In IDLE, accept blocks normally
            honestBehavior.event_NodeReceivesPropagatedContainer(c);
            break;

        case MONITORING:
            // In MONITORING, accept blocks and check for target transaction trigger
            honestBehavior.event_NodeReceivesPropagatedContainer(c);
            if (c.contains(targetTransaction)) {
                if (hiddenChainTip != null) throw new IllegalStateException("hiddenChainTip must be null in MONITORING state.");
                if (!hiddenChain.isEmpty()) throw new IllegalStateException("hiddenChain must be empty in MONITORING state.");
                // The hidden chain starts from the parent of the block containing the target
                hiddenChainTip = (Block) ((Block) c).getParent();
                considerAttacking();
            }
            break;

        case ATTACKING:
            // In ATTACKING, update the public chain to track attacker's advantage
            honestBehavior.event_NodeReceivesPropagatedContainer(c);
            // Note: evaluateAttackState() is called in event_NodeCompletesValidation()
            break;
        }
    }

    /**
     * Handles completion of block validation by this node.
     * <p>
     * Behavior depends on attack state:
     * <ul>
     *   <li><b>IDLE:</b> Delegates to honest behavior; block added to public blockchain.</li>
     *   <li><b>MONITORING:</b> Delegates to honest behavior; block added to public blockchain.</li>
     *   <li><b>ATTACKING:</b> Block is intercepted and added to the hidden chain instead
     *       of the public blockchain. After addition, evaluates whether the release
     *       advantage threshold has been reached.</li>
     * </ul>
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires c != null;
     *   //@ requires c instanceof Block;
     *   //@ requires time >= 0;
     *   //@ requires currentState != null;
     * }</pre>
     *
     * @param c    the validated block (must not be null, must be a {@linkplain Block})
     * @param time the current simulation time (must be non-negative)
     * @throws NullPointerException     if {@code c} is null
     * @throws IllegalArgumentException if {@code c} is not a Block, or {@code time} is negative
     */
    @Override
    public void event_NodeCompletesValidation(ITxContainer c, long time) {
        Objects.requireNonNull(c, "Container cannot be null");
        if (!(c instanceof Block)) {
            throw new IllegalArgumentException(
                "Expected Block instance, got: " + c.getClass().getSimpleName());
        }
        if (time < 0) throw new IllegalArgumentException("Time cannot be negative: " + time);

        switch (currentState) {
        case IDLE:
            // In IDLE, delegate to honest validation
            honestBehavior.event_NodeCompletesValidation(c, time);
            break;

        case MONITORING:
            // In MONITORING, delegate to honest validation
            honestBehavior.event_NodeCompletesValidation(c, time);
            break;

        case ATTACKING:
            // In ATTACKING, intercept block and add to hidden chain
            nodeCompletesMaliciousValidation(c, time);
            // Check whether the release advantage threshold has been reached
            evaluateAttackState(time);
            break;
        }
    }

    @Override
    protected boolean isWorthMining() {
        return (node.getMiningPool().getCount() > 0);
    }


    // ================================
    // STATE TRANSITION METHODS
    // ================================

    /**
     * Transitions the attack state from IDLE to MONITORING.
     * <p>
     * Enables passive observation of the network for attack opportunities.
     * In MONITORING state, the node operates honestly while waiting for a block
     * containing the target transaction, which automatically triggers transition
     * to ATTACKING state. No blocks are mined secretly in MONITORING state.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.IDLE;
     *   //@ ensures currentState == State.MONITORING;
     * }</pre>
     *
     * @throws IllegalStateException if not currently in IDLE state
     */
    public void goToMonitoringState() {
        if (currentState == State.ATTACKING) {
            throw new IllegalStateException(
                "Cannot switch from ATTACKING to MONITORING state");
        } else if (currentState == State.MONITORING) {
            throw new IllegalStateException(
                "Already in MONITORING state");
        }
        currentState = State.MONITORING;
    }

    /**
     * Transitions the attack state from MONITORING back to IDLE.
     * <p>
     * Stops passive observation and returns to normal honest operation.
     * No parameters are reset; the node can re-enter MONITORING with the same configuration.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.MONITORING;
     *   //@ ensures currentState == State.IDLE;
     * }</pre>
     *
     * @throws IllegalStateException if not currently in MONITORING state
     */
    public void goToIdleState() {
        if (currentState == State.ATTACKING) {
            throw new IllegalStateException(
                "Cannot switch from ATTACKING to IDLE state");
        } else if (currentState == State.IDLE) {
            throw new IllegalStateException(
                "Already in IDLE state");
        }
        currentState = State.IDLE;
    }

    /**
     * Cancels an ongoing attack without releasing the hidden chain.
     * <p>
     * Discards all accumulated hidden blocks and returns to IDLE state without
     * broadcasting anything to the network. Called when attack conditions are no
     * longer favorable (e.g., the public chain has outpaced the hidden chain).
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.ATTACKING;
     *   //@ ensures currentState == State.IDLE;
     *   //@ ensures hiddenChain.isEmpty();
     *   //@ ensures hiddenChainTip == null;
     * }</pre>
     */
    public void cancelAttack() {
        hiddenChain.clear();
        hiddenChainTip = null;
        currentState = State.IDLE;
    }


    // ================================
    // PRIVATE ATTACK METHODS
    // ================================

    /**
     * Evaluates whether the attack can start and initiates it if conditions are met.
     * <p>
     * Called in MONITORING state when the target transaction appears in a block.
     * The attack starts when the attacker's current advantage has reached (or fallen
     * to) the configured start threshold: {@code getAdvantage() <= startAdvantage}.
     * </p>
     * <p>
     * Example: if {@code startAdvantage = -2}, the attack starts once the attacker
     * is 2 blocks behind the longest public chain tip.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.MONITORING;
     *   //@ requires startAdvantage != null;
     * }</pre>
     *
     * @throws IllegalStateException if called in IDLE or ATTACKING state, or if
     *                               {@code startAdvantage} is not configured
     */
    private void considerAttacking() {
        if (currentState == State.IDLE) {
            throw new IllegalStateException("Should not consider attacking in IDLE state");
        }
        if (currentState == State.ATTACKING) {
            throw new IllegalStateException(
                "Should not consider attacking in ATTACKING state; behavior is already in that mode");
        }
        if (startAdvantage == null) {
            throw new IllegalStateException(
                "startAdvantage must be configured before considering attack");
        }

        if (getAdvantage() <= startAdvantage) {
            startAttack();
        }
    }

    /**
     * Initiates the hidden chain attack.
     * <p>
     * Validates attack parameters, clears any previous hidden chain state, switches
     * the node to attack hash power, and transitions to ATTACKING state. From this
     * point, validated blocks are added to the hidden chain rather than the public
     * blockchain.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.MONITORING;
     *   //@ requires startAdvantage != null && releaseAdvantage != null;
     *   //@ requires attackPower > 0;
     *   //@ ensures currentState == State.ATTACKING;
     *   //@ ensures hiddenChain.isEmpty();
     * }</pre>
     *
     * @throws IllegalStateException if attack parameters are invalid or not configured
     */
    private void startAttack() {
        validateAttackParameters();
        hiddenChain.clear();
        switchToAttackPower();
        currentState = State.ATTACKING;
    }

    /**
     * Stores the node's current hash power and switches to attack hash power.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState != State.ATTACKING;
     * }</pre>
     *
     * @throws IllegalStateException if already in ATTACKING state
     */
    private void switchToAttackPower() {
        if (currentState == State.ATTACKING) {
            throw new IllegalStateException("Switching to attack power while already in ATTACKING state");
        }
        honestPower = node.getHashPower();
        node.setHashPower(attackPower);
    }

    /**
     * Restores the node's hash power to its pre-attack value.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires honestPower != -1;
     * }</pre>
     *
     * @throws IllegalStateException if {@code honestPower} was never stored (attack never started)
     */
    private void switchToNormalPower() {
        if (honestPower == -1) {
            throw new IllegalStateException("honestPower uninitialized");
        }
        node.setHashPower(honestPower);
    }

    /**
     * Adds a validated block to the hidden chain during ATTACKING state.
     * <p>
     * Intercepts the block from the honest validation path, records validation
     * metadata, sets the block's parent to the current hidden chain tip, and
     * appends it to the hidden chain. Updates the hidden chain tip and restarts
     * mining on the next hidden block.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires c != null && c instanceof Block;
     *   //@ requires time >= 0;
     *   //@ requires currentState == State.ATTACKING;
     *   //@ ensures hiddenChainTip == c;
     *   //@ ensures hiddenChain.contains(c);
     *   //@ ensures ((Block) c).getParent() == old(hiddenChainTip);
     * }</pre>
     *
     * @param c    the block to add to the hidden chain (must not be null)
     * @param time the current simulation time
     */
    private void nodeCompletesMaliciousValidation(ITxContainer c, long time) {
        Objects.requireNonNull(c, "Container cannot be null");
        if (!(c instanceof Block)) {
            throw new IllegalArgumentException(
                "Expected Block instance, got: " + c.getClass().getSimpleName());
        }
        if (time < 0) throw new IllegalArgumentException("Time cannot be negative: " + time);

        Block block = (Block) c;

        // Add validation metadata to the block
        block.validateBlock(node.getMiningPool(),
                Simulation.currTime,
                System.currentTimeMillis() - Simulation.sysStartTime,
                node.getID(),
                "Node Completes Malicious Validation",
                node.getOperatingDifficulty(),
                node.getProspectiveCycles());

        // Run default post-validation actions (cycle stats etc.)
        node.completeValidation(node.getMiningPool(), time);

        // Report the validation event
        BitcoinReporter.reportBlockEvent(
                Simulation.currentSimulationID,
                block.getSimTime_validation(),
                block.getSysTime_validation(),
                block.getValidationNodeID(),
                block.getID(), ((block.getParent() == null) ? -1 : block.getParent().getID()),
                block.getHeight(),
                block.printIDs(";"),
                "Node Completes Validation",
                block.getValidationDifficulty(),
                block.getValidationCycles());

        // Link block to current hidden chain tip
        block.setParent(hiddenChainTip);

        // Height is 1 if first hidden block (tip was null), otherwise tip height + 1
        int blockHeight = (hiddenChainTip == null) ? 1 : hiddenChainTip.getHeight() + 1;
        block.setHeight(blockHeight);

        // Advance the hidden chain tip
        hiddenChainTip = block;
        hiddenChain.add(block);

        processPostValidationActivities(time);
    }

    /**
     * Performs cleanup and re-initialization steps following hidden block validation.
     * <p>
     * Stops mining, resets the next validation event, removes validated transactions
     * from the mining pool, reconstructs the pool, and reconsiders whether to continue
     * mining the next hidden block.
     * </p>
     *
     * @param time the current simulation time
     */
    protected void processPostValidationActivities(long time) {
        // Stop mining for now
        node.stopMining();
        // Reset the next validation event
        node.resetNextValidationEvent();
        // Remove validated transactions from the mining pool
        node.removeFromPool(node.getMiningPool());
        // Reconstruct the mining pool with remaining transactions
        honestBehavior.reconstructMiningPool();
        // Reconsider whether to mine the next hidden block
        considerMining(time);
    }

    /**
     * Evaluates whether the release advantage threshold has been reached.
     * <p>
     * Called after each hidden block is added. If the advantage is sufficient
     * ({@code getAdvantage() >= releaseAdvantage}), the hidden chain is released.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.ATTACKING;
     * }</pre>
     *
     * @param time the current simulation time for chain release broadcast
     * @throws IllegalStateException if not in ATTACKING state
     */
    private void evaluateAttackState(long time) {
        if (currentState != State.ATTACKING) {
            throw new IllegalStateException(
                "evaluateAttackState() can only be called in ATTACKING state, currently in: " + currentState);
        }

        if (getAdvantage() >= releaseAdvantage) {
            releaseChain(time);
        }
    }

    /**
     * Releases the accumulated hidden chain to the network.
     * <p>
     * Transitions to IDLE state, then broadcasts each block in the hidden chain
     * to the network (both receiving it locally and cloning it for propagation).
     * Upon completion, calls {@link #completeAttack()} to finalize cleanup.
     * </p>
     * <p>
     * The released chain may reorganize the public blockchain if it is longer than
     * the current main chain.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.ATTACKING;
     *   //@ requires !hiddenChain.isEmpty();
     *   //@ ensures currentState == State.IDLE;
     *   //@ ensures hiddenChain.isEmpty();
     *   //@ ensures hiddenChainTip == null;
     * }</pre>
     *
     * @param time the current simulation time for broadcast timestamp
     * @throws IllegalStateException if not in ATTACKING state
     */
    private void releaseChain(long time) {
        if (currentState != State.ATTACKING) {
            throw new IllegalStateException(
                "Cannot release chain outside of ATTACKING state, currently in: " + currentState);
        }

        // Transition to IDLE before broadcasting so received blocks are processed honestly
        currentState = State.IDLE;

        for (Block block : hiddenChain) {
            // Process the block locally as if received from another node
            event_NodeReceivesPropagatedContainer(block);
            // Propagate a clone to the rest of the network
            try {
                node.broadcastContainer((ITxContainer) block.clone(), time);
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }

        completeAttack();
    }

    /**
     * Finalizes the attack after chain release.
     * <p>
     * Clears hidden chain state, resets the hidden chain tip, and restores the
     * node's normal hash power. Called by {@link #releaseChain(long)} after all
     * hidden blocks have been broadcast.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures hiddenChain.isEmpty();
     *   //@ ensures hiddenChainTip == null;
     * }</pre>
     */
    private void completeAttack() {
        hiddenChain.clear();
        hiddenChainTip = null;
        switchToNormalPower();
    }

    /**
     * Validates that all required attack parameters are properly configured.
     * <p>
     * Checks:
     * <ul>
     *   <li>{@code attackPower > 0}: Mining capability must be positive.</li>
     *   <li>{@code startAdvantage != null}: Start threshold must be set.</li>
     *   <li>{@code releaseAdvantage != null}: Release threshold must be set.</li>
     *   <li>{@code releaseAdvantage >= startAdvantage}: Release should be achievable.</li>
     * </ul>
     * Warnings are logged for unusual but technically valid configurations.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires attackPower > 0;
     *   //@ requires startAdvantage != null;
     *   //@ requires releaseAdvantage != null;
     * }</pre>
     *
     * @throws IllegalStateException if any required parameter is not set or is invalid
     */
    private void validateAttackParameters() {
        if (attackPower <= 0) {
            throw new IllegalStateException(
                "Attack power validation failed: must be positive, got " + attackPower);
        }

        if (startAdvantage == null) {
            throw new IllegalStateException(
                "Start advantage validation failed: must be configured before attack initiation");
        }
        if (startAdvantage > 0) {
            String msg = "Attack starting with positive advantage (ahead): " + startAdvantage;
            Debug.p(1, "WARNING: " + msg);
            BitcoinReporter.addErrorEntry("WARNING: HiddenChainAttackBehavior " + msg);
        }

        if (releaseAdvantage == null) {
            throw new IllegalStateException(
                "Release advantage validation failed: must be configured before attack initiation");
        }
        if (releaseAdvantage < 0) {
            String msg = "Attack releasing with negative advantage: " + releaseAdvantage;
            Debug.p(1, "WARNING: " + msg);
            BitcoinReporter.addErrorEntry("WARNING: HiddenChainAttackBehavior " + msg);
        }

        if (releaseAdvantage < startAdvantage) {
            String msg = "Release advantage (" + releaseAdvantage +
                         ") less than start advantage (" + startAdvantage +
                         "). Attack may not progress.";
            Debug.p(1, "WARNING: " + msg);
            BitcoinReporter.addErrorEntry("WARNING: HiddenChainAttackBehavior " + msg);
        }
    }


    // ================================
    // GETTERS AND SETTERS
    // ================================

    /**
     * Returns the current operational state of the attack.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures \result != null;
     *   //@ ensures \result == currentState;
     * }</pre>
     *
     * @return the current {@linkplain State}
     */
    public State getAttackState() {
        return currentState;
    }

    /**
     * Returns the current advantage (hidden chain height minus public chain height).
     * <p>
     * Positive values indicate the hidden chain is ahead; zero or negative means it
     * is behind. This value determines whether to release the hidden chain.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires node != null;
     *   //@ requires node.getStructure() != null;
     *   //@ requires node.getStructure().getLongestTip() != null;
     *   //@ ensures \result == (hiddenChainTip == null ? 0 : hiddenChainTip.height)
     *   //                       - longestTip.height;
     * }</pre>
     *
     * @return the advantage in blocks (can be negative if hidden chain is behind)
     * @throws IllegalStateException if node, blockchain structure, or longest tip is null
     */
    public int getAdvantage() {
        if (node == null) {
            throw new IllegalStateException(
                "Cannot calculate advantage: BitcoinNode reference is null");
        }
        if (node.getStructure() == null) {
            throw new IllegalStateException(
                "Cannot calculate advantage: blockchain structure is null in node " + node.getID());
        }

        Block longestTip = node.getStructure().getLongestTip();
        if (longestTip == null) {
            throw new IllegalStateException(
                "Cannot calculate advantage: longest tip is null in node " + node.getID() +
                " (blockchain may be uninitialized)");
        }

        int hiddenHeight = (hiddenChainTip == null) ? 0 : hiddenChainTip.getHeight();
        int publicHeight = longestTip.getHeight();
        return hiddenHeight - publicHeight;
    }

    /**
     * Sets the attacker's mining power.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires power > 0;
     *   //@ ensures this.attackPower == power;
     * }</pre>
     *
     * @param power the mining power in trials per time unit (must be positive)
     * @throws IllegalArgumentException if {@code power <= 0}
     */
    public void setAttackPower(float power) {
        if (power <= 0) {
            throw new IllegalArgumentException(
                "Attack power must be positive, got: " + power);
        }
        this.attackPower = power;
    }

    /**
     * Returns the attacker's mining power.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures \result == this.attackPower;
     * }</pre>
     *
     * @return the mining power in trials per time unit
     */
    public float getAttackPower() {
        return attackPower;
    }

    /**
     * Sets the target transaction for the attack.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires txID >= -1;
     *   //@ ensures this.targetTransaction == txID;
     * }</pre>
     *
     * @param txID the transaction ID to target (-1 for no specific target)
     */
    public void setTargetTransaction(long txID) {
        this.targetTransaction = txID;
    }

    /**
     * Returns the target transaction ID.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures \result == this.targetTransaction;
     * }</pre>
     *
     * @return the target transaction ID (-1 if no target set)
     */
    public long getTargetTransaction() {
        return targetTransaction;
    }

    /**
     * Sets the advantage threshold that triggers attack start.
     * <p>
     * Typically zero or negative. For example, -2 means "start when 2 blocks behind".
     * The attack starts when {@code getAdvantage() <= startAdvantage}.
     * Must be set before entering MONITORING state.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures this.startAdvantage == startAdv;
     * }</pre>
     *
     * @param startAdv the start advantage threshold (typically zero or negative)
     */
    public void setStartAdvantage(Integer startAdv) {
        this.startAdvantage = startAdv;
    }

    /**
     * Returns the start advantage threshold.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures \result == this.startAdvantage;
     * }</pre>
     *
     * @return the start advantage threshold (null if not set)
     */
    public Integer getStartAdvantage() {
        return startAdvantage;
    }

    /**
     * Sets the advantage threshold that triggers hidden chain release.
     * <p>
     * Typically zero or positive. For example, 2 means "release when 2 blocks ahead".
     * The chain is released when {@code getAdvantage() >= releaseAdvantage}.
     * Zero reproduces Nakamoto attack probabilities.
     * Must be set before entering MONITORING state.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures this.releaseAdvantage == relAdv;
     * }</pre>
     *
     * @param relAdv the release advantage threshold (typically zero or positive)
     */
    public void setReleaseAdvantage(Integer relAdv) {
        this.releaseAdvantage = relAdv;
    }

    /**
     * Returns the release advantage threshold.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures \result == this.releaseAdvantage;
     * }</pre>
     *
     * @return the release advantage threshold (null if not set)
     */
    public Integer getReleaseAdvantage() {
        return releaseAdvantage;
    }

    /**
     * Returns the wrapped honest behavior strategy.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures \result == this.honestBehavior;
     * }</pre>
     *
     * @return the {@linkplain HonestNodeBehavior} instance
     */
    public HonestNodeBehavior getHonestBehavior() {
        return honestBehavior;
    }

    /**
     * Sets the wrapped honest behavior strategy.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures this.honestBehavior == honestBehavior;
     * }</pre>
     *
     * @param honestBehavior the honest behavior to wrap (must not be null)
     */
    public void setHonestBehavior(HonestNodeBehavior honestBehavior) {
        this.honestBehavior = honestBehavior;
    }


    // ================================
    // DEBUG / PRINT
    // ================================

    /**
     * Returns a string representation of the attack behavior state.
     *
     * <p><b>Format:</b></p>
     * <pre>
     * HiddenChainAttackBehavior{
     *     currentState=ATTACKING,
     *     attackPower=1.5,
     *     targetTransaction=42,
     *     startAdvantage=-2,
     *     releaseAdvantage=2,
     *     advantage=1,
     *     hiddenChainLength=5
     * }
     * </pre>
     *
     * @return formatted string with attack state, configuration, and current metrics
     */
    @Override
    public String toString() {
        return "HiddenChainAttackBehavior{" +
                "currentState=" + currentState +
                ", attackPower=" + attackPower +
                ", targetTransaction=" + targetTransaction +
                ", startAdvantage=" + startAdvantage +
                ", releaseAdvantage=" + releaseAdvantage +
                ", advantage=" + getAdvantage() +
                ", hiddenChainLength=" + hiddenChain.size() +
                '}';
    }

    /**
     * Returns a comma-separated string of block IDs in the hidden chain,
     * ordered from the chain root to the tip.
     * Returns an empty string if {@code hiddenChainTip} is null.
     *
     * @return block IDs from root to tip, e.g. {@code "5,6,7"}, or {@code ""} if empty
     */
    public String printHiddenChain() {
        if (hiddenChainTip == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        Block current = hiddenChainTip;

        while (current != null) {
            if (result.length() > 0) {
                result.append(",");
            }
            result.append(current.getID());
            current = (Block) current.getParent();
        }

        return result.toString();
    }


    // ================================
    // INNER ENUMS
    // ================================

    /**
     * Enumeration of attack operational states.
     */
    public enum State {
        /** Normal honest operation; no attack in progress. */
        IDLE,

        /** Monitoring the network for attack trigger; node operates honestly. */
        MONITORING,

        /** Actively mining a hidden chain while maintaining a public honest appearance. */
        ATTACKING
    }
}
