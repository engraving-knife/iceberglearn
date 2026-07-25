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

import java.net.URI;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.Base58;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

/**
 * 文件级说明：测试 MinioContainer 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 MinioContainer 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class MinioContainer extends GenericContainer<MinioContainer> {

  private static final int DEFAULT_PORT = 9000;
  private static final String DEFAULT_IMAGE = "minio/minio";
  private static final String DEFAULT_TAG = "edge";

  private static final String MINIO_ACCESS_KEY = "MINIO_ACCESS_KEY";
  private static final String MINIO_SECRET_KEY = "MINIO_SECRET_KEY";

  private static final String DEFAULT_STORAGE_DIRECTORY = "/data";
  private static final String HEALTH_ENDPOINT = "/minio/health/ready";

  /** 辅助方法：MinioContainer。 */
  public MinioContainer(AwsCredentials credentials) {
    this(DEFAULT_IMAGE + ":" + DEFAULT_TAG, credentials);
  }

  /** 辅助方法：MinioContainer。 */
  public MinioContainer(String image, AwsCredentials credentials) {
    super(image == null ? DEFAULT_IMAGE + ":" + DEFAULT_TAG : image);
    this.withNetworkAliases("minio-" + Base58.randomString(6))
        .withCommand("server", DEFAULT_STORAGE_DIRECTORY)
        .addExposedPort(DEFAULT_PORT);
    if (credentials != null) {
      this.withEnv(MINIO_ACCESS_KEY, credentials.accessKeyId())
          .withEnv(MINIO_SECRET_KEY, credentials.secretAccessKey());
    }

    // this enables virtual-host-style requests. see
    // https://github.com/minio/minio/tree/master/docs/config#domain
    this.withEnv("MINIO_DOMAIN", "localhost");

    setWaitStrategy(
        new HttpWaitStrategy()
            .forPort(DEFAULT_PORT)
            .forPath(HEALTH_ENDPOINT)
            .withStartupTimeout(Duration.ofMinutes(2)));
  }

  /** 辅助方法：getURI。 */
  public URI getURI() {
    return URI.create("http://" + getHost() + ":" + getMappedPort(DEFAULT_PORT));
  }
}
