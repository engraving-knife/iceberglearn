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
package org.apache.iceberg.mr.hive.serde.objectinspector;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.HiveDecimalObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.DecimalTypeInfo;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：Iceberg DECIMAL 类型在 Hive 侧的 ObjectInspector。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取呈现：把 Iceberg {@link BigDecimal} 转为 Hive {@link HiveDecimal} / {@link
 *       HiveDecimalWritable}。
 *   <li>写入转换：实现 {@link WriteObjectInspector}，把 {@link HiveDecimal} 转为 {@link BigDecimal}，并修复 0 值丢失
 *       scale 的问题。
 *   <li>对象拷贝：深拷贝 HiveDecimal / BigDecimal。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不同 DECIMAL 字段 precision/scale 不同，因此按 (precision, scale) 缓存实例， 用 Caffeine 缓存，10 分钟过期。
 *   <li>HiveDecimal -> BigDecimal 在值为 0 时会丢失 scale，因此在 convert 中显式 {@link BigDecimal#setScale(int)}
 *       恢复。
 * </ul>
 *
 * <p>上下游关系：被 {@link IcebergObjectInspector#primitive} 在 DECIMAL 分支按 precision/scale 创建；被 Hive SerDe
 * 在读写 DECIMAL 字段时调用。
 */
public final class IcebergDecimalObjectInspector extends AbstractPrimitiveJavaObjectInspector
    implements HiveDecimalObjectInspector, WriteObjectInspector {

  private static final Cache<Integer, IcebergDecimalObjectInspector> CACHE =
      Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();

  /**
   * 按 (precision, scale) 获取或创建 ObjectInspector 实例。
   *
   * <p>逻辑：校验 scale < precision 且不超过 Hive 上限；用 {@code precision << 8 | scale} 作为 key 从 Caffeine
   * 缓存中查找或创建。
   *
   * @param precision decimal 精度
   * @param scale decimal 标度
   * @return 缓存或新建的 ObjectInspector 实例
   */
  public static IcebergDecimalObjectInspector get(int precision, int scale) {
    Preconditions.checkArgument(scale < precision);
    Preconditions.checkArgument(precision <= HiveDecimal.MAX_PRECISION);
    Preconditions.checkArgument(scale <= HiveDecimal.MAX_SCALE);

    Integer key = precision << 8 | scale;
    return CACHE.get(key, k -> new IcebergDecimalObjectInspector(precision, scale));
  }

  private IcebergDecimalObjectInspector(int precision, int scale) {
    super(new DecimalTypeInfo(precision, scale));
  }

  /**
   * 把 Iceberg {@link BigDecimal} 转为 Hive {@link HiveDecimal}。
   *
   * @param o BigDecimal 对象，可为 null
   * @return HiveDecimal；o 为 null 时返回 null
   */
  @Override
  public HiveDecimal getPrimitiveJavaObject(Object o) {
    return o == null ? null : HiveDecimal.create((BigDecimal) o);
  }

  /**
   * 把 Iceberg {@link BigDecimal} 转为 {@link HiveDecimalWritable}。
   *
   * @param o BigDecimal 对象，可为 null
   * @return HiveDecimalWritable；o 为 null 时返回 null
   */
  @Override
  public HiveDecimalWritable getPrimitiveWritableObject(Object o) {
    HiveDecimal decimal = getPrimitiveJavaObject(o);
    return decimal == null ? null : new HiveDecimalWritable(decimal);
  }

  /**
   * 深拷贝 HiveDecimal / BigDecimal；其他类型原样返回。
   *
   * @param o 待拷贝对象
   * @return 拷贝结果；o 为 null 时返回 null
   */
  @Override
  public Object copyObject(Object o) {
    if (o == null) {
      return null;
    }

    if (o instanceof HiveDecimal) {
      HiveDecimal decimal = (HiveDecimal) o;
      return HiveDecimal.create(decimal.bigDecimalValue());
    } else if (o instanceof BigDecimal) {
      BigDecimal copy = new BigDecimal(o.toString());
      return copy;
    } else {
      return o;
    }
  }

  /**
   * 写入转换：把 Hive {@link HiveDecimal} 转为 Iceberg {@link BigDecimal}。
   *
   * <p>设计要点：HiveDecimal -> BigDecimal 在值为 0 时会丢失 scale，因此显式 {@link BigDecimal#setScale(int)} 恢复
   * scale。
   *
   * @param o HiveDecimal 对象
   * @return BigDecimal；o 为 null 时返回 null
   */
  @Override
  public BigDecimal convert(Object o) {
    if (o == null) {
      return null;
    }

    BigDecimal result = ((HiveDecimal) o).bigDecimalValue();
    // during the HiveDecimal to BigDecimal conversion the scale is lost, when the value is 0
    result = result.setScale(scale());
    return result;
  }
}
