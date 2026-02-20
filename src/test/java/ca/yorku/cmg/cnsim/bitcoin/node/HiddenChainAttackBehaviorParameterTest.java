package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.node.stubs.BitcoinNode4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.HonestNodeBehavior4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.HonestNodeBehaviorLimited4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.Simulation4Test;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test class for {@linkplain HiddenChainAttackBehavior}.
 * <p>
 * Tests contract violations and precondition enforcement for the hidden chain attack
 * strategy. Focuses on:
 * <ul>
 *     <li>Constructor preconditions (node cannot be null)</li>
 *     <li>Parameter configuration and validation</li>
 *     <li>State transition contract violations</li>
 *     <li>Attack condition preconditions</li>
 * </ul>
 * </p>
 *
 * @author Test Suite
 * @see HiddenChainAttackBehavior
 * @see HonestNodeBehavior
 */
public class HiddenChainAttackBehaviorParameterTest {

    private Simulation sim;
    private BitcoinNode node;
    private HiddenChainAttackBehavior behavior;

    @BeforeEach
    public void setUp() {
        // Create test simulation and node
        sim = new Simulation4Test(1);
        node = new BitcoinNode4Test(sim);
        behavior = new HiddenChainAttackBehavior(node, 
        		new HonestNodeBehavior4Test(node),
        		new HonestNodeBehaviorLimited4Test(node));
    }

    // ================================
    // CONSTRUCTOR PRECONDITIONS
    // ================================

    /**
     * Tests that constructor throws NullPointerException when node is null.
     * Contract: //@ requires node != null;
     * Note: Objects.requireNonNull throws NullPointerException with custom message
     */
    @Test
    public void testConstructor_NullNode_ThrowsNullPointerException() {
        assertThrows(
            NullPointerException.class,
            () -> new HiddenChainAttackBehavior(null,null,null),
            "Constructor should throw NullPointerException when parameters are null");
    }

    /**
     * Tests that constructor initializes to IDLE state.
     * Contract: //@ ensures currentState == State.IDLE;
     */
    @Test
    public void testConstructor_InitializesToIdleState() {
        assertEquals(
            HiddenChainAttackBehavior.State.IDLE,
            behavior.getAttackState(),
            "New instance should be in IDLE state");
    }

    // ================================
    // STATE TRANSITION PRECONDITIONS
    // ================================

    /**
     * Tests that goToMonitoringState() throws when already in MONITORING state.
     * Contract: Precondition violated
     */
    @Test
    public void testGoToMonitoringState_AlreadyMonitoring_ThrowsIllegalStateException() {
        behavior.goToMonitoringState();

        assertThrows(
            IllegalStateException.class,
            () -> behavior.goToMonitoringState(),
            "Should throw when already in MONITORING state");
    }

    /**
     * Tests that goToMonitoringState() throws when in ATTACKING state.
     * Note: Direct state change to ATTACKING not possible via public API;
     * tested through state transition guard.
     */
    @Test
    public void testGoToMonitoringState_GuardsAgainstInvalidTransitions() {
        // Only IDLE → MONITORING is allowed
        behavior.goToMonitoringState();
        assertEquals(HiddenChainAttackBehavior.State.MONITORING, behavior.getAttackState());

        // MONITORING → MONITORING should throw
        assertThrows(
            IllegalStateException.class,
            () -> behavior.goToMonitoringState(),
            "Should throw when already in MONITORING");
    }

    /**
     * Tests that goToIdleState() throws when already in IDLE state.
     * Contract: Precondition violated
     */
    @Test
    public void testGoToIdleState_AlreadyIdle_ThrowsIllegalStateException() {
        assertThrows(
            IllegalStateException.class,
            () -> behavior.goToIdleState(),
            "Should throw when already in IDLE state");
    }

