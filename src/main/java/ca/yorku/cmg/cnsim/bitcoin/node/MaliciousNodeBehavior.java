package ca.yorku.cmg.cnsim.bitcoin.node;
import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Debug;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;

import java.util.ArrayList;

public class MaliciousNodeBehavior extends DefaultNodeBehavior {
    //TODO: Make these parameterizable
	//private static final int MIN_CHAIN_LENGTH = 6;
	private static final int MIN_CHAIN_LENGTH = 2;
    private static final int MAX_CHAIN_LENGTH = 15;

    private ArrayList<Block> hiddenChain=new ArrayList<Block>();
    private Transaction targetTransaction;
	private int targetTxID;

    private boolean isAttackInProgress = false;
    private BitcoinNode node;
    private HonestNodeBehavior honestBehavior;
    private int blockchainSizeAtAttackStart;
    private Block lastBlock;
    private int publicChainGrowthSinceAttack;

    /** Number of block confirmations required before starting the attack (0 = immediate). */
    private int requiredConfirmationsBeforeAttack = 0;

    /** Height of the block containing the target transaction (-1 if not yet found). */
    private int targetTransactionBlockHeight = -1;


    
    /**
     * Constructor. Creates also a shadow honest behavior object.
     * @param node The node which has the behavior.
     */
    public MaliciousNodeBehavior(BitcoinNode node) {
        this.isAttackInProgress = false;
        this.node = node;
        this.honestBehavior = new HonestNodeBehavior(node);
    }



    @Override
    public void event_NodeReceivesClientTransaction(Transaction t, long time) {
        honestBehavior.event_NodeReceivesClientTransaction(t, time);
    }

    @Override
    public void event_NodeReceivesPropagatedTransaction(Transaction t, long time) {
        honestBehavior.event_NodeReceivesPropagatedTransaction(t, time);
    }

    private void startAttack(Block b) {
        BitcoinReporter.reportBlockEvent(
				Simulation.currentSimulationID,
        		Simulation.currTime,
        		System.currentTimeMillis() - Simulation.sysStartTime,
        		b.getCurrentNodeID(),
                b.getID(),
                ((b.getParent() == null) ? -1 : b.getParent().getID()),
                b.getHeight(),
                b.printIDs(";"),
                "Target Transaction Appeared - Attack Starts", 
                b.getValidationDifficulty(),
                b.getValidationCycles());
        isAttackInProgress = true;
        calculateBlockchainSizeAtAttackStart();
        Debug.p("Starting attack! at time " + Simulation.currTime);
    }


