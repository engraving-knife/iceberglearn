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
package org.apache.iceberg.mr.hive;

import static org.apache.iceberg.expressions.Expressions.and;
import static org.apache.iceberg.expressions.Expressions.equal;
import static org.apache.iceberg.expressions.Expressions.greaterThanOrEqual;
import static org.apache.iceberg.expressions.Expressions.in;
import static org.apache.iceberg.expressions.Expressions.isNaN;
import static org.apache.iceberg.expressions.Expressions.isNull;
import static org.apache.iceberg.expressions.Expressions.lessThan;
import static org.apache.iceberg.expressions.Expressions.lessThanOrEqual;
import static org.apache.iceberg.expressions.Expressions.not;
import static org.apache.iceberg.expressions.Expressions.or;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.hadoop.hive.ql.io.sarg.ExpressionTree;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentImpl;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.iceberg.common.DynFields;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.NaNUtil;

/**
 * 文件级说明：Hive SearchArgument 过滤条件 -> Iceberg Expression 转换器。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，负责把 Hive 下推的 谓词转换为 Iceberg 表达式，供 InputFormat
 * 做数据跳过 / 文件裁剪）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>解析 Hive {@link SearchArgument} 表达式树，递归翻译为 Iceberg {@link Expression}。
 *   <li>把 Hive {@link PredicateLeaf} 的各类操作符与字面量映射为 Iceberg 对应表达式与值， 并处理日期/时间戳时区与精度丢失等兼容性问题。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Hive 谓词下推以 {@link SearchArgument} 形式到达，Iceberg 自身有独立的 {@link Expression} 体系；本类做语义等价转换，使
 *       Iceberg 能利用文件统计信息裁剪数据。
 *   <li>对 NaN 特殊处理：当 EQUALS 字面量为 NaN 时，Iceberg 用 isNaN 表达式（因为 NaN != NaN）。
 *   <li>日期/时间戳：Hive 内部使用 java.util.Date 走默认时区，会丢失微秒；这里通过反射读取 PredicateLeafImpl 的 literal 字段（绕过 Kryo
 *       反序列化的 Date->Timestamp 转换）， 再用 LocalDateTime 等方式还原，避免时区与精度问题。
 * </ul>
 *
 * <p>上下游关系：上游被 HiveIcebergInputFormat / HiveIcebergStorageHandler 调用以应用 Hive 谓词； 下游依赖 iceberg-core
 * 的 {@link Expressions}、{@link DateTimeUtil}、{@link NaNUtil}。
 */
public class HiveIcebergFilterFactory {

  private HiveIcebergFilterFactory() {}

  /**
   * 将 Hive {@link SearchArgument} 转换为 Iceberg {@link Expression}。
   *
   * @param sarg Hive 搜索参数对象
   * @return 等价的 Iceberg 表达式
   */
  public static Expression generateFilterExpression(SearchArgument sarg) {
    return translate(sarg.getExpression(), sarg.getLeaves());
  }

