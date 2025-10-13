package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;


/**
 * Implements the canonical ("honest") node behavior in the simulated Bitcoin
 * network. An {@code HonestNodeBehavior} reacts to incoming transactions and
 * blocks in a manner consistent with the Bitcoin consensus protocol—verifying,
 * propagating, and mining without deviation or manipulation.
 * <p>
 * This class extends {@linkplain DefaultNodeBehavior}, inheriting core
 * Proof-of-Work and mining logic, and specializes it for an honest mining
 * strategy. The node:
 * </p>
 * <ul>
 *   <li>Receives transactions from clients and other nodes.</li>
 *   <li>Propagates valid transactions and blocks through the network.</li>
 *   <li>Validates mined blocks and integrates them into its local blockchain.</li>
 *   <li>Stops and restarts mining as required by consensus conditions.</li>
 * </ul>
 * <p>
 * All simulation-time and reporting operations are integrated with
 * {@linkplain BitcoinReporter} and {@linkplain Simulation} to allow detailed
 * tracking of network dynamics.
 * </p>
 *
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 * @see BitcoinNode
 * @see DefaultNodeBehavior
 * @see NodeBehaviorStrategy
 * @see BitcoinReporter
 */
public class HonestNodeBehavior extends DefaultNodeBehavior {

	
	// -----------------------------------------------
	// CONSTRUCTORS
	// -----------------------------------------------
	
    /**
     * Constructs an honest node behavior strategy and binds it to a specific
     * {@linkplain BitcoinNode} instance.
     *
     * @param node the node to which this behavior strategy is assigned
     */
    public HonestNodeBehavior(BitcoinNode node) {
        this.node = node;
    }

    
    // -----------------------------------------------
    // EVENT HANDLING
    // -----------------------------------------------
    
    /**
     * Handles transactions received directly from clients.
     * <p>
     * The node validates and adds the transaction to its pool (if appropriate),
     * then propagates it to peers. 
     * </p>
     *
     * @param t    the received transaction
     * @param time the current simulation time
     */
    @Override
    public void event_NodeReceivesClientTransaction(Transaction t, long time) {
        // Process the transaction as per normal rules
        // For instance, add the transaction to the node's pool if it's valid
        transactionReceipt(t,time);
        node.propagateTransaction(t,time);
    }

    /**
     * Handles transactions propagated from other nodes.
     * <p>
     * The node accepts new transactions not already present in its pool or
     * blockchain, adding them to its local pool and reconsidering whether to
     * initiate mining.
     * </p>
     *
     * @param t    the propagated transaction
     * @param time the current simulation time
     */
    @Override
    public void event_NodeReceivesPropagatedTransaction(Transaction t, long time) {
        // Handle reception of propagated transactions
        // Add to the pool if not already present and it's valid
        if (!node.getPool().contains(t) && !node.getStructure().contains(t)) {
            transactionReceipt(t,time);
        }
    }

    
    /**
     * Handles reception of a propagated block from another node.
     * <p>
     * The method validates structural consistency, logs the event using
     * {@linkplain BitcoinReporter}, and integrates the new block into the
     * blockchain if not already present.
     * </p>
     *
     * @param t the propagated transaction container (expected to be a {@linkplain Block})
     */
    @Override
    public void event_NodeReceivesPropagatedContainer(ITxContainer t) {
        Block b = (Block) t;
    
        b.setCurrentNodeID(node.getID());
        b.setLastBlockEvent("Node Receives Propagated Block");
        b.setValidationCycles(-1.0);
        b.setValidationDifficulty(-1.0);
     
        BitcoinReporter.reportBlockEvent(
				Simulation.currentSimulationID,
        		Simulation.currTime,
        		System.currentTimeMillis()- Simulation.sysStartTime,
        		b.getCurrentNodeID(),
                b.getID(),
                ((b.getParent() == null) ? -1 : b.getParent().getID()),b.getHeight(),
                b.printIDs(";"),
                b.getLastBlockEvent(), 
                b.getValidationDifficulty(),
                b.getValidationCycles());
                
        if (!node.getStructure().contains(b)){
            handleNewBlockReception(b);
        } else {
            //Discard the block and report the event.
            //reportBlockEvent(b, "Propagated Block Discarded");
        	BitcoinReporter.addErrorEntry("Node::event_NodeReceivesPropagatedContainer: NodeBlock " + b.getID() + " containing " + b.printIDs(",") + " received through propagation is found to overlap with structure.");
            b.setLastBlockEvent("ERROR: propagated Block already exists");
        	BitcoinReporter.reportBlockEvent(
					Simulation.currentSimulationID,
            		Simulation.currTime,
            		System.currentTimeMillis() - Simulation.sysStartTime,
            		b.getCurrentNodeID(),
                    b.getID(),
                    ((b.getParent() == null) ? -1 : b.getParent().getID()),
                    b.getHeight(),
                    b.printIDs(";"),
                    b.getLastBlockEvent(), 
                    b.getValidationDifficulty(),
                    b.getValidationCycles());
        }
    }



