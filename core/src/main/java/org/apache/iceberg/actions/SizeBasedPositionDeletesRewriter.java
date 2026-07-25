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
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.PositionDeletesScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.util.PropertyUtil;

/**
 * 基于大小的位置删除文件重写器。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：在 {@link SizeBasedFileRewriter} 基础上针对位置删除文件实现选文件与分组过滤逻辑。
 *
 * <p>设计意图：位置删除文件的重写只需考虑大小因素，不需关注关联删除（其本身即删除文件）， 因此仅按尺寸不合理筛选文件、按文件数/内容量过滤分组。
 *
 * <p>上下游关系：继承 {@link SizeBasedFileRewriter}，被具体引擎的位置删除文件重写实现继承。
 */
public abstract class SizeBasedPositionDeletesRewriter
    extends SizeBasedFileRewriter<PositionDeletesScanTask, DeleteFile> {

  /**
   * 构造位置删除文件重写器。
   *
   * @param table 目标表
   */
  protected SizeBasedPositionDeletesRewriter(Table table) {
    super(table);
  }

  /** 筛选出尺寸不合理的位置删除文件扫描任务。 */
  @Override
  protected Iterable<PositionDeletesScanTask> filterFiles(Iterable<PositionDeletesScanTask> tasks) {
    return Iterables.filter(tasks, this::wronglySized);
  }

  /** 过滤出值得重写的位置删除文件分组。 */
  @Override
  protected Iterable<List<PositionDeletesScanTask>> filterFileGroups(
      List<List<PositionDeletesScanTask>> groups) {
    return Iterables.filter(groups, this::shouldRewrite);
  }

  /** 判断分组是否应重写：文件数足够、内容足够或内容过多。 */
  private boolean shouldRewrite(List<PositionDeletesScanTask> group) {
    return enoughInputFiles(group) || enoughContent(group) || tooMuchContent(group);
  }

  /** 返回默认目标文件大小，取自表属性 DELETE_TARGET_FILE_SIZE_BYTES。 */
  @Override
  protected long defaultTargetFileSize() {
    return PropertyUtil.propertyAsLong(
        table().properties(),
        TableProperties.DELETE_TARGET_FILE_SIZE_BYTES,
        TableProperties.DELETE_TARGET_FILE_SIZE_BYTES_DEFAULT);
  }
}
