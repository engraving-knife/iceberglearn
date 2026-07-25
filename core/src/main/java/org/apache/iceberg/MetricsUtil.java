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

import static org.apache.iceberg.types.Types.NestedField.optional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 文件指标（metrics）工具类：提供指标裁剪、NaN 计数构造、可读指标 schema 生成等能力。
 *
 * <p>所属模块：iceberg-core（指标处理工具层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按字段 id 裁剪 {@link Metrics} 中的计数与上下界，用于投影下推等场景。
 *   <li>从写入期 {@link FieldMetrics} 流构造 NaN 计数 map。
 *   <li>定义"可读指标"（readable_metrics）列结构，把内部 ByteBuffer 形式的上下界转换为 人类可读的强类型值，便于在元数据表中查询。
 * </ul>
 *
 * <p>设计意图：把指标相关的通用操作集中到工具类，避免散落在各写入器与元数据表中。
 *
 * <p>上下游关系：被各元数据表（FILES、PARTITIONS 等）与写入器调用。
 */
public class MetricsUtil {

  private MetricsUtil() {}

  /**
   * 拷贝指标对象，但移除指定字段的 value/null/NaN 计数。
   *
   * @param metrics 原指标
   * @param excludedFieldIds 需移除计数的字段 id 集合
   * @return 新的指标对象
   */
  public static Metrics copyWithoutFieldCounts(Metrics metrics, Set<Integer> excludedFieldIds) {
    return new Metrics(
        metrics.recordCount(),
        metrics.columnSizes(),
        copyWithoutKeys(metrics.valueCounts(), excludedFieldIds),
        copyWithoutKeys(metrics.nullValueCounts(), excludedFieldIds),
        copyWithoutKeys(metrics.nanValueCounts(), excludedFieldIds),
        metrics.lowerBounds(),
        metrics.upperBounds());
  }

  /**
   * 拷贝指标对象，但移除指定字段的计数与上下界。
   *
   * @param metrics 原指标
   * @param excludedFieldIds 需移除计数与上下界的字段 id 集合
   * @return 新的指标对象
   */
  public static Metrics copyWithoutFieldCountsAndBounds(
      Metrics metrics, Set<Integer> excludedFieldIds) {
    return new Metrics(
        metrics.recordCount(),
        metrics.columnSizes(),
        copyWithoutKeys(metrics.valueCounts(), excludedFieldIds),
        copyWithoutKeys(metrics.nullValueCounts(), excludedFieldIds),
        copyWithoutKeys(metrics.nanValueCounts(), excludedFieldIds),
        copyWithoutKeys(metrics.lowerBounds(), excludedFieldIds),
        copyWithoutKeys(metrics.upperBounds(), excludedFieldIds));
  }

  private static <K, V> Map<K, V> copyWithoutKeys(Map<K, V> map, Set<K> keys) {
    if (map == null) {
      return null;
    }

    Map<K, V> filteredMap = Maps.newHashMap(map);

    for (K key : keys) {
      filteredMap.remove(key);
    }

    return filteredMap.isEmpty() ? null : filteredMap;
  }

  /**
   * 根据字段指标流与指标配置构造"字段 id -> NaN 计数"映射。
   *
   * <p>逻辑：过滤掉指标模式为 None 的字段，剩余字段按 id 收集 NaN 计数。
   *
   * @param fieldMetrics 写入期字段指标流
   * @param metricsConfig 指标配置
   * @param inputSchema 输入 schema
   * @return 字段 id 到 NaN 计数的映射
   */
  public static Map<Integer, Long> createNanValueCounts(
      Stream<FieldMetrics<?>> fieldMetrics, MetricsConfig metricsConfig, Schema inputSchema) {
    Preconditions.checkNotNull(metricsConfig, "metricsConfig is required");

    if (fieldMetrics == null || inputSchema == null) {
      return Maps.newHashMap();
    }

    return fieldMetrics
        .filter(
            metrics ->
                metricsMode(inputSchema, metricsConfig, metrics.id()) != MetricsModes.None.get())
        .collect(Collectors.toMap(FieldMetrics::id, FieldMetrics::nanValueCount));
  }

