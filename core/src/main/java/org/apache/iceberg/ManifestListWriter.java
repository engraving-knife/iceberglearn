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
import java.util.Iterator;
import java.util.Map;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * Manifest 列表写入器抽象类：把 {@link ManifestFile} 列表写入 Avro 格式的 manifest list 文件。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 snapshot 提交时写 manifest list 的核心组件。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>包装底层 {@link FileAppender}，在写入前通过 {@link #prepare(ManifestFile)} 为每个 manifest 补充序列号等元数据。
 *   <li>提供 V1/V2 两个具体子类，分别对应 Iceberg 格式 v1 与 v2 的 manifest list 写入。
 * </ul>
 *
 * <p>设计意图：模板方法模式——子类实现 prepare 与 newAppender，父类统一管理 add/addAll/close 等。 V2Writer 会写入
 * snapshot-id/parent-snapshot-id/sequence-number 元信息；V1Writer 不支持删除 manifest。
 *
 * <p>上下游关系：被 {@link SnapshotProducer} 等提交器调用；底层通过 {@link Avro} 写入 Avro 文件。
 */
abstract class ManifestListWriter implements FileAppender<ManifestFile> {
  private final FileAppender<ManifestFile> writer;

  /**
   * 构造方法：委托子类 {@link #newAppender} 创建底层 appender。
   *
   * @param file 输出文件
   * @param meta Avro 文件元数据
   */
  private ManifestListWriter(OutputFile file, Map<String, String> meta) {
    this.writer = newAppender(file, meta);
  }

  /** 子类实现：为每个 manifest 补充元数据（如序列号）后返回写入版。 */
  protected abstract ManifestFile prepare(ManifestFile manifest);

  /** 子类实现：创建底层 Avro appender。 */
  protected abstract FileAppender<ManifestFile> newAppender(
      OutputFile file, Map<String, String> meta);

  /** 写入单个 manifest：先 prepare 再委托底层 appender。 */
  @Override
  public void add(ManifestFile manifest) {
    writer.add(prepare(manifest));
  }

  @Override
  public void addAll(Iterator<ManifestFile> values) {
    values.forEachRemaining(this::add);
  }

  @Override
  public void addAll(Iterable<ManifestFile> values) {
    values.forEach(this::add);
  }

  @Override
  public Metrics metrics() {
    return writer.metrics();
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }

  @Override
  public long length() {
    return writer.length();
  }

  /**
   * V2 格式 manifest list 写入器：写入 snapshot-id/parent-snapshot-id/sequence-number 元信息， 并通过 {@link
   * V2Metadata.IndexedManifestFile} 为每个 manifest 索引序列号。
   */
  static class V2Writer extends ManifestListWriter {
    private final V2Metadata.IndexedManifestFile wrapper;

    V2Writer(OutputFile snapshotFile, long snapshotId, Long parentSnapshotId, long sequenceNumber) {
      super(
          snapshotFile,
          ImmutableMap.of(
              "snapshot-id", String.valueOf(snapshotId),
              "parent-snapshot-id", String.valueOf(parentSnapshotId),
              "sequence-number", String.valueOf(sequenceNumber),
              "format-version", "2"));
      this.wrapper = new V2Metadata.IndexedManifestFile(snapshotId, sequenceNumber);
    }

    @Override
    protected ManifestFile prepare(ManifestFile manifest) {
      return wrapper.wrap(manifest);
    }

    @Override
    protected FileAppender<ManifestFile> newAppender(OutputFile file, Map<String, String> meta) {
      try {
        return Avro.write(file)
            .schema(V2Metadata.MANIFEST_LIST_SCHEMA)
            .named("manifest_file")
            .meta(meta)
            .overwrite()
            .build();

      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to create snapshot list writer for path: %s", file);
      }
    }
  }

  /** V1 格式 manifest list 写入器：不写入 sequence-number；并校验 manifest 必须为 DATA 内容 （V1 不支持删除文件 manifest）。 */
  static class V1Writer extends ManifestListWriter {
    private final V1Metadata.IndexedManifestFile wrapper = new V1Metadata.IndexedManifestFile();

    V1Writer(OutputFile snapshotFile, long snapshotId, Long parentSnapshotId) {
      super(
          snapshotFile,
          ImmutableMap.of(
              "snapshot-id", String.valueOf(snapshotId),
              "parent-snapshot-id", String.valueOf(parentSnapshotId),
              "format-version", "1"));
    }

    @Override
    protected ManifestFile prepare(ManifestFile manifest) {
      Preconditions.checkArgument(
          manifest.content() == ManifestContent.DATA,
          "Cannot store delete manifests in a v1 table");
      return wrapper.wrap(manifest);
    }

    @Override
    protected FileAppender<ManifestFile> newAppender(OutputFile file, Map<String, String> meta) {
      try {
        return Avro.write(file)
            .schema(V1Metadata.MANIFEST_LIST_SCHEMA)
            .named("manifest_file")
            .meta(meta)
            .overwrite()
            .build();

      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to create snapshot list writer for path: %s", file);
      }
    }
  }
}
