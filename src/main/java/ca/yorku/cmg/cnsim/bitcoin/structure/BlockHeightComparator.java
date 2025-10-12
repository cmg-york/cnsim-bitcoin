package ca.yorku.cmg.cnsim.bitcoin.structure;

import java.util.Comparator;


/**
 * Comparator for {@linkplain ca.yorku.cmg.cnsim.bitcoin.Block} objects that orders them
 * based on their block height, with ties broken by block ID.
 * <p>
 * Higher blocks (larger height) are considered "less" to achieve descending order.
 * <p>
 * This comparator is suitable for sorting a list of blocks from the most recent to the earliest.
 * 
 * @see java.util.Comparator
 * @see ca.yorku.cmg.cnsim.bitcoin.Block
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 */
public class BlockHeightComparator implements Comparator<Block> {

	
    /**
     * Compares two {@linkplain ca.yorku.cmg.cnsim.bitcoin.Block} objects.
     *
     * <p>The comparison is primarily by block height in descending order. If the heights
     * are equal, the comparison falls back to the block ID in descending order.</p>
     *
     * @param b1 the first block to compare
     * @param b2 the second block to compare
     * @return a negative integer if {@code b1} is "greater" than {@code b2}, 
     *         a positive integer if {@code b1} is "less" than {@code b2}, 
     *         or zero if they are considered equal
     */
	@Override
	public int compare(Block b1, Block b2) {
		if (b1.getHeight() < b2.getHeight())
			return 1;
		else 
			if (b1.getHeight() == b2.getHeight()) 
				if (b1.getID() < b2.getID())
					return 1;
				else
					return -1;
			else //height is greater
				return -1;
	}

}
