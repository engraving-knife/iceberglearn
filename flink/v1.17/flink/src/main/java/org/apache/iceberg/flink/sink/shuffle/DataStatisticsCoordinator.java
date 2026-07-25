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
package org.apache.iceberg.flink.sink.shuffle;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.runtime.operators.coordination.OperatorCoordinator;
import org.apache.flink.runtime.operators.coordination.OperatorEvent;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.FatalExitExceptionHandler;
import org.apache.flink.util.FlinkRuntimeException;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.ThrowableCatchingRunnable;
import org.apache.flink.util.function.ThrowingRunnable;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：数据统计协调器，收集各子任务的数据统计并合并后下发。
 *
 * <p>所属模块：iceberg-flink（sink/shuffle 子包），实现 Flink 的 {@link OperatorCoordinator} 接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>接收各 {@link DataStatisticsOperator} 子任务上报的 {@link DataStatisticsEvent}。
 *   <li>使用 {@link AggregatedStatistics} 合并所有子任务的统计数据。
 *   <li>聚合完成后将全局统计数据下发给各子任务，供自定义分区器优化数据聚类。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>单线程执行器（coordinatorExecutor）保证线程安全，所有操作通过 CoordinatorExecutorThreadFactory 提交。
 *   <li>SubtaskGateways 管理各子任务的事件发送通道。
 *   <li>AggregatedStatisticsTracker 跟踪各 checkpoint 的聚合进度。
 * </ul>
 *
 * <p>上下游关系：接收来自 {@link DataStatisticsOperator} 子任务的事件；聚合结果下发回各子任务。
 *
 * @param <D> 数据统计类型
 * @param <S> 统计结果类型
 */
@Internal
class DataStatisticsCoordinator<D extends DataStatistics<D, S>, S> implements OperatorCoordinator {
  private static final Logger LOG = LoggerFactory.getLogger(DataStatisticsCoordinator.class);

  private final String operatorName;
  private final ExecutorService coordinatorExecutor;
  private final OperatorCoordinator.Context operatorCoordinatorContext;
  private final SubtaskGateways subtaskGateways;
  private final CoordinatorExecutorThreadFactory coordinatorThreadFactory;
  private final TypeSerializer<DataStatistics<D, S>> statisticsSerializer;
  private final transient AggregatedStatisticsTracker<D, S> aggregatedStatisticsTracker;
  private volatile AggregatedStatistics<D, S> completedStatistics;
  private volatile boolean started;

  /**
   * 构造方法。
   *
   * @param operatorName 算子名称
   * @param context OperatorCoordinator 上下文
   * @param statisticsSerializer 数据统计序列化器
   */
  DataStatisticsCoordinator(
      String operatorName,
      OperatorCoordinator.Context context,
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer) {
    this.operatorName = operatorName;
    this.coordinatorThreadFactory =
        new CoordinatorExecutorThreadFactory(
            "DataStatisticsCoordinator-" + operatorName, context.getUserCodeClassloader());
    this.coordinatorExecutor = Executors.newSingleThreadExecutor(coordinatorThreadFactory);
    this.operatorCoordinatorContext = context;
    this.subtaskGateways = new SubtaskGateways(operatorName, parallelism());
    this.statisticsSerializer = statisticsSerializer;
    this.aggregatedStatisticsTracker =
        new AggregatedStatisticsTracker<>(operatorName, statisticsSerializer, parallelism());
  }

  @Override
  public void start() throws Exception {
    LOG.info("Starting data statistics coordinator: {}.", operatorName);
    started = true;
  }

  @Override
  public void close() throws Exception {
    coordinatorExecutor.shutdown();
    LOG.info("Closed data statistics coordinator: {}.", operatorName);
  }

  @VisibleForTesting
  void callInCoordinatorThread(Callable<Void> callable, String errorMessage) {
    ensureStarted();
    // Ensure the task is done by the coordinator executor.
    if (!coordinatorThreadFactory.isCurrentThreadCoordinatorThread()) {
      try {
        Callable<Void> guardedCallable =
            () -> {
              try {
                return callable.call();
              } catch (Throwable t) {
                LOG.error(
                    "Uncaught Exception in data statistics coordinator: {} executor",
                    operatorName,
                    t);
                ExceptionUtils.rethrowException(t);
                return null;
              }
            };

        coordinatorExecutor.submit(guardedCallable).get();
      } catch (InterruptedException | ExecutionException e) {
        throw new FlinkRuntimeException(errorMessage, e);
      }
    } else {
      try {
        callable.call();
      } catch (Throwable t) {
        LOG.error(
            "Uncaught Exception in data statistics coordinator: {} executor", operatorName, t);
        throw new FlinkRuntimeException(errorMessage, t);
      }
    }
  }

