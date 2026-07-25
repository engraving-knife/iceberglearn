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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.metrics.MetricsContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：测试 GCSOutputStreamTest 的功能。
 *
 * <p>所属模块：iceberg-gcp。职责：验证 GCSOutputStreamTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class GCSOutputStreamTest {
  private static final Logger LOG = LoggerFactory.getLogger(GCSOutputStreamTest.class);
  private static final String BUCKET = "test-bucket";

  private final GCPProperties properties = new GCPProperties();
  private final Storage storage = LocalStorageHelper.getOptions().getService();
  private final Random random = new Random(1);

  /**
   * 测试场景：Write。
   *
   * <p>验证该方法在 Write 条件下的行为是否符合预期。
   */
  @Test
  public void testWrite() {
    // Run tests for both byte and array write paths
    Stream.of(true, false)
        .forEach(
            arrayWrite -> {
              // Test small file write
              writeAndVerify(storage, randomBlobId(), randomData(1024), arrayWrite);

              // Test large file
              writeAndVerify(storage, randomBlobId(), randomData(10 * 1024 * 1024), arrayWrite);
            });
  }

  /**
   * 测试场景：Multiple Close。
   *
   * <p>验证该方法在 Multiple Close 条件下的行为是否符合预期。
   */
  @Test
  public void testMultipleClose() throws IOException {
    GCSOutputStream stream =
        new GCSOutputStream(storage, randomBlobId(), properties, MetricsContext.nullMetrics());
    stream.close();
    stream.close();
  }

  /** 辅助方法：writeAndVerify。 */
  private void writeAndVerify(Storage client, BlobId uri, byte[] data, boolean arrayWrite) {
    try (GCSOutputStream stream =
        new GCSOutputStream(client, uri, properties, MetricsContext.nullMetrics())) {
      if (arrayWrite) {
        stream.write(data);
        assertThat(stream.getPos()).isEqualTo(data.length);
      } else {
        for (int i = 0; i < data.length; i++) {
          stream.write(data[i]);
          assertThat(stream.getPos()).isEqualTo(i + 1);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    byte[] actual = readGCSData(uri);
    assertThat(actual).isEqualTo(data);
  }

  /** 辅助方法：readGCSData。 */
  private byte[] readGCSData(BlobId blobId) {
    return storage.get(blobId).getContent();
  }

  /** 辅助方法：randomData。 */
  private byte[] randomData(int size) {
    byte[] result = new byte[size];
    random.nextBytes(result);
    return result;
  }

  /** 辅助方法：randomBlobId。 */
  private BlobId randomBlobId() {
    return BlobId.fromGsUtilUri(String.format("gs://%s/data/%s.dat", BUCKET, UUID.randomUUID()));
  }
}
