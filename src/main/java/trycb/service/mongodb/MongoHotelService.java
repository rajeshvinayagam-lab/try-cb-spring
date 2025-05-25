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

package trycb.service.mongodb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoCollection;

import trycb.config.mongodb.MongoHotelRepository;
import trycb.model.Result;
import trycb.service.HotelService;

/**
 * MongoDB implementation of the HotelService
 */
@Service
@Profile("mongodb")
public class MongoHotelService implements HotelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoHotelService.class);

    private final MongoHotelRepository hotelRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public MongoHotelService(MongoHotelRepository hotelRepository, MongoTemplate mongoTemplate) {
        this.hotelRepository = hotelRepository;
        this.mongoTemplate = mongoTemplate;
    }

//    public Result<List<Map<String, Object>>> findHotels(String location, String description) {
//        MongoCollection<Document> collection = mongoTemplate.getCollection("hotel");
//
//        List<Document> must = new ArrayList<>();
//        must.add(new Document("text", new Document("query", "hotel")
//                .append("path", "type")));
//
//        if (location != null && !location.isEmpty() && !"*".equals(location)) {
//            List<String> locationFields = Arrays.asList("country", "city", "state", "address");
//            List<Document> should = new ArrayList<>();
//            for (String field : locationFields) {
//                should.add(new Document("phrase", new Document("query", location).append("path", field)));
//            }
//            must.add(new Document("compound", new Document("should", should)));
//        }
//
//        if (description != null && !description.isEmpty() && !"*".equals(description)) {
//            List<String> descFields = Arrays.asList("description", "name");
//            List<Document> should = new ArrayList<>();
//            for (String field : descFields) {
//                should.add(new Document("phrase", new Document("query", description).append("path", field)));
//            }
//            must.add(new Document("compound", new Document("should", should)));
//        }
//
//        Document searchStage = new Document("$search", new Document("index", "hotels-index")
//                .append("compound", new Document("must", must)));
//
//        List<Document> pipeline = Arrays.asList(
//                searchStage,
//                new Document("$limit", 100),
//                new Document("$project", new Document("name", 1)
//                        .append("description", 1)
//                        .append("address", 1)
//                        .append("score", new Document("$meta", "searchScore")))
//        );
//
//        List<Document> results = collection.aggregate(pipeline).into(new ArrayList<>());
//
//        List<Map<String, Object>> resultList = new ArrayList<>();
//        for (Document doc : results) {
//            resultList.add(doc);
//        }
//
//        String queryType = "MongoDB Atlas FTS search - scoped to: hotel within fields country, city, state, address, name, description";
//        return Result.of(resultList, queryType);
//    }

    public Result<List<Map<String, Object>>> findHotels(String location, String description) {
        MongoCollection<Document> collection = mongoTemplate.getCollection("hotel");

        // Build the text search string
        StringBuilder searchQuery = new StringBuilder();
        if (location != null && !location.isEmpty() && !"*".equals(location)) {
            searchQuery.append(" ").append(location);
        }
        if (description != null && !description.isEmpty() && !"*".equals(description)) {
            searchQuery.append(" ").append(description);
        }

        // Text match stage
        Document matchStage = new Document("$match",
                new Document("$text", new Document("$search", searchQuery.toString()))
        );

        // Project stage with textScore
        Document projectStage = new Document("$project", new Document("_id", 0)
                .append("name", 1)
                .append("description", 1)
                .append("address", 1)
//                .append("score", new Document("$meta", "textScore"))
        );

        // Sort by textScore (optional but recommended)
//        Document sortStage = new Document("$sort", new Document("score", new Document("$meta", "textScore")));

        // Limit
        Document limitStage = new Document("$limit", 100);

        // Build pipeline
//        List<Document> pipeline = Arrays.asList(matchStage, sortStage, projectStage, limitStage);
        List<Document> pipeline = Arrays.asList(matchStage, projectStage, limitStage);

        // Run aggregation
        List<Document> results = collection.aggregate(pipeline).into(new ArrayList<>());

        // Transform results
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Document doc : results) {
            resultList.add(doc);
        }

        String queryType = "Local MongoDB Text Search - using $text index across type, country, city, state, address, name, description";
        return Result.of(resultList, queryType);
    }


    @Override
    public Result<List<Map<String, Object>>> findHotels(String description) {
        return findHotels("*", description);
    }

    @Override
    public Result<List<Map<String, Object>>> findAllHotels() {
        return findHotels("*", "*");
    }
}
