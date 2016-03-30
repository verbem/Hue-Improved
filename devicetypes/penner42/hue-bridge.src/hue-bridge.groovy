/**
 *  Hue Bridge
 *
 *  Copyright 2016 Alan Penner
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
	definition (name: "Hue Bridge", namespace: "penner42", author: "Alan Penner") {
		capability "Actuator"

		attribute "serialNumber", "string"
		attribute "networkAddress", "string"
		attribute "username", "string"

        command "discoverBulbs"
		command "discoverGroups"
        command "discoverScenes"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"rich-control"){
			tileAttribute ("device.serialNumber", key: "PRIMARY_CONTROL") {
				attributeState "default", label: "Hue Bridge", icon: "st.Lighting.light99-hue", backgroundColor: "#F3C200"
			}
		}
		standardTile("icon", "icon", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "default", label: "Hue Bridge", icon: "st.Lighting.light99-hue", backgroundColor: "#FFFFFF"
		}
		main (["icon"])
		details(["rich-control"])
	}
}

def discoverBulbs() {
	log.debug("Bridge discovering bulbs.")
	def host = this.device.currentValue("networkAddress") + ":80"
	def username = this.device.currentValue("username")

    return parent.getHubAction(host, "/api/${username}/lights", "discoverBulbs_response")
}

def discoverBulbs_response(resp) {
	log.debug("Bulb discovery response.")
	def body = new groovy.json.JsonSlurper().parseText(parseLanMessage(resp.description).body)
	sendEvent(name: "bulbDiscovery", value: device.hub.id, isStateChange: true, data: [body, this.device.deviceNetworkId])
}

def discoverScenes() {
	log.debug("Bridge discovering scenes.")
	def host = this.device.currentValue("networkAddress") + ":80"
	def username = this.device.currentValue("username")

    return parent.getHubAction(host, "/api/${username}/scenes", "discoverScenes_response")
}

def discoverScenes_response(resp) {
	log.debug("Scene discovery response.")
    def body = new groovy.json.JsonSlurper().parseText(parseLanMessage(resp.description).body)
	sendEvent(name: "sceneDiscovery", value: device.hub.id, isStateChange: true, data: [body, this.device.deviceNetworkId])
}

def discoverGroups() {
	log.debug("Bridge discovering groups.")
	def host = this.device.currentValue("networkAddress") + ":80"
	def username = this.device.currentValue("username")

    return parent.getHubAction(host, "/api/${username}/groups", "discoverGroups_response")
}

def discoverGroups_response(resp) {
	log.debug("Group discovery response.")
    def body = new groovy.json.JsonSlurper().parseText(parseLanMessage(resp.description).body)
	sendEvent(name: "groupDiscovery", value: device.hub.id, isStateChange: true, data: [body, this.device.deviceNetworkId])
}

// parse events into attributes
def parse(String description) {
	def parsedEvent = parseLanMessage(description)
	if (parsedEvent.headers && parsedEvent.body) {
		def headerString = parsedEvent.headers.toString()
		if (headerString.contains("application/json")) {
			def body = new groovy.json.JsonSlurper().parseText(parsedEvent.body)
			def bridge = parent.getBridge(parsedEvent.mac)
			/* response from bulb/group/scene command. Figure out which device it is, then pass it along to the device. */
			if (body[0] != null && body[0].success != null) {
				body.each{
					it.success.each { k, v ->
						def spl = k.split("/")
						def devId = ""
						if (spl[4] == "scene") {
							devId = bridge.value.mac + "/SCENE" + v
						} else {
							if (spl[1] == "lights") {
								spl[1] = "BULBS"
							}
							devId = bridge.value.mac + "/" + spl[1].toUpperCase()[0..-2] + spl[2]
						}
						def d = parent.getChildDevice(devId)
						log.debug(d)
						log.debug ("${devId} ${spl[3]} ${spl[4]} ${v}")
						d.updateStatus(spl[3], spl[4], v)
					}
				}
			} else if (body[0] != null && body[0].error != null) {
				log.debug("Error: ${body}")
			} else if (bridge) {
				// && parent.state.inItemDiscovery && parent.state.inItemDiscovery == bridge.value.mac) {
				def bulbs = bridge.value.bulbs
				def groups = bridge.value.groups
				def scenes = bridge.value.scenes

				body.each { k, v ->
					def deviceCreated = false
					if (v.type == "Extended color light" || v.type == "Color light" || v.type == "Dimmable light") {
						bulbs[k] = [id: k, name: v.name, type: v.type, state: v.state]
					} else if (v.type == "LightGroup" || v.type == "Room") {
						groups[k] = [id: k, name: v.name, type: v.type, action: v.action]
					} else {
						scenes[k] = [id: k, name: v.name]
					}
				}
				return createEvent(name: "itemDiscovery", value: device.hub.id, isStateChange: true, data: [bulbs, scenes, groups, bridge.value.mac])
			}
		} else {
			log.debug("Unrecognized messsage: ${parsedEvent.body}")
		}
	}
	return []
}