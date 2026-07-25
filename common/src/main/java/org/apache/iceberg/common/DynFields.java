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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Throwables;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;

/**
 * 文件级说明：动态字段访问工具类（基于反射）。
 *
 * <p>所属模块：iceberg-common（最底层的公共工具模块，被 api/core 及各引擎模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对 {@link java.lang.reflect.Field} 进行封装，提供类型安全的字段读取/写入能力。
 *   <li>把反射产生的受检异常（IllegalAccessException 等）统一包装为 RuntimeException， 让调用方免于繁琐的 try-catch。
 *   <li>提供“未绑定（Unbound）/已绑定（Bound）/静态（Static）”三种字段视图， 区分实例字段与静态字段的使用语义。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>跨版本兼容：Iceberg 需要在不同版本的 Spark/Flink/Hive 等引擎中运行，这些引擎的 内部类字段名/位置可能随版本变化。通过 Builder 依次尝试多个“候选
 *       类名+字段名”组合， 命中第一个可用实现即可，从而屏蔽底层版本差异。这是整个 Dyn* 系列工具的核心思想。
 *   <li>懒求值与短路：Builder 只在尚未命中时才继续尝试下一个候选，避免无谓反射开销。
 *   <li>AlwaysNull 占位：当允许“找不到也无所谓”时，返回一个永远为 null 的空实现， 使调用链不必处理 null 分支，符合空对象模式。
 * </ul>
 *
 * <p>上下游关系：本类不依赖 Iceberg 其他业务模块，仅依赖 relocated guava；被 core 及各引擎 集成模块用于访问引擎内部私有/隐藏字段。
 */
public class DynFields {

  private DynFields() {}

  /**
   * 未绑定字段视图，对 {@link java.lang.reflect.Field} 的便捷封装。
   *
   * <p>设计意图：把反射调用产生的受检异常统一包装为 RuntimeException，让调用方只需单个 Exception catch 块即可处理；与 {@link
   * BoundField}（已绑定）相对，调用时需显式传入 target。
   */
  public static class UnboundField<T> {
    private final Field field;
    private final String name;

    private UnboundField(Field field, String name) {
      this.field = field;
      this.name = name;
    }

    /**
     * 读取目标对象上本字段的值。
     *
     * <p>设计要点：通过反射 {@link Field#get(Object)} 取值，并把可能抛出的 IllegalAccessException 包装为 RuntimeException
     * 抛出，简化调用方异常处理。 泛型 T 由调用方指定，这里做未检查强转。
     *
     * @param target 字段所在的对象实例；静态字段可传 null
     * @return 字段值（已强转为 T）
     */
    @SuppressWarnings("unchecked")
    public T get(Object target) {
      try {
        return (T) field.get(target);
      } catch (IllegalAccessException e) {
        throw Throwables.propagate(e);
      }
    }

    /**
     * 设置目标对象上本字段的值。
     *
     * @param target 字段所在的对象实例；静态字段可传 null
     * @param value 要写入的值
     */
    public void set(Object target, T value) {
      try {
        field.set(target, value);
      } catch (IllegalAccessException e) {
        throw Throwables.propagate(e);
      }
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("class", field.getDeclaringClass().toString())
          .add("name", name)
          .add("type", field.getType())
          .toString();
    }

    /**
     * 将本字段绑定到指定实例，返回 BoundField（已绑定视图）。
     *
     * <p>设计要点：
     *
     * <ul>
     *   <li>静态字段不可绑定（AlwaysNull 例外，它本质是空对象）。
     *   <li>校验目标对象类型与字段声明类兼容（isAssignableFrom），避免运行期反射异常。
     * </ul>
     *
     * @param target 需要读写字段的对象实例
     * @return 与 target 绑定的 {@link BoundField}
     * @throws IllegalStateException 若字段是静态字段
     * @throws IllegalArgumentException 若 target 类型与字段声明类不兼容
     */
    public BoundField<T> bind(Object target) {
      Preconditions.checkState(
          !isStatic() || this == AlwaysNull.INSTANCE, "Cannot bind static field %s", name);
      Preconditions.checkArgument(
          field.getDeclaringClass().isAssignableFrom(target.getClass()),
          "Cannot bind field %s to instance of %s",
          name,
          target.getClass());

      return new BoundField<>(this, target);
    }

