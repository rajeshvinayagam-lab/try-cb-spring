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

package trycb.service;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.UpsertOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import trycb.config.Booking;
import trycb.config.BookingRepository;
import trycb.config.User;
import trycb.config.UserRepository;
import trycb.model.Result;
import trycb.service.mongodb.MongoTenantUserService;

@Service
public class TenantUser implements TenantUserService {

  private final TokenService jwtService;
  private final UserRepository userRepository;
  private final BookingRepository bookingRepository;

  private static final Logger LOGGER = LoggerFactory.getLogger(TenantUser.class);

  public TenantUser(TokenService tokenService, UserRepository userRepository, BookingRepository bookingRepository) {
    this.jwtService = tokenService;
    this.userRepository = userRepository;
    this.bookingRepository = bookingRepository;
  }

  /**
   * Try to log the given tenant user in.
   */
  public Result<Map<String, Object>> login(final String tenant, final String username, final String password) throws JsonProcessingException {
    UserRepository userRepository = this.userRepository.withScope(tenant);
    String queryType = String.format("KV get - scoped to %s.users: for password field in document %s", tenant,
        username);
    Optional<User> userHolder;
    try {
      userHolder = userRepository.findById(username);
    } catch (DocumentNotFoundException ex) {
      throw new AuthenticationCredentialsNotFoundException("User not found - Bad Username or Password");
    }
    User res = userHolder.get();
    LOGGER.info("passhash {} and password {}", res.password, password);
    String md5Password = password;
    if(!isValidMD5(password)) {
      md5Password = toMD5(password);
    }
    if (BCrypt.checkpw(md5Password, res.password)) {
      Map<String, Object> data = JsonObject.create().put("token", jwtService.buildToken(username)).toMap();
      return Result.of(data, queryType);
    } else {
      throw new AuthenticationCredentialsNotFoundException("Incorrect Password");
    }
  }

  /**
   * Create a tenant user.
   */
  public Result<Map<String, Object>> createLogin(final String tenant, final String username, final String password,
      DurabilityLevel expiry) {
    UserRepository userRepository = this.userRepository.withScope(tenant);
    String md5Password = password;
    if(!isValidMD5(password)) {
      md5Password = toMD5(password);
    }
    String passHash = BCrypt.hashpw(md5Password, BCrypt.gensalt());
    User user = new User(username, passHash);
    UpsertOptions options = UpsertOptions.upsertOptions();
    if (expiry.ordinal() > 0) {
      options.durability(expiry);
    }
    String queryType = String.format("KV insert - scoped to %s.users: document %s", tenant, username);
    try {
      userRepository.withOptions(options).save(user);
      Map<String, Object> data = JsonObject.create().put("token", jwtService.buildToken(username)).toMap();
      return Result.of(data, queryType);
    } catch (Exception e) {
      throw new AuthenticationServiceException("There was an error creating account");
    }
  }

  /*
   * Register a flight (or flights) for the given tenant user.
   */
  public Result<Map<String, Object>> registerFlightForUser(final String tenant, final String username, final JsonArray newFlights) {
    UserRepository userRepository = this.userRepository.withScope(tenant);
    BookingRepository bookingRepository = this.bookingRepository.withScope(tenant);
    Optional<User> userDataFetch;
    try {
      userDataFetch = userRepository.findById(username);
    } catch (DocumentNotFoundException ex) {
      throw new IllegalStateException();
    }
    User userData = userDataFetch.get();

    if (newFlights == null) {
      throw new IllegalArgumentException("No flights in payload");
    }

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
      Booking booking = new Booking(UUID.randomUUID().toString());
      booking.name = t.get("name").getAsString();
      booking.sourceairport = t.get("sourceairport").getAsString();
      booking.destinationairport = t.get("destinationairport").getAsString();
      booking.flight = t.get("flight").getAsString();

      if(Objects.nonNull(t.get("utc"))) {
        booking.utc = t.get("utc").getAsString();
      }
      if(Objects.nonNull(t.get("airlineid"))) {
        booking.airlineid = t.get("airlineid").getAsString();
      }
      if(Objects.nonNull(t.get("date"))) {
        booking.date = t.get("date").getAsString();
      }
      booking.price = t.get("price").getAsInt();
      if(Objects.nonNull(t.get("day"))) {
        booking.day = t.get("day").getAsInt();
      }

      bookingRepository.save(booking);
      allBookedFlights.add(booking.bookingId);
      added.add(t);
    }

    userData.setFlightIds(allBookedFlights.toArray(new String[] {}));
    userRepository.save(userData);

    com.google.gson.JsonObject responseData = new com.google.gson.JsonObject();
    responseData.add("added", added);

    String queryType = String.format("KV update - scoped to %s.user: for bookings field in document %s", tenant,
        username);
    return Result.of(convertJsonObjectToMap(responseData), queryType);
  }

  public Result<List<Map<String, Object>>> getFlightsForUser(final String tenant, final String username) {
    UserRepository userRepository = this.userRepository.withScope(tenant);
    BookingRepository bookingRepository = this.bookingRepository.withScope(tenant);
    Optional<User> userDoc;

    try {
      userDoc = userRepository.findById(username);
    } catch (DocumentNotFoundException ex) {
      return Result.of(Collections.emptyList());
    }
    User userData = userDoc.get();
    String[] flights = userData.getFlightIds();
    if (flights == null) {
      return Result.of(Collections.emptyList());
    }

    // The "flights" array contains flight ids. Convert them to actual objects.
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    for (String flightId : flights) {
      Optional<Booking> res;
      try {
        res = bookingRepository.findById(flightId);
      } catch (DocumentNotFoundException ex) {
        throw new RuntimeException("Unable to retrieve flight id " + flightId);
      }
      Map<String, Object> flight = res.get().toMap();
      results.add(flight);
    }

    String queryType = String.format("KV get - scoped to %s.user: for %d bookings in document %s", tenant,
        results.size(), username);
    return Result.of(results, queryType);
  }

  private void checkFlight(Object f) {
    if (f == null || !(f instanceof com.google.gson.JsonObject)) {
      throw new IllegalArgumentException("Each flight must be a non-null object");
    }
    com.google.gson.JsonObject flight = (com.google.gson.JsonObject) f;
    if (!flight.has("name") || !flight.has("date") || !flight.has("sourceairport")
            || !flight.has("destinationairport")) {
      throw new IllegalArgumentException("Malformed flight inside flights payload");
    }
  }

  private Map<String, Object> convertJsonObjectToMap(com.google.gson.JsonObject jsonObject) {
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
