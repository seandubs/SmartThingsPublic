/**
 *  Good Bye
 *
 *  Author: seandubs
 *  Date: 2020-12-13
 */
definition(
    name: "Unoccupied Mode Change",
    namespace: "seandubs",
    author: "Sean W",
    description: "Changes mode when motion ceases after a specific time",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
	section("When there is no motion on any of these sensors") {
		input "motionSensors", "capability.motionSensor", 
        title: "Motions sensors", 
        multiple: true,
        required: true
	}
	section("For this amount of time") {
        input "minutes", "number", title: "Minutes", required: true
	}
	section("Change to this mode") {
		input "newMode", "mode", title: "New Mode"
	}
	section( "Notifications" ) {
        input("recipients", "contact", title: "Send notifications to") {
            input "sendPushMessage", "enum", title: "Send a push notification?", options: ["Yes", "No"], required: false
            input "phoneNumber", "phone", title: "Send a Text Message?", required: false
        }
	}
}

def installed() {
	log.debug "Current mode = ${location.mode}"
    
	initialize()
}

def updated() {
	log.debug "Current mode = ${location.mode}"
    
	unsubscribe()
	initialize()
}

def initialize()
{
	subscribe(motionSensors, "motion.active", motionActiveHandler)
	subscribe(motionSensors, "motion.inactive", motionInactiveHandler)
	subscribe(location, modeChangeHandler)
	if (state.modeStartTime == null) {
		state.modeStartTime = 0
	}
}

def modeChangeHandler(evt) {
	state.modeStartTime = now()
}

def motionActiveHandler(evt)
{
	log.debug "Motion active"
}

def motionInactiveHandler(evt)
{
	// for backward compatibility
	if (state.modeStartTime == null) {
		subscribe(location, modeChangeHandler)
		state.modeStartTime = 0
	}

	if ( correctMode() ) {
		runIn(minutes * 60, scheduleCheck, [overwrite: false])
	}
}

def scheduleCheck()
{
	log.debug "scheduleCheck, currentMode = ${location.mode}, newMode = $newMode"
	
	if ( correctMode() ) {
		if ( allQuiet() ) {
			changeMode()
		}
	}
}

private changeMode() {
	log.debug "changeMode, location.mode = $location.mode, newMode = $newMode, location.modes = $location.modes"
	if (location.mode != newMode) {
		if (location.modes?.find{it.name == newMode}) {
			setLocationMode(newMode)
			send "${label} has changed the mode to '${newMode}'"
		}
		else {
			send "${label} tried to change to undefined mode '${newMode}'"
		}
	}
}

private correctMode() {
	if (location.mode != newMode) {
		true
	} else {
		log.debug "Location is already in the desired mode:  doing nothing"
		false
	}
}

private allQuiet() {
	def threshold = 1000 * 60 * minutes - 1000
	def states = motionSensors.collect { it.currentState("motion") ?: [:] }.sort { a, b -> b.dateCreated <=> a.dateCreated }
	if (states) {
		if (states.find { it.value == "active" }) {
			log.debug "Found active state"
			false
		} else {
			def sensor = states.first()
		    def elapsed = now() - sensor.rawDateCreated.time
			if (elapsed >= threshold) {
				log.debug "No active states, and enough time has passed"
				true
			} else {
				log.debug "No active states, but not enough time has passed"
				false
			}
		}
	} else {
		log.debug "No states to check for activity"
		true
	}
}

private send(msg) {
    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients)
    }
    else {
        if (sendPushMessage != "No") {
            log.debug("sending push message")
            sendPush(msg)
        }

        if (phoneNumber) {
            log.debug("sending text message")
            sendSms(phoneNumber, msg)
        }
    }

	log.debug msg
}

private getLabel() {
	app.label ?: "SmartThings"
}