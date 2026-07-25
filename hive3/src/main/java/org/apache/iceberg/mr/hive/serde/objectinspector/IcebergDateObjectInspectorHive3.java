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

import java.time.LocalDate;
import org.apache.hadoop.hive.common.type.Date;
import org.apache.hadoop.hive.serde2.io.DateWritableV2;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.DateObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.iceberg.util.DateTimeUtil;

/**
 * 文件级说明：Iceberg 日期类型在 Hive3 中的对象检查器（ObjectInspector）实现。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块，负责 Iceberg 与 Hive 之间的类型适配）。
 *
 * <p>职责：
 * <ul>
 *   <li>在 Hive3 序列化/反序列化框架中桥接 Iceberg 的 {@link LocalDate} 与 Hive 的
 *       {@link Date}/{@link DateWritableV2} 类型。</li>
 *   <li>实现 {@link DateObjectInspector} 与 {@link WriteObjectInspector} 双向转换契约。</li>
 *   <li>以单例方式提供，避免重复创建对象。</li>
 * </ul>
 *
 * <p>设计意图：Hive SerDe 框架通过 ObjectInspector 读写底层字段，本类作为 Iceberg
 * 内部 {@code LocalDate} 表达与 Hive 期望的 {@code Date} 表达之间的适配层，
 * 在 Hive3 引擎读写 Iceberg 表时被 {@code IcebergObjectInspector} 工厂装配使用。
 *
 * <p>上下游关系：上游为 Hive SerDe 调用方（IcebergStorageSerDe），下游为 Iceberg
 * 的 {@link DateTimeUtil} 工具类，用于日期到天数的换算。
 */
public final class IcebergDateObjectInspectorHive3 extends AbstractPrimitiveJavaObjectInspector
    implements DateObjectInspector, WriteObjectInspector {

  private static final IcebergDateObjectInspectorHive3 INSTANCE =
      new IcebergDateObjectInspectorHive3();

  /** 返回本检查器的单例实例。 */
  public static IcebergDateObjectInspectorHive3 get() {
    return INSTANCE;
  }

  /** 私有构造，使用 Hive 的 date 类型信息初始化父类。 */
  private IcebergDateObjectInspectorHive3() {
    super(TypeInfoFactory.dateTypeInfo);
  }

  /**
   * 将 Iceberg 内部的 {@link LocalDate} 转换为 Hive 的 {@link Date}。
   *
   * @param o Iceberg 侧的 LocalDate 对象，可为 null
   * @return Hive 侧的 Date 对象，输入为 null 时返回 null
   */
  @Override
  public Date getPrimitiveJavaObject(Object o) {
    if (o == null) {
      return null;
    }
    LocalDate date = (LocalDate) o;
    return Date.ofEpochDay(DateTimeUtil.daysFromDate(date));
  }

  /**
   * 将 Iceberg 内部的 {@link LocalDate} 转换为 Hive 的 {@link DateWritableV2}。
   *
   * @param o Iceberg 侧的 LocalDate 对象，可为 null
   * @return Hive 可写包装对象，输入为 null 时返回 null
   */
  @Override
  public DateWritableV2 getPrimitiveWritableObject(Object o) {
    return o == null ? null : new DateWritableV2(DateTimeUtil.daysFromDate((LocalDate) o));
  }

  /**
   * 复制日期对象，保证 Hive SerDe 在多行处理时不会共享引用。
   *
   * @param o 待复制对象，可为 {@link Date} 或 {@link LocalDate}
   * @return 新的副本；其他类型直接返回原对象
   */
  @Override
  public Object copyObject(Object o) {
    if (o == null) {
      return null;
    }

    if (o instanceof Date) {
      return new Date((Date) o);
    } else if (o instanceof LocalDate) {
      return LocalDate.of(
          ((LocalDate) o).getYear(), ((LocalDate) o).getMonth(), ((LocalDate) o).getDayOfMonth());
    } else {
      return o;
    }
  }

  /**
   * 将 Hive 的 {@link Date} 反向转换为 Iceberg 的 {@link LocalDate}，用于写入 Iceberg 表。
   *
   * @param o Hive 侧的 Date 对象，可为 null
   * @return Iceberg 侧的 LocalDate，输入为 null 时返回 null
   */
  @Override
  public LocalDate convert(Object o) {
    if (o == null) {
      return null;
    }

    Date date = (Date) o;
    return LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
  }
}
