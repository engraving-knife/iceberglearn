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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.function.Function;
import org.apache.iceberg.expressions.BoundLiteralPredicate;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundTransform;
import org.apache.iceberg.expressions.BoundUnaryPredicate;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.util.BinaryUtil;
import org.apache.iceberg.util.SerializableFunction;
import org.apache.iceberg.util.TruncateUtil;
import org.apache.iceberg.util.UnicodeUtil;

/**
 * 截断（truncate）分区变换：把源值按宽度截断后作为分区值。
 *
 * <p>所属模块：iceberg-api（分区变换实现，被 PartitionSpec 用于按前缀/区间分区）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对整数、长整型、Decimal、字符串、二进制按宽度截断，得到可比较的"前缀值"。
 *   <li>把源字段谓词投影为截断值上的谓词，用于分区裁剪。
 *   <li>按源类型分发到 TruncateInteger/TruncateLong/TruncateDecimal/TruncateString/TruncateByteBuffer。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>截断保持顺序（单调），可用于排序下推；satisfiesOrderOf 比较 width 判断粗细粒度包含关系。
 *   <li>不同类型截断语义不同：整数按 2 的幂次向下取整；字符串按码点截断；二进制按字节截断； Decimal 按 unscaledWidth 截断。具体算法委托 {@link
 *       TruncateUtil}、{@link UnicodeUtil}、 {@link BinaryUtil}，保证跨实现一致。
 *   <li>project 借助 {@link ProjectionUtil} 的 truncateXxx 系列方法做边界扩展，保证不漏数据。
 *   <li>基类 apply 抛异常强制走 bind 路径，避免无类型分派。
 * </ul>
 *
 * <p>上下游关系：由 {@link Transforms#truncate(int)} 构造；截断算法依赖 TruncateUtil 等； project 依赖 ProjectionUtil。
 *
 * @param <T> 源值与变换后值的 Java 类型
 */
class Truncate<T> implements Transform<T, T>, Function<T, T> {
  /**
   * 构造不感知类型的通用 Truncate 实例。
   *
   * @param width 截断宽度（必须为正）
   * @param <T> 源值类型
   * @return Truncate 实例
   */
  static <T> Truncate<T> get(int width) {
    Preconditions.checkArgument(width > 0, "Invalid truncate width: %s (must be > 0)", width);
    return new Truncate<>(width);
  }

  /**
   * 按源类型构造对应的 Truncate 子类实例，同时实现 {@link SerializableFunction}。
   *
   * <p>逻辑：INTEGER→TruncateInteger，LONG→TruncateLong，DECIMAL→TruncateDecimal，
   * STRING→TruncateString，BINARY→TruncateByteBuffer；其他类型抛 UnsupportedOperationException。
   *
   * @param type 源字段类型
   * @param width 截断宽度
   * @param <T> 源值类型
   * @param <R> 同时是 Truncate 与 SerializableFunction 的复合类型
   * @return 与类型匹配的 Truncate 子类实例
   * @deprecated 将在 2.0.0 移除
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  static <T, R extends Truncate<T> & SerializableFunction<T, T>> R get(Type type, int width) {
    Preconditions.checkArgument(width > 0, "Invalid truncate width: %s (must be > 0)", width);

    switch (type.typeId()) {
      case INTEGER:
        return (R) new TruncateInteger(width);
      case LONG:
        return (R) new TruncateLong(width);
      case DECIMAL:
        return (R) new TruncateDecimal(width);
      case STRING:
        return (R) new TruncateString(width);
      case BINARY:
        return (R) new TruncateByteBuffer(width);
      default:
        throw new UnsupportedOperationException("Cannot truncate type: " + type);
    }
  }

  @SuppressWarnings("checkstyle:VisibilityModifier")
  protected final int width;

  Truncate(int width) {
    this.width = width;
  }

  /**
   * 返回截断宽度。
   *
   * @return 截断宽度
   */
  public Integer width() {
    return width;
  }

