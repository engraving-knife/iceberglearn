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
package org.apache.iceberg;

import static org.apache.iceberg.PartitionSpec.unpartitioned;
import static org.apache.iceberg.TableMetadata.newTableMetadata;
import static org.apache.iceberg.TableMetadataParser.getFileExtension;
import static org.apache.iceberg.types.Types.NestedField.optional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import org.apache.iceberg.TableMetadataParser.Codec;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types.BooleanType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TableMetadataParserTest，用于验证 Table Metadata Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Table Metadata Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TableMetadataParserTest {

  private static final Schema SCHEMA = new Schema(optional(1, "b", BooleanType.get()));

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "codecName = {0}")
  public static Object[] parameters() {
    return new Object[] {"none", "gzip"};
  }

  private final String codecName;

  /** 辅助方法：table metadata parser test。 */
  public TableMetadataParserTest(String codecName) {
    this.codecName = codecName;
  }

  /**
   * 测试场景：compression property。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCompressionProperty() throws IOException {
    Codec codec = Codec.fromName(codecName);
    String fileExtension = getFileExtension(codec);
    String fileName = "v3" + fileExtension;
    OutputFile outputFile = Files.localOutput(fileName);
    Map<String, String> properties = Maps.newHashMap();
    properties.put(TableProperties.METADATA_COMPRESSION, codecName);
    String location = "file://tmp/db/table";
    TableMetadata metadata = newTableMetadata(SCHEMA, unpartitioned(), location, properties);
    TableMetadataParser.write(metadata, outputFile);
    Assert.assertEquals(codec == Codec.GZIP, isCompressed(fileName));
    TableMetadata actualMetadata =
        TableMetadataParser.read((FileIO) null, Files.localInput(new File(fileName)));
    verifyMetadata(metadata, actualMetadata);
  }

  /** 辅助方法：cleanup。 */
  @After
  public void cleanup() throws IOException {
    Codec codec = Codec.fromName(codecName);
    Path metadataFilePath = Paths.get("v3" + getFileExtension(codec));
    java.nio.file.Files.deleteIfExists(metadataFilePath);
  }

  /** 辅助方法：verify metadata。 */
  private void verifyMetadata(TableMetadata expected, TableMetadata actual) {
    Assert.assertEquals(expected.schema().asStruct(), actual.schema().asStruct());
    Assert.assertEquals(expected.location(), actual.location());
    Assert.assertEquals(expected.lastColumnId(), actual.lastColumnId());
    Assert.assertEquals(expected.properties(), actual.properties());
  }

  /** 辅助方法：is compressed。 */
  private boolean isCompressed(String path) throws IOException {
    try (InputStream ignored = new GZIPInputStream(new FileInputStream(new File(path)))) {
      return true;
    } catch (ZipException e) {
      if (e.getMessage().equals("Not in GZIP format")) {
        return false;
      } else {
        throw e;
      }
    }
  }
}
