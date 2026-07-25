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

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.source.SerializableTableWithSize;
import org.apache.iceberg.types.Types;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestFileIOSerialization 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 文件io序列化 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestFileIOSerialization {

  private static final Configuration CONF = new Configuration();
  private static final HadoopTables TABLES = new HadoopTables(CONF);

  private static final Schema SCHEMA =
      new Schema(
          required(1, "id", Types.LongType.get()),
          optional(2, "data", Types.StringType.get()),
          required(3, "date", Types.StringType.get()),
          optional(4, "double", Types.DoubleType.get()));

  private static final PartitionSpec SPEC =
      PartitionSpec.builderFor(SCHEMA).identity("date").build();

  private static final SortOrder SORT_ORDER = SortOrder.builderFor(SCHEMA).asc("id").build();

  static {
    CONF.set("k1", "v1");
    CONF.set("k2", "v2");
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private Table table;

  /** init表。 */
  @Before
  public void initTable() throws IOException {
    Map<String, String> props = ImmutableMap.of("k1", "v1", "k2", "v2");

    File tableLocation = temp.newFolder();
    Assert.assertTrue(tableLocation.delete());

    this.table = TABLES.create(SCHEMA, SPEC, SORT_ORDER, props, tableLocation.toString());
  }

  /** 测试Hadoop文件ioKryo序列化场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testHadoopFileIOKryoSerialization() throws IOException {
    FileIO io = table.io();
    Configuration expectedConf = ((HadoopFileIO) io).conf();

    Table serializableTable = SerializableTableWithSize.copyOf(table);
    FileIO deserializedIO = KryoHelpers.roundTripSerialize(serializableTable.io());
    Configuration actualConf = ((HadoopFileIO) deserializedIO).conf();

    Assert.assertEquals("Conf pairs must match", toMap(expectedConf), toMap(actualConf));
    Assert.assertEquals("Conf values must be present", "v1", actualConf.get("k1"));
    Assert.assertEquals("Conf values must be present", "v2", actualConf.get("k2"));
  }

  /** 测试Hadoop文件ioJava序列化场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testHadoopFileIOJavaSerialization() throws IOException, ClassNotFoundException {
    FileIO io = table.io();
    Configuration expectedConf = ((HadoopFileIO) io).conf();

    Table serializableTable = SerializableTableWithSize.copyOf(table);
    FileIO deserializedIO = TestHelpers.roundTripSerialize(serializableTable.io());
    Configuration actualConf = ((HadoopFileIO) deserializedIO).conf();

    Assert.assertEquals("Conf pairs must match", toMap(expectedConf), toMap(actualConf));
    Assert.assertEquals("Conf values must be present", "v1", actualConf.get("k1"));
    Assert.assertEquals("Conf values must be present", "v2", actualConf.get("k2"));
  }

  /** 到映射。 */
  private Map<String, String> toMap(Configuration conf) {
    Map<String, String> map = Maps.newHashMapWithExpectedSize(conf.size());
    conf.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
    return map;
  }
}
