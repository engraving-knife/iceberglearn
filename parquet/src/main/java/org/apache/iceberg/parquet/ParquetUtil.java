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
package org.apache.iceberg.parquet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.MetricsModes;
import org.apache.iceberg.MetricsModes.MetricsMode;
import org.apache.iceberg.MetricsUtil;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.BinaryUtil;
import org.apache.iceberg.util.UnicodeUtil;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Dictionary;
import org.apache.parquet.column.Encoding;
import org.apache.parquet.column.EncodingStats;
import org.apache.parquet.column.page.DictionaryPage;
import org.apache.parquet.column.page.PageReader;
import org.apache.parquet.column.statistics.Statistics;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ColumnPath;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.ParquetDecodingException;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;

/**
 * 文件级说明：Parquet 文件工具类，提供 metrics 提取、字典检测、Bloom Filter 检测等能力。
 *
 * <p>所属模块：iceberg-parquet（工具类，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 Parquet 文件 footer 提取 Iceberg {@link Metrics}（行数、列大小、值计数、null 计数、 上下界），支持截断（truncate）模式和
 *       NameMapping。
 *   <li>检测列是否包含非字典页（{@link #hasNonDictionaryPages}）、是否无 Bloom Filter 页。
 *   <li>读取字典页（{@link #readDictionary}）、提取 INT96 时间戳（{@link #extractTimestampInt96}）。
 *   <li>获取行组分裂偏移量（{@link #getSplitOffsets}）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>metrics 聚合：遍历所有行组的列元数据，按字段 ID 聚合统计值；若某行组缺统计则丢弃该列 的 null 计数和上下界（保守策略，避免不完整统计导致错误过滤）。
 *   <li>截断优化：对 STRING/BINARY 类型的上下界按 MetricsMode.Truncate 截断，减少存储开销。
 *   <li>INT96 兼容：extractTimestampInt96 将 Impala/Spark 旧版 INT96 时间戳转为微秒。
 * </ul>
 *
 * <p>上下游关系：被 iceberg-core 的 Parquet 写入器和 metrics 收集流程调用； 依赖
 * ParquetFileReader、ParquetSchemaUtil、ParquetConversions。
 */
public class ParquetUtil {
  // not meant to be instantiated
  private ParquetUtil() {}

  private static final long UNIX_EPOCH_JULIAN = 2_440_588L;

  /** 计算文件 metrics（不带 NameMapping）。 */
  public static Metrics fileMetrics(InputFile file, MetricsConfig metricsConfig) {
    return fileMetrics(file, metricsConfig, null);
  }

  /**
   * 计算文件 metrics：打开 Parquet 文件，从 footer 提取 metrics。
   *
   * @param file 输入文件
   * @param metricsConfig metrics 配置
   * @param nameMapping 字段名→ID 映射
   * @return 文件级 metrics
   */
  public static Metrics fileMetrics(
      InputFile file, MetricsConfig metricsConfig, NameMapping nameMapping) {
    try (ParquetFileReader reader = ParquetFileReader.open(ParquetIO.file(file))) {
      return footerMetrics(reader.getFooter(), Stream.empty(), metricsConfig, nameMapping);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to read footer of file: %s", file);
    }
  }

  public static Metrics footerMetrics(
      ParquetMetadata metadata, Stream<FieldMetrics<?>> fieldMetrics, MetricsConfig metricsConfig) {
    return footerMetrics(metadata, fieldMetrics, metricsConfig, null);
  }