    /**
     * Handles completion of block validation by this node.
     * <p>
     * Upon successful validation, the block is reported, added to the local
     * blockchain, and propagated to peers. The mining process is reset, and the
     * node re-evaluates whether to begin mining again.
     * </p>
     *
     * @param t    the transaction container (expected to be a {@linkplain Block})
     * @param time the current simulation time
     */
    @Override
    public void event_NodeCompletesValidation(ITxContainer t, long time) {
        Block b = (Block) t;
        
        //Add validation information to the block.
        b.validateBlock(node.getMiningPool(),
                Simulation.currTime,
                System.currentTimeMillis() - Simulation.sysStartTime,
                node.getID(),
                "Node Completes Validation",
                node.getOperatingDifficulty(),
                node.getProspectiveCycles());


        node.completeValidation(node.getMiningPool(), time);


        //Report the validation event
        BitcoinReporter.reportBlockEvent(
				Simulation.currentSimulationID,
        		b.getSimTime_validation(),
        		b.getSysTime_validation(),
        		b.getValidationNodeID(),
                b.getID(),((b.getParent() == null) ? -1 : b.getParent().getID()),
                b.getHeight(),
                b.printIDs(";"),
                "Node Completes Validation",
                b.getValidationDifficulty(),
                b.getValidationCycles());
        
        
        b.setParent(node.getStructure().getLongestTip());
        if (!node.getStructure().contains(b)) {
            b.setParent(null);
            //Add block to blockchain
            node.getStructure().addToStructure(b);
            
            //Propagate a clone of the block to the rest of the network
            try {
				node.propagateContainer((ITxContainer) b.clone(), time);
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
        } else {
        	BitcoinReporter.addErrorEntry("Node::event_NodeCompletesValidation: Block " + b.getID() + " containing " + b.printIDs(",") + " just validated is found to overlap with structure. This shouldn't happen as the node always updates its miningpool.");
            BitcoinReporter.reportBlockEvent(
					Simulation.currentSimulationID,
            		b.getSimTime_validation(),
            		b.getSysTime_validation()- Simulation.sysStartTime,
            		b.getValidationNodeID(),
                    b.getID(),((b.getParent() == null) ? -1 : b.getParent().getID()),
                    b.getHeight(),
                    b.printIDs(";"),
                    "Discarding own Block (ERROR)",
                    b.getValidationDifficulty(),
                    b.getValidationCycles());
            
        }

        processPostValidationActivities(time);
    }

    
    
    // -----------------------------------------------------------------------
    // HELPER METHODS
    // -----------------------------------------------------------------------


    /**
     * Integrates a newly received block into the node’s blockchain and updates
     * the transaction pool accordingly.
     * <p>
     * The mining pool is reconstructed, and mining activity is reconsidered in
     * light of the updated chain.
     * </p>
     *
     * @param b the newly received block
     */
    void handleNewBlockReception(Block b) {
        //Add block to blockchain
        node.getStructure().addToStructure(b);
        //Remove block transactions from pool.
        node.getPool().extractGroup(b);
        // Reconstruct mining pool based on the new information.
        reconstructMiningPool();
        //Consider starting or stopping mining.
        considerMining(Simulation.currTime);
    }

    
    /**
     * Performs cleanup and re-initialization steps following successful block
     * validation.
     * <p>
     * The node stops mining, clears pending validation events, removes validated
     * transactions from its pool, and decides whether to restart mining based on
     * remaining transactions.
     * </p>
     * <p><b>TODO:</b> Clarify the rationale for resetting mining and events post-validation.</p>
     *
     * @param time the current simulation time
     */
    void processPostValidationActivities(long time) {
        //Stop mining for now. TODO: why do you do this?
        node.stopMining();
        //Reset the next validation event. TODO: why do you do this?
        node.resetNextValidationEvent();
        //Remove the block's transactions from the mining pool.
        node.removeFromPool(node.getMiningPool());
        //Reconstruct mining pool, with whatever other transactions are there.
        reconstructMiningPool();
        //Consider if it is worth mining.
        considerMining(time);
    }


}
