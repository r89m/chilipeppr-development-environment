function detectChanges(callback){

    // Need to append a random string otherwise the server treats each request sequentially(!?!?!)
    $.get("/detect-changes", {_ : generateRandomString()}, function(data){

        if(callback){
            callback(data);
        }

        // If auto refresh is enabled, recreate the request to keep monitoring
        if(data.auto_refresh_enabled){
            detectChanges(callback);
        }
    }, "json");
}

function generateRandomString(){

    var text = "";
    var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    for( var i=0; i < 5; i++ )
        text += possible.charAt(Math.floor(Math.random() * possible.length));

    return text;
}