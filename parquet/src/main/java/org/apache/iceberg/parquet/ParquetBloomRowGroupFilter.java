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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Bound;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundExpressionVisitor;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.DecimalUtil;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.parquet.column.values.bloomfilter.BloomFilter;
import org.apache.parquet.hadoop.BloomFilterReader;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;

/**
 * 文件级说明：基于 Parquet Bloom Filter 的行组（Row Group）过滤器。
 *
 * <p>所属模块：iceberg-parquet（Parquet 读取优化，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：利用 Parquet 文件中列级 Bloom Filter，在读取前判断某个行组是否可能包含 满足过滤条件的数据行，从而跳过不相关的行组，减少 IO。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Bloom Filter 只能做等值（eq）和 IN 判断：基于哈希，无法排除 lt/gt/notEq 等条件， 这些操作直接返回 ROWS_MIGHT_MATCH。
 *   <li>BoundExpressionVisitor：将过滤表达式绑定到 schema 后，用访问器模式遍历表达式树， 每个谓词返回布尔值（是否可能匹配）。
 *   <li>延迟加载 Bloom Filter：loadBloomFilter 按需读取并缓存，避免为不需要的列读取 Bloom。
 * </ul>
 *
 * <p>上下游关系：被 Parquet 读取流程在行组级别调用；依赖 BloomFilterReader（Parquet 提供）、 ExpressionVisitors（表达式遍历）。
 */
public class ParquetBloomRowGroupFilter {
  private final Schema schema;
  private final Expression expr;
  private final boolean caseSensitive;

  /**
   * 构造过滤器（大小写敏感）。
   *
   * @param schema Iceberg schema
   * @param unbound 未绑定的过滤表达式
   */
  public ParquetBloomRowGroupFilter(Schema schema, Expression unbound) {
    this(schema, unbound, true);
  }

  /**
   * 构造过滤器。
   *
   * <p>逻辑：将表达式通过 {@link Binder#bind} 绑定到 schema（含 rewriteNot 重写 NOT）， 使后续访问器能直接处理绑定后的引用。
   *
   * @param schema Iceberg schema
   * @param unbound 未绑定的过滤表达式
   * @param caseSensitive 字段名匹配是否大小写敏感
   */
  public ParquetBloomRowGroupFilter(Schema schema, Expression unbound, boolean caseSensitive) {
    this.schema = schema;
    StructType struct = schema.asStruct();
    this.expr = Binder.bind(struct, Expressions.rewriteNot(unbound), caseSensitive);
    this.caseSensitive = caseSensitive;
  }

  /**
   * 判断行组是否可能包含满足过滤条件的数据行。
   *
   * <p>逻辑：创建 BloomEvalVisitor 并委托 eval 方法遍历表达式树。
   *
   * @param fileSchema Parquet 文件 schema
   * @param rowGroup 行组元数据
   * @param bloomReader Bloom Filter 读取器
   * @return false 表示行组不可能匹配（可跳过），true 表示可能匹配
   */
  public boolean shouldRead(
      MessageType fileSchema, BlockMetaData rowGroup, BloomFilterReader bloomReader) {
    return new BloomEvalVisitor().eval(fileSchema, rowGroup, bloomReader);
  }

  private static final boolean ROWS_MIGHT_MATCH = true;
  private static final boolean ROWS_CANNOT_MATCH = false;

  /**
   * Bloom Filter 表达式评估访问器：遍历绑定后的表达式，对每个谓词利用 Bloom Filter 判断。
   *
   * <p>设计要点：eq/in 谓词查 Bloom Filter；lt/gt/notEq/notIn/startsWith 等基于哈希无法排除， 直接返回 ROWS_MIGHT_MATCH。
   */
  private class BloomEvalVisitor extends BoundExpressionVisitor<Boolean> {
    private BloomFilterReader bloomReader;
    private Set<Integer> fieldsWithBloomFilter = null;
    private Map<Integer, ColumnChunkMetaData> columnMetaMap = null;
    private Map<Integer, BloomFilter> bloomCache = null;
    private Map<Integer, PrimitiveType> parquetPrimitiveTypes = null;
    private Map<Integer, Type> types = null;

