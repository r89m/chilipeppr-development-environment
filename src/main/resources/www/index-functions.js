$.get("/info", function(folder){

    // If this folder is a module, redirect straight to the module page
    if(folder.is_module){
        window.location.href = 'module.html';
    }

    var limitedPathName = folder.folder_path;

    var folderMaxDisplayLength = 80;

    if(limitedPathName.length > folderMaxDisplayLength){
        var segmentLength = (folderMaxDisplayLength / 2) - 5;
        var shortNameStart = limitedPathName.substr(0, segmentLength);
        var shortNameEnd = limitedPathName.substr(0 - segmentLength);
        limitedPathName = shortNameStart + "..." + shortNameEnd;
    }

    $('#folder-path').text(limitedPathName).attr("title", folder.folder_path);

    if(!folder.is_module){
        if(folder.modules.length == 0){
            // No modules found
            $('<div class="row"></div>').appendTo('#modules');
            var row = $('#modules').find(".row");
            $('#empty-col').appendTo(row);
            $('#no-modules').appendTo(row);
        } else {
            for(var x in folder.modules){
                var module = folder.modules[x];

                var moduleElement = $('#cell-template').clone();
                moduleElement.find("h2").text(module.info.name);
                moduleElement.find("code").text(module.path);
                moduleElement.find(".description").text(module.info.description);
                moduleElement.find("a").attr("href", "module.html?" + module.path);

                if(x % 3 == 0){
                    $('#modules').append('<div class="row"></div>');
                }

                $('#modules').find(".row").last().append(moduleElement);
            }
        }
    }

}, "json");

$.get("/version", function(data){

    if(data.version){
        $('#current-version').text(data.version);

        // Check to see if there's a later version online
        $.get("/version/new", function(data){

            if(data.new_version_available){
                $('#new-version').show();
            }
        }, "json");
    } else {
        $('#current-version').text("In Development");
    }
}, "json");

function detectFolderChanges(){

    detectChanges(function(data){

        // Check whether we care about the file(s) that were affected
        for(var x in data.affected_files){
            var filename = data.affected_files[x];
            // We only need to refresh the index page if a '.details' file has been created / deleted / modified
            if(filename.substr(-8) == '.details'){
                window.location.reload();
            }
        }
    });

}

detectFolderChanges();