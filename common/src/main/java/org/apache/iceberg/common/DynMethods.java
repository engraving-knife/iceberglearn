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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Throwables;

/**
 * 文件级说明：动态方法调用工具类（基于反射）。
 *
 * <p>所属模块：iceberg-common（最底层的公共工具模块，被 api/core 及各引擎模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对 {@link java.lang.reflect.Method} 进行封装，提供类型安全的方法反射调用能力。
 *   <li>把反射产生的受检异常（InvocationTargetException 等）统一包装为 RuntimeException， 让调用方免于繁琐的 try-catch。
 *   <li>提供“未绑定（Unbound）/已绑定（Bound）/静态（Static）”三种方法视图， 区分实例方法、静态方法以及与方法所属对象绑定的调用语义。
 *   <li>通过 Builder 按“候选逐个尝试，命中即止”的策略定位目标方法，屏蔽跨版本差异。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>跨版本兼容：Iceberg 需要在不同版本的 Spark/Flink/Hive 等引擎中运行，这些引擎的 内部类方法签名可能随版本变化。通过 Builder 依次尝试多个“候选
 *       类名+方法名+参数”组合， 命中第一个可用实现即可，从而屏蔽底层版本差异。这是整个 Dyn* 系列工具的核心思想。
 *   <li>懒求值与短路：Builder 只在尚未命中时才继续尝试下一个候选，避免无谓反射开销。
 *   <li>NOOP 空对象：当允许“找不到也无所谓”时，返回一个不执行任何操作、返回 null 的空实现， 使调用链不必处理 null 分支，符合空对象模式。
 *   <li>受检/非受检双版本：提供 buildChecked（抛 NoSuchMethodException）与 build（抛
 *       RuntimeException）两套构建接口，适配不同调用场景的异常处理偏好。
 * </ul>
 *
 * <p>上下游关系：本类不依赖 Iceberg 其他业务模块，仅依赖 relocated guava；被 core 及各引擎 集成模块用于调用引擎内部私有/隐藏方法。{@link
 * DynConstructors} 复用本类的 UnboundMethod 作为构造器调用的基类。
 */
public class DynMethods {

  private DynMethods() {}

  /**
   * 未绑定方法视图：对 {@link java.lang.reflect.Method} 的便捷封装。
   *
   * <p>设计要点：将反射调用产生的各类异常统一包装为 RuntimeException，使调用方可用单一 catch 块处理，或完全免于受检异常处理。“未绑定”指方法尚未与具体接收者对象关联，
   * 调用时需显式传入 target。
   */
  public static class UnboundMethod {

    private final Method method;
    private final String name;
    private final int argLength;

    UnboundMethod(Method method, String name) {
      this.method = method;
      this.name = name;
      this.argLength =
          (method == null || method.isVarArgs()) ? -1 : method.getParameterTypes().length;
    }

    /**
     * 反射调用目标方法（受检版本）。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>若 argLength &lt; 0（方法为可变参数或为 NOOP），直接将全部 args 透传给 {@link Method#invoke(Object,
     *       Object...)}。
     *   <li>否则按方法形参数量截取 args 的前 argLength 个参数后再调用，避免多余参数导致异常。
     *   <li>捕获 InvocationTargetException：先尝试把真实原因按 Exception/RuntimeException 透明抛出，否则包装为
     *       RuntimeException 抛出，保留原始异常栈。
     * </ol>
     *
     * @param target 方法调用的接收者对象；静态方法可传 null
     * @param args 方法实参数组
     * @param <R> 返回值类型（由调用方指定，做未检查强转）
     * @return 方法返回值（已强转为 R）
     * @throws Exception 当被调方法本身抛出受检异常时透传抛出
     */
    @SuppressWarnings("unchecked")
    public <R> R invokeChecked(Object target, Object... args) throws Exception {
      try {
        if (argLength < 0) {
          return (R) method.invoke(target, args);
        } else {
          return (R) method.invoke(target, Arrays.copyOfRange(args, 0, argLength));
        }

      } catch (InvocationTargetException e) {
        Throwables.propagateIfInstanceOf(e.getCause(), Exception.class);
        Throwables.propagateIfInstanceOf(e.getCause(), RuntimeException.class);
        throw Throwables.propagate(e.getCause());
      }
    }

