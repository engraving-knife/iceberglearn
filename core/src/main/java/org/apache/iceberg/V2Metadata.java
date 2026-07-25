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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Types;

/**
 * Iceberg 表格式 v2 元数据的 Avro 读写适配层。
 *
 * <p>所属模块：iceberg-core；层次定位：元数据序列化/兼容层，介于 {@link ManifestFile}/{@link ManifestEntry}/{@link
 * ContentFile} 内存模型与 Avro 持久化格式之间。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 v2 manifest list 与 manifest entry 的 Avro schema
 *   <li>提供 {@link IndexedManifestFile}、{@link IndexedManifestEntry}、{@link IndexedDataFile}
 *       包装器，将任意实现适配为 v2 Avro {@link IndexedRecord}
 *   <li>在写入时为未分配序列号的 manifest/entry 填充当前提交的序列号
 * </ul>
 *
 * <p>设计意图：v2 引入了序列号（sequence number）、manifest 内容类型、删除文件等概念。本类通过 包装器模式在不修改核心接口实现的前提下完成 v2
 * 兼容写入；包装器以字段序号（ordinal）映射到 Avro schema，并在 get() 中处理序列号继承逻辑，避免反射开销。
 *
 * <p>上下游关系：依赖 {@link AvroSchemaUtil}、{@link ManifestFile}、{@link ManifestEntry}、 {@link
 * ContentFile}；被 manifest 写入器/读取器在 v2 表场景下使用。
 */
class V2Metadata {
  /** 工具类私有构造器，禁止实例化。 */
  private V2Metadata() {}

  /**
   * v2 manifest list 文件的 Avro schema，相比 v1 增加了 manifest 内容类型、序列号、最小序列号、 密钥元数据等字段，且大部分字段标记为
   * required。
   */
  static final Schema MANIFEST_LIST_SCHEMA =
      new Schema(
          ManifestFile.PATH,
          ManifestFile.LENGTH,
          ManifestFile.SPEC_ID,
          ManifestFile.MANIFEST_CONTENT.asRequired(),
          ManifestFile.SEQUENCE_NUMBER.asRequired(),
          ManifestFile.MIN_SEQUENCE_NUMBER.asRequired(),
          ManifestFile.SNAPSHOT_ID.asRequired(),
          ManifestFile.ADDED_FILES_COUNT.asRequired(),
          ManifestFile.EXISTING_FILES_COUNT.asRequired(),
          ManifestFile.DELETED_FILES_COUNT.asRequired(),
          ManifestFile.ADDED_ROWS_COUNT.asRequired(),
          ManifestFile.EXISTING_ROWS_COUNT.asRequired(),
          ManifestFile.DELETED_ROWS_COUNT.asRequired(),
          ManifestFile.PARTITION_SUMMARIES);

  /**
   * 将任意 {@link ManifestFile} 实现包装为按 v2 schema 写入 Avro 的 {@link IndexedRecord}。
   *
   * <p>设计意图：在写入 manifest list 文件时，对未分配序列号的 manifest 填充当前提交的序列号， 保证 v2 表元数据中序列号字段的完整性。
   */
  static class IndexedManifestFile implements ManifestFile, IndexedRecord {
    /** 由 {@link #MANIFEST_LIST_SCHEMA} 转换而来的 Avro schema。 */
    private static final org.apache.avro.Schema AVRO_SCHEMA =
        AvroSchemaUtil.convert(MANIFEST_LIST_SCHEMA, "manifest_file");

    /** 当前提交的快照 ID，用于校验未分配序列号的 manifest 确属本次提交。 */
    private final long commitSnapshotId;
    /** 当前提交分配的序列号，用于填充未分配序列号的 manifest。 */
    private final long sequenceNumber;
    /** 被包装的原始 {@link ManifestFile}。 */
    private ManifestFile wrapped = null;

