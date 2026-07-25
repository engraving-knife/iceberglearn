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
package org.apache.iceberg.common;

import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;

/**
 * 文件级说明：动态类加载/查找工具类（基于反射）。
 *
 * <p>所属模块：iceberg-common（最底层的公共工具模块，被 api/core 及各引擎模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供按类名加载 {@link Class} 的能力，支持自定义 ClassLoader。
 *   <li>通过 Builder 按“候选逐个尝试，命中即止”的策略定位目标类，屏蔽跨版本差异。
 *   <li>收集所有失败候选类名，在未命中时拼入异常信息便于排查。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>跨版本兼容：Iceberg 需要在不同版本的 Spark/Flink/Hive 等引擎中运行，目标类的全限定名 可能随版本变化（类被移动或重命名）。通过 Builder
 *       依次尝试多个候选类名，命中第一个可用 实现即可。这是整个 Dyn* 系列工具的核心思想。
 *   <li>懒求值与短路：Builder 只在尚未命中时才尝试加载下一个候选，避免无谓反射开销。
 *   <li>orNull 空值回退：当允许“找不到也无所谓”时，返回 null 而非抛异常，使调用方按需处理。
 *   <li>受检/非受检双版本：提供 buildChecked（抛 ClassNotFoundException）与 build（抛
 *       RuntimeException）两套构建接口，适配不同调用场景的异常处理偏好。
 * </ul>
 *
 * <p>上下游关系：本类不依赖 Iceberg 其他业务模块，仅依赖 relocated guava；被 core 及各引擎 集成模块用于按版本兼容方式加载引擎内部类。与 {@link
 * DynMethods}、{@link DynConstructors}、 {@link DynFields} 同属 Dyn* 动态反射工具系列，本类是其中最基础的“类定位”工具。
 */
public class DynClasses {

  private DynClasses() {}

  /**
   * 创建一个用于动态查找类的 Builder。
   *
   * @return 新的 {@link Builder} 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 类查找构建器：采用“候选逐个尝试，命中即止”的策略定位目标类。
   *
   * <p>设计意图：调用方按优先级依次注册多个候选类名，Builder 仅在尚未命中时才尝试加载下一个 候选，从而实现跨版本/跨实现的兼容查找。所有候选类名（无论成功与否）都会被记录到
   * classNames 集合，最终在未命中时拼入异常信息，便于排查“为什么没找到类”。
   */
  public static class Builder {
    private ClassLoader loader = Thread.currentThread().getContextClassLoader();
    private Class<?> foundClass = null;
    private boolean nullOk = false;
    private Set<String> classNames = Sets.newLinkedHashSet();

    private Builder() {}

    /**
     * 设置用于按类名加载类的 {@link ClassLoader}。
     *
     * <p>若未设置，默认使用当前线程的上下文 ClassLoader。
     *
     * @param newLoader 类加载器
     * @return this，便于链式调用
     */
    public Builder loader(ClassLoader newLoader) {
      this.loader = newLoader;
      return this;
    }

    /**
     * 注册一个候选类名并尝试加载。
     *
     * <p>逻辑：先把候选类名记入 classNames 集合（用于最终异常信息）；若已命中类则直接返回 （短路）；否则通过 {@link Class#forName(String,
     * boolean, ClassLoader)} 加载类，命中则 记录到 foundClass；类不存在则忽略并继续，等待下一个候选。
     *
     * @param className 候选类全限定名
     * @return this，便于链式调用
     */
    public Builder impl(String className) {
      classNames.add(className);

      if (foundClass != null) {
        return this;
      }

      try {
        this.foundClass = Class.forName(className, true, loader);
      } catch (ClassNotFoundException e) {
        // not the right implementation
      }

      return this;
    }

    /**
     * 设置在未命中任何候选类时返回 null，而非抛出异常。
     *
     * @return this，便于链式调用
     */
    public Builder orNull() {
      this.nullOk = true;
      return this;
    }

    /**
     * 构建并返回命中的类（受检版本）。
     *
     * <p>逻辑：若未设置 orNull 且未命中任何候选，抛出 ClassNotFoundException，异常信息列出 所有候选类名便于排查；否则返回命中的 Class（可能为
     * null）。
     *
     * @param <S> 调用方期望的父类类型（做未检查强转）
     * @return 命中的 {@link Class}；若设置了 orNull 且未命中则为 null
     * @throws ClassNotFoundException 若未命中任何候选且未设置 orNull
     */
    @SuppressWarnings("unchecked")
    public <S> Class<? extends S> buildChecked() throws ClassNotFoundException {
      if (!nullOk && foundClass == null) {
        throw new ClassNotFoundException(
            "Cannot find class; alternatives: " + Joiner.on(", ").join(classNames));
      }
      return (Class<? extends S>) foundClass;
    }

    /**
     * 构建并返回命中的类（非受检版本）。
     *
     * <p>与 {@link #buildChecked()} 逻辑一致，区别仅在于未命中且未设置 orNull 时抛出 RuntimeException 而非
     * ClassNotFoundException，便于在不关心受检异常的调用链中使用。
     *
     * @param <S> 调用方期望的父类类型（做未检查强转）
     * @return 命中的 {@link Class}；若设置了 orNull 且未命中则为 null
     * @throws RuntimeException 若未命中任何候选且未设置 orNull
     */
    @SuppressWarnings("unchecked")
    public <S> Class<? extends S> build() {
      if (!nullOk && foundClass == null) {
        throw new RuntimeException(
            "Cannot find class; alternatives: " + Joiner.on(", ").join(classNames));
      }
      return (Class<? extends S>) foundClass;
    }
  }
}
