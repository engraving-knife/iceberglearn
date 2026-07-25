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
package org.apache.iceberg.io;

import static org.apache.iceberg.TableProperties.DEFAULT_FILE_FORMAT;
import static org.apache.iceberg.TableProperties.DEFAULT_FILE_FORMAT_DEFAULT;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.encryption.EncryptionManager;

/**
 * 文件级说明：数据/删除文件输出文件工厂，负责生成"唯一但可识别"的文件名并创建 {@link EncryptedOutputFile}。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按 [partitionId, taskId, operationId, fileCount, suffix] 模板生成全局唯一的文件名。
 *   <li>根据 {@link LocationProvider} 决定文件存放路径（区分分区/非分区）。
 *   <li>通过 {@link EncryptionManager} 对原始 {@link OutputFile} 进行加密包装。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>operationId（UUID）用于标识同一次写入操作产出的全部文件，便于在 Spark 任务失败后通过递归 listing + grep 定位孤儿文件进行清理。
 *   <li>fileCount 用 {@link AtomicInteger} 自增，保证同任务内多文件按序命名且线程安全。
 *   <li>采用 Builder 模式封装从 Table 提取 locationProvider/encryption 等依赖，便于扩展。
 * </ul>
 *
 * <p>上下游关系：由各 TaskWriter / RollingFileWriter 在创建新文件时调用；依赖 Table 提供 的
 * LocationProvider、EncryptionManager 与 FileIO。
 */
public class OutputFileFactory {
  private final PartitionSpec defaultSpec;
  private final FileFormat format;
  private final LocationProvider locations;
  private final Supplier<FileIO> ioSupplier;
  private final EncryptionManager encryptionManager;
  private final int partitionId;
  private final long taskId;
  // The purpose of this uuid is to be able to know from two paths that they were written by the
  // same operation.
  // That's useful, for example, if a Spark job dies and leaves files in the file system, you can
  // identify them all
  // with a recursive listing and grep.
  private final String operationId;
  private final AtomicInteger fileCount = new AtomicInteger(0);
  private final String suffix;

  /**
   * 带 operationId 的私有构造方法。
   *
   * <p>设计要点：[partitionId, taskId, operationId] 三元组必须在所有 JVM 实例间唯一，否则 不同实例可能生成同名文件相互覆盖。
   *
   * @param spec 默认分区规约，用于无显式 spec 的写入
   * @param format 文件格式，决定扩展名
   * @param locations 位置提供者，决定文件存放路径
   * @param ioSupplier FileIO 提供者
   * @param encryptionManager 加密管理器
   * @param partitionId 文件名第一段：分区 ID（来自引擎 task attempt）
   * @param taskId 文件名第二段：任务 ID
   * @param operationId 文件名第三段：操作 UUID
   * @param suffix 文件名后缀（可选）
   */
  private OutputFileFactory(
      PartitionSpec spec,
      FileFormat format,
      LocationProvider locations,
      Supplier<FileIO> ioSupplier,
      EncryptionManager encryptionManager,
      int partitionId,
      long taskId,
      String operationId,
      String suffix) {
    this.defaultSpec = spec;
    this.format = format;
    this.locations = locations;
    this.ioSupplier = ioSupplier;
    this.encryptionManager = encryptionManager;
    this.partitionId = partitionId;
    this.taskId = taskId;
    this.operationId = operationId;
    this.suffix = suffix;
  }

  /**
   * 创建 Builder，从 Table 提取默认 spec、format、io 等。
   *
   * @param table 关联的 Iceberg 表
   * @param partitionId 引擎任务分区 ID
   * @param taskId 引擎任务 ID
   * @return 新的 Builder 实例
   */
  public static Builder builderFor(Table table, int partitionId, long taskId) {
    return new Builder(table, partitionId, taskId);
  }

  /**
   * 生成下一个文件名，格式为 {@code %05d-%d-%s-%05d[-suffix].ext}。
   *
   * <p>逻辑：partitionId（5 位补零）、taskId、operationId（UUID）、自增 fileCount（5 位补零）、 可选 suffix，最后通过 format
   * 追加扩展名。
   *
   * @return 新的文件名字符串（含扩展名）
   */
  private String generateFilename() {
    return format.addExtension(
        String.format(
            "%05d-%d-%s-%05d%s",
            partitionId,
            taskId,
            operationId,
            fileCount.incrementAndGet(),
            null != suffix ? "-" + suffix : ""));
  }

