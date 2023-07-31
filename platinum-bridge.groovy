/**
 *  Hunter Douglas Platinum Gateway Bridge for Hubitat
 *  Schwark Satyavolu
 *  Originally based on: Allan Klein's (@allanak) and Mike Maxwell's code
 *
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

 def version() {"0.1.1"}

import hubitat.helper.InterfaceUtils

metadata {
    definition (name: "Hunter Douglas Platinum Gateway", namespace: "schwark", author: "Schwark Satyavolu") {
        capability "Initialize"
        capability "Refresh"
		capability "Telnet"

		command "runScene", ["number"]
		command "setShadeLevel", ["number", "number"]
    }
}

preferences {
    input name: "ip", type: "text", title: "Gateway IP", required: true
    input name: "port", type: "number", title: "Gateway Port", defaultValue: 522
    input name: "shadePrefix", type: "text", title: "Prefix for Shade Names", defaultValue: ""
    input name: "scenePrefix", type: "text", title: "Prefix for Scene Names", defaultValue: ""
    input name: "wantShades", type: "bool", title: "Create Switches for each Shade?", defaultValue: false
    input name: "debugMode", type: "bool", title: "Debug Mode", defaultValue: true
}

def updateStatus() {
	debug("updating status..")
	updateScenes()
	updateShades()
}

def refresh() {
	execCommand('data', null, updateStatus)
}

def installed() {
    debug("Gateway installed", "updated()")
    updated()
}

def updated() {
	getDB(true)

    debug("Gateway updated", "updated()")
    unschedule()
	initialize()
}

def initialize() {
	debug('Telnet Presence', "initialize()")
	state.queue = []
	state.processing = null
	if(settings.ip && settings.port) {
		telnetClose() 
		debug("connecting to ${settings.ip}:${settings.port}...")
		telnetConnect([termChars:[13]], ip, port as Integer, null, null)
		schedule('*/5 * * ? * *', execQueue)
	} else {
		logError("ip or port missing", "initialize()")
	}
}

def execCommand(command, params=null, callback=null) {
	state.queue.push(["cmd": command, "params": params, "callback": callback])
	//if("move" == command) state.queue.push(["cmd": "release"])
}

def execQueue() {
	if(state.processing && state.skippedTurns < 20) {
		state.skippedTurns = (state.skippedTurns ? state.skippedTurns : 0) + 1
		debug("still processing ${state.processing['cmd']} - so waiting for next turn #${state.skippedTurns}...")
		return
	}
	state.skippedTurns = 0
	def command = null
	if(state.queue) {
		command = state.queue.remove(0)
	} else {
		command = ["cmd": "ping"]
	}
	sendCommand(command['cmd'], command['params'], command['callback'])
}

private sendCommand(command, params=null, callback=null) {
	def commands = [
		"data": ["cmd": "\$dat", "sentinel":"\$upd01-"],
		"move": ["cmd": "\$pss%02d-%02d-%03d", "sentinel":"\$done"],
		"release": ["cmd": "\$rls", "sentinel":"\$act00-00-"],
		"exec": ["cmd": "\$inm%02d-", "sentinel":"\$act00-00-"],
		"ping": ["cmd": "\$dmy", "sentinel":"\$ack"]
	]
	state.processing = ["cmd": command, "params": params, "callback": callback, "sentinel": commands[command]['sentinel']]
	def msg = sprintf(commands[command]['cmd'], *(params ? params : []))
	sendMessage(msg)
}

def closeCommand() {
	def cmd = state.processing['cmd']
	debug("closing command ${cmd} with callback ${state.processing['callback']}", "closeCommand()")
	if(state.processing && state.processing['callback']) "${state.processing['callback']}"()
	state.processing = null
	if('move' == cmd) sendCommand('release')
}

def sendMessage(msg) {
	debug("sending command ${msg} on telnet...", "sendMessage()")
	sendHubCommand(new hubitat.device.HubAction(msg, hubitat.device.Protocol.TELNET))
}

def getDB(init) {
	if(!state.DB || init) {
		def DB = ['rooms':[:], 'shades':[:], 'scenes':[:]]
		state.DB = DB
	}
	return state.DB
}

private parse(String msg) 
{
	processLine(msg)
}

def telnetStatus(String status){
	debug("telnet error: ${status}", "telnetStatus()")
	if (status == "receive error: Stream is closed")
	{
		logError("Connection was dropped. Reconnecting...", "telnetStatus()")
		initialize()
		if(state.processing) {
			debug("Retrying command ${state.processing['cmd']} after reconnect...")
			execCommand(state.processing["cmd"], state.processing["params"], state.processing["callback"])
		}
	} 
}

def processLine(line) {
	def DB = getDB()
	def prefix = ""

    line = line.trim()
    if(!line) {
       return 
    }
    
    if(!prefix) {
      prefix = line[0..1]
    }
    else if(!line.startsWith(prefix)) {
      return
    }
    
    line = line.drop(2)
	if(state.processing && state.processing['sentinel'] == line) {
		closeCommand()
	}
  	//log.trace("processing line ${line}")
    if(line.startsWith("\$cr")) {
      // name of room
      def room_id = line[3..4]
      def room_name = line.split('-')[-1].trim()
      //debug("found room with ${room_id} and ${room_name}")
      DB['rooms'][room_id] = ['name':room_name, 'id':room_id, 'search':room_name.toLowerCase()]
    } else if(line.startsWith("\$cm")) {
      // name of scene
      def scene_id = line[3..4]
      def scene_name = line.split('-')[-1].trim()
      //debug("found scene with ${scene_id} and ${scene_name}")
      DB['scenes'][scene_id] = ['name':scene_name, 'id':scene_id, 'search':scene_name.toLowerCase()]
    } else if(line.startsWith("\$cs")) {
      // name of a shade
      def parts = line.split('-')
      def shade_id = line[3..4]
      def shade_name = parts[-1].trim()
      def room_id = parts[1]
      //debug("found shade with ${shade_id} and ${shade_name}")
      DB['shades'][shade_id] = ['name':shade_name, 'id':shade_id, 'search':shade_name.toLowerCase(), 'room': room_id]
    } else if(line.startsWith("\$cp")) {
      // state of a shade
      def shade_id = line[3..4]
      def stateTxt = line[-4..-2]
      def state = stateTxt.toInteger()/255.0
      //debug("found shade state with ${shade_id} and ${state}")
      def shade = DB['shades'][shade_id]
      if(shade) {
        DB['shades'][shade_id]['state'] = state
      }
    }
}

