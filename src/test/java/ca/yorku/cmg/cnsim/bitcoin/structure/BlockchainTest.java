/**
 *
 */
package ca.yorku.cmg.cnsim.bitcoin.structure;

import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.bitcoin.structure.Blockchain;
import ca.yorku.cmg.cnsim.bitcoin.testutils.TestTutorial;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

/**
 * @author Enterprise Systems Group (ESG) @ York University
 */
class BlockchainTest {
    private Blockchain blockchain;

    /**
     * Sets up the test environment before each test method.
     * <p>
     * This method initializes a new instance of the Blockchain class and resets
     * the static `currID` field of the Block class to 1 to ensure a consistent
     * starting state for each test.
     */
    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
        //Block.currID = 1;
        Block.setCurrID(1);
    }

    /**
     * Tests the overlap functionality between blocks.
     * <p>
     * This test verifies the overlap logic between transactions in different blocks.
     * It ensures that blocks with overlapping transactions are correctly identified.
     * <p>
     * The test covers the following scenarios:
     * 1. Two blocks with overlapping transactions.
     * 2. Two blocks without overlapping transactions.
     */
    @DisplayName("testBlockTransactionOverlap")
    @Test
    void testBlockTransactionOverlap() {
        // Scenario 1: Two blocks with overlapping transactions
        Block block1 = new Block();
        block1.addTransaction(new Transaction(1, 10, 10, 50));
        block1.addTransaction(new Transaction(2, 11, 20, 25));
        block1.addTransaction(new Transaction(3, 13, 100, 500));

        Block block2 = new Block();
        block2.addTransaction(new Transaction(3, 10, 10, 50));
        block2.addTransaction(new Transaction(4, 11, 20, 25));
        block2.addTransaction(new Transaction(5, 13, 100, 500));

        // Assert that the two blocks overlap
        assertTrue(block2.overlapsWith(block1), "Block2 should overlap with Block1");
        assertTrue(block1.overlapsWith(block2), "Block1 should overlap with Block2");

        // Scenario 2: Two blocks without overlapping transactions
        block2 = new Block();
        block2.addTransaction(new Transaction(4, 10, 10, 50));
        block2.addTransaction(new Transaction(5, 11, 20, 25));
        block2.addTransaction(new Transaction(6, 13, 100, 500));

        // Assert that the two blocks do not overlap
        assertFalse(block2.overlapsWith(block1), "Block2 should not overlap with Block1");
        assertFalse(block1.overlapsWith(block2), "Block1 should not overlap with Block2");
    }

    /**
     * Tests the {@linkplain cmg.cnsim.bitcoin.Blockchain#addToStructure(Block)} method.
     * This method verifies the correct addition of blocks to the blockchain,
     * checking both the blockchain structure and orphan management.
     * <p>
     * The following scenarios are tested:
     * 1. Adding a single block to the blockchain.
     * 2. Adding multiple blocks and verifying parent-child relationships.
     * 3. Handling orphan blocks correctly when their parents are later added to the blockchain.
     * 4. Ensuring the blockchain structure is maintained accurately after each addition.
     * 5. Checking the list of tips (blocks with no children) after various additions.
     */
    @Test
    final void testBlockInsertionAndOrphanManagement() {
    	
        //1
        Block block = new Block();
        block.addTransaction(new Transaction(1, 10, 10, 50));
        block.addTransaction(new Transaction(2, 11, 20, 25));
        block.addTransaction(new Transaction(3, 13, 100, 500));
        block.addTransaction(new Transaction(4, 14, 50, 10));
        block.addTransaction(new Transaction(5, 15, 70, 50));
        block.addTransaction(new Transaction(6, 16, 100, 50));
        blockchain.addToStructure(block);

        // Checking the structure after adding the first block
        String[] expected_1 = {"BlockID,ParentID,BlockHeight,Transactions",
                "1,-1,1,{1,2,3,4,5,6}"};
        assertArrayEquals(expected_1, blockchain.printStructure(),"Assertion 1");

        // Checking that there are no orphans
        String[] oxpected_1 = {"BlockID,ParentID,Transactions"};
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 2");

        
        //2 --> 1
        block = new Block();
        block.addTransaction(new Transaction(7, 19, 10, 55));
        block.addTransaction(new Transaction(8, 20, 25, 20));
        block.addTransaction(new Transaction(9, 21, 105, 10));
        block.addTransaction(new Transaction(10, 22, 55, 100));
        Block keep_2 = block;
        blockchain.addToStructure(block);

        String[] expected_2 = {"BlockID,ParentID,BlockHeight,Transactions",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_2, blockchain.printStructure(),"Assertion 3");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 4");


        //3 --> 2
        block = new Block();
        block.addTransaction(new Transaction(11, 23, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        block.addTransaction(new Transaction(13, 30, 505, 10));
        Block keep_3 = block;
        blockchain.addToStructure(block);

        String[] expected_3 = {"BlockID,ParentID,BlockHeight,Transactions",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_3, blockchain.printStructure(),"Assertion 5");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 6");


        //4 --> 2
        block = new Block();
        block.addTransaction(new Transaction(14, 35, 10, 505));
        block.addTransaction(new Transaction(15, 40, 250, 2));
        block.setParent(keep_2);
        Block keep_4_2 = block;
        blockchain.addToStructure(block);

        String[] expected_4 = {"BlockID,ParentID,BlockHeight,Transactions",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_4, blockchain.printStructure(),"Assertion 7");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 8");
        assertEquals(blockchain.printTips(","), "{3,4}","Assertion 9");


        //5 --> 4
        block = new Block();
        block.addTransaction(new Transaction(16, 41, 10, 505));
        block.addTransaction(new Transaction(17, 42, 250, 2));
        assertNull(block.getParent());
        blockchain.addToStructure(block);

        String[] expected_5 = {"BlockID,ParentID,BlockHeight,Transactions",
                "5,4,4,{16,17}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_5, blockchain.printStructure(),"Assertion 10");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 11");
        assertEquals(blockchain.printTips(","), "{3,5}","Assertion 12");


        //6 --> 5
        block = new Block();
        block.addTransaction(new Transaction(18, 41, 10, 505));
        block.addTransaction(new Transaction(19, 42, 250, 2));
        blockchain.addToStructure(block);

        String[] expected_6 = {"BlockID,ParentID,BlockHeight,Transactions",
                "6,5,5,{18,19}",
                "5,4,4,{16,17}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_6, blockchain.printStructure(),"Assertion 13");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 14");
        assertEquals(blockchain.printTips(","), "{3,6}","Assertion 15");


        //7 --> 2
        block = new Block();
        block.addTransaction(new Transaction(20, 25, 10, 505));
        block.addTransaction(new Transaction(21, 26, 250, 2));
        block.setParent(keep_2);
        blockchain.addToStructure(block);

        String[] expected_7 = {"BlockID,ParentID,BlockHeight,Transactions",
                "6,5,5,{18,19}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_7, blockchain.printStructure(),"Assertion 16");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 17");
        assertEquals("{3,6,7}", blockchain.printTips(","),"Assertion 18");


        //8 --> 6
        block = new Block();
        block.addTransaction(new Transaction(22, 41, 10, 505));
        block.addTransaction(new Transaction(23, 42, 250, 2));
        block.addTransaction(new Transaction(24, 42, 250, 2));
        blockchain.addToStructure(block);

        String[] expected_8 = {"BlockID,ParentID,BlockHeight,Transactions",
                "8,6,6,{22,23,24}",
                "6,5,5,{18,19}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_8, blockchain.printStructure(),"Assertion 19");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 20");
        assertEquals("{7,3,8}", blockchain.printTips(","),"Assertion 21");


        // Not added in the blockchain
        //9 --> 4
        block = new Block();
        block.addTransaction(new Transaction(25, 25, 10, 505));
        block.addTransaction(new Transaction(26, 26, 250, 2));
        block.setParent(keep_4_2);
        Block keep_9_4 = block;


        // Orphan Block
        //10 --> 9
        block = new Block();
        block.addTransaction(new Transaction(27, 25, 10, 505));
        block.addTransaction(new Transaction(28, 26, 250, 2));
        block.setParent(keep_9_4);
        Block keep_10_9 = block;
        blockchain.addToStructure(block);

        String[] expected_9 = {"BlockID,ParentID,BlockHeight,Transactions",
                "8,6,6,{22,23,24}",
                "6,5,5,{18,19}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_9, blockchain.printStructure(),"Assertion 22");
        assertEquals("{7,3,8}", blockchain.printTips(","),"Assertion 23");

        String[] oxpected_2 = {"BlockID,ParentID,Transactions",
                "10,9,{27,28}"
        };
        assertArrayEquals(oxpected_2, blockchain.printOrphans(),"Assertion 24");


        //11 --> 10
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(30, 26, 250, 2));
        block.addTransaction(new Transaction(31, 26, 250, 2));
        block.setParent(keep_10_9);
        blockchain.addToStructure(block);

        assertArrayEquals(expected_9, blockchain.printStructure(),"Assertion 25");
        assertEquals("{7,3,8}", blockchain.printTips(","),"Assertion 26");

        String[] oxpected_3 = {"BlockID,ParentID,Transactions",
                "10,9,{27,28}",
                "11,10,{29,30,31}"
        };
        assertArrayEquals(oxpected_3, blockchain.printOrphans(),"Assertion 27");

        //Plug 9 now. Orphans must be placed back.
        blockchain.addToStructure(keep_9_4);

        String[] expected_10 = {"BlockID,ParentID,BlockHeight,Transactions",
                "11,10,6,{29,30,31}",
                "8,6,6,{22,23,24}",
                "10,9,5,{27,28}",
                "6,5,5,{18,19}",
                "9,4,4,{25,26}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        assertArrayEquals(expected_10, blockchain.printStructure(),"Assertion 28");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 29");
        assertEquals("{7,3,8,11}", blockchain.printTips(","),"Assertion 30");
        //assertEquals("{3,8,11}", blockchain.printTips(","));

        // Not added in the blockchain
        //12 --> 3
        block = new Block();
        block.addTransaction(new Transaction(32, 25, 10, 505));
        block.addTransaction(new Transaction(33, 26, 250, 2));
        block.setParent(keep_3);
        Block keep_12_3 = block;


        // Orphan Block
        //13 --> 12
        block = new Block();
        block.addTransaction(new Transaction(34, 25, 10, 505));
        block.addTransaction(new Transaction(35, 26, 250, 2));
        block.setParent(keep_12_3);
        blockchain.addToStructure(block);

        assertArrayEquals(expected_10, blockchain.printStructure(),"Assertion 31");
        assertEquals("{7,3,8,11}", blockchain.printTips(","),"Assertion 32");
        
        String[] oxpected_4 = {"BlockID,ParentID,Transactions",
                "13,12,{34,35}"
        };
        assertArrayEquals(oxpected_4, blockchain.printOrphans(),"Assertion 33");

        //Plug 12 now. Orphans must be placed back.
        blockchain.addToStructure(keep_12_3);

        String[] expected_11 = {"BlockID,ParentID,BlockHeight,Transactions",
                "11,10,6,{29,30,31}",
                "8,6,6,{22,23,24}",
                "13,12,5,{34,35}",
                "10,9,5,{27,28}",
                "6,5,5,{18,19}",
                "12,3,4,{32,33}",
                "9,4,4,{25,26}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        assertArrayEquals(expected_11, blockchain.printStructure(),"Assertion 34");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 35");
        assertEquals("{7,8,11,13}", blockchain.printTips(","),"Assertion 36");
        

        //14 (overlaps with block 4)
        block = new Block();
        block.addTransaction(new Transaction(14, 25, 10, 505));
        block.addTransaction(new Transaction(36, 26, 250, 2));
        Block keep_14 = block;


        //15 --> 14
        block = new Block();
        block.addTransaction(new Transaction(37, 25, 10, 505));
        block.addTransaction(new Transaction(38, 26, 250, 2));
        block.setParent(keep_14);
        blockchain.addToStructure(block);

        assertArrayEquals(expected_11, blockchain.printStructure(),"Assertion 37");
        String[] oxpected_5 = {"BlockID,ParentID,Transactions",
                "15,14,{37,38}"
        };
        assertArrayEquals(oxpected_5, blockchain.printOrphans(),"Assertion 38");

        //It will be added to tip 13; because block 14 doesn't have overlaps with this tip
        blockchain.addToStructure(keep_14);

        String[] expected_12 = {"BlockID,ParentID,BlockHeight,Transactions",
                "15,14,7,{37,38}",
                "14,13,6,{14,36}",
                "11,10,6,{29,30,31}",
                "8,6,6,{22,23,24}",
                "13,12,5,{34,35}",
                "10,9,5,{27,28}",
                "6,5,5,{18,19}",
                "12,3,4,{32,33}",
                "9,4,4,{25,26}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 39");
        assertArrayEquals(expected_12, blockchain.printStructure(),"Assertion 40");
        assertEquals("{11,8,7,15}", blockchain.printTips(","),"Assertion 41");


        // 16 (overlaps with blocks: 11, 6, 3)
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(18, 41, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        blockchain.addToStructure(block);

        String[] expected_13 = {"BlockID,ParentID,BlockHeight,Transactions",
                "15,14,7,{37,38}",
                "14,13,6,{14,36}",
                "11,10,6,{29,30,31}",
                "8,6,6,{22,23,24}",
                "13,12,5,{34,35}",
                "10,9,5,{27,28}",
                "6,5,5,{18,19}",
                "16,7,4,{29,18,12}",
                "12,3,4,{32,33}",
                "9,4,4,{25,26}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 41");
        assertArrayEquals(expected_13, blockchain.printStructure(),"Assertion 42");
        assertEquals("{15,11,8,16}", blockchain.printTips(","),"Assertion 43");


        //17 (overlaps with blocks: 13, 5, 11, 7)
        // It has overlaps with all tips. Will be added to 
        block = new Block();
        block.addTransaction(new Transaction(34, 25, 10, 505));
        block.addTransaction(new Transaction(16, 41, 10, 505));
        block.addTransaction(new Transaction(31, 25, 250, 2));
        block.addTransaction(new Transaction(20, 25, 250, 2));
        blockchain.addToStructure(block);

        String[] expected_14 = {"BlockID,ParentID,BlockHeight,Transactions",
                "15,14,7,{37,38}",
                "14,13,6,{14,36}",
                "11,10,6,{29,30,31}",
                "8,6,6,{22,23,24}",
                "13,12,5,{34,35}",
                "10,9,5,{27,28}",
                "6,5,5,{18,19}",
                "16,7,4,{29,18,12}",
                "12,3,4,{32,33}",
                "9,4,4,{25,26}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "17,-1,1,{34,16,31,20}",
                "1,-1,1,{1,2,3,4,5,6}"};
        

        assertArrayEquals(expected_14, blockchain.printStructure(),"Assertion 44");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 45");
        assertEquals("{15,11,8,16,17}", blockchain.printTips(","),"Assertion 46");


        // 18 (Overlaps with first block)
        // Will be added on top of 17 / the only place without conflicts
        block = new Block();
        block.addTransaction(new Transaction(1, 25, 10, 505));
        blockchain.addToStructure(block);
        
        String[] expected_15 = {"BlockID,ParentID,BlockHeight,Transactions",
                "15,14,7,{37,38}",
                "14,13,6,{14,36}",
                "11,10,6,{29,30,31}",
                "8,6,6,{22,23,24}",
                "13,12,5,{34,35}",
                "10,9,5,{27,28}",
                "6,5,5,{18,19}",
                "16,7,4,{29,18,12}",
                "12,3,4,{32,33}",
                "9,4,4,{25,26}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "18,17,2,{1}",
                "2,1,2,{7,8,9,10}",
                "17,-1,1,{34,16,31,20}",
                "1,-1,1,{1,2,3,4,5,6}"};
        
        assertArrayEquals(expected_15, blockchain.printStructure(),"Assertion 47");
        assertArrayEquals(oxpected_1, blockchain.printOrphans(),"Assertion 48");
        assertEquals("{15,11,8,16,18}", blockchain.printTips(","),"Assertion 49");
    }

    
    
    
    
    @Test
    final void testBelief() {
    	
    	try {
			TestTutorial.start("Blockchain-testBelief.md");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	TestTutorial.step("# Blockchain#testBelief Test Tutorial");
    	
    	TestTutorial.step("#### Create block {1,2,3,4,5,6} and add it to structure");
    	
        //1
        Block block = new Block();
        block.addTransaction(new Transaction(1, 10, 10, 50));
        block.addTransaction(new Transaction(2, 11, 20, 25));
        block.addTransaction(new Transaction(3, 13, 100, 500));
        block.addTransaction(new Transaction(4, 14, 50, 10));
        block.addTransaction(new Transaction(5, 15, 70, 50));
        block.addTransaction(new Transaction(6, 16, 100, 50));
        Block keep_1 = block;
        blockchain.addToStructure(block);

        TestTutorial.step("Blockchain makes it point to -1 (which is not really a block). Here is state of the blockchain:");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        
        TestTutorial.step("At this point all transactions are believed:");
        
        String beliefBool = "";
        String beliefFloat = "";
        
        for (int i=1; i<=6; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        	
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        	beliefFloat += ", (" + i + ":" + blockchain.transactionBelief(i) + ")";
        }

        TestTutorial.code(beliefBool.substring(2) + "\n\n" + beliefFloat.substring(2));
        
        
        
        
        //2 --> 1
        block = new Block();
        block.addTransaction(new Transaction(7, 19, 10, 55));
        block.addTransaction(new Transaction(8, 20, 25, 20));
        block.addTransaction(new Transaction(9, 21, 105, 10));
        block.addTransaction(new Transaction(10, 22, 55, 100));
        Block keep_2 = block;
        blockchain.addToStructure(block);

        TestTutorial.step("#### Create block {7,8,9,10} and add on top of the first block");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.step("There is only one tip and that is 2.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));
        
        //3 --> 2
        block = new Block();
        block.addTransaction(new Transaction(11, 23, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        block.addTransaction(new Transaction(13, 30, 505, 10));
        Block keep_3 = block;
        blockchain.addToStructure(block);

        TestTutorial.step("#### Create block {11,12,13} and add it on top of the second block");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.step("There is only one tip and that is 3 (the current block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("All transactions are still believed.");
        for (int i=1; i<=13; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        	beliefFloat += ", (" + i + ":" + blockchain.transactionBelief(i) + ")";
        }

        TestTutorial.code(beliefBool.substring(2) + "\n\n" + beliefFloat.substring(2));
        
        //4 --> 2
        block = new Block();
        block.addTransaction(new Transaction(14, 35, 10, 505));
        block.addTransaction(new Transaction(15, 40, 250, 2));
        block.setParent(keep_2);
        Block keep_4_2 = block;
        blockchain.addToStructure(block);

        
        // ASSERTIONS
        for (int i=14; i<=15; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        for (int i=11; i<=13; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### FORK: Create block {14,15} and add it on top of the second block too.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.step("There are two tips now 4 (this block) and 3 (the previous block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed now.");
        for (int i=1; i<=15; i++) {
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        	beliefFloat += ", (" + i + ":" + blockchain.transactionBelief(i) + ")";
        }
        TestTutorial.code(beliefBool.substring(2) + "\n\n" + beliefFloat.substring(2));
        TestTutorial.step("Blockchain has made an arbitrary selection and believes only what is pointing at block 3. That means that transactions in block 4 are not believed.");

        
        
        //5 --> 4
        block = new Block();
        block.addTransaction(new Transaction(16, 41, 10, 505));
        block.addTransaction(new Transaction(17, 42, 250, 2));
        blockchain.addToStructure(block);

        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=15; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create block #5 {16,17} and add it on top of the Block #4.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.step("There are two tips now: 5 (this block) and 3 (the earlier block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed now. Transactions of block 3 are not believed anymore. It is a **stale** block.");
        beliefBool = "";
        beliefFloat = "";
        for (int i=1; i<=17; i++) {
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        	beliefFloat += ", (" + i + ":" + blockchain.transactionBelief(i) + ")";
        }
        TestTutorial.code(beliefBool.substring(2) + "\n\n" + beliefFloat.substring(2));
        TestTutorial.step("Blockchain has made an arbitrary selection and believes only what is pointing at block 3. That means that transactions in block 4 are not believed.");
        
        
        //6 --> 5
        block = new Block();
        block.addTransaction(new Transaction(18, 41, 10, 505));
        block.addTransaction(new Transaction(19, 42, 250, 2));
        blockchain.addToStructure(block);

        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create block #6 {18,19} and add it on top of the Block #5.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.step("There are two tips now: 6 (this block) and 3 (the short stale block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed now. Transactions of block 3 still not believed.");
        beliefBool = "";
        beliefFloat = "";
        for (int i=1; i<=19; i++) {
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        	beliefFloat += ", (" + i + ":" + blockchain.transactionBelief(i) + ")";
        }
        TestTutorial.code(beliefBool.substring(2) + "\n\n" + beliefFloat.substring(2));

        
        
        //7 --> 2
        block = new Block();
        block.addTransaction(new Transaction(20, 25, 10, 505));
        block.addTransaction(new Transaction(21, 26, 250, 2));
        block.setParent(keep_2);
        blockchain.addToStructure(block);

        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create block #6 {20,21} and add it on top of the Block #2.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.step("There are three tips: 7 (this block), 6 (previous block) and 3 (the short stale block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed now. Transactions of blocks 2 and 3 are not believed, not being part of the longest chain.");
        beliefBool = "";
        beliefFloat = "";
        for (int i=1; i<=21; i++) {
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        	beliefFloat += ", (" + i + ":" + blockchain.transactionBelief(i) + ")";
        }
        TestTutorial.code(beliefBool.substring(2) + "\n\n" + beliefFloat.substring(2));
        
        
        
        //8 --> 6
        block = new Block();
        block.addTransaction(new Transaction(22, 41, 10, 505));
        block.addTransaction(new Transaction(23, 42, 250, 2));
        block.addTransaction(new Transaction(24, 42, 250, 2));
        blockchain.addToStructure(block);

        
        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create block #8 {22,23,24} and add it on top of the Block #6.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.step("There are three tips now: 8 (this block), 7 (previous block) and 3 (the short stale block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed now. Transactions of blocks 2 and 3 are still not believed, not being part of the longest chain.");
        beliefBool = "";
        for (int i=1; i<=24; i++) {
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        }
        TestTutorial.code(beliefBool.substring(2));
        
        
        
        
        // Not added in the blockchain
        //9 --> 4
        block = new Block();
        block.addTransaction(new Transaction(25, 25, 10, 505));
        block.addTransaction(new Transaction(26, 26, 250, 2));
        block.setParent(keep_4_2);
        Block keep_9_4 = block;

        
        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=25; i<=26; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create block #9 {25,26} to point on top of #4 but don't append yet");
        TestTutorial.step("Do not add the block yet.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are three tips as before: 8 (this block), 7 (previous block) and 3 (the short stale block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed:");
        beliefBool = "";
        for (int i=1; i<=26; i++) {
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        }
        TestTutorial.code(beliefBool.substring(2));
        
        
        // Orphan Block
        //10 --> 9
        block = new Block();
        block.addTransaction(new Transaction(27, 25, 10, 505));
        block.addTransaction(new Transaction(28, 26, 250, 2));
        block.setParent(keep_9_4);
        Block keep_10_9 = block;
        blockchain.addToStructure(block);

        
        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=25; i<=28; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create block #10 {27,28} to point on top of #9 and append it");
        TestTutorial.step("The block must go to orphans (because 9 is absent).");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are three tips as before: 8 (this block), 7 (previous block) and 3 (the short stale block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed:");
        beliefBool = "";
        for (int i=1; i<=28; i++) {
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        }
        TestTutorial.code(beliefBool.substring(2));
        
        
        
        //11 --> 10
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(30, 26, 250, 2));
        block.addTransaction(new Transaction(31, 26, 250, 2));
        block.setParent(keep_10_9);
        blockchain.addToStructure(block);

        
        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=25; i<=31; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create one more block #11 {29,30,31} to point on top of #10 and append it");
        TestTutorial.step("The block must go to orphans (because 9 is absent from blockchain).");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are three tips as before: 8 (this block), 7 (previous block) and 3 (the short stale block).");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed:");
        beliefBool = "";
        for (int i=1; i<=31; i++) {
        	beliefBool += ", (" + i + ":" + blockchain.transactionBelieved(i) + ")";
        }
        TestTutorial.code(beliefBool.substring(2));
        
        
        
        //Plug 9 now. Orphans must be placed back.
        blockchain.addToStructure(keep_9_4);
        
        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=25; i<=31; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Plug #9 now");
        TestTutorial.step("The orphans 10, 11 will follow.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are four tips now: 11, 8, 7, 3.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed:");
        beliefBool = "";
        String beliefBoolNot = "";
        for (int i=1; i<=31; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
                
        // Not added in the blockchain
        //12 --> 3
        block = new Block();
        block.addTransaction(new Transaction(32, 25, 10, 505));
        block.addTransaction(new Transaction(33, 26, 250, 2));
        block.setParent(keep_3);
        Block keep_12_3 = block;

        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=25; i<=33; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create #12 {32,33} and have it point to 3. Do not add yet.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are four tips still: 11, 8, 7, 3.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed:");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=33; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
                
        // Orphan Block
        //13 --> 12
        block = new Block();
        block.addTransaction(new Transaction(34, 25, 10, 505));
        block.addTransaction(new Transaction(35, 26, 250, 2));
        block.setParent(keep_12_3);
        blockchain.addToStructure(block);

        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=25; i<=35; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create #13 {34,35} and have it point to 12. Add it.");
        TestTutorial.step("#### 13 {34,35}  goes to orphans");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are four tips still: 11, 8, 7, 3.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed:");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=35; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
        
        
        
        //Plug 12 now. Orphans must be placed back.
        blockchain.addToStructure(keep_12_3);

        
        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=25; i<=35; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Plug 12");
        TestTutorial.step("#### 13 -> 12 is plugged on 3. But still not longest chain.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are four tips still: 13, 11, 8, 7.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed. Longest chain is still the one under 8");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=35; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
        
        
        //14 (overlaps with block 4)
        block = new Block();
        block.addTransaction(new Transaction(14, 25, 10, 505));
        block.addTransaction(new Transaction(36, 26, 250, 2));
        Block keep_14 = block;

        //15 --> 14
        block = new Block();
        block.addTransaction(new Transaction(37, 25, 10, 505));
        block.addTransaction(new Transaction(38, 26, 250, 2));
        block.setParent(keep_14);
        blockchain.addToStructure(block);

        
        // ASSERTIONS
        for (int i=1; i<=10; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=11; i<=13; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=14; i<=19; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=20; i<=21; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=22; i<=24; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=25; i<=37; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Create 15 {37,38} --> 14 {14,36} and add 15. 14 is parentless.");
        TestTutorial.step("15 must be added to the orphans, and everything else is the same.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are four tips still: 13, 11, 8, 7.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed. Longest chain is still the one under 8");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=37; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
        
        
        //It will be added to tip 13; because block 14 doesn't have overlaps with this tip
        blockchain.addToStructure(keep_14);

        
        // ASSERTIONS
        
        for (int i=1; i<=14; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=15; i<=31; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=32; i<=38; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Plug 14.");
        TestTutorial.step("15-->14 will be appended to 13, rather than 8 or 10 due to the overlap in 4 (tx 14). Now 15 is the longest tip!");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are four tips now: 15, 11, 8, 7.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed. Longest chain is still the one under 15");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=38; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
        

        // 16 (overlaps with blocks: 11, 6, 3)
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(18, 41, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        blockchain.addToStructure(block);

        // ASSERTIONS
        for (int i=1; i<=14; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=15; i<=31; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=32; i<=38; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Add parentless 16 {12,18,29}.");
        TestTutorial.step("Overlaps with blocks: 11, 6, 3. Will be appended on 7");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are four tips now: 15, 11, 8, 15.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed. Longest chain is still the one under 15");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=38; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
        
        
        
        //17 (overlaps with blocks: 13, 5, 11, 7)
        // It has overlaps with all tips. Will be added to 
        block = new Block();
        block.addTransaction(new Transaction(34, 25, 10, 505));
        block.addTransaction(new Transaction(16, 41, 10, 505));
        block.addTransaction(new Transaction(31, 25, 250, 2));
        block.addTransaction(new Transaction(20, 25, 250, 2));
        blockchain.addToStructure(block);

        // ASSERTIONS
        
        for (int i=1; i<=14; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=15; i<=31; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=32; i<=38; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Add parentless 17 {16,20,31,34}");
        TestTutorial.step("(overlaps with blocks: 13, 5, 11, 7. Will be appended on root. Why? No non-ovelappig tip was found, it goes to root. In practice it may try to find a place in one of the branches.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are four tips now: 15, 11, 8, 16, 17.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed. Longest chain is still the one under 15");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=38; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
        
        
        // 18 (Overlaps with first block)
        // Will be added on top of 17 / the only place without conflicts
        block = new Block();
        block.addTransaction(new Transaction(1, 25, 10, 505));
        blockchain.addToStructure(block);
        
        
        // ASSERTIONS
        
        for (int i=1; i<=14; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=15; i<=31; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=32; i<=38; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Add parentless 18 {1}");
        TestTutorial.step("Overlaps with block: 1 which is the parent of everyone except 17. Will be appended on 17");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are the following tips now: 15, 11, 8, 16, 18.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed. Longest chain is still the one under 15");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=38; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
        
        
        String[] oxpected = {"BlockID,ParentID,Transactions"};
        String[] expected = {"BlockID,ParentID,BlockHeight,Transactions",
                "15,14,7,{37,38}",
                "14,13,6,{14,36}",
                "11,10,6,{29,30,31}",
                "8,6,6,{22,23,24}",
                "13,12,5,{34,35}",
                "10,9,5,{27,28}",
                "6,5,5,{18,19}",
                "16,7,4,{29,18,12}",
                "12,3,4,{32,33}",
                "9,4,4,{25,26}",
                "5,4,4,{16,17}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "18,17,2,{1}",
                "2,1,2,{7,8,9,10}",
                "17,-1,1,{34,16,31,20}",
                "1,-1,1,{1,2,3,4,5,6}"};
        
        assertArrayEquals(expected, blockchain.printStructure(),"Assertion 1");
        assertArrayEquals(oxpected, blockchain.printOrphans(),"Assertion 2");
        assertEquals("{15,11,8,16,18}", blockchain.printTips(","),"Assertion 3");
        
        
        // Hidden chain reveal!
        // Create 19
        block = new Block();
        block.addTransaction(new Transaction(8, 25, 10, 505));
        block.addTransaction(new Transaction(9, 26, 250, 2));
        block.addTransaction(new Transaction(10, 26, 250, 2));
        block.setParent(keep_1);
        Block keep_19 = block;
        
        // Create 20
        block = new Block();
        block.addTransaction(new Transaction(11, 25, 10, 505));
        block.addTransaction(new Transaction(12, 26, 250, 2));
        block.setParent(keep_19);
        Block keep_20 = block;
 
        // Create 21
        block = new Block();
        block.addTransaction(new Transaction(13, 25, 10, 505));
        block.addTransaction(new Transaction(14, 26, 250, 2));
        block.setParent(keep_20);
        Block keep_21 = block;

        // Create 22
        block = new Block();
        block.addTransaction(new Transaction(32, 25, 10, 505));
        block.addTransaction(new Transaction(33, 26, 250, 2));
        block.setParent(keep_21);
        Block keep_22 = block;
        
        // Create 23
        block = new Block();
        block.addTransaction(new Transaction(34, 25, 10, 505));
        block.addTransaction(new Transaction(35, 26, 250, 2));
        block.setParent(keep_22);
        Block keep_23 = block;
        
        
        // Create 24
        block = new Block();
        block.addTransaction(new Transaction(36, 25, 10, 505));
        block.addTransaction(new Transaction(37, 26, 250, 2));
        block.setParent(keep_23);
        Block keep_24 = block;
        
        // Create 25
        block = new Block();
        block.addTransaction(new Transaction(38, 26, 250, 2));
        block.setParent(keep_24);
        Block keep_25 = block;
        
        
        blockchain.addToStructure(keep_24);
        blockchain.addToStructure(keep_23);
        blockchain.addToStructure(keep_22);
        blockchain.addToStructure(keep_21);
        blockchain.addToStructure(keep_20);
        blockchain.addToStructure(keep_19);
        
        
        // ASSERTIONS
        for (int i=1; i<=14; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=15; i<=31; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=32; i<=38; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("## Hidden Chain attack");
        TestTutorial.step("On the previous fixture append blocks 19 - 25 on transaction 1, aimed at sensoring transaction 7");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are the following tips now: 15, 11, 8, 16, 18, 24.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed. Longest chain is still the one under 15");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=38; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));
        
        blockchain.addToStructure(keep_25);
        
        // ASSERTIONS
        for (int i=1; i<=6; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
    	assertFalse(blockchain.transactionBelieved(7));
    	assertEquals(0.0, blockchain.transactionBelief(7), 1e-9);
        for (int i=8; i<=14; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=15; i<=31; i++) {
        	assertFalse(blockchain.transactionBelieved(i));
        	assertEquals(0.0, blockchain.transactionBelief(i), 1e-9);
        }
        for (int i=32; i<=38; i++) {
        	assertTrue(blockchain.transactionBelieved(i));
        	assertEquals(1.0, blockchain.transactionBelief(i), 1e-9);
        }
        
        // COMMENTARY
        TestTutorial.step("#### Adding 25");
        TestTutorial.step("With the addition of 25, longest chaing is 25 and the attack has succeeded.");
        TestTutorial.code(String.join("\n",blockchain.printStructure()));
        TestTutorial.code(String.join("\n",blockchain.printOrphans()));
        TestTutorial.step("There are the following tips now: 15, 11, 8, 16, 18, 24.");
        TestTutorial.code(String.join("\n",blockchain.printTips(";")));

        TestTutorial.step("Here is what is believed. Longest chain is now under 25. Transaction 7 has effectivelly been invalidated.");
        beliefBool = "";
        beliefBoolNot = "";
        for (int i=1; i<=38; i++) {
        	if (blockchain.transactionBelieved(i))
        		beliefBool += ", " + i;
        	else 
        		beliefBoolNot += ", " + i;
        }
        TestTutorial.code("Believed: " + beliefBool.substring(2) + "\nNot Believed: " + beliefBoolNot.substring(2));

        
        TestTutorial.close();
    }

    
    
    
    /**
     * Tests the chain reorganization functionality with multiple tips in the blockchain.
     * <p>
     * This test verifies that the blockchain correctly handles forks and reorganizes
     * to maintain the longest chain as the canonical chain. It covers the following scenarios:
     * 1. Creating a genesis block and adding it to the blockchain.
     * 2. Adding two blocks that extend the genesis block, creating a fork with two tips.
     * 3. Extending one of the forks to make it the longest chain and verifying that the blockchain
     * resolves to this chain as the canonical chain.
     * 4. Ensuring the blockchain structure matches the expected structure after reorganization.
     * 5. Ensuring no orphan blocks remain after the reorganization.
     */
    @Test
    final void testChainReorganizationWithMultipleTips() {
        // Initial setup: Create a genesis block and add it to the blockchain
        Block genesisBlock = new Block();
        genesisBlock.addTransaction(new Transaction(0, 1, 1, 1));
        blockchain.addToStructure(genesisBlock);

        // Create and add the first block extending the genesis block
        Block firstChild = new Block();
        firstChild.setParent(genesisBlock); // Linking to genesis block
        firstChild.addTransaction(new Transaction(1, 10, 2, 2));
        blockchain.addToStructure(firstChild);

        // Create and add a second block extending the genesis block, creating a fork
        Block secondChild = new Block();
        secondChild.setParent(genesisBlock); // Also linking to genesis block
        secondChild.addTransaction(new Transaction(2, 20, 2, 2));
        blockchain.addToStructure(secondChild);

        // Verify that no blocks are identified as orphans
        String[] oxpected = {"BlockID,ParentID,Transactions"};
        assertArrayEquals(oxpected, blockchain.printOrphans());

        // Verify that the blockchain now has two tips, indicating a  fork
        assertTrue(blockchain.printTips(",").contains("{2,3}"), "There should be two tips representing a fork");

        // Add a new block that extends one of the forks, making it the longest chain
        Block extendingBlock = new Block();
        extendingBlock.setParent(firstChild); // Extending the firstChild, making its chain longer
        extendingBlock.addTransaction(new Transaction(3, 30, 3, 2));
        blockchain.addToStructure(extendingBlock);

        // Verify that the blockchain has resolved to a single tip
        assertEquals(4, blockchain.getLongestTip().getID(), "The extending block should make its chain the canonical chain");

        // Additional checks to ensure the blockchain structure is as expected
        String[] expectedStructure = {
                "BlockID,ParentID,BlockHeight,Transactions",
                "4,2,3,{3}",
                "3,1,2,{2}",
                "2,1,2,{1}",
                "1,-1,1,{0}"
        };
        assertArrayEquals(expectedStructure, blockchain.printStructure(), "Blockchain structure should match expected after reorganization");
    }

    /**
     * Tests the handling of complex forks and their reunification in the blockchain.
     * <p>
     * This test verifies that the blockchain correctly manages multiple forks and reunifies them
     * while maintaining the correct structure. It covers the following scenarios:
     * 1. Creating a genesis block and adding it to the blockchain.
     * 2. Adding two blocks that extend the genesis block, creating a fork with two tips.
     * 3. Extending both forks by adding blocks to each fork.
     * 4. Verifying the existence of multiple tips before reunification.
     * 5. Adding a new block that reunifies the forks by extending one of the forks.
     * 6. Ensuring the longest tip is correctly identified after reunification.
     * 7. Verifying the blockchain structure matches the expected structure after reunification.
     */
    @Test
    final void testHandlingComplexForks() {
        // Initial setup: Create a genesis block and add it to the blockchain
        Block genesisBlock = new Block();
        genesisBlock.addTransaction(new Transaction(0, 1, 1, 1));
        blockchain.addToStructure(genesisBlock);

        // Create and add two blocks extending the genesis block, creating initial fork
        Block firstFork = new Block();
        firstFork.setParent(genesisBlock);
        firstFork.addTransaction(new Transaction(1, 10, 2, 2));
        blockchain.addToStructure(firstFork);

        Block secondFork = new Block();
        secondFork.setParent(genesisBlock);
        secondFork.addTransaction(new Transaction(2, 20, 2, 2));
        blockchain.addToStructure(secondFork);

        // Extending the first fork
        Block thirdFork = new Block();
        thirdFork.setParent(firstFork);
        thirdFork.addTransaction(new Transaction(3, 30, 3, 3));
        blockchain.addToStructure(thirdFork);

        // Extending the second fork
        Block fourthFork = new Block();
        fourthFork.setParent(secondFork);
        fourthFork.addTransaction(new Transaction(4, 40, 4, 4));
        blockchain.addToStructure(fourthFork);

        // Verify the existence of multiple tips before reunification
        assertTrue(blockchain.printTips(",").contains("{4,5}"), "There should be two tips representing the forks");

        // Adding a new block that reunites the forks by choosing one as its parent
        Block reunificationBlock = new Block();
        reunificationBlock.setParent(thirdFork);
        reunificationBlock.addTransaction(new Transaction(5, 50, 5, 5));
        blockchain.addToStructure(reunificationBlock);

        // Verify that the longest tip is now the reunification block
        assertEquals(6, blockchain.getLongestTip().getID(), "After reunification, there should be only one tip");

        // Ensure the blockchain structure reflects the reunification correctly
        String[] expectedStructure = {
                "BlockID,ParentID,BlockHeight,Transactions",
                "6,4,4,{5}",
                "5,3,3,{4}",
                "4,2,3,{3}",
                "3,1,2,{2}",
                "2,1,2,{1}",
                "1,-1,1,{0}"
        };
        assertArrayEquals(expectedStructure, blockchain.printStructure(), "Blockchain structure should match expected after complex forks and reunification");
    }

    /**
     * Tests the blockchain's ability to handle the rapid addition of multiple blocks in quick succession.
     * <p>
     * This test verifies that the blockchain can correctly add multiple blocks in a short period,
     * maintaining the correct structure and integrity. It covers the following scenarios:
     * 1. Creating a genesis block and adding it to the blockchain.
     * 2. Rapidly adding a specified number of blocks to the blockchain, each extending the previous block.
     * 3. Verifying that all blocks were added correctly.
     * 4. Ensuring the blockchain's integrity and height are maintained.
     */
    @Test
    final void testHandlingRapidSuccessionOfBlocks() {
        // Initial setup: Create a genesis block and add it to the blockchain
        Block genesisBlock = new Block();
        genesisBlock.addTransaction(new Transaction(0, 1, 1, 1)); // Simplified genesis transaction
        blockchain.addToStructure(genesisBlock);

        // Rapidly add multiple blocks to the blockchain
        Block previousBlock = genesisBlock;
        final int numberOfBlocksToAdd = 10; // Simulate adding 10 blocks in rapid succession
        for (int i = 1; i <= numberOfBlocksToAdd; i++) {
            Block newBlock = new Block();
            newBlock.setParent(previousBlock);
            newBlock.addTransaction(new Transaction(i, 10L * i, 100 * i, 2)); // Add a unique transaction to each block
            blockchain.addToStructure(newBlock);
            previousBlock = newBlock; // Update the reference for the next block's parent
        }

        // Verify that all blocks were added correctly
        for (int i = 1; i <= numberOfBlocksToAdd; i++) {
            Block block = blockchain.getBlockByID(i);
            assertNotNull(block, "Block " + i + " should exist in the blockchain");
            assertEquals(1, block.getCount(), "Block " + i + " should contain exactly one transaction");
        }

        // Ensure the blockchain's integrity is maintained
        assertEquals(numberOfBlocksToAdd + 1, blockchain.getBlockchainHeight(), "Blockchain should have the correct number of blocks");
    }

    /**
     * Tests the blockchain's ability to handle orphan blocks and their integration once the missing parent block is added.
     * <p>
     * This test verifies that the blockchain can correctly identify orphan blocks, maintain their status,
     * and integrate them once their parent blocks are added. It covers the following scenarios:
     * 1. Creating a genesis block and adding it to the blockchain.
     * 2. Creating a block with a missing parent to simulate an orphan block.
     * 3. Attempting to add the orphan block to the blockchain and verifying its orphan status.
     * 4. Adding the missing parent block to the blockchain.
     * 5. Verifying that the orphan block is no longer an orphan and is correctly integrated into the blockchain.
     * 6. Ensuring the blockchain structure reflects the correct integration of the orphan block and its parent.
     */
    @Test
    void testOrphanBlockHandlingAndIntegration() {
        // Initial setup: Create a genesis block and add it to the blockchain
        Block genesisBlock = new Block();
        genesisBlock.addTransaction(new Transaction(0, System.currentTimeMillis(), 1.0f, 0.1f));
        blockchain.addToStructure(genesisBlock);

        // This parent ID points to a block that has not been added yet, simulating an orphan
        Block missingParentBlock = new Block();
        missingParentBlock.addTransaction(new Transaction(1, System.currentTimeMillis(), 2.0f, 0.2f));

        // Create a block that should be an orphan initially (its parent is not yet in the blockchain)
        Block orphanBlock = new Block();
        orphanBlock.addTransaction(new Transaction(2, System.currentTimeMillis(), 3.0f, 0.3f));
        orphanBlock.setParent(missingParentBlock);

        // Attempt to add the orphan block to the blockchain
        blockchain.addToStructure(orphanBlock);

        // Verify that the blockchain recognizes the block as an orphan
        assertEquals(1, blockchain.printOrphans().length - 1, "There should be one orphan block waiting for its parent");

        // Now add the missing parent block to the blockchain
        blockchain.addToStructure(missingParentBlock);

        // Verify that the orphan block is no longer an orphan and is integrated into the blockchain
        assertEquals(0, blockchain.printOrphans().length - 1, "There should be no orphans after adding the missing parent");

        // Verify the blockchain structure to ensure both the previously orphan block and its parent are correctly integrated
        String[] expectedStructure = {
                "BlockID,ParentID,BlockHeight,Transactions",
                String.format("%d,%d,3,{2}", orphanBlock.getID(), missingParentBlock.getID()),
                String.format("%d,1,2,{1}", missingParentBlock.getID()), // Assuming ID is assigned sequentially by the blockchain
                "1,-1,1,{0}" // Genesis block
        };
        assertArrayEquals(expectedStructure, blockchain.printStructure(), "Blockchain structure should include the orphan block and its parent correctly after integration");
    }

    /**
     * Tests the blockchain's handling of blocks with duplicate transactions.
     * <p>
     * This test verifies that the blockchain correctly handles a block that contains a duplicate transaction
     * already present in a previously added block. 
     * TODO: complete this description
     */
    @Test
    void testBlockWithDuplicateTransactionHandled() {
        // Initial setup: Create a first block and add it to the blockchain
        Block firstBlock = new Block();
        firstBlock.addTransaction(new Transaction(0, System.currentTimeMillis(), 1.0f, 0.1f));
        blockchain.addToStructure(firstBlock);
        // Block would be grounded to -1 (the genessis block that does not exist as a block per se)

        // Create and add a valid block extending the first block
        Block validBlock = new Block();
        validBlock.setParent(firstBlock); // Linking to first block
        Transaction uniqueTransaction = new Transaction(1, System.currentTimeMillis(), 2.0f, 0.2f);
        validBlock.addTransaction(uniqueTransaction);
        blockchain.addToStructure(validBlock);

        // Attempt to add a new block containing a duplicate of the existing transaction
        Block blockWithDuplicateTransaction = new Block();
        blockWithDuplicateTransaction.addTransaction(uniqueTransaction); // Adding the same transaction again
        blockchain.addToStructure(blockWithDuplicateTransaction);

        // At this point the new block will point to genesis (it is not discarded) given the conflict  
        String expectedTips = String.format("{%d,%d}", validBlock.getID(),blockWithDuplicateTransaction.getID());
        assertEquals(expectedTips, blockchain.printTips(","), "Blockchain should be consistent per path to root, not universally.");

        // Verify that the blockchain structure does not include the invalid block
        String[] expectedStructure = {
                "BlockID,ParentID,BlockHeight,Transactions",
                String.format("%d,1,2,{1}", validBlock.getID()),
                String.format("%d,-1,1,{1}", blockWithDuplicateTransaction.getID()),
                "1,-1,1,{0}" // Genesis block
        };
        
        assertArrayEquals(expectedStructure, blockchain.printStructure(), "Blockchain structure should not include the block with the duplicate transaction");

        // Additionally, check if the rejected block is considered an orphan or simply discarded
        assertEquals(0, blockchain.printOrphans().length - 1, "There should be no orphans from rejected blocks with duplicate transactions");
    }
}
