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

package trycb.config;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.SimpleCouchbaseClientFactory;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.core.convert.CouchbaseCustomConversions;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;
import org.springframework.data.couchbase.repository.config.RepositoryOperationsMapping;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.env.ClusterEnvironment;

@Configuration
@EnableCouchbaseRepositories
public class Database {

  @Value("${storage.host}") private String host;

  @Value("${storage.bucket}") private String bucketName;

  @Value("${storage.username}") private String username;

  @Value("${storage.password}") private String password;

  @Value("${storage.scope:inventory}")
  private String scopeName;

  @Bean
  public ClusterEnvironment clusterEnvironment() {
    return ClusterEnvironment.builder().build();
  }

  @Bean(destroyMethod = "disconnect")
  public Cluster couchbaseCluster(ClusterEnvironment couchbaseClusterEnvironment) {
    try {
      return Cluster.connect(host, username, password);
    } catch (Exception e) {
      throw e;
    }
  }

  @Bean
  public Bucket getCouchbaseBucket(Cluster cluster) {
    try {
      if (!cluster.buckets().getAllBuckets().containsKey(bucketName)) {
        throw new RuntimeException("Bucket " + bucketName + " not found in Couchbase cluster at " + host);
      }
      return cluster.bucket(bucketName);
    } catch (Exception e) {
      throw e;
    }
  }

  @Bean
  public CouchbaseClientFactory couchbaseClientFactory(Cluster cluster) {
    return new SimpleCouchbaseClientFactory(cluster, bucketName, scopeName);
  }

  @Bean
  public CouchbaseCustomConversions couchbaseCustomConversions() {
    return new CouchbaseCustomConversions(Collections.emptyList());
  }

  @Bean
  public CouchbaseMappingContext couchbaseMappingContext(CouchbaseCustomConversions conversions) {
    CouchbaseMappingContext context = new CouchbaseMappingContext();
    context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
    return context;
  }

  @Bean
  public MappingCouchbaseConverter mappingCouchbaseConverter(
          CouchbaseMappingContext mappingContext,
          CouchbaseCustomConversions conversions
  ) {
    MappingCouchbaseConverter converter = new MappingCouchbaseConverter(mappingContext);
    converter.setCustomConversions(conversions);
    return converter;
  }

  @Bean(name = "couchbaseTemplate")
  public CouchbaseTemplate couchbaseTemplate(
          CouchbaseClientFactory factory,
          MappingCouchbaseConverter converter
  ) {
    return new CouchbaseTemplate(factory, converter);
  }

  @Bean
  public RepositoryOperationsMapping couchbaseRepositoryOperationsMapping(
          @Qualifier("couchbaseTemplate") CouchbaseTemplate template
  ) {
    return new RepositoryOperationsMapping(template);
  }
}
