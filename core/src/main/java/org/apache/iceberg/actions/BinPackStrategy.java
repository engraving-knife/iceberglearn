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

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.FluentIterable;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.math.LongMath;
import org.apache.iceberg.util.BinPacking;
import org.apache.iceberg.util.BinPacking.ListPacker;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于文件大小的数据文件重写策略。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据文件大小挑选待重写文件：小于 {@link #MIN_FILE_SIZE_BYTES} 或大于 {@link #MAX_FILE_SIZE_BYTES} 的文件视为重写目标。
 *   <li>利用 {@link BinPacking} 将选中文件按 {@link RewriteDataFiles#MAX_FILE_GROUP_SIZE_BYTES}
 *       装箱分组；仅当分组文件数超过 {@link #MIN_INPUT_FILES} 或分组总大小可产出至少一个目标大小文件时， 才对该分组执行重写。
 *   <li>计算输出文件数与切分大小，避免产生过小的残余文件。
 * </ul>
 *
 * <p>设计意图：通过"大小阈值 + 装箱分组"实现小文件合并与大文件拆分，平衡读写放大与文件数。 各阈值均以目标文件大小为基准按比例默认取值，并支持配置覆盖。
 *
 * <p>上下游关系：实现 {@link RewriteStrategy}，被 {@link SortStrategy} 继承；由具体引擎的 {@link RewriteDataFiles}
 * 动作调用。
 *
 * @deprecated since 1.3.0, will be removed in 1.4.0; use {@link SizeBasedFileRewriter} instead.
 *     Note: This can only be removed once Spark 3.2 isn't using this API anymore.
 */
@Deprecated
public abstract class BinPackStrategy implements RewriteStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(BinPackStrategy.class);

  /**
   * 当一个分组的总大小不足 {@link RewriteDataFiles#TARGET_FILE_SIZE_BYTES} 时，要求分组中至少包含
   * 这么多文件才会被纳入重写。也可理解为重写后一个分组（分区）中允许残留的非目标大小文件的最大数量。
   */
  public static final String MIN_INPUT_FILES = "min-input-files";

  public static final int MIN_INPUT_FILES_DEFAULT = 5;

  /**
   * 调整被视为重写目标的文件大小下限：小于 {@link #MIN_FILE_SIZE_BYTES} 的文件将被重写。 该阈值与 {@link #MAX_FILE_SIZE_BYTES}
   * 相互独立。
   *
   * <p>默认为目标文件大小的 75%。
   */
  public static final String MIN_FILE_SIZE_BYTES = "min-file-size-bytes";

  public static final double MIN_FILE_SIZE_DEFAULT_RATIO = 0.75d;

  /**
   * 调整被视为重写目标的文件大小上限：大于 {@link #MAX_FILE_SIZE_BYTES} 的文件将被重写。 该阈值与 {@link #MIN_FILE_SIZE_BYTES}
   * 相互独立。
   *
   * <p>默认为目标文件大小的 180%。
   */
  public static final String MAX_FILE_SIZE_BYTES = "max-file-size-bytes";

  public static final double MAX_FILE_SIZE_DEFAULT_RATIO = 1.80d;

  /**
   * 数据文件关联的删除文件数量阈值：当某数据文件关联的删除文件数达到或超过此值时，无论其大小是否 落在 {@link #MIN_FILE_SIZE_BYTES}/{@link
   * #MAX_FILE_SIZE_BYTES} 区间内都会被重写；且包含此类 文件的分组将无视 {@link #MIN_INPUT_FILES} 限制直接重写。
   *
   * <p>默认为 {@link Integer#MAX_VALUE}，即默认不启用该特性。
   */
  public static final String DELETE_FILE_THRESHOLD = "delete-file-threshold";

  public static final int DELETE_FILE_THRESHOLD_DEFAULT = Integer.MAX_VALUE;

  static final long SPLIT_OVERHEAD = 1024 * 5;

  /** 是否重写全部文件（忽略大小判断）。默认为 false，仅重写尺寸不合理的文件。 */
  public static final String REWRITE_ALL = "rewrite-all";

  public static final boolean REWRITE_ALL_DEFAULT = false;

  private int minInputFiles;
  private int deleteFileThreshold;
  private long minFileSize;
  private long maxFileSize;
  private long targetFileSize;
  private long maxGroupSize;
  private boolean rewriteAll;

  /** 返回策略名称 "BINPACK"。 */
  @Override
  public String name() {
    return "BINPACK";
  }

  /** 返回本策略可接受的选项白名单。 */
  @Override
  public Set<String> validOptions() {
    return ImmutableSet.of(
        MIN_INPUT_FILES,
        DELETE_FILE_THRESHOLD,
        MIN_FILE_SIZE_BYTES,
        MAX_FILE_SIZE_BYTES,
        REWRITE_ALL);
  }

  /**
   * 解析并设置本策略的运行选项。
   *
   * <p>逻辑：优先从传入 options 读取目标文件大小，回退到表属性，再回退到默认值；随后按比例计算 最小/最大文件大小阈值，并读取分组大小、最小输入文件数、删除阈值、是否全量重写等参数；
   * 最后调用 {@link #validateOptions()} 校验合法性。
   *
   * @param options 选项键值对
   * @return 当前策略实例
   */
  @Override
  public RewriteStrategy options(Map<String, String> options) {
    targetFileSize =
        PropertyUtil.propertyAsLong(
            options,
            RewriteDataFiles.TARGET_FILE_SIZE_BYTES,
            PropertyUtil.propertyAsLong(
                table().properties(),
                TableProperties.WRITE_TARGET_FILE_SIZE_BYTES,
                TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT));

    minFileSize =
        PropertyUtil.propertyAsLong(
            options, MIN_FILE_SIZE_BYTES, (long) (targetFileSize * MIN_FILE_SIZE_DEFAULT_RATIO));

    maxFileSize =
        PropertyUtil.propertyAsLong(
            options, MAX_FILE_SIZE_BYTES, (long) (targetFileSize * MAX_FILE_SIZE_DEFAULT_RATIO));

    maxGroupSize =
        PropertyUtil.propertyAsLong(
            options,
            RewriteDataFiles.MAX_FILE_GROUP_SIZE_BYTES,
            RewriteDataFiles.MAX_FILE_GROUP_SIZE_BYTES_DEFAULT);

    minInputFiles = PropertyUtil.propertyAsInt(options, MIN_INPUT_FILES, MIN_INPUT_FILES_DEFAULT);

    deleteFileThreshold =
        PropertyUtil.propertyAsInt(options, DELETE_FILE_THRESHOLD, DELETE_FILE_THRESHOLD_DEFAULT);

    rewriteAll = PropertyUtil.propertyAsBoolean(options, REWRITE_ALL, REWRITE_ALL_DEFAULT);

    validateOptions();
    return this;
  }

  /**
   * 筛选需要重写的文件。
   *
   * <p>逻辑：若开启 {@code rewriteAll} 则返回全部文件；否则过滤出文件长度小于 {@code minFileSize}、 大于 {@code maxFileSize}
   * 或关联删除文件数过多的扫描任务。
   *
   * @param dataFiles 待筛选的文件扫描任务
   * @return 需重写的文件扫描任务
   */
  @Override
  public Iterable<FileScanTask> selectFilesToRewrite(Iterable<FileScanTask> dataFiles) {
    if (rewriteAll) {
      LOG.info("Table {} set to rewrite all data files", table().name());
      return dataFiles;
    } else {
      return FluentIterable.from(dataFiles)
          .filter(
              scanTask ->
                  scanTask.length() < minFileSize
                      || scanTask.length() > maxFileSize
                      || taskHasTooManyDeletes(scanTask));
    }
  }

  /**
   * 将待重写文件按大小装箱分组，并过滤出值得重写的分组。
   *
   * <p>逻辑：用 {@link BinPacking.ListPacker} 按 {@code maxGroupSize} 装箱；若开启全量重写则
   * 直接返回所有分组；否则保留满足以下任一条件的分组：文件数达到 {@code minInputFiles} 且多于 1 个、 总大小超过目标大小且多于 1
   * 个文件、总大小超过最大文件大小、或含关联删除过多的文件。
   *
   * @param dataFiles 待重写文件
   * @return 待执行重写的文件分组列表
   */
  @Override
  public Iterable<List<FileScanTask>> planFileGroups(Iterable<FileScanTask> dataFiles) {
    ListPacker<FileScanTask> packer = new BinPacking.ListPacker<>(maxGroupSize, 1, false);
    List<List<FileScanTask>> potentialGroups = packer.pack(dataFiles, FileScanTask::length);
    if (rewriteAll) {
      return potentialGroups;
    } else {
      return potentialGroups.stream()
          .filter(
              group ->
                  (group.size() >= minInputFiles && group.size() > 1)
                      || (sizeOfInputFiles(group) > targetFileSize && group.size() > 1)
                      || sizeOfInputFiles(group) > maxFileSize
                      || group.stream().anyMatch(this::taskHasTooManyDeletes))
          .collect(Collectors.toList());
    }
  }

  /** 返回目标文件大小（字节）。 */
  protected long targetFileSize() {
    return this.targetFileSize;
  }

  /**
   * 计算重写时应产出的输出文件数量，用于确定写入时的切分大小，避免产生过小的残余文件。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>总量小于目标大小则产出 1 个文件；
   *   <li>按向上取整计算含余数的文件数，若余数大于 {@code minFileSize} 则保留余数文件；
   *   <li>否则按向下取整计算文件数，估算平均文件大小，若平均大小不超过目标大小的 110% 且不超过 {@link
   *       #writeMaxFileSize()}，则向下取整（把余数分摊到其他文件），否则保留余数文件。
   * </ul>
   *
   * @param totalSizeInBytes 文件分组的总数据大小
   * @return 应产出的文件数量
   */
  protected long numOutputFiles(long totalSizeInBytes) {
    if (totalSizeInBytes < targetFileSize) {
      return 1;
    }

    long fileCountWithRemainder =
        LongMath.divide(totalSizeInBytes, targetFileSize, RoundingMode.CEILING);
    if (LongMath.mod(totalSizeInBytes, targetFileSize) > minFileSize) {
      // Our Remainder file is of valid size for this compaction so keep it
      return fileCountWithRemainder;
    }

    long fileCountWithoutRemainder =
        LongMath.divide(totalSizeInBytes, targetFileSize, RoundingMode.FLOOR);
    long avgFileSizeWithoutRemainder = totalSizeInBytes / fileCountWithoutRemainder;
    if (avgFileSizeWithoutRemainder < Math.min(1.1 * targetFileSize, writeMaxFileSize())) {
      // Round down and distribute remainder amongst other files
      return fileCountWithoutRemainder;
    } else {
      // Keep the remainder file
      return fileCountWithRemainder;
    }
  }

  /**
   * 返回实际写入时使用的切分大小：取估算切分大小（总量除以输出文件数，再加少量开销）与 {@link #writeMaxFileSize()} 的较小值。
   *
   * <p>设计要点：加入 {@link #SPLIT_OVERHEAD} 开销以避免估算误差导致多产生一个新文件。
   *
   * @param totalSizeInBytes 文件分组总大小
   * @return 写入切分大小
   */
  protected long splitSize(long totalSizeInBytes) {
    long estimatedSplitSize =
        (totalSizeInBytes / numOutputFiles(totalSizeInBytes)) + SPLIT_OVERHEAD;
    return Math.min(estimatedSplitSize, writeMaxFileSize());
  }

  /** 返回给定文件列表的总长度（字节）。 */
  protected long inputFileSize(List<FileScanTask> fileToRewrite) {
    return fileToRewrite.stream().mapToLong(FileScanTask::length).sum();
  }

  /**
   * 估算一个略大于目标大小的写入文件上限，用于任务创建，避免序列化/压缩后实际数据略超目标大小而 产生微小残余文件。
   *
   * <p>设计意图：写入时数据因压缩、序列化等因素可能与预估大小有偏差，若严格按目标大小切分会 产生一个接近目标大小的文件加上一个很小的残余文件。使用本方法估算的更大上限可尽量合并为单文件。
   *
   * @return 目标大小加上（最大文件大小与目标大小之差的一半）
   */
  protected long writeMaxFileSize() {
    return (long) (targetFileSize + ((maxFileSize - targetFileSize) * 0.5));
  }

  /** 计算分组中所有文件的总长度。 */
  private long sizeOfInputFiles(List<FileScanTask> group) {
    return group.stream().mapToLong(FileScanTask::length).sum();
  }

  /** 判断扫描任务关联的删除文件数是否达到或超过阈值。 */
  private boolean taskHasTooManyDeletes(FileScanTask task) {
    return task.deletes() != null && task.deletes().size() >= deleteFileThreshold;
  }

  /**
   * 校验已解析的选项取值是否合法。
   *
   * <p>逻辑：检查最小文件大小非负、最大文件大小大于最小、目标大小介于二者之间、最小输入文件数与 删除阈值均大于 0；任一不满足则抛出 {@link
   * IllegalArgumentException}。
   */
  private void validateOptions() {
    Preconditions.checkArgument(
        minFileSize >= 0,
        "Cannot set %s to a negative number, %s < 0",
        MIN_FILE_SIZE_BYTES,
        minFileSize);

    Preconditions.checkArgument(
        maxFileSize > minFileSize,
        "Cannot set %s greater than or equal to %s, %s >= %s",
        MIN_FILE_SIZE_BYTES,
        MAX_FILE_SIZE_BYTES,
        minFileSize,
        maxFileSize);

    Preconditions.checkArgument(
        targetFileSize > minFileSize,
        "Cannot set %s greater than or equal to %s, all files written will be smaller than the threshold, %s >= %s",
        MIN_FILE_SIZE_BYTES,
        RewriteDataFiles.TARGET_FILE_SIZE_BYTES,
        minFileSize,
        targetFileSize);

    Preconditions.checkArgument(
        targetFileSize < maxFileSize,
        "Cannot set %s is greater than or equal to %s, all files written will be larger than the threshold, %s >= %s",
        RewriteDataFiles.TARGET_FILE_SIZE_BYTES,
        MAX_FILE_SIZE_BYTES,
        targetFileSize,
        maxFileSize);

    Preconditions.checkArgument(
        minInputFiles > 0,
        "Cannot set %s is less than 1. All values less than 1 have the same effect as 1. %s < 1",
        MIN_INPUT_FILES,
        minInputFiles);

    Preconditions.checkArgument(
        deleteFileThreshold > 0,
        "Cannot set %s is less than 1. All values less than 1 have the same effect as 1. %s < 1",
        DELETE_FILE_THRESHOLD,
        deleteFileThreshold);
  }
}
