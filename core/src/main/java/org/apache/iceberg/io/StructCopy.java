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
package org.apache.iceberg.io;

import org.apache.iceberg.StructLike;

/**
 * 文件级说明：{@link StructLike} 的不可变深拷贝工具。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：将一个 StructLike 的字段值复制到一个新的 Object 数组中，并递归拷贝嵌套的 StructLike， 生成一个不可变的副本。不支持拷贝 list/map 类型字段。
 *
 * <p>设计意图：Iceberg 写入流程中分区键（PartitionKey）等 StructLike 对象常被上游复用（同一个 对象在每次 write 调用时被重新填充）。若需要将某个分区键作为
 * Map 的 key 持久保存，必须先做深拷贝， 否则后续写入会覆盖已保存的 key 值。本类以最小开销实现该需求：仅拷贝 Object 引用和嵌套 Struct， 不做类型感知的深拷贝（对
 * list/map 不适用）。
 *
 * <p>上下游关系：被 FanoutWriter、ClusteredWriter、PartitionedFanoutWriter、BaseTaskWriter 等写入器在保存分区键时调用。
 */
class StructCopy implements StructLike {

  /**
   * 创建 StructLike 的不可变拷贝。
   *
   * @param struct 待拷贝的 StructLike，为 null 时返回 null
   * @return 拷贝后的不可变 StructLike，或 null
   */
  static StructLike copy(StructLike struct) {
    return struct != null ? new StructCopy(struct) : null;
  }

  private final Object[] values;

  /**
   * 私有构造：逐字段拷贝源 StructLike 的值，遇到嵌套 StructLike 时递归拷贝。
   *
   * @param toCopy 源 StructLike
   */
  private StructCopy(StructLike toCopy) {
    this.values = new Object[toCopy.size()];

    for (int i = 0; i < values.length; i += 1) {
      Object value = toCopy.get(i, Object.class);

      if (value instanceof StructLike) {
        values[i] = copy((StructLike) value);
      } else {
        values[i] = value;
      }
    }
  }

  /** 返回字段数量。 */
  @Override
  public int size() {
    return values.length;
  }

  /** 按位置读取字段值并强转为指定类型。 */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    return javaClass.cast(values[pos]);
  }

  /** 不可变副本不支持 set，调用即抛出异常。 */
  @Override
  public <T> void set(int pos, T value) {
    throw new UnsupportedOperationException("Struct copy cannot be modified");
  }
}
