# 提交 1996：Flink: backport move unlock from MemoryLock open to TestCase Before to Flink 1.19

## 提交信息

- **序号**：1996 / 4088
- **哈希**：8d1ec91a2e9c6de2b8fb1ccc5dc82ac36f4e1b71
- **短哈希**：8d1ec91a2
- **日期**：2025-04-15 09:57:12 +0200
- **作者**：GuoYu
- **提交说明**：Flink: backport move unlock from MemoryLock open to TestCase Before to Flink 1.19 (#12795)
- **PR/Issue**：#12795（backport #12793）

## 总体目的

本提交是将 PR #12793（提交 1995）的修改反向移植（backport）到 Flink 1.19 模块。Iceberg 项目同时维护多个 Flink 版本的模块（1.19、1.20 等），需要确保相同的 bug 修复在所有支持的版本中都生效。

修改内容与提交 1995 完全一致，仅作用目录不同：提交 1995 修改的是 `flink/v1.20/`，而本提交修改的是 `flink/v1.19/`。

## 如何达成设计目的

将相同的锁清理逻辑修改应用到 Flink 1.19 模块的对应文件：
1. 在 `OperatorTestBase.before()` 中添加显式解锁调用
2. 清空 `MemoryLockFactory.open()` 方法
3. 在 `TestTriggerManager.before()` 中添加 `super.before()` 调用

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (修改, +5/-2 lines)

**修改目的**：将锁清理逻辑从 MemoryLockFactory.open() 移到测试基类的 @BeforeEach 方法。

**工作逻辑**：与提交 1995 完全一致，参见该提交分析。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManager.java` (修改, +1/-0 lines)

**修改目的**：修复子类 @BeforeEach 未调用父类初始化方法的问题。

**工作逻辑**：与提交 1995 完全一致，参见该提交分析。

## 总结

本提交是提交 1995 的 Flink 1.19 反向移植，将相同的锁清理逻辑修复应用到 `flink/v1.19/` 模块，确保所有支持的 Flink 版本中都修复了测试间锁状态污染的问题。
