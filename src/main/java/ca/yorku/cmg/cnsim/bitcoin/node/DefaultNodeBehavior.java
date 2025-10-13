package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
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
			if (!node.isMining()) {
				//TODO: Turn these assertions into exceptions?
				//It is not mining because it has never OR it has but then abandoned.
				assert((node.getNextValidationEvent() == null) || ((node.getNextValidationEvent() != null) ? node.getNextValidationEvent().ignoreEvt(): true));

				long interval = node.scheduleValidationEvent(new Block(node.getMiningPool().getTransactions()), time);				
				node.startMining(interval);
			} else {
				
				assert((node.getNextValidationEvent() != null) && !node.getNextValidationEvent().ignoreEvt());
				
			}
		} else {
			if (!node.isMining()) {
				assert((node.getNextValidationEvent() == null) || node.getNextValidationEvent().ignoreEvt());
				//All good otherwise!
			} else  {
				// Stop mining, invalidate any future validation event.
				assert((node.getNextValidationEvent() != null) && !node.getNextValidationEvent().ignoreEvt());
				node.getNextValidationEvent().ignoreEvt(true);
				
				node.stopMining();
				
				assert((node.getNextValidationEvent() == null) || ((node.getNextValidationEvent() != null) ? node.getNextValidationEvent().ignoreEvt(): true));
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
