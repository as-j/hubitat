definition(
    name: "When On Turn Off",
    namespace: "asj",
    author: "asj",
    description: "Turn off a Device when a Switch turns On",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section() {
                input name: "turnOnSwitch", type: "capability.switch", title: "Switch Turns On", multiple: true, required: false
                input name: "turnOffTime", type: "number", title: "Turn Off After Milliseconds", require: true
                input name: "turnOnContactSensor", type: "capability.contactSensor", title: "Contact Opens Turn Off Immediately", multiple: true, required: false
                input name: "turnOffSwitch", type: "capability.switch", title: "Turn off this Switch", multiple: true, required: true
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
                input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
            }
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

    // Turn on devices
    settings.turnOnSwitch.each { device ->
        if (logEnable) log.debug "${app.label}: subscribe to swtich.on for $device"
        subscribe(device, "switch.on", turnOnEvent)
    }
    settings.turnOnContactSensor.each { device ->
        if (logEnable) log.debug "${app.label}: subscribe to contact.open for $device"
        subscribe(device, "contact.open", turnOffNow)
    }

}

def turnOnEvent(evt) {
    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value"
    state.startAt = now()
    if (settings.turnOffTime) {
        runInMillis(settings.turnOffTime, turnOff, [overwrite: false])
    } else {
        turnOff()
    }
}

def turnOffNow(evt) {
    if (logEnable) log.debug "turnOnNow: $evt.displayName($evt.name) $evt.value"
    turnOff()
}

def turnOff() {
    if (logEnable) log.debug "turnOff() took: ${now() - state.startAt} ms"

    settings.turnOffSwitch.each { device ->
        if (logEnable) log.debug "turnOff(): turning off $device"
        device.off()
    }
}