    /**
     * 构造包装器。
     *
     * @param commitSnapshotId 当前提交的快照 ID
     * @param sequenceNumber 当前提交分配的序列号
     */
    IndexedManifestFile(long commitSnapshotId, long sequenceNumber) {
      this.commitSnapshotId = commitSnapshotId;
      this.sequenceNumber = sequenceNumber;
    }

    /**
     * 绑定待写入的 {@link ManifestFile}。
     *
     * @param file 待包装的 manifest 文件
     * @return 当前包装器自身
     */
    public ManifestFile wrap(ManifestFile file) {
      this.wrapped = file;
      return this;
    }

    /** @return 此包装器对应的 Avro schema */
    @Override
    public org.apache.avro.Schema getSchema() {
      return AVRO_SCHEMA;
    }

    /** 不支持通过 put 修改包装器，始终抛出异常。 */
    @Override
    public void put(int i, Object v) {
      throw new UnsupportedOperationException("Cannot modify IndexedManifestFile wrapper via put");
    }

    /**
     * 按字段序号返回对应字段值。
     *
     * <p>逻辑：对于序列号（pos=4）和最小序列号（pos=5），若 manifest 的序列号为未分配 （{@link
     * ManifestWriter#UNASSIGNED_SEQ}），则校验该 manifest 属于当前提交后返回 当前提交的序列号；否则返回原始值。
     *
     * @param pos 字段序号（0~14）
     * @return 对应字段的值
     */
    @Override
    public Object get(int pos) {
      switch (pos) {
        case 0:
          return wrapped.path();
        case 1:
          return wrapped.length();
        case 2:
          return wrapped.partitionSpecId();
        case 3:
          return wrapped.content().id();
        case 4:
          if (wrapped.sequenceNumber() == ManifestWriter.UNASSIGNED_SEQ) {
            // if the sequence number is being assigned here, then the manifest must be created by
            // the current
            // operation. to validate this, check that the snapshot id matches the current commit
            Preconditions.checkState(
                commitSnapshotId == wrapped.snapshotId(),
                "Found unassigned sequence number for a manifest from snapshot: %s",
                wrapped.snapshotId());
            return sequenceNumber;
          } else {
            return wrapped.sequenceNumber();
          }
        case 5:
          if (wrapped.minSequenceNumber() == ManifestWriter.UNASSIGNED_SEQ) {
            // same sanity check as above
            Preconditions.checkState(
                commitSnapshotId == wrapped.snapshotId(),
                "Found unassigned sequence number for a manifest from snapshot: %s",
                wrapped.snapshotId());
            // if the min sequence number is not determined, then there was no assigned sequence
            // number for any file
            // written to the wrapped manifest. replace the unassigned sequence number with the one
            // for this commit
            return sequenceNumber;
          } else {
            return wrapped.minSequenceNumber();
          }
        case 6:
          return wrapped.snapshotId();
        case 7:
          return wrapped.addedFilesCount();
        case 8:
          return wrapped.existingFilesCount();
        case 9:
          return wrapped.deletedFilesCount();
        case 10:
          return wrapped.addedRowsCount();
        case 11:
          return wrapped.existingRowsCount();
        case 12:
          return wrapped.deletedRowsCount();
        case 13:
          return wrapped.partitions();
        case 14:
          return wrapped.keyMetadata();
        default:
          throw new UnsupportedOperationException("Unknown field ordinal: " + pos);
      }
    }

    /** @return manifest 文件路径 */
    @Override
    public String path() {
      return wrapped.path();
    }

    /** @return manifest 文件长度 */
    @Override
    public long length() {
      return wrapped.length();
    }

    /** @return 分区规格 ID */
    @Override
    public int partitionSpecId() {
      return wrapped.partitionSpecId();
    }

    /** @return manifest 内容类型（数据/删除） */
    @Override
    public ManifestContent content() {
      return wrapped.content();
    }

    /** @return 序列号 */
    @Override
    public long sequenceNumber() {
      return wrapped.sequenceNumber();
    }

