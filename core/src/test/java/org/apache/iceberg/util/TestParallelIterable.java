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
package org.apache.iceberg.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestParallelIterable，用于验证 Parallel Iterable 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Parallel Iterable 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestParallelIterable {
  /**
   * 测试场景：close parallel iterator without complete iteration。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void closeParallelIteratorWithoutCompleteIteration()
      throws IOException, IllegalAccessException, NoSuchFieldException {
    ExecutorService executor = Executors.newFixedThreadPool(1);

    Iterable<CloseableIterable<Integer>> transform =
        Iterables.transform(
            Lists.newArrayList(1, 2, 3, 4, 5),
            item ->
                new CloseableIterable<Integer>() {
                  /** 辅助方法：close。 */
                  @Override
                  public void close() {}

                  /** 辅助方法：iterator。 */
                  @Override
                  public CloseableIterator<Integer> iterator() {
                    return CloseableIterator.withClose(Collections.singletonList(item).iterator());
                  }
                });

    ParallelIterable<Integer> parallelIterable = new ParallelIterable<>(transform, executor);
    CloseableIterator<Integer> iterator = parallelIterable.iterator();
    Field queueField = iterator.getClass().getDeclaredField("queue");
    queueField.setAccessible(true);
    ConcurrentLinkedQueue<?> queue = (ConcurrentLinkedQueue<?>) queueField.get(iterator);

    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isNotNull();
    Awaitility.await("Queue is populated")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> queueHasElements(iterator, queue));
    iterator.close();
    Awaitility.await("Queue is cleared")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(queue).isEmpty());
  }

  /** 辅助方法：queue has elements。 */
  private void queueHasElements(CloseableIterator<Integer> iterator, Queue queue) {
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isNotNull();
    assertThat(queue).isNotEmpty();
  }
}
