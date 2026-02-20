package ca.yorku.cmg.cnsim.bitcoin.node;


import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.exceptions.ConfigurationException;
import ca.yorku.cmg.cnsim.engine.node.AbstractNodeFactory;
import ca.yorku.cmg.cnsim.engine.node.IMiner;
import ca.yorku.cmg.cnsim.engine.node.PoWNodeSet;

/**
 * A factory for various kinds of blockchain nodes.
 *
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University`
 *
 */
public class BitcoinNodeFactory extends AbstractNodeFactory {

	String defaultNodeBehavior;
	PoWNodeSet nodeSet;

	/**
	 * Create a new factory of a specific type (e.g., Honest, Malicious, etc.) based on the sampler embedded in the Simulator object.
	 *
	 * @param defaultNodeType Is one of a list of strings identifying node type. Currently "Honest" is implemented.
	 * @param sim The simulator to which the node is attached, and from which sampling services are drawn.
	 */
	public BitcoinNodeFactory(String defaultNodeBehavior, Simulation sim){
		this.defaultNodeBehavior = defaultNodeBehavior;
		this.sim = sim;
		this.sampler = sim.getSampler();
	}

	public BitcoinNodeFactory(String defaultNodeBehavior, Simulation sim, PoWNodeSet nodes){
		this.defaultNodeBehavior = defaultNodeBehavior;
		this.sim = sim;
		this.sampler = sim.getSampler();
		this.nodeSet = nodes;
	}
	
	
	/**
	 * Create a new node based on the factory.
	 */
	@Override
	public IMiner createNewNode() {
		// First, create a BitcoinNode instance without a behavior strategy
		BitcoinNode node = new BitcoinNode(sim);
        
		// Set node properties from the sampler
		node.setHashPower(sampler.getNodeSampler().getNextNodeHashPower());
		node.setElectricPower(sampler.getNodeSampler().getNextNodeElectricPower());
		node.setElectricityCost(sampler.getNodeSampler().getNextNodeElectricityCost());
		node.setSimulation(sim);
		
		// Determine and set the appropriate behavior strategy and update hashpower
		NodeBehaviorStrategy strategy;
		
		
		switch (defaultNodeBehavior) {
			case "Hidden Chain Attack" :
				if (nodeSet == null)
					throw new IllegalArgumentException("Hidden Chain Attack behavior requires access to honest nodeset. Please insantiate factory with BitcoinNodeFactory(String, Simulation, PoWNodeSet)");
				strategy = new HiddenChainAttackBehavior(node, 
		        		new HonestNodeBehavior(node),
		        		new HonestNodeBehaviorLimited(node));
				//Configure it
				((HiddenChainAttackBehavior) strategy).setTargetTransaction(
						Config.getPropertyLong("hiddenChainAttack.targetTransaction"));
				((HiddenChainAttackBehavior) strategy).setStartAdvantage(
						Config.getPropertyInt("hiddenChainAttack.startAdvantage"));
				((HiddenChainAttackBehavior) strategy).setReleaseAdvantage(
						Config.getPropertyInt("hiddenChainAttack.releaseAdvantage"));
				((HiddenChainAttackBehavior) strategy).setAttackPower(
						getMaliciousPower(
								Config.getPropertyFloat("hiddenChainAttack.maliciousPowerRatio"),
								nodeSet.getTotalHashPower()
								)
						);
				
				if (Config.hasProperty("hiddenChainAttack.attackTimeOut")) {
					((HiddenChainAttackBehavior) strategy).setAttackTimeOut(
							Config.getPropertyLong("hiddenChainAttack.attackTimeOut"));
				}
				
				//IMPORTANT: enable the attack.
				((HiddenChainAttackBehavior) strategy).goToMonitoringState();
				
				break;
	
			case "Honest":
				strategy = new HonestNodeBehavior(node);
				break;
			
			default:
					throw new ConfigurationException("Unknown node behavior " + defaultNodeBehavior);
			}
			
			node.setBehaviorStrategy(strategy);
		return node;
	}
	
	private float getMaliciousPower(float maliciousPowerRatio, float totalHonestPower) {
		return((maliciousPowerRatio/(1-maliciousPowerRatio))*totalHonestPower);
	}
	
}
