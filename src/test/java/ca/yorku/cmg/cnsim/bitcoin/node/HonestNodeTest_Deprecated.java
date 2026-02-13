package ca.yorku.cmg.cnsim.bitcoin.node;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import ca.yorku.cmg.cnsim.bitcoin.node.stubs.BitcoinNode4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.HonestNodeBehavior4Test;
import ca.yorku.cmg.cnsim.bitcoin.node.stubs.Simulation4Test;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.bitcoin.testutils.TestTutorial;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TransactionGroup;

class HonestNodeTest_Deprecated {
	
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
	@Disabled
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
        
        TestTutorial.step("#### Create block 2: {7,8,9,10}");
        block = new Block();
        block.addTransaction(new Transaction(7, 19, 10, 55));
        block.addTransaction(new Transaction(8, 20, 25, 20));
        block.addTransaction(new Transaction(9, 21, 105, 10));
        block.addTransaction(new Transaction(10, 22, 55, 100));
        Block keep_2 = block;
        
        
        TestTutorial.step("#### Create block 3: {11,12,13}");
        block = new Block();
        block.addTransaction(new Transaction(11, 23, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        block.addTransaction(new Transaction(13, 30, 505, 10));
        Block keep_3 = block;
       
        
        TestTutorial.step("#### Receive blocks 1,2,3 in that order.");
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
  
        node.event_NodeReceivesPropagatedTransaction(new Transaction(20, 25, 10, 505),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(21, 26, 250, 2),0);
        
        block = new Block();
        block.addTransaction(new Transaction(20, 25, 10, 505));
        block.addTransaction(new Transaction(21, 26, 250, 2));
        block.setParent(keep_2);
        Block keep_7 = block;
    	
        node.event_NodeReceivesPropagatedContainer(keep_7);
        
        assertTrue(node.isMining());
        assertEquals("{18,19}", node.getPool().printIDs(","), "Assertion 21");
        assertEquals("{19,18}", node.getMiningPool().printIDs(","), "Assertion 22");
        
        String[] expected_6 = {"BlockID,ParentID,BlockHeight,Transactions",
        		"5,4,4,{17,16}",
        		"7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        
        assertArrayEquals(expected_6, node.getStructure().printStructure(),"Assertion 23");

        Block keep_6 = (Block) node.getNextValidationEvent().getContainer();
        
        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(),20);
        
        String[] expected_7 = {"BlockID,ParentID,BlockHeight,Transactions",
        		"6,5,5,{19,18}",
        		"5,4,4,{17,16}",
        		"7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        
        assertArrayEquals(expected_7, node.getStructure().printStructure(),"Assertion 23");
        
        node.event_NodeReceivesPropagatedTransaction(new Transaction(22, 41, 10, 505),0); 
        node.event_NodeReceivesPropagatedTransaction(new Transaction(23, 42, 250, 2),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(24, 42, 250, 2),0);
        
        node.event_NodeReceivesPropagatedTransaction(new Transaction(32, 25, 10, 505),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(35, 26, 250, 2),0);
        
        assertTrue(node.isMining());
        assertEquals("{22,23,24,32,35}", node.getPool().printIDs(","), "Assertion 24");
        assertEquals("{23,24,35,22,32}", node.getMiningPool().printIDs(","), "Assertion 25");
        
        Block.setCurrID(8);
        block = new Block();
        block.addTransaction(new Transaction(22, 41, 10, 505));
        block.addTransaction(new Transaction(23, 42, 250, 2));;
        block.addTransaction(new Transaction(24, 42, 250, 2));
        block.setParent(keep_6);
        Block keep_8 = block;
        node.event_NodeReceivesPropagatedContainer(keep_8);
        
        Block.setCurrID(9);
        block = new Block();
        block.addTransaction(new Transaction(25, 25, 10, 505));
        block.addTransaction(new Transaction(26, 26, 250, 2));
        block.setParent(keep_4_2);
        Block keep_9_4 = block;
        
        Block.setCurrID(10);
        block = new Block();
        block.addTransaction(new Transaction(27, 25, 10, 505));
        block.addTransaction(new Transaction(28, 26, 250, 2));
        block.setParent(keep_9_4);
        Block keep_10 = block;
        
        node.event_NodeReceivesPropagatedContainer(keep_10);
       
        node.event_NodeReceivesPropagatedContainer(keep_9_4);

        Block.setCurrID(12);
        block = new Block();
        block.addTransaction(new Transaction(32, 25, 10, 505));
        block.addTransaction(new Transaction(33, 26, 250, 2));
        block.setParent(keep_3);
        Block keep_12_3 = block;

        block = new Block();
        block.addTransaction(new Transaction(34, 25, 10, 505));
        block.addTransaction(new Transaction(35, 26, 250, 2));
        block.setParent(keep_12_3);
        Block keep_13 = block;
        
        node.event_NodeReceivesPropagatedContainer(keep_13);
        
        node.event_NodeReceivesPropagatedTransaction(new Transaction(33, 26, 250, 2),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(34, 25, 10, 505),0);
        node.event_NodeReceivesPropagatedContainer(keep_12_3);
        
        Block.setCurrID(11);
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(30, 26, 250, 2));
        block.addTransaction(new Transaction(31, 26, 250, 2));
        block.setParent(keep_10);
        Block keep_11 = block;

        node.event_NodeReceivesPropagatedContainer(keep_11);
        
        Block.setCurrID(12);
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(30, 26, 250, 2));
        block.addTransaction(new Transaction(31, 26, 250, 2));
        block.setParent(keep_10);
        Block keep_12 = block;
        node.event_NodeReceivesPropagatedContainer(keep_12);
        
        Block.setCurrID(15);
        block = new Block();
        // 16 (overlaps with blocks: 11, 6, 3)
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(18, 41, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        block.setParent(keep_7);
        Block keep_16 = block;
        
        node.event_NodeReceivesPropagatedContainer(keep_16);
        
        block = new Block();
        // 16 (overlaps with blocks: 11, 6, 3)
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(18, 41, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        block.setParent(keep_8);
        Block keep_17_1 = block;
        
        node.event_NodeReceivesPropagatedContainer(keep_17_1);
        
        keep_17_1.setParent(keep_13);
        node.event_NodeReceivesPropagatedContainer(keep_17_1);
        
        keep_17_1.setParent(keep_9_4);
        node.event_NodeReceivesPropagatedContainer(keep_17_1);
        
        node.event_NodeReceivesPropagatedTransaction(new Transaction(36, 41, 10, 505),0); 
        node.event_NodeReceivesPropagatedTransaction(new Transaction(37, 42, 250, 2),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(27, 42, 250, 2),0);
        
        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(),20);
        
		TestTutorial.close();
    }
	
	
}
