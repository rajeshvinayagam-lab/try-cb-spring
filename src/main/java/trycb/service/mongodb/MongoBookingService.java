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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import trycb.config.Booking;
import trycb.config.mongodb.MongoBooking;
import trycb.config.mongodb.MongoBookingRepository;
import trycb.model.Result;
import trycb.service.BookingService;

/**
 * MongoDB implementation of the BookingService
 */
@Service
@Profile("mongodb")
public class MongoBookingService implements BookingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoBookingService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    private final MongoBookingRepository bookingRepository;
    
    @Autowired
    public MongoBookingService(MongoBookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }
    
    @Override
    public Result<List<Booking>> findBookingsByUser(String username) {
        try {
            // Find bookings for the user
            List<MongoBooking> mongoBookings = bookingRepository.findByUsername(username);
            List<Booking> bookings = convertToBookings(mongoBookings);
            
            String queryType = "MongoDB query for bookings by username: " + username;
            return Result.of(bookings, queryType);
        } catch (Exception e) {
            LOGGER.error("Error finding bookings for user {}", username, e);
            return Result.error("Error finding bookings: " + e.getMessage());
        }
    }
    
    @Override
    public Result<Booking> createBooking(String username, Booking newBooking) {
        try {
            // Set the username and generate an ID
            newBooking.setUsername(username);
            if (newBooking.getId() == null || newBooking.getId().isEmpty()) {
                newBooking.setId("booking::" + UUID.randomUUID().toString());
            }
            
            // Set booked on date if not provided
            if (newBooking.getBookedon() == null || newBooking.getBookedon().isEmpty()) {
                newBooking.setBookedon(DATE_FORMAT.format(new Date()));
            }
            
            // Convert to MongoDB entity and save
            MongoBooking mongoBooking = convertToMongoBooking(newBooking);
            mongoBooking = bookingRepository.save(mongoBooking);
            
            // Convert back to Booking
            Booking savedBooking = convertToBooking(mongoBooking);
            
            String queryType = "MongoDB insert for new booking";
            return Result.of(savedBooking, queryType);
        } catch (Exception e) {
            LOGGER.error("Error creating booking for user {}", username, e);
            return Result.error("Error creating booking: " + e.getMessage());
        }
    }
    
    /**
     * Convert a MongoDB booking to the Booking entity
     */
    private Booking convertToBooking(MongoBooking mongoBooking) {
        Booking booking = new Booking();
        booking.setId(mongoBooking.getId());
        booking.setUsername(mongoBooking.getUsername());
        booking.setFlight(mongoBooking.getFlight());
        booking.setPrice(mongoBooking.getPrice());
        booking.setDate(mongoBooking.getDate());
        booking.setSourceairport(mongoBooking.getSourceairport());
        booking.setDestinationairport(mongoBooking.getDestinationairport());
        booking.setBookedon(mongoBooking.getBookedon());
        
        return booking;
    }
    
    /**
     * Convert a list of MongoDB bookings to Booking entities
     */
    private List<Booking> convertToBookings(List<MongoBooking> mongoBookings) {
        List<Booking> result = new ArrayList<>();
        
        for (MongoBooking mongoBooking : mongoBookings) {
            result.add(convertToBooking(mongoBooking));
        }
        
        return result;
    }
    
    /**
     * Convert a Booking entity to a MongoDB booking
     */
    private MongoBooking convertToMongoBooking(Booking booking) {
        MongoBooking mongoBooking = new MongoBooking();
        mongoBooking.setId(booking.getId());
        mongoBooking.setUsername(booking.getUsername());
        mongoBooking.setFlight(booking.getFlight());
        mongoBooking.setPrice(booking.getPrice());
        mongoBooking.setDate(booking.getDate());
        mongoBooking.setSourceairport(booking.getSourceairport());
        mongoBooking.setDestinationairport(booking.getDestinationairport());
        mongoBooking.setBookedon(booking.getBookedon());
        
        return mongoBooking;
    }
}