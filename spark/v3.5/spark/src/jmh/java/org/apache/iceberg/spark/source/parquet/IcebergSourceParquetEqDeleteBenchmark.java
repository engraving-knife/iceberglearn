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
package org.apache.iceberg.spark.source.parquet;

import java.io.IOException;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.spark.source.IcebergSourceDeleteBenchmark;
import org.openjdk.jmh.annotations.Param;

/**
 * 文件级说明：IcebergSourceParquetEqDeleteBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.5）。职责：对 Iceberg数据源Parquet等值删除 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
public class IcebergSourceParquetEqDeleteBenchmark extends IcebergSourceDeleteBenchmark {
  @Param({"0", "0.000001", "0.05", "0.25", "0.5", "1"})
  private double percentDeleteRow;

  /** 辅助方法：追加数据。 */
  @Override
  protected void appendData() throws IOException {
    for (int fileNum = 1; fileNum <= NUM_FILES; fileNum++) {
      writeData(fileNum);

      if (percentDeleteRow > 0) {
        // add equality deletes
        table().refresh();
        writeEqDeletes(NUM_ROWS, percentDeleteRow);
      }
    }
  }

  /** 辅助方法：文件格式。 */
  @Override
  protected FileFormat fileFormat() {
    return FileFormat.PARQUET;
  }
}
