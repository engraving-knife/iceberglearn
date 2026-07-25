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
package org.apache.iceberg.mr.hive.vector;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.io.IOConstants;
import org.apache.hadoop.hive.ql.io.orc.OrcSplit;
import org.apache.hadoop.hive.ql.io.orc.VectorizedOrcInputFormat;
import org.apache.hadoop.hive.ql.io.parquet.VectorizedParquetInputFormat;
import org.apache.hadoop.hive.serde2.ColumnProjectionUtils;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mr.mapred.MapredIcebergInputFormat;
import org.apache.iceberg.orc.VectorizedReadUtils;
import org.apache.iceberg.parquet.ParquetSchemaUtil;
import org.apache.iceberg.parquet.TypeWithSchemaVisitor;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.orc.impl.OrcTail;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.schema.MessageType;

/**
 * 文件级说明：为 Hive3 创建向量化记录读取器的工具类。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块的向量化读取子包）。
 *
 * <p>职责：
 * <ul>
 *   <li>根据数据文件格式（ORC/Parquet）创建匹配的 Hive 原生向量化 RecordReader。</li>
 *   <li>对 Iceberg 期望 schema 做剪枝与列名翻译，使 Hive 读取器读取正确的列。</li>
 *   <li>处理 identity 分区列：从读取列中剔除这些列改为常量填充，
 *       避免重复从数据文件读取。</li>
 *   <li>把 Hive RecordReader 包装为 CloseableIterable 供上层 Iceberg 调用。</li>
 * </ul>
 *
 * <p>设计意图：复用 Hive3 内置的向量化读取器实现，由 Iceberg 负责列投影与
 * 分区列常量化，最大化读取性能并保持与 Hive3 语义兼容。
 *
 * <p>上下游关系：上游为 {@code IcebergInputFormat} 的向量化分支，下游为
 * Hive3 的 {@code VectorizedOrcInputFormat} / {@code VectorizedParquetInputFormat}。
 */
public class HiveVectorizedReader {

  /** 私有构造，工具类禁止实例化。 */
  private HiveVectorizedReader() {}

  /**
   * 为给定文件扫描任务创建向量化读取的可迭代结果集。
   *
   * <p>逻辑：
   * <ol>
   *   <li>从 TaskAttemptContext 取出 JobConf 与 Reporter。</li>
   *   <li>若表有分区，对 identity 分区列从读取列表中剔除，记录其列下标与常量值。</li>
   *   <li>按文件格式选择 ORC 或 Parquet 读取器。</li>
   *   <li>包装为 CloseableIterable 返回。</li>
   * </ol>
   *
   * @param inputFile 待读取的 Iceberg 输入文件
   * @param task 文件扫描任务，含偏移与长度
   * @param idToConstant 字段 ID 到常量值的映射（用于分区列填充）
   * @param context MR2 任务上下文
   * @param <D> 返回元素类型，通常为 VectorizedRowBatch
   * @return 可关闭的向量批迭代器
   */
  public static <D> CloseableIterable<D> reader(
      InputFile inputFile,
      FileScanTask task,
      Map<Integer, ?> idToConstant,
      TaskAttemptContext context) {
    JobConf job = (JobConf) context.getConfiguration();
    Path path = new Path(inputFile.location());
    FileFormat format = task.file().format();
    Reporter reporter =
        ((MapredIcebergInputFormat.CompatibilityTaskAttemptContextImpl) context)
            .getLegacyReporter();

    // Hive by default requires partition columns to be read too. This is not required for identity
    // partition
    // columns, as we will add this as constants later.

    int[] partitionColIndices = null;
    Object[] partitionValues = null;
    PartitionSpec partitionSpec = task.spec();

    if (!partitionSpec.isUnpartitioned()) {
      List<Integer> readColumnIds = ColumnProjectionUtils.getReadColumnIDs(job);

      List<PartitionField> fields = partitionSpec.fields();
      List<Integer> partitionColIndicesList = Lists.newLinkedList();
      List<Object> partitionValuesList = Lists.newLinkedList();

      for (PartitionField field : fields) {
        if (field.transform().isIdentity()) {
          // Skip reading identity partition columns from source file...
          int hiveColIndex = field.sourceId() - 1;
          readColumnIds.remove((Integer) hiveColIndex);

          // ...and use the corresponding constant value instead
          partitionColIndicesList.add(hiveColIndex);
          partitionValuesList.add(idToConstant.get(field.sourceId()));
        }
      }

      partitionColIndices = partitionColIndicesList.stream().mapToInt(Integer::intValue).toArray();
      partitionValues = partitionValuesList.toArray(new Object[0]);

      ColumnProjectionUtils.setReadColumns(job, readColumnIds);
    }

    try {

      long start = task.start();
      long length = task.length();

      RecordReader<NullWritable, VectorizedRowBatch> recordReader = null;

      switch (format) {
        case ORC:
          recordReader = orcRecordReader(job, reporter, task, inputFile, path, start, length);
          break;
        case PARQUET:
          recordReader = parquetRecordReader(job, reporter, task, path, start, length);
          break;

        default:
          throw new UnsupportedOperationException(
              "Vectorized Hive reading unimplemented for format: " + format);
      }

      return createVectorizedRowBatchIterable(
          recordReader, job, partitionColIndices, partitionValues);

    } catch (IOException ioe) {
      throw new RuntimeException("Error creating vectorized record reader for " + inputFile, ioe);
    }
  }

