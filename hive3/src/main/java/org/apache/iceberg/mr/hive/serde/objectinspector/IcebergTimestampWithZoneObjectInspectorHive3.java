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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.apache.hadoop.hive.common.type.TimestampTZ;
import org.apache.hadoop.hive.serde2.io.TimestampLocalTZWritable;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.TimestampLocalTZObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;

/**
 * 文件级说明：Iceberg 带时区时间戳类型在 Hive3 中的对象检查器实现。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块，负责 Iceberg 与 Hive 之间的类型适配）。
 *
 * <p>职责：
 * <ul>
 *   <li>在 Hive3 序列化/反序列化框架中桥接 Iceberg 的 {@link OffsetDateTime} 与 Hive 的
 *       {@link TimestampTZ}/{@link TimestampLocalTZWritable} 类型。</li>
 *   <li>实现 {@link TimestampLocalTZObjectInspector} 与 {@link WriteObjectInspector} 双向转换契约。</li>
 *   <li>以单例方式提供，避免重复创建对象。</li>
 * </ul>
 *
 * <p>设计意图：Hive3 的 TimestampTZ 以 UTC 时区存储瞬时值并保留时区信息，
 * Iceberg 的带时区时间戳同样表达绝对瞬时值。本类负责在两侧表达之间进行偏移量与
 * UTC 标准化的双向转换，保证读写一致性。
 *
 * <p>上下游关系：上游为 Hive SerDe 调用方，下游为 Iceberg 时间戳读写工具，
 * 用于 Hive3 引擎读写带时区时间戳字段时的字段转换。
 */
public class IcebergTimestampWithZoneObjectInspectorHive3
    extends AbstractPrimitiveJavaObjectInspector
    implements TimestampLocalTZObjectInspector, WriteObjectInspector {

  private static final IcebergTimestampWithZoneObjectInspectorHive3 INSTANCE =
      new IcebergTimestampWithZoneObjectInspectorHive3();

  /** 返回本检查器的单例实例。 */
  public static IcebergTimestampWithZoneObjectInspectorHive3 get() {
    return INSTANCE;
  }

  /** 私有构造，使用 Hive 的 timestampLocalTZ 类型信息初始化父类。 */
  private IcebergTimestampWithZoneObjectInspectorHive3() {
    super(TypeInfoFactory.timestampLocalTZTypeInfo);
  }

  /**
   * 将 Hive 的 {@link TimestampTZ} 反向转换为 Iceberg 的 {@link OffsetDateTime}，用于写入 Iceberg 表。
   *
   * @param o Hive 侧的 TimestampTZ 对象，可为 null
   * @return Iceberg 侧的 OffsetDateTime，输入为 null 时返回 null
   */
  @Override
  public OffsetDateTime convert(Object o) {
    if (o == null) {
      return null;
    }
    ZonedDateTime zdt = ((TimestampTZ) o).getZonedDateTime();
    return OffsetDateTime.of(zdt.toLocalDateTime(), zdt.getOffset());
  }

  /**
   * 将 Iceberg 内部的 {@link OffsetDateTime} 转换为 Hive 的 {@link TimestampTZ}。
   *
   * <p>逻辑：将任意偏移量的瞬时值规范化为 UTC 时区下的 ZonedDateTime，
   * 再构造 Hive3 的 TimestampTZ 对象。
   *
   * @param o Iceberg 侧的 OffsetDateTime 对象，可为 null
   * @return Hive 侧的 TimestampTZ 对象，输入为 null 时返回 null
   */
  @Override
  public TimestampTZ getPrimitiveJavaObject(Object o) {
    if (o == null) {
      return null;
    }
    OffsetDateTime odt = (OffsetDateTime) o;
    ZonedDateTime zdt = odt.atZoneSameInstant(ZoneOffset.UTC);
    return new TimestampTZ(zdt);
  }

  /**
   * 将 Iceberg 时间戳转换为 Hive 的 {@link TimestampLocalTZWritable} 可写包装对象。
   *
   * @param o Iceberg 侧的 OffsetDateTime 对象，可为 null
   * @return Hive 可写包装对象，输入为 null 时返回 null
   */
  @Override
  public TimestampLocalTZWritable getPrimitiveWritableObject(Object o) {
    TimestampTZ tsTz = getPrimitiveJavaObject(o);
    return tsTz == null ? null : new TimestampLocalTZWritable(tsTz);
  }

  /**
   * 复制带时区时间戳对象，保留时区信息与瞬时值，避免 Hive 多行处理共享引用。
   *
   * @param o 待复制对象，可为 {@link TimestampTZ} 或 {@link OffsetDateTime}
   * @return 新的副本；其他类型直接返回原对象
   */
  @Override
  public Object copyObject(Object o) {
    if (o instanceof TimestampTZ) {
      TimestampTZ ts = (TimestampTZ) o;
      return new TimestampTZ(ts.getZonedDateTime());
    } else if (o instanceof OffsetDateTime) {
      OffsetDateTime odt = (OffsetDateTime) o;
      return OffsetDateTime.of(odt.toLocalDateTime(), odt.getOffset());
    } else {
      return o;
    }
  }
}
