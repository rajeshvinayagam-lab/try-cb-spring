# Data Access Layer Analysis

This document analyzes the data access layer of the try-cb-spring application, which demonstrates how Spring Data Couchbase is used to interact with Couchbase Server.

## Overview

The application uses a hybrid approach for data access:
1. Spring Data Couchbase repositories for standard CRUD operations
2. Custom N1QL queries for more complex data retrieval
3. Direct Couchbase Java SDK access for specialized operations like Full Text Search (FTS)

## Repository Structure

All repositories follow a similar pattern:
- Extend `CouchbaseRepository<Entity, String>` for basic CRUD operations
- Implement `DynamicProxyable<Repository>` to support dynamic scope/collection selection
- Use annotations to define scope, collection, and consistency requirements

### Key Repository Interfaces

1. **UserRepository**
   ```java
   @Repository("userRepository")
   @Collection("users")
   @ScanConsistency(query = QueryScanConsistency.REQUEST_PLUS)
   public interface UserRepository extends CouchbaseRepository<User, String>, DynamicProxyable<UserRepository> {
   }
   ```

2. **HotelRepository**
   ```java
   @Repository("hotelRepository")
   @Scope("inventory")
   @Collection("hotel")
   @ScanConsistency(query = QueryScanConsistency.REQUEST_PLUS)
   public interface HotelRepository extends CouchbaseRepository<Hotel, String>, DynamicProxyable<HotelRepository> {
   }
   ```

3. **AirportRepository**
   ```java
   @Repository("airportRepository")
   @Scope("inventory")
   @Collection("airport")
   @ScanConsistency(query = QueryScanConsistency.REQUEST_PLUS)
   public interface AirportRepository extends CouchbaseRepository<Airport, String>, DynamicProxyable<AirportRepository> {
     // Custom and derived query methods
     List<Airport> findAll();
     List<Airport> findByAirportnameStartsWith(String name);
     List<Airport> findByFaa(String faa);
     List<Airport> findByIcao(String iata);
   }
   ```

4. **FlightPathRepository**
   ```java
   @Repository("flightPathRepository")
   @Scope("inventory")
   @ScanConsistency(query = QueryScanConsistency.REQUEST_PLUS)
   public interface FlightPathRepository extends CouchbaseRepository<FlightPath, String>, DynamicProxyable<FlightPathRepository> {
     // Complex N1QL query with joins
     @Query("SELECT meta(r).id as __id, meta(r).cas as __cas, a.name, a.id airlineid, s.flight, s.day, s.utc, "
         + "r.sourceairport, r.destinationairport, r.equipment " + "FROM " + "airport src "
         + "INNER JOIN route r on r.sourceairport = src.faa " + "INNER JOIN airline a on r.airlineid = meta(a).id "
         + "UNNEST r.schedule AS s " + "INNER JOIN airport dst on r.destinationairport = dst.faa "
         + "where src.airportname=$1 and dst.airportname=$2 and s.day=$3")
     List<FlightPath> findFlights(String sourceAirport, String destinationAirport, Number day);
   }
   ```

5. **BookingRepository**
   ```java
   @Repository("bookingRepository")
   @Collection("bookings")
   public interface BookingRepository extends CouchbaseRepository<Booking, String>, DynamicProxyable<BookingRepository> {
   }
   ```

## Query Methods

The application uses three types of query methods:

1. **Built-in Repository Methods**
   - Standard methods from CouchbaseRepository like `findById()`, `save()`, etc.

2. **Derived Query Methods**
   - Spring automatically generates queries based on method names:
   - Example: `List<Airport> findByFaa(String faa);`

3. **Custom N1QL Queries**
   - Defined using the `@Query` annotation
   - Example from AirportRepository:
     ```java
     @Query("#{#n1ql.selectEntity} where #{#n1ql.filter} AND UPPER(airportname) LIKE ($1||'%')")
     List<Airport> findByAirportnameStartsWith(String name);
     ```
   - Complex example from FlightPathRepository that joins multiple collections

## Direct SDK Access

For more advanced operations, the application bypasses Spring Data and uses the Couchbase Java SDK directly:

```java
// Hotel.java service
@Autowired
public Hotel(HotelRepository hotelRepository) {
  this.hotelRepository = hotelRepository;
  // Use the Java SDK cluster and bucket objects directly
  this.cluster = hotelRepository.getOperations().getCouchbaseClientFactory().getCluster();
  this.bucket = hotelRepository.getOperations().getCouchbaseClientFactory().getBucket();
}
```

### Full Text Search Example

```java
// Building an FTS query
ConjunctionQuery fts = SearchQuery.conjuncts(SearchQuery.term("hotel").field("type"));
if (location != null && !location.isEmpty() && !"*".equals(location)) {
  fts.and(SearchQuery.disjuncts(
      SearchQuery.matchPhrase(location).field("country"),
      SearchQuery.matchPhrase(location).field("city"),
      SearchQuery.matchPhrase(location).field("state"),
      SearchQuery.matchPhrase(location).field("address")));
}

// Executing the search
SearchOptions opts = SearchOptions.searchOptions().limit(100);
SearchResult result = cluster.searchQuery("hotels-index", fts, opts);
```

### Subdocument Operations

The application also demonstrates efficient subdocument operations:

```java
// Lookup specific fields instead of entire document
res = collection.lookupIn(row.id(),
    Arrays.asList(
        get("country"), 
        get("city"), 
        get("state"), 
        get("address"), 
        get("name"), 
        get("description")
    )
);
```

## Configuration

Couchbase connection is configured in `Database.java`:

```java
@Configuration
@EnableCouchbaseRepositories
@EnableReactiveCouchbaseRepositories
public class Database extends AbstractCouchbaseConfiguration {
  @Value("${storage.host}") private String host;
  @Value("${storage.bucket}") private String bucket;
  @Value("${storage.username}") private String username;
  @Value("${storage.password}") private String password;
  
  @Override
  public String getConnectionString() {
    return host;
  }
  
  @Override
  public String getUserName() {
    return username;
  }
  
  @Override
  public String getPassword() {
    return password;
  }
  
  @Override
  public String getBucketName() {
    return bucket;
  }
  
  @Override
  public String typeKey() {
    return "type";
  }
}
```

## Key Takeaways

1. **Flexible Query Approach**: The application demonstrates three ways to query Couchbase: repository methods, derived queries, and custom N1QL queries.

2. **Collections Support**: The application leverages Couchbase's collections feature through `@Scope` and `@Collection` annotations.

3. **Direct SDK Access**: For specialized operations like FTS, the application accesses the Couchbase Java SDK directly.

4. **Consistency Control**: All repositories use `@ScanConsistency(query = QueryScanConsistency.REQUEST_PLUS)` to ensure query consistency.

5. **Dynamic Collection Selection**: The `DynamicProxyable` interface enables runtime selection of different scopes and collections.