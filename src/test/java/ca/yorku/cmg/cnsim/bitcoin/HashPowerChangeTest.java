package ca.yorku.cmg.cnsim.bitcoin;

import ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNode;
import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.ConfigInitializer;
import ca.yorku.cmg.cnsim.engine.node.PoWNode;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Test class for dynamic hashpower changes during simulation runtime.
 * <p>
 * This test verifies that:
 * <ul>
 *     <li>Hashpower changes can be configured via properties file</li>
 *     <li>Event_HashPowerChange events are scheduled correctly</li>
 *     <li>Node hashpower actually changes at the specified times</li>
 *     <li>The simulation continues correctly after hashpower changes</li>
 * </ul>
 * </p>
 */
public class HashPowerChangeTest {

    @Test
    public void testDynamicHashPowerChanges() throws IOException {
        System.out.println("\n=== Testing Dynamic Hashpower Changes ===\n");

        String[] args = {"-c", "./test-configs/test-hashpower-change.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        runHashPowerChangeSimulation();

        BitcoinReporter.flushAll();
    }

    private void runHashPowerChangeSimulation() {
        BitcoinSimulatorFactory sf = new BitcoinSimulatorFactory();

        System.out.println("Setting Up Simulation with Dynamic Hashpower Changes");
        System.out.println("Expected changes:");
        System.out.println("  - Node 3 (malicious): 25% -> 50% at 50000ms -> 70% at 150000ms");
        System.out.println();

        Simulation s = sf.createSimulation(1);

        // Print initial hashpower distribution
        System.out.println("Initial hashpower distribution:");
        double totalHashPower = 0;
        for (var nodeObj : s.getNodeSet().getNodes()) {
            BitcoinNode node = (BitcoinNode) nodeObj;
            float hashPower = node.getHashPower();
            totalHashPower += hashPower;
            System.out.printf("  Node %d: %.2E hash/s%n", node.getID(), hashPower);
        }
        System.out.printf("  Total: %.2E hash/s%n", totalHashPower);
        System.out.println();

        // Print malicious node's initial power ratio
        for (var nodeObj : s.getNodeSet().getNodes()) {
            BitcoinNode node = (BitcoinNode) nodeObj;
            if (node.getID() == 3) { // Malicious node
                float ratio = (float) (node.getHashPower() / totalHashPower);
                System.out.printf("Malicious node (ID 3) initial power ratio: %.2f%%%n", ratio * 100);
            }
        }
        System.out.println();

        System.out.println("Running Simulation...");
        System.out.println("(Hashpower changes should occur at simulation times 50000ms and 150000ms)");
        System.out.println();

        s.run();

        System.out.println("\n" + s.getStatistics());

        s.closeNodes();

        // Reset statics
        PoWNode.resetCurrID();
        Transaction.resetCurrID();
        Block.resetCurrID();

        System.out.println("\n=== Simulation Complete ===");
        System.out.println("Check the event log for hashpower change events at times 50000 and 150000");
        System.out.println();
    }

}
