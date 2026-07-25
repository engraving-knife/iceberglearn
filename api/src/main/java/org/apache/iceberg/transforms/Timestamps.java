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

import com.google.errorprone.annotations.Immutable;
import java.time.temporal.ChronoUnit;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundTransform;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 时间戳（TIMESTAMP）类型的时间粒度分区变换枚举：YEAR/MONTH/DAY/HOUR。
 *
 * <p>所属模块：iceberg-api（针对 TIMESTAMP 类型的分区变换，被 PartitionSpec 持有用于按时间粒度分区）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 TIMESTAMP（自 epoch 起的微秒数）转换为年份、月份、天或小时。
 *   <li>把源字段谓词投影为粒度值上的谓词，用于分区裁剪。
 *   <li>提供粒度间的排序包含关系判断（HOUR 排序可满足 DAY/MONTH/YEAR 排序等）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>用枚举承载 YEAR/MONTH/DAY/HOUR，天然单例且可被 valueOf 按名解析。
 *   <li>转换算法委托 {@link DateTimeUtil}，保证跨实现一致。
 *   <li>所有粒度均为有损变换（一个粒度值对应多个时间戳），project 必须调用 fixInclusiveTimeProjection/fixStrictTimeProjection
 *       修正边界，避免漏数据或误跳过。
 * </ul>
 *
 * <p>上下游关系：由 {@link Transforms#fromString(Type, String)} 在 TIMESTAMP 类型下解析得到； 投影依赖 {@link
 * ProjectionUtil}；时间换算依赖 {@link DateTimeUtil}； 被 {@link Days}/{@link Hours}/{@link Months}/{@link
 * Years} 等 TimeTransform 子类作为底层实现。
 */
enum Timestamps implements Transform<Long, Integer> {
  YEAR(ChronoUnit.YEARS, "year"),
  MONTH(ChronoUnit.MONTHS, "month"),
  DAY(ChronoUnit.DAYS, "day"),
  HOUR(ChronoUnit.HOURS, "hour");

  /**
   * 实际执行时间戳粒度换算的可序列化函数。
   *
   * <p>设计要点：枚举实例持有对应 Apply，bind 时直接返回，避免重复创建。
   */
  @Immutable
  static class Apply implements SerializableFunction<Long, Integer> {
    private final ChronoUnit granularity;

    Apply(ChronoUnit granularity) {
      this.granularity = granularity;
    }

    /**
     * 把微秒时间戳转换为对应粒度的值。
     *
     * <p>逻辑：YEARS 调用 microsToYears；MONTHS 调用 microsToMonths；DAYS 调用 microsToDays； HOURS 调用
     * microsToHours；其他粒度抛 UnsupportedOperationException。null 返回 null。
     *
     * @param timestampMicros 自 epoch 起的微秒数
     * @return 对应粒度的整数值
     */
    @Override
    public Integer apply(Long timestampMicros) {
      if (timestampMicros == null) {
        return null;
      }

      switch (granularity) {
        case YEARS:
          return DateTimeUtil.microsToYears(timestampMicros);
        case MONTHS:
          return DateTimeUtil.microsToMonths(timestampMicros);
        case DAYS:
          return DateTimeUtil.microsToDays(timestampMicros);
        case HOURS:
          return DateTimeUtil.microsToHours(timestampMicros);
        default:
          throw new UnsupportedOperationException("Unsupported time unit: " + granularity);
      }
    }
  }

  private final ChronoUnit granularity;
  private final String name;
  private final Apply apply;

  Timestamps(ChronoUnit granularity, String name) {
    this.granularity = granularity;
    this.name = name;
    this.apply = new Apply(granularity);
  }

  /**
   * 把微秒时间戳转换为对应粒度的值。
   *
   * @param timestampMicros 自 epoch 起的微秒数
   * @return 对应粒度的整数值
   */
  @Override
  public Integer apply(Long timestampMicros) {
    return apply.apply(timestampMicros);
  }

  /**
   * 绑定到具体类型，返回粒度换算函数。
   *
   * <p>逻辑：校验类型必须是 TIMESTAMP，返回枚举持有的 Apply。
   *
   * @param type 源字段类型
   * @return 粒度换算函数
   */
  @Override
  public SerializableFunction<Long, Integer> bind(Type type) {
    Preconditions.checkArgument(canTransform(type), "Cannot bind to unsupported type: %s", type);
    return apply;
  }

  /**
   * 判断类型是否可变换：仅支持 TIMESTAMP。
   *
   * @param type 待校验类型
   * @return TIMESTAMP 返回 true
   */
  @Override
  public boolean canTransform(Type type) {
    return type.typeId() == Type.TypeID.TIMESTAMP;
  }

  /**
   * 返回变换结果类型。
   *
   * <p>逻辑：DAY 粒度结果类型为 DATE；其余粒度结果类型为 INTEGER。
   *
   * @param sourceType 源类型
   * @return 结果类型
   */
  @Override
  public Type getResultType(Type sourceType) {
    if (granularity == ChronoUnit.DAYS) {
      return Types.DateType.get();
    }
    return Types.IntegerType.get();
  }

