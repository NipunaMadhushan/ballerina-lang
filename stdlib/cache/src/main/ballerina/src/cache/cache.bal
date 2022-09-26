// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/java;
import ballerina/task;
import ballerina/time;

# Represents configurations for the `cache:Cache` object.
#
# + capacity - Maximum number of entries allowed in the cache
# + evictionPolicy - The policy, which defines the cache eviction algorithm
# + evictionFactor - The factor by which the entries will be evicted once the cache is full
# + defaultMaxAgeInSeconds - The default value in seconds which all the cache entries are valid.
#                            '-1' means, the entries are valid forever. This will be overwritten by the the
#                            `maxAgeInSeconds` property set when inserting item to the cache
# + cleanupIntervalInSeconds - Interval of the timer task, which will clean up the cache
public type CacheConfig record {|
    int capacity = 20;
    AbstractEvictionPolicy evictionPolicy = new LruEvictionPolicy();
    float evictionFactor = 0.25;
    int defaultMaxAgeInSeconds = -1;
    int cleanupIntervalInSeconds?;
|};

type CacheEntry record {|
    any data;
    int expTime;       // exp time since epoch. calculated based on the `maxAge` parameter when inserting to map
|};

// Cleanup service which cleans the cache entries periodically.
boolean cleanupInProgress = false;

// Cleanup service which cleans the cache entries periodically.
final service cleanupService = service {
    resource function onTrigger(Cache cache) {
        // This check will skip the processes triggered while the clean up in progress.
        if (!cleanupInProgress) {
            cleanupInProgress = true;
            externCleanUp(cache, time:nanoTime());
            cleanupInProgress = false;
        }
    }
};