    /**
     * 反射调用目标方法（非受检版本）。
     *
     * <p>设计要点：委托 {@link #invokeChecked(Object, Object...)} 执行，并将其抛出的受检 Exception 透明转换为
     * RuntimeException，便于在不关心受检异常的调用链中使用。
     *
     * @param target 方法调用的接收者对象；静态方法可传 null
     * @param args 方法实参数组
     * @param <R> 返回值类型
     * @return 方法返回值
     */
    public <R> R invoke(Object target, Object... args) {
      try {
        return this.invokeChecked(target, args);
      } catch (Exception e) {
        Throwables.propagateIfInstanceOf(e, RuntimeException.class);
        throw Throwables.propagate(e);
      }
    }

    /**
     * 将本方法绑定到指定接收者对象，返回 BoundMethod（已绑定视图）。
     *
     * <p>设计要点：
     *
     * <ul>
     *   <li>静态方法不可绑定（静态方法应通过 {@link #asStatic()} 使用）。
     *   <li>校验接收者类型与方法声明类兼容（isAssignableFrom），避免运行期反射异常。
     * </ul>
     *
     * @param receiver 方法调用的接收者对象
     * @return 与 receiver 绑定的 {@link BoundMethod}
     * @throws IllegalStateException 若方法是静态方法
     * @throws IllegalArgumentException 若 receiver 类型与方法声明类不兼容
     */
    public BoundMethod bind(Object receiver) {
      Preconditions.checkState(
          !isStatic(), "Cannot bind static method %s", method.toGenericString());
      Preconditions.checkArgument(
          method.getDeclaringClass().isAssignableFrom(receiver.getClass()),
          "Cannot bind %s to instance of %s",
          method.toGenericString(),
          receiver.getClass());

      return new BoundMethod(this, receiver);
    }

    /** 返回该方法是否为静态方法。 */
    public boolean isStatic() {
      return Modifier.isStatic(method.getModifiers());
    }

    /** 返回本方法是否为 NOOP 空操作实现。 */
    public boolean isNoop() {
      return this == NOOP;
    }

    /**
     * 将本方法转换为 StaticMethod（静态方法视图）。
     *
     * @return 包装本方法的 {@link StaticMethod}
     * @throws IllegalStateException 若方法不是静态方法
     */
    public StaticMethod asStatic() {
      Preconditions.checkState(isStatic(), "Method is not static");
      return new StaticMethod(this);
    }

    @Override
    public String toString() {
      return "DynMethods.UnboundMethod(name=" + name + " method=" + method.toGenericString() + ")";
    }

    /**
     * NOOP 单例：空对象模式的实现。
     *
     * <p>设计意图：当 Builder 调用 {@link Builder#orNoop()} 且未命中任何候选方法时，返回此单例。 它不执行任何操作并返回 null，使调用方无需处理
     * null 分支。覆写了 bind/asStatic 等方法 以保证在“空操作”语义下仍可正常参与调用链。
     */
    private static final UnboundMethod NOOP =
        new UnboundMethod(null, "NOOP") {
          @Override
          public <R> R invokeChecked(Object target, Object... args) throws Exception {
            return null;
          }

          @Override
          public BoundMethod bind(Object receiver) {
            return new BoundMethod(this, receiver);
          }

          @Override
          public StaticMethod asStatic() {
            return new StaticMethod(this);
          }

          @Override
          public boolean isStatic() {
            return true;
          }

          @Override
          public String toString() {
            return "DynMethods.UnboundMethod(NOOP)";
          }
        };
  }

  /**
   * 已绑定方法视图：将 {@link UnboundMethod} 与固定接收者对象绑定后的产物。
   *
   * <p>设计要点：持有 receiver 引用，调用时无需再传入目标对象，便于在已知接收者的场景下 反复调用。
   */
  public static class BoundMethod {
    private final UnboundMethod method;
    private final Object receiver;

    private BoundMethod(UnboundMethod method, Object receiver) {
      this.method = method;
      this.receiver = receiver;
    }

    /**
     * 在已绑定的接收者上反射调用方法（受检版本）。
     *
     * @param args 方法实参数组
     * @param <R> 返回值类型
     * @return 方法返回值
     * @throws Exception 当被调方法本身抛出受检异常时透传抛出
     */
    public <R> R invokeChecked(Object... args) throws Exception {
      return method.invokeChecked(receiver, args);
    }