  /**
   * 递归遍历 Hive 表达式树并翻译为 Iceberg 表达式。
   *
   * <p>逻辑：根据当前节点的 operator 分支处理：
   *
   * <ul>
   *   <li>OR：所有子节点结果用 {@link Expressions#or} 合并，初始为 alwaysFalse。
   *   <li>AND：所有子节点结果用 {@link Expressions#and} 合并，初始为 alwaysTrue。
   *   <li>NOT：对唯一子节点结果取反。
   *   <li>LEAF：根据 leaf 索引取出 {@link PredicateLeaf}，委托给 {@link #translateLeaf}。
   *   <li>CONSTANT：不支持，抛出异常。
   * </ul>
   *
   * @param tree 当前要翻译的表达式树节点
   * @param leaves 树中所有叶子节点列表
   * @return 翻译后的 Iceberg 表达式
   */
  private static Expression translate(ExpressionTree tree, List<PredicateLeaf> leaves) {
    List<ExpressionTree> childNodes = tree.getChildren();
    switch (tree.getOperator()) {
      case OR:
        Expression orResult = Expressions.alwaysFalse();
        for (ExpressionTree child : childNodes) {
          orResult = or(orResult, translate(child, leaves));
        }
        return orResult;
      case AND:
        Expression result = Expressions.alwaysTrue();
        for (ExpressionTree child : childNodes) {
          result = and(result, translate(child, leaves));
        }
        return result;
      case NOT:
        return not(translate(childNodes.get(0), leaves));
      case LEAF:
        if (tree.getLeaf() >= leaves.size()) {
          throw new UnsupportedOperationException("No more leaves are available");
        }
        return translateLeaf(leaves.get(tree.getLeaf()));
      case CONSTANT:
        throw new UnsupportedOperationException("CONSTANT operator is not supported");
      default:
        throw new UnsupportedOperationException("Unknown operator: " + tree.getOperator());
    }
  }

  /**
   * 把单个 Hive 叶子谓词翻译为 Iceberg 表达式。
   *
   * <p>逻辑：按 PredicateLeaf.operator 分支：EQUALS（含 NaN 特判）、LESS_THAN、 LESS_THAN_EQUALS、IN、BETWEEN（拆成 >=
   * 下界 AND <= 上界）、IS_NULL。
   *
   * @param leaf Hive 叶子谓词
   * @return 等价的 Iceberg 表达式
   */
  private static Expression translateLeaf(PredicateLeaf leaf) {
    String column = leaf.getColumnName();
    switch (leaf.getOperator()) {
      case EQUALS:
        Object literal = leafToLiteral(leaf);
        return NaNUtil.isNaN(literal) ? isNaN(column) : equal(column, literal);
      case LESS_THAN:
        return lessThan(column, leafToLiteral(leaf));
      case LESS_THAN_EQUALS:
        return lessThanOrEqual(column, leafToLiteral(leaf));
      case IN:
        return in(column, leafToLiteralList(leaf));
      case BETWEEN:
        List<Object> icebergLiterals = leafToLiteralList(leaf);
        if (icebergLiterals.size() < 2) {
          throw new UnsupportedOperationException("Missing leaf literals: " + leaf);
        }
        return and(
            greaterThanOrEqual(column, icebergLiterals.get(0)),
            lessThanOrEqual(column, icebergLiterals.get(1)));
      case IS_NULL:
        return isNull(column);
      default:
        throw new UnsupportedOperationException("Unknown operator: " + leaf.getOperator());
    }
  }

  // PredicateLeafImpl has a work-around for Kryo serialization with java.util.Date objects where it
  // converts values to
  // Timestamp using Date#getTime. This conversion discards microseconds, so this is a necessary to
  // avoid it.
  private static final DynFields.UnboundField<?> LITERAL_FIELD =
      DynFields.builder().hiddenImpl(SearchArgumentImpl.PredicateLeafImpl.class, "literal").build();

  /**
   * 将 Hive 叶子谓词的字面量转换为 Iceberg 期望的 Java 对象。
   *
   * <p>逻辑：按类型分支：
   *
   * <ul>
   *   <li>LONG/BOOLEAN/STRING/FLOAT：直接返回。
   *   <li>DATE：若字面量是 {@link Date} 走 {@link #daysFromDate}；否则按 {@link Timestamp} 走 {@link
   *       #daysFromTimestamp}（Hive Kryo 把 Date 转 Timestamp 时会丢微秒）。
   *   <li>TIMESTAMP：通过反射读取原始 literal 字段，再走 {@link #microsFromTimestamp}。
   *   <li>DECIMAL：将 {@link HiveDecimalWritable} 转为 {@link BigDecimal} 并保留 scale。
   * </ul>
   *
   * @param leaf Hive 叶子谓词
   * @return 转换后的字面量值
   */
  private static Object leafToLiteral(PredicateLeaf leaf) {
    switch (leaf.getType()) {
      case LONG:
      case BOOLEAN:
      case STRING:
      case FLOAT:
        return leaf.getLiteral();
      case DATE:
        if (leaf.getLiteral() instanceof Date) {
          return daysFromDate((Date) leaf.getLiteral());
        }
        return daysFromTimestamp((Timestamp) leaf.getLiteral());
      case TIMESTAMP:
        return microsFromTimestamp((Timestamp) LITERAL_FIELD.get(leaf));
      case DECIMAL:
        return hiveDecimalToBigDecimal((HiveDecimalWritable) leaf.getLiteral());

      default:
        throw new UnsupportedOperationException("Unknown type: " + leaf.getType());
    }
  }

