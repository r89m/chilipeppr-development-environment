package uk.co.r10s;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by Richard on 25/04/2015.
 */
public class FolderMonitor implements Runnable {

    public static final int MAX_EVENT_AGE_MS = 5 * 1000;

    public static ArrayList<AffectedFile> affectedFiles = new ArrayList<AffectedFile>();

    private static Logger log = LogManager.getLogger(FolderMonitor.class);

    public static ArrayList<File> getAffectedFilesSince(long timestamp){

        ArrayList<File> filesToReturn = new ArrayList<File>();

        synchronized (affectedFiles){
            Iterator<AffectedFile> it = affectedFiles.iterator();
            while(it.hasNext()){
                AffectedFile af = it.next();
                if(af.getTimestsamp() > timestamp){
                    filesToReturn.add(af.getFile());
                }
            }
        }

        return filesToReturn;
    }

    private File folderToMonitor;

    public FolderMonitor(File folderToMonitor){

        this.folderToMonitor = folderToMonitor;
    }

    public void run() {

        log.info("Folder Monitor thread started");

        try {
            // Create watcher service
            WatchService watcher = FileSystems.getDefault().newWatchService();

            // Register root folder
            folderToMonitor.toPath().register(watcher,
                    ENTRY_CREATE,
                    ENTRY_DELETE,
                    ENTRY_MODIFY);

            log.info("Monitoring: " + folderToMonitor.toString());

            // Register all sub-directories
            for (File folder : folderToMonitor.listFiles()) {
                if (folder.isDirectory() && ChilipepprModule.isDirectoryAModule(folder)) {
                    folder.toPath().register(watcher,
                            ENTRY_CREATE,
                            ENTRY_DELETE,
                            ENTRY_MODIFY);
                    log.info("Monitoring: " + folder.toString());
                }
            }

            // Taken from: https://docs.oracle.com/javase/tutorial/essential/io/notification.html

            // wait for key to be signaled
            WatchKey key = null;

            long lastEventClearTimestamp = 0;

            while (true) {
                try {
                    // Wait for any events that come from the FileSystem - we don't mind waiting as we're on a different thread here
                    // but we'll timeout after 100ms so that we can also do some tidying up
                    key = watcher.poll(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    //We don't really mind, so just break out of the loop
                    break;
                }

                if (key != null) {
                    log.info("Got FileSystem event");
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        log.info("Kind: " + kind);

                        // This key is registered only
                        // for ENTRY_CREATE, DELETE and MODIFY events,
                        // but an OVERFLOW event can
                        // occur regardless if events
                        // are lost or discarded.
                        if (kind == OVERFLOW) {
                            continue;
                        }

                        // The filename is the
                        // context of the event.
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path folder = (Path) key.watchable();
                        Path filename = ev.context();

                        // Get the full filename for the affected file
                        Path absoluteFilename = Paths.get(folder.toAbsolutePath().toString(), filename.toString());
                        log.info("Filename: " + absoluteFilename);

                        synchronized (affectedFiles) {
                            affectedFiles.add(new AffectedFile(absoluteFilename.toFile(), System.currentTimeMillis()));
                            log.info("File event recorded");
                        }

                        // Reset the key so that we can get new events
                        boolean valid = key.reset();
                        if (!valid) {
                            // object no longer registered, break out of the loop
                            break;
                        }
                    }
                }

                // Only bother doing this once per 100ms
                if(System.currentTimeMillis() - lastEventClearTimestamp > 100){
                    // Iterate over the files already recorded and remove any that occured more than 5 seconds ago
                    synchronized (affectedFiles) {
                        Iterator<AffectedFile> it = affectedFiles.iterator();
                        long cutoffTimestamp = System.currentTimeMillis() - MAX_EVENT_AGE_MS;
                        while (it.hasNext()) {
                            AffectedFile file = it.next();
                            if (file.getTimestsamp() < cutoffTimestamp) {
                                it.remove();
                                log.info("Stale event found: " + file.getFile().toString());
                            }
                        }
                    }
                    lastEventClearTimestamp = System.currentTimeMillis();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
