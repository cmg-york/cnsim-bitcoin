package ca.yorku.cmg.cnsim.bitcoin.analysis;

import ca.yorku.cmg.cnsim.engine.Debug;
import ca.yorku.cmg.cnsim.engine.Simulation;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects metrics during simulation to compare actual attack outcomes
 * with theoretical predictions from the Bitcoin whitepaper.
 * <p>
 * This class tracks:
 * <ul>
 *   <li>Total number of attack attempts</li>
 *   <li>Number of successful attacks (attacker chain became longest)</li>
 *   <li>Number of failed attacks (honest chain remained longest)</li>
 *   <li>Attack durations and block counts</li>
 *   <li>Confirmation counts at attack initiation</li>
 * </ul>
 * </p>
 *
 * @author Amirreza Radjou for the Conceptual Modeling Group @ York University
 */
public class AttackMetricsCollector {

    private static AttackMetricsCollector instance;

    private int totalAttackAttempts = 0;
    private int successfulAttacks = 0;
    private int failedAttacks = 0;

    private double attackerHashPower = 0.0;
    private int confirmationsRequired = 0;

    // Track individual attack outcomes
    private Map<Integer, AttackOutcome> attackOutcomes = new HashMap<>();
    private int currentAttackId = 0;

    // Statistics
    private long totalAttackDuration = 0;
    private int totalHiddenBlocks = 0;
    private int totalPublicBlocksGenerated = 0;

    /**
     * Represents the outcome of a single attack attempt.
     */
    public static class AttackOutcome {
        public final int attackId;
        public final long startTime;
        public long endTime;
        public boolean successful;
        public int hiddenBlocksMined;
        public int publicBlocksMined;
        public int confirmationsAtStart;
        public String endReason; // "SUCCESS", "FAILED", "TIMEOUT"

        public AttackOutcome(int attackId, long startTime, int confirmationsAtStart) {
            this.attackId = attackId;
            this.startTime = startTime;
            this.confirmationsAtStart = confirmationsAtStart;
            this.hiddenBlocksMined = 0;
            this.publicBlocksMined = 0;
            this.endReason = "ONGOING";
        }

        public long getDuration() {
            return endTime - startTime;
        }
    }

    /**
     * Private constructor for singleton pattern.
     */
    private AttackMetricsCollector() {
    }

    /**
     * Gets the singleton instance of the metrics collector.
     *
     * @return the metrics collector instance
     */
    public static AttackMetricsCollector getInstance() {
        if (instance == null) {
            instance = new AttackMetricsCollector();
        }
        return instance;
    }

    /**
     * Resets all collected metrics. Should be called at the start of a new simulation.
     */
    public void reset() {
        totalAttackAttempts = 0;
        successfulAttacks = 0;
        failedAttacks = 0;
        attackOutcomes.clear();
        currentAttackId = 0;
        totalAttackDuration = 0;
        totalHiddenBlocks = 0;
        totalPublicBlocksGenerated = 0;
    }

    /**
     * Sets the attack configuration parameters.
     *
     * @param attackerHashPower the proportion of network hashpower controlled by attacker
     * @param confirmationsRequired the number of confirmations before attack starts
     */
    public void setConfiguration(double attackerHashPower, int confirmationsRequired) {
        this.attackerHashPower = attackerHashPower;
        this.confirmationsRequired = confirmationsRequired;
    }

    /**
     * Records the start of a new attack attempt.
     *
     * @param confirmationsAtStart the number of confirmations the target transaction had
     * @return the attack ID for tracking this specific attack
     */
    public int recordAttackStart(int confirmationsAtStart) {
        int attackId = currentAttackId++;
        totalAttackAttempts++;

        AttackOutcome outcome = new AttackOutcome(attackId, Simulation.currTime, confirmationsAtStart);
        attackOutcomes.put(attackId, outcome);

        Debug.p(String.format("[Attack %d] Started at time %d with %d confirmations",
            attackId, Simulation.currTime, confirmationsAtStart));

        return attackId;
    }

    /**
     * Records that the attacker mined a hidden block.
     *
     * @param attackId the ID of the ongoing attack
     */
    public void recordHiddenBlockMined(int attackId) {
        AttackOutcome outcome = attackOutcomes.get(attackId);
        if (outcome != null) {
            outcome.hiddenBlocksMined++;
            totalHiddenBlocks++;
        }
    }

    /**
     * Records that the public (honest) chain grew.
     *
     * @param attackId the ID of the ongoing attack
     */
    public void recordPublicBlockMined(int attackId) {
        AttackOutcome outcome = attackOutcomes.get(attackId);
        if (outcome != null) {
            outcome.publicBlocksMined++;
            totalPublicBlocksGenerated++;
        }
    }

    /**
     * Records a successful attack (attacker's chain became longest).
     *
     * @param attackId the ID of the attack
     */
    public void recordAttackSuccess(int attackId) {
        AttackOutcome outcome = attackOutcomes.get(attackId);
        if (outcome != null) {
            outcome.successful = true;
            outcome.endTime = Simulation.currTime;
            outcome.endReason = "SUCCESS";
            successfulAttacks++;
            totalAttackDuration += outcome.getDuration();

            Debug.p(String.format("[Attack %d] SUCCEEDED at time %d (duration: %d, hidden blocks: %d, public blocks: %d)",
                attackId, Simulation.currTime, outcome.getDuration(),
                outcome.hiddenBlocksMined, outcome.publicBlocksMined));
        }
    }

