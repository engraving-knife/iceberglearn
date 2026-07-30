# 提交 0516：Build: Bump io.netty:netty-buffer from 4.1.68.Final to 4.1.107.Final (#9744)

## 提交信息

- **序号**：0516 / 4088
- **哈希**：f1459e214493149f30d13e757b91aa18479778d9
- **短哈希**：f1459e214
- **日期**：2024-02-19 10:25:20 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.68.Final to 4.1.107.Final (#9744)
- **PR/Issue**：#9744

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，目的是把 `io.netty:netty-buffer` 升级到较新版本。Dependabot 在扫描时识别到仓库中存在 4.1.68.Final 这个较旧版本（即 `netty-buffer-compat`），并将其作为更新起点，目标版本是 4.1.107.Final。

这次升级的实际意义在于：

1. **修复安全漏洞**。Netty 4.1.x 在 4.1.68.Final 到 4.1.107.Final 之间发布了大量 patch 版本，期间修复了多个 CVE（例如 `CVE-2023-44487` HTTP/2 快速重置攻击相关、`CVE-2023-34462` SslHandler 相关问题等）。Netty 作为底层网络/缓冲库，长期不升级会暴露已知漏洞。
2. **与上层依赖（Arrow）对齐**。Iceberg 的 `iceberg-arrow` 模块通过 `runtimeOnly libs.netty.buffer` 显式声明 netty-buffer 版本，并把 Arrow 自身传递依赖的 netty-buffer 排除掉（见 `build.gradle` 第 797、804 行的 `exclude group: 'io.netty', module: 'netty-buffer'`），目的是"让 netty 库只来自 `iceberg-arrow`"（注释原文：`to make sure netty libs only come from project(':iceberg-arrow')`）。因此 Iceberg 必须主动跟进 netty 的版本，否则会强制 Arrow 使用一个过旧的 netty。
3. **统一两个 netty 版本**。仓库里维护了两个版本变量：`netty-buffer`（主版本，给 Spark 3.3/3.4/3.5 及 iceberg-arrow 用）和 `netty-buffer-compat`（兼容版本，给 Spark 3.2 用，原本锁在更老的 4.1.68.Final）。本次提交把两者统一抬升到 4.1.107.Final，消除了"主版本与兼容版本不一致"的隐患。

## 如何达成设计目的

提交方式非常直接：Dependabot 只修改了 `gradle/libs.versions.toml` 这一个中央版本目录文件，把其中两条 netty 版本声明同时改成 4.1.107.Final。所有依赖 `netty-buffer` 与 `netty-buffer-compat` 的下游模块（iceberg-arrow、spark/v3.2、spark/v3.3、spark/v3.4、spark/v3.5、integration tests 等）都通过 `version.ref` 间接引用，因此改一处即可全局生效，无需逐模块改动。

值得注意的是，本次提交的标题写的是"from 4.1.68.Final to 4.1.107.Final"，但实际 diff 显示是两条版本号同时被改动：

- `netty-buffer`：4.1.97.Final → 4.1.107.Final
- `netty-buffer-compat`：4.1.68.Final → 4.1.107.Final

也就是说 Dependabot 报告的"原版本 4.1.68.Final"取自 compat 那一行（仓库中最旧的那个 netty 版本），但它在生成 PR 时把两条变量一起对齐到 4.1.107.Final。这种做法的好处是让 Spark 3.2 也享受到安全修复，坏处是 Spark 3.2 当时本身依赖的 netty 还停留在较旧版本，强行抬到 4.1.107 可能引入潜在的 API/行为不兼容——不过 netty 4.1.x 在 patch 版本范围内保持了二进制兼容，所以风险可控。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把仓库中两条 netty-buffer 版本号统一升级到 4.1.107.Final，让所有下游模块通过 version.ref 自动获取新版本。

**工作逻辑**：该文件是 Iceberg 项目的 Gradle 版本目录（version catalog），集中管理所有第三方依赖的版本号。文件分为 `[versions]`（纯版本号字符串）和 `[libraries]`（坐标 + version.ref 引用）两段。本次只动 `[versions]` 段的两行：

```toml
# 修改前
netty-buffer = "4.1.97.Final"
netty-buffer-compat = "4.1.68.Final"

# 修改后
netty-buffer = "4.1.107.Final"
netty-buffer-compat = "4.1.107.Final"
```

对应的 `[libraries]` 段定义（未改动，仅作说明）：

```toml
netty-buffer = { module = "io.netty:netty-buffer", version.ref = "netty-buffer" }
netty-buffer-compat = { module = "io.netty:netty-buffer", version.ref = "netty-buffer-compat" }
```

### netty-buffer 在项目中的消费链路

为了说明这次升级影响的范围，下面梳理 netty-buffer 在 Iceberg 中的实际消费路径（这些文件本次未改动，但会随版本抬升而受影响）：

- **`iceberg-arrow` 模块（`build.gradle` 第 796–807 行）**：这是 netty-buffer 唯一的"直接消费方"。Arrow 的 `arrow-vector` 和 `arrow-memory-netty` 都依赖 netty-buffer 做堆外内存分配（ByteBuf），Iceberg 把这两个 Arrow 依赖里传递过来的 netty-buffer 全部 `exclude` 掉，再用 `runtimeOnly libs.netty.buffer` 显式引入自己控制的版本。这样 Iceberg 就能把 netty 版本牢牢锁在自己手里，避免 Arrow 升级时被动跟随。
- **Spark 各版本模块（`spark/v3.2`、`spark/v3.3`、`spark/v3.4`、`spark/v3.5` 的 `build.gradle`）**：这些模块同样对 `iceberg-arrow`、`spark-hive`、`arrow-vector`、`hadoop2-minicluster` 等依赖做了 `exclude group: 'io.netty', module: 'netty-buffer'`，目的是让 netty 库统一来自 iceberg-arrow。其中 Spark 3.2 因为运行时仍需要更老的 netty API，额外用 `runtimeOnly libs.netty.buffer.compat` 引入 compat 版本（见 `spark/v3.2/build.gradle` 第 95–96 行的注释 `// use netty-buffer compatible with Spark 3.2`）。本次升级后，Spark 3.2 也会使用 4.1.107.Final。
- **集成测试（`build.gradle` 第 580–596 行附近的 `integrationImplementation`）**：对 `hadoop2.minicluster` 和 `spark-hive` 同样做了 netty-buffer 排除，逻辑同上。

因此，这次单行级别的版本号改动，影响面覆盖了所有 Spark 模块运行时、Arrow 向量化读取路径以及集成测试的 netty 版本。

## 小结

这是一个由 Dependabot 触发的常规依赖升级，改动量极小（一行版本目录文件，两行变更），但影响面较广：它把仓库中两条 netty-buffer 版本号统一抬升到 4.1.107.Final，覆盖了 4.1.68.Final → 4.1.107.Final、4.1.97.Final → 4.1.107.Final 两个跨度，期间包含若干安全修复。Netty 在 Iceberg 中由 `iceberg-arrow` 模块统一管理（其他模块通过 exclude + version.ref 间接消费），所以这次升级对 Arrow 向量化读取路径和所有 Spark 运行时都生效。

**回迁到 1.4.x 的注意事项**：

1. 1.4.x 分支应当可以平滑 cherry-pick，因为只动了 `libs.versions.toml` 两行，无冲突风险。
2. 需要确认 1.4.x 当时使用的 Arrow 版本是否与 netty 4.1.107 兼容——通常 Arrow 的 netty 依赖范围较宽，但若 1.4.x 锁了更老的 Arrow，应回归测试向量化读（`iceberg-arrow`、`iceberg-spark` 的 Arrow 相关测试）。
3. Spark 3.2 在 1.4.x 是否仍然受支持需要核对。如果 1.4.x 已经放弃 Spark 3.2，则 `netty-buffer-compat` 这一行其实可以一并删除，不必保留。
4. 这次升级跨过了较多 patch 版本，建议在 1.4.x 上跑一遍 `iceberg-arrow`、`iceberg-spark-3.3/3.4/3.5` 的集成测试，确认 ByteBuf 相关行为没有回归。
