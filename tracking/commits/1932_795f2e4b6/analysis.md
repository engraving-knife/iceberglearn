# 提交 1932：Build: Revert AWS SDK from 2.30.31 to 2.29.52 (#12649)

## 提交信息

- **序号**：1932 / 4088
- **哈希**：795f2e4b677c1c3cf3e33e639296928bf86c4af9
- **短哈希**：795f2e4b6
- **日期**：2025-03-28 07:53:35 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Build: Revert AWS SDK from 2.30.31 to 2.29.52 (#12649)
- **PR/Issue**：#12649

## 总体目的

此提交将 Iceberg 项目使用的 AWS SDK for Java 2.x 从 2.30.31 回退到 2.29.52。这表明此前升级到 2.30.31（应该是更早的某次 Dependabot/人工升级）引入了问题，需要紧急回退到已知稳定的 2.29.52 版本。

回退 AWS SDK 这种核心依赖通常是因为新版本引入了破坏性变更、回归 bug、与项目其他依赖的兼容性问题，或者引发了测试失败。AWS SDK 2.30.x 相对 2.29.x 跨越了多个小版本，可能存在 API 行为变化、传递依赖冲突或运行时问题。回退是降低风险的最快手段，待问题在新版本中修复后再重新升级。

提交说明中标记为 "Revert"，表明这是一个保守的回滚操作，目的是恢复仓库到一个已验证可用的 AWS SDK 版本，保证构建和测试的稳定性。

## 如何达成设计目的

设计思路与依赖升级对称：在 `gradle/libs.versions.toml` 中将 `awssdk-bom` 版本字符串从 `2.30.31` 改回 `2.29.52`，所有引用该 BOM 的 AWS SDK 制品版本会随之回退。同时同步刷新各打包模块（aws-bundle、kafka-connect-runtime 的 hive 与 main 子模块）的 LICENSE/NOTICE 文件中第三方许可证清单，使其反映回退后的依赖版本。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：回退 AWS SDK BOM 版本。

**工作逻辑**：将 `[versions]` 区块中的 `awssdk-bom = "2.30.31"` 修改为 `awssdk-bom = "2.29.52"`。该 BOM 控制所有 `software.amazon.awssdk:*` 制品的版本，一次修改即可回退整个 AWS SDK 套件。

### `aws-bundle/LICENSE`、`aws-bundle/NOTICE` (修改)

**修改目的**：同步 aws-bundle 模块打包清单中的第三方依赖版本与许可证文本。

**工作逻辑**：将 LICENSE/NOTICE 中所有 `software.amazon.awssdk:*` 制品的版本号从 `2.30.31` 替换为 `2.29.52`，确保发布物的法律清单与实际依赖一致。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE`、`.../hive/NOTICE`、`.../main/LICENSE`、`.../main/NOTICE` (修改)

**修改目的**：同步 kafka-connect-runtime 模块（hive 与 main 两个打包变体）的第三方依赖清单。

**工作逻辑**：与 aws-bundle 同理，将清单中 AWS SDK 相关条目的版本号统一回退到 `2.29.52`。

## 总结

本次提交将 AWS SDK for Java 2.x 从 2.30.31 回退到 2.29.52，以规避新版本引入的未知问题，恢复到已知稳定版本。核心改动仅为 `gradle/libs.versions.toml` 中的版本字符串修改，并同步刷新 aws-bundle 与 kafka-connect-runtime 的 LICENSE/NOTICE 清单。
