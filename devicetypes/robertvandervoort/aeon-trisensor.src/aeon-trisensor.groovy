/**
 *
 *  Aeon TriSensor
 *   
 *	github: Robert Vandervoort (robertvandervoort)
 *	Date: 2018-06-01
 *
 *  Code has elements from other community sources @erocm23, @CyrilPeponnet. Greatly reworked and 
 *  optimized for improved battery life (hopefully) :) and ease of advanced configuration. I tried to get it
 *  as feature rich as possible. 
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
	definition (name: "Aeon Trisensor", namespace: "robertvandervoort", author: "Robert Vandervoort") {
		capability "Motion Sensor"
		capability "Temperature Measurement"
		capability "Illuminance Measurement"
		capability "Configuration"
		capability "Sensor"
		capability "Battery"
        capability "Refresh"
        capability "Health Check"
        
        command "resetBatteryRuntime"
		
        attribute   "needUpdate", "string"
        
        // RAW DESC zw:Ss type:0701 mfr:0371 prod:0102 model:0005 ver:2.07 zwv:4.61 lib:03 cc:5E,98,9F,55,6C sec:86,73,85,8E,59,72,5A,80,84,30,71,31,70,7A role:06 ff:8C07 ui:8C07
        // fingerprint deviceId: "0x2101", inClusters: "0x5E,0x86,0x72,0x59,0x85,0x73,0x71,0x84,0x80,0x30,0x31,0x70,0x98,0x7A,0x5A" // 1.07 & 1.08 Secure
        
        fingerprint mfr:"0371", prod:"0102", model:"0005", deviceJoinName: "Aeon TriSensor"

	}
    preferences {
        input description: "Once you change values on this page, the corner of the \"configuration\" icon will change orange until all configuration parameters are updated.", title: "Settings", displayDuringSetup: false, type: "paragraph", element: "paragraph"
		generate_preferences(configuration_model())
    }
	simulator {
	}
	tiles (scale: 2) {
		valueTile("temperature", "device.temperature", inactiveLabel: false, width:6, height:4) {
			state ("temperature", label:'${currentValue}°',
            	backgroundColors:[
                	[value: 31, color: "#153591"],
                	[value: 44, color: "#1e9cbb"],
                	[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
        standardTile("motion","device.motion", inactiveLabel: false, width: 2, height: 2) {
                state ("inactive",label:'no motion',icon:"st.motion.motion.inactive",backgroundColor:"#ffffff")
                state ("active",label:'motion',icon:"st.motion.motion.active",backgroundColor:"#00a0dc")
		}
		valueTile("illuminance", "device.illuminance", inactiveLabel: false, width: 2, height: 2) {
           state ("luminosity", label:'${currentValue} LUX', unit:"lux")
		}
		valueTile("battery", "device.battery", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}
        valueTile("batteryTile", "device.batteryTile", inactiveLabel: false, width: 2, height: 2) {
			state "batteryTile", label:'${currentValue}', unit:""
		}
        valueTile("currentFirmware", "device.currentFirmware", inactiveLabel: false, width: 2, height: 2) {
			state "currentFirmware", label:'Firmware: v${currentValue}', unit:""
		}
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        standardTile("configure", "device.needUpdate", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "NO" , label:'', action:"configuration.configure", icon:"st.secondary.configure"
            state "YES", label:'', action:"configuration.configure", icon:"https://github.com/erocm123/SmartThingsPublic/raw/master/devicetypes/erocm123/qubino-flush-1d-relay.src/configure@2x.png"
        }
        standardTile("batteryRuntime", "device.batteryRuntime", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "batteryRuntime", label:'Battery: ${currentValue} Double tap to reset counter', unit:"", action:"resetBatteryRuntime"
		}
        standardTile("statusText2", "device.statusText2", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "statusText2", label:'${currentValue}', unit:"", action:"resetBatteryRuntime"
		}
        
		main "temperature"
		details("temperature","illuminance","motion","batteryTile","refresh","configure","statusText2")
	}
}

def parse(String description)
{
	def result = []
    switch(description){
        case ~/Err 106.*/:
			state.sec = 0
			result = createEvent( name: "secureInclusion", value: "failed", isStateChange: true,
			descriptionText: "This sensor failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.")
        break
		case "updated":
        	result = createEvent( name: "Inclusion", value: "paired", isStateChange: true,
			descriptionText: "Update is hit when the device is paired")
            result << response(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 3600, nodeid:zwaveHubNodeId).format())
            result << response(zwave.batteryV1.batteryGet().format())
            result << response(zwave.versionV1.versionGet().format())
            result << response(zwave.manufacturerSpecificV2.manufacturerSpecificGet().format())
            result << response(configure())
        break
        default:
			def cmd = zwave.parse(description, [0x31: 5, 0x30: 2, 0x84: 1])
			if (cmd) {
                try {
				result += zwaveEvent(cmd)
                } catch (e) {
                log.debug "error: $e cmd: $cmd description $description"
                }
			}
        break
	}
    
    if(state.batteryRuntimeStart != null){
        sendEvent(name:"batteryRuntime", value:getBatteryRuntime(), displayed:false)
        if (device.currentValue('currentFirmware') != null){
            sendEvent(name:"statusText2", value: "Firmware: v${device.currentValue('currentFirmware')} - Battery: ${getBatteryRuntime()} Double tap to reset", displayed:false)
        } else {
            sendEvent(name:"statusText2", value: "Battery: ${getBatteryRuntime()} Double tap to reset", displayed:false)
        }
    } else {
        state.batteryRuntimeStart = now()
    }
	
    if ( result[0] != null ) { result }
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x31: 5, 0x30: 2, 0x84: 1])
	state.sec = 1
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
		createEvent(descriptionText: cmd.toString())
	}
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
	response(configure())
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
    if (cmd.parameterNumber.toInteger() == 81 && cmd.configurationValue == [255]) {
        update_current_properties([parameterNumber: "81", configurationValue: [1]])
        logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '1'")
    } else {
        update_current_properties(cmd)
        logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'")
    }
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpIntervalReport cmd)
{
	logging("WakeUpIntervalReport ${cmd.toString()}")
    state.wakeInterval = cmd.seconds
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
    logging("Battery Report: $cmd")
    def events = []
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} battery is low"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
    if(settings."101" == null || settings."101" == "241") {
        try {
            events << createEvent([name: "batteryTile", value: "Battery ${map.value}%", displayed:false])
        } catch (e) {
            logging("$e")
        }
    }
    events << createEvent(map)
    
    state.lastBatteryReport = now()
    return events
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			map.name = "temperature"
			def cmdScale = cmd.scale == 1 ? "F" : "C"
            state.realTemperature = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.value = getAdjustedTemp(state.realTemperature)
			map.unit = getTemperatureScale()
            logging("Temperature Report: $map.value")
			break;
		case 3:
			map.name = "illuminance"
            state.realLuminance = cmd.scaledSensorValue.toInteger()
			map.value = getAdjustedLuminance(cmd.scaledSensorValue.toInteger())
			map.unit = "lux"
            logging("Illuminance Report: $map.value")
			break;
		default:
			map.descriptionText = cmd.toString()
	}
    
    def request = update_needed_settings()
    
    if(request != []){
        return [response(commands(request)), createEvent(map)]
    } else {
        return createEvent(map)
    }

}

