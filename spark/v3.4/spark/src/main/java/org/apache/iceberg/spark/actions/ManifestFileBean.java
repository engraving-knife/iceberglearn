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
package org.apache.iceberg.spark.actions;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.iceberg.ManifestContent;
import org.apache.iceberg.ManifestFile;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：清单文件 Bean，以 Spark 可序列化行形式描述一个 manifest 文件的属性。
 *
 * <p>设计意图：作为动作结果集中 manifest 记录的载体。
 *
 * <p>上下游关系：由 RewriteManifestsSparkAction 等作为返回行使用。
 */
public class ManifestFileBean implements ManifestFile, Serializable {
  public static final Encoder<ManifestFileBean> ENCODER = Encoders.bean(ManifestFileBean.class);

  private String path = null;
  private Long length = null;
  private Integer partitionSpecId = null;
  private Long addedSnapshotId = null;
  private Integer content = null;
  private Long sequenceNumber = null;
  /** 执行 fromManifest 相关操作。 */
  public static ManifestFileBean fromManifest(ManifestFile manifest) {
    ManifestFileBean bean = new ManifestFileBean();

    bean.setPath(manifest.path());
    bean.setLength(manifest.length());
    bean.setPartitionSpecId(manifest.partitionSpecId());
    bean.setAddedSnapshotId(manifest.snapshotId());
    bean.setContent(manifest.content().id());
    bean.setSequenceNumber(manifest.sequenceNumber());

    return bean;
  }
  /** 返回 Path 属性。 */
  public String getPath() {
    return path;
  }
  /** 设置 Path 属性。 */
  public void setPath(String path) {
    this.path = path;
  }
  /** 返回 Length 属性。 */
  public Long getLength() {
    return length;
  }
  /** 设置 Length 属性。 */
  public void setLength(Long length) {
    this.length = length;
  }
  /** 返回 PartitionSpecId 属性。 */
  public Integer getPartitionSpecId() {
    return partitionSpecId;
  }
  /** 设置 PartitionSpecId 属性。 */
  public void setPartitionSpecId(Integer partitionSpecId) {
    this.partitionSpecId = partitionSpecId;
  }
  /** 返回 AddedSnapshotId 属性。 */
  public Long getAddedSnapshotId() {
    return addedSnapshotId;
  }
  /** 设置 AddedSnapshotId 属性。 */
  public void setAddedSnapshotId(Long addedSnapshotId) {
    this.addedSnapshotId = addedSnapshotId;
  }
  /** 返回 Content 属性。 */
  public Integer getContent() {
    return content;
  }
  /** 设置 Content 属性。 */
  public void setContent(Integer content) {
    this.content = content;
  }
  /** 返回 SequenceNumber 属性。 */
  public Long getSequenceNumber() {
    return sequenceNumber;
  }
  /** 设置 SequenceNumber 属性。 */
  public void setSequenceNumber(Long sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }
  /** 执行 path 相关操作。 */
  @Override
  public String path() {
    return path;
  }
  /** 执行 length 相关操作。 */
  @Override
  public long length() {
    return length;
  }
  /** 执行 partitionSpecId 相关操作。 */
  @Override
  public int partitionSpecId() {
    return partitionSpecId;
  }
  /** 执行 content 相关操作。 */
  @Override
  public ManifestContent content() {
    return ManifestContent.fromId(content);
  }
  /** 执行 sequenceNumber 相关操作。 */
  @Override
  public long sequenceNumber() {
    return sequenceNumber;
  }
  /** 执行 minSequenceNumber 相关操作。 */
  @Override
  public long minSequenceNumber() {
    return 0;
  }
  /** 执行 snapshotId 相关操作。 */
  @Override
  public Long snapshotId() {
    return addedSnapshotId;
  }
  /** 执行 addedFilesCount 相关操作。 */
  @Override
  public Integer addedFilesCount() {
    return null;
  }
  /** 执行 addedRowsCount 相关操作。 */
  @Override
  public Long addedRowsCount() {
    return null;
  }
  /** 执行 existingFilesCount 相关操作。 */
  @Override
  public Integer existingFilesCount() {
    return null;
  }
  /** 执行 existingRowsCount 相关操作。 */
  @Override
  public Long existingRowsCount() {
    return null;
  }
  /** 执行 deletedFilesCount 相关操作。 */
  @Override
  public Integer deletedFilesCount() {
    return null;
  }
  /** 执行 deletedRowsCount 相关操作。 */
  @Override
  public Long deletedRowsCount() {
    return null;
  }
  /** 执行 partitions 相关操作。 */
  @Override
  public List<PartitionFieldSummary> partitions() {
    return null;
  }
  /** 执行 keyMetadata 相关操作。 */
  @Override
  public ByteBuffer keyMetadata() {
    return null;
  }
  /** 返回副本。 */
  @Override
  public ManifestFile copy() {
    throw new UnsupportedOperationException("Cannot copy");
  }
}