    /**
     * 将本字段转为静态字段视图 {@link StaticField}。
     *
     * @return 该字段对应的 {@link StaticField}
     * @throws IllegalStateException 若字段非静态
     */
    public StaticField<T> asStatic() {
      Preconditions.checkState(isStatic(), "Field %s is not static", name);
      return new StaticField<>(this);
    }

    /** 返回该字段是否为静态字段。 */
    public boolean isStatic() {
      return Modifier.isStatic(field.getModifiers());
    }

    /** 返回该字段是否为 AlwaysNull 空对象。 */
    public boolean isAlwaysNull() {
      return this == AlwaysNull.INSTANCE;
    }
  }

  /** 空对象模式的字段实现：get 永远返回 null，set 不做任何事，用于"找不到字段也无所谓"的场景。 */
  private static class AlwaysNull extends UnboundField<Void> {
    private static final AlwaysNull INSTANCE = new AlwaysNull();

    private AlwaysNull() {
      super(null, "AlwaysNull");
    }

    @Override
    public Void get(Object target) {
      return null;
    }

    @Override
    public void set(Object target, Void value) {}

    @Override
    public String toString() {
      return "Field(AlwaysNull)";
    }

    @Override
    public boolean isStatic() {
      return true;
    }

    @Override
    public boolean isAlwaysNull() {
      return true;
    }
  }

  /** 静态字段视图：绑定到类而非实例，读写无需传入 target。 */
  public static class StaticField<T> {
    private final UnboundField<T> field;

    private StaticField(UnboundField<T> field) {
      this.field = field;
    }

    /** 读取静态字段值。 */
    public T get() {
      return field.get(null);
    }

    /** 写入静态字段值。 */
    public void set(T value) {
      field.set(null, value);
    }
  }

  /** 已绑定字段视图：持有目标实例，读写时无需再传 target。 */
  public static class BoundField<T> {
    private final UnboundField<T> field;
    private final Object target;

    private BoundField(UnboundField<T> field, Object target) {
      this.field = field;
      this.target = target;
    }

    /** 读取已绑定实例上的字段值。 */
    public T get() {
      return field.get(target);
    }

    /** 写入已绑定实例上的字段值。 */
    public void set(T value) {
      field.set(target, value);
    }
  }

  /** 创建字段查找构建器。 */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 字段查找构建器：采用“候选逐个尝试，命中即止”的策略定位目标字段。
   *
   * <p>设计意图：调用方按优先级依次注册多个候选（类名+字段名），Builder 仅在尚未命中时 才尝试下一个候选，从而实现跨版本/跨实现的兼容查找。所有失败候选会被收集到
   * candidates 集合，最终在 build 时拼入异常信息，便于排查“为什么没找到字段”。
   */
  public static class Builder {
    private ClassLoader loader = Thread.currentThread().getContextClassLoader();
    private UnboundField<?> field = null;
    private final Set<String> candidates = Sets.newHashSet();
    private boolean defaultAlwaysNull = false;

    private Builder() {}

    /**
     * 设置按类名查找类时使用的 {@link ClassLoader}。
     *
     * <p>未设置时使用当前线程的 ClassLoader。
     *
     * @param newLoader 类加载器
     * @return this，便于链式调用
     */
    public Builder loader(ClassLoader newLoader) {
      this.loader = newLoader;
      return this;
    }

    /**
     * 指示构建器在找不到任何实现时返回 AlwaysNull 空对象。
     *
     * @return this，便于链式调用
     */
    public Builder defaultAlwaysNull() {
      this.defaultAlwaysNull = true;
      return this;
    }

