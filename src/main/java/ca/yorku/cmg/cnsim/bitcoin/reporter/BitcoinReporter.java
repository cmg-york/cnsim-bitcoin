package ca.yorku.cmg.cnsim.bitcoin.reporter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.reporter.Reporter;


/**
 * Specialized reporter for Bitcoin simulations extending the base {@linkplain Reporter}.
 * <p>
 * In addition to the general simulation reports produced by {@link Reporter}, 
 * {@code BitcoinReporter} provides detailed reporting on:
 * <ul>
 *   <li>The state of the blockchain (including sequences of block IDs and orphaned blocks).</li>
 *   <li>Individual block events such as validation, mining, and propagation.</li>
 * </ul>
 * <p>
 * These reports are intended to be used from within Bitcoin-specific simulation
 * entities, e.g., {@linkplain ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNode} or
 * {@linkplain ca.yorku.cmg.cnsim.bitcoin.structure.Block}.
 * </p>
 * 
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 * 
 * @see Reporter
 * @see ca.yorku.cmg.cnsim.bitcoin.node.BitcoinNode
 * @see ca.yorku.cmg.cnsim.bitcoin.structure.Block
 */
public class BitcoinReporter extends Reporter {
	
    /** Log of individual block events for the simulation */
	protected static ArrayList<String> blockLog = new ArrayList<String>();
	
    /** Log of blockchain structure and orphaned blocks */
	protected static ArrayList<String> structureLog = new ArrayList<String>();
	
	 /** Writer used for flushing block logs to file */
	protected static FileWriter blockWriter;

    /** Flag indicating whether block events should be reported */
	protected static boolean reportBlockEvents;
	
    /** Flag indicating whether blockchain structure events should be reported */
	protected static boolean reportStructureEvents;
	
	
    /** Static initializer to add CSV headers for block and structure logs */
	static {
		blockLog.add("SimID, SimTime,SysTime,NodeID,"
				+ "BlockID,ParentID,Height,BlockContent,"
				+ "EvtType,Difficulty,Cycles");
		structureLog.add("SimID, SimTime, SysTime, NodeID, BlockID, ParentBlockID, Height, Content, Place");
	}
	
	 /**
     * Initializes reporting settings from simulation configuration properties.
     * <p>
     * Sets {@code reportBlockEvents} and {@code reportStructureEvents} based
     * on the configuration keys {@code reporter.reportBlockEvents} and
     * {@code reporter.reportStructureEvents}, respectively.
     */
	public static void initialize() {
		BitcoinReporter.reportBlockEvents(Config.getPropertyBoolean("reporter.reportBlockEvents"));
		BitcoinReporter.reportStructureEvents(Config.getPropertyBoolean("reporter.reportStructureEvents"));
	}
	
	
    /**
     * Adds blockchain and orphan block state to the structure log.
     *
     * @param blockchain an array of strings representing the current blockchain state
     * @param orphans an array of strings representing the orphaned blocks
     */
	public static void reportBlockChainState(String[] blockchain, String[] orphans) {
		if (BitcoinReporter.reportStructureEvents) {
			for (String s :blockchain) {
				//s = SimTime + "," + SysTime + "," + blockID + "," + s + ",blockchain";
				structureLog.add(s);
			}
			for (String s :orphans) {
				//s = SimTime + "," + SysTime + "," + blockID + "," + s + ",orphans";
				structureLog.add(s);
			}
		}
	}
	

    /**
     * Adds a detailed event line for a single block to the block log.
     * <p>
     * This includes simulation IDs, node and block IDs, height, involved transactions,
     * event type, validation difficulty, and mining cycles used.
     * </p>
     *
     * @param simID      the simulation ID
     * @param simTime    the simulation time of the event
     * @param sysTime    the system time of the event 
     * @param nodeID     the ID of the node relating to the event
     * @param blockID    the ID of the block
     * @param parentID   the parent block ID (or -1 if none)
     * @param height     the height of the block in the chain
     * @param txInvolved a string listing transactions included in the block
     * @param blockEvt   a description of the block event (e.g., "Node Complete Validation")
     * @param difficulty the mining difficulty under which the block was validated
     * @param cycles     the number of cycles dedicated to validate the block
     */
	public static void reportBlockEvent(
			int simID,
			long simTime, long sysTime, int nodeID,
			int blockID, int parentID, int height, String txInvolved,
			String blockEvt,
			double difficulty, //Difficulty: the difficulty under which the block was validated.
			double cycles) { //Cycles: the number of cycles dedicated to validate the block.
		if (BitcoinReporter.reportBlockEvents)
		blockLog.add(simID + "," +
				simTime + "," + 
				sysTime + "," +
				nodeID + "," +
				blockID + "," +
				parentID + "," +
				height + "," +
				txInvolved + "," +
				blockEvt + "," + //AppendedToChain, AddedAsOrphan, RemovedFromOrphan
				difficulty + "," +
				cycles
					);
	}

    /**
     * Flushes all custom Bitcoin reports (block events and structure) to files.
     */
	public static void flushCustomReports() {
        BitcoinReporter.flushBlockReport();
        BitcoinReporter.flushStructReport();
	}
	
	
	/**
	 * Save Block report to file. File name is "BlockLog - [Simulation Date Time].csv"
	 * 
	 */
	public static void flushBlockReport() {
		if (BitcoinReporter.reportBlockEvents) {
			FileWriter writer;
			try {
				writer = new FileWriter(Reporter.path + "BlockLog - " + Reporter.runId + ".csv");
				for(String str: blockLog) {
					  writer.write(str + System.lineSeparator());
					}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}
	}


	/**
	 * Save Blockchain report to file. File name is "StructureLog - [Simulation Date Time].csv"
	 * 
	 */
	public static void flushStructReport() {
		if (BitcoinReporter.reportStructureEvents) {
			FileWriter writer;
			try {
				writer = new FileWriter(Reporter.path + "StructureLog - " + Reporter.runId + ".csv");
				for(String str: structureLog) {
					  writer.write(str + System.lineSeparator());
					}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	
	// -----------------------------------------------
	// ENABLING / DISABLING REPORT TYPES
	// -----------------------------------------------
	
	
    /**
     * Sets whether block-level events should be logged.
     *
     * @param reportBlockEvents {@code true} to enable block event reporting, {@code false} to disable
     */
	public static void reportBlockEvents(boolean reportBlockEvents) {
		BitcoinReporter.reportBlockEvents = reportBlockEvents;
	}

	 /**
     * Sets whether blockchain structure events should be logged.
     *
     * @param reportStructureEvents {@code true} to enable structure event reporting, {@code false} to disable
     */
	public static void reportStructureEvents(boolean reportStructureEvents) {
		BitcoinReporter.reportStructureEvents = reportStructureEvents;
	}

    /**
     * Returns whether block-level events are being logged.
     *
     * @return {@code true} if block event reporting is enabled, {@code false} otherwise
     */
    public static boolean reportBlockEvents() {
        return BitcoinReporter.reportBlockEvents;
    }

    /**
     * Returns whether blockchain structure events are being logged.
     *
     * @return {@code true} if structure event reporting is enabled, {@code false} otherwise
     */
    public static boolean reportStructureEvents() {
        return BitcoinReporter.reportStructureEvents;
    }
	
	
}
