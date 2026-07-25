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
package org.apache.iceberg.flink.source.reader;

import java.io.Serializable;

/**
 * 记录工厂接口，定义创建记录实例的契约。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：抽象记录创建逻辑，支持 RowData 等类型。
 *
 * <p>设计意图：工厂接口；被 emitter 调用以复用记录。
 */
interface RecordFactory<T> extends Serializable {
  /** Create a batch of records */
  T[] createBatch(int batchSize);

  /** Clone record into the specified position of the batch array */
  void clone(T from, T[] batch, int position);
}
