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
 * 标记接口：暴露 Spark TableCatalog 背后持有的 Iceberg {@link Catalog} 实例。
 *
 * <p>所属模块：iceberg-spark（source 子包，Spark 数据源与 catalog 集成层）。
 *
 * <p>职责：为实现了 Spark {@link TableCatalog} 的 Iceberg catalog 提供统一访问底层 Iceberg {@link
 * org.apache.iceberg.catalog.Catalog} 的契约，供需要直接操作 Iceberg catalog 的组件使用。
 *
 * <p>设计意图：Spark catalog 接口与 Iceberg catalog 接口不同，通过此标记接口在二者间建立桥梁， 使调用方可向下转型获取真正的 Iceberg
 * catalog，而无需依赖具体实现类。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.spark.SparkCatalog} 等实现，被需要 Iceberg catalog 能力（如 namespace
 * 管理）的调用方使用。
 */
public interface HasIcebergCatalog extends TableCatalog {

  /**
   * 返回支撑当前 Spark catalog 的底层 Iceberg catalog。
   *
   * @return 底层 Iceberg catalog 实例
   */
  Catalog icebergCatalog();
}
