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
package org.apache.iceberg.mr.mapreduce;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataTableScan;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.SerializableTable;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.data.DeleteFilter;
import org.apache.iceberg.data.GenericDeleteFilter;
import org.apache.iceberg.data.IdentityPartitionConverters;
import org.apache.iceberg.data.InternalRecordWrapper;
import org.apache.iceberg.data.avro.DataReader;
import org.apache.iceberg.data.orc.GenericOrcReader;
import org.apache.iceberg.data.parquet.GenericParquetReaders;
import org.apache.iceberg.encryption.EncryptedFiles;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.expressions.Evaluator;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.hive.HiveVersion;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.mr.Catalogs;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.mr.hive.HiveIcebergStorageHandler;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.PartitionUtil;
import org.apache.iceberg.util.SerializationUtil;

/**
 * 文件级说明：Iceberg 的 MR v2（mapreduce）InputFormat，是 Iceberg 读取的核心实现。
 *
 * <p>所属模块：iceberg-mr（mapreduce 子包；为 MR v2 API 用户提供 Iceberg 读取入口， 也是 {@link
 * org.apache.iceberg.mr.mapred.MapredIcebergInputFormat} 与 {@link
 * org.apache.iceberg.mr.hive.HiveIcebergInputFormat} 的底层实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #getSplits(JobContext)}：根据配置加载表、构建 TableScan（含时间旅行、投影、列裁剪、 过滤下推、切分大小），规划出 {@link
 *       CombinedScanTask} 列表并包装为 {@link IcebergSplit}。
 *   <li>{@link #createRecordReader(InputSplit, TaskAttemptContext)}：返回 {@link
 *       IcebergRecordReader}，按文件格式（Avro/ORC/Parquet）打开数据文件， 应用相等性删除（equality delete）过滤与残留谓词过滤。
 *   <li>支持 GENERIC / HIVE / PIG 三种内存数据模型；HIVE 模式下走 Hive 向量化 reader。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把表对象通过 {@link SerializableTable#copyOf} 转为可序列化形式写入 split，避免 executor 重新访问 catalog；并可选跳过
 *       FileIO 配置序列化以减小 split 体积。
 *   <li>HIVE/PIG 模式不做残留过滤（仅 GENERIC 支持），因此在 split 规划阶段对 HIVE/PIG 调用 {@link
 *       #checkResiduals(CombinedScanTask)} 提前校验，发现未满足的残留谓词直接报错。
 *   <li>Hive 向量化 reader 类通过 {@link DynMethods} 反射加载，避免对 Hive 3 专用类的编译期依赖。
 *   <li>多个 FileScanTask 在同一 RecordReader 中顺序读取，task 间无缝切换。
 * </ul>
 *
 * <p>上下游关系：上游被 MR v2 引擎、{@link MapredIcebergInputFormat}（v1 适配）、 {@link
 * HiveIcebergInputFormat}（Hive 适配）调用；下游依赖 iceberg-core 的 {@link TableScan}、{@link Parquet}/{@link
 * ORC}/{@link Avro} 读取器、{@link DeleteFilter} 等。
 *
 * @param <T> 内存数据模型类型（PIG Tuple / Hive row / Iceberg Record）
 */
public class IcebergInputFormat<T> extends InputFormat<Void, T> {
  /**
   * 配置 Job 使用本 InputFormat，并返回 {@link InputFormatConfig.ConfigBuilder} 以便进一步配置。
   *
   * @param job MR v2 Job
   * @return 配置构造器
   */
  public static InputFormatConfig.ConfigBuilder configure(Job job) {
    job.setInputFormatClass(IcebergInputFormat.class);
    return new InputFormatConfig.ConfigBuilder(job.getConfiguration());
  }

