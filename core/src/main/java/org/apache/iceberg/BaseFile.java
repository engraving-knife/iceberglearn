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

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ArrayUtil;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.SerializableMap;

/**
 * 数据文件（{@link DataFile}）与删除文件（{@link DeleteFile}）的公共基类。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 Iceberg 表内容文件的核心数据模型。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link ContentFile} 接口，承载 spec id、文件路径、格式、分区值、记录数、文件大小、 列级统计（值计数、上下界等）、split 偏移、equality
 *       字段、加密 key 元数据等所有内容文件元数据。
 *   <li>实现 Avro 的 {@link IndexedRecord} 与 Iceberg 的 {@link StructLike}，使本类既能以 Avro 反射方式读写 manifest
 *       文件，又能以结构化形式参与表达式投影。
 *   <li>支持 schema 投影：通过 {@code fromProjectionPos} 把投影后字段顺序映射回完整字段顺序， 兼容 manifest 列裁剪读取。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>泛型 F 用于子类以自身类型返回（curiously recurring template），保证 {@link #copy()} 等返回类型。
 *   <li>同时实现 Avro 的 {@code SpecificData.SchemaConstructable}：Avro 反射读取 manifest 时， 通过 schema
 *       构造方法实例化，实现字段裁剪与版本兼容。
 *   <li>序列化友好：metrics 中 ByteBuffer 转为 byte[]，便于 Java 序列化与跨进程传递。
 * </ul>
 *
 * <p>上下游关系：被 {@link GenericDataFile}、{@link GenericDeleteFile} 等具体子类继承； 被 manifest 读写器（{@code
 * ManifestReader}/{@code ManifestWriter}）按 Avro 反射读写； 被扫描、合并、清理等流程消费。
 */
