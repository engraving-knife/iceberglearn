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
 * 按"年"分区的 {@link TimeTransform} 实现：单例，按源类型分发到 {@link Dates#YEAR} 或 {@link Timestamps#YEAR}。
 *
 * <p>所属模块：iceberg-api（PartitionSpec 持有的 year 粒度变换入口）。
 *
 * <p>职责：提供统一的 year 变换接口，屏蔽 DATE 与 TIMESTAMP 的底层差异；支持排序包含关系判断 （year 是最粗时间粒度，仅满足自身排序）。
 *
 * <p>设计意图：单例 + 类型擦除；序列化通过 {@link SerializationProxies#YearsTransformProxy} 替代以保持单例语义。
 *
 * <p>上下游关系：由 {@link Transforms#year()} 构造；被 PartitionSpec 持有。
 *
 * @param <T> 源值的 Java 类型
 */
class Years<T> extends TimeTransform<T> {
  private static final Years<?> INSTANCE = new Years<>();

  /**
   * 返回 Years 单例。
   *
   * @param <T> 源值类型
   * @return Years 单例
   */
  @SuppressWarnings("unchecked")
  static <T> Years<T> get() {
    return (Years<T>) INSTANCE;
  }

  /**
   * 按源类型返回对应的 YEAR 枚举变换。
   *
   * <p>逻辑：DATE→{@link Dates#YEAR}；TIMESTAMP→{@link Timestamps#YEAR}；其他类型抛 IllegalArgumentException。
   *
   * @param type 源字段类型
   * @return 对应的 YEAR 枚举实例
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Transform<T, Integer> toEnum(Type type) {
    switch (type.typeId()) {
      case DATE:
        return (Transform<T, Integer>) Dates.YEAR;
      case TIMESTAMP:
        return (Transform<T, Integer>) Timestamps.YEAR;
      default:
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }

  /**
   * year 变换结果类型恒为 INTEGER。
   *
   * @param sourceType 源类型（不参与判断）
   * @return IntegerType
   */
  @Override
  public Type getResultType(Type sourceType) {
    return Types.IntegerType.get();
  }

  /**
   * 判断 year 排序是否满足另一变换的排序。
   *
   * <p>逻辑：同一实例返回 true；对方是 Timestamps/Dates 时委托对应 YEAR 的判断； 对方是 Years 时返回 true；其余 false（year
   * 是最粗粒度，不满足更细粒度的排序）。
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
      return Timestamps.YEAR.satisfiesOrderOf(other);
    } else if (other instanceof Dates) {
      return Dates.YEAR.satisfiesOrderOf(other);
    } else if (other instanceof Years) {
      return true;
    }

    return false;
  }

  /**
   * 把 year 值格式化为人类可读字符串（如 "2020"）。
   *
   * @param alwaysInt 输出类型（恒为 INTEGER，不参与判断）
   * @param value year 值
   * @return 年份字符串；null 返回 "null"
   */
  @Override
  public String toHumanString(Type alwaysInt, Integer value) {
    return value != null ? TransformUtil.humanYear(value) : "null";
  }

  /**
   * 返回 "year" 字符串，用于元数据序列化。
   *
   * @return "year"
   */
  @Override
  public String toString() {
    return "year";
  }

  /**
   * 序列化替换：用代理对象替代本实例，保持单例语义。
   *
   * @return 序列化代理
   * @throws ObjectStreamException 不会抛出
   */
  Object writeReplace() throws ObjectStreamException {
    return SerializationProxies.YearsTransformProxy.get();
  }
}
