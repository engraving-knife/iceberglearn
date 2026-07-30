# 提交 0276：API, Core: Move SQLViewRepresentation to API (#9302)

## 提交信息

- **序号**：0276 / 4088
- **哈希**：8572c56e8dce17b9cc3d7ee05a61fdcc8b707021
- **短哈希**：8572c56e8
- **日期**：2023-12-14 17:38:13 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：API, Core: Move SQLViewRepresentation to API (#9302)
- **PR/Issue**：#9302

## 总体目的

本提交的核心目标是把 `SQLViewRepresentation` 接口从 `iceberg-core` 模块迁移到 `iceberg-api` 模块，使其成为公共 API 的一部分。这不是一次简单的文件搬家，而是 Iceberg 视图（view）API 演进过程中的一个关键架构调整，背后有清晰的模块边界和后续扩展需求在驱动。

在 Iceberg 的模块划分中，`iceberg-api` 模块定义对外暴露的抽象接口（如 `View`、`ViewRepresentation`、`ViewVersion` 等），下游引擎（Spark、Flink、Trino 等）和 catalog 实现者仅依赖 api 模块即可编写与 Iceberg 交互的代码；而 `iceberg-core` 模块负责这些接口的具体实现、元数据序列化/反序列化以及 Immutables 生成类等"实现细节"。在提交之前，`SQLViewRepresentation` 这个本应属于公共抽象的接口却位于 core 模块，且直接挂载了 Immutables 的 `@Value.Immutable` 注解，这导致 api 模块无法在不引入 core 依赖的情况下引用该类型。

迁移的动机直接来自紧随其后的 PR #9247（提交 0278）：该 PR 计划在 `View` 接口（位于 api 模块）上新增 `sqlFor(String dialect)` 方法，用于按 SQL 方言解析视图的表示。`sqlFor` 的返回类型正是 `SQLViewRepresentation`。如果 `SQLViewRepresentation` 仍留在 core 模块，那么 api 模块的 `View` 接口就无法引用它，整个 `sqlFor` API 就无从设计。因此本提交是后续方言解析能力的"前置地基"，先把类型放到正确的模块，再在其上构建新 API。

此外，把表示类型上移到 api 也符合 Iceberg 一贯的"接口在 api、生成类在 core"的设计约定（参考 `BaseViewVersion`/`ViewVersion`、`BaseViewHistoryEntry`/`ViewHistoryEntry` 等已有模式）：纯接口在 api 保持轻量与无依赖，core 中提供一个带 Immutables 注解的 `Base*` 子接口用于生成不可变实现类。

## 如何达成设计目的

提交通过两个步骤达成目的：

1. **搬迁接口**：把 `core/.../view/SQLViewRepresentation.java` 移动到 `api/.../view/SQLViewRepresentation.java`，git 以 `rename from ... rename to ...` 形式记录。搬迁过程中从接口上移除了 `@Value.Immutable` 注解及其 `import org.immutables.value.Value;`，仅保留接口本身的方法定义，并补上一行 Javadoc 说明类型用途。这样 api 模块得到一个干净的、无第三方注解依赖的纯接口。

2. **在 core 重建生成入口**：新增 `core/.../view/BaseSQLViewRepresentation.java`，这是一个包级可见的接口，继承自 api 的 `SQLViewRepresentation`，并挂载 Immutables 相关注解（`@Value.Immutable`、`@Value.Include`、`@Value.Style`）。通过 `@Value.Include(value = SQLViewRepresentation.class)` 告诉 Immutables 处理器为父接口 `SQLViewRepresentation` 生成实现类，并通过 `@Value.Style` 显式指定生成类名 `ImmutableSQLViewRepresentation`、公开的可见性与 builder 可见性。这样原先由 `SQLViewRepresentation` 自身触发 Immutables 生成的行为，现在改由 `BaseSQLViewRepresentation` 承担，生成的 `ImmutableSQLViewRepresentation` 类名与公开 API 保持兼容。

这种"接口上移 + Base 子接口兜底生成"的方式既纠正了模块归属，又没有破坏现有代码对 `ImmutableSQLViewRepresentation` 的引用，是低风险的迁移。

## 修改详情

### `api/src/main/java/org/apache/iceberg/view/SQLViewRepresentation.java`（由 `core/.../SQLViewRepresentation.java` 重命名而来）

**修改目的**：把 SQL 视图表示接口搬到 api 模块，使其成为公共 API 类型，并清除 Immutables 注解以保持 api 模块的纯净。

**工作逻辑**：

- 通过 `rename from core/src/main/java/org/apache/iceberg/view/SQLViewRepresentation.java` / `rename to api/src/main/java/org/apache/iceberg/view/SQLViewRepresentation.java` 完成跨模块搬迁。git 识别为 93% 相似度（剩余差异为下面两处改动）。
- 删除了原文件中的 `import org.immutables.value.Value;` 与接口上的 `@Value.Immutable` 注解。这是关键改动：api 模块不希望引入 Immutables 这个注解处理器的依赖，纯接口不应携带实现层注解。Immutables 生成职责被移交给新增的 `BaseSQLViewRepresentation`。
- 新增一行类级 Javadoc `/** SQLViewRepresentation represents views in SQL with a given dialect */`，简要说明类型用途（旧版本没有类级 Javadoc）。这虽是小改动，但体现了把接口升格为公共 API 后对其文档化的要求。

接口内部的方法定义（`type()` 返回 `Type.SQL`、`String sql()`、`String dialect()`）保持不变，对外契约完全兼容。

### `core/src/main/java/org/apache/iceberg/view/BaseSQLViewRepresentation.java`（新增）

**修改目的**：在 core 模块重新建立 Immutables 生成入口，接管原 `SQLViewRepresentation` 上的 `@Value.Immutable` 职责，确保 `ImmutableSQLViewRepresentation` 仍能被生成且类名不变。

**工作逻辑**：

新文件定义了一个包级可见的接口 `BaseSQLViewRepresentation extends SQLViewRepresentation`，并在其上挂载一组 Immutables 注解：

- `@Value.Immutable`：标记需要 Immutables 生成不可变实现类。
- `@Value.Include(value = SQLViewRepresentation.class)`：告诉 Immutables 处理器，把被 include 的 `SQLViewRepresentation`（api 模块的父接口）也作为生成目标。这是本提交的精髓所在——即便 `@Value.Immutable` 不再写在 `SQLViewRepresentation` 上，通过 `@Value.Include` 仍能让处理器为它生成实现，从而保留旧的 `ImmutableSQLViewRepresentation` 类名供调用方继续使用。
- `@SuppressWarnings("ImmutablesStyle")`：抑制 Immutables 风格检查告警。
- `@Value.Style(typeImmutable = "ImmutableSQLViewRepresentation", visibilityString = "PUBLIC", builderVisibilityString = "PUBLIC")`：显式定制生成风格——生成类名固定为 `ImmutableSQLViewRepresentation`（与原行为一致），类型与 builder 的可见性均为 `PUBLIC`。

包级可见（无 `public` 修饰符）意味着 `BaseSQLViewRepresentation` 仅在 core 模块内部可见，外部模块无法直接引用它，只能通过父接口 `SQLViewRepresentation`（api）编程，并通过 `ImmutableSQLViewRepresentation`（由 Immutables 生成的 public 类）实例化。这与 Iceberg 既有的 `BaseViewVersion`/`BaseViewHistoryEntry` 模式完全一致，是 core 模块对 Immutables 生成入口的标准封装方式。

## 小结

本提交是 Iceberg 视图 API 演进的前置重构：将 `SQLViewRepresentation` 从 core 上移到 api，使其成为真正意义上的公共类型，同时通过在 core 新增 `BaseSQLViewRepresentation` 承接 Immutables 生成职责，保证了对外契约（`ImmutableSQLViewRepresentation` 类名）的兼容性。这次迁移纠正了历史遗留的模块归属问题，使 api 模块的视图抽象更加完整，并为紧随其后的 PR #9247（在 `View` 接口上新增 `sqlFor` 方法）铺平了道路——没有这次搬迁，api 模块的 `View` 接口根本无法引用 `SQLViewRepresentation` 作为返回类型。提交改动小、风险低，但架构意义明确，是 Iceberg 视图能力扩展链条上的关键一环。
