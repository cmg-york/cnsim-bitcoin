package ca.yorku.cmg.cnsim.bitcoin.analysis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link AttackerSuccessProbabilityAnalyzer}.
 * <p>
 * Validates that the theoretical calculations match the values
 * published in the Bitcoin whitepaper.
 * </p>
 *
 * @author Amirreza Radjou for the Conceptual Modeling Group @ York University
 */
class AttackerSuccessProbabilityAnalyzerTest {

    private static final double EPSILON = 0.0000001; // Tolerance for floating point comparison

    /**
     * Tests probabilities for q=0.1 (10% attacker hashpower) against whitepaper values.
     */
    @Test
    void testProbabilities_q01() {
        double q = 0.1;

        // Expected values from Bitcoin whitepaper Table 1
        assertEquals(1.0000000, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 0), EPSILON);
        assertEquals(0.2045873, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 1), EPSILON);
        assertEquals(0.0509779, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 2), EPSILON);
        assertEquals(0.0131722, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 3), EPSILON);
        assertEquals(0.0034552, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 4), EPSILON);
        assertEquals(0.0009137, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 5), EPSILON);
        assertEquals(0.0002428, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 6), EPSILON);
        assertEquals(0.0000647, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 7), EPSILON);
        assertEquals(0.0000173, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 8), EPSILON);
        assertEquals(0.0000046, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 9), EPSILON);
        assertEquals(0.0000012, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 10), EPSILON);
    }

    /**
     * Tests probabilities for q=0.3 (30% attacker hashpower) against whitepaper values.
     */
    @Test
    void testProbabilities_q03() {
        double q = 0.3;

        // Expected values from Bitcoin whitepaper Table 2
        assertEquals(1.0000000, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 0), EPSILON);
        assertEquals(0.1773523, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 5), EPSILON);
        assertEquals(0.0416605, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 10), EPSILON);
        assertEquals(0.0101008, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 15), EPSILON);
        assertEquals(0.0024804, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 20), EPSILON);
        assertEquals(0.0006132, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 25), EPSILON);
        assertEquals(0.0001522, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 30), EPSILON);
        assertEquals(0.0000379, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 35), EPSILON);
        assertEquals(0.0000095, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 40), EPSILON);
        assertEquals(0.0000024, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 45), EPSILON);
        assertEquals(0.0000006, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 50), EPSILON);
    }

    /**
     * Tests required confirmations for P < 0.001 against whitepaper values.
     */
    @Test
    void testRequiredConfirmations_lessThan01Percent() {
        double targetProb = 0.001;

        // Expected values from Bitcoin whitepaper Table 3
        assertEquals(5, AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.10, targetProb));
        assertEquals(8, AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.15, targetProb));
        assertEquals(11, AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.20, targetProb));
        assertEquals(15, AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.25, targetProb));
        assertEquals(24, AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.30, targetProb));
        assertEquals(41, AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.35, targetProb));
        assertEquals(89, AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.40, targetProb));
        assertEquals(340, AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.45, targetProb));
    }

    /**
     * Tests that probability decreases as confirmations increase.
     */
    @Test
    void testProbability_decreasesWithConfirmations() {
        double q = 0.2;

        double prob0 = AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 0);
        double prob5 = AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 5);
        double prob10 = AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 10);

        assertTrue(prob0 > prob5);
        assertTrue(prob5 > prob10);
    }

    /**
     * Tests that probability increases as attacker hashpower increases.
     */
    @Test
    void testProbability_increasesWithHashPower() {
        int z = 6;

        double prob_q01 = AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.1, z);
        double prob_q02 = AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.2, z);
        double prob_q03 = AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.3, z);

        assertTrue(prob_q01 < prob_q02);
        assertTrue(prob_q02 < prob_q03);
    }

    /**
     * Tests edge case: zero confirmations always gives probability 1.
     */
    @Test
    void testProbability_zeroConfirmations() {
        assertEquals(1.0, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.1, 0));
        assertEquals(1.0, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.3, 0));
        assertEquals(1.0, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.4, 0));
    }

    /**
     * Tests edge case: majority attacker (q >= 0.5) always succeeds.
     */
    @Test
    void testProbability_majorityAttacker() {
        assertEquals(1.0, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.5, 10));
        assertEquals(1.0, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.6, 10));
        assertEquals(1.0, AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.9, 100));
    }

    /**
     * Tests that invalid inputs throw IllegalArgumentException.
     */
    @Test
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () ->
            AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(-0.1, 5));

        assertThrows(IllegalArgumentException.class, () ->
            AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(1.1, 5));

        assertThrows(IllegalArgumentException.class, () ->
            AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(0.3, -1));

        assertThrows(IllegalArgumentException.class, () ->
            AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(-0.1, 0.001));

        assertThrows(IllegalArgumentException.class, () ->
            AttackerSuccessProbabilityAnalyzer.calculateRequiredConfirmations(0.3, -0.1));
    }

    /**
     * Tests that the whitepaper comparison report is generated without errors.
     */
    @Test
    void testGenerateWhitepaperReport() {
        String report = AttackerSuccessProbabilityAnalyzer.generateWhitepaperComparisonReport();
        assertNotNull(report);
        assertTrue(report.contains("Bitcoin Whitepaper"));
        assertTrue(report.contains("q = 0.1"));
        assertTrue(report.contains("q = 0.3"));
    }

    /**
     * Tests simulation comparison report generation.
     */
    @Test
    void testCompareWithSimulation() {
        String report = AttackerSuccessProbabilityAnalyzer.compareWithSimulation(0.3, 10, 0.04, 1000);
        assertNotNull(report);
        assertTrue(report.contains("Theoretical"));
        assertTrue(report.contains("Simulation"));
        assertTrue(report.contains("0.30"));
    }

    /**
     * Tests that very high confirmation counts approach zero probability.
     */
    @Test
    void testProbability_highConfirmations() {
        double q = 0.3;
        double prob100 = AttackerSuccessProbabilityAnalyzer.calculateAttackerSuccessProbability(q, 100);

        assertTrue(prob100 < 0.000001); // Should be extremely small
    }
}
