# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-09-26

Replaces `cordova-plugin-device-orientation` (PLU-235): `getHeading()` / `watchHeading()` return the same
`{ magneticHeading, trueHeading, headingAccuracy, timestamp }` object as its `getCurrentHeading()`, and this
plugin declares **no location permission** (device-orientation added ACCESS_FINE_LOCATION and
ACCESS_COARSE_LOCATION to every app that installed it).

### Changed

- Android: heading comes from `TYPE_ROTATION_VECTOR` (gyro-fused, tilt-compensated) when the device has it, otherwise from accelerometer + magnetometer as before.
- Android: `getHeading` and `watchHeading` share one sensor listener. After a `getHeading` the sensor stays on for 3 s, so polling `getHeading` (the compass tab polls every 100 ms) is answered at once from the running sensor instead of registering new listeners on every call; it then stops by itself.
- Android: `headingAccuracy` is the rotation vector's estimated accuracy in degrees when the sensor reports it (-1 otherwise).
- Android: `trueHeading` = magnetic heading + declination (`GeomagneticField`) when the app ALREADY holds a location permission and a last known location exists. The plugin never asks for one; without it `trueHeading` equals `magneticHeading`, as in device-orientation on Android.
- Android: when `getRotationMatrix` fails (free fall / no field) that sample is skipped; before, it reported 0 (north).
- iOS: `getHeading` answers at once while heading updates run, keeps them running for 3 s after the last call, and no longer calls `stopUpdatingHeading` under a running `watchHeading` (it used to stop the watch 0.5 s after any `getHeading`). It waits up to 1 s for the first heading. All heading state is on the main queue.

- Android: `getAccuracy` / `isCalibrationNeeded` follow the rotation-vector sensor's accuracy while the heading runs on it (the magnetometer is not registered then).
- Android: `watchHeading`'s `filter` option (minimum change in degrees) is honoured; it was iOS-only.
- Android: `getMagnetometerInfo` includes `heading` (the last one) once `getHeading`/`watchHeading` has produced one.
- Android: a device with neither a rotation-vector sensor nor an accelerometer rejects `getHeading`/`watchHeading` with code `3` at once, instead of registering the magnetometer and timing out on every call.
- iOS: heading `timestamp` is when the heading was measured (`CLHeading.timestamp`), not when it was sent.
- Package: `files` whitelist, `repository` object, MIT `LICENSE` file, `cordovaDependencies` (cordova >= 10, cordova-android >= 11, cordova-ios >= 6; it said `cordova > 100`, which no CLI satisfies), `npm test` runs the Android tests.

### Fixed

- Android: a `watchHeading` started while the lingering `getHeading` sensor was being stopped could be left with no sensor (the sensor callback read the watch before taking the lock); the watch is now set and read under the lock.
- Android: a heading a hair below 0 degrees came out as 360 instead of 0; headings are always in [0, 360).
- Android: the location-permission check used `Context.checkSelfPermission` (API 23) directly; it now goes through `cordova.hasPermission`, safe on API 22.
- iOS: `stopWatchHeading` (and a page reload) reset `headingFilter`; a stopped watch's filter kept throttling `getHeading` polls.
- iOS: `getMagnetometerInfo` read `CLLocationManager.heading` from a background thread; it reads it on the main queue.
- Android: a `getHeading`, `getReading` or `getFieldStrength` that timed out left its sensor listener registered forever (battery drain; every later event hit a dead listener). `getMagnetometerInfo` leaked the same way when no reading arrived within 500 ms.

### Tests

- `tests/android/run.sh` (`npm test`): 13 heading tests (rotation-vector source and accuracy, polling reuses the sensor, timeout unregisters and answers once, idle stop after the linger, getHeading under a watch, declination only with a permission the app already holds, getReading timeout, a watch starting while the linger stops, the filter option, [0, 360) wrap, no heading sensors, heading in getMagnetometerInfo).

## [1.0.5] - not published (shipped in 1.1.0)

### Fixed

- Android: no more `NullPointerException` in `onSensorChanged` when `stopWatch`, `stopWatchHeading` or a page reload (`onReset`) happens while a sensor event is being delivered. The callbacks are read once per event (and are `volatile`); an event that finds no callback unregisters the listener.

### Tests

- `tests/android/run.sh`: runs `Magnetometer.java` on a JVM against fake Android sensor classes that reproduce the interleaving.

## [1.0.4]

- Version bump (no changelog entry at the time).

## [1.0.3] - 2025-02-04

### Changed

- Error handling now returns structured error objects with `code` and `message` properties
- Error code `3` (NOT_AVAILABLE) is now consistently returned when magnetometer sensor is unavailable
- Improved error detection for "sensor not available" scenarios across both Android and iOS

### Fixed

- Consistent error format across platforms (Android and iOS now both return `{code: 3, message: "..."}` for unavailable sensor)

## [1.0.0] - 2025-01-20

### Added

- Initial release
- `isAvailable()` - Check if magnetometer sensor is available
- `getReading()` - Get single magnetometer reading with x, y, z values in microteslas
- `getHeading()` - Get compass heading (magnetic and true north)
- `watchReadings()` - Continuous magnetometer monitoring with configurable frequency
- `stopWatch()` - Stop magnetometer monitoring
- `watchHeading()` - Continuous compass heading monitoring
- `stopWatchHeading()` - Stop heading monitoring
- `getMagnetometerInfo()` - Get complete magnetometer information
- `getAccuracy()` - Get current sensor accuracy level
- `isCalibrationNeeded()` - Check if calibration is required
- `getFieldStrength()` - Get total magnetic field magnitude
- Full TypeScript definitions
- Android support using SensorManager
- iOS support using CoreMotion and CoreLocation frameworks
- Browser support with Generic Sensor API and mock fallback