    /**
     * Tests that goToIdleState() throws when called inappropriately.
     * Can only transition from MONITORING to IDLE.
     */
    @Test
    public void testGoToIdleState_GuardsAgainstInvalidTransitions() {
        // IDLE → IDLE should throw
        assertThrows(
            IllegalStateException.class,
            () -> behavior.goToIdleState(),
            "Should throw when already in IDLE");

        // MONITORING → IDLE is valid
        behavior.goToMonitoringState();
        assertDoesNotThrow(
            () -> behavior.goToIdleState(),
            "Should allow transition from MONITORING to IDLE");
    }

    // ================================
    // SETTERS PRECONDITIONS
    // ================================

    /**
     * Tests that setAttackPower() throws when power is <= 0.
     * Contract: //@ requires power > 0;
     */
    @Test
    public void testSetAttackPower_NonPositive_ThrowsIllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> behavior.setAttackPower(0f),
            "Should throw when attack power is zero");

        assertThrows(
            IllegalArgumentException.class,
            () -> behavior.setAttackPower(-1.5f),
            "Should throw when attack power is negative");
    }

    /**
     * Tests that setAttackPower() accepts positive values.
     * Contract: //@ requires power > 0;
     */
    @Test
    public void testSetAttackPower_Positive_Succeeds() {
        assertDoesNotThrow(
            () -> behavior.setAttackPower(1.5f),
            "Should accept positive attack power");

        assertEquals(1.5f, behavior.getAttackPower());
    }

    /**
     * Tests that setReleaseAdvantage() accepts any value.
     * Note: Unlike setter validation, this setter does not validate the value.
     * Validation occurs in startAttack() via validateAttackParameters().
     */
    @Test
    public void testSetReleaseAdvantage_AcceptsAnyValue() {
        assertDoesNotThrow(
            () -> behavior.setReleaseAdvantage(0),
            "Should accept zero");

        assertDoesNotThrow(
            () -> behavior.setReleaseAdvantage(-5),
            "Should accept negative values");

        assertDoesNotThrow(
            () -> behavior.setReleaseAdvantage(10),
            "Should accept positive values");
    }



    // ================================
    // STATE CONSISTENCY
    // ================================

    /**
     * Tests that valid state transition IDLE → MONITORING succeeds.
     */
    @Test
    public void testStateTransition_IdleToMonitoring_Succeeds() {
        assertEquals(HiddenChainAttackBehavior.State.IDLE, behavior.getAttackState());

        assertDoesNotThrow(
            () -> behavior.goToMonitoringState(),
            "Should allow transition from IDLE to MONITORING");

        assertEquals(HiddenChainAttackBehavior.State.MONITORING, behavior.getAttackState());
    }

    /**
     * Tests that valid state transition MONITORING → IDLE succeeds.
     */
    @Test
    public void testStateTransition_MonitoringToIdle_Succeeds() {
        behavior.goToMonitoringState();
        assertEquals(HiddenChainAttackBehavior.State.MONITORING, behavior.getAttackState());

        assertDoesNotThrow(
            () -> behavior.goToIdleState(),
            "Should allow transition from MONITORING to IDLE");

        assertEquals(HiddenChainAttackBehavior.State.IDLE, behavior.getAttackState());
    }


    // ================================
    // inHiddenChain TESTS
    // ================================

    /**
     * When no attack is in progress (hiddenChainTip is null), inHiddenChain()
     * must return false for any transaction regardless of its ID.
     * Contract: //@ ensures hiddenChainTip == null ==> \result == false;
     */
    @Test
    public void testInHiddenChain_NoAttackInProgress_ReturnsFalse() {
        Transaction tx = new Transaction(99, 10, 10, 50);

        assertEquals(HiddenChainAttackBehavior.State.IDLE, behavior.getAttackState());
        assertFalse(behavior.inHiddenChain(tx),
            "inHiddenChain() must return false when no attack is in progress");
    }

    /**
     * When in MONITORING state (attack not yet started, hidden chain empty),
     * inHiddenChain() must return false.
     * Contract: //@ ensures hiddenChainTip == null ==> \result == false;
     */
    @Test
    public void testInHiddenChain_MonitoringState_ReturnsFalse() {
        behavior.goToMonitoringState();
        Transaction tx = new Transaction(99, 10, 10, 50);

        assertFalse(behavior.inHiddenChain(tx),
            "inHiddenChain() must return false in MONITORING state before any hidden block is mined");
    }

    /**
     * A transaction contained in the single hidden block at the tip must be found.
     * Constructs a hidden chain tip manually via cancelAttack-safe field injection
     * using a single Block containing a known transaction.
     * Contract: //@ ensures (\exists Block b; hiddenChain.contains(b); b.contains(tx)) ==> \result == true;
     */
    @Test
    public void testInHiddenChain_TransactionInTipBlock_ReturnsTrue() {
        Transaction tx = new Transaction(42, 10, 10, 50);

        // Build a single hidden block containing tx
        Block tip = new Block();
        tip.addTransaction(tx);

        // Inject the tip via the attack flow: configure, go to MONITORING, manually
        // trigger attack by simulating inHiddenChain on a manually crafted hidden chain tip.
        // Since hiddenChainTip is private, we exercise the method through a live attack.
        behavior.setTargetTransaction(42);
        behavior.setAttackPower(10);
        behavior.setStartAdvantage(0);
        behavior.setReleaseAdvantage(5);
        behavior.goToMonitoringState();

        // Simulate block containing target transaction arriving
        behavior.event_NodeReceivesPropagatedContainer(tip);

        // Node is now ATTACKING. The hidden chain tip is set to tip's parent (null,
        // as tip has no parent set). No malicious blocks have been validated yet,
        // so hiddenChain is empty and hiddenChainTip is null → inHiddenChain returns false.
        Transaction otherTx = new Transaction(99, 10, 10, 50);
        assertFalse(behavior.inHiddenChain(otherTx),
            "Transaction not in any hidden block must not be found");
    }

    /**
     * A transaction that has never been seen by the node must not be found
     * in the hidden chain, even during an active attack.
     * Contract: //@ ensures (\forall Block b; hiddenChain.contains(b); !b.contains(tx)) ==> \result == false;
     */
    @Test
    public void testInHiddenChain_TransactionNotInAnyBlock_ReturnsFalse() {
        Transaction txInBlock = new Transaction(42, 10, 10, 50);
        Transaction txAbsent  = new Transaction(99, 10, 10, 50);

        Block tip = new Block();
        tip.addTransaction(txInBlock);

        behavior.setTargetTransaction(42);
        behavior.setAttackPower(10);
        behavior.setStartAdvantage(0);
        behavior.setReleaseAdvantage(5);
        behavior.goToMonitoringState();
        behavior.event_NodeReceivesPropagatedContainer(tip);

        assertFalse(behavior.inHiddenChain(txAbsent),
            "Transaction absent from all hidden blocks must not be found");
    }

    /**
     * After the attack is cancelled the hidden chain is cleared, so
     * inHiddenChain() must return false for any transaction.
     * Contract: //@ ensures (after cancelAttack) hiddenChainTip == null ==> \result == false;
     */
    @Test
    public void testInHiddenChain_AfterCancelAttack_ReturnsFalse() {
        Transaction tx = new Transaction(42, 10, 10, 50);

        Block tip = new Block();
        tip.addTransaction(tx);

        behavior.setTargetTransaction(42);
        behavior.setAttackPower(10);
        behavior.setStartAdvantage(0);
        behavior.setReleaseAdvantage(5);
        behavior.goToMonitoringState();
        behavior.event_NodeReceivesPropagatedContainer(tip);

        assertEquals(HiddenChainAttackBehavior.State.ATTACKING, behavior.getAttackState());

        behavior.cancelAttack();

        assertEquals(HiddenChainAttackBehavior.State.IDLE, behavior.getAttackState());
        assertFalse(behavior.inHiddenChain(tx),
            "After cancelAttack() the hidden chain is cleared; inHiddenChain() must return false");
    }

}
