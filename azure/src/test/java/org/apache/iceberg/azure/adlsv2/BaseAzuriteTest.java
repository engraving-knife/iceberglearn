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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.azure.storage.file.datalake.DataLakeFileSystemClientBuilder;
import org.apache.iceberg.azure.AzureProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * 文件级说明：测试 BaseAzuriteTest 的功能。
 *
 * <p>所属模块：iceberg-azure。职责：验证 BaseAzuriteTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class BaseAzuriteTest {
  protected static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();

  /** 辅助方法：beforeAll。 */
  @BeforeAll
  public static void beforeAll() {
    AZURITE_CONTAINER.start();
  }

  /** 辅助方法：afterAll。 */
  @AfterAll
  public static void afterAll() {
    AZURITE_CONTAINER.stop();
  }

  /** 辅助方法：baseBefore。 */
  @BeforeEach
  public void baseBefore() {
    AZURITE_CONTAINER.createStorageContainer();
  }

  /** 辅助方法：baseAfter。 */
  @AfterEach
  public void baseAfter() {
    AZURITE_CONTAINER.deleteStorageContainer();
  }

  /** 辅助方法：createFileIO。 */
  protected ADLSFileIO createFileIO() {
    AzureProperties azureProps = spy(new AzureProperties());

    doAnswer(
            invoke -> {
              DataLakeFileSystemClientBuilder clientBuilder = invoke.getArgument(1);
              clientBuilder.endpoint(AZURITE_CONTAINER.endpoint());
              clientBuilder.credential(AZURITE_CONTAINER.credential());
              return null;
            })
        .when(azureProps)
        .applyClientConfiguration(any(), any());

    return new ADLSFileIO(azureProps);
  }
}