  /**
   * 从 Parquet footer 计算 Iceberg metrics（核心方法）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>获取带 ID 的 Parquet schema（通过 hasIds/nameMapping/addFallbackIds）；
   *   <li>遍历所有行组的列元数据，按字段 ID 聚合 columnSizes/valueCounts/nullValueCounts；
   *   <li>对有统计的列提取上下界（通过 ParquetConversions 转为 Iceberg Literal）， 按 MetricsMode 截断；
   *   <li>若有 Iceberg 写入期 FieldMetrics（float/double），优先使用；
   *   <li>缺统计的列丢弃其 null 计数和上下界。
   * </ol>
   *
   * @param metadata Parquet 文件元数据
   * @param fieldMetrics Iceberg 写入期收集的字段 metrics
   * @param metricsConfig metrics 配置
   * @param nameMapping 字段名→ID 映射
   * @return 聚合后的文件级 metrics
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public static Metrics footerMetrics(
      ParquetMetadata metadata,
      Stream<FieldMetrics<?>> fieldMetrics,
      MetricsConfig metricsConfig,
      NameMapping nameMapping) {
    Preconditions.checkNotNull(fieldMetrics, "fieldMetrics should not be null");

    long rowCount = 0;
    Map<Integer, Long> columnSizes = Maps.newHashMap();
    Map<Integer, Long> valueCounts = Maps.newHashMap();
    Map<Integer, Long> nullValueCounts = Maps.newHashMap();
    Map<Integer, Literal<?>> lowerBounds = Maps.newHashMap();
    Map<Integer, Literal<?>> upperBounds = Maps.newHashMap();
    Set<Integer> missingStats = Sets.newHashSet();

    // ignore metrics for fields we failed to determine reliable IDs
    MessageType parquetTypeWithIds = getParquetTypeWithIds(metadata, nameMapping);
    Schema fileSchema = ParquetSchemaUtil.convertAndPrune(parquetTypeWithIds);

    Map<Integer, FieldMetrics<?>> fieldMetricsMap =
        fieldMetrics.collect(Collectors.toMap(FieldMetrics::id, Function.identity()));

    List<BlockMetaData> blocks = metadata.getBlocks();
    for (BlockMetaData block : blocks) {
      rowCount += block.getRowCount();
      for (ColumnChunkMetaData column : block.getColumns()) {

        Integer fieldId = fileSchema.aliasToId(column.getPath().toDotString());
        if (fieldId == null) {
          // fileSchema may contain a subset of columns present in the file
          // as we prune columns we could not assign ids
          continue;
        }

        increment(columnSizes, fieldId, column.getTotalSize());

        MetricsMode metricsMode = MetricsUtil.metricsMode(fileSchema, metricsConfig, fieldId);
        if (metricsMode == MetricsModes.None.get()) {
          continue;
        }
        increment(valueCounts, fieldId, column.getValueCount());

        Statistics stats = column.getStatistics();
        if (stats != null && !stats.isEmpty()) {
          increment(nullValueCounts, fieldId, stats.getNumNulls());

          // when there are metrics gathered by Iceberg for a column, we should use those instead
          // of the ones from Parquet
          if (metricsMode != MetricsModes.Counts.get() && !fieldMetricsMap.containsKey(fieldId)) {
            Types.NestedField field = fileSchema.findField(fieldId);
            if (field != null && stats.hasNonNullValue() && shouldStoreBounds(column, fileSchema)) {
              Literal<?> min =
                  ParquetConversions.fromParquetPrimitive(
                      field.type(), column.getPrimitiveType(), stats.genericGetMin());
              updateMin(lowerBounds, fieldId, field.type(), min, metricsMode);
              Literal<?> max =
                  ParquetConversions.fromParquetPrimitive(
                      field.type(), column.getPrimitiveType(), stats.genericGetMax());
              updateMax(upperBounds, fieldId, field.type(), max, metricsMode);
            }
          }
        } else {
          missingStats.add(fieldId);
        }
      }
    }

    // discard accumulated values if any stats were missing
    for (Integer fieldId : missingStats) {
      nullValueCounts.remove(fieldId);
      lowerBounds.remove(fieldId);
      upperBounds.remove(fieldId);
    }

    updateFromFieldMetrics(fieldMetricsMap, metricsConfig, fileSchema, lowerBounds, upperBounds);

    return new Metrics(
        rowCount,
        columnSizes,
        valueCounts,
        nullValueCounts,
        MetricsUtil.createNanValueCounts(
            fieldMetricsMap.values().stream(), metricsConfig, fileSchema),
        toBufferMap(fileSchema, lowerBounds),
        toBufferMap(fileSchema, upperBounds));
  }

  private static void updateFromFieldMetrics(
      Map<Integer, FieldMetrics<?>> idToFieldMetricsMap,
      MetricsConfig metricsConfig,
      Schema schema,
      Map<Integer, Literal<?>> lowerBounds,
      Map<Integer, Literal<?>> upperBounds) {
    idToFieldMetricsMap
        .entrySet()
        .forEach(
            entry -> {
              int fieldId = entry.getKey();
              FieldMetrics<?> metrics = entry.getValue();
              MetricsMode metricsMode = MetricsUtil.metricsMode(schema, metricsConfig, fieldId);

              // only check for MetricsModes.None, since we don't truncate float/double values.
              if (metricsMode != MetricsModes.None.get()) {
                if (!metrics.hasBounds()) {
                  lowerBounds.remove(fieldId);
                  upperBounds.remove(fieldId);
                } else if (metrics.upperBound() instanceof Float) {
                  lowerBounds.put(fieldId, Literal.of((Float) metrics.lowerBound()));
                  upperBounds.put(fieldId, Literal.of((Float) metrics.upperBound()));
                } else if (metrics.upperBound() instanceof Double) {
                  lowerBounds.put(fieldId, Literal.of((Double) metrics.lowerBound()));
                  upperBounds.put(fieldId, Literal.of((Double) metrics.upperBound()));
                } else {
                  throw new UnsupportedOperationException(
                      "Expected only float or double column metrics");
                }
              }
            });
  }

  private static MessageType getParquetTypeWithIds(
      ParquetMetadata metadata, NameMapping nameMapping) {
    MessageType type = metadata.getFileMetaData().getSchema();

    if (ParquetSchemaUtil.hasIds(type)) {
      return type;
    }

    if (nameMapping != null) {
      return ParquetSchemaUtil.applyNameMapping(type, nameMapping);
    }

    return ParquetSchemaUtil.addFallbackIds(type);
  }

  /**
   * 获取行组起始偏移量列表（升序排列），用于文件分裂。
   *
   * @param md Parquet 元数据
   * @return 行组起始偏移量列表
   */
  public static List<Long> getSplitOffsets(ParquetMetadata md) {
    List<Long> splitOffsets = Lists.newArrayListWithExpectedSize(md.getBlocks().size());
    for (BlockMetaData blockMetaData : md.getBlocks()) {
      splitOffsets.add(blockMetaData.getStartingPos());
    }
    Collections.sort(splitOffsets);
    return splitOffsets;
  }