  /**
   * 截断单个值（已弃用，基类不支持）。
   *
   * <p>设计要点：基类无法在不知类型时截断，强制走 {@link #bind(Type)} 获取类型相关实现。
   *
   * @param value 源值
   * @return 截断后的值
   */
  @Override
  public T apply(T value) {
    throw new UnsupportedOperationException(
        "apply(value) is deprecated, use bind(Type).apply(value)");
  }

  /**
   * 绑定到具体类型，返回与类型匹配的截断函数。
   *
   * <p>逻辑：先校验类型可变换，再委托 {@link #get(Type, int)} 构造子类实例。
   *
   * @param type 源字段类型
   * @return 截断可序列化函数
   */
  @Override
  public SerializableFunction<T, T> bind(Type type) {
    Preconditions.checkArgument(canTransform(type), "Cannot bind to unsupported type: %s", type);
    return (SerializableFunction<T, T>) get(type, width);
  }

  /**
   * 判断类型是否可截断。
   *
   * <p>逻辑：INTEGER/LONG/STRING/BINARY/DECIMAL 返回 true，其余 false。
   *
   * @param type 待校验类型
   * @return 支持返回 true
   */
  @Override
  public boolean canTransform(Type type) {
    switch (type.typeId()) {
      case INTEGER:
      case LONG:
      case STRING:
      case BINARY:
      case DECIMAL:
        return true;
    }
    return false;
  }

  /**
   * inclusive 投影：委托给与谓词类型匹配的子类实现。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 截断值上的谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<T> project(String name, BoundPredicate<T> predicate) {
    Truncate<T> bound = (Truncate<T>) get(predicate.term().type(), width);
    return bound.project(name, predicate);
  }

  /**
   * strict 投影：委托给与谓词类型匹配的子类实现。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 截断值上的谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<T> projectStrict(String name, BoundPredicate<T> predicate) {
    Truncate<T> bound = (Truncate<T>) get(predicate.term().type(), width);
    return bound.projectStrict(name, predicate);
  }

  /**
   * Truncate 结果类型与源类型相同。
   *
   * @param sourceType 源类型
   * @return 同一类型
   */
  @Override
  public Type getResultType(Type sourceType) {
    return sourceType;
  }

  /** Truncate 保持顺序。 */
  @Override
  public boolean preservesOrder() {
    return true;
  }

