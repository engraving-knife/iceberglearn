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
package org.apache.iceberg;

import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Projections;

/**
 * 从表中删除文件的 API。
 *
 * <p>所属模块：iceberg-api（表更新操作接口层）。
 *
 * <p>职责：累积文件删除操作，生成新的 {@link Snapshot} 并提交为当前快照。支持按文件路径、 按 {@link DataFile}、按行级 {@link Expression}
 * 三种方式删除。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>提交时将这些变更应用到最新的表快照；若发生提交冲突，则将变更应用到新的最新快照 并重试，从而实现 OCC（乐观并发控制）语义。
 *   <li>按行表达式删除时采用两次投影：用 {@link Projections#inclusive(PartitionSpec)} 选出 可能含匹配行的候选文件，再用 {@link
 *       Projections#strict(PartitionSpec)} 判断文件是否 全部行都匹配，确保仅当文件全部行必然匹配时才删除该文件。
 * </ul>
 *
 * <p>上下游关系：继承 {@link SnapshotUpdate}；由 core 模块实现，被引擎/用户调用以删除数据。
 */
public interface DeleteFiles extends SnapshotUpdate<DeleteFiles> {
  /**
   * 按完全匹配的文件路径从表中删除文件。
   *
   * <p>设计要点：路径必须与表元数据中的路径完全相等，仅等价但不相同的路径不会被删除。 例如 {@code file:/path/file.avro} 与 {@code
   * file:///path/file.avro} 等价但不会被相互 匹配删除。
   *
   * @param path 待删除文件的完全限定路径
   * @return this，便于链式调用
   */
  DeleteFiles deleteFile(CharSequence path);

  /**
   * 按 {@link DataFile} 删除其对应路径的文件。
   *
   * <p>默认实现：直接委托给 {@link #deleteFile(CharSequence)}，传入文件的 path。
   *
   * @param file 待删除的 DataFile
   * @return this，便于链式调用
   */
  default DeleteFiles deleteFile(DataFile file) {
    deleteFile(file.path());
    return this;
  }

  /**
   * 按行级 {@link Expression} 删除匹配的数据文件。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>先用 {@link Projections#inclusive(PartitionSpec)} 把行表达式投影到分区级，选出 可能含匹配行的候选文件；
   *   <li>再用 {@link Projections#strict(PartitionSpec)} 判断文件分区数据是否使整个文件 必然全部匹配，若是则删除该文件。
   * </ul>
   *
   * <p>若某文件可能同时包含匹配与不匹配的行，则抛出 {@link ValidationException}，避免 误删部分数据。
   *
   * @param expr 作用于表行的表达式
   * @return this，便于链式调用
   * @throws ValidationException 若某文件可能同时包含匹配与不匹配的行
   */
  DeleteFiles deleteFromRowFilter(Expression expr);

  /**
   * 启用或关闭表达式绑定的大小写敏感。
   *
   * @param caseSensitive 表达式绑定是否大小写敏感
   * @return this，便于链式调用
   */
  DeleteFiles caseSensitive(boolean caseSensitive);

  /**
   * 启用校验：提交时确认本次删除涉及的文件仍然存在。
   *
   * <p>默认实现：抛出 {@link UnsupportedOperationException}，由具体实现类覆盖。
   *
   * @return this，便于链式调用
   */
  default DeleteFiles validateFilesExist() {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement validateFilesExist");
  }
}
