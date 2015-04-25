package uk.co.r10s;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by Richard on 23/04/2015.
 */
public class ChilipepprDevelopmentEnvironment {

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
}
