#import "RNCalendarEvents.h"
#import <React/RCTConvert.h>
#import <React/RCTUtils.h>
#import <EventKit/EventKit.h>

@interface RNCalendarEvents ()
@property (nonatomic, readonly) EKEventStore *eventStore;
@end

static NSString *const _id = @"id";
static NSString *const _calendarId = @"calendarId";
static NSString *const _title = @"title";
static NSString *const _location = @"location";
static NSString *const _startDate = @"startDate";
static NSString *const _endDate = @"endDate";
static NSString *const _allDay = @"allDay";
static NSString *const _notes = @"notes";
static NSString *const _url = @"url";
static NSString *const _alarms = @"alarms";
static NSString *const _recurrence = @"recurrence";
static NSString *const _isDetached = @"isDetached";
static NSString *const _availability = @"availability";
static NSString *const _attendees = @"attendees";
static NSString *const _timeZone = @"timeZone";
static NSString *const _occurrence = @"occurrence";

dispatch_queue_t serialQueue;

@implementation RNCalendarEvents

- (NSError *)exceptionToError:(NSException *)exception {
    NSMutableDictionary * info = [NSMutableDictionary dictionary];
    [info setValue:exception.name forKey:@"ExceptionName"];
    [info setValue:exception.reason forKey:@"ExceptionReason"];
    [info setValue:exception.callStackReturnAddresses forKey:@"ExceptionCallStackReturnAddresses"];
    [info setValue:exception.callStackSymbols forKey:@"ExceptionCallStackSymbols"];
    [info setValue:exception.userInfo forKey:@"ExceptionUserInfo"];

    NSError *error = [[NSError alloc]
                      initWithDomain:@"RNCalendarEvents"
                      code:-1
                      userInfo:info
                      ];
    return error;
}

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE()

#pragma mark -
#pragma mark Event Store Initialize

- (instancetype)init {
    self = [super init];
    if (self) {
        _eventStore = [[EKEventStore alloc] init];
        serialQueue = dispatch_queue_create("rncalendarevents.queue", DISPATCH_QUEUE_SERIAL);
    }
    return self;
}

#pragma mark -
#pragma mark Event Store Authorization

+ (BOOL)requiresMainQueueSetup
{
    return NO;
}

- (BOOL)isCalendarAccessGranted
{
    EKAuthorizationStatus status = [EKEventStore authorizationStatusForEntityType:EKEntityTypeEvent];

    return status == EKAuthorizationStatusAuthorized;
}

#pragma mark -
#pragma mark Event Store Accessors

