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

import static org.apache.iceberg.types.Types.NestedField.required;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.IndexedRecord;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.types.Types;

/**
 * Iceberg 表格式 v1 元数据的 Avro 读写适配层。
 *
 * <p>所属模块：iceberg-core；层次定位：元数据序列化/兼容层，介于 {@link ManifestFile}/{@link ManifestEntry}/{@link
 * DataFile} 内存模型与 Avro 持久化格式之间。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 v1 manifest list 与 manifest entry 的 Avro schema
 *   <li>提供 {@link IndexedManifestFile}、{@link IndexedManifestEntry}、{@link IndexedDataFile}
 *       包装器，将任意实现适配为 v1 Avro {@link IndexedRecord}
 *   <li>在 v1 格式下补充默认字段（如 block_size_in_bytes），屏蔽 v2 概念（如 sequence number）
 * </ul>
 *
 * <p>设计意图：v1 不支持序列号与 delete 文件等内容，本类通过包装器模式在不修改核心接口实现的前提下 完成 v1 兼容写入；包装器以字段序号（ordinal）映射到 Avro
 * schema，避免反射开销。
 *
 * <p>上下游关系：依赖 {@link AvroSchemaUtil}、{@link ManifestFile}、{@link ManifestEntry}、{@link DataFile}；被
 * manifest 写入器/读取器在 v1 表场景下使用。
 */
class V1Metadata {
  /** 工具类私有构造器，禁止实例化。 */
  private V1Metadata() {}

  /** v1 manifest list 文件的 Avro schema，包含路径、长度、spec ID、快照 ID、文件计数、行计数与 分区摘要等字段。 */
  static final Schema MANIFEST_LIST_SCHEMA =
      new Schema(
          ManifestFile.PATH,
          ManifestFile.LENGTH,
          ManifestFile.SPEC_ID,
          ManifestFile.SNAPSHOT_ID,
          ManifestFile.ADDED_FILES_COUNT,
          ManifestFile.EXISTING_FILES_COUNT,
          ManifestFile.DELETED_FILES_COUNT,
          ManifestFile.PARTITION_SUMMARIES,
          ManifestFile.ADDED_ROWS_COUNT,
          ManifestFile.EXISTING_ROWS_COUNT,
          ManifestFile.DELETED_ROWS_COUNT);

  /**
   * 将任意 {@link ManifestFile} 实现包装为按 v1 schema 写入 Avro 的 {@link IndexedRecord}。
   *
   * <p>设计意图：用于以旧 schema 写入 manifest list 文件，避免在 v1 表的元数据文件中写入序列号等 v2 才有的字段，从而保持 v1 兼容性。
   */
  static class IndexedManifestFile implements ManifestFile, IndexedRecord {
    /** 由 {@link #MANIFEST_LIST_SCHEMA} 转换而来的 Avro schema。 */
    private static final org.apache.avro.Schema AVRO_SCHEMA =
        AvroSchemaUtil.convert(MANIFEST_LIST_SCHEMA, "manifest_file");

    /** 被包装的实际 {@link ManifestFile} 实现。 */
    private ManifestFile wrapped = null;

    /**
     * 绑定待写入的 manifest 文件。
     *
     * @param file 待包装的 manifest 文件
     * @return 当前包装器自身
     */
    public ManifestFile wrap(ManifestFile file) {
      this.wrapped = file;
      return this;
    }

    /** @return 包装器对应的 Avro schema */
    @Override
    public org.apache.avro.Schema getSchema() {
      return AVRO_SCHEMA;
    }

    /**
     * 不支持通过 put 修改包装器。
     *
     * @param i 字段序号
     * @param v 字段值
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void put(int i, Object v) {
      throw new UnsupportedOperationException("Cannot modify IndexedManifestFile wrapper via put");
    }

    /**
     * 按字段序号返回 v1 schema 对应的字段值。
     *
     * <p>逻辑：0~10 分别映射到 path、length、partitionSpecId、snapshotId、addedFilesCount、
     * existingFilesCount、deletedFilesCount、partitions、addedRowsCount、existingRowsCount、
     * deletedRowsCount；其余序号抛出异常。
     *
     * @param pos 字段序号
     * @return 字段值
     * @throws UnsupportedOperationException 当序号未知时抛出
     */
    @Override
    public Object get(int pos) {
      switch (pos) {
        case 0:
          return path();
        case 1:
          return length();
        case 2:
          return partitionSpecId();
        case 3:
          return snapshotId();
        case 4:
          return addedFilesCount();
        case 5:
          return existingFilesCount();
        case 6:
          return deletedFilesCount();
        case 7:
          return partitions();
        case 8:
          return addedRowsCount();
        case 9:
          return existingRowsCount();
        case 10:
          return deletedRowsCount();
        default:
          throw new UnsupportedOperationException("Unknown field ordinal: " + pos);
      }
    }

