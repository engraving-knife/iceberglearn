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
package org.apache.iceberg.azure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.azure.core.credential.TokenCredential;
import com.azure.storage.file.datalake.DataLakeFileSystemClientBuilder;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 AzurePropertiesTest 的功能。
 *
 * <p>所属模块：iceberg-azure。职责：验证 AzurePropertiesTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class AzurePropertiesTest {

  /**
   * 测试场景：With Sas Token。
   *
   * <p>验证该方法在 With Sas Token 条件下的行为是否符合预期。
   */
  @Test
  public void testWithSasToken() {
    AzureProperties props =
        new AzureProperties(ImmutableMap.of("adls.sas-token.account1", "token"));

    DataLakeFileSystemClientBuilder clientBuilder = mock(DataLakeFileSystemClientBuilder.class);
    props.applyClientConfiguration("account1", clientBuilder);
    verify(clientBuilder).sasToken(any());
    verify(clientBuilder, times(0)).credential(any(TokenCredential.class));
  }

  /**
   * 测试场景：No Matching Sas Token。
   *
   * <p>验证该方法在 No Matching Sas Token 条件下的行为是否符合预期。
   */
  @Test
  public void testNoMatchingSasToken() {
    AzureProperties props =
        new AzureProperties(ImmutableMap.of("adls.sas-token.account1", "token"));

    DataLakeFileSystemClientBuilder clientBuilder = mock(DataLakeFileSystemClientBuilder.class);
    props.applyClientConfiguration("account2", clientBuilder);
    verify(clientBuilder, times(0)).sasToken(any());
    verify(clientBuilder).credential(any(TokenCredential.class));
  }

  /**
   * 测试场景：No Sas Token。
   *
   * <p>验证该方法在 No Sas Token 条件下的行为是否符合预期。
   */
  @Test
  public void testNoSasToken() {
    AzureProperties props = new AzureProperties();

    DataLakeFileSystemClientBuilder clientBuilder = mock(DataLakeFileSystemClientBuilder.class);
    props.applyClientConfiguration("account", clientBuilder);
    verify(clientBuilder, times(0)).sasToken(any());
    verify(clientBuilder).credential(any(TokenCredential.class));
  }

  /**
   * 测试场景：With Connection String。
   *
   * <p>验证该方法在 With Connection String 条件下的行为是否符合预期。
   */
  @Test
  public void testWithConnectionString() {
    AzureProperties props =
        new AzureProperties(ImmutableMap.of("adls.connection-string.account1", "http://endpoint"));

    DataLakeFileSystemClientBuilder clientBuilder = mock(DataLakeFileSystemClientBuilder.class);
    props.applyClientConfiguration("account1", clientBuilder);
    verify(clientBuilder).endpoint("http://endpoint");
  }

  /**
   * 测试场景：No Matching Connection String。
   *
   * <p>验证该方法在 No Matching Connection String 条件下的行为是否符合预期。
   */
  @Test
  public void testNoMatchingConnectionString() {
    AzureProperties props =
        new AzureProperties(ImmutableMap.of("adls.connection-string.account2", "http://endpoint"));

    DataLakeFileSystemClientBuilder clientBuilder = mock(DataLakeFileSystemClientBuilder.class);
    props.applyClientConfiguration("account1", clientBuilder);
    verify(clientBuilder).endpoint("https://account1");
  }

  /**
   * 测试场景：No Connection String。
   *
   * <p>验证该方法在 No Connection String 条件下的行为是否符合预期。
   */
  @Test
  public void testNoConnectionString() {
    AzureProperties props = new AzureProperties();

    DataLakeFileSystemClientBuilder clientBuilder = mock(DataLakeFileSystemClientBuilder.class);
    props.applyClientConfiguration("account", clientBuilder);
    verify(clientBuilder).endpoint("https://account");
  }
}
