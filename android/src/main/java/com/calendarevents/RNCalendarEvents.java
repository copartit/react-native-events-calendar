package com.calendarevents;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.Manifest;
import android.net.Uri;
import android.provider.CalendarContract;
import androidx.core.content.ContextCompat;
import android.database.Cursor;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.TimeZone;
import android.util.Log;

public class RNCalendarEvents extends ReactContextBaseJavaModule implements PermissionListener {

    private static int PERMISSION_REQUEST_CODE = 37;
    private final ReactContext reactContext;
    private static final String RNC_PREFS = "REACT_NATIVE_CALENDAR_PREFERENCES";
    private static final HashMap<Integer, Promise> permissionsPromises = new HashMap<>();

    public RNCalendarEvents(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNCalendarEvents";
    }

    //region Calendar Permissions
    private void requestCalendarPermission(boolean readOnly, final Promise promise)
    {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist");
            return;
        }
        if (!(currentActivity instanceof PermissionAwareActivity)) {
            promise.reject("E_ACTIVITY_NOT_PERMISSION_AWARE", "Activity does not implement the PermissionAwareActivity interface");
            return;
        }
        PermissionAwareActivity activity = (PermissionAwareActivity)currentActivity;
        PERMISSION_REQUEST_CODE++;
        permissionsPromises.put(PERMISSION_REQUEST_CODE, promise);
        String[] permissions = new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR};
        if (readOnly == true) {
            permissions = new String[]{Manifest.permission.READ_CALENDAR};
        }
        activity.requestPermissions(permissions, PERMISSION_REQUEST_CODE, this);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (permissionsPromises.containsKey(requestCode)) {

            // If request is cancelled, the result arrays are empty.
            Promise permissionsPromise = permissionsPromises.get(requestCode);

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionsPromise.resolve("authorized");
            } else if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                permissionsPromise.resolve("denied");
            } else if (permissionsPromises.size() == 1) {
                permissionsPromise.reject("permissions - unknown error", grantResults.length > 0 ? String.valueOf(grantResults[0]) : "Request was cancelled");
            }
            permissionsPromises.remove(requestCode);
        }

        return permissionsPromises.size() == 0;
    }

    private boolean haveCalendarPermissions(boolean readOnly) {
        int writePermission = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_CALENDAR);
        int readPermission = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.READ_CALENDAR);

        if (readOnly) {
            return readPermission == PackageManager.PERMISSION_GRANTED;
        }

        return writePermission == PackageManager.PERMISSION_GRANTED &&
                readPermission == PackageManager.PERMISSION_GRANTED;
    }
    
    private boolean shouldShowRequestPermissionRationale(boolean readOnly) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            Log.w(this.getName(), "Activity doesn't exist");
            return false;
        }
        if (!(currentActivity instanceof PermissionAwareActivity)) {
            Log.w(this.getName(), "Activity does not implement the PermissionAwareActivity interface");
            return false;
        }

        PermissionAwareActivity activity = (PermissionAwareActivity)currentActivity;

        if (readOnly) {
            return activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR);
        }
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR);
    }

    //endregion

    private long addEvent(String title, ReadableMap details, ReadableMap options) throws ParseException {
        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        boolean skipTimezone = false;
        if(details.hasKey("skipAndroidTimezone") && details.getBoolean("skipAndroidTimezone")){
            skipTimezone = true;
        }
        if(!skipTimezone){
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        ContentResolver cr = reactContext.getContentResolver();
        ContentValues eventValues = new ContentValues();

        if (title != null) {
            eventValues.put(CalendarContract.Events.TITLE, title);
        }

        if (details.hasKey("description")) {
            eventValues.put(CalendarContract.Events.DESCRIPTION, details.getString("description"));
        }

        if (details.hasKey("location")) {
            eventValues.put(CalendarContract.Events.EVENT_LOCATION, details.getString("location"));
        }

        if (details.hasKey("startDate")) {
            Calendar startCal = Calendar.getInstance();
            ReadableType type = details.getType("startDate");

            try {
                if (type == ReadableType.String) {
                    startCal.setTime(sdf.parse(details.getString("startDate")));
                    eventValues.put(CalendarContract.Events.DTSTART, startCal.getTimeInMillis());
                } else if (type == ReadableType.Number) {
                    eventValues.put(CalendarContract.Events.DTSTART, (long)details.getDouble("startDate"));
                }
            } catch (ParseException e) {
                e.printStackTrace();
                throw e;
            }
        }

        if (details.hasKey("recurrence")) {
            String rule = createRecurrenceRule(details.getString("recurrence"), details.getString("occurrence"));
            if (rule != null) {
                eventValues.put(CalendarContract.Events.RRULE, rule);
                eventValues.put(CalendarContract.Events.DURATION, "PT1H");
            }
        } else {
            if (details.hasKey("endDate")) {
                Calendar endCal = Calendar.getInstance();
                ReadableType type = details.getType("endDate");

                try {
                    if (type == ReadableType.String) {
                        endCal.setTime(sdf.parse(details.getString("endDate")));
                        eventValues.put(CalendarContract.Events.DTEND, endCal.getTimeInMillis());
                    } else if (type == ReadableType.Number) {
                        eventValues.put(CalendarContract.Events.DTEND, (long)details.getDouble("endDate"));
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        }

        if (details.hasKey("allDay")) {
            eventValues.put(CalendarContract.Events.ALL_DAY, details.getBoolean("allDay") ? 1 : 0);
        }

        if (details.hasKey("timeZone")) {
            eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, details.getString("timeZone"));
        } else {
            eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        }

        if (details.hasKey("endTimeZone")) {
            eventValues.put(CalendarContract.Events.EVENT_END_TIMEZONE, details.getString("endTimeZone"));
        } else {
            eventValues.put(CalendarContract.Events.EVENT_END_TIMEZONE, TimeZone.getDefault().getID());
        }

        if (details.hasKey("alarms")) {
            eventValues.put(CalendarContract.Events.HAS_ALARM, true);
        }

        if (details.hasKey("availability")) {
            eventValues.put(CalendarContract.Events.AVAILABILITY, availabilityConstantMatchingString(details.getString("availability")));
        }

        WritableNativeMap calendar;
        int eventID = -1;
        eventValues.put(CalendarContract.Events.CALENDAR_ID, 1);
        Uri createEventUri = CalendarContract.Events.CONTENT_URI;
        Uri eventUri = cr.insert(createEventUri, eventValues);

        if (eventUri != null) {
            String rowId = eventUri.getLastPathSegment();
            if (rowId != null) {
                eventID = Integer.parseInt(rowId);
                return eventID;
            }
        }
        return eventID;
    }

    //region Availability
    private Integer availabilityConstantMatchingString(String string) throws IllegalArgumentException {
        if (string.equals("free")){
            return CalendarContract.Events.AVAILABILITY_FREE;
        }

        if (string.equals("tentative")){
            return CalendarContract.Events.AVAILABILITY_TENTATIVE;
        }

        return CalendarContract.Events.AVAILABILITY_BUSY;
    }
    //endregion

    //region Recurrence Rule
    private String createRecurrenceRule(String recurrence, Integer occurrence) {
        String rrule;

        if (recurrence.equals("daily")) {
            rrule=  "FREQ=DAILY";
        } else if (recurrence.equals("weekly")) {
            rrule = "FREQ=WEEKLY";
        }  else if (recurrence.equals("monthly")) {
            rrule = "FREQ=MONTHLY";
        } else if (recurrence.equals("yearly")) {
            rrule = "FREQ=YEARLY";
        } else {
            return null;
        }

        if (occurrence != null) {
            rrule += ";COUNT=" + occurrence;
        }

        return rrule;
    }
    //endregion

    private String getPermissionKey(boolean readOnly) {
        String permissionKey = "permissionRequested"; // default to previous key for read/write, backwards-compatible
        if (readOnly) {
            permissionKey = "permissionRequestedRead"; // new key for read-only permission requests
        }
        return permissionKey;
    }

    //region React Native Methods
    @ReactMethod
    public void checkPermissions(boolean readOnly, Promise promise) {
        try {
            SharedPreferences sharedPreferences = reactContext.getSharedPreferences(RNC_PREFS, ReactContext.MODE_PRIVATE);
            boolean permissionRequested = sharedPreferences.getBoolean(getPermissionKey(readOnly), false);

            if (this.haveCalendarPermissions(readOnly)) {
                promise.resolve("authorized");
            } else if (!permissionRequested) {
                promise.resolve("undetermined");
            } else if (this.shouldShowRequestPermissionRationale(readOnly)) {
                promise.resolve("denied");
            } else {
                promise.resolve("restricted");
            }
        }
        catch(Throwable t) {
            Log.e("RNCalendarEvents error checking permissions", t.getMessage(), t);
            promise.reject("error checking permissions", t.getMessage(), t);
        }
    }

    @ReactMethod
    public void requestPermissions(boolean readOnly, Promise promise) {
        try {
            SharedPreferences sharedPreferences = reactContext.getSharedPreferences(RNC_PREFS, ReactContext.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getPermissionKey(readOnly), true);
            editor.apply();

            if (this.haveCalendarPermissions(readOnly)) {
                promise.resolve("authorized");
            } else {
                this.requestCalendarPermission(readOnly, promise);
            }
        }
        catch(Throwable t) {
            Log.e("RNCalendarEvents error requesting permissions", t.getMessage(), t);
            promise.reject("error requesting permissions", t.getMessage(), t);
        }
    }

    @ReactMethod
    public void saveEvent(final String title, final ReadableMap details, final ReadableMap options, final Promise promise) {
        if (this.haveCalendarPermissions(false)) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        long eventId;
                        try {
                            eventId = addEvent(title, details, options);
                            if (eventId > -1) {
                                promise.resolve(Long.toString(eventId));
                            } else {
                                promise.reject("add event error", "Unable to save event");
                            }
                        } catch (Throwable t) {
                            Log.e("RNCalendarEvents add event error", t.getMessage(), t);
                            promise.reject("add event error", t.getMessage(), t);
                        }
                    }
                });
                thread.start();
            } catch (Throwable t) {
                promise.reject("add event error", t.getMessage(), t);
            }
        } else {
            promise.reject("add event error", "you don't have permissions to add an event to the users calendar");
        }
    }
    //endregion
}
