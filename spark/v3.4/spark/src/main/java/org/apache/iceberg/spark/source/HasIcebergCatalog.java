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

import org.apache.iceberg.catalog.Catalog;
import org.apache.spark.sql.connector.catalog.TableCatalog;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：标识可获取底层 Iceberg Catalog 的接口，用于在 Spark catalog 与 Iceberg catalog 间桥接。
 *
 * <p>设计意图：以接口暴露内部 catalog 引用，便于扩展层访问底层能力。
 *
 * <p>上下游关系：由 SparkCatalog 实现；由扩展层使用。
 */
public interface HasIcebergCatalog extends TableCatalog {

  /**
   * Returns the underlying {@link org.apache.iceberg.catalog.Catalog} backing this Spark Catalog
   */
  Catalog icebergCatalog();
}
