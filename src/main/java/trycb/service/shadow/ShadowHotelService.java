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

package trycb.service.shadow;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import trycb.config.FeatureFlags;
import trycb.model.Result;
import trycb.service.Hotel;
import trycb.service.HotelService;
import trycb.service.mongodb.MongoHotelService;

/**
 * Shadow implementation of HotelService that can read from and write to both databases
 */
@Service
@Primary
@Profile("shadow")
public class ShadowHotelService implements HotelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowHotelService.class);
    private static final Random RANDOM = new Random();
    
    private final Hotel couchbaseService;
    private final MongoHotelService mongoService;
    private final FeatureFlags featureFlags;
    
    @Autowired
    public ShadowHotelService(
            @Qualifier("hotel") Hotel couchbaseService, 
            MongoHotelService mongoService,
            FeatureFlags featureFlags) {
        this.couchbaseService = couchbaseService;
        this.mongoService = mongoService;
        this.featureFlags = featureFlags;
    }
    
    @Override
    public Result<List<Map<String, Object>>> findHotels(String location, String description) {
        // Determine which database to read from based on feature flags
        if (shouldReadFromMongoDB()) {
            LOGGER.debug("Reading from MongoDB for findHotels({}, {})", location, description);
            Result<List<Map<String, Object>>> result = mongoService.findHotels(location, description);
            
            // If validation is enabled, compare with Couchbase result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findHotels", 
                        result, 
                        couchbaseService.findHotels(location, description));
            }
            
            return result;
        } else {
            LOGGER.debug("Reading from Couchbase for findHotels({}, {})", location, description);
            Result<List<Map<String, Object>>> result = couchbaseService.findHotels(location, description);
            
            // If validation is enabled, compare with MongoDB result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findHotels", 
                        result, 
                        mongoService.findHotels(location, description));
            }
            
            return result;
        }
    }

    @Override
    public Result<List<Map<String, Object>>> findHotels(String description) {
        // Determine which database to read from based on feature flags
        if (shouldReadFromMongoDB()) {
            LOGGER.debug("Reading from MongoDB for findHotels({})", description);
            Result<List<Map<String, Object>>> result = mongoService.findHotels(description);
            
            // If validation is enabled, compare with Couchbase result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findHotels", 
                        result, 
                        couchbaseService.findHotels(description));
            }
            
            return result;
        } else {
            LOGGER.debug("Reading from Couchbase for findHotels({})", description);
            Result<List<Map<String, Object>>> result = couchbaseService.findHotels(description);
            
            // If validation is enabled, compare with MongoDB result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findHotels", 
                        result, 
                        mongoService.findHotels(description));
            }
            
            return result;
        }
    }

    @Override
    public Result<List<Map<String, Object>>> findAllHotels() {
        // Determine which database to read from based on feature flags
        if (shouldReadFromMongoDB()) {
            LOGGER.debug("Reading from MongoDB for findAllHotels()");
            Result<List<Map<String, Object>>> result = mongoService.findAllHotels();
            
            // If validation is enabled, compare with Couchbase result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findAllHotels", 
                        result, 
                        couchbaseService.findAllHotels());
            }
            
            return result;
        } else {
            LOGGER.debug("Reading from Couchbase for findAllHotels()");
            Result<List<Map<String, Object>>> result = couchbaseService.findAllHotels();
            
            // If validation is enabled, compare with MongoDB result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findAllHotels", 
                        result, 
                        mongoService.findAllHotels());
            }
            
            return result;
        }
    }
    
    /**
     * Validate consistency between two database results
     */
    private <T> void validateConsistency(String operation, Result<T> primaryResult, Result<T> secondaryResult) {
        try {
            // Compare result sizes if they are collections
            if (primaryResult.getStatus() && secondaryResult.getStatus() && 
                primaryResult.getData() instanceof List && secondaryResult.getData() instanceof List) {
                
                List<?> primaryList = (List<?>) primaryResult.getData();
                List<?> secondaryList = (List<?>) secondaryResult.getData();
                
                if (primaryList.size() != secondaryList.size()) {
                    LOGGER.warn("Consistency validation failed for {}: Primary has {} results, secondary has {} results", 
                            operation, primaryList.size(), secondaryList.size());
                } else {
                    LOGGER.debug("Consistency validation passed for {}: Both databases returned {} results", 
                            operation, primaryList.size());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during consistency validation for " + operation, e);
        }
    }
    
    /**
     * Determines if the current request should read from MongoDB
     * Takes into account both configuration and shadow percentage
     */
    private boolean shouldReadFromMongoDB() {
        if (featureFlags.isReadFromMongoDB()) {
            return true;
        }
        
        if (featureFlags.isReadFromCouchbase()) {
            return false;
        }
        
        // If neither explicitly set, use shadow percentage
        if (featureFlags.getShadowPercentage() > 0) {
            return RANDOM.nextInt(100) < featureFlags.getShadowPercentage();
        }
        
        return false;
    }
}