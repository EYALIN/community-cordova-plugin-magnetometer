#import "CDVMagnetometer.h"

@implementation CDVMagnetometer

- (void)pluginInitialize {
    self.motionManager = [[CMMotionManager alloc] init];
    self.locationManager = [[CLLocationManager alloc] init];
    self.locationManager.delegate = self;
    self.currentAccuracy = 3; // Default to high
    self.calibrationNeeded = NO;
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
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Magnetometer not available"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
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

- (void)getHeading:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        if (![CLLocationManager headingAvailable]) {
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Heading not available"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
            return;
        }

        __block BOOL completed = NO;
        __weak CDVMagnetometer *weakSelf = self;

        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf.locationManager startUpdatingHeading];
        });

        // Wait for heading update
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            if (!completed) {
                completed = YES;
                CLHeading *heading = weakSelf.locationManager.heading;
                [weakSelf.locationManager stopUpdatingHeading];

                if (heading) {
                    NSDictionary *headingData = @{
                        @"magneticHeading": @(heading.magneticHeading),
                        @"trueHeading": @(heading.trueHeading),
                        @"headingAccuracy": @(heading.headingAccuracy),
                        @"timestamp": @([[NSDate date] timeIntervalSince1970] * 1000)
                    };

                    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:headingData];
                    [weakSelf.commandDelegate sendPluginResult:result callbackId:command.callbackId];
                } else {
                    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Failed to get heading"];
                    [weakSelf.commandDelegate sendPluginResult:result callbackId:command.callbackId];
                }
            }
        });
    }];
}

#pragma mark - Watch Readings

- (void)watchReadings:(CDVInvokedUrlCommand *)command {
    if (!self.motionManager.deviceMotionAvailable) {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Device motion not available"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
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
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Heading not available"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        return;
    }

    self.watchHeadingCallbackId = command.callbackId;

    NSNumber *filterArg = [command.arguments count] > 1 ? [command.arguments objectAtIndex:1] : nil;
    double filter = filterArg ? [filterArg doubleValue] : 0;

    if (filter > 0) {
        self.locationManager.headingFilter = filter;
    } else {
        self.locationManager.headingFilter = kCLHeadingFilterNone;
    }

    dispatch_async(dispatch_get_main_queue(), ^{
        [self.locationManager startUpdatingHeading];
    });
}

- (void)stopWatchHeading:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.locationManager stopUpdatingHeading];
    });
    self.watchHeadingCallbackId = nil;

    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

#pragma mark - CLLocationManagerDelegate

- (void)locationManager:(CLLocationManager *)manager didUpdateHeading:(CLHeading *)newHeading {
    if (self.watchHeadingCallbackId && newHeading) {
        NSDictionary *headingData = @{
            @"magneticHeading": @(newHeading.magneticHeading),
            @"trueHeading": @(newHeading.trueHeading),
            @"headingAccuracy": @(newHeading.headingAccuracy),
            @"timestamp": @([[NSDate date] timeIntervalSince1970] * 1000)
        };

        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:headingData];
        [result setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:result callbackId:self.watchHeadingCallbackId];
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

        // Get heading if available
        if ([CLLocationManager headingAvailable]) {
            CLHeading *heading = self.locationManager.heading;
            if (heading) {
                info[@"heading"] = @{
                    @"magneticHeading": @(heading.magneticHeading),
                    @"trueHeading": @(heading.trueHeading),
                    @"headingAccuracy": @(heading.headingAccuracy),
                    @"timestamp": @([[NSDate date] timeIntervalSince1970] * 1000)
                };
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
            CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Magnetometer not available"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
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
    [self.locationManager stopUpdatingHeading];
    self.watchCallbackId = nil;
    self.watchHeadingCallbackId = nil;
}

@end
