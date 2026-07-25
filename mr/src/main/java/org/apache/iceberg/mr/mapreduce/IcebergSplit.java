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
package org.apache.iceberg.mr.mapreduce;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.hadoop.Util;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.util.SerializationUtil;

// Since this class extends `mapreduce.InputSplit and implements `mapred.InputSplit`, it can be
// returned by both MR v1
// and v2 file formats.
/**
 * 文件级说明：Iceberg 的输入切分，同时兼容 MR v1 与 v2 接口。
 *
 * <p>所属模块：iceberg-mr（mapreduce 子包；是 Iceberg 读取并行度的基本单元，封装一个 {@link CombinedScanTask} 及其所属 {@link
 * Table}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>携带 {@link CombinedScanTask}（一组待读文件任务）与可序列化的 {@link Table}。
 *   <li>实现 {@link IcebergSplitContainer}，{@link #icebergSplit()} 返回自身。
 *   <li>提供切分长度与数据本地性信息（可选）。
 *   <li>实现 Hadoop 序列化，把 table 与 task 序列化传输到 executor。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>同时继承 v2 InputSplit 与实现 v1 InputSplit，让一个切分类型可被两套 API 使用。
 *   <li>locations 字段为 transient：worker 节点反序列化后 conf 为 null，无法重新计算本地性， 因此返回 "*" 表示任意位置。
 *   <li>table 与 task 用 {@link SerializationUtil} 做字节序列化，要求 table 是可序列化的 （{@link
 *       org.apache.iceberg.SerializableTable}）。
 * </ul>
 *
 * <p>上下游关系：上游由 {@link IcebergInputFormat#getSplits} 创建；被 {@link
 * org.apache.iceberg.mr.mapred.AbstractMapredIcebergRecordReader} 等读取。
 */
public class IcebergSplit extends InputSplit
    implements org.apache.hadoop.mapred.InputSplit, IcebergSplitContainer {

  private static final String[] ANYWHERE = new String[] {"*"};

  private Table table;
  private CombinedScanTask task;

  private transient String[] locations;
  private transient Configuration conf;

  /** Hadoop 反序列化用的无参构造器，外部不应直接调用。 */
  public IcebergSplit() {}

  /**
   * 构造切分。
   *
   * @param table 所属 Iceberg 表（需可序列化）
   * @param conf Hadoop 配置（用于本地性计算，transient 不参与序列化）
   * @param task 待执行的组合扫描任务
   */
  IcebergSplit(Table table, Configuration conf, CombinedScanTask task) {
    this.table = table;
    this.task = task;
    this.conf = conf;
  }

  /** 返回切分携带的组合扫描任务。 */
  public CombinedScanTask task() {
    return task;
  }

  /** 实现 {@link IcebergSplitContainer}，返回自身。 */
  @Override
  public IcebergSplit icebergSplit() {
    return this;
  }

  /** 返回切分总字节数：所有 FileScanTask 长度之和。 */
  @Override
  public long getLength() {
    return task.files().stream().mapToLong(FileScanTask::length).sum();
  }

  /**
   * 返回数据本地性提示（主机名数组）。
   *
   * <p>逻辑：若开启 {@link InputFormatConfig#LOCALITY} 且 conf 可用，则计算文件块位置； 否则返回 "*"。worker 节点上 conf 为
   * null，会返回 "*"。
   *
   * @return 主机名数组或 {"*"}
   */
  @Override
  public String[] getLocations() {
    // The implementation of getLocations() is only meant to be used during split computation
    // getLocations() won't be accurate when called on worker nodes and will always return "*"
    if (locations == null && conf != null) {
      boolean localityPreferred = conf.getBoolean(InputFormatConfig.LOCALITY, false);
      locations = localityPreferred ? Util.blockLocations(task, conf) : ANYWHERE.clone();
    } else {
      locations = ANYWHERE.clone();
    }

    return locations;
  }

  /**
   * 序列化切分：先写 table 字节长度+内容，再写 task 字节长度+内容。
   *
   * @param out 数据输出
   * @throws IOException 写入失败时抛出
   */
  @Override
  public void write(DataOutput out) throws IOException {
    byte[] tableData = SerializationUtil.serializeToBytes(table);
    out.writeInt(tableData.length);
    out.write(tableData);

    byte[] data = SerializationUtil.serializeToBytes(this.task);
    out.writeInt(data.length);
    out.write(data);
  }

  /**
   * 反序列化切分：依次读回 table 与 task。
   *
   * @param in 数据输入
   * @throws IOException 读取失败时抛出
   */
  @Override
  public void readFields(DataInput in) throws IOException {
    byte[] tableData = new byte[in.readInt()];
    in.readFully(tableData);
    this.table = SerializationUtil.deserializeFromBytes(tableData);

    byte[] data = new byte[in.readInt()];
    in.readFully(data);
    this.task = SerializationUtil.deserializeFromBytes(data);
  }

  /** 返回切分所属的 Iceberg 表。 */
  public Table table() {
    return table;
  }
}
