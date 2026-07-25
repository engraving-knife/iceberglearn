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
package org.apache.iceberg.actions;

import java.util.Map;
import java.util.Set;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.util.SortOrderUtil;

/**
 * 按列排序重写数据文件的策略。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：在 {@link BinPackStrategy} 基础上，重写时按指定 {@link SortOrder} 对数据重新排序， 使文件内数据按列最优布局。例如对按列 x 排序的文件
 * A(x:0-50)、B(x:10-40)、C(x:30-60)， 重写后可得到 A'(x:0-20)、B'(x:21-40)、C'(x:41-60)。
 *
 * <p>设计意图：通过排序重写改善数据聚集度，提升后续按该列过滤的查询性能。当前不做文件重叠检测， 当 {@link #REWRITE_ALL} 为 true 时重写全部文件，否则沿用
 * {@link BinPackStrategy} 的选文件逻辑。
 *
 * <p>上下游关系：继承 {@link BinPackStrategy}，被具体引擎的排序重写动作使用。
 *
 * @deprecated since 1.3.0, will be removed in 1.4.0; use {@link SizeBasedFileRewriter} instead.
 *     Note: This can only be removed once Spark 3.2 isn't using this API anymore.
 */
@Deprecated
public abstract class SortStrategy extends BinPackStrategy {

  private SortOrder sortOrder;

  /**
   * 设置重写时使用的排序顺序。
   *
   * <p>逻辑：禁止传入未排序的 order；通过 {@link SortOrderUtil#buildSortOrder} 结合表结构构建 完整排序顺序并保存。
   *
   * @param order 排序顺序
   * @return 当前策略实例（链式调用）
   */
  public SortStrategy sortOrder(SortOrder order) {
    Preconditions.checkArgument(!order.isUnsorted(), "Cannot set strategy sort order: unsorted");
    this.sortOrder = SortOrderUtil.buildSortOrder(table(), order);
    return this;
  }

  /** 返回当前排序顺序。 */
  protected SortOrder sortOrder() {
    return sortOrder;
  }

  /** 返回策略名称 "SORT"。 */
  @Override
  public String name() {
    return "SORT";
  }

  /** 返回本策略可接受的选项白名单（沿用父类）。 */
  @Override
  public Set<String> validOptions() {
    return ImmutableSet.<String>builder().addAll(super.validOptions()).build();
  }

  /**
   * 解析选项并确保排序顺序已设置。
   *
   * <p>逻辑：先调用父类 {@code options} 解析 BinPack 相关选项；若未显式设置排序顺序，则回退到 表自身的排序顺序；最后校验选项合法性。
   *
   * @param options 选项键值对
   * @return 当前策略实例
   */
  @Override
  public RewriteStrategy options(Map<String, String> options) {
    super.options(options); // Also checks validity of BinPack options

    if (sortOrder == null) {
      sortOrder = table().sortOrder();
    }

    validateOptions();
    return this;
  }

  /**
   * 校验排序顺序与表 schema 的合法性。
   *
   * <p>逻辑：确保排序顺序非空且非未排序，并检查其与表 schema 的兼容性。
   */
  protected void validateOptions() {
    Preconditions.checkArgument(
        !sortOrder.isUnsorted(),
        "Can't use %s when there is no sort order, either define table %s's sort order or set sort"
            + "order in the action",
        name(),
        table().name());

    SortOrder.checkCompatibility(sortOrder, table().schema());
  }
}
