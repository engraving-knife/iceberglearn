# 提交 1324：Build: Bump software.amazon.awssdk:bom from 2.29.1 to 2.29.6 (#11454)

## 提交信息

- **序号**：1324 / 4088
- **哈希**：e47fa6a581f1396c35b13ead228e886003c9dfdf
- **短哈希**：e47fa6a58
- **日期**：2024-11-04（Mon Nov 4 08:49:46 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.1 to 2.29.6 (#11454)
- **PR/Issue**：#11454

## 总体目的

由 Dependabot 自动发起的依赖版本升级：将 AWS SDK for Java 的 BOM（`software.amazon.awssdk:bom`）从 `2.29.1` 升级到 `2.29.6`，跨越 5 个 patch 版本。Iceberg 在 S3 集成（`aws-bundle`、`aws` 模块）以及 S3 Access Grants 等场景使用 AWS SDK，统一通过 BOM 管理所有 AWS SDK 模块的版本一致性。Dependabot 在检测到上游发布新版本后自动提交 PR，目的是及时获取 bug 修复与安全补丁，同时避免各 AWS SDK 子模块版本漂移。

提交说明中标注 `update-type: version-update:semver-patch`，即语义化版本的 patch 升级，按 Dependabot 策略属于低风险变更。

## 如何达成设计目的

只修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `awssdk-bom` 这一个版本键的值。Iceberg 使用 Gradle 的版本目录（Version Catalog）集中管理依赖版本，所有引用 `awssdk-bom` 的位置（如 `aws-bundle/build.gradle`、`build.gradle` 中的 `platform("software.amazon.awssdk:bom:${libs.versions.awssdk.bom.get()}")` 等）会自动解析到新版本，无需逐处修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：将第 32 行附近的版本声明由

```toml
awssdk-bom = "2.29.1"
```

改为

```toml
awssdk-bom = "2.29.6"
```

文件中其他 AWS 相关版本键（如 `awssdk-s3accessgrants = "2.3.0"`）保持不变。BOM 升级后，所有通过 `software.amazon.awssdk:bom` 导入的子模块（如 `s3`、`sts`、`apache-client`、`netty-nio-client` 等）会在依赖解析时统一使用 2.29.6 版本。

## 小结

- **成效**：AWS SDK for Java BOM 升级至 2.29.6，覆盖 2.29.1 至 2.29.6 之间的 patch 修复（通常包含 bug 修复与小改进）。属于典型的依赖维护性升级，无功能改动。
- **影响范围**：仅 1 个文件、1 行版本号变更。运行时影响取决于 2.29.1→2.29.6 之间 AWS SDK 的具体改动；对 Iceberg 而言主要影响 S3 相关模块的依赖解析。
- **回迁到 1.4.x 的注意事项**：**视情况可选回迁**。1.4.x 同样使用 `gradle/libs.versions.toml` 管理 AWS SDK 版本，且 1.4.x 发布周期内 AWS SDK 仍可能发布安全补丁。回迁此类 patch 级 BOM 升级通常风险低、收益是获取最新 bug 修复。但需注意：1.4.x 分支的 `libs.versions.toml` 可能已与 main 分支版本不同步，回迁前应确认 1.4.x 当前 AWS SDK 版本是否已高于 2.29.6（若是则无需回迁），并验证 S3 集成模块测试通过。若 1.4.x 已停止主动维护依赖升级，可不必回迁。
