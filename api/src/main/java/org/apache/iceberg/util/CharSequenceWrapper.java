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

import java.io.Serializable;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.JavaHashes;

/**
 * {@link CharSequence} 包装器：使不同类型的字符序列（String、StringBuilder 等）在 Map/Set 中 按内容判等与哈希，而非按各自类型特定的
 * equals/hashCode。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link CharSequence}，委托给被包装的序列。
 *   <li>覆盖 {@link #equals}/{@link #hashCode}，按内容比较与哈希，统一判等语义。
 *   <li>提供 {@link #set(CharSequence)} 可替换内部序列，便于作为可复用 key。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>JDK 中 String 与 StringBuilder 的 equals 互不相等，直接作为集合元素无法去重。本类 通过 {@link
 *       Comparators#charSequences()} 比较内容、{@link JavaHashes#hashCode} 计算 哈希，保证内容相同的序列判等一致。
 *   <li>可变设计：{@link #set} 允许复用同一 wrapper 实例承载不同序列，配合 {@link CharSequenceSet} 的 ThreadLocal
 *       缓存减少对象分配。
 *   <li>可序列化：继承 {@link Serializable}。
 * </ul>
 *
 * <p>上下游关系：作为 {@link CharSequenceSet} 的元素类型；也可用于 Map 的 key。
 */
public class CharSequenceWrapper implements CharSequence, Serializable {
  /**
   * 包装给定字符序列。
   *
   * @param seq 待包装的字符序列
   * @return 包装实例
   */
  public static CharSequenceWrapper wrap(CharSequence seq) {
    return new CharSequenceWrapper(seq);
  }

  private CharSequence wrapped;

  private CharSequenceWrapper(CharSequence wrapped) {
    this.wrapped = wrapped;
  }

  /**
   * 替换内部被包装的字符序列，返回自身以支持链式调用。
   *
   * @param newWrapped 新的字符序列
   * @return 本包装器
   */
  public CharSequenceWrapper set(CharSequence newWrapped) {
    this.wrapped = newWrapped;
    return this;
  }

  /**
   * 返回被包装的字符序列。
   *
   * @return 被包装的序列
   */
  public CharSequence get() {
    return wrapped;
  }

  /**
   * 按内容判断相等。
   *
   * <p>逻辑：同一对象返回 true；非 CharSequenceWrapper 返回 false；否则用 {@link Comparators#charSequences()}
   * 比较两者内容是否一致。
   *
   * @param other 待比较对象
   * @return 内容是否相等
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof CharSequenceWrapper)) {
      return false;
    }

    CharSequenceWrapper that = (CharSequenceWrapper) other;
    return Comparators.charSequences().compare(wrapped, that.wrapped) == 0;
  }

  /**
   * 按内容计算哈希值。
   *
   * @return 被包装序列的内容哈希
   */
  @Override
  public int hashCode() {
    return JavaHashes.hashCode(wrapped);
  }

  @Override
  public int length() {
    return wrapped.length();
  }

  @Override
  public char charAt(int index) {
    return wrapped.charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return wrapped.subSequence(start, end);
  }

  @Override
  public String toString() {
    return wrapped.toString();
  }
}
