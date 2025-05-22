# MongoDB Migration Plan

This document outlines a detailed plan for migrating the try-cb-spring application's data access layer from Couchbase to MongoDB while preserving existing functionality.

## Overview

The current application uses Spring Data Couchbase to interact with Couchbase Server. We'll adapt this to work with MongoDB by following these principles:
1. Maintain the same business logic and API behavior
2. Leverage Spring Data MongoDB's similar repository pattern
3. Create abstractions where necessary to handle database-specific features
4. Implement a phased migration approach

## Phase 1: Setup and Infrastructure

### 1.1 Add MongoDB Dependencies

Update `pom.xml` to include Spring Data MongoDB:

```xml
<!-- Add MongoDB support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

### 1.2 Create MongoDB Configuration

Create a MongoDB configuration class similar to the existing Couchbase configuration:

```java
package trycb.config.mongodb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

@Configuration
@Profile("mongodb")
@EnableMongoRepositories(basePackages = "trycb.config.mongodb")
public class MongoDBConfiguration extends AbstractMongoClientConfiguration {

    @Value("${mongodb.host}") private String host;
    @Value("${mongodb.database}") private String database;
    @Value("${mongodb.username}") private String username;
    @Value("${mongodb.password}") private String password;

    @Override
    protected String getDatabaseName() {
        return database;
    }

    @Override
    public MongoClient mongoClient() {
        String connectionString = String.format("mongodb://%s:%s@%s", username, password, host);
        return MongoClients.create(connectionString);
    }
}
```

### 1.3 Update Application Properties

Add MongoDB configuration to `application.properties`:

```properties
# MongoDB Configuration
mongodb.host=localhost:27017
mongodb.database=travel-sample
mongodb.username=admin
mongodb.password=password

# Profile configuration
spring.profiles.active=couchbase
```

## Phase 2: Create Repository Abstraction

### 2.1 Create Repository Interfaces

Create an abstraction layer to switch between Couchbase and MongoDB implementations:

```java
package trycb.repository;

// Generic repository interface
public interface DataRepository<T, ID> {
    T save(T entity);
    Iterable<T> saveAll(Iterable<T> entities);
    T findById(ID id);
    boolean existsById(ID id);
    Iterable<T> findAll();
    Iterable<T> findAllById(Iterable<ID> ids);
    void deleteById(ID id);
    void delete(T entity);
    void deleteAll();
    // Additional methods as needed
}
```

### 2.2 Create Model Adapters

For each entity, create MongoDB document versions with necessary annotations:

Example for Hotel:

```java
package trycb.config.mongodb;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "hotel")
public class MongoHotel {
    @Id
    private String id;
    private String name;
    private String description;
    private String address;
    private String city;
    private String state;
    private String country;
    
    // Getters, setters, and constructors
}
```

### 2.3 Create MongoDB Repositories

Create MongoDB repositories for each entity:

```java
package trycb.config.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoHotelRepository extends MongoRepository<MongoHotel, String> {
    // Custom query methods as needed
}
```

## Phase 3: Implement Service Adapters

### 3.1 Create Service Interfaces

Define service interfaces that abstract database-specific operations:

```java
package trycb.service;

import java.util.List;
import java.util.Map;

import trycb.model.Result;

public interface HotelService {
    Result<List<Map<String, Object>>> findHotels(String location, String description);
    Result<List<Map<String, Object>>> findHotels(String description);
    Result<List<Map<String, Object>>> findAllHotels();
}
```

### 3.2 Implement Couchbase Services

Refactor existing services to implement the new interfaces:

```java
package trycb.service.couchbase;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import trycb.service.HotelService;

@Service
@Profile("couchbase")
public class CouchbaseHotelService implements HotelService {
    // Existing implementation
}
```

### 3.3 Implement MongoDB Services

Create MongoDB implementations for each service:

```java
package trycb.service.mongodb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import trycb.config.mongodb.MongoHotel;
import trycb.config.mongodb.MongoHotelRepository;
import trycb.model.Result;
import trycb.service.HotelService;

@Service
@Profile("mongodb")
public class MongoHotelService implements HotelService {

    private final MongoHotelRepository hotelRepository;
    private final MongoTemplate mongoTemplate;
    
    @Autowired
    public MongoHotelService(MongoHotelRepository hotelRepository, MongoTemplate mongoTemplate) {
        this.hotelRepository = hotelRepository;
        this.mongoTemplate = mongoTemplate;
    }
    