def motionEvent(value) {
	def map = [name: "motion"]
	if (value) {
		map.value = "active"
		map.descriptionText = "$device.displayName detected motion"
	} else {
		map.value = "inactive"
		map.descriptionText = "$device.displayName motion has stopped"
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
    logging("SensorBinaryReport: $cmd")
	motionEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
    logging("BasicSet: $cmd")
	motionEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
    logging("NotificationReport: $cmd")
	def result = []
	if (cmd.notificationType == 7) {
		switch (cmd.event) {
			case 0:
				result << motionEvent(0)
				break
			case 8:
				result << motionEvent(1)
				break
		}		
	} else {
        logging("Need to handle this cmd.notificationType: ${cmd.notificationType}")
		result << createEvent(descriptionText: cmd.toString(), isStateChange: false)
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
    logging("Device ${device.displayName} woke up")
    
    def request = update_needed_settings()

    if(request != []){
       response(commands(request) + ["delay 5000", zwave.wakeUpV1.wakeUpNoMoreInformation().format()])
    } else {
       logging("No commands to send")
       response([zwave.wakeUpV1.wakeUpNoMoreInformation().format()])
    }
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
    logging(cmd)
    if(cmd.applicationVersion && cmd.applicationSubVersion) {
	    def firmware = "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}${location.getTemperatureScale() == 'C' ? 'EU':''}"
        state.needfwUpdate = "false"
        updateDataValue("firmware", firmware)
        createEvent(name: "currentFirmware", value: firmware)
    }
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    logging("Unknown Z-Wave Command: ${cmd.toString()}")
}

def refresh() {
   	logging("$device.displayName refresh()")

    def request = []
    if (state.lastRefresh != null && now() - state.lastRefresh < 5000) {
        logging("Refresh Double Press")
        state.currentProperties."111" = null
        state.wakeInterval = null
        def configuration = parseXml(configuration_model())
        configuration.Value.each
        {
            if ( "${it.@setting_type}" == "zwave" ) {
                request << zwave.configurationV1.configurationGet(parameterNumber: "${it.@index}".toInteger())
            }
        } 
        request << zwave.versionV1.versionGet()
        request << zwave.wakeUpV1.wakeUpIntervalGet()
    } else {
        request << zwave.batteryV1.batteryGet()
        request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1, scale:1)
        request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:3, scale:1)
    }

    state.lastRefresh = now()
    
    commands(request)
}

