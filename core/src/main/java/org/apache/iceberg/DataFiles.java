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
import org.apache.hadoop.fs.FileStatus;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.encryption.EncryptionKeyMetadata;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.util.ArrayUtil;
import org.apache.iceberg.util.ByteBuffers;

/**
 * {@link DataFile} 构造与分区数据处理工具类。
 *
 * <p>所属模块：iceberg-core（数据文件元数据构造层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link DataFile} 的 {@link Builder}，统一构造 {@link GenericDataFile} 实例。
 *   <li>封装分区数据（{@link PartitionData}）的创建、复制、从路径/值列表解析等操作。
 *   <li>支持从 manifest 文件构造一个"伪数据文件"用于元数据表展示。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把分区数据操作集中到工具类，避免 Builder 内部分区处理逻辑膨胀。
 *   <li>reuse 参数允许复用已有 {@link PartitionData} 缓冲区，减少对象分配。
 *   <li>Builder 采用 fluent 风格，便于链式调用。
 * </ul>
 *
 * <p>上下游关系：被各写入器、维护操作、元数据表等大量调用；底层依赖 {@link PartitionSpec}、{@link Conversions}、{@link
 * GenericDataFile} 等。
 */
public class DataFiles {

  private DataFiles() {}

  /**
   * 根据分区 spec 创建空的 {@link PartitionData}。
   *
   * @param spec 分区 spec
   * @return 新的空分区数据对象
   */
  static PartitionData newPartitionData(PartitionSpec spec) {
    return new PartitionData(spec.partitionType());
  }

  /**
   * 把分区数据从 {@link StructLike} 复制到 {@link PartitionData}（可复用缓冲区）。
   *
   * <p>逻辑：按分区字段顺序，使用各字段对应的 Java 类型从源对象读取并写入目标对象。
   *
   * @param spec 分区 spec
   * @param partitionData 源分区数据
   * @param reuse 可复用的目标对象；为 null 时新建
   * @return 填充后的 {@link PartitionData}
   */
  static PartitionData copyPartitionData(
      PartitionSpec spec, StructLike partitionData, PartitionData reuse) {
    Preconditions.checkArgument(
        spec.isPartitioned(), "Can't copy partition data to a unpartitioned table");
    PartitionData data = reuse;
    if (data == null) {
      data = newPartitionData(spec);
    }

    Class<?>[] javaClasses = spec.javaClasses();
    List<PartitionField> fields = spec.fields();
    for (int i = 0; i < fields.size(); i += 1) {
      data.set(i, partitionData.get(i, javaClasses[i]));
    }

    return data;
  }

  /**
   * 从分区路径字符串（如 "fieldA=1/fieldB=2"）解析填充 {@link PartitionData}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>按 "/" 分割得到各分区段。
   *   <li>校验段数与 spec 字段数一致。
   *   <li>逐段按 "=" 分割得到字段名与值字符串，校验字段名匹配，再用 {@link Conversions#fromPartitionString} 转换为对应类型并写入。
   * </ol>
   *
   * @param spec 分区 spec
   * @param partitionPath 分区路径字符串
   * @param reuse 可复用缓冲区；为 null 时新建
   * @return 填充后的 {@link PartitionData}
   */
  static PartitionData fillFromPath(PartitionSpec spec, String partitionPath, PartitionData reuse) {
    PartitionData data = reuse;
    if (data == null) {
      data = newPartitionData(spec);
    }

    String[] partitions = partitionPath.split("/", -1);
    Preconditions.checkArgument(
        partitions.length <= spec.fields().size(),
        "Invalid partition data, too many fields (expecting %s): %s",
        spec.fields().size(),
        partitionPath);
    Preconditions.checkArgument(
        partitions.length >= spec.fields().size(),
        "Invalid partition data, not enough fields (expecting %s): %s",
        spec.fields().size(),
        partitionPath);

    for (int i = 0; i < partitions.length; i += 1) {
      PartitionField field = spec.fields().get(i);
      String[] parts = partitions[i].split("=", 2);
      Preconditions.checkArgument(
          parts.length == 2 && parts[0] != null && field.name().equals(parts[0]),
          "Invalid partition: %s",
          partitions[i]);

      data.set(i, Conversions.fromPartitionString(data.getType(i), parts[1]));
    }

    return data;
  }

