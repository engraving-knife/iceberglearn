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

import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Function;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundTransform;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.BucketUtil;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 桶（bucket）分区变换：通过一致性哈希把源值映射到 [0, numBuckets) 区间内的整数桶号。
 *
 * <p>所属模块：iceberg-api（分区变换实现，被 PartitionSpec 持有并用于扫描规划与文件分布）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对整数、长整型、字符串、二进制、UUID、Decimal 等类型计算哈希并取模得到桶号。
 *   <li>把源字段上的谓词投影为桶号上的谓词，用于分区裁剪。
 *   <li>按源类型分发到对应的子类（BucketInteger/BucketLong/...），由子类提供具体 hash 实现。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用 Iceberg 规范的一致性哈希（{@link BucketUtil#hash}），保证跨引擎/跨实现桶号一致， 即同一值在任何环境下都落到同一桶，避免数据倾斜与重复。
 *   <li>用 {@code hash & Integer.MAX_VALUE} 抹掉符号位后再取模，保证桶号非负。
 *   <li>子类继承基类共享取模、project、equals 等逻辑，只覆盖 hash 方法，避免重复代码。
 *   <li>桶变换非单调，因此不支持比较类谓词投影；仅支持 eq/in 的 inclusive 投影与 notEq/notIn 的 strict 投影。
 * </ul>
 *
 * <p>上下游关系：由 {@link Transforms#bucket(int)} / {@link #get(Type, int)} 构造； 哈希算法依赖 {@link
 * BucketUtil}；project 依赖 {@link ProjectionUtil}。
 *
 * @param <T> 源值的 Java 类型
 */
class Bucket<T> implements Transform<T, Integer>, Serializable {
  /**
   * 构造一个不感知类型的通用 Bucket 实例。
   *
   * <p>设计要点：仅做参数校验（桶数必须为正），具体 hash 在子类中实现。适用于仅需元数据 而不实际执行变换的场景。
   *
   * @param numBuckets 桶数量
   * @param <T> 源值类型
   * @return Bucket 实例
   */
  static <T> Bucket<T> get(int numBuckets) {
    Preconditions.checkArgument(
        numBuckets > 0, "Invalid number of buckets: %s (must be > 0)", numBuckets);
    return new Bucket<>(numBuckets);
  }

  /**
   * 按源类型构造对应的 Bucket 子类实例，同时实现 {@link SerializableFunction} 以便直接用作绑定函数。
   *
   * <p>逻辑：依据 type 的 typeId 选择子类——DATE/INTEGER 用 BucketInteger， TIME/TIMESTAMP/LONG 用
   * BucketLong，DECIMAL 用 BucketDecimal，STRING 用 BucketString， FIXED/BINARY 用 BucketByteBuffer，UUID
   * 用 BucketUUID；其他类型抛 IllegalArgumentException。
   *
   * <p>设计要点：返回类型 B 同时是 Bucket 与 SerializableFunction，调用方可直接当绑定函数用， 省去额外 bind 步骤。
   *
   * @param type 源字段类型
   * @param numBuckets 桶数量
   * @param <T> 源值类型
   * @param <B> 同时是 Bucket 与 SerializableFunction 的复合类型
   * @return 与类型匹配的 Bucket 子类实例
   */
  @SuppressWarnings("unchecked")
  static <T, B extends Bucket<T> & SerializableFunction<T, Integer>> B get(
      Type type, int numBuckets) {
    Preconditions.checkArgument(
        numBuckets > 0, "Invalid number of buckets: %s (must be > 0)", numBuckets);

    switch (type.typeId()) {
      case DATE:
      case INTEGER:
        return (B) new BucketInteger(numBuckets);
      case TIME:
      case TIMESTAMP:
      case LONG:
        return (B) new BucketLong(numBuckets);
      case DECIMAL:
        return (B) new BucketDecimal(numBuckets);
      case STRING:
        return (B) new BucketString(numBuckets);
      case FIXED:
      case BINARY:
        return (B) new BucketByteBuffer(numBuckets);
      case UUID:
        return (B) new BucketUUID(numBuckets);
      default:
        throw new IllegalArgumentException("Cannot bucket by type: " + type);
    }
  }

  private final int numBuckets;

  private Bucket(int numBuckets) {
    this.numBuckets = numBuckets;
  }

  /**
   * 返回桶数量。
   *
   * @return 桶数量
   */
  public Integer numBuckets() {
    return numBuckets;
  }

  /**
   * 把本变换绑定到具体类型，返回对应的桶函数。
   *
   * <p>逻辑：先校验类型可变换，再委托 {@link #get(Type, int)} 构造与类型匹配的子类实例。
   *
   * @param type 源字段类型
   * @return 与类型匹配的可序列化桶函数
   */
  @Override
  public SerializableFunction<T, Integer> bind(Type type) {
    Preconditions.checkArgument(canTransform(type), "Cannot bucket by type: %s", type);
    return get(type, numBuckets);
  }

  /**
   * 计算源值的哈希值（由子类实现）。
   *
   * <p>设计要点：基类抛出异常，强制具体类型子类覆盖。
   *
   * @param value 源值
   * @return 哈希值
   */
  protected int hash(T value) {
    throw new UnsupportedOperationException(
        "hash(value) is not supported on the base Bucket class");
  }

  /**
   * 对源值执行桶变换：null 直接返回 null，否则对 hash 取非负后模桶数。
   *
   * @param value 源值
   * @return 桶号（[0, numBuckets)）；value 为 null 时返回 null
   */
  @Override
  public Integer apply(T value) {
    if (value == null) {
      return null;
    }
    return (hash(value) & Integer.MAX_VALUE) % numBuckets;
  }

  /**
   * 判断本变换是否支持给定类型。
   *
   * <p>逻辑：INTEGER/LONG/DATE/TIME/TIMESTAMP/STRING/BINARY/FIXED/DECIMAL/UUID 返回 true，其余 false。
   *
   * @param type 待校验类型
   * @return 支持返回 true
   */
  @Override
  public boolean canTransform(Type type) {
    switch (type.typeId()) {
      case INTEGER:
      case LONG:
      case DATE:
      case TIME:
      case TIMESTAMP:
      case STRING:
      case BINARY:
      case FIXED:
      case DECIMAL:
      case UUID:
        return true;
    }
    return false;
  }

  /**
   * 相等性判断：仅比较 numBuckets。
   *
   * @param o 另一个对象
   * @return 同为 Bucket 且桶数相同返回 true
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof Bucket)) {
      return false;
    }

    Bucket<?> bucket = (Bucket<?>) o;
    return numBuckets == bucket.numBuckets;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(numBuckets);
  }

  /**
   * 返回 "bucket[N]" 形式的字符串表示，用于元数据序列化与日志。
   *
   * @return 字符串表示
   */
  @Override
  public String toString() {
    return "bucket[" + numBuckets + "]";
  }

  /**
   * 把源字段谓词投影为桶号上的"包含型"谓词，用于挑选候选分区。
   *
   * <p>逻辑：先 bind 取桶函数；若谓词作用于已变换项（BoundTransform）则委托 {@link
   * ProjectionUtil#projectTransformPredicate}；一元谓词原样保留语义； EQ 字面量谓词对字面量桶变换后下推；IN 集合谓词用 transformSet
   * 批量变换； 比较类谓词与 notEq 不可投影返回 null。
   *
   * @param name 分区列名
   * @param predicate 源字段已绑定谓词
   * @return 桶号上的未绑定谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<Integer> project(String name, BoundPredicate<T> predicate) {
    Function<T, Integer> function = this.bind(predicate.term().type());
    if (predicate.term() instanceof BoundTransform) {
      return ProjectionUtil.projectTransformPredicate(this, name, predicate);
    }

    if (predicate.isUnaryPredicate()) {
      return Expressions.predicate(predicate.op(), name);
    } else if (predicate.isLiteralPredicate() && predicate.op() == Expression.Operation.EQ) {
      return Expressions.predicate(
          predicate.op(), name, function.apply(predicate.asLiteralPredicate().literal().value()));
    } else if (predicate.isSetPredicate()
        && predicate.op() == Expression.Operation.IN) { // notIn can't be projected
      return ProjectionUtil.transformSet(name, predicate.asSetPredicate(), function);
    }

    // comparison predicates can't be projected, notEq can't be projected
    // TODO: small ranges can be projected.
    // for example, (x > 0) and (x < 3) can be turned into in({1, 2}) and projected.
    return null;
  }

  /**
   * 把源字段谓词投影为桶号上的"严格型"谓词，用于整体跳过分区。
   *
   * <p>逻辑：与 {@link #project} 类似，但只对 notEq 字面量与 notIn 集合做投影； EQ/IN/比较类不可严格投影，返回 null。
   *
   * @param name 分区列名
   * @param predicate 源字段已绑定谓词
   * @return 桶号上的未绑定谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<Integer> projectStrict(String name, BoundPredicate<T> predicate) {
    Function<T, Integer> function = this.bind(predicate.term().type());
    if (predicate.term() instanceof BoundTransform) {
      return ProjectionUtil.projectTransformPredicate(this, name, predicate);
    }

    if (predicate.isUnaryPredicate()) {
      return Expressions.predicate(predicate.op(), name);
    } else if (predicate.isLiteralPredicate() && predicate.op() == Expression.Operation.NOT_EQ) {
      // TODO: need to translate not(eq(...)) into notEq in expressions
      return Expressions.predicate(
          predicate.op(), name, function.apply(predicate.asLiteralPredicate().literal().value()));
    } else if (predicate.isSetPredicate() && predicate.op() == Expression.Operation.NOT_IN) {
      return ProjectionUtil.transformSet(name, predicate.asSetPredicate(), function);
    }

    // no strict projection for comparison or equality
    return null;
  }

  /**
   * 桶变换的结果类型恒为 Integer。
   *
   * @param sourceType 源类型（不参与判断）
   * @return IntegerType
   */
  @Override
  public Type getResultType(Type sourceType) {
    return Types.IntegerType.get();
  }

  /** 整数桶变换子类：委托 {@link BucketUtil#hash(Integer)} 计算哈希。 */
  private static class BucketInteger extends Bucket<Integer>
      implements SerializableFunction<Integer, Integer> {

    private BucketInteger(int numBuckets) {
      super(numBuckets);
    }

    @Override
    protected int hash(Integer value) {
      return BucketUtil.hash(value);
    }
  }

  /** 长整型桶变换子类：委托 {@link BucketUtil#hash(Long)} 计算哈希。 */
  private static class BucketLong extends Bucket<Long>
      implements SerializableFunction<Long, Integer> {

    private BucketLong(int numBuckets) {
      super(numBuckets);
    }

    @Override
    protected int hash(Long value) {
      return BucketUtil.hash(value);
    }
  }

  /** 字符串桶变换子类：委托 {@link BucketUtil#hash(CharSequence)} 计算哈希。 */
  private static class BucketString extends Bucket<CharSequence>
      implements SerializableFunction<CharSequence, Integer> {

    private BucketString(int numBuckets) {
      super(numBuckets);
    }

    @Override
    protected int hash(CharSequence value) {
      return BucketUtil.hash(value);
    }
  }

  /** 二进制（ByteBuffer）桶变换子类：委托 {@link BucketUtil#hash(ByteBuffer)} 计算哈希。 */
  private static class BucketByteBuffer extends Bucket<ByteBuffer>
      implements SerializableFunction<ByteBuffer, Integer> {

    private BucketByteBuffer(int numBuckets) {
      super(numBuckets);
    }

    @Override
    protected int hash(ByteBuffer value) {
      return BucketUtil.hash(value);
    }
  }

  /** UUID 桶变换子类：委托 {@link BucketUtil#hash(UUID)} 计算哈希。 */
  private static class BucketUUID extends Bucket<UUID>
      implements SerializableFunction<UUID, Integer> {

    private BucketUUID(int numBuckets) {
      super(numBuckets);
    }

    @Override
    public int hash(UUID value) {
      return BucketUtil.hash(value);
    }
  }

  /** Decimal 桶变换子类：委托 {@link BucketUtil#hash(BigDecimal)} 计算哈希。 */
  private static class BucketDecimal extends Bucket<BigDecimal>
      implements SerializableFunction<BigDecimal, Integer> {

    private BucketDecimal(int numBuckets) {
      super(numBuckets);
    }

    @Override
    protected int hash(BigDecimal value) {
      return BucketUtil.hash(value);
    }
  }
}