def ping() {
   	logging("$device.displayName ping()")

    def request = []
    request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1, scale:1)
    request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:3, scale:1)
    
    commands(request)
}

def configure() {
    state.enableDebugging = settings.enableDebugging
    logging("Configuring Device For SmartThings Use")
    def cmds = []

    cmds = update_needed_settings()
    
    if (cmds != []) commands(cmds)
}

def updated()
{
    state.enableDebugging = settings.enableDebugging
    sendEvent(name: "checkInterval", value: 6 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    logging("updated() is being called")
        try {
            sendEvent(name:"batteryTile", value: "Battery ${(device.currentValue("battery") == null ? '?' : device.currentValue("battery"))}%", displayed:false)
        } catch (e) {
            logging("$e")
            sendEvent(name:"battery", value: "100", displayed:false)
            sendEvent(name:"batteryTile", value: "Battery ${(device.currentValue("battery") == null ? '?' : device.currentValue("battery"))}%", displayed:false)
        }
    
    state.needfwUpdate = ""
    
    if (state.realTemperature != null) sendEvent(name:"temperature", value: getAdjustedTemp(state.realTemperature))
    if (state.realLuminance != null) sendEvent(name:"illuminance", value: getAdjustedLuminance(state.realLuminance))
    
    def cmds = update_needed_settings()
    
    if (device.currentValue("battery") == null) cmds << zwave.batteryV1.batteryGet()
    if (device.currentValue("temperature") == null) cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1, scale:1)
    if (device.currentValue("illuminance") == null) cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:3, scale:1)
        
    //updateStatus()
    
    sendEvent(name:"needUpdate", value: device.currentValue("needUpdate"), displayed:false, isStateChange: true)
    
    response(commands(cmds))
}

def resetTamperAlert() {
    sendEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName tamper cleared")
    sendEvent(name: "acceleration", value: "inactive", descriptionText: "$device.displayName tamper cleared")
    sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped")
}

