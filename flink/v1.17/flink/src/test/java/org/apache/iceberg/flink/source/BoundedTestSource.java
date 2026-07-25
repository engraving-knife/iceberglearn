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
package org.apache.iceberg.flink.source;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.flink.api.common.state.CheckpointListener;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：测试 BoundedTestSource 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 BoundedTestSource 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public final class BoundedTestSource<T> implements SourceFunction<T>, CheckpointListener {

  private final List<List<T>> elementsPerCheckpoint;
  private final boolean checkpointEnabled;
  private volatile boolean running = true;

  private final AtomicInteger numCheckpointsComplete = new AtomicInteger(0);

  /** Emits all those elements in several checkpoints. */
  public BoundedTestSource(List<List<T>> elementsPerCheckpoint, boolean checkpointEnabled) {
    this.elementsPerCheckpoint = elementsPerCheckpoint;
    this.checkpointEnabled = checkpointEnabled;
  }

  /** 辅助方法：BoundedTestSource，Bounded Test Source。 */
  public BoundedTestSource(List<List<T>> elementsPerCheckpoint) {
    this(elementsPerCheckpoint, true);
  }

  /** Emits all those elements in a single checkpoint. */
  public BoundedTestSource(T... elements) {
    this(Collections.singletonList(Arrays.asList(elements)));
  }

  /** 辅助方法：run，run。 */
  @Override
  public void run(SourceContext<T> ctx) throws Exception {
    if (!checkpointEnabled) {
      Preconditions.checkArgument(
          elementsPerCheckpoint.size() <= 1,
          "There should be at most one list in the elementsPerCheckpoint when checkpoint is disabled.");
      elementsPerCheckpoint.stream().flatMap(List::stream).forEach(ctx::collect);
      return;
    }

    for (List<T> elements : elementsPerCheckpoint) {

      final int checkpointToAwait;
      /** 辅助方法：getCheckpointLock，get Checkpoint Lock。 */
      synchronized (ctx.getCheckpointLock()) {
        // Let's say checkpointToAwait = numCheckpointsComplete.get() + delta, in fact the value of
        // delta should not
        // affect the final table records because we only need to make sure that there will be
        // exactly
        // elementsPerCheckpoint.size() checkpoints to emit each records buffer from the original
        // elementsPerCheckpoint.
        // Even if the checkpoints that emitted results are not continuous, the correctness of the
        // data should not be
        // affected in the end. Setting the delta to be 2 is introducing the variable that produce
        // un-continuous
        // checkpoints that emit the records buffer from elementsPerCheckpoints.
        checkpointToAwait = numCheckpointsComplete.get() + 2;
        for (T element : elements) {
          ctx.collect(element);
        }
      }

      /** 辅助方法：getCheckpointLock，get Checkpoint Lock。 */
      synchronized (ctx.getCheckpointLock()) {
        while (running && numCheckpointsComplete.get() < checkpointToAwait) {
          ctx.getCheckpointLock().wait(1);
        }
      }
    }
  }

  /** 辅助方法：notifyCheckpointComplete，notify Checkpoint Complete。 */
  @Override
  public void notifyCheckpointComplete(long checkpointId) throws Exception {
    numCheckpointsComplete.incrementAndGet();
  }

  /** 辅助方法：cancel，cancel。 */
  @Override
  public void cancel() {
    running = false;
  }
}
