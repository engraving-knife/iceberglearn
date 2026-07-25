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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 快照（Snapshot）的 core 默认实现：表示表在某次提交后的不可变状态视图。
 *
 * <p>所属模块：iceberg-core，实现 {@link Snapshot} 接口，是表元数据中"快照"概念的具体承载者。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有快照 ID、父快照 ID、序列号、时间戳、操作类型、摘要、schema ID 等不可变属性。
 *   <li>支持两种 manifest 来源：v2 的 manifest list 文件位置，或 v1 的 manifest 文件位置数组。
 *   <li>懒加载并缓存所有 manifest、数据 manifest、删除 manifest，以及增删的数据/删除文件列表。
 * </ul>
 *
 * <p>设计意图：所有派生集合（{@code allManifests}、{@code dataManifests}、 {@code addedDataFiles} 等）标记 {@code
 * transient} 且懒加载，避免序列化开销与不必要的 IO； 首次访问时通过 {@link #cacheManifests(FileIO)} 或 {@code
 * cacheXxxChanges} 读取文件并缓存。 v1/v2 manifest 来源的差异通过构造器重载区分，{@link #cacheManifests(FileIO)} 内部统一处理。
 *
 * <p>上下游关系：由 {@link SnapshotParser} 从元数据 JSON 解析得到，或由提交流程 （{@code
 * SnapshotProducer}）创建；被扫描计划、过期快照、回滚等流程广泛消费。
 */
class BaseSnapshot implements Snapshot {
  private final long snapshotId;
  private final Long parentId;
  private final long sequenceNumber;
  private final long timestampMillis;
  private final String manifestListLocation;
  private final String operation;
  private final Map<String, String> summary;
  private final Integer schemaId;
  private final String[] v1ManifestLocations;

  // lazily initialized
  private transient List<ManifestFile> allManifests = null;
  private transient List<ManifestFile> dataManifests = null;
  private transient List<ManifestFile> deleteManifests = null;
  private transient List<DataFile> addedDataFiles = null;
  private transient List<DataFile> removedDataFiles = null;
  private transient List<DeleteFile> addedDeleteFiles = null;
  private transient List<DeleteFile> removedDeleteFiles = null;

  /**
   * 构造 v2 快照：manifest 通过 manifest list 文件位置引用。
   *
   * @param sequenceNumber 序列号
   * @param snapshotId 快照 ID
   * @param parentId 父快照 ID（可为 null）
   * @param timestampMillis 提交时间戳（毫秒）
   * @param operation 操作类型（append/overwrite/replace/delete）
   * @param summary 快照摘要
   * @param schemaId 提交时使用的 schema ID
   * @param manifestList manifest list 文件位置
   */
  BaseSnapshot(
      long sequenceNumber,
      long snapshotId,
      Long parentId,
      long timestampMillis,
      String operation,
      Map<String, String> summary,
      Integer schemaId,
      String manifestList) {
    this.sequenceNumber = sequenceNumber;
    this.snapshotId = snapshotId;
    this.parentId = parentId;
    this.timestampMillis = timestampMillis;
    this.operation = operation;
    this.summary = summary;
    this.schemaId = schemaId;
    this.manifestListLocation = manifestList;
    this.v1ManifestLocations = null;
  }

  /**
   * 构造 v1 快照：manifest 通过文件位置数组直接引用。
   *
   * @param sequenceNumber 序列号
   * @param snapshotId 快照 ID
   * @param parentId 父快照 ID（可为 null）
   * @param timestampMillis 提交时间戳（毫秒）
   * @param operation 操作类型
   * @param summary 快照摘要
   * @param schemaId schema ID
   * @param v1ManifestLocations v1 manifest 文件位置数组
   */
  BaseSnapshot(
      long sequenceNumber,
      long snapshotId,
      Long parentId,
      long timestampMillis,
      String operation,
      Map<String, String> summary,
      Integer schemaId,
      String[] v1ManifestLocations) {
    this.sequenceNumber = sequenceNumber;
    this.snapshotId = snapshotId;
    this.parentId = parentId;
    this.timestampMillis = timestampMillis;
    this.operation = operation;
    this.summary = summary;
    this.schemaId = schemaId;
    this.manifestListLocation = null;
    this.v1ManifestLocations = v1ManifestLocations;
  }

  /** 返回本快照的序列号。 */
  @Override
  public long sequenceNumber() {
    return sequenceNumber;
  }

  /** 返回本快照的 ID。 */
  @Override
  public long snapshotId() {
    return snapshotId;
  }

  /** 返回父快照 ID（首个快照返回 null）。 */
  @Override
  public Long parentId() {
    return parentId;
  }

  /** 返回快照提交时间戳（毫秒）。 */
  @Override
  public long timestampMillis() {
    return timestampMillis;
  }

  /** 返回本次提交的操作类型。 */
  @Override
  public String operation() {
    return operation;
  }

  /** 返回快照摘要（含文件计数、记录数等统计）。 */
  @Override
  public Map<String, String> summary() {
    return summary;
  }

  /** 返回提交时使用的 schema ID。 */
  @Override
  public Integer schemaId() {
    return schemaId;
  }

  /**
   * 懒加载并缓存本快照的全部 manifest（含数据与删除 manifest）。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若 FileIO 为 null，抛出 IllegalArgumentException。
   *   <li>若 {@code allManifests} 未初始化且持有 v1 manifest 位置数组，则将每个位置包装为 {@link
   *       GenericManifestFile}（sequenceNumber=0）。
   *   <li>否则从 {@code manifestListLocation} 读取 manifest list 文件得到 manifest 列表。
   *   <li>若数据/删除 manifest 未分类，则按 {@link ManifestContent} 过滤分别缓存。
   * </ul>
   *
   * @param fileIO 文件 IO 句柄
   * @throws IllegalArgumentException 当 FileIO 为 null
   */
  private void cacheManifests(FileIO fileIO) {
    if (fileIO == null) {
      throw new IllegalArgumentException("Cannot cache changes: FileIO is null");
    }

    if (allManifests == null && v1ManifestLocations != null) {
      // if we have a collection of manifest locations, then we need to load them here
      allManifests =
          Lists.transform(
              Arrays.asList(v1ManifestLocations),
              location -> new GenericManifestFile(fileIO.newInputFile(location), 0));
    }

    if (allManifests == null) {
      // if manifests isn't set, then the snapshotFile is set and should be read to get the list
      this.allManifests = ManifestLists.read(fileIO.newInputFile(manifestListLocation));
    }

    if (dataManifests == null || deleteManifests == null) {
      this.dataManifests =
          ImmutableList.copyOf(
              Iterables.filter(
                  allManifests, manifest -> manifest.content() == ManifestContent.DATA));
      this.deleteManifests =
          ImmutableList.copyOf(
              Iterables.filter(
                  allManifests, manifest -> manifest.content() == ManifestContent.DELETES));
    }
  }

  /**
   * 返回本快照所有 manifest（懒加载）。
   *
   * @param fileIO 文件 IO 句柄
   * @return manifest 文件列表
   */
  @Override
  public List<ManifestFile> allManifests(FileIO fileIO) {
    if (allManifests == null) {
      cacheManifests(fileIO);
    }
    return allManifests;
  }

  /**
   * 返回本快照的数据 manifest（懒加载）。
   *
   * @param fileIO 文件 IO 句柄
   * @return 数据 manifest 列表
   */
  @Override
  public List<ManifestFile> dataManifests(FileIO fileIO) {
    if (dataManifests == null) {
      cacheManifests(fileIO);
    }
    return dataManifests;
  }

  /**
   * 返回本快照的删除 manifest（懒加载）。
   *
   * @param fileIO 文件 IO 句柄
   * @return 删除 manifest 列表
   */
  @Override
  public List<ManifestFile> deleteManifests(FileIO fileIO) {
    if (deleteManifests == null) {
      cacheManifests(fileIO);
    }
    return deleteManifests;
  }

  /**
   * 返回本快照新增的数据文件（懒加载）。
   *
   * @param fileIO 文件 IO 句柄
   * @return 新增数据文件列表
   */
  @Override
  public List<DataFile> addedDataFiles(FileIO fileIO) {
    if (addedDataFiles == null) {
      cacheDataFileChanges(fileIO);
    }
    return addedDataFiles;
  }

  /**
   * 返回本快照删除的数据文件（懒加载）。
   *
   * @param fileIO 文件 IO 句柄
   * @return 删除数据文件列表
   */
  @Override
  public List<DataFile> removedDataFiles(FileIO fileIO) {
    if (removedDataFiles == null) {
      cacheDataFileChanges(fileIO);
    }
    return removedDataFiles;
  }

  /**
   * 返回本快照新增的删除文件（懒加载）。
   *
   * @param fileIO 文件 IO 句柄
   * @return 新增删除文件列表
   */
  @Override
  public Iterable<DeleteFile> addedDeleteFiles(FileIO fileIO) {
    if (addedDeleteFiles == null) {
      cacheDeleteFileChanges(fileIO);
    }
    return addedDeleteFiles;
  }

  /**
   * 返回本快照移除的删除文件（懒加载）。
   *
   * @param fileIO 文件 IO 句柄
   * @return 移除删除文件列表
   */
  @Override
  public Iterable<DeleteFile> removedDeleteFiles(FileIO fileIO) {
    if (removedDeleteFiles == null) {
      cacheDeleteFileChanges(fileIO);
    }
    return removedDeleteFiles;
  }

  /** 返回 manifest list 文件位置（v2 快照），v1 快照返回 null。 */
  @Override
  public String manifestListLocation() {
    return manifestListLocation;
  }

  /**
   * 懒加载并缓存本快照对删除文件的增删变更。
   *
   * <p>逻辑：从删除 manifest 中筛选本快照创建的 manifest（snapshotId 匹配）， 逐 manifest 读取条目，按 {@link
   * ManifestEntry.Status} 分类： {@code ADDED} 加入新增集合，{@code DELETED} 加入删除集合，{@code EXISTING} 忽略。
   *
   * @param fileIO 文件 IO 句柄
   * @throws IllegalArgumentException 当 FileIO 为 null
   */
  private void cacheDeleteFileChanges(FileIO fileIO) {
    Preconditions.checkArgument(fileIO != null, "Cannot cache delete file changes: FileIO is null");

    ImmutableList.Builder<DeleteFile> adds = ImmutableList.builder();
    ImmutableList.Builder<DeleteFile> deletes = ImmutableList.builder();

    Iterable<ManifestFile> changedManifests =
        Iterables.filter(
            deleteManifests(fileIO), manifest -> Objects.equal(manifest.snapshotId(), snapshotId));

    for (ManifestFile manifest : changedManifests) {
      try (ManifestReader<DeleteFile> reader =
          ManifestFiles.readDeleteManifest(manifest, fileIO, null)) {
        for (ManifestEntry<DeleteFile> entry : reader.entries()) {
          switch (entry.status()) {
            case ADDED:
              adds.add(entry.file().copy());
              break;
            case DELETED:
              deletes.add(entry.file().copyWithoutStats());
              break;
            default:
              // ignore existing
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to close manifest reader", e);
      }
    }

    this.addedDeleteFiles = adds.build();
    this.removedDeleteFiles = deletes.build();
  }

  /**
   * 懒加载并缓存本快照对数据文件的增删变更。
   *
   * <p>逻辑：从数据 manifest 中筛选本快照创建的 manifest，用 {@link ManifestGroup} 读取条目 （忽略 EXISTING），按状态分类：{@code
   * ADDED} 加入新增集合并保留统计信息， {@code DELETED} 加入删除集合且去除统计信息以节省内存；遇到非增非删状态抛 IllegalStateException。
   *
   * @param fileIO 文件 IO 句柄
   * @throws IllegalArgumentException 当 FileIO 为 null
   */
  private void cacheDataFileChanges(FileIO fileIO) {
    Preconditions.checkArgument(fileIO != null, "Cannot cache data file changes: FileIO is null");

    ImmutableList.Builder<DataFile> adds = ImmutableList.builder();
    ImmutableList.Builder<DataFile> deletes = ImmutableList.builder();

    // read only manifests that were created by this snapshot
    Iterable<ManifestFile> changedManifests =
        Iterables.filter(
            dataManifests(fileIO), manifest -> Objects.equal(manifest.snapshotId(), snapshotId));
    try (CloseableIterable<ManifestEntry<DataFile>> entries =
        new ManifestGroup(fileIO, changedManifests).ignoreExisting().entries()) {
      for (ManifestEntry<DataFile> entry : entries) {
        switch (entry.status()) {
          case ADDED:
            adds.add(entry.file().copy());
            break;
          case DELETED:
            deletes.add(entry.file().copyWithoutStats());
            break;
          default:
            throw new IllegalStateException(
                "Unexpected entry status, not added or deleted: " + entry);
        }
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to close entries while caching changes");
    }

    this.addedDataFiles = adds.build();
    this.removedDataFiles = deletes.build();
  }

  /**
   * 相等性判断：基于快照 ID、父快照 ID、序列号、时间戳、schema ID 比较。
   *
   * @param o 待比较对象
   * @return 相等返回 true
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o instanceof BaseSnapshot) {
      BaseSnapshot other = (BaseSnapshot) o;
      return this.snapshotId == other.snapshotId()
          && Objects.equal(this.parentId, other.parentId())
          && this.sequenceNumber == other.sequenceNumber()
          && this.timestampMillis == other.timestampMillis()
          && Objects.equal(this.schemaId, other.schemaId());
    }

    return false;
  }

  /** 哈希码：与 equals 字段一致。 */
  @Override
  public int hashCode() {
    return Objects.hashCode(
        this.snapshotId, this.parentId, this.sequenceNumber, this.timestampMillis, this.schemaId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", snapshotId)
        .add("timestamp_ms", timestampMillis)
        .add("operation", operation)
        .add("summary", summary)
        .add("manifest-list", manifestListLocation)
        .add("schema-id", schemaId)
        .toString();
  }
}