  /**
   * 计算输入切分。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>优先从 StorageHandler 序列化的配置加载 Table，否则用 {@link Catalogs#loadTable} 加载。
   *   <li>构建 {@link TableScan}：设置 caseSensitive、snapshotId（时间旅行）、asOfTime、 splitSize、读 schema
   *       投影、列选择、过滤表达式。
   *   <li>调用 {@link TableScan#planTasks()} 规划 CombinedScanTask，包装为 {@link IcebergSplit}。 HIVE/PIG
   *       模式下提前 {@link #checkResiduals} 校验残留谓词。
   *   <li>对 DataTableScan，按需跳过 FileIO 配置序列化以减小 split 体积。
   * </ol>
   *
   * @param context 作业上下文
   * @return 切分列表
   */
  @Override
  public List<InputSplit> getSplits(JobContext context) {
    Configuration conf = context.getConfiguration();
    Table table =
        Optional.ofNullable(
                HiveIcebergStorageHandler.table(conf, conf.get(InputFormatConfig.TABLE_IDENTIFIER)))
            .orElseGet(() -> Catalogs.loadTable(conf));

    TableScan scan =
        table
            .newScan()
            .caseSensitive(
                conf.getBoolean(
                    InputFormatConfig.CASE_SENSITIVE, InputFormatConfig.CASE_SENSITIVE_DEFAULT));
    long snapshotId = conf.getLong(InputFormatConfig.SNAPSHOT_ID, -1);
    if (snapshotId != -1) {
      scan = scan.useSnapshot(snapshotId);
    }
    long asOfTime = conf.getLong(InputFormatConfig.AS_OF_TIMESTAMP, -1);
    if (asOfTime != -1) {
      scan = scan.asOfTime(asOfTime);
    }
    long splitSize = conf.getLong(InputFormatConfig.SPLIT_SIZE, 0);
    if (splitSize > 0) {
      scan = scan.option(TableProperties.SPLIT_SIZE, String.valueOf(splitSize));
    }
    String schemaStr = conf.get(InputFormatConfig.READ_SCHEMA);
    if (schemaStr != null) {
      scan.project(SchemaParser.fromJson(schemaStr));
    }
    String[] selectedColumns = conf.getStrings(InputFormatConfig.SELECTED_COLUMNS);
    if (selectedColumns != null) {
      scan.select(selectedColumns);
    }

    // TODO add a filter parser to get rid of Serialization
    Expression filter =
        SerializationUtil.deserializeFromBase64(conf.get(InputFormatConfig.FILTER_EXPRESSION));
    if (filter != null) {
      scan = scan.filter(filter);
    }

    List<InputSplit> splits = Lists.newArrayList();
    boolean applyResidual = !conf.getBoolean(InputFormatConfig.SKIP_RESIDUAL_FILTERING, false);
    InputFormatConfig.InMemoryDataModel model =
        conf.getEnum(
            InputFormatConfig.IN_MEMORY_DATA_MODEL, InputFormatConfig.InMemoryDataModel.GENERIC);
    try (CloseableIterable<CombinedScanTask> tasksIterable = scan.planTasks()) {
      Table serializableTable = SerializableTable.copyOf(table);
      tasksIterable.forEach(
          task -> {
            if (applyResidual
                && (model == InputFormatConfig.InMemoryDataModel.HIVE
                    || model == InputFormatConfig.InMemoryDataModel.PIG)) {
              // TODO: We do not support residual evaluation for HIVE and PIG in memory data model
              // yet
              checkResiduals(task);
            }
            splits.add(new IcebergSplit(serializableTable, conf, task));
          });
    } catch (IOException e) {
      throw new UncheckedIOException(String.format("Failed to close table scan: %s", scan), e);
    }

    // if enabled, do not serialize FileIO hadoop config to decrease split size
    // However, do not skip serialization for metatable queries, because some metadata tasks cache
    // the IO object and we
    // wouldn't be able to inject the config into these tasks on the deserializer-side, unlike for
    // standard queries
    if (scan instanceof DataTableScan) {
      HiveIcebergStorageHandler.checkAndSkipIoConfigSerialization(conf, table);
    }

    return splits;
  }

  /**
   * 校验任务的残留谓词是否完全满足。
   *
   * <p>设计要点：HIVE/PIG 模式不支持残留过滤，因此若残留谓词非 alwaysTrue 则直接抛 UnsupportedOperationException，避免读到不该返回的行。
   *
   * @param task 组合扫描任务
   */
  private static void checkResiduals(CombinedScanTask task) {
    task.files()
        .forEach(
            fileScanTask -> {
              Expression residual = fileScanTask.residual();
              if (residual != null && !residual.equals(Expressions.alwaysTrue())) {
                throw new UnsupportedOperationException(
                    String.format(
                        "Filter expression %s is not completely satisfied. Additional rows "
                            + "can be returned not satisfied by the filter expression",
                        residual));
              }
            });
  }

