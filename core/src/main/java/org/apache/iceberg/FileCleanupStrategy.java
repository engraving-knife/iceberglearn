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

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件清理策略抽象基类：定义过期快照后删除数据/manifest/统计文件的统一框架。
 *
 * <p>所属模块：iceberg-core，被 {@code RemoveSnapshots}（过期快照）流程调用以回收存储。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供读取快照 manifest 列表的能力（含投影以减少读取字段）。
 *   <li>提供并行删除文件的能力（含重试与失败抑制）。
 *   <li>计算过期统计文件位置集合。
 *   <li>由子类实现 {@link #cleanFiles(TableMetadata, TableMetadata)} 具体清理逻辑。
 * </ul>
 *
 * <p>设计意图：将"删除文件"这一副作用操作抽象为策略类，分离清理逻辑与过期判断逻辑； 删除操作通过 {@link Tasks} 工具获得重试与 NotFoundException
 * 短路能力，避免因个别文件 已不存在导致整个清理失败。{@code deleteFunc} 注入允许调用方自定义删除行为（如 dry-run）。
 *
 * <p>上下游关系：由 {@code RemoveSnapshots}、{@code ManifestCleanupManager} 等子类继承； 依赖 {@link FileIO} 执行实际
 * IO，{@code planExecutorService}/{@code deleteExecutorService} 分别用于计划与删除的并行执行。
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
abstract class FileCleanupStrategy {
  private static final Logger LOG = LoggerFactory.getLogger(FileCleanupStrategy.class);

  protected final FileIO fileIO;
  protected final ExecutorService planExecutorService;
  private final Consumer<String> deleteFunc;
  private final ExecutorService deleteExecutorService;

  protected FileCleanupStrategy(
      FileIO fileIO,
      ExecutorService deleteExecutorService,
      ExecutorService planExecutorService,
      Consumer<String> deleteFunc) {
    this.fileIO = fileIO;
    this.deleteExecutorService = deleteExecutorService;
    this.planExecutorService = planExecutorService;
    this.deleteFunc = deleteFunc;
  }

  /**
   * 清理过期文件：由子类实现具体清理逻辑。
   *
   * @param beforeExpiration 过期前的表元数据
   * @param afterExpiration 过期后的表元数据
   */
  public abstract void cleanFiles(TableMetadata beforeExpiration, TableMetadata afterExpiration);

  private static final Schema MANIFEST_PROJECTION =
      ManifestFile.schema()
          .select(
              "manifest_path",
              "manifest_length",
              "partition_spec_id",
              "added_snapshot_id",
              "deleted_data_files_count");

  /**
   * 读取快照的 manifest 列表，仅投影必要字段以减少 IO。
   *
   * <p>逻辑：若快照有 manifest list 位置，则用 Avro 读取并投影 {@link #MANIFEST_PROJECTION} 字段； 否则直接返回快照已加载的
   * manifest 列表。
   *
   * @param snapshot 快照
   * @return manifest 可迭代集合
   */
  protected CloseableIterable<ManifestFile> readManifests(Snapshot snapshot) {
    if (snapshot.manifestListLocation() != null) {
      return Avro.read(fileIO.newInputFile(snapshot.manifestListLocation()))
          .rename("manifest_file", GenericManifestFile.class.getName())
          .classLoader(GenericManifestFile.class.getClassLoader())
          .project(MANIFEST_PROJECTION)
          .reuseContainers(true)
          .build();
    } else {
      return CloseableIterable.withNoopClose(snapshot.allManifests(fileIO));
    }
  }

  /**
   * 并行删除一组文件，含重试与失败抑制。
   *
   * <p>逻辑：通过 {@link Tasks} 并行执行 {@code deleteFunc}，重试 3 次， 遇到 {@link NotFoundException}
   * 时停止重试（文件已不存在视为成功）， 最终失败不抛异常仅记录警告日志。
   *
   * @param pathsToDelete 待删除文件路径集合
   * @param fileType 文件类型描述（用于日志）
   */
  protected void deleteFiles(Set<String> pathsToDelete, String fileType) {
    Tasks.foreach(pathsToDelete)
        .executeWith(deleteExecutorService)
        .retry(3)
        .stopRetryOn(NotFoundException.class)
        .suppressFailureWhenFinished()
        .onFailure(
            (file, thrown) -> LOG.warn("Delete failed for {} file: {}", fileType, file, thrown))
        .run(deleteFunc::accept);
  }

  /**
   * 计算过期统计文件位置集合：取过期前有而过期后没有的统计文件位置差集。
   *
   * @param beforeExpiration 过期前表元数据
   * @param afterExpiration 过期后表元数据
   * @return 过期统计文件路径集合
   */
  protected Set<String> expiredStatisticsFilesLocations(
      TableMetadata beforeExpiration, TableMetadata afterExpiration) {
    Set<String> statsFileLocationsBeforeExpiration = statsFileLocations(beforeExpiration);
    Set<String> statsFileLocationsAfterExpiration = statsFileLocations(afterExpiration);

    return Sets.difference(statsFileLocationsBeforeExpiration, statsFileLocationsAfterExpiration);
  }

  private Set<String> statsFileLocations(TableMetadata tableMetadata) {
    Set<String> statsFileLocations = Sets.newHashSet();

    if (tableMetadata.statisticsFiles() != null) {
      statsFileLocations =
          tableMetadata.statisticsFiles().stream()
              .map(StatisticsFile::path)
              .collect(Collectors.toSet());
    }

    return statsFileLocations;
  }
}
