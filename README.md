# community-cordova-plugin-magnetometer

Cordova plugin to access the device magnetometer (compass) sensor. Provides raw magnetic field data, compass heading, and continuous monitoring capabilities.

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
cordova plugin add https://github.com/AyalaCommunity/community-cordova-plugin-magnetometer.git
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

## License

MIT

## Author

EYALIN
