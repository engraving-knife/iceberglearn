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
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Bound;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundExpressionVisitor;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.NaNUtil;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Dictionary;
import org.apache.parquet.column.page.DictionaryPage;
import org.apache.parquet.column.page.DictionaryPageReadStore;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;

/**
 * 文件级说明：基于 Parquet 字典的 row group 过滤器，利用列字典内容评估表达式是否可能命中。
 *
 * <p>所属模块：iceberg-parquet（读取侧 row group 裁剪，下推过滤优化）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对给定 row group，读取各列的字典页，把字典值解码为 Java 集合。
 *   <li>用 {@link BoundExpressionVisitor} 遍历已绑定表达式，对 eq/lt/gt/in/startsWith 等 谓词在字典集合上求值，判断该 row
 *       group 是否可能包含匹配行。
 *   <li>对含非字典页（fallback 到 plain 编码）的列，保守返回可能匹配。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>字典下推：当列值基数低且使用字典编码时，字典集合远小于全量数据，可在读取前 快速排除不匹配的 row group，减少 IO。
 *   <li>缓存与懒加载：dict() 方法按字段 ID 缓存解码后的字典集合，避免重复解码。
 *   <li>保守策略：isNull/notNull 因字典不含 null 无法判断，统一返回可能匹配； 含非字典页的列也无法确定，返回可能匹配。
 * </ul>
 *
 * <p>上下游关系：被 {@link ParquetReader} / 读取入口在读取 row group 前调用； 依赖 {@link DictionaryPageReadStore} 与
 * {@link ParquetConversions}。
 */
public class ParquetDictionaryRowGroupFilter {
  private final Schema schema;
  private final Expression expr;

  /**
   * 构造字典过滤器，默认大小写敏感。
   *
   * @param schema Iceberg schema
   * @param unbound 未绑定表达式
   */
  public ParquetDictionaryRowGroupFilter(Schema schema, Expression unbound) {
    this(schema, unbound, true);
  }

  /**
   * 构造字典过滤器。
   *
   * <p>逻辑：把未绑定表达式通过 {@link Binder#bind} 绑定到 schema，并重写 not 操作。
   *
   * @param schema Iceberg schema
   * @param unbound 未绑定表达式
   * @param caseSensitive 是否大小写敏感
   */
  public ParquetDictionaryRowGroupFilter(Schema schema, Expression unbound, boolean caseSensitive) {
    this.schema = schema;
    StructType struct = schema.asStruct();
    this.expr = Binder.bind(struct, Expressions.rewriteNot(unbound), caseSensitive);
  }

  /**
   * 判断 row group 的字典是否可能包含匹配表达式的记录。
   *
   * @param fileSchema Parquet 文件 schema
   * @param rowGroup row group 元数据
   * @param dictionaries 字典页读取存储
   * @return false 表示该 row group 不可能包含匹配行，可跳过；true 表示可能匹配
   */
  public boolean shouldRead(
      MessageType fileSchema, BlockMetaData rowGroup, DictionaryPageReadStore dictionaries) {
    return new EvalVisitor().eval(fileSchema, rowGroup, dictionaries);
  }

  private static final boolean ROWS_MIGHT_MATCH = true;
  private static final boolean ROWS_CANNOT_MATCH = false;

  /**
   * 字典评估访问者：在字典集合上对已绑定表达式求值。
   *
   * <p>设计意图：持有字典页存储与缓存（dictCache/isFallback/mayContainNulls），
   * 每个谓词方法先检查列是否含非字典页（fallback），若是则保守返回可能匹配； 否则在解码后的字典集合上判断是否存在满足谓词的值。
   */
  private class EvalVisitor extends BoundExpressionVisitor<Boolean> {
    private DictionaryPageReadStore dictionaries = null;
    private Map<Integer, Set<?>> dictCache = null;
    private Map<Integer, Boolean> isFallback = null;
    private Map<Integer, Boolean> mayContainNulls = null;
    private Map<Integer, ColumnDescriptor> cols = null;
    private Map<Integer, Function<Object, Object>> conversions = null;

