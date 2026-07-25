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
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.iceberg.mr.mapreduce.IcebergSplit;

/**
 * 文件级说明：MR v1 RecordReader 的抽象基类，内部委托给 MR v2 RecordReader。
 *
 * <p>所属模块：iceberg-mr（mapred 子包；提供 Iceberg 在老版 mapred API 下的 RecordReader 适配）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link RecordReader}（mapred 接口），把调用委托给内部持有的 mapreduce （v2）{@link
 *       org.apache.hadoop.mapreduce.RecordReader}。
 *   <li>在构造时通过 {@link MapredIcebergInputFormat#newTaskAttemptContext} 把 JobConf/Reporter 包装为 v2
 *       {@link TaskAttemptContext}，并初始化内部 reader。
 * </ul>
 *
 * <p>设计意图：Iceberg 核心读取逻辑只实现一次（基于 v2 API），通过本类做 v1 -> v2 适配， 避免维护两套实现。子类负责 nextKeyValue/value 的具体桥接。
 *
 * <p>上下游关系：上游被 {@link MapredIcebergInputFormat} 的子类实例化；下游持有 v2 RecordReader 与 IcebergSplit。
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public abstract class AbstractMapredIcebergRecordReader<T> implements RecordReader<Void, T> {

  protected final org.apache.hadoop.mapreduce.RecordReader<Void, ?> innerReader;

  /**
   * 构造并初始化 RecordReader。
   *
   * <p>逻辑：把 JobConf + Reporter 包装为 v2 TaskAttemptContext，调用 {@link
   * org.apache.iceberg.mr.mapreduce.IcebergInputFormat#createRecordReader} 创建内部 reader， 再
   * initialize。InterruptedException 时恢复中断状态并包装为 RuntimeException。
   *
   * @param mapreduceInputFormat v2 InputFormat
   * @param split Iceberg 切分
   * @param job JobConf
   * @param reporter 进度上报器
   * @throws IOException 初始化失败时抛出
   */
  public AbstractMapredIcebergRecordReader(
      org.apache.iceberg.mr.mapreduce.IcebergInputFormat<?> mapreduceInputFormat,
      IcebergSplit split,
      JobConf job,
      Reporter reporter)
      throws IOException {
    TaskAttemptContext context = MapredIcebergInputFormat.newTaskAttemptContext(job, reporter);

    try {
      innerReader = mapreduceInputFormat.createRecordReader(split, context);
      innerReader.initialize(split, context);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  /** 返回 key，固定为 null（Iceberg 读取无 key）。 */
  @Override
  public Void createKey() {
    return null;
  }

  /**
   * 返回读取进度，委托给内部 reader。
   *
   * @return 进度 0~1
   * @throws IOException 获取进度失败时抛出
   */
  @Override
  public float getProgress() throws IOException {
    try {
      return innerReader.getProgress();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  /** 关闭内部 reader。 */
  @Override
  public void close() throws IOException {
    if (innerReader != null) {
      innerReader.close();
    }
  }
}
