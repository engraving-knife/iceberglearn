# 提交 1919：Core: Fallback to thread classloader when loading classes (#12613)

## 提交信息

- **序号**：1919 / 4088
- **哈希**：03ff41c189c7420992be0e4a4ddc63f005e2e0d5
- **短哈希**：03ff41c18
- **日期**：2025-03-25 06:30:49 +0100
- **作者**：Bryan Keller
- **提交说明**：Core: Fallback to thread classloader when loading classes (#12613)
- **PR/Issue**：#12613

## 总体目的

`DynConstructors`（位于 `iceberg-common` 模块）是 Iceberg 用来按类名动态加载并构造实现类的工具，广泛用于按需加载 Catalog、FileIO 等实现。它通过 builder 指定的 `ClassLoader loader` 调用 `Class.forName(className, true, loader)` 来加载类。

问题在于：在某些部署环境下，传入的 `loader`（例如平台类加载器或某个特定插件类加载器）无法找到目标实现类，而该实现类实际由当前线程的上下文类加载器（`Thread.currentThread().getContextClassLoader()`）可见。此时直接 `Class.forName` 抛 `ClassNotFoundException`，导致动态加载失败，即便实现类在 thread context classloader 中是可用的。

本提交为 `DynConstructors` 增加 fallback：当指定 loader 找不到类时，若该 loader 与当前线程上下文类加载器不同，则回退用线程上下文类加载器再尝试加载一次。这提升了在复杂类加载器层级（如 Spark/Flink 引擎插件、OSGi 等）下的兼容性。

## 如何达成设计目的

1. 抽取私有方法 `classForName(String className)`：先尝试 `Class.forName(className, true, loader)`；若抛 `ClassNotFoundException` 且 `loader != Thread.currentThread().getContextClassLoader()`，则用线程上下文类加载器再试一次；否则原样抛出。
2. 把 builder 内两处 `Class.forName(className, true, loader)` 调用（普通 `impl` 与 `hiddenImpl`）改为调用 `classForName(className)`。
3. 新增测试 `testLoaderFallback`：用一个看不到目标类的平台类加载器（`ClassLoader.getPlatformClassLoader()`）作为 loader，构造 `MyClass`（由测试类加载器加载），验证 fallback 能成功加载并实例化。
4. 顺带：`iceberg-common` 项目在 `build.gradle` 中加 `test { useJUnitPlatform() }` 以支持 JUnit 5（新测试用 JUnit 5 注解）；并把两处 `ClassCastException` 消息断言从 `hasMessage(...)` 改为 `hasMessageStartingWith(...)`，因为不同 JDK 版本的 ClassCastException 消息格式略有差异（新版 JDK 会带 "class " 前缀），放宽断言避免在不同 JDK 上不稳定。

## 修改详情

### `build.gradle` (修改, +3 lines)

**修改目的**：让 `iceberg-common` 模块测试使用 JUnit Platform（JUnit 5）。

**工作逻辑**：

```groovy
project(':iceberg-common') {
  test {
    useJUnitPlatform()
  }
  dependencies { ... }
}
```

### `common/src/main/java/org/apache/iceberg/common/DynConstructors.java` (修改, +14/-2 lines)

**修改目的**：加载类时回退到线程上下文类加载器。

**工作逻辑**：

新增私有方法：

```java
private Class<?> classForName(String className) throws ClassNotFoundException {
  try {
    return Class.forName(className, true, loader);
  } catch (ClassNotFoundException e) {
    if (loader != Thread.currentThread().getContextClassLoader()) {
      return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
    } else {
      throw e;
    }
  }
}
```

两处 `impl` / `hiddenImpl` 中 `Class<?> targetClass = Class.forName(className, true, loader);` 改为 `Class<?> targetClass = classForName(className);`。逻辑：先用 builder 指定的 loader，失败且该 loader 与线程上下文类加载器不同时回退，避免无限递归（若二者相同则不再重试）。

### `common/src/test/java/org/apache/iceberg/common/TestDynConstructors.java` (修改, +14/-4 lines)

**修改目的**：验证 fallback 行为并放宽 JDK 相关的消息断言。

**工作逻辑**：

新增 `testLoaderFallback`：

```java
DynConstructors.Ctor<MyInterface> ctor =
    DynConstructors.builder(MyInterface.class)
        .loader(ClassLoader.getPlatformClassLoader())
        .impl(MyClass.class)
        .buildChecked();
assertThat(ctor.newInstance()).isInstanceOf(MyClass.class);
```

平台类加载器看不到测试类 `MyClass`（测试类由应用类加载器加载），第一次 `Class.forName` 会失败，触发 fallback 到线程上下文类加载器成功加载。

同时把两处 ClassCastException 断言改为 `hasMessageStartingWith("class org.apache.iceberg.common.TestDynConstructors$MyUnrelatedClass cannot be cast to class ...")`，兼容不同 JDK 版本消息格式（新 JDK 消息以 "class " 开头，旧 JDK 不带）。

## 总结

本提交为 `DynConstructors` 增加类加载 fallback：当 builder 指定的 loader 找不到目标类时，回退到当前线程上下文类加载器再尝试，提升在复杂类加载器层级下的动态加载兼容性。同时启用 `iceberg-common` 的 JUnit 5 测试支持，新增 fallback 测试，并放宽 ClassCastException 消息断言以兼容多版本 JDK。