    /** @return 最小序列号 */
    @Override
    public long minSequenceNumber() {
      return wrapped.minSequenceNumber();
    }

    /** @return 快照 ID */
    @Override
    public Long snapshotId() {
      return wrapped.snapshotId();
    }

    /** @return 是否包含新增文件 */
    @Override
    public boolean hasAddedFiles() {
      return wrapped.hasAddedFiles();
    }

    /** @return 新增文件数 */
    @Override
    public Integer addedFilesCount() {
      return wrapped.addedFilesCount();
    }

    /** @return 新增行数 */
    @Override
    public Long addedRowsCount() {
      return wrapped.addedRowsCount();
    }

    /** @return 是否包含已存在文件 */
    @Override
    public boolean hasExistingFiles() {
      return wrapped.hasExistingFiles();
    }

    /** @return 已存在文件数 */
    @Override
    public Integer existingFilesCount() {
      return wrapped.existingFilesCount();
    }

    /** @return 已存在行数 */
    @Override
    public Long existingRowsCount() {
      return wrapped.existingRowsCount();
    }

    /** @return 是否包含已删除文件 */
    @Override
    public boolean hasDeletedFiles() {
      return wrapped.hasDeletedFiles();
    }

    /** @return 已删除文件数 */
    @Override
    public Integer deletedFilesCount() {
      return wrapped.deletedFilesCount();
    }

    /** @return 已删除行数 */
    @Override
    public Long deletedRowsCount() {
      return wrapped.deletedRowsCount();
    }

    /** @return 分区字段摘要列表 */
    @Override
    public List<PartitionFieldSummary> partitions() {
      return wrapped.partitions();
    }

    /** @return 加密密钥元数据 */
    @Override
    public ByteBuffer keyMetadata() {
      return wrapped.keyMetadata();
    }

    /** @return 被包装对象的副本 */
    @Override
    public ManifestFile copy() {
      return wrapped.copy();
    }
  }

  /**
   * 根据 partition 类型构造 v2 manifest entry 的 Avro schema。
   *
   * @param partitionType 分区类型描述
   * @return v2 manifest entry schema
   */
  static Schema entrySchema(Types.StructType partitionType) {
    return wrapFileSchema(fileType(partitionType));
  }

  /**
   * 将 data_file schema 包装为 v2 manifest entry schema，附加 status、snapshot_id、
   * sequence_number、file_sequence_number 字段。
   *
   * <p>逻辑：用于构建投影 schema，相比 v1 多了序列号与文件序列号字段。
   *
   * @param fileSchema 数据文件 schema
   * @return v2 manifest entry schema
   */
  static Schema wrapFileSchema(Types.StructType fileSchema) {
    // this is used to build projection schemas
    return new Schema(
        ManifestEntry.STATUS,
        ManifestEntry.SNAPSHOT_ID,
        ManifestEntry.SEQUENCE_NUMBER,
        ManifestEntry.FILE_SEQUENCE_NUMBER,
        required(ManifestEntry.DATA_FILE_ID, "data_file", fileSchema));
  }

  /**
   * 构造 v2 数据/删除文件的 schema（{@link Types.StructType}）。
   *
   * <p>逻辑：相比 v1 增加了 content（文件内容类型）与 equality_ids（等值删除字段 ID 列表）字段， 用于支持 v2 的删除文件语义。
   *
   * @param partitionType 分区类型描述
   * @return v2 文件 schema
   */
  static Types.StructType fileType(Types.StructType partitionType) {
    return Types.StructType.of(
        DataFile.CONTENT.asRequired(),
        DataFile.FILE_PATH,
        DataFile.FILE_FORMAT,
        required(
            DataFile.PARTITION_ID, DataFile.PARTITION_NAME, partitionType, DataFile.PARTITION_DOC),
        DataFile.RECORD_COUNT,
        DataFile.FILE_SIZE,
        DataFile.COLUMN_SIZES,
        DataFile.VALUE_COUNTS,
        DataFile.NULL_VALUE_COUNTS,
        DataFile.NAN_VALUE_COUNTS,
        DataFile.LOWER_BOUNDS,
        DataFile.UPPER_BOUNDS,
        DataFile.KEY_METADATA,
        DataFile.SPLIT_OFFSETS,
        DataFile.EQUALITY_IDS,
        DataFile.SORT_ORDER_ID);
  }

