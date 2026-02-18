package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.node.stubs.BitcoinNode4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.HonestNodeBehavior4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.Simulation4Test;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.bitcoin.testutils.BlockManagementTestHelper;
import ca.yorku.cmg.cnsim.bitcoin.testutils.TestTutorial;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * JUnit 5 test class for {@linkplain HiddenChainAttackBehavior}.
 * <p>
 * Tests contract violations and precondition enforcement for the hidden chain attack
 * strategy. Focuses on:
 * </p>
 *
 * @author Test Suite
 * @see HiddenChainAttackBehavior
 * @see HonestNodeBehavior
 */
public class HiddenChainAttackBehaviorFunctionalTest {

    private Simulation sim;
    private BitcoinNode node;
    private HiddenChainAttackBehavior behavior;
    private BlockManagementTestHelper helper;

    @BeforeEach
    public void setUp() {
        // Create test simulation and node
        sim = new Simulation4Test(1);
        node = new BitcoinNode4Test(sim);
        node.setHashPower(10);
        behavior = new HiddenChainAttackBehavior(node, new HonestNodeBehavior4Test(node));
        node.setBehaviorStrategy(behavior);
        helper = new BlockManagementTestHelper();
        Block.setCurrID(1);
    }
    
    
	/**
	 * Tests block arrivals externally or validated with hidden chain attack behavior.
	 * Executes the same block management scenarios as HonestNodeTest but with attack
	 * parameters configured. Attack is effectively disabled by never calling goToMonitoringState().
	 *
	 * Based on BlockchainTest#testBlockInsertionAndOrphanManagement fixture
	 * with extended honest node behavior testing.
	 */
	@Test
	void testHonestOperation_Idle() {
		behavior.setTargetTransaction(5); //Tx ID 5 exists in the fixture
		behavior.setAttackPower(30);
		behavior.setReleaseAdvantage(3);
		behavior.setStartAdvantage(0);

		String filename = "HiddenChainAttackBehaviorFunctionalTest-testHonestOperation_Idle.md"; 
		
        try {
            TestTutorial.start(filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        TestTutorial.step("# HiddenChainAttackBehaviorFunctionalTest#testHonestOperation_Idle Test Tutorial");
        TestTutorial.step("This test tests whether the HiddenChainAttackBehavior is operating properly under constant IDLE regime - i.e., MONITORING regime is not even enabled..");
        TestTutorial.step("Refer to src/test/resources/chainfixtures.drawio for visual.");
		TestTutorial.disableOutput();
		helper.executeBlockManagementTest(node);
		TestTutorial.enableOutput();
        TestTutorial.close();
	}

	/**
	 * Tests block arrivals externally or validated with hidden chain attack behavior.
	 * Executes the same block management scenarios as HonestNodeTest but with attack
	 * parameters configured (though attack is effectively disabled by setting
	 * targetTransaction=-1 and attackPower=0).
	 *
	 * Based on BlockchainTest#testBlockInsertionAndOrphanManagement fixture
	 * with extended honest node behavior testing.
	 */
	@Test
	void testHonestOperation_Monitoring() {
		behavior.setTargetTransaction(500); //Tx ID 500 does not exist for the fixture
		behavior.setAttackPower(10);
		behavior.setReleaseAdvantage(3);
		behavior.setStartAdvantage(0);

		behavior.goToMonitoringState();
		
		String filename = "HiddenChainAttackBehaviorFunctionalTest-testBlockManagement.md"; 
		
        try {
            TestTutorial.start(filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        TestTutorial.step("# HiddenChainAttackBehaviorFunctionalTest#testHonestOperation_Monitoring Test Tutorial");
        TestTutorial.step("This test tests whether the HiddenChainAttackBehavior is operating properly under constant IDLE regime - i.e., MONITORING regime is not even enabled..");
        TestTutorial.step("Refer to src/test/resources/chainfixtures.drawio for visual. Refer to ");
		TestTutorial.disableOutput();
		helper.executeBlockManagementTest(node);
		TestTutorial.enableOutput();
        TestTutorial.close();
	}


	/**
	 * Perform a simple attack on top of an existing fixture. Start immediately (advantage 0) and end when you are ahead by 3.
	 *
	 */
	@Test
	void testMaliciousOperation_SimpleAttack() {
		behavior.setTargetTransaction(40);
		behavior.setAttackPower(30); //Irrelevant
		behavior.setReleaseAdvantage(3);
		behavior.setStartAdvantage(0);

		String filename = "HiddenChainAttackBehaviorFunctionalTest-testMaliciousOperation_SimpleAttack.md";

        try {
            TestTutorial.start(filename);
        } catch (Exception e) {
            e.printStackTrace();
        }

        TestTutorial.step("# HiddenChainAttackBehaviorFunctionalTest#testMaliciousOperation_SimpleAttack Test Tutorial");
        TestTutorial.step("Perform a simple attack on top of an existing fixture. Start immediately (advantage 0) and end when you are ahead by 3.");
        TestTutorial.step("Refer to src/test/resources/chainfixtures.drawio for visual. Refer to ");
		TestTutorial.disableOutput();
		helper.executeBlockManagementTest(node);
		TestTutorial.enableOutput();

		printNodeStatus();
		// Initial state after fixture setup
		Assertions.assertEquals(HiddenChainAttackBehavior.State.IDLE, behavior.getAttackState(), "[A01]");
		Assertions.assertEquals(-7, behavior.getAdvantage(), "[A02]");
		Assertions.assertFalse(node.isMining(), "[A03]");

		behavior.goToMonitoringState();

        TestTutorial.step("#### Receive Transaction 40");
        TestTutorial.step("Nothing should happen");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(40, 10, 10, 50), 0);

        printNodeStatus();
        // After receiving TX 40 in MONITORING state - should remain in MONITORING
        Assertions.assertEquals(HiddenChainAttackBehavior.State.MONITORING, behavior.getAttackState(), "[A04]");
        Assertions.assertEquals(-7, behavior.getAdvantage(), "[A05]");


        TestTutorial.step("#### Create and receive target block 19: {40,41}");
        TestTutorial.step("Change should turn to attack. Advantage should raise to -1. Hidden chain includes everything from ");

        Block block = new Block();
        block.addTransaction(new Transaction(40, 10, 10, 50));
        block.addTransaction(new Transaction(41, 11, 20, 25));
        Block b19 = block;

        node.event_NodeReceivesPropagatedContainer(b19);


        printNodeStatus();
        // Block 19 contains target TX 40, attack initiated
        Assertions.assertEquals(HiddenChainAttackBehavior.State.ATTACKING, behavior.getAttackState(), "[A06]");
        Assertions.assertEquals(-1, behavior.getAdvantage(), "[A07]");
        Assertions.assertEquals("18,12,10,9,4,2,1", behavior.printHiddenChain(), "[A08]");
        Assertions.assertFalse(node.isMining(), "[A09]");


        TestTutorial.step("#### Receive Transactions 40, 41, 42, 43");
        TestTutorial.step("Pools should be enriched. Attack is still on.");

        node.event_NodeReceivesPropagatedTransaction(new Transaction(40, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(41, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(42, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(43, 10, 10, 50), 0);

        printNodeStatus();
        // Transactions 42, 43 added to pool (40, 41 already seen in block)
        Assertions.assertEquals(HiddenChainAttackBehavior.State.ATTACKING, behavior.getAttackState(), "[A10]");
        Assertions.assertEquals(-1, behavior.getAdvantage(), "[A11]");
        Assertions.assertTrue(node.isMining(), "[A12]");

        TestTutorial.step("#### Receive Block 21: {40, 43}");

        block = new Block();
        block.addTransaction(new Transaction(40, 10, 10, 50));
        block.addTransaction(new Transaction(43, 11, 20, 25));
        block.setParent(((Block) b19.getParent()).getParent());
        Block b21 = block;

        node.event_NodeReceivesPropagatedContainer(b21);

        printNodeStatus();
        // Block 21 creates orphan (not on main chain)
        Assertions.assertEquals(HiddenChainAttackBehavior.State.ATTACKING, behavior.getAttackState(), "[A13]");
        Assertions.assertEquals(-1, behavior.getAdvantage(), "[A14]");
        Assertions.assertTrue(node.isMining(), "[A15]");


        TestTutorial.step("#### Receive Transactions 44-47");
        TestTutorial.step("Pool enrichment attack is still on.");

        node.event_NodeReceivesPropagatedTransaction(new Transaction(44, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(45, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(46, 10, 10, 50), 0);


        printNodeStatus();
        // Pool enriched with 44, 45, 46
        Assertions.assertEquals(HiddenChainAttackBehavior.State.ATTACKING, behavior.getAttackState(), "[A16]");
        Assertions.assertEquals(-1, behavior.getAdvantage(), "[A17]");
        Assertions.assertTrue(node.isMining(), "[A18]");


        TestTutorial.step("#### Malicious Validate I");
        TestTutorial.step("Add the Block on top of 18.");

        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(), 0);

        printNodeStatus();
        // First malicious block (ID 20) added to hidden chain on top of 18
        Assertions.assertEquals(HiddenChainAttackBehavior.State.ATTACKING, behavior.getAttackState(), "[A19]");
        Assertions.assertEquals(0, behavior.getAdvantage(), "[A20]");
        Assertions.assertEquals("20,18,12,10,9,4,2,1", behavior.printHiddenChain(), "[A21]");
        Assertions.assertFalse(node.isMining(), "[A22]");


        TestTutorial.step("#### Malicious Validate II");
        TestTutorial.step("Add the Block on top of 20.");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(47, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(48, 10, 10, 50), 0);


        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(), 0);

        printNodeStatus();
        // Second malicious block (ID 22) added on top of 20
        Assertions.assertEquals(HiddenChainAttackBehavior.State.ATTACKING, behavior.getAttackState(), "[A23]");
        Assertions.assertEquals(1, behavior.getAdvantage(), "[A24]");
        Assertions.assertEquals("22,20,18,12,10,9,4,2,1", behavior.printHiddenChain(), "[A25]");
        Assertions.assertFalse(node.isMining(), "[A26]");


        TestTutorial.step("#### Malicious Validate III");
        TestTutorial.step("Add the Block on top of 22.");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(49, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(50, 10, 10, 50), 0);


        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(), 0);

        printNodeStatus();
        // Third malicious block (ID 23) added on top of 22
        Assertions.assertEquals(HiddenChainAttackBehavior.State.ATTACKING, behavior.getAttackState(), "[A27]");
        Assertions.assertEquals(2, behavior.getAdvantage(), "[A28]");
        Assertions.assertEquals("23,22,20,18,12,10,9,4,2,1", behavior.printHiddenChain(), "[A29]");
        Assertions.assertFalse(node.isMining(), "[A30]");


        TestTutorial.step("#### Malicious Validate IV");
        TestTutorial.step("Release.");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(51, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(52, 10, 10, 50), 0);

        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(), 0);

        printNodeStatus();
        // Fourth malicious block (ID 24) added; advantage reaches 3, attack released
        Assertions.assertEquals(HiddenChainAttackBehavior.State.IDLE, behavior.getAttackState(), "[A31]");
        Assertions.assertEquals(-11, behavior.getAdvantage(), "[A32]");
        Assertions.assertEquals("", behavior.printHiddenChain(), "[A33]");
        Assertions.assertFalse(node.isMining(), "[A34]");

        TestTutorial.step("#### Receive Parentless Block: {53, 54}");

        block = new Block();
        block.addTransaction(new Transaction(53, 10, 10, 50));
        block.addTransaction(new Transaction(54, 11, 20, 25));

        Block b25 = block;

        node.event_NodeReceivesPropagatedContainer(b25);

        printNodeStatus();
        // Parentless block becomes orphan
        Assertions.assertEquals(HiddenChainAttackBehavior.State.IDLE, behavior.getAttackState(), "[A35]");
        Assertions.assertEquals(-12, behavior.getAdvantage(), "[A36]");
        Assertions.assertFalse(node.isMining(), "[A37]");

        TestTutorial.step("#### Honest (now) Validate V");
        TestTutorial.step("Building on our dirty chain.");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(55, 10, 10, 50), 0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(56, 10, 10, 50), 0);


        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(), 0);

        printNodeStatus();
        // Final block mined on top of released chain
        Assertions.assertEquals(HiddenChainAttackBehavior.State.IDLE, behavior.getAttackState(), "[A38]");
        Assertions.assertEquals(-13, behavior.getAdvantage(), "[A39]");
        Assertions.assertFalse(node.isMining(), "[A40]");

        TestTutorial.close();
	}

	
	private void printNodeStatus() {
        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("State: " + behavior.getAttackState() + "\nAdvantage: " + behavior.getAdvantage() + "\nPower: " + behavior.getAttackPower() + "/" + node.getHashPower() + "\nHidden chain: " + behavior.printHiddenChain());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));
	}
	

}
