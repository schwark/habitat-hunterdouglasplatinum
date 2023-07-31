/**
 *  Hunter Douglas Platinum Gateway Scene Control Switch for SmartThings
 *  Schwark Satyavolu
 *  Originally based on: Allan Klein's (@allanak) and Mike Maxwell's code
 *
 *  Usage:
 *  1. Add this code as a device handler in the SmartThings IDE
 *  3. Create a device using PlatinumGatewaySceneSwitch as the device handler using a hexadecimal representation of IP:port as the device network ID value
 *  For example, a gateway at 192.168.1.222:522 would have a device network ID of C0A801DE:20A
 *  Note: Port 522 is the default Hunter Douglas Platinum Gateway port so you shouldn't need to change anything after the colon
 *  4. Enjoy the new functionality of the SmartThings app
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

metadata {
	definition (name: "Hunter Douglas Platinum Scene", namespace: "schwark", author: "Schwark Satyavolu") {
	capability "Switch"
	command "setSceneNo", ["number"]
	command "runScene"
	}
}

def updated() {
}

def runScene() {
	parent.runScene(state.sceneNo)
}

def on() {
	runScene()
	sendEvent(name: "switch", value: "on")
}

def off() {
	runScene()
	sendEvent(name: "switch", value: "off")
}

def setSceneNo(sceneNo) {
	state.sceneNo = sceneNo
}