def convertParam(number, value) {
	switch (number){
        case 41:
            //Parameter difference between firmware versions
        	if (settings."41".toInteger() != null && device.currentValue("currentFirmware") != null) {
                if (device.currentValue("currentFirmware") == "1.07" || device.currentValue("currentFirmware") == "1.08" || device.currentValue("currentFirmware") == "1.09") {
                    (value * 256) + 2
                } else if (device.currentValue("currentFirmware") == "1.10") {
                    (value * 65536) + 512
                } else if (device.currentValue("currentFirmware") == "1.10EU" || device.currentValue("currentFirmware") == "1.11EU") {
                    (value * 65536) + 256
                } else if (device.currentValue("currentFirmware") == "1.07EU" || device.currentValue("currentFirmware") == "1.08EU" || device.currentValue("currentFirmware") == "1.09EU") {
                    (value * 256) + 1
                } else {
                    value
                }	
            } else {
                value
            }
        break
        case 45:
            //Parameter difference between firmware versions
        	if (settings."45".toInteger() != null && device.currentValue("currentFirmware") != null && device.currentValue("currentFirmware") != "1.08")
            	2
            else
                value
        break
        case 101:
        	if (settings."40".toInteger() != null) {
                if (settings."40".toInteger() == 1) {
                   0
                } else {
                   value
                }	
            } else {
                241
            }
        break
    	case 201:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        case 202:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        case 203:
            if (value < 0)
            	65536 + value
        	else if (value > 1000)
            	value - 65536
            else
            	value
        break
        case 204:
        	if (value < 0)
            	256 + value
        	else if (value > 100)
            	value - 256
            else
            	value
        break
        default:
        	value
        break
    }
}

def update_current_properties(cmd)
{
    def currentProperties = state.currentProperties ?: [:]
    
    currentProperties."${cmd.parameterNumber}" = cmd.configurationValue

    if (settings."${cmd.parameterNumber}" != null)
    {   
            if (convertParam("${cmd.parameterNumber}".toInteger(), settings."${cmd.parameterNumber}".toInteger()) == cmd2Integer(cmd.configurationValue))
            {
                sendEvent(name:"needUpdate", value:"NO", displayed:false, isStateChange: true)
            }
            else
            {
                sendEvent(name:"needUpdate", value:"YES", displayed:false, isStateChange: true)
            }
    }

    state.currentProperties = currentProperties
}

def update_needed_settings()
{
    def cmds = []
    def currentProperties = state.currentProperties ?: [:]
     
    def configuration = parseXml(configuration_model())
    def isUpdateNeeded = "NO"
    
    if(!state.needfwUpdate || state.needfwUpdate == "") {
       logging("Requesting device firmware version")
       cmds << zwave.versionV1.versionGet()
    }

    if(state.wakeInterval == null || state.wakeInterval != getAdjustedWake()){
        logging("Setting Wake Interval to ${getAdjustedWake()}")
        cmds << zwave.wakeUpV1.wakeUpIntervalSet(seconds: getAdjustedWake(), nodeid:zwaveHubNodeId)
        cmds << zwave.wakeUpV1.wakeUpIntervalGet()
    }

    configuration.Value.each
    {     
        if ("${it.@setting_type}" == "zwave"){
            if (currentProperties."${it.@index}" == null)
            {
                if (device.currentValue("currentFirmware") == null || "${it.@fw}".indexOf(device.currentValue("currentFirmware")) >= 0){
                    isUpdateNeeded = "YES"
                    logging("Current value of parameter ${it.@index} is unknown")
                    cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
                }
            } 
            else if (settings."${it.@index}" != null && cmd2Integer(currentProperties."${it.@index}") != convertParam(it.@index.toInteger(), settings."${it.@index}".toInteger()))
            { 
                if (device.currentValue("currentFirmware") == null || "${it.@fw}".indexOf(device.currentValue("currentFirmware")) >= 0){
                    isUpdateNeeded = "YES"

                    logging("Parameter ${it.@index} will be updated to " + convertParam(it.@index.toInteger(), settings."${it.@index}".toInteger()))
                    
               
                    if (it.@index == "41") {
                        if (device.currentValue("currentFirmware") == "1.06" || device.currentValue("currentFirmware") == "1.06EU") {
                            cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertParam(it.@index.toInteger(), settings."${it.@index}".toInteger()), 2), parameterNumber: it.@index.toInteger(), size: 2)
                        } else if (device.currentValue("currentFirmware") == "1.10" || device.currentValue("currentFirmware") == "1.10EU" || device.currentValue("currentFirmware") == "1.11EU") {
                            cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertParam(it.@index.toInteger(), settings."${it.@index}".toInteger()), 4), parameterNumber: it.@index.toInteger(), size: 4)
                        } else {
                            cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertParam(it.@index.toInteger(), settings."${it.@index}".toInteger()), 3), parameterNumber: it.@index.toInteger(), size: 3)
                        }
                    } else {
                    
                        cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertParam(it.@index.toInteger(), settings."${it.@index}".toInteger()), it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
                     }

                    cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
                }
            } 
        }
    }
    
    sendEvent(name:"needUpdate", value: isUpdateNeeded, displayed:false, isStateChange: true)
    return cmds
}

