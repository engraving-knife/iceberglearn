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

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.CharSequenceSet;

/**
 * 文件级说明：任务写入的最终可序列化结果。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：封装一个写入任务产生的全部数据文件（{@link DataFile}）、删除文件（{@link DeleteFile}）
 * 以及被删除文件引用的数据文件路径（referencedDataFiles），作为 TaskWriter/PositionDeltaWriter 的最终输出。
 *
 * <p>设计意图：实现了 {@link Serializable}，可跨 JVM 边界传输（如 Spark executor -> driver）。 内部使用数组而非 List
 * 以减小序列化体积。通过 Builder 模式聚合多个子写入器的结果， 支持增量 add 和批量 addAll。
 *
 * <p>上下游关系：由 {@link BaseTaskWriter#complete()}、{@link BasePositionDeltaWriter#result()}
 * 构建；被引擎集成层（Spark/Flink）的 commit 协议消费，用于提交文件到 Iceberg 表元数据。
 */
public class WriteResult implements Serializable {
  private DataFile[] dataFiles;
  private DeleteFile[] deleteFiles;
  private CharSequence[] referencedDataFiles;

  private WriteResult(
      List<DataFile> dataFiles, List<DeleteFile> deleteFiles, CharSequenceSet referencedDataFiles) {
    this.dataFiles = dataFiles.toArray(new DataFile[0]);
    this.deleteFiles = deleteFiles.toArray(new DeleteFile[0]);
    this.referencedDataFiles = referencedDataFiles.toArray(new CharSequence[0]);
  }

  /** 返回本次写入产生的数据文件数组。 */
  public DataFile[] dataFiles() {
    return dataFiles;
  }

  /** 返回本次写入产生的删除文件数组。 */
  public DeleteFile[] deleteFiles() {
    return deleteFiles;
  }

  /** 返回被删除文件引用的数据文件路径数组。 */
  public CharSequence[] referencedDataFiles() {
    return referencedDataFiles;
  }

  /** 创建 Builder 实例。 */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * WriteResult 构建器，支持聚合多个写入结果。
   *
   * <p>设计意图：一个写入任务可能包含多个子写入器（如 data writer + delete writer）， Builder 提供统一入口将各子结果合并为最终 WriteResult。
   */
  public static class Builder {
    private final List<DataFile> dataFiles;
    private final List<DeleteFile> deleteFiles;
    private final CharSequenceSet referencedDataFiles;

    private Builder() {
      this.dataFiles = Lists.newArrayList();
      this.deleteFiles = Lists.newArrayList();
      this.referencedDataFiles = CharSequenceSet.empty();
    }

    /**
     * 合并另一个 WriteResult 的全部内容到当前 Builder。
     *
     * @param result 待合并的结果
     * @return this，便于链式调用
     */
    public Builder add(WriteResult result) {
      addDataFiles(result.dataFiles);
      addDeleteFiles(result.deleteFiles);
      addReferencedDataFiles(result.referencedDataFiles);

      return this;
    }

    /**
     * 批量合并多个 WriteResult。
     *
     * @param results 待合并的结果集合
     * @return this，便于链式调用
     */
    public Builder addAll(Iterable<WriteResult> results) {
      results.forEach(this::add);
      return this;
    }

    /** 添加数据文件（变长参数）。 */
    public Builder addDataFiles(DataFile... files) {
      Collections.addAll(dataFiles, files);
      return this;
    }

    /** 添加数据文件（迭代器）。 */
    public Builder addDataFiles(Iterable<DataFile> files) {
      Iterables.addAll(dataFiles, files);
      return this;
    }

    /** 添加删除文件（变长参数）。 */
    public Builder addDeleteFiles(DeleteFile... files) {
      Collections.addAll(deleteFiles, files);
      return this;
    }

    /** 添加删除文件（迭代器）。 */
    public Builder addDeleteFiles(Iterable<DeleteFile> files) {
      Iterables.addAll(deleteFiles, files);
      return this;
    }

    /** 添加被引用的数据文件路径（变长参数）。 */
    public Builder addReferencedDataFiles(CharSequence... files) {
      Collections.addAll(referencedDataFiles, files);
      return this;
    }

    /** 添加被引用的数据文件路径（迭代器）。 */
    public Builder addReferencedDataFiles(Iterable<CharSequence> files) {
      Iterables.addAll(referencedDataFiles, files);
      return this;
    }

    /** 构建最终的 WriteResult。 */
    public WriteResult build() {
      return new WriteResult(dataFiles, deleteFiles, referencedDataFiles);
    }
  }
}
