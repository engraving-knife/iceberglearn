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
package org.apache.iceberg.aws.util;

import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;

/**
 * 文件级说明：AWS SDK 调用重试检测器。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：作为 {@link MetricPublisher} 挂载到 AWS API 调用，事后查询该调用是否发生过重试，
 * 供调用方决定是否需要刷新某些易变状态（如清单文件列表、表元数据等）。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>度量是 AWS SDK 判断是否重试的唯一可靠途径，API 异常本身不携带重试信息。
 *   <li>递归遍历子度量集合，因为重试度量可能嵌套在子 MetricCollection 中。
 *   <li>使用 AtomicBoolean 保证线程安全，且一旦检测到重试即短路不再处理后续度量。
 * </ul>
 *
 * <p>上下游关系：由 S3FileIO 在发起 S3 请求时作为 metric publisher 传入； 调用完成后由调用方查询 {@link #retried()} 决定后续行为。
 */
public class RetryDetector implements MetricPublisher {
  private final AtomicBoolean retried = new AtomicBoolean(false);

  /**
   * 接收度量集合：若尚未检测到重试，则检查当前集合的 RETRY_COUNT 度量，任一值大于 0 即标记 已重试；否则递归检查子度量集合。
   *
   * <p>逻辑：短路——一旦 retried 已为 true 则直接返回，避免无谓遍历。
   *
   * @param metricCollection AWS SDK 发布的度量集合
   */
  @Override
  public void publish(MetricCollection metricCollection) {
    if (!retried.get()) {
      if (metricCollection.metricValues(CoreMetric.RETRY_COUNT).stream().anyMatch(i -> i > 0)) {
        retried.set(true);
      } else {
        metricCollection.children().forEach(this::publish);
      }
    }
  }

  /** 无资源需释放，空实现。 */
  @Override
  public void close() {}

  /**
   * 返回本次调用是否发生过重试。
   *
   * @return 发生过重试返回 true
   */
  public boolean retried() {
    return retried.get();
  }
}
