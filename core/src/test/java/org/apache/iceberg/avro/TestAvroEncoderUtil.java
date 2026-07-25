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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.iceberg.types.Type;

/**
 * 测试类：TestAvroEncoderUtil，用于验证 Avro Encoder Util 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Avro Encoder Util 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestAvroEncoderUtil extends AvroDataTest {

  /** 辅助方法：write and validate。 */
  @Override
  protected void writeAndValidate(org.apache.iceberg.Schema schema) throws IOException {
    List<GenericData.Record> expected = RandomAvroData.generate(schema, 100, 1990L);
    Map<Type, Schema> typeToSchema = AvroSchemaUtil.convertTypes(schema.asStruct(), "test");
    Schema avroSchema = typeToSchema.get(schema.asStruct());

    for (GenericData.Record record : expected) {
      byte[] serializedData = AvroEncoderUtil.encode(record, avroSchema);
      GenericData.Record expectedRecord = AvroEncoderUtil.decode(serializedData);

      // Fallback to compare the record's string, because its equals implementation will depend on
      // the avro schema.
      // While the avro schema will convert the 'map' type to be a list of key/value pairs for
      // non-string keys, it
      // would be failing to read the 'array' from a 'map'.
      assertThat(record.toString()).isEqualTo(expectedRecord.toString());

      byte[] serializedData2 = AvroEncoderUtil.encode(expectedRecord, avroSchema);
      assertThat(serializedData2).isEqualTo(serializedData);

      expectedRecord = AvroEncoderUtil.decode(serializedData2);
      assertThat(record.toString()).isEqualTo(expectedRecord.toString());
    }
  }
}
