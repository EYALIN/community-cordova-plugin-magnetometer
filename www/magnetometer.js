var exec = require('cordova/exec');

var PLUGIN_NAME = 'Magnetometer';

var MagnetometerPlugin = {
    /**
     * Check if magnetometer sensor is available on the device
     * @returns {Promise<number>} 1 if available, 0 if not
     */
    isAvailable: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'isAvailable', []);
        });
    },

    /**
     * Get current magnetometer reading (single reading)
     * @returns {Promise<object>} Magnetometer data with x, y, z values in microteslas
     */
    getReading: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'getReading', []);
        });
    },

    /**
     * Get current compass heading
     * @returns {Promise<object>} Heading data with magneticHeading, trueHeading, headingAccuracy, timestamp
     */
    getHeading: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'getHeading', []);
        });
    },

    /**
     * Start watching magnetometer readings continuously
     * @param {function} successCallback Called with magnetometer data on each update
     * @param {function} errorCallback Called on error
     * @param {object} options Optional settings { frequency: number (ms) }
     * @returns {string} Watch ID to use for stopping
     */
    watchReadings: function(successCallback, errorCallback, options) {
        var frequency = (options && options.frequency) ? options.frequency : 100;
        exec(successCallback, errorCallback, PLUGIN_NAME, 'watchReadings', [frequency]);
    },

    /**
     * Stop watching magnetometer readings
     * @returns {Promise<void>}
     */
    stopWatch: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'stopWatch', []);
        });
    },

    /**
     * Start watching compass heading continuously
     * @param {function} successCallback Called with heading data on each update
     * @param {function} errorCallback Called on error
     * @param {object} options Optional settings { frequency: number (ms), filter: number (degrees) }
     * @returns {string} Watch ID to use for stopping
     */
    watchHeading: function(successCallback, errorCallback, options) {
        var frequency = (options && options.frequency) ? options.frequency : 100;
        var filter = (options && options.filter) ? options.filter : 0;
        exec(successCallback, errorCallback, PLUGIN_NAME, 'watchHeading', [frequency, filter]);
    },

    /**
     * Stop watching compass heading
     * @returns {Promise<void>}
     */
    stopWatchHeading: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'stopWatchHeading', []);
        });
    },

    /**
     * Get complete magnetometer information
     * @returns {Promise<object>} Complete magnetometer info including availability, reading, heading, accuracy
     */
    getMagnetometerInfo: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'getMagnetometerInfo', []);
        });
    },

    /**
     * Get sensor accuracy level
     * @returns {Promise<number>} Accuracy: 0=unreliable, 1=low, 2=medium, 3=high
     */
    getAccuracy: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'getAccuracy', []);
        });
    },

    /**
     * Check if device needs calibration
     * @returns {Promise<number>} 1 if calibration needed, 0 if not
     */
    isCalibrationNeeded: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'isCalibrationNeeded', []);
        });
    },

    /**
     * Get magnetic field strength (magnitude)
     * @returns {Promise<number>} Field strength in microteslas
     */
    getFieldStrength: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, 'getFieldStrength', []);
        });
    }
};

module.exports = MagnetometerPlugin;
