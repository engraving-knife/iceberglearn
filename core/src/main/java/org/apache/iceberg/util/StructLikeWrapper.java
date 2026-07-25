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
package org.apache.iceberg.util;

import java.util.Comparator;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.JavaHash;
import org.apache.iceberg.types.Types;

/**
 * 将 {@link StructLike} 适配为可用于 Map/Set 的包装器，通过实现 {@link #equals(Object)} 与 {@link #hashCode()}
 * 提供基于结构内容的相等性语义。
 *
 * <p>所属模块：iceberg-core；层次定位：通用类型/值对象工具层。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>包装一个 {@link StructLike} 行，使其可作为哈希键使用
 *   <li>基于类型派生的 {@link Comparator} 与 {@link JavaHash} 提供相等性与哈希计算
 *   <li>支持廉价地复用同一包装器（{@link #copyFor(StructLike)}）以避免重复类型分析
 * </ul>
 *
 * <p>设计意图：{@link StructLike} 本身只定义访问接口，不提供 equals/hashCode；直接使用引用相等无法
 * 满足按值去重/聚合的需求。本包装器在构造期一次性确定比较器与哈希函数，运行期只做值比较，避免在 每次 equals/hashCode 时重复推导类型信息。哈希值采用惰性计算并缓存（{@code
 * hashCode} 字段）， 在 {@link #set(StructLike)} 切换底层行时失效重算。该类非线程安全。
 *
 * <p>上下游关系：依赖 {@link Comparators}、{@link JavaHash}、{@link Types.StructType}；
 * 被需要在内存中按行去重或聚合的上层逻辑使用（如删除文件聚合、合并扫描结果去重等）。
 */
public class StructLikeWrapper {

  /**
   * 根据结构类型构造一个空的 {@link StructLikeWrapper}。
   *
   * @param struct 结构类型描述，用于派生比较器与哈希函数
   * @return 新建的空包装器
   */
  public static StructLikeWrapper forType(Types.StructType struct) {
    return new StructLikeWrapper(struct);
  }

  /** 基于类型派生的行比较器。 */
  private final Comparator<StructLike> comparator;
  /** 基于类型派生的行哈希函数。 */
  private final JavaHash<StructLike> structHash;
  /** 缓存的哈希值，{@code null} 表示需要重算。 */
  private Integer hashCode;
  /** 当前包装的 {@link StructLike} 行。 */
  private StructLike struct;

  /**
   * 私有构造器，根据结构类型派生比较器与哈希函数。
   *
   * @param type 结构类型描述
   */
  private StructLikeWrapper(Types.StructType type) {
    this(Comparators.forType(type), JavaHash.forType(type));
  }

  /**
   * 私有构造器，直接指定比较器与哈希函数，用于 {@link #copyFor(StructLike)} 复用。
   *
   * @param comparator 行比较器
   * @param structHash 行哈希函数
   */
  private StructLikeWrapper(Comparator<StructLike> comparator, JavaHash<StructLike> structHash) {
    this.comparator = comparator;
    this.structHash = structHash;
    this.hashCode = null;
  }

  /**
   * 创建一个包装指定行的副本包装器。
   *
   * <p>逻辑：复用当前包装器已构造的比较器与哈希函数构造新包装器，并绑定到新行，避免重复类型分析。
   *
   * @param newStruct 要包装的 {@link StructLike} 行
   * @return 包装给定行的新 {@link StructLikeWrapper}
   */
  public StructLikeWrapper copyFor(StructLike newStruct) {
    return new StructLikeWrapper(comparator, structHash).set(newStruct);
  }

  /**
   * 将当前包装器绑定到新的行，并使缓存的哈希值失效。
   *
   * @param newStruct 要包装的新行
   * @return 当前包装器自身，便于链式调用
   */
  public StructLikeWrapper set(StructLike newStruct) {
    this.struct = newStruct;
    this.hashCode = null;
    return this;
  }

  /** 返回 当前包装的 {@link StructLike} 行。 */
  public StructLike get() {
    return struct;
  }

  /**
   * 基于结构内容比较两个包装器是否相等。
   *
   * <p>逻辑：先做引用相等短路；若一方为 null 另一方非 null 直接返回 false；否则用比较器比较底层行。
   *
   * @param other 待比较对象
   * @return 是否相等
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof StructLikeWrapper)) {
      return false;
    }

    StructLikeWrapper that = (StructLikeWrapper) other;

    if (this.struct == that.struct) {
      return true;
    }

    if (this.struct == null ^ that.struct == null) {
      return false;
    }

    return comparator.compare(this.struct, that.struct) == 0;
  }

  /**
   * 计算并缓存底层行的哈希值。
   *
   * <p>逻辑：若缓存为空则调用类型派生的 {@link JavaHash} 计算并写入缓存，后续直接返回缓存值； 缓存会在 {@link #set(StructLike)} 时被清空。
   *
   * @return 底层行的哈希值
   */
  @Override
  public int hashCode() {
    if (hashCode == null) {
      this.hashCode = structHash.hash(struct);
    }

    return hashCode;
  }
}