  /**
   * 从分区值列表（按 spec 字段顺序）填充 {@link PartitionData}。
   *
   * <p>逻辑：校验值列表长度与 spec 字段数一致，逐个用 {@link Conversions#fromPartitionString} 转换并写入。
   *
   * @param spec 分区 spec
   * @param partitionValues 分区值字符串列表
   * @param reuse 可复用缓冲区；为 null 时新建
   * @return 填充后的 {@link PartitionData}
   */
  static PartitionData fillFromValues(
      PartitionSpec spec, List<String> partitionValues, PartitionData reuse) {
    PartitionData data = reuse;
    if (data == null) {
      data = newPartitionData(spec);
    }

    Preconditions.checkArgument(
        partitionValues.size() == spec.fields().size(),
        "Invalid partition data, expecting %s fields, found %s",
        spec.fields().size(),
        partitionValues.size());

    for (int i = 0; i < partitionValues.size(); i += 1) {
      data.set(i, Conversions.fromPartitionString(data.getType(i), partitionValues.get(i)));
    }

    return data;
  }

  /**
   * 从分区路径字符串构造 {@link PartitionData}（便捷方法，不复用缓冲区）。
   *
   * @param spec 分区 spec
   * @param partitionPath 分区路径字符串
   * @return 新建的 {@link PartitionData}
   */
  public static PartitionData data(PartitionSpec spec, String partitionPath) {
    return fillFromPath(spec, partitionPath, null);
  }

  /**
   * 把分区数据从 {@link StructLike} 复制到新的 {@link PartitionData}（便捷方法，不复用缓冲区）。
   *
   * @param spec 分区 spec
   * @param partition 源分区数据
   * @return 新建的 {@link PartitionData}
   */
  public static PartitionData copy(PartitionSpec spec, StructLike partition) {
    return copyPartitionData(spec, partition, null);
  }

  /**
   * 从 manifest 文件构造一个"伪数据文件"，用于在元数据表中以 DataFile 形式展示 manifest。
   *
   * <p>逻辑：使用 manifest 路径作为文件路径，格式固定为 {@link FileFormat#AVRO}， 记录数取 addedFilesCount +
   * existingFilesCount，文件大小取 manifest.length()。
   *
   * @param manifest manifest 文件
   * @return 表示该 manifest 的无分区 {@link DataFile}
   */
  public static DataFile fromManifest(ManifestFile manifest) {
    Preconditions.checkArgument(
        manifest.addedFilesCount() != null && manifest.existingFilesCount() != null,
        "Cannot create data file from manifest: data file counts are missing.");

    return DataFiles.builder(PartitionSpec.unpartitioned())
        .withPath(manifest.path())
        .withFormat(FileFormat.AVRO)
        .withRecordCount(manifest.addedFilesCount() + manifest.existingFilesCount())
        .withFileSizeInBytes(manifest.length())
        .build();
  }

  /**
   * 创建一个 {@link Builder} 用于构造 {@link DataFile}。
   *
   * @param spec 数据文件所属的分区 spec
   * @return 新的 Builder 实例
   */
  public static Builder builder(PartitionSpec spec) {
    return new Builder(spec);
  }

