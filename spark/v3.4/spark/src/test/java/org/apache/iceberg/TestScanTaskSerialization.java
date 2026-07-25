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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.SparkTestBase;
import org.apache.iceberg.spark.source.ThreeColumnRecord;
import org.apache.iceberg.types.Types;
import org.apache.spark.SparkConf;
import org.apache.spark.serializer.KryoSerializer;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestScanTaskSerialization 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 扫描任务序列化 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestScanTaskSerialization extends SparkTestBase {

  private static final HadoopTables TABLES = new HadoopTables(new Configuration());
  private static final Schema SCHEMA =
      new Schema(
          optional(1, "c1", Types.IntegerType.get()),
          optional(2, "c2", Types.StringType.get()),
          optional(3, "c3", Types.StringType.get()));

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private String tableLocation = null;

  /** 初始化表路径。 */
  @Before
  public void setupTableLocation() throws Exception {
    File tableDir = temp.newFolder();
    this.tableLocation = tableDir.toURI().toString();
  }

  /** 测试基类combined扫描任务Kryo序列化场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBaseCombinedScanTaskKryoSerialization() throws Exception {
    BaseCombinedScanTask scanTask = prepareBaseCombinedScanTaskForSerDeTest();

    File data = temp.newFile();
    Assert.assertTrue(data.delete());
    Kryo kryo = new KryoSerializer(new SparkConf()).newKryo();

    try (Output out = new Output(new FileOutputStream(data))) {
      kryo.writeClassAndObject(out, scanTask);
    }

    try (Input in = new Input(new FileInputStream(data))) {
      Object obj = kryo.readClassAndObject(in);
      Assertions.assertThat(obj)
          .as("Should be a BaseCombinedScanTask")
          .isInstanceOf(BaseCombinedScanTask.class);
      TaskCheckHelper.assertEquals(scanTask, (BaseCombinedScanTask) obj);
    }
  }

  /** 测试基类combined扫描任务Java序列化场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBaseCombinedScanTaskJavaSerialization() throws Exception {
    BaseCombinedScanTask scanTask = prepareBaseCombinedScanTaskForSerDeTest();

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(scanTask);
    }

    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      Object obj = in.readObject();
      Assertions.assertThat(obj)
          .as("Should be a BaseCombinedScanTask")
          .isInstanceOf(BaseCombinedScanTask.class);
      TaskCheckHelper.assertEquals(scanTask, (BaseCombinedScanTask) obj);
    }
  }

  /** 测试基类扫描任务分组Kryo序列化场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  @SuppressWarnings("unchecked")
  public void testBaseScanTaskGroupKryoSerialization() throws Exception {
    BaseScanTaskGroup<FileScanTask> taskGroup = prepareBaseScanTaskGroupForSerDeTest();

    Assert.assertTrue("Task group can't be empty", taskGroup.tasks().size() > 0);

    File data = temp.newFile();
    Assert.assertTrue(data.delete());
    Kryo kryo = new KryoSerializer(new SparkConf()).newKryo();

    try (Output out = new Output(Files.newOutputStream(data.toPath()))) {
      kryo.writeClassAndObject(out, taskGroup);
    }

    try (Input in = new Input(Files.newInputStream(data.toPath()))) {
      Object obj = kryo.readClassAndObject(in);
      Assertions.assertThat(obj)
          .as("should be a BaseScanTaskGroup")
          .isInstanceOf(BaseScanTaskGroup.class);
      TaskCheckHelper.assertEquals(taskGroup, (BaseScanTaskGroup<FileScanTask>) obj);
    }
  }

  /** 测试基类扫描任务分组Java序列化场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  @SuppressWarnings("unchecked")
  public void testBaseScanTaskGroupJavaSerialization() throws Exception {
    BaseScanTaskGroup<FileScanTask> taskGroup = prepareBaseScanTaskGroupForSerDeTest();

    Assert.assertTrue("Task group can't be empty", taskGroup.tasks().size() > 0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(taskGroup);
    }

    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      Object obj = in.readObject();
      Assertions.assertThat(obj)
          .as("should be a BaseScanTaskGroup")
          .isInstanceOf(BaseScanTaskGroup.class);
      TaskCheckHelper.assertEquals(taskGroup, (BaseScanTaskGroup<FileScanTask>) obj);
    }
  }

  /** prepare基类combined扫描任务用于serde测试。 */
  private BaseCombinedScanTask prepareBaseCombinedScanTaskForSerDeTest() {
    Table table = initTable();
    CloseableIterable<FileScanTask> tasks = table.newScan().planFiles();
    return new BaseCombinedScanTask(Lists.newArrayList(tasks));
  }

  /** prepare基类扫描任务分组用于serde测试。 */
  private BaseScanTaskGroup<FileScanTask> prepareBaseScanTaskGroupForSerDeTest() {
    Table table = initTable();
    CloseableIterable<FileScanTask> tasks = table.newScan().planFiles();
    return new BaseScanTaskGroup<>(ImmutableList.copyOf(tasks));
  }

  /** init表。 */
  private Table initTable() {
    PartitionSpec spec = PartitionSpec.unpartitioned();
    Map<String, String> options = Maps.newHashMap();
    Table table = TABLES.create(SCHEMA, spec, options, tableLocation);

    List<ThreeColumnRecord> records1 =
        Lists.newArrayList(
            new ThreeColumnRecord(1, null, "AAAA"), new ThreeColumnRecord(1, "BBBBBBBBBB", "BBBB"));
    writeRecords(records1);

    List<ThreeColumnRecord> records2 =
        Lists.newArrayList(
            new ThreeColumnRecord(2, "CCCCCCCCCC", "CCCC"),
            new ThreeColumnRecord(2, "DDDDDDDDDD", "DDDD"));
    writeRecords(records2);

    table.refresh();

    return table;
  }

  /** 写记录。 */
  private void writeRecords(List<ThreeColumnRecord> records) {
    Dataset<Row> df = spark.createDataFrame(records, ThreeColumnRecord.class);
    writeDF(df);
  }

  /** 写df。 */
  private void writeDF(Dataset<Row> df) {
    df.select("c1", "c2", "c3").write().format("iceberg").mode("append").save(tableLocation);
  }
}
