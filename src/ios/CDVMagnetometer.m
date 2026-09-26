#import "CDVMagnetometer.h"

// Error codes - matching DeviceOrientation plugin convention
static const int ERROR_NOT_AVAILABLE = 3;

@implementation CDVMagnetometer

- (void)pluginInitialize {
    self.motionManager = [[CMMotionManager alloc] init];
    self.locationManager = [[CLLocationManager alloc] init];
    self.locationManager.delegate = self;
    self.pendingHeadingCallbackIds = [NSMutableArray array];
    self.headingUpdatesRunning = NO;
    self.lastHeadingRequestTime = 0;
    self.currentAccuracy = 3; // Default to high
    self.calibrationNeeded = NO;
}

#pragma mark - Error Helpers

- (void)sendErrorWithCode:(int)code message:(NSString *)message callbackId:(NSString *)callbackId {
    NSDictionary *error = @{
        @"code": @(code),
        @"message": message
    };
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:error];
    [self.commandDelegate sendPluginResult:result callbackId:callbackId];
}

- (void)sendErrorWithCode:(int)code message:(NSString *)message callbackId:(NSString *)callbackId keepCallback:(BOOL)keepCallback {
    NSDictionary *error = @{
        @"code": @(code),
        @"message": message
    };
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:error];
    [result setKeepCallbackAsBool:keepCallback];
    [self.commandDelegate sendPluginResult:result callbackId:callbackId];
}

#pragma mark - Availability Check

- (void)isAvailable:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        BOOL available = self.motionManager.magnetometerAvailable;
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:available ? 1 : 0];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

#pragma mark - Single Reading

- (void)getReading:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        if (!self.motionManager.magnetometerAvailable) {
            [self sendErrorWithCode:ERROR_NOT_AVAILABLE message:@"Magnetometer not available" callbackId:command.callbackId];
            return;
        }

        [self.motionManager startMagnetometerUpdates];

        // Wait briefly for sensor to stabilize
        [NSThread sleepForTimeInterval:0.1];

        CMMagnetometerData *data = self.motionManager.magnetometerData;
        [self.motionManager stopMagnetometerUpdates];

        if (data) {
            double x = data.magneticField.x;
            double y = data.magneticField.y;
            double z = data.magneticField.z;
            double magnitude = sqrt(x*x + y*y + z*z);

            NSDictionary *reading = @{
                @"x": @(x),
                @"y": @(y),
                @"z": @(z),
                @"magnitude": @(magnitude),
                @"timestamp": @([[NSDate date] timeIntervalSince1970] * 1000)
            };

            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:reading];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        } else {
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to get magnetometer reading"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }
    }];
}

#pragma mark - Heading

// getHeading and watchHeading share one stream of CLLocationManager heading updates. getHeading keeps
// the updates running for kHeadingLingerSeconds after the last call, so polling it (the shared compass
// tab polls every 100 ms) answers at once from the current heading. Upstream started updates, waited a
// fixed 0.5 s and then called stopUpdatingHeading - which also killed a running watchHeading.
// All heading state lives on the main queue.

static const NSTimeInterval kHeadingLingerSeconds = 3.0;
static const NSTimeInterval kHeadingTimeoutSeconds = 1.0;

- (NSDictionary *)headingDictionary:(CLHeading *)heading {
    return @{
        @"magneticHeading": @(heading.magneticHeading),
        @"trueHeading": @(heading.trueHeading),
        @"headingAccuracy": @(heading.headingAccuracy),
        // when the heading was measured, not when it was sent (a timeout answer may carry an older one)
        @"timestamp": @([(heading.timestamp ?: [NSDate date]) timeIntervalSince1970] * 1000)
    };
}

- (void)startHeadingUpdates {
    if (!self.headingUpdatesRunning) {
        [self.locationManager startUpdatingHeading];
        self.headingUpdatesRunning = YES;
    }
}

- (void)stopHeadingUpdates {
    [self.locationManager stopUpdatingHeading];
    self.headingUpdatesRunning = NO;
}

- (void)maybeStopHeadingUpdates {
    if (self.watchHeadingCallbackId == nil && self.pendingHeadingCallbackIds.count == 0
        && [NSDate timeIntervalSinceReferenceDate] - self.lastHeadingRequestTime >= kHeadingLingerSeconds) {
        [self stopHeadingUpdates];
    }
}

- (void)scheduleHeadingIdleStop {
    __weak CDVMagnetometer *weakSelf = self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)((kHeadingLingerSeconds + 0.05) * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        [weakSelf maybeStopHeadingUpdates];
    });
}

