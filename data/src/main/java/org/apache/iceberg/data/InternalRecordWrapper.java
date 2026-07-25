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
package org.apache.iceberg.data;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.function.Function;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.DateTimeUtil;

/**
 * 记录包装器：把外部记录（如 {@link Record}）适配为 Iceberg 内部 {@link StructLike}， 并在读取字段时按类型把 Java 时间/字节数组等对象转换为
 * Iceberg 内部表示。
 *
 * <p>所属模块：iceberg-data（向 JVM 应用提供基于 {@link Record} 等通用模型的 Iceberg 表读写支持； 本类是读写路径上的记录适配层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link StructLike}，把被包装记录的字段访问转发到底层记录。
 *   <li>为 DATE/TIME/TIMESTAMP/FIXED/STRUCT 等类型预置转换函数，读取时把 Java 原生对象 （{@link LocalDate} / {@link
 *       LocalTime} / {@link LocalDateTime} / {@link OffsetDateTime} / {@code byte[]} 等）转为 Iceberg
 *       内部值（天数、微秒、{@link ByteBuffer} 等）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>转换函数数组按字段位置索引，读取时 O(1) 定位，避免每次反射或类型判断。
 *   <li>对 STRUCT 类型递归构造嵌套 wrapper，支持嵌套结构转换。
 *   <li>不支持 {@link #set}，仅作只读视图；null 值直接返回避免转换函数处理 null。
 * </ul>
 *
 * <p>上下游关系：被 {@link DeleteFilter}（等值删除集合构建）、{@link GenericReader} （residual 过滤）等读写路径使用，用于把 Record
 * 适配为求值/比较所需的 StructLike。
 */
public class InternalRecordWrapper implements StructLike {
  private final Function<Object, Object>[] transforms;
  private StructLike wrapped = null;

  @SuppressWarnings("unchecked")
  /**
   * 按结构类型构造包装器，预先生成每个字段的类型转换函数。
   *
   * @param struct 结构类型，用于枚举字段并按类型生成转换函数
   */
  public InternalRecordWrapper(Types.StructType struct) {
    this(
        struct.fields().stream()
            .map(field -> converter(field.type()))
            .toArray(
                length -> (Function<Object, Object>[]) Array.newInstance(Function.class, length)));
  }

  private InternalRecordWrapper(Function<Object, Object>[] transforms) {
    this.transforms = transforms;
  }

  /**
   * 按 Iceberg 类型生成对应的 Java 对象转换函数。
   *
   * <p>逻辑：根据 {@link Type#typeId()} 分支：
   *
   * <ul>
   *   <li>DATE：{@link LocalDate} 转自纪元起的天数。
   *   <li>TIME：{@link LocalTime} 转微秒。
   *   <li>TIMESTAMP：带时区则 {@link OffsetDateTime} 转微秒，否则 {@link LocalDateTime} 转微秒。
   *   <li>FIXED：{@code byte[]} 包装为 {@link ByteBuffer}。
   *   <li>STRUCT：递归构造嵌套 {@link InternalRecordWrapper}，返回“包装子结构”的函数。
   *   <li>其它类型返回 null（表示无需转换，直接透传）。
   * </ul>
   *
   * @param type Iceberg 字段类型
   * @return 转换函数；无需转换时返回 null
   */
  private static Function<Object, Object> converter(Type type) {
    switch (type.typeId()) {
      case DATE:
        return date -> DateTimeUtil.daysFromDate((LocalDate) date);
      case TIME:
        return time -> DateTimeUtil.microsFromTime((LocalTime) time);
      case TIMESTAMP:
        if (((Types.TimestampType) type).shouldAdjustToUTC()) {
          return timestamp -> DateTimeUtil.microsFromTimestamptz((OffsetDateTime) timestamp);
        } else {
          return timestamp -> DateTimeUtil.microsFromTimestamp((LocalDateTime) timestamp);
        }
      case FIXED:
        return bytes -> ByteBuffer.wrap((byte[]) bytes);
      case STRUCT:
        InternalRecordWrapper wrapper = new InternalRecordWrapper(type.asStructType());
        return struct -> wrapper.wrap((StructLike) struct);
      default:
    }
    return null;
  }

  /** 返回当前被包装的 {@link StructLike} 记录。 */
  public StructLike get() {
    return wrapped;
  }

  /**
   * 基于同一套转换函数创建一个新包装器并绑定到指定记录（共享 transforms 数组）。
   *
   * <p>设计要点：复制而非复用本对象，避免多线程/多记录并发包装时互相覆盖 wrapped 字段。
   *
   * @param record 待包装的记录
   * @return 新的包装器（已 wrap）
   */
  public InternalRecordWrapper copyFor(StructLike record) {
    return new InternalRecordWrapper(transforms).wrap(record);
  }

  /**
   * 绑定当前包装器到指定记录（可复用本对象，避免反复创建）。
   *
   * @param record 待包装的记录
   * @return this
   */
  public InternalRecordWrapper wrap(StructLike record) {
    this.wrapped = record;
    return this;
  }

  @Override
  public int size() {
    return wrapped.size();
  }

  /**
   * 按位置读取字段值，必要时应用类型转换。
   *
   * <p>逻辑：若该位置存在转换函数且值非 null，则先用转换函数处理再强转返回； 否则直接从底层记录按 javaClass 取值。null 值直接返回 null（转换函数不处理 null）。
   *
   * @param pos 字段位置
   * @param javaClass 期望的 Java 类型
   * @param <T> 返回值类型
   * @return 字段值
   */
  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    if (transforms[pos] != null) {
      Object value = wrapped.get(pos, Object.class);
      if (value == null) {
        // transforms function don't allow to handle null values, so just return null here.
        return null;
      } else {
        return javaClass.cast(transforms[pos].apply(value));
      }
    }
    return wrapped.get(pos, javaClass);
  }

  /**
   * 不支持写入：本包装器为只读视图。
   *
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public <T> void set(int pos, T value) {
    throw new UnsupportedOperationException("Cannot update InternalRecordWrapper");
  }
}
