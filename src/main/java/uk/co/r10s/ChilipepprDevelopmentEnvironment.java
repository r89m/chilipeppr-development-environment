package uk.co.r10s;

import org.apache.logging.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

/**
 * Created by Richard on 23/04/2015.
 */
public class ChilipepprDevelopmentEnvironment {

    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(ChilipepprDevelopmentEnvironment.class);
    private static boolean isDebuggingWeb = false;

    public static void main(String[] args){

        // Create an instance of the development environment
        DevEnvironment environment = new DevEnvironment();
        // Register its command line arguments
        CmdLineParser parser = new CmdLineParser(environment);

        try{
            // Parse the command line arguments - if that worked, start the environment.
            parser.parseArgument(args);
            environment.start();
        } catch (CmdLineException e){
            // Report any errors with the given arguments
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }
    }

    public static String getCurrentVersion(){

        return ChilipepprDevelopmentEnvironment.class.getPackage().getImplementationVersion();
    }

    public static void setIsDebuggingWeb(boolean debugging){

        isDebuggingWeb = debugging;
    }

    public static boolean isDebuggingWeb(){

        return isDebuggingWeb;
    }

}
