package ca.yorku.cmg.cnsim.bitcoin;

import ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNode;
import ca.yorku.cmg.cnsim.bitcoin.node.HonestNodeBehavior;
import ca.yorku.cmg.cnsim.bitcoin.node.MaliciousNodeBehavior;
import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.config.ConfigInitializer;
import ca.yorku.cmg.cnsim.engine.node.PoWNode;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Manual test for malicious node behavior with confirmation delays.
 * This test creates a lightweight simulation to verify the counter-based
 * confirmation delay implementation works correctly.
 */
public class MaliciousBehaviorTest {

    @Test
    public void testMaliciousBehaviorWith0Confirmations() throws IOException {
        System.out.println("\n=== Testing Malicious Behavior with 0 Confirmations ===\n");

        String[] args = {"-c", "./test-configs/test-0-confirmations.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        runSimulationWithConfirmations(0);

        BitcoinReporter.flushAll();
    }

    @Test
    public void testMaliciousBehaviorWith2Confirmations() throws IOException {
        System.out.println("\n=== Testing Malicious Behavior with 2 Confirmations ===\n");

        String[] args = {"-c", "./test-configs/test-2-confirmations.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        runSimulationWithConfirmations(2);

        BitcoinReporter.flushAll();
    }

    @Test
    public void testMaliciousBehaviorWith5Confirmations() throws IOException {
        System.out.println("\n=== Testing Malicious Behavior with 5 Confirmations ===\n");

        String[] args = {"-c", "./test-configs/test-5-confirmations.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        runSimulationWithConfirmations(5);

        BitcoinReporter.flushAll();
    }

    @Test
    public void testMaliciousBehavior70Percent0Confirmations() throws IOException {
        System.out.println("\n=== Testing Malicious Behavior with 70% Hashpower, 0 Confirmations ===\n");

        String[] args = {"-c", "./test-configs/test-70percent-0conf.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        runSimulationWithConfirmations(0);

        BitcoinReporter.flushAll();
    }

    @Test
    public void testMaliciousBehavior70Percent3Confirmations() throws IOException {
        System.out.println("\n=== Testing Malicious Behavior with 70% Hashpower, 3 Confirmations ===\n");

        String[] args = {"-c", "./test-configs/test-70percent-3conf.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        runSimulationWithConfirmations(3);

        BitcoinReporter.flushAll();
    }

    @Test
    public void testMaliciousBehavior70Percent6Confirmations() throws IOException {
        System.out.println("\n=== Testing Malicious Behavior with 70% Hashpower, 6 Confirmations ===\n");

        String[] args = {"-c", "./test-configs/test-70percent-6conf.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        runSimulationWithConfirmations(6);

        BitcoinReporter.flushAll();
    }

    private void runSimulationWithConfirmations(int expectedConfirmations) {
        BitcoinSimulatorFactory sf = new BitcoinSimulatorFactory();

        System.out.println("Setting Up Simulation (attack.requiredConfirmations configured in properties)");
        Simulation s = sf.createSimulation(1);

        // Verify the malicious node has the correct confirmations from config
        for (var nodeObj : s.getNodeSet().getNodes()) {
            BitcoinNode node = (BitcoinNode) nodeObj;
            if (node.getBehaviorStrategy() instanceof MaliciousNodeBehavior) {
                MaliciousNodeBehavior malBehavior = (MaliciousNodeBehavior) node.getBehaviorStrategy();
                int actualConfirmations = malBehavior.getRequiredConfirmationsBeforeAttack();
                System.out.println("Malicious node " + node.getID() +
                                 " configured with " + actualConfirmations + " required confirmations (from config)");
                if (actualConfirmations != expectedConfirmations) {
                    System.err.println("WARNING: Expected " + expectedConfirmations +
                                     " confirmations but got " + actualConfirmations);
                }
            }
        }

        System.out.println("\nRunning Simulation...");
        s.run();

        System.out.println("\n" + s.getStatistics());

        s.closeNodes();

        // Reset statics
        PoWNode.resetCurrID();
        Transaction.resetCurrID();
        Block.resetCurrID();

        System.out.println("\n=== Simulation Complete ===\n");
    }
}
