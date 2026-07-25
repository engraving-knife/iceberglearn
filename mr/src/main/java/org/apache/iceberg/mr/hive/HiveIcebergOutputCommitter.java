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
package org.apache.iceberg.mr.hive;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobContext;
import org.apache.hadoop.mapred.OutputCommitter;
import org.apache.hadoop.mapred.TaskAttemptContext;
import org.apache.hadoop.mapred.TaskAttemptID;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.TaskType;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.hadoop.Util;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.mr.Catalogs;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Iceberg 写入路径的 Hive OutputCommitter，负责把任务产出的数据文件提交到 Iceberg 表。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，是 Hive 写 Iceberg 表 的提交器，独立于 Hive ACID 事务）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>任务级提交（commitTask）：把每个任务生成的 {@link DataFile} 列表序列化为 .forCommit 文件， 落到表 temp 目录。
 *   <li>作业级提交（commitJob）：并行收集所有任务的 .forCommit 文件，按目标表聚合后调用 {@link AppendFiles} 提交到 Iceberg 表。
 *   <li>中止/清理：abortTask/abortJob 删除已生成的数据文件与临时目录。
 *   <li>支持多表写入（Hive 多表 insert）与 Tez/MapReduce 两种执行引擎。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>两阶段提交：task 写数据文件 + forCommit 元数据，job 阶段才真正 append 到 Iceberg 表， 失败时可整体回滚。
 *   <li>forCommit 文件按 {@code task-<id>.forCommit} 命名，job 阶段按预期任务数顺序读取， 假设 taskId 从 0 连续生成。
 *   <li>使用两个线程池：fileExecutor 读 forCommit 文件，tableExecutor 并行处理多表， 大小均可由配置控制；tableExecutor 在单表时返回
 *       null 避免无谓线程开销。
 *   <li>任务失败容忍：用 {@link Tasks} 提供的 retry/suppressFailure 控制不同阶段的失败语义。
 * </ul>
 *
 * <p>上下游关系：上游由 Hive/Tez 执行引擎在 job/task 提交阶段调用；下游依赖 {@link HiveIcebergRecordWriter}（取任务产出文件）、{@link
 * Catalogs}（加载表）、 {@link Table#newAppend()}（提交快照）。
 */
public class HiveIcebergOutputCommitter extends OutputCommitter {
  private static final String FOR_COMMIT_EXTENSION = ".forCommit";

  private static final Logger LOG = LoggerFactory.getLogger(HiveIcebergOutputCommitter.class);

  /** 作业级初始化，当前无操作。 */
  @Override
  public void setupJob(JobContext jobContext) {
    // do nothing.
  }

  /** 任务级初始化，当前无操作。 */
  @Override
  public void setupTask(TaskAttemptContext taskAttemptContext) {
    // do nothing.
  }

  /**
   * 判断该任务是否需要提交。
   *
   * <p>逻辑：Reduce 阶段任务需要提交；若作业无 reducer（map-only），则 map 任务也需要提交。
   *
   * @param context 任务上下文
   * @return true 表示需要调用 commitTask
   */
  @Override
  public boolean needsTaskCommit(TaskAttemptContext context) {
    // We need to commit if this is the last phase of a MapReduce process
    return TaskType.REDUCE.equals(context.getTaskAttemptID().getTaskID().getTaskType())
        || context.getJobConf().getNumReduceTasks() == 0;
  }

  /**
   * 收集任务产出的数据文件，并写入 .forCommit 提交文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>通过 {@link TezUtil} 包装 context 以兼容 Tez 的 TaskAttemptID。
   *   <li>取出本任务的所有目标表与对应 {@link HiveIcebergRecordWriter}。
   *   <li>用 tableExecutor 并行处理每个目标表：从 writer 取已关闭数据文件， 生成 forCommit 文件路径并调用 {@link
   *       #createFileForCommit} 序列化写入。
   *   <li>提交完成后清理 writer 本地缓存。
   * </ol>
   *
   * @param originalContext 任务上下文（可能为 Tez 包装）
   * @throws IOException 写 forCommit 文件失败时抛出
   */
  @Override
  public void commitTask(TaskAttemptContext originalContext) throws IOException {
    TaskAttemptContext context = TezUtil.enrichContextWithAttemptWrapper(originalContext);

    TaskAttemptID attemptID = context.getTaskAttemptID();
    JobConf jobConf = context.getJobConf();
    Collection<String> outputs = HiveIcebergStorageHandler.outputTables(context.getJobConf());
    Map<String, HiveIcebergRecordWriter> writers =
        Optional.ofNullable(HiveIcebergRecordWriter.getWriters(attemptID))
            .orElseGet(
                () -> {
                  LOG.info(
                      "CommitTask found no writers for output tables: {}, attemptID: {}",
                      outputs,
                      attemptID);
                  return ImmutableMap.of();
                });

    ExecutorService tableExecutor = tableExecutor(jobConf, outputs.size());
    try {
      // Generates commit files for the target tables in parallel
      Tasks.foreach(outputs)
          .retry(3)
          .stopOnFailure()
          .throwFailureWhenFinished()
          .executeWith(tableExecutor)
          .run(
              output -> {
                Table table = HiveIcebergStorageHandler.table(context.getJobConf(), output);
                if (table != null) {
                  HiveIcebergRecordWriter writer = writers.get(output);
                  DataFile[] closedFiles;
                  if (writer != null) {
                    closedFiles = writer.dataFiles();
                  } else {
                    LOG.info(
                        "CommitTask found no writer for specific table: {}, attemptID: {}",
                        output,
                        attemptID);
                    closedFiles = new DataFile[0];
                  }
                  // Creating the file containing the data files generated by this task for this
                  // table
                  String fileForCommitLocation =
                      generateFileForCommitLocation(
                          table.location(),
                          jobConf,
                          attemptID.getJobID(),
                          attemptID.getTaskID().getId());
                  createFileForCommit(closedFiles, fileForCommitLocation, table.io());
                } else {
                  // When using Tez multi-table inserts, we could have more output tables in config
                  // than
                  // the actual tables this task has written to and has serialized in its config
                  LOG.info("CommitTask found no serialized table in config for table: {}.", output);
                }
              },
              IOException.class);
    } finally {
      if (tableExecutor != null) {
        tableExecutor.shutdown();
      }
    }

    // remove the writer to release the object
    HiveIcebergRecordWriter.removeWriters(attemptID);
  }

  /**
   * 中止任务：清理本任务的 writer 与已生成的数据文件。
   *
   * @param originalContext 任务上下文
   * @throws IOException 关闭 writer 失败时抛出
   */
  @Override
  public void abortTask(TaskAttemptContext originalContext) throws IOException {
    TaskAttemptContext context = TezUtil.enrichContextWithAttemptWrapper(originalContext);

    // Clean up writer data from the local store
    Map<String, HiveIcebergRecordWriter> writers =
        HiveIcebergRecordWriter.removeWriters(context.getTaskAttemptID());

    // Remove files if it was not done already
    if (writers != null) {
      for (HiveIcebergRecordWriter writer : writers.values()) {
        writer.close(true);
      }
    }
  }

  /**
   * 作业级提交：并行收集所有任务的 forCommit 文件，按表聚合后 append 到 Iceberg 表，最后清理临时目录。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>对每个目标表，加载表与 catalogName，生成 job 临时目录路径。
   *   <li>调用 {@link #commitTable} 收集 dataFiles 并 {@link AppendFiles#commit()}。
   *   <li>统计耗时后调用 {@link #cleanup} 删除所有临时目录。
   * </ol>
   *
   * @param originalContext 作业上下文
   * @throws IOException 文件访问失败时抛出
   */
  @Override
  public void commitJob(JobContext originalContext) throws IOException {
    JobContext jobContext = TezUtil.enrichContextWithVertexId(originalContext);
    JobConf jobConf = jobContext.getJobConf();

    long startTime = System.currentTimeMillis();
    LOG.info("Committing job {} has started", jobContext.getJobID());

    Collection<String> outputs = HiveIcebergStorageHandler.outputTables(jobContext.getJobConf());
    Collection<String> jobLocations = new ConcurrentLinkedQueue<>();

    ExecutorService fileExecutor = fileExecutor(jobConf);
    ExecutorService tableExecutor = tableExecutor(jobConf, outputs.size());
    try {
      // Commits the changes for the output tables in parallel
      Tasks.foreach(outputs)
          .throwFailureWhenFinished()
          .stopOnFailure()
          .executeWith(tableExecutor)
          .run(
              output -> {
                Table table = HiveIcebergStorageHandler.table(jobConf, output);
                if (table != null) {
                  String catalogName = HiveIcebergStorageHandler.catalogName(jobConf, output);
                  jobLocations.add(
                      generateJobLocation(table.location(), jobConf, jobContext.getJobID()));
                  commitTable(
                      table.io(), fileExecutor, jobContext, output, table.location(), catalogName);
                } else {
                  LOG.info(
                      "CommitJob found no serialized table in config for table: {}. Skipping job commit.",
                      output);
                }
              });
    } finally {
      fileExecutor.shutdown();
      if (tableExecutor != null) {
        tableExecutor.shutdown();
      }
    }

    LOG.info(
        "Commit took {} ms for job {}",
        System.currentTimeMillis() - startTime,
        jobContext.getJobID());

    cleanup(jobContext, jobLocations);
  }

  /**
   * 作业级中止：删除已生成的数据文件与 forCommit 文件，并清理临时目录。
   *
   * <p>逻辑：并行对每个目标表收集已提交数据文件，逐个删除；删除失败仅告警，确保中止流程完成。
   *
   * @param originalContext 作业上下文
   * @param status 作业状态码
   * @throws IOException 删除文件失败时抛出
   */
  @Override
  public void abortJob(JobContext originalContext, int status) throws IOException {
    JobContext jobContext = TezUtil.enrichContextWithVertexId(originalContext);
    JobConf jobConf = jobContext.getJobConf();

    LOG.info("Job {} is aborted. Data file cleaning started", jobContext.getJobID());
    Collection<String> outputs = HiveIcebergStorageHandler.outputTables(jobContext.getJobConf());
    Collection<String> jobLocations = new ConcurrentLinkedQueue<>();

    ExecutorService fileExecutor = fileExecutor(jobConf);
    ExecutorService tableExecutor = tableExecutor(jobConf, outputs.size());
    try {
      // Cleans up the changes for the output tables in parallel
      Tasks.foreach(outputs)
          .suppressFailureWhenFinished()
          .executeWith(tableExecutor)
          .onFailure((output, exc) -> LOG.warn("Failed cleanup table {} on abort job", output, exc))
          .run(
              output -> {
                LOG.info("Cleaning table {} with job id {}", output, jobContext.getJobID());
                Table table = HiveIcebergStorageHandler.table(jobConf, output);
                jobLocations.add(
                    generateJobLocation(table.location(), jobConf, jobContext.getJobID()));
                Collection<DataFile> dataFiles =
                    dataFiles(fileExecutor, table.location(), jobContext, table.io(), false);

                // Check if we have files already committed and remove data files if there are any
                if (dataFiles.size() > 0) {
                  Tasks.foreach(dataFiles)
                      .retry(3)
                      .suppressFailureWhenFinished()
                      .executeWith(fileExecutor)
                      .onFailure(
                          (file, exc) ->
                              LOG.warn(
                                  "Failed to remove data file {} on abort job", file.path(), exc))
                      .run(file -> table.io().deleteFile(file.path().toString()));
                }
              });
    } finally {
      fileExecutor.shutdown();
      if (tableExecutor != null) {
        tableExecutor.shutdown();
      }
    }

    LOG.info("Job {} is aborted. Data file cleaning finished", jobContext.getJobID());

    cleanup(jobContext, jobLocations);
  }

  /**
   * 提交单张表：收集该表所有任务的 dataFiles，调用 {@link AppendFiles} 提交到 Iceberg 表。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>用 name/location/catalogName 组装 catalog 属性，加载 {@link Table}。
   *   <li>调用 {@link #dataFiles} 并行读取所有 forCommit 文件得到 DataFile 列表。
   *   <li>若列表非空，创建 {@link AppendFiles}，append 所有文件后 commit；否则跳过提交。
   * </ol>
   *
   * @param io 读取 forCommit 文件的 FileIO
   * @param executor 并行读 forCommit 文件的执行器
   * @param jobContext 作业上下文
   * @param name 表标识符
   * @param location 表路径
   * @param catalogName catalog 名称
   */
  private void commitTable(
      FileIO io,
      ExecutorService executor,
      JobContext jobContext,
      String name,
      String location,
      String catalogName) {
    JobConf conf = jobContext.getJobConf();
    Properties catalogProperties = new Properties();
    catalogProperties.put(Catalogs.NAME, name);
    catalogProperties.put(Catalogs.LOCATION, location);
    if (catalogName != null) {
      catalogProperties.put(InputFormatConfig.CATALOG_NAME, catalogName);
    }
    Table table = Catalogs.loadTable(conf, catalogProperties);

    long startTime = System.currentTimeMillis();
    LOG.info(
        "Committing job has started for table: {}, using location: {}",
        table,
        generateJobLocation(location, conf, jobContext.getJobID()));

    Collection<DataFile> dataFiles = dataFiles(executor, location, jobContext, io, true);

    if (dataFiles.size() > 0) {
      // Appending data files to the table
      AppendFiles append = table.newAppend();
      dataFiles.forEach(append::appendFile);
      append.commit();
      LOG.info(
          "Commit took {} ms for table: {} with {} file(s)",
          System.currentTimeMillis() - startTime,
          table,
          dataFiles.size());
      LOG.debug("Added files {}", dataFiles);
    } else {
      LOG.info(
          "Commit took {} ms for table: {} with no new files",
          System.currentTimeMillis() - startTime,
          table);
    }
  }

  /**
   * 清理作业的临时目录：递归删除每个目标表的 job 临时目录。
   *
   * <p>删除失败仅告警，不抛异常。
   *
   * @param jobContext 作业上下文
   * @param jobLocations 待清理的临时目录集合
   * @throws IOException 不直接抛出，内部捕获
   */
  private void cleanup(JobContext jobContext, Collection<String> jobLocations) throws IOException {
    JobConf jobConf = jobContext.getJobConf();

    LOG.info("Cleaning for job {} started", jobContext.getJobID());

    // Remove the job's temp directories recursively.
    Tasks.foreach(jobLocations)
        .retry(3)
        .suppressFailureWhenFinished()
        .onFailure(
            (jobLocation, exc) ->
                LOG.debug("Failed to remove directory {} on job cleanup", jobLocation, exc))
        .run(
            jobLocation -> {
              LOG.info("Cleaning location: {}", jobLocation);
              Path toDelete = new Path(jobLocation);
              FileSystem fs = Util.getFs(toDelete, jobConf);
              fs.delete(toDelete, true);
            },
            IOException.class);

    LOG.info("Cleaning for job {} finished", jobContext.getJobID());
  }

  /**
   * 创建用于并行读取 forCommit 文件的固定线程池。
   *
   * <p>线程池大小由 {@link InputFormatConfig#COMMIT_FILE_THREAD_POOL_SIZE} 控制； 多表提交时应共享同一个实例。
   *
   * @param conf 配置
   * @return 文件读取线程池
   */
  private static ExecutorService fileExecutor(Configuration conf) {
    int size =
        conf.getInt(
            InputFormatConfig.COMMIT_FILE_THREAD_POOL_SIZE,
            InputFormatConfig.COMMIT_FILE_THREAD_POOL_SIZE_DEFAULT);
    return Executors.newFixedThreadPool(
        size,
        new ThreadFactoryBuilder()
            .setDaemon(true)
            .setPriority(Thread.NORM_PRIORITY)
            .setNameFormat("iceberg-commit-file-pool-%d")
            .build());
  }

  /**
   * 创建用于并行处理多表的固定线程池。
   *
   * <p>逻辑：取配置大小与 maxThreadNum 的较小值；若结果 <=1 则返回 null（无需并行）。
   *
   * @param conf 配置
   * @param maxThreadNum 期望处理的最大请求数
   * @return 表处理线程池，或 null 表示不需要并行
   */
  private static ExecutorService tableExecutor(Configuration conf, int maxThreadNum) {
    int size =
        conf.getInt(
            InputFormatConfig.COMMIT_TABLE_THREAD_POOL_SIZE,
            InputFormatConfig.COMMIT_TABLE_THREAD_POOL_SIZE_DEFAULT);
    size = Math.min(maxThreadNum, size);
    if (size > 1) {
      return Executors.newFixedThreadPool(
          size,
          new ThreadFactoryBuilder()
              .setDaemon(true)
              .setPriority(Thread.NORM_PRIORITY)
              .setNameFormat("iceberg-commit-table-pool-%d")
              .build());
    } else {
      return null;
    }
  }

  /**
   * 收集本作业某张表所有任务提交的 DataFile。
   *
   * <p>逻辑：根据 reduce/map 任务数计算期望文件数，按 taskId 0..N-1 顺序生成 forCommit 文件路径， 并行读取后聚合。throwOnFailure
   * 控制读取失败是否抛出（commit 用 true，abort 用 false）。
   *
   * @param executor 并行读取执行器
   * @param location 表路径
   * @param jobContext 作业上下文
   * @param io FileIO
   * @param throwOnFailure true 时读取失败抛异常
   * @return 收集到的 DataFile 列表
   */
  private static Collection<DataFile> dataFiles(
      ExecutorService executor,
      String location,
      JobContext jobContext,
      FileIO io,
      boolean throwOnFailure) {
    JobConf conf = jobContext.getJobConf();
    // If there are reducers, then every reducer will generate a result file.
    // If this is a map only task, then every mapper will generate a result file.
    int expectedFiles =
        conf.getNumReduceTasks() > 0 ? conf.getNumReduceTasks() : conf.getNumMapTasks();

    Collection<DataFile> dataFiles = new ConcurrentLinkedQueue<>();

    // Reading the committed files. The assumption here is that the taskIds are generated in
    // sequential order
    // starting from 0.
    Tasks.range(expectedFiles)
        .throwFailureWhenFinished(throwOnFailure)
        .executeWith(executor)
        .retry(3)
        .run(
            taskId -> {
              String taskFileName =
                  generateFileForCommitLocation(location, conf, jobContext.getJobID(), taskId);
              dataFiles.addAll(Arrays.asList(readFileForCommit(taskFileName, io)));
            });

    return dataFiles;
  }

  /**
   * 生成作业级临时目录路径：{@code <tableLocation>/temp/<queryId>-<jobId>}。
   *
   * @param location 表路径
   * @param conf 作业配置
   * @param jobId 作业 ID
   * @return 临时目录路径
   */
  @VisibleForTesting
  static String generateJobLocation(String location, Configuration conf, JobID jobId) {
    String queryId = conf.get(HiveConf.ConfVars.HIVEQUERYID.varname);
    return location + "/temp/" + queryId + "-" + jobId;
  }

  /**
   * 生成任务级 forCommit 文件路径：{@code <jobLocation>/task-<taskId>.forCommit}。
   *
   * @param location 表路径
   * @param conf 作业配置
   * @param jobId 作业 ID
   * @param taskId 任务 ID
   * @return forCommit 文件路径
   */
  private static String generateFileForCommitLocation(
      String location, Configuration conf, JobID jobId, int taskId) {
    return generateJobLocation(location, conf, jobId) + "/task-" + taskId + FOR_COMMIT_EXTENSION;
  }

  /**
   * 把 DataFile 数组序列化写入 forCommit 文件。
   *
   * @param closedFiles 待提交的数据文件数组
   * @param location forCommit 文件路径
   * @param io FileIO，用于创建输出文件
   * @throws IOException 写入失败时抛出
   */
  private static void createFileForCommit(DataFile[] closedFiles, String location, FileIO io)
      throws IOException {

    OutputFile fileForCommit = io.newOutputFile(location);
    try (ObjectOutputStream oos = new ObjectOutputStream(fileForCommit.createOrOverwrite())) {
      oos.writeObject(closedFiles);
    }
    LOG.debug("Iceberg committed file is created {}", fileForCommit);
  }

  /**
   * 读取 forCommit 文件并反序列化为 DataFile 数组。
   *
   * <p>读取或解析失败时抛出 {@link NotFoundException}。
   *
   * @param fileForCommitLocation forCommit 文件路径
   * @param io FileIO
   * @return 反序列化得到的 DataFile 数组
   */
  private static DataFile[] readFileForCommit(String fileForCommitLocation, FileIO io) {
    try (ObjectInputStream ois =
        new ObjectInputStream(io.newInputFile(fileForCommitLocation).newStream())) {
      return (DataFile[]) ois.readObject();
    } catch (ClassNotFoundException | IOException e) {
      throw new NotFoundException(
          "Can not read or parse committed file: %s", fileForCommitLocation);
    }
  }
}
