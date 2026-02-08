package ca.yorku.cmg.cnsim.bitcoin.node;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import ca.yorku.cmg.cnsim.bitcoin.node.stubs.BitcoinNode4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.HonestNodeBehavior4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.Simulation4Test;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;

class HonestNodeTest {
	
	Simulation4Test sim;
	BitcoinNode4Test node;
	
	@BeforeEach
	void setUp() throws Exception {
		this.sim = new Simulation4Test(1);
		this.node = new BitcoinNode4Test(sim);
		this.node.setBehaviorStrategy(new HonestNodeBehavior4Test(this.node));
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
	 * Tests block arrivals externally or validated
	 */
	@Test
	void testBlockManagement() {
		//1
        Block block = new Block();
        block.addTransaction(new Transaction(1, 10, 10, 50));
        block.addTransaction(new Transaction(2, 11, 20, 25));
        block.addTransaction(new Transaction(3, 13, 100, 500));
        block.addTransaction(new Transaction(4, 14, 50, 10));
        block.addTransaction(new Transaction(5, 15, 70, 50));
        block.addTransaction(new Transaction(6, 16, 100, 50));
        Block keep_1 = block;
        
        block = new Block();
        block.addTransaction(new Transaction(7, 19, 10, 55));
        block.addTransaction(new Transaction(8, 20, 25, 20));
        block.addTransaction(new Transaction(9, 21, 105, 10));
        block.addTransaction(new Transaction(10, 22, 55, 100));
        Block keep_2 = block;
        
        block = new Block();
        block.addTransaction(new Transaction(11, 23, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        block.addTransaction(new Transaction(13, 30, 505, 10));
        Block keep_3 = block;
       
        node.event_NodeReceivesPropagatedContainer(keep_1);
        node.event_NodeReceivesPropagatedContainer(keep_2);
        node.event_NodeReceivesPropagatedContainer(keep_3);
        
        String[] expected_3 = {"BlockID,ParentID,BlockHeight,Transactions",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        assertArrayEquals(expected_3, node.getStructure().printStructure(),"Assertion 1");

        // Checking that there are no orphans
        String[] oxpected_1 = {"BlockID,ParentID,Transactions"};
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"Assertion 2");
		
                
        //4 --> 2
        block = new Block();
        block.addTransaction(new Transaction(14, 35, 10, 505));
        block.addTransaction(new Transaction(15, 40, 250, 2));
        block.setParent(keep_2);
        Block keep_4_2 = block;
    	
        node.event_NodeReceivesPropagatedContainer(keep_4_2);

        System.err.println(String.join("\n",node.getStructure().printStructure()));
        
        String[] expected_4 = {"BlockID,ParentID,BlockHeight,Transactions",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_4, node.getStructure().printStructure(),"Assertion 7");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"Assertion 8");
        assertEquals( "{3,4}",node.getStructure().printTips(","),"Assertion 9");
        assertFalse(node.isMining());
        assertEquals("", node.getPool().debugPrintPoolTx(), "Assertion 10");
       
        //5 --> 4
        node.event_NodeReceivesPropagatedTransaction(new Transaction(16, 41, 10, 505),10);
        
        assertTrue(node.isMining());
        assertEquals("{16}", node.getPool().printIDs(","), "Assertion 10");
        assertEquals("{16}", node.getMiningPool().printIDs(","), "Assertion 11");

        node.event_NodeReceivesPropagatedTransaction(new Transaction(17, 42, 250, 2),20);
        
        assertTrue(node.isMining());
        assertEquals("{16,17}", node.getPool().printIDs(","), "Assertion 12");
        assertEquals("{17,16}", node.getMiningPool().printIDs(","), "Assertion 13");
       
        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(),20);

        assertEquals("{}", node.getPool().printIDs(","), "Assertion 14");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Assertion 15");

        String[] expected_5 = {"BlockID,ParentID,BlockHeight,Transactions",
        		"5,4,4,{17,16}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        
        assertArrayEquals(expected_5, node.getStructure().printStructure(),"Assertion 16");
        
        //6 --> 5
        node.event_NodeReceivesPropagatedTransaction(new Transaction(18, 41, 10, 505),10);
        
        assertTrue(node.isMining());
        assertEquals("{18}", node.getPool().printIDs(","), "Assertion 17");
        assertEquals("{18}", node.getMiningPool().printIDs(","), "Assertion 18");

        node.event_NodeReceivesPropagatedTransaction(new Transaction(19, 42, 250, 2),20);
        
        assertTrue(node.isMining());
        assertEquals("{18,19}", node.getPool().printIDs(","), "Assertion 19");
        assertEquals("{19,18}", node.getMiningPool().printIDs(","), "Assertion 20");
       
        // CONTINUE
        // Step:
        // 1. Receive transactions 20, 21
        // 2. Receive propagated block with 20, 21
        // 3. See what happens.
        // Step:
        // 1. Continue the scenario, come up with more ideas.
        
        
        
		System.err.println(String.join("\n",node.getStructure().printStructure()));
    }
	
	
}
