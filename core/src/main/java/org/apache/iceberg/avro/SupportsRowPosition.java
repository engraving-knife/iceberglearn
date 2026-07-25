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
package org.apache.iceberg.avro;

import java.util.function.Supplier;

/**
 * 支持设置行起始位置供应器的读取器接口。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 分片读取的行位置定位接口）。
 *
 * <p>职责：为 Avro 读取器提供回调入口，使其在读取一个 Avro split 时能获取该 split 的起始行位置， 用于在读取过程中维护行号信息（例如支撑 position-based
 * 删除或行级删除）。
 *
 * <p>设计意图：以接口注入方式解耦位置计算与读取逻辑，读取器按需通过 {@link Supplier} 懒取当前位置，避免在构造期就绑定具体位置。
 *
 * <p>上下游关系：由 Avro 读取器实现，由任务调度/扫描层在打开读取器时注入位置供应器。
 */
public interface SupportsRowPosition {

  /**
   * 设置行位置供应器，用于在读取 split 时获取起始行位置。
   *
   * @param posSupplier 行位置供应器，返回当前 split 的起始行号
   */
  void setRowPositionSupplier(Supplier<Long> posSupplier);
}
