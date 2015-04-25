package uk.co.r10s;

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

    private DevServer server;

    // Command line arguments
    @Option(name="-port", usage="Set which port the server will listen to")
    public int port = 5938;

    @Option(name="-folder", usage="Set which folder the server should monitor. Can be either a module folder, or a parent folder")
    public File monitorFolder = new File("");

    @Option(name="-disable-autorefresh", usage="Prevent the server automatically refreshing the page when content changes")
    public boolean disableAutoRefresh = false;

    @Option(name="-disable-launch-browser", usage="Launch the browser once the server has started")
    public boolean disableLaunchBrowser = false;




    public void start(){

        // Check that monitor folder exists before doing anything
        try {
            if (!Files.isDirectory(monitorFolder.toPath())) {
                System.err.println("The given folder could not be found: " + monitorFolder.toString());
                System.exit(1);
            }
        } catch (InvalidPathException e){
            System.err.println("The given folder path is not valid: " + monitorFolder.toString());
            System.exit(1);
        }

        monitorFolder = monitorFolder.getAbsoluteFile();

        try{
            // Setup the webserver to look at /resources/www as the root directory
            ArrayList rootDirs = new ArrayList();
            /*
            try {
                rootDirs.add(new File(this.getClass().getResource("www").toURI()));
            } catch (URISyntaxException e){
                e.printStackTrace();
            }
            */
            //TODO: Change path for production
            rootDirs.add(new File("D:\\Websites\\Main\\GitHub\\chilipeppr-development-environment\\src\\main\\resources\\www"));

            // Create instance of server
            server = new DevServer(port, rootDirs, monitorFolder, disableAutoRefresh);
            // ...and start it
            server.start();

            System.out.println("Chilipeppr Development Environment started on port: " + server.getPort());
            System.out.println("Base folder: " + monitorFolder.getAbsolutePath());
        } catch (IOException e){
            System.err.println("Couldn't start Chilipeppr Development Environment");
            if(e instanceof BindException && "Address already in use: JVM_Bind".equals(e.getMessage())){
                System.err.println("The port " + port + " is already in use. Please close whatever is using it or try a different port");
                System.exit(1);
            } else {
                e.printStackTrace();
            }
        }


        // The server thread runs as a daemon, so will only stay alive so long as other threads exist
        //      - we'll create a dummy one here to just sit here and keep the server alive
        Thread keepAliveThread = new Thread(new Runnable() {
            @Override
            public void run() {

                while(true){
                    try{
                        Thread.sleep(100);
                    } catch (InterruptedException e){
                        // Do nothing, just let it quite
                        break;
                    }
                }
            }
        });
        keepAliveThread.start();

        // If we want to launch the browser, do so
        if (!disableLaunchBrowser) {
            if (Desktop.isDesktopSupported()) {
                System.out.println("Launching browser...");
                try {
                    Desktop.getDesktop().browse(new URI("http://localhost:" + server.getPort()));
                } catch (URISyntaxException | IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.err.println("Desktop not supported - can't launch browser");
            }
        }
    }
}
