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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import trycb.config.FlightPath;
import trycb.config.mongodb.MongoFlightPath;
import trycb.config.mongodb.MongoFlightPathRepository;
import trycb.model.Result;
import trycb.service.FlightPathService;

/**
 * MongoDB implementation of the FlightPathService
 */
@Service
@Profile("mongodb")
public class MongoFlightPathService implements FlightPathService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoFlightPathService.class);
    
    private final MongoFlightPathRepository flightPathRepository;
    private final MongoTemplate mongoTemplate;
    
    @Autowired
    public MongoFlightPathService(MongoFlightPathRepository flightPathRepository, MongoTemplate mongoTemplate) {
        this.flightPathRepository = flightPathRepository;
        this.mongoTemplate = mongoTemplate;
    }
    
    @Override
    public Result<List<FlightPath>> findFlights(String from, String to, String leave) {
        try {
            int day = Integer.parseInt(leave);
            
            // Log query parameters
            LOGGER.info("Searching for flights from {} to {} on day {}", from, to, day);
            
            // Use the repository to find flights using aggregation
            List<MongoFlightPath> mongoFlights = flightPathRepository.findFlights(from, to, day);
            List<FlightPath> flightPaths = convertToFlightPaths(mongoFlights);
            
            String queryType = "MongoDB aggregation query for flights from " + from + " to " + to + " on day " + day;
            return Result.of(flightPaths, queryType);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid day format: {}", leave, e);
            return Result.error("Invalid day format: " + leave);
        } catch (Exception e) {
            LOGGER.error("Error searching for flights", e);
            return Result.error("Error searching for flights: " + e.getMessage());
        }
    }
    
    /**
     * Convert MongoDB flight documents to FlightPath objects
     */
    private List<FlightPath> convertToFlightPaths(List<MongoFlightPath> mongoFlights) {
        List<FlightPath> result = new ArrayList<>();
        
        for (MongoFlightPath mongoFlight : mongoFlights) {
            FlightPath flightPath = new FlightPath();
            flightPath.setId(mongoFlight.getId());
            flightPath.setName(mongoFlight.getName());
            flightPath.setAirlineid(mongoFlight.getAirlineid());
            flightPath.setFlight(mongoFlight.getFlight());
            flightPath.setDay(mongoFlight.getDay());
            flightPath.setUtc(mongoFlight.getUtc());
            flightPath.setSourceairport(mongoFlight.getSourceairport());
            flightPath.setDestinationairport(mongoFlight.getDestinationairport());
            flightPath.setEquipment(mongoFlight.getEquipment());
            
            result.add(flightPath);
        }
        
        return result;
    }
}