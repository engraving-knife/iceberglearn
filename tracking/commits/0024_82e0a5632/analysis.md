# 提交 0024：Core: Use visibility string instead of enum for Immutable visibility (#8752)

## 提交信息

- **序号**：0024 / 4088
- **哈希**：82e0a56323537432e087a07b4cfd5100bf37073d
- **短哈希**：82e0a5632
- **日期**：2023-10-09 08:59:40 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Use visibility string instead of enum for Immutable visibility (#8752)
- **PR/Issue**：#8752

## 总体目的

这个提交把 Iceberg core 模块中所有 `@Value.Style` 注解里的可见性配置，从引用 Immutables 枚举常量改为使用字符串字面量，目的是消除下游消费项目在编译时遇到的一批告警：

```
warning: unknown enum constant ImplementationVisibility.PACKAGE
  reason: class file for org.immutables.value.Value$Style$ImplementationVisibility not found
```

背景在于 Immutables 的注解处理机制与依赖传递问题。Iceberg 用 `@Value.Style(visibility = ImplementationVisibility.PUBLIC, builderVisibility = BuilderVisibility.PUBLIC)` 来控制生成的 `Immutable*` 类与 builder 的可见性。这里引用的 `ImplementationVisibility` / `BuilderVisibility` 是 `org.immutables.value.Value.Style` 的内部枚举。问题在于：Immutables 是一个注解处理依赖，通常以 `optional` 或 `compileOnly` 方式引入（只在编译期需要，不传递给下游）。当 Iceberg 把这些枚举常量直接写进自己公开 API 中的注解时，下游项目编译依赖 Iceberg 的代码（例如通过 `-classpath` 引用 Iceberg jar）时，javac 需要读取注解中的枚举常量，却发现 Immutables 不在自己的编译 classpath 上，于是输出"unknown enum constant"告警。这虽不影响生成的字节码行为，但会在消费方构建中产生大量噪声告警，影响开发体验。

上游 Immutables 在 [immutables/immutables#1474](https://github.com/immutables/immutables/pull/1474) 中新增了字符串形式的 `visibilityString` / `builderVisibilityString` 属性，并在 2.10.0 版本发布（即前一个提交 0022 升级到的版本）。改用字符串后，注解中不再出现对 Immutables 内部枚举类型的符号引用，下游编译时即使 classpath 上没有 Immutables 也不会触发告警。本提交因此紧随 0022 的版本升级，完成从枚举到字符串的迁移。

## 如何达成设计目的

改动覆盖 core 模块下 11 个文件，统一把 `@Value.Style(...)` 中的 `visibility = ImplementationVisibility.PUBLIC` 替换为 `visibilityString = "PUBLIC"`，`builderVisibility = BuilderVisibility.PACKAGE` / `PUBLIC` 替换为 `builderVisibilityString = "PACKAGE"` / `"PUBLIC"`，并移除对应的两个 import（`org.immutables.value.Value.Style.BuilderVisibility` 和 `org.immutables.value.Value.Style.ImplementationVisibility`）。这是一次机械式但跨多文件的批量重构，逻辑等价、行为不变，纯粹是注解属性表达形式的切换。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/Base*.java`（8 个 actions 基类）

涉及文件：
- [BaseDeleteOrphanFiles.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/actions/BaseDeleteOrphanFiles.java)
- [BaseDeleteReachableFiles.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/actions/BaseDeleteReachableFiles.java)
- [BaseExpireSnapshots.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/actions/BaseExpireSnapshots.java)
- [BaseMigrateTable.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/actions/BaseMigrateTable.java)
- [BaseRewriteDataFiles.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/actions/BaseRewriteDataFiles.java)
- [BaseRewriteManifests.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/actions/BaseRewriteManifests.java)
- [BaseRewritePositionalDeleteFiles.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/actions/BaseRewritePositionalDeleteFiles.java)
- [BaseSnapshotTable.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/actions/BaseSnapshotTable.java)

**修改目的**：把 actions 模块下 8 个 `@Value.Enclosing` + `@Value.Style` 注解的可见性配置从枚举改为字符串，并移除两个枚举 import。

**工作逻辑**：每个文件的改动模式完全一致——删除 `import org.immutables.value.Value.Style.BuilderVisibility;` 和 `import org.immutables.value.Value.Style.ImplementationVisibility;` 两行；将 `@Value.Style(...)` 内的 `visibility = ImplementationVisibility.PUBLIC` 改为 `visibilityString = "PUBLIC"`，`builderVisibility = BuilderVisibility.PUBLIC` 改为 `builderVisibilityString = "PUBLIC"`。`typeImmutableEnclosing` 等其他属性保持不变。语义等价：仍要求生成的 `Immutable*` 类与其 builder 均为 `PUBLIC` 可见性。

### `core/src/main/java/org/apache/iceberg/view/BaseViewHistoryEntry.java`

**修改目的**：view 历史条目基类的 `@Value.Style` 可见性从枚举改为字符串。

**工作逻辑**：同样删除两个枚举 import，把 `visibility = ImplementationVisibility.PUBLIC` 改为 `visibilityString = "PUBLIC"`，`builderVisibility = BuilderVisibility.PUBLIC` 改为 `builderVisibilityString = "PUBLIC"`。`typeImmutable = "ImmutableViewHistoryEntry"` 保持不变。

### `core/src/main/java/org/apache/iceberg/view/BaseViewVersion.java`

**修改目的**：view 版本基类的 `@Value.Style` 可见性从枚举改为字符串。

**工作逻辑**：与上面相同的替换模式，删除两个枚举 import，把两个 `visibility` 属性改为对应的 `visibilityString` 字符串形式，`typeImmutable = "ImmutableViewVersion"` 不变。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java`

**修改目的**：view 元数据接口的 `@Value.Style` 可见性从枚举改为字符串。

**工作逻辑**：该文件略有不同——它只声明了 `visibility`（值为 `ImplementationVisibility.PACKAGE`），没有 `builderVisibility`（因为 `@Value.Immutable(builder = false)` 不生成 builder）。改动删除 `import org.immutables.value.Value.Style.ImplementationVisibility;`，把 `@Value.Style(allParameters = true, visibility = ImplementationVisibility.PACKAGE)` 改为 `@Value.Style(allParameters = true, visibilityString = "PACKAGE")`。语义等价：生成的 `ImmutableViewMetadata` 仍为包级可见。

## 小结

借助 Immutables 2.10.0 新增的字符串风格属性，将 core 模块 11 处 `@Value.Style` 的可见性配置从枚举常量改为字符串字面量，消除因 Immutables 不在下游编译 classpath 而产生的 "unknown enum constant" 告警，改善消费方构建体验。
