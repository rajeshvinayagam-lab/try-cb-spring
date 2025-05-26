package trycb.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
@Getter
public class ApplicationProperties {

     @Value("${READ_FROM_DATABASE}")
     private String readFromDatabase;

     @Value("${WRITE_TO_DATABASE}")
     private String writeToDatabase;

     private String COUCHBASE = "couchbase";
     private String MONGODB = "mongodb";
     private String BOTH = "both";
}
