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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import trycb.config.mongodb.MongoHotel;
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
    
    @Override
    public Result<List<Map<String, Object>>> findHotels(String location, String description) {
        // Build text search criteria
        List<String> searchTerms = new ArrayList<>();
        if (location != null && !location.isEmpty() && !"*".equals(location)) {
            searchTerms.add(location);
        }
        
        if (description != null && !description.isEmpty() && !"*".equals(description)) {
            searchTerms.add(description);
        }
        
        List<MongoHotel> hotels;
        String queryType;
        
        if (searchTerms.isEmpty()) {
            // If no search terms, get all hotels
            hotels = hotelRepository.findByType("hotel");
            queryType = "MongoDB query for all hotels";
        } else {
            // Create text search criteria
            TextCriteria criteria = TextCriteria.forDefaultLanguage();
            criteria.matchingAny(searchTerms.toArray(new String[0]));
            
            // Create and execute text query
            TextQuery query = TextQuery.queryText(criteria).sortByScore();
            query.limit(100);
            
            logQuery("Text search for terms: " + String.join(", ", searchTerms));
            hotels = mongoTemplate.find(query, MongoHotel.class);
            queryType = "MongoDB text search on hotel collection";
        }
        
        return Result.of(convertToMapList(hotels), queryType);
    }
    
    @Override
    public Result<List<Map<String, Object>>> findHotels(String description) {
        return findHotels("*", description);
    }
    
    @Override
    public Result<List<Map<String, Object>>> findAllHotels() {
        List<MongoHotel> hotels = hotelRepository.findByType("hotel");
        String queryType = "MongoDB query for all hotels";
        return Result.of(convertToMapList(hotels), queryType);
    }
    
    /**
     * Convert MongoDB hotel documents to the same map format used by the Couchbase implementation
     */
    private List<Map<String, Object>> convertToMapList(List<MongoHotel> hotels) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (MongoHotel hotel : hotels) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", hotel.getName());
            map.put("description", hotel.getDescription());
            
            // Build the address string in the same format as the Couchbase implementation
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
    
    /**
     * Helper method to log the executing query
     */
    private static void logQuery(String query) {
        LOGGER.info("Executing MongoDB Query: {}", query);
    }
}