    @Override
    public Result<List<Map<String, Object>>> findHotels(String location, String description) {
        // Implement using MongoDB text search
        TextCriteria criteria = TextCriteria.forDefaultLanguage();
        
        if (location != null && !location.isEmpty() && !"*".equals(location)) {
            criteria.matching(location);
        }
        
        if (description != null && !description.isEmpty() && !"*".equals(description)) {
            criteria.matching(description);
        }
        
        TextQuery query = TextQuery.queryText(criteria).sortByScore();
        List<MongoHotel> hotels = mongoTemplate.find(query, MongoHotel.class);
        
        String queryType = "MongoDB text search on hotel collection";
        return Result.of(convertToMapList(hotels), queryType);
    }
    
    @Override
    public Result<List<Map<String, Object>>> findHotels(String description) {
        return findHotels("*", description);
    }
    
    @Override
    public Result<List<Map<String, Object>>> findAllHotels() {
        List<MongoHotel> hotels = hotelRepository.findAll();
        String queryType = "MongoDB findAll on hotel collection";
        return Result.of(convertToMapList(hotels), queryType);
    }
    
    private List<Map<String, Object>> convertToMapList(List<MongoHotel> hotels) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MongoHotel hotel : hotels) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", hotel.getName());
            map.put("description", hotel.getDescription());
            
            StringBuilder fullAddr = new StringBuilder();
            if (hotel.getAddress() != null)
                fullAddr.append(hotel.getAddress()).append(", ");
            if (hotel.getCity() != null)
                fullAddr.append(hotel.getCity()).append(", ");
            if (hotel.getState() != null)
                fullAddr.append(hotel.getState()).append(", ");
            if (hotel.getCountry() != null)
                fullAddr.append(hotel.getCountry());
            
            if (fullAddr.length() > 2 && fullAddr.charAt(fullAddr.length() - 2) == ',')
                fullAddr.delete(fullAddr.length() - 2, fullAddr.length() - 1);
            
            map.put("address", fullAddr.toString());
            result.add(map);
        }
        return result;
    }
}
```

## Phase 4: Handling Special Features

### 4.1 Full Text Search (FTS)

MongoDB's text search capabilities are different from Couchbase's FTS:

1. Create text indexes in MongoDB:
```javascript
db.hotel.createIndex({
    name: "text",
    description: "text",
    country: "text",
    city: "text",
    state: "text",
    address: "text"
})
```

2. Implement MongoDB text search as shown in the `MongoHotelService` example above.

### 4.2 N1QL Queries

For complex N1QL queries like in `FlightPathRepository`, use MongoDB's aggregation framework:

```java
public List<FlightPath> findFlights(String sourceAirport, String destinationAirport, Number day) {
    // MongoDB aggregation pipeline to join airport, route, and airline collections
    List<AggregationOperation> operations = new ArrayList<>();
    
    operations.add(Aggregation.match(Criteria.where("airportname").is(sourceAirport)));
    operations.add(Aggregation.lookup("route", "faa", "sourceairport", "routes"));
    operations.add(Aggregation.unwind("routes"));
    operations.add(Aggregation.lookup("airline", "routes.airlineid", "_id", "airline"));
    operations.add(Aggregation.unwind("airline"));
    operations.add(Aggregation.unwind("routes.schedule"));
    operations.add(Aggregation.match(Criteria.where("routes.schedule.day").is(day)));
    operations.add(Aggregation.lookup("airport", "routes.destinationairport", "faa", "destination"));
    operations.add(Aggregation.unwind("destination"));
    operations.add(Aggregation.match(Criteria.where("destination.airportname").is(destinationAirport)));
    
    // Project the fields needed for FlightPath
    operations.add(Aggregation.project()
        .and("routes._id").as("__id")
        .and("airline.name").as("name")
        .and("airline._id").as("airlineid")
        .and("routes.schedule.flight").as("flight")
        .and("routes.schedule.day").as("day")
        .and("routes.schedule.utc").as("utc")
        .and("routes.sourceairport").as("sourceairport")
        .and("routes.destinationairport").as("destinationairport")
        .and("routes.equipment").as("equipment"));
    
    Aggregation aggregation = Aggregation.newAggregation(operations);
    AggregationResults<FlightPath> results = mongoTemplate.aggregate(
        aggregation, "airport", FlightPath.class);
    
    return results.getMappedResults();
}
```

### 4.3 Subdocument Operations

For subdocument operations in Couchbase, use MongoDB's projection capabilities:

```java
public Map<String, Object> findHotelDetails(String id) {
    Document doc = mongoTemplate.getCollection("hotel")
        .find(new Document("_id", id))
        .projection(Projections.include("country", "city", "state", "address", "name", "description"))
        .first();
    
    if (doc == null) {
        return null;
    }
    
    Map<String, Object> result = new HashMap<>();
    result.put("name", doc.getString("name"));
    result.put("description", doc.getString("description"));
    
    // Build address as in the original code
    StringBuilder fullAddr = new StringBuilder();
    String address = doc.getString("address");
    String city = doc.getString("city");
    String state = doc.getString("state");
    String country = doc.getString("country");
    
    if (address != null)
        fullAddr.append(address).append(", ");
    if (city != null)
        fullAddr.append(city).append(", ");
    if (state != null)
        fullAddr.append(state).append(", ");
    if (country != null)
        fullAddr.append(country);
    
    if (fullAddr.length() > 2 && fullAddr.charAt(fullAddr.length() - 2) == ',')
        fullAddr.delete(fullAddr.length() - 2, fullAddr.length() - 1);
    
    result.put("address", fullAddr.toString());
    
    return result;
}
```

## Phase 5: Data Migration

### 5.1 Create MongoDB Collections

Create collections in MongoDB matching Couchbase structure:

```javascript
db.createCollection("users")
db.createCollection("hotel")
db.createCollection("airport")
db.createCollection("route")
db.createCollection("airline")
db.createCollection("bookings")
```

### 5.2 Export Data from Couchbase

Use Couchbase's export tools or custom scripts to export data:

```bash
# Example using cbexport
cbexport json -c couchbase://localhost -u Administrator -p password \
  -b travel-sample -f lines -o travel-sample.json
