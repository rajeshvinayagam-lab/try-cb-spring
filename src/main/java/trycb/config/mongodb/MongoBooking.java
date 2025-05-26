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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB representation of a Booking document
 */
@Document(collection = "bookings")
@Data
public class MongoBooking {
    @Id
    private String bookingId;

    private String name;
    private String sourceairport;
    private String destinationairport;
    private String flight;
    public String utc;
    public String airlineid;
    public String date;
    private Integer price;

    public Integer day;
    private String type = "booking";

    public Map<String,Object> toMap(){
        Map<String,Object> map= new HashMap<>(6);
        map.put("name", name);
        map.put("bookingId",bookingId);
        map.put("sourceairport", sourceairport);
        map.put("destinationairport", destinationairport);
        map.put("airlineid", airlineid);
        map.put( "utc", utc);
        map.put("date", date);
        map.put("flight", flight);
        map.put("day", day);
        return map;
    }
}
