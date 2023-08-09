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

 def version() {"1.0.0"}

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
    input name: "roomPrefix", type: "text", title: "Prefix for Room Names", defaultValue: ""
    input name: "shadePrefix", type: "text", title: "Prefix for Shade Names", defaultValue: ""
    input name: "scenePrefix", type: "text", title: "Prefix for Scene Names", defaultValue: ""
    input name: "wantShades", type: "bool", title: "Create Switches for each Shade?", defaultValue: false
    input name: "wantRooms", type: "bool", title: "Create Switches for each Room?", defaultValue: true
    input name: "openSuffix", type: "text", title: "Suffix on Room names for scene name to open shades in room", defaultValue: " Open"
    input name: "closeSuffix", type: "text", title: "Suffix on Room names for scene name to close shades in room", defaultValue: " Close"
    input name: "nameChanges", type: "text", title: "Name Translations for Open/Close Scene Names (changeroomname1=toscenename1,change2=to2) before suffixes", defaultValue: ""
    input name: "pruneMissing", type: "bool", title: "Remove Switches for missing Scenes/Shades", defaultValue: false
    input name: "autoOff", type: "bool", title: "Set scene switches off automatically after 5s", defaultValue: true
    input name: "debugMode", type: "bool", title: "Debug Mode", defaultValue: true
}

def refresh() {
    updateRoom('99', 'All')
    execCommand('data')
}

def installed() {
    debug("Gateway installed", "updated()")
    updated()
}

def updated() {
    debug("Gateway updated", "updated()")
    if(nameChanges) {
        def changes = nameChanges.split(',')
        changes?.each() {
            def parts = it.split('=')
            if(!state.nameChanges) state.nameChanges = [:]
            state.nameChanges[parts[0]] = parts[1]
        }
    }
    initialize()
    refresh()
}

