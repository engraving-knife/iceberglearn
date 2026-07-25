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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Throwables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：动态构造函数调用工具类（基于反射）。
 *
 * <p>所属模块：iceberg-common（最底层的公共工具模块，被 api/core 及各引擎模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对 {@link java.lang.reflect.Constructor} 进行封装，提供类型安全的对象实例化能力。
 *   <li>把反射构造产生的受检异常（InvocationTargetException 等）统一包装为 RuntimeException， 让调用方免于繁琐的 try-catch。
 *   <li>通过 Builder 按“候选逐个尝试，命中即止”的策略定位目标构造器，屏蔽跨版本差异。
 *   <li>收集所有失败候选及其异常，在未命中时作为 suppressed 异常拼入，便于排查。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>跨版本兼容：Iceberg 需要在不同版本的 Spark/Flink/Hive 等引擎中运行，目标类的构造器 签名可能随版本变化。通过 Builder 依次尝试多个“候选
 *       类名+参数类型”组合，命中第一个可用 实现即可，从而屏蔽底层版本差异。这是整个 Dyn* 系列工具的核心思想。
 *   <li>懒求值与短路：Builder 只在尚未命中时才尝试下一个候选，避免无谓反射开销。
 *   <li>复用 UnboundMethod：{@link Ctor} 继承自 {@link DynMethods.UnboundMethod}，使构造器
 *       调用与方法调用共享同一调用抽象（invoke/invokeChecked），便于在 {@link DynMethods.Builder} 中通过 ctorImpl 统一使用。
 *   <li>受检/非受检双版本：提供 buildChecked（抛 NoSuchMethodException）与 build（抛
 *       RuntimeException）两套构建接口，适配不同调用场景的异常处理偏好。
 * </ul>
 *
 * <p>上下游关系：本类不依赖 Iceberg 其他业务模块，仅依赖 relocated guava；继承 {@link DynMethods.UnboundMethod}，被 core
 * 及各引擎集成模块用于动态实例化引擎内部类。 与 {@link DynMethods}、{@link DynClasses}、{@link DynFields} 同属 Dyn* 动态反射工具系列。
 */
public class DynConstructors {

  private DynConstructors() {}

  /**
   * 构造器视图：继承 {@link DynMethods.UnboundMethod}，将构造器调用统一为方法调用语义。
   *
   * <p>设计要点：构造器没有传统意义上的接收者（target 必须为 null），故覆写 bind 抛出异常、 覆写 invoke/invokeChecked 校验 target 为 null
   * 后委托 newInstance。isStatic 恒为 true， 表示构造器调用不依赖实例。
   */
  public static class Ctor<C> extends DynMethods.UnboundMethod {
    private final Constructor<C> ctor;
    private final Class<? extends C> constructed;

    private Ctor(Constructor<C> constructor, Class<? extends C> constructed) {
      super(null, "newInstance");
      this.ctor = constructor;
      this.constructed = constructed;
    }

    /** 返回该构造器所构造的类型。 */
    public Class<? extends C> getConstructedClass() {
      return constructed;
    }

    /**
     * 通过反射构造新实例（受检版本）。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>若实参数量超过构造器形参数量，截取前 N 个参数（兼容可变参数与多余参数场景）。
     *   <li>否则直接透传 args 调用 {@link Constructor#newInstance(Object...)}。
     *   <li>捕获 InstantiationException/IllegalAccessException 直接抛出；捕获 InvocationTargetException 时按
     *       Exception/RuntimeException 透明抛出真实原因， 否则包装为 RuntimeException。
     * </ol>
     *
     * @param args 构造器实参数组
     * @return 新构造的实例
     * @throws Exception 当构造过程本身抛出受检异常时透传抛出
     */
    public C newInstanceChecked(Object... args) throws Exception {
      try {
        if (args.length > ctor.getParameterCount()) {
          return ctor.newInstance(Arrays.copyOfRange(args, 0, ctor.getParameterCount()));
        } else {
          return ctor.newInstance(args);
        }
      } catch (InstantiationException | IllegalAccessException e) {
        throw e;
      } catch (InvocationTargetException e) {
        Throwables.propagateIfInstanceOf(e.getCause(), Exception.class);
        Throwables.propagateIfInstanceOf(e.getCause(), RuntimeException.class);
        throw Throwables.propagate(e.getCause());
      }
    }