  /** 创建 RecordReader，返回新的 {@link IcebergRecordReader} 实例。 */
  @Override
  public RecordReader<Void, T> createRecordReader(InputSplit split, TaskAttemptContext context) {
    return new IcebergRecordReader<>();
  }

  /** Iceberg RecordReader 实现：顺序读取 split 内多个 FileScanTask，按文件格式打开数据。 */
  private static final class IcebergRecordReader<T> extends RecordReader<Void, T> {

    private static final String HIVE_VECTORIZED_READER_CLASS =
        "org.apache.iceberg.mr.hive.vector.HiveVectorizedReader";
    private static final DynMethods.StaticMethod HIVE_VECTORIZED_READER_BUILDER;

    static {
      if (HiveVersion.min(HiveVersion.HIVE_3)) {
        HIVE_VECTORIZED_READER_BUILDER =
            DynMethods.builder("reader")
                .impl(
                    HIVE_VECTORIZED_READER_CLASS,
                    InputFile.class,
                    FileScanTask.class,
                    Map.class,
                    TaskAttemptContext.class)
                .buildStatic();
      } else {
        HIVE_VECTORIZED_READER_BUILDER = null;
      }
    }

    private TaskAttemptContext context;
    private Schema tableSchema;
    private Schema expectedSchema;
    private String nameMapping;
    private boolean reuseContainers;
    private boolean caseSensitive;
    private InputFormatConfig.InMemoryDataModel inMemoryDataModel;
    private Iterator<FileScanTask> tasks;
    private T current;
    private CloseableIterator<T> currentIterator;
    private FileIO io;
    private EncryptionManager encryptionManager;

    /**
     * 初始化 RecordReader。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>从 split 取出 CombinedScanTask 与 Table；按需注入 FileIO 配置。
     *   <li>读取表 schema、name mapping、caseSensitive、expectedSchema（投影后）、 reuseContainers、内存数据模型。
     *   <li>打开第一个 FileScanTask 的迭代器。
     * </ol>
     *
     * @param split 输入切分
     * @param newContext 任务上下文
     */
    @Override
    public void initialize(InputSplit split, TaskAttemptContext newContext) {
      Configuration conf = newContext.getConfiguration();
      // For now IcebergInputFormat does its own split planning and does not accept FileSplit
      // instances
      CombinedScanTask task = ((IcebergSplit) split).task();
      this.context = newContext;
      Table table = ((IcebergSplit) split).table();
      HiveIcebergStorageHandler.checkAndSetIoConfig(conf, table);
      this.io = table.io();
      this.encryptionManager = table.encryption();
      this.tasks = task.files().iterator();
      this.tableSchema = InputFormatConfig.tableSchema(conf);
      this.nameMapping = table.properties().get(TableProperties.DEFAULT_NAME_MAPPING);
      this.caseSensitive =
          conf.getBoolean(
              InputFormatConfig.CASE_SENSITIVE, InputFormatConfig.CASE_SENSITIVE_DEFAULT);
      this.expectedSchema = readSchema(conf, tableSchema, caseSensitive);
      this.reuseContainers = conf.getBoolean(InputFormatConfig.REUSE_CONTAINERS, false);
      this.inMemoryDataModel =
          conf.getEnum(
              InputFormatConfig.IN_MEMORY_DATA_MODEL, InputFormatConfig.InMemoryDataModel.GENERIC);
      this.currentIterator = open(tasks.next(), expectedSchema).iterator();
    }

    /**
     * 推进到下一条记录。
     *
     * <p>逻辑：当前迭代器有下一条则返回；否则切换到下一个 FileScanTask 的迭代器；任务用尽时关闭并返回 false。
     *
     * @return true 表示有下一条记录
     */
    @Override
    public boolean nextKeyValue() throws IOException {
      while (true) {
        if (currentIterator.hasNext()) {
          current = currentIterator.next();
          return true;
        } else if (tasks.hasNext()) {
          currentIterator.close();
          currentIterator = open(tasks.next(), expectedSchema).iterator();
        } else {
          currentIterator.close();
          return false;
        }
      }
    }