  /**
   * 从指标配置中提取指定字段 id 的指标模式。
   *
   * @param inputSchema 输入 schema
   * @param metricsConfig 指标配置
   * @param fieldId 字段 id
   * @return 该字段的指标模式
   */
  public static MetricsModes.MetricsMode metricsMode(
      Schema inputSchema, MetricsConfig metricsConfig, int fieldId) {
    Preconditions.checkNotNull(inputSchema, "inputSchema is required");
    Preconditions.checkNotNull(metricsConfig, "metricsConfig is required");

    String columnName = inputSchema.findColumnName(fieldId);
    return metricsConfig.columnMode(columnName);
  }

  public static final List<ReadableMetricColDefinition> READABLE_METRIC_COLS =
      ImmutableList.of(
          new ReadableMetricColDefinition(
              "column_size",
              "Total size on disk",
              DataFile.COLUMN_SIZES,
              field -> Types.LongType.get(),
              (file, field) ->
                  file.columnSizes() == null ? null : file.columnSizes().get(field.fieldId())),
          new ReadableMetricColDefinition(
              "value_count",
              "Total count, including null and NaN",
              DataFile.VALUE_COUNTS,
              field -> Types.LongType.get(),
              (file, field) ->
                  file.valueCounts() == null ? null : file.valueCounts().get(field.fieldId())),
          new ReadableMetricColDefinition(
              "null_value_count",
              "Null value count",
              DataFile.NULL_VALUE_COUNTS,
              field -> Types.LongType.get(),
              (file, field) ->
                  file.nullValueCounts() == null
                      ? null
                      : file.nullValueCounts().get(field.fieldId())),
          new ReadableMetricColDefinition(
              "nan_value_count",
              "NaN value count",
              DataFile.NAN_VALUE_COUNTS,
              field -> Types.LongType.get(),
              (file, field) ->
                  file.nanValueCounts() == null
                      ? null
                      : file.nanValueCounts().get(field.fieldId())),
          new ReadableMetricColDefinition(
              "lower_bound",
              "Lower bound",
              DataFile.LOWER_BOUNDS,
              Types.NestedField::type,
              (file, field) ->
                  file.lowerBounds() == null
                      ? null
                      : Conversions.fromByteBuffer(
                          field.type(), file.lowerBounds().get(field.fieldId()))),
          new ReadableMetricColDefinition(
              "upper_bound",
              "Upper bound",
              DataFile.UPPER_BOUNDS,
              Types.NestedField::type,
              (file, field) ->
                  file.upperBounds() == null
                      ? null
                      : Conversions.fromByteBuffer(
                          field.type(), file.upperBounds().get(field.fieldId()))));

  public static final String READABLE_METRICS = "readable_metrics";

  /**
   * 可读指标列定义：把内部原始指标（如 columnSizes、lowerBounds 等）映射为可读列。
   *
   * <p>每个定义包含列名、文档、原始字段、类型函数与取值函数。
   */
  public static class ReadableMetricColDefinition {
    private final String name;
    private final String doc;
    private final Types.NestedField originalCol;
    private final TypeFunction typeFunction;
    private final MetricFunction metricFunction;

    public interface TypeFunction {
      Type type(Types.NestedField originalCol);
    }

    public interface MetricFunction {
      Object metric(ContentFile<?> file, Types.NestedField originalCol);
    }

    /**
     * @param name column name
     * @param doc column doc
     * @param originalCol original (raw) metric column field on metadata table
     * @param typeFunction function that returns the readable metric column type from original field
     *     type
     * @param metricFunction function that returns readable metric from data file
     */
    ReadableMetricColDefinition(
        String name,
        String doc,
        Types.NestedField originalCol,
        TypeFunction typeFunction,
        MetricFunction metricFunction) {
      this.name = name;
      this.doc = doc;
      this.originalCol = originalCol;
      this.typeFunction = typeFunction;
      this.metricFunction = metricFunction;
    }

    Types.NestedField originalCol() {
      return originalCol;
    }

    Type colType(Types.NestedField field) {
      return typeFunction.type(field);
    }

    String name() {
      return name;
    }

