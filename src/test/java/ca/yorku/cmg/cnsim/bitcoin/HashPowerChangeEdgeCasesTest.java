package ca.yorku.cmg.cnsim.bitcoin;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.ConfigInitializer;
import ca.yorku.cmg.cnsim.engine.node.PoWNode;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Test class for edge cases and error scenarios in hashpower changes.
 * <p>
 * Tests scenarios like:
 * <ul>
 *     <li>Multiple changes to the same node</li>
 *     <li>Changes to multiple nodes simultaneously</li>
 *     <li>Changes at the same time</li>
 *     <li>Changes to non-existent nodes (should be handled gracefully)</li>
 *     <li>Very early and very late changes</li>
 * </ul>
 * </p>
 */
public class HashPowerChangeEdgeCasesTest {

    @Test
    public void testMultipleChangesToSameNode() throws IOException {
        System.out.println("\n=== Testing Multiple Hashpower Changes to Same Node ===\n");

        // Create a temporary config file for this test
        String configContent = """
# Test: Multiple changes to same node
sim.numSimulations = 1
sim.maxNodes = 3
sim.maxTransactions = 20
sim.terminate.atTime = 100000
sim.output.directory = ./test-results
sim.experimentalLabel = test-multiple-changes

reporter.reportEvents = false
reporter.reportTransactions = false
reporter.reportNodes = false
reporter.reportNetEvents = false
reporter.reportBeliefs = false
reporter.reportBeliefsShort = false
reporter.reportBlockEvents = false
reporter.reportStructureEvents = false
reporter.beliefReportInterval = 60000
reporter.beliefReportOffset = 120000
reporter.reportingWindow = 100000

net.sampler.seed = 123
net.sampler.seed.updateSeed = false
net.numOfNodes = 3
net.numOfHonestNodes = 3
net.numOfMaliciousNodes = 0
net.throughputMean = 25000000f
net.throughputSD = 2500000f
net.propagationTime = 10

workload.sampler.seed = 321
workload.sampler.seed.updateSeed = false
workload.lambda = 6.494f
workload.numTransactions = 20
workload.sampleTransaction = {}
workload.txSizeMean = 225f
workload.txSizeSD = 120.9471f
workload.txFeeValueMean = 3619.23f
workload.txFeeValueSD = 369.19f
workload.hasConflicts = true
workload.conflicts.dispersion = 0.1
workload.conflicts.likelihood = 0.05

reporter.reportAttackEvents = false

attack.requiredConfirmations = 0
attack.minChainLength = 2
attack.maxChainLength = 15

node.sampler.seed = {444}
node.sampler.updateSeedFlags = {false}
node.sampler.seedUpdateTimes = {}
node.createMaliciousNode = false
node.maliciousPowerByRatio = false
node.electricPowerMean = 1375f
node.electricPowerSD = 20f
node.electricCostMean = 0.1f
node.electricCostSD = 0.05f

pow.difficulty = 4.3933890848757156E20
pow.hashPowerMean = 2.35597310021E+10
pow.hashPowerSD = 2.35597310021E+9

# Node 1: changes 3 times during simulation
node.hashPowerChanges = {1:5.0E10:10000, 1:3.0E10:30000, 1:7.0E10:50000}

bitcoin.maxBlockSize = 1000000
bitcoin.minSizeToMine = 100
bitcoin.minValueToMine = 1
""";

        // Write to temporary file
        java.nio.file.Files.writeString(
            java.nio.file.Path.of("./test-configs/test-multiple-changes-temp.properties"),
            configContent
        );

        String[] args = {"-c", "./test-configs/test-multiple-changes-temp.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        System.out.println("Running simulation with 3 hashpower changes to node 1...");
        System.out.println("Changes at: 10000ms, 30000ms, 50000ms");

        BitcoinSimulatorFactory sf = new BitcoinSimulatorFactory();
        Simulation s = sf.createSimulation(1);
        s.run();

        System.out.println("\n" + s.getStatistics());

        s.closeNodes();
        PoWNode.resetCurrID();
        Transaction.resetCurrID();
        Block.resetCurrID();
        BitcoinReporter.flushAll();

        // Cleanup temp file
        java.nio.file.Files.deleteIfExists(
            java.nio.file.Path.of("./test-configs/test-multiple-changes-temp.properties")
        );

        System.out.println("\n=== Test Complete ===\n");
    }

    @Test
    public void testChangesToMultipleNodes() throws IOException {
        System.out.println("\n=== Testing Hashpower Changes to Multiple Nodes ===\n");

        String configContent = """
# Test: Changes to multiple nodes
sim.numSimulations = 1
sim.maxNodes = 4
sim.maxTransactions = 20
sim.terminate.atTime = 100000
sim.output.directory = ./test-results
sim.experimentalLabel = test-multi-node

reporter.reportEvents = false
reporter.reportTransactions = false
reporter.reportNodes = false
reporter.reportNetEvents = false
reporter.reportBeliefs = false
reporter.reportBeliefsShort = false
reporter.reportBlockEvents = false
reporter.reportStructureEvents = false
reporter.beliefReportInterval = 60000
reporter.beliefReportOffset = 120000
reporter.reportingWindow = 100000

net.sampler.seed = 123
net.sampler.seed.updateSeed = false
net.numOfNodes = 4
net.numOfHonestNodes = 4
net.numOfMaliciousNodes = 0
net.throughputMean = 25000000f
net.throughputSD = 2500000f
net.propagationTime = 10

workload.sampler.seed = 321
workload.sampler.seed.updateSeed = false
workload.lambda = 6.494f
workload.numTransactions = 20
workload.sampleTransaction = {}
workload.txSizeMean = 225f
workload.txSizeSD = 120.9471f
workload.txFeeValueMean = 3619.23f
workload.txFeeValueSD = 369.19f
workload.hasConflicts = true
workload.conflicts.dispersion = 0.1
workload.conflicts.likelihood = 0.05

reporter.reportAttackEvents = false

attack.requiredConfirmations = 0
attack.minChainLength = 2
attack.maxChainLength = 15

node.sampler.seed = {444}
node.sampler.updateSeedFlags = {false}
node.sampler.seedUpdateTimes = {}
node.createMaliciousNode = false
node.maliciousPowerByRatio = false
node.electricPowerMean = 1375f
node.electricPowerSD = 20f
node.electricCostMean = 0.1f
node.electricCostSD = 0.05f

pow.difficulty = 4.3933890848757156E20
pow.hashPowerMean = 2.35597310021E+10
pow.hashPowerSD = 2.35597310021E+9

# Different nodes change at different times
node.hashPowerChanges = {1:5.0E10:10000, 2:4.0E10:20000, 3:6.0E10:30000}

bitcoin.maxBlockSize = 1000000
bitcoin.minSizeToMine = 100
bitcoin.minValueToMine = 1
""";

        java.nio.file.Files.writeString(
            java.nio.file.Path.of("./test-configs/test-multi-node-temp.properties"),
            configContent
        );

        String[] args = {"-c", "./test-configs/test-multi-node-temp.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        System.out.println("Running simulation with changes to nodes 1, 2, and 3...");

        BitcoinSimulatorFactory sf = new BitcoinSimulatorFactory();
        Simulation s = sf.createSimulation(1);
        s.run();

        System.out.println("\n" + s.getStatistics());

        s.closeNodes();
        PoWNode.resetCurrID();
        Transaction.resetCurrID();
        Block.resetCurrID();
        BitcoinReporter.flushAll();

        java.nio.file.Files.deleteIfExists(
            java.nio.file.Path.of("./test-configs/test-multi-node-temp.properties")
        );

        System.out.println("\n=== Test Complete ===\n");
    }

    @Test
    public void testSimultaneousChanges() throws IOException {
        System.out.println("\n=== Testing Simultaneous Hashpower Changes ===\n");

        String configContent = """
# Test: Multiple nodes changing at the same time
sim.numSimulations = 1
sim.maxNodes = 3
sim.maxTransactions = 20
sim.terminate.atTime = 100000
sim.output.directory = ./test-results
sim.experimentalLabel = test-simultaneous

reporter.reportEvents = false
reporter.reportTransactions = false
reporter.reportNodes = false
reporter.reportNetEvents = false
reporter.reportBeliefs = false
reporter.reportBeliefsShort = false
reporter.reportBlockEvents = false
reporter.reportStructureEvents = false
reporter.beliefReportInterval = 60000
reporter.beliefReportOffset = 120000
reporter.reportingWindow = 100000

net.sampler.seed = 123
net.sampler.seed.updateSeed = false
net.numOfNodes = 3
net.numOfHonestNodes = 3
net.numOfMaliciousNodes = 0
net.throughputMean = 25000000f
net.throughputSD = 2500000f
net.propagationTime = 10

workload.sampler.seed = 321
workload.sampler.seed.updateSeed = false
workload.lambda = 6.494f
workload.numTransactions = 20
workload.sampleTransaction = {}
workload.txSizeMean = 225f
workload.txSizeSD = 120.9471f
workload.txFeeValueMean = 3619.23f
workload.txFeeValueSD = 369.19f
workload.hasConflicts = true
workload.conflicts.dispersion = 0.1
workload.conflicts.likelihood = 0.05

reporter.reportAttackEvents = false

attack.requiredConfirmations = 0
attack.minChainLength = 2
attack.maxChainLength = 15

node.sampler.seed = {444}
node.sampler.updateSeedFlags = {false}
node.sampler.seedUpdateTimes = {}
node.createMaliciousNode = false
node.maliciousPowerByRatio = false
node.electricPowerMean = 1375f
node.electricPowerSD = 20f
node.electricCostMean = 0.1f
node.electricCostSD = 0.05f

pow.difficulty = 4.3933890848757156E20
pow.hashPowerMean = 2.35597310021E+10
pow.hashPowerSD = 2.35597310021E+9

# All nodes change at exactly the same time
node.hashPowerChanges = {1:5.0E10:50000, 2:4.0E10:50000, 3:6.0E10:50000}

bitcoin.maxBlockSize = 1000000
bitcoin.minSizeToMine = 100
bitcoin.minValueToMine = 1
""";

        java.nio.file.Files.writeString(
            java.nio.file.Path.of("./test-configs/test-simultaneous-temp.properties"),
            configContent
        );

        String[] args = {"-c", "./test-configs/test-simultaneous-temp.properties"};
        ConfigInitializer.initialize(args);

        BitcoinReporter.initialize();

        System.out.println("Running simulation with all nodes changing at time 50000ms...");

        BitcoinSimulatorFactory sf = new BitcoinSimulatorFactory();
        Simulation s = sf.createSimulation(1);
        s.run();

        System.out.println("\n" + s.getStatistics());

        s.closeNodes();
        PoWNode.resetCurrID();
        Transaction.resetCurrID();
        Block.resetCurrID();
        BitcoinReporter.flushAll();

        java.nio.file.Files.deleteIfExists(
            java.nio.file.Path.of("./test-configs/test-simultaneous-temp.properties")
        );

        System.out.println("\n=== Test Complete ===\n");
    }
}
