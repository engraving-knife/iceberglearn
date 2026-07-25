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
package org.apache.iceberg.transforms;

import java.util.List;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 分区规约（PartitionSpec）访问者接口：按分区字段的变换类型分派回调。
 *
 * <p>所属模块：iceberg-api（被 core 与各引擎用于遍历分区字段并生成引擎特定的表达式/输出）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为 identity/bucket/truncate/year/month/day/hour/alwaysNull/unknown 各类变换提供回调方法。
 *   <li>提供静态 {@link #visit(PartitionSpec, PartitionSpecVisitor)} 遍历分区规约的所有字段。
 * </ul>
 *
 * <p>设计意图：访问者模式把"变换类型"与"对变换的处理"解耦，调用方只需实现关心的回调， 其余走默认抛异常实现。每个变换提供带 fieldId 与不带 fieldId
 * 两套重载，便于不同场景使用。
 *
 * <p>上下游关系：被 core 的扫描规划、各引擎的分区下推、元数据序列化等使用；输入依赖 {@link PartitionSpec}、{@link PartitionField}、{@link
 * Schema}。
 *
 * @param <T> 访问者回调的返回类型
 */
public interface PartitionSpecVisitor<T> {
  /**
   * 访问 identity 分区字段（带 fieldId）。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T identity(int fieldId, String sourceName, int sourceId) {
    return identity(sourceName, sourceId);
  }

  /**
   * 访问 identity 分区字段（不带 fieldId），默认抛异常。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T identity(String sourceName, int sourceId) {
    throw new UnsupportedOperationException("Identity transform is not supported");
  }

  /**
   * 访问 bucket 分区字段（带 fieldId）。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param numBuckets 桶数量
   * @return 回调结果
   */
  default T bucket(int fieldId, String sourceName, int sourceId, int numBuckets) {
    return bucket(sourceName, sourceId, numBuckets);
  }

  /**
   * 访问 bucket 分区字段（不带 fieldId），默认抛异常。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param numBuckets 桶数量
   * @return 回调结果
   */
  default T bucket(String sourceName, int sourceId, int numBuckets) {
    throw new UnsupportedOperationException("Bucket transform is not supported");
  }

  /**
   * 访问 truncate 分区字段（带 fieldId）。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param width 截断宽度
   * @return 回调结果
   */
  default T truncate(int fieldId, String sourceName, int sourceId, int width) {
    return truncate(sourceName, sourceId, width);
  }

  /**
   * 访问 truncate 分区字段（不带 fieldId），默认抛异常。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param width 截断宽度
   * @return 回调结果
   */
  default T truncate(String sourceName, int sourceId, int width) {
    throw new UnsupportedOperationException("Truncate transform is not supported");
  }

  /**
   * 访问 year 分区字段（带 fieldId）。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T year(int fieldId, String sourceName, int sourceId) {
    return year(sourceName, sourceId);
  }

  /**
   * 访问 year 分区字段（不带 fieldId），默认抛异常。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T year(String sourceName, int sourceId) {
    throw new UnsupportedOperationException("Year transform is not supported");
  }

  /**
   * 访问 month 分区字段（带 fieldId）。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T month(int fieldId, String sourceName, int sourceId) {
    return month(sourceName, sourceId);
  }

  /**
   * 访问 month 分区字段（不带 fieldId），默认抛异常。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T month(String sourceName, int sourceId) {
    throw new UnsupportedOperationException("Month transform is not supported");
  }

  /**
   * 访问 day 分区字段（带 fieldId）。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T day(int fieldId, String sourceName, int sourceId) {
    return day(sourceName, sourceId);
  }

  /**
   * 访问 day 分区字段（不带 fieldId），默认抛异常。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T day(String sourceName, int sourceId) {
    throw new UnsupportedOperationException("Day transform is not supported");
  }

  /**
   * 访问 hour 分区字段（带 fieldId）。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T hour(int fieldId, String sourceName, int sourceId) {
    return hour(sourceName, sourceId);
  }

  /**
   * 访问 hour 分区字段（不带 fieldId），默认抛异常。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T hour(String sourceName, int sourceId) {
    throw new UnsupportedOperationException("Hour transform is not supported");
  }

  /**
   * 访问 alwaysNull（void）分区字段，默认抛异常。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @return 回调结果
   */
  default T alwaysNull(int fieldId, String sourceName, int sourceId) {
    throw new UnsupportedOperationException("Void transform is not supported");
  }

  /**
   * 访问未知变换的分区字段，默认抛异常。
   *
   * @param fieldId 分区字段 ID
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param transform 变换的字符串表示
   * @return 回调结果
   */
  default T unknown(int fieldId, String sourceName, int sourceId, String transform) {
    throw new UnsupportedOperationException(
        String.format("Unknown transform %s is not supported", transform));
  }

  /**
   * 遍历分区规约的所有字段并收集访问结果。
   *
   * <p>逻辑：按 spec.fields() 顺序逐个调用 {@link #visit(Schema, PartitionField, PartitionSpecVisitor)}，
   * 把结果收集到 List 返回。
   *
   * @param spec 待遍历的分区规约
   * @param visitor 访问者
   * @param <R> 返回类型
   * @return 每个字段访问结果的列表
   */
  static <R> List<R> visit(PartitionSpec spec, PartitionSpecVisitor<R> visitor) {
    List<R> results = Lists.newArrayListWithExpectedSize(spec.fields().size());

    for (PartitionField field : spec.fields()) {
      results.add(visit(spec.schema(), field, visitor));
    }

    return results;
  }

  /**
   * 访问单个分区字段，按变换类型分派到对应回调。
   *
   * <p>逻辑：先查源列名，再按 transform 的实际类型（Identity/Bucket/Truncate/Dates.YEAR/Timestamps.YEAR/
   * Years/.../VoidTransform/UnknownTransform）分派到 visitor 的对应方法；都不匹配抛 UnsupportedOperationException。
   *
   * @param schema 表 schema
   * @param field 分区字段
   * @param visitor 访问者
   * @param <R> 返回类型
   * @return 访问结果
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  static <R> R visit(Schema schema, PartitionField field, PartitionSpecVisitor<R> visitor) {
    String sourceName = schema.findColumnName(field.sourceId());
    Transform<?, ?> transform = field.transform();

    if (transform instanceof Identity) {
      return visitor.identity(field.fieldId(), sourceName, field.sourceId());
    } else if (transform instanceof Bucket) {
      int numBuckets = ((Bucket<?>) transform).numBuckets();
      return visitor.bucket(field.fieldId(), sourceName, field.sourceId(), numBuckets);
    } else if (transform instanceof Truncate) {
      int width = ((Truncate<?>) transform).width();
      return visitor.truncate(field.fieldId(), sourceName, field.sourceId(), width);
    } else if (transform == Dates.YEAR
        || transform == Timestamps.YEAR
        || transform instanceof Years) {
      return visitor.year(field.fieldId(), sourceName, field.sourceId());
    } else if (transform == Dates.MONTH
        || transform == Timestamps.MONTH
        || transform instanceof Months) {
      return visitor.month(field.fieldId(), sourceName, field.sourceId());
    } else if (transform == Dates.DAY || transform == Timestamps.DAY || transform instanceof Days) {
      return visitor.day(field.fieldId(), sourceName, field.sourceId());
    } else if (transform == Timestamps.HOUR || transform instanceof Hours) {
      return visitor.hour(field.fieldId(), sourceName, field.sourceId());
    } else if (transform instanceof VoidTransform) {
      return visitor.alwaysNull(field.fieldId(), sourceName, field.sourceId());
    } else if (transform instanceof UnknownTransform) {
      return visitor.unknown(field.fieldId(), sourceName, field.sourceId(), transform.toString());
    }

    throw new UnsupportedOperationException(
        String.format("Unknown transform class %s", field.transform().getClass().getName()));
  }
}
