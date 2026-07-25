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
package org.apache.iceberg.spark.actions;

import static org.apache.iceberg.types.Types.NestedField.optional;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileMetadata;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.TestHelpers;
import org.apache.iceberg.actions.ActionsProvider;
import org.apache.iceberg.actions.DeleteOrphanFiles;
import org.apache.iceberg.actions.DeleteReachableFiles;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.spark.SparkTestBase;
import org.apache.iceberg.types.Types;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestDeleteReachableFilesAction 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 删除reachable文件动作 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestDeleteReachableFilesAction extends SparkTestBase {
  private static final HadoopTables TABLES = new HadoopTables(new Configuration());
  private static final Schema SCHEMA =
      new Schema(
          optional(1, "c1", Types.IntegerType.get()),
          optional(2, "c2", Types.StringType.get()),
          optional(3, "c3", Types.StringType.get()));
  private static final int SHUFFLE_PARTITIONS = 2;

  private static final PartitionSpec SPEC = PartitionSpec.builderFor(SCHEMA).identity("c1").build();

  static final DataFile FILE_A =
      DataFiles.builder(SPEC)
          .withPath("/path/to/data-a.parquet")
          .withFileSizeInBytes(10)
          .withPartition(TestHelpers.Row.of(0))
          .withRecordCount(1)
          .build();
  static final DataFile FILE_B =
      DataFiles.builder(SPEC)
          .withPath("/path/to/data-b.parquet")
          .withFileSizeInBytes(10)
          .withPartition(TestHelpers.Row.of(1))
          .withRecordCount(1)
          .build();
  static final DataFile FILE_C =
      DataFiles.builder(SPEC)
          .withPath("/path/to/data-c.parquet")
          .withFileSizeInBytes(10)
          .withPartition(TestHelpers.Row.of(2))
          .withRecordCount(1)
          .build();
  static final DataFile FILE_D =
      DataFiles.builder(SPEC)
          .withPath("/path/to/data-d.parquet")
          .withFileSizeInBytes(10)
          .withPartition(TestHelpers.Row.of(3))
          .withRecordCount(1)
          .build();
  static final DeleteFile FILE_A_POS_DELETES =
      FileMetadata.deleteFileBuilder(SPEC)
          .ofPositionDeletes()
          .withPath("/path/to/data-a-pos-deletes.parquet")
          .withFileSizeInBytes(10)
          .withPartition(TestHelpers.Row.of(0))
          .withRecordCount(1)
          .build();
  static final DeleteFile FILE_A_EQ_DELETES =
      FileMetadata.deleteFileBuilder(SPEC)
          .ofEqualityDeletes()
          .withPath("/path/to/data-a-eq-deletes.parquet")
          .withFileSizeInBytes(10)
          .withPartition(TestHelpers.Row.of(0))
          .withRecordCount(1)
          .build();

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private Table table;

  /** 初始化表路径。 */
  @Before
  public void setupTableLocation() throws Exception {
    File tableDir = temp.newFolder();
    String tableLocation = tableDir.toURI().toString();
    this.table = TABLES.create(SCHEMA, SPEC, Maps.newHashMap(), tableLocation);
    spark.conf().set("spark.sql.shuffle.partitions", SHUFFLE_PARTITIONS);
  }

  /** 检查移除文件结果。 */
  private void checkRemoveFilesResults(
      long expectedDatafiles,
      long expectedPosDeleteFiles,
      long expectedEqDeleteFiles,
      long expectedManifestsDeleted,
      long expectedManifestListsDeleted,
      long expectedOtherFilesDeleted,
      DeleteReachableFiles.Result results) {
    Assert.assertEquals(
        "Incorrect number of manifest files deleted",
        expectedManifestsDeleted,
        results.deletedManifestsCount());
    Assert.assertEquals(
        "Incorrect number of datafiles deleted",
        expectedDatafiles,
        results.deletedDataFilesCount());
    Assert.assertEquals(
        "Incorrect number of position delete files deleted",
        expectedPosDeleteFiles,
        results.deletedPositionDeleteFilesCount());
    Assert.assertEquals(
        "Incorrect number of equality delete files deleted",
        expectedEqDeleteFiles,
        results.deletedEqualityDeleteFilesCount());
    Assert.assertEquals(
        "Incorrect number of manifest lists deleted",
        expectedManifestListsDeleted,
        results.deletedManifestListsCount());
    Assert.assertEquals(
        "Incorrect number of other lists deleted",
        expectedOtherFilesDeleted,
        results.deletedOtherFilesCount());
  }

  /** 数据文件cleanup带并行任务。 */
  @Test
  public void dataFilesCleanupWithParallelTasks() {
    table.newFastAppend().appendFile(FILE_A).commit();

    table.newFastAppend().appendFile(FILE_B).commit();

    table.newRewrite().rewriteFiles(ImmutableSet.of(FILE_B), ImmutableSet.of(FILE_D)).commit();

    table.newRewrite().rewriteFiles(ImmutableSet.of(FILE_A), ImmutableSet.of(FILE_C)).commit();

    Set<String> deletedFiles = ConcurrentHashMap.newKeySet();
    Set<String> deleteThreads = ConcurrentHashMap.newKeySet();
    AtomicInteger deleteThreadsIndex = new AtomicInteger(0);

    DeleteReachableFiles.Result result =
        sparkActions()
            .deleteReachableFiles(metadataLocation(table))
            .io(table.io())
            .executeDeleteWith(
                Executors.newFixedThreadPool(
                    4,
                    runnable -> {
                      Thread thread = new Thread(runnable);
                      thread.setName("remove-files-" + deleteThreadsIndex.getAndIncrement());
                      thread.setDaemon(
                          true); // daemon threads will be terminated abruptly when the JVM exits
                      return thread;
                    }))
            .deleteWith(
                s -> {
                  deleteThreads.add(Thread.currentThread().getName());
                  deletedFiles.add(s);
                })
            .execute();

    // Verifies that the delete methods ran in the threads created by the provided ExecutorService
    // ThreadFactory
    Assert.assertEquals(
        deleteThreads,
        Sets.newHashSet("remove-files-0", "remove-files-1", "remove-files-2", "remove-files-3"));

    Lists.newArrayList(FILE_A, FILE_B, FILE_C, FILE_D)
        .forEach(
            file ->
                Assert.assertTrue(
                    "FILE_A should be deleted", deletedFiles.contains(FILE_A.path().toString())));
    checkRemoveFilesResults(4L, 0, 0, 6L, 4L, 6, result);
  }

  /** 测试带expiringdanglingstage提交场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testWithExpiringDanglingStageCommit() {
    table.location();
    // `A` commit
    table.newAppend().appendFile(FILE_A).commit();

    // `B` staged commit
    table.newAppend().appendFile(FILE_B).stageOnly().commit();

    // `C` commit
    table.newAppend().appendFile(FILE_C).commit();

    DeleteReachableFiles.Result result =
        sparkActions().deleteReachableFiles(metadataLocation(table)).io(table.io()).execute();

    checkRemoveFilesResults(3L, 0, 0, 3L, 3L, 5, result);
  }

  /** 测试移除文件动作上空表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRemoveFileActionOnEmptyTable() {
    DeleteReachableFiles.Result result =
        sparkActions().deleteReachableFiles(metadataLocation(table)).io(table.io()).execute();

    checkRemoveFilesResults(0, 0, 0, 0, 0, 2, result);
  }

  /** 测试移除文件动作带reduced版本表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRemoveFilesActionWithReducedVersionsTable() {
    table.updateProperties().set(TableProperties.METADATA_PREVIOUS_VERSIONS_MAX, "2").commit();
    table.newAppend().appendFile(FILE_A).commit();

    table.newAppend().appendFile(FILE_B).commit();

    table.newAppend().appendFile(FILE_B).commit();

    table.newAppend().appendFile(FILE_C).commit();

    table.newAppend().appendFile(FILE_D).commit();

    DeleteReachableFiles baseRemoveFilesSparkAction =
        sparkActions().deleteReachableFiles(metadataLocation(table)).io(table.io());
    DeleteReachableFiles.Result result = baseRemoveFilesSparkAction.execute();

    checkRemoveFilesResults(4, 0, 0, 5, 5, 8, result);
  }

  /** 测试移除文件动作场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRemoveFilesAction() {
    table.newAppend().appendFile(FILE_A).commit();

    table.newAppend().appendFile(FILE_B).commit();

    DeleteReachableFiles baseRemoveFilesSparkAction =
        sparkActions().deleteReachableFiles(metadataLocation(table)).io(table.io());
    checkRemoveFilesResults(2, 0, 0, 2, 2, 4, baseRemoveFilesSparkAction.execute());
  }

  /** 测试位置删除文件场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPositionDeleteFiles() {
    table.newAppend().appendFile(FILE_A).commit();

    table.newAppend().appendFile(FILE_B).commit();

    table.newRowDelta().addDeletes(FILE_A_POS_DELETES).commit();

    DeleteReachableFiles baseRemoveFilesSparkAction =
        sparkActions().deleteReachableFiles(metadataLocation(table)).io(table.io());
    checkRemoveFilesResults(2, 1, 0, 3, 3, 5, baseRemoveFilesSparkAction.execute());
  }

  /** 测试等值删除文件场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testEqualityDeleteFiles() {
    table.newAppend().appendFile(FILE_A).commit();

    table.newAppend().appendFile(FILE_B).commit();

    table.newRowDelta().addDeletes(FILE_A_EQ_DELETES).commit();

    DeleteReachableFiles baseRemoveFilesSparkAction =
        sparkActions().deleteReachableFiles(metadataLocation(table)).io(table.io());
    checkRemoveFilesResults(2, 0, 1, 3, 3, 5, baseRemoveFilesSparkAction.execute());
  }

  /** 测试移除文件动作带默认io场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRemoveFilesActionWithDefaultIO() {
    table.newAppend().appendFile(FILE_A).commit();

    table.newAppend().appendFile(FILE_B).commit();

    // IO not set explicitly on removeReachableFiles action
    // IO defaults to HadoopFileIO
    DeleteReachableFiles baseRemoveFilesSparkAction =
        sparkActions().deleteReachableFiles(metadataLocation(table));
    checkRemoveFilesResults(2, 0, 0, 2, 2, 4, baseRemoveFilesSparkAction.execute());
  }

  /** 测试uselocal迭代器场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUseLocalIterator() {
    table.newFastAppend().appendFile(FILE_A).commit();

    table.newOverwrite().deleteFile(FILE_A).addFile(FILE_B).commit();

    table.newFastAppend().appendFile(FILE_C).commit();

    int jobsBefore = spark.sparkContext().dagScheduler().nextJobId().get();

    withSQLConf(
        ImmutableMap.of("spark.sql.adaptive.enabled", "false"),
        () -> {
          DeleteReachableFiles.Result results =
              sparkActions()
                  .deleteReachableFiles(metadataLocation(table))
                  .io(table.io())
                  .option("stream-results", "true")
                  .execute();

          int jobsAfter = spark.sparkContext().dagScheduler().nextJobId().get();
          int totalJobsRun = jobsAfter - jobsBefore;

          checkRemoveFilesResults(3L, 0, 0, 4L, 3L, 5, results);

          Assert.assertEquals(
              "Expected total jobs to be equal to total number of shuffle partitions",
              totalJobsRun,
              SHUFFLE_PARTITIONS);
        });
  }

  /** 测试ignore元数据文件非found场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testIgnoreMetadataFilesNotFound() {
    table.updateProperties().set(TableProperties.METADATA_PREVIOUS_VERSIONS_MAX, "1").commit();

    table.newAppend().appendFile(FILE_A).commit();
    // There are three metadata json files at this point
    DeleteOrphanFiles.Result result =
        sparkActions().deleteOrphanFiles(table).olderThan(System.currentTimeMillis()).execute();

    Assert.assertEquals("Should delete 1 file", 1, Iterables.size(result.orphanFileLocations()));
    Assert.assertTrue(
        "Should remove v1 file",
        StreamSupport.stream(result.orphanFileLocations().spliterator(), false)
            .anyMatch(file -> file.contains("v1.metadata.json")));

    DeleteReachableFiles baseRemoveFilesSparkAction =
        sparkActions().deleteReachableFiles(metadataLocation(table)).io(table.io());
    DeleteReachableFiles.Result res = baseRemoveFilesSparkAction.execute();

    checkRemoveFilesResults(1, 0, 0, 1, 1, 4, res);
  }

  /** 测试空iothrowsexception场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testEmptyIOThrowsException() {
    DeleteReachableFiles baseRemoveFilesSparkAction =
        sparkActions().deleteReachableFiles(metadataLocation(table)).io(null);
    AssertHelpers.assertThrows(
        "FileIO can't be null in DeleteReachableFiles action",
        IllegalArgumentException.class,
        "File IO cannot be null",
        baseRemoveFilesSparkAction::execute);
  }

  /** 测试移除文件动作当garbagecollectiondisabled场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRemoveFilesActionWhenGarbageCollectionDisabled() {
    table.updateProperties().set(TableProperties.GC_ENABLED, "false").commit();

    AssertHelpers.assertThrows(
        "Should complain about removing files when GC is disabled",
        ValidationException.class,
        "Cannot delete files: GC is disabled (deleting files may corrupt other tables)",
        () -> sparkActions().deleteReachableFiles(metadataLocation(table)).execute());
  }

  /** 元数据路径。 */
  private String metadataLocation(Table tbl) {
    return ((HasTableOperations) tbl).operations().current().metadataFileLocation();
  }

  /** Spark动作。 */
  private ActionsProvider sparkActions() {
    return SparkActions.get();
  }
}