  public void runInCoordinatorThread(Runnable runnable) {
    this.coordinatorExecutor.execute(
        new ThrowableCatchingRunnable(
            throwable ->
                this.coordinatorThreadFactory.uncaughtException(Thread.currentThread(), throwable),
            runnable));
  }

  private void runInCoordinatorThread(ThrowingRunnable<Throwable> action, String actionString) {
    ensureStarted();
    runInCoordinatorThread(
        () -> {
          try {
            action.run();
          } catch (Throwable t) {
            ExceptionUtils.rethrowIfFatalErrorOrOOM(t);
            LOG.error(
                "Uncaught exception in the data statistics coordinator: {} while {}. Triggering job failover",
                operatorName,
                actionString,
                t);
            operatorCoordinatorContext.failJob(t);
          }
        });
  }

  private void ensureStarted() {
    Preconditions.checkState(started, "The coordinator of %s has not started yet.", operatorName);
  }

  private int parallelism() {
    return operatorCoordinatorContext.currentParallelism();
  }

  private void handleDataStatisticRequest(int subtask, DataStatisticsEvent<D, S> event) {
    AggregatedStatistics<D, S> aggregatedStatistics =
        aggregatedStatisticsTracker.updateAndCheckCompletion(subtask, event);

    if (aggregatedStatistics != null) {
      completedStatistics = aggregatedStatistics;
      sendDataStatisticsToSubtasks(
          completedStatistics.checkpointId(), completedStatistics.dataStatistics());
    }
  }

  private void sendDataStatisticsToSubtasks(
      long checkpointId, DataStatistics<D, S> globalDataStatistics) {
    callInCoordinatorThread(
        () -> {
          DataStatisticsEvent<D, S> dataStatisticsEvent =
              DataStatisticsEvent.create(checkpointId, globalDataStatistics, statisticsSerializer);
          int parallelism = parallelism();
          for (int i = 0; i < parallelism; ++i) {
            subtaskGateways.getSubtaskGateway(i).sendEvent(dataStatisticsEvent);
          }

          return null;
        },
        String.format(
            "Failed to send operator %s coordinator global data statistics for checkpoint %d",
            operatorName, checkpointId));
  }

  @Override
  @SuppressWarnings("unchecked")
  public void handleEventFromOperator(int subtask, int attemptNumber, OperatorEvent event) {
    runInCoordinatorThread(
        () -> {
          LOG.debug(
              "Handling event from subtask {} (#{}) of {}: {}",
              subtask,
              attemptNumber,
              operatorName,
              event);
          Preconditions.checkArgument(event instanceof DataStatisticsEvent);
          handleDataStatisticRequest(subtask, ((DataStatisticsEvent<D, S>) event));
        },
        String.format(
            "handling operator event %s from subtask %d (#%d)",
            event.getClass(), subtask, attemptNumber));
  }

  @Override
  public void checkpointCoordinator(long checkpointId, CompletableFuture<byte[]> resultFuture) {
    runInCoordinatorThread(
        () -> {
          LOG.debug(
              "Snapshotting data statistics coordinator {} for checkpoint {}",
              operatorName,
              checkpointId);
          resultFuture.complete(
              DataStatisticsUtil.serializeAggregatedStatistics(
                  completedStatistics, statisticsSerializer));
        },
        String.format("taking checkpoint %d", checkpointId));
  }

  @Override
  public void notifyCheckpointComplete(long checkpointId) {}

  @Override
  public void resetToCheckpoint(long checkpointId, @Nullable byte[] checkpointData)
      throws Exception {
    Preconditions.checkState(
        !started, "The coordinator %s can only be reset if it was not yet started", operatorName);

    if (checkpointData == null) {
      LOG.info(
          "Data statistic coordinator {} has nothing to restore from checkpoint {}",
          operatorName,
          checkpointId);
      return;
    }

    LOG.info(
        "Restoring data statistic coordinator {} from checkpoint {}", operatorName, checkpointId);
    completedStatistics =
        DataStatisticsUtil.deserializeAggregatedStatistics(checkpointData, statisticsSerializer);
  }

  @Override
  public void subtaskReset(int subtask, long checkpointId) {
    runInCoordinatorThread(
        () -> {
          LOG.info(
              "Operator {} subtask {} is reset to checkpoint {}",
              operatorName,
              subtask,
              checkpointId);
          Preconditions.checkState(
              this.coordinatorThreadFactory.isCurrentThreadCoordinatorThread());
          subtaskGateways.reset(subtask);
        },
        String.format("handling subtask %d recovery to checkpoint %d", subtask, checkpointId));
  }