    /**
     * 初始化评估上下文并求值表达式。
     *
     * <p>逻辑：保存字典存储；遍历文件 schema 的列建立 fieldId->ColumnDescriptor 与类型转换器映射； 遍历 rowGroup 列元数据建立
     * isFallback（是否含非字典页）与 mayContainNulls 映射； 最后用 ExpressionVisitors 求值表达式。
     *
     * @param fileSchema Parquet 文件 schema
     * @param rowGroup row group 元数据
     * @param dictionaryReadStore 字典页读取存储
     * @return true 表示可能匹配，false 表示不可能匹配
     */
    private boolean eval(
        MessageType fileSchema,
        BlockMetaData rowGroup,
        DictionaryPageReadStore dictionaryReadStore) {
      this.dictionaries = dictionaryReadStore;
      this.dictCache = Maps.newHashMap();
      this.isFallback = Maps.newHashMap();
      this.mayContainNulls = Maps.newHashMap();
      this.cols = Maps.newHashMap();
      this.conversions = Maps.newHashMap();

      for (ColumnDescriptor desc : fileSchema.getColumns()) {
        PrimitiveType colType = fileSchema.getType(desc.getPath()).asPrimitiveType();
        if (colType.getId() != null) {
          int id = colType.getId().intValue();
          Type icebergType = schema.findType(id);
          cols.put(id, desc);
          conversions.put(id, ParquetConversions.converterFromParquet(colType, icebergType));
        }
      }

      for (ColumnChunkMetaData meta : rowGroup.getColumns()) {
        PrimitiveType colType = fileSchema.getType(meta.getPath().toArray()).asPrimitiveType();
        if (colType.getId() != null) {
          int id = colType.getId().intValue();
          isFallback.put(id, ParquetUtil.hasNonDictionaryPages(meta));
          mayContainNulls.put(id, mayContainNull(meta));
        }
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
      return !result;
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
      // dictionaries only contain non-nulls and cannot eliminate based on isNull or NotNull
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notNull(BoundReference<T> ref) {
      // dictionaries only contain non-nulls and cannot eliminate based on isNull or NotNull
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean isNaN(BoundReference<T> ref) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, comparatorForNaNPredicate(ref));
      return dictionary.stream().anyMatch(NaNUtil::isNaN) ? ROWS_MIGHT_MATCH : ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean notNaN(BoundReference<T> ref) {
      int id = ref.fieldId();

      if (mayContainNulls.get(id)) {
        return ROWS_MIGHT_MATCH;
      }

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, comparatorForNaNPredicate(ref));
      return dictionary.stream().allMatch(NaNUtil::isNaN) ? ROWS_CANNOT_MATCH : ROWS_MIGHT_MATCH;
    }

    private <T> Comparator<T> comparatorForNaNPredicate(BoundReference<T> ref) {
      // Construct the same comparator as in ComparableLiteral.comparator, ignoring null value order
      // as dictionary
      // cannot contain null values.
      // No need to check type: incompatible types will be handled during expression binding.
      return Comparators.forType(ref.type().asPrimitiveType());
    }

    @Override
    public <T> Boolean lt(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, lit.comparator());

      // if any item in the dictionary matches the predicate, then at least one row does
      for (T item : dictionary) {
        int cmp = lit.comparator().compare(item, lit.value());
        if (cmp < 0) {
          return ROWS_MIGHT_MATCH;
        }
      }

      return ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean ltEq(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, lit.comparator());

      // if any item in the dictionary matches the predicate, then at least one row does
      for (T item : dictionary) {
        int cmp = lit.comparator().compare(item, lit.value());
        if (cmp <= 0) {
          return ROWS_MIGHT_MATCH;
        }
      }

      return ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean gt(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, lit.comparator());

      // if any item in the dictionary matches the predicate, then at least one row does
      for (T item : dictionary) {
        int cmp = lit.comparator().compare(item, lit.value());
        if (cmp > 0) {
          return ROWS_MIGHT_MATCH;
        }
      }

      return ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean gtEq(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, lit.comparator());

      // if any item in the dictionary matches the predicate, then at least one row does
      for (T item : dictionary) {
        int cmp = lit.comparator().compare(item, lit.value());
        if (cmp >= 0) {
          return ROWS_MIGHT_MATCH;
        }
      }

      return ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean eq(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, lit.comparator());

      return dictionary.contains(lit.value()) ? ROWS_MIGHT_MATCH : ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean notEq(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, lit.comparator());
      if (dictionary.size() > 1 || mayContainNulls.get(id)) {
        return ROWS_MIGHT_MATCH;
      }

      return dictionary.contains(lit.value()) ? ROWS_CANNOT_MATCH : ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean in(BoundReference<T> ref, Set<T> literalSet) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, ref.comparator());

      // we need to find out the smaller set to iterate through
      Set<T> smallerSet;
      Set<T> biggerSet;

      if (literalSet.size() < dictionary.size()) {
        smallerSet = literalSet;
        biggerSet = dictionary;
      } else {
        smallerSet = dictionary;
        biggerSet = literalSet;
      }

