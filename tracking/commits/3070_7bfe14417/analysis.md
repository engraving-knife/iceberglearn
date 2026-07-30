# 提交 3070：Flink: fix VisibleForTesting import in ZkLockFactory (#14977)

## 提交信息

- **序号**：3070 / 4088
- **哈希**：7bfe144173cf45995b0dfc2639b20838cac6de4c
- **短哈希**：7bfe14417
- **日期**：2026-01-06
- **作者**：Huaxin Gao
- **提交说明**：Flink: fix VisibleForTesting import in ZkLockFactory (#14977)
- **PR/Issue**：#14977

## 总体目的

本提交修复 `ZkLockFactory` 类中 `VisibleForTesting` 注解的错误 import。`ZkLockFactory` 是 Flink 维护（maintenance）模块中基于 ZooKeeper 的锁工厂，用于在 Flink 维护任务中提供分布式锁能力。该类中使用了 `@VisibleForTesting` 注解来标注仅用于测试可见性的方法。

原代码错误地从 Curator 的 shaded 包导入该注解：`import org.apache.curator.shaded.com.google.common.annotations.VisibleForTesting;`。这是 Curator 依赖中 shaded（重定位）版本的 Guava 注解，属于内部实现细节，不应被外部代码直接引用。Iceberg 项目自身维护了一套 relocated Guava（`org.apache.iceberg.relocated.com.google.common.*`），正确做法应使用项目自身的 relocated 版本。使用 Curator shaded 包的类存在隐患：Curator 版本升级时 shaded 路径可能变化，导致编译中断；同时也违反了依赖隔离原则。

修复将 import 改为 `org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`，与项目其他代码保持一致。该修复覆盖 Flink v1.20、v2.0、v2.1 三个版本。

## 如何达成设计目的

改动极其简单，将三个 Flink 版本下 `ZkLockFactory.java` 中的 `VisibleForTesting` import 从 Curator shaded 包替换为 Iceberg relocated 包，每个文件仅改动两行（删一行旧 import、加一行新 import）。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (+1/-1 lines)

**修改目的**：修正 v1.20 版本的 `VisibleForTesting` import。

**工作逻辑**：
移除 `import org.apache.curator.shaded.com.google.common.annotations.VisibleForTesting;`，新增 `import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;`。其余代码不变。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (+1/-1 lines)

**修改目的**：修正 v2.0 版本的 `VisibleForTesting` import。

**工作逻辑**：与 v1.20 改动完全一致。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (+1/-1 lines)

**修改目的**：修正 v2.1 版本的 `VisibleForTesting` import。

**工作逻辑**：与 v1.20 改动完全一致。

## 总结

本提交将 `ZkLockFactory` 中错误引用的 Curator shaded 包 `VisibleForTesting` 注解改为项目自身的 relocated Guava 版本，消除依赖隐患并保持代码一致性。改动覆盖三个 Flink 版本，属于代码卫生（code hygiene）修复。
