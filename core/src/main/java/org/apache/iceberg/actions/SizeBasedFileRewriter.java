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
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.ContentScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.math.LongMath;
import org.apache.iceberg.util.BinPacking;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于文件大小的内容文件重写器。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据文件大小挑选重写目标：小于 {@link #MIN_FILE_SIZE_BYTES} 或大于 {@link #MAX_FILE_SIZE_BYTES} 的文件视为重写目标。
 *   <li>利用 {@link BinPacking} 装箱算法将选中文件按 {@link #MAX_FILE_GROUP_SIZE_BYTES} 分组；仅当 分组文件数超过 {@link
 *       #MIN_INPUT_FILES} 或可产出至少一个 {@link #TARGET_FILE_SIZE_BYTES} 大小的文件时才实际重写。
 *   <li>计算输出文件数与切分大小，避免产生过小残余文件。
 * </ul>
 *
 * <p>设计意图：提供大小阈值与装箱分组的通用实现，子类只需补充引擎相关的写入逻辑及额外的 选文件/过滤分组条件。各阈值以目标文件大小为基准按比例默认取值，支持配置覆盖。
 *
 * <p>上下游关系：实现 {@link FileRewriter}，被 {@link SizeBasedDataRewriter}、 {@link
 * SizeBasedPositionDeletesRewriter} 继承；由具体引擎的重写动作使用。
 */
public abstract class SizeBasedFileRewriter<T extends ContentScanTask<F>, F extends ContentFile<F>>
    implements FileRewriter<T, F> {

  private static final Logger LOG = LoggerFactory.getLogger(SizeBasedFileRewriter.class);

  /** 重写器尝试生成的目标输出文件大小。 */
  public static final String TARGET_FILE_SIZE_BYTES = "target-file-size-bytes";

  /**
   * 控制被视为重写目标的文件大小下限：小于该阈值的文件无论其他条件如何都会被纳入重写。
   *
   * <p>默认为目标文件大小的 75%。
   */
  public static final String MIN_FILE_SIZE_BYTES = "min-file-size-bytes";

  public static final double MIN_FILE_SIZE_DEFAULT_RATIO = 0.75;

  /**
   * 控制被视为重写目标的文件大小上限：大于该阈值的文件无论其他条件如何都会被纳入重写。
   *
   * <p>默认为目标文件大小的 180%。
   */
  public static final String MAX_FILE_SIZE_BYTES = "max-file-size-bytes";

  public static final double MAX_FILE_SIZE_DEFAULT_RATIO = 1.80;

  /** 文件数超过该阈值的分组无论其他条件如何都会被重写。确保文件数过多的分组即使总大小不足 目标文件大小也会被合并。也可理解为重写后一个分区中允许残留的尺寸不合理文件的最大数量。 */
  public static final String MIN_INPUT_FILES = "min-input-files";

  public static final int MIN_INPUT_FILES_DEFAULT = 5;

  /** 覆盖其他选项，强制重写所有提供的文件。 */
  public static final String REWRITE_ALL = "rewrite-all";

  public static final boolean REWRITE_ALL_DEFAULT = false;

  /** 控制单个文件组最大可重写数据量。用于将超大分区拆分为若干小段重写，避免因集群资源限制 无法整体重写（例如排序重写难以扩展到 TB 级分区，需分段处理以防资源耗尽）。 */
  public static final String MAX_FILE_GROUP_SIZE_BYTES = "max-file-group-size-bytes";

  public static final long MAX_FILE_GROUP_SIZE_BYTES_DEFAULT = 100L * 1024 * 1024 * 1024; // 100 GB

  private static final long SPLIT_OVERHEAD = 5 * 1024;

  private final Table table;
  private long targetFileSize;
  private long minFileSize;
  private long maxFileSize;
  private int minInputFiles;
  private boolean rewriteAll;
  private long maxGroupSize;

  /**
   * 构造基于大小的文件重写器。
   *
   * @param table 目标表
   */
  protected SizeBasedFileRewriter(Table table) {
    this.table = table;
  }

  /**
   * 返回默认目标文件大小，由子类按文件类型（数据/删除）提供。
   *
   * @return 默认目标文件大小（字节）
   */
  protected abstract long defaultTargetFileSize();

  /**
   * 子类实现的文件筛选逻辑，从扫描任务中选出需要重写的文件。
   *
   * @param tasks 待筛选的扫描任务
   * @return 需重写的扫描任务
   */
  protected abstract Iterable<T> filterFiles(Iterable<T> tasks);

  /**
   * 子类实现的文件分组过滤逻辑，从装箱结果中保留值得重写的分组。
   *
   * @param groups 装箱后的候选分组
   * @return 需重写的分组
   */
  protected abstract Iterable<List<T>> filterFileGroups(List<List<T>> groups);

  /** 返回目标表。 */
  protected Table table() {
    return table;
  }

  /** 返回本重写器支持的选项白名单。 */
  @Override
  public Set<String> validOptions() {
    return ImmutableSet.of(
        TARGET_FILE_SIZE_BYTES,
        MIN_FILE_SIZE_BYTES,
        MAX_FILE_SIZE_BYTES,
        MIN_INPUT_FILES,
        REWRITE_ALL,
        MAX_FILE_GROUP_SIZE_BYTES);
  }

  /**
   * 解析选项并初始化大小阈值、最小输入文件数、是否全量重写与最大分组大小等参数。
   *
   * @param options 选项键值对
   */
  @Override
  public void init(Map<String, String> options) {
    Map<String, Long> sizeThresholds = sizeThresholds(options);
    this.targetFileSize = sizeThresholds.get(TARGET_FILE_SIZE_BYTES);
    this.minFileSize = sizeThresholds.get(MIN_FILE_SIZE_BYTES);
    this.maxFileSize = sizeThresholds.get(MAX_FILE_SIZE_BYTES);

    this.minInputFiles = minInputFiles(options);
    this.rewriteAll = rewriteAll(options);
    this.maxGroupSize = maxGroupSize(options);

    if (rewriteAll) {
      LOG.info("Configured to rewrite all provided files in table {}", table.name());
    }
  }

  /** 判断任务对应文件大小是否不在 [minFileSize, maxFileSize] 区间内。 */
  protected boolean wronglySized(T task) {
    return task.length() < minFileSize || task.length() > maxFileSize;
  }

  /**
   * 规划文件分组：筛选文件后用 {@link BinPacking.ListPacker} 按 {@code maxGroupSize} 装箱， 再按子类逻辑过滤分组（全量重写时跳过筛选）。
   */
  @Override
  public Iterable<List<T>> planFileGroups(Iterable<T> tasks) {
    Iterable<T> filteredTasks = rewriteAll ? tasks : filterFiles(tasks);
    BinPacking.ListPacker<T> packer = new BinPacking.ListPacker<>(maxGroupSize, 1, false);
    List<List<T>> groups = packer.pack(filteredTasks, ContentScanTask::length);
    return rewriteAll ? groups : filterFileGroups(groups);
  }

  /** 判断分组文件数是否大于 1 且达到 {@code minInputFiles}。 */
  protected boolean enoughInputFiles(List<T> group) {
    return group.size() > 1 && group.size() >= minInputFiles;
  }

  /** 判断分组文件数是否大于 1 且总大小超过目标文件大小。 */
  protected boolean enoughContent(List<T> group) {
    return group.size() > 1 && inputSize(group) > targetFileSize;
  }

  /** 判断分组总大小是否超过最大文件大小。 */
  protected boolean tooMuchContent(List<T> group) {
    return inputSize(group) > maxFileSize;
  }

  /** 返回分组所有文件的总长度（字节）。 */
  protected long inputSize(List<T> group) {
    return group.stream().mapToLong(ContentScanTask::length).sum();
  }

  /** 返回写入切分大小：取估算切分大小（总量除以输出文件数再加开销）与 {@link #writeMaxFileSize()} 的较小值。加入开销以避免估算误差导致多产生新文件。 */
  protected long splitSize(long inputSize) {
    long estimatedSplitSize = (inputSize / numOutputFiles(inputSize)) + SPLIT_OVERHEAD;
    return Math.min(estimatedSplitSize, writeMaxFileSize());
  }

  /**
   * 计算重写一个文件分组时应产出的输出文件数量，避免产生过小的残余文件。
   *
   * <p>逻辑：以 10.1GB 数据、1GB 目标为例，向上取整得 11 个文件（含 0.1GB 残余），向下取整得 10 个 1.01GB
   * 文件。本方法根据把余数分摊到其他文件后的平均大小决定取舍：若余数大于 {@code minFileSize} 则保留余数文件；否则当平均大小不超过目标大小 110% 且不超过 {@link
   * #writeMaxFileSize()} 时向下取整，否则保留余数文件。
   *
   * @param inputSize 文件分组总大小
   * @return 应产出的文件数量
   */
  protected long numOutputFiles(long inputSize) {
    if (inputSize < targetFileSize) {
      return 1;
    }

    long numFilesWithRemainder = LongMath.divide(inputSize, targetFileSize, RoundingMode.CEILING);
    long numFilesWithoutRemainder = LongMath.divide(inputSize, targetFileSize, RoundingMode.FLOOR);
    long avgFileSizeWithoutRemainder = inputSize / numFilesWithoutRemainder;

    if (LongMath.mod(inputSize, targetFileSize) > minFileSize) {
      // the remainder file is of a valid size for this rewrite so keep it
      return numFilesWithRemainder;

    } else if (avgFileSizeWithoutRemainder < Math.min(1.1 * targetFileSize, writeMaxFileSize())) {
      // if the reminder is distributed amongst other files,
      // the average file size will be no more than 10% bigger than the target file size
      // so round down and distribute remainder amongst other files
      return numFilesWithoutRemainder;

    } else {
      // keep the remainder file as it is not OK to distribute it amongst other files
      return numFilesWithRemainder;
    }
  }

  /**
   * 估算一个略大于目标大小的写入文件上限，用于任务创建，避免序列化/压缩后实际数据略超目标 大小而产生微小残余文件。
   *
   * <p>设计意图：写入时数据因压缩、序列化等因素可能与预估大小有偏差，若严格按目标大小切分会 产生一个接近目标大小的文件加上一个很小的残余文件。使用本方法估算的更大上限可尽量合并为单文件。
   *
   * @return 目标大小加上（最大文件大小与目标大小之差的一半）
   */
  protected long writeMaxFileSize() {
    return (long) (targetFileSize + ((maxFileSize - targetFileSize) * 0.5));
  }

  /** 解析并校验目标/最小/最大文件大小阈值，返回阈值映射。 */
  private Map<String, Long> sizeThresholds(Map<String, String> options) {
    long target =
        PropertyUtil.propertyAsLong(options, TARGET_FILE_SIZE_BYTES, defaultTargetFileSize());

    long defaultMin = (long) (target * MIN_FILE_SIZE_DEFAULT_RATIO);
    long min = PropertyUtil.propertyAsLong(options, MIN_FILE_SIZE_BYTES, defaultMin);

    long defaultMax = (long) (target * MAX_FILE_SIZE_DEFAULT_RATIO);
    long max = PropertyUtil.propertyAsLong(options, MAX_FILE_SIZE_BYTES, defaultMax);

    Preconditions.checkArgument(
        target > 0, "'%s' is set to %s but must be > 0", TARGET_FILE_SIZE_BYTES, target);

    Preconditions.checkArgument(
        min >= 0, "'%s' is set to %s but must be >= 0", MIN_FILE_SIZE_BYTES, min);

    Preconditions.checkArgument(
        target > min,
        "'%s' (%s) must be > '%s' (%s), all new files will be smaller than the min threshold",
        TARGET_FILE_SIZE_BYTES,
        target,
        MIN_FILE_SIZE_BYTES,
        min);

    Preconditions.checkArgument(
        target < max,
        "'%s' (%s) must be < '%s' (%s), all new files will be larger than the max threshold",
        TARGET_FILE_SIZE_BYTES,
        target,
        MAX_FILE_SIZE_BYTES,
        max);

    Map<String, Long> values = Maps.newHashMap();

    values.put(TARGET_FILE_SIZE_BYTES, target);
    values.put(MIN_FILE_SIZE_BYTES, min);
    values.put(MAX_FILE_SIZE_BYTES, max);

    return values;
  }

  /** 解析并校验最小输入文件数。 */
  private int minInputFiles(Map<String, String> options) {
    int value = PropertyUtil.propertyAsInt(options, MIN_INPUT_FILES, MIN_INPUT_FILES_DEFAULT);
    Preconditions.checkArgument(
        value > 0, "'%s' is set to %s but must be > 0", MIN_INPUT_FILES, value);
    return value;
  }

  /** 解析并校验最大分组大小。 */
  private long maxGroupSize(Map<String, String> options) {
    long value =
        PropertyUtil.propertyAsLong(
            options, MAX_FILE_GROUP_SIZE_BYTES, MAX_FILE_GROUP_SIZE_BYTES_DEFAULT);
    Preconditions.checkArgument(
        value > 0, "'%s' is set to %s but must be > 0", MAX_FILE_GROUP_SIZE_BYTES, value);
    return value;
  }

  /** 解析是否全量重写。 */
  private boolean rewriteAll(Map<String, String> options) {
    return PropertyUtil.propertyAsBoolean(options, REWRITE_ALL, REWRITE_ALL_DEFAULT);
  }
}
