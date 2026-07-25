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

import java.io.IOException;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * manifest 文件的写入器基类，实现 {@link FileAppender} 接口。
 *
 * <p>所属模块：iceberg-core，负责将 {@link ContentFile} 以 {@link ManifestEntry} 形式 Avro 序列化 到 manifest
 * 文件，并累计分区统计与文件计数。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 add/existing/delete 三类入口，分别写入新增、已存在、删除条目。
 *   <li>维护 added/existing/deleted 的文件数与行数，以及分区摘要 {@link PartitionSummary}。
 *   <li>跟踪最小数据序列号，供 manifest list 构建时使用。
 *   <li>通过 {@link #toManifestFile()} 在关闭后产出 {@link ManifestFile} 元信息。
 * </ul>
 *
 * <p>设计意图：抽象类将公共计数与写入逻辑收敛于此，序列号赋予与版本差异由子类 （{@link V1Writer}/{@link V2Writer}/{@link
 * V2DeleteWriter}）通过 {@link #prepare(ManifestEntry)} 与 {@link #newAppender(PartitionSpec,
 * OutputFile)} 定制；{@code UNASSIGNED_SEQ} 占位待提交时回填。
 *
 * <p>上下游关系：由提交流程（如 {@code ManifestListWriter}）创建并写入；依赖 {@link FileAppender} 落盘，产出的 {@link
 * ManifestFile} 被汇总到 snapshot 的 manifest list。
 *
 * @param <F> 写入的文件类型，{@link DataFile} 或 {@link DeleteFile}
 */
public abstract class ManifestWriter<F extends ContentFile<F>> implements FileAppender<F> {
  // stand-in for the current sequence number that will be assigned when the commit is successful
  // this is replaced when writing a manifest list by the ManifestFile wrapper
  static final long UNASSIGNED_SEQ = -1L;

  private final OutputFile file;
  private final int specId;
  private final FileAppender<ManifestEntry<F>> writer;
  private final Long snapshotId;
  private final GenericManifestEntry<F> reused;
  private final PartitionSummary stats;

  private boolean closed = false;
  private int addedFiles = 0;
  private long addedRows = 0L;
  private int existingFiles = 0;
  private long existingRows = 0L;
  private int deletedFiles = 0;
  private long deletedRows = 0L;
  private Long minDataSequenceNumber = null;

  /**
   * 构造 manifest 写入器，初始化底层 appender、复用条目与分区摘要。
   *
   * @param spec 分区 spec
   * @param file 输出文件
   * @param snapshotId 当前快照 ID
   */
  private ManifestWriter(PartitionSpec spec, OutputFile file, Long snapshotId) {
    this.file = file;
    this.specId = spec.specId();
    this.writer = newAppender(spec, file);
    this.snapshotId = snapshotId;
    this.reused = new GenericManifestEntry<>(spec.partitionType());
    this.stats = new PartitionSummary(spec);
  }

  /**
   * 在写入前对条目做版本相关处理（如补全序列号），由子类实现。
   *
   * @param entry 原始条目
   * @return 处理后的条目
   */
  protected abstract ManifestEntry<F> prepare(ManifestEntry<F> entry);

  /**
   * 创建底层 Avro appender，由子类按 manifest 版本实现。
   *
   * @param spec 分区 spec
   * @param outputFile 输出文件
   * @return manifest 条目 appender
   */
  protected abstract FileAppender<ManifestEntry<F>> newAppender(
      PartitionSpec spec, OutputFile outputFile);

  /** 返回 manifest 内容类型，默认数据，删除 manifest 子类覆盖为 DELETES。 */
  protected ManifestContent content() {
    return ManifestContent.DATA;
  }

  /**
   * 写入一条条目并更新计数、分区摘要与最小数据序列号。
   *
   * <p>逻辑：按 status 累加对应文件数与行数；调用 {@link PartitionSummary#update} 更新分区统计； 若条目存活且携带数据序列号，则更新
   * minDataSequenceNumber；最后经 {@link #prepare} 处理后写出。
   *
   * @param entry 待写入条目
   */
  void addEntry(ManifestEntry<F> entry) {
    switch (entry.status()) {
      case ADDED:
        addedFiles += 1;
        addedRows += entry.file().recordCount();
        break;
      case EXISTING:
        existingFiles += 1;
        existingRows += entry.file().recordCount();
        break;
      case DELETED:
        deletedFiles += 1;
        deletedRows += entry.file().recordCount();
        break;
    }

    stats.update(entry.file().partition());

    if (entry.isLive()
        && entry.dataSequenceNumber() != null
        && (minDataSequenceNumber == null || entry.dataSequenceNumber() < minDataSequenceNumber)) {
      this.minDataSequenceNumber = entry.dataSequenceNumber();
    }

    writer.add(prepare(entry));
  }

  /**
   * 以新增条目写入一个文件，序列号在提交时赋予。
   *
   * <p>条目快照 ID 为本 manifest 的快照 ID，数据/文件序列号在提交时分配。
   *
   * @param addedFile 新增数据文件
   */
  @Override
  public void add(F addedFile) {
    addEntry(reused.wrapAppend(snapshotId, addedFile));
  }

