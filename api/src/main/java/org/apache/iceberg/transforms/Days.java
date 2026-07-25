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

import java.io.ObjectStreamException;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 按"天"分区的 {@link TimeTransform} 实现：单例，按源类型分发到 {@link Dates#DAY} 或 {@link Timestamps#DAY}。
 *
 * <p>所属模块：iceberg-api（PartitionSpec 持有的 day 粒度变换入口）。
 *
 * <p>职责：提供统一的 day 变换接口，屏蔽 DATE 与 TIMESTAMP 的底层差异；支持排序包含关系判断。
 *
 * <p>设计意图：单例 + 类型擦除使一个 INSTANCE 服务所有类型；通过 {@link #toEnum(Type)} 在 bind/project 阶段分发到具体枚举实现；序列化通过
 * {@link SerializationProxies#DaysTransformProxy} 替代以保持单例语义。
 *
 * <p>上下游关系：由 {@link Transforms#day()} 构造；被 PartitionSpec 持有。
 *
 * @param <T> 源值的 Java 类型
 */
public class Days<T> extends TimeTransform<T> {
  private static final Days<?> INSTANCE = new Days<>();

  /**
   * 返回 Days 单例。
   *
   * @param <T> 源值类型
   * @return Days 单例
   */
  @SuppressWarnings("unchecked")
  static <T> Days<T> get() {
    return (Days<T>) INSTANCE;
  }

  /**
   * 按源类型返回对应的 DAY 枚举变换。
   *
   * <p>逻辑：DATE→{@link Dates#DAY}；TIMESTAMP→{@link Timestamps#DAY}；其他类型抛 IllegalArgumentException。
   *
   * @param type 源字段类型
   * @return 对应的 DAY 枚举实例
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Transform<T, Integer> toEnum(Type type) {
    switch (type.typeId()) {
      case DATE:
        return (Transform<T, Integer>) Dates.DAY;
      case TIMESTAMP:
        return (Transform<T, Integer>) Timestamps.DAY;
      default:
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }

  /**
   * day 变换结果类型恒为 DATE。
   *
   * @param sourceType 源类型（不参与判断）
   * @return DateType
   */
  @Override
  public Type getResultType(Type sourceType) {
    return Types.DateType.get();
  }

  /**
   * 判断 day 排序是否满足另一变换的排序。
   *
   * <p>逻辑：同一实例返回 true；对方是 Timestamps/Dates 时委托对应 DAY 的判断； 对方是 Days/Months/Years 时返回 true（day
   * 是更细粒度，可满足其排序）；其余 false。
   *
   * @param other 另一个变换
   * @return 满足返回 true
   */
  @Override
  public boolean satisfiesOrderOf(Transform<?, ?> other) {
    if (this == other) {
      return true;
    }

    if (other instanceof Timestamps) {
      return Timestamps.DAY.satisfiesOrderOf(other);
    } else if (other instanceof Dates) {
      return Dates.DAY.satisfiesOrderOf(other);
    } else if (other instanceof Days || other instanceof Months || other instanceof Years) {
      return true;
    }

    return false;
  }

  /**
   * 把 day 值格式化为人类可读字符串（ISO 日期）。
   *
   * @param alwaysDate 输出类型（恒为 DATE，不参与判断）
   * @param value day 值
   * @return ISO 日期字符串；null 返回 "null"
   */
  @Override
  public String toHumanString(Type alwaysDate, Integer value) {
    return value != null ? TransformUtil.humanDay(value) : "null";
  }

  /**
   * 返回 "day" 字符串，用于元数据序列化。
   *
   * @return "day"
   */
  @Override
  public String toString() {
    return "day";
  }

  /**
   * 序列化替换：用代理对象替代本实例，保持单例语义。
   *
   * @return 序列化代理
   * @throws ObjectStreamException 不会抛出
   */
  Object writeReplace() throws ObjectStreamException {
    return SerializationProxies.DaysTransformProxy.get();
  }
}
