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

import java.io.File;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.orc.GenericOrcReader;
import org.apache.iceberg.data.orc.GenericOrcWriter;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.orc.ORC;

/**
 * 文件级说明：GenericOrcReaderBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-data。职责：对 通用ORC读取器 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
public class GenericOrcReaderBenchmark extends ReaderBenchmark {
  /** 辅助方法：读取器。 */
  @Override
  protected CloseableIterable<Record> reader(File file, Schema schema) {
    return ORC.read(Files.localInput(file))
        .project(schema)
        .createReaderFunc(fileSchema -> GenericOrcReader.buildReader(schema, fileSchema))
        .build();
  }

  /** 辅助方法：写入器。 */
  @Override
  protected FileAppender<Record> writer(File file, Schema schema) {
    return ORC.write(Files.localOutput(file))
        .schema(schema)
        .createWriterFunc(GenericOrcWriter::buildWriter)
        .build();
  }
}
