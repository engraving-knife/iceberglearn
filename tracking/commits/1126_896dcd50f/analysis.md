# 提交 1126：Flink: Fix compile warning (#11072)

## 提交信息

- **序号**：1126 / 4088
- **哈希**：896dcd50f959fb5e879229718b5a93161ffcd98e
- **短哈希**：896dcd50f
- **日期**：2024-09-04（Wed Sep 4 00:54:32 2024 +0530）
- **作者**：Ajanth Bhat <ajanthabhat@gmail.com>
- **提交说明**：Flink: Fix compile warning (#11072)
- **PR/Issue**：#11072

## 总体目的

Iceberg 在编译期启用了 error-prone 静态检查（见 baseline.gradle）。其中一些检查（如 `MissingOverride`）或 IDE/编译器本身会针对"内部 Builder 类缺少显式构造函数"发出告警：当一个非 static 的内部 Builder 类没有声明任何构造函数时，编译器会生成一个合成的默认构造函数，部分检查工具会提示应显式声明以避免歧义或与序列化/反射行为相关的问题。

`TableChange.Builder` 是 `TableChange` 内部的非静态嵌套类，且未声明构造函数，因此触发告警。本提交给 Flink v1.19 与 v1.20 两个版本下的 `TableChange.Builder` 各补一个显式的私有无参构造函数，消除告警，保持构建输出干净（这对持续启用更多 error-prone 检查的后续工作尤其重要）。

## 如何达成设计目的

在 `flink/v1.19` 和 `flink/v1.20` 的 `TableChange.java` 中，`Builder` 类的字段声明之后、第一个业务方法之前，插入一行 `private Builder() {}`。这是 Java 处理"内部类缺少显式构造函数"告警的标准做法。两个 Flink 版本下文件内容一致（同一份代码在两个版本目录维护），所以改动完全对称。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableChange.java`

**修改目的**：为内部 `Builder` 类补充显式私有构造函数，消除编译告警。

**工作逻辑**：在 `Builder` 类字段（`dataFileCount`、`deleteFileCount`、`...`、`commitCount`）声明之后插入：

```java
private Builder() {}
```

该构造函数与编译器自动合成的默认构造函数等价，但显式声明后可让 error-prone / IDE 检查不再提示。声明为 `private` 与 Builder 通常通过外部工厂方法创建的使用方式一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableChange.java`

**修改目的**：与 v1.19 同步，同样的修复。

**工作逻辑**：与 v1.19 完全相同的一行 `private Builder() {}`。

## 小结

- **成效**：消除 Flink v1.19/v1.20 维护算子 `TableChange.Builder` 的编译告警，配合后续（提交 1128）启用更多 error-prone 检查时保持构建干净。
- **影响范围**：仅两个 Flink 版本目录下的 `TableChange.java`，各加 2 行（含空行），无运行时行为变化——显式无参私有构造函数与编译器合成的默认构造函数语义一致。
- **回迁到 1.4.x 的注意事项**：这是消除告警的纯防御性改动，对 1.4.x 运行时无影响。1.4.x 若也构建 Flink v1.19/v1.20 模块且启用了相同检查，可选择性 cherry-pick 以保持构建干净；否则**无需回迁**。cherry-pick 无冲突风险。