/**
* Convert 1 and 2 bytes values to integer
*/
def cmd2Integer(array) { 
try {
switch(array.size()) {
	case 1:
		array[0]
    break
	case 2:
    	((array[0] & 0xFF) << 8) | (array[1] & 0xFF)
    break
    case 3:
    	((array[0] & 0xFF) << 16) | ((array[1] & 0xFF) << 8) | (array[2] & 0xFF)
    break
	case 4:
    	((array[0] & 0xFF) << 24) | ((array[1] & 0xFF) << 16) | ((array[2] & 0xFF) << 8) | (array[3] & 0xFF)
	break
}
}catch (e) {
log.debug "Error: cmd2Integer $e"
}
}

def integer2Cmd(value, size) {
    try{
	switch(size) {
	case 1:
		[value]
    break
	case 2:
    	def short value1   = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        [value2, value1]
    break
    case 3:
    	def short value1   = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        def short value3 = (value >> 16) & 0xFF
        [value3, value2, value1]
    break
	case 4:
    	def short value1 = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        def short value3 = (value >> 16) & 0xFF
        def short value4 = (value >> 24) & 0xFF
		[value4, value3, value2, value1]
	break
	}
    } catch (e) {
        log.debug "Error: integer2Cmd $e Value: $value"
    }
}

private command(physicalgraph.zwave.Command cmd) {
    
	if (state.sec && cmd.toString() /*!= "WakeUpIntervalGet()"*/) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay=1000) {
	delayBetween(commands.collect{ command(it) }, delay)
}

def generate_preferences(configuration_model)
{
    def configuration = parseXml(configuration_model)
   
    configuration.Value.each
    {
        switch(it.@type)
        {   
            case ["byte","short","four"]:
                input "${it.@index}", "number",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "list":
                def items = []
                it.Item.each { items << ["${it.@value}":"${it.@label}"] }
                input "${it.@index}", "enum",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}",
                    options: items
            break
            case "decimal":
               input "${it.@index}", "decimal",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "boolean":
               input "${it.@index}", "boolean",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
        }  
    }
}

private getBatteryRuntime() {
   def currentmillis = now() - state.batteryRuntimeStart
   def days=0
   def hours=0
   def mins=0
   def secs=0
   secs = (currentmillis/1000).toInteger() 
   mins=(secs/60).toInteger() 
   hours=(mins/60).toInteger() 
   days=(hours/24).toInteger() 
   secs=(secs-(mins*60)).toString().padLeft(2, '0') 
   mins=(mins-(hours*60)).toString().padLeft(2, '0') 
   hours=(hours-(days*24)).toString().padLeft(2, '0') 
 

  if (days>0) { 
      return "$days days and $hours:$mins:$secs"
  } else {
      return "$hours:$mins:$secs"
  }
}

private getRoundedInterval(number) {
    double tempDouble = (number / 60)
    if (tempDouble == tempDouble.round())
       return (tempDouble * 60).toInteger()
    else 
       return ((tempDouble.round() + 1) * 60).toInteger()
}

private getAdjustedWake(){
    def wakeValue
    wakeValue = 3600
    return wakeValue.toInteger()
}

private getAdjustedTemp(value) {
    
    value = Math.round((value as Double) * 100) / 100

	if (settings."201") {
	   return value =  value + Math.round(settings."201" * 100) /100
	} else {
       return value
    }
    
}

private getAdjustedLuminance(value) {
    
    value = Math.round((value as Double) * 100) / 100

	if (settings."203") {
	   return value =  value + Math.round(settings."203" * 100) /100
	} else {
       return value
    }
    
}