  @Override
  public void executionAttemptFailed(int subtask, int attemptNumber, @Nullable Throwable reason) {
    runInCoordinatorThread(
        () -> {
          LOG.info(
              "Unregistering gateway after failure for subtask {} (#{}) of data statistic {}",
              subtask,
              attemptNumber,
              operatorName);
          Preconditions.checkState(
              this.coordinatorThreadFactory.isCurrentThreadCoordinatorThread());
          subtaskGateways.unregisterSubtaskGateway(subtask, attemptNumber);
        },
        String.format("handling subtask %d (#%d) failure", subtask, attemptNumber));
  }

  @Override
  public void executionAttemptReady(int subtask, int attemptNumber, SubtaskGateway gateway) {
    Preconditions.checkArgument(subtask == gateway.getSubtask());
    Preconditions.checkArgument(attemptNumber == gateway.getExecution().getAttemptNumber());
    runInCoordinatorThread(
        () -> {
          Preconditions.checkState(
              this.coordinatorThreadFactory.isCurrentThreadCoordinatorThread());
          subtaskGateways.registerSubtaskGateway(gateway);
        },
        String.format(
            "making event gateway to subtask %d (#%d) available", subtask, attemptNumber));
  }

  @VisibleForTesting
  AggregatedStatistics<D, S> completedStatistics() {
    return completedStatistics;
  }

  private static class SubtaskGateways {
    private final String operatorName;
    private final Map<Integer, SubtaskGateway>[] gateways;

    private SubtaskGateways(String operatorName, int parallelism) {
      this.operatorName = operatorName;
      gateways = new Map[parallelism];

      for (int i = 0; i < parallelism; ++i) {
        gateways[i] = Maps.newHashMap();
      }
    }

    private void registerSubtaskGateway(OperatorCoordinator.SubtaskGateway gateway) {
      int subtaskIndex = gateway.getSubtask();
      int attemptNumber = gateway.getExecution().getAttemptNumber();
      Preconditions.checkState(
          !gateways[subtaskIndex].containsKey(attemptNumber),
          "Coordinator of %s already has a subtask gateway for %d (#%d)",
          operatorName,
          subtaskIndex,
          attemptNumber);
      LOG.debug(
          "Coordinator of {} registers gateway for subtask {} attempt {}",
          operatorName,
          subtaskIndex,
          attemptNumber);
      gateways[subtaskIndex].put(attemptNumber, gateway);
    }

    private void unregisterSubtaskGateway(int subtaskIndex, int attemptNumber) {
      LOG.debug(
          "Coordinator of {} unregisters gateway for subtask {} attempt {}",
          operatorName,
          subtaskIndex,
          attemptNumber);
      gateways[subtaskIndex].remove(attemptNumber);
    }

    private OperatorCoordinator.SubtaskGateway getSubtaskGateway(int subtaskIndex) {
      Preconditions.checkState(
          gateways[subtaskIndex].size() > 0,
          "Coordinator of %s subtask %d is not ready yet to receive events",
          operatorName,
          subtaskIndex);
      return Iterables.getOnlyElement(gateways[subtaskIndex].values());
    }

    private void reset(int subtaskIndex) {
      gateways[subtaskIndex].clear();
    }
  }

  private static class CoordinatorExecutorThreadFactory
      implements ThreadFactory, Thread.UncaughtExceptionHandler {

    private final String coordinatorThreadName;
    private final ClassLoader classLoader;
    private final Thread.UncaughtExceptionHandler errorHandler;

    @javax.annotation.Nullable private Thread thread;

    CoordinatorExecutorThreadFactory(
        final String coordinatorThreadName, final ClassLoader contextClassLoader) {
      this(coordinatorThreadName, contextClassLoader, FatalExitExceptionHandler.INSTANCE);
    }

    @org.apache.flink.annotation.VisibleForTesting
    CoordinatorExecutorThreadFactory(
        final String coordinatorThreadName,
        final ClassLoader contextClassLoader,
        final Thread.UncaughtExceptionHandler errorHandler) {
      this.coordinatorThreadName = coordinatorThreadName;
      this.classLoader = contextClassLoader;
      this.errorHandler = errorHandler;
    }

    @Override
    public synchronized Thread newThread(@NotNull Runnable runnable) {
      thread = new Thread(runnable, coordinatorThreadName);
      thread.setContextClassLoader(classLoader);
      thread.setUncaughtExceptionHandler(this);
      return thread;
    }

    @Override
    public synchronized void uncaughtException(Thread t, Throwable e) {
      errorHandler.uncaughtException(t, e);
    }

    boolean isCurrentThreadCoordinatorThread() {
      return Thread.currentThread() == thread;
    }
  }
}
