# Hunter Douglas Platinum Gateway Integration with Hubitat Hub

This is an integration for Hubitat hubs to control shades that use the Hunter Douglas Platinum Gateway hub. This is NOT for PowerView shades.

To use, go to your Hubitat hub, Go to Developer tools / Drivers Code and paste and save each of these files separately

1. https://raw.githubusercontent.com/schwark/hubitat-hunterdouglasplatinum/main/platinum-bridge.groovy
2. https://raw.githubusercontent.com/schwark/hubitat-hunterdouglasplatinum/main/platinum-scene-switch.groovy
3. https://raw.githubusercontent.com/schwark/hubitat-hunterdouglasplatinum/main/platinum-shade-switch.groovy

Then go to Devices / Add Device / Add Virtual Device. Name the device anything you want - like Hunter Douglas Gateway, or HDP Gateway, and pick "Hunter Douglas Platinum Gateway" from Type dropdown. Save the device.

Go to Devices / HDP Gateway. Scroll down and enter the Gateway IP address. If you want a switch created for EACH Shade, turn on the preference here - usually unnecessary for most people. Save Preferences

This should create a switch under HDP Gateway for each scene defined in your Hunter Douglas Platinum phone app. It can take upto a minute or two for these to be created.





