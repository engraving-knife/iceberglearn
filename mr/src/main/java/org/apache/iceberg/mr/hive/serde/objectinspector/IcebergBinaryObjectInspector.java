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

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.AbstractPrimitiveJavaObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.BinaryObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.io.BytesWritable;
import org.apache.iceberg.util.ByteBuffers;

/**
 * 文件级说明：Iceberg BINARY 类型在 Hive 侧的 ObjectInspector。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取呈现：把 Iceberg 内部 {@link ByteBuffer} 转为 Hive 期望的 byte[] 或 {@link BytesWritable}。
 *   <li>写入转换：实现 {@link WriteObjectInspector}，把 Hive byte[] 包装为 {@link ByteBuffer} 以写入 Iceberg
 *       Record。
 *   <li>对象拷贝：实现 {@link #copyObject(Object)} 提供深拷贝，避免共享可变状态。
 * </ul>
 *
 * <p>设计意图：Iceberg 用 {@link ByteBuffer} 表示二进制，Hive 用 byte[]；本类做两者互转。 采用单例（{@link #INSTANCE}）避免重复创建。
 *
 * <p>上下游关系：被 {@link IcebergObjectInspector#primitive} 在 BINARY 分支创建并返回； 被 Hive SerDe 在读写 BINARY
 * 字段时调用。
 */
public class IcebergBinaryObjectInspector extends AbstractPrimitiveJavaObjectInspector
    implements BinaryObjectInspector, WriteObjectInspector {

  private static final IcebergBinaryObjectInspector INSTANCE = new IcebergBinaryObjectInspector();

  /** 返回单例实例。 */
  public static IcebergBinaryObjectInspector get() {
    return INSTANCE;
  }

  private IcebergBinaryObjectInspector() {
    super(TypeInfoFactory.binaryTypeInfo);
  }

  /**
   * 把 Iceberg 的 {@link ByteBuffer} 转为 byte[]。
   *
   * @param o ByteBuffer 对象
   * @return 对应的 byte[]
   */
  @Override
  public byte[] getPrimitiveJavaObject(Object o) {
    return ByteBuffers.toByteArray((ByteBuffer) o);
  }

  /**
   * 把 Iceberg 的 {@link ByteBuffer} 转为 {@link BytesWritable}。
   *
   * @param o ByteBuffer 对象，可为 null
   * @return BytesWritable，o 为 null 时返回 null
   */
  @Override
  public BytesWritable getPrimitiveWritableObject(Object o) {
    return o == null ? null : new BytesWritable(getPrimitiveJavaObject(o));
  }

  /**
   * 深拷贝对象：byte[] 与 {@link ByteBuffer} 各自复制，其他类型原样返回。
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
    } else if (o instanceof ByteBuffer) {
      ByteBuffer copy =
          ByteBuffer.wrap(
              ((ByteBuffer) o).array(), ((ByteBuffer) o).arrayOffset(), ((ByteBuffer) o).limit());
      return copy;
    } else {
      return o;
    }
  }

  /**
   * 写入转换：把 Hive byte[] 包装为 {@link ByteBuffer}。
   *
   * @param o Hive 侧 byte[]
   * @return 包装后的 ByteBuffer；o 为 null 时返回 null
   */
  @Override
  public ByteBuffer convert(Object o) {
    return o == null ? null : ByteBuffer.wrap((byte[]) o);
  }
}
