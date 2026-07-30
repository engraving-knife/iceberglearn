# 提交 1256：API: Add RewriteTablePath action interface (#10920)

## 提交信息

- **序号**：1256 / 4088
- **哈希**：d61a98dc594e86484ea0d7d4bf8214d4ac546143
- **短哈希**：d61a98dc5
- **日期**：2024-10-18（Fri Oct 18 20:01:44 2024 +0200）
- **作者**：Laith AlZyoud <46904854+laithalzyoud@users.noreply.github.com>
- **提交说明**：API: Add RewriteTablePath action interface (#10920)
- **PR/Issue**：#10920

## 总体目的

Iceberg 表的元数据与数据文件中保存了大量绝对路径（如表 location、数据文件 URI、metadata 文件路径等）。当需要把一个表从一处存储迁移/复制到另一处（例如跨存储桶搬迁、云厂商迁移、目录结构调整）时，单纯物理拷贝文件并不够——元数据里仍指向旧路径，导致新位置上的表无法正常读写。社区此前缺乏一个统一的「重写表路径」动作接口来描述这类迁移操作。

本提交在 Iceberg 的 actions API 层新增 `RewriteTablePath` action 接口及其结果接口，并在 `ActionsProvider` 上挂载入口方法 `rewriteTablePath(Table)`。该接口抽象出两种拷贝模式：

1. **Complete copy（全量拷贝）**：把表的全部元数据文件重写到 staging 目录；
2. **Incremental copy（增量拷贝）**：仅重写自某 `startVersion` 起、至某 `endVersion` 止区间内新增的元数据文件，版本以 metadata.json 文件名标识。

接口提供配置源/目标前缀替换、起止版本、自定义 staging 位置等方法，并定义返回结果（staging 位置、源-目标路径清单文件、最新 metadata 版本名）。这是为表迁移场景提供的一层标准契约，具体引擎实现（Spark 等）后续按此接口落地。

本提交同时新增 `BaseRewriteTablePath`（core 侧基类，配 immutables 注解生成 `ImmutableRewriteTablePath.Result`），为后续实现铺路。

## 如何达成设计目的

遵循 Iceberg 既有的 Action 模式（参考已有的 `RewriteDataFiles`、`RewriteManifests`、`ComputeTableStats` 等 actions）：

- **接口分层**：`RewriteTablePath` 继承 `Action<RewriteTablePath, RewriteTablePath.Result>`，定义配置方法（返回 `this` 支持链式调用）与内嵌 `Result` 接口；`ActionsProvider` 用 `default` 方法抛 `UnsupportedOperationException` 挂载入口，保持对未实现该 action 的 provider 的向后兼容。
- **迁移语义建模**：用「source prefix → target prefix」替换表达路径改写；用「start/end metadata 版本」表达增量范围；用「staging location」表达过渡输出位置（默认表 metadata 目录子目录）。结果里的 `fileListLocation` 指向一份逗号分隔的「源路径,目标路径」清单，覆盖该版本区间内所有新增文件（含原始数据文件与重写到 staging 的元数据文件），便于外部执行实际的文件拷贝。
- **immutables 配套**：`BaseRewriteTablePath` 用 `@Value.Enclosing` + `@Value.Style` 定制生成类名 `ImmutableRewriteTablePath` 与公开可见性，内嵌 `@Value.Immutable` 的 `Result` 子接口，让结果对象可由 immutables 生成不可变实现。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/RewriteTablePath.java`（新增）

**修改目的**：定义重写表路径 action 的契约接口。

**工作逻辑**：接口继承 `Action<RewriteTablePath, RewriteTablePath.Result>`。类级 Javadoc 详述两种模式（全量/增量）、用途（作为全量或增量拷贝 Iceberg 表到目标前缀的起点），以及返回内容（最新 metadata.json 名、版本区间内新增文件的原-目标路径清单）。定义如下配置方法（均返回 `RewriteTablePath` 以支持链式）：

- `rewriteLocationPrefix(String sourcePrefix, String targetPrefix)`：配置源前缀→目标前缀的替换；
- `startVersion(String startVersion)`：起始 metadata 版本（metadata.json 文件名），只重写该版本之后新增的元数据文件，可选；
- `endVersion(String endVersion)`：结束 metadata 版本（含），只重写该版本及之前新增的，可选；
- `stagingLocation(String stagingLocation)`：自定义 staging 目录，默认为表 metadata 目录的子目录。

内嵌 `Result` 接口定义三个返回字段：`stagingLocation()`（重写文件的 staging 位置）、`fileListLocation()`（源-目标路径逗号分隔清单文件路径，覆盖原始数据文件与重写到 staging 的元数据文件）、`latestVersion()`（最新 metadata 文件版本名，拷贝完成后作为复制表的根）。

### `api/src/main/java/org/apache/iceberg/actions/ActionsProvider.java`

**修改目的**：在 actions provider 上挂载 `rewriteTablePath` 入口。

**工作逻辑**：在接口末尾新增 `default` 方法：

```java
/** Instantiates an action to rewrite all absolute paths in table metadata. */
default RewriteTablePath rewriteTablePath(Table table) {
  throw new UnsupportedOperationException(
      this.getClass().getName() + " does not implement rewriteTablePath");
}
```

与已有的 `rewriteDataFiles`、`rewriteManifests`、`computeTableStats` 等 default 方法一致，未实现的 provider 会抛 `UnsupportedOperationException`，保持向后兼容（不破坏既有 `ActionsProvider` 实现）。

### `core/src/main/java/org/apache/iceberg/actions/BaseRewriteTablePath.java`（新增）

**修改目的**：为引擎实现提供 immutables 结果基类。

**工作逻辑**：包级可见接口，`@Value.Enclosing` + `@SuppressWarnings("ImmutablesStyle")` + `@Value.Style(typeImmutableEnclosing = "ImmutableRewriteTablePath", visibilityString = "PUBLIC", builderVisibilityString = "PUBLIC")`，继承 `RewriteTablePath`。内嵌 `@Value.Immutable` 的 `Result` 接口继承 `RewriteTablePath.Result`，由 immutables 生成 `ImmutableRewriteTablePath.Result` 不可变实现，供具体 action 实现构造返回结果。

## 小结

- **成效**：在 actions API 层新增标准化的 `RewriteTablePath` action 接口，覆盖全量/增量两种表路径重写模式，定义源-目标前缀替换、起止版本范围、staging 位置等配置与结果契约；`ActionsProvider` 以 default 方法挂载入口，向后兼容；core 侧 `BaseRewriteTablePath` 配 immutables 注解生成结果实现，为后续引擎落地铺路。
- **影响范围**：api 模块 2 个文件（1 新增接口 + 1 改动 provider）、core 模块 1 个新增基类，共约 142 行新增。纯接口/契约层改动，无任何运行时逻辑实现，不影响现有功能。
- **回迁到 1.4.x 的注意事项**：这是新增的 action 接口，属纯增量、向后兼容（default 方法抛异常、新接口不破坏既有 API）。1.4.x 若需要表路径重写/迁移能力，**可安全回迁**接口定义部分（`RewriteTablePath`、`ActionsProvider` 的 default 方法、`BaseRewriteTablePath`）。回迁后 1.4.x 仅获得接口契约，无实际实现（需后续补 Spark 等引擎实现才有功能）。注意 api 模块属 Iceberg 二进制兼容承诺范围，新增接口方法不破坏现有实现；但若 1.4.x 的 `ActionsProvider` 实现类已 final 或有其他约束，应回归测试确保 default 方法挂载不引发编译/运行问题。
