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

import java.util.Arrays;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.BinaryObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.io.BytesWritable;

/**
 * 文件级说明：Iceberg FIXED 类型在 Hive 侧的 ObjectInspector。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取呈现：FIXED 类型底层即定长 byte[]，直接以 byte[] / {@link BytesWritable} 暴露给 Hive。
 *   <li>写入转换：实现 {@link WriteObjectInspector}，原样返回 byte[]（FIXED 不需要 ByteBuffer 包装）。
 *   <li>对象拷贝：深拷贝 byte[] 避免共享。
 * </ul>
 *
 * <p>设计意图：与 {@link IcebergBinaryObjectInspector} 类似，但 Iceberg 的 FIXED 字段内部 直接以 byte[] 表示，无需
 * ByteBuffer 中转，因此转换更简单。单例模式。
 *
 * <p>上下游关系：被 {@link IcebergObjectInspector#primitive} 在 FIXED 分支创建；被 Hive SerDe 在读写 FIXED 字段时调用。
 */
public class IcebergFixedObjectInspector extends AbstractPrimitiveJavaObjectInspector
    implements BinaryObjectInspector, WriteObjectInspector {

  private static final IcebergFixedObjectInspector INSTANCE = new IcebergFixedObjectInspector();

  /** 返回单例实例。 */
  public static IcebergFixedObjectInspector get() {
    return INSTANCE;
  }

  private IcebergFixedObjectInspector() {
    super(TypeInfoFactory.binaryTypeInfo);
  }

  /** FIXED 类型即 byte[]，直接返回。 */
  @Override
  public byte[] getPrimitiveJavaObject(Object o) {
    return (byte[]) o;
  }

  /**
   * 转为 {@link BytesWritable}。
   *
   * @param o byte[] 对象，可为 null
   * @return BytesWritable；o 为 null 时返回 null
   */
  @Override
  public BytesWritable getPrimitiveWritableObject(Object o) {
    return o == null ? null : new BytesWritable(getPrimitiveJavaObject(o));
  }

  /** 写入转换：FIXED 类型原样返回 byte[]。 */
  @Override
  public byte[] convert(Object o) {
    return o == null ? null : (byte[]) o;
  }

  /**
   * 深拷贝 byte[]；其他类型原样返回。
   *
   * @param o 待拷贝对象
   * @return 拷贝结果；o 为 null 时返回 null
   */
  @Override
  public Object copyObject(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof byte[]) {
      byte[] bytes = (byte[]) o;
      return Arrays.copyOf(bytes, bytes.length);
    } else {
      return o;
    }
  }
}
