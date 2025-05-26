package trycb.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import trycb.model.Error;
import trycb.model.IValue;
import trycb.service.Airport;
import trycb.service.AirportService;
import trycb.service.mongodb.MongoAirportService;

@RestController
@RequestMapping("/api/airports")
public class AirportController {

  private static final Logger LOGGER = LoggerFactory.getLogger(AirportController.class);

  private final ApplicationProperties applicationProperties;

  private final Airport airport;

  private final MongoAirportService mongoAirportService;

  public AirportController(ApplicationProperties applicationProperties,
                           Airport airport, MongoAirportService mongoAirportService) {
    this.applicationProperties = applicationProperties;
    this.airport = airport;
    this.mongoAirportService = mongoAirportService;
  }

  @GetMapping
  public ResponseEntity<? extends IValue> airports(@RequestParam("search") String search) {
    try {
      return ResponseEntity.ok(getAirportService().findAll(search));
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error("Failed with exception ", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Error(e.getMessage()));
    }
  }

  private AirportService getAirportService() {
    AirportService airportService = null;
    if (applicationProperties.getReadFromDatabase().equalsIgnoreCase(applicationProperties.getCOUCHBASE())) {
      airportService = airport;
    } else if (applicationProperties.getReadFromDatabase().equalsIgnoreCase(applicationProperties.getMONGODB())) {
      airportService = mongoAirportService;
    }
    return airportService;
  }

}
