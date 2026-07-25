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

import java.util.UUID;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.StringObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.io.Text;

/**
 * 文件级说明：Iceberg UUID 类型在 Hive 侧的 ObjectInspector。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取呈现：把 Iceberg {@link UUID} 转为 Hive 期望的字符串 / {@link Text}。
 *   <li>写入转换：实现 {@link WriteObjectInspector}，把 Hive 字符串解析为 {@link UUID}。
 *   <li>对象拷贝：深拷贝 {@link Text}。
 * </ul>
 *
 * <p>设计意图：Hive 没有原生 UUID 类型，用字符串承载；本类在两端做 UUID <-> String 互转。 单例模式。
 *
 * <p>上下游关系：被 {@link IcebergObjectInspector#primitive} 在 UUID 分支创建；被 Hive SerDe 在读写 UUID 字段时调用。
 */
public class IcebergUUIDObjectInspector extends AbstractPrimitiveJavaObjectInspector
    implements StringObjectInspector, WriteObjectInspector {

  private static final IcebergUUIDObjectInspector INSTANCE = new IcebergUUIDObjectInspector();

  private IcebergUUIDObjectInspector() {
    super(TypeInfoFactory.stringTypeInfo);
  }

  /** 返回单例实例。 */
  public static IcebergUUIDObjectInspector get() {
    return INSTANCE;
  }

  /**
   * 把 {@link UUID} 转为字符串。
   *
   * @param o UUID 对象，可为 null
   * @return UUID 的字符串表示；o 为 null 时返回 null
   */
  @Override
  public String getPrimitiveJavaObject(Object o) {
    return o == null ? null : o.toString();
  }

  /**
   * 把 {@link UUID} 转为 {@link Text}。
   *
   * @param o UUID 对象，可为 null
   * @return Text 对象；o 为 null 时返回 null
   */
  @Override
  public Text getPrimitiveWritableObject(Object o) {
    String value = getPrimitiveJavaObject(o);
    return value == null ? null : new Text(value);
  }

  /**
   * 写入转换：把 Hive 字符串解析为 {@link UUID}。
   *
   * @param o Hive 侧字符串对象
   * @return 解析得到的 UUID；o 为 null 时返回 null
   */
  @Override
  public UUID convert(Object o) {
    return o == null ? null : UUID.fromString(o.toString());
  }

  /**
   * 深拷贝 {@link Text}；其他类型原样返回。
   *
   * @param o 待拷贝对象
   * @return 拷贝结果；o 为 null 时返回 null
   */
  @Override
  public Object copyObject(Object o) {
    if (o == null) {
      return null;
    }

    if (o instanceof Text) {
      return new Text((Text) o);
    } else {
      return o;
    }
  }
}