```

### 5.3 Transform and Import Data

Transform exported data to MongoDB format and import:

```bash
# Process the exported data to convert to MongoDB format
python transform_data.py travel-sample.json mongodb-import.json

# Import into MongoDB
mongoimport --uri mongodb://admin:password@localhost:27017/travel-sample \
  --collection hotel --file mongodb-import-hotel.json
```

## Phase 6: Testing and Deployment

### 6.1 Integration Testing

Create integration tests that verify both implementations work identically:

```java
@SpringBootTest
@ActiveProfiles("couchbase")
class CouchbaseHotelServiceIntegrationTest {
    // Test Couchbase implementation
}

@SpringBootTest
@ActiveProfiles("mongodb")
class MongoDBHotelServiceIntegrationTest {
    // Test MongoDB implementation with identical expectations
}
```

### 6.2 Feature Toggle

Implement a feature toggle to switch between implementations:

```java
@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;
    
    @Autowired
    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }
    
    // Controller methods remain unchanged, service implementation is injected based on active profile
}
```

### 6.3 Deployment Strategy

1. Deploy with dual configurations enabled
2. Run in shadow mode (write to both databases, read from Couchbase)
3. Switch reads to MongoDB after validation
4. Eventually remove Couchbase dependencies

## Phase 7: Performance Tuning

### 7.1 Indexing Strategy

Create appropriate MongoDB indexes:

```javascript
// For airport searches
db.airport.createIndex({ "faa": 1 })
db.airport.createIndex({ "icao": 1 })
db.airport.createIndex({ "airportname": 1 })

// For route searches
db.route.createIndex({ "sourceairport": 1, "destinationairport": 1 })
db.route.createIndex({ "schedule.day": 1 })

// For hotel text search
db.hotel.createIndex({
    name: "text",
    description: "text",
    country: "text",
    city: "text",
    state: "text",
    address: "text"
})
```

### 7.2 Connection Pooling

Configure MongoDB connection pooling in the application properties:

```properties
spring.data.mongodb.min-connections-per-host=10
spring.data.mongodb.max-connections-per-host=100
spring.data.mongodb.max-wait-time=120000
spring.data.mongodb.connect-timeout=10000
```

## Conclusion

This migration plan provides a structured approach to migrate from Couchbase to MongoDB while preserving existing functionality. By using Spring Data's abstraction capabilities and proper service interfaces, we can maintain the same API behavior while changing the underlying database implementation.

The plan focuses on:
1. Creating proper abstractions
2. Handling database-specific features
3. Ensuring data integrity during migration
4. Providing a gradual transition with feature toggles

This approach minimizes risk and allows for validation at each step of the migration process.