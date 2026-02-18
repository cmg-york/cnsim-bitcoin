package ca.yorku.cmg.cnsim.bitcoin.testutils;

import ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNode;
import ca.yorku.cmg.cnsim.bitcoin.node.NodeBehaviorStrategy;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test helper utility for block management scenarios.
 * <p>
 * This class provides a reusable test fixture that exercises comprehensive block
 * arrival, transaction pooling, and validation scenarios. It encapsulates the
 * common test logic used across multiple test classes to avoid code duplication.
 * </p>
 * <p>
 * The helper creates a sequence of blocks and transactions that tests:
 * <ul>
 *   <li>Linear chain establishment</li>
 *   <li>Fork and orphan scenarios</li>
 *   <li>Mining cycles and pool management</li>
 *   <li>Transaction conflict handling</li>
 *   <li>Block validation and chain reorganization</li>
 * </ul>
 * </p>
 *
 * @author Test Suite
 * @see BlockManagementTestHelper#executeBlockManagementTest(BitcoinNode, String)
 */
public class BlockManagementTestHelper {

    /**
     * Executes the comprehensive block management test fixture.
     * <p>
     * This method exercises all block management scenarios, including creation,
     * propagation, validation, conflict handling, and orphan management using the
     * node's currently assigned behavior strategy.
     * </p>
     *
     * @param node     the Bitcoin node to test
     * @param filename the name of the markdown tutorial output file
     */
    public void executeBlockManagementTest(BitcoinNode node) {


        // ===== SETUP: Create initial blocks 1, 2, 3 and establish linear chain =====

        TestTutorial.step("#### Create block 1: {1,2,3,4,5,6}");

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


        TestTutorial.code(String.join("\n",node.getStructure().printStructure()));

        // Assertions: linear chain 1->2->3 should be established
        String[] expected_3 = {"BlockID,ParentID,BlockHeight,Transactions",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};
        assertArrayEquals(expected_3, node.getStructure().printStructure(),"After receiving blocks 1,2,3");

        String[] oxpected_1 = {"BlockID,ParentID,Transactions"};
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after blocks 1,2,3");


        // ===== FORK SCENARIO: Create block 4 as alternate child of block 2 =====

        TestTutorial.step("#### Create block 4: {14,15} to point to 2");

        block = new Block();
        block.addTransaction(new Transaction(14, 35, 10, 505));
        block.addTransaction(new Transaction(15, 40, 250, 2));
        block.setParent(keep_2);
        Block keep_4_2 = block;

        TestTutorial.step("#### Receive block 4");
        node.event_NodeReceivesPropagatedContainer(keep_4_2);

        TestTutorial.code(String.join("\n",node.getStructure().printStructure()));

        // Assertions: fork established with tips {3, 4}
        String[] expected_4 = {"BlockID,ParentID,BlockHeight,Transactions",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_4, node.getStructure().printStructure(),"After receiving block 4");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 4");
        assertEquals("{3,4}",node.getStructure().printTips(","),"Tips after block 4");
        assertFalse(node.isMining());
        assertEquals("", node.getPool().debugPrintPoolTx(), "Empty pool before transaction 16");


        // ===== FIRST MINING CYCLE: Node mines block 5 on top of block 4 =====

        TestTutorial.step("#### Receive transaction 16.");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(16, 41, 10, 505),10);
        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx()  +
                "\nMining Block ID: " + node.getNextValidationEvent().getContainer().getID());

        // Assertions after receiving transaction 16
        assertEquals("{3,4}", node.getStructure().printTips(","), "Tips after tx 16");
        assertTrue(node.isMining());
        assertEquals("{16}", node.getPool().printIDs(","), "Pool after tx 16");
        assertEquals("{16}", node.getMiningPool().printIDs(","), "Mining pool after tx 16");

        node.event_NodeReceivesPropagatedTransaction(new Transaction(17, 42, 250, 2),20);

        TestTutorial.step("#### Receive transaction 17.");
        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx() +
                "\nMining Block ID: " + node.getNextValidationEvent().getContainer().getID());

        // Assertions after receiving transaction 17
        assertEquals("{3,4}", node.getStructure().printTips(","), "Tips after tx 17");
        assertTrue(node.isMining());
        assertEquals("{16,17}", node.getPool().printIDs(","), "Pool after tx 17");
        assertEquals("{17,16}", node.getMiningPool().printIDs(","), "Mining pool after tx 17");

        TestTutorial.step("#### Complete validation.");
        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(),20);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());

        // Assertions after completing validation (block 5)
        assertEquals("{3,5}", node.getStructure().printTips(","), "Tips after validation of block 5");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after validation");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after validation");

        String[] expected_5 = {"BlockID,ParentID,BlockHeight,Transactions",
                "5,4,4,{17,16}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_5, node.getStructure().printStructure(),"Structure after block 5 validation");

        TestTutorial.code(String.join("\n",node.getStructure().printStructure()));


        // ===== SECOND MINING CYCLE: Node mines block 6 on top of block 5 =====

        TestTutorial.step("#### Receive transactions 18,19.");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(18, 41, 10, 505),10);

        assertTrue(node.isMining());
        assertEquals("{18}", node.getPool().printIDs(","), "Pool after tx 18");
        assertEquals("{18}", node.getMiningPool().printIDs(","), "Mining pool after tx 18");

        node.event_NodeReceivesPropagatedTransaction(new Transaction(19, 42, 250, 2),20);

        assertTrue(node.isMining());
        assertEquals("{18,19}", node.getPool().printIDs(","), "Pool after tx 19");
        assertEquals("{19,18}", node.getMiningPool().printIDs(","), "Mining pool after tx 19");

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx() +
                "\nMining Block ID: " + node.getNextValidationEvent().getContainer().getID());

        // Assertions after receiving transactions 18,19 (before 20,21)
        assertEquals("{3,5}", node.getStructure().printTips(","), "Tips after tx 18,19");
        assertTrue(node.isMining());

        TestTutorial.step("#### Receive transactions 20,21.");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(20, 25, 10, 505),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(21, 26, 250, 2),0);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());

        // Assertions after receiving transactions 20,21
        assertEquals("{3,5}", node.getStructure().printTips(","), "Tips after tx 20,21");
        assertTrue(node.isMining());

        block = new Block();
        block.addTransaction(new Transaction(20, 25, 10, 505));
        block.addTransaction(new Transaction(21, 26, 250, 2));
        block.setParent(keep_2);
        Block keep_7 = block;

        TestTutorial.step("#### Receive block 7: {20,21}");

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID() +
                "\nBlock ID of group being mined: " + node.getNextValidationEvent().getContainer().getID());


        node.event_NodeReceivesPropagatedContainer(keep_7);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code(String.join("\n",node.getStructure().printStructure()));

        // Assertions after receiving block 7
        assertEquals("{3,5,7}", node.getStructure().printTips(","), "Tips after block 7");
        assertTrue(node.isMining());
        assertEquals("{18,19}", node.getPool().printIDs(","), "Pool after block 7");
        assertEquals("{19,18}", node.getMiningPool().printIDs(","), "Mining pool after block 7");

        String[] expected_6 = {"BlockID,ParentID,BlockHeight,Transactions",
                "5,4,4,{17,16}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_6, node.getStructure().printStructure(),"Structure after block 7");

        TestTutorial.step("#### Complete validation.");

        Block keep_6 = (Block) node.getNextValidationEvent().getContainer();

        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(),20);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx()
        );
        TestTutorial.code(String.join("\n",node.getStructure().printStructure()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after completing validation (block 6)
        assertEquals("{7,3,6}", node.getStructure().printTips(","), "Tips after block 6 validation");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after block 6 validation");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after block 6 validation");

        String[] expected_7 = {"BlockID,ParentID,BlockHeight,Transactions",
                "6,5,5,{19,18}",
                "5,4,4,{17,16}",
                "7,2,3,{20,21}",
                "4,2,3,{14,15}",
                "3,2,3,{11,12,13}",
                "2,1,2,{7,8,9,10}",
                "1,-1,1,{1,2,3,4,5,6}"};

        assertArrayEquals(expected_7, node.getStructure().printStructure(),"Structure after block 6 validation");

        // ===== ORPHAN HANDLING & FORKING: Complex scenarios with orphans and overlaps =====

        TestTutorial.step("#### Receive transactions: {22,23,24,32,35}");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(22, 41, 10, 505),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(23, 42, 250, 2),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(24, 42, 250, 2),0);

        node.event_NodeReceivesPropagatedTransaction(new Transaction(32, 25, 10, 505),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(35, 26, 250, 2),0);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code(String.join("\n",node.getStructure().printStructure()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID() +
                "\nBlock ID of group being mined: " + node.getNextValidationEvent().getContainer().getID());

        // Assertions after receiving transactions {22,23,24,32,35}
        assertEquals("{7,3,6}", node.getStructure().printTips(","), "Tips after txs {22,23,24,32,35}");
        assertTrue(node.isMining());

        TestTutorial.step("#### Receive block 8:{22,23,24}");
        Block.setCurrID(8);
        block = new Block();
        block.addTransaction(new Transaction(22, 41, 10, 505));
        block.addTransaction(new Transaction(23, 42, 250, 2));
        block.addTransaction(new Transaction(24, 42, 250, 2));
        block.setParent(keep_6);
        Block keep_8 = block;
        node.event_NodeReceivesPropagatedContainer(keep_8);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID() +
                "\nBlock ID of group being mined: " + node.getNextValidationEvent().getContainer().getID());

        // Assertions after receiving block 8
        assertEquals("{7,3,8}", node.getStructure().printTips(","), "Tips after block 8");
        assertTrue(node.isMining());
        assertEquals("{32,35}", node.getPool().printIDs(","), "Pool after block 8");
        assertEquals("{35,32}", node.getMiningPool().printIDs(","), "Mining pool after block 8");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 8");

        TestTutorial.step("#### Receive block 10:{27,28}");

        // Create block 9 (won't be immediately added) and block 10 (orphan depending on 9)
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

        // Try to receive block 10 before its parent (block 9) - should become orphan
        node.event_NodeReceivesPropagatedContainer(keep_10);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID() +
                "\nBlock ID of group being mined: " + node.getNextValidationEvent().getContainer().getID());

        // Assertions after receiving block 10 (orphan)
        assertEquals("{7,3,8}", node.getStructure().printTips(","), "Tips after orphan block 10");
        assertTrue(node.isMining());
        assertEquals("{32,35}", node.getPool().printIDs(","), "Pool after orphan block 10");
        assertEquals("{35,32}", node.getMiningPool().printIDs(","), "Mining pool after orphan block 10");
        String[] orphans_10 = {"BlockID,ParentID,Transactions", "10,9,{27,28}"};
        assertArrayEquals(orphans_10, node.getStructure().printOrphans(),"Orphans after block 10");

        TestTutorial.step("#### Receive block 9:{25,26}");

        node.event_NodeReceivesPropagatedContainer(keep_9_4);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID() +
                "\nBlock ID of group being mined: " + node.getNextValidationEvent().getContainer().getID());

        // Assertions after receiving block 9 (connects orphan 10)
        assertEquals("{7,3,8,10}", node.getStructure().printTips(","), "Tips after block 9");
        assertTrue(node.isMining());
        assertEquals("{32,35}", node.getPool().printIDs(","), "Pool after block 9");
        assertEquals("{35,32}", node.getMiningPool().printIDs(","), "Mining pool after block 9");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 9");

        TestTutorial.step("#### Receive block 13:{34,35}");

        // Create block 12 (won't be immediately added) and block 13 (orphan depending on 12)
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

        // Try to receive block 13 before its parent (block 12) - should become orphan
        node.event_NodeReceivesPropagatedContainer(keep_13);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID() +
                "\nBlock ID of group being mined: " + node.getNextValidationEvent().getContainer().getID());

        // Assertions after receiving block 13 (orphan)
        assertEquals("{7,3,8,10}", node.getStructure().printTips(","), "Tips after orphan block 13");
        assertTrue(node.isMining());
        assertEquals("{32}", node.getPool().printIDs(","), "Pool after orphan block 13");
        assertEquals("{32}", node.getMiningPool().printIDs(","), "Mining pool after orphan block 13");
        String[] orphans_13 = {"BlockID,ParentID,Transactions", "13,12,{34,35}"};
        assertArrayEquals(orphans_13, node.getStructure().printOrphans(),"Orphans after block 13");

        TestTutorial.step("**NOTE**: Observe that the transactions contained in the block are excluded from the pool despite the later being placed in the orphans. This probably needs to change.");


        TestTutorial.step("#### Receive transactions: {33,34}");
        node.event_NodeReceivesPropagatedTransaction(new Transaction(33, 26, 250, 2),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(34, 25, 10, 505),0);
        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID() +
                "\nBlock ID of group being mined: " + node.getNextValidationEvent().getContainer().getID());
        TestTutorial.step("**NOTE**: When a new transaction arrives it is exlcluded if it is also in the orphans.");

        // Assertions after receiving transactions {33,34}
        assertEquals("{7,3,8,10}", node.getStructure().printTips(","), "Tips after txs {33,34}");
        assertTrue(node.isMining());
        assertEquals("{32,33}", node.getPool().printIDs(","), "Pool after txs {33,34}");

        TestTutorial.step("#### Receive block 12:{32,33}");
        // Now receive the parent block 12 - this should trigger resolution of orphan 13
        node.event_NodeReceivesPropagatedContainer(keep_12_3);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after receiving block 12
        assertEquals("{7,8,10,13}", node.getStructure().printTips(","), "Tips after block 12");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after block 12");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after block 12");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 12");

        TestTutorial.step("#### Receive block 11:{29,30,31}");

        // Block 11 extends the chain from block 10, connecting the orphan chain
        Block.setCurrID(11);
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(30, 26, 250, 2));
        block.addTransaction(new Transaction(31, 26, 250, 2));
        block.setParent(keep_10);
        Block keep_11 = block;

        node.event_NodeReceivesPropagatedContainer(keep_11);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after receiving block 11
        assertEquals("{7,8,13,11}", node.getStructure().printTips(","), "Tips after block 11");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after block 11");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after block 11");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 11");

        TestTutorial.step("#### Receive block 12 which is the same!");
        // Test receiving a duplicate block with different ID but same parent
        Block.setCurrID(12);
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(30, 26, 250, 2));
        block.addTransaction(new Transaction(31, 26, 250, 2));
        block.setParent(keep_10);
        Block keep_12 = block;
        node.event_NodeReceivesPropagatedContainer(keep_12);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after receiving duplicate block 12
        assertEquals("{7,8,13,11,12}", node.getStructure().printTips(","), "Tips after duplicate block 12");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after duplicate block 12");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after duplicate block 12");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after duplicate block 12");

        TestTutorial.step("**NOTE**: Duplicate node will be added OK, so long as there is no conflict on the chain.");

        // ===== OVERLAP/CONFLICT HANDLING: Test block rejection due to transaction conflicts =====

        TestTutorial.step("#### Receive block 16:{12,18,29} -> 7");
        // Block 16 has transactions that conflict with existing blocks (12 in block 3, 18 in block 6, 29 in block 11)
        Block.setCurrID(15);
        block = new Block();
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(18, 41, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        block.setParent(keep_7);
        Block keep_16 = block;

        node.event_NodeReceivesPropagatedContainer(keep_16);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after receiving block 16
        assertEquals("{8,13,11,12,16}", node.getStructure().printTips(","), "Tips after block 16");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after block 16");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after block 16");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 16");

        TestTutorial.step("**NOTE**: Duplicate node will be again added OK, so long as there is no conflict on the chain it is placed on.");

        // ===== CONFLICT TESTING: Attempt to add block 17 with overlapping txs to different parents =====

        TestTutorial.step("#### Receive block 17:{12,18,29} -> 8");
        // Block 17 has same conflicting transactions; try attaching to block 8
        block = new Block();
        block.addTransaction(new Transaction(29, 25, 10, 505));
        block.addTransaction(new Transaction(18, 41, 10, 505));
        block.addTransaction(new Transaction(12, 25, 250, 2));
        block.setParent(keep_8);
        Block keep_17_1 = block;

        // This should be rejected because block 8 -> block 6 -> txs 18,19
        node.event_NodeReceivesPropagatedContainer(keep_17_1);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after receiving block 17 -> 8 (rejected due to overlap)
        assertEquals("{8,13,11,12,16}", node.getStructure().printTips(","), "Tips after block 17->8 (rejected)");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after block 17->8");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after block 17->8");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 17->8");

        TestTutorial.step("**NOTE**: The block is rejected due to overlap.");

        TestTutorial.step("#### Receive block 17:{12,18,29} -> 13");
        // Try the same block with block 13 as parent - still conflicts
        keep_17_1.setParent(keep_13);
        node.event_NodeReceivesPropagatedContainer(keep_17_1);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after receiving block 17 -> 13 (rejected due to overlap)
        assertEquals("{8,13,11,12,16}", node.getStructure().printTips(","), "Tips after block 17->13 (rejected)");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after block 17->13");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after block 17->13");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 17->13");

        TestTutorial.step("**NOTE**: The block is rejected due to overlap.");

        TestTutorial.step("#### Receive block 17:{12,18,29} -> 9");
        keep_17_1.setParent(keep_9_4);
        node.event_NodeReceivesPropagatedContainer(keep_17_1);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after receiving block 17 -> 9 (accepted)
        assertEquals("{8,13,11,12,16,17}", node.getStructure().printTips(","), "Tips after block 17->9");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after block 17->9");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after block 17->9");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 17->9");

        TestTutorial.step("**NOTE**: The block is now added.");

        // ===== FINAL MINING CYCLE: Final block validation and pool management =====

        TestTutorial.step("#### Receive transactions: {36,37,27}");
        // Transaction 27 is already in the blockchain (block 10), so it may be handled differently
        node.event_NodeReceivesPropagatedTransaction(new Transaction(36, 41, 10, 505),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(37, 42, 250, 2),0);
        node.event_NodeReceivesPropagatedTransaction(new Transaction(27, 42, 250, 2),0);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));

        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions after receiving transactions {36,37,27}
        assertEquals("{8,13,11,12,16,17}", node.getStructure().printTips(","), "Tips after txs {36,37,27}");
        assertTrue(node.isMining());
        assertEquals("{36,37}", node.getPool().printIDs(","), "Pool after txs {36,37,27}");
        assertEquals("{37,36}", node.getMiningPool().printIDs(","), "Mining pool after txs {36,37,27}");

        TestTutorial.step("#### Node Validates: {36,37,27}");
        // Node validates the final block (block 18) with the received transactions
        node.event_NodeCompletesValidation(node.getNextValidationEvent().getContainer(),20);

        TestTutorial.step("#### Node status:");
        TestTutorial.code("Tips: " + node.getStructure().printTips(",") +
                "\nMining: " + node.isMining() +
                "\nPool: " + node.getPool().debugPrintPoolTx() +
                "\nMining Pool: " + node.getMiningPool().debugPrintPoolTx());
        TestTutorial.code("Structure:\n" + String.join("\n",node.getStructure().printStructure()));
        TestTutorial.code("Orphans: \n" + String.join("\n",node.getStructure().printOrphans()));
        TestTutorial.code("Current block ID received: " + block.getID() +
                "\nCurrent block ID (static): " + Block.getCurrID());

        // Assertions: Final state after block 18 validation
        // Tips updated with new block 18, mining stopped, pools cleared, no orphans remain
        assertEquals("{11,8,17,13,16,18}", node.getStructure().printTips(","), "Tips after block 18 validation");
        assertFalse(node.isMining());
        assertEquals("{}", node.getPool().printIDs(","), "Empty pool after block 18 validation");
        assertEquals("{}", node.getMiningPool().printIDs(","), "Empty mining pool after block 18 validation");
        assertArrayEquals(oxpected_1, node.getStructure().printOrphans(),"No orphans after block 18 validation");

        // ===== TEST COMPLETE =====

    }
}