    /**
     * 评估表达式。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>遍历行组所有列元数据，收集有 Bloom Filter 的字段 ID 集合；
     *   <li>若过滤条件引用的列与 Bloom Filter 列无交集，提前返回 ROWS_MIGHT_MATCH；
     *   <li>否则委托 ExpressionVisitors.visitEvaluator 遍历表达式树。
     * </ol>
     */
    private boolean eval(
        MessageType fileSchema, BlockMetaData rowGroup, BloomFilterReader bloomFilterReader) {
      this.bloomReader = bloomFilterReader;
      this.fieldsWithBloomFilter = Sets.newHashSet();
      this.columnMetaMap = Maps.newHashMap();
      this.bloomCache = Maps.newHashMap();
      this.parquetPrimitiveTypes = Maps.newHashMap();
      this.types = Maps.newHashMap();

      for (ColumnChunkMetaData meta : rowGroup.getColumns()) {
        PrimitiveType colType = fileSchema.getType(meta.getPath().toArray()).asPrimitiveType();
        if (colType.getId() != null) {
          int id = colType.getId().intValue();
          Type icebergType = schema.findType(id);
          if (!ParquetUtil.hasNoBloomFilterPages(meta)) {
            fieldsWithBloomFilter.add(id);
          }
          columnMetaMap.put(id, meta);
          parquetPrimitiveTypes.put(id, colType);
          types.put(id, icebergType);
        }
      }

      Set<Integer> filterRefs =
          Binder.boundReferences(schema.asStruct(), ImmutableList.of(expr), caseSensitive);
      // If the filter's column set doesn't overlap with any bloom filter columns, exit early with
      // ROWS_MIGHT_MATCH
      if (filterRefs.size() > 0 && Sets.intersection(fieldsWithBloomFilter, filterRefs).isEmpty()) {
        return ROWS_MIGHT_MATCH;
      }

      return ExpressionVisitors.visitEvaluator(expr, this);
    }

    @Override
    public Boolean alwaysTrue() {
      return ROWS_MIGHT_MATCH; // all rows match
    }

    @Override
    public Boolean alwaysFalse() {
      return ROWS_CANNOT_MATCH; // all rows fail
    }

    @Override
    public Boolean not(Boolean result) {
      // not() should be rewritten by RewriteNot
      // bloom filter is based on hash and cannot eliminate based on not
      throw new UnsupportedOperationException("This path shouldn't be reached.");
    }

    @Override
    public Boolean and(Boolean leftResult, Boolean rightResult) {
      return leftResult && rightResult;
    }

    @Override
    public Boolean or(Boolean leftResult, Boolean rightResult) {
      return leftResult || rightResult;
    }

