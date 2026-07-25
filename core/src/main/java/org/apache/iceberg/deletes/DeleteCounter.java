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
package org.apache.iceberg.deletes;

/**
 * 删除计数器：在应用删除时统计被删除的行数。
 *
 * <p>所属模块：iceberg-core，deletes 包内的轻量计数工具。
 *
 * <p>职责：维护一个 long 计数器，供删除过滤流程在判定行被删除时累加，最终用于产出删除统计指标。
 *
 * <p>设计意图：将计数能力从过滤逻辑中解耦，使得过滤迭代器无需关心统计细节，只需在删除命中时 调用 {@link #increment()}。该类非线程安全，面向单次读取任务使用。
 *
 * <p>上下游关系：被 {@link Deletes#filterDeleted}、{@link Deletes.PositionStreamDeleteFilter} 等
 * 删除过滤流程持有并在删除命中时调用。
 */
public class DeleteCounter {

  private long count = 0L;

  /** 将计数器加 1。 */
  public void increment() {
    count++;
  }

  /** 返回当前累计的删除行数。 */
  public long get() {
    return count;
  }
}
