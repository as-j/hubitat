/*
 * 
 * 19/12/11: Basic testing
 */

definition(
    name: "Test Capabilities",
    namespace: "asj",
    author: "asj",
    description: "Test capability selection",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage")
    }
}
def mainPage() {
    dynamicPage(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {

        section("App Name") {
            input "thisName", "text", title: "Name of this Application", defaultValue: app.getName(), submitOnChange: true
            if(thisName) app.updateLabel(thisName)
        }
        section() {
            input "filterText", "text", title: "Find Devices Matching This Name", submitOnChange: true
            state.filteredList = [:]
            if (filterText) {
                allDevices.each { device -> 
                    if (device.name.contains(filterText) || device.label?.contains(filterText)) {
                        state.filteredList[device.id] = "${device.label ?: device.name}"
                    }
                }
            }
            input "devices", "enum", title: "Filtered Listed", options: state.filteredList, multiple: true, submitOnChange: true
            settings.devices.each { deviceId ->
                def o = getDeviceObj(deviceId)
                paragraph("$deviceId name: ${state.filteredList[deviceId]} obj: ${o}/${o.class}")
            }
        }
        section("All devices", hideable: true) {
            input name: "allDevices", type: "capability.*", title: "Devices to Filter", multiple: true, required: false, submitOnChange: true
        }
        section("Debug Settings") {
            //standard logging options
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        }
    }
}

def getDeviceObj(id) {
    def found
    settings.allDevices.each { device -> 
        if (device.getId() == id) {
            log.debug "Found at $device for $id with id: ${device.id}"
            found = device
        }
    }
    return found
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

}


