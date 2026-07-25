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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.specific.SpecificData.SchemaConstructable;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;

/**
 * {@link ManifestFile} 的通用实现：承载单个 manifest 文件的元数据（路径、长度、spec id、内容类型、 序列号、文件/行数统计、分区摘要、加密 key 等）。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 manifest list 中每条记录的具体数据模型。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link ManifestFile} 接口并提供所有字段的访问方法。
 *   <li>实现 Avro {@link IndexedRecord} 与 Iceberg {@link StructLike}，支持 Avro 反射读写 manifest list。
 *   <li>支持 schema 投影：通过 {@code fromProjectionPos} 映射投影顺序回完整顺序。
 *   <li>提供 {@link CopyBuilder} 用于在拷贝时覆盖部分字段（如 snapshotId）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>同时实现 Avro SchemaConstructable：Avro 反射读取 manifest list 时按 schema 构造实例。
 *   <li>lazyLength：从 InputFile 构造时可懒加载文件长度，避免不必要的 IO。
 *   <li>equals/hashCode 仅按 manifestPath 比较，保证 manifest 在集合中按路径去重。
 * </ul>
 *
 * <p>上下游关系：被 {@link ManifestListWriter} 写入、被 {@link ManifestReader} 等读取； 被 snapshot/扫描/清理等流程消费。
 */
