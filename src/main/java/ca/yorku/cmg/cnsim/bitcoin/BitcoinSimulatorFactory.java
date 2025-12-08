package ca.yorku.cmg.cnsim.bitcoin;

import ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNodeFactory;
import ca.yorku.cmg.cnsim.engine.Debug;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.SimulatorFactory;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.event.Event_HashPowerChange;
import ca.yorku.cmg.cnsim.engine.node.AbstractNodeFactory;
import ca.yorku.cmg.cnsim.engine.node.IMiner;
import ca.yorku.cmg.cnsim.engine.node.INode;
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
 * This factory also supports dynamic hashpower changes during simulation runtime.
 * Hashpower changes can be configured using:
 * <ul>
 *     <li>{@code node.hashPowerChanges} - Format: {nodeID:newHashPower:time, ...}</li>
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
 * to provide a concrete Bitcoin-specific implementation, and overrides
 * {@linkplain SimulatorFactory#createSimulation(int)} to add hashpower change
 * event scheduling. All other aspects of simulation setup (network, transaction
 * workload, reporting, termination) are handled by the default implementations
 * of base {@linkplain SimulatorFactory}.
 * </p>
 *
 * @author
 *   Sotirios Liaskos for the Conceptual Modeling Group @ York University
 */
public class BitcoinSimulatorFactory extends SimulatorFactory {

	/**
	 * Represents a single hashpower change configuration entry.
	 * Contains the node ID, new hashpower value, and time when the change should occur.
	 */
	private static class HashPowerChange {
		final int nodeID;
		final float newHashPower;
		final long time;

		HashPowerChange(int nodeID, float newHashPower, long time) {
			this.nodeID = nodeID;
			this.newHashPower = newHashPower;
			this.time = time;
		}
	}

	/**
	 * Parses the hashpower change configuration string.
	 * <p>
	 * Expected format: {@code {nodeID:newHashPower:time, nodeID:newHashPower:time, ...}}
	 * </p>
	 * <p>
	 * Example: {@code {0:5.0E10:10000, 1:3.0E10:15000}}
	 * means node 0 changes to 5.0E10 hash/s at time 10000ms,
	 * and node 1 changes to 3.0E10 hash/s at time 15000ms.
	 * </p>
	 *
	 * @param input the configuration string
	 * @return array of {@link HashPowerChange} objects
	 * @throws IllegalArgumentException if the format is invalid
	 */
	private static HashPowerChange[] parseHashPowerChanges(String input) {
		if (input == null || input.isEmpty() || input.equals("{}")) {
			return new HashPowerChange[0];
		}

		// Validate braces
		if (!input.startsWith("{")) {
			throw new IllegalArgumentException("Error in node.hashPowerChanges: missing opening bracket. Got: " + input);
		}
		if (!input.endsWith("}")) {
			throw new IllegalArgumentException("Error in node.hashPowerChanges: missing closing bracket. Got: " + input);
		}

		// Remove braces and split by comma
		String trimmed = input.substring(1, input.length() - 1).trim();
		if (trimmed.isEmpty()) {
			return new HashPowerChange[0];
		}

		String[] entries = trimmed.split(",");
		HashPowerChange[] changes = new HashPowerChange[entries.length];

		for (int i = 0; i < entries.length; i++) {
			String entry = entries[i].trim();
			String[] parts = entry.split(":");

			if (parts.length != 3) {
				throw new IllegalArgumentException(
					"Error in node.hashPowerChanges: each entry must have format 'nodeID:hashPower:time'. Got: " + entry
				);
			}

			try {
				int nodeID = Integer.parseInt(parts[0].trim());
				float newHashPower = Float.parseFloat(parts[1].trim());
				long time = Long.parseLong(parts[2].trim());

				if (newHashPower < 0) {
					throw new IllegalArgumentException(
						"Error in node.hashPowerChanges: hashpower cannot be negative. Got: " + newHashPower
					);
				}

				if (time < 0) {
					throw new IllegalArgumentException(
						"Error in node.hashPowerChanges: time cannot be negative. Got: " + time
					);
				}

				changes[i] = new HashPowerChange(nodeID, newHashPower, time);

			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(
					"Error in node.hashPowerChanges: invalid number format in entry '" + entry + "': " + e.getMessage()
				);
			}
		}

		return changes;
	}

	/**
	 * Schedules hashpower change events based on configuration.
	 * <p>
	 * Reads the {@code node.hashPowerChanges} property and schedules
	 * {@link Event_HashPowerChange} events for each configured change.
	 * </p>
	 *
	 * @param s the {@link Simulation} for which to schedule hashpower changes
	 */
	private void scheduleHashPowerChanges(Simulation s) {
		try {
			String hashPowerChangesConfig = Config.getPropertyString("node.hashPowerChanges");
			if (hashPowerChangesConfig == null || hashPowerChangesConfig.isEmpty()) {
				return; // No hashpower changes configured
			}

			HashPowerChange[] changes = parseHashPowerChanges(hashPowerChangesConfig);
			if (changes.length == 0) {
				return;
			}

			Debug.p("Scheduling " + changes.length + " hashpower change event(s)");

			for (HashPowerChange change : changes) {
				// Get the node from the network's nodeset
				INode node = s.getNetwork().getNodeSet().getNodes().get(change.nodeID);

				if (node == null) {
					Debug.e("Warning: Cannot schedule hashpower change for node " + change.nodeID +
					        " - node not found. Total nodes: " + s.getNetwork().getNodeSet().getNodes().size());
					continue;
				}

				if (!(node instanceof IMiner)) {
					Debug.e("Warning: Cannot schedule hashpower change for node " + change.nodeID +
					        " - node does not implement IMiner interface");
					continue;
				}

				Event_HashPowerChange event = new Event_HashPowerChange(node, change.newHashPower, change.time);
				s.schedule(event);

				Debug.p("  Scheduled hashpower change for node " + change.nodeID +
				        " to " + change.newHashPower + " at time " + change.time);
			}

		} catch (Exception e) {
			Debug.e("Error scheduling hashpower changes: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Creates and fully configures a new {@link Simulation} instance for Bitcoin.
	 * <p>
	 * This method extends the parent's {@link SimulatorFactory#createSimulation(int)}
	 * by adding hashpower change event scheduling after the base simulation setup.
	 * </p>
	 *
	 * @param simID a unique identifier for the simulation
	 * @return a fully initialized and ready-to-run {@link Simulation}
	 */
	@Override
	public Simulation createSimulation(int simID) {
		// Call parent to create the base simulation
		Simulation s = super.createSimulation(simID);

		// Schedule hashpower changes (must be done after nodes are created)
		scheduleHashPowerChanges(s);

		return s;
	}

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
