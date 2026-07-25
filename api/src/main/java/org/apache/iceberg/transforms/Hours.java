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
 * 按"小时"分区的 {@link TimeTransform} 实现：单例，仅支持 TIMESTAMP，分发到 {@link Timestamps#HOUR}。
 *
 * <p>所属模块：iceberg-api（PartitionSpec 持有的 hour 粒度变换入口）。
 *
 * <p>职责：提供 hour 变换接口；支持排序包含关系判断（hour 是最细时间粒度，可满足 day/month/year）。
 *
 * <p>设计意图：单例 + 类型擦除；序列化通过 {@link SerializationProxies#HoursTransformProxy} 替代以保持单例语义。
 *
 * <p>上下游关系：由 {@link Transforms#hour()} 构造；被 PartitionSpec 持有。
 *
 * @param <T> 源值的 Java 类型
 */
public class Hours<T> extends TimeTransform<T> {
  private static final Hours<?> INSTANCE = new Hours<>();

  /**
   * 返回 Hours 单例。
   *
   * @param <T> 源值类型
   * @return Hours 单例
   */
  @SuppressWarnings("unchecked")
  static <T> Hours<T> get() {
    return (Hours<T>) INSTANCE;
  }

  /**
   * 按源类型返回对应的 HOUR 枚举变换。
   *
   * <p>逻辑：仅 TIMESTAMP→{@link Timestamps#HOUR}；其他类型抛 IllegalArgumentException。
   *
   * @param type 源字段类型
   * @return 对应的 HOUR 枚举实例
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Transform<T, Integer> toEnum(Type type) {
    if (type.typeId() == Type.TypeID.TIMESTAMP) {
      return (Transform<T, Integer>) Timestamps.HOUR;
    }

    throw new IllegalArgumentException("Unsupported type: " + type);
  }

  /**
   * 判断类型是否可变换：仅支持 TIMESTAMP。
   *
   * @param type 待校验类型
   * @return TIMESTAMP 返回 true
   */
  @Override
  public boolean canTransform(Type type) {
    return type.typeId() == Type.TypeID.TIMESTAMP;
  }

  /**
   * hour 变换结果类型恒为 INTEGER。
   *
   * @param sourceType 源类型（不参与判断）
   * @return IntegerType
   */
  @Override
  public Type getResultType(Type sourceType) {
    return Types.IntegerType.get();
  }

  /**
   * 判断 hour 排序是否满足另一变换的排序。
   *
   * <p>逻辑：同一实例返回 true；对方是 Timestamps 时仅当对方也是 HOUR 返回 true； 对方是 Hours/Days/Months/Years 时返回
   * true（hour 是最细粒度）；其余 false。
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
      return other == Timestamps.HOUR;
    } else if (other instanceof Hours
        || other instanceof Days
        || other instanceof Months
        || other instanceof Years) {
      return true;
    }

    return false;
  }

  /**
   * 把 hour 值格式化为人类可读字符串（ISO 日期时间到小时）。
   *
   * @param alwaysInt 输出类型（恒为 INTEGER，不参与判断）
   * @param value hour 值
   * @return ISO 字符串；null 返回 "null"
   */
  @Override
  public String toHumanString(Type alwaysInt, Integer value) {
    return value != null ? TransformUtil.humanHour(value) : "null";
  }

  /**
   * 返回 "hour" 字符串，用于元数据序列化。
   *
   * @return "hour"
   */
  @Override
  public String toString() {
    return "hour";
  }

  /**
   * 序列化替换：用代理对象替代本实例，保持单例语义。
   *
   * @return 序列化代理
   * @throws ObjectStreamException 不会抛出
   */
  Object writeReplace() throws ObjectStreamException {
    return SerializationProxies.HoursTransformProxy.get();
  }
}
