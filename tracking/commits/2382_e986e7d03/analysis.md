# 提交 2382：Core: Make SnapshotRefType public (#13621)

## 提交信息

- **序号**：2382 / 4088
- **哈希**：e986e7d039b874a786d3c543716b3fefdc79c361
- **短哈希**：e986e7d03
- **日期**：2025-07-22 12:35:58 +0200
- **作者**：Nikita Ryanov
- **提交说明**：Core: Make SnapshotRefType public (#13621)
- **PR/Issue**：#13621

## 总体目的

本提交将 `SnapshotRefType` 枚举从包级私有（package-private）改为 public（公开）。`SnapshotRefType` 是 Iceberg API 模块中定义快照引用类型的枚举，包含 `BRANCH`（分支）和 `TAG`（标签）两个值，用于标识快照引用的类型。

此前该枚举是包级私有的，限制了其在 api 模块外部 的使用。将其改为 public 后，其他模块和外部代码可以引用该枚举类型，这对于需要处理快照引用类型的工具和集成模块非常有用。例如，REST catalog 实现或其他需要序列化/反序列化快照引用类型的场景可能需要直接引用此枚举。

## 如何达成设计目的

设计思路非常简单，仅需将枚举声明的访问修饰符从默认（package-private）改为 public。由于 `SnapshotRefType` 位于 api 模块中，其 public 修饰符使该类型成为 Iceberg 公共 API 的一部分。

## 修改详情

### `api/src/main/java/org/apache/iceberg/SnapshotRefType.java` (+1/-1 lines)

**修改目的**：将 SnapshotRefType 枚举的访问级别从包级私有改为 public。

**工作逻辑**：将 `enum SnapshotRefType` 修改为 `public enum SnapshotRefType`。枚举的两个值 `BRANCH` 和 `TAG` 以及其内部方法保持不变。该枚举还包含从字符串解析类型的方法（通过 `fromString` 等），这些方法现在也通过 public 修饰符对外可见。

## 总结

本提交是一个简单的 API 可见性调整，将 `SnapshotRefType` 枚举从包级私有改为 public，仅修改 1 行代码。该修改使快照引用类型枚举成为 Iceberg 公共 API 的一部分，便于其他模块和外部代码引用，对于需要处理快照引用类型的集成场景具有重要意义。
