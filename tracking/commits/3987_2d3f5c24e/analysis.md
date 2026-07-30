# 提交 3987：Core: Handle ExceptionInInitializerError & NoClassDefFoundError invoke-time failures in DynMethods callers (#16611)

## 提交信息

- **序号**：3987 / 4088
- **哈希**：2d3f5c24e0697b361fe95362d8ee13b915e98048
- **短哈希**：2d3f5c24e
- **日期**：2026-07-06 11:21:58 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Handle ExceptionInInitializerError & NoClassDefFoundError invoke-time failures in DynMethods callers (#16611)
- **PR/Issue**：#16611

## 总体目的

本提交修复了 `InternalData` 和 `FormatModelRegistry` 中动态注册可选格式模块时的异常处理不完整问题。此前，这两个类通过 `DynMethods` 动态调用可选模块（如 `iceberg-parquet`、`iceberg-orc`）的 `register` 方法，但只捕获了 `NoSuchMethodException`（类不存在的情况）。

然而，当可选模块在 classpath 上但其传递依赖缺失时，调用 `register` 方法会抛出 `NoClassDefFoundError`（方法体引用了缺失的类）或 `ExceptionInInitializerError`（触发了失败的静态初始化器）。这两个 `Error` 不是 `Exception`，不会被 `catch (NoSuchMethodException e)` 捕获，导致应用崩溃而非优雅降级。

本提交扩展了异常处理，同时捕获 `NoSuchMethodException`、`NoClassDefFoundError` 和 `ExceptionInInitializerError`，确保可选模块缺失任何依赖时都能优雅降级。

## 如何达成设计目的

1. 提取公共的 `register(String classToRegister)` 方法，封装 DynMethods 调用和异常处理。
2. 在 catch 子句中同时捕获三种失败模式。
3. 改进日志输出：使用 `cause.toString()` 输出根本原因（`getCause()` 不为 null 时使用 cause），提供更有用的诊断信息。

## 修改详情

### `core/src/main/java/org/apache/iceberg/InternalData.java` (+18/-11 lines)

**修改目的**：扩展异常处理并提取 register 方法。

**工作逻辑**：
```java
private static void register(String classToRegister) {
  try {
    DynMethods.builder("register").impl(classToRegister).buildStaticChecked().invoke();
  } catch (NoSuchMethodException | NoClassDefFoundError | ExceptionInInitializerError e) {
    Throwable cause = e.getCause() != null ? e.getCause() : e;
    LOG.info("Cannot register {} for metadata files: {}", classToRegister, cause.toString());
  }
}
```
原内联代码仅 catch `NoSuchMethodException`，现在 catch 三种类型。Parquet 注册从内联 try-catch 改为调用 `register("org.apache.iceberg.InternalParquet")`。

### `core/src/main/java/org/apache/iceberg/formats/FormatModelRegistry.java` (+24/-10 lines)

**修改目的**：同样的异常处理改进。

**工作逻辑**：提取 `register(String classToRegister)` 方法，catch 三种异常类型，使用 cause.toString() 输出。Javadoc 说明三种失败模式：`NoSuchMethodException`（类不存在）、`NoClassDefFoundError`（传递依赖缺失）、`ExceptionInInitializerError`（静态初始化器失败）。

### `core/src/test/java/org/apache/iceberg/TestInternalData.java` (+36/-0 lines)

**修改目的**：验证三种失败模式的容错性。

**工作逻辑**：通过反射调用 private `register` 方法，新增三个测试：
- `registerToleratesMissingClass`：注册不存在的类，不抛异常。
- `registerToleratesNoClassDefFoundErrorOnInvoke`：注册一个 `register()` 方法抛出 `NoClassDefFoundError` 的内部类，不抛异常。
- `registerToleratesExceptionInInitializerErrorOnInvoke`：注册一个 `register()` 方法抛出 `ExceptionInInitializerError` 的内部类，不抛异常。

### `core/src/test/java/org/apache/iceberg/formats/TestFormatModelRegistry.java` (+38/-0 lines)

**修改目的**：同样的测试覆盖。

**工作逻辑**：与 InternalData 测试类似，验证三种失败模式的容错性，并断言 `FormatModelRegistry.models()` 为空（注册失败不影响注册表状态）。

## 总结

本提交修复了一个重要的健壮性问题：当可选格式模块（如 Parquet、ORC）在 classpath 上但其传递依赖缺失时，应用会因未捕获的 `Error` 而崩溃。修复方案简洁——扩展 catch 子句覆盖三种失败模式——但影响显著，确保了 Iceberg 在部分依赖缺失时能优雅降级而非崩溃。改进的日志输出也有助于诊断 classpath 问题。
