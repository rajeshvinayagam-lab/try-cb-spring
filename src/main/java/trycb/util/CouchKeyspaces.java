package trycb.util;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CouchKeyspaces {
     private String bucketName;
     private String scopeName;
     private String collectionName;
}
