definition(
    name: "Keep On When",
    namespace: "asj",
    author: "asj",
    description: "Turn on a switch when some condition is met for X time",
    iconUrl: "",
    iconX2Url: "")

preferences {
    page(name: "mainPage")
}
def mainPage() {
    dynamicPage(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
        section {
            input "thisName", "text", title: "Name of this Keep On Application Instance", submitOnChange: true
            if(thisName) app.updateLabel(thisName)
            input name: "keepOnSwitch", type: "capability.switch", title: "Switch To Turn on and Keep On", multiple: true, required: false, submitOnChange: true
            input name: "turnOnSwitch", type: "capability.switch", title: "Turned On and By This Switch Turning On and Kept On", multiple: true, required: false, submitOnChange: true
            input name: "turnOnPower", type: "capability.powerMeter", title: "Turned On When Power Exceeds and Kept On", multiple: true, required: false, submitOnChange: true
            paragraph "When Switch Turns on Keep on X Minutes:"
            turnOnSwitch.each { device ->
                input "switch_time_$device.id", "number", title: "$device", defaultValue: 10, submitOnChange: true, width: 6
            }
            turnOnPower.each { device ->
                paragraph "When $device exceeds power limit (cur: $device.currentPower) keep on for X minutes"
                input "power_num_$device.id", "number", title: "Number of Thresholds", defaultValue: 1, submitOnChange: true
                int num = (settings["power_num_$device.id"] ?: "1").toInteger()
                for (i in 1..num) {
                    input "power_time_${i}_$device.id", "number", title: "minutes $i", defaultValue: 10, submitOnChange: true, range: "*..*", width: 6
                    input "power_threshold_${i}_$device.id", "number", title: "power $i", defaultValue: 50, submitOnChange: true, width: 6
                }
            }
        }
        section("Debug Settings") {
           //standard logging options
           input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
        }
    }
}



/**
 *  installed()
 *
 *  Runs when the app is first installed.
 **/
def installed() {
    if (logEnable) log.debug "${app.label}: Installed with settings: ${settings}" 
    state.installedAt = now()
    updated()
}

/**
 *  uninstalled()
 *
 *  Runs when the app is uninstalled.
 **/
def uninstalled() {
    unschedule()
    unsubscribe()
    if (logEnable) log.debug "${app.label}: Uninstalled"
}

/**
 *  updated()
 * 
 *  Runs when app settings are changed.
 * 
 *  Updates device.state with input values and other hard-coded values.
 *  Refreshes scheduling and subscriptions.
 **/
def updated() {
    if (logEnable) log.debug "${app.label}: updated ${settings}"

    unsubscribe()
    unschedule()

    // Turn on devices
    settings.turnOnSwitch.each { device ->
        if (logEnable) log.debug "updated(): subscribe to switch.on for ${device}"
        subscribe(device, "switch.on", switchEvent)
    }

    settings.turnOnPower.each { device ->
        if (logEnable) log.debug "updated(): subscribe to power for ${device}"
        subscribe(device, "power", powerEvent)
    }
}

def keepOnFor(long onFor) {
    def prevOffAt = state.offAt ?: 0

    long newOffAt = now() + onFor*60*1000

    if (logEnable) log.debug "keepOnFor(): previous off at $prevOffAt new off at $newOffAt update: ${newOffAt > prevOffAt}"

    if (newOffAt > prevOffAt) {
        state.offAt = newOffAt

        def date = new Date()
        date.setTime(newOffAt)
        schedule(date, "turnOff")
        log.info "keepOn: new turn off $date ($onFor)"
    }

}

def switchEvent(evt) {
    def deviceId = evt.getDeviceId()
    if (logEnable) log.debug "switchEvent(): $evt.displayName/$deviceId($evt.name) $evt.value"

    state.switchOnAt = now()

    long onFor = settings["switch_time_$deviceId"]

    if (logEnable) log.debug "switchEvent(): switch on for: $onFor"

    turnOn()
    keepOnFor(onFor)
}

def powerEvent(evt) {
    def deviceId = evt.getDeviceId()
    if (logEnable) log.debug "powerEvent(): $evt.displayName/$deviceId($evt.name) $evt.value"
    int num = (settings["power_num_$deviceId"] ?: "1").toInteger()
    for (int i in 1..num) {
        def onFor = settings["power_time_${i}_$deviceId"]
        def threshold = settings["power_threshold_${i}_$deviceId"]
        def value = Double.parseDouble(evt.value) ?: 0
        if (logEnable) log.debug "powerEvent(): checking if power $value is above $threshold (${value >= threshold}) on for: $onFor"
        if (value >= threshold) {
            state.powerEventAt = now()
            turnOn()
            keepOnFor(onFor)
        }
    }
}

def turnOn() {
    if (logEnable) log.debug "turnOn() for ${settings.keepOnSwitch}"
    settings.keepOnSwitch.each { device ->
        if (device.currentSwitch != "on") {
            if (logEnable) log.debug "turnOn(): for $device ${device.currentSwitch} -> on"
            device.on()
        }
    }
}

def turnOff() {
    if (logEnable) log.debug "turnOff() for ${settings.keepOnSwitch}"
    settings.keepOnSwitch.each { device ->
        if (device.currentSwitch != "off") {
            if (logEnable) log.debug "turnOff(): for $device ${device.currentSwitch} -> off"
            device.off()
        }
    }
}

