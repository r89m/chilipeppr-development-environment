package uk.co.r10s;

import fi.iki.elonen.NanoHTTPD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Created by Richard on 23/04/2015.
 */
public class DevelopmentServer extends NanoHTTPD {

    private static Logger log = LogManager.getLogger(DevelopmentServer.class);

    private final static String URI_AUTO_REFRESH = "/detect-changes";
    private final static String URI_PROXY = "/geturl";
    private final static String URI_FOLDER_INFO = "/info";
    private final static String URI_VERSION = "/version";
    private final static String URI_VERSION_NEW = "/version/new";
    private final static String URI_SHUTDOWN = "/shutdown";

    private final static String URL_CHILIPEPPR_BASE = "http://www.chilipeppr.com/";
    private final static String URL_CHILIPEPPR_PROXY = URL_CHILIPEPPR_BASE + "geturl?url=";

    private enum ResponseStatus {OK, Error};

    private final static String JSON_FIELD_LATEST_VERSION = "latest_version";
    private final static String JSON_FIELD_VERSION = "version";
    private final static String JSON_FIELD_NEW_VERSION_AVAILABLE = "new_version_available";
    private final static String JSON_FIELD_IS_MODULE = "is_module";
    private final static String JSON_FIELD_MODULES = "modules";
    private final static String JSON_FIELD_MODULE_PATH = "path";
    private final static String JSON_FIELD_MODULE_INFO = "info";
    private final static String JSON_FIELD_MODULE_NAME = "name";
    private final static String JSON_FIELD_MODULE_DESCRIPTION = "description";
    private final static String JSON_FIELD_FOLDER_PATH = "folder_path";
    private final static String JSON_FIELD_AUTO_REFRESH_ENABLED = "auto_refresh_enabled";
    private final static String JSON_FIELD_AFFECTED_FILES = "affected_files";

    private int port;
    private File monitorFolder;
    private boolean disableAutoRefresh;

    public DevelopmentServer(int port, File monitorFolder, boolean disableAutoRefresh){

        super(null, port);
        this.port = port;
        this.monitorFolder = monitorFolder;
        this.disableAutoRefresh = disableAutoRefresh;
    }

    public int getPort(){

        return port;
    }

    protected String getStr(IHTTPSession session, String field, String defaultValue){

        String param = session.getParms().get(field);

        if(param == null || "".equals(param)){
            return defaultValue;
        } else {
            return param;
        }
    }

    protected String getStr(IHTTPSession session, String field){

        return getStr(session, field, null);
    }

    protected int getInt(IHTTPSession session, String field, int defaultValue){

        String rawValue = getStr(session, field);

        if(rawValue == null){
            return -1;
        } else {
            try {
                return Integer.parseInt(rawValue);
            } catch (NumberFormatException e){
                return -1;
            }
        }
    }

    protected int getInt(IHTTPSession session, String field){

        return getInt(session, field, -1);
    }

    @Override
    public Response serve(IHTTPSession session){

        log.info("Request received: {}", session.getUri());

        switch(session.getUri().toLowerCase()){

            case URI_AUTO_REFRESH:
                return detectChanges(session);

            case URI_PROXY:
                return proxyUrl(session);

            case URI_FOLDER_INFO:
                return getFolderInfo(session);

            case URI_VERSION:
                return getCurrentVersion(session);

            case URI_VERSION_NEW:
                return checkForNewVersion(session);

            case URI_SHUTDOWN:
                return shutdownEnvironement(session);

            default:
                return handleOtherRequests(session);
        }
    }

    private Response detectChanges(IHTTPSession session){

        JSONResponse response = new JSONResponse();
        response.status(ResponseStatus.OK);

        response.put(JSON_FIELD_AUTO_REFRESH_ENABLED, !disableAutoRefresh);

        if(!disableAutoRefresh){
            // Create an output array to store any affected files
            JSONArray eventFilenames = new JSONArray();

            long requestStartTimestamp = System.currentTimeMillis();
            long lastEventTimestamp = requestStartTimestamp;

            log.info("Start checking for file system events");

            // Enter a loop to keep checking for changed files / folders
            // We stay in the loop until a) any events have been detected and b) it's been more than 50ms since the last event
            while(lastEventTimestamp == requestStartTimestamp  || (System.currentTimeMillis() - lastEventTimestamp < 50)) {
                try{
                    // Get the list of affected files
                    ArrayList<File> affectedFiles = FolderMonitor.getAffectedFilesSince(lastEventTimestamp);

                    // If some events have been generated since we last checked
                    if(affectedFiles.size() > 0){
                        // Record the time at which they occurred
                        lastEventTimestamp = System.currentTimeMillis();
                        // Add those files to our response
                        Iterator it = affectedFiles.iterator();
                        while(it.hasNext()){
                            File affectedFile = (File) it.next();
                            // Make the path relative to the root folder
                            eventFilenames.put(monitorFolder.toPath().toAbsolutePath().relativize(affectedFile.toPath().toAbsolutePath()));
                        }
                    }

                    // Sleep for 100ms
                    Thread.sleep(100);
                } catch (InterruptedException e){
                    // We don't really mind, just break out of the loop
                    break;
                }
            }

            response.put(JSON_FIELD_AFFECTED_FILES, eventFilenames);
        }

        return response.toResponse();
    }

