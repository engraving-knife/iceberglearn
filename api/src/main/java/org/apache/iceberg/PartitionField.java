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
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.transforms.Transform;

/**
 * 表示 {@link PartitionSpec} 中的单个分区字段。
 *
 * <p>所属模块：iceberg-api（分区抽象层）。
 *
 * <p>职责：把一个源 schema 字段通过 {@link Transform} 映射为一个分区字段，记录其来源字段 ID、 分区字段 ID、名称与所用变换。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>分区字段 ID 在表的所有 partition spec 中全局唯一，从 1000 起分配，便于跨 spec 引用。
 *   <li>{@link #equals(Object)} 中对 transform 的比较使用其字符串形式而非引用，以兼容 不同实例但语义相同的 transform。
 * </ul>
 *
 * <p>上下游关系：由 {@link PartitionSpec} 持有；被扫描规划、分区值计算、文件写入等模块使用。
 */
public class PartitionField implements Serializable {
  private final int sourceId;
  private final int fieldId;
  private final String name;
  private final Transform<?, ?> transform;

  /**
   * 构造分区字段。
   *
   * @param sourceId 源 schema 字段 ID
   * @param fieldId 分区字段 ID（全表唯一）
   * @param name 分区字段名
   * @param transform 由源值生成分区值的变换
   */
  PartitionField(int sourceId, int fieldId, String name, Transform<?, ?> transform) {
    this.sourceId = sourceId;
    this.fieldId = fieldId;
    this.name = name;
    this.transform = transform;
  }

  /** 返回本分区字段在表 schema 中对应的源字段 ID。 */
  public int sourceId() {
    return sourceId;
  }

  /** 返回分区字段 ID（在表所有 partition spec 中全局唯一）。 */
  public int fieldId() {
    return fieldId;
  }

  /** 返回分区字段名。 */
  public String name() {
    return name;
  }

  /** 返回由源值生成分区值所使用的 {@link Transform}。 */
  public Transform<?, ?> transform() {
    return transform;
  }

  @Override
  public String toString() {
    return fieldId + ": " + name + ": " + transform + "(" + sourceId + ")";
  }

  /**
   * 相等性判断：sourceId、fieldId、name 相等且 transform 的字符串形式相等。
   *
   * <p>设计要点：transform 比较使用 toString 而非引用相等，以使不同实例但语义相同的 transform 也被认为相等。
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof PartitionField)) {
      return false;
    }

    PartitionField that = (PartitionField) other;
    return sourceId == that.sourceId
        && fieldId == that.fieldId
        && name.equals(that.name)
        && transform.toString().equals(that.transform.toString());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(sourceId, fieldId, name, transform);
  }
}
