# 提交 0635：Build: Bump io.netty:netty-buffer from 4.1.107.Final to 4.1.108.Final

## 提交信息

- **序号**：0635 / 4088
- **哈希**：baaedc6e041bd0f0dfc7bec2d4f2c67c1db1ef77
- **短哈希**：baaedc6e0
- **日期**：2024-03-27 17:19:20 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.107.Final to 4.1.108.Final (#10032)
- **PR/Issue**：#10032

## 总体目的

本提交由 Dependabot 自动生成，把 Iceberg 项目使用的 `io.netty:netty-buffer` 依赖从 `4.1.107.Final` 升级到 `4.1.108.Final`，让运行时依赖的 Netty 缓冲区实现保持与上游最新补丁版本同步，吸收上游的 bug 修复与可能的 CVE 补丁。

背景动机：与 0634 同属 Dependabot 自动升级类提交，在同一天连续合入。`io.netty:netty-buffer` 是 Netty 项目中提供 `ByteBuf` 字节缓冲区实现的核心模块。Netty 4.1.x 系列在 Java 生态中被广泛使用，每个 Final 版本通常包含若干 bug 修复、行为调整以及偶发的安全补丁，Dependabot 标注本次为 `version-update:semver-patch`（patch 级升级），不涉及 API 破坏性变更。

依赖在项目中的角色：

- `io.netty:netty-buffer` 在 Iceberg 中**作为运行时依赖**（`runtimeOnly`）使用，与 0634 升级的 `sqlite-jdbc`（仅测试用）不同，这次升级触及实际产物。
- 具体用途是为 **Apache Arrow 的 `arrow-memory-netty` 模块**提供 `ByteBuf` 后端。Arrow 的 `memory-netty` 模块是一个 off-heap 内存分配器实现，依赖 Netty 的 `netty-buffer` 与 `netty-common` 来管理堆外内存。Iceberg 在向量化读（vectorized read）等场景下使用 Arrow 做列式数据传输，因此需要拉入 Arrow 的 netty 内存分配器，进而间接依赖 `netty-buffer`。
- 在 `gradle/libs.versions.toml` 中，本提交同时修改两个版本变量：
  - `netty-buffer`：被 `core` 模块（`build.gradle` 第 807 行 `runtimeOnly libs.netty.buffer`）使用。
  - `netty-buffer-compat`：被 `spark/v3.2/build.gradle`（`runtimeOnly libs.netty.buffer.compat`）使用，注释明确写明"use netty-buffer compatible with Spark 3.2"——Spark 3.2 自带的 Netty 版本较旧，因此 Iceberg 在 Spark 3.2 模块中用一个"兼容版"的 netty-buffer 与之对齐。两者在 `libs.versions.toml` 中独立声明，但实际项目中通常保持同步升级（本提交就是把两者同时从 4.1.107 升到 4.1.108）。
- 在 `core/build.gradle` 中可以看到一个细节：`implementation(libs.arrow.memory.netty) { exclude group: 'io.netty', module: 'netty-buffer'; exclude group: 'io.netty', module: 'netty-common' }`——Iceberg 显式排除了 Arrow memory-netty 传递依赖里的 netty 模块，紧接着又用 `runtimeOnly libs.netty.buffer` 显式拉入自己想要的版本，从而把 netty-buffer 版本掌控在自己手中，避免 Arrow 升级时被动跟随 Arrow 的 netty 版本。这正是 Dependabot 升级 netty-buffer 能直接生效的机制基础。

## 如何达成设计目的

设计思路是 Dependabot 的标准目录化版本升级：在 `gradle/libs.versions.toml` 的 `[versions]` 段把两个变量同时改一行：

```toml
-netty-buffer = "4.1.107.Final"
-netty-buffer-compat = "4.1.107.Final"
+netty-buffer = "4.1.108.Final"
+netty-buffer-compat = "4.1.108.Final"
```

由于 `[libraries]` 段已声明 `netty-buffer = { module = "io.netty:netty-buffer", version.ref = "netty-buffer" }` 与 `netty-buffer-compat = { module = "io.netty:netty-buffer", version.ref = "netty-buffer-compat" }`，所有 `build.gradle` 中通过 `libs.netty.buffer` / `libs.netty.buffer.compat` 引用该依赖的模块都会在下次构建时拉取新版本，无需逐模块修改。Dependabot 同时附带了标准的元数据 footer 用于追溯升级来源与依赖类型。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `netty-buffer` 与 `netty-buffer-compat` 两个版本变量从 `4.1.107.Final` 升级到 `4.1.108.Final`。

**工作逻辑**：两行同步修改：

```toml
-netty-buffer = "4.1.107.Final"
-netty-buffer-compat = "4.1.107.Final"
+netty-buffer = "4.1.108.Final"
+netty-buffer-compat = "4.1.108.Final"
```

这两行位于 `[versions]` 段（约第 66-67 行附近）。同文件下方 `[libraries]` 段（第 138-139 行）通过 `version.ref` 引用这两个变量，本次修改无需触及——`version.ref` 会自动解析到新值。修改后影响范围：

- `core` 模块：`build.gradle` 中 `runtimeOnly libs.netty.buffer` 生效，影响 Iceberg core JAR 的运行时类路径。
- `spark/v3.2` 模块：`build.gradle` 中 `runtimeOnly libs.netty.buffer.compat` 生效，影响 Spark 3.2 集成产物。

## 小结

这是一个由 Dependabot 自动生成的补丁级依赖升级，单文件两行修改。与 0634 不同，`netty-buffer` 是 Iceberg core 的运行时依赖（为 Arrow 的 off-heap 内存分配器提供 `ByteBuf` 后端），因此本次升级会进入实际产物运行时类路径。但 patch 级升级通常无 API 破坏，对 Iceberg 主代码无影响。

回迁到 1.4.x 的注意事项：
- 当前 1.4.x 工作树中 `netty-buffer` 处于更旧的版本（4.1.97.Final），意味着 1.4.x 与 main 之间还隔着若干次中间升级（4.1.97 → ... → 4.1.107 → 4.1.108）。直接 cherry-pick 本提交可能会因上下文行不匹配而失败，需要手动把 1.4.x 的 `netty-buffer` 与 `netty-buffer-compat` 两个变量直接改成 4.1.108.Final，或者先回迁中间的几次 Dependabot 升级再回迁本提交。
- 建议把 0634 与 0635 一起回迁，保持依赖版本快照与 main 一致，便于后续升级的线性推进。
- 升级后建议在 1.4.x 的 CI 中跑一遍 core 与 spark/v3.2 的测试套件，确认 Arrow 向量化读路径在新 netty-buffer 版本下行为稳定（patch 升级很少出问题，但 Arrow/netty 的版本组合偶有 corner case）。
- 由于 1.4.x 后续版本中 Spark 3.2 会逐步被废弃，`netty-buffer-compat` 这个变量在 1.4.x 后续阶段可能不再需要维护，但只要 1.4.x 仍维护 Spark 3.2 模块，本提交就应被同步回迁以保持模块行为一致性。
