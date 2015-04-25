package uk.co.r10s;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.kohsuke.args4j.Option;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;

/**
 * Created by Richard on 23/04/2015.
 */
public class DevEnvironment {

    private static Logger log = LogManager.getLogger(DevEnvironment.class);

    private DevServer server;

    // Command line arguments
    @Option(name="--port", usage="Set which port the server will listen on. [Default is 5938")
    public int port = 5938;

    @Option(name="--folder", usage="Set which folder the server should monitor. Can be either a module folder, or a parent folder. [Default is the folder in which this jar is located]")
    public File monitorFolder = new File("");

    @Option(name="--disable-auto-refresh", usage="Prevent the server automatically refreshing the page when content changes.")
    public boolean disableAutoRefresh = false;

    @Option(name="--disable-launch-browser", usage="Launch the browser once the server has started.")
    public boolean disableLaunchBrowser = false;

    @Option(name="--debug-web", usage="Use a local copy of the website files so that debugging is easier", hidden=true)
    public boolean debugWeb = false;

    // Changing the log level at runtime doesn't seem to work at the moment, so hide this option
    //@Option(name="--log-level", usage="Select the level at which logging will be written to the console - useful if you want to see more of what's going on. [Default is DEBUG. Acceptable values are OFF, DEBUG, INFO]")
    public String loggingLevel = "DEBUG";



    public void start(){

        // Set whether or not we're debugging
        ChilipepprDevelopmentEnvironment.setIsDebuggingWeb(debugWeb);

        // Set the logging level
        LoggerContext ctx = (LoggerContext)LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);

        switch(loggingLevel){
            case "OFF":
                loggerConfig.setLevel(Level.OFF);
                break;

            case "INFO":
                loggerConfig.setLevel(Level.INFO);
                break;

            default:
                loggerConfig.setLevel(Level.DEBUG);
                break;
        }

        ctx.updateLoggers();

        log.info("Application started");

        // Check that monitor folder exists before doing anything
        try {
            if (!Files.isDirectory(monitorFolder.toPath())) {
                log.error("The given folder could not be found: {}", monitorFolder.toString());
                System.exit(1);
            }
        } catch (InvalidPathException e){
            log.error("The given folder path is not valid: {}", monitorFolder.toString());
            System.exit(1);
        }

        monitorFolder = monitorFolder.getAbsoluteFile();

        try{
            // Create instance of server
            server = new DevServer(port, monitorFolder, disableAutoRefresh);
            // ...and start it
            server.start();

            log.debug("Chilipeppr Development Environment started on port: {}", server.getPort());
            log.debug("Base folder: {}", monitorFolder.getAbsolutePath());
        } catch (IOException e){
            log.error("Couldn't start Chilipeppr Development Environment");
            if(e instanceof BindException && "Address already in use: JVM_Bind".equals(e.getMessage())){
                log.error("The port {} is already in use. Please close whatever is using it or try a different port", port);
                System.exit(1);
            } else {
                e.printStackTrace();
            }
        }


        // The server thread runs as a daemon, so will only stay alive so long as other threads exist
        //      - we'll use the folder monitor thread to do that if we've enabled auto refresh
        //              otherwise we'll create a dummy thread to sit there and do nothing
        if(!disableAutoRefresh) {
            Thread folderMonitorThread = new Thread(new FolderMonitor(monitorFolder));
            folderMonitorThread.start();
        } else {
            Thread keepAliveThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        // Snooze....
                        Thread.sleep(100000);
                    } catch (InterruptedException e){
                        // We don't really care about this exception
                    }
                }
            });
            keepAliveThread.start();
        }

        // If we want to launch the browser, do so
        if (!disableLaunchBrowser) {
            if (Desktop.isDesktopSupported()) {
                log.debug("Launching browser...");
                try {
                    Desktop.getDesktop().browse(new URI("http://localhost:" + server.getPort()));
                } catch (URISyntaxException | IOException e) {
                    e.printStackTrace();
                }
            } else {
                log.error("Desktop not supported - can't launch browser");
            }
        }
    }
}