    /** 返回 key，固定为 null。 */
    @Override
    public Void getCurrentKey() {
      return null;
    }

    /** 返回当前记录值。 */
    @Override
    public T getCurrentValue() {
      return current;
    }

    /**
     * 返回读取进度，委托给 context.getProgress()。
     *
     * <p>TODO：可基于已读行数估算更精确的进度。
     */
    @Override
    public float getProgress() {
      // TODO: We could give a more accurate progress based on records read from the file.
      // Context.getProgress does not
      // have enough information to give an accurate progress value. This isn't that easy, since we
      // don't know how much
      // of the input split has been processed and we are pushing filters into Parquet and ORC. But
      // we do know when a
      // file is opened and could count the number of rows returned, so we can estimate. And we
      // could also add a row
      // count to the readers so that we can get an accurate count of rows that have been either
      // returned or filtered
      // out.
      return context.getProgress();
    }

    /** 关闭当前迭代器。 */
    @Override
    public void close() throws IOException {
      currentIterator.close();
    }

    /**
     * 打开单个 FileScanTask 对应的数据文件。
     *
     * <p>逻辑：用 {@link EncryptionManager#decrypt} 解密 InputFile，按文件格式（Avro/ORC/Parquet） 创建对应 iterable。
     *
     * @param currentTask 文件扫描任务
     * @param readSchema 读 schema
     * @return 数据 iterable
     */
    private CloseableIterable<T> openTask(FileScanTask currentTask, Schema readSchema) {
      DataFile file = currentTask.file();
      InputFile inputFile =
          encryptionManager.decrypt(
              EncryptedFiles.encryptedInput(
                  io.newInputFile(file.path().toString()), file.keyMetadata()));

      CloseableIterable<T> iterable;
      switch (file.format()) {
        case AVRO:
          iterable = newAvroIterable(inputFile, currentTask, readSchema);
          break;
        case ORC:
          iterable = newOrcIterable(inputFile, currentTask, readSchema);
          break;
        case PARQUET:
          iterable = newParquetIterable(inputFile, currentTask, readSchema);
          break;
        default:
          throw new UnsupportedOperationException(
              String.format("Cannot read %s file: %s", file.format().name(), file.path()));
      }

      return iterable;
    }

    /**
     * 打开 FileScanTask 并按内存数据模型包装。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>PIG/HIVE：暂不支持 PIG；HIVE 直接 openTask（向量化由各 newXxxIterable 内部处理）。
     *   <li>GENERIC：用 {@link GenericDeleteFilter} 过滤 equality/position delete， 并按删除过滤后的
     *       requiredSchema 读取数据。
     * </ul>
     *
     * @param currentTask 文件扫描任务
     * @param readSchema 读 schema
     * @return 数据 iterable
     */
    @SuppressWarnings("unchecked")
    private CloseableIterable<T> open(FileScanTask currentTask, Schema readSchema) {
      switch (inMemoryDataModel) {
        case PIG:
          // TODO: Support Pig and Hive object models for IcebergInputFormat
          throw new UnsupportedOperationException("Pig and Hive object models are not supported.");
        case HIVE:
          return openTask(currentTask, readSchema);
        case GENERIC:
          DeleteFilter deletes = new GenericDeleteFilter(io, currentTask, tableSchema, readSchema);
          Schema requiredSchema = deletes.requiredSchema();
          return deletes.filter(openTask(currentTask, requiredSchema));
        default:
          throw new UnsupportedOperationException("Unsupported memory model");
      }
    }

    /**
     * 应用残留谓词过滤。
     *
     * <p>逻辑：若未跳过残留过滤且 residual 非 alwaysTrue，则用 {@link Evaluator} 对每条记录求值， 用 {@link
     * InternalRecordWrapper} 包装以适配类型。否则原样返回 iterable。
     *
     * @param iter 数据 iterable
     * @param residual 残留谓词
     * @param readSchema 读 schema
     * @return 过滤后的 iterable
     */
    private CloseableIterable<T> applyResidualFiltering(
        CloseableIterable<T> iter, Expression residual, Schema readSchema) {
      boolean applyResidual =
          !context.getConfiguration().getBoolean(InputFormatConfig.SKIP_RESIDUAL_FILTERING, false);

      if (applyResidual && residual != null && residual != Expressions.alwaysTrue()) {
        // Date and timestamp values are not the correct type for Evaluator.
        // Wrapping to return the expected type.
        InternalRecordWrapper wrapper = new InternalRecordWrapper(readSchema.asStruct());
        Evaluator filter = new Evaluator(readSchema.asStruct(), residual, caseSensitive);
        return CloseableIterable.filter(
            iter, record -> filter.eval(wrapper.wrap((StructLike) record)));
      } else {
        return iter;
      }
    }