    /** @return manifest 文件路径，委托给被包装对象 */
    @Override
    public String path() {
      return wrapped.path();
    }

    /** @return manifest 文件长度（字节），委托给被包装对象 */
    @Override
    public long length() {
      return wrapped.length();
    }

    /** @return 分区规格 ID，委托给被包装对象 */
    @Override
    public int partitionSpecId() {
      return wrapped.partitionSpecId();
    }

    /** @return manifest 内容类型，委托给被包装对象 */
    @Override
    public ManifestContent content() {
      return wrapped.content();
    }

    /** @return 序列号，委托给被包装对象（v1 不持久化该字段） */
    @Override
    public long sequenceNumber() {
      return wrapped.sequenceNumber();
    }

    /** @return 最小序列号，委托给被包装对象（v1 不持久化该字段） */
    @Override
    public long minSequenceNumber() {
      return wrapped.minSequenceNumber();
    }

    /** @return 快照 ID，委托给被包装对象 */
    @Override
    public Long snapshotId() {
      return wrapped.snapshotId();
    }

    /** @return 是否包含新增文件，委托给被包装对象 */
    @Override
    public boolean hasAddedFiles() {
      return wrapped.hasAddedFiles();
    }

    /** @return 新增文件计数，委托给被包装对象 */
    @Override
    public Integer addedFilesCount() {
      return wrapped.addedFilesCount();
    }

    /** @return 新增行数，委托给被包装对象 */
    @Override
    public Long addedRowsCount() {
      return wrapped.addedRowsCount();
    }

    /** @return 是否包含已存在文件，委托给被包装对象 */
    @Override
    public boolean hasExistingFiles() {
      return wrapped.hasExistingFiles();
    }

    /** @return 已存在文件计数，委托给被包装对象 */
    @Override
    public Integer existingFilesCount() {
      return wrapped.existingFilesCount();
    }

    /** @return 已存在行数，委托给被包装对象 */
    @Override
    public Long existingRowsCount() {
      return wrapped.existingRowsCount();
    }

    /** @return 是否包含已删除文件，委托给被包装对象 */
    @Override
    public boolean hasDeletedFiles() {
      return wrapped.hasDeletedFiles();
    }

    /** @return 已删除文件计数，委托给被包装对象 */
    @Override
    public Integer deletedFilesCount() {
      return wrapped.deletedFilesCount();
    }

    /** @return 已删除行数，委托给被包装对象 */
    @Override
    public Long deletedRowsCount() {
      return wrapped.deletedRowsCount();
    }

    /** @return 分区字段摘要列表，委托给被包装对象 */
    @Override
    public List<PartitionFieldSummary> partitions() {
      return wrapped.partitions();
    }

    /** @return 被包装对象的副本 */
    @Override
    public ManifestFile copy() {
      return wrapped.copy();
    }
  }

  /**
   * 根据 partition 类型构造 v1 manifest entry 的 Avro schema。
   *
   * @param partitionType 分区类型描述
   * @return v1 manifest entry schema
   */
  static Schema entrySchema(Types.StructType partitionType) {
    return wrapFileSchema(dataFileSchema(partitionType));
  }

  /**
   * 将 data_file schema 包装为 manifest entry schema，附加 status 与 snapshot_id 字段。
   *
   * <p>逻辑：用于构建投影 schema，包含 {@link ManifestEntry#STATUS}、{@link ManifestEntry#SNAPSHOT_ID} 以及嵌套的
   * data_file 字段。
   *
   * @param fileSchema 数据文件 schema
   * @return manifest entry schema
   */
  static Schema wrapFileSchema(Types.StructType fileSchema) {
    // this is used to build projection schemas
    return new Schema(
        ManifestEntry.STATUS,
        ManifestEntry.SNAPSHOT_ID,
        required(ManifestEntry.DATA_FILE_ID, "data_file", fileSchema));
  }

