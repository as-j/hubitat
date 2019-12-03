import groovy.transform.Field

definition(
    name: "Charge Temp Limiter",
    namespace: "asj",
    author: "asj",
    description: "Slow down charging if a temp gets to warm",
    iconUrl: "",
    iconX2Url: "")

preferences {
    page(name: "mainPage")
}
def mainPage() {
    dynamicPage(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
        section {
            input "thisName", "text", title: "Name of this Application", submitOnChange: true
            if(thisName) app.updateLabel(thisName)
            input name: "switchControl", type: "capability.switch", title: "Switch to Control", multiple: false, required: true, submitOnChange: true
            input name: "tempMonitor", type: "capability.temperatureMeasurement", title: "Temperature to Monitor", multiple: false, required: true, submitOnChange: true
            input name: "cautionTemp", type: "decimal", title: "Slow Charging Down Over this Temp", required: true, submitOnChange: true
            input name: "stopTemp", type: "decimal", title: "STOP Charging Down Over this Temp", required: true, submitOnChange: true
            input name: "cycleMins", type: "decimal", title: "Minutes to Cycle On/Off in Caution", required: true, submitOnChange: true

        }
        section("Debug Settings") {
           //standard logging options
           input name: "logNotifier", type: "capability.notification", title: "Log Events to", multiple: false, required: false, submitOnChange: true
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

    if (!state.chargeState) state.chargeState = "ok"
    if (!state.tempState) state.tempState = "ok"
    if (!state.startAt) state.startAt = now()
    if (!state.stopAt) state.stopAt = now()
    if (!state.tempUpdateAt) state.tempUpdateAt = now()

    subscribe(tempMonitor, "temperature", tempChanged)
    subscribe(switchControl, "switch", switchChanged)
    tempUpdate(tempMonitor.currentTemperature)
    schedule("13 */5 * * * ?", chargingStateUpdate)
    if (logEnable) runIn(1800,logsOff) 
}

def logsOff() {
    //logEnable = false
}

def tempChanged(evt) {
    if (logEnable) log.debug "tempChanged(): $evt.displayName($evt.name) $evt.value"
    state.tempUpdateAt = now()
    tempUpdate(Double.parseDouble(evt.value))
}

def tempUpdate(temp) {
    state.lastTemp = temp
    if (temp > stopTemp) {
        state.tempState = "stop"
    } else if(temp > cautionTemp) {
        state.tempState = "caution"
    } else {
        state.tempState = "ok"
    }
    if (logEnable) log.debug "tempUpdate(): Temp state is $temp/${state.tempState}"
    chargingStateUpdate()
}

def chargingStateUpdate() {

    //if ((switchControl.switch) == "on" && (switchControl.currentPower < 75)) {
    //    if (logEnable) log.debug "chargingStateUpdate(): switch power: ${switchControl.currentPower} is less than charging level, bypassing"
    //    return
    //}

    if (state.chargeState != state.tempState) {
        if (logEnable) log.debug "chargingStateUpdate(): change of state: ${state.chargeState} -> ${state.tempState}"
        logNotifier?.deviceNotification "chargingStateUpdate(): change of state: ${state.chargeState} -> ${state.tempState} temp: ${tempMonitor.currentTemperature}"
        if (state.tempState == "ok") gotoOk()
        if (state.tempState == "caution") gotoCaution()
        if (state.tempState == "stop") gotoStop()
        state.chargeState = state.tempState
    }
    
    if (state.chargeState == "ok") {
        startCharging()
    } else if(state.chargeState == "caution") {
        if (switchControl.currentSwitch == "on") {
            def onFor = (now() - state.startAt)/(60*1000)
            if (logEnable) log.debug "chargingStateUpdate(): on for: ${onFor}"
            logNotifier?.deviceNotification "chargingStateUpdate(): on for: ${onFor}/${cycleMins} temp: ${tempMonitor.currentTemperature}"
            if (onFor > cycleMins) {
                if (logEnable) log.debug "chargingStateUpdate(): cycling off after ${onFor}"
                stopCharging()
            }
        } else {
            def offFor = (now() - state.stopAt)/(60*1000)
            if (offFor > cycleMins) {
                if (logEnable) log.debug "chargingStateUpdate(): cycling on after ${offFor}"
                logNotifier?.deviceNotification "chargingStateUpdate(): off for: ${offFor} temp: ${tempMonitor.currentTemperature}"
                startCharging()
            }
        }
    } else if(State.chargeState == "stop") {
        if (logEnable) log.debug "chargingStateUpdate(): stopping due to over temp"
        logNotifier?.deviceNotification "chargingStateUpdate(): STOP temp: ${tempMonitor.currentTemperature}"
        stopCharging()
    }

    double lastTempUpdate = (now() - state.tempUpdateAt)/(60*1000)
    if (lastTempUpdate > 120) {
        // after 2 hours the sensor has left
        // been lost, whatever, goto ok and turn on the switch
        if (logEnable) log.debug "chargingStateUpdate(): haven't heard from temp sesnor in ${lastTempUpdate.round()} minutes, assuming ok"
        state.tempState = "ok"
        gotoOk()
    }
}

def gotoStop() {
    stopCharging()
    state.chargeState = "stop"
}

def gotoCaution() {
    state.chargeState = "caution"
}

def gotoOk() {
    startCharging()
    state.chargeState = "ok"
}

def startCharging() {
    switchControl.on()

}

def stopCharging() {
    switchControl.off()
}

def switchChanged(evt) {
    if (logEnable) log.debug "switchChanged(): $evt.displayName($evt.name) $evt.value"
    if (evt.value == "on")
        state.startAt = now()
    else
        state.stopAt = now()
}

