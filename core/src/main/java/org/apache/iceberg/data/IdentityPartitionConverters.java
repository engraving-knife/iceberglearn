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

import org.apache.avro.generic.GenericData;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.DateTimeUtil;

/**
 * 恒等分区值转换器：将 Iceberg 内部存储表示转换为通用 Java 值。
 *
 * <p>所属模块：iceberg-core，data 包内的分区值转换工具。
 *
 * <p>职责：将分区列的内部存储表示（如 long 微秒、int 天数）转换为 Iceberg 通用 Java 值 （如
 * LocalTime、LocalDate），用于在读取/写入时与外部系统交互。
 *
 * <p>设计意图：Iceberg 内部以紧凑形式存储时间类型（微秒/天数），但在与引擎或用户交互时 需转为 Java 时间 API 类型。本类集中处理这类转换，与 {@link
 * org.apache.iceberg.util.DateTimeUtil} 配合完成。FIXED 类型从 Avro 的 GenericData.Fixed 提取原始字节数组。
 *
 * <p>上下游关系：被分区值读取/转换流程调用，将内部表示转为通用值。
 */
public class IdentityPartitionConverters {
  private IdentityPartitionConverters() {}

  /**
   * 将内部存储表示转换为 Iceberg 通用值。
   *
   * <p>逻辑：null 直接返回；STRING 转为 toString；TIME 从微秒转 LocalTime；DATE 从天数转 LocalDate； TIMESTAMP 按是否带时区分别转
   * OffsetDateTime 或 LocalDateTime；FIXED 从 Avro GenericData.Fixed 提取字节数组；其余类型原样返回。
   *
   * @param type 字段类型
   * @param value 内部存储表示的值
   * @return 转换后的通用 Java 值
   */
  public static Object convertConstant(Type type, Object value) {
    if (value == null) {
      return null;
    }

    switch (type.typeId()) {
      case STRING:
        return value.toString();
      case TIME:
        return DateTimeUtil.timeFromMicros((Long) value);
      case DATE:
        return DateTimeUtil.dateFromDays((Integer) value);
      case TIMESTAMP:
        if (((Types.TimestampType) type).shouldAdjustToUTC()) {
          return DateTimeUtil.timestamptzFromMicros((Long) value);
        } else {
          return DateTimeUtil.timestampFromMicros((Long) value);
        }
      case FIXED:
        if (value instanceof GenericData.Fixed) {
          return ((GenericData.Fixed) value).bytes();
        }
        return value;
      default:
    }
    return value;
  }
}