public class GenericManifestFile
    implements ManifestFile, StructLike, IndexedRecord, SchemaConstructable, Serializable {
  private static final Schema AVRO_SCHEMA =
      AvroSchemaUtil.convert(ManifestFile.schema(), "manifest_file");

  private transient Schema avroSchema; // not final for Java serialization
  private int[] fromProjectionPos;

  // data fields
  private InputFile file = null;
  private String manifestPath = null;
  private Long length = null;
  private int specId = -1;
  private ManifestContent content = ManifestContent.DATA;
  private long sequenceNumber = 0;
  private long minSequenceNumber = 0;
  private Long snapshotId = null;
  private Integer addedFilesCount = null;
  private Integer existingFilesCount = null;
  private Integer deletedFilesCount = null;
  private Long addedRowsCount = null;
  private Long existingRowsCount = null;
  private Long deletedRowsCount = null;
  private PartitionFieldSummary[] partitions = null;
  private byte[] keyMetadata = null;

  /**
   * Avro 反射读取 manifest list 时使用：基于 Avro schema 构造实例并建立投影字段位置映射。
   *
   * <p>逻辑：把 Avro schema 转回 Iceberg struct，遍历投影字段在完整 schema 中查找位置， 建立 {@code fromProjectionPos} 映射表。
   *
   * @param avroSchema Avro 反射使用的 schema（可能为投影 schema）
   */
  public GenericManifestFile(Schema avroSchema) {
    this.avroSchema = avroSchema;

    List<Types.NestedField> fields = AvroSchemaUtil.convert(avroSchema).asStructType().fields();
    List<Types.NestedField> allFields = ManifestFile.schema().asStruct().fields();

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
  }

  /**
   * 基于 {@link InputFile} 构造 manifest 元数据（长度懒加载）。
   *
   * @param file manifest 文件
   * @param specId 分区 spec id
   */
  GenericManifestFile(InputFile file, int specId) {
    this.avroSchema = AVRO_SCHEMA;
    this.file = file;
    this.manifestPath = file.location();
    this.length = null; // lazily loaded from file
    this.specId = specId;
    this.sequenceNumber = 0;
    this.minSequenceNumber = 0;
    this.snapshotId = null;
    this.addedFilesCount = null;
    this.addedRowsCount = null;
    this.existingFilesCount = null;
    this.existingRowsCount = null;
    this.deletedFilesCount = null;
    this.deletedRowsCount = null;
    this.partitions = null;
    this.fromProjectionPos = null;
    this.keyMetadata = null;
  }

  /**
   * 全字段构造方法：用于从 manifest list 文件读取后构造完整实例。
   *
   * @param path manifest 路径
   * @param length 文件长度
   * @param specId 分区 spec id
   * @param content 内容类型（DATA/DELETES）
   * @param sequenceNumber 序列号
   * @param minSequenceNumber 最小序列号
   * @param snapshotId 关联快照 id
   * @param addedFilesCount 新增文件数
   * @param addedRowsCount 新增行数
   * @param existingFilesCount 已存在文件数
   * @param existingRowsCount 已存在行数
   * @param deletedFilesCount 删除文件数
   * @param deletedRowsCount 删除行数
   * @param partitions 分区字段摘要
   * @param keyMetadata 加密 key 元数据
   */
  public GenericManifestFile(
      String path,
      long length,
      int specId,
      ManifestContent content,
      long sequenceNumber,
      long minSequenceNumber,
      Long snapshotId,
      int addedFilesCount,
      long addedRowsCount,
      int existingFilesCount,
      long existingRowsCount,
      int deletedFilesCount,
      long deletedRowsCount,
      List<PartitionFieldSummary> partitions,
      ByteBuffer keyMetadata) {
    this.avroSchema = AVRO_SCHEMA;
    this.manifestPath = path;
    this.length = length;
    this.specId = specId;
    this.content = content;
    this.sequenceNumber = sequenceNumber;
    this.minSequenceNumber = minSequenceNumber;
    this.snapshotId = snapshotId;
    this.addedFilesCount = addedFilesCount;
    this.addedRowsCount = addedRowsCount;
    this.existingFilesCount = existingFilesCount;
    this.existingRowsCount = existingRowsCount;
    this.deletedFilesCount = deletedFilesCount;
    this.deletedRowsCount = deletedRowsCount;
    this.partitions = partitions == null ? null : partitions.toArray(new PartitionFieldSummary[0]);
    this.fromProjectionPos = null;
    this.keyMetadata = ByteBuffers.toByteArray(keyMetadata);
  }

  /**
   * 拷贝构造方法：深拷贝分区摘要数组与 key 元数据。
   *
   * @param toCopy 源 manifest 文件对象
   */
  private GenericManifestFile(GenericManifestFile toCopy) {
    this.avroSchema = toCopy.avroSchema;
    this.manifestPath = toCopy.manifestPath;
    this.length = toCopy.length;
    this.specId = toCopy.specId;
    this.content = toCopy.content;
    this.sequenceNumber = toCopy.sequenceNumber;
    this.minSequenceNumber = toCopy.minSequenceNumber;
    this.snapshotId = toCopy.snapshotId;
    this.addedFilesCount = toCopy.addedFilesCount;
    this.addedRowsCount = toCopy.addedRowsCount;
    this.existingFilesCount = toCopy.existingFilesCount;
    this.existingRowsCount = toCopy.existingRowsCount;
    this.deletedFilesCount = toCopy.deletedFilesCount;
    this.deletedRowsCount = toCopy.deletedRowsCount;
    if (toCopy.partitions != null) {
      this.partitions =
          Stream.of(toCopy.partitions)
              .map(PartitionFieldSummary::copy)
              .toArray(PartitionFieldSummary[]::new);
    } else {
      this.partitions = null;
    }
    this.fromProjectionPos = toCopy.fromProjectionPos;
    this.keyMetadata =
        toCopy.keyMetadata == null
            ? null
            : Arrays.copyOf(toCopy.keyMetadata, toCopy.keyMetadata.length);
  }

  /** Java 序列化使用的空构造方法。 */
  GenericManifestFile() {}

  /** 返回 manifest 文件路径。 */
  @Override
  public String path() {
    return manifestPath;
  }

  /**
   * 懒加载 manifest 文件长度。
   *
   * <p>逻辑：length 为 null 且 file 非 null 时从 InputFile 读取长度并缓存； 若 file 也为 null（投影读取时未包含 length 字段）则返回
   * null。
   */
  public Long lazyLength() {
    if (length == null) {
      if (file != null) {
        // this was created from an input file and length is lazily loaded
        this.length = file.getLength();
      } else {
        // this was loaded from a file without projecting length, throw an exception
        return null;
      }
    }
    return length;
  }

  /** 返回文件长度（委托 {@link #lazyLength()}）。 */
  @Override
  public long length() {
    return lazyLength();
  }

  /** 返回分区 spec id。 */
  @Override
  public int partitionSpecId() {
    return specId;
  }

  /** 返回 manifest 内容类型（DATA/DELETES）。 */
  @Override
  public ManifestContent content() {
    return content;
  }

  /** 返回序列号。 */
  @Override
  public long sequenceNumber() {
    return sequenceNumber;
  }

  /** 返回最小序列号。 */
  @Override
  public long minSequenceNumber() {
    return minSequenceNumber;
  }

  /** 返回关联快照 id。 */
  @Override
  public Long snapshotId() {
    return snapshotId;
  }

  /** 返回新增文件数。 */
  @Override
  public Integer addedFilesCount() {
    return addedFilesCount;
  }

  /** 返回新增行数。 */
  @Override
  public Long addedRowsCount() {
    return addedRowsCount;
  }

  /** 返回已存在文件数。 */
  @Override
  public Integer existingFilesCount() {
    return existingFilesCount;
  }

  /** 返回已存在行数。 */
  @Override
  public Long existingRowsCount() {
    return existingRowsCount;
  }

  /** 返回删除文件数。 */
  @Override
  public Integer deletedFilesCount() {
    return deletedFilesCount;
  }

  /** 返回删除行数。 */
  @Override
  public Long deletedRowsCount() {
    return deletedRowsCount;
  }

  /** 返回分区字段摘要列表。 */
  @Override
  public List<PartitionFieldSummary> partitions() {
    return partitions == null ? null : Arrays.asList(partitions);
  }

  /** 返回加密 key 元数据。 */
  @Override
  public ByteBuffer keyMetadata() {
    return keyMetadata == null ? null : ByteBuffer.wrap(keyMetadata);
  }

  /** 返回结构体字段数。 */
  @Override
  public int size() {
    return ManifestFile.schema().columns().size();
  }

  /** 按位置与 Java 类型读取字段值。 */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    return javaClass.cast(get(pos));
  }

  /**
   * {@link StructLike} 实现：按位置读取字段值。
   *
   * <p>逻辑：先按 {@code fromProjectionPos} 映射回完整字段序号，再 switch 返回对应字段； 未知序号抛 {@link
   * UnsupportedOperationException}。
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
        return manifestPath;
      case 1:
        return lazyLength();
      case 2:
        return specId;
      case 3:
        return content.id();
      case 4:
        return sequenceNumber;
      case 5:
        return minSequenceNumber;
      case 6:
        return snapshotId;
      case 7:
        return addedFilesCount;
      case 8:
        return existingFilesCount;
      case 9:
        return deletedFilesCount;
      case 10:
        return addedRowsCount;
      case 11:
        return existingRowsCount;
      case 12:
        return deletedRowsCount;
      case 13:
        return partitions();
      case 14:
        return keyMetadata();
      default:
        throw new UnsupportedOperationException("Unknown field ordinal: " + pos);
    }
  }

  /**
   * {@link StructLike} 实现：按位置写入字段值。
   *
   * <p>逻辑：先按 {@code fromProjectionPos} 映射回完整字段序号，再 switch 写入对应字段； 未知序号（更新版本新增字段）会被忽略以保证向后兼容。
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> void set(int i, T value) {
    int pos = i;
    // if the schema was projected, map the incoming ordinal to the expected one
    if (fromProjectionPos != null) {
      pos = fromProjectionPos[i];
    }
    switch (pos) {
      case 0:
        // always coerce to String for Serializable
        this.manifestPath = value.toString();
        return;
      case 1:
        this.length = (Long) value;
        return;
      case 2:
        this.specId = (Integer) value;
        return;
      case 3:
        this.content =
            value != null ? ManifestContent.values()[(Integer) value] : ManifestContent.DATA;
        return;
      case 4:
        this.sequenceNumber = value != null ? (Long) value : 0;
        return;
      case 5:
        this.minSequenceNumber = value != null ? (Long) value : 0;
        return;
      case 6:
        this.snapshotId = (Long) value;
        return;
      case 7:
        this.addedFilesCount = (Integer) value;
        return;
      case 8:
        this.existingFilesCount = (Integer) value;
        return;
      case 9:
        this.deletedFilesCount = (Integer) value;
        return;
      case 10:
        this.addedRowsCount = (Long) value;
        return;
      case 11:
        this.existingRowsCount = (Long) value;
        return;
      case 12:
        this.deletedRowsCount = (Long) value;
        return;
      case 13:
        this.partitions =
            value == null
                ? null
                : ((List<PartitionFieldSummary>) value).toArray(new PartitionFieldSummary[0]);
        return;
      case 14:
        this.keyMetadata = ByteBuffers.toByteArray((ByteBuffer) value);
        return;
      default:
        // ignore the object, it must be from a newer version of the format
    }
  }

  /** Avro {@link IndexedRecord#put} 实现：委托 {@link #set}。 */
  @Override
  public void put(int i, Object v) {
    set(i, v);
  }

  /** 深拷贝本 manifest 文件对象。 */
  @Override
  public ManifestFile copy() {
    return new GenericManifestFile(this);
  }

  /** 返回当前 Avro schema。 */
  @Override
  public Schema getSchema() {
    return avroSchema;
  }

  /** 相等性仅按 manifestPath 比较。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof GenericManifestFile)) {
      return false;
    }
    GenericManifestFile that = (GenericManifestFile) other;
    return Objects.equal(manifestPath, that.manifestPath);
  }

  /** hashCode 仅基于 manifestPath。 */
  @Override
  public int hashCode() {
    return Objects.hashCode(manifestPath);
  }

  /** 返回可读字符串表示，key 元数据以 "(redacted)" 隐藏。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("content", content)
        .add("path", manifestPath)
        .add("length", length)
        .add("partition_spec_id", specId)
        .add("added_snapshot_id", snapshotId)
        .add("added_data_files_count", addedFilesCount)
        .add("added_rows_count", addedRowsCount)
        .add("existing_data_files_count", existingFilesCount)
        .add("existing_rows_count", existingRowsCount)
        .add("deleted_data_files_count", deletedFilesCount)
        .add("deleted_rows_count", deletedRowsCount)
        .add("partitions", partitions)
        .add("key_metadata", keyMetadata == null ? "null" : "(redacted)")
        .add("sequence_number", sequenceNumber)
        .add("min_sequence_number", minSequenceNumber)
        .toString();
  }

  /** 创建 {@link CopyBuilder} 以拷贝并按需修改 manifest 字段。 */
  public static CopyBuilder copyOf(ManifestFile manifestFile) {
    return new CopyBuilder(manifestFile);
  }

  /** Manifest 拷贝构建器：允许在拷贝时覆盖个别字段（如 snapshotId）。 */
  public static class CopyBuilder {
    private final GenericManifestFile manifestFile;

    private CopyBuilder(ManifestFile toCopy) {
      if (toCopy instanceof GenericManifestFile) {
        this.manifestFile = new GenericManifestFile((GenericManifestFile) toCopy);
      } else {
        this.manifestFile =
            new GenericManifestFile(
                toCopy.path(),
                toCopy.length(),
                toCopy.partitionSpecId(),
                toCopy.content(),
                toCopy.sequenceNumber(),
                toCopy.minSequenceNumber(),
                toCopy.snapshotId(),
                toCopy.addedFilesCount(),
                toCopy.addedRowsCount(),
                toCopy.existingFilesCount(),
                toCopy.existingRowsCount(),
                toCopy.deletedFilesCount(),
                toCopy.deletedRowsCount(),
                copyList(toCopy.partitions(), PartitionFieldSummary::copy),
                toCopy.keyMetadata());
      }
    }

    /** 覆盖 snapshotId。 */
    public CopyBuilder withSnapshotId(Long newSnapshotId) {
      manifestFile.snapshotId = newSnapshotId;
      return this;
    }

    /** 返回构建好的 manifest 文件。 */
    public ManifestFile build() {
      return manifestFile;
    }
  }

  /** 工具方法：对列表元素逐一变换并返回新列表。 */
  private static <E, R> List<R> copyList(List<E> list, Function<E, R> transform) {
    if (list != null) {
      List<R> copy = Lists.newArrayListWithExpectedSize(list.size());
      for (E element : list) {
        copy.add(transform.apply(element));
      }
      return copy;
    }
    return null;
  }
}
