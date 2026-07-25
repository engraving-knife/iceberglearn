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

import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.types.Types;

/**
 * {@link ManifestEntry} 的通用实现：manifest 文件中单条文件记录的内存表示。
 *
 * <p>所属模块：iceberg-core（manifest 数据模型层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>表示 manifest 中的一条记录，包含状态（ADDED/EXISTING/DELETED）、快照 id、 数据序列号、文件序列号以及关联的 {@link
 *       ContentFile}；
 *   <li>同时实现 Avro 的 {@link IndexedRecord}、{@link SpecificData.SchemaConstructable} 和 {@link
 *       StructLike}，支持 Avro 读写与结构化访问。
 * </ul>
 *
 * <p>设计意图：manifest entry 是 manifest 文件的基本单元，本类把 Avro 序列化所需接口 与 Iceberg 的 {@link ManifestEntry}
 * 接口统一到同一对象，避免额外的对象拷贝。 通过 wrap* 系列方法可复用同一实例包装不同数据，减少 GC 压力。
 *
 * <p>上下游关系：被 {@link ManifestFiles} 读写 manifest 时使用；被扫描与提交逻辑消费。
 *
 * @param <F> 内容文件类型
 */
class GenericManifestEntry<F extends ContentFile<F>>
    implements ManifestEntry<F>, IndexedRecord, SpecificData.SchemaConstructable, StructLike {
  private final org.apache.avro.Schema schema;
  private Status status = Status.EXISTING;
  private Long snapshotId = null;
  private Long dataSequenceNumber = null;
  private Long fileSequenceNumber = null;
  private F file = null;

  /** 按 Avro schema 构造实例。 */
  GenericManifestEntry(org.apache.avro.Schema schema) {
    this.schema = schema;
  }

  /** 按分区类型构造实例，使用 V1 entry schema。 */
  GenericManifestEntry(Types.StructType partitionType) {
    this.schema = AvroSchemaUtil.convert(V1Metadata.entrySchema(partitionType), "manifest_entry");
  }

  /**
   * 拷贝构造：可选择完整拷贝或仅拷贝不含统计信息。
   *
   * @param toCopy 源实例
   * @param fullCopy true 完整拷贝，false 不拷贝统计信息
   */
  private GenericManifestEntry(GenericManifestEntry<F> toCopy, boolean fullCopy) {
    this.schema = toCopy.schema;
    this.status = toCopy.status;
    this.snapshotId = toCopy.snapshotId;
    this.dataSequenceNumber = toCopy.dataSequenceNumber;
    this.fileSequenceNumber = toCopy.fileSequenceNumber;
    this.file = toCopy.file().copy(fullCopy);
  }

  /**
   * 把本实例包装为 EXISTING 状态（复用另一条 entry 的全部字段）。
   *
   * @param entry 源 entry
   * @return 本实例
   */
  ManifestEntry<F> wrapExisting(ManifestEntry<F> entry) {
    return wrapExisting(
        entry.snapshotId(), entry.dataSequenceNumber(), entry.fileSequenceNumber(), entry.file());
  }

  /**
   * 把本实例包装为 EXISTING 状态。
   *
   * @param newSnapshotId 快照 id
   * @param newDataSequenceNumber 数据序列号
   * @param newFileSequenceNumber 文件序列号
   * @param newFile 内容文件
   * @return 本实例
   */
  ManifestEntry<F> wrapExisting(
      Long newSnapshotId, Long newDataSequenceNumber, Long newFileSequenceNumber, F newFile) {
    this.status = Status.EXISTING;
    this.snapshotId = newSnapshotId;
    this.dataSequenceNumber = newDataSequenceNumber;
    this.fileSequenceNumber = newFileSequenceNumber;
    this.file = newFile;
    return this;
  }

  /**
   * 把本实例包装为 ADDED 状态（不含序列号）。
   *
   * @param newSnapshotId 快照 id
   * @param newFile 内容文件
   * @return 本实例
   */
  ManifestEntry<F> wrapAppend(Long newSnapshotId, F newFile) {
    return wrapAppend(newSnapshotId, null, newFile);
  }

  /**
   * 把本实例包装为 ADDED 状态（含数据序列号）。
   *
   * @param newSnapshotId 快照 id
   * @param newDataSequenceNumber 数据序列号
   * @param newFile 内容文件
   * @return 本实例
   */
  ManifestEntry<F> wrapAppend(Long newSnapshotId, Long newDataSequenceNumber, F newFile) {
    this.status = Status.ADDED;
    this.snapshotId = newSnapshotId;
    this.dataSequenceNumber = newDataSequenceNumber;
    this.fileSequenceNumber = null;
    this.file = newFile;
    return this;
  }

  /**
   * 把本实例包装为 DELETED 状态（复用另一条 entry 的序列号与文件）。
   *
   * @param newSnapshotId 快照 id
   * @param entry 源 entry
   * @return 本实例
   */
  ManifestEntry<F> wrapDelete(Long newSnapshotId, ManifestEntry<F> entry) {
    return wrapDelete(
        newSnapshotId, entry.dataSequenceNumber(), entry.fileSequenceNumber(), entry.file());
  }

  /**
   * 把本实例包装为 DELETED 状态。
   *
   * @param newSnapshotId 快照 id
   * @param newDataSequenceNumber 数据序列号
   * @param newFileSequenceNumber 文件序列号
   * @param newFile 内容文件
   * @return 本实例
   */
  ManifestEntry<F> wrapDelete(
      Long newSnapshotId, Long newDataSequenceNumber, Long newFileSequenceNumber, F newFile) {
    this.status = Status.DELETED;
    this.snapshotId = newSnapshotId;
    this.dataSequenceNumber = newDataSequenceNumber;
    this.fileSequenceNumber = newFileSequenceNumber;
    this.file = newFile;
    return this;
  }

  /** 返回文件状态：EXISTING、ADDED 或 DELETED。 */
  @Override
  public Status status() {
    return status;
  }

  /** 返回文件被加入表时的快照 id。 */
  @Override
  public Long snapshotId() {
    return snapshotId;
  }

  /** 返回数据序列号。 */
  @Override
  public Long dataSequenceNumber() {
    return dataSequenceNumber;
  }

  /** 返回文件序列号。 */
  @Override
  public Long fileSequenceNumber() {
    return fileSequenceNumber;
  }

  /** 返回关联的内容文件。 */
  @Override
  public F file() {
    return file;
  }

  /** 完整拷贝本实例。 */
  @Override
  public ManifestEntry<F> copy() {
    return new GenericManifestEntry<>(this, true /* full copy */);
  }

  /** 拷贝本实例但不包含统计信息（节省内存）。 */
  @Override
  public ManifestEntry<F> copyWithoutStats() {
    return new GenericManifestEntry<>(this, false /* drop stats */);
  }

  /** 设置快照 id。 */
  @Override
  public void setSnapshotId(long newSnapshotId) {
    this.snapshotId = newSnapshotId;
  }

  /** 设置数据序列号。 */
  @Override
  public void setDataSequenceNumber(long newDataSequenceNumber) {
    this.dataSequenceNumber = newDataSequenceNumber;
  }

  /** 设置文件序列号。 */
  @Override
  public void setFileSequenceNumber(long newFileSequenceNumber) {
    this.fileSequenceNumber = newFileSequenceNumber;
  }

  /**
   * Avro IndexedRecord 写入：按字段序号设置值。
   *
   * @param i 字段序号
   * @param v 字段值
   */
  @Override
  @SuppressWarnings("unchecked")
  public void put(int i, Object v) {
    switch (i) {
      case 0:
        this.status = Status.values()[(Integer) v];
        return;
      case 1:
        this.snapshotId = (Long) v;
        return;
      case 2:
        this.dataSequenceNumber = (Long) v;
        return;
      case 3:
        this.fileSequenceNumber = (Long) v;
        return;
      case 4:
        this.file = (F) v;
        return;
      default:
        // ignore the object, it must be from a newer version of the format
    }
  }

  /** StructLike 写入：委托给 {@link #put}。 */
  @Override
  public <T> void set(int pos, T value) {
    put(pos, value);
  }

  /**
   * Avro IndexedRecord 读取：按字段序号获取值。
   *
   * @param i 字段序号
   * @return 字段值
   */
  @Override
  public Object get(int i) {
    switch (i) {
      case 0:
        return status.id();
      case 1:
        return snapshotId;
      case 2:
        return dataSequenceNumber;
      case 3:
        return fileSequenceNumber;
      case 4:
        return file;
      default:
        throw new UnsupportedOperationException("Unknown field ordinal: " + i);
    }
  }

  /** StructLike 读取：按位置获取值并转换为指定类型。 */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    return javaClass.cast(get(pos));
  }

  /** 返回 Avro schema。 */
  @Override
  public org.apache.avro.Schema getSchema() {
    return schema;
  }

  /** 返回字段数（5）。 */
  @Override
  public int size() {
    return 5;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("snapshot_id", snapshotId)
        .add("data_sequence_number", dataSequenceNumber)
        .add("file_sequence_number", fileSequenceNumber)
        .add("file", file)
        .toString();
  }
}