  /**
   * 以新增条目写入一个文件并指定数据序列号。
   *
   * <p>条目快照 ID 为本 manifest 的快照 ID，数据序列号取参数值，文件序列号在提交时分配。
   *
   * @param addedFile 新增数据文件
   * @param dataSequenceNumber 该文件的数据序列号
   */
  public void add(F addedFile, long dataSequenceNumber) {
    addEntry(reused.wrapAppend(snapshotId, dataSequenceNumber, addedFile));
  }

  /**
   * 以新增条目写入既有 {@link ManifestEntry}，按其数据序列号是否有效决定是否携带。
   *
   * @param entry 既有条目
   */
  void add(ManifestEntry<F> entry) {
    if (entry.dataSequenceNumber() != null && entry.dataSequenceNumber() >= 0) {
      addEntry(reused.wrapAppend(snapshotId, entry.dataSequenceNumber(), entry.file()));
    } else {
      addEntry(reused.wrapAppend(snapshotId, entry.file()));
    }
  }

  /**
   * 以已存在条目写入一个文件，保留原始序列号与快照 ID。
   *
   * <p>原始数据/文件序列号与快照 ID（提交时赋予）必须保留。
   *
   * @param existingFile 已存在文件
   * @param fileSnapshotId 该文件首次加入表时的快照 ID
   * @param dataSequenceNumber 数据序列号（首次加入时赋予）
   * @param fileSequenceNumber 文件序列号（首次加入时赋予）
   */
  public void existing(
      F existingFile, long fileSnapshotId, long dataSequenceNumber, Long fileSequenceNumber) {
    reused.wrapExisting(fileSnapshotId, dataSequenceNumber, fileSequenceNumber, existingFile);
    addEntry(reused);
  }

  /** 以已存在条目写入既有 {@link ManifestEntry}，保留其原始信息。 */
  void existing(ManifestEntry<F> entry) {
    addEntry(reused.wrapExisting(entry));
  }

  /**
   * 以删除条目写入一个文件，保留原始序列号。
   *
   * <p>条目快照 ID 为本 manifest 的快照 ID，但原始数据/文件序列号必须保留。
   *
   * @param deletedFile 待删除文件
   * @param dataSequenceNumber 数据序列号（首次加入时赋予）
   * @param fileSequenceNumber 文件序列号（首次加入时赋予）
   */
  public void delete(F deletedFile, long dataSequenceNumber, Long fileSequenceNumber) {
    addEntry(reused.wrapDelete(snapshotId, dataSequenceNumber, fileSequenceNumber, deletedFile));
  }

  /**
   * 以删除条目写入既有 {@link ManifestEntry}，使用当前快照 ID。
   *
   * <p>使用当前快照 ID 标记删除；当该快照被移除或无更早快照时，可安全删除数据文件。
   *
   * @param entry 既有条目
   */
  void delete(ManifestEntry<F> entry) {
    // Use the current Snapshot ID for the delete. It is safe to delete the data file from disk
    // when this Snapshot has been removed or when there are no Snapshots older than this one.
    addEntry(reused.wrapDelete(snapshotId, entry));
  }

  /** @return 底层 appender 的写入指标 */
  @Override
  public Metrics metrics() {
    return writer.metrics();
  }

  /** @return 已写入字节数 */
  @Override
  public long length() {
    return writer.length();
  }

  /**
   * 在写入器关闭后构建 {@link ManifestFile} 元信息。
   *
   * <p>逻辑：若 minDataSequenceNumber 为 null（未写过带序列号的条目），则传 {@link #UNASSIGNED_SEQ}
   * 让提交时继承；否则使用记录的最小数据序列号。
   *
   * @return manifest 文件元信息
   * @throws IllegalStateException 写入器未关闭时抛出
   */
  public ManifestFile toManifestFile() {
    Preconditions.checkState(closed, "Cannot build ManifestFile, writer is not closed");
    // if the minSequenceNumber is null, then no manifests with a sequence number have been written,
    // so the min data sequence number is the one that will be assigned when this is committed.
    // pass UNASSIGNED_SEQ to inherit it.
    long minSeqNumber = minDataSequenceNumber != null ? minDataSequenceNumber : UNASSIGNED_SEQ;
    return new GenericManifestFile(
        file.location(),
        writer.length(),
        specId,
        content(),
        UNASSIGNED_SEQ,
        minSeqNumber,
        snapshotId,
        addedFiles,
        addedRows,
        existingFiles,
        existingRows,
        deletedFiles,
        deletedRows,
        stats.summaries(),
        null);
  }

  /** 关闭写入器，标记已关闭并关闭底层 appender。 */
  @Override
  public void close() throws IOException {
    this.closed = true;
    writer.close();
  }

  /** V2 格式数据 manifest 写入器，使用 {@link V2Metadata} 补全序列号索引。 */
  static class V2Writer extends ManifestWriter<DataFile> {
    private final V2Metadata.IndexedManifestEntry<DataFile> entryWrapper;