- (void)getHeading:(CDVInvokedUrlCommand *)command {
    NSString *callbackId = command.callbackId;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (![CLLocationManager headingAvailable]) {
            [self sendErrorWithCode:ERROR_NOT_AVAILABLE message:@"Heading not available" callbackId:callbackId];
            return;
        }
        self.lastHeadingRequestTime = [NSDate timeIntervalSinceReferenceDate];
        [self scheduleHeadingIdleStop];

        // While updates run, CLLocationManager's heading IS the current one (it only withholds
        // changes smaller than headingFilter), so a poll is answered immediately.
        CLHeading *current = self.locationManager.heading;
        if (self.headingUpdatesRunning && current) {
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[self headingDictionary:current]];
            [self.commandDelegate sendPluginResult:result callbackId:callbackId];
            return;
        }

        [self.pendingHeadingCallbackIds addObject:callbackId];
        [self startHeadingUpdates];

        __weak CDVMagnetometer *weakSelf = self;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(kHeadingTimeoutSeconds * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            CDVMagnetometer *strongSelf = weakSelf;
            if (!strongSelf || ![strongSelf.pendingHeadingCallbackIds containsObject:callbackId]) {
                return;
            }
            [strongSelf.pendingHeadingCallbackIds removeObject:callbackId];
            CLHeading *last = strongSelf.locationManager.heading;
            CDVPluginResult *result = last
                ? [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[strongSelf headingDictionary:last]]
                : [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to get heading"];
            [strongSelf.commandDelegate sendPluginResult:result callbackId:callbackId];
            [strongSelf maybeStopHeadingUpdates];
        });
    });
}

#pragma mark - Watch Readings

- (void)watchReadings:(CDVInvokedUrlCommand *)command {
    if (!self.motionManager.deviceMotionAvailable) {
        [self sendErrorWithCode:ERROR_NOT_AVAILABLE message:@"Device motion not available" callbackId:command.callbackId];
        return;
    }

    // Stop any existing watch
    [self.motionManager stopDeviceMotionUpdates];

    self.watchCallbackId = command.callbackId;

    NSNumber *frequencyArg = [command.arguments objectAtIndex:0];
    double frequency = frequencyArg ? [frequencyArg doubleValue] : 100;
    self.motionManager.deviceMotionUpdateInterval = frequency / 1000.0;

    __weak CDVMagnetometer *weakSelf = self;

    // Use device motion with calibrated magnetic field (matches Android units in microteslas)
    [self.motionManager startDeviceMotionUpdatesUsingReferenceFrame:CMAttitudeReferenceFrameXMagneticNorthZVertical
                                                            toQueue:[NSOperationQueue mainQueue]
                                                        withHandler:^(CMDeviceMotion *motion, NSError *error) {
        if (error) {
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.localizedDescription];
            [result setKeepCallbackAsBool:YES];
            [weakSelf.commandDelegate sendPluginResult:result callbackId:weakSelf.watchCallbackId];
            return;
        }

        if (motion) {
            // Use calibrated magnetic field - values are in microteslas, same as Android
            double x = motion.magneticField.field.x;
            double y = motion.magneticField.field.y;
            double z = motion.magneticField.field.z;
            double magnitude = sqrt(x*x + y*y + z*z);

            NSDictionary *reading = @{
                @"x": @(x),
                @"y": @(y),
                @"z": @(z),
                @"magnitude": @(magnitude),
                @"timestamp": @([[NSDate date] timeIntervalSince1970] * 1000)
            };

            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:reading];
            [result setKeepCallbackAsBool:YES];
            [weakSelf.commandDelegate sendPluginResult:result callbackId:weakSelf.watchCallbackId];
        }
    }];
}

