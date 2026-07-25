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
package org.apache.iceberg.metrics;

import java.util.Collections;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 度量报告器组合工具类，用于把多个 {@link MetricsReporter} 合并为一个统一报告器。
 *
 * <p>所属模块：iceberg-core，度量包中实现"组合模式"的报告器聚合工具。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link #combine(MetricsReporter, MetricsReporter)} 将两个报告器合并为一个， 若其中之一为 null
 *       则直接返回另一个，避免无谓的包装。
 *   <li>内部以 {@link CompositeMetricsReporter} 持有去重后的报告器集合，统一转发上报调用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用基于身份的 {@link Sets#newIdentityHashSet()} 去重，避免报告器未实现 equals/hashCode
 *       时被错误合并；若传入的已是复合报告器，则展开其成员再合并， 防止嵌套层级过深。
 *   <li>组合上报时对单个报告器的异常做捕获并降级为日志告警，避免一个报告器失败影响其它报告器。
 * </ul>
 *
 * <p>上下游关系：被表/扫描/提交流程用于拼接多个报告器（如同时上报 REST 与控制台）； {@link CompositeMetricsReporter} 实现 {@link
 * MetricsReporter} 转发至上游各报告器。
 */
public class MetricsReporters {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsReporters.class);

  private MetricsReporters() {}

  /**
   * 合并两个 {@link MetricsReporter} 为一个统一报告器。
   *
   * <p>逻辑：若 first 为 null 返回 second；若 second 为 null 或两者为同一对象返回 first；
   * 否则把两者（若本身是复合报告器则展开其成员）加入身份去重集合，最终包装为 {@link CompositeMetricsReporter} 返回。
   *
   * @param first 第一个报告器，可为 null
   * @param second 第二个报告器，可为 null
   * @return 合并后的报告器；若两者均为 null 则返回 null
   */
  public static MetricsReporter combine(MetricsReporter first, MetricsReporter second) {
    if (null == first) {
      return second;
    } else if (null == second || first == second) {
      return first;
    }

    Set<MetricsReporter> reporters = Sets.newIdentityHashSet();

    if (first instanceof CompositeMetricsReporter) {
      reporters.addAll(((CompositeMetricsReporter) first).reporters());
    } else {
      reporters.add(first);
    }

    if (second instanceof CompositeMetricsReporter) {
      reporters.addAll(((CompositeMetricsReporter) second).reporters());
    } else {
      reporters.add(second);
    }

    return new CompositeMetricsReporter(reporters);
  }

  /**
   * 复合度量报告器，持有多个 {@link MetricsReporter} 并依次转发上报。
   *
   * <p>设计意图：包级可见（{@link VisibleForTesting} 便于测试访问），上报时逐个调用成员报告器， 单个报告器抛出的异常被捕获并记录为告警日志，不影响后续报告器的上报。
   */
  @VisibleForTesting
  static class CompositeMetricsReporter implements MetricsReporter {
    private final Set<MetricsReporter> reporters;

    private CompositeMetricsReporter(Set<MetricsReporter> reporters) {
      this.reporters = reporters;
    }

    /**
     * 依次向所有成员报告器转发度量报告。
     *
     * <p>逻辑：遍历成员集合逐个调用 {@link MetricsReporter#report(MetricsReport)}， 捕获单个报告器抛出的异常并以 warn
     * 级别记录日志，确保一个报告器失败不会中断其它报告器。
     *
     * @param report 度量报告
     */
    @Override
    public void report(MetricsReport report) {
      for (MetricsReporter reporter : reporters) {
        try {
          reporter.report(report);
        } catch (Exception e) {
          LOG.warn(
              "Could not report {} to {}",
              report.getClass().getName(),
              reporter.getClass().getName(),
              e);
        }
      }
    }

    /**
     * 返回成员报告器的不可修改视图。
     *
     * @return 不可修改的报告器集合
     */
    Set<MetricsReporter> reporters() {
      return Collections.unmodifiableSet(reporters);
    }
  }
}
