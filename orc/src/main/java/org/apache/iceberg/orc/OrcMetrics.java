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
package org.apache.iceberg.orc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.MetricsModes;
import org.apache.iceberg.MetricsModes.MetricsMode;
import org.apache.iceberg.MetricsUtil;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.UnicodeUtil;
import org.apache.orc.BooleanColumnStatistics;
import org.apache.orc.ColumnStatistics;
import org.apache.orc.DateColumnStatistics;
import org.apache.orc.DecimalColumnStatistics;
import org.apache.orc.DoubleColumnStatistics;
import org.apache.orc.IntegerColumnStatistics;
import org.apache.orc.Reader;
import org.apache.orc.StringColumnStatistics;
import org.apache.orc.TimestampColumnStatistics;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;

/**
 * ORC 文件 metrics 收集器：从 ORC Reader/Writer 的列统计中提取 Iceberg {@link Metrics}。
 *
 * <p>所属模块：iceberg-orc。用于在写入完成或读取文件时生成列级统计（行数、列大小、 值计数、null 计数、上下界、nan 计数），供 Iceberg 的元数据管理与查询优化使用。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>fromInputFile：从已有 ORC 文件读取列统计并构建 Metrics。
 *   <li>fromWriter：从 ORC Writer 获取列统计，合并 Iceberg 自跟踪的 FieldMetrics（float/double nan）。
 *   <li>buildOrcMetrics：核心构建逻辑，遍历列统计按 MetricsMode 决定收集粒度。
 *   <li>fromOrcMin/fromOrcMax：按类型把 ORC 列统计的上下界转为 Iceberg 二进制表示。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>ORC 不区分 NaN 与正常浮点值的上下界，Iceberg 自跟踪 FieldMetrics 修正此行为； 导入文件无 FieldMetrics 时回退到 ORC 统计并用
 *       replaceNaN 替换。
 *   <li>字符串上下界按 MetricsMode.Truncate 截断以控制 metrics 体积。
 *   <li>无 Iceberg id 的文件返回空 metrics（只有行数）。
 * </ul>
 *
 * <p>上下游关系：被 {@link OrcFileAppender#metrics()} 和 iceberg-core 的文件扫描调用； 依赖 {@link ORCSchemaUtil} 做
 * schema 转换与 id 查找。
 */
public class OrcMetrics {

  private enum Bound {
    LOWER,
    UPPER
  }

  private OrcMetrics() {}

  /** 从文件读取 metrics，使用默认 MetricsConfig。 */
  public static Metrics fromInputFile(InputFile file) {
    return fromInputFile(file, MetricsConfig.getDefault());
  }

  /** 从文件读取 metrics，指定 MetricsConfig，不带 NameMapping。 */
  public static Metrics fromInputFile(InputFile file, MetricsConfig metricsConfig) {
    return fromInputFile(file, metricsConfig, null);
  }

  /**
   * 从文件读取 metrics，指定 MetricsConfig 和 NameMapping。
   *
   * <p>设计要点：若文件为 HadoopInputFile 则复用其 Configuration，否则创建默认 Configuration。
   */
  public static Metrics fromInputFile(
      InputFile file, MetricsConfig metricsConfig, NameMapping mapping) {
    final Configuration config =
        (file instanceof HadoopInputFile)
            ? ((HadoopInputFile) file).getConf()
            : new Configuration();
    return fromInputFile(file, config, metricsConfig, mapping);
  }

