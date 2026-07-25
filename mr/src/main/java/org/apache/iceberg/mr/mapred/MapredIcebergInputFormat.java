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
package org.apache.iceberg.mr.mapred;

import java.io.IOException;
import java.util.Optional;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.StatusReporter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.task.JobContextImpl;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.mr.mapreduce.IcebergSplit;
import org.apache.iceberg.mr.mapreduce.IcebergSplitContainer;

/**
 * 文件级说明：Iceberg 的 MR v1（mapred）InputFormat，委托给 MR v2 实现。
 *
 * <p>所属模块：iceberg-mr（mapred 子包；为老版 mapred API 用户提供 Iceberg 读取入口， 被 HiveIcebergInputFormat 等继承复用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 mapred {@link InputFormat}，内部组合一个 v2 {@link
 *       org.apache.iceberg.mr.mapreduce.IcebergInputFormat} 实例。
 *   <li>把 JobConf/Reporter 包装为 v2 JobContext/TaskAttemptContext，桥接两套 API。
 *   <li>提供 {@link CompatibilityTaskAttemptContextImpl} 保留老版 Reporter 引用， 供 Hive 向量化 reader 等需要
 *       Reporter 的场景使用。
 * </ul>
 *
 * <p>设计意图：核心切分/读取逻辑只实现一次（v2），通过本类做 API 适配，避免双份代码。 {@link #toStatusReporter} 把老版 Reporter 适配为 v2
 * StatusReporter。
 *
 * <p>上下游关系：上游被 {@link org.apache.iceberg.mr.hive.HiveIcebergInputFormat} 继承； 下游委托给 v2 {@link
 * org.apache.iceberg.mr.mapreduce.IcebergInputFormat}。
 *
 * @param <T> 记录类型，默认 {@link Record}
 */
public class MapredIcebergInputFormat<T> implements InputFormat<Void, Container<T>> {

  private final org.apache.iceberg.mr.mapreduce.IcebergInputFormat<T> innerInputFormat;

  public MapredIcebergInputFormat() {
    this.innerInputFormat = new org.apache.iceberg.mr.mapreduce.IcebergInputFormat<>();
  }

  /**
   * 配置 JobConf 使用本 InputFormat，并返回 {@link InputFormatConfig.ConfigBuilder} 以便进一步配置。
   *
   * @param job JobConf
   * @return 配置构造器
   */
  public static InputFormatConfig.ConfigBuilder configure(JobConf job) {
    job.setInputFormat(MapredIcebergInputFormat.class);
    return new InputFormatConfig.ConfigBuilder(job);
  }

  /**
   * 计算输入切分，委托给内部 v2 InputFormat。
   *
   * <p>逻辑：把 JobConf 包装为 v2 JobContext，调用 v2 getSplits，再把 v2 InputSplit 数组转为 mapred InputSplit 数组。
   *
   * @param job JobConf
   * @param numSplits 期望切分数
   * @return mapred InputSplit 数组
   */
  @Override
  public InputSplit[] getSplits(JobConf job, int numSplits) throws IOException {
    return innerInputFormat.getSplits(newJobContext(job)).stream()
        .map(InputSplit.class::cast)
        .toArray(InputSplit[]::new);
  }

  /**
   * 创建 RecordReader。
   *
   * <p>逻辑：从 split 取出 IcebergSplit，构造 {@link MapredIcebergRecordReader}。
   *
   * @param split 输入切分
   * @param job JobConf
   * @param reporter 进度上报器
   * @return RecordReader
   */
  @Override
  public RecordReader<Void, Container<T>> getRecordReader(
      InputSplit split, JobConf job, Reporter reporter) throws IOException {
    IcebergSplit icebergSplit = ((IcebergSplitContainer) split).icebergSplit();
    return new MapredIcebergRecordReader<>(innerInputFormat, icebergSplit, job, reporter);
  }