    private Response proxyUrl(IHTTPSession session){

        String proxyUrl = getStr(session, "url");

        if(proxyUrl != null){
            try {
                URL url = new URL(URL_CHILIPEPPR_PROXY + URLEncoder.encode(proxyUrl, "UTF-8"));
                return redirectedResponse(url);
            } catch (MalformedURLException | UnsupportedEncodingException e){
                return new ErrorResponse(e);
            }


        } else {
            return new Response("Error: No URL provided");
        }
    }

    private Response getFolderInfo(IHTTPSession session){

        // Create the default response
        JSONResponse response = new JSONResponse();
        response.status(ResponseStatus.OK);
        response.put(JSON_FIELD_FOLDER_PATH, monitorFolder.getAbsolutePath());

        // If the monitored folder contains a file name 'demo.details', it is a module
        if(ChilipepprModule.isDirectoryAModule(monitorFolder)){
            response.put(JSON_FIELD_IS_MODULE, true);
        } else {
            response.put(JSON_FIELD_IS_MODULE, false);
            // If this folder potentially contains several folders, iterate over it's subfolders and
            //      find any that contain a 'demo.details' file
            JSONArray modules = new JSONArray();

            for(File module : monitorFolder.listFiles()){
                if(module.isDirectory() && ChilipepprModule.isDirectoryAModule(module)){
                    // Create an object to store information about the module
                    JSONObject moduleInfo = new JSONObject();

                    // Store the path to the module
                    moduleInfo.put(JSON_FIELD_MODULE_PATH, module.getName());

                    try {
                        // Parse the details yaml file
                        File moduleDetailsFile = new File(module, ChilipepprModule.MODULE_DETAILS_FILENAME);
                        FileInputStream is = new FileInputStream(moduleDetailsFile);
                        String yamlString = IOUtils.toString(is, "UTF-8");

                        int yamlStartPos = yamlString.indexOf("---");
                        int yamlEndPos = yamlString.indexOf("...");

                        JSONObject moduleDetailsJson;

                        // If demo.details is a valid JSFiddle details file, process. Otherwise reply with some default values
                        if(yamlStartPos > -1 && yamlEndPos > -1) {
                            yamlString = yamlString.substring(yamlStartPos);
                            yamlString = yamlString.substring(0, yamlEndPos);

                            Yaml moduleDetailsYaml = new Yaml();
                            Map<String, Object> moduleDetails = (Map<String, Object>) moduleDetailsYaml.load(yamlString);
                            moduleDetailsJson = new JSONObject(moduleDetails);
                        } else {
                            moduleDetailsJson = new JSONObject();
                            moduleDetailsJson.put(JSON_FIELD_MODULE_NAME, "Uninitialised")
                                    .put(JSON_FIELD_MODULE_DESCRIPTION, "This module doesn't currently have a valid demo.details YAML file - please check this out");
                        }
                        moduleInfo.put(JSON_FIELD_MODULE_INFO, moduleDetailsJson);
                    } catch (IOException e){
                        return new ErrorResponse(e);
                    }
                    modules.put(moduleInfo);
                }
            }
            response.put(JSON_FIELD_MODULES, modules);
        }

        return response.toResponse();
    }

    private Response getCurrentVersion(IHTTPSession session){

        JSONResponse response = new JSONResponse();
        response.status(ResponseStatus.OK);


        response.put(JSON_FIELD_VERSION, ChilipepprDevelopmentEnvironment.getCurrentVersion());

        return response.toResponse();
    }

