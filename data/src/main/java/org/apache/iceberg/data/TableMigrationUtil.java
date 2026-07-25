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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.orc.OrcMetrics;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.MoreExecutors;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.iceberg.util.Tasks;

/**
 * 表迁移工具：把已存在的（非 Iceberg）分区目录中的文件列举为 Iceberg {@link DataFile}， 用于将外部表数据导入 Iceberg 表。
 *
 * <p>所属模块：iceberg-data（向 JVM 应用提供基于 {@link Record} 等通用模型的 Iceberg 表读写支持；
 * 本类专注于“存量数据迁移”场景的文件列举与指标读取）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按分区目录列举数据文件（过滤隐藏文件），读取每个文件的指标（行数、边界、Null 计数等）。
 *   <li>支持 Avro/Parquet/ORC 三种格式：Parquet 与 ORC 从 footer 读取完整指标， Avro 仅能取行数（其余为 null）。
 *   <li>支持按指定线程数并行读取文件指标，加速大分区的迁移列举。
 *   <li>把文件状态与指标组装为 {@link DataFile}（含分区值、路径、大小、格式、指标）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>迁移场景下文件由外部系统写入，只能从 footer 反推指标；Iceberg 写入器才能产出的 指标（如 NaN 计数）此处无法填充。
 *   <li>依赖 Hadoop {@link FileSystem} 列目录，复用 {@link HadoopInputFile} 读取文件。
 *   <li>用 {@link Tasks} 工具做并行与失败重试，线程池在方法结束时关闭。
 * </ul>
 *
 * <p>上下游关系：被表迁移/导入流程（如 {@code SparkTableUtil} 或各引擎的 migrate 操作）调用； 依赖 iceberg-core 的 {@link
 * DataFiles}、{@link Metrics}、avro/parquet/orc 指标工具与 Hadoop FS。
 */
public class TableMigrationUtil {
  private static final PathFilter HIDDEN_PATH_FILTER =
      p -> !p.getName().startsWith("_") && !p.getName().startsWith(".");

  private TableMigrationUtil() {}

  /**
   * 列举分区目录下的数据文件并读取指标，单线程执行（委托给 {@link #listPartition(Map, String, String, PartitionSpec,
   * Configuration, MetricsConfig, NameMapping, int)}， parallelism=1）。
   *
   * <p>Parquet/ORC 从 footer 读取完整指标；Avro 仅取行数，其余指标为 null。 Iceberg 写入器专属指标（如 NaN 计数）无法从 footer 填充。
   *
   * @param partition 列名到分区值的映射
   * @param uri 分区目录 URI
   * @param format 分区格式（avro/parquet/orc）
   * @param spec 分区 spec
   * @param conf Hadoop 配置
   * @param metricsConfig 指标配置
   * @param mapping 字段名映射（用于 schema 演进场景）
   * @return 数据文件列表
   */
  public static List<DataFile> listPartition(
      Map<String, String> partition,
      String uri,
      String format,
      PartitionSpec spec,
      Configuration conf,
      MetricsConfig metricsConfig,
      NameMapping mapping) {
    return listPartition(partition, uri, format, spec, conf, metricsConfig, mapping, 1);
  }

