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
package org.apache.iceberg.actions;

import java.io.Closeable;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Queues;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异步提交服务：在文件组重写完成时陆续提交它们。
 *
 * <p>所属模块：iceberg-core 的 actions 包，为数据文件/位置删除文件的重写动作提供提交能力。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>异步接收重写完成的文件组（{@link #offer(Object)}），按 {@code rewritesPerCommit} 批量提交。
 *   <li>支持部分进度（partial-progress）：某批提交失败时清理对应文件组，不影响其他批次。
 *   <li>在 {@link #close()} 时等待所有提交完成或超时，并对超时未提交的文件组做清理。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>提交与重写解耦：重写线程只负责产出新文件并 offer，提交线程串行执行 commit，避免并发提交 造成 Iceberg 表状态冲突（Iceberg 的乐观锁要求提交串行化）。
 *   <li>单线程提交器：用单线程 ExecutorService 保证提交顺序，简化冲突处理。
 *   <li>部分进度容错：捕获 {@code commitOrClean} 异常后仅记日志不抛出，让后续批次继续尝试， 配合最终 {@link #close()} 校验是否仍有未提交组。
 * </ul>
 *
 * <p>上下游关系：由 {@link RewriteDataFilesCommitManager}、{@link RewritePositionDeletesCommitManager}
 * 等具体提交管理器继承，被对应重写动作的执行流程调用。依赖 {@link Table} 进行 commit。
 *
 * @param <T> 文件组的抽象类型（如 {@link RewriteFileGroup}）
 */
abstract class BaseCommitService<T> implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(BaseCommitService.class);

  public static final long TIMEOUT_IN_MS_DEFAULT = TimeUnit.MINUTES.toMillis(120);

  private final Table table;
  private final ExecutorService committerService;
  private final ConcurrentLinkedQueue<T> completedRewrites;
  private final ConcurrentLinkedQueue<String> inProgressCommits;
  private final ConcurrentLinkedQueue<T> committedRewrites;
  private final int rewritesPerCommit;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final long timeoutInMS;

  /**
   * 构造提交服务，使用默认超时时间。
   *
   * @param table 执行提交的目标表
   * @param rewritesPerCommit 单次提交包含的文件组数量
   */
  BaseCommitService(Table table, int rewritesPerCommit) {
    this(table, rewritesPerCommit, TIMEOUT_IN_MS_DEFAULT);
  }

  /**
   * 构造提交服务，指定超时时间。
   *
   * @param table 执行提交的目标表
   * @param rewritesPerCommit 单次提交包含的文件组数量
   * @param timeoutInMS 全部重写完成后等待提交完成的超时时间（毫秒）
   */
  BaseCommitService(Table table, int rewritesPerCommit, long timeoutInMS) {
    this.table = table;
    LOG.info(
        "Creating commit service for table {} with {} groups per commit", table, rewritesPerCommit);
    this.rewritesPerCommit = rewritesPerCommit;
    this.timeoutInMS = timeoutInMS;

    committerService =
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("Committer-Service").build());

    completedRewrites = Queues.newConcurrentLinkedQueue();
    committedRewrites = Queues.newConcurrentLinkedQueue();
    inProgressCommits = Queues.newConcurrentLinkedQueue();
  }

  /**
   * 对一批文件组执行提交操作；提交失败时应清理这批文件组产生的新文件。
   *
   * @param batch 待提交的文件组集合
   */
  protected abstract void commitOrClean(Set<T> batch);

  /**
   * 清理指定文件组：删除为其创建但尚未提交的新文件，不应抛出异常。
   *
   * @param group 尚未提交的文件组
   */
  protected abstract void abortFileGroup(T group);

  /**
   * 启动单线程提交执行器，循环处理待提交的文件组。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>当服务处于运行态或仍有待提交/进行中的组时持续循环；
   *   <li>队列为空且无进行中提交时短暂 sleep 让出 CPU；
   *   <li>服务已停止但仍有完成的重写待提交时，调用 {@link #commitReadyCommitGroups()} 收尾。
   * </ul>
   */
  public void start() {
    Preconditions.checkState(running.compareAndSet(false, true), "Commit service already started");
    LOG.info("Starting commit service for {}", table);
    committerService.execute(
        () -> {
          while (running.get() || completedRewrites.size() > 0 || inProgressCommits.size() > 0) {
            try {
              if (completedRewrites.size() == 0 && inProgressCommits.size() == 0) {
                // give other threads a chance to make progress
                Thread.sleep(100);
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new RuntimeException("Interrupted while processing commits", e);
            }

            // commit whatever is left once done with writing.
            if (!running.get() && completedRewrites.size() > 0) {
              commitReadyCommitGroups();
            }
          }
        });
  }

  /**
   * 将一个文件组放入待提交队列；若队列中文件组数达到 {@link #rewritesPerCommit} 则立即触发一次提交。
   *
   * @param group 待最终提交的文件组
   */
  public void offer(T group) {
    LOG.debug("Offered to commit service: {}", group);
    Preconditions.checkState(
        running.get(), "Cannot add rewrites to a service which has already been closed");
    completedRewrites.add(group);
    commitReadyCommitGroups();
  }

  /**
   * 返回所有已成功提交的文件组列表。
   *
   * <p>必须在服务关闭（{@link #close()}）后调用。
   *
   * @return 已提交文件组列表
   */
  public List<T> results() {
    Preconditions.checkState(
        committerService.isShutdown(),
        "Cannot get results from a service which has not been closed");
    return Lists.newArrayList(committedRewrites.iterator());
  }

  /**
   * 关闭提交服务：停止接收新组并等待所有提交完成或超时。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>将 running 置为 false，调用 {@code shutdown()} 停止接收新任务；
   *   <li>用 {@code awaitTermination} 等待提交线程结束，超时则标记并告警；
   *   <li>超时后清理剩余未提交的文件组（{@link #abortFileGroup(Object)}）；
   *   <li>校验最终状态：若有超时或残留未提交组则抛出异常，提示用户重试。
   * </ul>
   */
  @Override
  public void close() {
    Preconditions.checkState(
        running.compareAndSet(true, false), "Cannot close already closed commit service");
    LOG.info("Closing commit service for {} waiting for all commits to finish", table);
    committerService.shutdown();

    boolean timeout = false;
    try {
      // All rewrites have completed and all new files have been created, we are now waiting for
      // the commit pool to finish doing its commits to Iceberg State. In the case of partial
      // progress this should have been occurring simultaneously with rewrites, if not there should
      // be only a single commit operation.
      if (!committerService.awaitTermination(timeoutInMS, TimeUnit.MILLISECONDS)) {
        LOG.warn(
            "Commit operation did not complete within {} minutes ({} ms) of the all files "
                + "being rewritten. This may mean that some changes were not successfully committed to the "
                + "table.",
            TimeUnit.MILLISECONDS.toMinutes(timeoutInMS),
            timeoutInMS);
        timeout = true;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(
          "Cannot complete commit for rewrite, commit service interrupted", e);
    }

    if (!completedRewrites.isEmpty() && timeout) {
      LOG.error("Attempting to cleanup uncommitted file groups");
      synchronized (completedRewrites) {
        while (!completedRewrites.isEmpty()) {
          abortFileGroup(completedRewrites.poll());
        }
      }
    }

    Preconditions.checkArgument(
        !timeout && completedRewrites.isEmpty(),
        "Timeout occurred when waiting for commits to complete. "
            + "{} file groups committed. {} file groups remain uncommitted. "
            + "Retry this operation to attempt rewriting the failed groups.",
        committedRewrites.size(),
        completedRewrites.size());

    Preconditions.checkState(
        completedRewrites.isEmpty(),
        "File groups offered after service was closed, " + "they were not successfully committed.");
  }

  /**
   * 尝试组装并提交一批文件组。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>双重检查 {@link #canCreateCommitGroup()} 后，在 {@code synchronized} 块内从 {@code
   *       completedRewrites} 取出最多 {@code rewritesPerCommit} 个组组成批次；
   *   <li>生成一个 in-progress token 标记提交进行中，调用 {@link #commitOrClean(Set)} 提交；
   *   <li>提交成功则把批次加入 {@code committedRewrites}，失败仅记日志（部分进度），最终移除 token。
   * </ul>
   */
  private void commitReadyCommitGroups() {
    Set<T> batch = null;
    if (canCreateCommitGroup()) {
      synchronized (completedRewrites) {
        if (canCreateCommitGroup()) {
          batch = Sets.newHashSetWithExpectedSize(rewritesPerCommit);
          for (int i = 0; i < rewritesPerCommit && !completedRewrites.isEmpty(); i++) {
            batch.add(completedRewrites.poll());
          }
        }
      }
    }

    if (batch != null) {
      String inProgressCommitToken = UUID.randomUUID().toString();
      inProgressCommits.add(inProgressCommitToken);
      try {
        commitOrClean(batch);
        committedRewrites.addAll(batch);
      } catch (Exception e) {
        LOG.error("Failure during rewrite commit process, partial progress enabled. Ignoring", e);
      }
      inProgressCommits.remove(inProgressCommitToken);
    }
  }

  /** 判断是否可以组装一个提交批次：队列中文件组数达到 {@code rewritesPerCommit}，或重写已结束且仍有剩余组。 */
  @VisibleForTesting
  boolean canCreateCommitGroup() {
    // Either we have a full commit group, or we have completed writing and need to commit
    // what is left over
    boolean fullCommitGroup = completedRewrites.size() >= rewritesPerCommit;
    boolean writingComplete = !running.get() && completedRewrites.size() > 0;
    return fullCommitGroup || writingComplete;
  }

  /** 判断所有重写是否都已提交完成（队列和进行中标记均为空），供测试使用。 */
  @VisibleForTesting
  boolean completedRewritesAllCommitted() {
    return completedRewrites.isEmpty() && inProgressCommits.isEmpty();
  }
}
