package ca.yorku.cmg.cnsim.bitcoin.structure;

import ca.yorku.cmg.cnsim.engine.node.Node;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;
import ca.yorku.cmg.cnsim.engine.transaction.TransactionGroup;

import java.util.List;
import java.util.Objects;

/**
 * Represents a block in a blockchain. A {@linkplain Block} extends {@linkplain TransactionGroup} and contains
 * information about the block's position in the chain, validation statistics, and node ownership.
 * <p>
 * This class is designed for use in blockchain simulations and supports cloning and equality checks.
 * 
 * @author Sotirios Liaskos for the Conceptual Modeling Group @ York University
 * @see TransactionGroup
 * @see Node
 * @see Transaction
 */
public class Block extends TransactionGroup implements Cloneable {

    // ---------------------------------------------------------
	// Unique Block ID & ID Management
    // ---------------------------------------------------------

    private static int currID = 1;

    /**
     * Retrieves the next unique ID for a new {@linkplain Block}.
     *
     * @return the next block ID
     */
    public static int getNextID() {
        return currID++;
    }

    /**
     * Resets the next available block ID to 1.
     * Useful for when starting a new simulation run.
     */
    public static void resetCurrID() {
        currID = 1;
    }
    
    public static int getCurrID() {
        return currID;
    }

    public static void setCurrID(int currID) {
        Block.currID = currID;
    }
    

    
    //---------------------------------------------------------
    // FIELDS
    //---------------------------------------------------------

    /** Parent block in the blockchain (if any). */
    private TransactionGroup parent = null;

    /** Height of this block in the blockchain (if in one). */
    private int height = 0;


    /** Simulation and system validation timestamps. */
    private long simTime_validation = -1;
    private long sysTime_validation = -1;

    /** Node IDs for validation and current possession. */
    private int validationNodeID = -1;
    private int currentNodeID = -1;

    /** Difficulty under which validation took place */
    private double validationDifficulty = -1;

    /** Cycles dedicated for the validation of the block */
    private double validationCycles = -1;

    /** Last event that happened to the block */
    private String lastBlockEvent = "-1";
   
    
    
    // ---------------------------------------------------------
	// CONSTRUCTORS
    // ---------------------------------------------------------

    /**
     * Constructs a new {@linkplain Block} object with the next available ID and an empty {@link Context}.
     */
    public Block() {
        groupID = getNextID();
    }

    /**
     * Constructs a new {@linkplain Block} with a unique ID and an initial list of {@linkplain Transaction}s.
     *
     * @param initial initial list of transactions
     */
    public Block(List<Transaction> initial) {
        super(initial);
        groupID = getNextID();
    }

    
    
    // -------------------------------------------
    // MAIN METHODS
    // -------------------------------------------

    /**
     * Updates this {@linkplain Block} with validation information.
     * Typically invoked in response to a block validation event.
     *
     * @param newTransList the {@linkplain TransactionGroup} representing validated transactions
     * @param simTime      simulation time of the validation
     * @param sysTime      system time of the validation
     * @param nodeID       ID of the {@linkplain Node} performing validation
     * @param eventType    textual description of the event (for logging)
     * @param difficulty   difficulty under which the block was validated
     *                     TODO: check if this is correct.
     * @param cycles       computational cycles spent for validation
     */
    public void validateBlock(
            TransactionGroup newTransList,
            long simTime,
            long sysTime,
            int nodeID,
            String eventType,
            double difficulty,
            double cycles
    ) {
    	/** Update statistics about the block based on the contained transactions */
        super.updateTransactionGroup(newTransList.getTransactions());

        /** Record block information */
        simTime_validation = simTime;
        sysTime_validation = sysTime;
        validationNodeID = nodeID;
        currentNodeID = nodeID;
        validationDifficulty = difficulty;
        validationCycles = cycles;
    }



    // ------------------------------------------
    // OVERRIDDEN METHODS: IDENTITY AND CLONING
    // ------------------------------------------

    
    /**
     * Returns a shallow copy of this block.
     *
     * @return a clone of this block
     * @throws CloneNotSupportedException if the superclass does not support cloning
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Block block = (Block) other;
        return height == block.height
        		&& getID() == block.getID() /* NEW */
                && simTime_validation == block.simTime_validation
                && sysTime_validation == block.sysTime_validation
                && validationNodeID == block.validationNodeID
                && currentNodeID == block.currentNodeID
                && Double.compare(validationDifficulty, block.validationDifficulty) == 0
                && Double.compare(validationCycles, block.validationCycles) == 0
                && Objects.equals(parent, block.parent)
                && Objects.equals(lastBlockEvent, block.lastBlockEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
        		//context,
        		getID(), /* NEW */
        		parent, height, simTime_validation, sysTime_validation, validationNodeID, currentNodeID, validationDifficulty, validationCycles, lastBlockEvent);
    }





    
    
    // ---------------------------------------------------------
    // GETTERS, SETTERS AND OTHER UTILITY METHODS
    // ---------------------------------------------------------
    
    
    /**
     * Checks if the {@linkplain Block} has a parent.
     *
     * @return {@code true} if the block has a parent, {@code false} otherwise.
     */
    public boolean hasParent() {
        return parent != null;
    }
    
    public TransactionGroup getParent() {
        return parent;
    }

    public void setParent(TransactionGroup parent) {
        this.parent = parent;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getSimTime_validation() {
        return simTime_validation;
    }

    public void setSimTime_validation(long simTime_validation) {
        this.simTime_validation = simTime_validation;
    }

    public long getSysTime_validation() {
        return sysTime_validation;
    }

    public void setSysTime_validation(long sysTime_validation) {
        this.sysTime_validation = sysTime_validation;
    }

    public int getValidationNodeID() {
        return validationNodeID;
    }

    public void setValidationNodeID(int validationNodeID) {
        this.validationNodeID = validationNodeID;
    }

    public int getCurrentNodeID() {
        return currentNodeID;
    }

    public void setCurrentNodeID(int currentNodeID) {
        this.currentNodeID = currentNodeID;
    }

    public double getValidationDifficulty() {
        return validationDifficulty;
    }

    public void setValidationDifficulty(double validationDifficulty) {
        this.validationDifficulty = validationDifficulty;
    }

    public double getValidationCycles() {
        return validationCycles;
    }

    public void setValidationCycles(double validationCycles) {
        this.validationCycles = validationCycles;
    }

    public String getLastBlockEvent() {
        return lastBlockEvent;
    }

    public void setLastBlockEvent(String lastBlockEvent) {
        this.lastBlockEvent = lastBlockEvent;
    }

}
