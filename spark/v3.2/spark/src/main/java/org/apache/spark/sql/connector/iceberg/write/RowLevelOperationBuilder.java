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
package org.apache.spark.sql.connector.iceberg.write;

/**
 * Spark DataSource V2 连接器扩展的构建器，负责分步骤构造目标对象。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：接口 RowLevelOperationBuilder。
 *
 * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
 *
 * <p>上下游：由 DataSource V2 框架调用，桥接 Spark 与 Iceberg。
 */
public interface RowLevelOperationBuilder {
  /** 构造并返回目标对象。 */
  RowLevelOperation build();
}
