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
package org.apache.iceberg.data;

import java.io.Serializable;
import java.util.Map;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.data.avro.DataReader;
import org.apache.iceberg.data.orc.GenericOrcReader;
import org.apache.iceberg.data.parquet.GenericParquetReaders;
import org.apache.iceberg.expressions.Evaluator;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.PartitionUtil;

/**
 * 通用 Record 读取器：把 Iceberg 表扫描任务转换为 {@link Record} 流。
 *
 * <p>所属模块：iceberg-data（向 JVM 应用提供基于 {@link Record} 等通用模型的 Iceberg 表读写支持； 本类是读路径上“通用
 * Record”场景的核心读取器）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有表 IO、表 Schema、投影 Schema、大小写敏感、是否复用容器等读取上下文。
 *   <li>把 {@link CombinedScanTask} / {@link FileScanTask} 打开为 {@link Record} 流： 按 task 的文件格式选择
 *       Avro/Parquet/ORC 读取入口，并应用投影与分区常量下推。
 *   <li>为每个文件扫描任务构造 {@link GenericDeleteFilter}，把位置/等值删除应用到记录流。
 *   <li>在删除过滤之后应用 residual（残留）表达式做行级过滤。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link Serializable} 便于在分布式引擎中序列化分发。
 *   <li>ORC 读取时剔除分区常量列与元数据列（{@code selectNot}），避免 ORC 重复读取常量； Avro/Parquet 则由各自 Builder 处理。
 *   <li>residual 过滤委托给 {@link Evaluator}，分区裁剪后的剩余条件在此处行级求值。
 * </ul>
 *
 * <p>上下游关系：被 {@link TableScanIterable} 持有并调用；依赖 {@link FileIO}、各格式读取 Builder、 {@link
 * GenericDeleteFilter}、{@link InternalRecordWrapper}、{@link Evaluator}。
 */
class GenericReader implements Serializable {
  private final FileIO io;
  private final Schema tableSchema;
  private final Schema projection;
  private final boolean caseSensitive;
  private final boolean reuseContainers;

  /**
   * 从表扫描构造读取器。
   *
   * @param scan 表扫描（提供 io、表 schema、投影、大小写配置）
   * @param reuseContainers 是否复用记录容器以降低 GC 压力
   */
  GenericReader(TableScan scan, boolean reuseContainers) {
    this.io = scan.table().io();
    this.tableSchema = scan.table().schema();
    this.projection = scan.schema();
    this.caseSensitive = scan.isCaseSensitive();
    this.reuseContainers = reuseContainers;
  }

  /**
   * 打开一组组合扫描任务，返回扁平化的 Record 迭代器。
   *
   * <p>逻辑：把多个 CombinedScanTask 拆成 FileScanTask，逐个 open 后 concat，取迭代器。
   *
   * @param tasks 组合扫描任务集合
   * @return Record 迭代器
   */
  CloseableIterator<Record> open(CloseableIterable<CombinedScanTask> tasks) {
    Iterable<FileScanTask> fileTasks =
        Iterables.concat(Iterables.transform(tasks, CombinedScanTask::files));
    return CloseableIterable.concat(Iterables.transform(fileTasks, this::open)).iterator();
  }

  /**
   * 打开单个组合扫描任务，返回 {@link CombinedTaskIterable}。
   *
   * @param task 组合扫描任务
   * @return Record 可迭代对象
   */
  public CloseableIterable<Record> open(CombinedScanTask task) {
    return new CombinedTaskIterable(task);
  }

  /**
   * 打开单个文件扫描任务，返回应用删除与残留过滤后的 Record 流。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>构造 {@link GenericDeleteFilter}，取其 requiredSchema 作为实际读取 Schema。
   *   <li>{@link #openFile} 按格式读取数据文件得到 Record 流。
   *   <li>调用 {@link DeleteFilter#filter} 应用位置/等值删除。
   *   <li>{@link #applyResidual} 应用残留表达式做行级过滤。
   * </ol>
   *
   * @param task 文件扫描任务
   * @return 处理后的 Record 流
   */
  public CloseableIterable<Record> open(FileScanTask task) {
    DeleteFilter<Record> deletes = new GenericDeleteFilter(io, task, tableSchema, projection);
    Schema readSchema = deletes.requiredSchema();

    CloseableIterable<Record> records = openFile(task, readSchema);
    records = deletes.filter(records);
    records = applyResidual(records, readSchema, task.residual());

    return records;
  }

