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

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.TableMetadata.MetadataLogEntry;
import org.apache.iceberg.hadoop.Util;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 表可达文件路径收集工具。
 *
 * <p>所属模块：iceberg-core。职责：枚举一张表在文件系统中所有"可达"的文件路径，包括 metadata.json、 manifest list、manifest、数据文件与
 * delete 文件等，供过期文件清理或迁移使用。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>递归/非递归：支持只列当前元数据引用的文件，或递归遍历历史快照引用的全部文件。
 *   <li>Hadoop 路径约定：对 version-hint 文件按 Hadoop 表的固定布局推导路径。
 *   <li>谓词过滤：通过 {@link Predicate} 让调用方自定义过滤策略。
 * </ul>
 *
 * <p>上下游关系：被维护动作（如 {@code RemoveOrphanFilesAction}）与迁移工具调用； 依赖 {@link FileIO}、{@link TableMetadata}
 * 与 {@link Snapshot}。
 */
public class ReachableFileUtil {

  private static final Logger LOG = LoggerFactory.getLogger(ReachableFileUtil.class);
  private static final String METADATA_FOLDER_NAME = "metadata";

  /** 私有构造：工具类禁止实例化。 */
  private ReachableFileUtil() {}

  /**
   * 返回 version-hint 文件的位置。
   *
   * <p>仅 Hadoop 表存在 version-hint 文件，且这类表有固定的 metadata 目录布局 （{@code
   * &lt;table-location&gt;/metadata/version-hint.text}）。
   *
   * @param table 目标表
   * @return version-hint 文件的字符串路径
   */
  public static String versionHintLocation(Table table) {
    // only Hadoop tables have a hint file and such tables have a fixed metadata layout
    Path metadataPath = new Path(table.location() + "/" + METADATA_FOLDER_NAME);
    Path versionHintPath = new Path(metadataPath + "/" + Util.VERSION_HINT_FILENAME);
    return versionHintPath.toString();
  }

  /**
   * 收集表的 JSON metadata 文件位置集合。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>加入当前 metadata.json 的位置；
   *   <li>调用私有重载补充历史日志条目；recursive=true 时递归向上回溯。
   * </ol>
   *
   * @param table 目标表
   * @param recursive true 表示递归收集所有可达历史 metadata.json；false 只取当前元数据引用的
   * @return metadata.json 文件位置集合
   */
  public static Set<String> metadataFileLocations(Table table, boolean recursive) {
    Set<String> metadataFileLocations = Sets.newHashSet();
    TableOperations ops = ((HasTableOperations) table).operations();
    TableMetadata tableMetadata = ops.current();
    metadataFileLocations.add(tableMetadata.metadataFileLocation());
    metadataFileLocations(tableMetadata, metadataFileLocations, ops.io(), recursive);
    return metadataFileLocations;
  }

  /**
   * 把 metadata.previousFiles() 中的历史 metadata 文件位置加入集合； recursive=true 时找到第一个可读的历史 metadata 并递归向上回溯。
   *
   * @param metadata 当前元数据
   * @param metadataFileLocations 累积的文件位置集合（会被修改）
   * @param io 文件 IO
   * @param recursive 是否递归回溯历史
   */
  private static void metadataFileLocations(
      TableMetadata metadata, Set<String> metadataFileLocations, FileIO io, boolean recursive) {
    List<MetadataLogEntry> metadataLogEntries = metadata.previousFiles();
    if (metadataLogEntries.size() > 0) {
      for (MetadataLogEntry metadataLogEntry : metadataLogEntries) {
        metadataFileLocations.add(metadataLogEntry.file());
      }
      if (recursive) {
        TableMetadata previousMetadata = findFirstExistentPreviousMetadata(metadataLogEntries, io);
        if (previousMetadata != null) {
          metadataFileLocations(previousMetadata, metadataFileLocations, io, recursive);
        }
      }
    }
  }

  /**
   * 在历史日志条目中找到第一个可成功读取的 metadata 文件并解析。
   *
   * <p>设计要点：历史文件可能已被清理，故逐个尝试，遇到异常仅 error 日志后继续。
   *
   * @param metadataLogEntries 历史元数据日志条目列表
   * @param io 文件 IO
   * @return 第一个可读的历史 TableMetadata；全部不可读时返回 null
   */
  private static TableMetadata findFirstExistentPreviousMetadata(
      List<MetadataLogEntry> metadataLogEntries, FileIO io) {
    TableMetadata metadata = null;
    for (MetadataLogEntry metadataLogEntry : metadataLogEntries) {
      try {
        metadata = TableMetadataParser.read(io, metadataLogEntry.file());
        break;
      } catch (Exception e) {
        LOG.error("Failed to load {}", metadataLogEntry, e);
      }
    }
    return metadata;
  }

  /**
   * 列出表中所有快照的 manifest list 位置。
   *
   * @param table 目标表
   * @return manifest list 文件路径列表
   */
  public static List<String> manifestListLocations(Table table) {
    return manifestListLocations(table, null);
  }

  /**
   * 列出指定快照集合对应的 manifest list 位置。
   *
   * <p>snapshotIds 为 null 时表示取全部快照。manifestListLocation 为 null 的快照会被跳过。
   *
   * @param table 目标表
   * @param snapshotIds 想要的快照 id 集合，null 表示全部
   * @return manifest list 文件路径列表
   */
  public static List<String> manifestListLocations(Table table, Set<Long> snapshotIds) {
    Iterable<Snapshot> snapshots = table.snapshots();
    if (snapshotIds != null) {
      snapshots = Iterables.filter(snapshots, s -> snapshotIds.contains(s.snapshotId()));
    }

    List<String> manifestListLocations = Lists.newArrayList();
    for (Snapshot snapshot : snapshots) {
      String manifestListLocation = snapshot.manifestListLocation();
      if (manifestListLocation != null) {
        manifestListLocations.add(manifestListLocation);
      }
    }
    return manifestListLocations;
  }

  /**
   * 列出表中所有统计文件的位置（不过滤）。
   *
   * @param table 目标表
   * @return 统计文件路径列表
   */
  public static List<String> statisticsFilesLocations(Table table) {
    return statisticsFilesLocations(table, statisticsFile -> true);
  }

  /**
   * 列出表中符合谓词条件的统计文件位置。
   *
   * @param table 目标表
   * @param predicate 过滤谓词
   * @return 统计文件路径列表
   */
  public static List<String> statisticsFilesLocations(
      Table table, Predicate<StatisticsFile> predicate) {
    return table.statisticsFiles().stream()
        .filter(predicate)
        .map(StatisticsFile::path)
        .collect(Collectors.toList());
  }
}
