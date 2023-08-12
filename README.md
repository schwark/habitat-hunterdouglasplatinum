# Hunter Douglas Platinum Gateway Integration with Hubitat Hub

# How to install
The easiest way to install and keep up to date is to use [Hubitat Package Manager](https://hubitatpackagemanager.hubitatcommunity.com/installing.html). Search for Hunter Douglas to install - otherwise follow manual process below

This is an integration for Hubitat hubs to control shades that use the Hunter Douglas Platinum Gateway hub. This is NOT for PowerView shades.

To use, go to your Hubitat hub, Go to Developer tools / Drivers Code / Add New Driver and paste and save this file

https://raw.githubusercontent.com/schwark/hubitat-hunterdouglasplatinum/main/platinum-bridge.groovy

Then go to Devices / Add Device / Add Virtual Device. Name the device anything you want - like Hunter Douglas Gateway, or HDP Gateway, and pick "Hunter Douglas Platinum Gateway" from Type dropdown. Save the device.

Go to Devices / HDP Gateway. Scroll down and enter the Gateway IP address. If you want a switch created for EACH Shade, turn on the preference here - usually unnecessary for most people. Save Preferences

This should create a switch under HDP Gateway for each scene defined in your Hunter Douglas Platinum phone app. It can take upto a minute or two for these to be created.