  // we allow struct nesting, but not maps or arrays
  private static boolean shouldStoreBounds(ColumnChunkMetaData column, Schema schema) {
    if (column.getPrimitiveType().getPrimitiveTypeName() == PrimitiveType.PrimitiveTypeName.INT96) {
      // stats for INT96 are not reliable
      return false;
    }

    ColumnPath columnPath = column.getPath();
    Iterator<String> pathIterator = columnPath.iterator();
    Type currentType = schema.asStruct();

    while (pathIterator.hasNext()) {
      if (currentType == null || !currentType.isStructType()) {
        return false;
      }
      String fieldName = pathIterator.next();
      currentType = currentType.asStructType().fieldType(fieldName);
    }

    return currentType != null && currentType.isPrimitiveType();
  }

  private static void increment(Map<Integer, Long> columns, int fieldId, long amount) {
    if (columns != null) {
      if (columns.containsKey(fieldId)) {
        columns.put(fieldId, columns.get(fieldId) + amount);
      } else {
        columns.put(fieldId, amount);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> void updateMin(
      Map<Integer, Literal<?>> lowerBounds,
      int id,
      Type type,
      Literal<T> min,
      MetricsMode metricsMode) {
    Literal<T> currentMin = (Literal<T>) lowerBounds.get(id);
    if (currentMin == null || min.comparator().compare(min.value(), currentMin.value()) < 0) {
      if (metricsMode == MetricsModes.Full.get()) {
        lowerBounds.put(id, min);
      } else {
        MetricsModes.Truncate truncateMode = (MetricsModes.Truncate) metricsMode;
        int truncateLength = truncateMode.length();
        switch (type.typeId()) {
          case STRING:
            lowerBounds.put(
                id, UnicodeUtil.truncateStringMin((Literal<CharSequence>) min, truncateLength));
            break;
          case FIXED:
          case BINARY:
            lowerBounds.put(
                id, BinaryUtil.truncateBinaryMin((Literal<ByteBuffer>) min, truncateLength));
            break;
          default:
            lowerBounds.put(id, min);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> void updateMax(
      Map<Integer, Literal<?>> upperBounds,
      int id,
      Type type,
      Literal<T> max,
      MetricsMode metricsMode) {
    Literal<T> currentMax = (Literal<T>) upperBounds.get(id);
    if (currentMax == null || max.comparator().compare(max.value(), currentMax.value()) > 0) {
      if (metricsMode == MetricsModes.Full.get()) {
        upperBounds.put(id, max);
      } else {
        MetricsModes.Truncate truncateMode = (MetricsModes.Truncate) metricsMode;
        int truncateLength = truncateMode.length();
        switch (type.typeId()) {
          case STRING:
            Literal<CharSequence> truncatedMaxString =
                UnicodeUtil.truncateStringMax((Literal<CharSequence>) max, truncateLength);
            if (truncatedMaxString != null) {
              upperBounds.put(id, truncatedMaxString);
            }
            break;
          case FIXED:
          case BINARY:
            Literal<ByteBuffer> truncatedMaxBinary =
                BinaryUtil.truncateBinaryMax((Literal<ByteBuffer>) max, truncateLength);
            if (truncatedMaxBinary != null) {
              upperBounds.put(id, truncatedMaxBinary);
            }
            break;
          default:
            upperBounds.put(id, max);
        }
      }
    }
  }

  private static Map<Integer, ByteBuffer> toBufferMap(Schema schema, Map<Integer, Literal<?>> map) {
    Map<Integer, ByteBuffer> bufferMap = Maps.newHashMap();
    for (Map.Entry<Integer, Literal<?>> entry : map.entrySet()) {
      bufferMap.put(
          entry.getKey(),
          Conversions.toByteBuffer(schema.findType(entry.getKey()), entry.getValue().value()));
    }
    return bufferMap;
  }

  /**
   * 检测列是否包含非字典编码的页（用于判断是否适合字典过滤）。
   *
   * <p>逻辑：优先使用 EncodingStats；若不可用，回退到检查编码列表中是否除 PLAIN_DICTIONARY/RLE/BIT_PACKED 外还有其他编码。
   *
   * @param meta 列块元数据
   * @return true 表示存在非字典页
   */
  @SuppressWarnings("deprecation")
  public static boolean hasNonDictionaryPages(ColumnChunkMetaData meta) {
    EncodingStats stats = meta.getEncodingStats();
    if (stats != null) {
      return stats.hasNonDictionaryEncodedPages();
    }

    // without EncodingStats, fall back to testing the encoding list
    Set<Encoding> encodings = Sets.newHashSet(meta.getEncodings());
    if (encodings.remove(Encoding.PLAIN_DICTIONARY)) {
      // if remove returned true, PLAIN_DICTIONARY was present, which means at
      // least one page was dictionary encoded and 1.0 encodings are used

      // RLE and BIT_PACKED are only used for repetition or definition levels
      encodings.remove(Encoding.RLE);
      encodings.remove(Encoding.BIT_PACKED);

      // when empty, no encodings other than dictionary or rep/def levels
      return !encodings.isEmpty();
    } else {
      // if PLAIN_DICTIONARY wasn't present, then either the column is not
      // dictionary-encoded, or the 2.0 encoding, RLE_DICTIONARY, was used.
      // for 2.0, this cannot determine whether a page fell back without
      // page encoding stats
      return true;
    }
  }

  /** 检测列是否完全没有 Bloom Filter 页（offset ≤ 0 表示无）。 */
  public static boolean hasNoBloomFilterPages(ColumnChunkMetaData meta) {
    return meta.getBloomFilterOffset() <= 0;
  }

  /**
   * 读取字典页并初始化字典。
   *
   * @param desc 列描述符
   * @param pageSource 页面读取器
   * @return Dictionary 实例，若无字典页则返回 null
   * @throws ParquetDecodingException 若解码失败
   */
  public static Dictionary readDictionary(ColumnDescriptor desc, PageReader pageSource) {
    DictionaryPage dictionaryPage = pageSource.readDictionaryPage();
    if (dictionaryPage != null) {
      try {
        return dictionaryPage.getEncoding().initDictionary(desc, dictionaryPage);
      } catch (IOException e) {
        throw new ParquetDecodingException("could not decode the dictionary for " + desc, e);
      }
    }
    return null;
  }

  /**
   * 判断 Parquet 原始类型是否为整数类型（INT_8/INT_16/INT_32/DATE 逻辑类型，或 INT32 原始类型）。
   *
   * @param primitiveType Parquet 原始类型
   * @return true 表示整数类型
   */
  public static boolean isIntType(PrimitiveType primitiveType) {
    if (primitiveType.getOriginalType() != null) {
      switch (primitiveType.getOriginalType()) {
        case INT_8:
        case INT_16:
        case INT_32:
        case DATE:
          return true;
        default:
          return false;
      }
    }
    return primitiveType.getPrimitiveTypeName() == PrimitiveType.PrimitiveTypeName.INT32;
  }

  /**
   * 从 ByteBuffer 读取 INT96 时间戳并转为微秒。
   *
   * <p>逻辑：读取 8 字节纳秒（一天内时间）+ 4 字节 Julian Day， 转换为自 Unix Epoch 以来的微秒数。
   *
   * @param buffer 包含 12 字节 INT96 的 ByteBuffer
   * @return 自 Unix Epoch 以来的微秒数
   */
  public static long extractTimestampInt96(ByteBuffer buffer) {
    // 8 bytes (time of day nanos)
    long timeOfDayNanos = buffer.getLong();
    // 4 bytes(julianDay)
    int julianDay = buffer.getInt();
    return TimeUnit.DAYS.toMicros(julianDay - UNIX_EPOCH_JULIAN)
        + TimeUnit.NANOSECONDS.toMicros(timeOfDayNanos);
  }
}
