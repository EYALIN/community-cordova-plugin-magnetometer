var watchInterval = null;
var watchHeadingInterval = null;

module.exports = {
    isAvailable: function(successCallback, errorCallback) {
        // Check if Generic Sensor API is available
        var available = 'Magnetometer' in window || 'AbsoluteOrientationSensor' in window;
        successCallback(available ? 1 : 0);
    },

    getReading: function(successCallback, errorCallback) {
        if ('Magnetometer' in window) {
            try {
                var sensor = new window.Magnetometer({ frequency: 10 });
                sensor.addEventListener('reading', function() {
                    var x = sensor.x || 0;
                    var y = sensor.y || 0;
                    var z = sensor.z || 0;
                    var magnitude = Math.sqrt(x * x + y * y + z * z);

                    sensor.stop();
                    successCallback({
                        x: x,
                        y: y,
                        z: z,
                        magnitude: magnitude,
                        timestamp: Date.now()
                    });
                });
                sensor.addEventListener('error', function(event) {
                    errorCallback(event.error.message || 'Magnetometer error');
                });
                sensor.start();
            } catch (e) {
                // Return mock data if sensor not accessible
                successCallback({
                    x: 25.5,
                    y: -12.3,
                    z: 45.8,
                    magnitude: 54.2,
                    timestamp: Date.now()
                });
            }
        } else {
            // Return mock data for browsers without Magnetometer API
            successCallback({
                x: 25.5,
                y: -12.3,
                z: 45.8,
                magnitude: 54.2,
                timestamp: Date.now()
            });
        }
    },

    getHeading: function(successCallback, errorCallback) {
        if ('AbsoluteOrientationSensor' in window) {
            try {
                var sensor = new window.AbsoluteOrientationSensor({ frequency: 10 });
                sensor.addEventListener('reading', function() {
                    var q = sensor.quaternion;
                    var heading = quaternionToHeading(q);
                    sensor.stop();
                    successCallback({
                        magneticHeading: heading,
                        trueHeading: heading,
                        headingAccuracy: -1,
                        timestamp: Date.now()
                    });
                });
                sensor.addEventListener('error', function(event) {
                    errorCallback(event.error.message || 'Orientation sensor error');
                });
                sensor.start();
            } catch (e) {
                // Return mock heading
                successCallback({
                    magneticHeading: 180,
                    trueHeading: 180,
                    headingAccuracy: -1,
                    timestamp: Date.now()
                });
            }
        } else {
            // Return mock heading
            successCallback({
                magneticHeading: 180,
                trueHeading: 180,
                headingAccuracy: -1,
                timestamp: Date.now()
            });
        }
    },

    watchReadings: function(successCallback, errorCallback, args) {
        var frequency = args && args[0] ? args[0] : 100;

        if (watchInterval) {
            clearInterval(watchInterval);
        }

        if ('Magnetometer' in window) {
            try {
                var sensor = new window.Magnetometer({ frequency: 1000 / frequency });
                sensor.addEventListener('reading', function() {
                    var x = sensor.x || 0;
                    var y = sensor.y || 0;
                    var z = sensor.z || 0;
                    var magnitude = Math.sqrt(x * x + y * y + z * z);

                    successCallback({
                        x: x,
                        y: y,
                        z: z,
                        magnitude: magnitude,
                        timestamp: Date.now()
                    });
                });
                sensor.addEventListener('error', function(event) {
                    errorCallback(event.error.message || 'Magnetometer error');
                });
                sensor.start();

                // Store reference for stopping
                watchInterval = sensor;
            } catch (e) {
                // Fall back to mock data
                startMockWatch(successCallback, frequency);
            }
        } else {
            // Fall back to mock data
            startMockWatch(successCallback, frequency);
        }
    },

    stopWatch: function(successCallback, errorCallback) {
        if (watchInterval) {
            if (typeof watchInterval.stop === 'function') {
                watchInterval.stop();
            } else {
                clearInterval(watchInterval);
            }
            watchInterval = null;
        }
        successCallback();
    },

    watchHeading: function(successCallback, errorCallback, args) {
        var frequency = args && args[0] ? args[0] : 100;

        if (watchHeadingInterval) {
            if (typeof watchHeadingInterval.stop === 'function') {
                watchHeadingInterval.stop();
            } else {
                clearInterval(watchHeadingInterval);
            }
        }

        if ('AbsoluteOrientationSensor' in window) {
            try {
                var sensor = new window.AbsoluteOrientationSensor({ frequency: 1000 / frequency });
                sensor.addEventListener('reading', function() {
                    var q = sensor.quaternion;
                    var heading = quaternionToHeading(q);

                    successCallback({
                        magneticHeading: heading,
                        trueHeading: heading,
                        headingAccuracy: -1,
                        timestamp: Date.now()
                    });
                });
                sensor.addEventListener('error', function(event) {
                    errorCallback(event.error.message || 'Orientation sensor error');
                });
                sensor.start();

                watchHeadingInterval = sensor;
            } catch (e) {
                startMockHeadingWatch(successCallback, frequency);
            }
        } else {
            startMockHeadingWatch(successCallback, frequency);
        }
    },

    stopWatchHeading: function(successCallback, errorCallback) {
        if (watchHeadingInterval) {
            if (typeof watchHeadingInterval.stop === 'function') {
                watchHeadingInterval.stop();
            } else {
                clearInterval(watchHeadingInterval);
            }
            watchHeadingInterval = null;
        }
        successCallback();
    },

    getMagnetometerInfo: function(successCallback, errorCallback) {
        var available = 'Magnetometer' in window || 'AbsoluteOrientationSensor' in window;

        successCallback({
            isAvailable: available,
            reading: {
                x: 25.5,
                y: -12.3,
                z: 45.8,
                magnitude: 54.2,
                timestamp: Date.now()
            },
            heading: {
                magneticHeading: 180,
                trueHeading: 180,
                headingAccuracy: -1,
                timestamp: Date.now()
            },
            accuracy: 3,
            calibrationNeeded: false,
            platform: 'browser'
        });
    },

    getAccuracy: function(successCallback, errorCallback) {
        successCallback(3); // High accuracy (mock)
    },

    isCalibrationNeeded: function(successCallback, errorCallback) {
        successCallback(0); // No calibration needed (mock)
    },

    getFieldStrength: function(successCallback, errorCallback) {
        if ('Magnetometer' in window) {
            try {
                var sensor = new window.Magnetometer({ frequency: 10 });
                sensor.addEventListener('reading', function() {
                    var x = sensor.x || 0;
                    var y = sensor.y || 0;
                    var z = sensor.z || 0;
                    var magnitude = Math.sqrt(x * x + y * y + z * z);
                    sensor.stop();
                    successCallback(Math.round(magnitude));
                });
                sensor.addEventListener('error', function() {
                    successCallback(54); // Mock value
                });
                sensor.start();
            } catch (e) {
                successCallback(54); // Mock value
            }
        } else {
            successCallback(54); // Mock value
        }
    }
};

