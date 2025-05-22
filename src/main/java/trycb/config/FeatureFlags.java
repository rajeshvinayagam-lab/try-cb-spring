/**
 * Copyright (C) 2021 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */

package trycb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for application feature flags
 */
@Configuration
public class FeatureFlags {

    /**
     * Which database to read from (couchbase, mongodb, or auto)
     * auto will use the active profile
     */
    @Value("${feature.database.read:auto}")
    private String databaseRead;
    
    /**
     * Whether to write to Couchbase
     */
    @Value("${feature.database.write.couchbase:auto}")
    private String writeCouchbase;
    
    /**
     * Whether to write to MongoDB
     */
    @Value("${feature.database.write.mongodb:auto}")
    private String writeMongodb;
    
    /**
     * Whether to validate data consistency between databases
     */
    @Value("${feature.database.validate:false}")
    private boolean validateConsistency;
    
    /**
     * Whether data migration is enabled
     */
    @Value("${feature.migration.enabled:false}")
    private boolean migrationEnabled;
    
    /**
     * Percentage of traffic to route to MongoDB reads (when using shadow mode)
     */
    @Value("${feature.shadow.percentage:0}")
    private int shadowPercentage;
    
    /**
     * Check if reads should come from Couchbase
     */
    public boolean isReadFromCouchbase() {
        if ("couchbase".equalsIgnoreCase(databaseRead)) {
            return true;
        } else if ("mongodb".equalsIgnoreCase(databaseRead)) {
            return false;
        } else {
            // Auto - based on active profile
            return !isActiveProfileMongoDB();
        }
    }
    
    /**
     * Check if reads should come from MongoDB
     */
    public boolean isReadFromMongoDB() {
        if ("mongodb".equalsIgnoreCase(databaseRead)) {
            return true;
        } else if ("couchbase".equalsIgnoreCase(databaseRead)) {
            return false;
        } else {
            // Auto - based on active profile
            return isActiveProfileMongoDB();
        }
    }
    
    /**
     * Check if data should be written to Couchbase
     */
    public boolean isWriteToCouchbase() {
        if ("true".equalsIgnoreCase(writeCouchbase)) {
            return true;
        } else if ("false".equalsIgnoreCase(writeCouchbase)) {
            return false;
        } else {
            // Auto - write to Couchbase if active profile is couchbase
            return !isActiveProfileMongoDB();
        }
    }
    
    /**
     * Check if data should be written to MongoDB
     */
    public boolean isWriteToMongoDB() {
        if ("true".equalsIgnoreCase(writeMongodb)) {
            return true;
        } else if ("false".equalsIgnoreCase(writeMongodb)) {
            return false;
        } else {
            // Auto - write to MongoDB if active profile is mongodb
            return isActiveProfileMongoDB();
        }
    }
    
    /**
     * Check if shadow mode is enabled (writing to both databases)
     */
    public boolean isShadowModeEnabled() {
        return isWriteToCouchbase() && isWriteToMongoDB();
    }
    
    /**
     * Check if data consistency validation is enabled
     */
    public boolean isValidateConsistency() {
        return validateConsistency;
    }
    
    /**
     * Check if data migration is enabled
     */
    public boolean isMigrationEnabled() {
        return migrationEnabled;
    }
    
    /**
     * Get the percentage of traffic to route to MongoDB in shadow mode
     */
    public int getShadowPercentage() {
        return shadowPercentage;
    }
    
    /**
     * Helper method to check if the active profile is mongodb
     */
    private boolean isActiveProfileMongoDB() {
        try {
            return System.getProperty("spring.profiles.active", "").contains("mongodb");
        } catch (Exception e) {
            return false;
        }
    }
}