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
package org.apache.iceberg.aws.s3;

import java.util.Map;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestS3URI 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestS3URI 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestS3URI {

  /**
   * 测试场景：Location Parsing。
   *
   * <p>验证该方法在 Location Parsing 条件下的行为是否符合预期。
   */
  @Test
  public void testLocationParsing() {
    String p1 = "s3://bucket/path/to/file";
    S3URI uri1 = new S3URI(p1);

    Assertions.assertThat(uri1.bucket()).isEqualTo("bucket");
    Assertions.assertThat(uri1.key()).isEqualTo("path/to/file");
    Assertions.assertThat(uri1.toString()).isEqualTo(p1);
  }

  /**
   * 测试场景：Encoded String。
   *
   * <p>验证该方法在 Encoded String 条件下的行为是否符合预期。
   */
  @Test
  public void testEncodedString() {
    String p1 = "s3://bucket/path%20to%20file";
    S3URI uri1 = new S3URI(p1);

    Assertions.assertThat(uri1.bucket()).isEqualTo("bucket");
    Assertions.assertThat(uri1.key()).isEqualTo("path%20to%20file");
    Assertions.assertThat(uri1.toString()).isEqualTo(p1);
  }

  /**
   * 测试场景：Missing Scheme。
   *
   * <p>验证该方法在 Missing Scheme 条件下的行为是否符合预期。
   */
  @Test
  public void testMissingScheme() {

    Assertions.assertThatThrownBy(() -> new S3URI("/path/to/file"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid S3 URI, cannot determine scheme: /path/to/file");
  }

  /**
   * 测试场景：Only Bucket Name Location。
   *
   * <p>验证该方法在 Only Bucket Name Location 条件下的行为是否符合预期。
   */
  @Test
  public void testOnlyBucketNameLocation() {
    String p1 = "s3://bucket";
    S3URI url1 = new S3URI(p1);

    Assertions.assertThat(url1.bucket()).isEqualTo("bucket");
    Assertions.assertThat(url1.key()).isEqualTo("");
    Assertions.assertThat(url1.toString()).isEqualTo(p1);
  }

  /**
   * 测试场景：Query And Fragment。
   *
   * <p>验证该方法在 Query And Fragment 条件下的行为是否符合预期。
   */
  @Test
  public void testQueryAndFragment() {
    String p1 = "s3://bucket/path/to/file?query=foo#bar";
    S3URI uri1 = new S3URI(p1);

    Assertions.assertThat(uri1.bucket()).isEqualTo("bucket");
    Assertions.assertThat(uri1.key()).isEqualTo("path/to/file");
    Assertions.assertThat(uri1.toString()).isEqualTo(p1);
  }

  /**
   * 测试场景：Valid Schemes。
   *
   * <p>验证该方法在 Valid Schemes 条件下的行为是否符合预期。
   */
  @Test
  public void testValidSchemes() {
    for (String scheme : Lists.newArrayList("https", "s3", "s3a", "s3n", "gs")) {
      S3URI uri = new S3URI(scheme + "://bucket/path/to/file");
      Assertions.assertThat(uri.bucket()).isEqualTo("bucket");
      Assertions.assertThat(uri.key()).isEqualTo("path/to/file");
    }
  }

  /**
   * 测试场景：3 URI With Bucket To Access Point Mapping。
   *
   * <p>验证该方法在 3 URI With Bucket To Access Point Mapping 条件下的行为是否符合预期。
   */
  @Test
  public void testS3URIWithBucketToAccessPointMapping() {
    String p1 = "s3://bucket/path/to/file?query=foo#bar";
    Map<String, String> bucketToAccessPointMapping = ImmutableMap.of("bucket", "access-point");
    S3URI uri1 = new S3URI(p1, bucketToAccessPointMapping);

    Assertions.assertThat(uri1.bucket()).isEqualTo("access-point");
    Assertions.assertThat(uri1.key()).isEqualTo("path/to/file");
    Assertions.assertThat(uri1.toString()).isEqualTo(p1);
  }
}
