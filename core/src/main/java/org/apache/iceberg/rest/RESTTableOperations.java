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
package org.apache.iceberg.rest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.iceberg.LocationProviders;
import org.apache.iceberg.MetadataUpdate;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.UpdateRequirement;
import org.apache.iceberg.UpdateRequirements;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.rest.requests.UpdateTableRequest;
import org.apache.iceberg.rest.responses.ErrorResponse;
import org.apache.iceberg.rest.responses.LoadTableResponse;
import org.apache.iceberg.util.LocationUtil;

/**
 * 文件级说明：基于 REST 协议的 {@link TableOperations} 实现，负责单表的元数据提交与刷新。
 *
 * <p>所属模块：iceberg-core（REST Catalog 表操作层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link TableOperations}，通过 {@link RESTClient} 向服务端提交表元数据变更与刷新。
 *   <li>支持三种提交类型：CREATE（建表）、REPLACE（替换表）、SIMPLE（普通更新）。
 *   <li>提供元数据文件位置计算、{@link LocationProvider} 与临时表操作视图。
 * </ul>
 *
 * <p>设计意图：提交类型通过 {@link UpdateType} 区分，不同类型对应不同的需求校验 （{@link UpdateRequirements}）与错误处理策略。提交后自动降级为
 * SIMPLE，保证后续提交走普通流程。
 *
 * <p>上下游关系：由 {@link RESTSessionCatalog} 在加载/创建/注册表时构造；依赖 {@link RESTClient}、 {@link FileIO} 与
 * {@link ErrorHandlers}。
 */
class RESTTableOperations implements TableOperations {
  private static final String METADATA_FOLDER_NAME = "metadata";

  /** 提交类型枚举：CREATE 建表、REPLACE 替换表、SIMPLE 普通更新。 */
  enum UpdateType {
    CREATE,
    REPLACE,
    SIMPLE
  }

  private final RESTClient client;
  private final String path;
  private final Supplier<Map<String, String>> headers;
  private final FileIO io;
  private final List<MetadataUpdate> createChanges;
  private final TableMetadata replaceBase;
  private UpdateType updateType;
  private TableMetadata current;

  /**
   * 简单提交构造器，使用 SIMPLE 类型与空变更列表。
   *
   * @param client HTTP 客户端
   * @param path 表资源路径
   * @param headers 请求头供应器
   * @param io 表级 FileIO
   * @param current 当前表元数据
   */
  RESTTableOperations(
      RESTClient client,
      String path,
      Supplier<Map<String, String>> headers,
      FileIO io,
      TableMetadata current) {
    this(client, path, headers, io, UpdateType.SIMPLE, Lists.newArrayList(), current);
  }

  /**
   * 完整构造器，指定提交类型与建表变更列表。
   *
   * @param client HTTP 客户端
   * @param path 表资源路径
   * @param headers 请求头供应器
   * @param io 表级 FileIO
   * @param updateType 提交类型
   * @param createChanges 建表/替换的变更列表
   * @param current 当前表元数据（CREATE 类型下会被置为 null）
   */
  RESTTableOperations(
      RESTClient client,
      String path,
      Supplier<Map<String, String>> headers,
      FileIO io,
      UpdateType updateType,
      List<MetadataUpdate> createChanges,
      TableMetadata current) {
    this.client = client;
    this.path = path;
    this.headers = headers;
    this.io = io;
    this.updateType = updateType;
    this.createChanges = createChanges;
    this.replaceBase = current;
    if (updateType == UpdateType.CREATE) {
      this.current = null;
    } else {
      this.current = current;
    }
  }

  /** 返回当前表元数据。 */
  @Override
  public TableMetadata current() {
    return current;
  }

  /**
   * 从服务端刷新表元数据，GET 表资源路径并更新当前元数据。
   *
   * @return 刷新后的表元数据
   */
  @Override
  public TableMetadata refresh() {
    return updateCurrentMetadata(
        client.get(path, LoadTableResponse.class, headers, ErrorHandlers.tableErrorHandler()));
  }

