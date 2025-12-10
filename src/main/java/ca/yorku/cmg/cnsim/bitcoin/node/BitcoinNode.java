package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Blockchain;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.node.INode;
import ca.yorku.cmg.cnsim.engine.node.PoWNode;
import ca.yorku.cmg.cnsim.engine.reporter.Reporter;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TransactionGroup;


/**
 * Represents a node in the simulated Bitcoin network. Each {@code BitcoinNode}
 * maintains its own {@link Blockchain} and transaction pool, and participates
 * in the mining and transaction propagation processes through a configurable
 * {@link NodeBehaviorStrategy}.
 * <p>
 * This class extends {@linkplain PoWNode} and provides Bitcoin-specific behavior
 * such as proof-of-work mining thresholds, transaction validation, and
 * blockchain reporting.
 * </p>
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Maintain local blockchain and mining pool state</li>
 *   <li>Delegate event handling to its configured {@link NodeBehaviorStrategy}</li>
 *   <li>Interface with {@link Reporter} and {@link BitcoinReporter} for simulation reporting</li>
 * </ul>
 *
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 * @see PoWNode
 * @see NodeBehaviorStrategy
 * @see Blockchain
 */
public class BitcoinNode extends PoWNode {
	private NodeBehaviorStrategy behaviorStrategy;
	private TransactionGroup miningPool;
	private Blockchain blockchain;

	private Double operatingDifficulty;
	private long minValueToMine;
	private long minSizeToMine;


	// -----------------------------------------------
	// CONSTRUCTORS
	// -----------------------------------------------

	/**
	 * Constructs a new {@code BitcoinNode} without setting behavior strategy.
	 *
	 * @param sim the {@link Simulation} instance to which this node belongs
	 */
	public BitcoinNode(Simulation sim) {
		super(sim);
		blockchain = new Blockchain();
		miningPool = new TransactionGroup();
		minValueToMine = Config.getPropertyLong("bitcoin.minValueToMine");
		minSizeToMine = Config.getPropertyLong("bitcoin.minSizeToMine");
		this.operatingDifficulty = Config.getPropertyDouble("pow.difficulty");
	}

	/**
	 * Constructs a new {@code BitcoinNode} with a specified behavior strategy.
	 *
	 * @param sim the {@link Simulation} instance to which this node belongs
	 * @param behaviorStrategy the {@link NodeBehaviorStrategy} defining how this node behaves
	 */
	public BitcoinNode(Simulation sim, NodeBehaviorStrategy behaviorStrategy) {
		this(sim);
		this.behaviorStrategy = behaviorStrategy;
	}





	//-----------------------------------------------
	// EVENT HANDLING
	//-----------------------------------------------

	/**
	 * Sets the node's {@link NodeBehaviorStrategy}, e.g., Honest or Malicious.
	 *
	 * @param strategy the behavior strategy to assign
	 */
	public void setBehaviorStrategy(NodeBehaviorStrategy strategy) {
		this.behaviorStrategy = strategy;
	}
	
	/**
	 * Returns the node's current {@link NodeBehaviorStrategy}.
	 *
	 * @return the behavior strategy, or null if not set
	 */
	public NodeBehaviorStrategy getBehaviorStrategy() {
		return this.behaviorStrategy;
	}

	/** {@inheritDoc} */
	@Override
	public void event_NodeReceivesClientTransaction(Transaction t, long time) {
		behaviorStrategy.event_NodeReceivesClientTransaction(t, time);
	}

	/** {@inheritDoc} */
	@Override
	public void event_NodeReceivesPropagatedContainer(ITxContainer t) {
		behaviorStrategy.event_NodeReceivesPropagatedContainer(t);
	}

	/** {@inheritDoc} */
	@Override
	public void event_NodeReceivesPropagatedTransaction(Transaction t, long time) {
		behaviorStrategy.event_NodeReceivesPropagatedTransaction(t, time);
	}

	/** {@inheritDoc} */
	@Override
	public void event_NodeCompletesValidation(ITxContainer t, long time) {
		behaviorStrategy.event_NodeCompletesValidation(t, time);
	}



	/**
	 * Called by the behavior strategy when validation is complete.
	 *
	 * @param miningPool the {@link TransactionGroup} representing validated transactions
	 * @param time the simulation time at which validation completed
	 */
	public void completeValidation(TransactionGroup miningPool, long time) {
		super.event_NodeCompletesValidation(miningPool, time);
	}


	//-----------------------------------------------
	// REPORTING BEHAVIORS
	//-----------------------------------------------

	/** {@inheritDoc} */
	@Override
	public void beliefReport(long[] sample, long time) {

		for (int i = 0; i < sample.length; i++) {
			Reporter.addBeliefEntry(
					this.sim.getSimID(), 
					this.getID(), 
					sample[i],
					blockchain.transactionBelief(sample[i]), 
					time);

		}
	}

	/** {@inheritDoc} */
	@Override
	public void timeAdvancementReport() {
		// TODO Auto-generated method stub
	}

	/** {@inheritDoc} */
	@Override
	public void periodicReport() {
		// TODO Auto-generated method stub
	}

	/** {@inheritDoc} */
	@Override
	public void nodeStatusReport() {
		// TODO Auto-generated method stub

	}

	/** {@inheritDoc} */
	@Override
	public void structureReport() {
		// TODO Auto-generated method stub

	}



	// -----------------------------------------------
	// GETTERS, SETTERS, OTHER
	// -----------------------------------------------


	public TransactionGroup getMiningPool() {
		return miningPool;
	}


	public void setMiningPool(TransactionGroup miningPool) {
		this.miningPool = miningPool;
	}


	public double getOperatingDifficulty () {
		return (this.operatingDifficulty);
	}


	/**
	 * Sets the minimum total transaction value required to start mining.
	 *
	 * @param minValueToMine new threshold value
	 */
	public void setMinValueToMine(long minValueToMine) {
		this.minValueToMine = minValueToMine;
	}


	public long getMinValueToMine() {
		return minValueToMine;
	}


	/**
	 * Sets the minimum total transaction size (in TODO units) required for this node
	 * to consider mining a new block.
	 * <p>
	 * If the sum of the transactions in the mining pool is below this
	 * threshold, the node will not attempt mining.
	 * </p>
	 */
	public void setMinSizeToMine(long minSizeToMine) {
		this.minSizeToMine = minSizeToMine;
	}

	public long getMinSizeToMine() {
		return minSizeToMine;
	}


	public void setStructure(Blockchain blockchain) {
		this.blockchain = blockchain;
	}

	@Override
	public Blockchain getStructure() {
		return blockchain;
	}

	//TODO: move this to engine.node.Node
	public double getProspectiveCycles() {
		return super.getProspectiveMiningCycles();
	}

	@Override
	public void close(INode n) {
		BitcoinReporter.reportBlockChainState(
				this.blockchain.printStructureReport(this.getID()), 
				this.blockchain.printOrphansReport(this.getID()));
	}
}
