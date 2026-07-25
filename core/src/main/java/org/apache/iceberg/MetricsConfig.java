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
package org.apache.iceberg;

import static org.apache.iceberg.TableProperties.DEFAULT_WRITE_METRICS_MODE;
import static org.apache.iceberg.TableProperties.DEFAULT_WRITE_METRICS_MODE_DEFAULT;
import static org.apache.iceberg.TableProperties.METRICS_MAX_INFERRED_COLUMN_DEFAULTS;
import static org.apache.iceberg.TableProperties.METRICS_MAX_INFERRED_COLUMN_DEFAULTS_DEFAULT;
import static org.apache.iceberg.TableProperties.METRICS_MODE_COLUMN_CONF_PREFIX;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.apache.iceberg.MetricsModes.MetricsMode;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SerializableMap;
import org.apache.iceberg.util.SortOrderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：列级指标（metrics）采集配置。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>决定写入数据文件时为每个列采集哪些统计指标（counts / truncate(16) / full / none）。
 *   <li>支持表级默认模式 + 列级覆盖 + 排序列自动提升 三层优先级。
 *   <li>对宽表自动限制参与默认指标采集的列数，避免 manifest 文件膨胀。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不可变（{@link Immutable}）+ {@link Serializable}，可安全地在引擎与 writer 间传递。
 *   <li>把配置解析（{@code from}）与配置使用（{@link #columnMode}）解耦，便于复用与单测。
 *   <li>排序列即使默认模式是 None/Counts 也会自动升到 truncate(16)，因为排序列用于 数据裁剪非常关键。
 *   <li>用户配置非法时不抛异常，而是回退到默认值并 warn 日志，保证写入不中断。
 * </ul>
 *
 * <p>上下游关系：被 {@code BaseOverwriteFiles}、{@code AppendFiles}、各 writer 模块在 写数据/删除文件前调用以决定指标采集策略；依赖
 * {@link TableProperties} 读配置、 {@link SortOrderUtil} 找排序列、{@link MetricsModes} 解析模式字符串。
 */
@Immutable
public final class MetricsConfig implements Serializable {

  private static final Logger LOG = LoggerFactory.getLogger(MetricsConfig.class);
  private static final Joiner DOT = Joiner.on('.');

  // Disable metrics by default for wide tables to prevent excessive metadata
  private static final MetricsMode DEFAULT_MODE =
      MetricsModes.fromString(DEFAULT_WRITE_METRICS_MODE_DEFAULT);
  private static final MetricsConfig DEFAULT = new MetricsConfig(ImmutableMap.of(), DEFAULT_MODE);

  private final Map<String, MetricsMode> columnModes;
  private final MetricsMode defaultMode;

  /**
   * 私有构造：拷贝传入的列模式映射为不可变序列化版本。
   *
   * @param columnModes 列名到模式的映射
   * @param defaultMode 默认模式（未在 columnModes 中出现的列使用此模式）
   */
  private MetricsConfig(Map<String, MetricsMode> columnModes, MetricsMode defaultMode) {
    this.columnModes = SerializableMap.copyOf(columnModes).immutableMap();
    this.defaultMode = defaultMode;
  }

  /**
   * 返回全局默认 MetricsConfig（空列模式 + 默认模式）。
   *
   * @return 默认配置实例
   */
  public static MetricsConfig getDefault() {
    return DEFAULT;
  }

  /**
   * 从表属性配置创建指标配置（不含 schema 与排序信息）。
   *
   * <p>解析 {@code write.metadata.metrics.default} 与 {@code write.metadata.metrics.column.*} 属性。
   *
   * @param props 表属性配置
   * @return 指标配置
   * @deprecated use {@link MetricsConfig#forTable(Table)}
   */
  @Deprecated
  public static MetricsConfig fromProperties(Map<String, String> props) {
    return from(props, null, null);
  }

  /**
   * 从表对象创建指标配置，包含表的 schema 与 sortOrder。
   *
   * @param table Iceberg 表
   * @return 指标配置
   */
  public static MetricsConfig forTable(Table table) {
    return from(table.properties(), table.schema(), table.sortOrder());
  }

  /**
   * Creates a metrics config for a position delete file.
   *
   * <p>中文说明：为位置删除（position delete）文件构造指标配置。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>强制 {@code file_path} 与 {@code pos} 列使用 Full 模式（删除文件必须能精确匹配）；
   *   <li>取主表配置，把列模式前缀 {@code _spec.delete_file.}（拼成嵌套字段路径）；
   *   <li>保留主表的 defaultMode。
   * </ol>
   *
   * @param table an Iceberg table / 关联的 Iceberg 主表
   * @return 适用于位置删除文件的指标配置
   */
  public static MetricsConfig forPositionDelete(Table table) {
    ImmutableMap.Builder<String, MetricsMode> columnModes = ImmutableMap.builder();

    columnModes.put(MetadataColumns.DELETE_FILE_PATH.name(), MetricsModes.Full.get());
    columnModes.put(MetadataColumns.DELETE_FILE_POS.name(), MetricsModes.Full.get());

    MetricsConfig tableConfig = forTable(table);

    MetricsMode defaultMode = tableConfig.defaultMode;
    tableConfig.columnModes.forEach(
        (columnAlias, mode) -> {
          String positionDeleteColumnAlias =
              DOT.join(MetadataColumns.DELETE_FILE_ROW_FIELD_NAME, columnAlias);
          columnModes.put(positionDeleteColumnAlias, mode);
        });

    return new MetricsConfig(columnModes.build(), defaultMode);
  }

  /**
   * 根据属性覆盖、schema 与排序规则为所有列生成指标配置。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>读取宽表列数上限，决定默认模式应用范围；
   *   <li>解析用户配置的默认模式（write.metadata.metrics.default）；
   *   <li>将排序列自动提升到 truncate(16)；
   *   <li>应用列级用户覆盖（write.metadata.metrics.column.*）。
   * </ol>
   *
   * @param props 表属性，读取 metrics 覆盖与默认模式
   * @param schema 表 schema（用于宽表列数判断）
   * @param order 排序规则，排序列将被提升到 truncate(16)
   * @return 指标配置
   */
  private static MetricsConfig from(Map<String, String> props, Schema schema, SortOrder order) {
    int maxInferredDefaultColumns = maxInferredColumnDefaults(props);
    Map<String, MetricsMode> columnModes = Maps.newHashMap();

    // Handle user override of default mode
    MetricsMode defaultMode;
    String configuredDefault = props.get(DEFAULT_WRITE_METRICS_MODE);
    if (configuredDefault != null) {
      // a user-configured default mode is applied for all columns
      defaultMode = parseMode(configuredDefault, DEFAULT_MODE, "default");

    } else if (schema == null || schema.columns().size() <= maxInferredDefaultColumns) {
      // there are less than the inferred limit, so the default is used everywhere
      defaultMode = DEFAULT_MODE;

    } else {
      // an inferred default mode is applied to the first few columns, up to the limit
      Schema subSchema = new Schema(schema.columns().subList(0, maxInferredDefaultColumns));
      for (Integer id : TypeUtil.getProjectedIds(subSchema)) {
        columnModes.put(subSchema.findColumnName(id), DEFAULT_MODE);
      }

      // all other columns don't use metrics
      defaultMode = MetricsModes.None.get();
    }

    // First set sorted column with sorted column default (can be overridden by user)
    MetricsMode sortedColDefaultMode = sortedColumnDefaultMode(defaultMode);
    Set<String> sortedCols = SortOrderUtil.orderPreservingSortedColumns(order);
    sortedCols.forEach(sc -> columnModes.put(sc, sortedColDefaultMode));

    // Handle user overrides of defaults
    for (String key : props.keySet()) {
      if (key.startsWith(METRICS_MODE_COLUMN_CONF_PREFIX)) {
        String columnAlias = key.replaceFirst(METRICS_MODE_COLUMN_CONF_PREFIX, "");
        MetricsMode mode = parseMode(props.get(key), defaultMode, "column " + columnAlias);
        columnModes.put(columnAlias, mode);
      }
    }

    return new MetricsConfig(columnModes, defaultMode);
  }

  /**
   * 当默认模式为 None 或 Counts 时，自动将排序列提升到 truncate(16)。
   *
   * @param defaultMode 默认模式
   * @return 排序列应使用的模式
   */
  private static MetricsMode sortedColumnDefaultMode(MetricsMode defaultMode) {
    if (defaultMode == MetricsModes.None.get() || defaultMode == MetricsModes.Counts.get()) {
      return MetricsModes.Truncate.withLength(16);
    } else {
      return defaultMode;
    }
  }

  /**
   * 读取宽表自动推断默认指标列数上限。
   *
   * <p>从表属性中读取 {@code write.metadata.metrics.max-inferred-column-defaults}， 若为负数则 warn 并回退到默认值。
   *
   * @param properties 表属性
   * @return 用于决定默认模式应用列数的上限
   */
  private static int maxInferredColumnDefaults(Map<String, String> properties) {
    int maxInferredDefaultColumns =
        PropertyUtil.propertyAsInt(
            properties,
            METRICS_MAX_INFERRED_COLUMN_DEFAULTS,
            METRICS_MAX_INFERRED_COLUMN_DEFAULTS_DEFAULT);
    if (maxInferredDefaultColumns < 0) {
      LOG.warn(
          "Invalid value for {} (negative): {}, falling back to {}",
          METRICS_MAX_INFERRED_COLUMN_DEFAULTS,
          maxInferredDefaultColumns,
          METRICS_MAX_INFERRED_COLUMN_DEFAULTS_DEFAULT);
      return METRICS_MAX_INFERRED_COLUMN_DEFAULTS_DEFAULT;
    } else {
      return maxInferredDefaultColumns;
    }
  }

  /**
   * 解析用户配置的模式字符串，失败时回退到 fallback。
   *
   * <p>解析失败不抛异常，仅 warn 日志，保证写入流程不中断。
   *
   * @param modeString 模式字符串（如 "none" / "counts" / "truncate(16)" / "full"）
   * @param fallback 解析失败时使用的回退模式
   * @param context 出错日志中的上下文描述（如 "column foo" / "default"）
   * @return 解析得到的模式，或 fallback
   */
  private static MetricsMode parseMode(String modeString, MetricsMode fallback, String context) {
    try {
      return MetricsModes.fromString(modeString);
    } catch (IllegalArgumentException err) {
      // User override was invalid, log the error and use the default
      LOG.warn("Ignoring invalid metrics mode ({}): {}", context, modeString, err);
      return fallback;
    }
  }

  /**
   * 校验所有列级覆盖配置中引用的列名都能在给定 schema 中找到。
   *
   * @param schema 表 schema
   * @throws ValidationException 当存在引用了不存在列名的覆盖配置时
   */
  public void validateReferencedColumns(Schema schema) {
    for (String column : columnModes.keySet()) {
      ValidationException.check(
          schema.findField(column) != null,
          "Invalid metrics config, could not find column %s from table prop %s in schema %s",
          column,
          METRICS_MODE_COLUMN_CONF_PREFIX + column,
          schema);
    }
  }

  /**
   * 查询某个列的指标采集模式。
   *
   * <p>优先使用列级覆盖，未配置则回退到 defaultMode。
   *
   * @param columnAlias 列别名（点分路径）
   * @return 该列应使用的指标模式
   */
  public MetricsMode columnMode(String columnAlias) {
    return columnModes.getOrDefault(columnAlias, defaultMode);
  }
}
