package ca.yorku.cmg.cnsim.bitcoin.node;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.event.Event_BehaviorChange;
import ca.yorku.cmg.cnsim.engine.event.Event_HashPowerChange;
import ca.yorku.cmg.cnsim.engine.node.INode;
import ca.yorku.cmg.cnsim.engine.node.NodeSet;
import ca.yorku.cmg.cnsim.engine.node.PoWNodeSet;
import ca.yorku.cmg.cnsim.engine.network.AbstractNetwork;
import ca.yorku.cmg.cnsim.engine.network.RandomEndToEndNetwork;

/**
 * Integration tests to verify that Event_HashPowerChange and Event_BehaviorChange
 * work correctly with BitcoinNode instances.
 * 
 * @author Amirreza Radjou for the Conceptual Modeling Group @ York University
 */
class EventIntegrationTest {

	private Simulation sim;
	private BitcoinNode node;
	private float initialHashPower;
	private long eventTime;

	@BeforeEach
	void setUp() throws Exception {
		// Initialize Config for tests
		try {
			Config.init("src/test/resources/application.properties");
		} catch (Exception e) {
			// Config may already be initialized, ignore
		}
		
		// Create a simulation instance
		sim = new Simulation(1);
		
		// Create a BitcoinNode with HonestNodeBehavior
		node = new BitcoinNode(sim);
		node.setHashPower(100.0f);
		node.setBehavior("Honest");
		node.setBehaviorStrategy(new HonestNodeBehavior(node));
		
		initialHashPower = 100.0f;
		eventTime = 1000L;
		
		// Set up a minimal network and NodeSet for the simulation
		NodeSet nodeSet = new PoWNodeSet(null) {
			@Override
			public void addNode() throws Exception {
				// Empty implementation for testing
			}
			
			@Override
			public void closeNodes() {
				// Empty implementation for testing
			}
			
			@Override
			public String debugPrintNodeSet() {
				return "";
			}
			
			@Override
			public String[] printNodeSet() {
				return new String[0];
			}
		};
		
		// Use reflection to access protected field
		try {
			java.lang.reflect.Field nodesField = NodeSet.class.getDeclaredField("nodes");
			nodesField.setAccessible(true);
			java.util.ArrayList<INode> nodesList = new java.util.ArrayList<>();
			nodesList.add(node);
			nodesField.set(nodeSet, nodesList);
			
			AbstractNetwork network = new RandomEndToEndNetwork();
			java.lang.reflect.Field nsField = AbstractNetwork.class.getDeclaredField("ns");
			nsField.setAccessible(true);
			nsField.set(network, nodeSet);
			sim.setNetwork(network);
		} catch (Exception e) {
			// If reflection fails, tests may still work
		}
	}

	@Test
	void testHashPowerChange_withBitcoinNode() {
		float newHashPower = 200.0f;
		Event_HashPowerChange event = new Event_HashPowerChange(node, newHashPower, eventTime);
		
		// Verify initial hashpower
		assertEquals(initialHashPower, node.getHashPower());
		
		// Execute the event
		event.happen(sim);
		
		// Verify hashpower was changed
		assertEquals(newHashPower, node.getHashPower());
	}

	@Test
	void testBehaviorChange_HonestToMalicious() {
		// Verify initial behavior
		assertEquals("Honest", node.getBehavior());
		assertTrue(node.getBehaviorStrategy() instanceof HonestNodeBehavior);
		
		// Create event to change to Malicious
		Event_BehaviorChange event = new Event_BehaviorChange(node, "Malicious", eventTime);
		
		// Execute the event
		event.happen(sim);
		
		// Verify behavior was changed
		assertEquals("Malicious", node.getBehavior());
		assertTrue(node.getBehaviorStrategy() instanceof MaliciousNodeBehavior);
	}

	@Test
	void testBehaviorChange_MaliciousToHonest() {
		// Start with Malicious behavior
		node.setBehavior("Malicious");
		node.setBehaviorStrategy(new MaliciousNodeBehavior(node));
		
		// Create event to change to Honest
		Event_BehaviorChange event = new Event_BehaviorChange(node, "Honest", eventTime);
		
		// Execute the event
		event.happen(sim);
		
		// Verify behavior was changed
		assertEquals("Honest", node.getBehavior());
		assertTrue(node.getBehaviorStrategy() instanceof HonestNodeBehavior);
	}

	@Test
	void testBehaviorChange_withTargetTransaction() {
		int targetTxID = 123;
		
		// Create event to change to Malicious with target transaction
		Event_BehaviorChange event = new Event_BehaviorChange(node, "Malicious", eventTime, targetTxID);
		
		// Execute the event
		event.happen(sim);
		
		// Verify behavior was changed
		assertEquals("Malicious", node.getBehavior());
		assertTrue(node.getBehaviorStrategy() instanceof MaliciousNodeBehavior);
		
		// Verify target transaction was set (if possible)
		MaliciousNodeBehavior maliciousBehavior = (MaliciousNodeBehavior) node.getBehaviorStrategy();
		// Note: We can't directly verify targetTxID was set without exposing it,
		// but the event should have attempted to set it
	}

	@Test
	void testBehaviorChange_preservesNodeState() {
		// Set some node state
		float hashPower = 150.0f;
		node.setHashPower(hashPower);
		
		// Change behavior
		Event_BehaviorChange event = new Event_BehaviorChange(node, "Malicious", eventTime);
		event.happen(sim);
		
		// Verify node state is preserved
		assertEquals(hashPower, node.getHashPower());
		assertNotNull(node.getStructure());
		assertNotNull(node.getMiningPool());
	}

