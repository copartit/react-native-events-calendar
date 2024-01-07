import { NativeModules } from "react-native";

const RNCalendarEvents = NativeModules.RNCalendarEvents;

export default {
  checkPermissions(readOnly = false) {
    // readOnly is ignored on iOS, the platform does not support it.
    return RNCalendarEvents.checkPermissions();
  },
  requestPermissions(readOnly = false) {
    // readOnly is ignored on iOS, the platform does not support it.
    return RNCalendarEvents.requestPermissions();
  },
  saveEvent(title, details, options = {}) {
    return RNCalendarEvents.saveEvent(title, details, options);
  },
};
