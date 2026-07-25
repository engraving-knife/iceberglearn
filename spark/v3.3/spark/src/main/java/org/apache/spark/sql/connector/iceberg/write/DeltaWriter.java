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

import java.io.IOException;
import org.apache.spark.sql.connector.write.DataWriter;

/**
 * Spark DataSource V2 连接器扩展的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：接口 DeltaWriter。
 *
 * <p>上下游：由 DataSource V2 框架调用，桥接 Spark 与 Iceberg。
 */
public interface DeltaWriter<T> extends DataWriter<T> {
  /** 删除数据或文件。 */
  void delete(T metadata, T id) throws IOException;

  /** 更新数据或状态。 */
  void update(T metadata, T id, T row) throws IOException;

  /** 执行该方法的具体逻辑。 */
  void insert(T row) throws IOException;

  /** 写入数据。 */
  @Override
  default void write(T row) throws IOException {
    insert(row);
  }
}
