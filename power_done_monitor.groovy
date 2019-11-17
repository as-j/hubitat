import groovy.transform.Field

@Field static List timeOptions = [
    "5 Seconds",
    "10 Seconds",
    "30 Seconds",
    "1 Minute",
    "2 Minute",
    "3 Minute",
    "5 Minutes",
    "15 Minutes",
    "30 Minutes",
    "1 Hour",
]



definition(
    name: "Done Power Monitor",
    namespace: "asj",
    author: "asj",
    description: "Send a notification when the power drops under a threshold",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section() {
                input name: "powerMonitor", type: "capability.powerMeter", title: "Monitor Power For", multiple: false, required: false
                input name: "onThreshold", type: "number", title: "On when power over this level", require: true
                input name: "offThreshold", type: "number", title: "Off when power under this level", require: true
                input name: "offDelay", type: "enum", title: "Time Power Must Stay Under Off Threshold", defaultValue: timeOptions[0], options: timeOptions
                input name: "notifyDevices", type: "capability.notification", title: "Send Notifcation to", multiple: false, required: false
                input name: "notifyContact", type: "capability.contactSensor", title: "Toggle Contact on Done", multiple: false, required: false
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

    subscribe(powerMonitor, "power", powerChanged)
    if (logEnable) runIn(1800,logsOff) 
}

def powerChanged(evt) {
    if (logEnable) log.debug "powerChanged(): $evt.displayName($evt.name) $evt.value"

    def value = Double.parseDouble(evt.value)

    if (value > onThreshold) {
        state.deviceOn = true
        state.overThreshold = true
        // Cancel timeouts
        unschedule(deviceOff)
    }

    if ((value < offThreshold) && (state.overThreshold)) {
        state.overThreshold = false
        runIn(getTimeValue(offDelay), deviceOff)
    }
}

def deviceOff() {
    if (logEnable) log.debug "${app.label}: deviceOff(): device has been under threshold for $offDelay $notityContact"
    state.deviceOn = false

    notifyContact.each { device ->
        if (logEnable) log.debug "${app.label}: send contact open to $device"
        device.open()
        runIn(5, contactOff)
    }

    notifyDevices.each { device ->
        if (logEnable) log.debug "${app.label}: send notification to $device"
        device.deviceNotification("${app.label}: ${powerMonitor} has finished")
    }
}

def contactOff() {
    notifyContact.each { device ->
        device.close()
    }
}

def logsOff() {
    log.warn "${app.label}: turning off debug logs"
    logEnable = false
}

private getTimeValue(report) {
    def reportValue
    def prMatch = (report =~ /(\d+) Seconds/)
    if (prMatch) reportValue = prMatch[0][1].toInteger()
    prMatch = (report =~ /(\d+) Minute/)
    if (prMatch) reportValue = prMatch[0][1].toInteger() * 60
    prMatch = (report =~ /(\d+) Hour/)
    if (prMatch) reportValue = prMatch[0][1].toInteger() * 3600

    return reportValue
}


