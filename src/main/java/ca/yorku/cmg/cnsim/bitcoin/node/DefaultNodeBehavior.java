package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.node.INode;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TransactionGroup;
import ca.yorku.cmg.cnsim.engine.transaction.TxValuePerSizeComparator;

public abstract class DefaultNodeBehavior implements NodeBehaviorStrategy {
	
    protected BitcoinNode node; // Reference to the BitcoinNode
	
    
	// -----------------------------------------------
	// PoW AND MINING RELATED BEHAVIORS
	// -----------------------------------------------

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
	
	public boolean isWorthMining() {
		return((node.getMiningPool().getValue() > node.getMinValueToMine()));
	}

	
	protected void reconstructMiningPool() {
		node.setMiningPool(node.getPool().getTopN(Config.getPropertyLong("bitcoin.maxBlockSize"), 
				new TxValuePerSizeComparator()));
	}

	public void completeValidation(TransactionGroup miningPool, long time) {
		node.event_NodeCompletesValidation(miningPool, time);
	}
	
	protected void transactionReceipt(Transaction t, long time) {
		node.addTransactionToPool(t);
		reconstructMiningPool();
		considerMining(time);
	}

    
	@Override
	public abstract void event_NodeReceivesClientTransaction(Transaction t, long time);
	
	@Override
	public abstract void event_NodeReceivesPropagatedTransaction(Transaction t, long time);
	
	@Override
	public abstract void event_NodeReceivesPropagatedContainer(ITxContainer t);
	
	@Override
	public abstract void event_NodeCompletesValidation(ITxContainer t, long time);
}
