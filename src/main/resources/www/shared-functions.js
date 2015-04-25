function detectChanges(callback){

    console.log("start detecting");

    $.get("/detect-changes", function(data){

        console.log("detected");

        if(callback){
            console.log("callback");
            callback(data);
        }

        // If auto refresh is enabled, recreate the request to keep monitoring
        if(data.auto_refresh_enabled){
            detectChanges(callback);
        }
    }, "json");
}