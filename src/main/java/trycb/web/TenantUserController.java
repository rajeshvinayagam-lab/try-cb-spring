package trycb.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trycb.model.Error;
import trycb.model.IValue;
import trycb.model.Result;
import trycb.service.Airport;
import trycb.service.AirportService;
import trycb.service.TenantUser;
import trycb.service.TenantUserService;
import trycb.service.TokenService;
import trycb.service.mongodb.MongoAirportService;
import trycb.service.mongodb.MongoTenantUserService;

@RestController
@RequestMapping("/api/tenants")
public class TenantUserController {

  private final TokenService jwtService;

  private final ApplicationProperties applicationProperties;

  private final TenantUser tenantUser;

  private final MongoTenantUserService mongoTenantUserService;


  private static final Logger LOGGER = LoggerFactory.getLogger(TenantUserController.class);

  @Value("${storage.expiry:0}") private int expiry;

  @Autowired
  public TenantUserController(TokenService jwtService, ApplicationProperties applicationProperties,
                              TenantUser tenantUser, MongoTenantUserService mongoTenantUserService) {
    this.jwtService = jwtService;
    this.applicationProperties = applicationProperties;
    this.tenantUser = tenantUser;
    this.mongoTenantUserService = mongoTenantUserService;
  }

  @PostMapping(value = "/{tenant}/user/login")
  public ResponseEntity<? extends IValue> login(@PathVariable("tenant") String tenant,
      @RequestBody Map<String, String> loginInfo) {
    String user = loginInfo.get("user");
    String password = loginInfo.get("password");
    if (user == null || password == null) {
      return ResponseEntity.badRequest().body(new Error("User or password missing, or malformed request"));
    }

    try {
      Result<Map<String, Object>> mapResult = null;
      if (applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getCOUCHBASE()) ||
              applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getBOTH())) {
        mapResult = tenantUser.login(tenant, user, password);
      }
      if (applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getMONGODB()) ||
              applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getBOTH())) {
        mapResult = mongoTenantUserService.login(tenant, user, password);
      }
      return ResponseEntity.ok(mapResult);
    } catch (AuthenticationException e) {
      e.printStackTrace();
      LOGGER.error("Authentication failed with exception", e);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Error(e.getMessage()));
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error("Failed with exception", e);
      return ResponseEntity.status(500).body(new Error(e.getMessage()));
    }
  }

  @PostMapping(value = "/{tenant}/user/signup")
  public ResponseEntity<? extends IValue> createLogin(@PathVariable("tenant") String tenant, @RequestBody String json) {
    JsonObject jsonData = JsonParser.parseString(json).getAsJsonObject();
    try {
      Result<Map<String, Object>> result = null;
      if (applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getCOUCHBASE()) ||
              applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getBOTH())) {
        result = tenantUser.createLogin(tenant, jsonData.get("user").getAsString(),
                jsonData.get("password").getAsString(), DurabilityLevel.values()[expiry]);
      }
      if (applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getMONGODB()) ||
              applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getBOTH())) {
        result = mongoTenantUserService.createLogin(tenant, jsonData.get("user").getAsString(),
                jsonData.get("password").getAsString(), DurabilityLevel.values()[expiry]);
      }

      return ResponseEntity.status(HttpStatus.CREATED).body(result);
    } catch (AuthenticationServiceException e) {
      e.printStackTrace();
      LOGGER.error("Authentication failed with exception", e);
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error(e.getMessage()));
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error("Failed with exception", e);
      return ResponseEntity.status(500).body(new Error(e.getMessage()));
    }
  }

  @PutMapping(value = "/{tenant}/user/{username}/flights")
  public ResponseEntity<? extends IValue> book(@PathVariable("tenant") String tenant,
      @PathVariable("username") String username, @RequestBody String json,
      @RequestHeader("Authorization") String authentication) {
    if (authentication == null || !authentication.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Error("Bearer Authentication must be used"));
    }
    try {
      JsonObject jsonData = JsonParser.parseString(json).getAsJsonObject();
      jwtService.verifyAuthenticationHeader(authentication, username);
      Result<Map<String, Object>> result = null;
      if (applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getCOUCHBASE()) ||
              applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getBOTH())) {
        result = tenantUser.registerFlightForUser(tenant, username, jsonData.getAsJsonArray("flights"));
      }
      if (applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getMONGODB()) ||
              applicationProperties.getWriteToDatabase().equalsIgnoreCase(applicationProperties.getBOTH())) {
        result = mongoTenantUserService.registerFlightForUser(tenant, username, jsonData.getAsJsonArray("flights"));
      }
      return ResponseEntity.ok().body(result);
    } catch (IllegalStateException e) {
      e.printStackTrace();
      LOGGER.error("Failed with invalid state exception", e);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Error("Forbidden, you can't book for this user"));
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
      LOGGER.error("Failed with invalid argument exception", e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Error(e.getMessage()));
    }
  }

  @GetMapping(value = "/{tenant}/user/{username}/flights")
  public Object booked(@PathVariable("tenant") String tenant, @PathVariable("username") String username,
      @RequestHeader("Authorization") String authentication) {
    if (authentication == null || !authentication.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Error("Bearer Authentication must be used"));
    }

    try {
      jwtService.verifyAuthenticationHeader(authentication, username);
      return ResponseEntity.ok(getTenantUserService().getFlightsForUser(tenant, username));
    } catch (IllegalStateException e) {
      e.printStackTrace();
      LOGGER.error("Failed with invalid state exception", e);
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(new Error("Forbidden, you don't have access to this cart"));
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
      LOGGER.error("Failed with invalid argument exception", e);
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(new Error("Forbidden, you don't have access to this cart"));
    }
  }

  private TenantUserService getTenantUserService() {
    TenantUserService tenantUserService = null;
    if (applicationProperties.getReadFromDatabase().equalsIgnoreCase(applicationProperties.getCOUCHBASE())) {
      tenantUserService = tenantUser;
    } else if (applicationProperties.getReadFromDatabase().equalsIgnoreCase(applicationProperties.getMONGODB())) {
      tenantUserService = mongoTenantUserService;
    }
    return tenantUserService;
  }

}
