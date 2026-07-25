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
package org.apache.iceberg.spark;

import org.apache.spark.sql.util.CaseInsensitiveStringMap;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Iceberg 的 Spark 函数目录实现，向 Spark 注册 Iceberg 内置标量函数（bucket、truncate、hours 等）。
 *
 * <p>设计意图：实现 SupportsFunctions 接口，以懒加载方式按需绑定函数。
 *
 * <p>上下游关系：由 SparkCatalog 混入；依赖 SparkFunctions。
 */
public class SparkFunctionCatalog implements SupportsFunctions {

  private static final SparkFunctionCatalog INSTANCE = new SparkFunctionCatalog();

  private String name = "iceberg-function-catalog";
  /** 返回值。 */
  public static SparkFunctionCatalog get() {
    return INSTANCE;
  }
  /** 执行 initialize 相关操作。 */
  @Override
  public void initialize(String catalogName, CaseInsensitiveStringMap options) {
    this.name = catalogName;
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return name;
  }
}