    /**
     * 注册一个候选实现：先按类名加载类，再查找其公开字段。
     *
     * <p>逻辑：若已命中字段则直接返回（短路）；否则用 ClassLoader 反射加载类并委托 {@link #impl(Class, String)}
     * 查找字段；类不存在则把候选加入失败集合并继续。
     *
     * @param className 候选类全限定名
     * @param fieldName 候选字段名
     * @return this，便于链式调用
     */
    public Builder impl(String className, String fieldName) {
      // don't do any work if an implementation has been found
      if (field != null) {
        return this;
      }

      try {
        Class<?> targetClass = Class.forName(className, true, loader);
        impl(targetClass, fieldName);
      } catch (ClassNotFoundException e) {
        // not the right implementation
        candidates.add(className + "." + fieldName);
      }
      return this;
    }

    /**
     * 注册一个候选实现：在给定类上查找公开字段。
     *
     * @param targetClass 目标类实例
     * @param fieldName 字段名
     * @return this，便于链式调用
     * @see java.lang.Class#forName(String)
     * @see java.lang.Class#getField(String)
     */
    public Builder impl(Class<?> targetClass, String fieldName) {
      // don't do any work if an implementation has been found
      if (field != null || targetClass == null) {
        return this;
      }

      try {
        this.field = new UnboundField<>(targetClass.getField(fieldName), fieldName);
      } catch (NoSuchFieldException e) {
        // not the right implementation
        candidates.add(targetClass.getName() + "." + fieldName);
      }
      return this;
    }

    /**
     * 注册一个"隐藏实现"候选：先按类名加载类，再查找其非公开字段。
     *
     * @param className 类全限定名
     * @param fieldName 字段名
     * @return this，便于链式调用
     * @see java.lang.Class#forName(String)
     * @see java.lang.Class#getField(String)
     */
    public Builder hiddenImpl(String className, String fieldName) {
      // don't do any work if an implementation has been found
      if (field != null) {
        return this;
      }

      try {
        Class<?> targetClass = Class.forName(className, true, loader);
        hiddenImpl(targetClass, fieldName);
      } catch (ClassNotFoundException e) {
        // not the right implementation
        candidates.add(className + "." + fieldName);
      }
      return this;
    }

    /**
     * 注册一个“隐藏实现”候选：访问类的非公开（private/protected）字段。
     *
     * <p>逻辑：通过 {@link Class#getDeclaredField(String)} 获取声明字段（含非公开）， 再用 {@link
     * AccessController#doPrivileged} 包裹 {@code setAccessible(true)} 以绕过
     * 访问检查。失败（SecurityException/NoSuchFieldException）时记录候选并继续。
     *
     * <p>设计意图：引擎内部许多字段是非公开的，Iceberg 需要读取它们以实现集成， 故提供 hidden 系列方法在受控前提下访问隐藏字段。
     *
     * @param targetClass 目标类实例
     * @param fieldName 字段名
     * @return this，便于链式调用
     */
    public Builder hiddenImpl(Class<?> targetClass, String fieldName) {
      // don't do any work if an implementation has been found
      if (field != null || targetClass == null) {
        return this;
      }

      try {
        Field hidden = targetClass.getDeclaredField(fieldName);
        AccessController.doPrivileged(new MakeFieldAccessible(hidden));
        this.field = new UnboundField(hidden, fieldName);
      } catch (SecurityException | NoSuchFieldException e) {
        // unusable
        candidates.add(targetClass.getName() + "." + fieldName);
      }
      return this;
    }