    @Override
    public void event_NodeReceivesPropagatedContainer(ITxContainer t) {
        Block b = (Block) t;
        
        //updateBlockContext(b);
        
        b.setCurrentNodeID(node.getID());
        b.setLastBlockEvent("Node Receives Propagated Block");
        b.setValidationCycles(-1.0);
        b.setValidationDifficulty(-1.0);
     
        BitcoinReporter.reportBlockEvent(
				Simulation.currentSimulationID,
        		Simulation.currTime,
        		System.currentTimeMillis() - Simulation.sysStartTime,
        		b.getCurrentNodeID(),
                b.getID(),
                ((b.getParent() == null) ? -1 : b.getParent().getID()),b.getHeight(),
                b.printIDs(";"),
                b.getLastBlockEvent(), 
                b.getValidationDifficulty(),
                b.getValidationCycles());
        
        //TODO: why is this below a t and not a b?
        if (!isAttackInProgress && t.contains(targetTxID)) {
            lastBlock = (Block) b.getParent();
            if (!node.getStructure().contains(b)) {
                //reportBlockEvent(b, b.getContext().blockEvt);
                handleNewBlockReceptionInAttack(b);

                // Record the transaction block height if not already set
                if (targetTransactionBlockHeight == -1) {
                    targetTransactionBlockHeight = b.getHeight();
                }

                // Check if we have enough confirmations before starting attack
                if (hasEnoughConfirmations()) {
                    startAttack(b);
                } else {
                    // Not enough confirmations yet - log and wait
                    int currentConfirmations = getCurrentConfirmations();
                    Debug.p("Target transaction appeared at height " + targetTransactionBlockHeight +
                            ", waiting for " + requiredConfirmationsBeforeAttack +
                            " confirmations. Current: " + currentConfirmations);
                }
            } else { //Does not contain target transaction
                BitcoinReporter.reportBlockEvent(
						Simulation.currentSimulationID,
                		Simulation.currTime,
                		System.currentTimeMillis() - Simulation.sysStartTime,
                		b.getCurrentNodeID(),
                        b.getID(),
                        ((b.getParent() == null) ? -1 : b.getParent().getID()),b.getHeight(),
                        b.printIDs(";"),
                        "Propagated Block Discarded (already exists)",
                        b.getValidationDifficulty(),
                        b.getValidationCycles());
                //reportBlockEvent(b, "Propagated Block Discarded");
            }
        }
        else if (isAttackInProgress) { //attack is in progress or block does not contain target
            if (!node.getStructure().contains(b)) {
                //reportBlockEvent(b, b.getContext().blockEvt);
                handleNewBlockReceptionInAttack(b);
            } else {
                //Discard the block and report the event.
                BitcoinReporter.reportBlockEvent(
						Simulation.currentSimulationID,
                		Simulation.currTime,
                		System.currentTimeMillis() - Simulation.sysStartTime,
                		b.getCurrentNodeID(),
                        b.getID(),
                        ((b.getParent() == null) ? -1 : b.getParent().getID()),b.getHeight(),
                        b.printIDs(";"),
                        "Propagated Block Discarded (already exists)",
                        b.getValidationDifficulty(),
                        b.getValidationCycles());
                //reportBlockEvent(b, "Propagated Block Discarded");
            }
            checkAndRevealHiddenChain(b);
        }
        else { //attack not in progress
            // Check if we're waiting for confirmations and now have enough
            if (targetTransactionBlockHeight != -1 && !hasEnoughConfirmations()) {
                // We've seen the target transaction but don't have enough confirmations yet
                // Check if this new block gives us enough confirmations
                if (!node.getStructure().contains(b)) {
                    honestBehavior.handleNewBlockReception(b);

                    // After adding the block, check again if we have enough confirmations
                    if (hasEnoughConfirmations()) {
                        // Find the block containing the target transaction and start the attack
                        Block targetBlock = findBlockContainingTransaction(targetTxID);
                        if (targetBlock != null) {
                            lastBlock = (Block) targetBlock.getParent();
                            startAttack(targetBlock);
                        }
                    } else {
                        int currentConfirmations = getCurrentConfirmations();
                        Debug.p("Received new block at height " + b.getHeight() +
                                ", waiting for " + requiredConfirmationsBeforeAttack +
                                " confirmations. Current: " + currentConfirmations);
                    }
                } else {
                    BitcoinReporter.reportBlockEvent(
                            Simulation.currentSimulationID,
                            Simulation.currTime,
                            System.currentTimeMillis() - Simulation.sysStartTime,
                            b.getCurrentNodeID(),
                            b.getID(),
                            ((b.getParent() == null) ? -1 : b.getParent().getID()),b.getHeight(),
                            b.printIDs(";"),
                            "Propagated Block Discarded (already exists)",
                            b.getValidationDifficulty(),
                            b.getValidationCycles());
                }
            } else {
                // Normal honest behavior (no target transaction yet, or confirmations already met)
                if (!node.getStructure().contains(b)) {
                    //reportBlockEvent(b, b.getContext().blockEvt);
                    honestBehavior.handleNewBlockReception(b);
                } else {
                    //reportBlockEvent(b, "Propagated Block Discarded");
                    BitcoinReporter.reportBlockEvent(
                            Simulation.currentSimulationID,
                            Simulation.currTime,
                            System.currentTimeMillis() - Simulation.sysStartTime,
                            b.getCurrentNodeID(),
                            b.getID(),
                            ((b.getParent() == null) ? -1 : b.getParent().getID()),b.getHeight(),
                            b.printIDs(";"),
                            "Propagated Block Discarded (already exists)",
                            b.getValidationDifficulty(),
                            b.getValidationCycles());
                }
            }
        }
    }