    /**
     * 创建 Avro 数据 iterable。
     *
     * <p>逻辑：构建 {@link Avro.ReadBuilder}，按需设置 reuseContainers、nameMapping； GENERIC 模式用 {@link
     * DataReader} 作为 reader func；PIG/HIVE 暂不支持 Avro。 最后应用残留过滤。
     *
     * @param inputFile 输入文件
     * @param task 文件扫描任务
     * @param readSchema 读 schema
     * @return Avro iterable
     */
    private CloseableIterable<T> newAvroIterable(
        InputFile inputFile, FileScanTask task, Schema readSchema) {
      Avro.ReadBuilder avroReadBuilder =
          Avro.read(inputFile).project(readSchema).split(task.start(), task.length());
      if (reuseContainers) {
        avroReadBuilder.reuseContainers();
      }
      if (nameMapping != null) {
        avroReadBuilder.withNameMapping(NameMappingParser.fromJson(nameMapping));
      }

      switch (inMemoryDataModel) {
        case PIG:
        case HIVE:
          // TODO implement value readers for Pig and Hive
          throw new UnsupportedOperationException(
              "Avro support not yet supported for Pig and Hive");
        case GENERIC:
          avroReadBuilder.createReaderFunc(
              (expIcebergSchema, expAvroSchema) ->
                  DataReader.create(
                      expIcebergSchema,
                      expAvroSchema,
                      constantsMap(task, IdentityPartitionConverters::convertConstant)));
      }
      return applyResidualFiltering(avroReadBuilder.build(), task.residual(), readSchema);
    }

    /**
     * 创建 Parquet 数据 iterable。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>PIG：暂不支持。
     *   <li>HIVE：Hive 3+ 通过反射调用向量化 reader；Hive 2 不支持。
     *   <li>GENERIC：用 {@link GenericParquetReaders#buildReader} 构建 reader，
     *       支持过滤下推、reuseContainers、nameMapping。
     * </ul>
     *
     * 最后应用残留过滤。
     *
     * @param inputFile 输入文件
     * @param task 文件扫描任务
     * @param readSchema 读 schema
     * @return Parquet iterable
     */
    private CloseableIterable<T> newParquetIterable(
        InputFile inputFile, FileScanTask task, Schema readSchema) {
      Map<Integer, ?> idToConstant =
          constantsMap(task, IdentityPartitionConverters::convertConstant);
      CloseableIterable<T> parquetIterator = null;

      switch (inMemoryDataModel) {
        case PIG:
          throw new UnsupportedOperationException("Parquet support not yet supported for Pig");
        case HIVE:
          if (HiveVersion.min(HiveVersion.HIVE_3)) {
            parquetIterator =
                HIVE_VECTORIZED_READER_BUILDER.invoke(inputFile, task, idToConstant, context);
          } else {
            throw new UnsupportedOperationException(
                "Vectorized read is unsupported for Hive 2 integration.");
          }
          break;
        case GENERIC:
          Parquet.ReadBuilder parquetReadBuilder =
              Parquet.read(inputFile)
                  .project(readSchema)
                  .filter(task.residual())
                  .caseSensitive(caseSensitive)
                  .split(task.start(), task.length());
          if (reuseContainers) {
            parquetReadBuilder.reuseContainers();
          }
          if (nameMapping != null) {
            parquetReadBuilder.withNameMapping(NameMappingParser.fromJson(nameMapping));
          }
          parquetReadBuilder.createReaderFunc(
              fileSchema ->
                  GenericParquetReaders.buildReader(
                      readSchema,
                      fileSchema,
                      constantsMap(task, IdentityPartitionConverters::convertConstant)));
          parquetIterator = parquetReadBuilder.build();
      }
      return applyResidualFiltering(parquetIterator, task.residual(), readSchema);
    }