    /**
     * 通过反射构造新实例（非受检版本）。
     *
     * <p>设计要点：委托 {@link #newInstanceChecked(Object...)} 执行，并将其抛出的受检 Exception 透明转换为
     * RuntimeException，便于在不关心受检异常的调用链中使用。
     *
     * @param args 构造器实参数组
     * @return 新构造的实例
     */
    public C newInstance(Object... args) {
      try {
        return newInstanceChecked(args);
      } catch (Exception e) {
        Throwables.propagateIfInstanceOf(e, RuntimeException.class);
        throw Throwables.propagate(e);
      }
    }

    /**
     * 以方法调用语义触发构造（非受检版本）。
     *
     * <p>设计要点：target 必须为 null（构造器无接收者），校验后委托 {@link #newInstance(Object...)}。
     *
     * @param target 必须为 null
     * @param args 构造器实参数组
     * @param <R> 返回值类型
     * @return 新构造的实例
     */
    @Override
    @SuppressWarnings("unchecked")
    public <R> R invoke(Object target, Object... args) {
      Preconditions.checkArgument(
          target == null, "Invalid call to constructor: target must be null");
      return (R) newInstance(args);
    }

    /**
     * 以方法调用语义触发构造（受检版本）。
     *
     * @param target 必须为 null
     * @param args 构造器实参数组
     * @param <R> 返回值类型
     * @return 新构造的实例
     * @throws Exception 当构造过程本身抛出受检异常时透传抛出
     */
    @Override
    @SuppressWarnings("unchecked")
    public <R> R invokeChecked(Object target, Object... args) throws Exception {
      Preconditions.checkArgument(
          target == null, "Invalid call to constructor: target must be null");
      return (R) newInstanceChecked(args);
    }

    /**
     * 构造器不支持绑定接收者。
     *
     * @throws IllegalStateException 总是抛出，因构造器无法绑定到实例
     */
    @Override
    public DynMethods.BoundMethod bind(Object receiver) {
      throw new IllegalStateException("Cannot bind constructors");
    }

    /** 构造器视为静态调用，恒返回 true。 */
    @Override
    public boolean isStatic() {
      return true;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "(constructor=" + ctor + ", class=" + constructed + ")";
    }
  }

  /**
   * 创建一个无基类约束的构造器查找 Builder。
   *
   * @return 新的 {@link Builder} 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 创建一个以指定类为基类约束的构造器查找 Builder。
   *
   * @param baseClass 基类，用于在未命中时拼入异常信息辅助排查
   * @return 新的 {@link Builder} 实例
   */
  public static Builder builder(Class<?> baseClass) {
    return new Builder(baseClass);
  }

  /**
   * 构造器查找构建器：采用“候选逐个尝试，命中即止”的策略定位目标构造器。
   *
   * <p>设计意图：调用方按优先级依次注册多个候选（类名+参数类型），Builder 仅在尚未命中时 才尝试下一个候选，从而实现跨版本/跨实现的兼容查找。提供 impl（公开构造器）与
   * hiddenImpl（非公开构造器）两套候选注册接口。所有失败候选的异常会被收集到 problems 映射， 最终在未命中时作为 suppressed
   * 异常附加到抛出的异常上，便于定位每个候选为何失败。
   */
  public static class Builder {
    private final Class<?> baseClass;
    private ClassLoader loader = Thread.currentThread().getContextClassLoader();
    private Ctor ctor = null;
    private Map<String, Throwable> problems = Maps.newHashMap();

    public Builder(Class<?> baseClass) {
      this.baseClass = baseClass;
    }