    @Override
    public <T> Boolean isNull(BoundReference<T> ref) {
      // bloom filter only contain non-nulls and cannot eliminate based on isNull or NotNull
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notNull(BoundReference<T> ref) {
      // bloom filter only contain non-nulls and cannot eliminate based on isNull or NotNull
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean isNaN(BoundReference<T> ref) {
      // bloom filter is based on hash and cannot eliminate based on isNaN or notNaN
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notNaN(BoundReference<T> ref) {
      // bloom filter is based on hash and cannot eliminate based on isNaN or notNaN
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean lt(BoundReference<T> ref, Literal<T> lit) {
      // bloom filter is based on hash and cannot eliminate based on lt or ltEq or gt or gtEq
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean ltEq(BoundReference<T> ref, Literal<T> lit) {
      // bloom filter is based on hash and cannot eliminate based on lt or ltEq or gt or gtEq
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean gt(BoundReference<T> ref, Literal<T> lit) {
      // bloom filter is based on hash and cannot eliminate based on lt or ltEq or gt or gtEq
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean gtEq(BoundReference<T> ref, Literal<T> lit) {
      // bloom filter is based on hash and cannot eliminate based on lt or ltEq or gt or gtEq
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean eq(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();
      if (!fieldsWithBloomFilter.contains(id)) { // no bloom filter
        return ROWS_MIGHT_MATCH;
      }

      BloomFilter bloom = loadBloomFilter(id);
      Type type = types.get(id);
      T value = lit.value();
      return shouldRead(parquetPrimitiveTypes.get(id), value, bloom, type);
    }

    @Override
    public <T> Boolean notEq(BoundReference<T> ref, Literal<T> lit) {
      // bloom filter is based on hash and cannot eliminate based on notEq
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean in(BoundReference<T> ref, Set<T> literalSet) {
      int id = ref.fieldId();
      if (!fieldsWithBloomFilter.contains(id)) { // no bloom filter
        return ROWS_MIGHT_MATCH;
      }
      BloomFilter bloom = loadBloomFilter(id);
      Type type = types.get(id);
      for (T e : literalSet) {
        if (shouldRead(parquetPrimitiveTypes.get(id), e, bloom, type)) {
          return ROWS_MIGHT_MATCH;
        }
      }
      return ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean notIn(BoundReference<T> ref, Set<T> literalSet) {
      // bloom filter is based on hash and cannot eliminate based on notIn
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean startsWith(BoundReference<T> ref, Literal<T> lit) {
      // bloom filter is based on hash and cannot eliminate based on startsWith
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notStartsWith(BoundReference<T> ref, Literal<T> lit) {
      // bloom filter is based on hash and cannot eliminate based on startsWith
      return ROWS_MIGHT_MATCH;
    }

    /** 按需加载并缓存指定字段 ID 的 Bloom Filter。 */
    private BloomFilter loadBloomFilter(int id) {
      if (bloomCache.containsKey(id)) {
        return bloomCache.get(id);
      } else {
        ColumnChunkMetaData columnChunkMetaData = columnMetaMap.get(id);
        BloomFilter bloomFilter = bloomReader.readBloomFilter(columnChunkMetaData);
        if (bloomFilter == null) {
          throw new IllegalStateException("Failed to read required bloom filter for id: " + id);
        } else {
          bloomCache.put(id, bloomFilter);
        }

        return bloomFilter;
      }
    }

    /**
     * 对单个值查询 Bloom Filter，判断是否可能存在。
     *
     * <p>逻辑：按 Parquet 原始类型和 Iceberg 类型计算哈希值并查询 Bloom Filter。 处理 INT32/INT64（含
     * decimal）、FLOAT/DOUBLE、BINARY（含 string/decimal/UUID）等类型。
     *
     * @param primitiveType Parquet 原始类型
     * @param value 待查询的值
     * @param bloom Bloom Filter 实例
     * @param type Iceberg 类型（用于类型分派）
     * @return true 表示可能存在，false 表示不存在
     */
    private <T> boolean shouldRead(
        PrimitiveType primitiveType, T value, BloomFilter bloom, Type type) {
      long hashValue = 0;
      switch (primitiveType.getPrimitiveTypeName()) {
        case INT32:
          switch (type.typeId()) {
            case DECIMAL:
              BigDecimal decimalValue = (BigDecimal) value;
              hashValue = bloom.hash(decimalValue.unscaledValue().intValue());
              return bloom.findHash(hashValue);
            case INTEGER:
            case DATE:
              hashValue = bloom.hash(((Number) value).intValue());
              return bloom.findHash(hashValue);
            default:
              return ROWS_MIGHT_MATCH;
          }
        case INT64:
          switch (type.typeId()) {
            case DECIMAL:
              BigDecimal decimalValue = (BigDecimal) value;
              hashValue = bloom.hash(decimalValue.unscaledValue().longValue());
              return bloom.findHash(hashValue);
            case LONG:
            case TIME:
            case TIMESTAMP:
              hashValue = bloom.hash(((Number) value).longValue());
              return bloom.findHash(hashValue);
            default:
              return ROWS_MIGHT_MATCH;
          }
        case FLOAT:
          hashValue = bloom.hash(((Number) value).floatValue());
          return bloom.findHash(hashValue);
        case DOUBLE:
          hashValue = bloom.hash(((Number) value).doubleValue());
          return bloom.findHash(hashValue);
        case FIXED_LEN_BYTE_ARRAY:
        case BINARY:
          switch (type.typeId()) {
            case STRING:
              hashValue = bloom.hash(Binary.fromCharSequence((CharSequence) value));
              return bloom.findHash(hashValue);
            case BINARY:
            case FIXED:
              hashValue = bloom.hash(Binary.fromConstantByteBuffer((ByteBuffer) value));
              return bloom.findHash(hashValue);
            case DECIMAL:
              DecimalLogicalTypeAnnotation metadata =
                  (DecimalLogicalTypeAnnotation) primitiveType.getLogicalTypeAnnotation();
              int scale = metadata.getScale();
              int precision = metadata.getPrecision();
              byte[] requiredBytes = new byte[TypeUtil.decimalRequiredBytes(precision)];
              byte[] binary =
                  DecimalUtil.toReusedFixLengthBytes(
                      precision, scale, (BigDecimal) value, requiredBytes);
              hashValue = bloom.hash(Binary.fromConstantByteArray(binary));
              return bloom.findHash(hashValue);
            case UUID:
              hashValue = bloom.hash(Binary.fromConstantByteArray(UUIDUtil.convert((UUID) value)));
              return bloom.findHash(hashValue);
            default:
              return ROWS_MIGHT_MATCH;
          }
        default:
          return ROWS_MIGHT_MATCH;
      }
    }

    @Override
    public <T> Boolean handleNonReference(Bound<T> term) {
      return ROWS_MIGHT_MATCH;
    }
  }
}