  /**
   * 为非分区写入生成 {@link EncryptedOutputFile}。
   *
   * <p>逻辑：通过 LocationProvider 在数据根目录下生成新位置，再用 ioSupplier 创建 OutputFile， 最后用 encryptionManager 加密包装。
   *
   * @return 加密后的输出文件
   */
  public EncryptedOutputFile newOutputFile() {
    OutputFile file = ioSupplier.get().newOutputFile(locations.newDataLocation(generateFilename()));
    return encryptionManager.encrypt(file);
  }

  /**
   * 为默认 spec 的分区写入生成 {@link EncryptedOutputFile}。
   *
   * @param partition 分区值
   * @return 加密后的输出文件
   */
  public EncryptedOutputFile newOutputFile(StructLike partition) {
    return newOutputFile(defaultSpec, partition);
  }

  /**
   * 为指定 spec 的分区写入生成 {@link EncryptedOutputFile}。
   *
   * <p>逻辑：通过 LocationProvider 在指定 spec/partition 下生成新位置，再创建并加密 OutputFile。
   *
   * @param spec 分区规约
   * @param partition 分区值
   * @return 加密后的输出文件
   */
  public EncryptedOutputFile newOutputFile(PartitionSpec spec, StructLike partition) {
    String newDataLocation = locations.newDataLocation(spec, partition, generateFilename());
    OutputFile rawOutputFile = ioSupplier.get().newOutputFile(newDataLocation);
    return encryptionManager.encrypt(rawOutputFile);
  }

  /**
   * OutputFileFactory 构建器：从 Table 自动提取依赖，并允许覆盖默认 spec/operationId/format 等。
   *
   * <p>设计意图：屏蔽从 Table 获取 locationProvider/encryption 等内部细节，调用方只需提供 必要的 partitionId/taskId，其余按需覆盖。
   */
  public static class Builder {
    private final Table table;
    private final int partitionId;
    private final long taskId;
    private PartitionSpec defaultSpec;
    private String operationId;
    private FileFormat format;
    private String suffix;
    private Supplier<FileIO> ioSupplier;

    private Builder(Table table, int partitionId, long taskId) {
      this.table = table;
      this.partitionId = partitionId;
      this.taskId = taskId;
      this.defaultSpec = table.spec();
      this.operationId = UUID.randomUUID().toString();

      String formatAsString =
          table.properties().getOrDefault(DEFAULT_FILE_FORMAT, DEFAULT_FILE_FORMAT_DEFAULT);
      this.format = FileFormat.fromString(formatAsString);
      this.ioSupplier = table::io;
    }

    /** 设置默认分区规约。 */
    public Builder defaultSpec(PartitionSpec newDefaultSpec) {
      this.defaultSpec = newDefaultSpec;
      return this;
    }

    /** 设置操作 ID（默认为随机 UUID）。 */
    public Builder operationId(String newOperationId) {
      this.operationId = newOperationId;
      return this;
    }

    /** 设置文件格式。 */
    public Builder format(FileFormat newFormat) {
      this.format = newFormat;
      return this;
    }

    /** 设置文件名后缀。 */
    public Builder suffix(String newSuffix) {
      this.suffix = newSuffix;
      return this;
    }

    /**
     * 配置 FileIO 供应商，可在表刷新时动态获取最新的 FileIO 实例。
     *
     * @param newIoSupplier FileIO 供应商
     * @return this 构建器
     */
    public Builder ioSupplier(Supplier<FileIO> newIoSupplier) {
      this.ioSupplier = newIoSupplier;
      return this;
    }

    /**
     * 构建 OutputFileFactory，从 Table 提取 LocationProvider 与 EncryptionManager。
     *
     * @return 新的 OutputFileFactory 实例
     */
    public OutputFileFactory build() {
      LocationProvider locations = table.locationProvider();
      EncryptionManager encryption = table.encryption();
      return new OutputFileFactory(
          defaultSpec,
          format,
          locations,
          ioSupplier,
          encryption,
          partitionId,
          taskId,
          operationId,
          suffix);
    }
  }
}
