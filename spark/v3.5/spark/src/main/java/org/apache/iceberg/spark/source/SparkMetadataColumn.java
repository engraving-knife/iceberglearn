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

import org.apache.spark.sql.connector.catalog.MetadataColumn;
import org.apache.spark.sql.types.DataType;

/**
 * Iceberg 元数据列的 Spark {@link MetadataColumn} 实现。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source 子包。
 *
 * <p>职责：把 Iceberg 的元数据列（如 _file、_pos、_spec_id 等）包装为 Spark MetadataColumn， 供 Spark
 * 在查询元数据列时识别其名称、类型与可空性。
 *
 * <p>设计意图：简单值对象，仅持有 name/dataType/isNullable 三字段。
 */
public class SparkMetadataColumn implements MetadataColumn {

  private final String name;
  private final DataType dataType;
  private final boolean isNullable;

  /** 构造元数据列，指定名称、类型与可空性。 */
  public SparkMetadataColumn(String name, DataType dataType, boolean isNullable) {
    this.name = name;
    this.dataType = dataType;
    this.isNullable = isNullable;
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return name;
  }
  /** 执行 dataType 相关操作。 */
  @Override
  public DataType dataType() {
    return dataType;
  }
  /** 判断是否 Nullable。 */
  @Override
  public boolean isNullable() {
    return isNullable;
  }
}
