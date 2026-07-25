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
package org.apache.iceberg.deletes;

import static org.apache.iceberg.MetadataColumns.DELETE_FILE_PATH;
import static org.apache.iceberg.MetadataColumns.DELETE_FILE_POS;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileMetadata;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsUtil;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.encryption.EncryptionKeyMetadata;
import org.apache.iceberg.io.DeleteWriteResult;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.FileWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.util.CharSequenceSet;

/**
 * 位置删除文件写入器：处理已按文件路径与行位置排序的位置删除记录。
 *
 * <p>所属模块：iceberg-core，deletes 包内删除文件写入的实现之一。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>接收 {@link PositionDelete} 记录并委托底层 {@link FileAppender} 写入。
 *   <li>跟踪本删除文件所引用的数据文件集合（referencedDataFiles）。
 *   <li>close 时产出位置删除文件元数据 {@link DeleteFile}，并按引用数据文件数量调整指标。
 * </ul>
 *
 * <p>设计意图：Iceberg 规范要求位置删除文件内的记录必须按文件路径与行位置排序，本写入器 假设上游已保证该排序，自身不做重排，因此性能开销最低。若上游无法保证排序，应改用 {@link
 * SortingPositionOnlyDeleteWriter} 在内存中重排后再写入。当引用多个数据文件时， path 字段的计数与边界统计会失真（不同文件路径混合），故通过 {@link
 * #metrics()} 去除这些统计， 仅当引用单个数据文件时保留字段边界以利于读取侧裁剪。
 *
 * <p>上下游关系：依赖 {@link FileAppender}、{@link FileMetadata}、{@link MetricsUtil}； 被任务写入流程调用，产出 {@link
 * DeleteWriteResult}（含删除文件元数据与引用数据文件集合）。
 *
 * @param <T> 被删除行数据的类型
 */
public class PositionDeleteWriter<T> implements FileWriter<PositionDelete<T>, DeleteWriteResult> {
  private static final Set<Integer> FILE_AND_POS_FIELD_IDS =
      ImmutableSet.of(DELETE_FILE_PATH.fieldId(), DELETE_FILE_POS.fieldId());

  private final FileAppender<StructLike> appender;
  private final FileFormat format;
  private final String location;
  private final PartitionSpec spec;
  private final StructLike partition;
  private final ByteBuffer keyMetadata;
  private final CharSequenceSet referencedDataFiles;
  private DeleteFile deleteFile = null;

  /**
   * 构造位置删除写入器。
   *
   * @param appender 底层列式追加器，写入 StructLike 行
   * @param format 文件格式
   * @param location 删除文件输出路径
   * @param spec 分区规格
   * @param partition 分区值
   * @param keyMetadata 加密密钥元数据，可为 null
   */
  public PositionDeleteWriter(
      FileAppender<StructLike> appender,
      FileFormat format,
      String location,
      PartitionSpec spec,
      StructLike partition,
      EncryptionKeyMetadata keyMetadata) {
    this.appender = appender;
    this.format = format;
    this.location = location;
    this.spec = spec;
    this.partition = partition;
    this.keyMetadata = keyMetadata != null ? keyMetadata.buffer() : null;
    this.referencedDataFiles = CharSequenceSet.empty();
  }

  /**
   * 写入一条位置删除记录。
   *
   * <p>逻辑：先将记录的数据文件路径加入引用集合，再委托 appender 追加写入。
   *
   * @param positionDelete 位置删除记录
   */
  @Override
  public void write(PositionDelete<T> positionDelete) {
    referencedDataFiles.add(positionDelete.path());
    appender.add(positionDelete);
  }

  /** 返回当前已写入字节数。 */
  @Override
  public long length() {
    return appender.length();
  }

  /**
   * 关闭写入器并产出位置删除文件元数据。
   *
   * <p>逻辑：若尚未产出 deleteFile，则先关闭底层 appender，再通过 {@link FileMetadata#deleteFileBuilder}
   * 构建位置删除文件元数据（含格式、路径、分区、 加密元数据、split 偏移、文件大小与调整后的指标）。通过 deleteFile==null 保证幂等。
   *
   * @throws IOException 关闭 appender 时发生 IO 异常
   */
  @Override
  public void close() throws IOException {
    if (deleteFile == null) {
      appender.close();
      this.deleteFile =
          FileMetadata.deleteFileBuilder(spec)
              .ofPositionDeletes()
              .withFormat(format)
              .withPath(location)
              .withPartition(partition)
              .withEncryptionKeyMetadata(keyMetadata)
              .withSplitOffsets(appender.splitOffsets())
              .withFileSizeInBytes(appender.length())
              .withMetrics(metrics())
              .build();
    }
  }

  /** 返回本删除文件引用的数据文件路径集合。 */
  public CharSequenceSet referencedDataFiles() {
    return referencedDataFiles;
  }

  /**
   * 返回已产出的删除文件元数据。
   *
   * @return 删除文件元数据
   * @throws IllegalStateException 若写入器尚未关闭
   */
  public DeleteFile toDeleteFile() {
    Preconditions.checkState(deleteFile != null, "Cannot create delete file from unclosed writer");
    return deleteFile;
  }

  /** 返回写入结果，包含删除文件元数据与引用数据文件集合。 */
  @Override
  public DeleteWriteResult result() {
    return new DeleteWriteResult(toDeleteFile(), referencedDataFiles());
  }

  /**
   * 根据引用数据文件数量调整写入指标。
   *
   * <p>逻辑：获取 appender 的原始指标；若引用了多个数据文件，path 字段的计数与边界混合了
   * 多个文件路径，需移除字段计数与边界（copyWithoutFieldCountsAndBounds）；若仅引用单个数据文件， 则只需移除字段计数（保留边界以利裁剪）。file_path 与
   * pos 字段的统计统一被剔除。
   *
   * @return 调整后的指标
   */
  private Metrics metrics() {
    Metrics metrics = appender.metrics();
    if (referencedDataFiles.size() > 1) {
      return MetricsUtil.copyWithoutFieldCountsAndBounds(metrics, FILE_AND_POS_FIELD_IDS);
    } else {
      return MetricsUtil.copyWithoutFieldCounts(metrics, FILE_AND_POS_FIELD_IDS);
    }
  }
}
