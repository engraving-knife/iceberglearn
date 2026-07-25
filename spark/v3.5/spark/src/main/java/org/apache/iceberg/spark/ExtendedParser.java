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
package org.apache.iceberg.spark;

import java.util.List;
import org.apache.iceberg.NullOrder;
import org.apache.iceberg.SortDirection;
import org.apache.iceberg.expressions.Term;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.parser.ParserInterface;

/**
 * Iceberg 扩展的 SQL 解析器接口。
 *
 * <p>所属模块：iceberg-spark。扩展 Spark {@link ParserInterface}，在原生解析能力之上提供 Iceberg 特有的排序规则（sort
 * order）字符串解析能力，用于把表属性中以字符串形式存储的 排序规则解析为结构化对象。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.spark.Spark3Util} 等通过 {@link #parseSortOrder} 静态方法调用；实际实现由
 * Iceberg 扩展解析器（IcebergSparkSqlExtensionsParser）提供。
 */
public interface ExtendedParser extends ParserInterface {
  /**
   * 排序字段原始表示，携带 Iceberg {@link Term}、排序方向与空值顺序。
   *
   * <p>所属模块：iceberg-spark。作为 {@link #parseSortOrder} 的返回元素，是解析结果与 后续构建 Iceberg 排序描述符之间的中间结构。
   */
  class RawOrderField {
    private final Term term;
    private final SortDirection direction;
    private final NullOrder nullOrder;

    /** 以 term、方向、空值顺序构造。 */
    public RawOrderField(Term term, SortDirection direction, NullOrder nullOrder) {
      this.term = term;
      this.direction = direction;
      this.nullOrder = nullOrder;
    }

    /** 返回排序项。 */
    public Term term() {
      return term;
    }

    /** 返回排序方向。 */
    public SortDirection direction() {
      return direction;
    }

    /** 返回空值顺序。 */
    public NullOrder nullOrder() {
      return nullOrder;
    }
  }

  /**
   * 将排序规则字符串解析为 {@link RawOrderField} 列表。
   *
   * <p>逻辑：取 Spark 会话的解析器，若为 {@link ExtendedParser} 则委托其解析；解析异常包装为 IllegalArgumentException；解析器非
   * Iceberg 扩展时抛出 IllegalStateException。
   */
  static List<RawOrderField> parseSortOrder(SparkSession spark, String orderString) {
    if (spark.sessionState().sqlParser() instanceof ExtendedParser) {
      ExtendedParser parser = (ExtendedParser) spark.sessionState().sqlParser();
      try {
        return parser.parseSortOrder(orderString);
      } catch (AnalysisException e) {
        throw new IllegalArgumentException(
            String.format("Unable to parse sortOrder: %s", orderString), e);
      }
    } else {
      throw new IllegalStateException(
          "Cannot parse order: parser is not an Iceberg ExtendedParser");
    }
  }

  /** 解析排序规则字符串，由扩展解析器实现。 */
  List<RawOrderField> parseSortOrder(String orderString) throws AnalysisException;
}
