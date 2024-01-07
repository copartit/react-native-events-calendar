import { NativeModules } from "react-native";

const RNCalendarEvents = NativeModules.RNCalendarEvents;

export default {
  async checkPermissions(readOnly = false) {
    return RNCalendarEvents.checkPermissions(readOnly);
  },
  async requestPermissions(readOnly = false) {
    return RNCalendarEvents.requestPermissions(readOnly);
  },
  async saveEvent(title, details, options = { sync: false }) {
    return RNCalendarEvents.saveEvent(title, details, options);
  },
};
