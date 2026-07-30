# 提交 3268：Build: Enable JavaUtilDate ErrorProne rule (#15346)

## 提交信息

- **序号**：3268 / 4088
- **哈希**：e9aa1c6c7aa508e6ec568717d85164a5e9ff6518
- **短哈希**：e9aa1c6c7
- **日期**：2026-02-17
- **作者**：Yuya Ebihara
- **提交说明**：Build: Enable JavaUtilDate ErrorProne rule (#15346)
- **PR/Issue**：#15346

## 总体目的

本提交在项目的 ErrorProne 静态检查配置中启用 `JavaUtilDate` 规则并设为 `ERROR` 级别。`JavaUtilDate` 是 ErrorProne 内置的一条检查规则，用于阻止使用 `java.util.Date`、`java.util.Calendar`、`java.text.SimpleDateFormat` 等 Java 早期遗留的日期时间 API——这些类存在众所周知的缺陷：可变（`Date` 内部 long 可被 `setTime` 改写）、时区处理混乱（`Date.toString()` 用默认 JVM 时区）、月份从 0 开始、`SimpleDateFormat` 非线程安全等。在现代 Java 代码库中，正确做法是使用 `java.time`（JSR-310）包下的 `Instant`、`LocalDateTime`、`ZonedDateTime`、`Duration` 等不可变类型。

启用这条规则的背景是项目里已存在 `@SuppressWarnings("JavaUtilDate")` 的使用（如 `gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java` 中因 GCP SDK 直接消费 `java.util.Date` 而不得不保留并显式抑制），说明代码库此前已经在为引入这条规则做准备：在无法避免使用遗留 API 的边界处逐个加上抑制注解，而本次提交把规则真正打开为 ERROR，从而在 CI 构建时阻止任何新的 `java.util.Date` 用法被引入。这是 Iceberg 代码质量基线（baseline）的常规收紧操作，`baseline.gradle` 中此前已启用 `InconsistentCapitalization`、`InconsistentHashCode`、`IntLongMath`、`JdkObsolete`、`LambdaMethodReference` 等一系列规则，本次只是按字母序在 `IntLongMath` 与 `JdkObsolete` 之间新增 `JavaUtilDate` 一行。

## 如何达成设计目的

整体思路很简单：在 `baseline.gradle` 的 ErrorProne 编译器参数列表中，按字母顺序在 `-Xep:IntLongMath:ERROR` 之后、`-Xep:JdkObsolete:ERROR` 之前，新增一行 `-Xep:JavaUtilDate:ERROR`。所有子项目（`subprojects` 块）的 Java 编译都会因此把 `java.util.Date` 等用法视为编译错误。需要保留用法的边界处（如与第三方 SDK 交互的 GCP 模块）已通过 `@SuppressWarnings("JavaUtilDate")` 显式豁免，所以启用规则不会破坏构建。

## 修改详情

### `baseline.gradle` (+1/-0 lines)

**修改目的**：把 `JavaUtilDate` 检查提升为编译期 ERROR。

**工作逻辑**：在 `subprojects` 块的 ErrorProne 编译器参数列表（`compilerArgs`）中，新增 `'-Xep:JavaUtilDate:ERROR'`。该参数在 `'-Xep:IntLongMath:ERROR'` 与 `'-Xep:JdkObsolete:ERROR'` 之间，符合 ErrorProne 规则名字母序排列的现有约定。一旦启用，任何子项目源码中未经 `@SuppressWarnings("JavaUtilDate")` 豁免的 `java.util.Date`、`java.util.Calendar`、`java.text.SimpleDateFormat` 用法都会使编译失败，从而在 CI 阶段就把这类遗留 API 的新引入拦截下来。

## 总结

本提交通过在 `baseline.gradle` 中启用 `JavaUtilDate:ERROR`，把"禁止使用遗留日期时间 API"纳入项目编译期质量门禁，与既有 `JdkObsolete` 等规则一脉相承。由于需要保留用法的边界处已提前加上 `@SuppressWarnings` 豁免，启用规则是纯增量收紧，不破坏现有构建，但能有效防止 `java.util.Date` 等遗留类型再次进入代码库，推动项目继续向 `java.time` 迁移。
