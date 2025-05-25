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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.bson.BsonValue;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryResult;
import com.google.gson.JsonElement;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertManyResult;

/**
 * Utility for database migration and setup tasks
 */
@Component
@Profile("mongodb")
public class DatabaseMigrationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrationUtil.class);

    @Value("${migration.enabled:false}")
    private boolean migrationEnabled;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final MongoTemplate mongoTemplate;
    private final Cluster cluster;

    public DatabaseMigrationUtil(MongoTemplate mongoTemplate, Cluster cluster) {
        this.mongoTemplate = mongoTemplate;
        this.cluster = cluster;
    }

    /**
     * Initialize MongoDB indexes and other required setup
     */
//    @PostConstruct
    @EventListener(ApplicationReadyEvent.class)
    public void initializeMongoDB() {
        LOGGER.info("Setting up MongoDB indexes...");

        // Create text indexes for hotel search
        TextIndexDefinition hotelTextIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
            .onField("name", 3.0f)
            .onField("description", 2.0f)
            .onField("city", 2.0f)
            .onField("country", 2.0f)
            .onField("state", 1.0f)
            .onField("address", 1.0f).named("hotels-index")
            .build();

        mongoTemplate.indexOps("hotel").ensureIndex(hotelTextIndex);
        LOGGER.info("Created text index for hotel collection");

        // Create indexes for airport collection
        IndexOperations airportIndexOps = mongoTemplate.indexOps("airport");
        airportIndexOps.ensureIndex(new Index().on("faa", Sort.Direction.ASC));
        airportIndexOps.ensureIndex(new Index().on("icao", Sort.Direction.ASC));
        airportIndexOps.ensureIndex(new Index().on("airportname", Sort.Direction.ASC));
        LOGGER.info("Created indexes for airport collection");

        // Create indexes for flightpath collection
        IndexOperations flightpathIndexOps = mongoTemplate.indexOps("route");
        flightpathIndexOps.ensureIndex(new Index().on("sourceairport", Sort.Direction.ASC)
                                                  .on("destinationairport", Sort.Direction.ASC).named("route_idx"));
        LOGGER.info("Created indexes for flightpath collection");

        // Create indexes for bookings collection
        IndexOperations bookingIndexOps = mongoTemplate.indexOps("bookings");
        bookingIndexOps.ensureIndex(new Index().on("username", Sort.Direction.ASC));
        LOGGER.info("Created indexes for bookings collection");

        // Create indexes for users collection
        IndexOperations userIndexOps = mongoTemplate.indexOps("users");
        userIndexOps.ensureIndex(new Index().on("username", Sort.Direction.ASC).unique());
        LOGGER.info("Created indexes for users collection");

        // If migration is enabled, perform data migration
        if (migrationEnabled) {
            LOGGER.info("Data migration is enabled");
            try {
                executorService.submit(() -> {
                    migrateData();
                });
            } catch (Exception e) {
                LOGGER.error("Error during data migration", e);
            }
        }
    }

    /**
     * Migrate data from Couchbase to MongoDB
     *
     * This method would implement the actual migration logic, but for now it's just a placeholder
     */
    private void migrateData() {
        try {
            LOGGER.info("Sleep thread for 10 seconds to ensure MongoDB and Couch is ready");
            Thread.sleep(10000); // Delay to ensure MongoDB is ready

            LOGGER.info("Starting data migration from Couchbase to MongoDB");

            String queryStatement = "SELECT * FROM system:keyspaces";
            LOGGER.info("Query Statement: " + queryStatement);
            String query = String.format(queryStatement);
            QueryResult queryResult = cluster.query(query);
            List<JsonObject> jsonObjectList = mapRowToObject(queryResult);

            List<CouchKeyspaces> couchKeyspacesList = createCouchKeyspacesResponse(jsonObjectList);
            migrateAllKeyspaces(couchKeyspacesList);

            LOGGER.info("Data migration completed successfully");
        } catch(Exception e) {
            LOGGER.error("Error during data migration in method migrateData", e);
        }
    }

    private List<CouchKeyspaces> createCouchKeyspacesResponse(final List<JsonObject> jsonObjectList) {
        List<CouchKeyspaces> couchKeyspacesList = new ArrayList<>();

        for (JsonObject jsonObject : jsonObjectList) {
            if (Objects.nonNull(jsonObject.get("keyspaces"))) {
                JsonObject jsonObjectContent = (JsonObject) jsonObject.get("keyspaces");
                if (Objects.nonNull(jsonObjectContent.getString("bucket")) && Objects.nonNull(jsonObjectContent.getString("scope")) &&
                        Objects.nonNull(jsonObjectContent.getString("id"))) {
                    CouchKeyspaces couchKeyspaces = new CouchKeyspaces();
                    couchKeyspaces.setBucketName(jsonObjectContent.getString("bucket"));
                    couchKeyspaces.setScopeName(jsonObjectContent.getString("scope"));
                    couchKeyspaces.setCollectionName(jsonObjectContent.getString("id"));
                    couchKeyspacesList.add(couchKeyspaces);
                }
            }
        }
        return couchKeyspacesList;
    }

    private void migrateAllKeyspaces(List<CouchKeyspaces> couchKeyspacesList) {
        List<String> failedCollectionList = new ArrayList<>();
        for (CouchKeyspaces couchKeyspaces : couchKeyspacesList) {
            int retryCount = 0;
            while (retryCount < 3) {
                try {
                    String bucketName = couchKeyspaces.getBucketName();
                    String scopeName = couchKeyspaces.getScopeName();
                    String collectionName = couchKeyspaces.getCollectionName();
                    LOGGER.info("MongoDB migrating for collection {}", collectionName);
                    String queryStatement = String.format("SELECT META().id as _id, `%s`.* FROM `%s`.`%s`.`%s`", collectionName, bucketName, scopeName, collectionName);
                    LOGGER.info("Migration query {}", queryStatement);
                    QueryResult queryResult = cluster.query(queryStatement);
                    List<JsonObject> jsonObjectList =  mapRowToObject(queryResult);

                    String mongoCollectionName = collectionName;
                    LOGGER.info("Mongo Collection Name: " + mongoCollectionName);
                    LOGGER.info("Couch Scope Collection Size: " + jsonObjectList.size());

                    if(jsonObjectList.size() > 0) {
                        insertMultipleDocumentsInMongo(mongoCollectionName, jsonObjectList);
                    }
                    retryCount = 3; // Exit loop after successful migration
                } catch (Exception e) {
                    LOGGER.error("Error during migration for keyspace: " + couchKeyspaces.getCollectionName(), e);
                    retryCount++;
                    if(retryCount == 3) {
                        failedCollectionList.add(couchKeyspaces.getBucketName() + "_" + couchKeyspaces.getScopeName() + "_" + couchKeyspaces.getCollectionName());
                    }
                }
            }

        }
        LOGGER.info("Migration failed for collections {}", failedCollectionList);
        LOGGER.info("Migration completed for all other keyspaces");
    }

    public void insertMultipleDocumentsInMongo(String collectionName, List<JsonObject> jsonObjectList) {
        MongoCollection<Document> mongoCollection = mongoTemplate.getCollection(collectionName);
        List<Document> documentList = new ArrayList<>();
        int chunkSize = 100;
        int totalDocuments = jsonObjectList.size();
        for (int i=0; i < totalDocuments; i++) {
            Document document = parseWithNormalizedKeys(jsonObjectList.get(i));
            document.put("_id", jsonObjectList.get(i).getString("_id")); // Ensure _id is set
            documentList.add(document);
//            documentList.add(Document.parse(jsonObjectList.get(i).toString()));
            if((i != 0 && i % chunkSize == 0) || i == totalDocuments - 1) {
                LOGGER.info("Inserting documents {} to {} out of {} in {}", i - chunkSize, i, totalDocuments, collectionName);
                mongoCollection.insertMany(documentList);
                documentList.clear();
            }
        }
    }

    private List<JsonObject> mapRowToObject(QueryResult queryResult) {
        try {
            return queryResult.rowsAsObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to map document content to jsonObject", e);
        }
    }

    private Document parseWithNormalizedKeys(JsonObject cbJsonObject) {
        JsonObject normalized = normalizeJsonKeys(cbJsonObject);
        return Document.parse(normalized.toString());
    }

    private JsonObject normalizeJsonKeys(JsonObject input) {
        JsonObject result = JsonObject.create();

        for (Map.Entry<String, Object> entry : input.toMap().entrySet()) {
            String normalizedKey = normalizeKey(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof Map) {
                result.put(normalizedKey, normalizeJsonKeys(JsonObject.from((Map<String, Object>) value)));
            } else if (value instanceof Iterable) {
                result.put(normalizedKey, normalizeJsonArray(value));
            } else {
                result.put(normalizedKey, value);
            }
        }

        return result;
    }

    private JsonArray normalizeJsonArray(Object value) {
        JsonArray array = JsonArray.create();

        for (Object item : (Iterable<?>) value) {
            if (item instanceof Map) {
                array.add(normalizeJsonKeys(JsonObject.from((Map<String, Object>) item)));
            } else if (item instanceof Iterable) {
                array.add(normalizeJsonArray(item));
            } else {
                array.add(item);
            }
        }

        return array;
    }

    private String normalizeKey(String key) {
        return key.toLowerCase()
                .replaceAll("\\s+", "_")
                .replaceAll("[()]", "")
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
