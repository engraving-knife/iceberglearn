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
 * Iceberg 表在 Spark DataSource V2 中的实现。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SerializableTableWithSize。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
public class SerializableTableWithSize extends SerializableTable implements KnownSizeEstimation {

  private static final long SIZE_ESTIMATE = 32_768L;

  /** 构造 SerializableTableWithSize 实例。 */
  protected SerializableTableWithSize(Table table) {
    super(table);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public long estimatedSize() {
    return SIZE_ESTIMATE;
  }

  /** 执行该方法的具体逻辑。 */
  public static Table copyOf(Table table) {
    if (table instanceof BaseMetadataTable) {
      /** 执行该方法的具体逻辑。 */
      return new SerializableMetadataTableWithSize((BaseMetadataTable) table);
    } else {
      /** 执行该方法的具体逻辑。 */
      return new SerializableTableWithSize(table);
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现，封装提交或表元数据。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 SerializableMetadataTableWithSize。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  public static class SerializableMetadataTableWithSize extends SerializableMetadataTable
      implements KnownSizeEstimation {

    /** 构造 SerializableMetadataTableWithSize 实例。 */
    protected SerializableMetadataTableWithSize(BaseMetadataTable metadataTable) {
      super(metadataTable);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public long estimatedSize() {
      return SIZE_ESTIMATE;
    }
  }
}
