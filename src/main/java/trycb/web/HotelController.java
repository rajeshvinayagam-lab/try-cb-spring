package trycb.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import trycb.model.Error;
import trycb.model.IValue;
import trycb.service.FlightPath;
import trycb.service.FlightPathService;
import trycb.service.Hotel;
import trycb.service.HotelService;
import trycb.service.mongodb.MongoFlightPathService;
import trycb.service.mongodb.MongoHotelService;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

  private final ApplicationProperties applicationProperties;

  private final Hotel hotel;

  private final MongoHotelService mongoHotelService;

  private static final Logger LOGGER = LoggerFactory.getLogger(HotelController.class);
  private static final String LOG_FAILURE_MESSAGE = "Failed with exception";

  public HotelController(ApplicationProperties applicationProperties,
                         Hotel hotel, MongoHotelService mongoHotelService) {
    this.applicationProperties = applicationProperties;
    this.hotel = hotel;
    this.mongoHotelService = mongoHotelService;
  }

  @GetMapping(value = "/{description}/{location}/", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<? extends IValue> findHotelsByDescriptionAndLocation(@PathVariable("location") String location,
      @PathVariable("description") String desc) {
    try {
      return ResponseEntity.ok(getHotelService().findHotels(location, desc));
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error(LOG_FAILURE_MESSAGE, e);
      return ResponseEntity.badRequest().body(new Error(e.getMessage()));
    }
  }

  @GetMapping(value = "/{description}/", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<? extends IValue> findHotelsByDescription(@PathVariable("description") String desc) {
    try {
      return ResponseEntity.ok(getHotelService().findHotels(desc));
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error(LOG_FAILURE_MESSAGE, e);
      return ResponseEntity.badRequest().body(new Error(e.getMessage()));
    }
  }

  @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<? extends IValue> findAllHotels() {
    try {
      return ResponseEntity.ok(getHotelService().findAllHotels());
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error(LOG_FAILURE_MESSAGE, e);
      return ResponseEntity.badRequest().body(new Error(e.getMessage()));
    }
  }

  private HotelService getHotelService() {
    HotelService hotelService = null;
    if (applicationProperties.getReadFromDatabase().equalsIgnoreCase(applicationProperties.getCOUCHBASE())) {
      hotelService = hotel;
    } else if (applicationProperties.getReadFromDatabase().equalsIgnoreCase(applicationProperties.getMONGODB())) {
      hotelService = mongoHotelService;
    }
    return hotelService;
  }

}
