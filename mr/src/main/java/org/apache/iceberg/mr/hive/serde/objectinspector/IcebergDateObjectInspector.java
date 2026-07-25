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

import java.sql.Date;
import java.time.LocalDate;
import org.apache.hadoop.hive.serde2.io.DateWritable;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.DateObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.iceberg.util.DateTimeUtil;

/**
 * 文件级说明：Iceberg DATE 类型在 Hive 侧的 ObjectInspector（Hive 2 版本实现）。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取呈现：把 Iceberg {@link LocalDate} 转为 Hive 期望的 {@link java.sql.Date} / {@link DateWritable}。
 *   <li>写入转换：实现 {@link WriteObjectInspector}，把 {@link java.sql.Date} 转为 {@link LocalDate}。
 *   <li>对象拷贝：深拷贝 Date / LocalDate。
 * </ul>
 *
 * <p>设计意图：Iceberg 用 {@link LocalDate} 表示日期，Hive 用 {@link java.sql.Date}；
 * 本类做两者互转，避免时区相关的问题。单例模式。Hive 3 由 Hive3 子类替代以适配 API 变更。
 *
 * <p>上下游关系：被 {@link IcebergObjectInspector#DATE_INSPECTOR} 通过反射加载；被 Hive SerDe 在读写 DATE 字段时调用。
 */
public final class IcebergDateObjectInspector extends AbstractPrimitiveJavaObjectInspector
    implements DateObjectInspector, WriteObjectInspector {

  private static final IcebergDateObjectInspector INSTANCE = new IcebergDateObjectInspector();

  /** 返回单例实例。 */
  public static IcebergDateObjectInspector get() {
    return INSTANCE;
  }

  private IcebergDateObjectInspector() {
    super(TypeInfoFactory.dateTypeInfo);
  }

  /**
   * 把 Iceberg {@link LocalDate} 转为 Hive {@link java.sql.Date}。
   *
   * @param o LocalDate 对象，可为 null
   * @return java.sql.Date；o 为 null 时返回 null
   */
  @Override
  public Date getPrimitiveJavaObject(Object o) {
    return o == null ? null : Date.valueOf((LocalDate) o);
  }

  /**
   * 把 Iceberg {@link LocalDate} 转为 {@link DateWritable}。
   *
   * @param o LocalDate 对象，可为 null
   * @return DateWritable；o 为 null 时返回 null
   */
  @Override
  public DateWritable getPrimitiveWritableObject(Object o) {
    return o == null ? null : new DateWritable(DateTimeUtil.daysFromDate((LocalDate) o));
  }

  /**
   * 深拷贝 Date / LocalDate；其他类型原样返回。
   *
   * @param o 待拷贝对象
   * @return 拷贝结果；o 为 null 时返回 null
   */
  @Override
  public Object copyObject(Object o) {
    if (o == null) {
      return null;
    }

    if (o instanceof Date) {
      return new Date(((Date) o).getTime());
    } else if (o instanceof LocalDate) {
      return LocalDate.of(
          ((LocalDate) o).getYear(), ((LocalDate) o).getMonth(), ((LocalDate) o).getDayOfMonth());
    } else {
      return o;
    }
  }

  /**
   * 写入转换：把 Hive {@link java.sql.Date} 转为 Iceberg {@link LocalDate}。
   *
   * @param o java.sql.Date 对象
   * @return LocalDate；o 为 null 时返回 null
   */
  @Override
  public LocalDate convert(Object o) {
    return o == null ? null : ((Date) o).toLocalDate();
  }
}
