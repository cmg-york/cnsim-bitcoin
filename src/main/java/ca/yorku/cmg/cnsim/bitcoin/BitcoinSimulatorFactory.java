package ca.yorku.cmg.cnsim.bitcoin;

import ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNodeFactory;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.SimulatorFactory;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.node.AbstractNodeFactory;
import ca.yorku.cmg.cnsim.engine.node.PoWNodeSet;


/**
 * A concrete {@linkplain SimulatorFactory} for Bitcoin network simulations.
 * <p>
 * This factory defines how the node set is created for the simulation,
 * specifically differentiating between honest and malicious Bitcoin nodes.
 * It uses {@linkplain BitcoinNodeFactory} to generate nodes with specific
 * roles and properties based on configuration parameters.
 * </p>
 * <p>
 * The number of honest and malicious nodes is determined by configuration
 * properties:
 * <ul>
 *     <li>{@code net.numOfHonestNodes}</li>
 *     <li>{@code net.numOfMaliciousNodes}</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * <pre>
 * {@linkplain BitcoinSimulatorFactory} factory = new {@linkplain BitcoinSimulatorFactory}();
 * {@linkplain Simulation} sim = factory.createSimulation(simID);
 * </pre>
 *
 * <p>
 * This class overrides {@linkplain SimulatorFactory#createNodeSet(Simulation)}
 * to provide a concrete Bitcoin-specific implementation, while all other
 * aspects of simulation setup (network, transaction workload, reporting,
 * termination) are handled by the default implementations of base {@linkplain SimulatorFactory}.
 * </p>
 * 
 * @author
 *   Sotirios Liaskos for the Conceptual Modeling Group @ York University
 */
public class BitcoinSimulatorFactory extends SimulatorFactory {
	
    /**
     * Creates a {@linkplain PoWNodeSet} for a Bitcoin simulation.
     * <p>
     * This method instantiates honest nodes first, using the {@linkplain BitcoinNodeFactory} 
     * with type "Honest", then switches to a factory for malicious nodes.
     * The numbers of each node type are retrieved from the configuration.
     * </p>
     *
     * @param s the {@linkplain Simulation} for which the node set is being created
     * @return a {@linkplain PoWNodeSet} containing both honest and malicious nodes
     */
    @Override
	public PoWNodeSet createNodeSet(Simulation s) {
		AbstractNodeFactory nf = new BitcoinNodeFactory("Honest", s);
		PoWNodeSet ns = new PoWNodeSet(nf);

		ns.addNodes(Config.getPropertyInt("net.numOfHonestNodes"));

		ns.setNodeFactory(new BitcoinNodeFactory("Malicious", s, ns));
		ns.addNodes(Config.getPropertyInt("net.numOfMaliciousNodes"));

		return ns;
	}

}
