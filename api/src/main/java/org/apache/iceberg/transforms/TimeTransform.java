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
package org.apache.iceberg.transforms;

import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundTransform;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 时间粒度分区变换的抽象基类：把按"概念粒度"（如 day/month/year）的变换分发到具体类型对应的 枚举实现（{@link Dates} 或 {@link Timestamps}）。
 *
 * <p>所属模块：iceberg-api（PartitionSpec 持有的时间粒度变换的统一入口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供统一的 day/month/year/hour 变换接口，屏蔽 DATE 与 TIMESTAMP 的实现差异。
 *   <li>把谓词投影、bind 等委托给与源类型匹配的枚举实例。
 * </ul>
 *
 * <p>设计意图：调用方在构造 PartitionSpec 时通常只知道"按 day 分区"，不关心底层是 DATE 还是 TIMESTAMP。TimeTransform 通过 {@link
 * #toEnum(Type)} 在 bind/project 阶段按类型分发到 {@link Dates#DAY}/{@link Timestamps#DAY}
 * 等，实现"概念粒度"与"具体类型实现"解耦。
 *
 * <p>上下游关系：被 {@link Days}/{@link Hours}/{@link Months}/{@link Years} 继承； 投影依赖 {@link
 * ProjectionUtil}。
 *
 * @param <S> 源值的 Java 类型
 */
abstract class TimeTransform<S> implements Transform<S, Integer> {
  /**
   * 按源类型返回对应的枚举粒度变换。
   *
   * @param type 源字段类型
   * @return 与类型匹配的 {@link Dates} 或 {@link Timestamps} 枚举实例
   */
  protected abstract Transform<S, Integer> toEnum(Type type);

  /**
   * 绑定到具体类型，委托给对应枚举实例的 bind。
   *
   * @param type 源字段类型
   * @return 粒度换算函数
   */
  @Override
  public SerializableFunction<S, Integer> bind(Type type) {
    return toEnum(type).bind(type);
  }

  /** 时间粒度变换保持顺序。 */
  @Override
  public boolean preservesOrder() {
    return true;
  }

  /**
   * 判断类型是否可变换：支持 DATE 与 TIMESTAMP。
   *
   * @param type 待校验类型
   * @return DATE/TIMESTAMP 返回 true
   */
  @Override
  public boolean canTransform(Type type) {
    return type.typeId() == Type.TypeID.DATE || type.typeId() == Type.TypeID.TIMESTAMP;
  }

  /**
   * inclusive 投影：委托给与谓词类型匹配的枚举实例。
   *
   * <p>逻辑：BoundTransform 委托 projectTransformPredicate；否则按谓词项类型分发到枚举实例的 project。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 粒度值上的谓词
   */
  @Override
  public UnboundPredicate<Integer> project(String name, BoundPredicate<S> predicate) {
    if (predicate.term() instanceof BoundTransform) {
      return ProjectionUtil.projectTransformPredicate(this, name, predicate);
    }

    return toEnum(predicate.term().type()).project(name, predicate);
  }

  /**
   * strict 投影：委托给与谓词类型匹配的枚举实例。
   *
   * <p>逻辑：BoundTransform 委托 projectTransformPredicate；否则按谓词项类型分发到枚举实例的 projectStrict。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 粒度值上的谓词
   */
  @Override
  public UnboundPredicate<Integer> projectStrict(String name, BoundPredicate<S> predicate) {
    if (predicate.term() instanceof BoundTransform) {
      return ProjectionUtil.projectTransformPredicate(this, name, predicate);
    }

    return toEnum(predicate.term().type()).projectStrict(name, predicate);
  }

  /**
   * 返回去重名称 "time"，使同源字段上的多个时间粒度变换互斥。
   *
   * @return "time"
   */
  @Override
  public String dedupName() {
    return "time";
  }
}