  /**
   * {@link DataFile} 构建器：采用 fluent 风格收集数据文件元数据字段。
   *
   * <p>设计意图：
   *
   * <ul>
   *   <li>必填字段（filePath/format/fileSizeInBytes/recordCount）在 {@link #build()} 时校验。
   *   <li>分区数据按 spec 自动初始化，未分区表保持 null。
   *   <li>提供 {@link #clear()} 与 {@link #copy(DataFile)} 支持缓冲区复用，适合批量写入场景。
   * </ul>
   */
  public static class Builder {
    private final PartitionSpec spec;
    private final boolean isPartitioned;
    private final int specId;
    private PartitionData partitionData;
    private String filePath = null;
    private FileFormat format = null;
    private long recordCount = -1L;
    private long fileSizeInBytes = -1L;

    // optional fields
    private Map<Integer, Long> columnSizes = null;
    private Map<Integer, Long> valueCounts = null;
    private Map<Integer, Long> nullValueCounts = null;
    private Map<Integer, Long> nanValueCounts = null;
    private Map<Integer, ByteBuffer> lowerBounds = null;
    private Map<Integer, ByteBuffer> upperBounds = null;
    private ByteBuffer keyMetadata = null;
    private List<Long> splitOffsets = null;
    private List<Integer> equalityFieldIds = null;
    private Integer sortOrderId = SortOrder.unsorted().orderId();

    /**
     * 构造 Builder，根据 spec 是否分区初始化 partitionData。
     *
     * @param spec 数据文件所属的分区 spec
     */
    public Builder(PartitionSpec spec) {
      this.spec = spec;
      this.specId = spec.specId();
      this.isPartitioned = spec.isPartitioned();
      this.partitionData = isPartitioned ? newPartitionData(spec) : null;
    }

    /**
     * 清空所有可变字段，便于复用 Builder 实例。
     *
     * <p>逻辑：分区数据调用 clear()，其余字段重置为初始值。
     */
    public void clear() {
      if (isPartitioned) {
        partitionData.clear();
      }
      this.filePath = null;
      this.format = null;
      this.recordCount = -1L;
      this.fileSizeInBytes = -1L;
      this.columnSizes = null;
      this.valueCounts = null;
      this.nullValueCounts = null;
      this.nanValueCounts = null;
      this.lowerBounds = null;
      this.upperBounds = null;
      this.splitOffsets = null;
      this.sortOrderId = SortOrder.unsorted().orderId();
    }

    /**
     * 从已有 {@link DataFile} 复制所有字段到本 Builder，便于基于已有文件做小幅修改。
     *
     * <p>逻辑：校验 specId 一致后复制分区数据、文件路径、格式、记录数、文件大小、各指标 map、 加密
     * key（深拷贝）、splitOffsets（不可变副本）、sortOrderId。
     *
     * @param toCopy 源数据文件
     * @return 当前 Builder
     */
    public Builder copy(DataFile toCopy) {
      if (isPartitioned) {
        Preconditions.checkState(
            specId == toCopy.specId(), "Cannot copy a DataFile with a different spec");
        this.partitionData = copyPartitionData(spec, toCopy.partition(), partitionData);
      }
      this.filePath = toCopy.path().toString();
      this.format = toCopy.format();
      this.recordCount = toCopy.recordCount();
      this.fileSizeInBytes = toCopy.fileSizeInBytes();
      this.columnSizes = toCopy.columnSizes();
      this.valueCounts = toCopy.valueCounts();
      this.nullValueCounts = toCopy.nullValueCounts();
      this.nanValueCounts = toCopy.nanValueCounts();
      this.lowerBounds = toCopy.lowerBounds();
      this.upperBounds = toCopy.upperBounds();
      this.keyMetadata =
          toCopy.keyMetadata() == null ? null : ByteBuffers.copy(toCopy.keyMetadata());
      this.splitOffsets =
          toCopy.splitOffsets() == null ? null : ImmutableList.copyOf(toCopy.splitOffsets());
      this.sortOrderId = toCopy.sortOrderId();
      return this;
    }

