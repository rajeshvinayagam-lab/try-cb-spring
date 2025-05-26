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

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import trycb.config.mongodb.MongoBooking;
import trycb.config.mongodb.MongoBookingRepository;
import trycb.config.mongodb.MongoUser;
import trycb.config.mongodb.MongoUserRepository;
import trycb.model.Result;
import trycb.service.TokenService;
import trycb.service.TenantUserService;

/**
 * MongoDB implementation of the UserService
 */
@Service
public class MongoTenantUserService implements TenantUserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoTenantUserService.class);

    private final TokenService jwtService;
    private final MongoUserRepository userRepository;
    private final MongoBookingRepository bookingRepository;


    @Autowired
    public MongoTenantUserService(MongoBookingRepository bookingRepository, MongoUserRepository userRepository, TokenService jwtService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Override
    public Result<Map<String, Object>> createLogin(final String tenant, final String username, final String password, DurabilityLevel expiry) {
        Optional<MongoUser> userOpt = userRepository.findByTenantAndUsername(tenant, username);
        if (userOpt.isPresent()) {
            throw new AuthenticationCredentialsNotFoundException("Invalid Tenant - Username already exists");
        }

        String md5Password = password;
        if(!isValidMD5(password)) {
            md5Password = toMD5(password);
        }
        String passHash = BCrypt.hashpw(md5Password, BCrypt.gensalt());

        // Create the user
        MongoUser mongoUser = new MongoUser();
        mongoUser.setId("user::" + username);
        mongoUser.setTenant(tenant);
        mongoUser.setUsername(username);
        mongoUser.setPassword(passHash);

        // Save the user
        userRepository.save(mongoUser);

        try {
            // Return the success response
            Map<String, Object> data = new HashMap<>();
            data.put("token", jwtService.buildToken(username));
            return Result.of(data, "MongoDB: createLogin for the Tenant user");
        } catch (Exception e) {
            throw new AuthenticationServiceException("There was an error creating account");
        }
    }

    @Override
    public Result<Map<String, Object>> login(final String tenant, final String username, final String password) throws JsonProcessingException {
        try {
            Optional<MongoUser> userOpt = userRepository.findByTenantAndUsername(tenant, username);

            if (!userOpt.isPresent()) {
                throw new AuthenticationCredentialsNotFoundException("Invalid tenant - username not found");
            }

            MongoUser user = userOpt.get();
            LOGGER.info("passhash {} and password {}", user.getPassword(), password);
            // Check the password
            String md5Password = password;
            if(!isValidMD5(password)) {
                md5Password = toMD5(password);
            }
            if (!BCrypt.checkpw(md5Password, user.getPassword())) {
                throw new AuthenticationCredentialsNotFoundException("Incorrect password");
            }

            // Create a token
            String token = jwtService.buildToken(username);

            // Return the success response
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
//            result.put("user", username);

            return Result.of(result, "MongoDB - Login successful");
        } catch (Exception e) {
            LOGGER.error("Error logging in", e);
            throw new AuthenticationCredentialsNotFoundException("Error logging in: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> registerFlightForUser(final String tenant, final String username, final JsonArray newFlights) {
        LOGGER.info("Inside registerFlightForUser tenant {} username {} with flights {}", tenant, username, newFlights);
        if (newFlights == null) {
            throw new IllegalArgumentException("No flights in payload");
        }

        Optional<MongoUser> userOpt = userRepository.findByTenantAndUsername(tenant, username);

        if (!userOpt.isPresent()) {
            throw new AuthenticationCredentialsNotFoundException("Invalid tenant - username");
        }
        MongoUser userData = userOpt.get();

        JsonArray added = new JsonArray();
        ArrayList<String> allBookedFlights = null;
        if (userData.getFlightIds() != null) {
            allBookedFlights = new ArrayList(Arrays.asList(userData.getFlightIds())); // ArrayList(Arrays.asList(newFlights));
        } else {
            allBookedFlights = new ArrayList<>(newFlights.size());
        }

        for (Object newFlight : newFlights) {
            checkFlight(newFlight);
            com.google.gson.JsonObject t = ((com.google.gson.JsonObject) newFlight);
            t.addProperty("bookedon", "try-cb-spring");
            MongoBooking booking = new MongoBooking();
            booking.setBookingId(UUID.randomUUID().toString());
            booking.setName(t.get("name").getAsString());
            booking.setSourceairport(t.get("sourceairport").getAsString());
            booking.setDestinationairport(t.get("destinationairport").getAsString());
            booking.setFlight(t.get("flight").getAsString());
            if(Objects.nonNull(t.get("utc"))) {
                booking.setUtc(t.get("utc").getAsString());
            }
            if(Objects.nonNull(t.get("airlineid"))) {
                booking.setAirlineid(t.get("airlineid").getAsString());
            }
            if(Objects.nonNull(t.get("date"))) {
                booking.setDate(t.get("date").getAsString());
            }
            booking.setPrice(t.get("price").getAsInt());
            if(Objects.nonNull(t.get("day"))) {
                booking.setDay(t.get("day").getAsInt());
            }
            bookingRepository.save(booking);
            allBookedFlights.add(booking.getBookingId());
            added.add(t);
        }

        userData.setFlightIds(allBookedFlights.toArray(new String[] {}));
        userRepository.save(userData);

        JsonObject responseData = new JsonObject();
        responseData.add("added", added);

        String queryType = String.format("MongoDB update - scoped to %s.user: for bookings field in document %s", tenant,
                username);
        return Result.of(convertJsonObjectToMap(responseData), queryType);
    }

    @Override
    public Result<List<Map<String, Object>>> getFlightsForUser(final String tenant, final String username) {
        LOGGER.info("Inside getFlightsForUser tenant {} username {}", tenant, username);
        Optional<MongoUser> userOpt = userRepository.findByTenantAndUsername(tenant, username);

        if (!userOpt.isPresent()) {
            throw new AuthenticationCredentialsNotFoundException("Invalid tenant - username");
        }

        MongoUser userData = userOpt.get();
        String[] flights = userData.getFlightIds();
        if (flights == null) {
            return Result.of(Collections.emptyList());
        }

        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (String flightId : flights) {
            Optional<MongoBooking> res;
            try {
                res = bookingRepository.findByBookingId(flightId);
            } catch (DocumentNotFoundException ex) {
                throw new RuntimeException("Unable to retrieve flight id " + flightId);
            }
            Map<String, Object> flight = res.get().toMap();
            results.add(flight);
        }

        String queryType = String.format("MongoDB get - scoped to %s.user: for %d bookings in document %s", tenant,
                results.size(), username);
        return Result.of(results, queryType);
    }

    private static void checkFlight(Object f) {
        if (f == null || !(f instanceof JsonObject)) {
            throw new IllegalArgumentException("Each flight must be a non-null object");
        }
        JsonObject flight = (JsonObject) f;
        if (!flight.has("name") || !flight.has("date") || !flight.has("sourceairport")
                || !flight.has("destinationairport")) {
            throw new IllegalArgumentException("Malformed flight inside flights payload");
        }
    }

    private Map<String, Object> convertJsonObjectToMap(JsonObject jsonObject) {
        Map<String, Object> map = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            map.put(entry.getKey(), convertJsonElement(entry.getValue()));
        }

        return map;
    }

    private Object convertJsonElement(JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return primitive.getAsNumber();
            if (primitive.isString()) return primitive.getAsString();
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Object> list = new ArrayList<>();
            for (JsonElement item : array) {
                list.add(convertJsonElement(item));
            }
            return list;
        } else if (element.isJsonObject()) {
            return convertJsonObjectToMap(element.getAsJsonObject());
        }

        return null; // fallback
    }

    public boolean isValidMD5(String input) {
        return input != null && input.matches("^[a-fA-F0-9]{32}$");
    }

    public String toMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 error", e);
        }
    }
}
