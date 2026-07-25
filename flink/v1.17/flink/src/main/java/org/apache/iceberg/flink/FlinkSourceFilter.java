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
 * 基于 Iceberg 表达式对 Flink {@link RowData} 进行行级过滤的过滤器。
 *
 * <p>所属模块：iceberg-flink，实现 Flink {@link FilterFunction}，桥接 Iceberg {@link Evaluator}。
 *
 * <p>职责：将 RowData 包装为 Iceberg {@link StructLike} 后，用预编译的 {@link Evaluator} 计算表达式结果， 决定该行是否保留。
 *
 * <p>设计意图：{@code wrapper} 采用 volatile + 懒初始化（非同步双检），因为 RowDataWrapper 无状态可重用，
 * 即使多线程各创建一份也不影响正确性，仅在首次调用时付出一次构造开销。
 *
 * <p>上下游关系：被 Flink source 的过滤算子调用；下游依赖 {@link RowDataWrapper} 与 {@link Evaluator}。
 */
public class FlinkSourceFilter implements FilterFunction<RowData> {

  private final RowType rowType;
  private final Evaluator evaluator;
  private final Types.StructType struct;
  private volatile RowDataWrapper wrapper;

  /**
   * 构造过滤器。
   *
   * <p>逻辑：将 schema 转为 Flink RowType，并以 schema 的 struct 与表达式构造 Iceberg {@link Evaluator}。
   *
   * @param schema Iceberg 表 schema
   * @param expr 过滤表达式
   * @param caseSensitive 是否大小写敏感
   */
  public FlinkSourceFilter(Schema schema, Expression expr, boolean caseSensitive) {
    this.rowType = FlinkSchemaUtil.convert(schema);
    this.struct = schema.asStruct();
    this.evaluator = new Evaluator(struct, expr, caseSensitive);
  }

  /**
   * 判断单行是否满足过滤表达式。
   *
   * <p>逻辑：懒初始化 RowDataWrapper，将 value 包装后交由 evaluator 求值。
   *
   * @param value 待判断的行
   * @return 满足表达式返回 true
   */
  @Override
  public boolean filter(RowData value) {
    if (wrapper == null) {
      this.wrapper = new RowDataWrapper(rowType, struct);
    }
    return evaluator.eval(wrapper.wrap(value));
  }
}
