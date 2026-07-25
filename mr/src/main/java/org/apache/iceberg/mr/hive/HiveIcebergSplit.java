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
package org.apache.iceberg.mr.hive;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.iceberg.mr.mapreduce.IcebergSplit;
import org.apache.iceberg.mr.mapreduce.IcebergSplitContainer;
import org.apache.iceberg.util.SerializationUtil;

/**
 * 文件级说明：Iceberg 切分在 Hive 侧的适配器，继承 Hive {@link FileSplit} 以满足 Hive 要求。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，是 {@link IcebergSplit} 与 Hive 切分体系之间的桥梁）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有内部 {@link IcebergSplit}，对外暴露 Hive 期望的 {@link FileSplit} 接口。
 *   <li>提供表 location 作为 path，便于 Hive 据此反查 {@code PartitionDesc}/{@code TableDesc} 从而找到对应的
 *       InputFormat。
 *   <li>实现自定义序列化/反序列化以同时保存 tableLocation 与内部 IcebergSplit。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Hive 要求文件格式返回的 split 必须是 {@link FileSplit} 子类，否则无法纳入 {@code HiveInputFormat}/{@code
 *       CombineHiveInputFormat} 调度。
 *   <li>Hive 用 split 的 path 反查分区/表描述对象，从而在同一个 MR 作业中混合读取不同文件格式； 这里返回表 location 而非具体文件路径，让 Hive
 *       把整张表当作一个“可拆分单元”。
 *   <li>提供无参构造器供 Hadoop 反序列化框架使用。
 * </ul>
 *
 * <p>上下游关系：上游由 {@link HiveIcebergInputFormat#getSplits} 创建；下游被 Hive 切分调度 与 {@link
 * IcebergSplitContainer#icebergSplit()} 调用者使用。
 */
// Hive requires file formats to return splits that are instances of `FileSplit`.
public class HiveIcebergSplit extends FileSplit implements IcebergSplitContainer {

  private IcebergSplit innerSplit;

  // Hive uses the path name of a split to map it back to a partition (`PartitionDesc`) or table
  // description object
  // (`TableDesc`) which specifies the relevant input format for reading the files belonging to that
  // partition or table.
  // That way, `HiveInputFormat` and `CombineHiveInputFormat` can read files with different file
  // formats in the same
  // MapReduce job and merge compatible splits together.
  private String tableLocation;

  /** Hadoop 反序列化用的无参构造器，外部不应直接调用。 */
  public HiveIcebergSplit() {}

  /**
   * 构造 Hive 侧切分。
   *
   * @param split 内部 Iceberg 切分
   * @param tableLocation 表 location，作为 Hive 反查 TableDesc 的 key
   */
  HiveIcebergSplit(IcebergSplit split, String tableLocation) {
    this.innerSplit = split;
    this.tableLocation = tableLocation;
  }

  /** 返回内部封装的 {@link IcebergSplit}。 */
  @Override
  public IcebergSplit icebergSplit() {
    return innerSplit;
  }

  /** 返回切分字节数，委托给内部 IcebergSplit。 */
  @Override
  public long getLength() {
    return innerSplit.getLength();
  }

  /** 返回数据位置提示（主机名数组），委托给内部 IcebergSplit。 */
  @Override
  public String[] getLocations() {
    return innerSplit.getLocations();
  }

  /**
   * 返回切分对应的路径。
   *
   * <p>设计要点：返回表 location 而非具体数据文件路径，便于 Hive 据此反查 TableDesc。
   */
  @Override
  public Path getPath() {
    return new Path(tableLocation);
  }

  /** 起始偏移恒为 0（Iceberg 切分不基于文件偏移）。 */
  @Override
  public long getStart() {
    return 0;
  }

  /**
   * 序列化切分：先写 tableLocation 长度+字节，再委托内部 IcebergSplit 自行序列化。
   *
   * @param out 数据输出
   * @throws IOException 写入失败时抛出
   */
  @Override
  public void write(DataOutput out) throws IOException {
    byte[] bytes = SerializationUtil.serializeToBytes(tableLocation);
    out.writeInt(bytes.length);
    out.write(bytes);

    innerSplit.write(out);
  }

  /**
   * 反序列化切分：先读 tableLocation，再重建内部 IcebergSplit 并读取其字段。
   *
   * @param in 数据输入
   * @throws IOException 读取失败时抛出
   */
  @Override
  public void readFields(DataInput in) throws IOException {
    byte[] bytes = new byte[in.readInt()];
    in.readFully(bytes);
    tableLocation = SerializationUtil.deserializeFromBytes(bytes);

    innerSplit = new IcebergSplit();
    innerSplit.readFields(in);
  }
}
