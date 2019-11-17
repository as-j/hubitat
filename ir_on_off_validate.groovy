definition(
    name: "IR On/Off and Validate",
    namespace: "asj",
    author: "asj",
    description: "Turn On or Off then Validate Power Change",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section() {
                input name: "turnOnSwitch", type: "capability.switch", title: "Virtual Switch That Turns On/Off", multiple: true, required: false
                input name: "powerMonitor", type: "capability.powerMeter", title: "Monitor Power Switch", multiple: false, required: false
                input name: "onThreshold", type: "number", title: "On when power over this level", require: true
                input name: "offThreshold", type: "number", title: "Off when power under this level", require: true
                input name: "retryDelay", type: "number", title: "How Long to Wait", defaultValue: 30, require: true
                input name: "onURI", type: "string", title: "URL To Turn On", require: true
                input name: "offURI", type: "string", title: "URL To Turn Off", require: true
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
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
    if (logEnable) log.debug "${app.label}: subscribe to switch for ${settings.turnOnSwitch}"
    subscribe(settings.turnOnSwitch, "switch.on", turnOnEvent)
    subscribe(settings.turnOnSwitch, "switch.off", turnOffEvent)
    subscribe(settings.powerMonitor, "power", powerEvent)
}

def validateOn() {
    if (logEnable) log.debug "verifyOn(): last known power: ${state.currentPower} vs ${settings.onThreshold} age: ${now() - state.powerAt}"
    if (state.currentPower > settings.onThreshold) {
        if (logEnable) log.info "verifyOn: Power is verified ON!"
    } else {
        log.info "verifyOn: Power is still off! retries: ${state.retries}"
        if(state.retries--) runIn(settings.retryDelay, on)
    }
}

def turnOnEvent(evt) {
    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value"
    state.turnOnAt = now()
    on(4)
}

def verifyOff() {
    if (logEnable) log.debug "verifyOff(): last known power: ${state.currentPower} <? ${settings.offThreshold} age: ${now() - state.powerAt}"
    if (state.currentPower < settings.offThreshold) {
        if (logEnable) log.info "verifyOff: Power is OFF!"
    } else {
        log.info "verifyOn: Power is still on! retries: ${state.retries}"
        if(state.retries--) runIn(settings.retryDelay, off)
    }
}

def turnOffEvent(evt) {
    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value"
    state.turnOffAt = now()
    off(4)
}

def powerEvent(evt) {
    if (logEnable) log.debug "powerEvent(): $evt.displayName($evt.name) $evt.value"
    state.powerAt = now()
    state.currentPower = evt.value.toDouble()
}

def refreshPower() {
    if (logEnable) log.debug "refreshPower(): calling refresh, last known power: ${state.currentPower} age: ${state.powerAt ? now() - state.powerAt : 0}"
    settings.powerMonitor.refresh()
}

def on(tries = null) {
    if (tries)
        state.retries = tries
    if (logEnable) log.debug "on(): Sending on GET request to [${settings.onURI}]"

    if (!settings.onURI) {
        return;
    }
    
    try {
        httpGet(settings.onURI) { resp ->
            if (logEnable && resp.data) log.debug "on(): resp.data: ${resp.data} success: ${resp.success}"
        }
    } catch (Exception e) {
        log.warn "on() Call to on failed: ${e.message}"
    }
    unschedule()
    state.currentPower = null
    runIn((settings.retryDelay/2).toInteger(), refreshPower)
    runIn(settings.retryDelay, validateOn)
}

def off(tries = null) {
    if (tries)
        state.retries = tries
    if (logEnable) log.debug "Sending off GET request to [${settings.offURI}]"

    if (!settings.offURI) {
        return;
    }
    
    try {
        httpGet(settings.offURI) { resp ->
            if (logEnable && resp.data) log.debug "off: resp.data ${resp.data} success: ${resp.success}"
        }
    } catch (Exception e) {
        log.warn "Call to off failed: ${e.message}"
    }
    unschedule()
    state.currentPower = null
    runIn((settings.retryDelay/2).toInteger(), refreshPower)
    runIn(settings.retryDelay, verifyOff)


}




