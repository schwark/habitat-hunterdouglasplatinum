/**
 *  Hunter Douglas Platinum Gateway Bridge for Hubitat
 *  Schwark Satyavolu
 *  ************* THIS IS NOT USED - ONLY FOR ARCHIVAL ****************
 *
 */

String appVersion() { return "1.0.0" }
String appModified() { return "2022-07-28" }
String appAuthor() { return "Schwark Satyavolu" }

String GATEWAY_ID() { return "HDPGATEWAY" }

definition(
    name: "Hunter Douglas Platinum Connect",
    namespace: "schwark",
    author: "Schwark Satyavolu",
    description: "Allows you to control your Hunter Douglas Platinum Shades with Hubitat",
    category: "Integration",
    iconUrl: "https://is1-ssl.mzstatic.com/image/thumb/Purple128/v4/2c/e8/fc/2ce8fc28-1314-4303-512f-97635091325f/AppIcon-1x_U007emarketing-85-220-9.png/246x0w.png",
    iconX2Url: "https://is1-ssl.mzstatic.com/image/thumb/Purple128/v4/2c/e8/fc/2ce8fc28-1314-4303-512f-97635091325f/AppIcon-1x_U007emarketing-85-220-9.png/246x0w.png",
    singleInstance: true
)

preferences {
    page(name: "mainPage", title: "Hunter Douglas Platinum Setup", install: true, uninstall: true)
}

def installed() {
    debug("Installed with settings: ${settings}", "installed()")
	updated()
}

def uninstalled() {
    debug("Uninstalling with settings: ${settings}", "uninstalled()")
    unschedule()

    removeChildDevices()
}

def updated() {
    unsubscribe()
    unschedule()

    debug("Updated with settings: ${settings}", "updated()")

    if (sanityCheck()) {
        // update child devices after app updated
        updateChildDevices()

        initialize()
    }
}

def initialize() {
    debug("Initializing Hunter Douglas Platinum Shades", "initialize()")

    unsubscribe()
    unschedule()

    // remove location subscription aftwards
    state.subscribe = false
}

def mainPage() {
    dynamicPage(name: "main", title: "Hunter Douglas Platinum Setup", uninstall: true, install: true) {
        section {
            input "ip", "text", title: "Hub IP", required: true
        }
        section {
            input "port", "number", title: "Hub Port", defaultValue: 522
        }
//        section {
//            input "pollEvery", "enum", title: "How often should the panel be polled for updates?", options: ["1 Minute", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "60 Minutes", "3 Hours", "Never"], defaultValue: "30 Minutes", required: true
//        }
        section {
            input "wantShades", "bool", title: "Create Shades?", description: "Create a switch for each shade?", defaultValue: false
        }
        section {
            input "debugMode", "bool", title: "Enable debugging", defaultValue: true
        }
    }
}

def makeNetworkId(ip) { 
	String hexIp = ip.tokenize('.').collect {String.format('%02X', it.toInteger()) }.join() 
	String hexPort = String.format('%04X', port.toInteger()) 
	debug()"The target device is configured as: ${hexIp}-${hexPort}")
	return "${hexIp}-${hexPort}" 
}

/*
def netbios_encode(name, type)
    String result = ""
    name = String.format("%-15s", name.toUpperCase()) + type
    result = Character.toString((char)(name.length() * 2)) + name:gsub(".", function(c) 
        return Character.toString((char)(((byte)(c)>>4)+(byte)('A'))) + Character.toString((char)(((byte)(c)&0xF)+(byte)('A')))
    end) + "\00"
    return result
end

def netbios_decode(name)
    String result = ""
    for i = 1, #name, 2 do
        local c = name:sub(i,i)
        local d = name:sub(i+1, i+1)
        result = result .. string.char(((string.byte(c)-string.byte('A'))<<4) + ((string.byte(d)-string.byte('A'))&0xF))
    end
    return result
end

def netbios_lookup(name)
    String WORKSTATION_SERVICE = "\x00"
    String SERVER_SERVICE = "\x20"
    String transaction_id = "\x00\x01"
    String broadcast_header = "\x01\x10"
    String rest_header = "\x00\x01\x00\x00\x00\x00\x00\x00"
    String nbns_prefix = transaction_id + broadcast_header + rest_header
    String nbns_suffix = "\x00\x20\x00\x01"
    String broadcast_addr = "255.255.255.255"
    Integer nbns_port = 137
    String ip = null
    local response, port
    local udp = socket.udp()
    udp:setoption("broadcast", true)
    udp:setsockname("*",0)
    udp:settimeout(2)
    --udp:setpeername(broadcast_addr, nbns_port)
    String query = nbns_prefix + netbios_encode(name, SERVER_SERVICE) + nbns_suffix
    udp:sendto(query, broadcast_addr, nbns_port)
    --udp:send(query)
    response, ip, port = udp:receivefrom()
    if "timeout" ~= ip and "closed" ~= ip then
        log.info("NetBios response received from "..ip)
    else
        ip = nil
    end
    udp:close()
    return ip
end
*/

def addGateway() {
	def gateway = null
	if(settings.ip) {
		gateway = createChildDevice("gateway", "Hunter Douglas Platinum Gateway", GATEWAY_ID())
	} else {
		debug("IP not set", "addGateway()")
	}
	return gateway
}

/////////////////////////////////////
def locationHandler(evt) {
	log.debug "$locationHandler(evt.description)"
	def description = evt.description
	def hub = evt?.hubId
	state.hubId = hub
	log.debug("location handler: event description is ${description}")
}

/////////////////////////////////////
def doDeviceSync(){
	log.debug "Doing Platinum Gateway Device Sync!"

	if(!state.subscribe) {
		subscribe(location, null, locationHandler, [filterEvents:false])
		state.subscribe = true
	}
	updateStatus()
}

private getGateway() {
	return getChildDevice(getDeviceId(GATEWAY_ID()))
}

private updateChildDevices() {
	def gateway = addGateway()
	gateway.setIPPort(settings.ip, settings.port)
}

private debug(logMessage, fromMethod="") {
    if (debugMode) {
        def fMethod = ""

        if (fromMethod) {
            fMethod = ".${fromMethod}"
        }

        log.debug("[Hunter Douglas Platinum Connect] DEBUG: ${fMethod}: ${logMessage}")
    }
}

private logError(fromMethod, e) {
    log.error("[Hunter Douglas Platinum Connect] ERROR: (${fromMethod}): ${e}")
}