    String doc() {
      return doc;
    }

    Object value(ContentFile<?> dataFile, Types.NestedField dataField) {
      return metricFunction.metric(dataFile, dataField);
    }
  }

  /**
   * 单个原始列的可读指标结构：按 READABLE_METRIC_COLS 顺序存储指标值， 支持按投影位置访问。只读，set 抛出 UnsupportedOperationException。
   */
  public static class ReadableColMetricsStruct implements StructLike {

    private final String columnName;
    private final Map<Integer, Integer> projectionMap;
    private final Object[] metrics;

    /**
     * 构造单列可读指标结构。
     *
     * @param columnName 列名
     * @param projection 投影字段（决定哪些指标列可见）
     * @param metrics 按 READABLE_METRIC_COLS 顺序的指标值数组
     */
    public ReadableColMetricsStruct(
        String columnName, Types.NestedField projection, Object... metrics) {
      this.columnName = columnName;
      this.projectionMap = readableMetricsProjection(projection);
      this.metrics = metrics;
    }

    @Override
    public int size() {
      return projectionMap.size();
    }

    @Override
    public <T> T get(int pos, Class<T> javaClass) {
      Object value = get(pos);
      return value == null ? null : javaClass.cast(value);
    }

    @Override
    public <T> void set(int pos, T value) {
      throw new UnsupportedOperationException("ReadableColMetricsStruct is read only");
    }

    private Object get(int pos) {
      int projectedPos = projectionMap.get(pos);
      return metrics[projectedPos];
    }

    /** Returns map of projected position to actual position of this struct's fields */
    private Map<Integer, Integer> readableMetricsProjection(Types.NestedField projection) {
      Map<Integer, Integer> result = Maps.newHashMap();

      Set<String> projectedFields =
          Sets.newHashSet(
              projection.type().asStructType().fields().stream()
                  .map(Types.NestedField::name)
                  .collect(Collectors.toSet()));

      int projectedIndex = 0;
      for (int fieldIndex = 0; fieldIndex < READABLE_METRIC_COLS.size(); fieldIndex++) {
        ReadableMetricColDefinition readableMetric = READABLE_METRIC_COLS.get(fieldIndex);

        if (projectedFields.contains(readableMetric.name())) {
          result.put(projectedIndex, fieldIndex);
          projectedIndex++;
        }
      }
      return result;
    }

    String columnName() {
      return columnName;
    }
  }

  /**
   * 全表所有原始列的可读指标结构：包含每个原始列对应的 {@link ReadableColMetricsStruct}。 只读，set 抛出
   * UnsupportedOperationException。
   */
  public static class ReadableMetricsStruct implements StructLike {

    private final List<StructLike> columnMetrics;

    public ReadableMetricsStruct(List<StructLike> columnMetrics) {
      this.columnMetrics = columnMetrics;
    }

    @Override
    public int size() {
      return columnMetrics.size();
    }

    @Override
    public <T> T get(int pos, Class<T> javaClass) {
      return javaClass.cast(columnMetrics.get(pos));
    }

    @Override
    public <T> void set(int pos, T value) {
      throw new UnsupportedOperationException("ReadableMetricsStruct is read only");
    }
  }

  /**
   * 为元数据表动态计算 readable_metrics 列的 schema。
   *
   * <p>逻辑：遍历数据表所有原始列，为每列生成一个嵌套 struct（含全部可读指标子列）， 字段 id 从 metadataTableSchema.highestFieldId()
   * 之后递增；最后按列名排序并包装为 顶层 readable_metrics 字段。
   *
   * @param dataTableSchema 数据表 schema
   * @param metadataTableSchema 已有元数据表 schema（用于保证字段 id 唯一）
   * @return readable_metrics 列的 schema
   */
  public static Schema readableMetricsSchema(Schema dataTableSchema, Schema metadataTableSchema) {
    List<Types.NestedField> fields = Lists.newArrayList();
    Map<Integer, String> idToName = dataTableSchema.idToName();
    AtomicInteger nextId = new AtomicInteger(metadataTableSchema.highestFieldId());

    for (int id : idToName.keySet()) {
      Types.NestedField field = dataTableSchema.findField(id);

      if (field.type().isPrimitiveType()) {
        String colName = idToName.get(id);

        fields.add(
            Types.NestedField.of(
                nextId.incrementAndGet(),
                true,
                colName,
                Types.StructType.of(
                    READABLE_METRIC_COLS.stream()
                        .map(
                            m ->
                                optional(
                                    nextId.incrementAndGet(), m.name(), m.colType(field), m.doc()))
                        .collect(Collectors.toList())),
                String.format("Metrics for column %s", colName)));
      }
    }

    fields.sort(Comparator.comparing(Types.NestedField::name));
    return new Schema(
        optional(
            nextId.incrementAndGet(),
            "readable_metrics",
            Types.StructType.of(fields),
            "Column metrics in readable form"));
  }

