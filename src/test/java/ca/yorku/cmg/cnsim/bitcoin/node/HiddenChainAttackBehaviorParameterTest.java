package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.node.stubs.BitcoinNode4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.Simulation4Test;
import ca.yorku.cmg.cnsim.engine.Simulation;
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
        behavior = new HiddenChainAttackBehavior(node);
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
            () -> new HiddenChainAttackBehavior(null),
            "Constructor should throw NullPointerException when node is null");
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

}
