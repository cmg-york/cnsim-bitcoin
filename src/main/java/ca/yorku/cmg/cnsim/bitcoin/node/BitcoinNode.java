package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Blockchain;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.node.INode;
import ca.yorku.cmg.cnsim.engine.node.Node;
import ca.yorku.cmg.cnsim.engine.reporter.Reporter;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TransactionGroup;

 
public class BitcoinNode extends Node {
	private NodeBehaviorStrategy behaviorStrategy;
	private TransactionGroup miningPool;
	private Blockchain blockchain;

	private Double operatingDifficulty;
	private long minValueToMine;
	private long minSizeToMine;


	// -----------------------------------------------
	// CONSTRUCTORS
	// -----------------------------------------------

	public BitcoinNode(Simulation sim) {
		super(sim);
		blockchain = new Blockchain();
		miningPool = new TransactionGroup();
		minValueToMine = Config.getPropertyLong("bitcoin.minValueToMine");
		minSizeToMine = Config.getPropertyLong("bitcoin.minSizeToMine");
		this.operatingDifficulty = Config.getPropertyDouble("pow.difficulty");
	}
	public BitcoinNode(Simulation sim, NodeBehaviorStrategy behaviorStrategy) {
		super(sim);
		this.behaviorStrategy = behaviorStrategy;
		blockchain = new Blockchain();
		miningPool = new TransactionGroup();
		minValueToMine = Config.getPropertyLong("bitcoin.minValueToMine");
		minSizeToMine = Config.getPropertyLong("bitcoin.minSizeToMine");

		this.operatingDifficulty = Config.getPropertyDouble("pow.difficulty");
	}





	//-----------------------------------------------
	// EVENT HANDLING
	//-----------------------------------------------

	public void setBehaviorStrategy(NodeBehaviorStrategy strategy) {
		this.behaviorStrategy = strategy;
	}
	
	@Override
	public void event_NodeReceivesClientTransaction(Transaction t, long time) {
		behaviorStrategy.event_NodeReceivesClientTransaction(t, time);
	}


	@Override
	public void event_NodeReceivesPropagatedContainer(ITxContainer t) {
		behaviorStrategy.event_NodeReceivesPropagatedContainer(t);
	}


	public void event_NodeReceivesPropagatedTransaction(Transaction t, long time) {
		behaviorStrategy.event_NodeReceivesPropagatedTransaction(t, time);
	}

	@Override
	public void event_NodeCompletesValidation(ITxContainer t, long time) {
		behaviorStrategy.event_NodeCompletesValidation(t, time);
	}
	
	
	
 	// Called by the behavior strategy when validation is complete
	public void completeValidation(TransactionGroup miningPool, long time) {
		super.event_NodeCompletesValidation(miningPool, time);
	}
	
	
	//-----------------------------------------------
	// REPORTING BEHAVIORS
	//-----------------------------------------------
	
	
	@Override
	public void beliefReport(long[] sample, long time) {
		if (Reporter.reportsBeliefs() || Reporter.reportsBeliefsShort()) 
		for (int i = 0; i < sample.length; i++) {
			Reporter.addBeliefEntry(this.sim.getSimID(), this.getID(), sample[i], blockchain.transactionBelieved(sample[i]), time);	
		}
	}
	
	@Override
	public void timeAdvancementReport() {
		// TODO Auto-generated method stub
	}

	@Override
	public void periodicReport() {
		// TODO Auto-generated method stub
	}

	

	@Override
	public void nodeStatusReport() {
		// TODO Auto-generated method stub
		
	}

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

	public long getMinValueToMine() {
		return minValueToMine;
	}


	public void setMinValueToMine(long minValueToMine) {
		this.minValueToMine = minValueToMine;
	}


	public long getMinSizeToMine() {
		return minSizeToMine;
	}

	public void setMinSizeToMine(long minSizeToMine) {
		this.minSizeToMine = minSizeToMine;
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
		return super.prospectiveMiningCycles;
	}

	
	@Override
	public void close(INode n) {
		BitcoinReporter.reportBlockChainState(
				this.blockchain.printStructureReport(this.getID()), 
				this.blockchain.printOrphansReport(this.getID()));
	}
	

}