    /**
     * 从 Hadoop {@link FileStatus} 设置文件路径与大小。
     *
     * @param stat Hadoop 文件状态
     * @return 当前 Builder
     */
    public Builder withStatus(FileStatus stat) {
      this.filePath = stat.getPath().toString();
      this.fileSizeInBytes = stat.getLen();
      return this;
    }

    /**
     * 从 {@link InputFile} 设置文件路径与大小；若为 {@link HadoopInputFile} 则委托 {@link #withStatus} 以获取更准确的
     * FileStatus 信息。
     *
     * @param file 输入文件
     * @return 当前 Builder
     */
    public Builder withInputFile(InputFile file) {
      if (file instanceof HadoopInputFile) {
        return withStatus(((HadoopInputFile) file).getStat());
      }

      this.filePath = file.location();
      this.fileSizeInBytes = file.getLength();
      return this;
    }

    /**
     * 从加密输出文件设置文件信息与加密 key 元数据。
     *
     * @param newEncryptedFile 加密输出文件
     * @return 当前 Builder
     */
    public Builder withEncryptedOutputFile(EncryptedOutputFile newEncryptedFile) {
      withInputFile(newEncryptedFile.encryptingOutputFile().toInputFile());
      withEncryptionKeyMetadata(newEncryptedFile.keyMetadata());
      return this;
    }

    /**
     * 设置文件路径。
     *
     * @param newFilePath 文件路径
     * @return 当前 Builder
     */
    public Builder withPath(String newFilePath) {
      this.filePath = newFilePath;
      return this;
    }

    /**
     * 按字符串名称设置文件格式。
     *
     * @param newFormat 格式名
     * @return 当前 Builder
     */
    public Builder withFormat(String newFormat) {
      this.format = FileFormat.fromString(newFormat);
      return this;
    }

    /**
     * 设置文件格式。
     *
     * @param newFormat 文件格式
     * @return 当前 Builder
     */
    public Builder withFormat(FileFormat newFormat) {
      this.format = newFormat;
      return this;
    }

    /**
     * 从 {@link StructLike} 设置分区数据（仅分区表生效）。
     *
     * @param newPartition 分区数据
     * @return 当前 Builder
     */
    public Builder withPartition(StructLike newPartition) {
      if (isPartitioned) {
        this.partitionData = copyPartitionData(spec, newPartition, partitionData);
      }
      return this;
    }

    /**
     * 设置文件中的记录数。
     *
     * @param newRecordCount 记录数
     * @return 当前 Builder
     */
    public Builder withRecordCount(long newRecordCount) {
      this.recordCount = newRecordCount;
      return this;
    }

    /**
     * 设置文件大小（字节）。
     *
     * @param newFileSizeInBytes 文件大小
     * @return 当前 Builder
     */
    public Builder withFileSizeInBytes(long newFileSizeInBytes) {
      this.fileSizeInBytes = newFileSizeInBytes;
      return this;
    }

    /**
     * 从分区路径字符串设置分区数据。
     *
     * @param newPartitionPath 分区路径字符串
     * @return 当前 Builder
     */
    public Builder withPartitionPath(String newPartitionPath) {
      Preconditions.checkArgument(
          isPartitioned || newPartitionPath.isEmpty(),
          "Cannot add partition data for an unpartitioned table");
      if (!newPartitionPath.isEmpty()) {
        this.partitionData = fillFromPath(spec, newPartitionPath, partitionData);
      }
      return this;
    }

    /**
     * 从分区值列表设置分区数据。
     *
     * @param partitionValues 分区值字符串列表
     * @return 当前 Builder
     */
    public Builder withPartitionValues(List<String> partitionValues) {
      Preconditions.checkArgument(
          isPartitioned ^ partitionValues.isEmpty(),
          "Table must be partitioned or partition values must be empty");
      if (!partitionValues.isEmpty()) {
        this.partitionData = fillFromValues(spec, partitionValues, partitionData);
      }
      return this;
    }

