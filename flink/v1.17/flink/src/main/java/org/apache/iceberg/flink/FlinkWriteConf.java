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
package org.apache.iceberg.flink;

import java.time.Duration;
import java.util.Map;
import org.apache.flink.annotation.Experimental;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;

/**
 * Iceberg Flink 写入侧的配置视图。
 *
 * <p>所属模块：iceberg-flink，封装 sink 写入相关的全部可配置项的解析结果。
 *
 * <p>职责：通过 {@link FlinkConfParser} 按优先级（写入选项 > Flink 配置 > 表属性 > 默认值）解析 覆写/upsert
 * 模式、文件格式、目标文件大小、各格式压缩参数、数据分布模式、分支、写入并行度等。
 *
 * <p>设计意图：与 {@link FlinkReadConf} 对称，集中收敛配置解析，对外暴露类型安全的 getter。 注意本类不参与序列化。
 *
 * <p>上下游关系：由 Flink sink 写入器/提交器读取；上游依赖 {@link FlinkWriteOptions}、 {@link FlinkConfigOptions} 与
 * {@link TableProperties}。
 */
public class FlinkWriteConf {

  private final FlinkConfParser confParser;

  /**
   * 构造写入配置视图。
   *
   * @param table Iceberg 表
   * @param writeOptions 写入选项（最高优先级）
   * @param readableConfig Flink 配置
   */
  public FlinkWriteConf(
      Table table, Map<String, String> writeOptions, ReadableConfig readableConfig) {
    this.confParser = new FlinkConfParser(table, writeOptions, readableConfig);
  }

  /** 是否为覆写模式（覆盖已有数据）。 */
  public boolean overwriteMode() {
    return confParser
        .booleanConf()
        .option(FlinkWriteOptions.OVERWRITE_MODE.key())
        .flinkConfig(FlinkWriteOptions.OVERWRITE_MODE)
        .defaultValue(FlinkWriteOptions.OVERWRITE_MODE.defaultValue())
        .parse();
  }

  /** 是否启用 upsert 模式。 */
  public boolean upsertMode() {
    return confParser
        .booleanConf()
        .option(FlinkWriteOptions.WRITE_UPSERT_ENABLED.key())
        .flinkConfig(FlinkWriteOptions.WRITE_UPSERT_ENABLED)
        .tableProperty(TableProperties.UPSERT_ENABLED)
        .defaultValue(TableProperties.UPSERT_ENABLED_DEFAULT)
        .parse();
  }

  /** 写入数据文件的格式（Parquet/Avro/ORC）。 */
  public FileFormat dataFileFormat() {
    String valueAsString =
        confParser
            .stringConf()
            .option(FlinkWriteOptions.WRITE_FORMAT.key())
            .flinkConfig(FlinkWriteOptions.WRITE_FORMAT)
            .tableProperty(TableProperties.DEFAULT_FILE_FORMAT)
            .defaultValue(TableProperties.DEFAULT_FILE_FORMAT_DEFAULT)
            .parse();
    return FileFormat.fromString(valueAsString);
  }

  /** 目标数据文件大小（字节）。 */
  public long targetDataFileSize() {
    return confParser
        .longConf()
        .option(FlinkWriteOptions.TARGET_FILE_SIZE_BYTES.key())
        .flinkConfig(FlinkWriteOptions.TARGET_FILE_SIZE_BYTES)
        .tableProperty(TableProperties.WRITE_TARGET_FILE_SIZE_BYTES)
        .defaultValue(TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT)
        .parse();
  }

  /** Parquet 压缩编解码器。 */
  public String parquetCompressionCodec() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_CODEC.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_CODEC)
        .tableProperty(TableProperties.PARQUET_COMPRESSION)
        .defaultValue(TableProperties.PARQUET_COMPRESSION_DEFAULT)
        .parse();
  }

  /** Parquet 压缩级别（可选）。 */
  public String parquetCompressionLevel() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_LEVEL.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_LEVEL)
        .tableProperty(TableProperties.PARQUET_COMPRESSION_LEVEL)
        .defaultValue(TableProperties.PARQUET_COMPRESSION_LEVEL_DEFAULT)
        .parseOptional();
  }

  /** Avro 压缩编解码器。 */
  public String avroCompressionCodec() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_CODEC.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_CODEC)
        .tableProperty(TableProperties.AVRO_COMPRESSION)
        .defaultValue(TableProperties.AVRO_COMPRESSION_DEFAULT)
        .parse();
  }

  /** Avro 压缩级别（可选）。 */
  public String avroCompressionLevel() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_LEVEL.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_LEVEL)
        .tableProperty(TableProperties.AVRO_COMPRESSION_LEVEL)
        .defaultValue(TableProperties.AVRO_COMPRESSION_LEVEL_DEFAULT)
        .parseOptional();
  }

  /** ORC 压缩编解码器。 */
  public String orcCompressionCodec() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_CODEC.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_CODEC)
        .tableProperty(TableProperties.ORC_COMPRESSION)
        .defaultValue(TableProperties.ORC_COMPRESSION_DEFAULT)
        .parse();
  }

  /** ORC 压缩策略。 */
  public String orcCompressionStrategy() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_STRATEGY.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_STRATEGY)
        .tableProperty(TableProperties.ORC_COMPRESSION_STRATEGY)
        .defaultValue(TableProperties.ORC_COMPRESSION_STRATEGY_DEFAULT)
        .parse();
  }

  /** 写入数据分布模式（NONE/HASH/RANGE）。 */
  public DistributionMode distributionMode() {
    String modeName =
        confParser
            .stringConf()
            .option(FlinkWriteOptions.DISTRIBUTION_MODE.key())
            .flinkConfig(FlinkWriteOptions.DISTRIBUTION_MODE)
            .tableProperty(TableProperties.WRITE_DISTRIBUTION_MODE)
            .defaultValue(TableProperties.WRITE_DISTRIBUTION_MODE_NONE)
            .parse();
    return DistributionMode.fromName(modeName);
  }

  /** 工作线程池大小。 */
  public int workerPoolSize() {
    return confParser
        .intConf()
        .flinkConfig(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE)
        .defaultValue(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.defaultValue())
        .parse();
  }

  /** 写入目标分支名。 */
  public String branch() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.BRANCH.key())
        .defaultValue(FlinkWriteOptions.BRANCH.defaultValue())
        .parse();
  }

  /** 写入算子并行度（可选）。 */
  public Integer writeParallelism() {
    return confParser.intConf().option(FlinkWriteOptions.WRITE_PARALLELISM.key()).parseOptional();
  }

  /**
   * sink writer 子任务刷新表实例的间隔（实验性，未来可能变更或移除）。
   *
   * <p>未指定时默认不刷新表。
   *
   * @return 刷新间隔，未配置返回 null
   */
  @Experimental
  public Duration tableRefreshInterval() {
    return confParser
        .durationConf()
        .option(FlinkWriteOptions.TABLE_REFRESH_INTERVAL.key())
        .flinkConfig(FlinkWriteOptions.TABLE_REFRESH_INTERVAL)
        .parseOptional();
  }
}
