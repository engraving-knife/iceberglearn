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
package org.apache.iceberg.data;

import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;

/**
 * 面向 {@link Record} 的删除过滤器实现：把 {@link DeleteFilter} 的抽象钩子绑定到 通用 Record 模型与 {@link FileIO}。
 *
 * <p>所属模块：iceberg-data（向 JVM 应用提供基于 {@link Record} 等通用模型的 Iceberg 表读写支持； 本类是读路径上“通用
 * Record”场景的删除过滤具体实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为单个 {@link FileScanTask} 构造删除过滤器，传入数据文件路径与关联删除文件。
 *   <li>实现 {@link DeleteFilter#asStructLike}：用 {@link InternalRecordWrapper} 把 {@link Record} 包装为
 *       {@link StructLike}，供等值删除集合判定。
 *   <li>实现 {@link DeleteFilter#getInputFile}：通过 {@link FileIO} 按 location 打开删除文件。
 *   <li>重写 {@link DeleteFilter#pos}：直接从 Record 读取 {@code _pos}（Record 已实现 StructLike）。
 * </ul>
 *
 * <p>设计意图：作为 {@link DeleteFilter} 与通用 Record 读取路径之间的薄适配层， 把“如何拿到 InputFile”与“如何把 Record 当
 * StructLike 用”这两个引擎相关决策收敛到此处， 使 {@link DeleteFilter} 的核心过滤逻辑保持引擎无关。
 *
 * <p>上下游关系：被 {@link GenericReader#open(FileScanTask)} 创建并使用；依赖 {@link FileIO}（来自表）与 {@link
 * InternalRecordWrapper}。
 */
public class GenericDeleteFilter extends DeleteFilter<Record> {
  private final FileIO io;
  private final InternalRecordWrapper asStructLike;

  /**
   * 构造通用 Record 删除过滤器。
   *
   * <p>逻辑：调用父类构造完成删除文件拆分与 requiredSchema 计算；保存 FileIO； 基于 requiredSchema 创建 {@link
   * InternalRecordWrapper}（用于把 Record 转为 StructLike）。
   *
   * @param io 文件 IO，用于打开删除文件
   * @param task 当前文件扫描任务（提供数据文件路径与删除文件列表）
   * @param tableSchema 表 Schema
   * @param requestedSchema 用户请求的投影 Schema
   */
  public GenericDeleteFilter(
      FileIO io, FileScanTask task, Schema tableSchema, Schema requestedSchema) {
    super(task.file().path().toString(), task.deletes(), tableSchema, requestedSchema);
    this.io = io;
    this.asStructLike = new InternalRecordWrapper(requiredSchema().asStruct());
  }

  /**
   * 取 Record 在数据文件中的行号。
   *
   * <p>重写父类默认实现：Record 自身即 {@link StructLike}，故直接用 posAccessor 读取， 无需再经 InternalRecordWrapper 包装。
   *
   * @param record 通用 Record
   * @return 行号
   */
  @Override
  protected long pos(Record record) {
    return (Long) posAccessor().get(record);
  }

  /**
   * 把 Record 包装为 {@link StructLike}，用于等值删除判定。
   *
   * @param record 通用 Record
   * @return 已包装的 StructLike 视图
   */
  @Override
  protected StructLike asStructLike(Record record) {
    return asStructLike.wrap(record);
  }

  /**
   * 按 location 返回删除文件对应的 {@link InputFile}。
   *
   * @param location 删除文件路径
   * @return InputFile
   */
  @Override
  protected InputFile getInputFile(String location) {
    return io.newInputFile(location);
  }
}
