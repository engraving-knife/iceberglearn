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
package org.apache.iceberg.aws.dynamodb;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.aws.AwsClientFactories;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

/**
 * 文件级说明：TestDynamoDbLockManager 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 dynamodb锁manager 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class TestDynamoDbLockManager {

  private static final ForkJoinPool POOL = new ForkJoinPool(16);

  private static String lockTableName;
  private static DynamoDbClient dynamo;

  private DynamoDbLockManager lockManager;
  private String entityId;
  private String ownerId;

  /** 初始化：beforeClass，在测试类加载时准备共享的测试环境与数据。 */
  @BeforeClass
  public static void beforeClass() {
    lockTableName = genTableName();
    dynamo = AwsClientFactories.defaultFactory().dynamo();
  }

  /** 初始化：before，在每个测试方法执行前准备测试环境与数据。 */
  @Before
  public void before() {
    lockManager = new DynamoDbLockManager(dynamo, lockTableName);
    entityId = UUID.randomUUID().toString();
    ownerId = UUID.randomUUID().toString();
  }

  /** 清理：afterClass，在所有测试方法执行完毕后释放共享资源。 */
  @AfterClass
  public static void afterClass() {
    dynamo.deleteTable(DeleteTableRequest.builder().tableName(lockTableName).build());
  }

  /**
   * 测试场景：表creation。
   *
   * <p>验证该方法在 表creation 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testTableCreation() {
    Assert.assertTrue(lockManager.tableExists(lockTableName));
  }

  /**
   * 测试场景：acquireonce单个process。
   *
   * <p>验证该方法在 acquireonce单个process 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testAcquireOnceSingleProcess() {
    lockManager.acquireOnce(entityId, ownerId);
    Map<String, AttributeValue> key = Maps.newHashMap();
    key.put("entityId", AttributeValue.builder().s(entityId).build());
    GetItemResponse response =
        dynamo.getItem(GetItemRequest.builder().tableName(lockTableName).key(key).build());
    Assert.assertTrue("should have item in dynamo after acquire", response.hasItem());
    Assert.assertEquals(entityId, response.item().get("entityId").s());
    Assert.assertEquals(ownerId, response.item().get("ownerId").s());
    Assert.assertNotNull(response.item().get("version"));
    Assert.assertNotNull(response.item().get("leaseDurationMs"));
  }

  /**
   * 测试场景：acquireonce多processes。
   *
   * <p>验证该方法在 acquireonce多processes 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testAcquireOnceMultiProcesses() throws Exception {
    List<Boolean> results =
        POOL.submit(
                () ->
                    IntStream.range(0, 16)
                        .parallel()
                        .mapToObj(
                            i -> {
                              try {
                                DynamoDbLockManager threadLocalLockManager =
                                    new DynamoDbLockManager(dynamo, lockTableName);
                                threadLocalLockManager.acquireOnce(
                                    entityId, UUID.randomUUID().toString());
                                return true;
                              } catch (ConditionalCheckFailedException e) {
                                return false;
                              }
                            })
                        .collect(Collectors.toList()))
            .get();
    Assert.assertEquals(
        "should have only 1 process succeeded in acquisition",
        1,
        results.stream().filter(s -> s).count());
  }

  /**
   * 测试场景：测试releaseandacquire。
   *
   * <p>验证该方法在对应输入下的行为与断言结果是否符合预期。
   */
  @Test
  public void testReleaseAndAcquire() {
    Assert.assertTrue(lockManager.acquire(entityId, ownerId));
    Assert.assertTrue(lockManager.release(entityId, ownerId));
    Assert.assertTrue(lockManager.acquire(entityId, ownerId));
  }

  /**
   * 测试场景：release带wrongowner。
   *
   * <p>验证该方法在 release带wrongowner 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testReleaseWithWrongOwner() {
    Assert.assertTrue(lockManager.acquire(entityId, ownerId));
    Assert.assertFalse(lockManager.release(entityId, UUID.randomUUID().toString()));
  }

  /**
   * 测试场景：acquire单个process。
   *
   * <p>验证该方法在 acquire单个process 条件下的行为与断言结果是否符合预期。
   */
  @Test
  @SuppressWarnings({"DangerousCompletableFutureUsage", "FutureReturnValueIgnored"})
  public void testAcquireSingleProcess() throws Exception {
    Assert.assertTrue(lockManager.acquire(entityId, ownerId));
    String oldOwner = ownerId;

    CompletableFuture.supplyAsync(
        () -> {
          try {
            Thread.sleep(5000);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          Assert.assertTrue(lockManager.release(entityId, oldOwner));
          return null;
        });

    ownerId = UUID.randomUUID().toString();
    long start = System.currentTimeMillis();
    Assert.assertTrue(lockManager.acquire(entityId, ownerId));
    Assert.assertTrue("should succeed after 5 seconds", System.currentTimeMillis() - start >= 5000);
  }

  /**
   * 测试场景：acquire多process所有succeed。
   *
   * <p>验证该方法在 acquire多process所有succeed 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testAcquireMultiProcessAllSucceed() throws Exception {
    lockManager.initialize(
        ImmutableMap.of(
            CatalogProperties.LOCK_ACQUIRE_INTERVAL_MS, "500",
            CatalogProperties.LOCK_ACQUIRE_TIMEOUT_MS, "100000000",
            CatalogProperties.LOCK_TABLE, lockTableName));
    long start = System.currentTimeMillis();
    List<Boolean> results =
        POOL.submit(
                () ->
                    IntStream.range(0, 16)
                        .parallel()
                        .mapToObj(
                            i -> {
                              DynamoDbLockManager threadLocalLockManager =
                                  new DynamoDbLockManager(dynamo, lockTableName);
                              String owner = UUID.randomUUID().toString();
                              boolean succeeded = threadLocalLockManager.acquire(entityId, owner);
                              if (succeeded) {
                                try {
                                  Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                  throw new RuntimeException(e);
                                }
                                Assert.assertTrue(threadLocalLockManager.release(entityId, owner));
                              }
                              return succeeded;
                            })
                        .collect(Collectors.toList()))
            .get();
    Assert.assertEquals(
        "all lock acquire should succeed sequentially",
        16,
        results.stream().filter(s -> s).count());
    Assert.assertTrue(
        "must take more than 16 seconds", System.currentTimeMillis() - start >= 16000);
  }

  /**
   * 测试场景：acquire多processonlyonesucceed。
   *
   * <p>验证该方法在 acquire多processonlyonesucceed 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testAcquireMultiProcessOnlyOneSucceed() throws Exception {
    lockManager.initialize(
        ImmutableMap.of(
            CatalogProperties.LOCK_ACQUIRE_TIMEOUT_MS,
            "10000",
            CatalogProperties.LOCK_TABLE,
            lockTableName));

    List<Boolean> results =
        POOL.submit(
                () ->
                    IntStream.range(0, 16)
                        .parallel()
                        .mapToObj(
                            i -> {
                              DynamoDbLockManager threadLocalLockManager =
                                  new DynamoDbLockManager(dynamo, lockTableName);
                              return threadLocalLockManager.acquire(entityId, ownerId);
                            })
                        .collect(Collectors.toList()))
            .get();
    Assert.assertEquals(
        "only 1 thread should have acquired the lock", 1, results.stream().filter(s -> s).count());
  }

  /**
   * 测试场景：表creationfailure。
   *
   * <p>验证该方法在 表creationfailure 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testTableCreationFailure() {
    DynamoDbClient dynamo2 = Mockito.mock(DynamoDbClient.class);
    Mockito.doThrow(ResourceNotFoundException.class)
        .when(dynamo2)
        .describeTable(Mockito.any(DescribeTableRequest.class));
    AssertHelpers.assertThrows(
        "should fail to initialize the lock manager",
        IllegalStateException.class,
        "Cannot find Dynamo table",
        () -> new DynamoDbLockManager(dynamo2, lockTableName));
  }

  /** 辅助方法：gen表name。 */
  private static String genTableName() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