  /**
   * 将任意 {@link ManifestEntry} 实现包装为按 v2 schema 写入 Avro 的 {@link IndexedRecord}。
   *
   * <p>设计意图：在写入 manifest entry 时处理序列号继承逻辑——若 entry 的数据序列号为 null， 则校验该 entry 属于当前提交且状态为
   * ADDED，使其在读取时继承当前提交的序列号。
   *
   * @param <F> 文件内容类型
   */
  static class IndexedManifestEntry<F extends ContentFile<F>>
      implements ManifestEntry<F>, IndexedRecord {
    /** 由 {@link #entrySchema} 转换而来的 Avro schema。 */
    private final org.apache.avro.Schema avroSchema;
    /** 当前提交的快照 ID，用于校验未分配序列号的 entry 确属本次提交。 */
    private final Long commitSnapshotId;
    /** 用于包装嵌套 data_file 字段的包装器。 */
    private final IndexedDataFile<?> fileWrapper;
    /** 被包装的实际 {@link ManifestEntry} 实现。 */
    private ManifestEntry<F> wrapped = null;

    /**
     * 构造 manifest entry 包装器。
     *
     * @param commitSnapshotId 当前提交的快照 ID
     * @param partitionType 分区类型描述，用于构造 schema
     */
    IndexedManifestEntry(Long commitSnapshotId, Types.StructType partitionType) {
      this.avroSchema = AvroSchemaUtil.convert(entrySchema(partitionType), "manifest_entry");
      this.commitSnapshotId = commitSnapshotId;
      this.fileWrapper = new IndexedDataFile<>(partitionType);
    }

    /**
     * 绑定待写入的 manifest entry。
     *
     * @param entry 待包装的 manifest entry
     * @return 当前包装器自身
     */
    public IndexedManifestEntry<F> wrap(ManifestEntry<F> entry) {
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
     * 按字段序号返回 v2 schema 对应的字段值。
     *
     * <p>逻辑：0 映射到 status、1 映射到 snapshotId、2 映射到 data_sequence_number（若为 null 则校验快照 ID 与状态后返回 null
     * 以触发继承）、3 映射到 file_sequence_number、4 映射到 data_file（通过 {@link IndexedDataFile} 包装）；其余序号抛出异常。
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
          if (wrapped.dataSequenceNumber() == null) {
            // if the entry's data sequence number is null,
            // then it will inherit the sequence number of the current commit.
            // to validate that this is correct, check that the snapshot id is either null (will
            // also be inherited) or that it matches the id of the current commit.
            Preconditions.checkState(
                wrapped.snapshotId() == null || wrapped.snapshotId().equals(commitSnapshotId),
                "Found unassigned sequence number for an entry from snapshot: %s",
                wrapped.snapshotId());

            // inheritance should work only for ADDED entries
            Preconditions.checkState(
                wrapped.status() == Status.ADDED,
                "Only entries with status ADDED can have null sequence number");

            return null;
          }
          return wrapped.dataSequenceNumber();
        case 3:
          return wrapped.fileSequenceNumber();
        case 4:
          return fileWrapper.wrap(wrapped.file());
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

    /** @return 数据序列号，委托给被包装对象 */
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

    /** @return 文件序列号，委托给被包装对象 */
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

    /** @return 关联的文件，委托给被包装对象 */
    @Override
    public F file() {
      return wrapped.file();
    }

    /** @return 被包装对象的副本 */
    @Override
    public ManifestEntry<F> copy() {
      return wrapped.copy();
    }

    /** @return 被包装对象去除统计信息的副本 */
    @Override
    public ManifestEntry<F> copyWithoutStats() {
      return wrapped.copyWithoutStats();
    }
  }

  /**
   * 将任意 {@link ContentFile}（{@link DataFile} 或 {@link DeleteFile}）实现包装为按 v2 schema 写入 Avro 的 {@link
   * IndexedRecord}。
   *
   * <p>设计意图：用于以 v2 schema 写入 data_file/delete_file 字段，支持 v2 新增的 content （文件内容类型）与
   * equality_ids（等值删除字段 ID 列表）字段。
   *
   * @param <F> 文件内容类型
   */
  static class IndexedDataFile<F> implements ContentFile<F>, IndexedRecord {
    /** 由 {@link #fileType} 转换而来的 Avro schema。 */
    private final org.apache.avro.Schema avroSchema;
    /** 用于包装 partition 字段的 {@link IndexedStructLike}。 */
    private final IndexedStructLike partitionWrapper;
    /** 被包装的实际 {@link ContentFile} 实现。 */
    private ContentFile<F> wrapped = null;

    /**
     * 构造 data/delete file 包装器。
     *
     * @param partitionType 分区类型描述，用于构造 schema
     */
    IndexedDataFile(Types.StructType partitionType) {
      this.avroSchema = AvroSchemaUtil.convert(fileType(partitionType), "data_file");
      this.partitionWrapper = new IndexedStructLike(avroSchema.getField("partition").schema());
    }

    /**
     * 绑定待写入的文件。
     *
     * @param file 待包装的文件
     * @return 当前包装器自身
     */
    @SuppressWarnings("unchecked")
    IndexedDataFile<F> wrap(ContentFile<?> file) {
      this.wrapped = (ContentFile<F>) file;
      return this;
    }

    /** @return 包装器对应的 Avro schema */
    @Override
    public org.apache.avro.Schema getSchema() {
      return avroSchema;
    }

    /**
     * 按字段序号返回 v2 schema 对应的字段值。
     *
     * <p>逻辑：0~15 分别映射到 content、path、format、partition、recordCount、fileSizeInBytes、
     * columnSizes、valueCounts、nullValueCounts、nanValueCounts、lowerBounds、upperBounds、
     * keyMetadata、splitOffsets、equalityFieldIds、sortOrderId；其余序号抛出 {@link
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
          return wrapped.content().id();
        case 1:
          return wrapped.path().toString();
        case 2:
          return wrapped.format() != null ? wrapped.format().toString() : null;
        case 3:
          return partitionWrapper.wrap(wrapped.partition());
        case 4:
          return wrapped.recordCount();
        case 5:
          return wrapped.fileSizeInBytes();
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
          return wrapped.equalityFieldIds();
        case 15:
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

    /** @return 文件位置（v2 包装器不持久化该字段，返回 null） */
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

    /** @return 等值删除字段 ID 列表，委托给被包装对象 */
    @Override
    public List<Integer> equalityFieldIds() {
      return wrapped.equalityFieldIds();
    }

    /** @return 排序规则 ID，委托给被包装对象 */
    @Override
    public Integer sortOrderId() {
      return wrapped.sortOrderId();
    }

    /** @return 数据序列号，委托给被包装对象 */
    @Override
    public Long dataSequenceNumber() {
      return wrapped.dataSequenceNumber();
    }

    /** @return 文件序列号，委托给被包装对象 */
    @Override
    public Long fileSequenceNumber() {
      return wrapped.fileSequenceNumber();
    }

    /**
     * 不支持复制包装器，始终抛出异常。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public F copy() {
      throw new UnsupportedOperationException("Cannot copy IndexedDataFile wrapper");
    }

    /**
     * 不支持复制去除统计信息的包装器，始终抛出异常。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public F copyWithoutStats() {
      throw new UnsupportedOperationException("Cannot copy IndexedDataFile wrapper");
    }
  }
}