# The `cache:Cache` object, which is used for all the cache-related operations. It is not recommended to insert `()`
# as the value of the cache since it doesn't make any sense to cache a nil.
public type Cache object {

    *AbstractCache;

    private int capacity;
    private AbstractEvictionPolicy evictionPolicy;
    private float evictionFactor;
    private int defaultMaxAgeInSeconds;

    # Called when a new `cache:Cache` object is created.
    #
    # + cacheConfig - Configurations for the `cache:Cache` object
    public function __init(CacheConfig cacheConfig = {}) {
        self.capacity = cacheConfig.capacity;
        self.evictionPolicy = cacheConfig.evictionPolicy;
        self.evictionFactor = cacheConfig.evictionFactor;
        self.defaultMaxAgeInSeconds = cacheConfig.defaultMaxAgeInSeconds;

        // Cache capacity must be a positive value.
        if (self.capacity <= 0) {
            panic prepareError("Capacity must be greater than 0.");
        }
        // Cache eviction factor must be between 0.0 (exclusive) and 1.0 (inclusive).
        if (self.evictionFactor <= 0 || self.evictionFactor > 1) {
            panic prepareError("Cache eviction factor must be between 0.0 (exclusive) and 1.0 (inclusive).");
        }

        // Cache eviction factor must be between 0.0 (exclusive) and 1.0 (inclusive).
        if (self.defaultMaxAgeInSeconds != -1 && self.defaultMaxAgeInSeconds <= 0) {
            panic prepareError("Default max age should be greater than 0 or -1 for indicate forever valid.");
        }
       externInit(self);

        int? cleanupIntervalInSeconds = cacheConfig?.cleanupIntervalInSeconds;
        if (cleanupIntervalInSeconds is int) {
            task:TimerConfiguration timerConfiguration = {
                intervalInMillis: cleanupIntervalInSeconds,
                initialDelayInMillis: cleanupIntervalInSeconds
            };
            task:Scheduler cleanupScheduler = new(timerConfiguration);
            task:SchedulerError? result = cleanupScheduler.attach(cleanupService, self, self.evictionPolicy);
            if (result is task:SchedulerError) {
                panic prepareError("Failed to create the cache cleanup task.", result);
            }
            result = cleanupScheduler.start();
            if (result is task:SchedulerError) {
                panic prepareError("Failed to start the cache cleanup task.", result);
            }
        }
    }

    # Adds the given key value pair to the cache. If the cache previously contained a value associated with the
    # provided key, the old value wil be replaced by the newly-provided value.
    #
    # + key - Key of the value to be cached
    # + value - Value to be cached. Value should not be `()`
    # + maxAgeInSeconds - The time in seconds for which the cache entry is valid. If the value is '-1', the entry is
    #                     valid forever.
    # + return - `()` if successfully added to the cache or `Error` if a `()` value is inserted to the cache.
    public function put(string key, any value, int maxAgeInSeconds = -1) returns Error? {
        if (value is ()) {
            return prepareError("Unsupported cache value '()' for the key: " + key + ".",
                                logLevel = LOG_LEVEL_DEBUG);
        }

        // Calculate the `expTime` of the cache entry based on the `maxAgeInSeconds` property and
        // `defaultMaxAgeInSeconds` property.
        int calculatedExpTime = -1;
        if (maxAgeInSeconds != -1 && maxAgeInSeconds > 0) {
            calculatedExpTime = time:nanoTime() + (maxAgeInSeconds * 1000 * 1000 * 1000);
        } else {
            if (self.defaultMaxAgeInSeconds != -1) {
                calculatedExpTime = time:nanoTime() + (self.defaultMaxAgeInSeconds * 1000 * 1000 * 1000);
            }
        }

        CacheEntry entry = {
            data: value,
            expTime: calculatedExpTime
        };
        return externPut(self, java:fromString(key), entry);
    }

    # Returns the cached value associated with the provided key.
    #
    # + key - Key of the cached value, which should be retrieved
    # + return - The cached value associated with the provided key or an `Error` if the provided cache key is not
    #            exisiting in the cache or any error occurred while retrieving the value from the cache.
    public function get(string key) returns any|Error {
        if (!self.hasKey(key)) {
            return prepareError("Cache entry from the given key: " + key + ", is not available.",
                                logLevel = LOG_LEVEL_DEBUG);
        }

        any? entry = externGet(self, java:fromString(key), time:nanoTime());
        if (entry is CacheEntry) {
            return entry.data;
        }
    }

    # Discards a cached value from the cache.
    #
    # + key - Key of the cache value, which needs to be discarded from the cache
    # + return - `()` if successfully discarded the value or an `Error` if the provided cache key is not present in the
    #            cache
    public function invalidate(string key) returns Error? {
        if (!self.hasKey(key)) {
            return prepareError("Cache entry from the given key: " + key + ", is not available.",
                                logLevel = LOG_LEVEL_DEBUG);
        }
        externRemove(self, java:fromString(key));
    }

    # Discards all the cached values from the cache.
    #
    # + return - `()` if successfully discarded all the values from the cache or an `Error` if any error occurred while
    # discarding all the values from the cache.
    public function invalidateAll() returns Error? {
        externRemoveAll(self);
    }

    # Checks whether the given key has an associated cached value.
    #
    # + key - The key to be checked in the cache
    # + return - `true` if a cached value is available for the provided key or `false` if there is no cached value
    #            associated for the given key
    public function hasKey(string key) returns boolean {
        return externHasKey(self, java:fromString(key));
    }

    # Returns a list of all the keys from the cache.
    #
    # + return - Array of all the keys from the cache
    public function keys() returns string[] {
        handle[] result = externKeys(self);
        string[] keys = [];
        int index = 0;
        foreach var key in result {
            keys[index] = <string>java:toString(key);
            index = index + 1;
        }
        return keys;
    }

    # Returns the size of the cache.
    #
    # + return - The size of the cache
    public function size() returns int {
        return externSize(self);
    }

    # Returns the capacity of the cache.
    #
    # + return - The capacity of the cache
    public function capacity() returns int {
        return self.capacity;
    }
};

function externInit(Cache cache) = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;

function externRemoveAll(Cache cache) = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;

function externHasKey(Cache cache, handle key) returns boolean = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;

function externKeys(Cache cache) returns handle[] = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;

function externSize(Cache cache) returns int = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;

function externPut(Cache cache, handle key, any value) = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;

function externGet(Cache cache, handle key, int currentTime) returns CacheEntry? = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;

function externRemove(Cache cache, handle key) = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;

function externCleanUp(Cache cache, int currentTime) = @java:Method {
    class: "org.ballerinalang.stdlib.cache.nativeimpl.Cache"
} external;