def resetBatteryRuntime() {
    if (state.lastReset != null && now() - state.lastReset < 5000) {
        logging("Reset Double Press")
        state.batteryRuntimeStart = now()
        //updateStatus()
    }
    state.lastReset = now()
}

private updateStatus(){
   def result = []
   if(state.batteryRuntimeStart != null){
        sendEvent(name:"batteryRuntime", value:getBatteryRuntime(), displayed:false)
        if (device.currentValue('currentFirmware') != null){
            sendEvent(name:"statusText2", value: "Firmware: v${device.currentValue('currentFirmware')} - Battery: ${getBatteryRuntime()} Double tap to reset", displayed:false)
        } else {
            sendEvent(name:"statusText2", value: "Battery: ${getBatteryRuntime()} Double tap to reset", displayed:false)
        }
    } else {
        state.batteryRuntimeStart = now()
    }

    String statusText = ""
    if(device.currentValue('illuminance') != null)
        statusText = statusText + "LUX ${device.currentValue('illuminance')} - "

    if (statusText != ""){
        statusText = statusText.substring(0, statusText.length() - 2)
        sendEvent(name:"statusText", value: statusText, displayed:false)
    }
}

private def logging(message) {
    if (state.enableDebugging == null || state.enableDebugging == "true") log.debug "$message"
}

