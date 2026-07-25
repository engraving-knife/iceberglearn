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
import org.immutables.value.Value;
import org.immutables.value.Value.Style.BuilderVisibility;
import org.immutables.value.Value.Style.ImplementationVisibility;

/**
 * 重写数据文件动作的基础接口（基于 Immutables 生成不可变实现）。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：为 {@link RewriteDataFiles} 动作定义 core 侧的基础契约及多个不可变结果/信息类型 （{@link Result}、{@link
 * FileGroupRewriteResult}、{@link FileGroupFailureResult}、{@link FileGroupInfo}）， 由 Immutables
 * 注解处理器生成 {@code ImmutableRewriteDataFiles} 实现类。
 *
 * <p>设计意图：通过 Immutables 的 {@code @Value.Style} 统一控制生成类可见性；各结果接口对父接口的 计数方法提供 {@code @Value.Default}
 * 默认实现，保证即使 builder 未显式设置也有缺省值，便于聚合统计。
 *
 * <p>上下游关系：实现 {@link RewriteDataFiles}（iceberg-api），被具体引擎模块的重写数据文件动作继承使用。
 */
@Value.Enclosing
@SuppressWarnings("ImmutablesStyle")
@Value.Style(
    typeImmutableEnclosing = "ImmutableRewriteDataFiles",
    visibility = ImplementationVisibility.PUBLIC,
    builderVisibility = BuilderVisibility.PUBLIC)
interface BaseRewriteDataFiles extends RewriteDataFiles {

  /**
   * 重写数据文件动作的不可变结果类型，继承 {@link RewriteDataFiles.Result}。
   *
   * <p>重写各计数方法并提供默认值，确保结果统计字段（新增/重写/失败文件数、重写字节数、失败列表）均有缺省值。
   */
  @Value.Immutable
  interface Result extends RewriteDataFiles.Result {
    @Override
    @Value.Default
    default List<RewriteDataFiles.FileGroupFailureResult> rewriteFailures() {
      return RewriteDataFiles.Result.super.rewriteFailures();
    }

    @Override
    @Value.Default
    default int addedDataFilesCount() {
      return RewriteDataFiles.Result.super.addedDataFilesCount();
    }

    @Override
    @Value.Default
    default int rewrittenDataFilesCount() {
      return RewriteDataFiles.Result.super.rewrittenDataFilesCount();
    }

    @Override
    @Value.Default
    default long rewrittenBytesCount() {
      return RewriteDataFiles.Result.super.rewrittenBytesCount();
    }

    @Override
    @Value.Default
    default int failedDataFilesCount() {
      return RewriteDataFiles.Result.super.failedDataFilesCount();
    }
  }

  /** 单个文件组重写结果的不可变类型，继承 {@link RewriteDataFiles.FileGroupRewriteResult}。 */
  @Value.Immutable
  interface FileGroupRewriteResult extends RewriteDataFiles.FileGroupRewriteResult {
    @Override
    @Value.Default
    default long rewrittenBytesCount() {
      return RewriteDataFiles.FileGroupRewriteResult.super.rewrittenBytesCount();
    }
  }

  /** 单个文件组重写失败结果的不可变类型，继承 {@link RewriteDataFiles.FileGroupFailureResult}。 */
  @Value.Immutable
  interface FileGroupFailureResult extends RewriteDataFiles.FileGroupFailureResult {}

  /** 单个文件组元信息的不可变类型，继承 {@link RewriteDataFiles.FileGroupInfo}。 */
  @Value.Immutable
  interface FileGroupInfo extends RewriteDataFiles.FileGroupInfo {}
}
