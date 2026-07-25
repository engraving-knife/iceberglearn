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
import java.util.Map;
import org.apache.hadoop.hive.ql.exec.FileSinkOperator;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TaskAttemptID;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.io.PartitionedFanoutWriter;
import org.apache.iceberg.mr.mapred.Container;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Iceberg 的 Hive 记录写入器，按分区 fan-out 写数据文件。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，是 Hive 写 Iceberg 表 的真正执行者，被 {@link
 * HiveIcebergOutputFormat} 创建）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>同时实现 Hive {@link FileSinkOperator.RecordWriter} 与 MapReduce {@link
 *       org.apache.hadoop.mapred.RecordWriter} 双接口。
 *   <li>继承 {@link PartitionedFanoutWriter}，按分区键路由记录到不同的 appender， 达到目标大小后滚动出新文件。
 *   <li>维护“TaskAttemptID -> (表名 -> writer)”的静态并发映射，供 {@link HiveIcebergOutputCommitter}
 *       在任务提交时按表取出已生成的数据文件。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>复用 {@link PartitionKey} 对象避免每条记录都创建新对象。
 *   <li>静态 writers map 用并发 map，因为部分执行引擎（如 Tez）会共享容器，需要线程安全。
 *   <li>close(abort=true) 时并行删除已写出的数据文件，使用 worker 池加速。
 * </ul>
 *
 * <p>上下游关系：上游由 {@link HiveIcebergOutputFormat} 创建；下游由 {@link HiveIcebergOutputCommitter#commitTask}
 * 调用 {@link #dataFiles()} 取出已关闭文件， 父类 {@link PartitionedFanoutWriter} 负责 appender 滚动与文件生成。
 */
class HiveIcebergRecordWriter extends PartitionedFanoutWriter<Record>
    implements FileSinkOperator.RecordWriter,
        org.apache.hadoop.mapred.RecordWriter<NullWritable, Container<Record>> {
  private static final Logger LOG = LoggerFactory.getLogger(HiveIcebergRecordWriter.class);

  // The current key is reused at every write to avoid unnecessary object creation
  private final PartitionKey currentKey;
  private final FileIO io;

  // <TaskAttemptId, <TABLE_NAME, HiveIcebergRecordWriter>> map to store the active writers
  // Stored in concurrent map, since some executor engines can share containers
  private static final Map<TaskAttemptID, Map<String, HiveIcebergRecordWriter>> writers =
      Maps.newConcurrentMap();

  /** 移除并返回指定任务的所有 writer 映射，供 commit/abort 阶段使用。 */
  static Map<String, HiveIcebergRecordWriter> removeWriters(TaskAttemptID taskAttemptID) {
    return writers.remove(taskAttemptID);
  }

  /** 返回指定任务当前的 writer 映射（不移除）。 */
  static Map<String, HiveIcebergRecordWriter> getWriters(TaskAttemptID taskAttemptID) {
    return writers.get(taskAttemptID);
  }

  /**
   * 构造写入器。
   *
   * <p>逻辑：调用父类 {@link PartitionedFanoutWriter} 构造器初始化分区 fan-out 写入； 创建可复用的 {@link
   * PartitionKey}；把自己注册到静态 writers map （TaskAttemptID -> 表名 -> 本 writer），便于后续 commit 取出。
   *
   * @param schema 表 schema
   * @param spec 分区规格
   * @param format 文件格式
   * @param appenderFactory appender 工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO
   * @param targetFileSize 目标文件大小（达到后滚动）
   * @param taskAttemptID 任务尝试 ID
   * @param tableName 表名（作为 writers map 的 key）
   */
  HiveIcebergRecordWriter(
      Schema schema,
      PartitionSpec spec,
      FileFormat format,
      FileAppenderFactory<Record> appenderFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSize,
      TaskAttemptID taskAttemptID,
      String tableName) {
    super(spec, format, appenderFactory, fileFactory, io, targetFileSize);
    this.io = io;
    this.currentKey = new PartitionKey(spec, schema);
    writers.putIfAbsent(taskAttemptID, Maps.newConcurrentMap());
    writers.get(taskAttemptID).put(tableName, this);
  }

  /**
   * 计算记录所属的分区键。
   *
   * <p>设计要点：复用 currentKey 对象，调用 {@link PartitionKey#partition(Record)} 原地更新， 避免每条记录都分配新对象。
   *
   * @param row 待写入记录
   * @return 当前记录对应的分区键（复用对象）
   */
  @Override
  protected PartitionKey partition(Record row) {
    currentKey.partition(row);
    return currentKey;
  }

  /**
   * Hive FileSinkOperator 写入入口：从 {@link Container} 取出 Record 后委托父类写入。
   *
   * @param row 包装在 Container 中的 Iceberg Record
   * @throws IOException 写入失败时抛出
   */
  @Override
  public void write(Writable row) throws IOException {
    super.write(((Container<Record>) row).get());
  }

  /**
   * MapReduce RecordWriter 写入入口：忽略 key，直接委托给 {@link #write(Writable)}。
   *
   * @param key NullWritable key（不使用）
   * @param value 包装在 Container 中的 Record
   * @throws IOException 写入失败时抛出
   */
  @Override
  public void write(NullWritable key, Container value) throws IOException {
    write(value);
  }

  /**
   * 关闭写入器。
   *
   * <p>逻辑：调用父类 {@link PartitionedFanoutWriter#dataFiles()} 取出已生成的数据文件； 若 abort 为真，则用 worker
   * 池并行删除这些文件（清理失败仅记 debug 日志）。
   *
   * @param abort true 表示中止写入并删除已生成文件；false 表示正常关闭
   * @throws IOException 关闭或删除失败时抛出
   */
  @Override
  public void close(boolean abort) throws IOException {
    DataFile[] dataFiles = super.dataFiles();

    // If abort then remove the unnecessary files
    if (abort) {
      Tasks.foreach(dataFiles)
          .executeWith(ThreadPools.getWorkerPool())
          .retry(3)
          .suppressFailureWhenFinished()
          .onFailure(
              (file, exception) ->
                  LOG.debug("Failed on to remove file {} on abort", file, exception))
          .run(dataFile -> io.deleteFile(dataFile.path().toString()));
    }

    LOG.info(
        "IcebergRecordWriter is closed with abort={}. Created {} files", abort, dataFiles.length);
  }

  /**
   * Hive Reporter 关闭入口：以非中止模式调用 {@link #close(boolean)}。
   *
   * @param reporter 进度上报器（不使用）
   * @throws IOException 关闭失败时抛出
   */
  @Override
  public void close(Reporter reporter) throws IOException {
    close(false);
  }
}
