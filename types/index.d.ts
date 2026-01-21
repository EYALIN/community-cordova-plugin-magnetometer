/**
 * Magnetometer reading data with raw magnetic field values
 */
export interface IMagnetometerReading {
    /** Magnetic field strength along X axis in microteslas (μT) */
    x: number;
    /** Magnetic field strength along Y axis in microteslas (μT) */
    y: number;
    /** Magnetic field strength along Z axis in microteslas (μT) */
    z: number;
    /** Total magnetic field magnitude in microteslas (μT) */
    magnitude: number;
    /** Timestamp of the reading in milliseconds */
    timestamp: number;
}

/**
 * Compass heading data
 */
export interface IHeadingData {
    /** Magnetic heading in degrees (0-359.99), relative to magnetic north */
    magneticHeading: number;
    /** True heading in degrees (0-359.99), relative to geographic north (requires location services) */
    trueHeading: number;
    /** Heading accuracy in degrees (iOS only, -1 if unavailable) */
    headingAccuracy: number;
    /** Timestamp of the reading in milliseconds */
    timestamp: number;
}

/**
 * Complete magnetometer information
 */
export interface IMagnetometerInfo {
    /** Whether magnetometer sensor is available */
    isAvailable: boolean;
    /** Current magnetometer reading */
    reading: IMagnetometerReading;
    /** Current compass heading */
    heading: IHeadingData;
    /** Sensor accuracy level: 0=unreliable, 1=low, 2=medium, 3=high */
    accuracy: number;
    /** Whether calibration is needed */
    calibrationNeeded: boolean;
    /** Device platform (android/ios/browser) */
    platform: string;
}

/**
 * Watch options for continuous readings
 */
export interface IWatchOptions {
    /** Update frequency in milliseconds (default: 100) */
    frequency?: number;
    /** Minimum heading change in degrees to trigger update (heading watch only) */
    filter?: number;
}

/**
 * Magnetometer sensor accuracy levels
 */
export enum MagnetometerAccuracy {
    UNRELIABLE = 0,
    LOW = 1,
    MEDIUM = 2,
    HIGH = 3
}

/**
 * Magnetometer plugin manager
 */
export default class MagnetometerManager {
    /**
     * Check if magnetometer sensor is available on the device
     * @returns Promise resolving to 1 if available, 0 if not
     */
    isAvailable(): Promise<number>;

    /**
     * Get current magnetometer reading (single reading)
     * @returns Promise resolving to magnetometer data with x, y, z values
     */
    getReading(): Promise<IMagnetometerReading>;

    /**
     * Get current compass heading
     * @returns Promise resolving to heading data
     */
    getHeading(): Promise<IHeadingData>;

    /**
     * Start watching magnetometer readings continuously
     * @param successCallback Called with magnetometer data on each update
     * @param errorCallback Called on error
     * @param options Optional settings including frequency
     */
    watchReadings(
        successCallback: (data: IMagnetometerReading) => void,
        errorCallback: (error: string) => void,
        options?: IWatchOptions
    ): void;

    /**
     * Stop watching magnetometer readings
     * @returns Promise resolving when stopped
     */
    stopWatch(): Promise<void>;

    /**
     * Start watching compass heading continuously
     * @param successCallback Called with heading data on each update
     * @param errorCallback Called on error
     * @param options Optional settings including frequency and filter
     */
    watchHeading(
        successCallback: (data: IHeadingData) => void,
        errorCallback: (error: string) => void,
        options?: IWatchOptions
    ): void;

    /**
     * Stop watching compass heading
     * @returns Promise resolving when stopped
     */
    stopWatchHeading(): Promise<void>;

    /**
     * Get complete magnetometer information
     * @returns Promise resolving to complete magnetometer info
     */
    getMagnetometerInfo(): Promise<IMagnetometerInfo>;

    /**
     * Get sensor accuracy level
     * @returns Promise resolving to accuracy: 0=unreliable, 1=low, 2=medium, 3=high
     */
    getAccuracy(): Promise<number>;

    /**
     * Check if device needs calibration
     * @returns Promise resolving to 1 if calibration needed, 0 if not
     */
    isCalibrationNeeded(): Promise<number>;

    /**
     * Get magnetic field strength (magnitude)
     * @returns Promise resolving to field strength in microteslas
     */
    getFieldStrength(): Promise<number>;
}
