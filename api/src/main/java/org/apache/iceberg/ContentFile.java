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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * 文件级说明：内容文件（{@link DataFile} 与 {@link DeleteFile}）的公共父接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 模块实现具体的数据/删除文件类）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>抽象数据文件与删除文件共有的元信息访问能力：路径、格式、分区、记录数、文件大小、 列级统计（上下界、值计数等）、加密元数据、分片偏移、序列号等。
 *   <li>提供文件拷贝能力（含/不含统计信息），以支持 manifest 读取器对文件实例的复用与防御性拷贝。
 * </ul>
 *
 * <p>设计意图：通过统一父接口，扫描规划、统计收集、缓存等通用逻辑可同时处理数据文件与删除文件， 避免重复代码。泛型参数 {@code <F>} 用于让 {@link #copy()}
 * 等方法返回具体的文件类型， 保持类型安全。
 *
 * <p>上下游关系：被 {@link ManifestFile}、{@link FileScanTask}、{@link ContentScanTask} 等 普遍引用；由 core 模块的
 * {@code GenericDataFile}/{@code GenericDeleteFile} 等实现。
 *
 * @param <F> 具体文件类型的 Java 类（如 {@link DataFile}）
 */
public interface ContentFile<F> {
  /**
   * 返回该文件在 manifest 中的序号位置；若该文件并非从 manifest 读取，则返回 null。
   *
   * @return manifest 中的序号，或 null
   */
  Long pos();

  /** 返回该文件分区元信息所使用的 {@link PartitionSpec} 的 ID。 */
  int specId();

  /**
   * 返回文件存储的内容类型：{@link FileContent#DATA}、{@link FileContent#POSITION_DELETES} 或 {@link
   * FileContent#EQUALITY_DELETES}。
   */
  FileContent content();

  /** 返回文件的完全限定路径，可直接用于构造 Hadoop {@code Path}。 */
  CharSequence path();

  /** 返回文件格式（如 Parquet、ORC、Avro）。 */
  FileFormat format();

  /** 返回该文件所属的分区，以 {@link StructLike} 形式表示。 */
  StructLike partition();

  /** 返回文件中顶层记录的总数。 */
  long recordCount();

  /** 返回文件大小（字节数）。 */
  long fileSizeInBytes();

  /** 返回按列 ID 到该列字节数的映射；若未收集则返回 null。 */
  Map<Integer, Long> columnSizes();

  /**
   * 返回按列 ID 到该列值计数（包含 null 与 NaN）的映射；若未收集则返回 null。
   *
   * @return 列值计数映射，或 null
   */
  Map<Integer, Long> valueCounts();

  /** 返回按列 ID 到该列 null 值计数的映射；若未收集则返回 null。 */
  Map<Integer, Long> nullValueCounts();

  /** 返回按列 ID 到该列 NaN 值计数的映射；若未收集则返回 null。 */
  Map<Integer, Long> nanValueCounts();

  /** 返回按列 ID 到该列值下界的映射；若未收集则返回 null。 */
  Map<Integer, ByteBuffer> lowerBounds();

  /** 返回按列 ID 到该列值上界的映射；若未收集则返回 null。 */
  Map<Integer, ByteBuffer> upperBounds();

  /** 返回该文件的加密元数据；若文件为明文存储则返回 null。 */
  ByteBuffer keyMetadata();

  /**
   * 返回推荐的分片偏移位置列表；若不适用则返回 null。
   *
   * <p>当可用时，该信息用于规划扫描任务，以这些偏移作为任务边界。返回列表必须升序排列。
   *
   * @return 分片偏移列表，或 null
   */
  List<Long> splitOffsets();

  /**
   * 返回 equality delete 文件中用于等值比较的字段 ID 集合。
   *
   * <p>equality delete 文件可能包含未参与等值比较的额外数据字段。参与等值比较的列子集通过 ID 跟踪。额外列可用于重建变更，且其统计信息会在作业规划阶段使用。
   *
   * @return 与本删除文件记录进行等值比较的字段 ID 列表
   */
  List<Integer> equalityFieldIds();

  /**
   * 返回该文件的排序序号 ID，描述文件的排序方式。
   *
   * <p>当数据文件与 equality delete 文件共享同一排序序号 ID 时，可更高效地合并，此信息用于优化合并。
   *
   * @return 排序序号 ID，默认 null 表示未排序或未知
   */
  default Integer sortOrderId() {
    return null;
  }

  /**
   * 返回该文件的数据序列号。
   *
   * <p>数据序列号表示该文件应当应用的序列号。注意：数据序列号可能与文件被添加时所在快照的 序列号（即文件序列号）不同。新快照可以添加属于更早序列号的文件（例如 compaction 产物）。
   * 当文件被标记为删除时，数据序列号也不变。
   *
   * <p>若数据序列号未知，返回 null。可能出现在读取旧版 v2 manifest 且 manifest 条目状态为 DELETED 且未持久化数据序列号的情况（旧版 Iceberg）。
   *
   * @return 数据序列号，或 null
   */
  default Long dataSequenceNumber() {
    return null;
  }

  /**
   * 返回该文件的文件序列号。
   *
   * <p>文件序列号表示文件被添加时所在快照的序列号。文件序列号总是在提交时分配，不能显式指定 （这一点与数据序列号不同）。文件序列号一经分配不再改变。在重写（如 compaction）场景下，
   * 文件序列号可能高于数据序列号。
   *
   * <p>若文件序列号未知，返回 null。可能出现在读取旧版 v2 manifest 且 manifest 条目状态为 EXISTING 或 DELETED 且未持久化文件序列号的情况（旧版
   * Iceberg）。
   *
   * @return 文件序列号，或 null
   */
  default Long fileSequenceNumber() {
    return null;
  }

  /**
   * 拷贝本文件。manifest 读取器可能复用文件实例，从任务中收集文件时应使用本方法做防御性拷贝。
   *
   * @return 本数据文件的拷贝
   */
  F copy();

  /**
   * 拷贝本文件但不包含文件统计信息。manifest 读取器可能复用文件实例，收集文件时若无需统计信息 可使用本方法以减少内存占用。
   *
   * @return 本数据文件的拷贝（不含上下界、值计数、null 计数、NaN 计数等统计）
   */
  F copyWithoutStats();

  /**
   * 拷贝本文件，可选择是否保留统计信息。manifest 读取器可能复用文件实例，从任务收集文件时可使用本方法。
   *
   * @param withStats 为 {@code false} 时拷贝结果不含统计信息
   * @return 本数据文件的拷贝；若 {@code withStats} 为 {@code false}，则不含上下界、值计数、 null 计数、NaN 计数等统计
   */
  default F copy(boolean withStats) {
    return withStats ? copy() : copyWithoutStats();
  }
}
