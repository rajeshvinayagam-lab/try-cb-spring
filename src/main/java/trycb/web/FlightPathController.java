package trycb.web;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import trycb.model.Error;
import trycb.model.IValue;
import trycb.service.Airport;
import trycb.service.AirportService;
import trycb.service.FlightPath;
import trycb.service.FlightPathService;
import trycb.service.mongodb.MongoAirportService;
import trycb.service.mongodb.MongoFlightPathService;

@RestController
@RequestMapping("/api/flightPaths")
public class FlightPathController {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlightPathController.class);

  private final ApplicationProperties applicationProperties;

  private final FlightPath flightPath;

  private final MongoFlightPathService mongoFlightPathService;

  public FlightPathController(ApplicationProperties applicationProperties,
                              FlightPath flightPath, MongoFlightPathService mongoFlightPathService) {
    this.applicationProperties = applicationProperties;
    this.flightPath = flightPath;
    this.mongoFlightPathService = mongoFlightPathService;
  }

  @GetMapping("/{from}/{to}")
  public ResponseEntity<? extends IValue> all(@PathVariable("from") String from, @PathVariable("to") String to,
      @RequestParam String leave) {
    try {
      Calendar calendar = Calendar.getInstance(Locale.US);
      calendar.setTime(DateFormat.getDateInstance(DateFormat.SHORT, Locale.US).parse(leave));
      return ResponseEntity.ok(getFlightPathService().findAll(from, to, calendar));
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error("Failed with exception", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Error(e.getMessage()));
    }
  }

  private FlightPathService getFlightPathService() {
    FlightPathService flightPathService = null;
    if (applicationProperties.getReadFromDatabase().equalsIgnoreCase(applicationProperties.getCOUCHBASE())) {
      flightPathService = flightPath;
    } else if (applicationProperties.getReadFromDatabase().equalsIgnoreCase(applicationProperties.getMONGODB())) {
      flightPathService = mongoFlightPathService;
    }
    return flightPathService;
  }

}
