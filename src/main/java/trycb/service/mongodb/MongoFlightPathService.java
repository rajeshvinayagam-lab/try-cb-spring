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
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
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
public class MongoFlightPathService implements FlightPathService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoFlightPathService.class);

    private final MongoFlightPathRepository flightPathRepository;

    @Autowired
    public MongoFlightPathService(MongoFlightPathRepository flightPathRepository) {
        this.flightPathRepository = flightPathRepository;
    }

    public Result<List<Map<String, Object>>> findAll(String from, String to, Calendar leave) {
        int day = leave.get(Calendar.DAY_OF_WEEK);
        LOGGER.info("Searching for flights from {} to {} on day {}", from, to, day);

        List<MongoFlightPath> flightPaths = flightPathRepository.findFlights(from, to, day);
        Random rand = new Random();
        List<Map<String, Object>> data = new LinkedList<Map<String, Object>>();
        for (MongoFlightPath f : flightPaths) {
            Map<String, Object> row = f.toMap();
            row.put("flighttime", rand.nextInt(8000));
            row.put("price", Math.ceil((Integer) row.get("flighttime") / 8 * 100) / 100);
            row.put("date", leave.getTime());
            data.add(row);
        }

        String querytype = "MongoDB Query - scoped to inventory: ";
        return Result.of(data, querytype);
    }

}