    private Response checkForNewVersion(IHTTPSession session){

        JSONResponse response = new JSONResponse();
        response.status(ResponseStatus.OK);

        String latestVersion = ChilipepprDevelopmentEnvironment.getLatestVersion();
        String currentVersion = ChilipepprDevelopmentEnvironment.getCurrentVersion();

        if(currentVersion != null){
            response.put(JSON_FIELD_NEW_VERSION_AVAILABLE, !currentVersion.equals(latestVersion));
        } else {
            response.put(JSON_FIELD_NEW_VERSION_AVAILABLE, false);
        }

        response.put(JSON_FIELD_LATEST_VERSION, latestVersion);

        if(currentVersion != null){
            response.put(JSON_FIELD_NEW_VERSION_AVAILABLE, !currentVersion.equals(latestVersion));
        } else {
            response.put(JSON_FIELD_NEW_VERSION_AVAILABLE, false);
        }

        return response.toResponse();
    }

    private Response shutdownEnvironement(IHTTPSession session){

        // Shutdown using a thread so that we can give a reponse before the server quits
        Thread shutdownThread = new Thread(new Runnable() {
            @Override
            public void run() {

                try{
                    Thread.sleep(100);
                    System.exit(0);
                } catch (InterruptedException e){
                    // We don't mind an interruption here
                }
            }
        });
        shutdownThread.start();
        return new Response("Development Environment Shutdown successfully. You can restart it by running the Chilipeppr Development Environment jar");
    }

    private Response handleOtherRequests(IHTTPSession session){

        if(monitorFolder == null){
            return new Response("No folder is currently being monitored");
        }

        Path modulePath = Paths.get(monitorFolder.getAbsolutePath(), session.getUri());

        // Serve files found in the monitored folder ourselves
        if(Files.isRegularFile(modulePath)){
            try {
                return new Response(Response.Status.OK, getMimeType(session.getUri()), new FileInputStream(modulePath.toString()));
            } catch (FileNotFoundException e){
                return new ErrorResponse(e);
            }
        } else {
            // Otherwise, try finding it within the jar
            String uri = session.getUri();
            if ("/".equals(uri)){
                uri = uri + "index.html";
            }

            InputStream serveFile;

            if(ChilipepprDevelopmentEnvironment.isDebuggingWeb()){
                try {
                    Path filePath = Paths.get("D:\\Working Directory\\GitHub\\chilipeppr-development-environment\\src\\main\\resources\\www", uri);
                    if(Files.exists(filePath)) {
                        serveFile = new FileInputStream(filePath.toString());
                    } else {
                        serveFile = null;
                    }
                } catch (FileNotFoundException e){
                    // Something strange went wrong, but oh well, just let the server know we couldn't find the file
                    serveFile = null;
                }
            } else {
                String resourcePath = "/www" + uri;
                serveFile = this.getClass().getResourceAsStream(resourcePath);
            }

            if(serveFile != null){
                String mimeType = getMimeType(uri);
                return new Response(Response.Status.OK, mimeType, serveFile);
            } else {
                // If it can't be found, let's use ChiliPeppr's proxy
                try {
                    URL url = new URL(URL_CHILIPEPPR_BASE + session.getUri());
                    return redirectedResponse(url);
                } catch (MalformedURLException e) {
                    return new ErrorResponse(e);
                }
            }
        }
    }

    private Response redirectedResponse(URL url){

        try {
            // Detect MIME type
            String mimeType = getMimeType(url.toString());

            InputStream is = url.openStream();
            String response = IOUtils.toString(is, "UTF-8");

            return new Response(Response.Status.OK, mimeType, response);
        } catch (IOException e){
            return new ErrorResponse(e);
        }
    }

    private String getMimeType(String uri){

        String ext = uri.substring(uri.lastIndexOf(".") + 1);

        switch (ext){

            case "css":
                return "text/css";

            case "js":
                return "application/javascript";

            case "details":
                return "text/plain";

            case "html":
                return "text/html";

            case "ico":
                return "image/x-icon";

            default:
                return "text/plain";

        }
    }

    public class JSONResponse extends JSONObject{

        public JSONResponse(){

            super();
            // Setup the default response
            status(ResponseStatus.Error);
        }

        public Response toResponse(){

            return new Response(this.toString());
        }

        public void status(ResponseStatus status){

            String statusStr = "unknown";

            if(status == ResponseStatus.OK) {
                statusStr = "okay";
            } else if(status == ResponseStatus.Error) {
                statusStr = "error";
            }
            put("status", statusStr);
        }

        public void message(String message){

            put("message", message);
        }
    }

    private class ErrorResponse extends Response{

        public ErrorResponse(Exception e){

            super(e.getMessage());
            log.debug(e.getMessage());
        }
    }
}