  /**
   * 从文件元数据构造可读指标结构行。
   *
   * <p>逻辑：遍历数据表字段，对每个原始列按 READABLE_METRIC_COLS 取值； 仅保留投影 schema 中包含的列；最后按列名排序。
   *
   * @param schema 原数据表 schema
   * @param file 含指标的文件
   * @param projectedSchema 用户投影 schema
   * @return 可读指标结构
   */
  public static ReadableMetricsStruct readableMetricsStruct(
      Schema schema, ContentFile<?> file, Types.StructType projectedSchema) {
    Map<Integer, String> idToName = schema.idToName();
    List<ReadableColMetricsStruct> colMetrics = Lists.newArrayList();

    for (int id : idToName.keySet()) {
      String qualifiedName = idToName.get(id);
      Types.NestedField field = schema.findField(id);

      Object[] metrics =
          READABLE_METRIC_COLS.stream()
              .map(readableMetric -> readableMetric.value(file, field))
              .toArray();

      if (field.type().isPrimitiveType() // Iceberg stores metrics only for primitive types
          && projectedSchema.field(qualifiedName)
              != null) { // User has requested this column metric
        colMetrics.add(
            new ReadableColMetricsStruct(
                qualifiedName, projectedSchema.field(qualifiedName), metrics));
      }
    }

    colMetrics.sort(Comparator.comparing(ReadableColMetricsStruct::columnName));
    return new ReadableMetricsStruct(
        colMetrics.stream().map(m -> (StructLike) m).collect(Collectors.toList()));
  }

  /**
   * 在原 struct 基础上附加 readable_metrics 列的自定义 struct 实现。
   *
   * <p>设计意图：元数据表行原本不含 readable_metrics，本类在指定位置插入该列， 同时保持其他列的原有位置，避免重写整个行结构。
   */
  static class StructWithReadableMetrics implements StructLike {
    private final StructLike struct;
    private final MetricsUtil.ReadableMetricsStruct readableMetrics;
    private final int projectionColumnCount;
    private final int metricsPosition;

    /**
     * 构造附加 readable_metrics 列的 struct。
     *
     * @param struct 原 struct
     * @param structSize 总列数（含 readable_metrics）
     * @param readableMetrics readable_metrics 结构
     * @param metricsPosition readable_metrics 列位置
     */
    StructWithReadableMetrics(
        StructLike struct,
        int structSize,
        MetricsUtil.ReadableMetricsStruct readableMetrics,
        int metricsPosition) {
      this.struct = struct;
      this.readableMetrics = readableMetrics;
      this.projectionColumnCount = structSize;
      this.metricsPosition = metricsPosition;
    }

    @Override
    public int size() {
      return projectionColumnCount;
    }

    @Override
    public <T> T get(int pos, Class<T> javaClass) {
      if (pos < metricsPosition) {
        return struct.get(pos, javaClass);
      } else if (pos == metricsPosition) {
        return javaClass.cast(readableMetrics);
      } else {
        // columnCount = fileAsStruct column count + the readable metrics field.
        // When pos is greater than metricsPosition, the actual position of the field in
        // fileAsStruct should be subtracted by 1.
        return struct.get(pos - 1, javaClass);
      }
    }

    @Override
    public <T> void set(int pos, T value) {
      throw new UnsupportedOperationException("StructWithReadableMetrics is read only");
    }
  }
}
