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

import java.util.Properties;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.api.hive_metastoreConstants;
import org.apache.hadoop.hive.ql.exec.FileSinkOperator;
import org.apache.hadoop.hive.ql.io.HiveOutputFormat;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.TaskAttemptID;
import org.apache.hadoop.util.Progressable;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.mr.Catalogs;
import org.apache.iceberg.mr.mapred.Container;
import org.apache.iceberg.util.PropertyUtil;

/**
 * 文件级说明：Iceberg 的 Hive OutputFormat 实现（mapred 老接口）。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，是 Hive 写 Iceberg 表 的入口，负责为每个任务创建 {@link
 * HiveIcebergRecordWriter}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link OutputFormat} 与 {@link HiveOutputFormat} 双接口，分别供 MapReduce 与 Hive
 *       FileSinkOperator 调用。
 *   <li>从 JobConf 与表属性中收集 schema/spec/fileFormat/targetFileSize 等，构造写入器。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>两个接口的 writer 创建路径最终都委托给同一个 {@link #writer(JobConf)} 方法，避免逻辑 重复。
 *   <li>文件格式、目标文件大小等参数取自 Iceberg 表属性（{@link TableProperties}），保持与 Iceberg 引擎本身一致的行为。
 *   <li>operationId 由 queryId + jobId 组成，确保同作业多任务生成的文件名不冲突。
 * </ul>
 *
 * <p>上下游关系：上游由 Hive 执行引擎在写入阶段调用；下游创建 {@link HiveIcebergRecordWriter} 真正写数据文件，并依赖 {@link
 * HiveIcebergStorageHandler} 提供 table/schema 信息。
 */
public class HiveIcebergOutputFormat<T>
    implements OutputFormat<NullWritable, Container<Record>>,
        HiveOutputFormat<NullWritable, Container<Record>> {

  /**
   * Hive FileSinkOperator 入口：创建记录写入器。
   *
   * <p>Hive 通过此方法获取 writer，参数中 tableAndSerDeProperties 已被 StorageHandler 处理过， 这里直接委托给 {@link
   * #writer(JobConf)}。
   *
   * @param jc JobConf
   * @param finalOutPath 最终输出路径（Iceberg 内部自己管理路径，这里不使用）
   * @param valueClass 值类型
   * @param isCompressed 是否压缩（由 Iceberg 表属性决定，这里忽略）
   * @param tableAndSerDeProperties 表与 SerDe 属性
   * @param progress 进度上报器
   * @return Hive 记录写入器
   */
  @Override
  public FileSinkOperator.RecordWriter getHiveRecordWriter(
      JobConf jc,
      Path finalOutPath,
      Class valueClass,
      boolean isCompressed,
      Properties tableAndSerDeProperties,
      Progressable progress) {
    return writer(jc);
  }

  /**
   * MapReduce OutputFormat 入口：创建记录写入器。
   *
   * <p>委托给 {@link #writer(JobConf)}。
   *
   * @param ignored 文件系统（不使用）
   * @param job JobConf
   * @param name 输出文件名（不使用，Iceberg 自己命名）
   * @param progress 进度上报器
   * @return 记录写入器
   */
  @Override
  public org.apache.hadoop.mapred.RecordWriter<NullWritable, Container<Record>> getRecordWriter(
      FileSystem ignored, JobConf job, String name, Progressable progress) {
    return writer(job);
  }

  /** 输出规格检查，当前不做任何检查。 */
  @Override
  public void checkOutputSpecs(FileSystem ignored, JobConf job) {
    // Not doing any check.
  }

  /**
   * 实际构造 {@link HiveIcebergRecordWriter} 的工厂方法。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从 JobConf 取 TaskAttemptID（经 TezUtil 包装以兼容 Tez）。
   *   <li>从 StorageHandler 序列化的配置中加载目标 Table、Schema、PartitionSpec。
   *   <li>从表属性读取文件格式与目标文件大小。
   *   <li>计算 partitionId/taskId/operationId，构造 {@link OutputFileFactory}。
   *   <li>组装 {@link HiveIcebergRecordWriter}。
   * </ol>
   *
   * @param jc JobConf
   * @return Iceberg 记录写入器
   */
  private static HiveIcebergRecordWriter writer(JobConf jc) {
    TaskAttemptID taskAttemptID = TezUtil.taskAttemptWrapper(jc);
    // It gets the config from the FileSinkOperator which has its own config for every target table
    Table table =
        HiveIcebergStorageHandler.table(jc, jc.get(hive_metastoreConstants.META_TABLE_NAME));
    Schema schema = HiveIcebergStorageHandler.schema(jc);
    PartitionSpec spec = table.spec();
    FileFormat fileFormat =
        FileFormat.fromString(
            PropertyUtil.propertyAsString(
                table.properties(),
                TableProperties.DEFAULT_FILE_FORMAT,
                TableProperties.DEFAULT_FILE_FORMAT_DEFAULT));
    long targetFileSize =
        PropertyUtil.propertyAsLong(
            table.properties(),
            TableProperties.WRITE_TARGET_FILE_SIZE_BYTES,
            TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT);
    FileIO io = table.io();
    int partitionId = taskAttemptID.getTaskID().getId();
    int taskId = taskAttemptID.getId();
    String operationId =
        jc.get(HiveConf.ConfVars.HIVEQUERYID.varname) + "-" + taskAttemptID.getJobID();
    OutputFileFactory outputFileFactory =
        OutputFileFactory.builderFor(table, partitionId, taskId)
            .format(fileFormat)
            .operationId(operationId)
            .build();
    String tableName = jc.get(Catalogs.NAME);

    return new HiveIcebergRecordWriter(
        schema,
        spec,
        fileFormat,
        new GenericAppenderFactory(schema, spec),
        outputFileFactory,
        io,
        targetFileSize,
        taskAttemptID,
        tableName);
  }
}
