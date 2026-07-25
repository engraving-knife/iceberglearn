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
package org.apache.iceberg.aliyun.oss;

import static com.aliyun.oss.internal.OSSUtils.OSS_RESOURCE_MANAGER;

import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestOSSURI 的功能。
 *
 * <p>所属模块：iceberg-aliyun。职责：验证 TestOSSURI 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestOSSURI {
  /**
   * 测试场景：Url Parsing。
   *
   * <p>验证该方法在 Url Parsing 条件下的行为是否符合预期。
   */
  @Test
  public void testUrlParsing() {
    String location = "oss://bucket/path/to/file";
    OSSURI uri = new OSSURI(location);

    Assert.assertEquals("bucket", uri.bucket());
    Assert.assertEquals("path/to/file", uri.key());
    Assert.assertEquals(location, uri.toString());
  }

  /**
   * 测试场景：Encoded String。
   *
   * <p>验证该方法在 Encoded String 条件下的行为是否符合预期。
   */
  @Test
  public void testEncodedString() {
    String location = "oss://bucket/path%20to%20file";
    OSSURI uri = new OSSURI(location);

    Assert.assertEquals("bucket", uri.bucket());
    Assert.assertEquals("path%20to%20file", uri.key());
    Assert.assertEquals(location, uri.toString());
  }

  /**
   * 测试场景：invalid Bucket。
   *
   * <p>验证该方法在 invalid Bucket 条件下的行为是否符合预期。
   */
  @Test
  public void invalidBucket() {

    Assertions.assertThatThrownBy(() -> new OSSURI("https://test_bucket/path/to/file"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            OSS_RESOURCE_MANAGER.getFormattedString("BucketNameInvalid", "test_bucket"));
  }

  /**
   * 测试场景：missing Key。
   *
   * <p>验证该方法在 missing Key 条件下的行为是否符合预期。
   */
  @Test
  public void missingKey() {

    Assertions.assertThatThrownBy(() -> new OSSURI("https://bucket/"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Missing key in OSS location");
  }

  /**
   * 测试场景：invalid Key。
   *
   * <p>验证该方法在 invalid Key 条件下的行为是否符合预期。
   */
  @Test
  public void invalidKey() {
    Assertions.assertThatThrownBy(() -> new OSSURI("https://bucket/\\path/to/file"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            OSS_RESOURCE_MANAGER.getFormattedString("ObjectKeyInvalid", "\\path/to/file"));
  }

  /**
   * 测试场景：relative Pathing。
   *
   * <p>验证该方法在 relative Pathing 条件下的行为是否符合预期。
   */
  @Test
  public void relativePathing() {

    Assertions.assertThatThrownBy(() -> new OSSURI("/path/to/file"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Invalid OSS location");
  }

  /**
   * 测试场景：invalid Scheme。
   *
   * <p>验证该方法在 invalid Scheme 条件下的行为是否符合预期。
   */
  @Test
  public void invalidScheme() {

    Assertions.assertThatThrownBy(() -> new OSSURI("invalid://bucket/"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Invalid scheme");
  }

  /**
   * 测试场景：Fragment。
   *
   * <p>验证该方法在 Fragment 条件下的行为是否符合预期。
   */
  @Test
  public void testFragment() {
    String location = "oss://bucket/path/to/file#print";
    OSSURI uri = new OSSURI(location);

    Assert.assertEquals("bucket", uri.bucket());
    Assert.assertEquals("path/to/file", uri.key());
    Assert.assertEquals(location, uri.toString());
  }

  /**
   * 测试场景：Query And Fragment。
   *
   * <p>验证该方法在 Query And Fragment 条件下的行为是否符合预期。
   */
  @Test
  public void testQueryAndFragment() {
    String location = "oss://bucket/path/to/file?query=foo#bar";
    OSSURI uri = new OSSURI(location);

    Assert.assertEquals("bucket", uri.bucket());
    Assert.assertEquals("path/to/file", uri.key());
    Assert.assertEquals(location, uri.toString());
  }

  /**
   * 测试场景：Valid Schemes。
   *
   * <p>验证该方法在 Valid Schemes 条件下的行为是否符合预期。
   */
  @Test
  public void testValidSchemes() {
    for (String scheme : Lists.newArrayList("https", "oss")) {
      OSSURI uri = new OSSURI(scheme + "://bucket/path/to/file");
      Assert.assertEquals("bucket", uri.bucket());
      Assert.assertEquals("path/to/file", uri.key());
    }
  }
}