- (NSDictionary *)buildAndSaveEvent:(NSDictionary *)details options:(NSDictionary *)options
{
    EKEvent *calendarEvent = nil;
    NSString *calendarId = [RCTConvert NSString:details[_calendarId]];
    NSString *eventId = [RCTConvert NSString:details[_id]];
    NSString *title = [RCTConvert NSString:details[_title]];
    NSString *location = [RCTConvert NSString:details[_location]];
    NSDate *startDate = [RCTConvert NSDate:details[_startDate]];
    NSDate *endDate = [RCTConvert NSDate:details[_endDate]];
    NSNumber *allDay = [RCTConvert NSNumber:details[_allDay]];
    NSString *notes = [RCTConvert NSString:details[_notes]];
    NSString *url = [RCTConvert NSString:details[_url]];
    NSArray *alarms = [RCTConvert NSArray:details[_alarms]];
    NSString *recurrence = [RCTConvert NSString:details[_recurrence]];
    NSString *availability = [RCTConvert NSString:details[_availability]];
    NSString *timeZone = [RCTConvert NSString:details[_timeZone]];
    NSInteger occurrence = [RCTConvert NSString:details[_occurrence]];

    if (eventId) {
        Boolean futureEvents = [RCTConvert BOOL:options[@"futureEvents"]];
        NSDate *exceptionDate = [RCTConvert NSDate:options[@"exceptionDate"]];

        if(exceptionDate) {
            NSPredicate *predicate = [self.eventStore predicateForEventsWithStartDate:exceptionDate
                                                                              endDate:endDate
                                                                            calendars:nil];
            NSArray *calendarEvents = [self.eventStore eventsMatchingPredicate:predicate];

            for (EKEvent *event in calendarEvents) {
                if ([event.calendarItemIdentifier isEqualToString:eventId] && [event.startDate isEqualToDate:exceptionDate]) {
                    calendarEvent = event;
                    break;
                }
            }
        }
        else {
            calendarEvent = (EKEvent *)[self.eventStore calendarItemWithIdentifier:eventId];
        }
    } else {
        calendarEvent = [EKEvent eventWithEventStore:self.eventStore];
        calendarEvent.calendar = [self.eventStore defaultCalendarForNewEvents];
        calendarEvent.timeZone = [NSTimeZone defaultTimeZone];

        if (calendarId) {
            EKCalendar *calendar = [self.eventStore calendarWithIdentifier:calendarId];

            if (calendar) {
                calendarEvent.calendar = calendar;
            }
        }
    }

    if (timeZone) {
      calendarEvent.timeZone = [NSTimeZone timeZoneWithName:timeZone];
    }

    if (title) {
        calendarEvent.title = title;
    }

    if (location) {
        calendarEvent.location = location;
    }

    if (startDate) {
        calendarEvent.startDate = startDate;
    }

    if (allDay) {
        calendarEvent.allDay = [allDay boolValue];
    }

    if (notes) {
        calendarEvent.notes = notes;
    }

    if (recurrence) {
        EKRecurrenceRule *rule = [self createRecurrenceRule:recurrence occurrence:occurrence];
        if (rule) {
            calendarEvent.recurrenceRules = [NSArray arrayWithObject:rule];
        }
    } else {
        if (endDate) {
            calendarEvent.endDate = endDate;
        }
    }

    if (availability) {
        calendarEvent.availability = [self availablilityConstantMatchingString:availability];
    }

    if (![url isKindOfClass: [NSNull class]]) {
        NSURL *URL = [NSURL URLWithString:[url stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]]];
        if (URL) {
            calendarEvent.URL = URL;
        }
    }

    if ([details objectForKey:@"structuredLocation"] && [[details objectForKey:@"structuredLocation"] count]) {
        NSDictionary *locationOptions = [details valueForKey:@"structuredLocation"];
        NSDictionary *geo = [locationOptions valueForKey:@"coords"];
        CLLocation *geoLocation = [[CLLocation alloc] initWithLatitude:[[geo valueForKey:@"latitude"] doubleValue]
                                                             longitude:[[geo valueForKey:@"longitude"] doubleValue]];
        
        calendarEvent.structuredLocation = [EKStructuredLocation locationWithTitle:[locationOptions valueForKey:@"title"]];
        calendarEvent.structuredLocation.geoLocation = geoLocation;
        calendarEvent.structuredLocation.radius = [[locationOptions valueForKey:@"radius"] doubleValue];
    }
    
    return [self saveEvent:calendarEvent options:options];
}

- (NSDictionary *)saveEvent:(EKEvent *)calendarEvent options:(NSDictionary *)options
{
    NSMutableDictionary *response = [NSMutableDictionary dictionaryWithDictionary:@{@"success": [NSNull null], @"error": [NSNull null]}];
    NSDate *exceptionDate = [RCTConvert NSDate:options[@"exceptionDate"]];
    EKSpan eventSpan = EKSpanFutureEvents;

    if (exceptionDate) {
        calendarEvent.startDate = exceptionDate;
        eventSpan = EKSpanThisEvent;
    }

    NSError *error = nil;
    BOOL success = [self.eventStore saveEvent:calendarEvent span:eventSpan commit:YES error:&error];

    if (!success) {
        [response setValue:[error.userInfo valueForKey:@"NSLocalizedDescription"] forKey:@"error"];
    } else {
        [response setValue:calendarEvent.calendarItemIdentifier forKey:@"success"];
    }
    return [response copy];
}

#pragma mark -
#pragma mark RecurrenceRules

