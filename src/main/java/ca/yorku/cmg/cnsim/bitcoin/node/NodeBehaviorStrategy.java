package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;


/**
 * Defines the strategy interface for Bitcoin node behavior within the
 * {@linkplain ca.yorku.cmg.cnsim.engine.Simulation} environment.
 * <p>
 * Implementations of this interface encapsulate the decision-making logic
 * for how a {@linkplain BitcoinNode} responds to simulation events such as
 * receiving client transactions, propagated transactions or blocks, and
 * completing validation of mined blocks.
 * </p>
 * <p>
 * Concrete strategies may implement “honest” behavior, selfish mining,
 * or experimental protocols by providing custom logic for each event type.
 * </p>
 *
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 * @see BitcoinNode
 * @see DefaultNodeBehavior
 * @see HonestNodeBehavior
 */
public interface NodeBehaviorStrategy {
	
    /**
     * Invoked when the node receives a transaction directly from a client.
     *
     * @param t    the transaction received from the client
     * @param time the current simulation time
     */
    void event_NodeReceivesClientTransaction(Transaction t, long time);
    
    /**
     * Invoked when the node receives a transaction propagated from another node.
     *
     * @param t    the propagated transaction
     * @param time the current simulation time
     */
    void event_NodeReceivesPropagatedTransaction(Transaction t, long time);
    
    /**
     * Invoked when the node receives a propagated container of transactions,
     * such as a block or batch of transactions.
     *
     * @param t the propagated transaction container
     */
    void event_NodeReceivesPropagatedContainer(ITxContainer t);
    
    /**
     * Invoked when the node completes validation of a transaction container,
     * such as a newly mined or propagated block.
     *
     * @param t    the validated transaction container
     * @param time the current simulation time
     */
    void event_NodeCompletesValidation(ITxContainer t, long time);
}
