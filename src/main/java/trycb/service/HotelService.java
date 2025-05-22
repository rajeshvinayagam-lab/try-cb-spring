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

import java.util.List;
import java.util.Map;

import trycb.model.Result;

/**
 * Service interface for hotel operations
 */
public interface HotelService {
    /**
     * Search for hotels by location and description
     * 
     * @param location hotel location (city, state, country, address)
     * @param description hotel description or name
     * @return result with list of hotels matching criteria
     */
    Result<List<Map<String, Object>>> findHotels(String location, String description);
    
    /**
     * Search for hotels by description
     * 
     * @param description hotel description or name
     * @return result with list of hotels matching criteria
     */
    Result<List<Map<String, Object>>> findHotels(String description);
    
    /**
     * Find all hotels
     * 
     * @return result with list of all hotels
     */
    Result<List<Map<String, Object>>> findAllHotels();
}