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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.apache.hadoop.hive.serde2.io.TimestampWritable;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.TimestampObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;

/**
 * 文件级说明：Iceberg TIMESTAMP WITH ZONE 类型在 Hive 侧的 ObjectInspector（Hive 2 版本实现）。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取呈现：把 Iceberg {@link OffsetDateTime} 转为 Hive {@link java.sql.Timestamp} / {@link
 *       TimestampWritable}（按 UTC 即时点转换）。
 *   <li>写入转换：实现 {@link WriteObjectInspector}，把 {@link java.sql.Timestamp} 转为 UTC 时区的 {@link
 *       OffsetDateTime}。
 *   <li>对象拷贝：深拷贝 Timestamp / OffsetDateTime（含 nanos）。
 * </ul>
 *
 * <p>设计意图：Hive Timestamp 不携带时区信息，Iceberg 用 {@link OffsetDateTime} 表示带时区 时间戳；本类以 UTC
 * 为桥梁做互转。单例模式。Hive 3 由 Hive3 子类替代。
 *
 * <p>上下游关系：被 {@link IcebergObjectInspector#TIMESTAMP_INSPECTOR_WITH_TZ} 通过反射加载； 被 Hive SerDe 在读写
 * TIMESTAMPTZ 字段时调用。
 */
public class IcebergTimestampWithZoneObjectInspector extends AbstractPrimitiveJavaObjectInspector
    implements TimestampObjectInspector, WriteObjectInspector {

  private static final IcebergTimestampWithZoneObjectInspector INSTANCE =
      new IcebergTimestampWithZoneObjectInspector();

  /** 返回单例实例。 */
  public static IcebergTimestampWithZoneObjectInspector get() {
    return INSTANCE;
  }

  private IcebergTimestampWithZoneObjectInspector() {
    super(TypeInfoFactory.timestampTypeInfo);
  }

  /**
   * 写入转换：把 Hive {@link java.sql.Timestamp} 转为 UTC 时区的 {@link OffsetDateTime}。
   *
   * @param o java.sql.Timestamp 对象
   * @return UTC OffsetDateTime；o 为 null 时返回 null
   */
  @Override
  public OffsetDateTime convert(Object o) {
    return o == null ? null : OffsetDateTime.ofInstant(((Timestamp) o).toInstant(), ZoneOffset.UTC);
  }

  /**
   * 把 Iceberg {@link OffsetDateTime} 转为 Hive {@link java.sql.Timestamp}（取即时点）。
   *
   * @param o OffsetDateTime 对象，可为 null
   * @return java.sql.Timestamp；o 为 null 时返回 null
   */
  @Override
  public Timestamp getPrimitiveJavaObject(Object o) {
    return o == null ? null : Timestamp.from(((OffsetDateTime) o).toInstant());
  }

  /**
   * 把 Iceberg {@link OffsetDateTime} 转为 {@link TimestampWritable}。
   *
   * @param o OffsetDateTime 对象，可为 null
   * @return TimestampWritable；o 为 null 时返回 null
   */
  @Override
  public TimestampWritable getPrimitiveWritableObject(Object o) {
    Timestamp ts = getPrimitiveJavaObject(o);
    return ts == null ? null : new TimestampWritable(ts);
  }

  /**
   * 深拷贝 Timestamp（含 nanos）与 OffsetDateTime；其他类型原样返回。
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
    } else if (o instanceof OffsetDateTime) {
      OffsetDateTime odt = (OffsetDateTime) o;
      return OffsetDateTime.ofInstant(odt.toInstant(), odt.getOffset());
    } else {
      return o;
    }
  }
}
