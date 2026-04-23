package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TxValuePerSizeComparator;



/**
 * An abstract base class providing default behaviors for Bitcoin network nodes
 * within the {@linkplain ca.yorku.cmg.cnsim.engine.Simulation} environment.
 * <p>
 * This class encapsulates common node behaviors such as transaction receipt,
 * mining initiation and termination, and mining pool reconstruction. Concrete
 * behavior strategies (e.g., honest, selfish, or experimental nodes) extend
 * this class and implement event-handling methods corresponding to key
 * simulation events and/or override the default implementations herein.
 * </p>
 * <p>
 * The {@code DefaultNodeBehavior} acts as a strategy implementation shared by
 * instances of {@linkplain BitcoinNode}. Its role is to decide when and how a
 * node participates in mining activities depending on simulation parameters,
 * transaction value thresholds, and block size constraints.
 * </p>
 *
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 * @see BitcoinNode
 * @see NodeBehaviorStrategy
 * @see Block
 */
public abstract class DefaultNodeBehavior implements NodeBehaviorStrategy {
	
	
    /**
     * The {@linkplain BitcoinNode} to which this behavior strategy is attached.
     * Provides access to node-level state such as mining status, transaction pool,
     * and scheduling functions.
     */
    protected BitcoinNode node; // Reference to the BitcoinNode
	
    
	// -----------------------------------------------
	// PoW AND MINING RELATED BEHAVIORS
	// -----------------------------------------------

    
    /**
     * Evaluates whether the node should be mining and manages transitions between
     * mining and idle states.
     * <p>
     * This method ensures consistency between node state, mining status, and
     * pending validation events. Depending on whether mining is deemed worthwhile
     * (as determined by {@link #isWorthMining()}), this method may:
     * <ul>
     *   <li>Start mining by scheduling a validation event for a new block, or</li>
     *   <li>Stop mining and invalidate any scheduled validation event.</li>
     * </ul>
     *
     * @param time the current simulation time
     * @see #isWorthMining()
     * @see BitcoinNode#isMining()
     * @see BitcoinNode#startMining(long)
     * @see BitcoinNode#stopMining()
     */
	protected void considerMining(long time) {
		if (isWorthMining()) {
			//Start mining and schedule a new validation event
			
			if (!node.isMining()) { //Not mining already

				//It is not mining because it has never mined OR it previous mining has but then abandoned.

				//(node.getNextValidationEvent() != null) && !node.getNextValidationEvent().ignoreEvt() => PROBLEM 
				if ((node.getNextValidationEvent() != null) && !node.getNextValidationEvent().ignoreEvt()) {
					throw new IllegalStateException("Unexpected state of idle miner: active prospective validation");
				}

				
				node.resetNextValidationEvent();

				// Schedule this next validation event.
				long itr = node.scheduleValidationEvent(new Block(node.getMiningPool().getTransactions()), time);
				node.startMining(itr);					
				
				//TODO: what is this?
				/*
				if ((node.getNextValidationEvent() != null) && (node.getNextValidationEvent().getTime() > time) 
						&& false) {
					long nextValTime = node.getNextValidationEvent().getTime();
					node.getNextValidationEvent().ignoreEvt(false);
					node.scheduleValidationEvent_Deterministic(
								new Block(node.getMiningPool().getTransactions()), nextValTime);
					node.startMining(nextValTime - time);
				} else { Normal Behavior } */
			} else { //MINING ALREADY
				if ((node.getNextValidationEvent() == null) || node.getNextValidationEvent().ignoreEvt()) {
					throw new IllegalStateException("Unexpected state of active miner: no prospective validation time.");
				}
			}
		} else { //NOT WORTH MINING
			if (!node.isMining()) {
				if ((node.getNextValidationEvent() != null) && !node.getNextValidationEvent().ignoreEvt()) {
					throw new IllegalStateException("Unexpected state of idle miner: active prospective validation");
				}
				
			} else  {
				if ((node.getNextValidationEvent() == null) || node.getNextValidationEvent().ignoreEvt()) {
					throw new IllegalStateException("Unexpected state of active miner: no prospective validation time.");
				}

				// Stop mining, invalidate any future validation event.
				node.getNextValidationEvent().ignoreEvt(true);
				node.stopMining();
				
				if (!((node.getNextValidationEvent() == null) || ((node.getNextValidationEvent() != null) ? node.getNextValidationEvent().ignoreEvt(): true))) {
					throw new IllegalStateException("Unexpected state of active miner eager to continue mining.");
				}

			}
		}
	}
	
	
    /**
     * Determines whether the current mining pool is sufficiently valuable to
     * justify mining activity.
     *
     * @return {@code true} if the total value of the node’s mining pool exceeds
     *         {@linkplain BitcoinNode#getMinValueToMine()}, otherwise {@code false}
     */
	protected boolean isWorthMining() {
		return((node.getMiningPool().getValue() > node.getMinValueToMine()));
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
        int conflict = node.getSim().getConflictRegistry().getMatch((int) t.getID());

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


    protected boolean transactionContainedInPool (Transaction t) {
    	return(node.getPool().contains(t));
    }
    
    
    protected boolean transactionContainedInStructure (Transaction t) {
    	return(node.getStructure().contains(t));
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
            int conflict = node.getSim().getConflictRegistry().getMatch((int) r.getID());
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
    protected void handleNewBlockReception(Block b) {
    	/* if ((b.getID() == 1717) && node.getID() == 30) {
        	BitcoinReporter.addErrorEntry("1717 processed by Default handleNewBlockReception in 30");
        } */
        // Add block to the blockchain
        node.getStructure().addToStructure(b);
        // Remove block's transactions from the pool
        // (conflicts are not expected to be there since the pool is guarded)
        node.getPool().extractGroup(b);
        // Rebuild the mining pool based on the updated state
        reconstructMiningPool();
        // Reconsider whether to start or stop mining
        considerMining(Simulation.currTime);
        
    	/*
        if ((b.getID() == 1717) && node.getID() == 30) {
        	BitcoinReporter.addErrorEntry("Mining pool is now:" + node.getMiningPool().printIDs(";"));
        	BitcoinReporter.addErrorEntry("Pool is now:" + node.getPool().printIDs(";"));
        } */
        
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
    protected void processPostValidationActivities(long time) {
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
	
	
	
    /**
     * Reconstructs the node’s mining pool by selecting the top transactions
     * according to value-per-size ratio.
     * <p>
     * The number of transactions included is limited by the configuration property
     * {@code bitcoin.maxBlockSize}.
     * </p>
     *
     * @see TxValuePerSizeComparator
     */
	protected void reconstructMiningPool() {
		node.setMiningPool(node.getPool().getTopN(Config.getPropertyLong("bitcoin.maxBlockSize"), 
				new TxValuePerSizeComparator()));
	
        /* if ((node.getID() == 30) && (node.getMiningPool().contains(71) || (node.getMiningPool().contains(72)))) {
            BitcoinReporter.addErrorEntry(Simulation.currTime + ": node 30 completes reconstructs pool such that " + node.getMiningPool().printIDs(";") + ".");
        } */
	}


    /**
     * Handles the receipt of a transaction and updates the mining pool accordingly.
     * <p>
     * This method adds the transaction to the pool, reconstructs the mining pool,
     * and then re-evaluates whether mining should begin or continue. This default implementation
     *  does so by executing the following methods in this sequence: 
     *  <ul>
     *  	<li>{@link BitcoinNode#addTransactionToPool(Transaction)}</li>
     *  	<li>{@link #reconstructMiningPool()}</li>
     *   	<li>{@link #considerMining(long)}</li>
     * </ul>
     *  
     * @param t    the received transaction
     * @param time the current simulation time
     */
	protected void transactionReceipt(Transaction t, long time) {
		node.addTransactionToPool(t);
		reconstructMiningPool();
		considerMining(time);
	}

	
	/**
	 * Checks if transaction is valid.
	 * @param tx The transactions whose validity is 
	 * @return {@code true} if transaction is valid, {@code false} otherwise
	 */
    //protected abstract boolean transactionValid(Transaction tx); 
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract void event_NodeReceivesClientTransaction(Transaction t, long time);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract void event_NodeReceivesPropagatedTransaction(Transaction t, long time);
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract void event_NodeReceivesPropagatedContainer(ITxContainer t);
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract void event_NodeCompletesValidation(ITxContainer t, long time);
}
