# Honest Node Behavior

## event_NodeReceivesClientTransaction(Transaction t, long time)

- \[check dependencies and conflicts\]
- \[DEFAULT\].transactionReceipt(t, time)
	- \[NODE\].addTransactionToPool(t)
	- \[DEFAULT\].reconstructMiningPool()
	- \[DEFAULT\].considerMining()
		- \[DEFAULT\].isWorthMining();
		- \[NODE\].scheduleValidationEvent(\[mining pool\])
		- \[NODE\].startMining(interval);
- \[NODE\].broadcastTransaction(t, time)

## event_NodeReceivesPropagatedTransaction(Transaction t, long time)

- \[DEFAULT\].transactionReceipt(t, time)
	- \[NODE\].addTransactionToPool(t)
	- \[DEFAULT\].reconstructMiningPool()
	- \[DEFAULT\].considerMining()
		- \[DEFAULT\].isWorthMining();
		- \[NODE\].scheduleValidationEvent(\[mining pool\])
		- \[NODE\].startMining(interval);
		
## event_NodeReceivesPropagatedContainer(ITxContainer t)

- \[Perform various conflict and dependency checks\]
- \[DEFAULT\].handleNewBlockReception(t)
	- \[NODE\].getStructure().addToStructure(b)
	- \[NODE\].getPool().extractGroup(b);
	- \[DEFAULT\].reconstrcuctMiningPool();
	- \[DEFAULT\].considerMining()
		- \[DEFAULT\].isWorthMining();
		- \[NODE\].scheduleValidationEvent(\[mining pool\])
		- \[NODE\].startMining(interval);
		
## event_NodeCompletesValidation(ITxContainer t, long time);

- \[NODE\].completeValidation(\[mining pool\], time)
- \[set parent to null\]
- \[NODE\].getStructure().addToStructure(b)
- \[NODE\].broadCastContainer()
- \[DEFAULT\].processPostValidationActivities(time)
	- \[NODE\].stopMining()
	- \[NODE\].resetNextValidationEvent()
	
	
	
	
	
	
	