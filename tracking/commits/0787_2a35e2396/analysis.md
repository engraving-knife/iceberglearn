# 提交 0787：Build: Bump software.amazon.awssdk:bom from 2.25.57 to 2.25.60 (#10385)

## 提交信息

- **序号**：0787 / 4088
- **哈希**：2a35e239603056d99a5576d25ac20529ee0d04c9
- **短哈希**：2a35e2396
- **日期**：2024-05-27 09:48:58 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.57 to 2.25.60 (#10385)
- **PR/Issue**：#10385

## 总体目的

由 Dependabot 自动发起的依赖版本升级，将 AWS SDK for Java 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 `2.25.57` 升至 `2.25.60`。BOM 用于统一管理 AWS SDK 各子模块（如 S3、DynamoDB、KMS、STS 等）的版本，升级 BOM 即可让所有受其管辖的 AWS SDK 模块同步对齐到新版本，获取最近三个补丁版本带来的缺陷修复与小幅改进，保持依赖栈最新、降低后续累积升级风险。

版本跨度为 `2.25.57 → 2.25.60`，属同一 `2.25.x` 修订线内的补丁级递增（仅 patch 段位 +3），无主版本/次版本变化，API 兼容性预期良好。

## 如何达成设计目的

通过修改 Gradle 版本目录（Version Catalog）文件 `gradle/libs.versions.toml`，将 `awssdk-bom` 版本字面量从 `"2.25.57"` 改为 `"2.25.60"`。Gradle 在解析时会把该版本号代入所有以 `awssdk-bom` 为版本引用的依赖与 BOM 约束，下游模块（如 `bundled-s3`、`aws-bundle`、S3AccessGrants 等）无需逐个修改即可自动获取新版本。此为单点配置改动，符合版本目录集中管理依赖版本的设计。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `awssdk-bom` 版本号从 `2.25.57` 升级到 `2.25.60`。

**工作逻辑**：在 `[versions]` 节中，原行 `awssdk-bom = "2.25.57"` 修改为 `awssdk-bom = "2.25.60"`。该 key 在版本目录中被各处 `${libs.versions.awssdk-bom}` 引用，升级后所有引用处自动指向新版本。值得注意的是，紧邻的 `awssdk-s3accessgrants = "2.0.0"` 等独立版本号不在本次升级范围内（其版本线独立）。

**变更前**：
```toml
awssdk-bom = "2.25.57"
```

**变更后**：
```toml
awssdk-bom = "2.25.60"
```

统计：1 file changed, 1 insertion(+), 1 deletion(-)。

## 小结

- **成效**：将 AWS SDK BOM 升至 2.25.60，对齐上游最新补丁，获取缺陷修复；通过 BOM 机制一处变更即覆盖全部 AWS SDK 子模块，升级成本低、收益面广。
- **影响范围**：仅依赖版本配置改动，无源码、API 改动。影响所有依赖 `awssdk-bom` 的模块（S3、KMS、STS、DynamoDB、S3AccessGrants 等）的构建产物依赖版本；运行时行为预期无破坏性变化。
- **回迁注意事项**：回迁到 1.4.x 分支无障碍，仅需修改同一行 `awssdk-bom` 版本号。需确认 1.4.x 分支上的 `awssdk-s3accessgrants` 版本号是否仍为 `2.0.0`（若 1.4.x 分支尚未引入 S3AccessGrants，对应行可忽略）。AWS SDK 2.25.x 系列对 Java 8/11 兼容，与 1.4.x 的 JDK 基线无冲突。建议回迁后执行一次构建验证 AWS 相关模块（如 `aws-bundle`、`s3`）能正常拉取新版本。