def initialize() {
    unschedule()
    debug('Telnet Presence', "initialize()")
    state.queue = []
    state.processing = null
    if(settings.ip && settings.port) {
        telnetClose() 
        debug("connecting to ${settings.ip}:${settings.port}...")
        telnetConnect([termChars:[13]], ip, port as Integer, null, null)
        schedule('*/10 * * ? * *', execQueue)
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
    if(state.skippedTurns >= 20) initialize()
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
    if('ping' != cmd) {
        debug("closing command ${cmd} with callback ${state.processing['callback']}", "closeCommand()")
        log.info("Command ${cmd} completed")
    }
    if(state.processing && state.processing['callback']) "${state.processing['callback']}"()
    state.processing = null
    if('move' == cmd) sendCommand('release')
}

def sendMessage(msg) {
    if('$dmy' != msg) log.info("sending command ${msg} to Hunter Douglas Platinum gateway...")
    sendHubCommand(new hubitat.device.HubAction(msg, hubitat.device.Protocol.TELNET))
}

private parse(String msg) 
{
    //debug(msg)
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

    if(line == "${prefix}HunterDouglas Shade Controller") {
        log.info("connected as connection # ${prefix}")
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
      updateRoom(room_id, room_name)
      //debug("found room with ${room_id} and ${room_name}")
    } else if(line.startsWith("\$cm")) {
      // name of scene
      def scene_id = line[3..4]
      def scene_name = line.split('-')[-1].trim()
      //debug("found scene with ${scene_id} and ${scene_name}")
      updateScene(scene_id, scene_name)
    } else if(line.startsWith("\$cs")) {
      // name of a shade
      def parts = line.split('-')
      def shade_id = line[3..4]
      def shade_name = parts[-1].trim()
      def room_id = parts[1]
      //debug("found shade with ${shade_id} and ${shade_name}")
      updateShade(shade_id, shade_name, null)
      updateRoomShade(shade_id, 100, room_id)
    } else if(line.startsWith("\$cp")) {
      // state of a shade
      def shade_id = line[3..4]
      def stateTxt = line[-4..-2]
      def state = (stateTxt.toInteger()/255.0)*100 as Integer
      updateShade(shade_id, null, state)
      updateRoomShade(shade_id, state)
      //debug("found shade state with ${shade_id} and ${state}")
    }
}

def updateScene(id, name) {
    def namePrefix = scenePrefix
    if(namePrefix) {
        namePrefix = namePrefix.trim()+" "
    }
    debug("processing scene ${id} with name ${name}")
    if(!state.scenes) state.scenes = [:]
    state.scenes[name] = id
    createChildDevice("scene", name, id)
}

def updateRoomState(room_id) {
    if(!wantRooms || !state?.roomShades[room_id]) return
    def open = true
    state.roomShades[room_id].each() { shade_id, level ->
        open = (open && level > 95)
    }
    def cd = getChildDevice(makeChildDeviceId('room', room_id))
    if(cd) cd.sendEvent(name: 'switch', value: open ? 'off' : 'on')
}

def updateRoomShade(shade_id, level, room_id=null) {
    if(wantRooms && shade_id && level != null) {
        if(!room_id) {
            state?.roomShades.each() { rid, sids ->
                if(sids?.containsKey(shade_id) && '00' != rid) room_id = rid
            }
        }
        if(room_id){
            for(id in ['00', room_id]) {
                if(!state.roomShades) state.roomShades = [:]
                if(!state.roomShades[id]) state.roomShades[id] = [:]
                state.roomShades[id][shade_id] = level
                updateRoomState(id)
            }
        }
    }
}

def updateRoom(id, name) {
    def namePrefix = roomPrefix
    if(namePrefix) {
        namePrefix = namePrefix.trim()+" "
    }
    debug("processing room ${id} with name ${name}")
    if(wantRooms) {
        name = getRoomBase(name)
        createChildDevice("room", "${name} Shades", id)
    }
}

def updateShade(id, name, level=null) {
    def namePrefix = shadePrefix
    if(namePrefix) {
        namePrefix = namePrefix.trim()+" "
    }
    debug("processing shade ${id} with name ${name} and level ${level}")
    if(wantShades) {
        def cd = createChildDevice("shade", name, id)
        if(level) {
            cd.sendEvent(name: 'level', value: level)
            if(0 == level) { // open
                cd.sendEvent(name: 'switch', value: 'off')
            } else { // closed
                cd.sendEvent(name: 'switch', value: 'on')
            }
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
    def name = "Hunter Douglas ${type}"

    if(!createdDevice && label) {
        try {
            def component = 'shade' == deviceType ? 'Generic Component Dimmer' : 'Generic Component Switch'
            // create the child device
            addChildDevice("hubitat", component, deviceId, [label : "${label}", isComponent: false, name: "${name}"])
            createdDevice = getChildDevice(deviceId)
            def created = createdDevice ? "created" : "failed creation"
            log.info("Child device type: ${type} id: ${deviceId} label: ${label} ${created}")
        } catch (e) {
            logError("Failed to add child device with error: ${e}", "createChildDevice()")
        }
    } else {
        debug("Child device type: ${type} id: ${deviceId} already exists", "createChildDevice()")
        if(label && label != createdDevice.getLabel()) {
            createdDevice.setLabel(label)
            createdDevice.sendEvent(name:'label', value: label, isStateChange: true)
        }
        if(name && name != createdDevice.getName()) {
            createdDevice.setName(name)
            createdDevice.sendEvent(name:'name', value: name, isStateChange: true)
        }
        if(!debugMode) {
            createdDevice.updateSetting('logEnable', false)
            createdDevice.updateSetting('txtEnable', false)
        }
    }
    if('shade' == deviceType) createdDevice.sendEvent(name: 'switch', value: 'off')
    return createdDevice
}

def getRoomBase(roomName) {
    return roomName.replaceAll(/(?i) (bedroom|room|doors?|shades)/,'')
}

def getRoomScene(cd, on=true) {
    def suffix = on ? closeSuffix : openSuffix
    def label = state?.nameChanges?."${cd.label}" ?: cd.label
    def clean = label ? getRoomBase(label) : null
    def scene_name = label ? "${clean}${suffix}" : null
    if(scene_name && state.scenes?."${scene_name}") {
        return state.scenes[scene_name]
    }
    return null
}

void componentRefresh(cd) {
    debug("received refresh request from ${cd.displayName}")
    refresh()
}

def turnOff(map) {
    def cd = getChildDevice(map['device'])
    cd.sendEvent(name: 'switch', value: 'off')
}

def componentOn(cd) {
    debug("received on request from DN = ${cd.name}, DNI = ${cd.deviceNetworkId}")
    def idparts = cd.deviceNetworkId.split("-")
    def id = idparts[-1] as Integer
    def type = idparts[-2].toLowerCase()
    if('room' == type) id = getRoomScene(cd, true)
    'shade' == type ? setShadeLevel(id, 0) : runScene(id)
    cd.sendEvent(name: 'switch', value: 'on')
    if(autoOff && 'scene' == type) runIn(5, 'turnOff', [data: [device: cd.deviceNetworkId]])
    //runIn(20, 'refresh')
}

def componentOff(cd) {
    debug("received off request from DN = ${cd.name}, DNI = ${cd.deviceNetworkId}")
    def idparts = cd.deviceNetworkId.split("-")
    def id = idparts[-1] as Integer
    def type = idparts[-2].toLowerCase()
    if('room' == type) id = getRoomScene(cd, false)
    'shade' == type ? setShadeLevel(id, 100) : runScene(id)
    cd.sendEvent(name: 'switch', value: 'off')
    //runIn(20, 'refresh')
}

def componentSetLevel(cd, level) {
    debug("received shade level request from DN = ${cd.name}, DNI = ${cd.deviceNetworkId}")
    def idparts = cd.deviceNetworkId.split("-")
    def id = idparts[-1] as Integer
    def type = idparts[-2].toLowerCase()
    return setShadeLevel(id, level)
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