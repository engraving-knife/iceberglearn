# 提交 2865：core: Use EncryptionUtil's classloader (#14452)

## 提交信息

- **序号**：2865 / 4088
- **哈希**：0a92386de954632f79f7be04368f64bb80d1f67e
- **短哈希**：0a92386de
- **日期**：2025-11-11 17:45:50 -0800
- **作者**：hsiang-c
- **提交说明**：core: Use EncryptionUtil's classloader (#14452)
- **PR/Issue**：#14452

## 总体目的

这个提交修复了 `EncryptionUtil.createKmsClient` 方法中的类加载器问题。该方法使用 `DynConstructors` 动态加载 KMS（Key Management Service）客户端实现类。此前代码没有显式指定类加载器，`DynConstructors` 默认会使用当前线程的上下文类加载器（`Thread.currentThread().getContextClassLoader()`）来查找类。

在某些部署环境中（如 Spark、Flink 等分布式计算框架），线程上下文类加载器可能与加载 Iceberg 核心库的类加载器不同。这会导致 `DynConstructors` 无法找到由 Iceberg 类加载器加载的 KMS 实现类，从而抛出类未找到异常。

该提交通过显式使用 `EncryptionUtil` 自身的类加载器来加载 KMS 实现类，确保在多类加载器环境下能正确找到目标类。

## 如何达成设计目的

通过在 `DynConstructors.builder()` 调用中显式指定 `EncryptionUtil.class.getClassLoader()` 作为类加载器，使动态加载使用与 EncryptionUtil 相同的类加载器。同时添加了单元测试验证类加载器行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (+5/-1 lines)

**修改目的**：在动态构造 KMS 客户端时使用 EncryptionUtil 的类加载器。

**工作逻辑**：在 `createKmsClient` 方法中，将原来的 `DynConstructors.builder(KeyManagementClient.class).impl(kmsImpl).buildChecked()` 修改为 `DynConstructors.builder(KeyManagementClient.class).loader(EncryptionUtil.class.getClassLoader()).impl(kmsImpl).buildChecked()`。通过 `.loader()` 方法显式指定使用 `EncryptionUtil` 类的类加载器来查找 `kmsImpl` 指定的实现类，而非依赖线程上下文类加载器。

### `core/src/test/java/org/apache/iceberg/encryption/TestEncryptionUtil.java` (+86/-0 lines)

**修改目的**：添加单元测试验证类加载器行为。

**工作逻辑**：新建测试文件，包含 `testClassLoader` 测试方法和自定义类加载器内部类：

1. **UnitTestCustomClassLoader**：自定义类加载器，继承 `ClassLoader`，重写 `findClass` 方法从 classpath 资源中直接加载类的字节码并定义类。这样可以在与系统类加载器隔离的环境中加载类。

2. **testClassLoader 测试**：
   - 使用自定义类加载器加载 `EncryptionUtil` 类和 `UnitestKMS` 类。
   - 通过反射调用 `EncryptionUtil.createKmsClient` 方法，传入 KMS 实现类名。
   - 验证返回的 KMS 客户端对象的类加载器是自定义类加载器，而非线程上下文类加载器。
   - 这证明了 `createKmsClient` 使用了 `EncryptionUtil` 自身的类加载器（而非线程上下文类加载器）来加载 KMS 实现类。

注意：测试中提到的 `UnitestKMS` 类虽然在测试中被引用但在 diff 中未直接展示（可能已在其他地方定义或作为测试依赖存在）。

## 总结

这个提交修复了 `EncryptionUtil.createKmsClient` 方法在多类加载器环境下无法找到 KMS 实现类的问题。通过显式使用 `EncryptionUtil` 类的类加载器替代默认的线程上下文类加载器，确保在 Spark、Flink 等分布式计算框架中能正确加载 KMS 客户端实现。修改涉及 1 行核心代码变更和新增的 86 行单元测试，测试通过自定义类加载器验证了类加载行为的正确性。
