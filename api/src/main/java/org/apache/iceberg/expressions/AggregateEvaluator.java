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

import java.util.List;
import java.util.stream.Collectors;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.expressions.BoundAggregate.Aggregator;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;

/**
 * 聚合表达式求值器：对一组 {@link BoundAggregate} 进行增量聚合，最终产出聚合结果。
 *
 * <p>所属模块：iceberg-api（表达式/谓词体系的核心抽象层，定义表达式语义与求值接口； 由 core 模块在写入 DataFile / manifest
 * 统计、读取侧聚合下推等场景调用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有多个 {@link Aggregator}（每个聚合表达式一个），对外提供统一的 update/result 入口。
 *   <li>同时支持对行数据（{@link StructLike}）和数据文件（{@link DataFile}）做增量更新： 前者用于行级聚合，后者用于直接消费 DataFile
 *       上预统计的列指标。
 *   <li>把多个聚合结果组装为单一 {@link StructLike}（{@link ArrayStructLike}）， 便于上层将其作为一行统计结果写出或传递。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>聚合与谓词共用一套 {@link BoundTerm} 体系，但聚合需要“增量累积”语义，故引入 {@link Aggregator} 状态对象，由每个 BoundAggregate
 *       自行决定如何累积。
 *   <li>结果按声明顺序以 field id = pos 写入 StructType，使下游消费者可按位置稳定访问。
 *   <li>NullSafeAggregator 在文件级聚合时遇到缺失值会标记 isValid=false，避免静默丢失数据。
 * </ul>
 *
 * <p>上下游关系：由 {@link Binder#bind} 将未绑定聚合绑定到结构后构造；被 core 模块的统计写入、 扫描规划等逻辑调用。
 */
public class AggregateEvaluator {

  /**
   * 基于 Schema 与未绑定聚合表达式列表创建求值器。
   *
   * <p>逻辑：将 Schema 转为 StructType 后委托 {@link #create(Types.StructType, List)} 完成绑定与构造。
   *
   * @param schema 表 schema，用于解析命名引用
   * @param aggregates 未绑定的聚合表达式列表
   * @return 已绑定的 {@link AggregateEvaluator}
   */
  public static AggregateEvaluator create(Schema schema, List<Expression> aggregates) {
    return create(schema.asStruct(), aggregates);
  }

  /**
   * 基于已绑定聚合列表创建求值器（跳过绑定步骤）。
   *
   * @param aggregates 已绑定到具体 schema 的聚合列表
   * @return 新建的 {@link AggregateEvaluator}
   */
  public static AggregateEvaluator create(List<BoundAggregate<?, ?>> aggregates) {
    return new AggregateEvaluator(aggregates);
  }

  /**
   * 基于 StructType 与未绑定聚合表达式列表创建求值器（内部入口）。
   *
   * <p>逻辑：遍历聚合表达式，使用 {@link Binder#bind(StructType, Expression)} 将每个未绑定 表达式绑定到结构，并强制转为 {@link
   * BoundAggregate}，最终交给私有构造器。
   *
   * @param struct 结构类型，用于解析字段引用
   * @param aggregates 未绑定聚合表达式列表
   * @return 已绑定的 {@link AggregateEvaluator}
   */
  private static AggregateEvaluator create(Types.StructType struct, List<Expression> aggregates) {
    List<BoundAggregate<?, ?>> boundAggregates =
        aggregates.stream()
            .map(expr -> Binder.bind(struct, expr))
            .map(bound -> (BoundAggregate<?, ?>) bound)
            .collect(Collectors.toList());

    return new AggregateEvaluator(boundAggregates);
  }

  private final List<Aggregator<?>> aggregators;
  private final Types.StructType resultType;
  private final List<BoundAggregate<?, ?>> aggregates;

  /**
   * 私有构造器：基于已绑定聚合列表构建求值器。
   *
   * <p>逻辑：为每个聚合创建对应的 {@link Aggregator}，并按位置构造结果 StructType， 字段 id 取为位置 pos、字段名取自 {@link
   * BoundAggregate#describe()}、类型取自 {@link BoundAggregate#type()}，且统一声明为 optional（聚合可能产出 null）。
   *
   * @param aggregates 已绑定聚合列表
   */
  private AggregateEvaluator(List<BoundAggregate<?, ?>> aggregates) {
    ImmutableList.Builder<Aggregator<?>> aggregatorsBuilder = ImmutableList.builder();
    List<Types.NestedField> resultFields = Lists.newArrayList();

    for (int pos = 0; pos < aggregates.size(); pos += 1) {
      BoundAggregate<?, ?> aggregate = aggregates.get(pos);
      aggregatorsBuilder.add(aggregate.newAggregator());
      resultFields.add(Types.NestedField.optional(pos, aggregate.describe(), aggregate.type()));
    }

    this.aggregators = aggregatorsBuilder.build();
    this.resultType = Types.StructType.of(resultFields);
    this.aggregates = aggregates;
  }

  /**
   * 用一行数据更新所有聚合器（行级增量聚合入口）。
   *
   * @param struct 一行数据
   */
  public void update(StructLike struct) {
    for (Aggregator<?> aggregator : aggregators) {
      aggregator.update(struct);
    }
  }

  /**
   * 用一个数据文件更新所有聚合器（文件级聚合入口，复用 DataFile 上的预统计值）。
   *
   * @param file 数据文件
   */
  public void update(DataFile file) {
    for (Aggregator<?> aggregator : aggregators) {
      aggregator.update(file);
    }
  }

  /**
   * 返回聚合结果的 StructType。
   *
   * <p>每个聚合对应一个字段，字段 id 为其位置下标，字段名由聚合描述生成。
   *
   * @return 结果结构类型
   */
  public Types.StructType resultType() {
    return resultType;
  }

  /**
   * 判断所有聚合器是否都处于有效状态。
   *
   * <p>当某聚合器在文件级聚合遇到缺失值等情况时会被标记为无效，整体结果应视为不可信。
   *
   * @return 全部有效返回 true
   */
  public boolean allAggregatorsValid() {
    return aggregators.stream().allMatch(BoundAggregate.Aggregator::isValid);
  }

  /**
   * 计算并返回所有聚合器的当前聚合结果，封装为 {@link StructLike}。
   *
   * <p>逻辑：按顺序调用每个聚合器的 {@link Aggregator#result()}，结果数组以 {@link ArrayStructLike}
   * 包装返回，使调用方可按位置读取各聚合值。
   *
   * @return 聚合结果（结构与 {@link #resultType()} 一致）
   */
  public StructLike result() {
    Object[] results =
        aggregators.stream().map(BoundAggregate.Aggregator::result).toArray(Object[]::new);
    return new ArrayStructLike(results);
  }

  /**
   * 返回此求值器使用的已绑定聚合列表。
   *
   * @return 已绑定聚合列表
   */
  public List<BoundAggregate<?, ?>> aggregates() {
    return aggregates;
  }

  /**
   * 基于数组的 {@link StructLike} 实现，用于把聚合结果数组包装成结构化访问对象。
   *
   * <p>设计意图：聚合结果以 Object[] 形式持有，但下游常需要 StructLike 接口视图， 此内部类做无拷贝适配。
   */
  private static class ArrayStructLike implements StructLike {
    private final Object[] values;

    private ArrayStructLike(Object[] values) {
      this.values = values;
    }

    @Override
    public int size() {
      return values.length;
    }

    @Override
    public <T> T get(int pos, Class<T> javaClass) {
      return javaClass.cast(values[pos]);
    }

    @Override
    public <T> void set(int pos, T value) {
      values[pos] = value;
    }
  }
}
