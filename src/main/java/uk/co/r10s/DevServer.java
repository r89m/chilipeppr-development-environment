package uk.co.r10s;

import fi.iki.elonen.SimpleWebServer;
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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by Richard on 23/04/2015.
 */
public class DevServer extends SimpleWebServer {

    private final static String URI_AUTO_REFRESH = "/detect-changes";
    private final static String URI_PROXY = "/geturl";
    private final static String URI_FOLDER_INFO = "/info";
    private final static String URI_VERSION = "/version";
    private final static String URI_VERSION_NEW = "/version/new";

    private final static String URL_CHILIPEPPR_BASE = "http://www.chilipeppr.com/";
    private final static String URL_CHILIPEPPR_PROXY = URL_CHILIPEPPR_BASE + "geturl?url=";

    private int port;
    private File monitorFolder;
    private boolean disableAutoRefresh;
    private File webRoot;

    public DevServer(int port, ArrayList rootDirs, File monitorFolder, boolean disableAutoRefresh){

        super(null, port, rootDirs, true);
        this.port = port;
        this.monitorFolder = monitorFolder;
        this.disableAutoRefresh = disableAutoRefresh;
        this.webRoot = (File) rootDirs.get(0);
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

    @Override
    public Response serve(IHTTPSession session){

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

            default:
                return handleOtherRequests(session);
        }
    }

    private Response detectChanges(IHTTPSession session){

        JSONResponse response = new JSONResponse();
        response.status("okay");

        response.put("auto_refresh_enabled", !disableAutoRefresh);

        if(!disableAutoRefresh){
            try {
                // Create watcher service
                WatchService watcher = FileSystems.getDefault().newWatchService();

                // Register root folder
                monitorFolder.toPath().register(watcher,
                        ENTRY_CREATE,
                        ENTRY_DELETE,
                        ENTRY_MODIFY);

                // Register all sub-directories
                for(File folder : monitorFolder.listFiles()){
                    if(folder.isDirectory()) {
                        folder.toPath().register(watcher,
                                ENTRY_CREATE,
                                ENTRY_DELETE,
                                ENTRY_MODIFY);
                    }
                }

                // Taken from: https://docs.oracle.com/javase/tutorial/essential/io/notification.html

                // wait for key to be signaled
                WatchKey key = null;

                JSONArray eventFilenames = new JSONArray();

                do {
                    try {
                        // First time round, we want to wait for as long as necessary, but after that we only want to wait
                        //  a maximum of 50ms between events - this means we can catch all events that happen together, without
                        //  making the change detection much less responsive
                        if(key == null) {
                            key = watcher.take();
                        } else {
                            key = watcher.poll(50, TimeUnit.MILLISECONDS);
                        }
                    } catch (InterruptedException e) {
                        return new ErrorResponse(e);
                    }

                    if(key != null){
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();

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
                            Path folder = (Path)key.watchable();
                            Path filename = ev.context();

                            Path absoluteFilename = Paths.get(folder.toAbsolutePath().toString(), filename.toString());

                            // We don't really care what kind of event occurred - just let the browser decide if it wants to reload
                            eventFilenames.put(monitorFolder.toPath().toAbsolutePath().relativize(absoluteFilename.toAbsolutePath()));

                            // reset the key
                            boolean valid = key.reset();
                            if (!valid) {
                                // object no longer registered
                                key = null;
                            }
                        }
                    }
                } while(key != null);

                response.put("affected_files", eventFilenames);

            }catch(IOException e){
                return new ErrorResponse(e);
            }
        }

        return response.toResponse();
    }

    private Response proxyUrl(IHTTPSession session){

        String proxyUrl = getStr(session, "url");

        if(proxyUrl != null){
            try {
                URL url = new URL(URL_CHILIPEPPR_PROXY + URLEncoder.encode(proxyUrl));
                return redirectedResponse(url);
            } catch (MalformedURLException e){
                return new ErrorResponse(e);
            }


        } else {
            return new Response("Error: No URL provided");
        }
    }

    private Response getFolderInfo(IHTTPSession session){

        // Create the default response
        JSONResponse response = new JSONResponse();
        response.status("okay");
        response.put("folder_path", monitorFolder.getAbsolutePath());

        // If the monitored folder contains a file name 'demo.details', it is a module
        if(Files.exists(new File(monitorFolder, "demo.details").toPath())){
            response.put("is_module", true);
        } else {
            response.put("is_module", false);
            // If this folder potentially contains several folders, iterate over it's subfolders and
            //      find any that contain a 'demo.details' file
            JSONArray modules = new JSONArray();

            for(File module : monitorFolder.listFiles()){
                File moduleDetailsFile = new File(module, "demo.details");
                if(module.isDirectory() && Files.exists(moduleDetailsFile.toPath())){
                    // Create an object to store information about the module
                    JSONObject moduleInfo = new JSONObject();

                    // Store the path to the module
                    moduleInfo.put("path", module.getName());

                    try {
                        // Parse the details yaml file
                        FileInputStream is = new FileInputStream(moduleDetailsFile);
                        String yamlString = IOUtils.toString(is, "UTF-8");

                        yamlString = yamlString.substring(yamlString.indexOf("---"));
                        yamlString = yamlString.substring(0, yamlString.indexOf("..."));

                        Yaml moduleDetailsYaml = new Yaml();
                        Map<String,Object> moduleDetails = (Map<String, Object>)moduleDetailsYaml.load(yamlString);
                        JSONObject moduleDetailsJson = new JSONObject(moduleDetails);
                        moduleInfo.put("info", moduleDetailsJson);
                    } catch (IOException e){
                        response.put("status", "error");
                        response.put("error_message", e.getMessage());
                        e.printStackTrace();
                        break;
                    }
                    modules.put(moduleInfo);
                }
            }

            response.put("modules", modules);
        }


        return response.toResponse();
    }

    private Response getCurrentVersion(IHTTPSession session){

        JSONResponse response = new JSONResponse();
        response.status("okay");

        String version = getClass().getPackage().getImplementationVersion();

        if(version == null){
            version = "INVALID";
        }
        response.put("version", version);

        return response.toResponse();
    }

    private Response checkForNewVersion(IHTTPSession session){

        //TODO: Implement
        JSONResponse response = new JSONResponse();
        response.put("new_version_available", false);
        return response.toResponse();
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
            // Otherwise let the normal server respond
            Response response = super.serve(session);

            // Override MIME type if the default MIME type has been used
            if(response.getMimeType() == SimpleWebServer.MIME_DEFAULT_BINARY) {
                response.setMimeType(getMimeType(session.getUri()));
            }

            // If the response is not found, it's quite likely that we want to use the Chilipeppr proxy, so do so
            if(response.getStatus() == Response.Status.NOT_FOUND) {
                try {
                    URL url = new URL(URL_CHILIPEPPR_BASE + session.getUri());
                    return redirectedResponse(url);
                } catch (MalformedURLException e) {
                    return new ErrorResponse(e);
                }
            } else {
                return response;
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
                return SimpleWebServer.MIME_DEFAULT_BINARY;

        }
    }

    public class JSONResponse extends JSONObject{

        public JSONResponse(){

            super();
            // Setup the default response
            status("error");
        }

        public Response toResponse(){

            return new Response(this.toString());
        }

        public void status(String status){

            put("status", status);
        }

        public void message(String message){

            put("message", message);
        }
    }

    private class ErrorResponse extends Response{

        public ErrorResponse(Exception e){

            super(e.getMessage());
            System.out.println(e.getMessage());
        }
    }
}
