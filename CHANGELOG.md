# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