    /**
     * 从 {@link Metrics} 一次性设置所有指标字段（记录数、列大小、值计数、null 计数、 NaN 计数、上下界）。
     *
     * <p>设计要点：recordCount 为 null 时设为 -1 以便后续 build 校验失败。
     *
     * @param metrics 文件指标
     * @return 当前 Builder
     */
    public Builder withMetrics(Metrics metrics) {
      // check for null to avoid NPE when unboxing
      this.recordCount = metrics.recordCount() == null ? -1 : metrics.recordCount();
      this.columnSizes = metrics.columnSizes();
      this.valueCounts = metrics.valueCounts();
      this.nullValueCounts = metrics.nullValueCounts();
      this.nanValueCounts = metrics.nanValueCounts();
      this.lowerBounds = metrics.lowerBounds();
      this.upperBounds = metrics.upperBounds();
      return this;
    }

    /**
     * 设置文件切分偏移列表（不可变副本）。
     *
     * @param offsets 切分偏移列表
     * @return 当前 Builder
     */
    public Builder withSplitOffsets(List<Long> offsets) {
      if (offsets != null) {
        this.splitOffsets = ImmutableList.copyOf(offsets);
      } else {
        this.splitOffsets = null;
      }
      return this;
    }

    /**
     * 设置等值删除（equality delete）文件涉及的字段 id 列表。
     *
     * @param equalityIds 字段 id 列表
     * @return 当前 Builder
     */
    public Builder withEqualityFieldIds(List<Integer> equalityIds) {
      if (equalityIds != null) {
        this.equalityFieldIds = ImmutableList.copyOf(equalityIds);
      }

      return this;
    }

    /**
     * 设置加密 key 元数据（{@link ByteBuffer} 形式）。
     *
     * @param newKeyMetadata 加密 key 元数据
     * @return 当前 Builder
     */
    public Builder withEncryptionKeyMetadata(ByteBuffer newKeyMetadata) {
      this.keyMetadata = newKeyMetadata;
      return this;
    }

    /**
     * 设置加密 key 元数据（{@link EncryptionKeyMetadata} 形式），委托给 ByteBuffer 版本。
     *
     * @param newKeyMetadata 加密 key 元数据
     * @return 当前 Builder
     */
    public Builder withEncryptionKeyMetadata(EncryptionKeyMetadata newKeyMetadata) {
      return withEncryptionKeyMetadata(newKeyMetadata.buffer());
    }

    /**
     * 设置文件所属的排序订单 id。
     *
     * @param newSortOrder 排序订单
     * @return 当前 Builder
     */
    public Builder withSortOrder(SortOrder newSortOrder) {
      if (newSortOrder != null) {
        this.sortOrderId = newSortOrder.orderId();
      }
      return this;
    }

    /**
     * 构建最终的 {@link DataFile} 实例。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>校验必填字段：filePath、format（若未指定则从文件名推断）、fileSizeInBytes、recordCount。
     *   <li>分区表时复制 partitionData 以保证不可变性。
     *   <li>构造 {@link GenericDataFile} 返回。
     * </ol>
     *
     * @return 新建的 {@link DataFile}
     */
    public DataFile build() {
      Preconditions.checkArgument(filePath != null, "File path is required");
      if (format == null) {
        this.format = FileFormat.fromFileName(filePath);
      }
      Preconditions.checkArgument(format != null, "File format is required");
      Preconditions.checkArgument(fileSizeInBytes >= 0, "File size is required");
      Preconditions.checkArgument(recordCount >= 0, "Record count is required");

      return new GenericDataFile(
          specId,
          filePath,
          format,
          isPartitioned ? partitionData.copy() : null,
          fileSizeInBytes,
          new Metrics(
              recordCount,
              columnSizes,
              valueCounts,
              nullValueCounts,
              nanValueCounts,
              lowerBounds,
              upperBounds),
          keyMetadata,
          splitOffsets,
          ArrayUtil.toIntArray(equalityFieldIds),
          sortOrderId);
    }
  }
}
