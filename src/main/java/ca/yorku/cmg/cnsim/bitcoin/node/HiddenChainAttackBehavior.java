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
     * Enumeration of possible attack states.
     * Controls whether and how the node participates in hidden chain mining.
     */
    private State currentState;

    /**
     * Attacker's mining power (in trials per unit of time). See unit documentation.
     * Value of -1 indicates uninitialized; must be set before attack initiation.
     * Used in attack parameter validation.
     */
    private float attackPower = -1;

    /**
     * Attacker normal/initial mining power (in trials per unit of time). See unit documentation.
     * Value of -1 indicates uninitialized. Serves as a temporary storage of initial hashpower 
     * prior to commencement of the attack, so that the node can revert to it after the attack is over.  
     */
    private float honestPower = -1;
    
    
    /**
     * Target transaction ID for the attack (double-spend or orphan attempt).
     * In MONITORING state, the attacker waits for a block containing this transaction.
     * When received, the attack is triggered. Value of -1 indicates no target set.
     */
    private long targetTransaction = -1;

    /**
     * Attacker's initial disadvantage (blocks behind main chain) to trigger attack start.
     * Typically a negative number. For example, -2 means "start when 2 blocks behind".
     * Must be set via {@link #setStartAdvantage(Integer)} before entering MONITORING state.
     */
    private Integer startAdvantage;

    /**
     * Attacker's advantage threshold (blocks ahead) that triggers hidden chain release.
     * Typically zero or positive. Zero reproduces Nakamoto attack probabilities.
     * When {@code getAdvantage() >= releaseAdvantage}, the hidden chain is released.
     */
    private Integer releaseAdvantage;

    /**
     * Private blockchain maintained by the attacker.
     * Contains blocks mined during ATTACKING state that have not yet been released
     * to the network. This chain is separate from and typically ahead of the main
     * blockchain. Cleared when attack completes or transitions to IDLE.
     */
    private ArrayList<Block> hiddenChain;

    /**
     * Reference to the tip (latest block) of the hidden chain.
     * Initially null; takes parent reference from blocks added during attack via
     * {@link #nodeCompletesMaliciousValidation(ITxContainer, long)}.
     * Updated as new blocks are added to hidden chain during mining.
     * Used to calculate advantage and track hidden chain height.
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
     *   //@ ensures this.node == node;
     *   //@ ensures currentState == State.IDLE;
     *   //@ ensures hiddenChain != null && hiddenChain.isEmpty();
     *   //@ ensures honestBehavior != null;
     * }</pre>
     *
     * @param node the Bitcoin node to which this behavior is attached
     * @throws IllegalArgumentException if {@code node} is null
     */
    public HiddenChainAttackBehavior(BitcoinNode node, HonestNodeBehavior beh) {
        Objects.requireNonNull(node, "BitcoinNode cannot be null");
        this.node = node;
        this.honestBehavior = beh;
        this.currentState = State.IDLE;
        this.hiddenChain = new ArrayList<>();
    }


    // ================================
    // MAIN PUBLIC METHODS
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
     *   //@ requires t != null && time >= 0;
     *   //@ requires currentState != null;
     *   //@ ensures (currentState == MONITORING && t.ID == targetTransaction) ==> (tx ignored);
     *   //@ ensures (currentState == ATTACKING && t.ID == targetTransaction) ==> (exception);
     * }</pre>
     *
     * @param t    the received transaction (must not be null)
     * @param time the current simulation time (must be non-negative)
     * @throws IllegalArgumentException if {@code t} is null or {@code time} is negative
     * @throws IllegalStateException if target transaction arrives during ATTACKING state
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
     *       mining hidden chain; rejects target transaction.</li>
     * </ul>
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires t != null && time >= 0;
     *   //@ requires currentState != null;
     *   //@ ensures (currentState == MONITORING && t.ID == targetTransaction) ==> (tx ignored);
     *   //@ ensures (currentState == ATTACKING && t.ID == targetTransaction) ==> (exception);
     * }</pre>
     *
     * @param t    the propagated transaction (must not be null)
     * @param time the current simulation time (must be non-negative)
     * @throws IllegalArgumentException if {@code t} is null or {@code time} is negative
     * @throws IllegalStateException if target transaction arrives during ATTACKING state
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
            // In ATTACKING, target transaction should not arrive; maintain honest pool
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
     *   <li><b>MONITORING:</b> Delegates to honest behavior; if block contains target
     *       transaction, considers initiating attack via {@link #considerAttacking()}.</li>
     *   <li><b>ATTACKING:</b> Delegates to honest behavior for public chain update;
     *       tracks public chain length to evaluate if attack remains viable.</li>
     * </ul>
     * Attack initiation is triggered by target transaction appearing in any block.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires c != null && c instanceof Block;
     *   //@ requires currentState != null;
     *   //@ ensures (currentState == MONITORING && c.contains(targetTransaction)) ==> (currentState == ATTACKING);
     * }</pre>
     *
     * @param c the propagated block container (must not be null, must be a Block)
     * @throws IllegalArgumentException if {@code c} is null or not a Block instance
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
                // Target transaction found in block - initiate attack
            	if (hiddenChainTip != null) throw new IllegalStateException("hiddenChainTip must  be null at MONITORING state.");
            	if (!hiddenChain.isEmpty()) throw new IllegalStateException("hiddenChain must be empty at MONITORING state.");

            	//The hidden chain tip will now point to the parent of the target block.
            	hiddenChainTip = (Block) ((Block) c).getParent();
            	
            	//See if it is time to start the attack and start it.
                considerAttacking();
            }
            break;

        case ATTACKING:
            // In ATTACKING, update public chain to monitor advantage
            honestBehavior.event_NodeReceivesPropagatedContainer(c);
            // Note: evaluateAttackState() called in event_NodeCompletesValidation()
            break;
        }
    }

    /**
     * Evaluates whether attack conditions are favorable and initiates attack if ready.
     * <p>
     * Called in MONITORING state when the target transaction appears in a block.
     * Checks if the attacker's current advantage has reached (or fallen below, since
     * {@code startAdvantage} is typically negative) the configured start threshold.
     * </p>
     * <p>
     * Attack triggers when: {@code getAdvantage() <= startAdvantage}
     * </p>
     * <p>
     * Example: If {@code startAdvantage = -2}, attack starts once attacker is 2 blocks
     * behind the longest public chain tip. A more negative threshold delays attack start.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.MONITORING;
     *   //@ requires startAdvantage != null;
     * }</pre>
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
     * Handles completion of block validation by this node.
     * <p>
     * Behavior depends on attack state:
     * <ul>
     *   <li><b>IDLE:</b> Delegates to honest behavior; block added to public blockchain.</li>
     *   <li><b>MONITORING:</b> Delegates to honest behavior; block added to public blockchain.</li>
     *   <li><b>ATTACKING:</b> Block added to hidden chain (not public blockchain); after
     *       addition, evaluates if advantage threshold reached to trigger release.</li>
     * </ul>
     * In ATTACKING state, the validated block is intercepted and added to the hidden
     * chain instead of being propagated. The public blockchain is not updated.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires c != null && c instanceof Block && time >= 0;
     *   //@ requires currentState != null;
     *   //@ ensures (currentState == IDLE || MONITORING) ==> (public chain updated);
     *   //@ ensures (currentState == ATTACKING) ==> (hiddenChain.size() > old(hiddenChain.size()));
     *   //@ ensures (currentState == ATTACKING && advantage >= releaseAdvantage) ==> (chain released);
     * }</pre>
     *
     * @param c    the validated block (must not be null, must be a Block)
     * @param time the current simulation time (must be non-negative)
     * @throws IllegalArgumentException if preconditions violated
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
            // Check if release advantage reached
            evaluateAttackState(time);
            break;
        }

        
    }
    
    @Override
    protected boolean isWorthMining() {
    	return(node.getMiningPool().getCount() > 0);
    }
    

	/**
     * Adds a validated block to the hidden chain during attack.
     * <p>
     * Intercepts the block from honest validation and integrates it into the hidden
     * chain structure. Updates the hidden chain tip and tracks the growing chain length.
     * The block's parent is set to the current hidden chain tip to maintain continuity.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires c != null && c instanceof Block && time >= 0;
     *   //@ requires currentState == ATTACKING;
     *   //@ ensures hiddenChainTip == c;
     *   //@ ensures hiddenChain.contains(c);
     *   //@ ensures c.getParent() == old(hiddenChainTip);
     * }</pre>
     *
     * @param c    the block to add to hidden chain (must not be null)
     * @param time the simulation time (validation parameter, not used in this method)
     */
    private void nodeCompletesMaliciousValidation(ITxContainer c, long time) {
        Objects.requireNonNull(c, "Container cannot be null");
        if (!(c instanceof Block)) {
            throw new IllegalArgumentException(
                "Expected Block instance, got: " + c.getClass().getSimpleName());
        }
        if (time < 0) throw new IllegalArgumentException("Time cannot be negative: " + time);

        System.err.println("About to complete valdation of " + c.getID() + " with " + c.printIDs(","));
        System.err.println("Meanwhile, parent is " + ((c == null)?"null": c.getID()));
        
        Block block = (Block) c;

        
        //Add validation information to the block.
        block.validateBlock(node.getMiningPool(),
                Simulation.currTime,
                System.currentTimeMillis() - Simulation.sysStartTime,
                node.getID(),
                "Node Completes Malicious Validation",
                node.getOperatingDifficulty(),
                node.getProspectiveCycles());

        //Run default actions (mostly cycle stats)
        node.completeValidation(node.getMiningPool(), time);

        //Report the validation event
        BitcoinReporter.reportBlockEvent(
				Simulation.currentSimulationID,
        		block.getSimTime_validation(),
        		block.getSysTime_validation(),
        		block.getValidationNodeID(),
                block.getID(),((block.getParent() == null) ? -1 : block.getParent().getID()),
                block.getHeight(),
                block.printIDs(";"),
                "Node Completes Validation",
                block.getValidationDifficulty(),
                block.getValidationCycles());
        
        // Set block's parent to current hidden chain tip
        block.setParent(hiddenChainTip);

        // Calculate block height: 1 if tip is null (genesis), otherwise tip height + 1
        int blockHeight = (hiddenChainTip == null) ? 1 : hiddenChainTip.getHeight() + 1;
        block.setHeight(blockHeight);

        // Update tip and add to chain
        hiddenChainTip = block;
        hiddenChain.add(block);
        System.err.println("Adding " + block.getID() + " to point to " + block.getParent().getID());
        
        processPostValidationActivities(time);
        
    }


	protected void processPostValidationActivities(long time) {
        //Stop mining for now.
        node.stopMining();
        //Reset the next validation event.
        node.resetNextValidationEvent();
        //Remove the block's transactions from the mining pool.
        node.removeFromPool(node.getMiningPool());
        //Reconstruct mining pool, with whatever other transactions are there.
        honestBehavior.reconstructMiningPool();
        //Consider if it is worth mining.
        considerMining(time);
	}


	/**
     * Transitions the attack state from IDLE to MONITORING.
     * <p>
     * Enables passive observation of network conditions for attack opportunities.
     * In MONITORING state, the node waits for a block containing the target
     * transaction, which triggers automatic transition to ATTACKING state.
     * No blocks are mined or hidden in MONITORING state.
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
                "Cannot force switch from ATTACKING to MONITORING state");
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
     * No parameters are reset; can re-enter MONITORING with same configuration.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.MONITORING;
     *   //@ ensures currentState == State.IDLE;
     * }</pre>
     *
     * @throws IllegalStateException if not currently in MONITORING state or already IDLE
     */
    public void goToIdleState() {
        if (currentState == State.ATTACKING) {
            throw new IllegalStateException(
                "Cannot force switch from ATTACKING to IDLE state");
        } else if (currentState == State.IDLE) {
            throw new IllegalStateException(
                "Already in IDLE state");
        }
        currentState = State.IDLE;
    }

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
     * Returns the current advantage (hidden chain length minus public chain length).
     * <p>
     * Calculates the relative advantage as: {@code hiddenChainHeight - publicChainHeight}.
     * Positive values indicate hidden chain is ahead; zero or negative means it is behind.
     * This value determines whether the hidden chain should be released via {@code releaseAdvantage}.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires node != null;
     *   //@ requires node.getStructure() != null;
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
        int publicHeight = (longestTip == null) ? 0 : longestTip.getHeight();
        return hiddenHeight - publicHeight;
    }


    // ================================
    // MAIN PRIVATE METHODS
    // ================================

    /**
     * Evaluates whether the attack advantage threshold has been reached for release.
     * <p>
     * Called after each block completion during ATTACKING state. If the hidden chain
     * advantage reaches or exceeds the configured {@code releaseAdvantage} threshold,
     * the hidden chain is automatically released via {@link #releaseChain(long)}.
     * </p>
     * <p>
     * The advantage calculation is: {@code getAdvantage() >= releaseAdvantage}.
     * For example, if {@code releaseAdvantage = 2}, chain is released once hidden
     * chain is 2+ blocks ahead of public chain.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.ATTACKING;
     *   //@ ensures (getAdvantage() >= releaseAdvantage) ==> (chain released);
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
        	System.err.println("Release Chain");
            releaseChain(time);
            
        }
    }
    
    /**
     * Initiates the hidden chain attack.
     * <p>
     * Called when the target transaction appears in a block during MONITORING state,
     * or explicitly to begin attack. Transitions to ATTACKING state and sets the
     * hidden chain tip to the public blockchain's current tip. This method validates
     * attack parameters ({@code startAdvantage}, {@code releaseAdvantage}, {@code attackPower})
     * and clears any previous hidden chain state.
     * </p>
     * <p>
     * The node will continue honest operations on the public chain while secretly
     * mining blocks on the hidden chain, which begins from the current network state.
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

    private void switchToAttackPower() {
    	if (currentState == State.ATTACKING) {
    		throw new IllegalStateException("switching to attack power, while already being in Attack stage");
    	}
    	honestPower = node.getHashPower();
		node.setHashPower(attackPower);
	}
    
	/**
     * Releases the accumulated hidden chain to the network.
     * <p>
     * Broadcasts all blocks accumulated in the hidden chain to the network. Each block
     * is cloned before broadcast to prevent external modification. Upon successful
     * broadcast, the attack is completed and the node transitions back to IDLE state.
     * </p>
     * <p>
     * The released chain may reorganize the public blockchain if it is longer than
     * the current main chain. Nodes receiving these blocks will integrate them according
     * to their consensus rules.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires currentState == State.ATTACKING;
     *   //@ requires hiddenChain != null && hiddenChain.size() > 0;
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

        //Back to normal state
        currentState = State.IDLE;
        // Broadcast each hidden block to the network
        
        for (Block block : hiddenChain) {
        	//Pretend this came from a different node:
        	event_NodeReceivesPropagatedContainer(block);
        	//... but also propagate to others
            try {
                node.broadcastContainer((ITxContainer) block.clone(), time);
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }

        //Add the hidden chain to your blockchain
        
        completeAttack();
    }

    /**
     * Finalizes the attack and transitions to IDLE state.
     * <p>
     * Clears the hidden chain state and resets attack tracking. This method is called
     * after the hidden chain has been released to the network via {@link #releaseChain(long)}.
     * The node returns to normal IDLE state and can be configured for a new attack cycle
     * if needed.
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
    private void completeAttack() {
        hiddenChain.clear();
        hiddenChainTip = null;
        switchToNormalPower();
    }


    
    private void switchToNormalPower() {
    	if (honestPower == -1) {
    		throw new IllegalStateException("honestPower uninitialized");
    	}
		node.setHashPower(honestPower);
	}


	/**
     * Cancels an ongoing attack without releasing the hidden chain.
     * <p>
     * Discards all accumulated blocks in the hidden chain and returns to IDLE state
     * without broadcasting any data to the network. This method is called when attack
     * conditions are no longer favorable (e.g., public chain outpaces hidden chain).
     * Call this to abandon an attack silently.
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
    // HELPER METHODS
    // ================================

    // (None currently - all helper logic is in main private methods)


    // ================================
    // DEBUG/PRINT/TOSTRING METHODS
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


    // ================================
    // SETTERS AND GETTERS
    // ================================

    /**
     * Sets the attacker's mining power relative to network participants.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires power > 0;
     *   //@ ensures this.attackPower == power;
     * }</pre>
     *
     * @param power the mining power multiplier (must be positive)
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
     *   //@ ensures \result > 0;
     *   //@ ensures \result == this.attackPower;
     * }</pre>
     *
     * @return the mining power multiplier
     */
    public float getAttackPower() {
        return attackPower;
    }

    /**
     * Sets the target transaction for the attack (double-spend or orphan).
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
     * Returns the target transaction for the attack.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures \result == this.targetTransaction;
     * }</pre>
     *
     * @return the target transaction ID (-1 if no target)
     */
    public long getTargetTransaction() {
        return targetTransaction;
    }

    /**
     * Sets the initial disadvantage threshold (blocks behind) that triggers attack.
     * <p>
     * This is the node's starting position relative to the network. Typically a
     * negative number (e.g., -2 means "start 2 blocks behind"). Must be set before
     * entering MONITORING state.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires startAdv != null;
     *   //@ ensures this.startAdvantage == startAdv;
     * }</pre>
     *
     * @param startAdv the initial advantage threshold (typically negative)
     */
    public void setStartAdvantage(Integer startAdv) {
        this.startAdvantage = startAdv;
    }

    /**
     * Returns the initial disadvantage threshold.
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
     * When {@code getAdvantage() >= releaseAdvantage}, the hidden chain is released.
     * Typically zero or positive (e.g., 2 means "release when 2 blocks ahead").
     * Must be set before entering MONITORING state. Zero reproduces Nakamoto probabilities.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires relAdv != null;
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
     *   //@ ensures \result != null;
     *   //@ ensures \result == this.honestBehavior;
     * }</pre>
     *
     * @return the {@linkplain HonestNodeBehavior} instance
     */
    public HonestNodeBehavior getHonestBehavior() {
        return honestBehavior;
    }

    
    public void setHonestBehavior(HonestNodeBehavior honestBehavior) {
		this.honestBehavior = honestBehavior;
	}

    // ================================
    // VALIDATOR METHODS
    // ================================

    /**
     * Validates that all required attack parameters are properly configured.
     * <p>
     * Checks:
     * <ul>
     *   <li>{@code attackPower > 0}: Mining capability must be positive.</li>
     *   <li>{@code startAdvantage != null}: Initial position must be set.</li>
     *   <li>{@code releaseAdvantage != null}: Release threshold must be set.</li>
     *   <li>{@code releaseAdvantage >= startAdvantage}: Release should be achievable.</li>
     * </ul>
     * Warnings are logged for unusual configurations (positive start, negative release, etc).
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
    // INNER ENUMS
    // ================================

    /**
     * Enumeration of attack operational states.
     */
    public enum State {
        /**
         * Normal honest operation; no attack in progress.
         */
        IDLE,

        /**
         * In watch for attack trigger.
         * Node operates honestly while monitoring.
         */
        MONITORING,

        /**
         * Actively mining a hidden chain while maintaining public honest appearance.
         */
        ATTACKING
    }
    
    
    // ================================
    // DEBUG PRINT
    // ================================
    /** 
     * Prints the hidden chain Block IDs starting from hiddenChainTip until {@linkplain Block} parent (retrieved through {@linkplain Block#getParent()}) is null. 
     * Empty string if hiddenChainTip is already null.  
     * @return
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
    
}
