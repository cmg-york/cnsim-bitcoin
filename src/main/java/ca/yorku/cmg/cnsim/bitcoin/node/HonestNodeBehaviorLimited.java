package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.structure.Block;

public class HonestNodeBehaviorLimited extends HonestNodeBehavior {

	public HonestNodeBehaviorLimited(BitcoinNode node) {
		super(node);
	}

	@Override
    protected void handleNewBlockReception(Block b) {
        // Add block to the blockchain
        node.getStructure().addToStructure(b);
    }
	
}