  /**
   * 提交表元数据变更，根据提交类型构造不同的需求与变更列表后 POST 到服务端。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>按 {@link UpdateType} 分支处理：CREATE 校验 base 为 null 并合并 createChanges； REPLACE 校验 base 非空并基于
   *       replaceBase 计算需求；SIMPLE 直接使用 metadata.changes()。
   *   <li>构造 {@link UpdateTableRequest} 并 POST，由错误处理器抛出提交失败等异常。
   *   <li>提交后将 updateType 置为 SIMPLE，并更新当前元数据。
   * </ol>
   *
   * @param base 期望的基线元数据（CREATE 时为 null）
   * @param metadata 待提交的目标元数据
   */
  @Override
  public void commit(TableMetadata base, TableMetadata metadata) {
    Consumer<ErrorResponse> errorHandler;
    List<UpdateRequirement> requirements;
    List<MetadataUpdate> updates;
    switch (updateType) {
      case CREATE:
        Preconditions.checkState(
            base == null, "Invalid base metadata for create transaction, expected null: %s", base);
        updates =
            ImmutableList.<MetadataUpdate>builder()
                .addAll(createChanges)
                .addAll(metadata.changes())
                .build();
        requirements = UpdateRequirements.forCreateTable(updates);
        errorHandler = ErrorHandlers.tableErrorHandler(); // throws NoSuchTableException
        break;

      case REPLACE:
        Preconditions.checkState(base != null, "Invalid base metadata: null");
        updates =
            ImmutableList.<MetadataUpdate>builder()
                .addAll(createChanges)
                .addAll(metadata.changes())
                .build();
        // use the original replace base metadata because the transaction will refresh
        requirements = UpdateRequirements.forReplaceTable(replaceBase, updates);
        errorHandler = ErrorHandlers.tableCommitHandler();
        break;

      case SIMPLE:
        Preconditions.checkState(base != null, "Invalid base metadata: null");
        updates = metadata.changes();
        requirements = UpdateRequirements.forUpdateTable(base, updates);
        errorHandler = ErrorHandlers.tableCommitHandler();
        break;

      default:
        throw new UnsupportedOperationException(
            String.format("Update type %s is not supported", updateType));
    }

    UpdateTableRequest request = new UpdateTableRequest(requirements, updates);

    // the error handler will throw necessary exceptions like CommitFailedException and
    // UnknownCommitStateException
    // TODO: ensure that the HTTP client lib passes HTTP client errors to the error handler
    LoadTableResponse response =
        client.post(path, request, LoadTableResponse.class, headers, errorHandler);

    // all future commits should be simple commits
    this.updateType = UpdateType.SIMPLE;

    updateCurrentMetadata(response);
  }

  /** 返回表级 FileIO。 */
  @Override
  public FileIO io() {
    return io;
  }

  /**
   * 根据加载表响应更新当前元数据。仅当当前元数据为空或元数据文件位置变化时才更新， 避免无变化的重复解析。
   *
   * @param response 加载表响应
   * @return 当前表元数据
   */
  private TableMetadata updateCurrentMetadata(LoadTableResponse response) {
    // LoadTableResponse is used to deserialize the response, but config is not allowed by the REST
    // spec so it can be
    // safely ignored. there is no requirement to update config on refresh or commit.
    if (current == null
        || !Objects.equals(current.metadataFileLocation(), response.metadataLocation())) {
      this.current = response.tableMetadata();
    }

    return current;
  }

  /**
   * 计算元数据文件位置。优先使用配置的 WRITE_METADATA_LOCATION，否则使用表位置下的 metadata 目录。
   *
   * @param metadata 表元数据
   * @param filename 元数据文件名
   * @return 元数据文件完整路径
   */
  private static String metadataFileLocation(TableMetadata metadata, String filename) {
    String metadataLocation = metadata.properties().get(TableProperties.WRITE_METADATA_LOCATION);

    if (metadataLocation != null) {
      return String.format("%s/%s", LocationUtil.stripTrailingSlash(metadataLocation), filename);
    } else {
      return String.format("%s/%s/%s", metadata.location(), METADATA_FOLDER_NAME, filename);
    }
  }

  /** 返回当前表元数据下指定文件名的元数据文件位置。 */
  @Override
  public String metadataFileLocation(String filename) {
    return metadataFileLocation(current(), filename);
  }

  /** 根据当前表位置与属性返回 {@link LocationProvider}。 */
  @Override
  public LocationProvider locationProvider() {
    return LocationProviders.locationsFor(current().location(), current().properties());
  }

  /**
   * 返回一个临时 {@link TableOperations} 视图，使用未提交的元数据，禁止 refresh/commit。
   *
   * <p>设计意图：用于在事务提交前基于未提交元数据执行操作（如写入），而不影响真实表状态。
   *
   * @param uncommittedMetadata 未提交的元数据
   * @return 临时表操作视图
   */
  @Override
  public TableOperations temp(TableMetadata uncommittedMetadata) {
    return new TableOperations() {
      @Override
      public TableMetadata current() {
        return uncommittedMetadata;
      }

      @Override
      public TableMetadata refresh() {
        throw new UnsupportedOperationException(
            "Cannot call refresh on temporary table operations");
      }

      @Override
      public void commit(TableMetadata base, TableMetadata metadata) {
        throw new UnsupportedOperationException("Cannot call commit on temporary table operations");
      }

      @Override
      public String metadataFileLocation(String fileName) {
        return RESTTableOperations.metadataFileLocation(uncommittedMetadata, fileName);
      }

      @Override
      public LocationProvider locationProvider() {
        return LocationProviders.locationsFor(
            uncommittedMetadata.location(), uncommittedMetadata.properties());
      }

      @Override
      public FileIO io() {
        return RESTTableOperations.this.io();
      }

      @Override
      public EncryptionManager encryption() {
        return RESTTableOperations.this.encryption();
      }

      @Override
      public long newSnapshotId() {
        return RESTTableOperations.this.newSnapshotId();
      }
    };
  }
}
