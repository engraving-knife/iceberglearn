# 提交 2451：Core: make BaseRowDelta public (#13643)

## 提交信息

- **序号**：2451 / 4088
- **哈希**：2876b959d2c01ef60dc4da97a8dcf1b2d405414c
- **短哈希**：2876b959d
- **日期**：2025-08-04 11:37:34 -0700
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: make BaseRowDelta public (#13643)
- **PR/Issue**：#13643

## 总体目的

该提交将 `BaseRowDelta` 类的访问修饰符从包级私有（package-private）改为公开（public），同时将其构造函数从包级私有改为受保护（protected）。

`BaseRowDelta` 是 Iceberg Core 模块中实现 `RowDelta` 接口的核心类，继承自 `MergingSnapshotProducer<RowDelta>`。它用于执行行级别的增量更新操作（同时添加数据文件和删除文件）。此前该类为包级私有，意味着只有在 `org.apache.iceberg` 包内的类才能访问和扩展它。

将此类公开的动机可能是为了让外部模块或集成方能够扩展 `BaseRowDelta` 的功能，例如自定义行级更新行为或添加额外的验证逻辑。这在一些高级使用场景中是必要的，例如引擎集成（Spark、Flink 等）可能需要继承此类来实现特定的写入语义。

## 如何达成设计目的

通过两个简单的访问修饰符修改：

1. **类级别**：将 `class BaseRowDelta` 改为 `public class BaseRowDelta`，使外部包可以访问该类。
2. **构造函数级别**：将构造函数 `BaseRowDelta(String tableName, TableOperations ops)` 改为 `protected BaseRowDelta(...)`，这样子类可以调用构造函数，但外部类不能直接实例化（必须通过工厂方法或子类）。

这种设计既开放了类的扩展能力，又保持了对实例化的一定控制——外部可以通过继承来扩展功能，但不能绕过框架直接创建实例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseRowDelta.java` (+2/-2 lines)

**修改目的**：将 `BaseRowDelta` 类公开，使其可被外部模块继承扩展。

**工作逻辑**：

第一处修改（类声明）：
```java
// 修改前
class BaseRowDelta extends MergingSnapshotProducer<RowDelta> implements RowDelta {
// 修改后
public class BaseRowDelta extends MergingSnapshotProducer<RowDelta> implements RowDelta {
```

第二处修改（构造函数）：
```java
// 修改前
BaseRowDelta(String tableName, TableOperations ops) {
// 修改后
protected BaseRowDelta(String tableName, TableOperations ops) {
```

构造函数改为 `protected` 而非 `public` 是一个精心考虑的设计：它允许子类调用 `super(tableName, ops)`，但阻止外部直接 `new BaseRowDelta(...)`，确保实例创建仍然通过 Table API 的标准路径进行。

## 总结

这是一个小但重要的 API 可见性修改。将 `BaseRowDelta` 从包级私有提升为 public，并使用 protected 构造函数，既允许了外部模块继承扩展该类的功能，又保持了对实例化的合理控制。这为引擎集成和高级使用场景提供了更大的灵活性，同时不破坏现有的封装设计。
