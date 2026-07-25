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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.util.PropertyUtil;

/**
 * 基于大小的数据文件重写器。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：在 {@link SizeBasedFileRewriter} 基础上针对数据文件实现选文件与分组过滤逻辑， 额外支持按关联删除文件数量阈值挑选需要重写的文件。
 *
 * <p>设计意图：数据文件重写除大小因素外，还需考虑关联删除文件过多的情况（删除过多会降低读性能）， 故扩展 {@code DELETE_FILE_THRESHOLD}
 * 选项，将此类文件也纳入重写目标。
 *
 * <p>上下游关系：继承 {@link SizeBasedFileRewriter}，被具体引擎的数据文件重写实现继承。
 */
public abstract class SizeBasedDataRewriter extends SizeBasedFileRewriter<FileScanTask, DataFile> {

  /**
   * 数据文件关联的删除文件数量阈值：当某数据文件关联的删除文件数达到或超过此值时，无论其大小是否 落在 {@link #MIN_FILE_SIZE_BYTES}/{@link
   * #MAX_FILE_SIZE_BYTES} 区间内都会被重写；且包含此类 文件的分组将无视 {@link #MIN_INPUT_FILES} 限制直接重写。
   *
   * <p>默认为 {@link Integer#MAX_VALUE}，即默认不启用该特性。
   */
  public static final String DELETE_FILE_THRESHOLD = "delete-file-threshold";

  public static final int DELETE_FILE_THRESHOLD_DEFAULT = Integer.MAX_VALUE;

  private int deleteFileThreshold;

  /**
   * 构造数据文件重写器。
   *
   * @param table 目标表
   */
  protected SizeBasedDataRewriter(Table table) {
    super(table);
  }

  /** 返回本重写器支持的选项白名单（含父类选项与删除文件阈值）。 */
  @Override
  public Set<String> validOptions() {
    return ImmutableSet.<String>builder()
        .addAll(super.validOptions())
        .add(DELETE_FILE_THRESHOLD)
        .build();
  }

  /** 初始化：先调用父类初始化，再解析删除文件阈值。 */
  @Override
  public void init(Map<String, String> options) {
    super.init(options);
    this.deleteFileThreshold = deleteFileThreshold(options);
  }

  /** 筛选出尺寸不合理或关联删除过多的数据文件扫描任务。 */
  @Override
  protected Iterable<FileScanTask> filterFiles(Iterable<FileScanTask> tasks) {
    return Iterables.filter(tasks, task -> wronglySized(task) || tooManyDeletes(task));
  }

  /** 判断扫描任务关联的删除文件数是否达到或超过阈值。 */
  private boolean tooManyDeletes(FileScanTask task) {
    return task.deletes() != null && task.deletes().size() >= deleteFileThreshold;
  }

  /** 过滤出值得重写的数据文件分组。 */
  @Override
  protected Iterable<List<FileScanTask>> filterFileGroups(List<List<FileScanTask>> groups) {
    return Iterables.filter(groups, this::shouldRewrite);
  }

  /** 判断分组是否应重写：文件数足够、内容足够、内容过多或含关联删除过多的文件。 */
  private boolean shouldRewrite(List<FileScanTask> group) {
    return enoughInputFiles(group)
        || enoughContent(group)
        || tooMuchContent(group)
        || anyTaskHasTooManyDeletes(group);
  }

  /** 判断分组中是否存在关联删除过多的任务。 */
  private boolean anyTaskHasTooManyDeletes(List<FileScanTask> group) {
    return group.stream().anyMatch(this::tooManyDeletes);
  }

  /** 返回默认目标文件大小，取自表属性 WRITE_TARGET_FILE_SIZE_BYTES。 */
  @Override
  protected long defaultTargetFileSize() {
    return PropertyUtil.propertyAsLong(
        table().properties(),
        TableProperties.WRITE_TARGET_FILE_SIZE_BYTES,
        TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT);
  }

  /** 解析并校验删除文件阈值。 */
  private int deleteFileThreshold(Map<String, String> options) {
    int value =
        PropertyUtil.propertyAsInt(options, DELETE_FILE_THRESHOLD, DELETE_FILE_THRESHOLD_DEFAULT);
    Preconditions.checkArgument(
        value >= 0, "'%s' is set to %s but must be >= 0", DELETE_FILE_THRESHOLD, value);
    return value;
  }
}