    @Override
    public void event_NodeCompletesValidation(ITxContainer t, long time) {
        if (isAttackInProgress) {
            Block newBlock = (Block) t;
            newBlock.validateBlock(node.getMiningPool(),
            		Simulation.currTime, 
            		System.currentTimeMillis()- Simulation.sysStartTime, 
            		node.getID(), 
            		"Node Completes Validation", 
            		node.getOperatingDifficulty(), 
            		node.getProspectiveCycles());
            
            node.completeValidation(node.getMiningPool(), time);

            BitcoinReporter.reportBlockEvent(
					Simulation.currentSimulationID,
            		newBlock.getSimTime_validation(),
            		newBlock.getSysTime_validation() - Simulation.sysStartTime,
            		newBlock.getValidationNodeID(),
            		newBlock.getID(),((newBlock.getParent() == null) ? -1 : newBlock.getParent().getID()),
            		newBlock.getHeight(),
            		newBlock.printIDs(";"),
                    "Node Completes Validation",
                    newBlock.getValidationDifficulty(),
                    newBlock.getValidationCycles());
            
            
            if (!node.getStructure().contains(newBlock)) {
                //reportBlockEvent(newBlock, newBlock.getContext().blockEvt);
                BitcoinReporter.reportBlockEvent(
						Simulation.currentSimulationID,
                		newBlock.getSimTime_validation(),
                		newBlock.getSysTime_validation() - Simulation.sysStartTime,
                		newBlock.getValidationNodeID(),
                		newBlock.getID(),((newBlock.getParent() == null) ? -1 : newBlock.getParent().getID()),
                		newBlock.getHeight(),
                		newBlock.printIDs(";"),
                        "Adding block to hidden chain",
                        newBlock.getValidationDifficulty(),
                        newBlock.getValidationCycles());
                hiddenChain.add(newBlock);
            } else {
                //System.out.println(node.getID()+ " contains " + newBlock.getID() + " in its blockchain in completes validation");
                //System.out.println(node.getID()+ " contains " + newBlock.getID() + " in its blockchain in completes validation");
                //reportBlockEvent(newBlock, "Discarding own Block (ERROR)");
                BitcoinReporter.reportBlockEvent(
						Simulation.currentSimulationID,
                		newBlock.getSimTime_validation(),
                		newBlock.getSysTime_validation() - Simulation.sysStartTime,
                		newBlock.getValidationNodeID(),
                		newBlock.getID(),((newBlock.getParent() == null) ? -1 : newBlock.getParent().getID()),
                		newBlock.getHeight(),
                		newBlock.printIDs(";"),
                        "ERROR: Discarding own Block",
                        newBlock.getValidationDifficulty(),
                        newBlock.getValidationCycles());
            }
            manageMiningPostValidation();
            checkAndRevealHiddenChain(newBlock);
        } else { //Attack not in progress
            Block b = (Block) t;
            b.validateBlock(node.getMiningPool(),
            		Simulation.currTime, 
            		System.currentTimeMillis() - Simulation.sysStartTime, 
            		node.getID(), 
            		"Node Completes Validation", 
            		node.getOperatingDifficulty(), 
            		node.getProspectiveCycles());
            //node.completeValidation(node.miningPool, time);
            node.completeValidation(node.getMiningPool(), time);



            if(b.contains(targetTxID)){
                if (!node.getStructure().contains(b)) {
                    //Report validation
                    //reportBlockEvent(b, b.getContext().blockEvt);
                    BitcoinReporter.reportBlockEvent(
    						Simulation.currentSimulationID,
                    		b.getSimTime_validation(),
                    		b.getSysTime_validation() - Simulation.sysStartTime,
                    		b.getValidationNodeID(),
                    		b.getID(),((b.getParent() == null) ? -1 : b.getParent().getID()),
                    		b.getHeight(),
                    		b.printIDs(";"),
                            "Node Completes Validation",
                            b.getValidationDifficulty(),
                            b.getValidationCycles());

                    node.getStructure().addToStructure(b);
                    node.propagateContainer(b, time);
                    lastBlock = (Block) b.getParent();

                    // Record the transaction block height if not already set
                    if (targetTransactionBlockHeight == -1) {
                        targetTransactionBlockHeight = b.getHeight();
                    }

                    // Check if we have enough confirmations before starting attack
                    if (hasEnoughConfirmations()) {
                        startAttack(b);
                    } else {
                        // Not enough confirmations yet - log and wait
                        int currentConfirmations = getCurrentConfirmations();
                        Debug.p("Node completed validation of block with target transaction at height " +
                                targetTransactionBlockHeight + ", waiting for " +
                                requiredConfirmationsBeforeAttack + " confirmations. Current: " +
                                currentConfirmations);
                    }

                    node.stopMining();
                    node.resetNextValidationEvent();
                    reconstructMiningPool();
                    node.getMiningPool().removeTransaction(targetTxID);
                    considerMining(Simulation.currTime);
                } else {
                    BitcoinReporter.reportBlockEvent(
    						Simulation.currentSimulationID,
                    		b.getSimTime_validation(),
                    		b.getSysTime_validation() - Simulation.sysStartTime,
                    		b.getValidationNodeID(),
                    		b.getID(),((b.getParent() == null) ? -1 : b.getParent().getID()),
                    		b.getHeight(),
                    		b.printIDs(";"),
                            "Error: Discarding own Block",
                            b.getValidationDifficulty(),
                            b.getValidationCycles());
                    System.out.println(node.getID()+ " contains " + b.getID() + " in its blockchain in completes validation");
                    //reportBlockEvent(b, "Discarding own Block (ERROR)");
                }
                node.stopMining();
                node.resetNextValidationEvent();
                reconstructMiningPool();
                node.getMiningPool().removeTransaction(targetTxID);
                considerMining(Simulation.currTime);
            } else {
                b.setParent(node.getStructure().getLongestTip());
                if (!node.getStructure().contains(b)){
                    //reportBlockEvent(b, b.getContext().blockEvt);
                    BitcoinReporter.reportBlockEvent(
    						Simulation.currentSimulationID,
                    		b.getSimTime_validation(),
                    		b.getSysTime_validation() - Simulation.sysStartTime,
                    		b.getValidationNodeID(),
                    		b.getID(),((b.getParent() == null) ? -1 : b.getParent().getID()),
                    		b.getHeight(),
                    		b.printIDs(";"),
                            "Node Completes Validation",
                            b.getValidationDifficulty(),
                            b.getValidationCycles());
                	
                    b.setParent(null);
                    node.getStructure().addToStructure(b);
                    try {
                    	//Propagate a clone of the block to the rest of the network
						node.propagateContainer((ITxContainer) b.clone(), time);
					} catch (CloneNotSupportedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                } else {
                    //reportBlockEvent(b, "Discarding own Block (ERROR)");
                    BitcoinReporter.reportBlockEvent(
    						Simulation.currentSimulationID,
                    		b.getSimTime_validation(),
                    		b.getSysTime_validation() - Simulation.sysStartTime,
                    		b.getValidationNodeID(),
                    		b.getID(),((b.getParent() == null) ? -1 : b.getParent().getID()),
                    		b.getHeight(),
                    		b.printIDs(";"),
                            "Error: Discarding own Block",
                            b.getValidationDifficulty(),
                            b.getValidationCycles());
                }
                honestBehavior.processPostValidationActivities(time);
            }
        }
    }

    private void revealHiddenChain() {
        for (int i = hiddenChain.size()-1; i >= 0; i--) {
            Block b = hiddenChain.get(i);
            b.setParent(i==0 ? lastBlock : hiddenChain.get(i-1));
            node.getStructure().addToStructure(b);
            node.propagateContainer(b, Simulation.currTime);
        }
        isAttackInProgress = false;
        hiddenChain = new ArrayList<Block>();
        node.removeFromPool(targetTxID);
        Debug.p("Chain reveal! at time " + Simulation.currTime);
    }

    
    /*
    private void reportBlockEvent(Block b, String blockEvt) {
        BitcoinReporter.reportBlockEvent(
				Simulation.currentSimulationID,b.getContext().simTime, b.getContext().sysTime  - Simulation.sysStartTime, b.getContext().nodeID,
                b.getID(),((b.getParent() == null) ? -1 : b.getParent().getID()),b.getHeight(),b.printIDs(";"),
                blockEvt, b.getContext().difficulty,b.getContext().cycles);
    }

    private void updateBlockContext(Block b) {
        //TODO: updating of context here seems wrong!
        //Update context information for reporting
        b.getContext().simTime = Simulation.currTime;
        b.getContext().sysTime = System.currentTimeMillis() - Simulation.sysStartTime;
        b.getContext().nodeID = node.getID();
        b.getContext().blockEvt = "Node Receives Propagated Block";
        b.getContext().cycles = -1;
        b.getContext().difficulty = -1;
    }

     */

    public void setTargetTransaction(Transaction targetTransaction) {
        this.targetTransaction = targetTransaction;
    }

    public void setTargetTransaction(int targetTxID) {
        this.targetTxID = targetTxID;
    }

    /**
     * Sets the number of block confirmations required before starting the attack.
     * A confirmation means a new block has been added on top of the block containing the target transaction.
     *
     * @param confirmations the number of confirmations (0 = start attack immediately when transaction appears)
     */
    public void setRequiredConfirmationsBeforeAttack(int confirmations) {
        if (confirmations < 0) {
            throw new IllegalArgumentException("Required confirmations cannot be negative");
        }
        this.requiredConfirmationsBeforeAttack = confirmations;
    }

    /**
     * Gets the number of block confirmations required before starting the attack.
     *
     * @return the required confirmations
     */
    public int getRequiredConfirmationsBeforeAttack() {
        return requiredConfirmationsBeforeAttack;
    }

    
    
    private void manageMiningPostValidation() {
        node.stopMining();
        node.resetNextValidationEvent();
        node.removeFromPool(node.getMiningPool());
        reconstructMiningPool();
        node.getMiningPool().removeTransaction(targetTxID);
        considerMining(Simulation.currTime);
    }

    private void calculateBlockchainSizeAtAttackStart() {
        if (node.getStructure().getBlockchainHeight() == 0) {
            blockchainSizeAtAttackStart = 0;
            return;
        }
        Block tip = node.getStructure().getLongestTip();
        blockchainSizeAtAttackStart = tip.contains(targetTxID) ? tip.getHeight() - 1 : tip.getHeight();
    }

    private void handleNewBlockReceptionInAttack(Block b) {
        node.getStructure().addToStructure(b);
        reconstructMiningPool();
        node.getMiningPool().removeTransaction(targetTxID);
        considerMining(Simulation.currTime);
    }

    private boolean shouldRevealHiddenChain() {
        return (hiddenChain.size() > publicChainGrowthSinceAttack && publicChainGrowthSinceAttack > MIN_CHAIN_LENGTH)
                || publicChainGrowthSinceAttack > MAX_CHAIN_LENGTH;
    }

    private void checkAndRevealHiddenChain(Block b) {
        publicChainGrowthSinceAttack = node.getStructure().getLongestTip().getHeight() - blockchainSizeAtAttackStart;
        if (shouldRevealHiddenChain()) {
            BitcoinReporter.reportBlockEvent(
					Simulation.currentSimulationID,
            		Simulation.currTime,
            		System.currentTimeMillis() - Simulation.sysStartTime,
            		b.getCurrentNodeID(),
                    b.getID(),
                    ((b.getParent() == null) ? -1 : b.getParent().getID()),b.getHeight(),
                    b.printIDs(";"),
                    "Reveal of hidden chain starts here.",
                    b.getValidationDifficulty(),
                    b.getValidationCycles());
            revealHiddenChain();
        }
    }

    /**
     * Calculates the current number of confirmations for the target transaction.
     * A transaction has N confirmations when there are N blocks built on top of
     * the block containing the transaction.
     *
     * @return the number of confirmations, or -1 if transaction not found or no blockchain structure
     */
    private int getCurrentConfirmations() {
        if (targetTransactionBlockHeight == -1) {
            // Transaction block height not yet determined
            return -1;
        }

        if (node.getStructure() == null) {
            return -1;
        }

        Block longestTip = node.getStructure().getLongestTip();
        if (longestTip == null) {
            return -1;
        }

        int currentHeight = longestTip.getHeight();
        int confirmations = currentHeight - targetTransactionBlockHeight;
        return Math.max(0, confirmations);
    }

    /**
     * Checks if enough confirmations have occurred to start the attack.
     *
     * @return true if attack should start, false if waiting for more confirmations
     */
    private boolean hasEnoughConfirmations() {
        if (requiredConfirmationsBeforeAttack == 0) {
            return true; // Immediate attack
        }

        int currentConfirmations = getCurrentConfirmations();
        return currentConfirmations >= requiredConfirmationsBeforeAttack;
    }

    /**
     * Finds and returns the block containing the target transaction.
     * Since Blockchain doesn't expose getBlockchain(), we traverse from the longest tip
     * back to genesis to find the transaction.
     *
     * @param targetTxID the transaction ID to search for
     * @return the block containing the transaction, or null if not found
     */
    private Block findBlockContainingTransaction(int targetTxID) {
        if (node.getStructure() == null) {
            return null;
        }

        Block longestTip = node.getStructure().getLongestTip();
        if (longestTip == null) {
            return null;
        }

        // Traverse from tip to genesis
        Block current = longestTip;
        while (current != null) {
            if (current.contains(targetTxID)) {
                return current;
            }
            current = (Block) current.getParent();
        }

        return null;
    }
}



