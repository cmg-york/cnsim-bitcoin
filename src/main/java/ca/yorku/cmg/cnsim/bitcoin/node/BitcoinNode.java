package ca.yorku.cmg.cnsim.bitcoin.node;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.bitcoin.structure.Blockchain;
import ca.yorku.cmg.cnsim.engine.IStructure;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.node.INode;
import ca.yorku.cmg.cnsim.engine.node.Node;
import ca.yorku.cmg.cnsim.engine.reporter.Reporter;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TransactionGroup;
import ca.yorku.cmg.cnsim.engine.transaction.TxValuePerSizeComparator;

 
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

	
	// -----------------------------------------------
	// PoW AND MINING RELATED BEHAVIORS
	// -----------------------------------------------

	protected void considerMining(long time) {
		if (isWorthMining()) {
			//Start mining and schedule a new validation event
			if (!isMining()) {
				//TODO: Turn these assertions into exceptions?
				//It is not mining because it has never OR it has but then abandoned.
				assert((getNextValidationEvent() == null) || ((getNextValidationEvent() != null) ? getNextValidationEvent().ignoreEvt(): true));

				long interval = scheduleValidationEvent(new Block(getMiningPool().getTransactions()), time);				
				startMining(interval);
			} else {
				
				assert((getNextValidationEvent() != null) && !getNextValidationEvent().ignoreEvt());
				
			}
		} else {
			if (!isMining()) {
				assert((getNextValidationEvent() == null) || getNextValidationEvent().ignoreEvt());
				//All good otherwise!
			} else  {
				// Stop mining, invalidate any future validation event.
				assert((getNextValidationEvent() != null) && !getNextValidationEvent().ignoreEvt());
				getNextValidationEvent().ignoreEvt(true);
				
				stopMining();
				
				assert((getNextValidationEvent() == null) || ((getNextValidationEvent() != null) ? getNextValidationEvent().ignoreEvt(): true));
			}
		}
	}
	
	public boolean isWorthMining() {
		return((getMiningPool().getValue() > getMinValueToMine()));
	}

	
	protected void reconstructMiningPool() {
		setMiningPool(getPool().getTopN(Config.getPropertyLong("bitcoin.maxBlockSize"), 
				new TxValuePerSizeComparator()));
	}

	public void completeValidation(TransactionGroup miningPool, long time) {
		super.event_NodeCompletesValidation(miningPool, time);
	}
	
	protected void transactionReceipt(Transaction t, long time) {
		addTransactionToPool(t);
		reconstructMiningPool();
		considerMining(time);
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
				//Simulation.currTime, System.currentTimeMillis(), this.getID(),
				this.blockchain.printStructureReport(this.getID()), 
				this.blockchain.printOrphansReport(this.getID()));
	}
	

}
