# 提交 2944：Refactor SnapshotAncestryValidator (#14650)

## 提交信息

- **序号**：2944 / 4088
- **哈希**：29a144adca05344de2a8aab05e2b3385c4053c8f
- **短哈希**：29a144adc
- **日期**：2025-12-02
- **作者**：aiborodin
- **提交说明**：Refactor SnapshotAncestryValidator
- **PR/Issue**：#14650

## 总体目的

这是一次 API 层面的重构，目标是清理 `SnapshotAncestryValidator` 接口的设计：移除它对 `java.util.function.Function<Iterable<Snapshot>, Boolean>` 的继承，并把方法从 `Boolean apply(...)` 改为 `boolean validate(...)`（返回原始类型 `boolean`）。

原设计的几个问题：

1. **继承 `Function` 带来不必要的耦合**：`SnapshotAncestryValidator` 本质是一个领域语义的校验器（校验快照祖先是否合法），却为了"能当函数用"而继承标准库的 `Function<Iterable<Snapshot>, Boolean>`。这把校验器与 `Function` 的契约绑死——任何持有 `Function` 类型引用的代码都可以把它当通用函数调用，语义上模糊了"这是一个校验器"的意图。继承 `Function` 还意味着它必须叫 `apply`、必须接受 `Iterable<Snapshot>` 并返回 `Boolean`，命名与签名都被锁死。

2. **返回装箱 `Boolean` 而非原始 `boolean`**：因为是 `Function<..., Boolean>`，返回类型必须是装箱的 `Boolean` 对象。这带来两个后果：
   - 调用方在 `SnapshotProducer` 中把它赋给 `boolean valid = ...apply(...)` 会触发自动拆箱，若实现意外返回 `null` 将抛 `NullPointerException`；
   - 每次调用都产生不必要的装箱/拆箱开销（虽然单次校验开销可忽略，但从 API 健壮性角度仍不理想）。

3. **方法名 `apply` 不表意**：`apply` 是通用函数式接口的标准方法名，对校验器来说，叫 `validate` 远比 `apply` 更能表达"我在做校验"的领域语义，提升代码可读性。

重构后：接口仍保留 `@FunctionalInterface`（可作为 lambda 目标，`NON_VALIDATING = baseSnapshots -> true` 仍可用），但不再继承 `Function`，方法改名 `validate` 并返回原始 `boolean`，彻底消除上述三点问题。这是一次有意的 API 演进（方法名与返回类型均变），所有实现/调用方同步更新。

## 如何达成设计目的

改动覆盖接口定义与全部 5 处实现/调用点：

