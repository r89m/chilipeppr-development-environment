package uk.co.r10s;

import java.io.File;

/**
 * Created by Richard on 25/04/2015.
 */
public class AffectedFile {

    private File file;
    private long timestsamp;

    public AffectedFile(File file, long timestamp){

        this.file = file;
        this.timestsamp = timestamp;
    }

    public File getFile(){

        return this.file;
    }

    public long getTimestsamp(){

        return this.timestsamp;
    }
}
