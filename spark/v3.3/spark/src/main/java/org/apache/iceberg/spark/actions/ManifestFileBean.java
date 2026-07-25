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

import java.nio.ByteBuffer;
import java.util.List;
import org.apache.iceberg.ManifestContent;
import org.apache.iceberg.ManifestFile;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;

/**
 * 基于 Spark 执行的 Iceberg 表维护动作，作为 JavaBean 用于 Spark 编码器。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 ManifestFileBean。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
 */
public class ManifestFileBean implements ManifestFile {
  public static final Encoder<ManifestFileBean> ENCODER = Encoders.bean(ManifestFileBean.class);

  private String path = null;
  private Long length = null;
  private Integer partitionSpecId = null;
  private Long addedSnapshotId = null;
  private Integer content = null;

  /** 返回path。 */
  public String getPath() {
    return path;
  }

  /** 设置path。 */
  public void setPath(String path) {
    this.path = path;
  }

  /** 返回length。 */
  public Long getLength() {
    return length;
  }

  /** 设置length。 */
  public void setLength(Long length) {
    this.length = length;
  }

  /** 返回partitionspecid。 */
  public Integer getPartitionSpecId() {
    return partitionSpecId;
  }

  /** 设置partitionspecid。 */
  public void setPartitionSpecId(Integer partitionSpecId) {
    this.partitionSpecId = partitionSpecId;
  }

  /** 返回addedsnapshotid。 */
  public Long getAddedSnapshotId() {
    return addedSnapshotId;
  }

  /** 设置addedsnapshotid。 */
  public void setAddedSnapshotId(Long addedSnapshotId) {
    this.addedSnapshotId = addedSnapshotId;
  }

  /** 返回content。 */
  public Integer getContent() {
    return content;
  }

  /** 设置content。 */
  public void setContent(Integer content) {
    this.content = content;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String path() {
    return path;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public long length() {
    return length;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public int partitionSpecId() {
    return partitionSpecId;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public ManifestContent content() {
    return ManifestContent.fromId(content);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public long sequenceNumber() {
    return 0;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public long minSequenceNumber() {
    return 0;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Long snapshotId() {
    return addedSnapshotId;
  }

  /**
   * 添加元素或项。
   *
   * @return 结果对象
   */
  @Override
  public Integer addedFilesCount() {
    return null;
  }

  /**
   * 添加元素或项。
   *
   * @return 结果对象
   */
  @Override
  public Long addedRowsCount() {
    return null;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Integer existingFilesCount() {
    return null;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Long existingRowsCount() {
    return null;
  }

  /**
   * 删除数据或文件。
   *
   * @return 结果对象
   */
  @Override
  public Integer deletedFilesCount() {
    return null;
  }

  /**
   * 删除数据或文件。
   *
   * @return 结果对象
   */
  @Override
  public Long deletedRowsCount() {
    return null;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public List<PartitionFieldSummary> partitions() {
    return null;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public ByteBuffer keyMetadata() {
    return null;
  }

  /**
   * 返回当前对象的副本。
   *
   * @return 结果对象
   */
  @Override
  public ManifestFile copy() {
    throw new UnsupportedOperationException("Cannot copy");
  }
}
