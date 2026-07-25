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
package org.apache.iceberg.flink.source.split;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.connector.source.SourceSplit;
import org.apache.flink.core.memory.DataInputDeserializer;
import org.apache.flink.core.memory.DataOutputSerializer;
import org.apache.flink.util.InstantiationUtil;
import org.apache.iceberg.BaseCombinedScanTask;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.FileScanTaskParser;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：Iceberg source 的 split 表示，封装 {@link CombinedScanTask} 与读取位置。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/split 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装 Iceberg 的 {@link CombinedScanTask} 作为 Flink SourceSplit。
 *   <li>维护当前读取的文件偏移与记录偏移，支持故障恢复时续读。
 *   <li>提供 V1（Java 序列化）与 V2（自定义 JSON 序列化）两种序列化方式。
 * </ul>
 *
 * <p>设计意图：split 频繁写入 checkpoint，缓存序列化字节数组以降低重复序列化开销； V2 使用 JSON 序列化以避免类版本变化引发的兼容问题。
 *
 * <p>上下游关系：上游为 {@link org.apache.iceberg.flink.source.enumerator.AbstractIcebergEnumerator} 创建
 * split，下游为 {@link org.apache.iceberg.flink.source.reader.IcebergSourceSplitReader} 读取。
 */
@Internal
public class IcebergSourceSplit implements SourceSplit, Serializable {
  private static final long serialVersionUID = 1L;
  private static final ThreadLocal<DataOutputSerializer> SERIALIZER_CACHE =
      ThreadLocal.withInitial(() -> new DataOutputSerializer(1024));

  private final CombinedScanTask task;

  private int fileOffset;
  private long recordOffset;

  // split 频繁被序列化到 checkpoint，缓存字节表示以降低重复序列化开销。
  @Nullable private transient byte[] serializedBytesCache;

  private IcebergSourceSplit(CombinedScanTask task, int fileOffset, long recordOffset) {
    this.task = task;
    this.fileOffset = fileOffset;
    this.recordOffset = recordOffset;
  }

  /** 从 CombinedScanTask 构造 split，文件偏移与记录偏移均为 0。 */
  public static IcebergSourceSplit fromCombinedScanTask(CombinedScanTask combinedScanTask) {
    return fromCombinedScanTask(combinedScanTask, 0, 0L);
  }

  /** 从 CombinedScanTask 构造 split，指定初始文件偏移与记录偏移。 */
  public static IcebergSourceSplit fromCombinedScanTask(
      CombinedScanTask combinedScanTask, int fileOffset, long recordOffset) {
    return new IcebergSourceSplit(combinedScanTask, fileOffset, recordOffset);
  }

  /** 返回该 split 的 CombinedScanTask。 */
  public CombinedScanTask task() {
    return task;
  }

  /** 返回当前文件偏移。 */
  public int fileOffset() {
    return fileOffset;
  }

  /** 返回当前记录偏移。 */
  public long recordOffset() {
    return recordOffset;
  }

  /** 返回 split ID，由其包含的文件列表拼接生成。 */
  @Override
  public String splitId() {
    return MoreObjects.toStringHelper(this).add("files", toString(task.files())).toString();
  }

  /**
   * 更新读取位置。
   *
   * <p>逻辑：位置变化后使序列化缓存失效，避免 checkpoint 写入过期数据。
   *
   * @param newFileOffset 新文件偏移
   * @param newRecordOffset 新记录偏移
   */
  public void updatePosition(int newFileOffset, long newRecordOffset) {
    // 位置变化后使缓存失效
    serializedBytesCache = null;
    fileOffset = newFileOffset;
    recordOffset = newRecordOffset;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("files", toString(task.files()))
        .add("fileOffset", fileOffset)
        .add("recordOffset", recordOffset)
        .toString();
  }

  /** 把文件列表拼接为字符串。 */
  private String toString(Collection<FileScanTask> files) {
    return Iterables.toString(
        files.stream()
            .map(
                fileScanTask ->
                    MoreObjects.toStringHelper(fileScanTask)
                        .add("file", fileScanTask.file().path().toString())
                        .add("start", fileScanTask.start())
                        .add("length", fileScanTask.length())
                        .toString())
            .collect(Collectors.toList()));
  }

  /**
   * V1 序列化（Java 默认序列化），缓存结果以加速重复序列化。
   *
   * @return 序列化字节数组
   * @throws IOException 序列化失败时抛出
   */
  byte[] serializeV1() throws IOException {
    if (serializedBytesCache == null) {
      serializedBytesCache = InstantiationUtil.serializeObject(this);
    }

    return serializedBytesCache;
  }

  /** V1 反序列化，按 IcebergSourceSplit 的类加载器反序列化。 */
  static IcebergSourceSplit deserializeV1(byte[] serialized) throws IOException {
    try {
      return InstantiationUtil.deserializeObject(
          serialized, IcebergSourceSplit.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Failed to deserialize the split.", e);
    }
  }

  /**
   * V2 序列化（自定义格式：fileOffset、recordOffset 与每个 FileScanTask 的 JSON）。
   *
   * <p>逻辑：从 ThreadLocal 取 DataOutputSerializer，依次写入偏移、task 数与每个 task 的 JSON， 然后缓存字节数组以加速重复序列化。
   *
   * @return 序列化字节数组
   * @throws IOException 序列化失败时抛出
   */
  byte[] serializeV2() throws IOException {
    if (serializedBytesCache == null) {
      DataOutputSerializer out = SERIALIZER_CACHE.get();
      Collection<FileScanTask> fileScanTasks = task.tasks();
      Preconditions.checkArgument(
          fileOffset >= 0 && fileOffset < fileScanTasks.size(),
          "Invalid file offset: %s. Should be within the range of [0, %s)",
          fileOffset,
          fileScanTasks.size());

      out.writeInt(fileOffset);
      out.writeLong(recordOffset);
      out.writeInt(fileScanTasks.size());

      for (FileScanTask fileScanTask : fileScanTasks) {
        String taskJson = FileScanTaskParser.toJson(fileScanTask);
        out.writeUTF(taskJson);
      }

      serializedBytesCache = out.getCopyOfBuffer();
      out.clear();
    }

    return serializedBytesCache;
  }

  /**
   * V2 反序列化，按 fileOffset、recordOffset、task 数与各 task JSON 依次读取， 并按 caseSensitive 解析每个 task。
   *
   * @param serialized 序列化字节
   * @param caseSensitive 是否大小写敏感
   * @return 反序列化得到的 IcebergSourceSplit
   * @throws IOException 反序列化失败时抛出
   */
  static IcebergSourceSplit deserializeV2(byte[] serialized, boolean caseSensitive)
      throws IOException {
    DataInputDeserializer in = new DataInputDeserializer(serialized);
    int fileOffset = in.readInt();
    long recordOffset = in.readLong();
    int taskCount = in.readInt();

    List<FileScanTask> tasks = Lists.newArrayListWithCapacity(taskCount);
    for (int i = 0; i < taskCount; ++i) {
      String taskJson = in.readUTF();
      FileScanTask task = FileScanTaskParser.fromJson(taskJson, caseSensitive);
      tasks.add(task);
    }

    CombinedScanTask combinedScanTask = new BaseCombinedScanTask(tasks);
    return IcebergSourceSplit.fromCombinedScanTask(combinedScanTask, fileOffset, recordOffset);
  }
}
