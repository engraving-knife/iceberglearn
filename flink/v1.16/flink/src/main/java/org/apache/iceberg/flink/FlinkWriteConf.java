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
 * Iceberg Flink sink 的写入配置类，按优先级解析各项写入参数。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：基于 {@link FlinkConfParser} 从写入选项、 Flink 全局配置和表属性中读取 sink
 * 相关配置（文件格式、压缩、upsert、分发模式等）。
 *
 * <p>设计意图：配置类，方法自描述单个配置项；优先级为写入选项 > Flink 全局配置 > 表属性 > 默认值。 注意：本类不可序列化。
 *
 * <p>优先级（从高到低）：
 *
 * <ol>
 *   <li>写入选项（Write options）
 *   <li>Flink ReadableConfig
 *   <li>表元数据（Table metadata）
 * </ol>
 *
 * 若写入选项未设置，本类先查 Flink 配置，再回退到表元数据。
 */
public class FlinkWriteConf {

  private final FlinkConfParser confParser;

  /** 构造写入配置，绑定 Iceberg 表、用户写入选项与 Flink 可读配置。 */
  public FlinkWriteConf(
      Table table, Map<String, String> writeOptions, ReadableConfig readableConfig) {
    this.confParser = new FlinkConfParser(table, writeOptions, readableConfig);
  }

  /** 是否覆盖写入模式，默认 false。 */
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

  /** 读取数据文件格式（Parquet/Avro/ORC），覆盖表属性 write.format.default。 */
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

  /** 读取目标数据文件大小（字节）。 */
  public long targetDataFileSize() {
    return confParser
        .longConf()
        .option(FlinkWriteOptions.TARGET_FILE_SIZE_BYTES.key())
        .flinkConfig(FlinkWriteOptions.TARGET_FILE_SIZE_BYTES)
        .tableProperty(TableProperties.WRITE_TARGET_FILE_SIZE_BYTES)
        .defaultValue(TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT)
        .parse();
  }

  /** 读取 Parquet 压缩 codec。 */
  public String parquetCompressionCodec() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_CODEC.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_CODEC)
        .tableProperty(TableProperties.PARQUET_COMPRESSION)
        .defaultValue(TableProperties.PARQUET_COMPRESSION_DEFAULT)
        .parse();
  }

  /** 读取 Parquet 压缩级别（可选）。 */
  public String parquetCompressionLevel() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_LEVEL.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_LEVEL)
        .tableProperty(TableProperties.PARQUET_COMPRESSION_LEVEL)
        .defaultValue(TableProperties.PARQUET_COMPRESSION_LEVEL_DEFAULT)
        .parseOptional();
  }

  /** 读取 Avro 压缩 codec。 */
  public String avroCompressionCodec() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_CODEC.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_CODEC)
        .tableProperty(TableProperties.AVRO_COMPRESSION)
        .defaultValue(TableProperties.AVRO_COMPRESSION_DEFAULT)
        .parse();
  }

  /** 读取 Avro 压缩级别（可选）。 */
  public String avroCompressionLevel() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_LEVEL.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_LEVEL)
        .tableProperty(TableProperties.AVRO_COMPRESSION_LEVEL)
        .defaultValue(TableProperties.AVRO_COMPRESSION_LEVEL_DEFAULT)
        .parseOptional();
  }

  /** 读取 ORC 压缩 codec。 */
  public String orcCompressionCodec() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_CODEC.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_CODEC)
        .tableProperty(TableProperties.ORC_COMPRESSION)
        .defaultValue(TableProperties.ORC_COMPRESSION_DEFAULT)
        .parse();
  }

  /** 读取 ORC 压缩策略。 */
  public String orcCompressionStrategy() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.COMPRESSION_STRATEGY.key())
        .flinkConfig(FlinkWriteOptions.COMPRESSION_STRATEGY)
        .tableProperty(TableProperties.ORC_COMPRESSION_STRATEGY)
        .defaultValue(TableProperties.ORC_COMPRESSION_STRATEGY_DEFAULT)
        .parse();
  }

  /** 读取数据分发模式（NONE/HASH/RANGE）。 */
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

  /** 读取用于 manifest 规划/扫描的 worker 线程池大小。 */
  public int workerPoolSize() {
    return confParser
        .intConf()
        .flinkConfig(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE)
        .defaultValue(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.defaultValue())
        .parse();
  }

  /** 读取写入的目标分支。 */
  public String branch() {
    return confParser
        .stringConf()
        .option(FlinkWriteOptions.BRANCH.key())
        .defaultValue(FlinkWriteOptions.BRANCH.defaultValue())
        .parse();
  }

  /** 读取写入算子并行度（可选）。 */
  public Integer writeParallelism() {
    return confParser.intConf().option(FlinkWriteOptions.WRITE_PARALLELISM.key()).parseOptional();
  }

  /**
   * 实验性：读取 sink writer 子任务中刷新表实例的间隔。
   *
   * <p>注意：未来版本可能移除或更改。未设置时默认不刷新表。
   *
   * @return 刷新表的间隔
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
