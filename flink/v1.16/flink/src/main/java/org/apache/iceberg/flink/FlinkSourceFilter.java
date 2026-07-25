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
package org.apache.iceberg.flink;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.Evaluator;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.types.Types;

/**
 * Flink FilterFunction 实现，用 Iceberg {@link Evaluator} 在运行时对 RowData 求值过滤表达式。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：把 Flink {@link RowData} 包装为 Iceberg 内部表示， 然后调用 {@link
 * Evaluator#eval} 判断记录是否满足给定表达式。
 *
 * <p>设计意图：适配器模式——把 Iceberg Evaluator 适配为 Flink FilterFunction。 上下游：被 Flink legacy source
 * 算子调用，向用户提供行级过滤能力。
 */
public class FlinkSourceFilter implements FilterFunction<RowData> {

  private final RowType rowType;
  private final Evaluator evaluator;
  private final Types.StructType struct;
  private volatile RowDataWrapper wrapper;

  /** 构造过滤器，按 schema 创建 Iceberg Evaluator。 */
  public FlinkSourceFilter(Schema schema, Expression expr, boolean caseSensitive) {
    this.rowType = FlinkSchemaUtil.convert(schema);
    this.struct = schema.asStruct();
    this.evaluator = new Evaluator(struct, expr, caseSensitive);
  }

  /** 包装 RowData 后调用 Iceberg Evaluator 判断是否保留该行。 */
  @Override
  public boolean filter(RowData value) {
    if (wrapper == null) {
      this.wrapper = new RowDataWrapper(rowType, struct);
    }
    return evaluator.eval(wrapper.wrap(value));
  }
}
