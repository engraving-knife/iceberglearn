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

import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 创建与加载表实现的通用接口。
 *
 * <p>所属模块：iceberg-api（表入口抽象层）。
 *
 * <p>职责：定义一套按标识符创建/加载/判断表是否存在的统一 API，由具体 catalog 实现 （如 HadoopCatalog、JdbcCatalog、HiveCatalog
 * 等）提供底层存储绑定。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>{@code tableIdentifier} 由底层实现解释（如 database.table_name）。
 *   <li>create 提供多个重载默认方法，按"未指定则用默认值"的策略逐层委托，最终落到 含 SortOrder 的完整版本（默认抛
 *       UnsupportedOperationException，由具体实现覆盖）。
 * </ul>
 *
 * <p>上下游关系：被 catalog 模块实现；被上层应用/引擎用于获取 {@link Table} 实例。
 */
public interface Tables {
  /** 用未分区、默认属性创建表。 */
  default Table create(Schema schema, String tableIdentifier) {
    return create(schema, PartitionSpec.unpartitioned(), ImmutableMap.of(), tableIdentifier);
  }

  /** 用指定分区 spec、默认属性创建表。 */
  default Table create(Schema schema, PartitionSpec spec, String tableIdentifier) {
    return create(schema, spec, ImmutableMap.of(), tableIdentifier);
  }

  /** 用指定分区 spec 与属性、未排序创建表。 */
  default Table create(
      Schema schema, PartitionSpec spec, Map<String, String> properties, String tableIdentifier) {
    return create(schema, spec, SortOrder.unsorted(), properties, tableIdentifier);
  }

  /**
   * 用指定 schema、分区 spec、sort order、属性创建表。
   *
   * <p>默认实现：抛 {@link UnsupportedOperationException}，由具体实现覆盖。
   *
   * @param schema 表 schema
   * @param spec 分区 spec
   * @param order sort order
   * @param properties 表属性
   * @param tableIdentifier 表标识符
   * @return 新创建的 {@link Table}
   */
  default Table create(
      Schema schema,
      PartitionSpec spec,
      SortOrder order,
      Map<String, String> properties,
      String tableIdentifier) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement create with a sort order");
  }

  /**
   * 按标识符加载已存在的表。
   *
   * @param tableIdentifier 表标识符
   * @return 加载到的 {@link Table}
   */
  Table load(String tableIdentifier);

  /**
   * 判断指定标识符的表是否存在。
   *
   * @param tableIdentifier 表标识符
   * @return 表存在返回 true
   */
  boolean exists(String tableIdentifier);
}
