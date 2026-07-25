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
package org.apache.iceberg.gcp.gcs;

import org.apache.iceberg.exceptions.ValidationException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 GCSLocationTest 的功能。
 *
 * <p>所属模块：iceberg-gcp。职责：验证 GCSLocationTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class GCSLocationTest {
  /**
   * 测试场景：Location Parsing。
   *
   * <p>验证该方法在 Location Parsing 条件下的行为是否符合预期。
   */
  @Test
  public void testLocationParsing() {
    String p1 = "gs://bucket/path/to/prefix";
    GCSLocation location = new GCSLocation(p1);

    Assertions.assertThat(location.bucket()).isEqualTo("bucket");
    Assertions.assertThat(location.prefix()).isEqualTo("path/to/prefix");
  }

  /**
   * 测试场景：Encoded String。
   *
   * <p>验证该方法在 Encoded String 条件下的行为是否符合预期。
   */
  @Test
  public void testEncodedString() {
    String p1 = "gs://bucket/path%20to%20prefix";
    GCSLocation location = new GCSLocation(p1);

    Assertions.assertThat(location.bucket()).isEqualTo("bucket");
    Assertions.assertThat(location.prefix()).isEqualTo("path%20to%20prefix");
  }

  /**
   * 测试场景：Missing Scheme。
   *
   * <p>验证该方法在 Missing Scheme 条件下的行为是否符合预期。
   */
  @Test
  public void testMissingScheme() {
    Assertions.assertThatThrownBy(() -> new GCSLocation("/path/to/prefix"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid GCS URI, cannot determine scheme: /path/to/prefix");
  }

  /**
   * 测试场景：Invalid Scheme。
   *
   * <p>验证该方法在 Invalid Scheme 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidScheme() {
    Assertions.assertThatThrownBy(() -> new GCSLocation("s3://bucket/path/to/prefix"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid GCS URI, invalid scheme: s3");
  }

  /**
   * 测试场景：Only Bucket Name Location。
   *
   * <p>验证该方法在 Only Bucket Name Location 条件下的行为是否符合预期。
   */
  @Test
  public void testOnlyBucketNameLocation() {
    String p1 = "gs://bucket";
    GCSLocation location = new GCSLocation(p1);

    Assertions.assertThat(location.bucket()).isEqualTo("bucket");
    Assertions.assertThat(location.prefix()).isEqualTo("");
  }

  /**
   * 测试场景：Query And Fragment。
   *
   * <p>验证该方法在 Query And Fragment 条件下的行为是否符合预期。
   */
  @Test
  public void testQueryAndFragment() {
    String p1 = "gs://bucket/path/to/prefix?query=foo#bar";
    GCSLocation location = new GCSLocation(p1);

    Assertions.assertThat(location.bucket()).isEqualTo("bucket");
    Assertions.assertThat(location.prefix()).isEqualTo("path/to/prefix");
  }
}
