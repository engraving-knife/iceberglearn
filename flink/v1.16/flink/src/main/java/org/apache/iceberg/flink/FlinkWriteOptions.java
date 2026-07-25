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
import org.apache.flink.annotation.Experimental;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.iceberg.SnapshotRef;

/**
 * Flink Iceberg sink 的写入选项（SQL hint 与 connector 选项）。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：声明 Iceberg sink 可用的写入选项，
 * 包括文件格式、目标文件大小、压缩参数、upsert、overwrite、分发模式、写入分支等。
 *
 * <p>设计意图：以 {@link ConfigOption} 形式声明，多数选项可覆盖同名表属性。
 */
public class FlinkWriteOptions {

  private FlinkWriteOptions() {}

  /** 写入文件格式，覆盖表的 write.format.default。 */
  public static final ConfigOption<String> WRITE_FORMAT =
      ConfigOptions.key("write-format").stringType().noDefaultValue();

  /** 目标文件大小（字节），覆盖表的 write.target-file-size-bytes。 */
  public static final ConfigOption<Long> TARGET_FILE_SIZE_BYTES =
      ConfigOptions.key("target-file-size-bytes").longType().noDefaultValue();

  /** 压缩 codec，覆盖表的 write.&lt;FILE_FORMAT&gt;.compression-codec。 */
  public static final ConfigOption<String> COMPRESSION_CODEC =
      ConfigOptions.key("compression-codec").stringType().noDefaultValue();

  /** 压缩级别，覆盖表的 write.&lt;FILE_FORMAT&gt;.compression-level。 */
  public static final ConfigOption<String> COMPRESSION_LEVEL =
      ConfigOptions.key("compression-level").stringType().noDefaultValue();

  /** 压缩策略，覆盖表的 write.&lt;FILE_FORMAT&gt;.compression-strategy。 */
  public static final ConfigOption<String> COMPRESSION_STRATEGY =
      ConfigOptions.key("compression-strategy").stringType().noDefaultValue();

  /** 是否启用 upsert，覆盖表的 write.upsert.enabled。 */
  public static final ConfigOption<Boolean> WRITE_UPSERT_ENABLED =
      ConfigOptions.key("upsert-enabled").booleanType().noDefaultValue();

  /** 是否覆盖写入模式，默认 false。 */
  public static final ConfigOption<Boolean> OVERWRITE_MODE =
      ConfigOptions.key("overwrite-enabled").booleanType().defaultValue(false);

  /** 数据分发模式，覆盖表的 write.distribution-mode。 */
  public static final ConfigOption<String> DISTRIBUTION_MODE =
      ConfigOptions.key("distribution-mode").stringType().noDefaultValue();

  /** 写入的目标分支，默认 main。 */
  public static final ConfigOption<String> BRANCH =
      ConfigOptions.key("branch").stringType().defaultValue(SnapshotRef.MAIN_BRANCH);

  /** 写入算子并行度。 */
  public static final ConfigOption<Integer> WRITE_PARALLELISM =
      ConfigOptions.key("write-parallelism").intType().noDefaultValue();

  /** 实验性：表元数据刷新间隔。 */
  @Experimental
  public static final ConfigOption<Duration> TABLE_REFRESH_INTERVAL =
      ConfigOptions.key("table-refresh-interval").durationType().noDefaultValue();
}
