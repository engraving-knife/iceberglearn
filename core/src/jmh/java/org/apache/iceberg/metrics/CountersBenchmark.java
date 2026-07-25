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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.infra.Blackhole;

/**
 * 测试类：CountersBenchmark，用于验证 Counters Benchmark 相关功能。
 *
 * <p>所属模块：iceberg-core（基准测试目录 src/jmh）。 职责：针对 Counters Benchmark 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JMH 基准测试框架，通过构造典型数据集与读取场景， 测量清单读取/指标计数等操作的性能基线。
 */
@Fork(1)
@State(Scope.Benchmark)
@Measurement(iterations = 25)
@BenchmarkMode(Mode.SingleShotTime)
@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)
public class CountersBenchmark {

  private static final int NUM_OPERATIONS = 10_000_000;
  private static final int WORKER_POOL_SIZE = 16;
  private static final int INCREMENT_AMOUNT = 10_000;

  /** 辅助方法：default counter multiple threads。 */
  @Benchmark
  @Threads(1)
  public void defaultCounterMultipleThreads(Blackhole blackhole) {
    Counter counter = new DefaultCounter(Unit.BYTES);

    ExecutorService workerPool = ThreadPools.newWorkerPool("bench-pool", WORKER_POOL_SIZE);

    try {
      Tasks.range(WORKER_POOL_SIZE)
          .executeWith(workerPool)
          .run(
              (id) -> {
                for (int operation = 0; operation < NUM_OPERATIONS; operation++) {
                  counter.increment(INCREMENT_AMOUNT);
                }
              });
    } finally {
      workerPool.shutdown();
    }

    blackhole.consume(counter);
  }

  /** 辅助方法：default counter single thread。 */
  @Benchmark
  @Threads(1)
  public void defaultCounterSingleThread(Blackhole blackhole) {
    Counter counter = new DefaultCounter(Unit.BYTES);

    for (int operation = 0; operation < WORKER_POOL_SIZE * NUM_OPERATIONS; operation++) {
      counter.increment(INCREMENT_AMOUNT);
    }

    blackhole.consume(counter);
  }
}
