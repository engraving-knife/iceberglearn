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
package org.apache.iceberg.avro;

import static org.apache.avro.Schema.Type.INT;
import static org.apache.avro.Schema.Type.LONG;
import static org.apache.avro.Schema.Type.NULL;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.iceberg.Files;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 测试类：TestAvroOptionsWithNonNullDefaults，用于验证 Avro Options With Non Null Defaults 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Avro Options With Non Null Defaults
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestAvroOptionsWithNonNullDefaults {

  @TempDir Path temp;

  /**
   * 测试场景：write and validate option with non null defaults pruning。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void writeAndValidateOptionWithNonNullDefaultsPruning() throws IOException {
    Schema writeSchema =
        Schema.createRecord(
            "root",
            null,
            null,
            false,
            ImmutableList.of(
                new Schema.Field(
                    "field",
                    Schema.createUnion(Schema.createArray(Schema.create(INT)), Schema.create(NULL)),
                    null,
                    ImmutableList.of())));

    GenericData.Record record1 = new GenericData.Record(writeSchema);
    record1.put("field", ImmutableList.of(1, 2, 3));
    GenericData.Record record2 = new GenericData.Record(writeSchema);
    record2.put("field", null);

    File testFile = temp.toFile();
    assertThat(testFile.delete()).as("Delete should succeed").isTrue();

    try (DataFileWriter<GenericData.Record> writer =
        new DataFileWriter<>(new GenericDatumWriter<>())) {
      writer.create(writeSchema, testFile);
      writer.append(record1);
      writer.append(record2);
    }

    List<GenericData.Record> expected = ImmutableList.of(record1, record2);

    org.apache.iceberg.Schema readIcebergSchema = AvroSchemaUtil.toIceberg(writeSchema);
    List<GenericData.Record> rows;
    try (AvroIterable<GenericData.Record> reader =
        Avro.read(Files.localInput(testFile)).project(readIcebergSchema).build()) {
      rows = Lists.newArrayList(reader);
    }

    for (int i = 0; i < expected.size(); i += 1) {
      AvroTestHelpers.assertEquals(readIcebergSchema.asStruct(), expected.get(i), rows.get(i));
    }
  }

  /**
   * 测试场景：write and validate option with non null defaults evolution。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void writeAndValidateOptionWithNonNullDefaultsEvolution() throws IOException {
    Schema writeSchema =
        Schema.createRecord(
            "root",
            null,
            null,
            false,
            ImmutableList.of(
                new Schema.Field(
                    "field",
                    Schema.createUnion(Schema.create(INT), Schema.create(NULL)),
                    null,
                    -1)));

    GenericData.Record record1 = new GenericData.Record(writeSchema);
    record1.put("field", 1);
    GenericData.Record record2 = new GenericData.Record(writeSchema);
    record2.put("field", null);

    File testFile = temp.toFile();
    assertThat(testFile.delete()).as("Delete should succeed").isTrue();

    try (DataFileWriter<GenericData.Record> writer =
        new DataFileWriter<>(new GenericDatumWriter<>())) {
      writer.create(writeSchema, testFile);
      writer.append(record1);
      writer.append(record2);
    }

    Schema readSchema =
        Schema.createRecord(
            "root",
            null,
            null,
            false,
            ImmutableList.of(
                new Schema.Field(
                    "field",
                    Schema.createUnion(Schema.create(LONG), Schema.create(NULL)),
                    null,
                    -1L)));

    GenericData.Record expectedRecord1 = new GenericData.Record(readSchema);
    expectedRecord1.put("field", 1L);
    GenericData.Record expectedRecord2 = new GenericData.Record(readSchema);
    expectedRecord2.put("field", null);
    List<GenericData.Record> expected = ImmutableList.of(expectedRecord1, expectedRecord2);

    org.apache.iceberg.Schema readIcebergSchema = AvroSchemaUtil.toIceberg(readSchema);
    List<GenericData.Record> rows;
    try (AvroIterable<GenericData.Record> reader =
        Avro.read(Files.localInput(testFile)).project(readIcebergSchema).build()) {
      rows = Lists.newArrayList(reader);
    }

    for (int i = 0; i < expected.size(); i += 1) {
      AvroTestHelpers.assertEquals(readIcebergSchema.asStruct(), expected.get(i), rows.get(i));
    }
  }
}
