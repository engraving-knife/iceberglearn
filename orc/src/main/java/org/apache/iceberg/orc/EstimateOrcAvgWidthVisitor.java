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
package org.apache.iceberg.orc;

import java.util.List;
import java.util.Optional;
import org.apache.orc.TypeDescription;

/**
 * 估算 ORC schema 各节点平均字节宽度的访问器。
 *
 * <p>所属模块：iceberg-orc。用于在缺少文件统计时，按字段类型粗略估算每行各列占用的 平均字节数，供 Iceberg 的 split 规划与文件大小估算使用。
 *
 * <p>职责：遍历 ORC TypeDescription 树，返回各节点的估算宽度（Integer）。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>struct 宽度 = 各子字段宽度之和。
 *   <li>list 宽度 = 元素宽度（近似单元素）。
 *   <li>map 宽度 = key 宽度 + value 宽度。
 *   <li>叶子节点按 ORC category 给定经验值：定长数值 8 字节、timestamp 12 字节、 变长字符串/二进制 128 字节、decimal = precision +
 *       2。
 *   <li>无 Iceberg id 的字段返回 0（不参与统计）。
 * </ul>
 *
 * <p>上下游关系：被 {@link OrcMetrics} 等需要估算列宽的场景调用。
 */
public class EstimateOrcAvgWidthVisitor extends OrcSchemaVisitor<Integer> {

  @Override
  /** struct 宽度 = 所有子字段宽度之和。 */
  public Integer record(TypeDescription record, List<String> names, List<Integer> fieldWidths) {
    return fieldWidths.stream().reduce(Integer::sum).orElse(0);
  }

  @Override
  /** list 宽度 = 元素宽度（按单元素估算）。 */
  public Integer list(TypeDescription array, Integer elementWidth) {
    return elementWidth;
  }

  @Override
  /** map 宽度 = key 宽度 + value 宽度。 */
  public Integer map(TypeDescription map, Integer keyWidth, Integer valueWidth) {
    return keyWidth + valueWidth;
  }

  @Override
  /**
   * 估算叶子节点宽度。
   *
   * <p>逻辑：无 Iceberg id 返回 0；否则按 ORC category 查表返回经验宽度： 定长类型 8 字节，timestamp 12 字节，变长类型 128
   * 字节，decimal = precision + 2。
   *
   * @throws IllegalArgumentException 出现未覆盖的 ORC 类型
   */
  public Integer primitive(TypeDescription primitive) {
    Optional<Integer> icebergIdOpt = ORCSchemaUtil.icebergID(primitive);

    if (!icebergIdOpt.isPresent()) {
      return 0;
    }

    switch (primitive.getCategory()) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
      case FLOAT:
      case BOOLEAN:
      case LONG:
      case DOUBLE:
      case DATE:
        return 8;
      case TIMESTAMP:
      case TIMESTAMP_INSTANT:
        return 12;
      case STRING:
      case VARCHAR:
      case BINARY:
        return 128;
      case DECIMAL:
        return primitive.getPrecision() + 2;
      default:
        throw new IllegalArgumentException("Can't handle " + primitive);
    }
  }
}
