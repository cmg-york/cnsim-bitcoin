package ca.yorku.cmg.cnsim.bitcoin.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import ca.yorku.cmg.cnsim.bitcoin.node.stubs.BitcoinNode4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.HonestNodeBehavior4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.Simulation4Test;
import ca.yorku.cmg.cnsim.bitcoin.testutils.BlockManagementTestHelper;
import ca.yorku.cmg.cnsim.bitcoin.testutils.TestTutorial;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;

class HonestNodeTest {

	Simulation4Test sim;
	BitcoinNode4Test node;
	BlockManagementTestHelper helper;

	@BeforeEach
	void setUp() throws Exception {
		this.sim = new Simulation4Test(1);
		this.node = new BitcoinNode4Test(sim);
		this.node.setBehaviorStrategy(new HonestNodeBehavior4Test(this.node));
		this.helper = new BlockManagementTestHelper();
	}

	/**
	 * Tests pool and mining pool management, as well as status (mining vs. non-mining)
	 */
	@Test
	@Disabled
	void testPoolManagement() {
		//node.event_NodeReceivesClientTransaction(new Transaction(1, 10, 10, 50), 10);
		node.event_NodeReceivesPropagatedTransaction(new Transaction(2, 10, 10, 50), 10);
	}

	/** 
	 * Tries to reproduce cases in which the node validates a block but the pool is conflicting.  
	 * 
	 */
	@Test
	void testConflictingPool() {
	
	}
	
	
	/**
	 * Tests block arrivals externally or validated
	 * Based on BlockchainTest#testBlockInsertionAndOrphanManagement fixture
	 * with extended honest node behavior testing
	 */
	@Test
	void testBlockManagement() {
		String filename = "HonestNodeTest-testBlockManagement.md"; 
		
        try {
            TestTutorial.start(filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        TestTutorial.step("# HonestNodeTest-testBlockManagement Test Tutorial");
        TestTutorial.step("Tests block arrival and validation is handled correctly by node.");
        TestTutorial.step("Refer to src/test/resources/chainfixtures.drawio for visual.");

        helper.executeBlockManagementTest(node);
		
        TestTutorial.close();
		
		
	}


}