  /**
   * 应用残留（residual）表达式做行级过滤。
   *
   * <p>逻辑：若 residual 非空且非恒真，则用 {@link InternalRecordWrapper} 包装记录、 {@link Evaluator}
   * 求值，过滤掉不满足的行；否则直接返回原流。
   *
   * @param records 记录流
   * @param recordSchema 记录 Schema
   * @param residual 分区裁剪后剩余的过滤表达式
   * @return 过滤后的记录流
   */
  private CloseableIterable<Record> applyResidual(
      CloseableIterable<Record> records, Schema recordSchema, Expression residual) {
    if (residual != null && residual != Expressions.alwaysTrue()) {
      InternalRecordWrapper wrapper = new InternalRecordWrapper(recordSchema.asStruct());
      Evaluator filter = new Evaluator(recordSchema.asStruct(), residual, caseSensitive);
      return CloseableIterable.filter(records, record -> filter.eval(wrapper.wrap(record)));
    }

    return records;
  }

  /**
   * 按文件格式打开数据文件并按投影读取为 Record 流。
   *
   * <p>逻辑：用 {@link FileIO} 取 InputFile，{@link PartitionUtil#constantsMap} 取分区常量
   * （用于填充分区列值，避免从文件重复读取）；按格式走 Avro/Parquet/ORC 读取 Builder：
   *
   * <ul>
   *   <li>Avro：用 {@link DataReader#create}，按 split 范围读取，可选复用容器。
   *   <li>Parquet：用 {@code GenericParquetReaders.buildReader}，下推 residual 过滤与大小写配置。
   *   <li>ORC：先剔除分区常量列与元数据列（{@code selectNot}），再读取；ORC 自动复用容器。
   * </ul>
   *
   * @param task 文件扫描任务
   * @param fileProjection 实际读取投影（已含删除判定所需字段）
   * @return Record 流
   */
  private CloseableIterable<Record> openFile(FileScanTask task, Schema fileProjection) {
    InputFile input = io.newInputFile(task.file().path().toString());
    Map<Integer, ?> partition =
        PartitionUtil.constantsMap(task, IdentityPartitionConverters::convertConstant);

    switch (task.file().format()) {
      case AVRO:
        Avro.ReadBuilder avro =
            Avro.read(input)
                .project(fileProjection)
                .createReaderFunc(
                    avroSchema -> DataReader.create(fileProjection, avroSchema, partition))
                .split(task.start(), task.length());

        if (reuseContainers) {
          avro.reuseContainers();
        }

        return avro.build();

      case PARQUET:
        Parquet.ReadBuilder parquet =
            Parquet.read(input)
                .project(fileProjection)
                .createReaderFunc(
                    fileSchema ->
                        GenericParquetReaders.buildReader(fileProjection, fileSchema, partition))
                .split(task.start(), task.length())
                .caseSensitive(caseSensitive)
                .filter(task.residual());

        if (reuseContainers) {
          parquet.reuseContainers();
        }

        return parquet.build();

      case ORC:
        Schema projectionWithoutConstantAndMetadataFields =
            TypeUtil.selectNot(
                fileProjection, Sets.union(partition.keySet(), MetadataColumns.metadataFieldIds()));
        ORC.ReadBuilder orc =
            ORC.read(input)
                .project(projectionWithoutConstantAndMetadataFields)
                .createReaderFunc(
                    fileSchema ->
                        GenericOrcReader.buildReader(fileProjection, fileSchema, partition))
                .split(task.start(), task.length())
                .caseSensitive(caseSensitive)
                .filter(task.residual());

        return orc.build();

      default:
        throw new UnsupportedOperationException(
            String.format(
                "Cannot read %s file: %s", task.file().format().name(), task.file().path()));
    }
  }

  /**
   * 组合任务可迭代对象：把一个 {@link CombinedScanTask} 的多个文件任务串联为单个 Record 流， 并通过 {@link CloseableGroup}
   * 统一管理底层资源的关闭。
   */
  private class CombinedTaskIterable extends CloseableGroup implements CloseableIterable<Record> {
    private final CombinedScanTask task;

    /**
     * 构造组合任务可迭代对象。
     *
     * @param task 组合扫描任务
     */
    private CombinedTaskIterable(CombinedScanTask task) {
      this.task = task;
    }

    @Override
    public CloseableIterator<Record> iterator() {
      CloseableIterator<Record> iter =
          CloseableIterable.concat(Iterables.transform(task.files(), GenericReader.this::open))
              .iterator();
      addCloseable(iter);
      return iter;
    }
  }
}
