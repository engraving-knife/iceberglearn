/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.aws;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.DeleteDatabaseRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.CreateAccessPointRequest;
import software.amazon.awssdk.services.s3control.model.DeleteAccessPointRequest;

/**
 * 文件级说明：AwsIntegTestUtil 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 aws集成测试 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class AwsIntegTestUtil {

  private static final Logger LOG = LoggerFactory.getLogger(AwsIntegTestUtil.class);

  /** 构造方法：AwsIntegTestUtil。 */
  private AwsIntegTestUtil() {}

  /** 辅助方法：测试region。 */
  public static String testRegion() {
    return System.getenv("AWS_REGION");
  }

  /** 辅助方法：测试交叉region。 */
  public static String testCrossRegion() {
    String crossRegion = System.getenv("AWS_CROSS_REGION");
    Preconditions.checkArgument(
        !testRegion().equals(crossRegion),
        "AWS_REGION should not be equal to " + "AWS_CROSS_REGION");
    return crossRegion;
  }

  /** 辅助方法：测试桶name。 */
  public static String testBucketName() {
    return System.getenv("AWS_TEST_BUCKET");
  }

  /** 辅助方法：测试交叉region桶name。 */
  public static String testCrossRegionBucketName() {
    return System.getenv("AWS_TEST_CROSS_REGION_BUCKET");
  }

  /** 辅助方法：测试accountid。 */
  public static String testAccountId() {
    return System.getenv("AWS_TEST_ACCOUNT_ID");
  }

  /** 辅助方法：cleans3桶。 */
  public static void cleanS3Bucket(S3Client s3, String bucketName, String prefix) {
    boolean hasContent = true;
    while (hasContent) {
      ListObjectsV2Response response =
          s3.listObjectsV2(
              ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).build());
      hasContent = response.hasContents();
      if (hasContent) {
        s3.deleteObjects(
            DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(
                    Delete.builder()
                        .objects(
                            response.contents().stream()
                                .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                                .collect(Collectors.toList()))
                        .build())
                .build());
      }
    }
  }

  /** 辅助方法：cleanGlue目录。 */
  public static void cleanGlueCatalog(GlueClient glue, List<String> namespaces) {
    for (String namespace : namespaces) {
      try {
        // delete db also delete tables
        glue.deleteDatabase(DeleteDatabaseRequest.builder().name(namespace).build());
      } catch (Exception e) {
        LOG.error("Cannot delete namespace {}", namespace, e);
      }
    }
  }

  /** 辅助方法：创建s3control客户端。 */
  public static S3ControlClient createS3ControlClient(String region) {
    return S3ControlClient.builder()
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .region(Region.of(region))
        .build();
  }

  /** 辅助方法：创建accesspoint。 */
  public static void createAccessPoint(
      S3ControlClient s3ControlClient, String accessPointName, String bucketName) {
    try {
      s3ControlClient.createAccessPoint(
          CreateAccessPointRequest.builder()
              .name(accessPointName)
              .bucket(bucketName)
              .accountId(testAccountId())
              .build());
    } catch (Exception e) {
      LOG.error("Cannot create access point {}", accessPointName, e);
    }
  }

  /** 辅助方法：删除accesspoint。 */
  public static void deleteAccessPoint(S3ControlClient s3ControlClient, String accessPointName) {
    try {
      s3ControlClient.deleteAccessPoint(
          DeleteAccessPointRequest.builder()
              .name(accessPointName)
              .accountId(testAccountId())
              .build());
    } catch (Exception e) {
      LOG.error("Cannot delete access point {}", accessPointName, e);
    }
  }
}
