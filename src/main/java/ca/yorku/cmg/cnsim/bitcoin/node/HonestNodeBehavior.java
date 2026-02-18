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


    // ================================
    // CONSTRUCTORS
    // ================================

    /**
     * Constructs an honest node behavior strategy and binds it to a specific
     * {@linkplain BitcoinNode} instance.
     *
     * @param node the node to which this behavior strategy is assigned
     */
    public HonestNodeBehavior(BitcoinNode node) {
        this.node = node;
    }


    // ================================
    // EVENT HANDLERS
    // ================================

    /**
     * Handles transactions received directly from clients.
     * <p>
     * The transaction is accepted if it is conflict-free and all its dependencies
     * are present. If accepted, it is added to the pool and broadcast to peers.
     * Otherwise it is discarded and the rejection reason is logged.
     * </p>
     *
     * @param t    the received transaction
     * @param time the current simulation time
     */
    @Override
    public void event_NodeReceivesClientTransaction(Transaction t, long time) {
        boolean conflictFree = conflictFree(t);
        boolean dependenciesPresent = dependenciesPresent(t);

        if (conflictFree && dependenciesPresent) {
            // Transaction can be added and broadcast
            transactionReceipt(t, time);
            node.broadcastTransaction(t, time);
        } else {
            // Dependencies and/or conflict constraints not satisfied - discard
            String msg = (dependenciesPresent ? " " : " dependencies not satisfied ") +
                         (conflictFree ? " " : " conflicts present");
            BitcoinReporter.addEvent(
                    Simulation.currentSimulationID,
                    -1,
                    Simulation.currTime,
                    System.currentTimeMillis() - Simulation.sysStartTime,
                    "Discarding Tx due to: " + msg,
                    node.getID(),
                    t.getID(),
                    "");
        }
    }

    /**
     * Handles transactions propagated from other nodes.
     * <p>
     * The transaction is accepted if it is conflict-free, all its dependencies are
     * present, and it is not already in the pool or blockchain structure. If
     * accepted, it is added to the pool and mining is reconsidered. Otherwise it
     * is discarded and the rejection reason is logged.
     * </p>
     *
     * @param t    the propagated transaction
     * @param time the current simulation time
     */
    @Override
    public void event_NodeReceivesPropagatedTransaction(Transaction t, long time) {
        boolean conflictFree = conflictFree(t);
        boolean dependenciesPresent = dependenciesPresent(t);
        boolean containedInPool = node.getPool().contains(t);
        boolean containedInStructure = node.getStructure().contains(t);

        if (conflictFree && dependenciesPresent) {
            if (!containedInPool && !containedInStructure) {
                // Transaction can be received
                transactionReceipt(t, time);
            } else {
                // Transaction is already in the pool or blockchain structure
                String msg = (containedInPool ? " pool, " : "") + (containedInStructure ? " structure." : "");
                BitcoinReporter.addEvent(
                        Simulation.currentSimulationID,
                        -1,
                        Simulation.currTime,
                        System.currentTimeMillis() - Simulation.sysStartTime,
                        "Discarding Tx due to: tx contained in system" + msg,
                        node.getID(),
                        t.getID(),
                        "");
            }
        } else {
            // Dependencies and/or conflict constraints are not satisfied
            String msg = (dependenciesPresent ? " " : " dependencies not satisfied ") +
                         (conflictFree ? " " : " conflicts present");
            BitcoinReporter.addEvent(
                    Simulation.currentSimulationID,
                    -1,
                    Simulation.currTime,
                    System.currentTimeMillis() - Simulation.sysStartTime,
                    "Discarding Tx due to: " + msg,
                    node.getID(),
                    t.getID(),
                    "");
        }
    }

    /**
     * Handles reception of a propagated block from another node.
     * <p>
     * Logs the reception event, constructs a conflict block to check for
     * structural conflicts, and validates the block against the local blockchain.
     * A block is accepted if:
     * <ul>
     *   <li>None of its transactions are already in the structure on the chain
     *       where it would be placed.</li>
     *   <li>None of its transactions conflict with transactions already in the
     *       structure.</li>
     *   <li>All of its transaction dependencies are satisfied by the structure.</li>
     * </ul>
     * Accepted blocks are integrated via {@link #handleNewBlockReception(Block)}.
     * Rejected blocks are logged as errors.
     * </p>
     *
     * @param t the propagated container (expected to be a {@linkplain Block})
     */
    @Override
    public void event_NodeReceivesPropagatedContainer(ITxContainer t) {
        Block b = (Block) t;

        // The block is a copy of the block object validated by the originating node
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
                ((b.getParent() == null) ? -1 : b.getParent().getID()), b.getHeight(),
                b.printIDs(";"),
                b.getLastBlockEvent(),
                b.getValidationDifficulty(),
                b.getValidationCycles());

        // Create an artificial block containing all conflict-group transactions of b
        Block cB = getConflictBlock(b);

        // VALIDATE BLOCK
        if (!node.getStructure().contains(b) &&          // no tx of b overlaps the chain it would join
            !node.getStructure().contains(cB) &&          // no conflicting tx is already in the structure
            node.getStructure().satisfiesDependencies(b, node.getSim().getDependencyRegistry())) {
            // Block is valid - accept it
            handleNewBlockReception(b);
        } else {
            // Block is invalid - discard and log the reason
            String msg = "";
            if (node.getStructure().contains(b)) {
                msg += "overlap with structure, ";
            }
            if (node.getStructure().contains(cB)) {
                msg += "conflict with structure, ";
            }
            if (!node.getStructure().satisfiesDependencies(b, node.getSim().getDependencyRegistry())) {
                msg += "not satisfy dependencies, ";
            }

            BitcoinReporter.addErrorEntry("Node::event_NodeReceivesPropagatedContainer: (" +
                    node.getSim().getSimID() + "," + Simulation.currTime + ") Node " + this.node.getID() +
                    " Block " + b.getID() + " containing " + b.printIDs(",") +
                    " received through propagation is found to " + msg + ".");
            b.setLastBlockEvent("ERROR: propagated Block discarded");
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

            BitcoinReporter.addEvent(
                    Simulation.currentSimulationID,
                    -1,
                    Simulation.currTime,
                    System.currentTimeMillis() - Simulation.sysStartTime,
                    "Discarding Propagated Container due to: " + msg,
                    node.getID(),
                    t.getID(),
                    "");
        }
    }

    /**
     * Handles completion of block validation by this node.
     * <p>
     * Records validation metadata, logs the event, runs a sanity check to detect
     * unexpected overlaps with the existing structure, adds the block to the local
     * blockchain, broadcasts a clone to peers, and triggers post-validation
     * activities (mining pool update, mining reconsideration).
     * </p>
     *
     * @param t    the validated container (expected to be a {@linkplain Block})
     * @param time the current simulation time
     */
    @Override
    public void event_NodeCompletesValidation(ITxContainer t, long time) {
        Block b = (Block) t;

        // Record validation metadata on the block
        b.validateBlock(node.getMiningPool(),
                Simulation.currTime,
                System.currentTimeMillis() - Simulation.sysStartTime,
                node.getID(),
                "Node Completes Validation",
                node.getOperatingDifficulty(),
                node.getProspectiveCycles());

        // Run default post-validation actions (cycle stats etc.)
        node.completeValidation(node.getMiningPool(), time);

        // Report the validation event
        BitcoinReporter.reportBlockEvent(
                Simulation.currentSimulationID,
                b.getSimTime_validation(),
                b.getSysTime_validation(),
                b.getValidationNodeID(),
                b.getID(), ((b.getParent() == null) ? -1 : b.getParent().getID()),
                b.getHeight(),
                b.printIDs(";"),
                "Node Completes Validation",
                b.getValidationDifficulty(),
                b.getValidationCycles());

        // SANITY CHECK: verify the validated block does not overlap the structure
        b.setParent(node.getStructure().getLongestTip());
        if (node.getStructure().contains(b)) {
            String msg = "Node::event_NodeCompletesValidation: Block " + b.getID() +
                    " containing " + b.printIDs(",") +
                    " just validated is found to overlap with structure: \n" +
                    node.getStructure().printStructure() +
                    " Orphans are: \n:" + node.getStructure().printOrphans() +
                    "\n This should not happen as the node always updates its miningpool" +
                    " to have no overlaps with structure.\n\n";
            BitcoinReporter.addErrorEntry(msg);
            BitcoinReporter.reportBlockEvent(
                    Simulation.currentSimulationID,
                    b.getSimTime_validation(),
                    b.getSysTime_validation() - Simulation.sysStartTime,
                    b.getValidationNodeID(),
                    b.getID(), ((b.getParent() == null) ? -1 : b.getParent().getID()),
                    b.getHeight(),
                    b.printIDs(";"),
                    "Validating Inconsistent Block (WARNING) - See ERROR log.",
                    b.getValidationDifficulty(),
                    b.getValidationCycles());
        }
        // END SANITY CHECK

        // Reset parent to null and let Structure find the correct parent during insertion
        b.setParent(null);
        node.getStructure().addToStructure(b);

        // Propagate a clone of the block to the rest of the network
        try {
            node.broadcastContainer((ITxContainer) b.clone(), time);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        processPostValidationActivities(time);
    }


    // ================================
    // HELPER METHODS
    // ================================

    /**
     * Returns whether the transaction is free of conflicts with the node's current
     * pool, mining pool, and blockchain structure.
     * <p>
     * Looks up the transaction's conflict partner in the conflict registry and
     * checks whether that partner is present anywhere in the node's state.
     * </p>
     *
     * @param t the transaction to check
     * @return {@code true} if no conflicting transaction is present; {@code false} otherwise
     * @throws IllegalStateException if the conflict entry for {@code t} is uninitialized
     */
    protected boolean conflictFree(Transaction t) {
        long conflict = node.getSim().getConflictRegistry().getMatch((int) t.getID());

        if (conflict == -2) throw new IllegalStateException(
                "Conflict for transaction " + t.getID() + " uninitialized");

        // Transaction is conflict-free if no conflict partner exists, or if the
        // conflict partner is absent from the pool, structure, and mining pool
        boolean conflictFree =
                (conflict == -1)  // no conflict partner registered
                ||
                !(node.getPool().contains(conflict)
                  || node.getStructure().contains(conflict)
                  || node.getMiningPool().contains(conflict));

        return conflictFree;
    }

    /**
     * Returns whether all dependencies of the transaction are satisfied.
     * <p>
     * Delegates to {@code TransactionGroup#satisfiesDependenciesOf_Incl_3rdGroup},
     * which checks the pool, mining pool, and the full blockchain structure as a
     * third group.
     * </p>
     *
     * @param t the transaction to check
     * @return {@code true} if all dependencies are satisfied; {@code false} otherwise
     */
    protected boolean dependenciesPresent(Transaction t) {
        return node.getPool().satisfiesDependenciesOf_Incl_3rdGroup(
                t.getID(),
                // Include the entire blockchain structure as the third dependency group
                node.getStructure().getTransactionGroup(),
                node.getSim().getDependencyRegistry());
    }

    /**
     * Builds a synthetic block containing the conflict-group counterparts of each
     * transaction in {@code b}, based on the node's conflict registry.
     * <p>
     * The returned transactions are manufactured stand-ins (same ID only); they are
     * not the real transaction objects. Transaction equality is assumed to be
     * determined by ID.
     * </p>
     *
     * @param b the block whose transactions are examined for conflicts
     * @return a new {@linkplain Block} containing one stand-in transaction per
     *         conflicting transaction in {@code b}; empty if none conflict
     */
    protected Block getConflictBlock(Block b) {
        Block conflictBlock = new Block();
        for (Transaction r : b.getTransactions()) {
            long conflict = node.getSim().getConflictRegistry().getMatch((int) r.getID());
            if (conflict != -1) {
                conflictBlock.addTransaction(new Transaction(conflict));
            }
        }
        return conflictBlock;
    }

    /**
     * Integrates a newly received block into the node's blockchain and updates
     * the transaction pool accordingly.
     * <p>
     * Adds the block to the structure, removes its transactions from the pool,
     * reconstructs the mining pool, and reconsiders whether to mine.
     * </p>
     *
     * @param b the newly received and validated block
     */
    void handleNewBlockReception(Block b) {
        // Add block to the blockchain
        node.getStructure().addToStructure(b);
        // Remove block's transactions from the pool
        // (conflicts are not expected to be there since the pool is guarded)
        node.getPool().extractGroup(b);
        // Rebuild the mining pool based on the updated state
        reconstructMiningPool();
        // Reconsider whether to start or stop mining
        considerMining(Simulation.currTime);
    }

    /**
     * Performs cleanup and re-initialization steps following successful block
     * validation.
     * <p>
     * Stops mining, resets the pending validation event, removes the validated
     * transactions from the mining pool, reconstructs the pool, and reconsiders
     * whether to restart mining.
     * </p>
     *
     * @param time the current simulation time
     */
    void processPostValidationActivities(long time) {
        // Stop mining for now
        node.stopMining();
        // Reset the next validation event
        node.resetNextValidationEvent();
        // Remove validated transactions from the mining pool
        node.removeFromPool(node.getMiningPool());
        // Reconstruct the mining pool with remaining transactions
        reconstructMiningPool();
        // Reconsider whether it is worth mining
        considerMining(time);
    }
}