  /**
   * mapred RecordReader 实现，桥接 v2 RecordReader 到 mapred 接口。
   *
   * <p>持有 splitLength 用于 {@link #getPos()} 计算。
   */
  private static final class MapredIcebergRecordReader<T>
      extends AbstractMapredIcebergRecordReader<Container<T>> {

    private final long splitLength; // for getPos()

    MapredIcebergRecordReader(
        org.apache.iceberg.mr.mapreduce.IcebergInputFormat<T> mapreduceInputFormat,
        IcebergSplit split,
        JobConf job,
        Reporter reporter)
        throws IOException {
      super(mapreduceInputFormat, split, job, reporter);
      splitLength = split.getLength();
    }

    /**
     * 推进到下一条记录。
     *
     * <p>逻辑：调用内部 v2 reader 的 nextKeyValue；若有值则把当前 value 填入 Container 并返回 true。
     *
     * @param key 不使用
     * @param value 用于承载当前记录的 Container
     * @return true 表示有下一条记录
     */
    @Override
    public boolean next(Void key, Container<T> value) throws IOException {
      try {
        if (innerReader.nextKeyValue()) {
          value.set((T) innerReader.getCurrentValue());
          return true;
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(ie);
      }

      return false;
    }

    /** 创建并返回一个新的空 Container。 */
    @Override
    public Container<T> createValue() {
      return new Container<>();
    }

    /** 返回已读字节估算：splitLength * progress。 */
    @Override
    public long getPos() throws IOException {
      return (long) (splitLength * getProgress());
    }
  }

  /** 从 JobConf 构造 v2 JobContext，JobID 缺失时使用新建的 JobID。 */
  private static JobContext newJobContext(JobConf job) {
    JobID jobID = Optional.ofNullable(JobID.forName(job.get(JobContext.ID))).orElseGet(JobID::new);

    return new JobContextImpl(job, jobID);
  }

  /**
   * 从 JobConf + Reporter 构造 v2 TaskAttemptContext。
   *
   * <p>使用 {@link CompatibilityTaskAttemptContextImpl} 以保留老版 Reporter 引用。
   *
   * @param job JobConf
   * @param reporter 老版 Reporter
   * @return v2 TaskAttemptContext
   */
  public static TaskAttemptContext newTaskAttemptContext(JobConf job, Reporter reporter) {
    TaskAttemptID taskAttemptID =
        Optional.ofNullable(TaskAttemptID.forName(job.get(JobContext.TASK_ATTEMPT_ID)))
            .orElseGet(TaskAttemptID::new);

    return new CompatibilityTaskAttemptContextImpl(job, taskAttemptID, reporter);
  }

  /**
   * 兼容性 TaskAttemptContext，额外持有老版 {@link Reporter} 引用。
   *
   * <p>设计意图：Hive 向量化 reader 等场景需要访问老版 Reporter，因此在此处保存以便后续取出。
   */
  public static class CompatibilityTaskAttemptContextImpl extends TaskAttemptContextImpl {

    private final Reporter legacyReporter;

    public CompatibilityTaskAttemptContextImpl(
        Configuration conf, TaskAttemptID taskId, Reporter reporter) {
      super(conf, taskId, toStatusReporter(reporter));
      this.legacyReporter = reporter;
    }

    /** 返回保留的老版 Reporter。 */
    public Reporter getLegacyReporter() {
      return legacyReporter;
    }
  }

  /** 把老版 {@link Reporter} 适配为 v2 {@link StatusReporter}。 */
  private static StatusReporter toStatusReporter(Reporter reporter) {
    return new StatusReporter() {
      @Override
      public Counter getCounter(Enum<?> name) {
        return reporter.getCounter(name);
      }

      @Override
      public Counter getCounter(String group, String name) {
        return reporter.getCounter(group, name);
      }

      @Override
      public void progress() {
        reporter.progress();
      }

      @Override
      public float getProgress() {
        return reporter.getProgress();
      }

      @Override
      public void setStatus(String status) {
        reporter.setStatus(status);
      }
    };
  }
}
