package ca.yorku.cmg.cnsim.bitcoin.analysis;

import ca.yorku.cmg.cnsim.engine.Debug;

/**
 * Analyzes and calculates attacker success probability based on the Bitcoin whitepaper model.
 * <p>
 * This class implements the mathematical model from Section 11 of the Bitcoin whitepaper
 * (Satoshi Nakamoto, 2008) to calculate the probability that an attacker can successfully
 * execute a double-spend attack given:
 * <ul>
 *   <li>q: The proportion of network hashpower controlled by the attacker</li>
 *   <li>z: The number of confirmation blocks the recipient waits for</li>
 * </ul>
 * </p>
 * <p>
 * The model assumes the attacker starts mining a parallel chain immediately after the
 * transaction is broadcast, attempting to create a longer chain to replace the honest chain
 * and reverse the transaction.
 * </p>
 *
 * @author Amirreza Radjou for the Conceptual Modeling Group @ York University
 * @see <a href="https://bitcoin.org/bitcoin.pdf">Bitcoin: A Peer-to-Peer Electronic Cash System</a>
 */
public class AttackerSuccessProbabilityAnalyzer {

    /**
     * Calculates the probability that an attacker can catch up and overtake the honest chain.
     * <p>
     * Based on the formula from the Bitcoin whitepaper:
     * <pre>
     * P = sum(k=0 to z) [ (lambda^k * e^-lambda) / k! * (1 - (q/p)^(z-k)) ]
     * where lambda = z * (q/p)
     * </pre>
     * </p>
     *
     * @param q the proportion of network hashpower controlled by the attacker (0 < q < 1)
     * @param z the number of confirmation blocks
     * @return the probability of attacker success (0 to 1)
     * @throws IllegalArgumentException if q is not in range (0, 1) or z is negative
     */
    public static double calculateAttackerSuccessProbability(double q, int z) {
        // Validate inputs
        if (q <= 0.0 || q >= 1.0) {
            throw new IllegalArgumentException("Attacker hashpower proportion q must be in range (0, 1), got: " + q);
        }
        if (z < 0) {
            throw new IllegalArgumentException("Number of confirmations z must be non-negative, got: " + z);
        }

        // Special case: if attacker has majority hashpower, attack always succeeds
        if (q >= 0.5) {
            return 1.0;
        }

        // If no confirmations required, attacker always succeeds initially
        if (z == 0) {
            return 1.0;
        }

        double p = 1.0 - q;
        double lambda = z * (q / p);
        double sum = 1.0;

        // Calculate the summation term
        for (int k = 0; k <= z; k++) {
            // Calculate Poisson probability: (lambda^k * e^-lambda) / k!
            double poisson = Math.exp(-lambda);
            for (int i = 1; i <= k; i++) {
                poisson *= lambda / i;
            }

            // Calculate catch-up probability term
            double catchUpProb;
            if (k <= z) {
                catchUpProb = 1.0 - Math.pow(q / p, z - k);
            } else {
                catchUpProb = 1.0;
            }

            sum -= poisson * catchUpProb;
        }

        return sum;
    }

    /**
     * Calculates the minimum number of confirmations needed to achieve a target security level.
     *
     * @param q the proportion of network hashpower controlled by the attacker
     * @param targetProbability the maximum acceptable probability of attack success (e.g., 0.001 for 0.1%)
     * @return the minimum number of confirmations needed
     * @throws IllegalArgumentException if q is not in range (0, 1) or targetProbability is not in range (0, 1)
     */
    public static int calculateRequiredConfirmations(double q, double targetProbability) {
        if (q <= 0.0 || q >= 1.0) {
            throw new IllegalArgumentException("Attacker hashpower proportion q must be in range (0, 1)");
        }
        if (targetProbability <= 0.0 || targetProbability >= 1.0) {
            throw new IllegalArgumentException("Target probability must be in range (0, 1)");
        }
        if (q >= 0.5) {
            return Integer.MAX_VALUE; // Cannot achieve security with majority attacker
        }

        // Binary search for minimum z
        int z = 0;
        while (calculateAttackerSuccessProbability(q, z) > targetProbability) {
            z++;
            if (z > 1000) { // Safety limit
                break;
            }
        }

        return z;
    }