    /**
     * 在已绑定的接收者上反射调用方法（非受检版本）。
     *
     * @param args 方法实参数组
     * @param <R> 返回值类型
     * @return 方法返回值
     */
    public <R> R invoke(Object... args) {
      return method.invoke(receiver, args);
    }
  }

  /**
   * 静态方法视图：包装一个静态 {@link UnboundMethod}，调用时无需接收者对象。
   *
   * <p>设计要点：内部以 null 作为接收者调用 UnboundMethod，对应 Java 反射对静态方法的约定。
   */
  public static class StaticMethod {
    private final UnboundMethod method;

    private StaticMethod(UnboundMethod method) {
      this.method = method;
    }

    /**
     * 反射调用静态方法（受检版本）。
     *
     * @param args 方法实参数组
     * @param <R> 返回值类型
     * @return 方法返回值
     * @throws Exception 当被调方法本身抛出受检异常时透传抛出
     */
    public <R> R invokeChecked(Object... args) throws Exception {
      return method.invokeChecked(null, args);
    }

    /**
     * 反射调用静态方法（非受检版本）。
     *
     * @param args 方法实参数组
     * @param <R> 返回值类型
     * @return 方法返回值
     */
    public <R> R invoke(Object... args) {
      return method.invoke(null, args);
    }
  }

  /**
   * 创建一个用于动态定位方法的 Builder。
   *
   * @param methodName 待查找的方法名
   * @return 新的 {@link Builder} 实例
   */
  public static Builder builder(String methodName) {
    return new Builder(methodName);
  }

  /**
   * 方法查找构建器：采用“候选逐个尝试，命中即止”的策略定位目标方法。
   *
   * <p>设计意图：调用方按优先级依次注册多个候选（类名+方法名+参数类型），Builder 仅在尚未 命中时才尝试下一个候选，从而实现跨版本/跨实现的兼容查找。提供 impl（公开方法）与
   * hiddenImpl（非公开方法）两套候选注册接口，并通过 orNoop 支持空对象回退。
   */
  public static class Builder {
    private final String name;
    private ClassLoader loader = Thread.currentThread().getContextClassLoader();
    private UnboundMethod method = null;

