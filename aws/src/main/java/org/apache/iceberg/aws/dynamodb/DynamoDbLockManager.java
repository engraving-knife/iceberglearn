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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.aws.AwsClientFactories;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.LockManagers;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.RequestLimitExceededException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.TransactionConflictException;

/**
 * 文件级说明：基于 Amazon DynamoDB 的分布式锁管理器。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 Iceberg {@link org.apache.iceberg.util.LockManagers.LockManager} SPI，为 catalog 提交
 *       提供“实体级”互斥锁，防止并发提交造成元数据冲突。
 *   <li>通过 DynamoDB 表的 PutItem 条件表达式实现加锁与释放，依赖 DynamoDB 强一致性读。
 *   <li>后台心跳线程定期续约锁的 leaseDuration，避免长事务期间锁被抢占。
 *   <li>首次使用时自动创建锁表（按需付费计费模式）并等待其 ACTIVE。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>选 DynamoDB 作为锁存储：提供低延迟、强一致、高可用且免运维的分布式协调能力， 适合多引擎并发访问同一 catalog 的场景。
 *   <li>乐观条件写：用 conditionExpression 保证“不存在才写入”或“版本匹配才覆盖”， 把并发冲突交给 DynamoDB 服务端裁决。
 *   <li>租约 + 心跳：leaseDurationMs 表示锁过期时间，持有者通过周期性心跳延长租约； 若持有者宕机，过期后其他竞争者可基于版本号抢占，避免死锁。
 *   <li>指数退避重试：针对限流、事务冲突等可重试异常，按 Tasks 框架做指数退避， 提升在限流场景下的成功率。
 * </ul>
 *
 * <p>上下游关系：由 catalog（如 GlueCatalog、HiveCatalog on AWS）在 commit 流程中通过 {@link LockManagers#get(Map)}
 * 加载；依赖 {@link AwsClientFactories} 构造 DynamoDB 客户端。
 */
