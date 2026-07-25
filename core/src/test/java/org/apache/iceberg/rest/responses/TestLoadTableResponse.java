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
package org.apache.iceberg.rest.responses;

import static org.apache.iceberg.TestHelpers.assertSameSchemaList;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.iceberg.NullOrder;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableMetadataParser;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestLoadTableResponse，用于验证 Load Table Response 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Load Table Response 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestLoadTableResponse extends RequestResponseTestBase<LoadTableResponse> {

  private static final String TEST_METADATA_LOCATION =
      "s3://bucket/test/location/metadata/v1.metadata.json";

  private static final String TEST_TABLE_LOCATION = "s3://bucket/test/location";

  private static final Schema SCHEMA_7 =
      new Schema(
          7,
          Types.NestedField.required(1, "x", Types.LongType.get()),
          Types.NestedField.required(2, "y", Types.LongType.get(), "comment"),
          Types.NestedField.required(3, "z", Types.LongType.get()));

  private static final PartitionSpec SPEC_5 =
      PartitionSpec.builderFor(SCHEMA_7).withSpecId(5).build();

  private static final SortOrder SORT_ORDER_3 =
      SortOrder.builderFor(SCHEMA_7)
          .withOrderId(3)
          .asc("y", NullOrder.NULLS_FIRST)
          .desc(Expressions.bucket("z", 4), NullOrder.NULLS_LAST)
          .build();

  private static final Map<String, String> TABLE_PROPS =
      ImmutableMap.of(
          "format-version", "1",
          "owner", "hank");

  private static final Map<String, String> CONFIG = ImmutableMap.of("foo", "bar");

  /** 辅助方法：all fields from spec。 */
  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"metadata-location", "metadata", "config"};
  }

  /** 辅助方法：create example instance。 */
  @Override
  public LoadTableResponse createExampleInstance() {
    TableMetadata metadata =
        TableMetadata.buildFrom(
                TableMetadata.newTableMetadata(
                    SCHEMA_7, SPEC_5, SORT_ORDER_3, TEST_TABLE_LOCATION, TABLE_PROPS))
            .discardChanges()
            .withMetadataLocation(TEST_METADATA_LOCATION)
            .build();

    return LoadTableResponse.builder().withTableMetadata(metadata).addAllConfig(CONFIG).build();
  }

  /** 辅助方法：deserialize。 */
  @Override
  public LoadTableResponse deserialize(String json) throws JsonProcessingException {
    LoadTableResponse resp = mapper().readValue(json, LoadTableResponse.class);
    resp.validate();
    return resp;
  }

  /**
   * 测试场景：failures。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFailures() {
    Assertions.assertThatThrownBy(() -> LoadTableResponse.builder().build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid metadata: null");
  }

  /**
   * 测试场景：round trip serde with 1 table metadata。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRoundTripSerdeWithV1TableMetadata() throws Exception {
    String tableMetadataJson = readTableMetadataInputFile("TableMetadataV1Valid.json");
    TableMetadata v1Metadata =
        TableMetadataParser.fromJson(TEST_METADATA_LOCATION, tableMetadataJson);
    // Convert the TableMetadata JSON from the file to an object and then back to JSON so that
    // missing fields
    // are filled in with their default values.
    String json =
        String.format(
            "{\"metadata-location\":\"%s\",\"metadata\":%s,\"config\":{\"foo\":\"bar\"}}",
            TEST_METADATA_LOCATION, TableMetadataParser.toJson(v1Metadata));
    LoadTableResponse resp =
        LoadTableResponse.builder().withTableMetadata(v1Metadata).addAllConfig(CONFIG).build();
    assertRoundTripSerializesEquallyFrom(json, resp);
  }

  /**
   * 测试场景：missing schema type。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMissingSchemaType() throws Exception {
    // When the schema type (struct) is missing
    String tableMetadataJson = readTableMetadataInputFile("TableMetadataV1MissingSchemaType.json");
    Assertions.assertThatThrownBy(
            () -> TableMetadataParser.fromJson(TEST_METADATA_LOCATION, tableMetadataJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot parse type from json:");
  }

  /**
   * 测试场景：round trip serde with 2 table metadata。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRoundTripSerdeWithV2TableMetadata() throws Exception {
    String tableMetadataJson = readTableMetadataInputFile("TableMetadataV2Valid.json");
    TableMetadata v2Metadata =
        TableMetadataParser.fromJson(TEST_METADATA_LOCATION, tableMetadataJson);
    // Convert the TableMetadata JSON from the file to an object and then back to JSON so that
    // missing fields
    // are filled in with their default values.
    String json =
        String.format(
            "{\"metadata-location\":\"%s\",\"metadata\":%s,\"config\":{\"foo\":\"bar\"}}",
            TEST_METADATA_LOCATION, TableMetadataParser.toJson(v2Metadata));
    LoadTableResponse resp =
        LoadTableResponse.builder().withTableMetadata(v2Metadata).addAllConfig(CONFIG).build();
    assertRoundTripSerializesEquallyFrom(json, resp);
  }

  /**
   * 测试场景：can deserialize without default values。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCanDeserializeWithoutDefaultValues() throws Exception {
    String metadataJson = readTableMetadataInputFile("TableMetadataV1Valid.json");
    // `config` is missing in the JSON
    String json =
        String.format(
            "{\"metadata-location\":\"%s\",\"metadata\":%s}", TEST_METADATA_LOCATION, metadataJson);
    TableMetadata metadata = TableMetadataParser.fromJson(TEST_METADATA_LOCATION, metadataJson);
    LoadTableResponse actual = deserialize(json);
    LoadTableResponse expected = LoadTableResponse.builder().withTableMetadata(metadata).build();
    assertEquals(actual, expected);
    Assertions.assertThat(actual.config())
        .as("Deserialized JSON with missing fields should have the default values")
        .isEqualTo(ImmutableMap.of());
  }

  /** 辅助方法：assert equals。 */
  @Override
  public void assertEquals(LoadTableResponse actual, LoadTableResponse expected) {
    Assertions.assertThat(actual.config())
        .as("Should have the same configuration")
        .isEqualTo(expected.config());
    assertEqualTableMetadata(actual.tableMetadata(), expected.tableMetadata());
    Assertions.assertThat(actual.metadataLocation())
        .as("Should have the same metadata location")
        .isEqualTo(expected.metadataLocation());
  }

  /** 辅助方法：assert equal table metadata。 */
  private void assertEqualTableMetadata(TableMetadata actual, TableMetadata expected) {
    Assertions.assertThat(actual.formatVersion())
        .as("Format version should match")
        .isEqualTo(expected.formatVersion());
    Assertions.assertThat(actual.uuid()).as("Table UUID should match").isEqualTo(expected.uuid());
    Assertions.assertThat(actual.location())
        .as("Table location should match")
        .isEqualTo(expected.location());
    Assertions.assertThat(actual.lastColumnId())
        .as("Last column id")
        .isEqualTo(expected.lastColumnId());
    Assertions.assertThat(actual.schema().asStruct())
        .as("Schema should match")
        .isEqualTo(expected.schema().asStruct());
    assertSameSchemaList(expected.schemas(), actual.schemas());
    Assertions.assertThat(actual.currentSchemaId())
        .as("Current schema id should match")
        .isEqualTo(expected.currentSchemaId());
    Assertions.assertThat(actual.schema().asStruct())
        .as("Schema should match")
        .isEqualTo(expected.schema().asStruct());
    Assertions.assertThat(actual.lastSequenceNumber())
        .as("Last sequence number should match")
        .isEqualTo(expected.lastSequenceNumber());
    Assertions.assertThat(actual.spec().toString())
        .as("Partition spec should match")
        .isEqualTo(expected.spec().toString());
    Assertions.assertThat(actual.defaultSpecId())
        .as("Default spec ID should match")
        .isEqualTo(expected.defaultSpecId());
    Assertions.assertThat(actual.specs())
        .as("PartitionSpec map should match")
        .isEqualTo(expected.specs());
    Assertions.assertThat(actual.defaultSortOrderId())
        .as("Default Sort ID should match")
        .isEqualTo(expected.defaultSortOrderId());
    Assertions.assertThat(actual.sortOrder())
        .as("Sort order should match")
        .isEqualTo(expected.sortOrder());
    Assertions.assertThat(actual.sortOrders())
        .as("Sort order map should match")
        .isEqualTo(expected.sortOrders());
    Assertions.assertThat(actual.properties())
        .as("Properties should match")
        .isEqualTo(expected.properties());
    Assertions.assertThat(Lists.transform(actual.snapshots(), Snapshot::snapshotId))
        .as("Snapshots should match")
        .isEqualTo(Lists.transform(expected.snapshots(), Snapshot::snapshotId));
    Assertions.assertThat(actual.snapshotLog())
        .as("History should match")
        .isEqualTo(expected.snapshotLog());
    Snapshot expectedCurrentSnapshot = expected.currentSnapshot();
    Snapshot actualCurrentSnapshot = actual.currentSnapshot();
    Assertions.assertThat(
            expectedCurrentSnapshot != null && actualCurrentSnapshot != null
                || expectedCurrentSnapshot == null && actualCurrentSnapshot == null)
        .as("Both expected and actual current snapshot should either be null or non-null")
        .isTrue();
    if (expectedCurrentSnapshot != null) {
      Assertions.assertThat(actual.currentSnapshot().snapshotId())
          .as("Current snapshot ID should match")
          .isEqualTo(expected.currentSnapshot().snapshotId());
      Assertions.assertThat(actual.currentSnapshot().parentId())
          .as("Parent snapshot ID should match")
          .isEqualTo(expected.currentSnapshot().parentId());
      Assertions.assertThat(actual.currentSnapshot().schemaId())
          .as("Schema ID for current snapshot should match")
          .isEqualTo(expected.currentSnapshot().schemaId());
    }
    Assertions.assertThat(actual.metadataFileLocation())
        .as("Metadata file location should match")
        .isEqualTo(expected.metadataFileLocation());
    Assertions.assertThat(actual.lastColumnId())
        .as("Last column id should match")
        .isEqualTo(expected.lastColumnId());
    Assertions.assertThat(actual.schema().asStruct())
        .as("Schema should match")
        .isEqualTo(expected.schema().asStruct());
    assertSameSchemaList(expected.schemas(), actual.schemas());
    Assertions.assertThat(actual.currentSchemaId())
        .as("Current schema id should match")
        .isEqualTo(expected.currentSchemaId());
    Assertions.assertThat(actual.refs()).as("Refs map should match").isEqualTo(expected.refs());
  }

  /** 辅助方法：read table metadata input file。 */
  private String readTableMetadataInputFile(String fileName) throws Exception {
    Path path = Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
    return String.join("", java.nio.file.Files.readAllLines(path));
  }
}
