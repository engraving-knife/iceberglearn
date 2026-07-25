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
 * 日期（DATE）类型的时间粒度分区变换枚举：YEAR/MONTH/DAY。
 *
 * <p>所属模块：iceberg-api（针对 DATE 类型的分区变换，被 PartitionSpec 持有用于按时间粒度分区）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 DATE（自 epoch 起的天数）转换为年份、月份或保留为天。
 *   <li>把源字段谓词投影为粒度值上的谓词，用于分区裁剪。
 *   <li>提供粒度间的排序包含关系判断（DAY 排序可满足 MONTH/YEAR 排序等）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>用枚举而非类承载 YEAR/MONTH/DAY，天然单例且可被 valueOf 按名解析。
 *   <li>转换算法委托 {@link DateTimeUtil}，保证跨实现一致。
 *   <li>YEAR/MONTH 是有损变换（一个值对应多天），project 时调用 fixInclusiveTimeProjection/fixStrictTimeProjection
 *       扩展边界，避免漏数据；DAY 无损直接投影。
 * </ul>
 *
 * <p>上下游关系：由 {@link Transforms#fromString(Type, String)} 在 DATE 类型下解析得到； 投影依赖 {@link
 * ProjectionUtil}；时间换算依赖 {@link DateTimeUtil}。
 */
enum Dates implements Transform<Integer, Integer> {
  YEAR(ChronoUnit.YEARS, "year"),
  MONTH(ChronoUnit.MONTHS, "month"),
  DAY(ChronoUnit.DAYS, "day");

  /**
   * 实际执行日期粒度换算的可序列化函数。
   *
   * <p>设计要点：枚举实例持有对应 Apply，bind 时直接返回，避免重复创建。
   */
  @Immutable
  static class Apply implements SerializableFunction<Integer, Integer> {
    private final ChronoUnit granularity;

    Apply(ChronoUnit granularity) {
      this.granularity = granularity;
    }

    /**
     * 把 epoch 天数转换为对应粒度的值。
     *
     * <p>逻辑：YEARS 调用 daysToYears；MONTHS 调用 daysToMonths；DAYS 原样返回； 其他粒度抛
     * UnsupportedOperationException。null 返回 null。
     *
     * @param days 自 epoch 起的天数
     * @return 对应粒度的整数值
     */
    @Override
    public Integer apply(Integer days) {
      if (days == null) {
        return null;
      }

      switch (granularity) {
        case YEARS:
          return DateTimeUtil.daysToYears(days);
        case MONTHS:
          return DateTimeUtil.daysToMonths(days);
        case DAYS:
          return days;
        default:
          throw new UnsupportedOperationException("Unsupported time unit: " + granularity);
      }
    }
  }

  private final ChronoUnit granularity;
  private final String name;
  private final Apply apply;

  Dates(ChronoUnit granularity, String name) {
    this.granularity = granularity;
    this.name = name;
    this.apply = new Apply(granularity);
  }

  /**
   * 把 epoch 天数转换为对应粒度的值。
   *
   * @param days 自 epoch 起的天数
   * @return 对应粒度的整数值
   */
  @Override
  public Integer apply(Integer days) {
    return apply.apply(days);
  }

  /**
   * 绑定到具体类型，返回粒度换算函数。
   *
   * <p>逻辑：校验类型必须是 DATE，返回枚举持有的 Apply。
   *
   * @param type 源字段类型
   * @return 粒度换算函数
   */
  @Override
  public SerializableFunction<Integer, Integer> bind(Type type) {
    Preconditions.checkArgument(canTransform(type), "Cannot bind to unsupported type: %s", type);
    return apply;
  }

  /**
   * 判断类型是否可变换：仅支持 DATE。
   *
   * @param type 待校验类型
   * @return DATE 返回 true
   */
  @Override
  public boolean canTransform(Type type) {
    return type.typeId() == Type.TypeID.DATE;
  }

  /**
   * 返回变换结果类型。
   *
   * <p>逻辑：DAY 粒度结果类型仍为 DATE；YEAR/MONTH 粒度结果类型为 INTEGER。
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

  /** 日期粒度变换保持顺序。 */
  @Override
  public boolean preservesOrder() {
    return true;
  }