  /**
   * 判断本截断排序是否满足另一截断的排序。
   *
   * <p>逻辑：若为同一实例返回 true；若对方也是 Truncate，则当对方 width 不大于本实例 width 时 返回 true（更细粒度的截断排序可满足更粗粒度的截断排序）；其余
   * false。
   *
   * @param other 另一个变换
   * @return 满足返回 true
   */
  @Override
  public boolean satisfiesOrderOf(Transform<?, ?> other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof Truncate)) {
      return false;
    }

    Truncate<?> otherTrunc = (Truncate<?>) other;
    return otherTrunc.width <= width;
  }

  /**
   * 相等性：同为 Truncate 且 width 相同。
   *
   * @param o 另一个对象
   * @return 相等返回 true
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof Truncate)) {
      return false;
    }

    Truncate<?> that = (Truncate<?>) o;
    return width == that.width;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(width);
  }

  /**
   * 返回 "truncate[width]" 形式字符串，用于元数据序列化。
   *
   * @return 字符串表示
   */
  @Override
  public String toString() {
    return "truncate[" + width + "]";
  }

  /** 整数截断子类：委托 {@link TruncateUtil#truncateInt} 把值向下截断到 width 的倍数。 */
  private static class TruncateInteger extends Truncate<Integer>
      implements SerializableFunction<Integer, Integer> {

    private TruncateInteger(int width) {
      super(width);
    }

    /**
     * 校验类型必须是 INTEGER，并返回自身作为绑定函数。
     *
     * @param type 源类型
     * @return 自身
     */
    @Override
    public SerializableFunction<Integer, Integer> bind(Type type) {
      Preconditions.checkArgument(
          type.typeId() == Type.TypeID.INTEGER,
          "Cannot bind truncate to a different type: %s",
          type);
      return this;
    }

    /**
     * 把整数向下截断到 width 的倍数。
     *
     * @param value 源值
     * @return 截断后的值；null 返回 null
     */
    @Override
    public Integer apply(Integer value) {
      if (value == null) {
        return null;
      }

      return TruncateUtil.truncateInt(width, value);
    }

    /**
     * inclusive 投影：把整数谓词边界扩展到对应的截断区间。
     *
     * <p>逻辑：BoundTransform 委托 projectTransformPredicate；一元谓词保留算子； 字面量谓词委托 truncateInteger；IN 集合委托
     * transformSet；其余返回 null。
     *
     * @param name 分区列名
     * @param pred 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<Integer> project(String name, BoundPredicate<Integer> pred) {
      if (pred.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, pred);
      }

      if (pred.isUnaryPredicate()) {
        return Expressions.predicate(pred.op(), name);
      } else if (pred.isLiteralPredicate()) {
        return ProjectionUtil.truncateInteger(name, pred.asLiteralPredicate(), this);
      } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.IN) {
        return ProjectionUtil.transformSet(name, pred.asSetPredicate(), this);
      }
      return null;
    }

    /**
     * strict 投影：把整数谓词收紧到能整体跳过的截断区间。
     *
     * <p>逻辑：BoundTransform 委托 projectTransformPredicate；一元谓词保留算子； 字面量谓词委托
     * truncateIntegerStrict；NOT_IN 集合委托 transformSet；其余返回 null。
     *
     * @param name 分区列名
     * @param pred 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<Integer> projectStrict(String name, BoundPredicate<Integer> pred) {
      if (pred.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, pred);
      }

      // TODO: for integers, can this return the original predicate?
      // No. the predicate needs to be in terms of the applied value. For all x, apply(x) <= x.
      // Therefore, the lower bound can be transformed outside of a greater-than bound.
      if (pred instanceof BoundUnaryPredicate) {
        return Expressions.predicate(pred.op(), name);
      } else if (pred instanceof BoundLiteralPredicate) {
        return ProjectionUtil.truncateIntegerStrict(name, pred.asLiteralPredicate(), this);
      } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.NOT_IN) {
        return ProjectionUtil.transformSet(name, pred.asSetPredicate(), this);
      }
      return null;
    }
  }

  /** 长整型截断子类：委托 {@link TruncateUtil#truncateLong} 把值向下截断到 width 的倍数。 */
  private static class TruncateLong extends Truncate<Long>
      implements SerializableFunction<Long, Long> {

    private TruncateLong(int width) {
      super(width);
    }

    /**
     * 校验类型必须是 LONG，并返回自身。
     *
     * @param type 源类型
     * @return 自身
     */
    @Override
    public SerializableFunction<Long, Long> bind(Type type) {
      Preconditions.checkArgument(
          type.typeId() == Type.TypeID.LONG, "Cannot bind truncate to a different type: %s", type);
      return this;
    }

    /**
     * 把长整型向下截断到 width 的倍数。
     *
     * @param value 源值
     * @return 截断后的值；null 返回 null
     */
    @Override
    public Long apply(Long value) {
      if (value == null) {
        return null;
      }

      return TruncateUtil.truncateLong(width, value);
    }

    /**
     * inclusive 投影：把长整型谓词边界扩展到截断区间。
     *
     * @param name 分区列名
     * @param pred 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<Long> project(String name, BoundPredicate<Long> pred) {
      if (pred.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, pred);
      }

      if (pred.isUnaryPredicate()) {
        return Expressions.predicate(pred.op(), name);
      } else if (pred.isLiteralPredicate()) {
        return ProjectionUtil.truncateLong(name, pred.asLiteralPredicate(), this);
      } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.IN) {
        return ProjectionUtil.transformSet(name, pred.asSetPredicate(), this);
      }
      return null;
    }

    /**
     * strict 投影：把长整型谓词收紧到能整体跳过的截断区间。
     *
     * @param name 分区列名
     * @param pred 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<Long> projectStrict(String name, BoundPredicate<Long> pred) {
      if (pred.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, pred);
      }

      if (pred.isUnaryPredicate()) {
        return Expressions.predicate(pred.op(), name);
      } else if (pred.isLiteralPredicate()) {
        return ProjectionUtil.truncateLongStrict(name, pred.asLiteralPredicate(), this);
      } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.NOT_IN) {
        return ProjectionUtil.transformSet(name, pred.asSetPredicate(), this);
      }
      return null;
    }
  }

  /** 字符串截断子类：委托 {@link UnicodeUtil#truncateString} 按码点截断。 */
  private static class TruncateString extends Truncate<CharSequence>
      implements SerializableFunction<CharSequence, CharSequence> {

    private TruncateString(int length) {
      super(length);
    }

    /**
     * 校验类型必须是 STRING，并返回自身。
     *
     * @param type 源类型
     * @return 自身
     */
    @Override
    public SerializableFunction<CharSequence, CharSequence> bind(Type type) {
      Preconditions.checkArgument(
          type.typeId() == Type.TypeID.STRING,
          "Cannot bind truncate to a different type: %s",
          type);
      return this;
    }

    /**
     * 按码点截断字符串到 width 长度。
     *
     * @param value 源字符串
     * @return 截断后的 CharSequence；null 返回 null
     */
    @Override
    public CharSequence apply(CharSequence value) {
      if (value == null) {
        return null;
      }

      return UnicodeUtil.truncateString(value, width);
    }

    /**
     * 判断本字符串截断排序是否满足另一变换的排序。
     *
     * <p>逻辑：同一实例返回 true；若对方也是 TruncateString，则当本实例 width 不小于对方 width 时返回 true；其余 false。
     *
     * @param other 另一个变换
     * @return 满足返回 true
     */
    @Override
    public boolean satisfiesOrderOf(Transform<?, ?> other) {
      if (this == other) {
        return true;
      } else if (other instanceof TruncateString) {
        TruncateString otherTransform = (TruncateString) other;
        return width() >= otherTransform.width();
      }

      return false;
    }

    /**
     * inclusive 投影：处理字符串谓词（含 STARTS_WITH 优化）。
     *
     * <p>逻辑：BoundTransform 委托 projectTransformPredicate；一元谓词保留算子； 字面量谓词中——STARTS_WITH 在字面量长度小于/等于
     * width 时退化为等值或原样下推， 否则委托 truncateArray；NOT_STARTS_WITH 类似处理；其他算子委托 truncateArray； IN 集合委托
     * transformSet；其余返回 null。
     *
     * @param name 分区列名
     * @param predicate 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<CharSequence> project(
        String name, BoundPredicate<CharSequence> predicate) {
      if (predicate.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, predicate);
      }

      if (predicate.isUnaryPredicate()) {
        return Expressions.predicate(predicate.op(), name);
      } else if (predicate.isLiteralPredicate()) {
        BoundLiteralPredicate<CharSequence> pred = predicate.asLiteralPredicate();
        switch (pred.op()) {
          case STARTS_WITH:
            if (pred.literal().value().length() < width()) {
              return Expressions.predicate(pred.op(), name, pred.literal().value());
            } else if (pred.literal().value().length() == width()) {
              return Expressions.equal(name, pred.literal().value());
            }

            return ProjectionUtil.truncateArray(name, pred, this);

          case NOT_STARTS_WITH:
            if (pred.literal().value().length() < width()) {
              return Expressions.predicate(pred.op(), name, pred.literal().value());
            } else if (pred.literal().value().length() == width()) {
              return Expressions.notEqual(name, pred.literal().value());
            }

            return null;

          default:
            return ProjectionUtil.truncateArray(name, pred, this);
        }
      } else if (predicate.isSetPredicate() && predicate.op() == Expression.Operation.IN) {
        return ProjectionUtil.transformSet(name, predicate.asSetPredicate(), this);
      }
      return null;
    }

    /**
     * strict 投影：处理字符串谓词（含 STARTS_WITH 优化）。
     *
     * <p>逻辑：与 {@link #project} 类似，但 STARTS_WITH 在字面量长度大于 width 时返回 null （无法严格投影），NOT_STARTS_WITH
     * 在大于 width 时把字面量截断后再下推； 其他算子委托 truncateArrayStrict；NOT_IN 集合委托 transformSet；其余返回 null。
     *
     * @param name 分区列名
     * @param predicate 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<CharSequence> projectStrict(
        String name, BoundPredicate<CharSequence> predicate) {
      if (predicate.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, predicate);
      }

      if (predicate instanceof BoundUnaryPredicate) {
        return Expressions.predicate(predicate.op(), name);
      } else if (predicate instanceof BoundLiteralPredicate) {
        BoundLiteralPredicate<CharSequence> pred = predicate.asLiteralPredicate();
        switch (pred.op()) {
          case STARTS_WITH:
            if (pred.literal().value().length() < width()) {
              return Expressions.predicate(pred.op(), name, pred.literal().value());
            } else if (pred.literal().value().length() == width()) {
              return Expressions.equal(name, pred.literal().value());
            }

            return null;

          case NOT_STARTS_WITH:
            if (pred.literal().value().length() < width()) {
              return Expressions.predicate(pred.op(), name, pred.literal().value());
            } else if (pred.literal().value().length() == width()) {
              return Expressions.notEqual(name, pred.literal().value());
            }

            return Expressions.predicate(pred.op(), name, apply(pred.literal().value()).toString());

          default:
            return ProjectionUtil.truncateArrayStrict(name, pred, this);
        }
      } else if (predicate.isSetPredicate() && predicate.op() == Expression.Operation.NOT_IN) {
        return ProjectionUtil.transformSet(name, predicate.asSetPredicate(), this);
      }
      return null;
    }
  }

  /** 二进制截断子类：委托 {@link BinaryUtil#truncateBinaryUnsafe} 按字节截断。 */
  private static class TruncateByteBuffer extends Truncate<ByteBuffer>
      implements SerializableFunction<ByteBuffer, ByteBuffer> {

    private TruncateByteBuffer(int length) {
      super(length);
    }

    /**
     * 校验类型必须是 BINARY 或 FIXED，并返回自身。
     *
     * @param type 源类型
     * @return 自身
     */
    @Override
    public SerializableFunction<ByteBuffer, ByteBuffer> bind(Type type) {
      Preconditions.checkArgument(
          type.typeId() == Type.TypeID.BINARY || type.typeId() == Type.TypeID.FIXED,
          "Cannot bind truncate to a different type: %s",
          type);
      return this;
    }

    /**
     * 按字节截断二进制到 width 长度。
     *
     * @param value 源二进制
     * @return 截断后的 ByteBuffer；null 返回 null
     */
    @Override
    public ByteBuffer apply(ByteBuffer value) {
      if (value == null) {
        return null;
      }

      return BinaryUtil.truncateBinaryUnsafe(value, width);
    }

    /**
     * inclusive 投影：把二进制谓词边界扩展到截断区间。
     *
     * @param name 分区列名
     * @param pred 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<ByteBuffer> project(String name, BoundPredicate<ByteBuffer> pred) {
      if (pred.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, pred);
      }

      if (pred.isUnaryPredicate()) {
        return Expressions.predicate(pred.op(), name);
      } else if (pred.isLiteralPredicate()) {
        return ProjectionUtil.truncateArray(name, pred.asLiteralPredicate(), this);
      } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.IN) {
        return ProjectionUtil.transformSet(name, pred.asSetPredicate(), this);
      }
      return null;
    }

    /**
     * strict 投影：把二进制谓词收紧到能整体跳过的截断区间。
     *
     * @param name 分区列名
     * @param pred 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<ByteBuffer> projectStrict(
        String name, BoundPredicate<ByteBuffer> pred) {
      if (pred.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, pred);
      }

      if (pred.isUnaryPredicate()) {
        return Expressions.predicate(pred.op(), name);
      } else if (pred.isLiteralPredicate()) {
        return ProjectionUtil.truncateArrayStrict(name, pred.asLiteralPredicate(), this);
      } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.NOT_IN) {
        return ProjectionUtil.transformSet(name, pred.asSetPredicate(), this);
      }
      return null;
    }
  }

  /** Decimal 截断子类：委托 {@link TruncateUtil#truncateDecimal} 按 unscaledWidth 截断。 */
  private static class TruncateDecimal extends Truncate<BigDecimal>
      implements SerializableFunction<BigDecimal, BigDecimal> {

    private final BigInteger unscaledWidth;

    private TruncateDecimal(int unscaledWidth) {
      super(unscaledWidth);
      this.unscaledWidth = BigInteger.valueOf(unscaledWidth);
    }

    /**
     * 校验类型必须是 DECIMAL，并返回自身。
     *
     * @param type 源类型
     * @return 自身
     */
    @Override
    public SerializableFunction<BigDecimal, BigDecimal> bind(Type type) {
      Preconditions.checkArgument(
          type.typeId() == Type.TypeID.DECIMAL,
          "Cannot bind truncate to a different type: %s",
          type);
      return this;
    }

    /**
     * 把 Decimal 按 unscaledWidth 截断到对应倍数。
     *
     * @param value 源 Decimal
     * @return 截断后的 BigDecimal；null 返回 null
     */
    @Override
    public BigDecimal apply(BigDecimal value) {
      if (value == null) {
        return null;
      }

      return TruncateUtil.truncateDecimal(unscaledWidth, value);
    }

    /**
     * inclusive 投影：把 Decimal 谓词边界扩展到截断区间。
     *
     * @param name 分区列名
     * @param pred 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<BigDecimal> project(String name, BoundPredicate<BigDecimal> pred) {
      if (pred.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, pred);
      }

      if (pred.isUnaryPredicate()) {
        return Expressions.predicate(pred.op(), name);
      } else if (pred.isLiteralPredicate()) {
        return ProjectionUtil.truncateDecimal(name, pred.asLiteralPredicate(), this);
      } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.IN) {
        return ProjectionUtil.transformSet(name, pred.asSetPredicate(), this);
      }
      return null;
    }

    /**
     * strict 投影：把 Decimal 谓词收紧到能整体跳过的截断区间。
     *
     * @param name 分区列名
     * @param pred 源字段谓词
     * @return 截断值上的谓词
     */
    @Override
    public UnboundPredicate<BigDecimal> projectStrict(
        String name, BoundPredicate<BigDecimal> pred) {
      if (pred.term() instanceof BoundTransform) {
        return ProjectionUtil.projectTransformPredicate(this, name, pred);
      }

      if (pred.isUnaryPredicate()) {
        return Expressions.predicate(pred.op(), name);
      } else if (pred.isLiteralPredicate()) {
        return ProjectionUtil.truncateDecimalStrict(name, pred.asLiteralPredicate(), this);
      } else if (pred.isSetPredicate() && pred.op() == Expression.Operation.NOT_IN) {
        return ProjectionUtil.transformSet(name, pred.asSetPredicate(), this);
      }
      return null;
    }
  }
}
