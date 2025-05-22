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
import trycb.config.FlightPath;
import trycb.model.Result;
import trycb.service.FlightPathService;
import trycb.service.mongodb.MongoFlightPathService;

/**
 * Shadow implementation of FlightPathService that can read from and write to both databases
 */
@Service
@Primary
@Profile("shadow")
public class ShadowFlightPathService implements FlightPathService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowFlightPathService.class);
    private static final Random RANDOM = new Random();
    
    private final FlightPathService couchbaseService;
    private final MongoFlightPathService mongoService;
    private final FeatureFlags featureFlags;
    
    @Autowired
    public ShadowFlightPathService(
            @Qualifier("flightPath") FlightPathService couchbaseService, 
            MongoFlightPathService mongoService,
            FeatureFlags featureFlags) {
        this.couchbaseService = couchbaseService;
        this.mongoService = mongoService;
        this.featureFlags = featureFlags;
    }
    
    @Override
    public Result<List<FlightPath>> findFlights(String from, String to, String leave) {
        // Determine which database to read from based on feature flags
        if (shouldReadFromMongoDB()) {
            LOGGER.debug("Reading from MongoDB for findFlights({}, {}, {})", from, to, leave);
            Result<List<FlightPath>> result = mongoService.findFlights(from, to, leave);
            
            // If validation is enabled, compare with Couchbase result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findFlights", 
                        result, 
                        couchbaseService.findFlights(from, to, leave));
            }
            
            return result;
        } else {
            LOGGER.debug("Reading from Couchbase for findFlights({}, {}, {})", from, to, leave);
            Result<List<FlightPath>> result = couchbaseService.findFlights(from, to, leave);
            
            // If validation is enabled, compare with MongoDB result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findFlights", 
                        result, 
                        mongoService.findFlights(from, to, leave));
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