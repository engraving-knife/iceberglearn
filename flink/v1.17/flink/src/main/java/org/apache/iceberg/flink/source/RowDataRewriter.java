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

import static org.apache.iceberg.TableProperties.DEFAULT_NAME_MAPPING;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SerializableTable;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.sink.RowDataTaskWriterFactory;
import org.apache.iceberg.flink.sink.TaskWriterFactory;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.TaskWriter;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：RowData 重写器，用于将读取的 RowData 重新写入 Iceberg 数据文件。
 *
 * <p>所属模块：iceberg-flink（source 子包），用于数据重写/Compaction 场景。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取 Iceberg 表的 CombinedScanTask，将 RowData 重新写入新的数据文件。
 *   <li>支持分区表和非分区表的重写。
 *   <li>收集重写结果（WriteResult）。
 * </ul>
 *
 * <p>设计意图：用于 Iceberg 表的维护操作（如 Compaction、Rewrite Data Files）， 将读取的数据按新的分区/文件大小策略重新写入。
 *
 * <p>上下游关系：被 Flink 的 rewrite data files 操作调用； 内部使用 {@link RowDataFileScanTaskReader} 读取和 {@link
 * TaskWriterFactory} 写入。
 */
public class RowDataRewriter {

  private static final Logger LOG = LoggerFactory.getLogger(RowDataRewriter.class);

  private final Schema schema;
  private final String nameMapping;
  private final FileIO io;
  private final boolean caseSensitive;
  private final EncryptionManager encryptionManager;
  private final TaskWriterFactory<RowData> taskWriterFactory;
  private final String tableName;

  public RowDataRewriter(
      Table table, boolean caseSensitive, FileIO io, EncryptionManager encryptionManager) {
    this.schema = table.schema();
    this.caseSensitive = caseSensitive;
    this.io = io;
    this.encryptionManager = encryptionManager;
    this.nameMapping =
        PropertyUtil.propertyAsString(table.properties(), DEFAULT_NAME_MAPPING, null);
    this.tableName = table.name();

    String formatString =
        PropertyUtil.propertyAsString(
            table.properties(),
            TableProperties.DEFAULT_FILE_FORMAT,
            TableProperties.DEFAULT_FILE_FORMAT_DEFAULT);
    FileFormat format = FileFormat.fromString(formatString);
    RowType flinkSchema = FlinkSchemaUtil.convert(table.schema());
    this.taskWriterFactory =
        new RowDataTaskWriterFactory(
            SerializableTable.copyOf(table),
            flinkSchema,
            Long.MAX_VALUE,
            format,
            table.properties(),
            null,
            false);
  }

  public List<DataFile> rewriteDataForTasks(
      DataStream<CombinedScanTask> dataStream, int parallelism) throws Exception {
    RewriteMap map =
        new RewriteMap(
            schema, nameMapping, io, caseSensitive, encryptionManager, taskWriterFactory);
    DataStream<List<DataFile>> ds = dataStream.map(map).setParallelism(parallelism);
    return Lists.newArrayList(ds.executeAndCollect("Rewrite table :" + tableName)).stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  public static class RewriteMap extends RichMapFunction<CombinedScanTask, List<DataFile>> {

    private TaskWriter<RowData> writer;
    private int subTaskId;
    private int attemptId;

    private final Schema schema;
    private final String nameMapping;
    private final FileIO io;
    private final boolean caseSensitive;
    private final EncryptionManager encryptionManager;
    private final TaskWriterFactory<RowData> taskWriterFactory;
    private final RowDataFileScanTaskReader rowDataReader;

    public RewriteMap(
        Schema schema,
        String nameMapping,
        FileIO io,
        boolean caseSensitive,
        EncryptionManager encryptionManager,
        TaskWriterFactory<RowData> taskWriterFactory) {
      this.schema = schema;
      this.nameMapping = nameMapping;
      this.io = io;
      this.caseSensitive = caseSensitive;
      this.encryptionManager = encryptionManager;
      this.taskWriterFactory = taskWriterFactory;
      this.rowDataReader =
          new RowDataFileScanTaskReader(
              schema, schema, nameMapping, caseSensitive, Collections.emptyList());
    }

    @Override
    public void open(Configuration parameters) {
      this.subTaskId = getRuntimeContext().getIndexOfThisSubtask();
      this.attemptId = getRuntimeContext().getAttemptNumber();
      // Initialize the task writer factory.
      this.taskWriterFactory.initialize(subTaskId, attemptId);
    }

    @Override
    public List<DataFile> map(CombinedScanTask task) throws Exception {
      // Initialize the task writer.
      this.writer = taskWriterFactory.create();
      try (DataIterator<RowData> iterator =
          new DataIterator<>(rowDataReader, task, io, encryptionManager)) {
        while (iterator.hasNext()) {
          RowData rowData = iterator.next();
          writer.write(rowData);
        }
        return Lists.newArrayList(writer.dataFiles());
      } catch (Throwable originalThrowable) {
        try {
          LOG.error("Aborting commit for  (subTaskId {}, attemptId {})", subTaskId, attemptId);
          writer.abort();
          LOG.error("Aborted commit for  (subTaskId {}, attemptId {})", subTaskId, attemptId);
        } catch (Throwable inner) {
          if (originalThrowable != inner) {
            originalThrowable.addSuppressed(inner);
            LOG.warn("Suppressing exception in catch: {}", inner.getMessage(), inner);
          }
        }

        if (originalThrowable instanceof Exception) {
          throw originalThrowable;
        } else {
          throw new RuntimeException(originalThrowable);
        }
      }
    }
  }
}
