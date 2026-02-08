package ca.yorku.cmg.cnsim.bitcoin.node.stubs;


import ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNode;
import ca.yorku.cmg.cnsim.bitcoin.structure.Blockchain;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.event.Event_ContainerValidation;
import ca.yorku.cmg.cnsim.engine.transaction.ITxContainer;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TransactionGroup;

public class BitcoinNode4Test extends BitcoinNode {

	public BitcoinNode4Test(Simulation sim) {
        this.sim = sim;
        this.pool = new TransactionGroup();
        this.ID = 1;
		
		this.blockchain = new Blockchain();
		this.miningPool = new TransactionGroup();
		this.minValueToMine = 0;
		this.minSizeToMine = 0;
		this.operatingDifficulty = 0d;
	}
	
	@Override
	public long scheduleValidationEvent(ITxContainer b, long time) {
		Event_ContainerValidation e = new Event_ContainerValidation(b, this, time);
		this.nextValidationEvent = e;
		return(time);
	}
	
	@Override
	public void broadcastTransaction(Transaction t, long time) {
		//Just sit back, relax, have a sip of tea... 
	}
	
	@Override
	public void broadcastContainer(ITxContainer txc, long time) {
		//Or maybe take a nap!
	}
}