    /**
     * 构建并返回命中的字段（受检版本）。
     *
     * <p>逻辑：若已命中候选则返回该 UnboundField；否则若设置了 defaultAlwaysNull 则返回 AlwaysNull 空对象；否则抛出
     * NoSuchFieldException，异常信息列出所有失败候选便于排查。
     *
     * @param <T> 字段值的 Java 类型
     * @return 命中的 {@link UnboundField}
     * @throws NoSuchFieldException 若无任何候选命中且未设置 defaultAlwaysNull
     */
    @SuppressWarnings("unchecked")
    public <T> UnboundField<T> buildChecked() throws NoSuchFieldException {
      if (field != null) {
        return (UnboundField<T>) field;
      } else if (defaultAlwaysNull) {
        return (UnboundField<T>) AlwaysNull.INSTANCE;
      } else {
        throw new NoSuchFieldException(
            "Cannot find field from candidates: " + Joiner.on(", ").join(candidates));
      }
    }

    /**
     * 构建并返回绑定到指定 target 的已命名字段（受检版本）。
     *
     * @param target 需要读写字段的对象实例
     * @param <T> 字段值的 Java 类型
     * @return 绑定到 target 的 {@link BoundField}
     * @throws IllegalStateException 若字段为静态
     * @throws IllegalArgumentException 若 target 类型与字段声明类不兼容
     * @throws NoSuchFieldException 若无候选命中
     */
    public <T> BoundField<T> buildChecked(Object target) throws NoSuchFieldException {
      return this.<T>buildChecked().bind(target);
    }

    /**
     * 构建并返回命中的字段（非受检版本）。
     *
     * <p>与 {@link #buildChecked()} 逻辑一致，区别仅在于未命中且未设置 defaultAlwaysNull 时 抛出 RuntimeException 而非
     * NoSuchFieldException，便于在不关心受检异常的调用链中使用。
     *
     * @param <T> 字段值的 Java 类型
     * @return 命中的 {@link UnboundField}
     * @throws RuntimeException 若无任何候选命中且未设置 defaultAlwaysNull
     */
    @SuppressWarnings("unchecked")
    public <T> UnboundField<T> build() {
      if (field != null) {
        return (UnboundField<T>) field;
      } else if (defaultAlwaysNull) {
        return (UnboundField<T>) AlwaysNull.INSTANCE;
      } else {
        throw new RuntimeException(
            "Cannot find field from candidates: " + Joiner.on(", ").join(candidates));
      }
    }

    /**
     * 构建并返回绑定到指定 target 的已命名字段（非受检版本）。
     *
     * @param target 需要读写字段的对象实例
     * @param <T> 字段值的 Java 类型
     * @return 绑定到 target 的 {@link BoundField}
     * @throws IllegalStateException 若字段为静态
     * @throws IllegalArgumentException 若 target 类型与字段声明类不兼容
     * @throws RuntimeException 若无候选命中
     */
    public <T> BoundField<T> build(Object target) {
      return this.<T>build().bind(target);
    }

    /**
     * 构建并返回命中的静态字段（受检版本）。
     *
     * @param <T> 字段值的 Java 类型
     * @return 该字段对应的 {@link StaticField}
     * @throws IllegalStateException 若字段非静态
     * @throws NoSuchFieldException 若无候选命中
     */
    public <T> StaticField<T> buildStaticChecked() throws NoSuchFieldException {
      return this.<T>buildChecked().asStatic();
    }

    /**
     * 构建并返回命中的静态字段（非受检版本）。
     *
     * @param <T> 字段值的 Java 类型
     * @return 该字段对应的 {@link StaticField}
     * @throws IllegalStateException 若字段非静态
     * @throws RuntimeException 若无候选命中
     */
    public <T> StaticField<T> buildStatic() {
      return this.<T>build().asStatic();
    }
  }

  /** 特权动作：将隐藏字段设为可访问，绕过 Java 访问检查。 */
  private static class MakeFieldAccessible implements PrivilegedAction<Void> {
    private Field hidden;

    MakeFieldAccessible(Field hidden) {
      this.hidden = hidden;
    }

    @Override
    public Void run() {
      hidden.setAccessible(true);
      return null;
    }
  }
}
