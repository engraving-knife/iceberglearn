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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.apache.hadoop.hive.common.type.Timestamp;
import org.apache.hadoop.hive.serde2.io.TimestampWritableV2;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.TimestampObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;

/**
 * 文件级说明：Iceberg 无时区时间戳类型在 Hive3 中的对象检查器实现。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块，负责 Iceberg 与 Hive 之间的类型适配）。
 *
 * <p>职责：
 * <ul>
 *   <li>在 Hive3 序列化/反序列化框架中桥接 Iceberg 的 {@link LocalDateTime} 与 Hive 的
 *       {@link Timestamp}/{@link TimestampWritableV2} 类型。</li>
 *   <li>实现 {@link TimestampObjectInspector} 与 {@link WriteObjectInspector} 双向转换契约。</li>
 *   <li>以单例方式提供，避免重复创建对象。</li>
 * </ul>
 *
 * <p>设计意图：Hive3 的 Timestamp 类型不带时区信息，Iceberg 的时间戳同样无时区，
 * 二者天然对应。本类负责在两侧表达之间按 UTC 偏移进行瞬时值与纳秒精度的换算。
 *
 * <p>上下游关系：上游为 Hive SerDe 调用方，下游为 Iceberg 时间戳读写工具，
 * 用于 Hive3 引擎读写 Iceberg 表时的字段转换。
 */
public class IcebergTimestampObjectInspectorHive3 extends AbstractPrimitiveJavaObjectInspector
    implements TimestampObjectInspector, WriteObjectInspector {

  private static final IcebergTimestampObjectInspectorHive3 INSTANCE =
      new IcebergTimestampObjectInspectorHive3();

  /** 返回本检查器的单例实例。 */
  public static IcebergTimestampObjectInspectorHive3 get() {
    return INSTANCE;
  }

  /** 私有构造，使用 Hive 的 timestamp 类型信息初始化父类。 */
  private IcebergTimestampObjectInspectorHive3() {
    super(TypeInfoFactory.timestampTypeInfo);
  }

  /**
   * 将 Hive 的 {@link Timestamp} 反向转换为 Iceberg 的 {@link LocalDateTime}，用于写入 Iceberg 表。
   *
   * @param o Hive 侧的 Timestamp 对象，可为 null
   * @return Iceberg 侧的 LocalDateTime，输入为 null 时返回 null
   */
  @Override
  public LocalDateTime convert(Object o) {
    if (o == null) {
      return null;
    }
    Timestamp timestamp = (Timestamp) o;
    return LocalDateTime.ofEpochSecond(
        timestamp.toEpochSecond(), timestamp.getNanos(), ZoneOffset.UTC);
  }

  /**
   * 将 Iceberg 内部的 {@link LocalDateTime} 转换为 Hive 的 {@link Timestamp}。
   *
   * @param o Iceberg 侧的 LocalDateTime 对象，可为 null
   * @return Hive 侧的 Timestamp 对象（含纳秒），输入为 null 时返回 null
   */
  @Override
  public Timestamp getPrimitiveJavaObject(Object o) {
    if (o == null) {
      return null;
    }
    LocalDateTime time = (LocalDateTime) o;
    Timestamp timestamp = Timestamp.ofEpochMilli(time.toInstant(ZoneOffset.UTC).toEpochMilli());
    timestamp.setNanos(time.getNano());
    return timestamp;
  }

  /**
   * 将 Iceberg 时间戳转换为 Hive 的 {@link TimestampWritableV2} 可写包装对象。
   *
   * @param o Iceberg 侧的 LocalDateTime 对象，可为 null
   * @return Hive 可写包装对象，输入为 null 时返回 null
   */
  @Override
  public TimestampWritableV2 getPrimitiveWritableObject(Object o) {
    Timestamp ts = getPrimitiveJavaObject(o);
    return ts == null ? null : new TimestampWritableV2(ts);
  }

  /**
   * 复制时间戳对象，保留纳秒精度，避免 Hive 多行处理共享引用。
   *
   * @param o 待复制对象，可为 {@link Timestamp} 或 {@link LocalDateTime}
   * @return 新的副本；其他类型直接返回原对象
   */
  @Override
  public Object copyObject(Object o) {
    if (o == null) {
      return null;
    }

    if (o instanceof Timestamp) {
      Timestamp ts = (Timestamp) o;
      Timestamp copy = new Timestamp(ts);
      copy.setNanos(ts.getNanos());
      return copy;
    } else if (o instanceof LocalDateTime) {
      return LocalDateTime.of(((LocalDateTime) o).toLocalDate(), ((LocalDateTime) o).toLocalTime());
    } else {
      return o;
    }
  }
}