public class DynamoDbLockManager extends LockManagers.BaseLockManager {

  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbLockManager.class);

  private static final String COL_LOCK_ENTITY_ID = "entityId";
  private static final String COL_LEASE_DURATION_MS = "leaseDurationMs";
  private static final String COL_VERSION = "version";
  private static final String COL_LOCK_OWNER_ID = "ownerId";

  /** 释放锁时使用的条件：实体 ID 与 owner ID 同时匹配。 */
  private static final String CONDITION_LOCK_ID_MATCH =
      String.format("%s = :eid AND %s = :oid", COL_LOCK_ENTITY_ID, COL_LOCK_OWNER_ID);
  /** 加锁时使用的条件：实体尚不存在（即未被任何竞争者持有）。 */
  private static final String CONDITION_LOCK_ENTITY_NOT_EXIST =
      String.format("attribute_not_exists(%s)", COL_LOCK_ENTITY_ID);
  /** 抢占过期锁时使用的条件：实体不存在，或实体 ID 与版本号同时匹配。 */
  private static final String CONDITION_LOCK_ENTITY_NOT_EXIST_OR_VERSION_MATCH =
      String.format(
          "attribute_not_exists(%s) OR (%s = :eid AND %s = :vid)",
          COL_LOCK_ENTITY_ID, COL_LOCK_ENTITY_ID, COL_VERSION);

  private static final int LOCK_TABLE_CREATION_WAIT_ATTEMPTS_MAX = 5;
  private static final int RELEASE_RETRY_ATTEMPTS_MAX = 5;

  private static final List<KeySchemaElement> LOCK_TABLE_SCHEMA =
      Lists.newArrayList(
          KeySchemaElement.builder()
              .attributeName(COL_LOCK_ENTITY_ID)
              .keyType(KeyType.HASH)
              .build());

  private static final List<AttributeDefinition> LOCK_TABLE_COL_DEFINITIONS =
      Lists.newArrayList(
          AttributeDefinition.builder()
              .attributeName(COL_LOCK_ENTITY_ID)
              .attributeType(ScalarAttributeType.S)
              .build());

  private final Map<String, DynamoDbHeartbeat> heartbeats = Maps.newHashMap();

  private DynamoDbClient dynamo;
  private String lockTableName;

  /** 供反射加载的无参构造器，构造后必须调用 {@link #initialize(Map)} 完成初始化。 */
  public DynamoDbLockManager() {}

  /**
   * 供测试使用的构造器，直接注入 DynamoDB 客户端与表名并确保锁表存在。
   *
   * @param dynamo dynamo client
   * @param lockTableName lock table name
   */
  public DynamoDbLockManager(DynamoDbClient dynamo, String lockTableName) {
    super.initialize(Maps.newHashMap());
    this.dynamo = dynamo;
    this.lockTableName = lockTableName;
    ensureLockTableExistsOrCreate();
  }

  /**
   * 确保锁表存在，不存在则按需付费模式创建并等待其进入 ACTIVE 状态。
   *
   * <p>逻辑：先 {@link #tableExists} 探测；不存在则调用 createTable，再用 Tasks 框架 最多重试 5 次轮询表状态直到 ACTIVE，否则抛
   * IllegalStateException。
   */
  private void ensureLockTableExistsOrCreate() {

    if (tableExists(lockTableName)) {
      return;
    }

    LOG.info("Dynamo lock table {} not found, trying to create", lockTableName);
    dynamo.createTable(
        CreateTableRequest.builder()
            .tableName(lockTableName)
            .keySchema(lockTableSchema())
            .attributeDefinitions(lockTableColDefinitions())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());

    Tasks.foreach(lockTableName)
        .retry(LOCK_TABLE_CREATION_WAIT_ATTEMPTS_MAX)
        .throwFailureWhenFinished()
        .onlyRetryOn(IllegalStateException.class)
        .run(this::checkTableActive);
  }

  /**
   * 通过 DescribeTable 探测表是否存在，捕获 ResourceNotFoundException 视为不存在。
   *
   * @param tableName 表名
   * @return 表存在返回 true
   */
  @VisibleForTesting
  boolean tableExists(String tableName) {
    try {
      dynamo.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
      return true;
    } catch (ResourceNotFoundException e) {
      return false;
    }
  }

  /**
   * 检查表是否处于 ACTIVE 状态，否则抛 IllegalStateException 触发外层重试。
   *
   * @param tableName 表名
   */
  private void checkTableActive(String tableName) {
    try {
      DescribeTableResponse response =
          dynamo.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
      TableStatus currentStatus = response.table().tableStatus();
      if (!currentStatus.equals(TableStatus.ACTIVE)) {
        throw new IllegalStateException(
            String.format(
                "Dynamo table %s is not active, current status: %s", tableName, currentStatus));
      }
    } catch (ResourceNotFoundException e) {
      throw new IllegalStateException(String.format("Cannot find Dynamo table %s", tableName));
    }
  }

  /**
   * 从 catalog properties 初始化锁管理器：构造 DynamoDB 客户端、读取锁表名并确保表存在。
   *
   * @param properties catalog properties
   */
  @Override
  public void initialize(Map<String, String> properties) {
    super.initialize(properties);
    this.dynamo = AwsClientFactories.from(properties).dynamo();
    this.lockTableName = properties.get(CatalogProperties.LOCK_TABLE);
    Preconditions.checkNotNull(lockTableName, "DynamoDB lock table name must not be null");
    ensureLockTableExistsOrCreate();
  }

  /**
   * 阻塞式获取指定实体的锁，使用指数退避无限重试直到成功或超时。
   *
   * <p>逻辑：以 acquireTimeoutMs 为总超时，按指数退避反复调用 {@link #acquireOnce}，
   * 仅对限流、事务冲突、条件检查失败、服务端错误等可重试异常重试。任何不可恢复异常 都会以返回 false 表示加锁失败。
   *
   * @param entityId 被锁实体（如表的 commit 标识）
   * @param ownerId 锁持有者标识
   * @return 加锁成功返回 true，失败返回 false
   */
  @Override
  public boolean acquire(String entityId, String ownerId) {
    try {
      Tasks.foreach(entityId)
          .throwFailureWhenFinished()
          .retry(Integer.MAX_VALUE - 1)
          .exponentialBackoff(acquireIntervalMs(), acquireIntervalMs(), acquireTimeoutMs(), 1)
          .onlyRetryOn(
              ConditionalCheckFailedException.class,
              ProvisionedThroughputExceededException.class,
              TransactionConflictException.class,
              RequestLimitExceededException.class,
              InternalServerErrorException.class)
          .run(id -> acquireOnce(id, ownerId));
      return true;
    } catch (DynamoDbException e) {
      return false;
    }
  }

  /**
   * 执行一次加锁尝试：表为空则直接写入新锁；否则等待租约过期后基于版本号条件覆盖。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>强一致读当前实体项。
   *   <li>无项：以“实体不存在”为条件写入新锁。
   *   <li>有项：sleep 当前 leaseDuration 等待过期，再以“不存在或版本匹配”为条件写入新锁。
   *   <li>成功后启动/重置心跳。
   * </ol>
   *
   * @param entityId 实体标识
   * @param ownerId 持有者标识
   */
  @VisibleForTesting
  void acquireOnce(String entityId, String ownerId) {
    GetItemResponse response =
        dynamo.getItem(
            GetItemRequest.builder()
                .tableName(lockTableName)
                .consistentRead(true)
                .key(toKey(entityId))
                .build());

    if (!response.hasItem()) {
      dynamo.putItem(
          PutItemRequest.builder()
              .tableName(lockTableName)
              .item(toNewItem(entityId, ownerId, heartbeatTimeoutMs()))
              .conditionExpression(CONDITION_LOCK_ENTITY_NOT_EXIST)
              .build());
    } else {
      Map<String, AttributeValue> currentItem = response.item();

      try {
        Thread.sleep(Long.parseLong(currentItem.get(COL_LEASE_DURATION_MS).n()));
      } catch (InterruptedException e) {
        throw new IllegalStateException(
            String.format(
                "Fail to acquire lock %s by %s, interrupted during sleep", entityId, ownerId),
            e);
      }

      dynamo.putItem(
          PutItemRequest.builder()
              .tableName(lockTableName)
              .item(toNewItem(entityId, ownerId, heartbeatTimeoutMs()))
              .conditionExpression(CONDITION_LOCK_ENTITY_NOT_EXIST_OR_VERSION_MATCH)
              .expressionAttributeValues(
                  ImmutableMap.of(
                      ":eid", AttributeValue.builder().s(entityId).build(),
                      ":vid", AttributeValue.builder().s(currentItem.get(COL_VERSION).s()).build()))
              .build());
    }

    startNewHeartbeat(entityId, ownerId);
  }

  /**
   * 为指定实体启动新的心跳任务，若已有旧心跳先取消再覆盖。
   *
   * @param entityId 实体标识
   * @param ownerId 持有者标识
   */
  private void startNewHeartbeat(String entityId, String ownerId) {
    if (heartbeats.containsKey(entityId)) {
      heartbeats.remove(entityId).cancel();
    }

    DynamoDbHeartbeat heartbeat =
        new DynamoDbHeartbeat(
            dynamo, lockTableName, heartbeatIntervalMs(), heartbeatTimeoutMs(), entityId, ownerId);
    heartbeat.schedule(scheduler());
    heartbeats.put(entityId, heartbeat);
  }

  /**
   * 释放指定实体的锁，要求 entity 与 owner 同时匹配。
   *
   * <p>逻辑：以“实体 ID 与 owner ID 同时匹配”为条件 DeleteItem，最多重试 5 次。 条件检查失败或 DynamoDB
   * 异常均记为错误日志，最终无论成功失败都尝试取消心跳。
   *
   * @param entityId 实体标识
   * @param ownerId 持有者标识
   * @return 释放成功返回 true
   */
  @Override
  public boolean release(String entityId, String ownerId) {
    boolean succeeded = false;
    DynamoDbHeartbeat heartbeat = heartbeats.get(entityId);
    try {
      Tasks.foreach(entityId)
          .retry(RELEASE_RETRY_ATTEMPTS_MAX)
          .throwFailureWhenFinished()
          .onlyRetryOn(
              ProvisionedThroughputExceededException.class,
              TransactionConflictException.class,
              RequestLimitExceededException.class,
              InternalServerErrorException.class)
          .run(
              id ->
                  dynamo.deleteItem(
                      DeleteItemRequest.builder()
                          .tableName(lockTableName)
                          .key(toKey(id))
                          .conditionExpression(CONDITION_LOCK_ID_MATCH)
                          .expressionAttributeValues(toLockIdValues(id, ownerId))
                          .build()));
      succeeded = true;
    } catch (ConditionalCheckFailedException e) {
      LOG.error(
          "Failed to release lock for entity: {}, owner: {}, lock entity does not exist or owner not match",
          entityId,
          ownerId,
          e);
    } catch (DynamoDbException e) {
      LOG.error(
          "Failed to release lock for entity: {}, owner: {}, encountered unexpected DynamoDB exception",
          entityId,
          ownerId,
          e);
    } finally {
      if (heartbeat != null && heartbeat.ownerId().equals(ownerId)) {
        heartbeat.cancel();
      }
    }

    return succeeded;
  }

  private static Map<String, AttributeValue> toKey(String entityId) {
    return ImmutableMap.of(COL_LOCK_ENTITY_ID, AttributeValue.builder().s(entityId).build());
  }

  private static Map<String, AttributeValue> toNewItem(
      String entityId, String ownerId, long heartbeatTimeoutMs) {
    return ImmutableMap.of(
        COL_LOCK_ENTITY_ID, AttributeValue.builder().s(entityId).build(),
        COL_LOCK_OWNER_ID, AttributeValue.builder().s(ownerId).build(),
        COL_VERSION, AttributeValue.builder().s(UUID.randomUUID().toString()).build(),
        COL_LEASE_DURATION_MS,
            AttributeValue.builder().n(Long.toString(heartbeatTimeoutMs)).build());
  }

  private static Map<String, AttributeValue> toLockIdValues(String entityId, String ownerId) {
    return ImmutableMap.of(
        ":eid", AttributeValue.builder().s(entityId).build(),
        ":oid", AttributeValue.builder().s(ownerId).build());
  }

  /** 关闭 DynamoDB 客户端并取消所有正在运行的心跳任务。 */
  @Override
  public void close() {
    dynamo.close();
    heartbeats.values().forEach(DynamoDbHeartbeat::cancel);
    heartbeats.clear();
  }

  /**
   * 返回锁表的 KeySchema，供用户自行建表时参考。
   *
   * @return lock table schema
   */
  public static List<KeySchemaElement> lockTableSchema() {
    return LOCK_TABLE_SCHEMA;
  }

  /**
   * 返回锁表的列定义，供用户自行建表时参考。
   *
   * @return lock table column definition
   */
  public static List<AttributeDefinition> lockTableColDefinitions() {
    return LOCK_TABLE_COL_DEFINITIONS;
  }

  /**
   * 锁的心跳任务：周期性地用条件 PutItem 续约租约。
   *
   * <p>设计意图：把续约逻辑独立为 Runnable，便于复用父类提供的调度器； 续约失败仅记日志不抛出，避免调度线程因单次失败而终止。
   */
  private static class DynamoDbHeartbeat implements Runnable {

    private final DynamoDbClient dynamo;
    private final String lockTableName;
    private final long intervalMs;
    private final long timeoutMs;
    private final String entityId;
    private final String ownerId;
    private ScheduledFuture<?> future;

    DynamoDbHeartbeat(
        DynamoDbClient dynamo,
        String lockTableName,
        long intervalMs,
        long timeoutMs,
        String entityId,
        String ownerId) {
      this.dynamo = dynamo;
      this.lockTableName = lockTableName;
      this.intervalMs = intervalMs;
      this.timeoutMs = timeoutMs;
      this.entityId = entityId;
      this.ownerId = ownerId;
      this.future = null;
    }

    /**
     * 执行一次心跳：以 owner 匹配为条件 PutItem 续约 leaseDuration 与 version。
     *
     * <p>条件检查失败说明锁已被他人抢占，记错误日志（可能存在不安全并发提交）。
     */
    @Override
    public void run() {
      try {
        dynamo.putItem(
            PutItemRequest.builder()
                .tableName(lockTableName)
                .item(toNewItem(entityId, ownerId, timeoutMs))
                .conditionExpression(CONDITION_LOCK_ID_MATCH)
                .expressionAttributeValues(toLockIdValues(entityId, ownerId))
                .build());
      } catch (ConditionalCheckFailedException e) {
        LOG.error(
            "Fail to heartbeat for entity: {}, owner: {} due to conditional check failure, "
                + "unsafe concurrent commits might be going on",
            entityId,
            ownerId,
            e);
      } catch (RuntimeException e) {
        LOG.error("Failed to heartbeat for entity: {}, owner: {}", entityId, ownerId, e);
      }
    }

    /** 返回本心跳对应的 owner 标识。 */
    public String ownerId() {
      return ownerId;
    }

    /**
     * 以固定速率注册到调度器，立即开始第一次执行。
     *
     * @param scheduler 调度器
     */
    public void schedule(ScheduledExecutorService scheduler) {
      future = scheduler.scheduleAtFixedRate(this, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** 取消调度（不中断正在执行的回合）。 */
    public void cancel() {
      if (future != null) {
        future.cancel(false);
      }
    }
  }
}