  /**
   * 判断本粒度排序是否满足另一变换的排序。
   *
   * <p>逻辑：同一实例返回 true；若对方也是 Dates，则本实例粒度天数不大于对方粒度天数时返回 true （如 DAY 排序可满足 MONTH/YEAR 排序）；其余 false。
   *
   * @param other 另一个变换
   * @return 满足返回 true
   */
  @Override
  public boolean satisfiesOrderOf(Transform<?, ?> other) {
    if (this == other) {
      return true;
    }

    if (other instanceof Dates) {
      // test the granularity, in days. day(ts) => 1 day, months(ts) => 30 days, and day satisfies
      // the order of months
      Dates otherTransform = (Dates) other;
      return granularity.getDuration().toDays()
          <= otherTransform.granularity.getDuration().toDays();
    }

    return false;
  }

  /**
   * inclusive 投影：把日期谓词边界扩展到粒度区间，保证不漏数据。
   *
   * <p>逻辑：BoundTransform 委托 projectTransformPredicate；一元谓词保留算子； 字面量谓词委托 truncateInteger 后，对非 DAY
   * 粒度调用 fixInclusiveTimeProjection 修正边界； IN 集合委托 transformSet 后同样修正；其余返回 null。
   *
   * @param fieldName 分区列名
   * @param pred 源字段谓词
   * @return 粒度值上的谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<Integer> project(String fieldName, BoundPredicate<Integer> pred) {
    if (pred.term() instanceof BoundTransform) {
      return ProjectionUtil.projectTransformPredicate(this, fieldName, pred);
    }

    if (pred.isUnaryPredicate()) {
      return Expressions.predicate(pred.op(), fieldName);

    } else if (pred.isLiteralPredicate()) {
      UnboundPredicate<Integer> projected =
          ProjectionUtil.truncateInteger(fieldName, pred.asLiteralPredicate(), apply);
      if (this != DAY) {
        return ProjectionUtil.fixInclusiveTimeProjection(projected);
      }

      return projected;

    } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.IN) {
      UnboundPredicate<Integer> projected =
          ProjectionUtil.transformSet(fieldName, pred.asSetPredicate(), apply);
      if (this != DAY) {
        return ProjectionUtil.fixInclusiveTimeProjection(projected);
      }

      return projected;
    }

    return null;
  }

  /**
   * strict 投影：把日期谓词收紧到能整体跳过的粒度区间。
   *
   * <p>逻辑：与 {@link #project} 类似，但对非 DAY 粒度调用 fixStrictTimeProjection 修正边界。
   *
   * @param fieldName 分区列名
   * @param pred 源字段谓词
   * @return 粒度值上的谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<Integer> projectStrict(String fieldName, BoundPredicate<Integer> pred) {
    if (pred.term() instanceof BoundTransform) {
      return ProjectionUtil.projectTransformPredicate(this, fieldName, pred);
    }

    if (pred.isUnaryPredicate()) {
      return Expressions.predicate(pred.op(), fieldName);

    } else if (pred.isLiteralPredicate()) {
      UnboundPredicate<Integer> projected =
          ProjectionUtil.truncateIntegerStrict(fieldName, pred.asLiteralPredicate(), apply);
      if (this != DAY) {
        return ProjectionUtil.fixStrictTimeProjection(projected);
      }

      return projected;

    } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.NOT_IN) {
      UnboundPredicate<Integer> projected =
          ProjectionUtil.transformSet(fieldName, pred.asSetPredicate(), apply);
      if (this != DAY) {
        return ProjectionUtil.fixStrictTimeProjection(projected);
      }

      return projected;
    }

    return null;
  }

  /**
   * 按粒度把值格式化为人类可读字符串。
   *
   * <p>逻辑：YEARS 调用 humanYear；MONTHS 调用 humanMonth；DAYS 调用 humanDay；null 返回 "null"。
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
      default:
        throw new UnsupportedOperationException("Unsupported time unit: " + granularity);
    }
  }

  /**
   * 返回粒度名称（year/month/day），用于元数据序列化。
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