    /**
     * 创建 ORC 数据 iterable。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>PIG：暂不支持。
     *   <li>HIVE：Hive 3+ 通过反射调用向量化 reader；Hive 2 不支持。
     *   <li>GENERIC：用 {@link GenericOrcReader#buildReader} 构建 reader，
     *       投影时排除常量与元数据字段，支持过滤下推、nameMapping。
     * </ul>
     *
     * ORC 暂不支持 reuseContainers。最后应用残留过滤。
     *
     * @param inputFile 输入文件
     * @param task 文件扫描任务
     * @param readSchema 读 schema
     * @return ORC iterable
     */
    private CloseableIterable<T> newOrcIterable(
        InputFile inputFile, FileScanTask task, Schema readSchema) {
      Map<Integer, ?> idToConstant =
          constantsMap(task, IdentityPartitionConverters::convertConstant);
      Schema readSchemaWithoutConstantAndMetadataFields =
          TypeUtil.selectNot(
              readSchema, Sets.union(idToConstant.keySet(), MetadataColumns.metadataFieldIds()));

      CloseableIterable<T> orcIterator = null;
      // ORC does not support reuse containers yet
      switch (inMemoryDataModel) {
        case PIG:
          // TODO: implement value readers for Pig
          throw new UnsupportedOperationException("ORC support not yet supported for Pig");
        case HIVE:
          if (HiveVersion.min(HiveVersion.HIVE_3)) {
            orcIterator =
                HIVE_VECTORIZED_READER_BUILDER.invoke(inputFile, task, idToConstant, context);
          } else {
            throw new UnsupportedOperationException(
                "Vectorized read is unsupported for Hive 2 integration.");
          }
          break;
        case GENERIC:
          ORC.ReadBuilder orcReadBuilder =
              ORC.read(inputFile)
                  .project(readSchemaWithoutConstantAndMetadataFields)
                  .filter(task.residual())
                  .caseSensitive(caseSensitive)
                  .split(task.start(), task.length());
          orcReadBuilder.createReaderFunc(
              fileSchema -> GenericOrcReader.buildReader(readSchema, fileSchema, idToConstant));

          if (nameMapping != null) {
            orcReadBuilder.withNameMapping(NameMappingParser.fromJson(nameMapping));
          }
          orcIterator = orcReadBuilder.build();
      }

      return applyResidualFiltering(orcIterator, task.residual(), readSchema);
    }

    /**
     * 计算文件任务的常量列映射（identity 分区列）。
     *
     * <p>逻辑：若 expectedSchema 投影了 identity 分区列，则用 {@link PartitionUtil#constantsMap} 生成 字段ID -> 常量值
     * 的映射；否则返回空 map。
     *
     * @param task 文件扫描任务
     * @param converter 类型转换函数
     * @return 常量列映射
     */
    private Map<Integer, ?> constantsMap(
        FileScanTask task, BiFunction<Type, Object, Object> converter) {
      PartitionSpec spec = task.spec();
      Set<Integer> idColumns = spec.identitySourceIds();
      Schema partitionSchema = TypeUtil.select(expectedSchema, idColumns);
      boolean projectsIdentityPartitionColumns = !partitionSchema.columns().isEmpty();
      if (projectsIdentityPartitionColumns) {
        return PartitionUtil.constantsMap(task, converter);
      } else {
        return Collections.emptyMap();
      }
    }

    /**
     * 解析实际读取 schema。
     *
     * <p>逻辑：优先使用配置中的 read schema；否则按 selectedColumns 做列裁剪（区分大小写敏感）； 都未设置则返回全表 schema。
     *
     * @param conf 配置
     * @param tableSchema 表 schema
     * @param caseSensitive 是否大小写敏感
     * @return 实际读取 schema
     */
    private static Schema readSchema(
        Configuration conf, Schema tableSchema, boolean caseSensitive) {
      Schema readSchema = InputFormatConfig.readSchema(conf);

      if (readSchema != null) {
        return readSchema;
      }

      String[] selectedColumns = InputFormatConfig.selectedColumns(conf);
      if (selectedColumns == null) {
        return tableSchema;
      }

      return caseSensitive
          ? tableSchema.select(selectedColumns)
          : tableSchema.caseInsensitiveSelect(selectedColumns);
    }
  }
}