- (void)stopWatch:(CDVInvokedUrlCommand *)command {
    [self.motionManager stopDeviceMotionUpdates];
    self.watchCallbackId = nil;

    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

#pragma mark - Watch Heading

- (void)watchHeading:(CDVInvokedUrlCommand *)command {
    if (![CLLocationManager headingAvailable]) {
        [self sendErrorWithCode:ERROR_NOT_AVAILABLE message:@"Heading not available" callbackId:command.callbackId];
        return;
    }

    NSNumber *filterArg = [command.arguments count] > 1 ? [command.arguments objectAtIndex:1] : nil;
    double filter = [filterArg isKindOfClass:[NSNumber class]] ? [filterArg doubleValue] : 0;
    NSString *callbackId = command.callbackId;

    dispatch_async(dispatch_get_main_queue(), ^{
        self.watchHeadingCallbackId = callbackId;
        self.locationManager.headingFilter = filter > 0 ? filter : kCLHeadingFilterNone;
        [self startHeadingUpdates];
    });
}

- (void)stopWatchHeading:(CDVInvokedUrlCommand *)command {
    NSString *callbackId = command.callbackId;
    dispatch_async(dispatch_get_main_queue(), ^{
        self.watchHeadingCallbackId = nil;
        // the watch's filter would otherwise keep throttling getHeading polls (locationManager.heading)
        self.locationManager.headingFilter = kCLHeadingFilterNone;
        [self maybeStopHeadingUpdates];
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    });
}

#pragma mark - CLLocationManagerDelegate

- (void)locationManager:(CLLocationManager *)manager didUpdateHeading:(CLHeading *)newHeading {
    if (!newHeading) {
        return;
    }
    NSDictionary *headingData = [self headingDictionary:newHeading];

    NSString *watchId = self.watchHeadingCallbackId;
    if (watchId) {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:headingData];
        [result setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:result callbackId:watchId];
    }

    if (self.pendingHeadingCallbackIds.count > 0) {
        NSArray *waiting = [self.pendingHeadingCallbackIds copy];
        [self.pendingHeadingCallbackIds removeAllObjects];
        for (NSString *callbackId in waiting) {
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:headingData];
            [self.commandDelegate sendPluginResult:result callbackId:callbackId];
        }
    }
}

- (BOOL)locationManagerShouldDisplayHeadingCalibration:(CLLocationManager *)manager {
    self.calibrationNeeded = YES;
    return YES;
}

#pragma mark - Info Methods

- (void)getMagnetometerInfo:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        BOOL available = self.motionManager.magnetometerAvailable;

        NSMutableDictionary *info = [NSMutableDictionary dictionary];
        info[@"isAvailable"] = @(available);
        info[@"accuracy"] = @(self.currentAccuracy);
        info[@"calibrationNeeded"] = @(self.calibrationNeeded);
        info[@"platform"] = @"ios";

        if (available) {
            [self.motionManager startMagnetometerUpdates];
            [NSThread sleepForTimeInterval:0.1];

            CMMagnetometerData *data = self.motionManager.magnetometerData;
            [self.motionManager stopMagnetometerUpdates];

            if (data) {
                double x = data.magneticField.x;
                double y = data.magneticField.y;
                double z = data.magneticField.z;
                double magnitude = sqrt(x*x + y*y + z*z);

                info[@"reading"] = @{
                    @"x": @(x),
                    @"y": @(y),
                    @"z": @(z),
                    @"magnitude": @(magnitude),
                    @"timestamp": @([[NSDate date] timeIntervalSince1970] * 1000)
                };
            }
        }

        // Last heading, if heading updates ever ran. Read on the main queue, where the manager lives.
        if ([CLLocationManager headingAvailable]) {
            __block NSDictionary *headingData = nil;
            dispatch_sync(dispatch_get_main_queue(), ^{
                CLHeading *heading = self.locationManager.heading;
                if (heading) {
                    headingData = [self headingDictionary:heading];
                }
            });
            if (headingData) {
                info[@"heading"] = headingData;
            }
        }

        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:info];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
}

- (void)getAccuracy:(CDVInvokedUrlCommand *)command {
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:self.currentAccuracy];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

- (void)isCalibrationNeeded:(CDVInvokedUrlCommand *)command {
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:self.calibrationNeeded ? 1 : 0];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

- (void)getFieldStrength:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        if (!self.motionManager.magnetometerAvailable) {
            [self sendErrorWithCode:ERROR_NOT_AVAILABLE message:@"Magnetometer not available" callbackId:command.callbackId];
            return;
        }

        [self.motionManager startMagnetometerUpdates];
        [NSThread sleepForTimeInterval:0.1];

        CMMagnetometerData *data = self.motionManager.magnetometerData;
        [self.motionManager stopMagnetometerUpdates];

        if (data) {
            double x = data.magneticField.x;
            double y = data.magneticField.y;
            double z = data.magneticField.z;
            double magnitude = sqrt(x*x + y*y + z*z);

            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDouble:magnitude];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        } else {
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to get field strength"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }
    }];
}

- (void)onReset {
    [self.motionManager stopDeviceMotionUpdates];
    [self.motionManager stopMagnetometerUpdates];
    self.watchCallbackId = nil;
    dispatch_async(dispatch_get_main_queue(), ^{
        self.watchHeadingCallbackId = nil;
        self.locationManager.headingFilter = kCLHeadingFilterNone;
        [self.pendingHeadingCallbackIds removeAllObjects]; // the page that asked is gone
        self.lastHeadingRequestTime = 0;
        [self stopHeadingUpdates];
    });
}

@end
