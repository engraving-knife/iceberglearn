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
package org.apache.iceberg.puffin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.iceberg.puffin.PuffinCompressionCodec.NONE;
import static org.apache.iceberg.puffin.PuffinCompressionCodec.ZSTD;
import static org.apache.iceberg.puffin.PuffinFormatTestUtil.EMPTY_PUFFIN_UNCOMPRESSED_FOOTER_SIZE;
import static org.apache.iceberg.puffin.PuffinFormatTestUtil.SAMPLE_METRIC_DATA_COMPRESSED_ZSTD_FOOTER_SIZE;
import static org.apache.iceberg.puffin.PuffinFormatTestUtil.readTestResource;
import static org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap.toImmutableMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import javax.annotation.Nullable;
import org.apache.iceberg.inmemory.InMemoryInputFile;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.Pair;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestPuffinReader，用于验证 Puffin Reader 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Puffin Reader 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestPuffinReader {
  /**
   * 测试场景：empty footer uncompressed。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEmptyFooterUncompressed() throws Exception {
    testEmpty("v1/empty-puffin-uncompressed.bin", EMPTY_PUFFIN_UNCOMPRESSED_FOOTER_SIZE);
  }

  /**
   * 测试场景：empty with unknown footer size。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEmptyWithUnknownFooterSize() throws Exception {
    testEmpty("v1/empty-puffin-uncompressed.bin", null);
  }

  /**
   * 测试场景：empty。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  private void testEmpty(String resourceName, @Nullable Long footerSize) throws Exception {
    InMemoryInputFile inputFile = new InMemoryInputFile(readTestResource(resourceName));
    Puffin.ReadBuilder readBuilder = Puffin.read(inputFile).withFileSize(inputFile.getLength());
    if (footerSize != null) {
      readBuilder = readBuilder.withFooterSize(footerSize);
    }
    try (PuffinReader reader = readBuilder.build()) {
      FileMetadata fileMetadata = reader.fileMetadata();
      assertThat(fileMetadata.properties()).as("file properties").isEqualTo(ImmutableMap.of());
      assertThat(fileMetadata.blobs()).as("blob list").isEmpty();
    }
  }

  /**
   * 测试场景：wrong footer size。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWrongFooterSize() throws Exception {
    String resourceName = "v1/sample-metric-data-compressed-zstd.bin";
    long footerSize = SAMPLE_METRIC_DATA_COMPRESSED_ZSTD_FOOTER_SIZE;
    testWrongFooterSize(resourceName, footerSize - 1, "Invalid file: expected magic at offset");
    testWrongFooterSize(resourceName, footerSize + 1, "Invalid file: expected magic at offset");
    testWrongFooterSize(resourceName, footerSize - 10, "Invalid file: expected magic at offset");
    testWrongFooterSize(resourceName, footerSize + 10, "Invalid file: expected magic at offset");
    testWrongFooterSize(resourceName, footerSize - 10000, "Invalid footer size");
    testWrongFooterSize(resourceName, footerSize + 10000, "Invalid footer size");
  }

  /**
   * 测试场景：wrong footer size。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  private void testWrongFooterSize(
      String resourceName, long wrongFooterSize, String expectedMessagePrefix) throws Exception {
    InMemoryInputFile inputFile = new InMemoryInputFile(readTestResource(resourceName));
    Puffin.ReadBuilder builder =
        Puffin.read(inputFile).withFileSize(inputFile.getLength()).withFooterSize(wrongFooterSize);
    assertThatThrownBy(
            () -> {
              try (PuffinReader reader = builder.build()) {
                reader.fileMetadata();
              }
            })
        .hasMessageStartingWith(expectedMessagePrefix);
  }

  /**
   * 测试场景：read metric data uncompressed。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadMetricDataUncompressed() throws Exception {
    testReadMetricData("v1/sample-metric-data-uncompressed.bin", NONE);
  }

  /**
   * 测试场景：read metric data compressed zstd。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadMetricDataCompressedZstd() throws Exception {
    testReadMetricData("v1/sample-metric-data-compressed-zstd.bin", ZSTD);
  }

  /**
   * 测试场景：read metric data。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  private void testReadMetricData(String resourceName, PuffinCompressionCodec expectedCodec)
      throws Exception {
    InMemoryInputFile inputFile = new InMemoryInputFile(readTestResource(resourceName));
    try (PuffinReader reader = Puffin.read(inputFile).build()) {
      FileMetadata fileMetadata = reader.fileMetadata();
      assertThat(fileMetadata.properties())
          .as("file properties")
          .isEqualTo(ImmutableMap.of("created-by", "Test 1234"));
      assertThat(fileMetadata.blobs()).as("blob list").hasSize(2);

      BlobMetadata firstBlob = fileMetadata.blobs().get(0);
      assertThat(firstBlob.type()).as("type").isEqualTo("some-blob");
      assertThat(firstBlob.inputFields()).as("columns").isEqualTo(ImmutableList.of(1));
      assertThat(firstBlob.offset()).as("offset").isEqualTo(4);
      assertThat(firstBlob.compressionCodec())
          .as("compression codec")
          .isEqualTo(expectedCodec.codecName());

      BlobMetadata secondBlob = fileMetadata.blobs().get(1);
      assertThat(secondBlob.type()).as("type").isEqualTo("some-other-blob");
      assertThat(secondBlob.inputFields()).as("columns").isEqualTo(ImmutableList.of(2));
      assertThat(secondBlob.offset())
          .as("offset")
          .isEqualTo(firstBlob.offset() + firstBlob.length());
      assertThat(secondBlob.compressionCodec())
          .as("compression codec")
          .isEqualTo(expectedCodec.codecName());

      Map<BlobMetadata, byte[]> read =
          Streams.stream(reader.readAll(ImmutableList.of(firstBlob, secondBlob)))
              .collect(toImmutableMap(Pair::first, pair -> ByteBuffers.toByteArray(pair.second())));

      assertThat(read)
          .as("read")
          .containsOnlyKeys(firstBlob, secondBlob)
          .containsEntry(firstBlob, "abcdefghi".getBytes(UTF_8))
          .containsEntry(
              secondBlob,
              "some blob \u0000 binary data 🤯 that is not very very very very very very long, is it?"
                  .getBytes(UTF_8));
    }
  }

  /**
   * 测试场景：validate footer size value。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testValidateFooterSizeValue() throws Exception {
    // Ensure the definition of SAMPLE_METRIC_DATA_COMPRESSED_ZSTD_FOOTER_SIZE remains accurate
    InMemoryInputFile inputFile =
        new InMemoryInputFile(readTestResource("v1/sample-metric-data-compressed-zstd.bin"));
    try (PuffinReader reader =
        Puffin.read(inputFile)
            .withFooterSize(SAMPLE_METRIC_DATA_COMPRESSED_ZSTD_FOOTER_SIZE)
            .build()) {
      assertThat(reader.fileMetadata().properties())
          .isEqualTo(ImmutableMap.of("created-by", "Test 1234"));
    }
  }
}