abstract class BaseFile<F>
    implements ContentFile<F>,
        IndexedRecord,
        StructLike,
        SpecificData.SchemaConstructable,
        Serializable {
  static final Types.StructType EMPTY_STRUCT_TYPE = Types.StructType.of();
  static final PartitionData EMPTY_PARTITION_DATA =
      new PartitionData(EMPTY_STRUCT_TYPE) {
        @Override
        public PartitionData copy() {
          return this; // this does not change
        }
      };

  private int[] fromProjectionPos;
  private Types.StructType partitionType;

  private Long fileOrdinal = null;
  private int partitionSpecId = -1;
  private FileContent content = FileContent.DATA;
  private String filePath = null;
  private FileFormat format = null;
  private PartitionData partitionData = null;
  private Long recordCount = null;
  private long fileSizeInBytes = -1L;
  private Long dataSequenceNumber = null;
  private Long fileSequenceNumber = null;

  // optional fields
  private Map<Integer, Long> columnSizes = null;
  private Map<Integer, Long> valueCounts = null;
  private Map<Integer, Long> nullValueCounts = null;
  private Map<Integer, Long> nanValueCounts = null;
  private Map<Integer, ByteBuffer> lowerBounds = null;
  private Map<Integer, ByteBuffer> upperBounds = null;
  private long[] splitOffsets = null;
  private int[] equalityIds = null;
  private byte[] keyMetadata = null;
  private Integer sortOrderId;

  // cached schema
  private transient Schema avroSchema = null;

  /**
   * Avro 反射读取 manifest 时使用：基于 Avro schema 构造实例，并按投影字段建立位置映射。
   *
   * <p>逻辑：将 Avro schema 转回 Iceberg {@link Types.StructType}；提取 partition 字段类型； 遍历 schema
   * 中的字段，对每个字段在完整字段集合（含 ROW_POSITION）中查找位置，建立 {@code fromProjectionPos} 映射表，找不到则抛 {@link
   * IllegalArgumentException}。
   *
   * <p>设计要点：当 manifest schema 是投影后的子集时（如只读部分列），通过映射表把投影顺序 转回完整顺序，复用统一 {@code put}/{@code get} 实现。
   *
   * @param avroSchema Avro 反射使用的 schema（可能为投影 schema）
   */
  BaseFile(Schema avroSchema) {
    this.avroSchema = avroSchema;

    Types.StructType schema = AvroSchemaUtil.convert(avroSchema).asNestedType().asStructType();

    // partition type may be null if the field was not projected
    Type partType = schema.fieldType("partition");
    if (partType != null) {
      this.partitionType = partType.asNestedType().asStructType();
    } else {
      this.partitionType = EMPTY_STRUCT_TYPE;
    }

    List<Types.NestedField> fields = schema.fields();
    List<Types.NestedField> allFields = Lists.newArrayList();
    allFields.addAll(DataFile.getType(partitionType).fields());
    allFields.add(MetadataColumns.ROW_POSITION);

    this.fromProjectionPos = new int[fields.size()];
    for (int i = 0; i < fromProjectionPos.length; i += 1) {
      boolean found = false;
      for (int j = 0; j < allFields.size(); j += 1) {
        if (fields.get(i).fieldId() == allFields.get(j).fieldId()) {
          found = true;
          fromProjectionPos[i] = j;
        }
      }

      if (!found) {
        throw new IllegalArgumentException("Cannot find projected field: " + fields.get(i));
      }
    }

    this.partitionData = new PartitionData(partitionType);
  }

  /**
   * 全字段构造方法：由 {@code DataFiles.Builder}/{@code DeleteFiles.Builder} 调用以构造完整文件元数据。
   *
   * <p>逻辑：写入必填字段（spec id、content、path、format、partition、size、recordCount）与所有 列级统计、split 偏移、equality
   * 字段、sortOrderId、key 元数据；partition 为 null 时使用 {@link #EMPTY_PARTITION_DATA} 占位（非分区表）。
   *
   * <p>设计要点：ByteBuffer 类型的 lower/upper bounds 与 keyMetadata 转为可序列化形式 （{@link
   * SerializableByteBufferMap}、byte[]），保证跨进程传输稳定。
   *
   * @param specId 分区 spec id
   * @param content 文件内容类型（DATA/POSITION_DELETES/EQUALITY_DELETES）
   * @param filePath 文件路径
   * @param format 文件格式（PARQUET/AVRO/ORC）
   * @param partition 分区值（非分区表传 null）
   * @param fileSizeInBytes 文件字节数
   * @param recordCount 记录数
   * @param columnSizes 列字节数映射
   * @param valueCounts 列值计数
   * @param nullValueCounts 列 null 值计数
   * @param nanValueCounts 列 NaN 值计数
   * @param lowerBounds 列下界
   * @param upperBounds 列上界
   * @param splitOffsets 文件 split 偏移列表
   * @param equalityFieldIds equality delete 字段 id 数组
   * @param sortOrderId 排序顺序 id
   * @param keyMetadata 加密 key 元数据
   */
  BaseFile(
      int specId,
      FileContent content,
      String filePath,
      FileFormat format,
      PartitionData partition,
      long fileSizeInBytes,
      long recordCount,
      Map<Integer, Long> columnSizes,
      Map<Integer, Long> valueCounts,
      Map<Integer, Long> nullValueCounts,
      Map<Integer, Long> nanValueCounts,
      Map<Integer, ByteBuffer> lowerBounds,
      Map<Integer, ByteBuffer> upperBounds,
      List<Long> splitOffsets,
      int[] equalityFieldIds,
      Integer sortOrderId,
      ByteBuffer keyMetadata) {
    this.partitionSpecId = specId;
    this.content = content;
    this.filePath = filePath;
    this.format = format;

    // this constructor is used by DataFiles.Builder, which passes null for unpartitioned data
    if (partition == null) {
      this.partitionData = EMPTY_PARTITION_DATA;
      this.partitionType = EMPTY_PARTITION_DATA.getPartitionType();
    } else {
      this.partitionData = partition;
      this.partitionType = partition.getPartitionType();
    }

    // this will throw NPE if metrics.recordCount is null
    this.recordCount = recordCount;
    this.fileSizeInBytes = fileSizeInBytes;
    this.columnSizes = columnSizes;
    this.valueCounts = valueCounts;
    this.nullValueCounts = nullValueCounts;
    this.nanValueCounts = nanValueCounts;
    this.lowerBounds = SerializableByteBufferMap.wrap(lowerBounds);
    this.upperBounds = SerializableByteBufferMap.wrap(upperBounds);
    this.splitOffsets = ArrayUtil.toLongArray(splitOffsets);
    this.equalityIds = equalityFieldIds;
    this.sortOrderId = sortOrderId;
    this.keyMetadata = ByteBuffers.toByteArray(keyMetadata);
  }

  /**
   * 拷贝构造方法：用于在扫描/缓存场景中复制文件元数据。
   *
   * <p>逻辑：复制基础字段、partition 数据；若 {@code fullCopy} 为 true 则深拷贝所有列级统计， 否则置
   * null（用于减少传输负担的场景，如任务序列化到引擎）。ByteBuffer 类统计通过 {@link SerializableByteBufferMap} 重新包装，byte[] 类型通过
   * {@link Arrays#copyOf} 拷贝。
   *
   * @param toCopy 源文件对象
   * @param fullCopy 是否完整拷贝（含列级统计）
   */
  BaseFile(BaseFile<F> toCopy, boolean fullCopy) {
    this.fileOrdinal = toCopy.fileOrdinal;
    this.partitionSpecId = toCopy.partitionSpecId;
    this.content = toCopy.content;
    this.filePath = toCopy.filePath;
    this.format = toCopy.format;
    this.partitionData = toCopy.partitionData.copy();
    this.partitionType = toCopy.partitionType;
    this.recordCount = toCopy.recordCount;
    this.fileSizeInBytes = toCopy.fileSizeInBytes;
    if (fullCopy) {
      this.columnSizes = SerializableMap.copyOf(toCopy.columnSizes);
      this.valueCounts = SerializableMap.copyOf(toCopy.valueCounts);
      this.nullValueCounts = SerializableMap.copyOf(toCopy.nullValueCounts);
      this.nanValueCounts = SerializableMap.copyOf(toCopy.nanValueCounts);
      this.lowerBounds = SerializableByteBufferMap.wrap(SerializableMap.copyOf(toCopy.lowerBounds));
      this.upperBounds = SerializableByteBufferMap.wrap(SerializableMap.copyOf(toCopy.upperBounds));
    } else {
      this.columnSizes = null;
      this.valueCounts = null;
      this.nullValueCounts = null;
      this.nanValueCounts = null;
      this.lowerBounds = null;
      this.upperBounds = null;
    }
    this.fromProjectionPos = toCopy.fromProjectionPos;
    this.keyMetadata =
        toCopy.keyMetadata == null
            ? null
            : Arrays.copyOf(toCopy.keyMetadata, toCopy.keyMetadata.length);
    this.splitOffsets =
        toCopy.splitOffsets == null
            ? null
            : Arrays.copyOf(toCopy.splitOffsets, toCopy.splitOffsets.length);
    this.equalityIds =
        toCopy.equalityIds != null
            ? Arrays.copyOf(toCopy.equalityIds, toCopy.equalityIds.length)
            : null;
    this.sortOrderId = toCopy.sortOrderId;
    this.dataSequenceNumber = toCopy.dataSequenceNumber;
    this.fileSequenceNumber = toCopy.fileSequenceNumber;
  }

  /** Java 序列化使用的空构造方法。 */
  BaseFile() {}

  /** 返回该文件所属的分区 spec id。 */
  @Override
  public int specId() {
    return partitionSpecId;
  }

  /** 设置分区 spec id（合并 manifest 等场景下重写）。 */
  void setSpecId(int specId) {
    this.partitionSpecId = specId;
  }

  /** 返回数据序列号（写入数据时的 snapshot 序列号，可能为 null）。 */
  @Override
  public Long dataSequenceNumber() {
    return dataSequenceNumber;
  }

  /** 设置数据序列号（manifest 写入时回填）。 */
  public void setDataSequenceNumber(Long dataSequenceNumber) {
    this.dataSequenceNumber = dataSequenceNumber;
  }

  /** 返回文件序列号（文件在表中出现的全局序列号，可能为 null）。 */
  @Override
  public Long fileSequenceNumber() {
    return fileSequenceNumber;
  }

  /** 设置文件序列号。 */
  public void setFileSequenceNumber(Long fileSequenceNumber) {
    this.fileSequenceNumber = fileSequenceNumber;
  }

  /**
   * 子类提供按分区结构生成 Avro schema 的能力，用于 Avro 反射读写 manifest。
   *
   * @param partitionStruct 分区结构类型
   * @return 该子类对应的 Avro schema
   */
  protected abstract Schema getAvroSchema(Types.StructType partitionStruct);

  /**
   * 返回当前实例的 Avro schema，懒初始化。
   *
   * <p>逻辑：若尚未缓存，则调用 {@link #getAvroSchema(Types.StructType)} 按 partitionType 生成并缓存。
   *
   * @return Avro schema
   */
  @Override
  public Schema getSchema() {
    if (avroSchema == null) {
      this.avroSchema = getAvroSchema(partitionType);
    }
    return avroSchema;
  }

  /**
   * Avro {@link IndexedRecord} 接口实现：按位置写入字段值。
   *
   * <p>逻辑：先按 {@code fromProjectionPos} 把投影序号映射回完整字段序号，再按序号 switch 写入 对应字段；ByteBuffer 类统计通过 {@link
   * SerializableByteBufferMap#wrap} 包装为可序列化形式， keyMetadata 转为 byte[]。未知序号（更新版本新增字段）会被忽略以保证向后兼容。
   *
   * @param i 字段位置（投影后）
   * @param value 字段值
   */
  @Override
  @SuppressWarnings("unchecked")
  public void put(int i, Object value) {
    int pos = i;
    // if the schema was projected, map the incoming ordinal to the expected one
    if (fromProjectionPos != null) {
      pos = fromProjectionPos[i];
    }
    switch (pos) {
      case 0:
        this.content = value != null ? FileContent.values()[(Integer) value] : FileContent.DATA;
        return;
      case 1:
        // always coerce to String for Serializable
        this.filePath = value.toString();
        return;
      case 2:
        this.format = FileFormat.fromString(value.toString());
        return;
      case 3:
        this.partitionSpecId = (value != null) ? (Integer) value : -1;
        return;
      case 4:
        this.partitionData = (PartitionData) value;
        return;
      case 5:
        this.recordCount = (Long) value;
        return;
      case 6:
        this.fileSizeInBytes = (Long) value;
        return;
      case 7:
        this.columnSizes = (Map<Integer, Long>) value;
        return;
      case 8:
        this.valueCounts = (Map<Integer, Long>) value;
        return;
      case 9:
        this.nullValueCounts = (Map<Integer, Long>) value;
        return;
      case 10:
        this.nanValueCounts = (Map<Integer, Long>) value;
        return;
      case 11:
        this.lowerBounds = SerializableByteBufferMap.wrap((Map<Integer, ByteBuffer>) value);
        return;
      case 12:
        this.upperBounds = SerializableByteBufferMap.wrap((Map<Integer, ByteBuffer>) value);
        return;
      case 13:
        this.keyMetadata = ByteBuffers.toByteArray((ByteBuffer) value);
        return;
      case 14:
        this.splitOffsets = ArrayUtil.toLongArray((List<Long>) value);
        return;
      case 15:
        this.equalityIds = ArrayUtil.toIntArray((List<Integer>) value);
        return;
      case 16:
        this.sortOrderId = (Integer) value;
        return;
      case 17:
        this.fileOrdinal = (long) value;
        return;
      default:
        // ignore the object, it must be from a newer version of the format
    }
  }

  /** {@link StructLike#set} 实现：委托 {@link #put}。 */
  @Override
  public <T> void set(int pos, T value) {
    put(pos, value);
  }

  /**
   * Avro {@link IndexedRecord} 接口实现：按位置读取字段值。
   *
   * <p>逻辑：先按 {@code fromProjectionPos} 把投影序号映射回完整字段序号，再 switch 返回对应字段； 未知序号抛 {@link
   * UnsupportedOperationException}，与 put 的“忽略未知”策略不同—— 读取未知字段意味着 schema 不兼容，应立即暴露问题。
   *
   * @param i 字段位置（投影后）
   * @return 字段值
   */
  @Override
  public Object get(int i) {
    int pos = i;
    // if the schema was projected, map the incoming ordinal to the expected one
    if (fromProjectionPos != null) {
      pos = fromProjectionPos[i];
    }
    switch (pos) {
      case 0:
        return content.id();
      case 1:
        return filePath;
      case 2:
        return format != null ? format.toString() : null;
      case 3:
        return partitionSpecId;
      case 4:
        return partitionData;
      case 5:
        return recordCount;
      case 6:
        return fileSizeInBytes;
      case 7:
        return columnSizes;
      case 8:
        return valueCounts;
      case 9:
        return nullValueCounts;
      case 10:
        return nanValueCounts;
      case 11:
        return lowerBounds;
      case 12:
        return upperBounds;
      case 13:
        return keyMetadata();
      case 14:
        return splitOffsets();
      case 15:
        return equalityFieldIds();
      case 16:
        return sortOrderId;
      case 17:
        return fileOrdinal;
      default:
        throw new UnsupportedOperationException("Unknown field ordinal: " + pos);
    }
  }

  /** 按指定 Java 类型读取字段值（强转）。 */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    return javaClass.cast(get(pos));
  }

  /** 返回本类结构体的字段数（按完整 schema，不含投影）。 */
  @Override
  public int size() {
    return DataFile.getType(EMPTY_STRUCT_TYPE).fields().size();
  }

  /** 返回文件在 manifest 中的行位置。 */
  @Override
  public Long pos() {
    return fileOrdinal;
  }

  /** 返回文件内容类型（数据/位置删除/等值删除）。 */
  @Override
  public FileContent content() {
    return content;
  }

  /** 返回文件路径。 */
  @Override
  public CharSequence path() {
    return filePath;
  }

  /** 返回文件格式。 */
  @Override
  public FileFormat format() {
    return format;
  }

  /** 返回文件分区值。 */
  @Override
  public StructLike partition() {
    return partitionData;
  }

  /** 返回文件记录数。 */
  @Override
  public long recordCount() {
    return recordCount;
  }

  /** 返回文件字节数。 */
  @Override
  public long fileSizeInBytes() {
    return fileSizeInBytes;
  }

  /** 返回列字节数映射（只读视图）。 */
  @Override
  public Map<Integer, Long> columnSizes() {
    return toReadableMap(columnSizes);
  }

  /** 返回列值计数（只读视图）。 */
  @Override
  public Map<Integer, Long> valueCounts() {
    return toReadableMap(valueCounts);
  }

  /** 返回列 null 值计数（只读视图）。 */
  @Override
  public Map<Integer, Long> nullValueCounts() {
    return toReadableMap(nullValueCounts);
  }

  /** 返回列 NaN 值计数（只读视图）。 */
  @Override
  public Map<Integer, Long> nanValueCounts() {
    return toReadableMap(nanValueCounts);
  }

  /** 返回列下界（只读视图）。 */
  @Override
  public Map<Integer, ByteBuffer> lowerBounds() {
    return toReadableByteBufferMap(lowerBounds);
  }

  /** 返回列上界（只读视图）。 */
  @Override
  public Map<Integer, ByteBuffer> upperBounds() {
    return toReadableByteBufferMap(upperBounds);
  }

  /** 返回加密 key 元数据（byte 数组转 ByteBuffer 视图）。 */
  @Override
  public ByteBuffer keyMetadata() {
    return keyMetadata != null ? ByteBuffer.wrap(keyMetadata) : null;
  }

  /**
   * 返回文件 split 偏移列表（只读视图）。
   *
   * <p>逻辑：仅当 split offsets 完整有效时（最后一个 offset 不超过文件大小）才返回，否则返回 null。
   *
   * @return split 偏移列表或 null
   */
  @Override
  public List<Long> splitOffsets() {
    if (hasWellDefinedOffsets()) {
      return ArrayUtil.toUnmodifiableLongList(splitOffsets);
    }

    return null;
  }

  /**
   * 返回原始的 split 偏移数组（内部使用，避免 List 装箱开销）。
   *
   * @return split 偏移数组或 null
   */
  long[] splitOffsetArray() {
    if (hasWellDefinedOffsets()) {
      return splitOffsets;
    }

    return null;
  }

  /**
   * 判断 split offsets 是否完整有效。
   *
   * <p>逻辑：split offsets 非空、非 0 长度、且最后一个偏移不超过文件大小。 最后一个偏移超过文件大小说明 split 元数据已损坏，不应使用。
   */
  private boolean hasWellDefinedOffsets() {
    // If the last split offset is past the file size this means the split offsets are corrupted and
    // should not be used
    return splitOffsets != null
        && splitOffsets.length != 0
        && splitOffsets[splitOffsets.length - 1] < fileSizeInBytes;
  }

  /** 返回 equality delete 涉及的字段 id 列表。 */
  @Override
  public List<Integer> equalityFieldIds() {
    return ArrayUtil.toIntList(equalityIds);
  }

  /** 返回排序顺序 id。 */
  @Override
  public Integer sortOrderId() {
    return sortOrderId;
  }

  /**
   * 把内部 Map 转换为对外只读视图：若已是 {@link SerializableMap} 则返回其不可变视图， 否则用 {@link
   * Collections#unmodifiableMap} 包装。null 直接返回 null。
   */
  private static <K, V> Map<K, V> toReadableMap(Map<K, V> map) {
    if (map == null) {
      return null;
    } else if (map instanceof SerializableMap) {
      return ((SerializableMap<K, V>) map).immutableMap();
    } else {
      return Collections.unmodifiableMap(map);
    }
  }

  /** 与 {@link #toReadableMap} 类似，但针对 ByteBuffer 值类型的 Map。 */
  private static Map<Integer, ByteBuffer> toReadableByteBufferMap(Map<Integer, ByteBuffer> map) {
    if (map == null) {
      return null;
    } else if (map instanceof SerializableByteBufferMap) {
      return ((SerializableByteBufferMap) map).immutableMap();
    } else {
      return Collections.unmodifiableMap(map);
    }
  }

  /** 返回文件元数据的可读字符串表示，key 元数据以 "(redacted)" 隐藏。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("content", content.toString().toLowerCase(Locale.ROOT))
        .add("file_path", filePath)
        .add("file_format", format)
        .add("spec_id", specId())
        .add("partition", partitionData)
        .add("record_count", recordCount)
        .add("file_size_in_bytes", fileSizeInBytes)
        .add("column_sizes", columnSizes)
        .add("value_counts", valueCounts)
        .add("null_value_counts", nullValueCounts)
        .add("nan_value_counts", nanValueCounts)
        .add("lower_bounds", lowerBounds)
        .add("upper_bounds", upperBounds)
        .add("key_metadata", keyMetadata == null ? "null" : "(redacted)")
        .add("split_offsets", splitOffsets == null ? "null" : splitOffsets())
        .add("equality_ids", equalityIds == null ? "null" : equalityFieldIds())
        .add("sort_order_id", sortOrderId)
        .add("data_sequence_number", dataSequenceNumber == null ? "null" : dataSequenceNumber)
        .add("file_sequence_number", fileSequenceNumber == null ? "null" : fileSequenceNumber)
        .toString();
  }
}
