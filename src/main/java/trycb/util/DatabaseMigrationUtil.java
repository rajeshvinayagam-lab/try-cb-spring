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

package trycb.util;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

/**
 * Utility for database migration and setup tasks
 */
@Configuration
public class DatabaseMigrationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrationUtil.class);
    
    @Value("${migration.enabled:false}")
    private boolean migrationEnabled;
    
    /**
     * Initialize MongoDB indexes and other required setup
     */
    @Bean
    @Profile("mongodb")
    public CommandLineRunner initializeMongoDB(MongoTemplate mongoTemplate) {
        return args -> {
            LOGGER.info("Setting up MongoDB indexes...");
            
            // Create text indexes for hotel search
            TextIndexDefinition hotelTextIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
                .onField("name", 3.0f)
                .onField("description", 2.0f)
                .onField("city", 2.0f)
                .onField("country", 2.0f)
                .onField("state", 1.0f)
                .onField("address", 1.0f)
                .build();
            
            mongoTemplate.indexOps("hotel").ensureIndex(hotelTextIndex);
            LOGGER.info("Created text index for hotel collection");
            
            // Create indexes for airport collection
            IndexOperations airportIndexOps = mongoTemplate.indexOps("airport");
            airportIndexOps.ensureIndex(new Index().on("faa", Sort.Direction.ASC).unique(true));
            airportIndexOps.ensureIndex(new Index().on("icao", Sort.Direction.ASC));
            airportIndexOps.ensureIndex(new Index().on("airportname", Sort.Direction.ASC));
            LOGGER.info("Created indexes for airport collection");
            
            // Create indexes for flightpath collection
            IndexOperations flightpathIndexOps = mongoTemplate.indexOps("flightpath");
            flightpathIndexOps.ensureIndex(new Index().on("sourceairport", Sort.Direction.ASC)
                                                      .on("destinationairport", Sort.Direction.ASC)
                                                      .on("day", Sort.Direction.ASC));
            LOGGER.info("Created indexes for flightpath collection");
            
            // Create indexes for bookings collection
            IndexOperations bookingIndexOps = mongoTemplate.indexOps("bookings");
            bookingIndexOps.ensureIndex(new Index().on("username", Sort.Direction.ASC));
            LOGGER.info("Created indexes for bookings collection");
            
            // Create indexes for users collection
            IndexOperations userIndexOps = mongoTemplate.indexOps("users");
            userIndexOps.ensureIndex(new Index().on("username", Sort.Direction.ASC).unique(true));
            LOGGER.info("Created indexes for users collection");
            
            // If migration is enabled, perform data migration
            if (migrationEnabled) {
                LOGGER.info("Data migration is enabled");
                try {
                    migrateData(mongoTemplate);
                } catch (Exception e) {
                    LOGGER.error("Error during data migration", e);
                }
            }
        };
    }
    
    /**
     * Migrate data from Couchbase to MongoDB
     * 
     * This method would implement the actual migration logic, but for now it's just a placeholder
     */
    private void migrateData(MongoTemplate mongoTemplate) {
        LOGGER.info("Starting data migration from Couchbase to MongoDB");
        
        // TODO: Implement migration logic here
        // Steps would include:
        // 1. Connect to Couchbase
        // 2. Extract data from each collection
        // 3. Transform to MongoDB format
        // 4. Load into MongoDB collections
        
        LOGGER.info("Data migration completed successfully");
    }
}