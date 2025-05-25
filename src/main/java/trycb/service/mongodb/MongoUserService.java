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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import trycb.config.mongodb.MongoUser;
import trycb.config.mongodb.MongoUserRepository;
import trycb.model.Result;
import trycb.service.TokenService;
import trycb.service.UserService;

/**
 * MongoDB implementation of the UserService
 */
@Service
@Profile("mongodb")
public class MongoUserService implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoUserService.class);

    private final MongoUserRepository userRepository;
    private final TokenService tokenService;

    @Autowired
    public MongoUserService(MongoUserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    @Override
    public Result<Map<String, Object>> register(String username, String password, String name) {
        if (userRepository.existsByUsername(username)) {
            throw new AuthenticationCredentialsNotFoundException("Username already exists.");
        }

        try {
            // Hash the password
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            // Create the user
            MongoUser user = new MongoUser();
            user.setId("user::" + username);
            user.setUsername(username);
            user.setPassword(hashedPassword);
            user.setName(name);

            // Save the user
            userRepository.save(user);

            // Create a token
            String token = tokenService.buildToken(username);

            // Return the success response
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("user", username);

            return Result.of(result, "User registration successful");
        } catch (Exception e) {
            LOGGER.error("Error registering user", e);
            throw new AuthenticationCredentialsNotFoundException("Error registering user: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> login(String username, String password) {
        try {
            // Find the user
            Optional<MongoUser> userOpt = userRepository.findByUsername(username);

            if (!userOpt.isPresent()) {
                throw new AuthenticationCredentialsNotFoundException("Invalid username or password");
            }

            MongoUser user = userOpt.get();

            // Check the password
            if (!BCrypt.checkpw(password, user.getPassword())) {
                throw new AuthenticationCredentialsNotFoundException("Invalid username or password");
            }

            // Create a token
            String token = tokenService.buildToken(username);

            // Return the success response
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("user", username);

            return Result.of(result, "Login successful");
        } catch (Exception e) {
            LOGGER.error("Error logging in", e);
            throw new AuthenticationCredentialsNotFoundException("Error logging in: " + e.getMessage());
        }
    }
}
