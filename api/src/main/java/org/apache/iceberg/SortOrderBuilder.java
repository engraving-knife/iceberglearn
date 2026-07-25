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
package org.apache.iceberg;

import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Term;

/**
 * 文件级说明：排序序号构建器接口，提供添加排序字段的方法。
 *
 * <p>所属模块：iceberg-api（核心接口层）。
 *
 * <p>职责：为排序序号（{@link SortOrder}）的构建提供链式 API，支持按字段名或表达式项 （{@link
 * Term}）添加升序（asc）或降序（desc）排序字段，并可指定空值排序位置。
 *
 * <p>设计意图：通过方法重载提供便捷入口——按字段名可自动转换为 {@link Term}，asc/desc 各自 有默认空值位置（asc 默认 NULLS_FIRST，desc 默认
 * NULLS_LAST），减少调用方样板代码。 泛型参数 {@code <R>} 允许返回具体构建器类型以支持链式调用。
 *
 * <p>上下游关系：由 {@link SortOrder#builderFor(Schema)} 等返回； 被 {@link ReplaceSortOrder} 等更新 API 使用。
 *
 * @param <R> 链式调用的返回类型
 */
public interface SortOrderBuilder<R> {

  /**
   * 按字段名添加升序排序字段，空值排在最前（NULLS_FIRST）。
   *
   * @param name 字段名
   * @return this，便于链式调用
   */
  default R asc(String name) {
    return asc(Expressions.ref(name), NullOrder.NULLS_FIRST);
  }

  /**
   * 按字段名添加升序排序字段，指定空值排序位置。
   *
   * @param name 字段名
   * @param nullOrder 空值排序位置
   * @return this，便于链式调用
   */
  default R asc(String name, NullOrder nullOrder) {
    return asc(Expressions.ref(name), nullOrder);
  }

  /**
   * 按表达式项添加升序排序字段，空值排在最前（NULLS_FIRST）。
   *
   * @param term 表达式项
   * @return this，便于链式调用
   */
  default R asc(Term term) {
    return asc(term, NullOrder.NULLS_FIRST);
  }

  /**
   * 按表达式项添加升序排序字段，指定空值排序位置。
   *
   * @param term 表达式项
   * @param nullOrder 空值排序位置
   * @return this，便于链式调用
   */
  R asc(Term term, NullOrder nullOrder);

  /**
   * 按字段名添加降序排序字段，空值排在最后（NULLS_LAST）。
   *
   * @param name 字段名
   * @return this，便于链式调用
   */
  default R desc(String name) {
    return desc(Expressions.ref(name), NullOrder.NULLS_LAST);
  }

  /**
   * 按字段名添加降序排序字段，指定空值排序位置。
   *
   * @param name 字段名
   * @param nullOrder 空值排序位置
   * @return this，便于链式调用
   */
  default R desc(String name, NullOrder nullOrder) {
    return desc(Expressions.ref(name), nullOrder);
  }

  /**
   * 按表达式项添加降序排序字段，空值排在最后（NULLS_LAST）。
   *
   * @param term 表达式项
   * @return this，便于链式调用
   */
  default R desc(Term term) {
    return desc(term, NullOrder.NULLS_LAST);
  }

  /**
   * 按表达式项添加降序排序字段，指定空值排序位置。
   *
   * @param term 表达式项
   * @param nullOrder 空值排序位置
   * @return this，便于链式调用
   */
  R desc(Term term, NullOrder nullOrder);

  /**
   * 设置排序列名解析的大小写敏感性。
   *
   * @param caseSensitive 为 true 时列名解析区分大小写
   * @return this，便于链式调用
   */
  default R caseSensitive(boolean caseSensitive) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement caseSensitive");
  };
}
