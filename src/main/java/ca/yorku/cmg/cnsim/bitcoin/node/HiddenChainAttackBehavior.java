package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Debug;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.event.Event_ContainerValidation;
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
     * An alternative specialization of honest behavior with useful tweaks
     */
    private HonestNodeBehavior altHonestBehavior;
    
    
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
	 * The total power of the network excluding the power of the attacker in 
	 * either attack or idle/monitoring mode
	 */
	private float networkHonestPower;
    
    
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
     * The number of confirmations that the target transaction has received, i.e. the number of blocks that
     * are on top of the block containing the transaction, plus the block itself.
     * Initially null. 
     */
    private Integer releaseConfirmations;
    

	/**
     * When time reaches this point, MONITORING or ATTACKING must cease.
     * -1 means there is no time out (disabled).
     */
    private long attackTimeOut = -1;
    
    
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
     *   //@ requires altBeh != null;
     *   //@ ensures this.node == node;
     *   //@ ensures currentState == State.IDLE;
     *   //@ ensures hiddenChain != null && hiddenChain.isEmpty();
     *   //@ ensures honestBehavior != null;
     * }</pre>
     *
     * @param node the Bitcoin node to which this behavior is attached (must not be null)
     * @param beh  the honest behavior to wrap and delegate to (must not be null)
     * @param altBeh a limited version of honest behavior to wrap and delegate to (must not be null)
     * @throws NullPointerException if {@code node} is null
     */
    public HiddenChainAttackBehavior(BitcoinNode node, 
    		HonestNodeBehavior beh,
    		HonestNodeBehavior altBeh) {
        Objects.requireNonNull(node, "BitcoinNode cannot be null");
        this.node = node;
        this.honestBehavior = beh;
        this.altHonestBehavior = altBeh;
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
            // NORMALLY: you should ignore the target transaction 
        	// (wait for it in a block)
        	// HOWEVER: we receive it normally here for the purpose of compliance
        	// with theoretical calculations
            if (t.getID() != targetTransaction) {
                honestBehavior.event_NodeReceivesClientTransaction(t, time);
            }
            
            break;

        case ATTACKING:
        	// Accept non-target transactions that do not 
        	// overlap your hidden chain to maintain honest appearance
        	if (!inHiddenChain(t) && !(t.getID() == targetTransaction)) {
        		altHonestBehavior.event_NodeReceivesClientTransaction(t, time);
        	}
        	
        	//Actually..
        	if (t.getID() == targetTransaction) {
        		throw new IllegalStateException("Receiving target transaction while in attack state. How did I enter attack state if transaction hadn't arrived first?");
        	}
        	
            break;
        }
        
    	if (attackTimedOut(time)) {
    		handleTimeOut(time);
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
        
        /*
        if (node.getID() == 30 && (t.getID() == 71 || t.getID() == 72)) {
        	BitcoinReporter.addErrorEntry("Receiving transaction " + t.getID() + " under state " + getAttackState());
        }*/
        
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
        	if (attackTimedOut(time)) {
        		handleTimeOut(time);
            }
            break;

        case ATTACKING:
            // In ATTACKING, maintain honest pool for public chain mining
            /* if (t.getID() == targetTransaction) {
                throw new IllegalStateException(
                    "Target transaction propagated during attack (should not occur): " + t.getID());
            }*/
        	
        	// Reject the transaction if it is in the hiddenchain
        	// otherwise do what an honest node would do.
        	if (!inHiddenChain(t) && !(t.getID() == targetTransaction)) {
        		altHonestBehavior.event_NodeReceivesPropagatedTransaction(t, time);
        	} 
            break;
        }
        
    	if (attackTimedOut(time)) {
    		handleTimeOut(time);
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
                if (hiddenChainTip != null) 
                	throw new IllegalStateException("hiddenChainTip must be null in MONITORING state.");
                if (!hiddenChain.isEmpty()) 
                	throw new IllegalStateException("hiddenChain must be empty in MONITORING state.");
                
                // The hidden chain starts from the parent of the block containing the target
                hiddenChainTip = (Block) ((Block) c).getParent();
                considerAttacking();
            }
            break;

        case ATTACKING:
            // In ATTACKING, update the public chain to track attacker's advantage
        	// however use the limited honest behavior in which the pool is not cleared. 
            altHonestBehavior.event_NodeReceivesPropagatedContainer(c);
           	evaluateAttackState(Simulation.currTime);
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

        /*
        if ((node.getID() == 30) && (c.contains(71) || (c.contains(72)))) {
        	Block l = (Block) c;
            BitcoinReporter.addErrorEntry(Simulation.currTime + ": node 30 completes validation of " + node.getMiningPool().printIDs(";") + " which points to " + (l.getParent() != null ? l.getParent().getID() : "null") + ". State is " + getAttackState());
        } */

        
        
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
            BitcoinReporter.addEvent(
            		node.getSim().getSimID(),
            		-1,
            		Simulation.currTime,
            		-1,
            		"Validating Malicious block",
            		node.getID(),
            		-1,
            		"Hidden Chain: " + printHiddenChainAndContent("-") 
            		);
            // In ATTACKING, intercept block and add to hidden chain
            nodeCompletesMaliciousValidation(c, time);
            // Check whether the release advantage threshold has been reached
           	evaluateAttackState(time);
            break;
        }
        
    	if (attackTimedOut(time)) {
    		handleTimeOut(time);
        } 
        
    }

    @Override
    protected boolean isWorthMining() {
    	if (currentState == State.ATTACKING) {
    		//If attacking don't be picky, just make sure there are tx's
    		return (node.getMiningPool().getCount() > 0);
    	} else
    		return (super.isWorthMining());
    }

    private void handleTimeOut(long time) {
        switch (currentState) {
        case IDLE:
        	break;
        case MONITORING: 
        	//Simply change state.
        	goToIdleState();
        	break;
        case ATTACKING:
        	completeAttack(time);
        	break;
        }
    	
    }

    private boolean attackTimedOut(long time) {
    	if ((attackTimeOut != -1) && (time>=attackTimeOut)) 
    		return(true);
    	else
    		return(false);
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
        
        
        
        BitcoinReporter.addEvent(
        		node.getSim().getSimID(),
        		-1,
        		Simulation.currTime,
        		-1,
        		"Going into Monitoring",
        		node.getID(),
        		-1,
        		"Power: " + node.getHashPower() 
        		);
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
        //BitcoinReporter.addErrorEntry(Simulation.currTime + ": node going to IDLE");
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
        //BitcoinReporter.addErrorEntry(Simulation.currTime + ": node going to IDLE");
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

        if (getCurrentAdvantage() <= startAdvantage) {
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
    	//BitcoinReporter.addErrorEntry(Simulation.currTime + ": node going to ATTACK");
        
        
        BitcoinReporter.addEvent(
        		node.getSim().getSimID(),
        		-1,
        		Simulation.currTime,
        		-1,
        		"Attack is Starting",
        		node.getID(),
        		-1,
        		"Power: " + attackPower
        		);
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
        rescheduleNextValidation();
    }

    
    private void rescheduleNextValidation() {
    	if (node.isMining()) {
    		Event_ContainerValidation oldValidationEvent = node.getNextValidationEvent();
    		node.scheduleValidationEvent(new Block(node.getMiningPool().getTransactions()), Simulation.currTime);
    		
    		long oldTime = oldValidationEvent.getTime();
    		long newTime = node.getNextValidationEvent().getTime(); 
    		
    		if (oldTime < newTime) {
    			//BitcoinReporter.addErrorEntry("*** STAYING to old validation event (oldtime,newtime) =  (" + oldTime + "," + newTime + ")." );
    			
    			node.getNextValidationEvent().ignoreEvt(true);
    			oldValidationEvent.ignoreEvt(false);
    			//Go back to the original validation event
    			node.setNextValidationEvent(oldValidationEvent);
    			
    		} else {
    			//BitcoinReporter.addErrorEntry("*** SWITHCING to new validation event (oldtime,newtime) =  (" + oldTime + "," + newTime + ")." );
    			node.getNextValidationEvent().ignoreEvt(false);
    			oldValidationEvent.ignoreEvt(true);
    		}
    	} else {
    		if ((node.getNextValidationEvent()!= null) && !node.getNextValidationEvent().ignoreEvt()) {
    			BitcoinReporter.addErrorEntry("*** VERY STRANGE: node not mining and still has next valiation event!");	
    		}
    	}
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
                "Node Completes Malicious Validation",
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

        if (getReleaseStrategy() == ReleaseStrategy.ADVANTAGE) {
	        if (getCurrentAdvantage() >= getReleaseAdvantage()) {
	            completeAttack(time);
	        }
        } else if (getReleaseStrategy() == ReleaseStrategy.CONFIRMATIONS) {
	        if ((getCurrentConfirmations() >= getReleaseConfirmations()) && 
	        (getCurrentAdvantage() >= 1)) {
	            completeAttack(time);
	            //BitcoinReporter.addErrorEntry(node.getSim().getSimID() + "," + getCurrentConfirmations() + "," + getCurrentAdvantage() + ", true" );
	        } else {
	        	//BitcoinReporter.addErrorEntry(node.getSim().getSimID() + "," + getCurrentConfirmations() + "," + getCurrentAdvantage()+ ", false");
	        }
        }
    }

    /**
     * Releases the accumulated hidden chain to the network.
     * <p>
     * Transitions to IDLE state, then broadcasts each block in the hidden chain
     * to the network (both receiving it locally and cloning it for propagation).
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
    public void completeAttack(long time) {
        if (currentState != State.ATTACKING) {
            throw new IllegalStateException(
                "Cannot release chain outside of ATTACKING state, currently in: " + currentState);
        }

        // Transition to IDLE before broadcasting so received blocks are processed honestly
        currentState = State.IDLE;
        //BitcoinReporter.addErrorEntry(Simulation.currTime + ": node going to IDLE");
        
        BitcoinReporter.addEvent(
        		node.getSim().getSimID(),
        		-1,
        		Simulation.currTime,
        		-1,
        		"Releasing chain",
        		node.getID(),
        		-1,
        		"Hidden Chain: " + printHiddenChainAndContent("-") 
        		);
        
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

        hiddenChain.clear();
        hiddenChainTip = null;
        
        BitcoinReporter.addErrorEntry(getNetworkHonestPower() + "," 
        + getAttackPower());
        
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
        
        getReleaseStrategy(); 
        
        if (startAdvantage > 0) {
            String msg = "Attack starting with positive advantage (ahead): " + startAdvantage;
            Debug.p(1, "WARNING: " + msg);
            BitcoinReporter.addErrorEntry("WARNING: HiddenChainAttackBehavior " + msg);
        }

        if ((getReleaseStrategy() == ReleaseStrategy.ADVANTAGE) && (releaseAdvantage < 0)) {
            String msg = "Attack releasing with negative advantage: " + releaseAdvantage;
            Debug.p(1, "WARNING: " + msg);
            BitcoinReporter.addErrorEntry("WARNING: HiddenChainAttackBehavior " + msg);
        }

        if ((getReleaseStrategy() == ReleaseStrategy.ADVANTAGE) && (releaseAdvantage < startAdvantage)) {
            String msg = "Release advantage (" + releaseAdvantage +
                         ") less than start advantage (" + startAdvantage +
                         "). Attack may not progress.";
            Debug.p(1, "WARNING: " + msg);
            BitcoinReporter.addErrorEntry("WARNING: HiddenChainAttackBehavior " + msg);
        }
        
    }

    

    /**
     * Determines whether a given transaction is contained in any block of the hidden chain.
     * <p>
     * Traverses the hidden chain from tip to root, checking each block for the transaction.
     * Returns {@code false} immediately if no attack is in progress (i.e., the hidden chain
     * tip is {@code null}). This method is state-independent: it may be called in any attack
     * state, but will only return {@code true} while an attack is active and the transaction
     * has been mined into a hidden block.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires tx != null;
     *   //@ ensures hiddenChainTip == null ==> \result == false;
     *   //@ ensures (\exists Block b; hiddenChain.contains(b); b.contains(tx))
     *   //            ==> \result == true;
     *   //@ ensures (\forall Block b; hiddenChain.contains(b); !b.contains(tx))
     *   //            ==> \result == false;
     * }</pre>
     *
     * @param tx the transaction to search for (must not be null)
     * @return {@code true} if {@code tx} is found in any block of the hidden chain;
     *         {@code false} otherwise, including when no attack is in progress
     */
    public boolean inHiddenChain(Transaction tx) {
        if (hiddenChainTip == null) {
            return false;
        }

        Block current = hiddenChainTip;
        while (current != null) {
        	if (current.contains(tx)) {
        		return(true);
        	}
            current = (Block) current.getParent();
        }

        return false;
    }
    
    public int getCurrentConfirmations() {
    	if (targetTransaction == -1) {
    		throw new IllegalStateException("Target Transaction has not been set: " + targetTransaction);
    	}
    	Integer confirmations = node.getStructure().getConfirmations(targetTransaction);
    	
    	if (confirmations == null) {
    		return -1;
    	} else {
    		return (confirmations);
    	}
    }
    
    
    // ================================
    // GETTERS AND SETTERS
    // ================================

    
    
    public Integer getReleaseConfirmations() {
		return releaseConfirmations;
	}

	public void setReleaseConfirmations(Integer releaseConfirmations) {
		if ((releaseConfirmations == null) || (releaseConfirmations < 0 )) {
			throw new IllegalArgumentException("Release confirmations must be non-zero");
		}
		this.releaseConfirmations = releaseConfirmations;
	}
    
	public ReleaseStrategy getReleaseStrategy() {
		if ((releaseConfirmations == null) && (releaseAdvantage == null)) {
			throw new IllegalStateException("Cannot infer release strategy: neither releaseConfirmations nor releaseAdvantage have been set.");
		} else if (((releaseConfirmations != null) && (releaseAdvantage != null))) {
			throw new IllegalStateException("Cannot infer release strategy: both releaseConfirmations and releaseAdvantage have been set.");
		} else if (((releaseConfirmations != null) && (releaseAdvantage == null))) {
			return ReleaseStrategy.CONFIRMATIONS;
		} else if (((releaseConfirmations == null) && (releaseAdvantage != null))) {
			return ReleaseStrategy.ADVANTAGE;
		}
		throw new IllegalStateException("Error in conditional.");
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
    public int getCurrentAdvantage() {
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
     * Sets the attacker's mining power, i.e. the power that the attacker 
     * will assume once the attack starts. The attacker will revert to 
     * original power after the attack
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

    
	public void setNetworkHonestPower(float f) {
		this.networkHonestPower = f;
	}
    
	public float getNetworkHonestPower() {
		return (this.networkHonestPower);
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

    /**
     * Sets the simulation time at which an ongoing MONITORING or ATTACKING phase
     * must be forcibly terminated.
     * <p>
     * When the simulation clock reaches this value, {@link #handleTimeOut()} should
     * be called to abort the attack. A value of {@code -1} (or any sentinel agreed
     * by the caller) indicates that no timeout is in effect.
     * </p>
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ requires timeOut >= 0;
     *   //@ ensures this.attackTimeOut == timeOut;
     * }</pre>
     *
     * @param timeOut the simulation time (in milliseconds from sim start) after which
     *                the attack must cease; must be non-negative
     * @throws IllegalArgumentException if {@code timeOut} is negative
     */
    public void setAttackTimeOut(long timeOut) {
        if (timeOut < 0) {
            throw new IllegalArgumentException("Attack timeout must be non-negative, got: " + timeOut);
        }
        this.attackTimeOut = timeOut;
    }

    /**
     * Returns the simulation time at which the current MONITORING or ATTACKING phase
     * will be forcibly terminated.
     *
     * <p><b>JML Contract:</b></p>
     * <pre>{@code
     *   //@ ensures \result == this.attackTimeOut;
     * }</pre>
     *
     * @return the timeout value in simulation-time milliseconds
     */
    public long getAttackTimeOut() {
        return attackTimeOut;
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
                ", releaseConfirmations=" + releaseConfirmations +
                ", advantage=" + getCurrentAdvantage() +
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
    public String printHiddenChain(String sep) {
        if (hiddenChainTip == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        Block current = hiddenChainTip;

        while (current != null) {
            if (result.length() > 0) {
                result.append(sep);
            }
            result.append(current.getID());
            current = (Block) current.getParent();
        }

        return result.toString();
    }


    
    public String printHiddenChainAndContent(String sep) {
        if (hiddenChainTip == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        Block current = hiddenChainTip;

        while (current != null) {
            if (result.length() > 0) {
                result.append(sep);
            }
            result.append(current.getID() + " (" + current.printIDs(";") + ")");
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
    
    /**
     * Enumeration of attack operational states.
     */
    public enum ReleaseStrategy {
        /** Release based on the advantage of your chain. */
        ADVANTAGE,

        /** Release based on the number of confirmations of the target transaction. */
        CONFIRMATIONS
    }


    
}
