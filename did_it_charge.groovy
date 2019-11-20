import groovy.transform.Field

@Field static List dayOptions = [
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
]

definition(
    name: "Did it charge",
    namespace: "asj",
    author: "asj",
    description: "Send a notification if a device did not cahrge",
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
            input name: "powerMonitor", type: "capability.energyMeter", title: "Device to Monitor", multiple: false, required: true, submitOnChange: true
            input name: "checkDays",  type: "enum", title: "Days of the Week", options: dayOptions, submitOnChange: true, width: 6, multiple: true
            input name: "checkTime",  type: "time", title: "Time to check", required: true, submitOnChange: true
            input name: "chargeThreshold", type: "decimal", title: "Charge Threshold", required: true, submitOnChange: true
            input name: "notifyList", type: "capability.notification", title: "Send Notifications To", multiple: true, required: true, submitOnChange: true

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

    def schDate = toDateTime(settings.checkTime)
    def schHour = schDate.format("HH")
    def schMinute = schDate.format("mm")
    def schWeekday = schDate.format("E")

    state.weekday = schWeekday

    schedule("15 $schMinute $schHour * * ?", scheduleTime)
    //scheduleTime()
}

def scheduleTime() {
    if (logEnable) log.debug "${app.label}: checkTime(): Validating power"

    def date = new Date()
    def today_weekday = date.format("E")
    def is_day = false
    settings.checkDays.each { day ->
        if (day.contains(today_weekday)) is_day = true
    }

    if (is_day) {
        if (settings.powerMonitor.currentEnergy < settings.chargeThreshold) {
            if (logEnable) log.debug "${app.label}: checkTime(): Weekday is $today_weekday: $is_day ${settings.powerMonitor.currentEnergy}"
            settings.notifyList.each { device -> 
                device.deviceNotification("${app.label} did not charge")
            }
        }
    }

    settings.powerMonitor.resetEnergy()

}


