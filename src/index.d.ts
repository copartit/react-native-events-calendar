// Type definitions for react-native-calendar v1.7.0
// Typescript version: 3.0

type ISODateString = string;
export type AuthorizationStatus =
  | "denied"
  | "restricted"
  | "authorized"
  | "undetermined";
export type RecurrenceFrequency = "daily" | "weekly" | "monthly" | "yearly";

/** iOS ONLY - GeoFenced alarm location */
interface AlarmStructuredLocation {
  /** The title of the location. */
  title: string;
  /** A value indicating how a location-based alarm is triggered. */
  proximity: "enter" | "leave" | "none";
  /** A minimum distance from the core location that would trigger the calendar event's alarm. */
  radius: number;
  /** The geolocation coordinates, as an object with latitude and longitude properties. */
  coords: { latitude: number; longitude: number };
}

export interface Options {
  /** The start date of a recurring event's exception instance. Used for updating single event in a recurring series. */
  exceptionDate?: ISODateString;
  /** iOS ONLY - If true the update will span all future events. If false it only update the single instance. */
  futureEvents?: boolean;
  /** ANDROID ONLY - If true, can help avoid syncing issues */
  sync?: boolean;
}

interface Alarm<D = ISODateString | number> {
  /** When saving an event, if a Date is given, an alarm will be set with an absolute date. If a Number is given, an alarm will be set with a relative offset (in minutes) from the start date. When reading an event this will always be an ISO Date string */
  date: D;
  /** iOS ONLY - The location to trigger an alarm. */
  structuredLocation?: AlarmStructuredLocation;
}

interface CalendarEventBase {
  /** The start date of the calendar event in ISO format */
  startDate: ISODateString;
  /** The end date of the calendar event in ISO format. */
  endDate?: ISODateString;
  /** Unique id for the calendar where the event will be saved. Defaults to the device's default  calendar. */
  calendarId?: string;
  /** Indicates whether the event is an all-day event. */
  allDay?: boolean;
  /** The simple recurrence frequency of the calendar event. */
  recurrence?: RecurrenceFrequency;
  /** Number of event occurrences */
  occurrence?: number;
  /** The location associated with the calendar event. */
  location?: string;
  /** iOS ONLY - The location with coordinates. */
  structuredLocation?: AlarmStructuredLocation;
  /** iOS ONLY - Indicates whether an event is a detached instance of a repeating event. */
  isDetached?: boolean;
  /** iOS ONLY - The url associated with the calendar event. */
  url?: string;
  /** iOS ONLY - The notes associated with the calendar event. */
  notes?: string;
  /** ANDROID ONLY - The description associated with the calendar event. */
  description?: string;
  /** iOS ONLY - The time zone associated with the event */
  timeZone?: string;
}

export interface CalendarEventWritable extends CalendarEventBase {
  /** Unique id for the calendar event, used for updating existing events */
  id?: string;
  /** The alarms associated with the calendar event, as an array of alarm objects. */
  alarms?: Array<Alarm<ISODateString | number>>;
}

export default class ReactNativeCalendarEvents {
  /**
   * Get calendar authorization status.
   * @param readOnly - optional, default false, use true to check for calendar read-only vs calendar read/write. Android-specific, iOS is always read/write
   */
  static checkPermissions(readOnly?: boolean): Promise<AuthorizationStatus>;
  /**
   * Request calendar authorization. Authorization must be granted before accessing calendar events.
   * @param readOnly - optional, default false, use true to request for calendar read-only vs calendar read/write. Android-specific, iOS is always read/write
   */
  static requestPermissions(readOnly?: boolean): Promise<AuthorizationStatus>;
  /**
   * Creates or updates a calendar event. To update an event, the event id must be defined.
   * @param title - The title of the event
   * @param details - Event details
   * @param [options] - Options specific to the saved event.
   * @returns - Promise resolving to saved event's ID.
   */
  static saveEvent(
    title: string,
    details: CalendarEventWritable,
    options?: Options
  ): Promise<string>;
}