    public Builder() {
      this.baseClass = null;
    }

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
     * 注册一个候选实现：先按类名加载类，再查找其公开构造器。
     *
     * <p>逻辑：若已命中构造器则直接返回（短路）；否则用 ClassLoader 反射加载类并委托 {@link #impl(Class, Class[])}
     * 查找构造器；类加载失败（NoClassDefFoundError/ ClassNotFoundException）时记录异常到 problems 并继续。
     *
     * @param className 候选类全限定名
     * @param types 构造器形参类型数组
     * @return this，便于链式调用
     */
    public Builder impl(String className, Class<?>... types) {
      // don't do any work if an implementation has been found
      if (ctor != null) {
        return this;
      }

      try {
        Class<?> targetClass = Class.forName(className, true, loader);
        impl(targetClass, types);
      } catch (NoClassDefFoundError | ClassNotFoundException e) {
        // cannot load this implementation
        problems.put(className, e);
      }
      return this;
    }

    /**
     * 注册一个候选实现：在指定类上查找公开构造器。
     *
     * <p>逻辑：若已命中构造器则直接返回（短路）；否则通过 {@link Class#getConstructor(Class[])} 查找公开构造器并包装为 Ctor；未找到则记录异常到
     * problems 并继续。
     *
     * @param targetClass 候选类实例
     * @param types 构造器形参类型数组
     * @param <T> 候选类类型
     * @return this，便于链式调用
     */
    public <T> Builder impl(Class<T> targetClass, Class<?>... types) {
      // don't do any work if an implementation has been found
      if (ctor != null) {
        return this;
      }

      try {
        ctor = new Ctor<T>(targetClass.getConstructor(types), targetClass);
      } catch (NoSuchMethodException e) {
        // not the right implementation
        problems.put(methodName(targetClass, types), e);
      }
      return this;
    }

    /**
     * 注册一个“隐藏实现”候选：在 Builder 构造时传入的 baseClass 上查找非公开构造器。
     *
     * @param types 构造器形参类型数组
     * @return this，便于链式调用
     */
    public Builder hiddenImpl(Class<?>... types) {
      hiddenImpl(baseClass, types);
      return this;
    }

    /**
     * 注册一个“隐藏实现”候选：先按类名加载类，再查找其非公开构造器。
     *
     * <p>逻辑：若已命中构造器则直接返回（短路）；否则用 ClassLoader 反射加载类并委托 {@link #hiddenImpl(Class, Class[])}
     * 查找非公开构造器；类加载失败时记录异常到 problems 并继续。
     *
     * @param className 候选类全限定名
     * @param types 构造器形参类型数组
     * @return this，便于链式调用
     */
    @SuppressWarnings("unchecked")
    public Builder hiddenImpl(String className, Class<?>... types) {
      // don't do any work if an implementation has been found
      if (ctor != null) {
        return this;
      }

      try {
        Class targetClass = Class.forName(className, true, loader);
        hiddenImpl(targetClass, types);
      } catch (NoClassDefFoundError | ClassNotFoundException e) {
        // cannot load this implementation
        problems.put(className, e);
      }
      return this;
    }

    /**
     * 注册一个“隐藏实现”候选：访问指定类的非公开（private/protected）构造器。
     *
     * <p>逻辑：通过 {@link Class#getDeclaredConstructor(Class[])} 获取声明构造器（含非公开）， 再用 {@link
     * AccessController#doPrivileged} 包裹 {@code setAccessible(true)} 以绕过
     * 访问检查。失败（SecurityException/NoSuchMethodException）时记录异常到 problems 并继续。
     *
     * <p>设计意图：引擎内部许多构造器是非公开的，Iceberg 需要实例化它们以实现集成， 故提供 hidden 系列方法在受控前提下访问隐藏构造器。
     *
     * @param targetClass 目标类实例
     * @param types 构造器形参类型数组
     * @param <T> 目标类类型
     * @return this，便于链式调用
     */
    public <T> Builder hiddenImpl(Class<T> targetClass, Class<?>... types) {
      // don't do any work if an implementation has been found
      if (ctor != null) {
        return this;
      }

      try {
        Constructor<T> hidden = targetClass.getDeclaredConstructor(types);
        AccessController.doPrivileged(new MakeAccessible(hidden));
        ctor = new Ctor<T>(hidden, targetClass);
      } catch (SecurityException e) {
        // unusable
        problems.put(methodName(targetClass, types), e);
      } catch (NoSuchMethodException e) {
        // not the right implementation
        problems.put(methodName(targetClass, types), e);
      }
      return this;
    }

    /**
     * 构建并返回命中的构造器（受检版本）。
     *
     * <p>逻辑：若已命中候选则返回该 Ctor；否则抛出由 {@link #buildCheckedException} 构造的 NoSuchMethodException，异常信息包含
     * baseClass 与所有失败候选的明细，并把每个失败候选的 异常作为 suppressed 附加，便于排查。
     *
     * @param <C> 构造器所构造的类型
     * @return 命中的 {@link Ctor}
     * @throws NoSuchMethodException 若无任何候选命中
     */
    @SuppressWarnings("unchecked")
    public <C> Ctor<C> buildChecked() throws NoSuchMethodException {
      if (ctor != null) {
        return ctor;
      }
      throw buildCheckedException(baseClass, problems);
    }

    /**
     * 构建并返回命中的构造器（非受检版本）。
     *
     * <p>与 {@link #buildChecked()} 逻辑一致，区别仅在于未命中时抛出 RuntimeException 而非
     * NoSuchMethodException，便于在不关心受检异常的调用链中使用。
     *
     * @param <C> 构造器所构造的类型
     * @return 命中的 {@link Ctor}
     * @throws RuntimeException 若无任何候选命中
     */
    @SuppressWarnings("unchecked")
    public <C> Ctor<C> build() {
      if (ctor != null) {
        return ctor;
      }
      throw buildRuntimeException(baseClass, problems);
    }
  }