function startMockWatch(successCallback, frequency) {
    var angle = 0;
    watchInterval = setInterval(function() {
        angle += 0.1;
        successCallback({
            x: 25.5 + Math.sin(angle) * 5,
            y: -12.3 + Math.cos(angle) * 5,
            z: 45.8 + Math.sin(angle * 0.5) * 3,
            magnitude: 54.2 + Math.sin(angle) * 2,
            timestamp: Date.now()
        });
    }, frequency);
}

function startMockHeadingWatch(successCallback, frequency) {
    var heading = 180;
    watchHeadingInterval = setInterval(function() {
        heading = (heading + 1) % 360;
        successCallback({
            magneticHeading: heading,
            trueHeading: heading,
            headingAccuracy: -1,
            timestamp: Date.now()
        });
    }, frequency);
}

function quaternionToHeading(q) {
    if (!q || q.length < 4) return 0;

    var x = q[0], y = q[1], z = q[2], w = q[3];

    // Calculate yaw (heading) from quaternion
    var siny_cosp = 2 * (w * z + x * y);
    var cosy_cosp = 1 - 2 * (y * y + z * z);
    var yaw = Math.atan2(siny_cosp, cosy_cosp);

    // Convert to degrees and normalize to 0-360
    var heading = yaw * 180 / Math.PI;
    if (heading < 0) heading += 360;

    return heading;
}

require('cordova/exec/proxy').add('Magnetometer', module.exports);
