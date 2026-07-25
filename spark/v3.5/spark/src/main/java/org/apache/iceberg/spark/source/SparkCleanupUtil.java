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
package org.apache.iceberg.spark.source;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.io.BulkDeletionFailureException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.SupportsBulkOperations;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.apache.spark.TaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 清理已写入但未提交文件的工具类。
 *
 * <p>所属模块：iceberg-spark（source 子包）。在写任务失败或事务回滚后，删除残留的数据/删除文件， 避免存储泄漏。优先使用批量删除，否则在工作线程池中带重试地逐个删除。
 *
 * <p>设计意图：区分 driver 与 executor 调用场景（executor 版本附带 Spark 任务信息日志），
 * 统一封装删除重试与异常抑制逻辑，失败仅告警不抛出，保证清理不影响主流程。
 *
 * <p>上下游关系：由 {@link SparkWrite} 等在任务失败或中止时调用，依赖 {@link FileIO} 删除文件。
 */
class SparkCleanupUtil {

  private static final Logger LOG = LoggerFactory.getLogger(SparkCleanupUtil.class);

  private static final int DELETE_NUM_RETRIES = 3;
  private static final int DELETE_MIN_RETRY_WAIT_MS = 100; // 100 ms
  private static final int DELETE_MAX_RETRY_WAIT_MS = 30 * 1000; // 30 seconds
  private static final int DELETE_TOTAL_RETRY_TIME_MS = 2 * 60 * 1000; // 2 minutes

  private SparkCleanupUtil() {}

  /**
   * 尽可能删除某任务产出的文件。
   *
   * <p>注意：本方法会记录 Spark 任务信息，仅应在 executor 上调用；driver 上请使用 {@link #deleteFiles(String, FileIO,
   * List)}。
   *
   * @param io 用于删除文件的 {@link FileIO}
   * @param files 待删除文件列表
   */
  public static void deleteTaskFiles(FileIO io, List<? extends ContentFile<?>> files) {
    deleteFiles(taskInfo(), io, files);
  }

  /** 返回当前任务的描述信息（格式对齐 Spark 内部日志），无 TaskContext 时返回 unknown task。 */
  // the format matches what Spark uses for internal logging
  private static String taskInfo() {
    TaskContext taskContext = TaskContext.get();
    if (taskContext == null) {
      return "unknown task";
    } else {
      return String.format(
          "partition %d (task %d, attempt %d, stage %d.%d)",
          taskContext.partitionId(),
          taskContext.taskAttemptId(),
          taskContext.attemptNumber(),
          taskContext.stageId(),
          taskContext.stageAttemptNumber());
    }
  }

  /**
   * 尽可能删除给定文件。
   *
   * @param context 调用方描述，用于日志
   * @param io 用于删除文件的 {@link FileIO}
   * @param files 待删除文件列表
   */
  public static void deleteFiles(String context, FileIO io, List<? extends ContentFile<?>> files) {
    List<String> paths = Lists.transform(files, file -> file.path().toString());
    deletePaths(context, io, paths);
  }

  /** 按路径删除：支持批量则批量删除，否则逐个删除。 */
  private static void deletePaths(String context, FileIO io, List<String> paths) {
    if (io instanceof SupportsBulkOperations) {
      SupportsBulkOperations bulkIO = (SupportsBulkOperations) io;
      bulkDelete(context, bulkIO, paths);
    } else {
      delete(context, io, paths);
    }
  }

  /** 批量删除文件，部分失败时告警已删除数量。 */
  private static void bulkDelete(String context, SupportsBulkOperations io, List<String> paths) {
    try {
      io.deleteFiles(paths);
      LOG.info("Deleted {} file(s) using bulk deletes ({})", paths.size(), context);

    } catch (BulkDeletionFailureException e) {
      int deletedFilesCount = paths.size() - e.numberFailedObjects();
      LOG.warn(
          "Deleted only {} of {} file(s) using bulk deletes ({})",
          deletedFilesCount,
          paths.size(),
          context);
    }
  }

  /** 在工作线程池中带指数退避重试逐个删除文件，NotFound 时停止重试，失败仅告警。 */
  private static void delete(String context, FileIO io, List<String> paths) {
    AtomicInteger deletedFilesCount = new AtomicInteger(0);

    Tasks.foreach(paths)
        .executeWith(ThreadPools.getWorkerPool())
        .stopRetryOn(NotFoundException.class)
        .suppressFailureWhenFinished()
        .onFailure((path, exc) -> LOG.warn("Failed to delete {} ({})", path, context, exc))
        .retry(DELETE_NUM_RETRIES)
        .exponentialBackoff(
            DELETE_MIN_RETRY_WAIT_MS,
            DELETE_MAX_RETRY_WAIT_MS,
            DELETE_TOTAL_RETRY_TIME_MS,
            2 /* exponential */)
        .run(
            path -> {
              io.deleteFile(path);
              deletedFilesCount.incrementAndGet();
            });

    if (deletedFilesCount.get() < paths.size()) {
      LOG.warn("Deleted only {} of {} file(s) ({})", deletedFilesCount, paths.size(), context);
    } else {
      LOG.info("Deleted {} file(s) ({})", paths.size(), context);
    }
  }
}
