package ca.yorku.cmg.cnsim.bitcoin.node.stubs;

import ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNode;
import ca.yorku.cmg.cnsim.bitcoin.node.HonestNodeBehaviorLimited;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TxValuePerSizeComparator;

public class HonestNodeBehaviorLimited4Test extends HonestNodeBehaviorLimited {

	public HonestNodeBehaviorLimited4Test(BitcoinNode node) {
		super(node);
	}
	
	public boolean conflictFree(Transaction t) {
		return(true);
	}
	
    public boolean dependenciesPresent(Transaction t) {
    	return(true);
    }
    
	@Override
    protected void reconstructMiningPool() {
		node.setMiningPool(node.getPool().getTopN(5000000, 
				new TxValuePerSizeComparator()));
	}
	
	@Override
	protected Block getConflictBlock(Block b) {
		int prev = Block.getCurrID();
		Block c = new Block();
		Block.setCurrID(prev);
    	return (c);
    }
	
	
}
