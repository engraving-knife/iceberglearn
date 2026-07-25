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

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：IcebergSourceNestedListDataBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.4）。职责：对 Iceberg数据源嵌套列表数据 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
public abstract class IcebergSourceNestedListDataBenchmark extends IcebergSourceBenchmark {

  /** 辅助方法：initHadoop配置。 */
  @Override
  protected Configuration initHadoopConf() {
    return new Configuration();
  }

  /** 辅助方法：init表。 */
  @Override
  protected final Table initTable() {
    Schema schema =
        new Schema(
            required(0, "id", Types.LongType.get()),
            optional(
                1,
                "outerlist",
                Types.ListType.ofOptional(
                    2,
                    Types.StructType.of(
                        required(
                            3,
                            "innerlist",
                            Types.ListType.ofRequired(4, Types.StringType.get()))))));
    PartitionSpec partitionSpec = PartitionSpec.unpartitioned();
    HadoopTables tables = new HadoopTables(hadoopConf());
    Map<String, String> properties = Maps.newHashMap();
    properties.put(TableProperties.METADATA_COMPRESSION, "gzip");
    return tables.create(schema, partitionSpec, properties, newTableLocation());
  }
}
