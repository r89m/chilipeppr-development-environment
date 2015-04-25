package uk.co.r10s;

import java.io.File;
import java.nio.file.Files;

/**
 * Created by Richard on 25/04/2015.
 */
public class ChilipepprModule {

    public final static String MODULE_DETAILS_FILENAME = "demo.details";

    public static boolean isDirectoryAModule(File directory){

        File moduleDetailsFile = new File(directory, MODULE_DETAILS_FILENAME);
        return Files.exists(moduleDetailsFile.toPath());
    }
}