def updateScenes() {
	def DB = getDB()
	debug("Updating Scenes...")
	if(!state.scenes) {
		state.scenes = [:]
	}
	state.scenes.each() { id, sceneDevice ->
		if(DB['scenes'][id]) {
			// update device
			if(DB['scenes'][id]['name'] != sceneDevice.label) {
				debug("processing scene ${id} from name ${sceneDevice.label} to ${DB['scenes'][id]['name']}")
				sceneDevice.sendEvent(name:'label', value: DB['scenes'][id]['name'], isStateChange: true)
			}
			DB['scenes'].remove(id)
		} else {
			// remove device
			debug("removing scene ${id} from name ${sceneDevice.displayName}")
			deleteChildDevice(sceneDevice.deviceNetworkId)
		}
	}
	def namePrefix = scenePrefix
	if(namePrefix) {
		namePrefix = namePrefix.trim()+" "
	}
	DB['scenes']?.each() { id, sceneMap ->
		def name = sceneMap['name']
		debug("processing scene ${id} with name ${name}")
		def sceneDevice = createChildDevice("scene", name, id)
		if(sceneDevice) {
			debug("created child device scene ${id} with name ${name}")
			sceneDevice.setSceneNo(id)
			state.scenes[id] = sceneDevice
		}
	}
}

def updateShades() {
	if(!wantShades) return
	def DB = getDB()
	debug("Updating Shades...")

	if(!state.shades) {
		state.shades = [:]
	}
	state.shades.each() { id, shadeDevice ->
		if(DB['shades'][id]) {
			// update device
			if(DB['shades'][id]['name'] != shadeDevice.label) {
				log.debug("processing shade rename ${id} from name ${shadeDevice.label} to ${DB['shades'][id]['name']}")
				shadeDevice.sendEvent(name:'label', value: DB['shades'][id]['name'], isStateChange: true)
			}
			DB['shades'].remove(id)
		} else {
			// remove device
			debug("removing shade ${id} from name ${shadeDevice.displayName}")
			deleteChildDevice(shadeDevice.deviceNetworkId)
		}
	}
	def namePrefix = shadePrefix
	if(namePrefix) {
		namePrefix = namePrefix.trim()+" "
	}
	DB['shades']?.each() { id, shadeMap ->
		def name = shadeMap['name']
		debug("processing shade ${id} with name ${name}")
		def shadeDevice = createChildDevice("shade", name, id)
		if(shadeDevice) {
			debug("created child device shade ${id} with name ${name}")
			shadeDevice.setShadeNo(id)
			state.shades[id] = shadeDevice
		}
	}
}

def runScene(sceneId) {
	debug("Running Scene ${sceneId}", "runScene()")
	execCommand("exec", [sceneId as Integer])
}

def setShadeLevel(shadeId, percent) {
	debug("Controlling ${shadeId} to ${percent}", "shadeControl()")
	def id = shadeId as Integer
	def shadeValue = 255 - ((percent as Integer) * 2.55).toInteger()
	execCommand("move", [id, 4, shadeValue as Integer])
}

private createChildDevice(deviceType, label, id) {
	def type = deviceType.capitalize()
	def deviceId = makeChildDeviceId(deviceType, id)
	def createdDevice = getChildDevice(deviceId)

	if(!createdDevice) {
		try {
			// create the child device
			addChildDevice("schwark", "Hunter Douglas Platinum ${type}", deviceId, [label : "${label}", isComponent: false, name: "${label}"])
			createdDevice = getChildDevice(deviceId)
			def created = createdDevice ? "created" : "failed creation"
			debug("Child device type: ${type} id: ${deviceId} label: ${label} ${created}", "createChildDevice()")
		} catch (e) {
			logError("Failed to add child device with error: ${e}", "createChildDevice()")
		}
	} else {
		debug("Child device type: ${type} id: ${deviceId} already exists", "createChildDevice()")
	}
	return createdDevice
}

def makeChildDeviceId(deviceType, id) {
	def hubid = 'HDPGATEWAY'
	return "${hubid}-${deviceType.toUpperCase()}-${id}"
}

private removeChildDevices() {
    def childDevices = getChildDevices()

    try {
        childDevices.each { child ->
			def deviceId = child.getDeviceNetworkId()
            debug("Removing child device: ${deviceId}", "removeChildDevices()")
            deleteChildDevice(deviceId)
        }
    } catch (e) {
        logError("removeChildDevices", e)
    }
}

private debug(logMessage, fromMethod="") {
    if (settings.debugMode) {
        def fMethod = ""

        if (fromMethod) {
            fMethod = ".${fromMethod}"
        }

        log.debug("[Hunter Douglas Platinum Gateway] DEBUG: ${fMethod}: ${logMessage}")
    }
}

private logError(fromMethod, e) {
    log.error("[Hunter Douglas Platinum Gateway] ERROR: (${fromMethod}): ${e}")
}