    /**
     * Generates a detailed report of attacker success probabilities for various
     * hashpower ratios and confirmation counts, matching the tables from the whitepaper.
     *
     * @return a formatted string containing the analysis report
     */
    public static String generateWhitepaperComparisonReport() {
        StringBuilder report = new StringBuilder();
        report.append("=".repeat(80)).append("\n");
        report.append("Bitcoin Whitepaper Attack Success Probability Analysis\n");
        report.append("Based on: Bitcoin: A Peer-to-Peer Electronic Cash System (Satoshi Nakamoto, 2008)\n");
        report.append("=".repeat(80)).append("\n\n");

        // Table 1: q=0.1 (10% attacker hashpower)
        report.append("Attacker Hashpower: q = 0.1 (10%)\n");
        report.append("-".repeat(40)).append("\n");
        report.append(String.format("%-15s %s\n", "Confirmations", "Success Probability"));
        report.append("-".repeat(40)).append("\n");
        for (int z = 0; z <= 10; z++) {
            double prob = calculateAttackerSuccessProbability(0.1, z);
            report.append(String.format("z = %-10d P = %.7f\n", z, prob));
        }
        report.append("\n");

        // Table 2: q=0.3 (30% attacker hashpower)
        report.append("Attacker Hashpower: q = 0.3 (30%)\n");
        report.append("-".repeat(40)).append("\n");
        report.append(String.format("%-15s %s\n", "Confirmations", "Success Probability"));
        report.append("-".repeat(40)).append("\n");
        for (int z = 0; z <= 50; z += 5) {
            double prob = calculateAttackerSuccessProbability(0.3, z);
            report.append(String.format("z = %-10d P = %.7f\n", z, prob));
        }
        report.append("\n");

        // Table 3: Required confirmations for P < 0.1%
        report.append("Required Confirmations for P < 0.001 (0.1% attack success)\n");
        report.append("-".repeat(40)).append("\n");
        report.append(String.format("%-15s %s\n", "Attacker Power", "Min Confirmations"));
        report.append("-".repeat(40)).append("\n");
        double[] qValues = {0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45};
        for (double q : qValues) {
            int z = calculateRequiredConfirmations(q, 0.001);
            report.append(String.format("q = %.2f        z = %d\n", q, z));
        }
        report.append("\n");

        return report.toString();
    }

    /**
     * Compares theoretical probabilities with simulation results.
     * <p>
     * This method should be called after simulation completion with the actual
     * attack success metrics from the simulation.
     * </p>
     *
     * @param q the actual proportion of network hashpower controlled by the attacker
     * @param z the number of confirmations used in the simulation
     * @param simulationSuccessRate the observed success rate from the simulation (successful attacks / total attempts)
     * @param totalAttempts the total number of attack attempts in the simulation
     * @return a formatted comparison report
     */
    public static String compareWithSimulation(double q, int z, double simulationSuccessRate, int totalAttempts) {
        double theoreticalProbability = calculateAttackerSuccessProbability(q, z);
        double difference = Math.abs(theoreticalProbability - simulationSuccessRate);
        double percentDifference = (difference / theoreticalProbability) * 100.0;

        StringBuilder report = new StringBuilder();
        report.append("=".repeat(80)).append("\n");
        report.append("Theoretical vs. Simulation Comparison\n");
        report.append("=".repeat(80)).append("\n");
        report.append(String.format("Attacker Hashpower (q):           %.2f (%.1f%%)\n", q, q * 100));
        report.append(String.format("Honest Hashpower (p):             %.2f (%.1f%%)\n", 1.0 - q, (1.0 - q) * 100));
        report.append(String.format("Confirmations (z):                %d\n", z));
        report.append(String.format("Total Attack Attempts:            %d\n", totalAttempts));
        report.append("-".repeat(80)).append("\n");
        report.append(String.format("Theoretical Success Probability:  %.7f (%.4f%%)\n",
            theoreticalProbability, theoreticalProbability * 100));
        report.append(String.format("Simulation Success Rate:          %.7f (%.4f%%)\n",
            simulationSuccessRate, simulationSuccessRate * 100));
        report.append(String.format("Absolute Difference:              %.7f\n", difference));
        report.append(String.format("Relative Difference:              %.2f%%\n", percentDifference));
        report.append("=".repeat(80)).append("\n");

        // Analysis
        if (percentDifference < 5.0) {
            report.append("✓ Simulation results closely match theoretical predictions (< 5% difference)\n");
        } else if (percentDifference < 15.0) {
            report.append("⚠ Moderate difference between simulation and theory (5-15% difference)\n");
            report.append("  This may be due to network delays, orphan blocks, or statistical variance.\n");
        } else {
            report.append("✗ Significant difference between simulation and theory (> 15% difference)\n");
            report.append("  Consider investigating:\n");
            report.append("  - Network propagation delays\n");
            report.append("  - Block generation variance\n");
            report.append("  - Attack strategy implementation\n");
            report.append("  - Sample size (need more attack attempts for statistical significance)\n");
        }

        return report.toString();
    }

    /**
     * Prints a comprehensive analysis including both theoretical predictions
     * and guidelines for simulation validation.
     */
    public static void printComprehensiveAnalysis() {
        Debug.p(generateWhitepaperComparisonReport());

        Debug.p("\nGuidelines for Simulation Validation:");
        Debug.p("=".repeat(80));
        Debug.p("1. Run simulations with various q values (e.g., 0.1, 0.2, 0.3, 0.4)");
        Debug.p("2. For each q, test multiple confirmation counts (z)");
        Debug.p("3. Record attack success rate: (successful attacks) / (total attempts)");
        Debug.p("4. Use compareWithSimulation() to validate against theoretical model");
        Debug.p("5. Ensure sufficient sample size (at least 100 attack attempts per configuration)");
        Debug.p("6. Account for network effects not captured in the theoretical model:");
        Debug.p("   - Block propagation delays");
        Debug.p("   - Network partitions");
        Debug.p("   - Mining pool behavior");
        Debug.p("   - Selfish mining strategies");
        Debug.p("=".repeat(80));
    }

    /**
     * Main method for standalone testing and report generation.
     */
    public static void main(String[] args) {
        printComprehensiveAnalysis();

        // Example simulation comparison
        Debug.p("\n\nExample Simulation Comparison:");
        Debug.p(compareWithSimulation(0.3, 10, 0.045, 1000));
    }
}