  /**
   * 特权动作：在受控前提下对隐藏构造器调用 setAccessible(true)。
   *
   * <p>设计意图：通过 {@link AccessController#doPrivileged} 提升 privileges，绕过 SecurityManager
   * 对非公开成员访问检查的限制，用于 hiddenImpl 系列方法。
   */
  private static class MakeAccessible implements PrivilegedAction<Void> {
    private Constructor<?> hidden;

    MakeAccessible(Constructor<?> hidden) {
      this.hidden = hidden;
    }

    @Override
    public Void run() {
      hidden.setAccessible(true);
      return null;
    }
  }

  /**
   * 构造未命中时的受检异常，包含 baseClass 信息与所有失败候选明细。
   *
   * <p>设计要点：把 problems 中每个失败候选的异常通过 {@code addSuppressed} 附加到结果异常， 保留完整失败上下文，便于调用方定位每个候选为何失败。
   *
   * @param baseClass 基类，用于异常信息
   * @param problems 候选名到失败异常的映射
   * @return 携带所有失败上下文的 NoSuchMethodException
   */
  private static NoSuchMethodException buildCheckedException(
      Class<?> baseClass, Map<String, Throwable> problems) {
    NoSuchMethodException exc =
        new NoSuchMethodException(
            "Cannot find constructor for " + baseClass + "\n" + formatProblems(problems));
    problems.values().forEach(exc::addSuppressed);
    return exc;
  }

  /**
   * 构造未命中时的非受检异常，包含 baseClass 信息与所有失败候选明细。
   *
   * @param baseClass 基类，用于异常信息
   * @param problems 候选名到失败异常的映射
   * @return 携带所有失败上下文的 RuntimeException
   */
  private static RuntimeException buildRuntimeException(
      Class<?> baseClass, Map<String, Throwable> problems) {
    RuntimeException exc =
        new RuntimeException(
            "Cannot find constructor for " + baseClass + "\n" + formatProblems(problems));
    problems.values().forEach(exc::addSuppressed);
    return exc;
  }

  /**
   * 将失败候选映射格式化为多行字符串，每行描述一个候选的缺失原因。
   *
   * <p>逻辑：遍历 problems 入口，对每个候选拼接“Missing {候选名} [{异常类名}: {异常消息}]”， 以换行分隔，前置制表符缩进，便于在异常信息中阅读。
   *
   * @param problems 候选名到失败异常的映射
   * @return 格式化后的多行字符串
   */
  private static String formatProblems(Map<String, Throwable> problems) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, Throwable> problem : problems.entrySet()) {
      if (first) {
        first = false;
      } else {
        sb.append("\n");
      }
      sb.append("\tMissing ")
          .append(problem.getKey())
          .append(" [")
          .append(problem.getValue().getClass().getName())
          .append(": ")
          .append(problem.getValue().getMessage())
          .append("]");
    }
    return sb.toString();
  }

  /**
   * 拼接“类名(参数类型列表)”形式的候选标识字符串，用于 problems 映射的 key。
   *
   * @param targetClass 目标类
   * @param types 构造器形参类型数组
   * @return 形如 com.foo.Bar(java.lang.String,int) 的字符串
   */
  private static String methodName(Class<?> targetClass, Class<?>... types) {
    StringBuilder sb = new StringBuilder();
    sb.append(targetClass.getName()).append("(");
    boolean first = true;
    for (Class<?> type : types) {
      if (first) {
        first = false;
      } else {
        sb.append(",");
      }
      sb.append(type.getName());
    }
    sb.append(")");
    return sb.toString();
  }
}
