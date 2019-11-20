definition(
    name: "Scheduled On/Off + Presense",
    namespace: "asj",
    author: "asj",
    description: "Turn On of Off at a Certain time, if someone is home",
    iconUrl: "",
    iconX2Url: "")

preferences {
    page(name: "mainPage")
}
def mainPage() {
    dynamicPage(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
        section {
            input "thisName", "text", title: "Name of Scheduler", submitOnChange: true
            if(thisName) app.updateLabel(thisName)
            input name: "turnOnSwitch", type: "capability.switch", title: "Switch To That Turn On/Off", multiple: true, required: false, submitOnChange: true
            input name: "turnOnTime",  type: "time", title: "Turn On Time", required: true, submitOnChange: true
            input name: "turnOffTime",  type: "time", title: "Turn Off Time", required: true, submitOnChange: true
            input name: "presenceSensor", type: "capability.presenceSensor", title: "Who must be home:", multiple: true, required: false, submitOnChange: true
            checkPresence()
            paragraph "Presence: Any One Home: ${state.anyoneHome}"
            paragraph "In Time Window: ${isTimeBetween()}"
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
    settings.presenceSensor.each { device ->
        if (logEnable) log.debug "updated(): subscribe to presence for ${device}"
        subscribe(device, "presence", presenceEvent)
    }
    checkPresence()
    
    def onDate = toDateTime(settings.turnOnTime)
    def onHour = onDate.format("HH")
    def onMinute = onDate.format("mm")

    def offDate = toDateTime(settings.turnOffTime)
    def offHour = offDate.format("HH")
    def offMinute = offDate.format("mm")

    schedule("10 $onMinute $onHour * * ?", turnOnTime)
    schedule("10 $offMinute $offHour * * ?", turnOffTime)
        
}

def isTimeBetween() {
    def onDate = toDateTime(settings.turnOnTime)
    def onHour = onDate.format("HH").toInteger()
    def onMinute = onDate.format("mm").toInteger()
    def onDec = onHour*100 + onMinute

    def offDate = toDateTime(settings.turnOffTime)
    def offHour = offDate.format("HH").toInteger()
    def offMinute = offDate.format("mm").toInteger()
    def offDec = offHour*100 + offMinute
    
    def currently = new Date()
    def nowHour = currently.format("HH").toInteger()
    def nowMinute = currently.format("mm").toInteger()
    def nowDec = nowHour*100 + nowMinute
    
    if (logEnable) log.debug "isBetween(): on: $onDec off: $offDec now: $nowDec"
    
    if (onDec <= offDec) { // Ex: 0900 -> 2100 
        if ((nowDec >= onDec) && (nowDec < offDec)) return true
    } else { // Ex: 2100 -> 0730
        if ((nowDec >= onDec) || (nowDec < offDec)) return true
    }
    return false
}

def checkPresence() {
    def anyHome = false
    def everyoneHome = true
    settings.presenceSensor.each { device ->
        if (device.currentPresence == "present") {
            anyHome = true
        } else {
            everyoneHome = false
        }
    }
    state.anyoneHome = anyHome
    state.everyOneHome = everyoneHome
    if (logEnable) log.debug "checkPresence(): presence check, allHome: $anyHome everyOnHome: $everyoneHome"
}

def checkInTime() {
    return 
}

def presenceEvent(evt) {
    if (logEnable) log.debug "presenceEvent(): $evt.displayName($evt.name) $evt.value"
    state.presenceChangeAt = now()
    
    if (evt.value == "present") {
        doTurnOn()
    } else {
        doTurnOff()
    }
}

def turnOnTime() {
    if (logEnable) log.debug "turnOnTime()"
    doTurnOn()
}

def turnOffTime() {
    if (logEnable) log.debug "turnOffTime()"
    doTurnOff()
}

def doTurnOn() {
    checkPresence()
    state.inTime = isTimeBetween()
    if (state.anyoneHome && state.inTime) {
        state.turnOnAt = now()
        settings.turnOnSwitch.each { device ->
            if (logEnable) log.debug "doTurnOn(): $device ${device.currentSwitch} -> on"
            device.on()
        }
    }
}

def doTurnOff() {
    if (logEnable) log.debug "doTurnOff(): devices: ${settings.turnOnSwitch}"
    checkPresence()
    state.inTime = isTimeBetween()
    if (!state.inTime || !state.anyoneHome) {
        state.turnOnAt = now()
        settings.turnOnSwitch.each { device ->
            if (logEnable) log.debug "doTurnOff(): $device ${device.currentSwitch} -> off"
            device.off()
        }
    }
}


