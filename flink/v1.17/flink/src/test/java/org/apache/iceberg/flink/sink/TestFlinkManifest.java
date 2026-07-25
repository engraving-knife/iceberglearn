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
package org.apache.iceberg.flink.sink;

import static org.apache.iceberg.flink.sink.ManifestOutputFileFactory.FLINK_MANIFEST_LOCATION;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.flink.core.io.SimpleVersionedSerialization;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.table.data.RowData;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.ManifestFile;
import org.apache.iceberg.ManifestFiles;
import org.apache.iceberg.Table;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.SimpleDataUtil;
import org.apache.iceberg.flink.TestHelpers;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.WriteResult;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestFlinkManifest 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestFlinkManifest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkManifest {
  private static final Configuration CONF = new Configuration();

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Table table;
  private FileAppenderFactory<RowData> appenderFactory;
  private final AtomicInteger fileCount = new AtomicInteger(0);

  /** 辅助方法：before，before。 */
  @Before
  public void before() throws IOException {
    File folder = tempFolder.newFolder();
    String warehouse = folder.getAbsolutePath();

    String tablePath = warehouse.concat("/test");
    Assert.assertTrue("Should create the table directory correctly.", new File(tablePath).mkdir());

    // Construct the iceberg table.
    table = SimpleDataUtil.createTable(tablePath, ImmutableMap.of(), false);

    int[] equalityFieldIds =
        new int[] {
          table.schema().findField("id").fieldId(), table.schema().findField("data").fieldId()
        };
    this.appenderFactory =
        new FlinkAppenderFactory(
            table,
            table.schema(),
            FlinkSchemaUtil.convert(table.schema()),
            table.properties(),
            table.spec(),
            equalityFieldIds,
            table.schema(),
            null);
  }

  /**
   * 测试场景：IO。
   *
   * <p>验证该方法在 IO 条件下的行为是否符合预期。
   */
  @Test
  public void testIO() throws IOException {
    String flinkJobId = newFlinkJobId();
    String operatorId = newOperatorUniqueId();
    for (long checkpointId = 1; checkpointId <= 3; checkpointId++) {
      ManifestOutputFileFactory factory =
          FlinkManifestUtil.createOutputFileFactory(
              () -> table, table.properties(), flinkJobId, operatorId, 1, 1);
      final long curCkpId = checkpointId;

      List<DataFile> dataFiles = generateDataFiles(10);
      List<DeleteFile> eqDeleteFiles = generateEqDeleteFiles(5);
      List<DeleteFile> posDeleteFiles = generatePosDeleteFiles(5);
      DeltaManifests deltaManifests =
          FlinkManifestUtil.writeCompletedFiles(
              WriteResult.builder()
                  .addDataFiles(dataFiles)
                  .addDeleteFiles(eqDeleteFiles)
                  .addDeleteFiles(posDeleteFiles)
                  .build(),
              () -> factory.create(curCkpId),
              table.spec());

      WriteResult result =
          FlinkManifestUtil.readCompletedFiles(deltaManifests, table.io(), table.specs());
      Assert.assertEquals("Size of data file list are not equal.", 10, result.deleteFiles().length);
      for (int i = 0; i < dataFiles.size(); i++) {
        TestHelpers.assertEquals(dataFiles.get(i), result.dataFiles()[i]);
      }
      Assert.assertEquals("Size of delete file list are not equal.", 10, result.dataFiles().length);
      for (int i = 0; i < 5; i++) {
        TestHelpers.assertEquals(eqDeleteFiles.get(i), result.deleteFiles()[i]);
      }
      for (int i = 0; i < 5; i++) {
        TestHelpers.assertEquals(posDeleteFiles.get(i), result.deleteFiles()[5 + i]);
      }
    }
  }

  /**
   * 测试场景：User Provided Manifest Location。
   *
   * <p>验证该方法在 User Provided Manifest Location 条件下的行为是否符合预期。
   */
  @Test
  public void testUserProvidedManifestLocation() throws IOException {
    long checkpointId = 1;
    String flinkJobId = newFlinkJobId();
    String operatorId = newOperatorUniqueId();
    File userProvidedFolder = tempFolder.newFolder();
    Map<String, String> props =
        ImmutableMap.of(FLINK_MANIFEST_LOCATION, userProvidedFolder.getAbsolutePath() + "///");
    ManifestOutputFileFactory factory =
        new ManifestOutputFileFactory(() -> table, props, flinkJobId, operatorId, 1, 1);

    List<DataFile> dataFiles = generateDataFiles(5);
    DeltaManifests deltaManifests =
        FlinkManifestUtil.writeCompletedFiles(
            WriteResult.builder().addDataFiles(dataFiles).build(),
            () -> factory.create(checkpointId),
            table.spec());

    Assert.assertNotNull("Data manifest shouldn't be null", deltaManifests.dataManifest());
    Assert.assertNull("Delete manifest should be null", deltaManifests.deleteManifest());
    Assert.assertEquals(
        "The newly created manifest file should be located under the user provided directory",
        userProvidedFolder.toPath(),
        Paths.get(deltaManifests.dataManifest().path()).getParent());

    WriteResult result =
        FlinkManifestUtil.readCompletedFiles(deltaManifests, table.io(), table.specs());

    Assert.assertEquals(0, result.deleteFiles().length);
    Assert.assertEquals(5, result.dataFiles().length);

    Assert.assertEquals(
        "Size of data file list are not equal.", dataFiles.size(), result.dataFiles().length);
    for (int i = 0; i < dataFiles.size(); i++) {
      TestHelpers.assertEquals(dataFiles.get(i), result.dataFiles()[i]);
    }
  }

  /**
   * 测试场景：Versioned Serializer。
   *
   * <p>验证该方法在 Versioned Serializer 条件下的行为是否符合预期。
   */
  @Test
  public void testVersionedSerializer() throws IOException {
    long checkpointId = 1;
    String flinkJobId = newFlinkJobId();
    String operatorId = newOperatorUniqueId();
    ManifestOutputFileFactory factory =
        FlinkManifestUtil.createOutputFileFactory(
            () -> table, table.properties(), flinkJobId, operatorId, 1, 1);

    List<DataFile> dataFiles = generateDataFiles(10);
    List<DeleteFile> eqDeleteFiles = generateEqDeleteFiles(10);
    List<DeleteFile> posDeleteFiles = generatePosDeleteFiles(10);
    DeltaManifests expected =
        FlinkManifestUtil.writeCompletedFiles(
            WriteResult.builder()
                .addDataFiles(dataFiles)
                .addDeleteFiles(eqDeleteFiles)
                .addDeleteFiles(posDeleteFiles)
                .build(),
            () -> factory.create(checkpointId),
            table.spec());

    byte[] versionedSerializeData =
        SimpleVersionedSerialization.writeVersionAndSerialize(
            DeltaManifestsSerializer.INSTANCE, expected);
    DeltaManifests actual =
        SimpleVersionedSerialization.readVersionAndDeSerialize(
            DeltaManifestsSerializer.INSTANCE, versionedSerializeData);
    TestHelpers.assertEquals(expected.dataManifest(), actual.dataManifest());
    TestHelpers.assertEquals(expected.deleteManifest(), actual.deleteManifest());

    byte[] versionedSerializeData2 =
        SimpleVersionedSerialization.writeVersionAndSerialize(
            DeltaManifestsSerializer.INSTANCE, actual);
    Assert.assertArrayEquals(versionedSerializeData, versionedSerializeData2);
  }

  /**
   * 测试场景：Compatibility。
   *
   * <p>验证该方法在 Compatibility 条件下的行为是否符合预期。
   */
  @Test
  public void testCompatibility() throws IOException {
    // The v2 deserializer should be able to deserialize the v1 binary.
    long checkpointId = 1;
    String flinkJobId = newFlinkJobId();
    String operatorId = newOperatorUniqueId();
    ManifestOutputFileFactory factory =
        FlinkManifestUtil.createOutputFileFactory(
            () -> table, table.properties(), flinkJobId, operatorId, 1, 1);

    List<DataFile> dataFiles = generateDataFiles(10);
    ManifestFile manifest =
        FlinkManifestUtil.writeDataFiles(factory.create(checkpointId), table.spec(), dataFiles);
    byte[] dataV1 =
        SimpleVersionedSerialization.writeVersionAndSerialize(new V1Serializer(), manifest);

    DeltaManifests delta =
        SimpleVersionedSerialization.readVersionAndDeSerialize(
            DeltaManifestsSerializer.INSTANCE, dataV1);
    Assert.assertNull("Serialization v1 don't include delete files.", delta.deleteManifest());
    Assert.assertNotNull(
        "Serialization v1 should not have null data manifest.", delta.dataManifest());
    TestHelpers.assertEquals(manifest, delta.dataManifest());

    List<DataFile> actualFiles =
        FlinkManifestUtil.readDataFiles(delta.dataManifest(), table.io(), table.specs());
    Assert.assertEquals(10, actualFiles.size());
    for (int i = 0; i < 10; i++) {
      TestHelpers.assertEquals(dataFiles.get(i), actualFiles.get(i));
    }
  }

  private static class V1Serializer implements SimpleVersionedSerializer<ManifestFile> {

    /** 辅助方法：getVersion，get Version。 */
    @Override
    public int getVersion() {
      return 1;
    }

    /** 辅助方法：serialize，serialize。 */
    @Override
    public byte[] serialize(ManifestFile m) throws IOException {
      return ManifestFiles.encode(m);
    }

    /** 辅助方法：deserialize，deserialize。 */
    @Override
    public ManifestFile deserialize(int version, byte[] serialized) throws IOException {
      return ManifestFiles.decode(serialized);
    }
  }

  /** 辅助方法：writeDataFile，write Data File。 */
  private DataFile writeDataFile(String filename, List<RowData> rows) throws IOException {
    return SimpleDataUtil.writeFile(
        table,
        table.schema(),
        table.spec(),
        CONF,
        table.location(),
        FileFormat.PARQUET.addExtension(filename),
        rows);
  }

  /** 辅助方法：writeEqDeleteFile，write Eq Delete File。 */
  private DeleteFile writeEqDeleteFile(String filename, List<RowData> deletes) throws IOException {
    return SimpleDataUtil.writeEqDeleteFile(
        table, FileFormat.PARQUET, filename, appenderFactory, deletes);
  }

  /** 辅助方法：writePosDeleteFile，write Pos Delete File。 */
  private DeleteFile writePosDeleteFile(String filename, List<Pair<CharSequence, Long>> positions)
      throws IOException {
    return SimpleDataUtil.writePosDeleteFile(
        table, FileFormat.PARQUET, filename, appenderFactory, positions);
  }

  /** 辅助方法：generateDataFiles，generate Data Files。 */
  private List<DataFile> generateDataFiles(int fileNum) throws IOException {
    List<RowData> rowDataList = Lists.newArrayList();
    List<DataFile> dataFiles = Lists.newArrayList();
    for (int i = 0; i < fileNum; i++) {
      rowDataList.add(SimpleDataUtil.createRowData(i, "a" + i));
      dataFiles.add(writeDataFile("data-file-" + fileCount.incrementAndGet(), rowDataList));
    }
    return dataFiles;
  }

  /** 辅助方法：generateEqDeleteFiles，generate Eq Delete Files。 */
  private List<DeleteFile> generateEqDeleteFiles(int fileNum) throws IOException {
    List<RowData> rowDataList = Lists.newArrayList();
    List<DeleteFile> deleteFiles = Lists.newArrayList();
    for (int i = 0; i < fileNum; i++) {
      rowDataList.add(SimpleDataUtil.createDelete(i, "a" + i));
      deleteFiles.add(
          writeEqDeleteFile("eq-delete-file-" + fileCount.incrementAndGet(), rowDataList));
    }
    return deleteFiles;
  }

  /** 辅助方法：generatePosDeleteFiles，generate Pos Delete Files。 */
  private List<DeleteFile> generatePosDeleteFiles(int fileNum) throws IOException {
    List<Pair<CharSequence, Long>> positions = Lists.newArrayList();
    List<DeleteFile> deleteFiles = Lists.newArrayList();
    for (int i = 0; i < fileNum; i++) {
      positions.add(Pair.of("data-file-1", (long) i));
      deleteFiles.add(
          writePosDeleteFile("pos-delete-file-" + fileCount.incrementAndGet(), positions));
    }
    return deleteFiles;
  }

  /** 辅助方法：newFlinkJobId，new Flink Job Id。 */
  private static String newFlinkJobId() {
    return UUID.randomUUID().toString();
  }

  /** 辅助方法：newOperatorUniqueId，new Operator Unique Id。 */
  private static String newOperatorUniqueId() {
    return UUID.randomUUID().toString();
  }
}
