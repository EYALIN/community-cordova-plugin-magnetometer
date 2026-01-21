[![NPM version](https://img.shields.io/npm/v/community-cordova-plugin-magnetometer)](https://www.npmjs.com/package/community-cordova-plugin-magnetometer)

# Community Cordova Magnetometer Plugin

Cordova plugin to access the device magnetometer (compass) sensor. Provides raw magnetic field data, compass heading, and continuous monitoring capabilities.

I dedicate a considerable amount of my free time to developing and maintaining many cordova plugins for the community ([See the list with all my maintained plugins][community_plugins]).
To help ensure this plugin is kept updated,
new features are added and bugfixes are implemented quickly,
please donate a couple of dollars (or a little more if you can stretch) as this will help me to afford to dedicate time to its maintenance.
Please consider donating if you're using this plugin in an app that makes you money,
or if you're asking for new features or priority bug fixes. Thank you!

[![](https://img.shields.io/static/v1?label=Sponsor%20Me&style=for-the-badge&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/eyalin)

## Supported Platforms

- Android
- iOS
- Browser (with Generic Sensor API support, falls back to mock data)

## Installation

```bash
cordova plugin add community-cordova-plugin-magnetometer
```

Or install from GitHub:

```bash
cordova plugin add https://github.com/EYALIN/community-cordova-plugin-magnetometer.git
```

## Usage

### TypeScript

```typescript
import MagnetometerManager, {
    IMagnetometerReading,
    IHeadingData,
    IMagnetometerInfo
} from 'community-cordova-plugin-magnetometer';

// Check availability
const available = await MagnetometerManager.isAvailable();
console.log('Magnetometer available:', available === 1);

// Get single reading
const reading: IMagnetometerReading = await MagnetometerManager.getReading();
console.log(`Magnetic field: X=${reading.x}, Y=${reading.y}, Z=${reading.z} μT`);

// Get compass heading
const heading: IHeadingData = await MagnetometerManager.getHeading();
console.log(`Heading: ${heading.magneticHeading}°`);

// Get complete info
const info: IMagnetometerInfo = await MagnetometerManager.getMagnetometerInfo();
console.log('Magnetometer info:', info);
```

### JavaScript

```javascript
// Check if magnetometer is available
MagnetometerPlugin.isAvailable()
    .then(function(available) {
        console.log('Magnetometer available:', available === 1);
    });

// Get single magnetometer reading
MagnetometerPlugin.getReading()
    .then(function(reading) {
        console.log('X:', reading.x, 'μT');
        console.log('Y:', reading.y, 'μT');
        console.log('Z:', reading.z, 'μT');
        console.log('Magnitude:', reading.magnitude, 'μT');
    })
    .catch(function(error) {
        console.error('Error:', error);
    });

// Get compass heading
MagnetometerPlugin.getHeading()
    .then(function(heading) {
        console.log('Magnetic Heading:', heading.magneticHeading, '°');
        console.log('True Heading:', heading.trueHeading, '°');
    });

// Watch magnetometer readings continuously
MagnetometerPlugin.watchReadings(
    function(reading) {
        console.log('Reading:', reading.x, reading.y, reading.z);
    },
    function(error) {
        console.error('Error:', error);
    },
    { frequency: 100 } // Update every 100ms
);

// Stop watching readings
MagnetometerPlugin.stopWatch();

// Watch compass heading continuously
MagnetometerPlugin.watchHeading(
    function(heading) {
        console.log('Heading:', heading.magneticHeading, '°');
    },
    function(error) {
        console.error('Error:', error);
    },
    { frequency: 100, filter: 1 } // Update every 100ms, minimum 1° change
);

// Stop watching heading
MagnetometerPlugin.stopWatchHeading();
```

## API Reference

### Methods

#### `isAvailable(): Promise<number>`

Check if magnetometer sensor is available on the device.

**Returns:** `1` if available, `0` if not.

---

#### `getReading(): Promise<IMagnetometerReading>`

Get a single magnetometer reading.

**Returns:**
```typescript
{
    x: number;        // Magnetic field X axis (μT)
    y: number;        // Magnetic field Y axis (μT)
    z: number;        // Magnetic field Z axis (μT)
    magnitude: number; // Total field strength (μT)
    timestamp: number; // Reading timestamp (ms)
}
```

---

#### `getHeading(): Promise<IHeadingData>`

Get current compass heading.

**Returns:**
```typescript
{
    magneticHeading: number;  // Degrees from magnetic north (0-359.99)
    trueHeading: number;      // Degrees from true north (0-359.99)
    headingAccuracy: number;  // Accuracy in degrees (iOS only, -1 if unavailable)
    timestamp: number;        // Reading timestamp (ms)
}
```

---

#### `watchReadings(successCallback, errorCallback, options?): void`

Start watching magnetometer readings continuously.

**Parameters:**
- `successCallback`: Function called with `IMagnetometerReading` on each update
- `errorCallback`: Function called on error
- `options`: Optional settings
  - `frequency`: Update interval in milliseconds (default: 100)

---

#### `stopWatch(): Promise<void>`

Stop watching magnetometer readings.

---

#### `watchHeading(successCallback, errorCallback, options?): void`

Start watching compass heading continuously.

**Parameters:**
- `successCallback`: Function called with `IHeadingData` on each update
- `errorCallback`: Function called on error
- `options`: Optional settings
  - `frequency`: Update interval in milliseconds (default: 100)
  - `filter`: Minimum heading change in degrees to trigger update (iOS only)

---

#### `stopWatchHeading(): Promise<void>`

Stop watching compass heading.

---

#### `getMagnetometerInfo(): Promise<IMagnetometerInfo>`

Get complete magnetometer information.

**Returns:**
```typescript
{
    isAvailable: boolean;
    reading: IMagnetometerReading;
    heading: IHeadingData;
    accuracy: number;         // 0=unreliable, 1=low, 2=medium, 3=high
    calibrationNeeded: boolean;
    platform: string;         // 'android', 'ios', or 'browser'
}
```

---

#### `getAccuracy(): Promise<number>`

Get current sensor accuracy level.

**Returns:** Accuracy level:
- `0` - Unreliable
- `1` - Low
- `2` - Medium
- `3` - High

---

#### `isCalibrationNeeded(): Promise<number>`

Check if the magnetometer needs calibration.

**Returns:** `1` if calibration needed, `0` if not.

---

#### `getFieldStrength(): Promise<number>`

Get the total magnetic field strength (magnitude).

**Returns:** Field strength in microteslas (μT).

## Interfaces

### IMagnetometerReading

```typescript
interface IMagnetometerReading {
    x: number;         // Magnetic field X axis in microteslas (μT)
    y: number;         // Magnetic field Y axis in microteslas (μT)
    z: number;         // Magnetic field Z axis in microteslas (μT)
    magnitude: number; // Total magnetic field magnitude in microteslas (μT)
    timestamp: number; // Timestamp of the reading in milliseconds
}
```

### IHeadingData

```typescript
interface IHeadingData {
    magneticHeading: number;  // Heading relative to magnetic north (0-359.99°)
    trueHeading: number;      // Heading relative to true north (0-359.99°)
    headingAccuracy: number;  // Accuracy in degrees (iOS only)
    timestamp: number;        // Timestamp in milliseconds
}
```

### IMagnetometerInfo

```typescript
interface IMagnetometerInfo {
    isAvailable: boolean;
    reading: IMagnetometerReading;
    heading: IHeadingData;
    accuracy: number;
    calibrationNeeded: boolean;
    platform: string;
}
```

## Platform Notes

### iOS

- Uses `CoreMotion` framework for raw magnetometer data
- Uses `CoreLocation` framework for compass heading
- `trueHeading` requires location services to be enabled
- `headingAccuracy` is available and indicates the accuracy in degrees
- Calibration prompt is shown automatically when needed

### Android

- Uses `SensorManager` with `TYPE_MAGNETIC_FIELD` sensor
- Compass heading calculated using rotation matrix from magnetometer + accelerometer
- `headingAccuracy` returns `-1` (not available on Android)
- `trueHeading` equals `magneticHeading` (GPS-based declination not implemented)

### Browser

- Uses Generic Sensor API (`Magnetometer`, `AbsoluteOrientationSensor`) when available
- Falls back to mock data for testing when sensors not available
- Mock data simulates realistic sensor behavior with slight variations

## Typical Magnetic Field Values

- Earth's magnetic field: 25-65 μT (varies by location)
- Near a smartphone: 100-500 μT
- Near a strong magnet: 1000+ μT

## Use Cases

- Metal detector apps
- Compass applications
- Augmented reality navigation
- Magnetic field measurement tools
- Device orientation detection

## About the Community Cordova Plugins

This plugin is part of the **Community Cordova Plugins** collection, maintained by [EYALIN](https://github.com/EYALIN).

I spend most of my free time maintaining and improving these open-source Cordova plugins to help the mobile development community. The goal is to keep essential Cordova plugins alive, updated, and compatible with the latest platform versions.

### Maintained Plugins

Here are some of the community plugins I maintain:

| Plugin | Description |
|--------|-------------|
| [community-cordova-plugin-admob](https://github.com/eyalin/eyalin-admob-plus) | AdMob integration for ads monetization |
| [community-cordova-plugin-battery-status](https://github.com/EYALIN/community-cordova-plugin-battery-status) | Battery level and charging status |
| [community-cordova-plugin-cpu](https://github.com/EYALIN/community-cordova-plugin-cpu) | CPU information and usage |
| [community-cordova-plugin-ram](https://github.com/EYALIN/community-cordova-plugin-ram) | RAM/memory information |
| [community-cordova-plugin-apps](https://github.com/EYALIN/community-cordova-plugin-apps) | Installed apps information |
| [community-cordova-plugin-file-opener](https://github.com/EYALIN/community-cordova-plugin-file-opener) | Open files with external apps |
| [community-cordova-plugin-files-picker](https://github.com/EYALIN/community-cordova-plugin-files-picker) | Native file picker |
| [community-cordova-plugin-image-picker](https://github.com/EYALIN/community-cordova-plugin-image-picker) | Native image picker |
| [community-cordova-plugin-firebase-analytics](https://github.com/EYALIN/community-cordova-plugin-firebase-analytics) | Firebase Analytics integration |
| [community-cordova-plugin-social-sharing](https://github.com/EYALIN/community-cordova-plugin-social-sharing) | Social sharing functionality |
| [community-cordova-plugin-sqlite](https://github.com/EYALIN/community-cordova-plugin-sqlite) | SQLite database |
| [community-cordova-plugin-sim](https://github.com/EYALIN/community-cordova-plugin-sim) | SIM card information |
| [community-cordova-plugin-wifi](https://github.com/EYALIN/community-cordova-plugin-wifi) | WiFi network information |
| [community-cordova-plugin-nfc](https://github.com/EYALIN/community-cordova-plugin-nfc) | NFC functionality |
| [community-cordova-plugin-inappbrowser](https://github.com/EYALIN/community-cordova-plugin-inappbrowser) | In-app browser |
| [community-cordova-plugin-security-check](https://github.com/EYALIN/community-cordova-plugin-security-check) | Device security status checks |
| [community-cordova-plugin-magnetometer](https://github.com/EYALIN/community-cordova-plugin-magnetometer) | Magnetometer/compass sensor |
| [community-cordova-plugin-screen-time](https://github.com/EYALIN/community-cordova-plugin-screen-time) | Screen time tracking |
| [community-cordova-plugin-native-settings](https://github.com/EYALIN/community-cordova-plugin-native-settings) | Open native device settings |
| [community-cordova-plugin-printer](https://github.com/EYALIN/community-cordova-plugin-printer) | Document printing |
| ...and many more!

### Why Support?

Maintaining these plugins requires significant time and effort:
- Keeping up with Android and iOS platform changes
- Testing on multiple devices and OS versions
- Responding to issues and pull requests
- Adding new features requested by the community
- Writing documentation and examples

If these plugins help you in your projects, please consider supporting the work:

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/eyalin)

[![Sponsor](https://img.shields.io/badge/Sponsor-GitHub-pink.svg)](https://github.com/sponsors/eyalin)

Your support helps keep these plugins maintained and free for everyone!

## License

MIT

## Author

EYALIN

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

[community_plugins]: https://github.com/eyalin?tab=repositories&q=community-cordova-plugin
