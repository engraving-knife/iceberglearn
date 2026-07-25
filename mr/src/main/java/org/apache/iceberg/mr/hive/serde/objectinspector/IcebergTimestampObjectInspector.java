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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.apache.hadoop.hive.serde2.io.TimestampWritable;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.TimestampObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;

/**
 * 文件级说明：Iceberg TIMESTAMP（不带时区）类型在 Hive 侧的 ObjectInspector（Hive 2 版本实现）。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取呈现：把 Iceberg {@link LocalDateTime} 转为 Hive {@link java.sql.Timestamp} / {@link
 *       TimestampWritable}。
 *   <li>写入转换：实现 {@link WriteObjectInspector}，把 {@link java.sql.Timestamp} 转为 {@link LocalDateTime}。
 *   <li>对象拷贝：深拷贝 Timestamp / LocalDateTime（含 nanos）。
 * </ul>
 *
 * <p>设计意图：Iceberg 用 {@link LocalDateTime} 表示无时区时间戳，Hive 用 {@link
 * java.sql.Timestamp}；本类做互转，避免时区影响。单例模式。Hive 3 由 Hive3 子类替代。
 *
 * <p>上下游关系：被 {@link IcebergObjectInspector#TIMESTAMP_INSPECTOR} 通过反射加载； 被 Hive SerDe 在读写 TIMESTAMP
 * 字段时调用。
 */
public class IcebergTimestampObjectInspector extends AbstractPrimitiveJavaObjectInspector
    implements TimestampObjectInspector, WriteObjectInspector {

  private static final IcebergTimestampObjectInspector INSTANCE =
      new IcebergTimestampObjectInspector();

  /** 返回单例实例。 */
  public static IcebergTimestampObjectInspector get() {
    return INSTANCE;
  }

  private IcebergTimestampObjectInspector() {
    super(TypeInfoFactory.timestampTypeInfo);
  }

  /**
   * 写入转换：把 Hive {@link java.sql.Timestamp} 转为 Iceberg {@link LocalDateTime}。
   *
   * @param o java.sql.Timestamp 对象
   * @return LocalDateTime；o 为 null 时返回 null
   */
  @Override
  public LocalDateTime convert(Object o) {
    return o == null ? null : ((Timestamp) o).toLocalDateTime();
  }

  /**
   * 把 Iceberg {@link LocalDateTime} 转为 Hive {@link java.sql.Timestamp}。
   *
   * @param o LocalDateTime 对象，可为 null
   * @return java.sql.Timestamp；o 为 null 时返回 null
   */
  @Override
  public Timestamp getPrimitiveJavaObject(Object o) {
    return o == null ? null : Timestamp.valueOf((LocalDateTime) o);
  }

  /**
   * 把 Iceberg {@link LocalDateTime} 转为 {@link TimestampWritable}。
   *
   * @param o LocalDateTime 对象，可为 null
   * @return TimestampWritable；o 为 null 时返回 null
   */
  @Override
  public TimestampWritable getPrimitiveWritableObject(Object o) {
    Timestamp ts = getPrimitiveJavaObject(o);
    return ts == null ? null : new TimestampWritable(ts);
  }

  /**
   * 深拷贝 Timestamp（含 nanos）与 LocalDateTime；其他类型原样返回。
   *
   * @param o 待拷贝对象
   * @return 拷贝结果
   */
  @Override
  public Object copyObject(Object o) {
    if (o instanceof Timestamp) {
      Timestamp ts = (Timestamp) o;
      Timestamp copy = new Timestamp(ts.getTime());
      copy.setNanos(ts.getNanos());
      return copy;
    } else if (o instanceof LocalDateTime) {
      LocalDateTime ldt = (LocalDateTime) o;
      return LocalDateTime.of(ldt.toLocalDate(), ldt.toLocalTime());
    } else {
      return o;
    }
  }
}
