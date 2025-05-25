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

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB representation of a FlightPath document
 */
@Document(collection = "route")
@CompoundIndexes({
    @CompoundIndex(name = "route_idx", def = "{'sourceairport': 1, 'destinationairport': 1}")
})
public class MongoFlightPath {
    @Id
    private String id;

    private String name; // airline name
    private String airlineid;
    private String flight;
    private int day;
    private String utc;
    private String sourceairport;
    private String destinationairport;
    private String equipment;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAirlineid() {
        return airlineid;
    }

    public void setAirlineid(String airlineid) {
        this.airlineid = airlineid;
    }

    public String getFlight() {
        return flight;
    }

    public void setFlight(String flight) {
        this.flight = flight;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public String getUtc() {
        return utc;
    }

    public void setUtc(String utc) {
        this.utc = utc;
    }

    public String getSourceairport() {
        return sourceairport;
    }

    public void setSourceairport(String sourceairport) {
        this.sourceairport = sourceairport;
    }

    public String getDestinationairport() {
        return destinationairport;
    }

    public void setDestinationairport(String destinationairport) {
        this.destinationairport = destinationairport;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public Map<String,Object> toMap(){
        Map<String,Object> map= new HashMap<>(6);
        map.put("name",name);
        map.put("flight", flight);
        map.put("airlineid", airlineid);
        map.put("utc", utc);
        map.put("day", day);
        map.put("sourceairport", sourceairport);
        map.put("destinationairport", destinationairport);
        map.put("equipment", equipment);
        return map;
    }
}