    /**
     * Records a failed attack (honest chain remained longest).
     *
     * @param attackId the ID of the attack
     */
    public void recordAttackFailure(int attackId) {
        AttackOutcome outcome = attackOutcomes.get(attackId);
        if (outcome != null) {
            outcome.successful = false;
            outcome.endTime = Simulation.currTime;
            outcome.endReason = "FAILED";
            failedAttacks++;
            totalAttackDuration += outcome.getDuration();

            Debug.p(String.format("[Attack %d] FAILED at time %d (duration: %d, hidden blocks: %d, public blocks: %d)",
                attackId, Simulation.currTime, outcome.getDuration(),
                outcome.hiddenBlocksMined, outcome.publicBlocksMined));
        }
    }

    /**
     * Gets the current success rate.
     *
     * @return the proportion of successful attacks
     */
    public double getSuccessRate() {
        if (totalAttackAttempts == 0) {
            return 0.0;
        }
        return (double) successfulAttacks / totalAttackAttempts;
    }

    /**
     * Gets the total number of attack attempts.
     *
     * @return the total attempts
     */
    public int getTotalAttackAttempts() {
        return totalAttackAttempts;
    }

    /**
     * Gets the number of successful attacks.
     *
     * @return the successful attack count
     */
    public int getSuccessfulAttacks() {
        return successfulAttacks;
    }

    /**
     * Gets the number of failed attacks.
     *
     * @return the failed attack count
     */
    public int getFailedAttacks() {
        return failedAttacks;
    }

    /**
     * Generates a summary report of all collected metrics.
     *
     * @return a formatted string containing the metrics summary
     */
    public String generateSummaryReport() {
        StringBuilder report = new StringBuilder();
        report.append("=".repeat(80)).append("\n");
        report.append("Attack Metrics Summary\n");
        report.append("=".repeat(80)).append("\n");
        report.append(String.format("Configuration:\n"));
        report.append(String.format("  Attacker Hashpower: %.2f (%.1f%%)\n", attackerHashPower, attackerHashPower * 100));
        report.append(String.format("  Confirmations Required: %d\n", confirmationsRequired));
        report.append(String.format("\n"));
        report.append(String.format("Attack Statistics:\n"));
        report.append(String.format("  Total Attempts:      %d\n", totalAttackAttempts));
        report.append(String.format("  Successful:          %d (%.2f%%)\n",
            successfulAttacks, (getSuccessRate() * 100)));
        report.append(String.format("  Failed:              %d (%.2f%%)\n",
            failedAttacks, ((double) failedAttacks / totalAttackAttempts * 100)));
        report.append(String.format("\n"));

        if (totalAttackAttempts > 0) {
            report.append(String.format("Block Statistics:\n"));
            report.append(String.format("  Average Hidden Blocks per Attack:  %.2f\n",
                (double) totalHiddenBlocks / totalAttackAttempts));
            report.append(String.format("  Average Public Blocks during Attack: %.2f\n",
                (double) totalPublicBlocksGenerated / totalAttackAttempts));
            report.append(String.format("  Average Attack Duration: %.2f time units\n",
                (double) totalAttackDuration / totalAttackAttempts));
        }

        report.append("=".repeat(80)).append("\n");

        return report.toString();
    }

    /**
     * Generates a comparison report with theoretical predictions.
     *
     * @return a formatted comparison report
     */
    public String generateComparisonReport() {
        if (totalAttackAttempts == 0) {
            return "No attack data collected yet.\n";
        }

        StringBuilder report = new StringBuilder();
        report.append(generateSummaryReport());
        report.append("\n");
        report.append(AttackerSuccessProbabilityAnalyzer.compareWithSimulation(
            attackerHashPower,
            confirmationsRequired,
            getSuccessRate(),
            totalAttackAttempts
        ));

        return report.toString();
    }

    /**
     * Exports detailed attack outcomes to a CSV file for further analysis.
     *
     * @param filename the output file path
     * @throws IOException if file writing fails
     */
    public void exportToCSV(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Write header
            writer.println("AttackID,StartTime,EndTime,Duration,Successful,HiddenBlocks,PublicBlocks,ConfirmationsAtStart,EndReason");

            // Write data
            for (AttackOutcome outcome : attackOutcomes.values()) {
                writer.printf("%d,%d,%d,%d,%b,%d,%d,%d,%s\n",
                    outcome.attackId,
                    outcome.startTime,
                    outcome.endTime,
                    outcome.getDuration(),
                    outcome.successful,
                    outcome.hiddenBlocksMined,
                    outcome.publicBlocksMined,
                    outcome.confirmationsAtStart,
                    outcome.endReason
                );
            }
        }
    }

    /**
     * Prints the current metrics to debug output.
     */
    public void printCurrentMetrics() {
        Debug.p(generateSummaryReport());
    }

    /**
     * Prints the comparison with theoretical model to debug output.
     */
    public void printComparison() {
        Debug.p(generateComparisonReport());
    }
}
