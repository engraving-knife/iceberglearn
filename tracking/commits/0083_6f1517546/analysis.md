# 提交 0083：Core: Derive View operation from version (#8678)

## 提交信息

- **序号**：0083 / 4088
- **哈希**：6f1517546aceee19e10f38e36df7b072267fe6db
- **短哈希**：6f1517546
- **日期**：2023-10-20
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Derive View operation from version (#8678)
- **PR/Issue**：#8678

## 总体目的

这个提交重构了 Iceberg View（视图）规范中 `operation`（操作类型）的语义与实现方式：从"在 view version 的 summary 中显式存储 `operation` 字段"改为"根据 versionId 派生 operation"。这是 View 规范（view-spec）演进中的一个重要简化。

**背景与动机**：在此之前，view-spec 规定 view version 的 summary 中必须包含一个 `operation` 键，取值为 `create` 或 `replace`，用于标识该版本是创建视图还是替换视图产生。[`ViewVersion.operation()`](../../../../api/src/main/java/org/apache/iceberg/view/ViewVersion.java) 的默认实现从 `summary().get("operation")` 读取，而 [`BaseViewVersion`](../../../../core/src/main/java/org/apache/iceberg/view/BaseViewVersion.java) 还会用 `Preconditions.checkArgument` 强制校验 summary 中存在 `operation` 键，缺失则抛 `IllegalArgumentException`。这种设计有几个问题：

1. **冗余**：`operation` 完全可以由 versionId 推断——versionId 为 1 即为 `create`，否则为 `replace`，因为视图不存在 update 操作，只有 create/replace。强制存储一个可派生的字段增加了写入与解析负担。
2. **校验过严**：`BaseViewVersion` 的强制校验使得任何 summary 中缺少 `operation` 的 view version（包括旧版本或第三方写入的元数据）在调用 `operation()` 时直接抛异常，容错性差。`TestViewVersionParser.testFailParsingMissingOperation` 正是测这种抛异常行为。
3. **去重逻辑偏差**：[`ViewMetadata.sameViewVersion`](../../../../core/src/main/java/org/apache/iceberg/view/ViewMetadata.java) 在比较两个 view version 是否等价时，此前忽略 summary 差异，导致仅 summary（含 operation）不同的版本会被视为重复而合并。但 summary 中可能携带用户自定义元数据（如 user、engine-name），不应被无差别去重。

**引入的能力**：修改后，`operation()` 改为 `versionId() == 1 ? "create" : "replace"`，operation 不再是 summary 的必需字段；`BaseViewVersion` 移除了强制校验；`sameViewVersion` 改为比较 summary（保留自定义元数据的差异性）；catalog 在创建/替换视图时不再写 `operation` 到 summary，而是写入 `EnvironmentContext`（引擎名、版本等运行时上下文）。同时 view-spec.md 删除了 `operation` 作为 required 字段的说明。这使 View 规范更简洁、容错性更好，并与 Table 元数据中"operation 可派生"的设计哲学对齐。

## 如何达成设计目的

整体设计思路是"派生优于存储"。改动分四部分：(1) API 层 [`ViewVersion.operation()`](../../../../api/src/main/java/org/apache/iceberg/view/ViewVersion.java) 默认实现改为基于 versionId 派生；(2) Core 层 [`BaseViewVersion`](../../../../core/src/main/java/org/apache/iceberg/view/BaseViewVersion.java) 移除强制校验并委托给父接口默认实现，[`ViewMetadata`](../../../../core/src/main/java/org/apache/iceberg/view/ViewMetadata.java) 的去重逻辑改为纳入 summary 比较；catalog 写入侧（[`BaseMetastoreViewCatalog`](../../../../core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java)、[`ViewVersionReplace`](../../../../core/src/main/java/org/apache/iceberg/view/ViewVersionReplace.java)）用 `EnvironmentContext.get()` 替代硬编码的 `operation`；(3) 规范文档 [`format/view-spec.md`](../../../../format/view-spec.md) 删除 operation required 行；(4) 大量测试与 JSON 测试资源把 summary 中的 `operation` 替换为 `user` 等自定义键，并删除旧的缺失 operation 抛异常测试，新增带自定义 summary 的去重测试。

## 修改详情

### `api/src/main/java/org/apache/iceberg/view/ViewVersion.java`

**修改目的**：将 `operation()` 的默认实现从读取 summary 改为根据 versionId 派生。

**工作逻辑**：原实现 `return summary().get("operation");` 改为 `return versionId() == 1 ? "create" : "replace";`。语义依据：视图版本 1 一定是初始创建，后续版本一定是替换（视图不支持部分更新，只有 create/replace 两种操作）。这样 operation 不再依赖 summary 内容，无需写入与持久化。

### `core/src/main/java/org/apache/iceberg/view/BaseViewVersion.java`

**修改目的**：移除对 summary 中 `operation` 键的强制校验，并委托给父接口的派生实现。

**工作逻辑**：原 `@Value.Lazy default String operation()` 中有 `Preconditions.checkArgument(summary().containsKey("operation"), "Invalid view version summary, missing operation")` 并返回 `summary().get("operation")`。修改后移除该 Preconditions 校验与 import，直接 `return ViewVersion.super.operation();`（即调用父接口新增的派生实现）。这解决了 summary 缺少 operation 时调用 `operation()` 抛异常的容错问题。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java`

**修改目的**：修正 view version 去重逻辑，使其纳入 summary 比较，保留自定义元数据的差异性。

**工作逻辑**：`sameViewVersion(ViewVersion one, ViewVersion two)` 方法此前比较 representations、defaultCatalog、defaultNamespace、schemaId，但忽略 summary（注释说"ignoring the view version id, the creation timestamp, and the summary"）。修改后注释改为"ignoring the view version id, the creation timestamp, and the operation"，并在比较链首部加入 `Objects.equals(one.summary(), two.summary())`。由于 operation 不再存于 summary，summary 现在承载的是用户/引擎自定义元数据，去重时理应区分。配套新增 `viewVersionDeduplicationWithCustomSummary` 测试验证此行为。

### `core/src/main/java/org/apache/iceberg/view/BaseMetastoreViewCatalog.java`

**修改目的**：视图创建/替换时不再向 summary 写入硬编码的 `operation`，改为写入运行时环境上下文。

**工作逻辑**：在 `create` 与 `replace` 路径中，原本分别 `.putSummary("operation", "create")` 和 `.putSummary("operation", "replace")`，现统一改为 `.putAllSummary(EnvironmentContext.get())`。`EnvironmentContext.get()` 返回引擎名、引擎版本等运行时元数据，这些是 view-spec 中 summary 的 optional 字段，比硬编码 operation 更有价值。新增 `import org.apache.iceberg.EnvironmentContext;`。

### `core/src/main/java/org/apache/iceberg/view/ViewVersionReplace.java`

**修改目的**：替换视图版本时同样用 `EnvironmentContext` 替代硬编码 `operation`。

**工作逻辑**：将 `.putSummary("operation", "replace")` 改为 `.putAllSummary(EnvironmentContext.get())`，与 `BaseMetastoreViewCatalog` 保持一致。新增 `import org.apache.iceberg.EnvironmentContext;`。

### `format/view-spec.md`

**修改目的**：更新 View 规范文档，移除 `operation` 作为 summary 必需字段的约定。

**工作逻辑**：删除 summary 表格中的 `_required_ | operation | ...` 行，并删除规范示例 JSON 中的 `"operation" : "create"` 字段（共 3 处）。规范现在不再要求 summary 持久化 operation。

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`

**修改目的**：适配 `AddViewVersion` 测试用例，summary 不再用 `operation` 而改用 `user`。

**工作逻辑**：两处 viewVersion 构造从 `.putSummary("operation", "replace")` 改为 `.putSummary("user", "some-user")`，对应 JSON 期望串中的 `"summary":{"operation":"replace"}` 改为 `"summary":{"user":"some-user"}`。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java`

**修改目的**：适配去重与构建测试，移除对 `operation` summary 的依赖，并新增自定义 summary 去重测试。

**工作逻辑**：多处 viewVersion 构造删除 `.putSummary("operation", ...)` 或改用 `.putSummary("user", "some-user")` / `.summary(ImmutableMap.of("user", "some-user"))`。原 `viewVersionDeduplication` 测试中通过修改 summary（含 operation）构造"重复"版本的用例，改为通过修改 timestampMillis 构造重复版本。新增 `viewVersionDeduplicationWithCustomSummary` 测试：构造仅 summary 不同的 view version，验证它们不会被去重，且 versionId 被重新分配，断言最终 versions 大小为 3。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadataParser.java`

**修改目的**：适配序列化测试，summary 从 `operation` 改为 `user`。

**工作逻辑**：5 处 viewVersion 构造的 `.summary(ImmutableMap.of("operation", "create/replace"))` 改为 `.summary(ImmutableMap.of("user", "some-user"))`。

### `core/src/test/java/org/apache/iceberg/view/TestViewVersionParser.java`

**修改目的**：移除缺失 operation 抛异常的测试，并适配其余测试的 summary 内容。

**工作逻辑**：删除整个 `testFailParsingMissingOperation` 测试方法（该方法此前验证 summary 缺 operation 时 `operation()` 抛 `IllegalArgumentException`，与新派生逻辑不再兼容）。其余测试 summary 从 `ImmutableMap.of("operation", "create", "user", "some-user")` 改为 `ImmutableMap.of("user", "some-user")`，对应 JSON 期望串同步删除 `"operation":"create"`。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：适配 catalog 测试，新增对派生 operation 的断言，移除对 summary 含 operation 的断言。

**工作逻辑**：在 `createView`/`replaceView` 测试中新增 `assertThat(view.currentVersion().operation()).isEqualTo("create")` 验证派生逻辑。构建期望 viewVersion 时从 `.putSummary("operation", "create")` 改为 `.summary(view.currentVersion().summary())`（直接复用实际 summary）。删除 `assertThat(replacedViewVersion.summary()).hasSize(1).containsEntry("operation", "replace")` 等旧断言（共 2 处），因为 summary 不再包含 operation。

### JSON 测试资源文件（`ValidViewMetadata.json` 等 6 个文件）

**修改目的**：更新测试用 JSON 元数据资源，使 summary 与新规范一致。

**工作逻辑**：`ValidViewMetadata.json`、`ViewMetadataInvalidCurrentSchema.json`、`ViewMetadataInvalidCurrentVersion.json`、`ViewMetadataMissingCurrentVersion.json`、`ViewMetadataMissingLocation.json`、`ViewMetadataMultipleSQLsForDialect.json` 中，所有 `"summary": {"operation":"create/replace"}` 改为 `"summary": {"user": "some-user"}`。这些 JSON 主要用于解析器/校验器的反序列化测试。

## 小结

这个提交将 View 的 `operation` 从 summary 中显式存储改为根据 versionId 派生，简化了规范、提升了容错性，并让 summary 专注于承载有价值的运行时元数据，是 View 规范演进中的重要一步。
