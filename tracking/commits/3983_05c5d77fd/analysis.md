# 提交 3983：Core: Extend org.apache.iceberg.hadoop.Configurable in HadoopConfigurable (#16736)

## 提交信息

- **序号**：3983 / 4088
- **哈希**：05c5d77fde4117a6b9474b4d1191a3bf4e6a6145
- **短哈希**：05c5d77fd
- **日期**：2026-07-06 08:44:57 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Core: Extend org.apache.iceberg.hadoop.Configurable in HadoopConfigurable (#16736)
- **PR/Issue**：#16736

## 总体目的

本提交将 `HadoopConfigurable` 接口从继承 Hadoop 的原始 `Configurable` 接口改为继承泛型版本 `Configurable<Configuration>`。Hadoop 提供了两个 `Configurable` 接口：非泛型的 `org.apache.hadoop.conf.Configurable`（返回 `Object`）和泛型的 `org.apache.hadoop.conf.Configurable<T>`（返回类型 T）。

此前 `HadoopConfigurable extends Configurable` 继承的是非泛型版本，导致 `getConf()` 返回 `Object`，调用方需要强制转换。改为 `Configurable<Configuration>` 后，`getConf()` 直接返回 `Configuration` 类型，类型安全更好。

同时，本提交为 `setConf` 和 `getConf` 提供了默认实现（抛出 `UnsupportedOperationException`），使接口与 Java 8+ 的默认方法特性一致，减少实现类的样板代码。

## 如何达成设计目的

1. 将 `extends Configurable` 改为 `extends Configurable<Configuration>`。
2. 新增 `setConf(Configuration conf)` 和 `getConf()` 的 default 方法，默认抛出 `UnsupportedOperationException`。
3. 更新 Spark 测试中的类型转换，从 `(Configurable)` 改为 `(HadoopConfigurable)`，利用类型安全的接口。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopConfigurable.java` (+22/-4 lines)

**修改目的**：改用泛型 Configurable 接口并添加默认方法。

**工作逻辑**：
```java
// 旧：public interface HadoopConfigurable extends Configurable
// 新：
public interface HadoopConfigurable extends Configurable<Configuration> {

  void serializeConfWith(
      Function<Configuration, SerializableSupplier<Configuration>> confSerializer);

  @Override
  default void setConf(Configuration conf) {
    throw new UnsupportedOperationException("setConf is not implemented");
  }

  default Configuration getConf() {
    throw new UnsupportedOperationException("getConf is not implemented");
  }
}
```

### Spark v3.5/v4.0/v4.0 的 `TestSparkCatalogHadoopOverrides.java` (+5/-5 lines each)

**修改目的**：使用类型安全的 HadoopConfigurable 接口。

**工作逻辑**：将 `((Configurable) table.io()).getConf()` 改为 `((HadoopConfigurable) table.io()).getConf()`，避免了不必要的强制转换（因为 `getConf()` 现在直接返回 `Configuration`）。涉及 4 处类型转换。

## 总结

本提交是一个类型安全改进，将 `HadoopConfigurable` 改为继承泛型 `Configurable<Configuration>`，使 `getConf()` 返回正确的类型，同时通过默认方法提供向后兼容。这是一个小但重要的 API 改进，减少了调用方的强制转换需求。