  /**
   * 列举分区目录下的数据文件并读取指标，按指定线程数并行读取文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从 spec 字段名与 partition 映射构造分区值列表。
   *   <li>用 Hadoop {@link FileSystem} 列出分区目录下非隐藏文件。
   *   <li>按格式选择指标读取方式（Avro 行数 / Parquet footer / ORC footer）， 通过 {@link Tasks}
   *       并行执行（parallelism&gt;1 时建线程池）。
   *   <li>把每个文件状态与指标组装为 {@link DataFile} 并返回。
   * </ol>
   *
   * <p>Parquet/ORC 从 footer 读取完整指标；Avro 仅取行数。Iceberg 写入器专属指标 （如 NaN 计数）无法从 footer 填充。IO 异常包装为
   * RuntimeException，线程池在 finally 关闭。
   *
   * @param partition 列名到分区值的映射
   * @param partitionUri 分区目录 URI
   * @param format 分区格式（avro/parquet/orc）
   * @param spec 分区 spec
   * @param conf Hadoop 配置
   * @param metricsSpec 指标配置
   * @param mapping 字段名映射
   * @param parallelism 并行读取的线程数
   * @return 数据文件列表
   */
  public static List<DataFile> listPartition(
      Map<String, String> partition,
      String partitionUri,
      String format,
      PartitionSpec spec,
      Configuration conf,
      MetricsConfig metricsSpec,
      NameMapping mapping,
      int parallelism) {
    ExecutorService service = null;
    try {
      List<String> partitionValues =
          spec.fields().stream()
              .map(PartitionField::name)
              .map(partition::get)
              .collect(Collectors.toList());

      Path partitionDir = new Path(partitionUri);
      FileSystem fs = partitionDir.getFileSystem(conf);
      List<FileStatus> fileStatus =
          Arrays.stream(fs.listStatus(partitionDir, HIDDEN_PATH_FILTER))
              .filter(FileStatus::isFile)
              .collect(Collectors.toList());
      DataFile[] datafiles = new DataFile[fileStatus.size()];
      Tasks.Builder<Integer> task =
          Tasks.range(fileStatus.size()).stopOnFailure().throwFailureWhenFinished();

      if (parallelism > 1) {
        service = migrationService(parallelism);
        task.executeWith(service);
      }

      if (format.contains("avro")) {
        task.run(
            index -> {
              Metrics metrics = getAvroMetrics(fileStatus.get(index).getPath(), conf);
              datafiles[index] =
                  buildDataFile(fileStatus.get(index), partitionValues, spec, metrics, "avro");
            });
      } else if (format.contains("parquet")) {
        task.run(
            index -> {
              Metrics metrics =
                  getParquetMetrics(fileStatus.get(index).getPath(), conf, metricsSpec, mapping);
              datafiles[index] =
                  buildDataFile(fileStatus.get(index), partitionValues, spec, metrics, "parquet");
            });
      } else if (format.contains("orc")) {
        task.run(
            index -> {
              Metrics metrics =
                  getOrcMetrics(fileStatus.get(index).getPath(), conf, metricsSpec, mapping);
              datafiles[index] =
                  buildDataFile(fileStatus.get(index), partitionValues, spec, metrics, "orc");
            });
      } else {
        throw new UnsupportedOperationException("Unknown partition format: " + format);
      }
      return Arrays.asList(datafiles);
    } catch (IOException e) {
      throw new RuntimeException("Unable to list files in partition: " + partitionUri, e);
    } finally {
      if (service != null) {
        service.shutdown();
      }
    }
  }

  /**
   * 读取 Avro 文件的指标：仅能取行数，其余指标（边界/Null/NaN 计数等）为 null。
   *
   * @param path 文件路径
   * @param conf Hadoop 配置
   * @return 仅含行数的 Metrics
   */
  private static Metrics getAvroMetrics(Path path, Configuration conf) {
    try {
      InputFile file = HadoopInputFile.fromPath(path, conf);
      long rowCount = Avro.rowCount(file);
      return new Metrics(rowCount, null, null, null, null);
    } catch (UncheckedIOException e) {
      throw new RuntimeException("Unable to read Avro file: " + path, e);
    }
  }

  /**
   * 从 Parquet 文件 footer 读取指标。
   *
   * @param path 文件路径
   * @param conf Hadoop 配置
   * @param metricsSpec 指标配置
   * @param mapping 字段名映射
   * @return 文件指标
   */
  private static Metrics getParquetMetrics(
      Path path, Configuration conf, MetricsConfig metricsSpec, NameMapping mapping) {
    try {
      InputFile file = HadoopInputFile.fromPath(path, conf);
      return ParquetUtil.fileMetrics(file, metricsSpec, mapping);
    } catch (UncheckedIOException e) {
      throw new RuntimeException("Unable to read the metrics of the Parquet file: " + path, e);
    }
  }

  /**
   * 从 ORC 文件 footer 读取指标。
   *
   * @param path 文件路径
   * @param conf Hadoop 配置
   * @param metricsSpec 指标配置
   * @param mapping 字段名映射
   * @return 文件指标
   */
  private static Metrics getOrcMetrics(
      Path path, Configuration conf, MetricsConfig metricsSpec, NameMapping mapping) {
    try {
      return OrcMetrics.fromInputFile(HadoopInputFile.fromPath(path, conf), metricsSpec, mapping);
    } catch (UncheckedIOException e) {
      throw new RuntimeException("Unable to read the metrics of the Orc file: " + path, e);
    }
  }

  /**
   * 把文件状态与指标组装为 Iceberg {@link DataFile}。
   *
   * @param stat Hadoop 文件状态（路径、大小）
   * @param partitionValues 分区值列表
   * @param spec 分区 spec
   * @param metrics 文件指标
   * @param format 文件格式名
   * @return 构建好的 DataFile
   */
  private static DataFile buildDataFile(
      FileStatus stat,
      List<String> partitionValues,
      PartitionSpec spec,
      Metrics metrics,
      String format) {
    return DataFiles.builder(spec)
        .withPath(stat.getPath().toString())
        .withFormat(format)
        .withFileSizeInBytes(stat.getLen())
        .withMetrics(metrics)
        .withPartitionValues(partitionValues)
        .build();
  }

  /**
   * 创建用于迁移的固定线程池，退出时自动回收线程。
   *
   * @param concurrentDeletes 线程数
   * @return 退出时自动关闭的 ExecutorService
   */
  private static ExecutorService migrationService(int concurrentDeletes) {
    return MoreExecutors.getExitingExecutorService(
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                concurrentDeletes,
                new ThreadFactoryBuilder().setNameFormat("table-migration-%d").build()));
  }
}
