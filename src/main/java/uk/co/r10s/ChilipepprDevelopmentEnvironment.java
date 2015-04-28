package uk.co.r10s;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Created by Richard on 23/04/2015.
 */
public class ChilipepprDevelopmentEnvironment {

    private static Logger log = LogManager.getLogger(ChilipepprDevelopmentEnvironment.class);
    private static boolean isDebuggingWeb = false;
    // Store the latest version so that we don't need to request it from GitHub everytime
    private static String latestVersion = null;

    public static void main(String[] args){


        getLatestVersion();

        // Create an instance of the development environment
        ChilipepprDevelopmentEnvironment environment = new ChilipepprDevelopmentEnvironment();
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

    public static String getLatestVersion(){

        // If we don't have the latest version, try getting it from the GitHub repository
        if(latestVersion == null) {

            // Set latest version to blank
            latestVersion = "";

            try {
                // GitHub API base URL
                URL githubApi = new URL("https://api.github.com/repos/shaggythesheep/chilipeppr-development-environment/");

                // Get the latest release (first one in the returned array that has prerelease == false)
                URL releasesUrl = new URL(githubApi, "releases");
                String releasesStr = IOUtils.toString(releasesUrl.openStream(), "UTF-8");
                System.out.println(releasesStr);
                JSONArray releases = new JSONArray(releasesStr);

                String latestTagName = null;

                // Iterate over all the releases
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = (JSONObject) releases.get(i);
                    // If this release is not prerelease, get it's tag name and then break out of the loop
                    if (release.getBoolean("prerelease") == false) {
                        latestTagName = release.getString("tag_name");
                        break;
                    }
                }

                // If a valid release and tag name were not found, return blank
                if (latestTagName != null){
                    System.out.println(latestTagName);

                    // Get the list of tags
                    URL tagsUrl = new URL(githubApi, "tags");
                    String tagsStr = IOUtils.toString(tagsUrl.openStream(), "UTF-8");
                    System.out.println(tagsStr);
                    JSONArray tags = new JSONArray(tagsStr);

                    String latestCommit = null;

                    // Iterate over all the releases
                    for (int i = 0; i < tags.length(); i++) {
                        JSONObject tag = (JSONObject) tags.get(i);
                        // If this tags name matches the one we got from the latest release, record the commit hash and quit the loop
                        if (latestTagName.equals(tag.getString("name"))) {
                            latestCommit = tag.getJSONObject("commit").getString("sha");
                            break;
                        }
                    }

                    // If no commit was found, return blank
                    if (latestCommit != null) {

                        // Download the build.gradle file
                        URL latestBuildGradle = new URL("https://raw.githubusercontent.com/shaggythesheep/chilipeppr-development-environment/" + latestCommit + "/build.gradle");
                        String latestBuildGradleStr = IOUtils.toString(latestBuildGradle.openStream(), "UTF-8");

                        // Use a simple string find to find the Implementation-Version (ie Version Number)
                        String versionFindString = "Implementation-Version";
                        int versionTitlePos = latestBuildGradleStr.indexOf(versionFindString);
                        if (versionTitlePos > -1) {
                            // Try looking for a colon and if it was found, determine the version number
                            int versionStartPos = latestBuildGradleStr.indexOf(":", versionTitlePos);
                            if (versionStartPos > -1){
                                // Retrieve the version string
                                latestVersion = latestBuildGradleStr.substring(versionStartPos + 1, latestBuildGradleStr.indexOf("\n", versionStartPos));
                                // Remove any quotes and whitespace
                                latestVersion = StringUtils.strip(StringUtils.strip(latestVersion, "\"'"));
                            }
                        }
                    }
                }
            } catch (IOException e){
                log.error(e);
            }
        }

        return latestVersion;
    }


    public static void setIsDebuggingWeb(boolean debugging){

        isDebuggingWeb = debugging;
    }

    public static boolean isDebuggingWeb(){

        return isDebuggingWeb;
    }

    public static void launchBrowser(int port){

        launchBrowser(port, "");
    }

    public static void launchBrowser(int port, String uri){

        if (Desktop.isDesktopSupported()) {
            log.debug("Launching browser...");
            try {
                Desktop.getDesktop().browse(new URI("http://localhost:" + port + "/" + uri));
            } catch (URISyntaxException | IOException e) {
                e.printStackTrace();
            }
        } else {
            log.error("Desktop not supported - can't launch browser");
        }
    }

    private DevelopmentServer server;

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
            server = new DevelopmentServer(port, monitorFolder, disableAutoRefresh);
            // ...and start it
            server.start();
            log.debug("Chilipeppr Development Environment started on port: {}", server.getPort());
            log.debug("Base folder: {}", monitorFolder.getAbsolutePath());

        } catch (IOException e){
            log.error("Couldn't start Chilipeppr Development Environment");
            if(e instanceof BindException && "Address already in use: JVM_Bind".equals(e.getMessage())){
                log.error("The port {} is already in use. You might want to change port or close whatever else is using it. In the meantime, we'll try loading the browser unless you've told me not to", port);
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
        if (!disableLaunchBrowser){
            String folderQryString = "";
            try{
                folderQryString = "?" + URLEncoder.encode(monitorFolder.toString(), "UTF-8");
            } catch (UnsupportedEncodingException e){
                e.printStackTrace();
            }

            launchBrowser(server.getPort(), folderQryString);
        }
    }
}