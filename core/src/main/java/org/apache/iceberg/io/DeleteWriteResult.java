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
package org.apache.iceberg.io;

import java.util.Collections;
import java.util.List;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.util.CharSequenceSet;

/**
 * 文件级说明：删除文件写入结果。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：封装一次删除写入操作产生的 {@link DeleteFile} 列表，以及这些删除文件所引用的 数据文件路径集合（referencedDataFiles）。
 *
 * <p>设计意图：position-delete 文件需要记录它删除的是哪些数据文件中的行，因此结果中除了删除文件 列表外还携带被引用数据文件路径集合，供提交时用于 CDC
 * 等场景。equality-delete 不引用具体数据文件。 与 {@link DataWriteResult} 一样，本类不可序列化，由上层包装为 {@link WriteResult}
 * 回传引擎。
 *
 * <p>上下游关系：由 {@link RollingPositionDeleteWriter}、{@link RollingEqualityDeleteWriter}、 {@link
 * SortedPosDeleteWriter} 等产生；由 {@link BasePositionDeltaWriter} 聚合。
 */
public class DeleteWriteResult {
  private final List<DeleteFile> deleteFiles;
  private final CharSequenceSet referencedDataFiles;

  /** 单删除文件构造器，无引用数据文件。 */
  public DeleteWriteResult(DeleteFile deleteFile) {
    this.deleteFiles = Collections.singletonList(deleteFile);
    this.referencedDataFiles = CharSequenceSet.empty();
  }

  /** 单删除文件构造器，带引用数据文件集合。 */
  public DeleteWriteResult(DeleteFile deleteFile, CharSequenceSet referencedDataFiles) {
    this.deleteFiles = Collections.singletonList(deleteFile);
    this.referencedDataFiles = referencedDataFiles;
  }

  /** 多删除文件构造器，无引用数据文件。 */
  public DeleteWriteResult(List<DeleteFile> deleteFiles) {
    this.deleteFiles = deleteFiles;
    this.referencedDataFiles = CharSequenceSet.empty();
  }

  /** 多删除文件构造器，带引用数据文件集合。 */
  public DeleteWriteResult(List<DeleteFile> deleteFiles, CharSequenceSet referencedDataFiles) {
    this.deleteFiles = deleteFiles;
    this.referencedDataFiles = referencedDataFiles;
  }

  /** 返回本次写入产生的删除文件列表。 */
  public List<DeleteFile> deleteFiles() {
    return deleteFiles;
  }

  /** 返回被这些删除文件引用的数据文件路径集合。 */
  public CharSequenceSet referencedDataFiles() {
    return referencedDataFiles;
  }

  /** 判断是否引用了数据文件（position-delete 通常为 true，equality-delete 为 false）。 */
  public boolean referencesDataFiles() {
    return referencedDataFiles != null && referencedDataFiles.size() > 0;
  }
}
