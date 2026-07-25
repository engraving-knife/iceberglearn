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
 * Iceberg 表在 Spark DataSource V2 中的实现，实现 Spark 目录服务以加载和管理 Iceberg 表。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：接口 HasIcebergCatalog。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
public interface HasIcebergCatalog extends TableCatalog {

  /** 执行该方法的具体逻辑。 */
  Catalog icebergCatalog();
}
