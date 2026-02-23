package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;

public class HonestNodeBehaviorLimited extends HonestNodeBehavior {

	public HonestNodeBehaviorLimited(BitcoinNode node) {
		super(node);
	}

	@Override
    protected boolean transactionContainedInStructure (Transaction t) {
    	return(false);
    }
	
	
	@Override
    protected void handleNewBlockReception(Block b) {
        // Add block to the blockchain
        node.getStructure().addToStructure(b);
    }
	
}
