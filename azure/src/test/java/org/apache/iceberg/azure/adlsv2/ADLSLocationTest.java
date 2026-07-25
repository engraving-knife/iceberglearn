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
package org.apache.iceberg.azure.adlsv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.iceberg.exceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 文件级说明：测试 ADLSLocationTest 的功能。
 *
 * <p>所属模块：iceberg-azure。职责：验证 ADLSLocationTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class ADLSLocationTest {
  /**
   * 测试场景：Location Parsing。
   *
   * <p>验证该方法在 Location Parsing 条件下的行为是否符合预期。
   */
  @ParameterizedTest
  @ValueSource(strings = {"abfs", "abfss"})
  public void testLocationParsing(String scheme) {
    String p1 = scheme + "://container@account.dfs.core.windows.net/path/to/file";
    ADLSLocation location = new ADLSLocation(p1);

    assertThat(location.storageAccount()).isEqualTo("account.dfs.core.windows.net");
    assertThat(location.container().get()).isEqualTo("container");
    assertThat(location.path()).isEqualTo("path/to/file");
  }

  /**
   * 测试场景：Encoded String。
   *
   * <p>验证该方法在 Encoded String 条件下的行为是否符合预期。
   */
  @Test
  public void testEncodedString() {
    String p1 = "abfs://container@account.dfs.core.windows.net/path%20to%20file";
    ADLSLocation location = new ADLSLocation(p1);

    assertThat(location.storageAccount()).isEqualTo("account.dfs.core.windows.net");
    assertThat(location.container().get()).isEqualTo("container");
    assertThat(location.path()).isEqualTo("path%20to%20file");
  }

  /**
   * 测试场景：Missing Scheme。
   *
   * <p>验证该方法在 Missing Scheme 条件下的行为是否符合预期。
   */
  @Test
  public void testMissingScheme() {
    assertThatThrownBy(() -> new ADLSLocation("/path/to/file"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid ADLS URI: /path/to/file");
  }

  /**
   * 测试场景：Invalid Scheme。
   *
   * <p>验证该方法在 Invalid Scheme 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidScheme() {
    assertThatThrownBy(() -> new ADLSLocation("s3://bucket/path/to/file"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid ADLS URI: s3://bucket/path/to/file");
  }

  /**
   * 测试场景：No Container。
   *
   * <p>验证该方法在 No Container 条件下的行为是否符合预期。
   */
  @Test
  public void testNoContainer() {
    String p1 = "abfs://account.dfs.core.windows.net/path/to/file";
    ADLSLocation location = new ADLSLocation(p1);

    assertThat(location.storageAccount()).isEqualTo("account.dfs.core.windows.net");
    assertThat(location.container().isPresent()).isFalse();
    assertThat(location.path()).isEqualTo("path/to/file");
  }

  /**
   * 测试场景：No Path。
   *
   * <p>验证该方法在 No Path 条件下的行为是否符合预期。
   */
  @Test
  public void testNoPath() {
    String p1 = "abfs://container@account.dfs.core.windows.net";
    ADLSLocation location = new ADLSLocation(p1);

    assertThat(location.storageAccount()).isEqualTo("account.dfs.core.windows.net");
    assertThat(location.container().get()).isEqualTo("container");
    assertThat(location.path()).isEqualTo("");
  }

  /**
   * 测试场景：Query And Fragment。
   *
   * <p>验证该方法在 Query And Fragment 条件下的行为是否符合预期。
   */
  @Test
  public void testQueryAndFragment() {
    String p1 = "abfs://container@account.dfs.core.windows.net/path/to/file?query=foo#123";
    ADLSLocation location = new ADLSLocation(p1);

    assertThat(location.storageAccount()).isEqualTo("account.dfs.core.windows.net");
    assertThat(location.container().get()).isEqualTo("container");
    assertThat(location.path()).isEqualTo("path/to/file");
  }

  /**
   * 测试场景：Query And Fragment No Path。
   *
   * <p>验证该方法在 Query And Fragment No Path 条件下的行为是否符合预期。
   */
  @Test
  public void testQueryAndFragmentNoPath() {
    String p1 = "abfs://container@account.dfs.core.windows.net?query=foo#123";
    ADLSLocation location = new ADLSLocation(p1);

    assertThat(location.storageAccount()).isEqualTo("account.dfs.core.windows.net");
    assertThat(location.container().get()).isEqualTo("container");
    assertThat(location.path()).isEqualTo("");
  }
}
