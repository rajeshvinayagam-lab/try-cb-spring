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

package trycb.config.mongodb;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * MongoDB repository for FlightPath documents
 */
@Repository("mongoFlightPathRepository")
@Profile("mongodb")
public interface MongoFlightPathRepository extends MongoRepository<MongoFlightPath, String> {

    /**
     * Find flights by source and destination airports, and day
     */
    @Query("{$and: [{'sourceairport': ?0}, {'destinationairport': ?1}, {'day': ?2}]}")
    List<MongoFlightPath> findBySourceAndDestinationAndDay(String sourceAirport, String destinationAirport, int day);

    /**
     * Finds flights using an aggregation pipeline to join data from multiple collections (airport, route, airline)
     */
    @Aggregation(pipeline = {
            "{ $match: { sourceairport: { $exists: true }, destinationairport: { $exists: true } } }",
            "{ $lookup: { from: 'airport', localField: 'sourceairport', foreignField: 'faa', as: 'src' } }",
            "{ $unwind: '$src' }",
            "{ $match: { 'src.airportname': ?0 } }",
            "{ $lookup: { from: 'airport', localField: 'destinationairport', foreignField: 'faa', as: 'dst' } }",
            "{ $unwind: '$dst' }",
            "{ $match: { 'dst.airportname': ?1 } }",
            "{ $lookup: { from: 'airline', localField: 'airlineid', foreignField: '_id', as: 'airline' } }",
            "{ $unwind: '$airline' }",
            "{ $unwind: '$schedule' }",
            "{ $match: { 'schedule.day': ?2 } }",
            "{ $project: { " +
                    "id: '$_id', " +
                    "airlineid: '$airlineid', " +
                    "name: '$airline.name', " +
                    "flight: '$schedule.flight', " +
                    "day: '$schedule.day', " +
                    "utc: '$schedule.utc', " +
                    "sourceairport: '$sourceairport', " +
                    "destinationairport: '$destinationairport', " +
                    "equipment: '$equipment' " +
                    "} }"
    })
    List<MongoFlightPath> findFlights(String sourceAirport, String destinationAirport, int day);
}
