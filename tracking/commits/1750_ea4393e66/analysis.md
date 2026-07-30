# 提交 1750：Parquet: Fix errorprone warning (#12324)

## 提交信息

- **序号**：1750 / 4088
- **哈希**：ea4393e66b4305863f1d1385d8424412c0d373c2
- **短哈希**：ea4393e66
- **日期**：2025-02-19 08:52:52 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Parquet: Fix errorprone warning (#12324)
- **PR/Issue**：#12324

## 总体目的

本提交旨在修复 Error Prone（Java 静态代码分析工具）产生的告警。Error Prone 是 Google 开发的编译期代码检查工具，Iceberg 项目通过 `baseline.gradle` 配置了大量的 Error Prone 检查规则。本提交一方面启用了两个新的 Error Prone 检查规则（将它们设为 ERROR 级别），另一方面修复了 `Parquet.java` 中触发这些新规则的代码。

具体来说，新启用的两个规则是：
- `UnnecessaryLambdaArgumentParentheses`：当 lambda 表达式的参数只有一个且无需类型声明时，括号是多余的，应省略。
- `UnusedTypeParameter`：当方法声明了泛型类型参数但从未使用时，应该移除该类型参数。

这些规则有助于保持代码简洁和一致性。

## 如何达成设计目的

提交分两步达成目的：
1. 在 `baseline.gradle` 中启用两个 Error Prone 规则，将 `UnnecessaryLambdaArgumentParentheses` 和 `UnusedTypeParameter` 设为 ERROR 级别。这确保后续所有代码都必须遵守这两个规则。
2. 修复 `Parquet.java` 中触发这两个规则的现有代码：移除未使用的泛型类型参数 `<T>`，并去掉单参数 lambda 表达式多余的括号。

## 修改详情

### `baseline.gradle`（修改, +2/-0 lines）

**修改目的**：启用两个新的 Error Prone 检查规则。

**工作逻辑**：在 Error Prone 规则列表中新增两行配置：
- `-Xep:UnnecessaryLambdaArgumentParentheses:ERROR`：将单参数 lambda 表达式多余括号设为编译错误。
- `-Xep:UnusedTypeParameter:ERROR`：将未使用的泛型类型参数设为编译错误。

这两个规则按字母顺序插入到现有的规则列表中。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java`（修改, +2/-2 lines）

**修改目的**：修复因新启用规则而触发的 Error Prone 告警。

**工作逻辑**：
1. `setBloomFilterConfig` 方法原本声明为 `private <T> void setBloomFilterConfig(...)`，但泛型参数 `<T>` 在方法体内从未使用。移除 `<T>` 后改为 `private void setBloomFilterConfig(...)`，消除 `UnusedTypeParameter` 告警。
2. 在 `readBuilder` 的 lambda 表达式中，原本写作 `(fileType) -> readerFuncWithSchema.apply(schema, fileType)`，由于单参数 lambda 无需括号，改为 `fileType -> readerFuncWithSchema.apply(schema, fileType)`，消除 `UnnecessaryLambdaArgumentParentheses` 告警。

## 小结

- **成效**：启用了两个新的 Error Prone 代码质量规则，并修复了 `Parquet.java` 中触发这些规则的代码，使代码更简洁、更符合规范。
- **影响范围**：涉及构建配置（`baseline.gradle`）和 Parquet 模块的一个核心类。新规则将影响整个项目的后续代码提交，所有子项目都会受到这两个规则约束。
- **回迁到 1.4.x 的注意事项**：回迁时需注意 1.4.x 分支的 `baseline.gradle` 中是否已有其他冲突规则配置。同时需要检查 1.4.x 分支中是否有其他代码会触发这两个新启用的规则——如果存在多处违规代码，启用 ERROR 级别会导致编译失败，因此需要同时修复所有违规点。建议谨慎评估 1.4.x 分支中受影响的代码量后再决定是否回迁。