- 接口本身：移除 `extends Function<...>`、删除 `import java.util.function.Function` 与 `@Override`、把 `Boolean apply(...)` 改为 `boolean validate(...)`；
- 调用方 `SnapshotProducer`：把 `apply(snapshotAncestry)` 改为 `validate(snapshotAncestry)`，由于现在返回原始 `boolean`，赋值给 `boolean valid` 不再涉及拆箱；
- 实现方：`TestSnapshotProducer`、Flink 三个版本（v1.20/v2.0/v2.1）的 `DynamicCommitter` 内部匿名类、Kafka Connect 的 `Coordinator` 内部匿名类，均把 `public Boolean apply(...)` 改为 `public boolean validate(...)`，移除/调整 `@Override`（`@Override` 在实现接口方法时仍可保留，diff 中保留是因为方法名变了，重新标注）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/SnapshotAncestryValidator.java` (+2/-4 lines)

**修改目的**：重构接口定义，移除 `Function` 继承，方法改名并返回原始 `boolean`。

**工作逻辑**：
关键改动：
- 删除 `import java.util.function.Function;`；
- 接口声明从 `public interface SnapshotAncestryValidator extends Function<Iterable<Snapshot>, Boolean>` 改为 `public interface SnapshotAncestryValidator`；
- 抽象方法从
  ```java
  @Override
  Boolean apply(Iterable<Snapshot> baseSnapshots);
  ```
  改为
  ```java
  boolean validate(Iterable<Snapshot> baseSnapshots);
  ```
  删除 `@Override`（不再覆盖 `Function` 的方法），返回类型由装箱 `Boolean` 改为原始 `boolean`，方法名从通用 `apply` 改为表意的 `validate`。

注意 `@FunctionalInterface` 注解保留，且静态常量 `SnapshotAncestryValidator NON_VALIDATING = baseSnapshots -> true;` 无需改动——lambda 仍能匹配新的 `validate` 方法签名（参数 `Iterable<Snapshot>`、返回 `boolean`，lambda 体 `true` 自动匹配原始 boolean 返回）。`errorMessage()` 默认方法也未受影响。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+1/-1 lines)

**修改目的**：更新接口唯一的运行时调用点。

**工作逻辑**：
在 `SnapshotProducer`（`abstract class SnapshotProducer<ThisT> implements SnapshotUpdate<ThisT>`，约 354 行）中，校验快照祖先的逻辑由：
```java
boolean valid = snapshotAncestryValidator.apply(snapshotAncestry);
```
改为
```java
boolean valid = snapshotAncestryValidator.validate(snapshotAncestry);
```
后续 `ValidationException.check(valid, "Snapshot ancestry validation failed: %s", ...)` 不变。改造后赋值不再有拆箱——`validate` 直接返回原始 `boolean`，消除了实现返回 `null` 时抛 NPE 的风险。

### `core/src/test/java/org/apache/iceberg/TestSnapshotProducer.java` (+1/-1 lines)

**修改目的**：更新测试中匿名 `SnapshotAncestryValidator` 实现的方法签名。

**工作逻辑**：
测试构造一个始终返回 `false` 的校验器以触发校验失败路径：
```java
SnapshotAncestryValidator validator =
    new SnapshotAncestryValidator() {
      @Override
      public boolean validate(Iterable<Snapshot> baseSnapshots) {
        return false;
      }
      ...
```
方法名从 `apply` 改为 `validate`，返回类型从 `Boolean` 改为 `boolean`。`@Override` 保留（实现接口方法仍可标注）。

### `flink/v1.20/flink/.../DynamicCommitter.java`、`flink/v2.0/flink/.../DynamicCommitter.java`、`flink/v2.1/flink/.../DynamicCommitter.java`（各 +1/-1 lines）

**修改目的**：更新 Flink 三个版本中 `DynamicCommitter` 内部匿名 `SnapshotAncestryValidator` 实现的方法签名。

**工作逻辑**：
三处改动完全相同（Flink v1.20、v2.0、v2.1 的 `DynamicCommitter.java` 第 369 行附近的匿名内部类）：
```java
@Override
public boolean validate(Iterable<Snapshot> baseSnapshots) {
  long maxCommittedCheckpointId =
      getMaxCommittedCheckpointId(baseSnapshots, flinkJobId, flinkOperatorId);
  if (maxCommittedCheckpointId >= stagedCheckpointId) {
    ...
```
原 `public Boolean apply(...)` 改为 `public boolean validate(...)`。这个匿名校验器用于 Flink 动态 sink 提交时校验：基于快照祖先计算已提交的最大 checkpoint id，判断当前 staged checkpoint 是否已被提交过，从而避免重复提交。返回原始 `boolean` 后，`expectedOffsets.equals(...)` 等比较结果直接作为 `boolean` 返回，无需装箱。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+1/-1 lines)

**修改目的**：更新 Kafka Connect 模块 `Coordinator` 内部匿名实现的方法签名。

**工作逻辑**：
在 `Coordinator` 类的某个内部匿名 `SnapshotAncestryValidator`（约 308 行）中：
```java
@Override
public boolean validate(Iterable<Snapshot> baseSnapshots) {
  lastCommittedOffsets = lastCommittedOffsets(baseSnapshots);
  return expectedOffsets.equals(lastCommittedOffsets);
}
```
原 `public Boolean apply(...)` 改为 `public boolean validate(...)`。该实现用快照祖先推导出"已提交的 offset 映射"，与期望 offset 比较，判断本次提交是否安全。`Map.equals` 返回 `boolean`，现在直接返回无需装箱。

## 总结

本次提交重构了 `SnapshotAncestryValidator` API：移除对 `java.util.function.Function` 的继承、方法从 `Boolean apply` 改为 `boolean validate`。这使接口语义更清晰（`validate` 比 `apply` 更表意）、消除装箱/拆箱与潜在 `null` 返回的 NPE 风险、解除与标准 `Function` 契约的不必要耦合。改动同步覆盖了 `api`、`core`、Flink 三个版本、Kafka Connect 共 7 处文件，是一次自洽的 API 演进，保留了 `@FunctionalInterface` 与 lambda 友好性，对下游实现者是源码级 break，但行为不变。
