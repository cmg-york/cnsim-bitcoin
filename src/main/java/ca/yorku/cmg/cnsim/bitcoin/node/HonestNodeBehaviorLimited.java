package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;

/**
 * A testing-only variant of {@linkplain HonestNodeBehavior} that loosens two
 * validation checks so unit tests can drive predictable flows through the pool
 * and mining logic without the full blockchain-consistency machinery engaging.
 * <p>
 * Two behaviors are weakened relative to the standard honest strategy:
 * <ul>
 *   <li>The "is this transaction already in the local structure?" guard is
 *       removed, letting a test replay the same transaction through the pool
 *       without the blockchain silently swallowing it.</li>
 *   <li>New-block reception is reduced to a bare structure insertion — no
 *       pool extraction, no mining-pool reconstruction, no reconsideration of
 *       mining. Test state therefore remains deterministic across block
 *       arrivals.</li>
 * </ul>
 * <p>
 * Not intended for production simulations.
 * {@linkplain HiddenChainAttackBehavior} also uses this class as its
 * {@code altHonestBehavior} so that public-chain blocks received during an
 * attack do not clear the attacker's pool.
 *
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 * @see HonestNodeBehavior
 * @see HiddenChainAttackBehavior
 */
public class HonestNodeBehaviorLimited extends HonestNodeBehavior {

	/**
	 * Constructs a limited honest behavior bound to the given node.
	 *
	 * @param node the Bitcoin node this behavior is attached to
	 */
	public HonestNodeBehaviorLimited(BitcoinNode node) {
		super(node);
	}

	/**
	 * Always returns {@code false}, skipping the "already in blockchain"
	 * containment check that the base class performs. Lets tests feed the same
	 * transaction through the pool repeatedly without it being rejected as a
	 * duplicate against chain state.
	 *
	 * @param t the transaction to check (ignored)
	 * @return always {@code false}
	 */
	@Override
    protected boolean transactionContainedInStructure (Transaction t) {
    	return(false);
    }


	/**
	 * Minimal new-block integration: adds the block to the node's structure
	 * and stops. Unlike the base class, this override does <i>not</i> extract
	 * the block's transactions from the pool, does not reconstruct the mining
	 * pool, and does not reconsider whether the node should be mining. Keeps
	 * pool contents deterministic across block arrivals in tests and keeps the
	 * attacker's pool untouched when used as the {@code altHonestBehavior} of
	 * {@linkplain HiddenChainAttackBehavior}.
	 *
	 * @param b the received block
	 */
	@Override
    protected void handleNewBlockReception(Block b) {
        // Add block to the blockchain
        node.getStructure().addToStructure(b);
    }

}
