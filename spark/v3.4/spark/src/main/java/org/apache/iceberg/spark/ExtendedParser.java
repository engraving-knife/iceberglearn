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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：扩展解析器接口，在 Spark 原生解析器之上提供 Iceberg 专属 SQL 语句解析能力。
 *
 * <p>设计意图：以接口形式抽象扩展解析点，便于 Spark3Util 等组件调用 Iceberg 扩展语法。
 *
 * <p>上下游关系：由 Spark3Util 调用；实现为 IcebergSparkSqlExtensionsParser。
 */
public interface ExtendedParser extends ParserInterface {
  class RawOrderField {
    private final Term term;
    private final SortDirection direction;
    private final NullOrder nullOrder;

    public RawOrderField(Term term, SortDirection direction, NullOrder nullOrder) {
      this.term = term;
      this.direction = direction;
      this.nullOrder = nullOrder;
    }
    /** 执行 term 相关操作。 */
    public Term term() {
      return term;
    }
    /** 执行 direction 相关操作。 */
    public SortDirection direction() {
      return direction;
    }
    /** 执行 nullOrder 相关操作。 */
    public NullOrder nullOrder() {
      return nullOrder;
    }
  }
  /** 执行 parseSortOrder 相关操作。 */
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

  List<RawOrderField> parseSortOrder(String orderString) throws AnalysisException;
}
