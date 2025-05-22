# MongoDB Migration Summary

## Overview

This document summarizes the implementation of MongoDB support in the try-cb-spring application. The migration enables the application to run with either Couchbase or MongoDB as the database backend without affecting the application's functionality or API behavior.

## Implementation Details

### Architecture Changes

1. **Profile-Based Configuration**
   - Added Spring profiles: `couchbase` (default), `mongodb`, and `shadow`
   - Application can run with either database by setting the active profile
   - All database-specific code is isolated using Spring's `@Profile` annotation

2. **Service Layer Abstraction**
   - Created service interfaces to abstract database implementations:
     - `HotelService`
     - `AirportService`
     - `FlightPathService`
     - `UserService`
     - `BookingService`
   - Both Couchbase and MongoDB implementations follow the same interfaces
   - Controllers remain unchanged and interact only with interfaces

3. **MongoDB Model Classes**
   - Created MongoDB entity models for all domain objects:
     - `MongoHotel`
     - `MongoAirport`
     - `MongoFlightPath`
     - `MongoUser`
     - `MongoBooking`
   - Added appropriate annotations for MongoDB mapping and indexing
   - Implemented conversion between MongoDB models and domain models

4. **MongoDB Repositories**
   - Implemented MongoDB repositories for all entities:
     - `MongoHotelRepository`
     - `MongoAirportRepository`
     - `MongoFlightPathRepository`
     - `MongoUserRepository`
     - `MongoBookingRepository`
   - Used MongoDB-specific query techniques (text search, aggregation)

5. **MongoDB Service Implementations**
   - Created MongoDB implementations of all service interfaces:
     - `MongoHotelService`
     - `MongoAirportService`
     - `MongoFlightPathService`
     - `MongoUserService`
     - `MongoBookingService`
   - Maintained identical functionality while using MongoDB-specific approaches

6. **Feature Flags and Shadow Mode**
   - Implemented `FeatureFlags` configuration class to control database behavior
   - Created shadow service implementations that can read from one database and write to both:
     - `ShadowHotelService`
     - `ShadowFlightPathService`
     - `ShadowBookingService`
   - Added data consistency validation between databases
   - Implemented percentage-based traffic routing for gradual migration

### Special Features Implementation

1. **Full Text Search (FTS)**
   - Couchbase: Used Couchbase FTS API with custom query building
   - MongoDB: Implemented using MongoDB text indexes and `TextCriteria`
   - Hotel search results maintain the same format and behavior

2. **Complex N1QL Queries**
   - Couchbase: Used custom N1QL queries for complex joins
   - MongoDB: Implemented using aggregation framework with pipeline stages
   - Flight search maintains the same functionality

3. **Authentication/Authorization**
   - Maintained the same JWT-based authentication system
   - Both implementations use the same `TokenService`
   - Password hashing and verification work identically

4. **Subdocument Operations**
   - Couchbase: Used subdocument API for efficient partial document access
   - MongoDB: Used MongoDB projection to achieve the same optimization

### Database Setup and Migration

1. **Index Creation**
   - Created `DatabaseMigrationUtil` to set up MongoDB indexes
   - Text indexes for hotel search
   - Regular indexes for other collections (airport, flightpath, etc.)

2. **Data Migration Support**
   - Added migration flag to control data migration process
   - Prepared placeholder for actual migration implementation
   - Structure for connecting to both databases during migration

3. **Docker Support**
   - Created `docker-compose-mongodb.yml` for MongoDB deployment
   - Created `docker-compose-shadow.yml` for dual database support
   - Added MongoDB initialization scripts in `mongodb-init` directory
   - Updated documentation with MongoDB Docker instructions

## Running with MongoDB

### Docker Deployment

```bash
# Run the application with MongoDB
docker-compose -f docker-compose-mongodb.yml up

# Run in shadow mode (dual database support)
docker-compose -f docker-compose-shadow.yml up
```

### Manual Deployment

```bash
# Start MongoDB
docker-compose -f docker-compose-mongodb.yml up mongodb

# Run the application with MongoDB profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=mongodb --mongodb.host=localhost:27017 --mongodb.database=travel-sample --mongodb.username=admin --mongodb.password=password"

# Run in shadow mode
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=shadow --feature.database.read=couchbase --feature.database.write.couchbase=true --feature.database.write.mongodb=true"
```

## Feature Flags

The application supports the following feature flags to control database behavior:

| Flag                          | Description                                           | Values                    |
|-------------------------------|-------------------------------------------------------|---------------------------|
| feature.database.read         | Which database to read from                           | couchbase, mongodb, auto  |
| feature.database.write.couchbase | Whether to write to Couchbase                     | true, false, auto         |
| feature.database.write.mongodb | Whether to write to MongoDB                         | true, false, auto         |
| feature.database.validate     | Whether to validate data consistency                  | true, false               |
| feature.migration.enabled     | Whether data migration is enabled                     | true, false               |
| feature.shadow.percentage     | Percentage of traffic to route to MongoDB reads       | 0-100                     |

### Phased Migration Strategy

1. **Shadow Writing Phase**
   ```
   feature.database.read=couchbase
   feature.database.write.couchbase=true
   feature.database.write.mongodb=true
   feature.database.validate=true
   ```
   - Read from Couchbase (production)
   - Write to both databases
   - Validate data consistency

2. **Gradual Traffic Shift**
   ```
   feature.database.read=auto
   feature.database.write.couchbase=true
   feature.database.write.mongodb=true
   feature.shadow.percentage=10
   ```
   - Route 10% of reads to MongoDB
   - Continue writing to both databases
   - Gradually increase percentage over time

3. **MongoDB Primary**
   ```
   feature.database.read=mongodb
   feature.database.write.couchbase=true
   feature.database.write.mongodb=true
   ```
   - Read from MongoDB primarily
   - Continue writing to Couchbase for safety

4. **MongoDB Only**
   ```
   feature.database.read=mongodb
   feature.database.write.couchbase=false
   feature.database.write.mongodb=true
   ```
   - MongoDB becomes the only database

## Benefits of the Migration

1. **Database Flexibility**
   - Application can now run with either Couchbase or MongoDB
   - Easy switching between databases using profiles
   - No changes required to client applications or APIs

2. **Enhanced Performance Options**
   - Leverage MongoDB's strengths for certain workloads
   - Maintain Couchbase's advantages for others
   - Benchmark and choose the optimal database for specific use cases

3. **Improved Architecture**
   - Better separation of concerns
   - Reduced coupling between business logic and data access
   - More maintainable and testable codebase

4. **Safe Migration Path**
   - Shadow mode allows for zero-downtime migration
   - Consistency validation ensures data integrity
   - Gradual traffic shifting minimizes risk

5. **Future Expansion**
   - Pattern established for adding additional database backends
   - Could extend to support other databases (PostgreSQL, etc.)

## Limitations and Future Work

1. **Data Migration Implementation**
   - Complete the data migration utility for transferring data between databases
   - Add data validation and integrity checks during migration

2. **Testing Coverage**
   - Add comprehensive integration tests for both database implementations
   - Create performance benchmarks to compare database options

3. **Deployment Optimizations**
   - Tune MongoDB connection pooling and performance settings
   - Add database health checks and monitoring

4. **Shadow Mode Enhancements**
   - Add metrics collection for read/write performance comparison
   - Implement automatic rollback mechanism for failed writes
   - Add transaction support for cross-database consistency