def configuration_model()
{
'''
<configuration>
	<Value type="short" byteSize="2" index="1" label="Motion Retrigger Time" min="0" max="32767" value="30" setting_type="zwave" fw="2.07">
		<Help>
Delay time before PIR sensor can be triggered again to reset motion timeout counter.
Range: 0~32767.
Default: 30
Note:
Value = 0 will disable PIR sensor from triggering until motion timeout has finished.
		</Help>
	</Value>

	<Value type="short" byteSize="2" index="2" label="Motion Clear Time" min="1" max="32767" value="240" setting_type="zwave" fw="2.07">
		<Help>
Time in seconds to clear motion event after a motion event detected.
Range: 1~32767.
Default: 240
Note:
The device will send a clear event report to controller and send BASIC_SET = 0x00 to nodes associated in group 2. Unit: Second.
		</Help>
	</Value>

	<Value type="byte" byteSize="1" index="3" label="Motion Sensitivity" min="0" max="11" value="11" setting_type="zwave" fw="2.07">
		<Help>
Configures sensitivity of the PIR motion detector.
Range: 0~11.
Default: 11
Note:
0 – PIR sensor disabled
1 – Lowest sensitivity
11 – Highest sensitivity
		</Help>
	</Value>
	
	<Value type="list" index="4" label="Binary Sensor Report Enable" min="0" max="1" value="0" byteSize="1" setting_type="zwave" fw="2.07">
		<Help>
Which command should be sent when the motion sensor is triggered
Default: Basic Set
		</Help>
        <Item label="Disable sensor binary report" value="0" />
        <Item label="Enable sensor binary report" value="1" />
	</Value>

	<Value type="list" index="5" label="Basic Set to Associated Nodes Enable" min="0" max="3" value="0" byteSize="1" setting_type="zwave" fw="2.07">
		<Help>
This parameter enables or disables sending BASIC_SET command to nodes associated in group 2 and group 3.
		</Help>
        <Item label="Disable All Group Basic Set Command" value="0" />
        <Item label="Enabled Group 2 Basic Set Command" value="1" />
		<Item label="Enabled Group 3 Basic Set Command" value="2" />
		<Item label="Enabled Group 2 and 3 Basic Set Command" value="3" />
	</Value>

	<Value type="list" index="6" label="Basic Set Value Settings" min="0" max="5" value="0" byteSize="1" setting_type="zwave" fw="2.07">
		<Help>
This controls what gets sent as BASIC_SET command to nodes associated with the above parameter when enabled.
		</Help>
        <Item label="Send BASIC_SET = 0xFF when triggered 0x00 when cleared (typical)" value="0" />
        <Item label="Send BASIC_SET = 0x00 when triggered 0xFF when cleared (reverse)" value="1" />
		<Item label="Send BASIC_SET = 0xFF when triggered ONLY (no clear)" value="2" />
		<Item label="Send BASIC_SET = 0x00 when triggered ONLY (no clear)" value="3" />
		<Item label="Send BASIC_SET = 0x00 when cleared ONLY (no trigger)" value="4" />
		<Item label="Send BASIC_SET = 0xFF when cleared ONLY (no trigger)" value="5" />
	</Value>
	
	<Value type="short" byteSize="2" index="7" label="Temperature Alarm Value" min="-400" max="1185" value="750" setting_type="zwave" fw="2.07">
		<Help>
Threshold value for temperature alarm trigger.
Range: -400~1185.
Default: 750
Note:
When the current ambient temperature value is larger than this configuration value, device will send a BASIC_SET = 0xFF to nodes associated in group 3.
If current temperature value is less than this value, device will send a BASIC_SET = 0x00 to nodes associated in group 3.
Value = [Value] × 0.1(Celsius / Fahrenheit)
		</Help>
	</Value>

	<Value type="list" index="10" label="LED indicator disable" min="0" max="1" value="1" byteSize="1" setting_type="zwave" fw="2.07">
		<Help>
Sets whether the LED blinks on status reports or not.
Default: ENABLED
		</Help>
        <Item label="Disable LED flashes" value="0" />
        <Item label="Enable LED flashes" value="1" />
	</Value>

	<Value type="list" index="20" label="Temperature Scale" min="0" max="1" value="1" byteSize="1" setting_type="zwave" fw="2.07">
		<Help>
Choose which temperature scale to report in.
Default: Fahrenheight
		</Help>
        <Item label="Celsius" value="0" />
        <Item label="Fahrenheit" value="1" />
	</Value>

	<Value type="short" byteSize="2" index="21" label="Temperature reporting threshold" min="0" max="250" value="20" setting_type="zwave" fw="2.07">
		<Help>
Change threshold value for change in temperature to induce an automatic report.			
Range: 0~ 250
Default: 20
Note:
Value = [Value] × 0.1(Celsius / Fahrenheit)
Setting of value 20 can be a change of -2.0 or +2.0 degrees.
		</Help>
	</Value>

	<Value type="short" byteSize="2" index="22" label="Illuminance reporting threshold" min="0" max="10000" value="100" setting_type="zwave" fw="2.07">
		<Help>
Change threshold value for change in light sensor to induce an automatic report
Scale: Lux
Range: 0~10000
Default: 100
		</Help>
	</Value>
	
	<Value type="short" byteSize="2" index="23" label="Temperature Sensor Report Interval" min="1" max="32767" value="3600" setting_type="zwave" fw="2.07">
		<Help>
Number of seconds between temperature reports. Longer values lead to longer battery life.
Range: 0~10000
Default: 3600
		</Help>
	</Value>

	<Value type="short" byteSize="2" index="24" label="Illuminance Sensor Report Interval" min="1" max="32767" value="3600" setting_type="zwave" fw="2.07">
		<Help>
Number of seconds between illuminance reports. Longer values lead to longer battery life.
Range: 0~10000
Default: 3600
		</Help>
	</Value>

	<Value type="short" byteSize="2" index="30" label="Temperature offset" min="-200" max="200" value="0" setting_type="zwave" fw="2.07">
		<Help>
Temperature Offset Value = [Value] * 0.1(Celsius / Fahrenheit)
Range: -200 ~ 200
Default: 0
		</Help>
	</Value>

	<Value type="short" byteSize="2" index="31" label="Illuminance offset" min="-1000" max="1000" value="0" setting_type="zwave" fw="2.07">
		<Help>
Adjusts LUX reading of illuminance sensor by this amount.
Range: -1000 ~ 1000
Default: 0
		</Help>
	</Value>
	
    <Value type="short" byteSize="2" index="100" label="Light Sensor Calibrated Coefficient" min="1" max="32767" value="1024" setting_type="zwave" fw="2.07">
		<Help>
Defines calibrated light scaled for illuminance sensor reading.
Range: 1 ~ 32767
Default: 1024
		</Help>
	</Value>
    
	<Value type="boolean" index="enableDebugging" label="Enable Debug Logging?" value="true" setting_type="preference" fw="2.07">
		<Help>
		</Help>
	</Value>
</configuration>
'''
}
