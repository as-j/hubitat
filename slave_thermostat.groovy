definition(
    name: "Slave Thermostat",
    namespace: "asj",
    author: "asj",
    description: "Turn on thermostat when another one is on",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section() {
                input name: "slaveThermostat", type: "capability.thermostat", title: "Slave Thermostat", multiple: false, required: false
                input name: "masterThermostat", type: "capability.thermostat", title: "Master Thermostat", multiple: false, required: false
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
    
    if (!state.masterTemp) state.masterTemp = masterThermostat.currentTemperature
    if (!state.masterOperatingState) state.masterOperatingState = masterThermostat.currentThermostatOperatingState
    if (!state.heatingSetPoint) state.heatingSetpoint = masterThermostat.currentHeatingSetpoint
    if (!state.idleLastAt) state.idleLastAt = now()
    
    // Turn on devices
    settings.slaveThermostat.each { device ->
        if (logEnable) log.debug "${app.label}: subscribe to swtich.on for $device"
        subscribe(device, "thermostatMode", slaveMode)
    }
    settings.masterThermostat.each { device ->
        if (logEnable) log.debug "${app.label}: subscribe to temp for $device"                
        subscribe(device, "temperature", masterTemp)
        subscribe(device, "thermostatOperatingState", masterOperatingState)
        subscribe(device, "heatingSetpoint", masterSetPoint)
    }
    setAirbnbMode()
}

def setAirbnbMode() {
    if (state.masterOperatingState == "pending heat") {
        state.heatingAt = state.heatingAt ?: now()
        def heatingFor = (now() - state.heatingAt)/(60*1000)
        def diffTemp = state.masterSetPoint - state.masterTemp
        if ((heatingFor < 60) || (diffTemp >= 1)) {
            log.info "setAirbnbMode: heat: heatingFor: $heatingFor diffTepmp: $diffTemp"
            settings.slaveThermostat.heat()
        } else {
            log.info "setAirbnbMode: off (stuck upstairs)"
            settings.slaveThermostat.off()
        }
        // Make sure we run now and then
        runIn(3600, setAirbnbMode)
    } else {
        log.info "setAirbnbMode: off (${state.masterOperatingState})"
        settings.slaveThermostat.off()
        state.heatingAt = null
        unschedule(setAirbnbMode)
    }
}

def masterTemp(evt) {
    if (logEnable) log.debug "masterTemp(): $evt.displayName($evt.name) $evt.value"
    state.masterTemp = evt.value
    setAirbnbMode()
}

def masterOperatingState(evt) {
    if (logEnable) log.debug "masterOperatingState(): $evt.displayName($evt.name) $evt.value"
    state.masterOperatingState = evt.value
    setAirbnbMode()
}

def masterSetPoint(evt) {
    if (logEnable) log.debug "masterSetPoint(): $evt.displayName($evt.name) $evt.value"
    state.masterSetPoint = evt.value
    setAirbnbMode()
}

def slaveMode(evt) {
    if (logEnable) log.debug "masterSetPoint(): $evt.displayName($evt.name) $evt.value"
    state.slaveMode = evt.value
    runIn(60, setAirbnbMode)
}




