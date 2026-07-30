# 提交 1474：Build: Bump nessie from 0.100.2 to 0.101.0 (#11722)

## 提交信息

- **序号**：1474 / 4088
- **哈希**：70d87f1750627b14b3b25a0216a97db86a786992
- **短哈希**：70d87f175
- **日期**：2024-12-09（Mon Dec 9 14:45:16 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.100.2 to 0.101.0 (#11722)
- **PR/Issue**：#11722

## 总体目的

Apache Iceberg 的 `nessie` 模块为 ProjectNessie 提供了 `NessieCatalog` 实现，作为 Iceberg 表与视图的版本化存储后端。Iceberg 通过 Gradle 版本目录 `gradle/libs.versions.toml` 统一管理 Nessie 多个 artifact 的版本，包括：
- `org.projectnessie.nessie:nessie-client`（生产依赖，运行时与 Nessie Server 通信）
- `org.projectnessie.nessie:nessie-jaxrs-testextension`（测试依赖，用于在测试中启动 Nessie REST 服务）
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`（测试依赖，内存存储测试扩展）
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`（测试依赖，存储扩展）

这些 artifact 共享同一个版本号 `nessie`，由 Dependabot 自动跟踪。本提交把 Nessie 从 0.100.2 升级到 0.101.0（minor 版本升级）。

minor 版本升级意味着 Nessie 可能引入新特性或对 API 进行小幅调整，但通常不会有大面积破坏性变更。对 Iceberg 来说，需要确保 `NessieCatalog` 及其测试在升级后仍能正常工作。

## 如何达成设计目的

直接修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，把 `nessie` 别名的版本字符串从 `"0.100.2"` 改为 `"0.101.0"`。所有引用 `version.ref = "nessie"` 的 Nessie artifact 都会跟随升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Nessie 版本。

**工作逻辑**：版本目录文件中第 73 行：

```toml
-nessie = "0.100.2"
+nessie = "0.101.0"
```

`nessie` 是版本别名，被 `[libraries]` 区块中的 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 四个库通过 `version.ref = "nessie"` 统一引用。修改一处即可同步升级全部 4 个 artifact。

本次升级属 semver 的 minor 升级（0.100.x → 0.101.x），上游 Nessie 0.101.0 可能新增 API 或调整现有 API。提交说明中 Dependabot 标注 `update-type: version-update:semver-minor`，相比 patch 升级风险略高，但 Nessie 项目本身承诺 minor 版本内尽量保持向后兼容。

## 小结

- **成效**：Nessie 由 0.100.2 升至 0.101.0，Iceberg `nessie` 模块同步升级，跟随上游获得新特性与修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，单行改动；实际影响范围取决于 0.101.0 相对 0.100.2 的 API 变更，理论上需运行 `nessie` 模块测试确认。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支使用的 Nessie 版本通常较旧（例如 0.92.x 系列）。本提交是 main 分支的常规升级，1.4.x 不需要直接回迁到 0.101.0——若回迁，反而可能因 1.4.x 的 `NessieCatalog` 代码未跟上 Nessie 0.101.x 的 API 变更而引入编译或运行错误。建议 1.4.x 沿用自己的 Dependabot 升级路径，**通常无需回迁**。