  /**
   * 创建 ORC 向量化记录读取器。
   *
   * <p>逻辑：必须将 OrcTail 元数据传入 OrcSplit，否则向量化 reader 会按 start+length
   * 推断 ORC 文件结尾，导致读取 tail 失败。
   *
   * @param job 任务配置
   * @param reporter 进度上报器
   * @param task 文件扫描任务
   * @param inputFile Iceberg 输入文件
   * @param path HDFS 路径
   * @param start 起始偏移
   * @param length 读取长度
   * @return Hive ORC 向量化 RecordReader
   * @throws IOException 当读取 ORC 元数据失败时抛出
   */
  private static RecordReader<NullWritable, VectorizedRowBatch> orcRecordReader(
      JobConf job,
      Reporter reporter,
      FileScanTask task,
      InputFile inputFile,
      Path path,
      long start,
      long length)
      throws IOException {
    // Metadata information has to be passed along in the OrcSplit. Without specifying this, the
    // vectorized
    // reader will assume that the ORC file ends at the task's start + length, and might fail
    // reading the tail..
    OrcTail orcTail = VectorizedReadUtils.getOrcTail(inputFile, job);

    InputSplit split =
        new OrcSplit(
            path,
            null,
            start,
            length,
            (String[]) null,
            orcTail,
            false,
            false,
            Lists.newArrayList(),
            0,
            task.length(),
            path.getParent());
    return new VectorizedOrcInputFormat().getRecordReader(split, job, reporter);
  }

  /**
   * 创建 Parquet 向量化记录读取器。
   *
   * <p>逻辑：读取 Parquet 文件 footer 取得文件 schema，结合 Iceberg 期望 schema 做列剪枝；
   * 通过 {@link ParquetSchemaFieldNameVisitor} 生成与文件字段名匹配的列名列表，
   * 写入 JobConf 的 IOConstants.COLUMNS 供 Hive 读取器使用。
   *
   * @param job 任务配置
   * @param reporter 进度上报器
   * @param task 文件扫描任务
   * @param path HDFS 路径
   * @param start 起始偏移
   * @param length 读取长度
   * @return Hive Parquet 向量化 RecordReader
   * @throws IOException 当读取 Parquet footer 失败时抛出
   */
  private static RecordReader<NullWritable, VectorizedRowBatch> parquetRecordReader(
      JobConf job, Reporter reporter, FileScanTask task, Path path, long start, long length)
      throws IOException {
    InputSplit split = new FileSplit(path, start, length, job);
    VectorizedParquetInputFormat inputFormat = new VectorizedParquetInputFormat();

    MessageType fileSchema = ParquetFileReader.readFooter(job, path).getFileMetaData().getSchema();
    MessageType typeWithIds = null;
    Schema expectedSchema = task.spec().schema();

    if (ParquetSchemaUtil.hasIds(fileSchema)) {
      typeWithIds = ParquetSchemaUtil.pruneColumns(fileSchema, expectedSchema);
    } else {
      typeWithIds =
          ParquetSchemaUtil.pruneColumnsFallback(
              ParquetSchemaUtil.addFallbackIds(fileSchema), expectedSchema);
    }

    ParquetSchemaFieldNameVisitor psv = new ParquetSchemaFieldNameVisitor(fileSchema);
    TypeWithSchemaVisitor.visit(expectedSchema.asStruct(), typeWithIds, psv);
    job.set(IOConstants.COLUMNS, psv.retrieveColumnNameList());

    return inputFormat.getRecordReader(split, job, reporter);
  }

  /**
   * 把 Hive RecordReader 包装为 CloseableIterable，并在迭代过程中应用分区列常量化。
   *
   * @param hiveRecordReader 底层 Hive 向量化 RecordReader
   * @param job 任务配置
   * @param partitionColIndices 分区列在向量批中的下标数组
   * @param partitionValues 分区常量值数组，与下标一一对应
   * @param <D> 返回元素类型
   * @return 可关闭的向量批迭代器
   */
  private static <D> CloseableIterable<D> createVectorizedRowBatchIterable(
      RecordReader<NullWritable, VectorizedRowBatch> hiveRecordReader,
      JobConf job,
      int[] partitionColIndices,
      Object[] partitionValues) {

    VectorizedRowBatchIterator iterator =
        new VectorizedRowBatchIterator(hiveRecordReader, job, partitionColIndices, partitionValues);

    return new CloseableIterable<D>() {

      @Override
      public CloseableIterator iterator() {
        return iterator;
      }

      @Override
      public void close() throws IOException {
        iterator.close();
      }
    };
  }
}
