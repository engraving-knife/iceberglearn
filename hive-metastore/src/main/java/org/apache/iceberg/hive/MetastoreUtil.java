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
package org.apache.iceberg.hive;

import java.util.Map;
import org.apache.hadoop.hive.common.StatsSetupConst;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.EnvironmentContext;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * Hive Metastore 操作工具类。
 *
 * <p>所属模块：iceberg-hive-metastore（Hive Metastore 调用辅助层）。
 *
 * <p>职责：封装对 HMS {@code alter_table} 接口的跨版本兼容调用，并在调用时注入环境上下文 以关闭 HMS 的统计信息自动更新，避免递归文件列举。
 *
 * <p>设计意图：不同 Hive 版本的 {@code alter_table} 方法签名不同（有的支持 {@link EnvironmentContext}，有的不支持）。通过 {@link
 * DynMethods} 按优先级依次尝试 {@code alter_table_with_environmentContext}、带 EnvironmentContext 的
 * alter_table、 不带 EnvironmentContext 的 alter_table，命中第一个可用实现，屏蔽版本差异。
 *
 * <p>上下游关系：被 {@link HiveCatalog#renameTable} 和 {@link HiveTableOperations} 在 修改 HMS 表元数据时调用。
 */
public class MetastoreUtil {
  private static final DynMethods.UnboundMethod ALTER_TABLE =
      DynMethods.builder("alter_table")
          .impl(
              IMetaStoreClient.class,
              "alter_table_with_environmentContext",
              String.class,
              String.class,
              Table.class,
              EnvironmentContext.class)
          .impl(
              IMetaStoreClient.class,
              "alter_table",
              String.class,
              String.class,
              Table.class,
              EnvironmentContext.class)
          .impl(IMetaStoreClient.class, "alter_table", String.class, String.class, Table.class)
          .build();

  private MetastoreUtil() {}

  /**
   * 调用 HMS alter_table 修改表定义（无额外环境变量）。
   *
   * <p>委托给带 extraEnv 的重载方法，传入空 Map。
   *
   * @param client HMS 客户端
   * @param databaseName database 名
   * @param tblName 表名
   * @param table 新的表定义
   */
  public static void alterTable(
      IMetaStoreClient client, String databaseName, String tblName, Table table) {
    alterTable(client, databaseName, tblName, table, ImmutableMap.of());
  }

  /**
   * 调用 HMS alter_table 修改表定义，并注入关闭统计更新的环境上下文。
   *
   * <p>逻辑：合并传入的额外环境变量与 {@link StatsSetupConst#DO_NOT_UPDATE_STATS}=true， 构造 {@link
   * EnvironmentContext} 后通过反射调用匹配版本的 alter_table。
   *
   * <p>设计要点：关闭 HMS 的统计信息自动更新，可避免 HMS 在 alter_table 时对表数据目录 做递归文件列举（Iceberg 表由自身管理统计信息，HMS
   * 列举既慢又无意义）。
   *
   * @param client HMS 客户端
   * @param databaseName database 名
   * @param tblName 表名
   * @param table 新的表定义
   * @param extraEnv 额外环境变量
   */
  public static void alterTable(
      IMetaStoreClient client,
      String databaseName,
      String tblName,
      Table table,
      Map<String, String> extraEnv) {
    Map<String, String> env = Maps.newHashMapWithExpectedSize(extraEnv.size() + 1);
    env.putAll(extraEnv);
    env.put(StatsSetupConst.DO_NOT_UPDATE_STATS, StatsSetupConst.TRUE);

    ALTER_TABLE.invoke(client, databaseName, tblName, table, new EnvironmentContext(env));
  }
}
