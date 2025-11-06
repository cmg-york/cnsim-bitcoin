package ca.yorku.cmg.cnsim.bitcoin;

import java.io.IOException;

import ca.yorku.cmg.cnsim.bitcoin.reporter.BitcoinReporter;
import ca.yorku.cmg.cnsim.bitcoin.structure.Block;
import ca.yorku.cmg.cnsim.engine.Simulation;
import ca.yorku.cmg.cnsim.engine.config.Config;
import ca.yorku.cmg.cnsim.engine.config.ConfigInitializer;
import ca.yorku.cmg.cnsim.engine.node.PoWNode;
import ca.yorku.cmg.cnsim.engine.transaction.Transaction;


public class BitcoinMainDriver {

    public static void main(String[] args) {
        BitcoinMainDriver b = new BitcoinMainDriver();
        b.run(args);
    }


    private void run(String[] args) {
    	
        Package pkg = ca.yorku.cmg.cnsim.engine.config.Config.class.getPackage();
        System.out.println("\n  * CNSim Engine Version: " + pkg.getImplementationVersion());
        
        System.out.println("  * Current directory: " + System.getProperty("user.dir"));
    	System.out.println("  * Initializing Configurator");

    	// Initialize Config
        try{
            ConfigInitializer.initialize(args);
        } catch (IOException e){
            e.printStackTrace();
            System.exit(1);
        }

        
        // Initialize Bitcoin reporter
        BitcoinReporter.initialize();
        
        
        // Get the number of simulations to run
        int numSimulations = Config.getPropertyInt("sim.numSimulations");
        int simFrom = Config.getPropertyInt("sim.numSimulations.From");
        int simTo = Config.getPropertyInt("sim.numSimulations.To");

        if ((simFrom == -1) || (simTo == -1)) {
            for (int simID = 1; simID <= numSimulations; simID++) {
                runSingleSimulation(simID);
            }
        } else {
            for (int simID = simFrom; simID <= simTo; simID++) {
                runSingleSimulation(simID);
            }
        }
        
        BitcoinReporter.flushAll();
    }

    private void runSingleSimulation(int simID) {
        
        BitcoinSimulatorFactory sf = new BitcoinSimulatorFactory();

        System.out.println("\n  * Setting Up Simulation #" + simID);
        Simulation s = sf.createSimulation(simID);

        System.out.println("\n  * Running Simulation #" + simID);
        s.run();

        System.out.println(s.getStatistics());

        s.closeNodes();

        
        //
        //
        // Reset Statics
        //
        //
        PoWNode.resetCurrID();
        Transaction.resetCurrID();
        Block.resetCurrID();
        
        
    }


}