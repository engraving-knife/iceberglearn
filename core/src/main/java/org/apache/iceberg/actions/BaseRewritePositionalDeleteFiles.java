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

import org.immutables.value.Value;
import org.immutables.value.Value.Style.BuilderVisibility;
import org.immutables.value.Value.Style.ImplementationVisibility;

/**
 * 重写位置删除文件动作的基础接口（基于 Immutables 生成不可变实现）。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：为 {@link RewritePositionDeleteFiles} 动作定义 core 侧的基础契约及多个不可变结果/信息类型 （{@link Result}、{@link
 * FileGroupRewriteResult}、{@link FileGroupInfo}）， 由 Immutables 注解处理器生成 {@code
 * ImmutableRewritePositionDeleteFiles} 实现类。
 *
 * <p>设计意图：通过 Immutables 的 {@code @Value.Style} 统一控制生成类可见性；Result 接口对父接口的计数方法提供
 * {@code @Value.Default} 默认实现，保证即使 builder 未显式设置也有缺省值。位置删除文件（positional delete）用于标记被删除的行位置。
 *
 * <p>上下游关系：实现 {@link RewritePositionDeleteFiles}（iceberg-api），被具体引擎模块的重写位置删除文件动作继承使用。
 */
@Value.Enclosing
@SuppressWarnings("ImmutablesStyle")
@Value.Style(
    typeImmutableEnclosing = "ImmutableRewritePositionDeleteFiles",
    visibility = ImplementationVisibility.PUBLIC,
    builderVisibility = BuilderVisibility.PUBLIC)
interface BaseRewritePositionalDeleteFiles extends RewritePositionDeleteFiles {

  /**
   * 重写位置删除文件动作的不可变结果类型，继承 {@link RewritePositionDeleteFiles.Result}。
   *
   * <p>重写各计数方法并提供默认值，确保结果统计字段（重写/新增删除文件数、字节数）均有缺省值。
   */
  @Value.Immutable
  interface Result extends RewritePositionDeleteFiles.Result {
    @Override
    @Value.Default
    default int rewrittenDeleteFilesCount() {
      return RewritePositionDeleteFiles.Result.super.rewrittenDeleteFilesCount();
    }

    @Override
    @Value.Default
    default int addedDeleteFilesCount() {
      return RewritePositionDeleteFiles.Result.super.addedDeleteFilesCount();
    }

    @Override
    @Value.Default
    default long rewrittenBytesCount() {
      return RewritePositionDeleteFiles.Result.super.rewrittenBytesCount();
    }

    @Override
    @Value.Default
    default long addedBytesCount() {
      return RewritePositionDeleteFiles.Result.super.addedBytesCount();
    }
  }

  /** 单个文件组重写结果的不可变类型，继承 {@link RewritePositionDeleteFiles.FileGroupRewriteResult}。 */
  @Value.Immutable
  interface FileGroupRewriteResult extends RewritePositionDeleteFiles.FileGroupRewriteResult {}

  /** 单个文件组元信息的不可变类型，继承 {@link RewritePositionDeleteFiles.FileGroupInfo}。 */
  @Value.Immutable
  interface FileGroupInfo extends RewritePositionDeleteFiles.FileGroupInfo {}
}
