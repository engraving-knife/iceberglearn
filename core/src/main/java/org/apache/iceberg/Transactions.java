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

import org.apache.iceberg.BaseTransaction.TransactionType;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 事务工厂：创建各种类型的事务（{@link Transaction}）实例。
 *
 * <p>所属模块：iceberg-core（事务入口层）。
 *
 * <p>职责：提供创建不同事务类型的静态工厂方法——创建表、替换表、创建或替换表、普通事务。
 *
 * <p>设计意图：把事务构造细节封装在工厂方法中，调用方只需指定类型即可获得正确配置的 {@link BaseTransaction}，避免直接暴露构造器。
 *
 * <p>上下游关系：被 catalog/表操作类调用创建事务；底层委托给 {@link BaseTransaction}。
 */
public final class Transactions {
  private Transactions() {}

  /**
   * 创建一个"创建或替换表"事务。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   * @param start 起始元数据
   * @return 事务实例
   */
  public static Transaction createOrReplaceTableTransaction(
      String tableName, TableOperations ops, TableMetadata start) {
    return new BaseTransaction(tableName, ops, TransactionType.CREATE_OR_REPLACE_TABLE, start);
  }

  /**
   * 创建一个"替换表"事务。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   * @param start 起始元数据
   * @return 事务实例
   */
  public static Transaction replaceTableTransaction(
      String tableName, TableOperations ops, TableMetadata start) {
    return new BaseTransaction(tableName, ops, TransactionType.REPLACE_TABLE, start);
  }

  /**
   * 创建一个"替换表"事务，并指定 metrics reporter。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   * @param start 起始元数据
   * @param reporter 指标上报器
   * @return 事务实例
   */
  public static Transaction replaceTableTransaction(
      String tableName, TableOperations ops, TableMetadata start, MetricsReporter reporter) {
    return new BaseTransaction(tableName, ops, TransactionType.REPLACE_TABLE, start, reporter);
  }

  /**
   * 创建一个"创建表"事务；若表已存在则抛异常。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   * @param start 起始元数据
   * @return 事务实例
   * @throws IllegalArgumentException 表已存在时
   */
  public static Transaction createTableTransaction(
      String tableName, TableOperations ops, TableMetadata start) {
    Preconditions.checkArgument(
        ops.current() == null, "Cannot start create table transaction: table already exists");
    return new BaseTransaction(tableName, ops, TransactionType.CREATE_TABLE, start);
  }

  /**
   * 创建一个"创建表"事务，并指定 metrics reporter；若表已存在则抛异常。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   * @param start 起始元数据
   * @param reporter 指标上报器
   * @return 事务实例
   * @throws IllegalArgumentException 表已存在时
   */
  public static Transaction createTableTransaction(
      String tableName, TableOperations ops, TableMetadata start, MetricsReporter reporter) {
    Preconditions.checkArgument(
        ops.current() == null, "Cannot start create table transaction: table already exists");
    return new BaseTransaction(tableName, ops, TransactionType.CREATE_TABLE, start, reporter);
  }

  /**
   * 创建一个普通事务：基于当前表元数据，支持多次更新后一次性提交。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   * @return 事务实例
   */
  public static Transaction newTransaction(String tableName, TableOperations ops) {
    return new BaseTransaction(tableName, ops, TransactionType.SIMPLE, ops.refresh());
  }

  /**
   * 创建一个普通事务，并指定 metrics reporter。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   * @param reporter 指标上报器
   * @return 事务实例
   */
  public static Transaction newTransaction(
      String tableName, TableOperations ops, MetricsReporter reporter) {
    return new BaseTransaction(tableName, ops, TransactionType.SIMPLE, ops.refresh(), reporter);
  }
}
