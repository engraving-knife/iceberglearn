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
import org.apache.iceberg.DataFile;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 旧版重写数据文件动作的结果对象。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：承载一次重写数据文件动作中被删除的旧数据文件列表与新添加的数据文件列表。
 *
 * <p>设计意图：作为 {@link BaseRewriteDataFilesAction} 的执行结果类型，采用简单可变字段持有结果， 供调用方获取变更明细。提供 {@link
 * #empty()} 空结果占位，表示无需重写。
 *
 * <p>上下游关系：由 {@link BaseRewriteDataFilesAction#execute()} 产生并返回；对应新版 API 结果见 {@link
 * RewriteDataFiles.Result}。
 */
public class RewriteDataFilesActionResult {

  /** 空结果单例，表示没有文件被重写。 */
  private static final RewriteDataFilesActionResult EMPTY =
      new RewriteDataFilesActionResult(ImmutableList.of(), ImmutableList.of());

  /** 重写后被删除的旧数据文件列表。 */
  private List<DataFile> deletedDataFiles;
  /** 重写后新增的数据文件列表。 */
  private List<DataFile> addedDataFiles;

  /**
   * 构造结果对象。
   *
   * @param deletedDataFiles 被删除的旧数据文件列表
   * @param addedDataFiles 新添加的数据文件列表
   */
  public RewriteDataFilesActionResult(
      List<DataFile> deletedDataFiles, List<DataFile> addedDataFiles) {
    this.deletedDataFiles = deletedDataFiles;
    this.addedDataFiles = addedDataFiles;
  }

  /** 返回空结果单例，表示没有文件被重写。 */
  static RewriteDataFilesActionResult empty() {
    return EMPTY;
  }

  /** 返回被删除的旧数据文件列表。 */
  public List<DataFile> deletedDataFiles() {
    return deletedDataFiles;
  }

  /** 返回新添加的数据文件列表。 */
  public List<DataFile> addedDataFiles() {
    return addedDataFiles;
  }
}