    V2Writer(PartitionSpec spec, OutputFile file, Long snapshotId) {
      super(spec, file, snapshotId);
      this.entryWrapper = new V2Metadata.IndexedManifestEntry<>(snapshotId, spec.partitionType());
    }

    /** 用 {@link V2Metadata.IndexedManifestEntry} 包装条目以补全序列号。 */
    @Override
    protected ManifestEntry<DataFile> prepare(ManifestEntry<DataFile> entry) {
      return entryWrapper.wrap(entry);
    }

    /**
     * 创建 V2 数据 manifest 的 Avro appender，写入 schema/partition-spec 等元数据。
     *
     * @param spec 分区 spec
     * @param file 输出文件
     * @return manifest 条目 appender
     */
    @Override
    protected FileAppender<ManifestEntry<DataFile>> newAppender(
        PartitionSpec spec, OutputFile file) {
      Schema manifestSchema = V2Metadata.entrySchema(spec.partitionType());
      try {
        return Avro.write(file)
            .schema(manifestSchema)
            .named("manifest_entry")
            .meta("schema", SchemaParser.toJson(spec.schema()))
            .meta("partition-spec", PartitionSpecParser.toJsonFields(spec))
            .meta("partition-spec-id", String.valueOf(spec.specId()))
            .meta("format-version", "2")
            .meta("content", "data")
            .overwrite()
            .build();
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to create manifest writer for path: %s", file);
      }
    }
  }

  /** V2 格式删除 manifest 写入器，内容类型为 DELETES。 */
  static class V2DeleteWriter extends ManifestWriter<DeleteFile> {
    private final V2Metadata.IndexedManifestEntry<DeleteFile> entryWrapper;

    V2DeleteWriter(PartitionSpec spec, OutputFile file, Long snapshotId) {
      super(spec, file, snapshotId);
      this.entryWrapper = new V2Metadata.IndexedManifestEntry<>(snapshotId, spec.partitionType());
    }

    /** 用 {@link V2Metadata.IndexedManifestEntry} 包装条目以补全序列号。 */
    @Override
    protected ManifestEntry<DeleteFile> prepare(ManifestEntry<DeleteFile> entry) {
      return entryWrapper.wrap(entry);
    }

    /**
     * 创建 V2 删除 manifest 的 Avro appender，元数据 content 标记为 deletes。
     *
     * @param spec 分区 spec
     * @param file 输出文件
     * @return manifest 条目 appender
     */
    @Override
    protected FileAppender<ManifestEntry<DeleteFile>> newAppender(
        PartitionSpec spec, OutputFile file) {
      Schema manifestSchema = V2Metadata.entrySchema(spec.partitionType());
      try {
        return Avro.write(file)
            .schema(manifestSchema)
            .named("manifest_entry")
            .meta("schema", SchemaParser.toJson(spec.schema()))
            .meta("partition-spec", PartitionSpecParser.toJsonFields(spec))
            .meta("partition-spec-id", String.valueOf(spec.specId()))
            .meta("format-version", "2")
            .meta("content", "deletes")
            .overwrite()
            .build();
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to create manifest writer for path: %s", file);
      }
    }

    /** @return 删除 manifest 内容类型 DELETES */
    @Override
    protected ManifestContent content() {
      return ManifestContent.DELETES;
    }
  }

  /** V1 格式数据 manifest 写入器，使用 {@link V1Metadata} 处理条目。 */
  static class V1Writer extends ManifestWriter<DataFile> {
    private final V1Metadata.IndexedManifestEntry entryWrapper;

    V1Writer(PartitionSpec spec, OutputFile file, Long snapshotId) {
      super(spec, file, snapshotId);
      this.entryWrapper = new V1Metadata.IndexedManifestEntry(spec.partitionType());
    }

    /** 用 {@link V1Metadata.IndexedManifestEntry} 包装条目。 */
    @Override
    protected ManifestEntry<DataFile> prepare(ManifestEntry<DataFile> entry) {
      return entryWrapper.wrap(entry);
    }

    /**
     * 创建 V1 数据 manifest 的 Avro appender，元数据 format-version 标记为 1。
     *
     * @param spec 分区 spec
     * @param file 输出文件
     * @return manifest 条目 appender
     */
    @Override
    protected FileAppender<ManifestEntry<DataFile>> newAppender(
        PartitionSpec spec, OutputFile file) {
      Schema manifestSchema = V1Metadata.entrySchema(spec.partitionType());
      try {
        return Avro.write(file)
            .schema(manifestSchema)
            .named("manifest_entry")
            .meta("schema", SchemaParser.toJson(spec.schema()))
            .meta("partition-spec", PartitionSpecParser.toJsonFields(spec))
            .meta("partition-spec-id", String.valueOf(spec.specId()))
            .meta("format-version", "1")
            .overwrite()
            .build();
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to create manifest writer for path: %s", file);
      }
    }
  }
}