  /**
   * 从文件读取 metrics 的内部实现：创建 ORC Reader 后委托 buildOrcMetrics。
   *
   * @throws RuntimeIOException 打开文件失败
   */
  static Metrics fromInputFile(
      InputFile file, Configuration config, MetricsConfig metricsConfig, NameMapping mapping) {
    try (Reader orcReader = ORC.newFileReader(file, config)) {
      return buildOrcMetrics(
          orcReader.getNumberOfRows(),
          orcReader.getSchema(),
          orcReader.getStatistics(),
          Stream.empty(),
          metricsConfig,
          mapping);
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe, "Failed to open file: %s", file.location());
    }
  }

  /**
   * 从 ORC Writer 读取 metrics：取 Writer 的列统计，合并 Iceberg 自跟踪的 FieldMetrics。
   *
   * @throws RuntimeIOException 获取统计失败
   */
  static Metrics fromWriter(
      Writer writer, Stream<FieldMetrics<?>> fieldMetricsStream, MetricsConfig metricsConfig) {
    try {
      return buildOrcMetrics(
          writer.getNumberOfRows(),
          writer.getSchema(),
          writer.getStatistics(),
          fieldMetricsStream,
          metricsConfig,
          null);
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe, "Failed to get statistics from writer");
    }
  }

  /**
   * 核心构建逻辑：遍历 ORC 列统计，按 MetricsMode 收集各字段 metrics。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若 schema 无 id 且有 NameMapping，先 applyNameMapping 补全 id。
   *   <li>若仍无 id，返回空 metrics（仅行数）。
   *   <li>遍历 colStats，按字段 id 和 MetricsMode 收集 columnSizes/valueCounts/nullCounts/上下界。
   *   <li>float/double 优先用 Iceberg FieldMetrics 的上下界（排除 NaN）。
   *   <li>字符串按 Truncate 模式截断上下界。
   * </ol>
   */
  private static Metrics buildOrcMetrics(
      final long numOfRows,
      final TypeDescription orcSchema,
      final ColumnStatistics[] colStats,
      final Stream<FieldMetrics<?>> fieldMetricsStream,
      final MetricsConfig metricsConfig,
      final NameMapping mapping) {
    final TypeDescription orcSchemaWithIds =
        (!ORCSchemaUtil.hasIds(orcSchema) && mapping != null)
            ? ORCSchemaUtil.applyNameMapping(orcSchema, mapping)
            : orcSchema;
    final Set<Integer> statsColumns = statsColumns(orcSchemaWithIds);
    final MetricsConfig effectiveMetricsConfig =
        Optional.ofNullable(metricsConfig).orElseGet(MetricsConfig::getDefault);
    Map<Integer, Long> columnSizes = Maps.newHashMapWithExpectedSize(colStats.length);
    Map<Integer, Long> valueCounts = Maps.newHashMapWithExpectedSize(colStats.length);
    Map<Integer, Long> nullCounts = Maps.newHashMapWithExpectedSize(colStats.length);

    if (!ORCSchemaUtil.hasIds(orcSchemaWithIds)) {
      return new Metrics(numOfRows, columnSizes, valueCounts, nullCounts, null, null, null);
    }

    final Schema schema = ORCSchemaUtil.convert(orcSchemaWithIds);
    Map<Integer, ByteBuffer> lowerBounds = Maps.newHashMap();
    Map<Integer, ByteBuffer> upperBounds = Maps.newHashMap();

    Map<Integer, FieldMetrics<?>> fieldMetricsMap =
        Optional.ofNullable(fieldMetricsStream)
            .map(stream -> stream.collect(Collectors.toMap(FieldMetrics::id, Function.identity())))
            .orElseGet(Maps::newHashMap);

    for (int i = 0; i < colStats.length; i++) {
      final ColumnStatistics colStat = colStats[i];
      final TypeDescription orcCol = orcSchemaWithIds.findSubtype(i);
      final Optional<Types.NestedField> icebergColOpt =
          ORCSchemaUtil.icebergID(orcCol).map(schema::findField);

      if (icebergColOpt.isPresent()) {
        final Types.NestedField icebergCol = icebergColOpt.get();
        final int fieldId = icebergCol.fieldId();

        final MetricsMode metricsMode =
            MetricsUtil.metricsMode(schema, effectiveMetricsConfig, icebergCol.fieldId());
        columnSizes.put(fieldId, colStat.getBytesOnDisk());

        if (metricsMode == MetricsModes.None.get()) {
          continue;
        }

        if (statsColumns.contains(fieldId)) {
          // Since ORC does not track null values nor repeated ones, the value count for columns in
          // containers (maps, list) may be larger than what it actually is, however these are not
          // used in experssions right now. For such cases, we use the value number of values
          // directly stored in ORC.
          if (colStat.hasNull()) {
            nullCounts.put(fieldId, numOfRows - colStat.getNumberOfValues());
          } else {
            nullCounts.put(fieldId, 0L);
          }
          valueCounts.put(fieldId, colStat.getNumberOfValues() + nullCounts.get(fieldId));

          if (metricsMode != MetricsModes.Counts.get()) {
            Optional<ByteBuffer> orcMin =
                (colStat.getNumberOfValues() > 0)
                    ? fromOrcMin(
                        icebergCol.type(), colStat, metricsMode, fieldMetricsMap.get(fieldId))
                    : Optional.empty();
            orcMin.ifPresent(byteBuffer -> lowerBounds.put(icebergCol.fieldId(), byteBuffer));
            Optional<ByteBuffer> orcMax =
                (colStat.getNumberOfValues() > 0)
                    ? fromOrcMax(
                        icebergCol.type(), colStat, metricsMode, fieldMetricsMap.get(fieldId))
                    : Optional.empty();
            orcMax.ifPresent(byteBuffer -> upperBounds.put(icebergCol.fieldId(), byteBuffer));
          }
        }
      }
    }

    return new Metrics(
        numOfRows,
        columnSizes,
        valueCounts,
        nullCounts,
        MetricsUtil.createNanValueCounts(
            fieldMetricsMap.values().stream(), effectiveMetricsConfig, schema),
        lowerBounds,
        upperBounds);
  }

  /**
   * 从 ORC 列统计提取下界，转为 Iceberg 二进制表示。
   *
   * <p>逻辑：按 ColumnStatistics 子类型分发（Integer/Double/String/Decimal/Date/Timestamp/Boolean）； Double
   * 优先用 FieldMetrics（排除 NaN），否则用 ORC 统计并 replaceNaN； 最后按 MetricsMode 截断字符串。
   */
  private static Optional<ByteBuffer> fromOrcMin(
      Type type,
      ColumnStatistics columnStats,
      MetricsMode metricsMode,
      FieldMetrics<?> fieldMetrics) {
    Object min = null;
    if (columnStats instanceof IntegerColumnStatistics) {
      min = ((IntegerColumnStatistics) columnStats).getMinimum();
      if (type.typeId() == Type.TypeID.INTEGER) {
        min = Math.toIntExact((long) min);
      }
    } else if (columnStats instanceof DoubleColumnStatistics) {
      if (fieldMetrics != null) {
        // since Orc includes NaN for upper/lower bounds of floating point columns, and we don't
        // want this behavior,
        // we have tracked metrics for such columns ourselves and thus do not need to rely on Orc's
        // column statistics.
        min = fieldMetrics.lowerBound();
      } else {
        // imported files will not have metrics that were tracked by Iceberg, so fall back to the
        // file's metrics.
        min =
            replaceNaN(
                ((DoubleColumnStatistics) columnStats).getMinimum(), Double.NEGATIVE_INFINITY);
        if (type.typeId() == Type.TypeID.FLOAT) {
          min = ((Double) min).floatValue();
        }
      }
    } else if (columnStats instanceof StringColumnStatistics) {
      min = ((StringColumnStatistics) columnStats).getMinimum();
    } else if (columnStats instanceof DecimalColumnStatistics) {
      min =
          Optional.ofNullable(((DecimalColumnStatistics) columnStats).getMinimum())
              .map(
                  minStats ->
                      minStats.bigDecimalValue().setScale(((Types.DecimalType) type).scale()))
              .orElse(null);
    } else if (columnStats instanceof DateColumnStatistics) {
      min = (int) ((DateColumnStatistics) columnStats).getMinimumDayOfEpoch();
    } else if (columnStats instanceof TimestampColumnStatistics) {
      TimestampColumnStatistics tColStats = (TimestampColumnStatistics) columnStats;
      Timestamp minValue = tColStats.getMinimumUTC();
      min =
          Optional.ofNullable(minValue)
              .map(v -> DateTimeUtil.microsFromInstant(v.toInstant()))
              .orElse(null);
    } else if (columnStats instanceof BooleanColumnStatistics) {
      BooleanColumnStatistics booleanStats = (BooleanColumnStatistics) columnStats;
      min = booleanStats.getFalseCount() <= 0;
    }

    return Optional.ofNullable(
        Conversions.toByteBuffer(type, truncateIfNeeded(Bound.LOWER, type, min, metricsMode)));
  }

  /**
   * 从 ORC 列统计提取上界，转为 Iceberg 二进制表示。
   *
   * <p>逻辑：同 {@link #fromOrcMin}，区别仅在取 maximum 和 upperBound。
   */
  private static Optional<ByteBuffer> fromOrcMax(
      Type type,
      ColumnStatistics columnStats,
      MetricsMode metricsMode,
      FieldMetrics<?> fieldMetrics) {
    Object max = null;
    if (columnStats instanceof IntegerColumnStatistics) {
      max = ((IntegerColumnStatistics) columnStats).getMaximum();
      if (type.typeId() == Type.TypeID.INTEGER) {
        max = Math.toIntExact((long) max);
      }
    } else if (columnStats instanceof DoubleColumnStatistics) {
      if (fieldMetrics != null) {
        // since Orc includes NaN for upper/lower bounds of floating point columns, and we don't
        // want this behavior,
        // we have tracked metrics for such columns ourselves and thus do not need to rely on Orc's
        // column statistics.
        max = fieldMetrics.upperBound();
      } else {
        // imported files will not have metrics that were tracked by Iceberg, so fall back to the
        // file's metrics.
        max =
            replaceNaN(
                ((DoubleColumnStatistics) columnStats).getMaximum(), Double.POSITIVE_INFINITY);
        if (type.typeId() == Type.TypeID.FLOAT) {
          max = ((Double) max).floatValue();
        }
      }
    } else if (columnStats instanceof StringColumnStatistics) {
      max = ((StringColumnStatistics) columnStats).getMaximum();
    } else if (columnStats instanceof DecimalColumnStatistics) {
      max =
          Optional.ofNullable(((DecimalColumnStatistics) columnStats).getMaximum())
              .map(
                  maxStats ->
                      maxStats.bigDecimalValue().setScale(((Types.DecimalType) type).scale()))
              .orElse(null);
    } else if (columnStats instanceof DateColumnStatistics) {
      max = (int) ((DateColumnStatistics) columnStats).getMaximumDayOfEpoch();
    } else if (columnStats instanceof TimestampColumnStatistics) {
      TimestampColumnStatistics tColStats = (TimestampColumnStatistics) columnStats;
      Timestamp maxValue = tColStats.getMaximumUTC();
      max =
          Optional.ofNullable(maxValue)
              .map(v -> DateTimeUtil.microsFromInstant(v.toInstant()))
              .orElse(null);
    } else if (columnStats instanceof BooleanColumnStatistics) {
      BooleanColumnStatistics booleanStats = (BooleanColumnStatistics) columnStats;
      max = booleanStats.getTrueCount() > 0;
    }
    return Optional.ofNullable(
        Conversions.toByteBuffer(type, truncateIfNeeded(Bound.UPPER, type, max, metricsMode)));
  }

  /** NaN 替换：导入文件的 ORC 统计可能含 NaN，用正/负无穷替换以排除 NaN。 */
  private static Object replaceNaN(double value, double replacement) {
    return Double.isNaN(value) ? replacement : value;
  }

  /**
   * 按 MetricsMode.Truncate 截断字符串上下界。
   *
   * <p>逻辑：仅对 STRING 类型且 Truncate 模式生效；UPPER 用 truncateStringMax（可能增长 1 字符）， LOWER 用
   * truncateStringMin。非字符串或非 Truncate 模式原样返回。
   */
  private static Object truncateIfNeeded(
      Bound bound, Type type, Object value, MetricsMode metricsMode) {
    // Out of the two types which could be truncated, string or binary, ORC only supports string
    // bounds.
    // Therefore, truncation will be applied if needed only on string type.
    if (!(metricsMode instanceof MetricsModes.Truncate)
        || type.typeId() != Type.TypeID.STRING
        || value == null) {
      return value;
    }

    CharSequence charSequence = (CharSequence) value;
    MetricsModes.Truncate truncateMode = (MetricsModes.Truncate) metricsMode;
    int truncateLength = truncateMode.length();

    switch (bound) {
      case UPPER:
        return Optional.ofNullable(
                UnicodeUtil.truncateStringMax(Literal.of(charSequence), truncateLength))
            .map(Literal::value)
            .orElse(charSequence);
      case LOWER:
        return UnicodeUtil.truncateStringMin(Literal.of(charSequence), truncateLength).value();
      default:
        throw new RuntimeException("No other bound is defined.");
    }
  }

  /** 用 StatsColumnsVisitor 收集所有有 Iceberg id 的叶子列 id 集合。 */
  private static Set<Integer> statsColumns(TypeDescription schema) {
    return OrcSchemaVisitor.visit(schema, new StatsColumnsVisitor());
  }

  /** 收集需要统计的列 id 集合的访问器：struct 汇总子字段并加上自身各子列 id。 */
  private static class StatsColumnsVisitor extends OrcSchemaVisitor<Set<Integer>> {
    @Override
    public Set<Integer> record(
        TypeDescription record, List<String> names, List<Set<Integer>> fields) {
      ImmutableSet.Builder<Integer> result = ImmutableSet.builder();
      fields.stream().filter(Objects::nonNull).forEach(result::addAll);
      record.getChildren().stream()
          .map(ORCSchemaUtil::icebergID)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .forEach(result::add);
      return result.build();
    }
  }
}
