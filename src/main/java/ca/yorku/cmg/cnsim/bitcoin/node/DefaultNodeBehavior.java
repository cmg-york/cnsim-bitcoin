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
	
    
	@Override
	public abstract void event_NodeReceivesClientTransaction(Transaction t, long time);
	
	@Override
	public abstract void event_NodeReceivesPropagatedTransaction(Transaction t, long time);
	
	@Override
	public abstract void event_NodeReceivesPropagatedContainer(ITxContainer t);
	
	@Override
	public abstract void event_NodeCompletesValidation(ITxContainer t, long time);
}
