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
package org.apache.iceberg.mr.hive;

import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.serde2.SerDeException;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.RandomGenericData;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.mr.Catalogs;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.mr.hive.serde.objectinspector.IcebergObjectInspector;
import org.apache.iceberg.mr.mapred.Container;
import org.apache.iceberg.types.Types;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestHiveIcebergSerDe 的功能。
 *
 * <p>所属模块：iceberg-mr。职责：验证 TestHiveIcebergSerDe 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestHiveIcebergSerDe {

  private static final Schema schema =
      new Schema(required(1, "string_field", Types.StringType.get()));

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  /**
   * 测试场景：Initialize。
   *
   * <p>验证该方法在 Initialize 条件下的行为是否符合预期。
   */
  @Test
  public void testInitialize() throws IOException, SerDeException {
    File location = tmp.newFolder();
    Assert.assertTrue(location.delete());

    Configuration conf = new Configuration();

    Properties properties = new Properties();
    properties.setProperty("location", location.toString());
    properties.setProperty(InputFormatConfig.CATALOG_NAME, Catalogs.ICEBERG_HADOOP_TABLE_NAME);

    HadoopTables tables = new HadoopTables(conf);
    tables.create(schema, location.toString());

    HiveIcebergSerDe serDe = new HiveIcebergSerDe();
    serDe.initialize(conf, properties);

    Assert.assertEquals(IcebergObjectInspector.create(schema), serDe.getObjectInspector());
  }

  /**
   * 测试场景：Deserialize。
   *
   * <p>验证该方法在 Deserialize 条件下的行为是否符合预期。
   */
  @Test
  public void testDeserialize() {
    HiveIcebergSerDe serDe = new HiveIcebergSerDe();

    Record record = RandomGenericData.generate(schema, 1, 0).get(0);
    Container<Record> container = new Container<>();
    container.set(record);

    Assert.assertEquals(record, serDe.deserialize(container));
  }
}
