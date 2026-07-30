# 提交 0634：Build: Bump org.xerial:sqlite-jdbc from 3.45.1.0 to 3.45.2.0

## 提交信息

- **序号**：0634 / 4088
- **哈希**：371a6b7ff7f3a776e1616e90a95ec99c64df1ac2
- **短哈希**：371a6b7ff
- **日期**：2024-03-27 17:18:49 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial:sqlite-jdbc from 3.45.1.0 to 3.45.2.0 (#9974)
- **PR/Issue**：#9974

## 总体目的

本提交由 Dependabot 自动生成，把 Iceberg 项目使用的 `org.xerial:sqlite-jdbc` 依赖从 `3.45.1.0` 升级到 `3.45.2.0`，让测试用的 SQLite JDBC 驱动保持与上游最新补丁版本同步，吸收上游的 bug 修复与小型改进。

背景动机：Dependabot 会定期扫描 `gradle/libs.versions.toml`（项目集中维护依赖版本的目录）中声明的依赖，发现上游有新版本时自动提 PR。`sqlite-jdbc` 是 Xerial 维护的纯 Java SQLite JDBC 驱动，发布较为频繁，每个补丁版本通常包含对 native 绑定、JDBC API 行为或新版本 SQLite 引擎的更新。版本号 `3.45.1.0` → `3.45.2.0` 属于第四位（patch）级别的小幅升级（dependabot 在 PR 描述中标注为 `version-update:semver-patch`），不涉及上游 API 变更，预期对 Iceberg 现有调用方无破坏性影响。

依赖在项目中的角色：

- `org.xerial:sqlite-jdbc` 在 Iceberg 中**仅作为测试依赖**使用，标注为 `dependency-type: direct:production`（dependabot 的元数据中虽写为 production，但项目内实际声明为 `testImplementation`）。
- 它为 Iceberg 的 `JdbcCatalog`（Iceberg 提供的一种把 catalog 元数据存储在 JDBC 数据库中的实现）测试提供后端：测试通过 `jdbc:sqlite:file::memory:?<dbName>` 这种 in-memory JDBC URL 启动一个进程内的 SQLite 实例作为 catalog 的存储后端，避免依赖外部数据库服务，让单元/集成测试可独立运行。
- 具体使用方包括：`core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`、`core/src/test/java/org/apache/iceberg/jdbc/TestJdbcTableConcurrency.java`、`core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`、`aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java`，以及各 Spark 模块（`spark/v3.2` ~ `spark/v3.5`）的 `build.gradle` 中的 `testImplementation libs.sqlite.jdbc`。主代码 `core/src/main/java` 不直接 import SQLite 类，`JdbcCatalog` 通过标准 `java.sql.*` 接口与驱动交互，因此驱动升级对生产代码无影响。

## 如何达成设计目的

设计思路就是 Dependabot 的标准单行升级：在 `gradle/libs.versions.toml` 中把 `sqlite-jdbc` 的版本字符串从 `3.45.1.0` 改为 `3.45.2.0`。由于项目所有使用 SQLite 的模块都通过 `libs.sqlite.jdbc` 这个 version catalog 别名引用，且别名指向 `version.ref = "sqlite-jdbc"`，所以一处版本号变更会自动传导到所有声明该别名的 build.gradle 文件，无需逐模块修改。

Dependabot 提交时同时附带了标准的元数据 footer（`updated-dependencies`、`Signed-off-by`、`Co-authored-by`），用于追溯升级来源与依赖类型。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `sqlite-jdbc` 版本号从 `3.45.1.0` 升级到 `3.45.2.0`。

**工作逻辑**：单行修改：

```toml
-sqlite-jdbc = "3.45.1.0"
+sqlite-jdbc = "3.45.2.0"
```

该行位于 `[versions]` 段（约第 85 行附近），是 `sqlite-jdbc` 这个版本变量的唯一定义点。同文件下方 `[libraries]` 段还有一处 `sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }` 引用了该变量，本次修改无需触及——`version.ref` 会自动解析到新值。

修改后，所有 `build.gradle` 中通过 `libs.sqlite.jdbc` 引用该依赖的模块（包括 `core`、`aws`、`spark/v3.2`、`spark/v3.3`、`spark/v3.4`、`spark/v3.5`）都会在下次构建时拉取 `3.45.2.0` 版本。

## 小结

这是一个由 Dependabot 自动生成的补丁级依赖升级，单一文件单行修改。`sqlite-jdbc` 在 Iceberg 中是测试依赖，作为 `JdbcCatalog` 测试的 in-memory 后端，主代码不直接使用，因此本次升级对生产代码零影响，对测试代码预期也无破坏（patch 级升级，无 API 变更）。

回迁到 1.4.x 的注意事项：
- 直接 cherry-pick 即可，仅修改 `gradle/libs.versions.toml` 一行，与 1.4.x 的依赖管理结构兼容。
- 由于是测试依赖，回迁风险极低；如 1.4.x 的 CI 跑全套 JdbcCatalog 测试，升级后注意观察是否有因上游 SQLite 行为微调导致的个别测试失败（实践中 patch 升级很少出现这种情况）。
- 建议把 0634 与 0635（同日提交的 netty-buffer 升级）一起回迁，保持依赖版本快照与 main 一致，便于后续升级的线性推进。
