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

import org.apache.iceberg.BaseMetadataTable;
import org.apache.iceberg.SerializableTable;
import org.apache.iceberg.Table;
import org.apache.spark.util.KnownSizeEstimation;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：可序列化且带大小的表包装器，在 Driver 端估算表大小并将表序列化分发到 Executor。
 *
 * <p>设计意图：实现 Spark Serializable，缓存表大小以支持 Spark 的估算接口。
 *
 * <p>上下游关系：由 SparkScan 在构建 InputPartition 时使用。
 */
public class SerializableTableWithSize extends SerializableTable implements KnownSizeEstimation {

  private static final long SIZE_ESTIMATE = 32_768L;

  protected SerializableTableWithSize(Table table) {
    super(table);
  }
  /** 执行 estimatedSize 相关操作。 */
  @Override
  public long estimatedSize() {
    return SIZE_ESTIMATE;
  }
  /** 执行 copyOf 相关操作。 */
  public static Table copyOf(Table table) {
    if (table instanceof BaseMetadataTable) {
      return new SerializableMetadataTableWithSize((BaseMetadataTable) table);
    } else {
      return new SerializableTableWithSize(table);
    }
  }

  public static class SerializableMetadataTableWithSize extends SerializableMetadataTable
      implements KnownSizeEstimation {

    protected SerializableMetadataTableWithSize(BaseMetadataTable metadataTable) {
      super(metadataTable);
    }
    /** 执行 estimatedSize 相关操作。 */
    @Override
    public long estimatedSize() {
      return SIZE_ESTIMATE;
    }
  }
}
