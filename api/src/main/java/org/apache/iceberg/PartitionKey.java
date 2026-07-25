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
package org.apache.iceberg;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 文件级说明：分区值结构体，表示一行数据对应的分区键。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 与各引擎模块使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>基于 {@link PartitionSpec} 与输入 {@link Schema}，将一行数据转换为其分区值元组。
 *   <li>实现 {@link StructLike}，便于按位置读写分区值；实现 {@link Serializable} 以支持序列化。
 *   <li>提供 {@link #toPath()} 将分区键转为路径字符串，用于构造文件存储路径。
 * </ul>
 *
 * <p>设计意图：分区键是数据写入与读取时定位分区的核心。构造时预解析每个分区字段的 {@link org.apache.iceberg.util.SerializableFunction
 * 变换函数}与数据行字段访问器 （{@link Accessor}），后续 {@link #partition(StructLike)} 调用时只需逐字段应用变换即可， 避免每次分区都重新解析
 * schema，提升性能。变换函数与访问器在拷贝时共享（不可变）， 仅 partitionTuple 数组需要深拷贝。
 *
 * <p>上下游关系：由写入器（如 {@code WriterFactory}）与扫描规划组件创建； 被 {@link
 * PartitionSpec#partitionToPath(PartitionKey)} 等使用以生成物理路径。
 */
public class PartitionKey implements StructLike, Serializable {

  private final PartitionSpec spec;
  private final int size;
  private final Object[] partitionTuple;
  private final SerializableFunction[] transforms;
  private final Accessor<StructLike>[] accessors;

  /**
   * 根据分区 spec 与输入 schema 构造分区键。
   *
   * <p>逻辑：遍历 spec 中每个分区字段，从 inputSchema 获取对应源字段的访问器 accessor， 校验 accessor 非空，并将分区字段的变换函数绑定到
   * accessor 的类型上。transforms 与 accessors 数组按分区字段顺序存储，供后续 {@link #partition(StructLike)} 使用。
   *
   * @param spec 分区 spec
   * @param inputSchema 输入数据的 schema（用于定位源字段）
   * @throws IllegalArgumentException 若某分区字段的源字段在 inputSchema 中找不到对应访问器
   */
  @SuppressWarnings("unchecked")
  public PartitionKey(PartitionSpec spec, Schema inputSchema) {
    this.spec = spec;

    List<PartitionField> fields = spec.fields();
    this.size = fields.size();
    this.partitionTuple = new Object[size];
    this.transforms = new SerializableFunction[size];
    this.accessors = (Accessor<StructLike>[]) Array.newInstance(Accessor.class, size);

    Schema schema = spec.schema();
    for (int i = 0; i < size; i += 1) {
      PartitionField field = fields.get(i);
      Accessor<StructLike> accessor = inputSchema.accessorForField(field.sourceId());
      Preconditions.checkArgument(
          accessor != null,
          "Cannot build accessor for field: " + schema.findField(field.sourceId()));
      this.accessors[i] = accessor;
      this.transforms[i] = field.transform().bind(accessor.type());
    }
  }

  /**
   * 拷贝构造方法：基于已有 PartitionKey 创建副本。
   *
   * <p>设计要点：spec、transforms、accessors 等不可变引用直接共享；仅 partitionTuple 数组 通过 {@link System#arraycopy}
   * 做深拷贝，保证副本修改不影响原对象。
   *
   * @param toCopy 被拷贝的分区键
   */
  private PartitionKey(PartitionKey toCopy) {
    this.spec = toCopy.spec;
    this.size = toCopy.size;
    this.partitionTuple = new Object[toCopy.partitionTuple.length];
    this.transforms = toCopy.transforms;
    this.accessors = toCopy.accessors;

    System.arraycopy(toCopy.partitionTuple, 0, this.partitionTuple, 0, partitionTuple.length);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < partitionTuple.length; i += 1) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(partitionTuple[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  /** 返回本分区键的深拷贝副本。 */
  public PartitionKey copy() {
    return new PartitionKey(this);
  }

  /** 通过 {@link PartitionSpec#partitionToPath(PartitionKey)} 将本分区键转为路径字符串。 */
  public String toPath() {
    return spec.partitionToPath(this);
  }

  /**
   * 用指定数据行重新计算并替换本键的分区值。
   *
   * <p>逻辑：遍历每个分区字段，通过预绑定的 accessor 从 row 中取出源字段值， 再通过 transforms[i] 变换函数计算分区值，写入 partitionTuple[i]。
   *
   * @param row 一行数据（{@link StructLike}）
   */
  @SuppressWarnings("unchecked")
  public void partition(StructLike row) {
    for (int i = 0; i < partitionTuple.length; i += 1) {
      Function<Object, Object> transform = transforms[i];
      partitionTuple[i] = transform.apply(accessors[i].get(row));
    }
  }

  /** 返回分区字段的数量。 */
  @Override
  public int size() {
    return size;
  }

  /**
   * 按位置获取分区值并强转为指定 Java 类型。
   *
   * @param pos 字段位置
   * @param javaClass 期望的 Java 类型
   * @return 分区值
   */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    return javaClass.cast(partitionTuple[pos]);
  }

  /**
   * 按位置设置分区值。
   *
   * @param pos 字段位置
   * @param value 要设置的值
   */
  @Override
  public <T> void set(int pos, T value) {
    partitionTuple[pos] = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof PartitionKey)) {
      return false;
    }

    PartitionKey that = (PartitionKey) o;
    return Arrays.equals(partitionTuple, that.partitionTuple);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(partitionTuple);
  }
}
