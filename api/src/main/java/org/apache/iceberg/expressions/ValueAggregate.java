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
package org.apache.iceberg.expressions;

import org.apache.iceberg.DataFile;
import org.apache.iceberg.StructLike;

/**
 * 基于字段值的聚合基类：为 {@link MaxAggregate}、{@link MinAggregate} 等提供统一的 行级与文件级求值骨架。
 *
 * <p>所属模块：iceberg-api（表达式体系聚合分支的中间抽象层，继承 {@link BoundAggregate}， 将输入类型与输出类型相同的聚合（如 MAX/MIN）公共逻辑下沉）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>行级求值 {@link #eval(StructLike)}：直接委托 term 对行求值，返回字段值。
 *   <li>文件级求值 {@link #eval(DataFile)}：先由子类通过 {@link #evaluateRef(DataFile)} 从文件列统计（如
 *       upperBounds/lowerBounds）取出该文件的聚合值，再用 {@link SingleValueStruct} 把该单值包装成 {@link
 *       StructLike}，复用 term 的 eval 完成求值。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>文件级求值复用行级 term.eval 路径：通过 {@link SingleValueStruct} 把从文件统计取出的 单个标量值伪装成一行数据，使 term 的 eval
 *       逻辑无需为文件场景单独实现，减少重复代码。
 *   <li>{@link #evaluateRef(DataFile)} 留给子类实现（默认抛 UnsupportedOperationException）：MAX 取上界、MIN
 *       取下界，各自定义从文件取哪个值。
 *   <li>{@link SingleValueStruct} 为只读单字段结构，{@link #set} 永远抛异常，保证不会被误写。
 * </ul>
 *
 * <p>上下游关系：继承自 {@link BoundAggregate}；被 {@link MaxAggregate}、{@link MinAggregate} 继承；被 {@link
 * AggregateEvaluator} 在文件级聚合时调用 {@link #eval(DataFile)}。
 *
 * @param <T> 聚合输入值的 Java 类型（输入与输出同类型）
 */
class ValueAggregate<T> extends BoundAggregate<T, T> {
  private final SingleValueStruct valueStruct = new SingleValueStruct();

  /**
   * 构造基于值的聚合。
   *
   * @param op 聚合操作类型（如 MAX、MIN）
   * @param term 已绑定 term，用于在行/文件上求出字段值
   */
  protected ValueAggregate(Operation op, BoundTerm<T> term) {
    super(op, term);
  }

  /**
   * 行级求值：直接委托 term 对输入行求值。
   *
   * @param struct 输入数据行
   * @return 该行上 term 求出的字段值
   */
  @Override
  public T eval(StructLike struct) {
    return term().eval(struct);
  }

  /**
   * 文件级求值：从文件列统计取出聚合值并经 term 求值返回。
   *
   * <p>逻辑：先调用 {@link #evaluateRef(DataFile)}（由子类实现）从文件统计中取出该文件的 聚合值（如 MAX 取上界），将其装入 {@link
   * SingleValueStruct}，再委托 term.eval 完成求值。 这样文件级与行级共用同一条 term.eval 路径。
   *
   * @param file 数据文件
   * @return 该文件级别的聚合值
   */
  @Override
  public T eval(DataFile file) {
    valueStruct.setValue(evaluateRef(file));
    return term().eval(valueStruct);
  }

  /**
   * 从文件列统计中提取该文件的聚合值，由子类实现。
   *
   * <p>设计要点：基类默认抛 {@link UnsupportedOperationException}，强制子类（如 {@link MaxAggregate} 取上界、{@link
   * MinAggregate} 取下界）覆盖。
   *
   * @param file 数据文件
   * @return 该文件对应的聚合值（如最大值、最小值）
   * @throws UnsupportedOperationException 若子类未覆盖本方法
   */
  protected Object evaluateRef(DataFile file) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement eval(DataFile)");
  }

  /**
   * 单值只读结构：把从文件统计取出的一个标量值包装成 {@link StructLike}， 使 term 的 eval 逻辑可统一处理行数据与文件级单值。
   *
   * <p>设计意图：term.eval 接收 {@link StructLike}，而文件级聚合只有一个标量值， 通过本适配器避免为文件场景单独实现 term 求值。{@link #set}
   * 永远抛异常以标记只读。
   */
  private static class SingleValueStruct implements StructLike {
    private Object value;

    /**
     * 设置当前包装的单值。
     *
     * @param value 从文件统计取出的标量值
     */
    private void setValue(Object value) {
      this.value = value;
    }

    /** 返回 1，本结构仅含一个字段。 */
    @Override
    public int size() {
      return 1;
    }

    /**
     * 按位置取值（仅 pos=0 有效），直接返回包装的单值。
     *
     * @param pos 字段位置（本结构仅 0 有效）
     * @param javaClass 期望的 Java 类型（用于类型安全，此处直接强转）
     * @return 包装的单值
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(int pos, Class<T> javaClass) {
      return (T) value;
    }

    /**
     * 不支持写入：本结构为只读适配器。
     *
     * @throws UnsupportedOperationException 永远抛出
     */
    @Override
    public <T> void set(int pos, T value1) {
      throw new UnsupportedOperationException("Cannot update a read-only struct");
    }
  }
}
