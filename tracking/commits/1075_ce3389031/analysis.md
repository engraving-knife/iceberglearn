# 提交 1075：Enable UnusedMethod error-prone check (#10968)

## 提交信息

- **序号**：1075 / 4088
- **哈希**：ce33890314fbab3cf31371a8c5087f5e34f51a81
- **短哈希**：ce3389031
- **日期**：2024-08-20 19:21:09 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Enable UnusedMethod error-prone check (#10968)
- **PR/Issue**：#10968

## 总体目的

随着代码库长期演进，类中常常会留下"从未被任何地方调用"的方法：可能是早期 API 设计遗留、重构后被遗忘的旧实现、或者为了"将来可能用到"而保留的扩展点。这些未使用的方法带来多重负面影响：

1. **维护负担**：维护者需要花精力理解这些方法的存在意义，无法判断能否安全删除。
2. **API 表面膨胀**：对公开类而言，未使用方法会成为事实上的公共 API 一部分，下游可能开始依赖它，使后续清理变得困难。
3. **掩盖死代码**：未被调用的方法可能引用了其它也已废弃的类型/字段，形成"死代码链条"，干扰重构和审计。
4. **测试盲区**：未被调用意味着通常也未被测试，其行为正确性无法保证。

error-prone 是 Google 开发的 Java 编译期静态分析工具，Iceberg 通过 `baseline.gradle` 在每个子项目编译时启用其规则集。本提交的目的是开启 error-prone 的 `UnusedMethod` 检查并设为 ERROR 级别，使任何新增/既存的未被调用方法在编译期即报错，强制开发者要么删除该方法、要么用 `@SuppressWarnings("UnusedMethod")` 显式标注保留意图，从源头杜绝死代码积累。

## 如何达成设计目的

实现方式非常直接：在 `baseline.gradle` 的 error-prone 规则列表中追加一行 `'-Xep:UnusedMethod:ERROR'`，将该检查启用并设为 ERROR 级别。由于本提交之前已经做过代码库扫描和清理（PR 描述中暗示此前已无 UnusedMethod 违规，或已逐个处理），因此启用 ERROR 级别不会导致现有编译失败，只对未来新增违规生效。

启用后：

- 任何未被引用的实例/静态方法都会让编译失败；
- 公共 API 中确实需要保留但暂未被内部使用的方法（例如为外部集成方提供的扩展点）可用 `@SuppressWarnings("UnusedMethod")` 显式保留；
- 这与 Iceberg 既有的 `UnusedVariable`、`MissingOverride` 等规则一起，构成更完整的"代码卫生"防线。

## 修改详情

### `baseline.gradle`

**修改目的**：在 error-prone 检查规则列表中新增 `UnusedMethod:ERROR`，开启编译期对未使用方法的强制检查。

**工作逻辑**：

```diff
           '-Xep:TypeParameterUnusedInFormals:OFF',
           // Palantir's UnnecessarilyQualified may throw during analysis
           '-Xep:UnnecessarilyQualified:OFF',
+          '-Xep:UnusedMethod:ERROR',
       )
```

在 `subprojects` 块的 error-prone 规则数组末尾新增一条 `'-Xep:UnusedMethod:ERROR'`：

- `-Xep` 是 error-prone 的规则配置前缀；
- `UnusedMethod` 是规则名，检测项目中没有任何调用点的非入口方法；
- `:ERROR` 表示违规级别为编译错误（其它可选级别为 `WARN`、`OFF`）。

启用 ERROR 级别后，所有子项目的 Java 编译都会经过此检查；违规代码会导致构建失败。规则列表中其余已有的 `:OFF` 规则（如 `TypeParameterUnusedInFormals:OFF`、`UnnecessarilyQualified:OFF`）维持原状，说明本提交仅启用此前缺失的 UnusedMethod 检查，不调整其它规则。

## 小结

- **成效**：在编译期启用了 `UnusedMethod:ERROR` 检查，使 Iceberg 所有子项目的 Java 代码中不能再保留未被调用的方法，从源头阻止死代码积累，降低维护成本、收敛 API 表面。
- **影响范围**：仅 `baseline.gradle` 一个文件、1 行新增。改动本身不修改任何业务代码，但会作用于所有子项目的后续编译。
- **回迁到 1.4.x 的注意事项**：本提交属于代码质量基础设施改进，**适合回迁到 1.4.x 维护分支**，有助于在 1.4.x 后续 patch 中阻止新增死代码。但回迁前必须先在 1.4.x 分支上跑一次编译验证：1.4.x 分支可能存在 main 分支已清理掉但 1.4.x 尚未清理的未使用方法（例如 1.4.x 特有的旧 API、main 上已删除但 1.4.x 仍保留的兼容代码），如果存在则会导致 1.4.x 编译失败。建议先在 1.4.x 上扫描 `UnusedMethod` 违规（可临时开启为 WARN 级别观察），逐个评估：删除、加 `@SuppressWarnings`、或保留为 OFF，确认无违规后再 cherry-pick 本提交设为 ERROR。对于 1.4.x 这种维护分支，可考虑设为 WARN 而非 ERROR 以降低风险，与 main 策略略有不同。