      for (T e : smallerSet) {
        if (biggerSet.contains(e)) {
          // value sets intersect so rows match
          return ROWS_MIGHT_MATCH;
        }
      }

      // value sets are disjoint so rows don't match
      return ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean notIn(BoundReference<T> ref, Set<T> literalSet) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, ref.comparator());
      if (dictionary.size() > literalSet.size() || mayContainNulls.get(id)) {
        return ROWS_MIGHT_MATCH;
      }

      // ROWS_CANNOT_MATCH if no values in the dictionary that are not also in the set (the
      // difference is empty)
      return Sets.difference(dictionary, literalSet).isEmpty()
          ? ROWS_CANNOT_MATCH
          : ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean startsWith(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, lit.comparator());
      for (T item : dictionary) {
        if (item.toString().startsWith(lit.value().toString())) {
          return ROWS_MIGHT_MATCH;
        }
      }

      return ROWS_CANNOT_MATCH;
    }

    @Override
    public <T> Boolean notStartsWith(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Boolean hasNonDictPage = isFallback.get(id);
      if (hasNonDictPage == null || hasNonDictPage) {
        return ROWS_MIGHT_MATCH;
      }

      Set<T> dictionary = dict(id, lit.comparator());
      for (T item : dictionary) {
        if (!item.toString().startsWith(lit.value().toString())) {
          return ROWS_MIGHT_MATCH;
        }
      }

      return ROWS_CANNOT_MATCH;
    }

    /**
     * 读取并解码指定字段的字典，缓存后返回。
     *
     * <p>逻辑：先查缓存；未命中则读取字典页，按编码初始化 Dictionary， 按物理类型逐个解码字典值并经类型转换器转为 Iceberg 类型，存入 TreeSet 后缓存。
     *
     * @param id 字段 ID
     * @param comparator 用于 TreeSet 排序的比较器
     * @param <T> 值类型
     * @return 解码后的字典值集合
     * @throws IllegalStateException 字典页不存在
     * @throws IllegalArgumentException 不支持的字典物理类型
     */
    @SuppressWarnings("unchecked")
    private <T> Set<T> dict(int id, Comparator<T> comparator) {
      Preconditions.checkNotNull(dictionaries, "Dictionary is required");

      Set<?> cached = dictCache.get(id);
      if (cached != null) {
        return (Set<T>) cached;
      }

      ColumnDescriptor col = cols.get(id);
      DictionaryPage page = dictionaries.readDictionaryPage(col);
      // may not be dictionary-encoded
      if (page == null) {
        throw new IllegalStateException("Failed to read required dictionary page for id: " + id);
      }

      Function<Object, Object> conversion = conversions.get(id);

      Dictionary dict;
      try {
        dict = page.getEncoding().initDictionary(col, page);
      } catch (IOException e) {
        throw new RuntimeIOException("Failed to create reader for dictionary page");
      }

      Set<T> dictSet = Sets.newTreeSet(comparator);

      for (int i = 0; i <= dict.getMaxId(); i++) {
        switch (col.getPrimitiveType().getPrimitiveTypeName()) {
          case FIXED_LEN_BYTE_ARRAY:
            dictSet.add((T) conversion.apply(dict.decodeToBinary(i)));
            break;
          case BINARY:
            dictSet.add((T) conversion.apply(dict.decodeToBinary(i)));
            break;
          case INT32:
            dictSet.add((T) conversion.apply(dict.decodeToInt(i)));
            break;
          case INT64:
            dictSet.add((T) conversion.apply(dict.decodeToLong(i)));
            break;
          case FLOAT:
            dictSet.add((T) conversion.apply(dict.decodeToFloat(i)));
            break;
          case DOUBLE:
            dictSet.add((T) conversion.apply(dict.decodeToDouble(i)));
            break;
          default:
            throw new IllegalArgumentException(
                "Cannot decode dictionary of type: "
                    + col.getPrimitiveType().getPrimitiveTypeName());
        }
      }

      dictCache.put(id, dictSet);

      return dictSet;
    }

    @Override
    public <T> Boolean handleNonReference(Bound<T> term) {
      return ROWS_MIGHT_MATCH;
    }
  }

  /**
   * 判断列块是否可能包含 null 值。
   *
   * <p>逻辑：统计为空或 numNulls != 0 时返回 true。
   *
   * @param meta 列块元数据
   * @return true 表示可能含 null
   */
  private static boolean mayContainNull(ColumnChunkMetaData meta) {
    return meta.getStatistics() == null || meta.getStatistics().getNumNulls() != 0;
  }
}
