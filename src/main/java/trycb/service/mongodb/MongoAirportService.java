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
import org.springframework.stereotype.Service;

import trycb.config.Airport;
import trycb.config.mongodb.MongoAirport;
import trycb.config.mongodb.MongoAirportRepository;
import trycb.model.Result;
import trycb.service.AirportService;

/**
 * MongoDB implementation of the AirportService
 */
@Service
@Profile("mongodb")
public class MongoAirportService implements AirportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoAirportService.class);
    
    private final MongoAirportRepository airportRepository;
    
    @Autowired
    public MongoAirportService(MongoAirportRepository airportRepository) {
        this.airportRepository = airportRepository;
    }
    
    @Override
    public Result<List<Airport>> findAirports(String name) {
        try {
            List<MongoAirport> mongoAirports = airportRepository.findByAirportnameStartsWith(name.toUpperCase());
            List<Airport> airports = convertToAirports(mongoAirports);
            
            String queryType = "MongoDB query for airports with name starting with: " + name;
            return Result.of(airports, queryType);
        } catch (Exception e) {
            LOGGER.error("Error finding airports by name {}", name, e);
            return Result.error("Error finding airports: " + e.getMessage());
        }
    }
    
    @Override
    public Result<List<Airport>> findAirportsByFaa(String faa) {
        try {
            List<MongoAirport> mongoAirports = airportRepository.findByFaa(faa);
            List<Airport> airports = convertToAirports(mongoAirports);
            
            String queryType = "MongoDB query for airports with FAA code: " + faa;
            return Result.of(airports, queryType);
        } catch (Exception e) {
            LOGGER.error("Error finding airports by FAA {}", faa, e);
            return Result.error("Error finding airports: " + e.getMessage());
        }
    }
    
    @Override
    public Result<List<Airport>> findAirportsByIcao(String icao) {
        try {
            List<MongoAirport> mongoAirports = airportRepository.findByIcao(icao);
            List<Airport> airports = convertToAirports(mongoAirports);
            
            String queryType = "MongoDB query for airports with ICAO code: " + icao;
            return Result.of(airports, queryType);
        } catch (Exception e) {
            LOGGER.error("Error finding airports by ICAO {}", icao, e);
            return Result.error("Error finding airports: " + e.getMessage());
        }
    }
    
    /**
     * Convert MongoDB airports to Airport entities
     */
    private List<Airport> convertToAirports(List<MongoAirport> mongoAirports) {
        List<Airport> result = new ArrayList<>();
        
        for (MongoAirport mongoAirport : mongoAirports) {
            Airport airport = new Airport();
            airport.setId(mongoAirport.getId());
            airport.setFaa(mongoAirport.getFaa());
            airport.setIcao(mongoAirport.getIcao());
            airport.setAirportname(mongoAirport.getAirportname());
            airport.setCity(mongoAirport.getCity());
            airport.setCountry(mongoAirport.getCountry());
            airport.setGeo_lat(mongoAirport.getGeo_lat());
            airport.setGeo_long(mongoAirport.getGeo_long());
            
            result.add(airport);
        }
        
        return result;
    }
}