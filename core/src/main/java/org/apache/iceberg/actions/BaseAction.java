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
package org.apache.iceberg.actions;

import org.apache.iceberg.MetadataTableType;
import org.apache.iceberg.Table;

/**
 * 表维护动作（Action）的抽象基类。
 *
 * <p>所属模块：iceberg-core 的 actions 包，为各类表维护动作（重写数据文件、过期快照、删除孤立文件等） 提供通用能力。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link Action} 接口，约束子类以构建器风格配置并执行动作。
 *   <li>提供元数据表（MetadataTable）标识符的构造能力，屏蔽不同 Catalog（路径式 / HadoopCatalog / HiveCatalog）下元数据表引用方式的差异。
 * </ul>
 *
 * <p>设计意图：把与具体动作无关的"表引用"和"元数据表名解析"逻辑上提至基类，子类只需关注各自动作的 参数与执行逻辑。泛型 {@code ThisT}
 * 用于构建器方法的链式返回类型，{@code R} 为执行结果类型。
 *
 * <p>上下游关系：实现 {@link Action}（iceberg-api），被 {@link BaseSnapshotUpdateAction} 及各具体 动作（如 {@link
 * BaseExpireSnapshots}、{@link BaseRewriteDataFiles} 等）继承。
 */
abstract class BaseAction<ThisT, R> implements Action<ThisT, R> {

  /**
   * 返回当前动作所操作的目标表。
   *
   * <p>子类负责提供具体的表实例，供基类方法（如元数据表名构造）使用。
   *
   * @return 目标 {@link Table}
   */
  protected abstract Table table();

  /**
   * 基于当前表名构造元数据表的标识符。
   *
   * @param type 元数据表类型（如 ALL_MANIFESTS、ENTRIES 等）
   * @return 元数据表标识字符串
   */
  protected String metadataTableName(MetadataTableType type) {
    return metadataTableName(table().name(), type);
  }

  /**
   * 根据表名及其所属 Catalog 类型，构造元数据表的标识符。
   *
   * <p>逻辑：不同 Catalog（路径式、HadoopCatalog、HiveCatalog）对元数据表的引用方式不同， 此方法按表名前缀/特征分派：
   *
   * <ul>
   *   <li>表名含 "/" 视为路径式，用 "#" 拼接类型；
   *   <li>"hadoop." 前缀的 HadoopCatalog 表，改用表 location 拼接（因按名加载时会走 HiveCatalog）；
   *   <li>"hive." 前缀的 HiveCatalog 表，去掉逻辑名前缀后用 "." 拼接（兼容 Spark 2.4）；
   *   <li>其余情况用 "." 拼接类型。
   * </ul>
   *
   * @param tableName 表名或标识符
   * @param type 元数据表类型
   * @return 元数据表标识字符串
   */
  protected String metadataTableName(String tableName, MetadataTableType type) {
    if (tableName.contains("/")) {
      return tableName + "#" + type;
    } else if (tableName.startsWith("hadoop.")) {
      // for HadoopCatalog tables, use the table location to load the metadata table
      // because IcebergCatalog uses HiveCatalog when the table is identified by name
      return table().location() + "#" + type;
    } else if (tableName.startsWith("hive.")) {
      // HiveCatalog prepend a logical name which we need to drop for Spark 2.4
      return tableName.replaceFirst("hive\\.", "") + "." + type;
    } else {
      return tableName + "." + type;
    }
  }
}