  /** v1 特有的 block_size_in_bytes 字段，字段 ID 为 105。 */
  private static final Types.NestedField BLOCK_SIZE =
      required(105, "block_size_in_bytes", Types.LongType.get());

  /**
   * 构造 v1 数据文件的 schema（{@link Types.StructType}）。
   *
   * <p>逻辑：包含文件路径、格式、分区值、记录数、文件大小、block_size_in_bytes（v1 特有）以及 各类统计信息字段（列大小、值计数、空值计数、NaN
   * 计数、上下界、密钥、split 偏移、排序 ID）。
   *
   * @param partitionType 分区类型描述
   * @return v1 数据文件 schema
   */
  static Types.StructType dataFileSchema(Types.StructType partitionType) {
    return Types.StructType.of(
        DataFile.FILE_PATH,
        DataFile.FILE_FORMAT,
        required(DataFile.PARTITION_ID, DataFile.PARTITION_NAME, partitionType),
        DataFile.RECORD_COUNT,
        DataFile.FILE_SIZE,
        BLOCK_SIZE,
        DataFile.COLUMN_SIZES,
        DataFile.VALUE_COUNTS,
        DataFile.NULL_VALUE_COUNTS,
        DataFile.NAN_VALUE_COUNTS,
        DataFile.LOWER_BOUNDS,
        DataFile.UPPER_BOUNDS,
        DataFile.KEY_METADATA,
        DataFile.SPLIT_OFFSETS,
        DataFile.SORT_ORDER_ID);
  }

  /**
   * 将任意 {@link ManifestEntry} 实现包装为按 v1 schema 写入 Avro 的 {@link IndexedRecord}。
   *
   * <p>设计意图：用于以 v1 schema 写入 manifest entry，屏蔽 v2 才有的 sequence number 等字段， 保持 v1 兼容性。内部持有一个 {@link
   * IndexedDataFile} 用于包装嵌套的 data_file 字段。
   */
  static class IndexedManifestEntry implements ManifestEntry<DataFile>, IndexedRecord {
    /** 由 {@link #entrySchema} 转换而来的 Avro schema。 */
    private final org.apache.avro.Schema avroSchema;
    /** 用于包装嵌套 data_file 字段的包装器。 */
    private final IndexedDataFile fileWrapper;
    /** 被包装的实际 {@link ManifestEntry} 实现。 */
    private ManifestEntry<DataFile> wrapped = null;

    /**
     * 构造 manifest entry 包装器。
     *
     * @param partitionType 分区类型描述，用于构造 schema
     */
    IndexedManifestEntry(Types.StructType partitionType) {
      this.avroSchema = AvroSchemaUtil.convert(entrySchema(partitionType), "manifest_entry");
      this.fileWrapper = new IndexedDataFile(avroSchema.getField("data_file").schema());
    }

    /**
     * 绑定待写入的 manifest entry。
     *
     * @param entry 待包装的 manifest entry
     * @return 当前包装器自身
     */
    public IndexedManifestEntry wrap(ManifestEntry<DataFile> entry) {
      this.wrapped = entry;
      return this;
    }

    /** @return 包装器对应的 Avro schema */
    @Override
    public org.apache.avro.Schema getSchema() {
      return avroSchema;
    }

    /**
     * 不支持通过 put 修改包装器。
     *
     * @param i 字段序号
     * @param v 字段值
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void put(int i, Object v) {
      throw new UnsupportedOperationException("Cannot modify IndexedManifestEntry wrapper via put");
    }

    /**
     * 按字段序号返回 v1 schema 对应的字段值。
     *
     * <p>逻辑：0 映射到 status、1 映射到 snapshotId、2 映射到 data_file（通过 {@link IndexedDataFile} 包装）；其余序号抛出异常。
     *
     * @param i 字段序号
     * @return 字段值
     * @throws UnsupportedOperationException 当序号未知时抛出
     */
    @Override
    public Object get(int i) {
      switch (i) {
        case 0:
          return wrapped.status().id();
        case 1:
          return wrapped.snapshotId();
        case 2:
          DataFile file = wrapped.file();
          if (file != null) {
            return fileWrapper.wrap(file);
          }
          return null;
        default:
          throw new UnsupportedOperationException("Unknown field ordinal: " + i);
      }
    }

