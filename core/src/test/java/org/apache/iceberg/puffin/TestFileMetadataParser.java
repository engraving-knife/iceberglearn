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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestFileMetadataParser，用于验证 File Metadata Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 File Metadata Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestFileMetadataParser {
  /**
   * 测试场景：invalid json。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidJson() {
    assertThatThrownBy(() -> FileMetadataParser.fromJson((String) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("argument \"content\" is null");

    assertThatThrownBy(() -> FileMetadataParser.fromJson(""))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("No content to map due to end-of-input");

    assertThatThrownBy(() -> FileMetadataParser.fromJson("{"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Unexpected end-of-input: expected close marker for Object");

    assertThatThrownBy(() -> FileMetadataParser.fromJson("{\"blobs\": []"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Unexpected end-of-input: expected close marker for Object");
  }

  /**
   * 测试场景：minimal file metadata。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMinimalFileMetadata() {
    testJsonSerialization(
        new FileMetadata(ImmutableList.of(), ImmutableMap.of()),
        "{\n" + "  \"blobs\" : [ ]\n" + "}");
  }

  /**
   * 测试场景：file properties。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFileProperties() {
    testJsonSerialization(
        new FileMetadata(ImmutableList.of(), ImmutableMap.of("a property", "a property value")),
        "{\n"
            + "  \"blobs\" : [ ],\n"
            + "  \"properties\" : {\n"
            + "    \"a property\" : \"a property value\"\n"
            + "  }\n"
            + "}");

    testJsonSerialization(
        new FileMetadata(
            ImmutableList.of(),
            ImmutableMap.of("a property", "a property value", "another one", "also with value")),
        "{\n"
            + "  \"blobs\" : [ ],\n"
            + "  \"properties\" : {\n"
            + "    \"a property\" : \"a property value\",\n"
            + "    \"another one\" : \"also with value\"\n"
            + "  }\n"
            + "}");
  }

  /**
   * 测试场景：missing blobs。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMissingBlobs() {
    assertThatThrownBy(() -> FileMetadataParser.fromJson("{\"properties\": {}}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing field: blobs");
  }

  /**
   * 测试场景：bad blobs。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBadBlobs() {
    assertThatThrownBy(() -> FileMetadataParser.fromJson("{\"blobs\": {}}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse blobs from non-array: {}");
  }

  /**
   * 测试场景：blob metadata。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBlobMetadata() {
    testJsonSerialization(
        new FileMetadata(
            ImmutableList.of(
                new BlobMetadata(
                    "type-a", ImmutableList.of(1), 14, 3, 4, 16, null, ImmutableMap.of()),
                new BlobMetadata(
                    "type-bbb",
                    ImmutableList.of(2, 3, 4),
                    77,
                    4,
                    Integer.MAX_VALUE * 10000L,
                    79834,
                    null,
                    ImmutableMap.of())),
            ImmutableMap.of()),
        "{\n"
            + "  \"blobs\" : [ {\n"
            + "    \"type\" : \"type-a\",\n"
            + "    \"fields\" : [ 1 ],\n"
            + "    \"snapshot-id\" : 14,\n"
            + "    \"sequence-number\" : 3,\n"
            + "    \"offset\" : 4,\n"
            + "    \"length\" : 16\n"
            + "  }, {\n"
            + "    \"type\" : \"type-bbb\",\n"
            + "    \"fields\" : [ 2, 3, 4 ],\n"
            + "    \"snapshot-id\" : 77,\n"
            + "    \"sequence-number\" : 4,\n"
            + "    \"offset\" : 21474836470000,\n"
            + "    \"length\" : 79834\n"
            + "  } ]\n"
            + "}");
  }

  /**
   * 测试场景：blob properties。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBlobProperties() {
    testJsonSerialization(
        new FileMetadata(
            ImmutableList.of(
                new BlobMetadata(
                    "type-a",
                    ImmutableList.of(1),
                    14,
                    3,
                    4,
                    16,
                    null,
                    ImmutableMap.of("some key", "some value"))),
            ImmutableMap.of()),
        "{\n"
            + "  \"blobs\" : [ {\n"
            + "    \"type\" : \"type-a\",\n"
            + "    \"fields\" : [ 1 ],\n"
            + "    \"snapshot-id\" : 14,\n"
            + "    \"sequence-number\" : 3,\n"
            + "    \"offset\" : 4,\n"
            + "    \"length\" : 16,\n"
            + "    \"properties\" : {\n"
            + "      \"some key\" : \"some value\"\n"
            + "    }\n"
            + "  } ]\n"
            + "}");
  }

  /**
   * 测试场景：field number out of range。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFieldNumberOutOfRange() {
    assertThatThrownBy(
            () ->
                FileMetadataParser.fromJson(
                    "{\n"
                        + "  \"blobs\" : [ {\n"
                        + "    \"type\" : \"type-a\",\n"
                        + "    \"fields\" : [ "
                        + (Integer.MAX_VALUE + 1L)
                        + " ],\n"
                        + "    \"offset\" : 4,\n"
                        + "    \"length\" : 16\n"
                        + "  } ]\n"
                        + "}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse integer from non-int value in fields: 2147483648");
  }

  /**
   * 测试场景：json serialization。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  private void testJsonSerialization(FileMetadata fileMetadata, String json) {
    assertThat(FileMetadataParser.toJson(fileMetadata, true)).isEqualTo(json);

    // Test round-trip. Note that FileMetadata doesn't implement equals()
    FileMetadata parsed = FileMetadataParser.fromJson(json);
    assertThat(FileMetadataParser.toJson(parsed, true)).isEqualTo(json);
  }
}