  /** 时间戳粒度变换保持顺序。 */
  @Override
  public boolean preservesOrder() {
    return true;
  }

  /**
   * 判断本粒度排序是否满足另一变换的排序。
   *
   * <p>逻辑：同一实例返回 true；若对方也是 Timestamps，则本实例粒度小时数不大于对方粒度小时数时 返回 true（如 HOUR 排序可满足 DAY/MONTH/YEAR
   * 排序）；其余 false。
   *
   * @param other 另一个变换
   * @return 满足返回 true
   */
  @Override
  public boolean satisfiesOrderOf(Transform<?, ?> other) {
    if (this == other) {
      return true;
    }

    if (other instanceof Timestamps) {
      // test the granularity, in hours. hour(ts) => 1 hour, day(ts) => 24 hours, and hour satisfies
      // the order of day
      Timestamps otherTransform = (Timestamps) other;
      return granularity.getDuration().toHours()
          <= otherTransform.granularity.getDuration().toHours();
    }

    return false;
  }

  /**
   * inclusive 投影：把时间戳谓词边界扩展到粒度区间，保证不漏数据。
   *
   * <p>逻辑：BoundTransform 委托 projectTransformPredicate；一元谓词保留算子； 字面量谓词委托 truncateLong 后调用
   * fixInclusiveTimeProjection 修正边界； IN 集合委托 transformSet 后同样修正；其余返回 null。
   *
   * @param fieldName 分区列名
   * @param pred 源字段谓词
   * @return 粒度值上的谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<Integer> project(String fieldName, BoundPredicate<Long> pred) {
    if (pred.term() instanceof BoundTransform) {
      return ProjectionUtil.projectTransformPredicate(this, fieldName, pred);
    }

    if (pred.isUnaryPredicate()) {
      return Expressions.predicate(pred.op(), fieldName);

    } else if (pred.isLiteralPredicate()) {
      UnboundPredicate<Integer> projected =
          ProjectionUtil.truncateLong(fieldName, pred.asLiteralPredicate(), apply);
      return ProjectionUtil.fixInclusiveTimeProjection(projected);

    } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.IN) {
      UnboundPredicate<Integer> projected =
          ProjectionUtil.transformSet(fieldName, pred.asSetPredicate(), apply);
      return ProjectionUtil.fixInclusiveTimeProjection(projected);
    }

    return null;
  }

  /**
   * strict 投影：把时间戳谓词收紧到能整体跳过的粒度区间。
   *
   * <p>逻辑：与 {@link #project} 类似，但调用 fixStrictTimeProjection 修正边界。
   *
   * @param fieldName 分区列名
   * @param pred 源字段谓词
   * @return 粒度值上的谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<Integer> projectStrict(String fieldName, BoundPredicate<Long> pred) {
    if (pred.term() instanceof BoundTransform) {
      return ProjectionUtil.projectTransformPredicate(this, fieldName, pred);
    }

    if (pred.isUnaryPredicate()) {
      return Expressions.predicate(pred.op(), fieldName);

    } else if (pred.isLiteralPredicate()) {
      UnboundPredicate<Integer> projected =
          ProjectionUtil.truncateLongStrict(fieldName, pred.asLiteralPredicate(), apply);
      return ProjectionUtil.fixStrictTimeProjection(projected);

    } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.NOT_IN) {
      UnboundPredicate<Integer> projected =
          ProjectionUtil.transformSet(fieldName, pred.asSetPredicate(), apply);
      return ProjectionUtil.fixStrictTimeProjection(projected);
    }

    return null;
  }

  /**
   * 按粒度把值格式化为人类可读字符串。
   *
   * <p>逻辑：YEARS 调用 humanYear；MONTHS 调用 humanMonth；DAYS 调用 humanDay；HOURS 调用 humanHour； null 返回
   * "null"。
   *
   * @param outputType 输出类型
   * @param value 变换值
   * @return 人类可读字符串
   */
  @Override
  public String toHumanString(Type outputType, Integer value) {
    if (value == null) {
      return "null";
    }

    switch (granularity) {
      case YEARS:
        return TransformUtil.humanYear(value);
      case MONTHS:
        return TransformUtil.humanMonth(value);
      case DAYS:
        return TransformUtil.humanDay(value);
      case HOURS:
        return TransformUtil.humanHour(value);
      default:
        throw new UnsupportedOperationException("Unsupported time unit: " + granularity);
    }
  }

  /**
   * 返回粒度名称（year/month/day/hour），用于元数据序列化。
   *
   * @return 粒度名称
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * 返回去重名称 "time"，使同源字段上的多个时间粒度变换互斥。
   *
   * @return "time"
   */
  @Override
  public String dedupName() {
    return "time";
  }
}
