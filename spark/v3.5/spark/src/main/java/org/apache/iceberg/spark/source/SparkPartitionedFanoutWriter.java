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
package org.apache.iceberg.spark.source;

import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.io.PartitionedFanoutWriter;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.StructType;

/**
 * Spark 适配的分区扇出写入器：按行所属分区路由数据到对应分区的写入器。
 *
 * <p>所属模块：iceberg-spark（source 子包，Spark 数据源写入路径）。
 *
 * <p>职责：接收 Spark {@link InternalRow}，计算其分区键，将数据分发到对应分区的文件写入器， 继承 {@link PartitionedFanoutWriter}
 * 管理多分区并发写入与文件滚动。
 *
 * <p>设计意图：通过 {@link InternalRowWrapper} 把 Spark 行包装为 Iceberg 可访问的形式， 复用 {@link PartitionKey}
 * 计算分区，避免为每行创建新 key 对象（复用 partitionKey）。
 *
 * <p>上下游关系：被 Spark 数据源写端在分区写入场景下使用，产出分区数据文件。
 */
public class SparkPartitionedFanoutWriter extends PartitionedFanoutWriter<InternalRow> {
  private final PartitionKey partitionKey;
  private final InternalRowWrapper internalRowWrapper;

  /**
   * 构造分区扇出写入器。
   *
   * @param spec 分区规格
   * @param format 文件格式
   * @param appenderFactory 文件追加器工厂
   * @param fileFactory 输出文件工厂
   * @param io 文件 IO
   * @param targetFileSize 目标文件大小（触发滚动）
   * @param schema Iceberg schema
   * @param sparkSchema Spark schema
   */
  public SparkPartitionedFanoutWriter(
      PartitionSpec spec,
      FileFormat format,
      FileAppenderFactory<InternalRow> appenderFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSize,
      Schema schema,
      StructType sparkSchema) {
    super(spec, format, appenderFactory, fileFactory, io, targetFileSize);
    this.partitionKey = new PartitionKey(spec, schema);
    this.internalRowWrapper = new InternalRowWrapper(sparkSchema);
  }

  /**
   * 计算给定行所属的分区键。
   *
   * <p>设计要点：复用内部 partitionKey 对象，通过 internalRowWrapper 包装行后计算分区。
   *
   * @param row Spark 内部行
   * @return 该行对应的分区键
   */
  @Override
  protected PartitionKey partition(InternalRow row) {
    partitionKey.partition(internalRowWrapper.wrap(row));
    return partitionKey;
  }
}