  /**
   * 将 Hive 叶子谓词的字面量列表转换为 Iceberg 期望的对象列表（IN / BETWEEN 用）。
   *
   * <p>逻辑：按类型分支处理字面量列表，TIMESTAMP/DATE/DECIMAL 需做与 {@link #leafToLiteral} 相同的转换。
   *
   * @param leaf Hive 叶子谓词
   * @return 转换后的字面量列表
   */
  private static List<Object> leafToLiteralList(PredicateLeaf leaf) {
    switch (leaf.getType()) {
      case LONG:
      case BOOLEAN:
      case FLOAT:
      case STRING:
        return leaf.getLiteralList();
      case DATE:
        return leaf.getLiteralList().stream()
            .map(value -> daysFromDate((Date) value))
            .collect(Collectors.toList());
      case DECIMAL:
        return leaf.getLiteralList().stream()
            .map(value -> hiveDecimalToBigDecimal((HiveDecimalWritable) value))
            .collect(Collectors.toList());
      case TIMESTAMP:
        return leaf.getLiteralList().stream()
            .map(value -> microsFromTimestamp((Timestamp) value))
            .collect(Collectors.toList());
      default:
        throw new UnsupportedOperationException("Unknown type: " + leaf.getType());
    }
  }

  /**
   * 将 Hive {@link HiveDecimalWritable} 转为 {@link BigDecimal}，并保留原始 scale。
   *
   * @param hiveDecimalWritable Hive decimal 可写对象
   * @return 等价的 BigDecimal（带正确 scale）
   */
  private static BigDecimal hiveDecimalToBigDecimal(HiveDecimalWritable hiveDecimalWritable) {
    return hiveDecimalWritable
        .getHiveDecimal()
        .bigDecimalValue()
        .setScale(hiveDecimalWritable.scale());
  }

  // Hive uses `java.sql.Date.valueOf(lit.toString());` to convert a literal to Date
  // Which uses `java.util.Date()` internally to create the object and that uses the
  // TimeZone.getDefaultRef()
  // To get back the expected date we have to use the LocalDate which gets rid of the TimeZone
  // misery as it uses
  // the year/month/day to generate the object
  private static int daysFromDate(Date date) {
    return DateTimeUtil.daysFromDate(date.toLocalDate());
  }

  // Hive uses `java.sql.Timestamp.valueOf(lit.toString());` to convert a literal to Timestamp
  // Which again uses `java.util.Date()` internally to create the object which uses the
  // TimeZone.getDefaultRef()
  // To get back the expected timestamp we have to use the LocalDateTime which gets rid of the
  // TimeZone misery
  // as it uses the year/month/day/hour/min/sec/nanos to generate the object
  private static int daysFromTimestamp(Timestamp timestamp) {
    return DateTimeUtil.daysFromDate(timestamp.toLocalDateTime().toLocalDate());
  }

  // We have to use the LocalDateTime to get the micros. See the comment above.
  private static long microsFromTimestamp(Timestamp timestamp) {
    return DateTimeUtil.microsFromTimestamp(timestamp.toLocalDateTime());
  }
}