    /** @return entry 状态，委托给被包装对象 */
    @Override
    public Status status() {
      return wrapped.status();
    }

    /** @return 快照 ID，委托给被包装对象 */
    @Override
    public Long snapshotId() {
      return wrapped.snapshotId();
    }

    /**
     * 设置快照 ID，委托给被包装对象。
     *
     * @param snapshotId 快照 ID
     */
    @Override
    public void setSnapshotId(long snapshotId) {
      wrapped.setSnapshotId(snapshotId);
    }

    /** @return 数据序列号，委托给被包装对象（v1 不持久化该字段） */
    @Override
    public Long dataSequenceNumber() {
      return wrapped.dataSequenceNumber();
    }

    /**
     * 设置数据序列号，委托给被包装对象。
     *
     * @param dataSequenceNumber 数据序列号
     */
    @Override
    public void setDataSequenceNumber(long dataSequenceNumber) {
      wrapped.setDataSequenceNumber(dataSequenceNumber);
    }

    /** @return 文件序列号，委托给被包装对象（v1 不持久化该字段） */
    @Override
    public Long fileSequenceNumber() {
      return wrapped.fileSequenceNumber();
    }

    /**
     * 设置文件序列号，委托给被包装对象。
     *
     * @param fileSequenceNumber 文件序列号
     */
    @Override
    public void setFileSequenceNumber(long fileSequenceNumber) {
      wrapped.setFileSequenceNumber(fileSequenceNumber);
    }

    /** @return 关联的数据文件，委托给被包装对象 */
    @Override
    public DataFile file() {
      return wrapped.file();
    }

    /** @return 被包装对象的副本 */
    @Override
    public ManifestEntry<DataFile> copy() {
      return wrapped.copy();
    }

    /** @return 被包装对象去除统计信息的副本 */
    @Override
    public ManifestEntry<DataFile> copyWithoutStats() {
      return wrapped.copyWithoutStats();
    }
  }

  /**
   * 将任意 {@link DataFile} 实现包装为按 v1 schema 写入 Avro 的 {@link IndexedRecord}。
   *
   * <p>设计意图：用于以 v1 schema 写入 data_file 字段，补充 v1 特有的 block_size_in_bytes 默认值（64MB），屏蔽 v2 才有的
   * content/sequence number 等字段。
   */
  static class IndexedDataFile implements DataFile, IndexedRecord {
    /** v1 默认 block size：64MB。 */
    private static final long DEFAULT_BLOCK_SIZE = 64 * 1024 * 1024;

    /** 由上层传入的 Avro schema。 */
    private final org.apache.avro.Schema avroSchema;
    /** 用于包装 partition 字段的 {@link IndexedStructLike}。 */
    private final IndexedStructLike partitionWrapper;
    /** 被包装的实际 {@link DataFile} 实现。 */
    private DataFile wrapped = null;

    /**
     * 构造 data file 包装器。
     *
     * @param avroSchema data_file 字段对应的 Avro schema
     */
    IndexedDataFile(org.apache.avro.Schema avroSchema) {
      this.avroSchema = avroSchema;
      this.partitionWrapper = new IndexedStructLike(avroSchema.getField("partition").schema());
    }

    /**
     * 绑定待写入的数据文件。
     *
     * @param file 待包装的数据文件
     * @return 当前包装器自身
     */
    IndexedDataFile wrap(DataFile file) {
      this.wrapped = file;
      return this;
    }

    /**
     * 按字段序号返回 v1 schema 对应的字段值。
     *
     * <p>逻辑：0~14 分别映射到 path、format、partition、recordCount、fileSizeInBytes、 block_size_in_bytes（返回默认
     * 64MB）、columnSizes、valueCounts、nullValueCounts、
     * nanValueCounts、lowerBounds、upperBounds、keyMetadata、splitOffsets、sortOrderId； 其余序号抛出 {@link
     * IllegalArgumentException}。
     *
     * @param pos 字段序号
     * @return 字段值
     * @throws IllegalArgumentException 当序号未知时抛出
     */
    @Override
    public Object get(int pos) {
      switch (pos) {
        case 0:
          return wrapped.path().toString();
        case 1:
          return wrapped.format() != null ? wrapped.format().toString() : null;
        case 2:
          return partitionWrapper.wrap(wrapped.partition());
        case 3:
          return wrapped.recordCount();
        case 4:
          return wrapped.fileSizeInBytes();
        case 5:
          return DEFAULT_BLOCK_SIZE;
        case 6:
          return wrapped.columnSizes();
        case 7:
          return wrapped.valueCounts();
        case 8:
          return wrapped.nullValueCounts();
        case 9:
          return wrapped.nanValueCounts();
        case 10:
          return wrapped.lowerBounds();
        case 11:
          return wrapped.upperBounds();
        case 12:
          return wrapped.keyMetadata();
        case 13:
          return wrapped.splitOffsets();
        case 14:
          return wrapped.sortOrderId();
      }
      throw new IllegalArgumentException("Unknown field ordinal: " + pos);
    }

