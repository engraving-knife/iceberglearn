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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * {@link InheritableMetadata} 工厂类：根据 manifest 或快照信息生成可继承的元数据视图。
 *
 * <p>所属模块：iceberg-core（manifest 元数据继承机制层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为 manifest 读取过程提供"继承元数据"：当 manifest 条目本身未显式携带 snapshotId/sequenceNumber 时，由继承元数据补全。
 *   <li>区分三种场景：基于 manifest 的常规继承、用于表拷贝的快照重写、空继承（要求显式值）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Iceberg 的 manifest 条目在某些版本/场景下不持久化 snapshotId/sequenceNumber， 需要从 manifest
 *       文件本身的元数据继承下来，以保持读取语义一致。
 *   <li>工厂方法返回不同实现，调用方无需关心具体继承策略。
 * </ul>
 *
 * <p>上下游关系：被 {@code ManifestReader} 等在读取 manifest 条目时调用； 产出 {@link InheritableMetadata} 应用于 {@link
 * ManifestEntry}。
 */
class InheritableMetadataFactory {

  private static final InheritableMetadata EMPTY = new EmptyInheritableMetadata();

  private InheritableMetadataFactory() {}

  /**
   * 返回空继承元数据：要求 manifest 条目自带 snapshotId，否则在 apply 时抛异常。
   *
   * @return 空继承元数据单例
   */
  static InheritableMetadata empty() {
    return EMPTY;
  }

  /**
   * 从 manifest 文件元数据构造继承元数据：把 manifest 的 specId、snapshotId、 sequenceNumber 用于补全其内部条目的对应字段。
   *
   * @param manifest manifest 文件
   * @return 基于 manifest 的继承元数据
   */
  static InheritableMetadata fromManifest(ManifestFile manifest) {
    Preconditions.checkArgument(
        manifest.snapshotId() != null,
        "Cannot read from ManifestFile with null (unassigned) snapshot ID");
    return new BaseInheritableMetadata(
        manifest.partitionSpecId(), manifest.snapshotId(), manifest.sequenceNumber());
  }

  /**
   * 构造用于"表拷贝"场景的继承元数据：仅重写 snapshotId，其余字段保持不变。
   *
   * @param snapshotId 新快照 id
   * @return 拷贝场景的继承元数据
   */
  static InheritableMetadata forCopy(long snapshotId) {
    return new CopyMetadata(snapshotId);
  }

  /** 常规继承元数据：携带 manifest 级别的 specId、snapshotId、sequenceNumber， 在 apply 时按规则补全到 manifest 条目。 */
  static class BaseInheritableMetadata implements InheritableMetadata {
    private final int specId;
    private final long snapshotId;
    private final long sequenceNumber;

    private BaseInheritableMetadata(int specId, long snapshotId, long sequenceNumber) {
      this.specId = specId;
      this.snapshotId = snapshotId;
      this.sequenceNumber = sequenceNumber;
    }

    /**
     * 把继承元数据应用到 manifest 条目，补全缺失字段。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>snapshotId 为 null 时用继承的 snapshotId 填充。
     *   <li>dataSequenceNumber 为 null 时：v1 表（sequenceNumber=0）直接默认为 0； v2 表仅对 ADDED 状态条目继承
     *       sequenceNumber。
     *   <li>fileSequenceNumber 同上规则。
     *   <li>若文件对象是 {@link BaseFile}，则同步设置 specId 与序列号字段。
     * </ul>
     *
     * @param manifestEntry 待补全的 manifest 条目
     * @param <F> 文件类型
     * @return 补全后的同一 manifest 条目
     */
    @Override
    public <F extends ContentFile<F>> ManifestEntry<F> apply(ManifestEntry<F> manifestEntry) {
      if (manifestEntry.snapshotId() == null) {
        manifestEntry.setSnapshotId(snapshotId);
      }

      // in v1 tables, the data sequence number is not persisted and can be safely defaulted to 0
      // in v2 tables, the data sequence number should be inherited iff the entry status is ADDED
      if (manifestEntry.dataSequenceNumber() == null
          && (sequenceNumber == 0 || manifestEntry.status() == ManifestEntry.Status.ADDED)) {
        manifestEntry.setDataSequenceNumber(sequenceNumber);
      }

      // in v1 tables, the file sequence number is not persisted and can be safely defaulted to 0
      // in v2 tables, the file sequence number should be inherited iff the entry status is ADDED
      if (manifestEntry.fileSequenceNumber() == null
          && (sequenceNumber == 0 || manifestEntry.status() == ManifestEntry.Status.ADDED)) {
        manifestEntry.setFileSequenceNumber(sequenceNumber);
      }

      if (manifestEntry.file() instanceof BaseFile) {
        BaseFile<?> file = (BaseFile<?>) manifestEntry.file();
        file.setSpecId(specId);
        file.setDataSequenceNumber(manifestEntry.dataSequenceNumber());
        file.setFileSequenceNumber(manifestEntry.fileSequenceNumber());
      }

      return manifestEntry;
    }
  }

  /** 拷贝场景的继承元数据：仅把条目的 snapshotId 重写为新快照 id，用于表拷贝操作。 */
  static class CopyMetadata implements InheritableMetadata {
    private final long snapshotId;

    private CopyMetadata(long snapshotId) {
      this.snapshotId = snapshotId;
    }

    @Override
    public <F extends ContentFile<F>> ManifestEntry<F> apply(ManifestEntry<F> manifestEntry) {
      manifestEntry.setSnapshotId(snapshotId);
      return manifestEntry;
    }
  }

  /**
   * 空继承元数据：不补全任何字段，要求条目自带 snapshotId，否则抛出 {@link IllegalArgumentException}。
   *
   * <p>设计意图：在不需要继承（例如新写入的 manifest）的场景下使用， 强制条目显式携带必要字段，避免静默错误。
   */
  static class EmptyInheritableMetadata implements InheritableMetadata {

    private EmptyInheritableMetadata() {}

    @Override
    public <F extends ContentFile<F>> ManifestEntry<F> apply(ManifestEntry<F> manifestEntry) {
      if (manifestEntry.snapshotId() == null) {
        throw new IllegalArgumentException(
            "Entries must have explicit snapshot ids if inherited metadata is empty");
      }
      return manifestEntry;
    }
  }
}
