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
package org.apache.iceberg.dell.ecs;

import java.util.Map;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 基于 ECS 对象的 {@link TableOperations} 实现，利用对象 E-Tag 实现乐观并发提交（CAS）。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 ECS 表对象（{@code *.table}）加载当前 metadata location 并刷新表元数据。
 *   <li>提交新版本元数据：写新 metadata 文件后，通过 CAS 更新表对象的 {@code iceberg_metadata_location} 属性。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>对象即元数据存储：不依赖外部 metastore，直接把表元数据 location 记录在 ECS 对象的 user properties 中，实现“对象存储即目录”的轻量化
 *       catalog。
 *   <li>乐观锁提交：刷新时缓存对象 E-Tag，提交时用 {@code If-Match} 条件写回。 若期间被他人修改则 E-Tag 失配，提交失败抛 {@link
 *       CommitFailedException}， 由上层重试。建表则用 {@code If-None-Match: *} 保证对象不存在。
 *   <li>继承 {@link BaseMetastoreTableOperations}：复用 metadata location 刷新与 版本文件写入的公共逻辑。
 * </ul>
 *
 * <p>上下游关系：由 {@link EcsCatalog#newTableOps} 创建；通过 {@link EcsCatalog} 读写表对象属性；通过 {@link FileIO}
 * 读写实际元数据/数据文件。
 */
public class EcsTableOperations extends BaseMetastoreTableOperations {

  /** 表对象 user properties 中记录 Iceberg metadata location 的键。 */
  public static final String ICEBERG_METADATA_LOCATION = "iceberg_metadata_location";

  private final String tableName;
  private final FileIO fileIO;
  private final EcsCatalog catalog;
  private final EcsURI tableObject;

  /**
   * 缓存的表对象 E-Tag，用于 CAS 提交。
   *
   * <p>在 {@link #doRefresh()} 刷新时重置为最新值，在 {@link #doCommit(TableMetadata, TableMetadata)} 提交时作为
   * {@code If-Match} 条件使用。
   */
  private String eTag;

  /**
   * 构造表操作实例。
   *
   * @param tableName 表全名（catalog 名 + 表标识）
   * @param tableObject 表元数据对象 location
   * @param fileIO 文件 IO，用于读写元数据/数据文件
   * @param catalog 所属 catalog，用于读写表对象属性
   */
  public EcsTableOperations(
      String tableName, EcsURI tableObject, FileIO fileIO, EcsCatalog catalog) {
    this.tableName = tableName;
    this.tableObject = tableObject;
    this.fileIO = fileIO;
    this.catalog = catalog;
  }

  @Override
  protected String tableName() {
    return tableName;
  }

  @Override
  public FileIO io() {
    return fileIO;
  }

  /**
   * 刷新表元数据：从 ECS 表对象读取最新 metadata location。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若表对象不存在：已加载过的表抛 {@link NoSuchTableException}（疑似被删/移动）， 否则 metadataLocation 置 null。
   *   <li>若表对象存在：加载其属性，缓存 E-Tag，取出 {@link #ICEBERG_METADATA_LOCATION}，要求非空。
   *   <li>调用 {@link #refreshFromMetadataLocation(String)} 完成实际元数据加载。
   * </ul>
   */
  @Override
  protected void doRefresh() {
    String metadataLocation;
    if (!catalog.objectMetadata(tableObject).isPresent()) {
      if (currentMetadataLocation() != null) {
        throw new NoSuchTableException(
            "Metadata object %s is absent while refresh a loaded table. "
                + "Maybe the table is deleted/moved.",
            tableObject);
      } else {
        metadataLocation = null;
      }
    } else {
      EcsCatalog.Properties metadata = catalog.loadProperties(tableObject);
      this.eTag = metadata.eTag();
      metadataLocation = metadata.content().get(ICEBERG_METADATA_LOCATION);
      Preconditions.checkNotNull(
          metadataLocation, "Can't find location from table metadata %s", tableObject);
    }

    refreshFromMetadataLocation(metadataLocation);
  }

  /**
   * 提交新版本表元数据，基于 E-Tag 做乐观并发控制。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>写新的 metadata 文件得到 newMetadataLocation。
   *   <li>建表（base 为 null）：用 {@code putNewProperties}（{@code If-None-Match: *}） 创建表对象，失败说明表已存在，抛
   *       {@link CommitFailedException}。
   *   <li>更新表（base 非空）：用缓存的 E-Tag 做 {@code updatePropertiesObject} （{@code If-Match}），失败说明被并发修改，抛
   *       {@link CommitFailedException}。
   * </ul>
   *
   * @param base 提交前的基础元数据，null 表示建表
   * @param metadata 提交后的新元数据
   * @throws CommitFailedException 当并发冲突导致 CAS 失败时抛出
   */
  @Override
  protected void doCommit(TableMetadata base, TableMetadata metadata) {
    boolean newTable = base == null;
    String newMetadataLocation = writeNewMetadataIfRequired(newTable, metadata);
    if (base == null) {
      // 建表：表对象必须不存在
      if (!catalog.putNewProperties(tableObject, buildProperties(newMetadataLocation))) {
        throw new CommitFailedException("Table is existing when create table %s", tableName());
      }
    } else {
      String cachedETag = eTag;
      Preconditions.checkNotNull(cachedETag, "E-Tag must be not null when update table");
      // 更新表：E-Tag 必须存在且匹配
      boolean result =
          catalog.updatePropertiesObject(
              tableObject, cachedETag, buildProperties(newMetadataLocation));
      if (!result) {
        throw new CommitFailedException(
            "Replace failed, E-Tag %s mismatch for table %s", cachedETag, tableName());
      }
    }
  }

  /**
   * 构造表对象的属性，仅包含 metadata location 一项。
   *
   * @param metadataLocation 新的 metadata 文件 location
   * @return 不可变属性 Map
   */
  private Map<String, String> buildProperties(String metadataLocation) {
    return ImmutableMap.of(ICEBERG_METADATA_LOCATION, metadataLocation);
  }
}
