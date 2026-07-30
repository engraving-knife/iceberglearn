# 提交 0953：Build: Bump nessie from 0.92.1 to 0.93.1 (#10727)

## 提交信息

- **序号**：0953 / 4088
- **哈希**：0e678ce588c75019948b28a26572e972ce8a07ef
- **短哈希**：0e678ce58
- **日期**：2024-07-21（Sun Jul 21 19:14:14 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.92.1 to 0.93.1 (#10727)
- **PR/Issue**：#10727

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。Nessie 是 Iceberg 支持的目录服务（catalog）之一，Iceberg 通过 `nessie` 依赖集成 Nessie 提供的版本化目录能力。本次提交将 Nessie 版本从 0.92.1 升级到 0.93.1，目的是跟进上游 Nessie 的最新发布版本，获取其修复、改进以及潜在的 API 兼容性更新，确保 Iceberg 与 Nessie 的集成始终基于受支持的依赖版本，降低安全风险与已知 bug 风险。

此类 dependabot 升级属于例行维护，通常由 CI 验证通过后直接合入，无需人工编写新功能代码。

## 如何达成设计目的

实现方式为修改 Gradle 版本目录（version catalog）文件 `gradle/libs.versions.toml`，将其中 `nessie` 的版本声明从 `0.92.1` 改为 `0.93.1`。由于 Iceberg 所有引用 Nessie 的模块都通过该版本目录统一引用版本号，因此单点修改即可让所有相关模块同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 依赖版本从 0.92.1 升级到 0.93.1。

**工作逻辑**：版本目录（TOML 格式）中定义了各依赖的统一版本号。本提交仅修改 `nessie` 一行：

```diff
-nessie = "0.92.1"
+nessie = "0.93.1"
```

修改后，所有通过 `${libs.nessie}` 等方式引用该版本号的 Gradle 模块（如 `nessie` 集成测试、NessieCatalog 相关模块）在构建时会自动拉取 0.93.1 版本。

## 小结

- **成效**：完成 Nessie 依赖从 0.92.1 到 0.93.1 的例行升级，使 Iceberg 与 Nessie 的集成基于较新版本。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，1 行改动。影响所有引用 nessie 版本的模块的构建产物依赖版本，但不改变 Iceberg 自身代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯依赖升级，**可回迁但需谨慎**。回迁前应确认 1.4.x 分支的 Nessie 集成测试在该版本下能够通过，并检查 0.92.1 → 0.93.1 之间 Nessie 是否有破坏性 API 变更影响 1.4.x 中的 NessieCatalog 实现。若 1.4.x 分支已有自己的 Nessie 版本节奏，可按维护分支策略决定是否跟随升级。