    public Builder(String methodName) {
      this.name = methodName;
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
     * 若尚未命中任何实现，则使用 NOOP 空方法作为回退。
     *
     * <p>注意：调用本方法后再调用 impl 系列方法将不会匹配（因 method 已被设为 NOOP）。
     *
     * @return this，便于链式调用
     */
    public Builder orNoop() {
      if (method == null) {
        this.method = UnboundMethod.NOOP;
      }
      return this;
    }

    /**
     * 注册一个候选实现：先按类名加载类，再查找其公开方法。
     *
     * <p>逻辑：若已命中方法则直接返回（短路）；否则用 ClassLoader 反射加载类并委托 {@link #impl(Class, String, Class[])}
     * 查找方法；类不存在则忽略并继续。
     *
     * @param className 候选类全限定名
     * @param methodName 候选方法名
     * @param argClasses 方法形参类型数组
     * @return this，便于链式调用
     */
    public Builder impl(String className, String methodName, Class<?>... argClasses) {
      // don't do any work if an implementation has been found
      if (method != null) {
        return this;
      }

      try {
        Class<?> targetClass = Class.forName(className, true, loader);
        impl(targetClass, methodName, argClasses);
      } catch (ClassNotFoundException e) {
        // not the right implementation
      }
      return this;
    }

    /**
     * 注册一个候选实现：先按类名加载类，再用 Builder 构造时传入的方法名查找公开方法。
     *
     * @param className 候选类全限定名
     * @param argClasses 方法形参类型数组
     * @return this，便于链式调用
     */
    public Builder impl(String className, Class<?>... argClasses) {
      impl(className, name, argClasses);
      return this;
    }

    /**
     * 注册一个候选实现：在指定类上查找公开方法。
     *
     * <p>逻辑：若已命中方法则直接返回（短路）；否则通过 {@link Class#getMethod(String, Class[])} 查找公开方法并包装为
     * UnboundMethod；未找到则忽略并继续。
     *
     * @param targetClass 候选类实例
     * @param methodName 候选方法名
     * @param argClasses 方法形参类型数组
     * @return this，便于链式调用
     */
    public Builder impl(Class<?> targetClass, String methodName, Class<?>... argClasses) {
      // don't do any work if an implementation has been found
      if (method != null) {
        return this;
      }

      try {
        this.method = new UnboundMethod(targetClass.getMethod(methodName, argClasses), name);
      } catch (NoSuchMethodException e) {
        // not the right implementation
      }
      return this;
    }

    /**
     * 注册一个候选实现：在指定类上用 Builder 构造时传入的方法名查找公开方法。
     *
     * @param targetClass 候选类实例
     * @param argClasses 方法形参类型数组
     * @return this，便于链式调用
     */
    public Builder impl(Class<?> targetClass, Class<?>... argClasses) {
      impl(targetClass, name, argClasses);
      return this;
    }

    /**
     * 注册一个候选实现：查找指定类的公开构造函数，并包装为可调用的 UnboundMethod。
     *
     * <p>设计意图：某些场景下“方法调用”实际是构造新对象，本方法委托 {@link DynConstructors.Builder} 查找构造器，使方法调用与对象构造统一抽象。
     *
     * @param targetClass 候选类实例
     * @param argClasses 构造器形参类型数组
     * @return this，便于链式调用
     */
    public Builder ctorImpl(Class<?> targetClass, Class<?>... argClasses) {
      // don't do any work if an implementation has been found
      if (method != null) {
        return this;
      }

      try {
        this.method = new DynConstructors.Builder().impl(targetClass, argClasses).buildChecked();
      } catch (NoSuchMethodException e) {
        // not the right implementation
      }
      return this;
    }

    /**
     * 注册一个候选实现：按类名加载类并查找其公开构造函数，包装为可调用的 UnboundMethod。
     *
     * @param className 候选类全限定名
     * @param argClasses 构造器形参类型数组
     * @return this，便于链式调用
     */
    public Builder ctorImpl(String className, Class<?>... argClasses) {
      // don't do any work if an implementation has been found
      if (method != null) {
        return this;
      }

      try {
        this.method = new DynConstructors.Builder().impl(className, argClasses).buildChecked();
      } catch (NoSuchMethodException e) {
        // not the right implementation
      }
      return this;
    }

    /**
     * 注册一个“隐藏实现”候选：先按类名加载类，再查找其非公开方法。
     *
     * <p>逻辑：若已命中方法则直接返回（短路）；否则用 ClassLoader 反射加载类并委托 {@link #hiddenImpl(Class, String, Class[])}
     * 查找非公开方法；类不存在则忽略并继续。
     *
     * @param className 候选类全限定名
     * @param methodName 候选方法名
     * @param argClasses 方法形参类型数组
     * @return this，便于链式调用
     */
    public Builder hiddenImpl(String className, String methodName, Class<?>... argClasses) {
      // don't do any work if an implementation has been found
      if (method != null) {
        return this;
      }

      try {
        Class<?> targetClass = Class.forName(className, true, loader);
        hiddenImpl(targetClass, methodName, argClasses);
      } catch (ClassNotFoundException e) {
        // not the right implementation
      }
      return this;
    }

    /**
     * 注册一个“隐藏实现”候选：先按类名加载类，再用 Builder 构造时传入的方法名查找非公开方法。
     *
     * @param className 候选类全限定名
     * @param argClasses 方法形参类型数组
     * @return this，便于链式调用
     */
    public Builder hiddenImpl(String className, Class<?>... argClasses) {
      hiddenImpl(className, name, argClasses);
      return this;
    }

    /**
     * 注册一个“隐藏实现”候选：访问指定类的非公开（private/protected）方法。
     *
     * <p>逻辑：通过 {@link Class#getDeclaredMethod(String, Class[])} 获取声明方法（含非公开）， 再用 {@link
     * AccessController#doPrivileged} 包裹 {@code setAccessible(true)} 以绕过
     * 访问检查。失败（SecurityException/NoSuchMethodException）时忽略并继续。
     *
     * <p>设计意图：引擎内部许多方法是非公开的，Iceberg 需要调用它们以实现集成， 故提供 hidden 系列方法在受控前提下访问隐藏方法。
     *
     * @param targetClass 目标类实例
     * @param methodName 方法名
     * @param argClasses 方法形参类型数组
     * @return this，便于链式调用
     */
    public Builder hiddenImpl(Class<?> targetClass, String methodName, Class<?>... argClasses) {
      // don't do any work if an implementation has been found
      if (method != null) {
        return this;
      }

      try {
        Method hidden = targetClass.getDeclaredMethod(methodName, argClasses);
        AccessController.doPrivileged(new MakeAccessible(hidden));
        this.method = new UnboundMethod(hidden, name);
      } catch (SecurityException | NoSuchMethodException e) {
        // unusable or not the right implementation
      }
      return this;
    }

    /**
     * 注册一个“隐藏实现”候选：在指定类上用 Builder 构造时传入的方法名查找非公开方法。
     *
     * @param targetClass 目标类实例
     * @param argClasses 方法形参类型数组
     * @return this，便于链式调用
     */
    public Builder hiddenImpl(Class<?> targetClass, Class<?>... argClasses) {
      hiddenImpl(targetClass, name, argClasses);
      return this;
    }

    /**
     * 构建并返回命中的方法（非受检版本）。
     *
     * @return 命中的 {@link UnboundMethod}
     * @throws RuntimeException 若无任何候选命中且未设置 orNoop
     */
    public UnboundMethod build() {
      if (method != null) {
        return method;
      } else {
        throw new RuntimeException("Cannot find method: " + name);
      }
    }

    /**
     * 构建并返回命中的方法，并将其绑定到指定接收者（非受检版本）。
     *
     * @param receiver 方法调用的接收者对象
     * @return 与 receiver 绑定的 {@link BoundMethod}
     * @throws IllegalStateException 若方法是静态方法
     * @throws IllegalArgumentException 若 receiver 类型与方法声明类不兼容
     * @throws RuntimeException 若无任何候选命中且未设置 orNoop
     */
    public BoundMethod build(Object receiver) {
      return build().bind(receiver);
    }

    /**
     * 构建并返回命中的方法（受检版本）。
     *
     * @return 命中的 {@link UnboundMethod}
     * @throws NoSuchMethodException 若无任何候选命中且未设置 orNoop
     */
    public UnboundMethod buildChecked() throws NoSuchMethodException {
      if (method != null) {
        return method;
      } else {
        throw new NoSuchMethodException("Cannot find method: " + name);
      }
    }

    /**
     * 构建并返回命中的方法，并将其绑定到指定接收者（受检版本）。
     *
     * @param receiver 方法调用的接收者对象
     * @return 与 receiver 绑定的 {@link BoundMethod}
     * @throws IllegalStateException 若方法是静态方法
     * @throws IllegalArgumentException 若 receiver 类型与方法声明类不兼容
     * @throws NoSuchMethodException 若无任何候选命中且未设置 orNoop
     */
    public BoundMethod buildChecked(Object receiver) throws NoSuchMethodException {
      return buildChecked().bind(receiver);
    }

    /**
     * 构建并返回命中的静态方法（受检版本）。
     *
     * @return 命中的 {@link StaticMethod}
     * @throws IllegalStateException 若方法不是静态方法
     * @throws NoSuchMethodException 若无任何候选命中且未设置 orNoop
     */
    public StaticMethod buildStaticChecked() throws NoSuchMethodException {
      return buildChecked().asStatic();
    }

    /**
     * 构建并返回命中的静态方法（非受检版本）。
     *
     * @return 命中的 {@link StaticMethod}
     * @throws IllegalStateException 若方法不是静态方法
     * @throws RuntimeException 若无任何候选命中且未设置 orNoop
     */
    public StaticMethod buildStatic() {
      return build().asStatic();
    }
  }

  /**
   * 特权动作：在受控前提下对隐藏方法调用 setAccessible(true)。
   *
   * <p>设计意图：通过 {@link AccessController#doPrivileged} 提升 privileges，绕过 SecurityManager
   * 对非公开成员访问检查的限制，用于 hiddenImpl 系列方法。
   */
  private static class MakeAccessible implements PrivilegedAction<Void> {
    private Method hidden;

    MakeAccessible(Method hidden) {
      this.hidden = hidden;
    }

    @Override
    public Void run() {
      hidden.setAccessible(true);
      return null;
    }
  }
}