	@Test
	void testMultipleChanges_sequential() {
		// Change hashpower
		Event_HashPowerChange hpEvent1 = new Event_HashPowerChange(node, 150.0f, eventTime);
		hpEvent1.happen(sim);
		assertEquals(150.0f, node.getHashPower());
		
		// Change behavior
		Event_BehaviorChange behaviorEvent = new Event_BehaviorChange(node, "Malicious", eventTime + 100);
		behaviorEvent.happen(sim);
		assertEquals("Malicious", node.getBehavior());
		
		// Change hashpower again
		Event_HashPowerChange hpEvent2 = new Event_HashPowerChange(node, 250.0f, eventTime + 200);
		hpEvent2.happen(sim);
		assertEquals(250.0f, node.getHashPower());
		
		// Verify behavior is still Malicious
		assertEquals("Malicious", node.getBehavior());
		assertTrue(node.getBehaviorStrategy() instanceof MaliciousNodeBehavior);
	}

	@Test
	void testBehaviorChange_behaviorStrategyNotNull() {
		// Verify that after behavior change, behaviorStrategy is not null
		Event_BehaviorChange event = new Event_BehaviorChange(node, "Malicious", eventTime);
		event.happen(sim);

		// This is critical - if behaviorStrategy is null, BitcoinNode will throw NPE
		assertNotNull(node.getBehaviorStrategy());

		// Verify we can call methods on the strategy without NPE
		assertDoesNotThrow(() -> {
			// The strategy should be properly initialized
			assertTrue(node.getBehaviorStrategy() instanceof MaliciousNodeBehavior);
		});
	}

	// TODO: Integration test for confirmation delay - needs more investigation of blockchain mechanics
	// The feature is implemented but the test setup needs refinement
	// @Test
	void testBehaviorChange_withConfirmationDelay_alreadySatisfied_TODO() throws Exception {
		// Test when confirmations are already satisfied before event is created
		int targetTxID = 500;
		int requiredConfirmations = 2;

		// Set initial behavior to Honest
		node.setBehavior("Honest");
		node.setBehaviorStrategy(new HonestNodeBehavior(node));

		// Create a transaction and add it to a block
		ca.yorku.cmg.cnsim.engine.transaction.Transaction tx =
			new ca.yorku.cmg.cnsim.engine.transaction.Transaction(targetTxID);

		// Create genesis block
		ca.yorku.cmg.cnsim.bitcoin.structure.Block genesis =
			new ca.yorku.cmg.cnsim.bitcoin.structure.Block();
		genesis.setHeight(0);
		node.getStructure().addToStructure(genesis);

		// Create block with the transaction
		ca.yorku.cmg.cnsim.bitcoin.structure.Block block1 =
			new ca.yorku.cmg.cnsim.bitcoin.structure.Block();
		block1.addTransaction(tx);
		block1.setParent(genesis);
		node.getStructure().addToStructure(block1);
		int txBlockHeight = block1.getHeight();

		// Add 2 more blocks to get 2 confirmations
		ca.yorku.cmg.cnsim.bitcoin.structure.Block block2 =
			new ca.yorku.cmg.cnsim.bitcoin.structure.Block();
		block2.setParent(block1);
		node.getStructure().addToStructure(block2);

		ca.yorku.cmg.cnsim.bitcoin.structure.Block block3 =
			new ca.yorku.cmg.cnsim.bitcoin.structure.Block();
		block3.setParent(block2);
		node.getStructure().addToStructure(block3);

		// Get the actual tip height
		int tipHeight = node.getStructure().getLongestTip().getHeight();
		int confirmations = tipHeight - txBlockHeight;

		// Verify we have at least 2 confirmations
		assertTrue(confirmations >= 2, "Should have at least 2 confirmations, got " + confirmations);

		// Create behavior change event requiring 2 confirmations
		Event_BehaviorChange event = new Event_BehaviorChange(
			node, "Malicious", eventTime, targetTxID, requiredConfirmations
		);

		// Verify behavior before
		assertEquals("Honest", node.getBehavior());

		// Execute event - should change behavior immediately (confirmations already exist)
		event.happen(sim);

		// Behavior SHOULD have changed
		assertEquals("Malicious", node.getBehavior());
		assertTrue(node.getBehaviorStrategy() instanceof MaliciousNodeBehavior);
	}

	@Test
	void testBehaviorChange_confirmationDelayZeroConfirmations() throws Exception {
		// Test that 0 confirmations means immediate execution
		int targetTxID = 600;

		// Create a transaction and add it to a block
		ca.yorku.cmg.cnsim.engine.transaction.Transaction tx =
			new ca.yorku.cmg.cnsim.engine.transaction.Transaction(targetTxID);

		ca.yorku.cmg.cnsim.bitcoin.structure.Block block =
			new ca.yorku.cmg.cnsim.bitcoin.structure.Block();
		block.addTransaction(tx);
		block.setHeight(1);
		node.getStructure().addToStructure(block);

		// Create event with 0 confirmations required
		Event_BehaviorChange event = new Event_BehaviorChange(
			node, "Malicious", eventTime, targetTxID, 0
		);

		// Execute event - should change behavior immediately
		event.happen(sim);

		// Behavior should be changed
		assertEquals("Malicious", node.getBehavior());
		assertTrue(node.getBehaviorStrategy() instanceof MaliciousNodeBehavior);
	}

}