    /**
     * 不支持通过 put 修改包装器。
     *
     * @param i 字段序号
     * @param v 字段值
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void put(int i, Object v) {
      throw new UnsupportedOperationException("Cannot modify IndexedDataFile wrapper via put");
    }

    /** @return 包装器对应的 Avro schema */
    @Override
    public org.apache.avro.Schema getSchema() {
      return avroSchema;
    }

    /** @return 文件位置（v1 包装器不持久化该字段，返回 null） */
    @Override
    public Long pos() {
      return null;
    }

    /** @return 分区规格 ID，委托给被包装对象 */
    @Override
    public int specId() {
      return wrapped.specId();
    }

    /** @return 文件内容类型，委托给被包装对象 */
    @Override
    public FileContent content() {
      return wrapped.content();
    }

    /** @return 文件路径，委托给被包装对象 */
    @Override
    public CharSequence path() {
      return wrapped.path();
    }

    /** @return 文件格式，委托给被包装对象 */
    @Override
    public FileFormat format() {
      return wrapped.format();
    }

    /** @return 分区值，委托给被包装对象 */
    @Override
    public StructLike partition() {
      return wrapped.partition();
    }

    /** @return 记录数，委托给被包装对象 */
    @Override
    public long recordCount() {
      return wrapped.recordCount();
    }

    /** @return 文件大小（字节），委托给被包装对象 */
    @Override
    public long fileSizeInBytes() {
      return wrapped.fileSizeInBytes();
    }

    /** @return 列大小映射，委托给被包装对象 */
    @Override
    public Map<Integer, Long> columnSizes() {
      return wrapped.columnSizes();
    }

    /** @return 值计数映射，委托给被包装对象 */
    @Override
    public Map<Integer, Long> valueCounts() {
      return wrapped.valueCounts();
    }

    /** @return 空值计数映射，委托给被包装对象 */
    @Override
    public Map<Integer, Long> nullValueCounts() {
      return wrapped.nullValueCounts();
    }

    /** @return NaN 值计数映射，委托给被包装对象 */
    @Override
    public Map<Integer, Long> nanValueCounts() {
      return wrapped.nanValueCounts();
    }

    /** @return 下界映射，委托给被包装对象 */
    @Override
    public Map<Integer, ByteBuffer> lowerBounds() {
      return wrapped.lowerBounds();
    }

    /** @return 上界映射，委托给被包装对象 */
    @Override
    public Map<Integer, ByteBuffer> upperBounds() {
      return wrapped.upperBounds();
    }

    /** @return 密钥元数据，委托给被包装对象 */
    @Override
    public ByteBuffer keyMetadata() {
      return wrapped.keyMetadata();
    }

    /** @return split 偏移列表，委托给被包装对象 */
    @Override
    public List<Long> splitOffsets() {
      return wrapped.splitOffsets();
    }

    /** @return 排序规则 ID，委托给被包装对象 */
    @Override
    public Integer sortOrderId() {
      return wrapped.sortOrderId();
    }

    /** @return 数据序列号，委托给被包装对象（v1 不持久化该字段） */
    @Override
    public Long dataSequenceNumber() {
      return wrapped.dataSequenceNumber();
    }

    /** @return 文件序列号，委托给被包装对象（v1 不持久化该字段） */
    @Override
    public Long fileSequenceNumber() {
      return wrapped.fileSequenceNumber();
    }

    /** @return 被包装对象的副本 */
    @Override
    public DataFile copy() {
      return wrapped.copy();
    }

    /** @return 被包装对象去除统计信息的副本 */
    @Override
    public DataFile copyWithoutStats() {
      return wrapped.copyWithoutStats();
    }
  }
}
