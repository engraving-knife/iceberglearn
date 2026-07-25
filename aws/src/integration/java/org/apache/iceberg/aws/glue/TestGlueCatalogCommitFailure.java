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
package org.apache.iceberg.aws.glue;

import java.io.File;
import java.util.Map;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.aws.s3.S3TestUtil;
import org.apache.iceberg.aws.util.RetryDetector;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.ForbiddenException;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.metrics.MetricCollector;
import software.amazon.awssdk.services.glue.model.AccessDeniedException;
import software.amazon.awssdk.services.glue.model.ConcurrentModificationException;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GlueException;
import software.amazon.awssdk.services.glue.model.ValidationException;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 文件级说明：TestGlueCatalogCommitFailure 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 Glue目录提交failure 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class TestGlueCatalogCommitFailure extends GlueTestBase {

  /**
   * 测试场景：failed提交。
   *
   * <p>验证该方法在 failed提交 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testFailedCommit() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps, new CommitFailedException("Datacenter on fire"));

    AssertHelpers.assertThrows(
        "Commit failed exception should directly throw",
        CommitFailedException.class,
        "Datacenter on fire",
        () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals("No new metadata files should exist", 2, metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：failed提交throwsunknownexception。
   *
   * <p>验证该方法在 failed提交throwsunknownexception 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testFailedCommitThrowsUnknownException() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps);

    AssertHelpers.assertThrows(
        "Should throw CommitStateUnknownException since exception is unexpected",
        CommitStateUnknownException.class,
        "Datacenter on fire",
        () -> spyOps.commit(metadataV2, metadataV1));
    Mockito.verify(spyOps, Mockito.times(1)).refresh();

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals(
        "Client could not determine outcome so new metadata file should also exist",
        3,
        metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：并发modificationexceptiondoes非检查提交status。
   *
   * <p>验证该方法在 并发modificationexceptiondoes非检查提交status 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testConcurrentModificationExceptionDoesNotCheckCommitStatus() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps, ConcurrentModificationException.builder().build());

    AssertHelpers.assertThrowsWithCause(
        "GlueCatalog should fail on concurrent modifications",
        CommitFailedException.class,
        "Glue detected concurrent update",
        ConcurrentModificationException.class,
        null,
        () -> spyOps.commit(metadataV2, metadataV1));
    Mockito.verify(spyOps, Mockito.times(0)).refresh();

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals("No new metadata files should exist", 2, metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：检查提交status后retries。
   *
   * <p>验证该方法在 检查提交status后retries 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCheckCommitStatusAfterRetries() {
    String namespace = createNamespace();
    String tableName = createTable(namespace);
    TableIdentifier tableId = TableIdentifier.of(namespace, tableName);

    GlueTableOperations spyOps =
        Mockito.spy((GlueTableOperations) glueCatalog.newTableOps(tableId));
    GlueCatalog spyCatalog = Mockito.spy(glueCatalog);
    Mockito.doReturn(spyOps).when(spyCatalog).newTableOps(Mockito.eq(tableId));
    Table table = spyCatalog.loadTable(tableId);

    TableMetadata metadataV1 = spyOps.current();
    simulateRetriedCommit(spyOps, true /* report retry */);
    updateTable(table, spyOps);

    Assert.assertNotEquals("Current metadata should have changed", metadataV1, spyOps.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(spyOps.current()));
    Assert.assertEquals(
        "No new metadata files should exist", 2, metadataFileCount(spyOps.current()));
  }

  /**
   * 测试场景：noretryawarenesscorrupts表。
   *
   * <p>验证该方法在 noretryawarenesscorrupts表 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNoRetryAwarenessCorruptsTable() {
    // This test exists to replicate the issue the prior test validates the fix for
    // See https://github.com/apache/iceberg/issues/7151
    String namespace = createNamespace();
    String tableName = createTable(namespace);
    TableIdentifier tableId = TableIdentifier.of(namespace, tableName);

    GlueTableOperations spyOps =
        Mockito.spy((GlueTableOperations) glueCatalog.newTableOps(tableId));
    GlueCatalog spyCatalog = Mockito.spy(glueCatalog);
    Mockito.doReturn(spyOps).when(spyCatalog).newTableOps(Mockito.eq(tableId));
    Table table = spyCatalog.loadTable(tableId);

    // Its possible that Glue or DynamoDB might someday make changes that render the retry detection
    // mechanism unnecessary. At that time, this test would start failing while the prior one would
    // still work. If or when that happens, we can re-evaluate whether the mechanism is still
    // necessary.
    simulateRetriedCommit(spyOps, false /* hide retry */);
    Assertions.assertThatThrownBy(() -> updateTable(table, spyOps))
        .as("Hidden retry causes writer to conflict with itself")
        .isInstanceOf(CommitFailedException.class)
        .hasMessageContaining("Glue detected concurrent update")
        .cause()
        .isInstanceOf(ConcurrentModificationException.class);

    Assertions.assertThatThrownBy(() -> glueCatalog.loadTable(tableId))
        .as("Table still accessible despite hidden retry, underlying assumptions may have changed")
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Location does not exist");
  }

  /** 辅助方法：simulateretried提交。 */
  private void simulateRetriedCommit(GlueTableOperations spyOps, boolean reportRetry) {
    // Perform a successful commit, then call it again, optionally letting the retryDetector know
    // about it
    Mockito.doAnswer(
            i -> {
              final MetricCollector metrics = MetricCollector.create("test");
              metrics.reportMetric(CoreMetric.RETRY_COUNT, reportRetry ? 1 : 0);

              i.callRealMethod();
              i.getArgument(3, RetryDetector.class).publish(metrics.collect());
              i.callRealMethod();
              return null;
            })
        .when(spyOps)
        .persistGlueTable(Mockito.any(), Mockito.anyMap(), Mockito.any(), Mockito.any());
  }

  /**
   * 测试场景：提交throwsexceptionwhilesucceeded。
   *
   * <p>验证该方法在 提交throwsexceptionwhilesucceeded 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCommitThrowsExceptionWhileSucceeded() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);

    // Simulate a communication error after a successful commit
    commitAndThrowException(ops, spyOps);

    // Shouldn't throw because the commit actually succeeds even though persistTable throws an
    // exception
    spyOps.commit(metadataV2, metadataV1);

    ops.refresh();
    Assert.assertNotEquals("Current metadata should have changed", metadataV2, ops.current());
    Assert.assertTrue(
        "Current metadata file should still exist", metadataFileExists(ops.current()));
    Assert.assertEquals(
        "Commit should have been successful and new metadata file should be made",
        3,
        metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：failed提交throwsunknownexception当status检查fails。
   *
   * <p>验证该方法在 failed提交throwsunknownexception当status检查fails 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testFailedCommitThrowsUnknownExceptionWhenStatusCheckFails() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps);
    breakFallbackCatalogCommitCheck(spyOps);

    AssertHelpers.assertThrows(
        "Should throw CommitStateUnknownException since the catalog check was blocked",
        CommitStateUnknownException.class,
        "Datacenter on fire",
        () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();

    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue(
        "Current metadata file should still exist", metadataFileExists(ops.current()));
    Assert.assertEquals(
        "Client could not determine outcome so new metadata file should also exist",
        3,
        metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：succeeded提交throwsunknownexception。
   *
   * <p>验证该方法在 succeeded提交throwsunknownexception 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testSucceededCommitThrowsUnknownException() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    commitAndThrowException(ops, spyOps);
    breakFallbackCatalogCommitCheck(spyOps);

    AssertHelpers.assertThrows(
        "Should throw CommitStateUnknownException since the catalog check was blocked",
        CommitStateUnknownException.class,
        "Datacenter on fire",
        () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();

    Assert.assertNotEquals("Current metadata should have changed", ops.current(), metadataV2);
    Assert.assertTrue(
        "Current metadata file should still exist", metadataFileExists(ops.current()));
  }

  /**
   * 测试场景：exceptionthrownin并发提交。
   *
   * <p>验证该方法在 exceptionthrownin并发提交 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testExceptionThrownInConcurrentCommit() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    concurrentCommitAndThrowException(ops, spyOps, table);

    /*
    This commit and our concurrent commit should succeed even though this commit throws an exception
    after the persist operation succeeds
     */
    spyOps.commit(metadataV2, metadataV1);

    ops.refresh();
    Assert.assertNotEquals("Current metadata should have changed", metadataV2, ops.current());
    Assert.assertTrue(
        "Current metadata file should still exist", metadataFileExists(ops.current()));
    Assert.assertEquals(
        "The column addition from the concurrent commit should have been successful",
        2,
        ops.current().schema().columns().size());
  }

  /** 辅助方法：并发提交andthrowexception。 */
  private void concurrentCommitAndThrowException(
      GlueTableOperations realOps, GlueTableOperations spyOperations, Table table) {
    // Simulate a communication error after a successful commit
    Mockito.doAnswer(
            i -> {
              Map<String, String> mapProperties = i.getArgument(1, Map.class);
              realOps.persistGlueTable(
                  i.getArgument(0, software.amazon.awssdk.services.glue.model.Table.class),
                  mapProperties,
                  i.getArgument(2, TableMetadata.class),
                  i.getArgument(3, RetryDetector.class));

              // new metadata location is stored in map property, and used for locking
              String newMetadataLocation =
                  mapProperties.get(BaseMetastoreTableOperations.METADATA_LOCATION_PROP);

              // Simulate lock expiration or removal, use commit status null to avoid deleting data
              realOps.cleanupMetadataAndUnlock(null, newMetadataLocation);

              table.refresh();
              table.updateSchema().addColumn("newCol", Types.IntegerType.get()).commit();
              throw new RuntimeException("Datacenter on fire");
            })
        .when(spyOperations)
        .persistGlueTable(Mockito.any(), Mockito.anyMap(), Mockito.any(), Mockito.any());
  }

  /**
   * 测试场景：创建表带invaliddb。
   *
   * <p>验证该方法在 创建表带invaliddb 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateTableWithInvalidDB() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps, EntityNotFoundException.builder().build());

    AssertHelpers.assertThrows(
        "Should throw not found exception",
        NotFoundException.class,
        "because Glue cannot find the requested entity",
        () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals("No new metadata files should exist", 2, metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：Glueaccessdeniedexception。
   *
   * <p>验证该方法在 Glueaccessdeniedexception 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testGlueAccessDeniedException() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps, AccessDeniedException.builder().build());

    AssertHelpers.assertThrows(
        "Should throw forbidden exception",
        ForbiddenException.class,
        "because Glue cannot access the requested resources",
        () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals("No new metadata files should exist", 2, metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：Glue校验exception。
   *
   * <p>验证该方法在 Glue校验exception 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testGlueValidationException() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps, ValidationException.builder().build());

    AssertHelpers.assertThrows(
        "Should throw validation exception",
        org.apache.iceberg.exceptions.ValidationException.class,
        "because Glue encountered a validation exception while accessing requested resources",
        () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals("No new metadata files should exist", 2, metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：测试s3exception。
   *
   * <p>验证该方法在对应输入下的行为与断言结果是否符合预期。
   */
  @Test
  public void testS3Exception() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps, S3Exception.builder().statusCode(300).build());

    AssertHelpers.assertThrows(
        null, S3Exception.class, () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals("No new metadata files should exist", 2, metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：otherGlueexception。
   *
   * <p>验证该方法在 otherGlueexception 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testOtherGlueException() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps, GlueException.builder().statusCode(300).build());

    AssertHelpers.assertThrows(
        null, GlueException.class, () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals("No new metadata files should exist", 2, metadataFileCount(ops.current()));
  }

  /**
   * 测试场景：internalservererrorretry提交。
   *
   * <p>验证该方法在 internalservererrorretry提交 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testInternalServerErrorRetryCommit() {
    Table table = setupTable();
    GlueTableOperations ops = (GlueTableOperations) ((HasTableOperations) table).operations();

    TableMetadata metadataV1 = ops.current();
    TableMetadata metadataV2 = updateTable(table, ops);

    GlueTableOperations spyOps = Mockito.spy(ops);
    failCommitAndThrowException(spyOps, GlueException.builder().statusCode(500).build());

    AssertHelpers.assertThrows(
        null, CommitFailedException.class, () -> spyOps.commit(metadataV2, metadataV1));

    ops.refresh();
    Assert.assertEquals("Current metadata should not have changed", metadataV2, ops.current());
    Assert.assertTrue("Current metadata should still exist", metadataFileExists(metadataV2));
    Assert.assertEquals("No new metadata files should exist", 2, metadataFileCount(ops.current()));
  }

  /** 辅助方法：初始化表。 */
  private Table setupTable() {
    String namespace = createNamespace();
    String tableName = createTable(namespace);
    return glueCatalog.loadTable(TableIdentifier.of(namespace, tableName));
  }

  /** 辅助方法：更新表。 */
  private TableMetadata updateTable(Table table, GlueTableOperations ops) {
    table.updateSchema().addColumn("n", Types.IntegerType.get()).commit();

    ops.refresh();

    TableMetadata metadataV2 = ops.current();

    Assert.assertEquals(2, metadataV2.schema().columns().size());
    return metadataV2;
  }

  /** 辅助方法：提交andthrowexception。 */
  private void commitAndThrowException(GlueTableOperations realOps, GlueTableOperations spyOps) {
    Mockito.doAnswer(
            i -> {
              realOps.persistGlueTable(
                  i.getArgument(0, software.amazon.awssdk.services.glue.model.Table.class),
                  i.getArgument(1, Map.class),
                  i.getArgument(2, TableMetadata.class),
                  i.getArgument(3, RetryDetector.class));
              throw new RuntimeException("Datacenter on fire");
            })
        .when(spyOps)
        .persistGlueTable(Mockito.any(), Mockito.anyMap(), Mockito.any(), Mockito.any());
  }

  /** 辅助方法：fail提交andthrowexception。 */
  private void failCommitAndThrowException(GlueTableOperations spyOps) {
    failCommitAndThrowException(spyOps, new RuntimeException("Datacenter on fire"));
  }

  /** 辅助方法：fail提交andthrowexception。 */
  private void failCommitAndThrowException(GlueTableOperations spyOps, Exception exceptionToThrow) {
    Mockito.doThrow(exceptionToThrow)
        .when(spyOps)
        .persistGlueTable(Mockito.any(), Mockito.anyMap(), Mockito.any(), Mockito.any());
  }

  /** 辅助方法：breakfallback目录提交检查。 */
  private void breakFallbackCatalogCommitCheck(GlueTableOperations spyOperations) {
    Mockito.when(spyOperations.refresh())
        .thenThrow(new RuntimeException("Still on fire")); // Failure on commit check
  }

  /** 辅助方法：元数据文件存在。 */
  private boolean metadataFileExists(TableMetadata metadata) {
    try {
      s3.headObject(
          HeadObjectRequest.builder()
              .bucket(S3TestUtil.getBucketFromUri(metadata.metadataFileLocation()))
              .key(S3TestUtil.getKeyFromUri(metadata.metadataFileLocation()))
              .build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  /** 辅助方法：元数据文件计数。 */
  private int metadataFileCount(TableMetadata metadata) {
    return (int)
        s3
            .listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(S3TestUtil.getBucketFromUri(metadata.metadataFileLocation()))
                    .prefix(
                        new File(S3TestUtil.getKeyFromUri(metadata.metadataFileLocation()))
                            .getParent())
                    .build())
            .contents().stream()
            .filter(s3Object -> s3Object.key().endsWith("metadata.json"))
            .count();
  }
}
