# ChiliPeppr Development Environment #
This is an application that will allow you to edit and test your new ChiliPeppr modules on your local machine, 
meaning you can take advantage of all the goodness that your favourite IDE / Text Editor / Version Control system has to offer

## Screenshots ##
###List of modules found###
![List of Modules Found](/screenshots/overview.png?raw=true)
###Preview your module###
![List of Modules Found](/screenshots/module.png?raw=true)

## Running ##
1. Download [chilipeppr-development-environment.jar](https://github.com/shaggythesheep/chilipeppr-development-environment/blob/master/chilipeppr-development-environment.jar)
2. Place it in the folder that contains your module and double click. It'll start a server that looks at that folder and open your web browser to let you view the results.

If it doesn't run, open the command line to the directory the jar is in and run the following `java -jar chilipeppr-development-environment.jar`

## Advanced ##
There are a few command line options that let you have a little more control over how the server works

|Command|Arguments|Description|
|-------|---------|-----------|
|`--port`|Port Number|lets you specify which port you want the server to run on - useful if something is already using port `5938` or you want to run several instances (although using --folder is a better option)|
|`--folder`|Path to Folder|lets you specify the folder in which to find your module(s). If the folder given finds several modules in its subdirectories you will be shown a list of them|
|`--disable-auto-refresh`|None|by default the server automatically refrehes the page content when you make any changes - this might not always be useful, so you can turn it off|
|`--disable-launch-browser`|None|to make this as easy as possible to run, the server launches a web browser page when it starts, again this can be disabled|