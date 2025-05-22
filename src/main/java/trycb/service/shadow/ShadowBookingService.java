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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import trycb.config.Booking;
import trycb.config.FeatureFlags;
import trycb.model.Result;
import trycb.service.BookingService;
import trycb.service.mongodb.MongoBookingService;

/**
 * Shadow implementation of BookingService that can read from and write to both databases
 */
@Service
@Primary
@Profile("shadow")
public class ShadowBookingService implements BookingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowBookingService.class);
    private static final Random RANDOM = new Random();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    
    private final BookingService couchbaseService;
    private final MongoBookingService mongoService;
    private final FeatureFlags featureFlags;
    
    @Autowired
    public ShadowBookingService(
            @Qualifier("bookingService") BookingService couchbaseService, 
            MongoBookingService mongoService,
            FeatureFlags featureFlags) {
        this.couchbaseService = couchbaseService;
        this.mongoService = mongoService;
        this.featureFlags = featureFlags;
    }
    
    @Override
    public Result<List<Booking>> findBookingsByUser(String username) {
        // Determine which database to read from based on feature flags
        if (shouldReadFromMongoDB()) {
            LOGGER.debug("Reading from MongoDB for findBookingsByUser({})", username);
            Result<List<Booking>> result = mongoService.findBookingsByUser(username);
            
            // If validation is enabled, compare with Couchbase result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findBookingsByUser", 
                        result, 
                        couchbaseService.findBookingsByUser(username));
            }
            
            return result;
        } else {
            LOGGER.debug("Reading from Couchbase for findBookingsByUser({})", username);
            Result<List<Booking>> result = couchbaseService.findBookingsByUser(username);
            
            // If validation is enabled, compare with MongoDB result
            if (featureFlags.isValidateConsistency()) {
                validateConsistency("findBookingsByUser", 
                        result, 
                        mongoService.findBookingsByUser(username));
            }
            
            return result;
        }
    }

    @Override
    public Result<Booking> createBooking(String username, Booking newBooking) {
        LOGGER.debug("Creating booking for user: {}", username);
        
        // Determine primary and secondary databases for writes
        BookingService primaryService;
        BookingService secondaryService;
        
        if (featureFlags.isReadFromMongoDB()) {
            primaryService = mongoService;
            secondaryService = couchbaseService;
        } else {
            primaryService = couchbaseService;
            secondaryService = mongoService;
        }
        
        // Create booking in primary database
        Result<Booking> primaryResult = primaryService.createBooking(username, newBooking);
        
        // If primary write was successful and we should write to both databases
        if (primaryResult.getStatus() && isShadowWriteEnabled()) {
            // Write to secondary database asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    LOGGER.debug("Shadow writing booking to secondary database for user: {}", username);
                    secondaryService.createBooking(username, primaryResult.getData());
                } catch (Exception e) {
                    LOGGER.error("Error during shadow write for booking creation", e);
                }
            }, EXECUTOR);
        }
        
        return primaryResult;
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
    
    /**
     * Determines if shadow writes are enabled
     */
    private boolean isShadowWriteEnabled() {
        return featureFlags.isShadowModeEnabled() && 
               featureFlags.isWriteToCouchbase() && 
               featureFlags.isWriteToMongoDB();
    }
}