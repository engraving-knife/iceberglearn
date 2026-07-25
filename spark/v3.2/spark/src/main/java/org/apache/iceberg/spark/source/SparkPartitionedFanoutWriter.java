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
 * Iceberg 表在 Spark DataSource V2 中的实现的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkPartitionedFanoutWriter。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
public class SparkPartitionedFanoutWriter extends PartitionedFanoutWriter<InternalRow> {
  private final PartitionKey partitionKey;
  private final InternalRowWrapper internalRowWrapper;

  /** 构造 SparkPartitionedFanoutWriter 实例。 */
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

  /** 执行该方法的具体逻辑。 */
  @Override
  protected PartitionKey partition(InternalRow row) {
    partitionKey.partition(internalRowWrapper.wrap(row));
    return partitionKey;
  }
}
