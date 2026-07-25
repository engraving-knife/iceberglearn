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

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakePathClientBuilder;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

/**
 * 文件级说明：测试 AzuriteContainer 的功能。
 *
 * <p>所属模块：iceberg-azure。职责：验证 AzuriteContainer 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class AzuriteContainer extends GenericContainer<AzuriteContainer> {

  private static final int DEFAULT_PORT = 10000; // default blob service port
  private static final String DEFAULT_IMAGE = "mcr.microsoft.com/azure-storage/azurite";
  private static final String DEFAULT_TAG = "3.26.0";
  private static final String LOG_WAIT_REGEX =
      "Azurite Blob service is successfully listening at .*";

  public static final String ACCOUNT = "account";
  public static final String KEY = "key";
  public static final String STORAGE_CONTAINER = "container";

  /** 辅助方法：AzuriteContainer。 */
  public AzuriteContainer() {
    this(DEFAULT_IMAGE + ":" + DEFAULT_TAG);
  }

  /** 辅助方法：AzuriteContainer。 */
  public AzuriteContainer(String image) {
    super(image == null ? DEFAULT_IMAGE + ":" + DEFAULT_TAG : image);
    this.addExposedPort(DEFAULT_PORT);
    this.addEnv("AZURITE_ACCOUNTS", ACCOUNT + ":" + KEY);
    this.setWaitStrategy(new LogMessageWaitStrategy().withRegEx(LOG_WAIT_REGEX));
  }

  /** 辅助方法：createStorageContainer。 */
  public void createStorageContainer() {
    serviceClient().createFileSystem(STORAGE_CONTAINER);
  }

  /** 辅助方法：deleteStorageContainer。 */
  public void deleteStorageContainer() {
    serviceClient().deleteFileSystem(STORAGE_CONTAINER);
  }

  /** 辅助方法：createFile。 */
  public void createFile(String path, byte[] data) {
    try (OutputStream out = fileClient(path).getOutputStream()) {
      out.write(data);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 辅助方法：serviceClient。 */
  public DataLakeServiceClient serviceClient() {
    return new DataLakeServiceClientBuilder()
        .endpoint(endpoint())
        .credential(credential())
        .buildClient();
  }

  /** 辅助方法：fileClient。 */
  public DataLakeFileClient fileClient(String path) {
    return new DataLakePathClientBuilder()
        .endpoint(endpoint())
        .credential(credential())
        .fileSystemName(STORAGE_CONTAINER)
        .pathName(path)
        .buildFileClient();
  }

  /** 辅助方法：location。 */
  public String location(String path) {
    return String.format("abfs://%s@%s.dfs.core.windows.net/%s", STORAGE_CONTAINER, ACCOUNT, path);
  }

  /** 辅助方法：endpoint。 */
  public String endpoint() {
    return String.format("http://%s:%d/%s", getHost(), getMappedPort(DEFAULT_PORT), ACCOUNT);
  }

  /** 辅助方法：credential。 */
  public StorageSharedKeyCredential credential() {
    return new StorageSharedKeyCredential(ACCOUNT, KEY);
  }
}
