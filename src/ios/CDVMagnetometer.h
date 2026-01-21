#import <Cordova/CDVPlugin.h>
#import <CoreMotion/CoreMotion.h>
#import <CoreLocation/CoreLocation.h>

@interface CDVMagnetometer : CDVPlugin <CLLocationManagerDelegate>

@property (nonatomic, strong) CMMotionManager *motionManager;
@property (nonatomic, strong) CLLocationManager *locationManager;
@property (nonatomic, strong) NSString *watchCallbackId;
@property (nonatomic, strong) NSString *watchHeadingCallbackId;
@property (nonatomic, assign) int currentAccuracy;
@property (nonatomic, assign) BOOL calibrationNeeded;

- (void)isAvailable:(CDVInvokedUrlCommand *)command;
- (void)getReading:(CDVInvokedUrlCommand *)command;
- (void)getHeading:(CDVInvokedUrlCommand *)command;
- (void)watchReadings:(CDVInvokedUrlCommand *)command;
- (void)stopWatch:(CDVInvokedUrlCommand *)command;
- (void)watchHeading:(CDVInvokedUrlCommand *)command;
- (void)stopWatchHeading:(CDVInvokedUrlCommand *)command;
- (void)getMagnetometerInfo:(CDVInvokedUrlCommand *)command;
- (void)getAccuracy:(CDVInvokedUrlCommand *)command;
- (void)isCalibrationNeeded:(CDVInvokedUrlCommand *)command;
- (void)getFieldStrength:(CDVInvokedUrlCommand *)command;

@end
