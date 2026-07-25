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
 * Iceberg Spark 集成相关组件，扩展 Spark SQL 解析能力。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：接口 ExtendedParser。
 */
public interface ExtendedParser extends ParserInterface {
  /**
   * Iceberg Spark 集成相关组件。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 RawOrderField。
   */
  class RawOrderField {
    private final Term term;
    private final SortDirection direction;
    private final NullOrder nullOrder;

    /** 构造 RawOrderField 实例。 */
    public RawOrderField(Term term, SortDirection direction, NullOrder nullOrder) {
      this.term = term;
      this.direction = direction;
      this.nullOrder = nullOrder;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public Term term() {
      return term;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public SortDirection direction() {
      return direction;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public NullOrder nullOrder() {
      return nullOrder;
    }
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
  List<RawOrderField> parseSortOrder(String orderString) throws AnalysisException;
}
