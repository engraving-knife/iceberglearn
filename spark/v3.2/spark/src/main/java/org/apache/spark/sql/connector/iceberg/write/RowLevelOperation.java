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

import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.connector.write.WriteBuilder;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

/**
 * Spark DataSource V2 连接器扩展。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：接口 RowLevelOperation。
 *
 * <p>上下游：由 DataSource V2 框架调用，桥接 Spark 与 Iceberg。
 */
public interface RowLevelOperation {

  /**
   * Spark DataSource V2 连接器扩展。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：枚举 Command。
   *
   * <p>上下游：由 DataSource V2 框架调用，桥接 Spark 与 Iceberg。
   */
  enum Command {
    DELETE,
    UPDATE,
    MERGE
  }

  /** 执行该方法的具体逻辑。 */
  default String description() {
    return this.getClass().toString();
  }

  /** 执行该方法的具体逻辑。 */
  Command command();

  /** 执行该方法的具体逻辑。 */
  ScanBuilder newScanBuilder(CaseInsensitiveStringMap options);

  /** 执行该方法的具体逻辑。 */
  WriteBuilder newWriteBuilder(ExtendedLogicalWriteInfo info);

  /** 执行该方法的具体逻辑。 */
  default NamedReference[] requiredMetadataAttributes() {
    return new NamedReference[0];
  }
}