-(EKRecurrenceFrequency)frequencyMatchingName:(NSString *)name
{
    EKRecurrenceFrequency recurrence = nil;

    if ([name isEqualToString:@"weekly"]) {
        recurrence = EKRecurrenceFrequencyWeekly;
    } else if ([name isEqualToString:@"monthly"]) {
        recurrence = EKRecurrenceFrequencyMonthly;
    } else if ([name isEqualToString:@"yearly"]) {
        recurrence = EKRecurrenceFrequencyYearly;
    } else if ([name isEqualToString:@"daily"]) {
        recurrence = EKRecurrenceFrequencyDaily;
    }
    return recurrence;
}

-(EKRecurrenceRule *)createRecurrenceRule:(NSString *)frequency occurrence:(NSInteger)occurrence
{
    EKRecurrenceRule *rule = nil;
    EKRecurrenceEnd *recurrenceEnd = nil;
    NSInteger recurrenceInterval = 1;
    NSArray *validFrequencyTypes = @[@"daily", @"weekly", @"monthly", @"yearly"];

    if (frequency && [validFrequencyTypes containsObject:frequency]) {

        if (occurrence && occurrence > 0) {
            recurrenceEnd = [EKRecurrenceEnd recurrenceEndWithOccurrenceCount:occurrence];
        }

        rule = [[EKRecurrenceRule alloc] initRecurrenceWithFrequency:[self frequencyMatchingName:frequency]
                                                                interval:1
                                                                    daysOfTheWeek:nil
                                                                    daysOfTheMonth:nil
                                                                    monthsOfTheYear:nil
                                                                    weeksOfTheYear:nil
                                                                    daysOfTheYear:nil
                                                                    setPositions:nil
                                                                    end:recurrenceEnd];
    }
    return rule;
}

- (EKEventAvailability)availablilityConstantMatchingString:(NSString *)string
{
    if([string isEqualToString:@"busy"]) {
        return EKEventAvailabilityBusy;
    }

    if([string isEqualToString:@"free"]) {
        return EKEventAvailabilityFree;
    }

    if([string isEqualToString:@"tentative"]) {
        return EKEventAvailabilityTentative;
    }

    if([string isEqualToString:@"unavailable"]) {
        return EKEventAvailabilityUnavailable;
    }

    return EKEventAvailabilityNotSupported;
}

#pragma mark -
#pragma mark RCT Exports

RCT_EXPORT_METHOD(checkPermissions:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    NSString *status;
    EKAuthorizationStatus authStatus = [EKEventStore authorizationStatusForEntityType:EKEntityTypeEvent];

    switch (authStatus) {
        case EKAuthorizationStatusDenied:
            status = @"denied";
            break;
        case EKAuthorizationStatusRestricted:
            status = @"restricted";
            break;
        case EKAuthorizationStatusAuthorized:
            status = @"authorized";
            break;
        default:
            status = @"undetermined";
            break;
    }

    resolve(status);
}

RCT_EXPORT_METHOD(requestPermissions:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    [self.eventStore requestAccessToEntityType:EKEntityTypeEvent completion:^(BOOL granted, NSError *error) {
        NSString *status = granted ? @"authorized" : @"denied";
        if (!error) {
            resolve(status);
        } else {
            reject(@"error", @"authorization request error", error);
        }
    }];
}

RCT_EXPORT_METHOD(saveEvent:(NSString *)title
                  settings:(NSDictionary *)settings
                  options:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    if (![self isCalendarAccessGranted]) {
        reject(@"error", @"unauthorized to access calendar", nil);
        return;
    }
    
    __weak RNCalendarEvents *weakSelf = self;
    dispatch_async(serialQueue, ^{
    @try {
    RNCalendarEvents *strongSelf = weakSelf;
    
    NSMutableDictionary *details = [NSMutableDictionary dictionaryWithDictionary:settings];
    [details setValue:title forKey:_title];

            NSDictionary *response = [strongSelf buildAndSaveEvent:details options:options];

            if ([response valueForKey:@"success"] != [NSNull null]) {
                resolve([response valueForKey:@"success"]);
            } else {
                reject(@"error", [response valueForKey:@"error"], nil);
            }
        }
        @catch (NSException *exception) {
            reject(@"error", @"saveEvent error", [self exceptionToError:exception]);
        }
    });
}

@end
