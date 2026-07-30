# 提交 1492：Build: Bump nessie from 0.101.0 to 0.101.2 (#11791)

## 提交信息

- **序号**：1492 / 4088
- **哈希**：fd739b32b4713370218cfd8f46ad525bc8c203f1
- **短哈希**：fd739b32b
- **日期**：2024-12-15（Sun Dec 15 15:42:53 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump nessie from 0.101.0 to 0.101.2 (#11791)
- **PR/Issue**：#11791

## 总体目的

本提交由 Dependabot 自动生成，将 Apache Iceberg 仓库所依赖的 Nessie 版本从 0.101.0 升级到 0.101.2（语义化版本补丁升级）。Nessie 是一个提供"事务型目录"（transactional catalog）能力的开源项目，Iceberg 在测试与部分实现中使用以下四个 Nessie 制品：

- `org.projectnessie.nessie:nessie-client`
- `org.projectnessie.nessie:nessie-jaxrs-testextension`
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`

升级补丁版本通常是为了获得 bug 修复、安全补丁或小的兼容性改进，避免长期落后而积压技术债。由于这四个制品在 Iceberg 中都标记为 `direct:production`，且升级类型为 `version-update:semver-patch`，按语义化版本约定应保持向后兼容。

## 如何达成设计目的

直接修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `nessie` 版本变量的值由 `0.101.0` 改为 `0.101.2`。这是单一变量修改，所有引用该变量的依赖都会自动同步升级，无需修改构建脚本逻辑。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 依赖版本统一从 0.101.0 升级到 0.101.2。

**工作逻辑**：版本目录（Version Catalog）是 Gradle 推荐的依赖集中管理方式。本文件中以 `nessie = "..."` 形式声明版本号，构建脚本中通过 `libs.nessie` 等访问器引用。仅修改这一行即可让上述四个 Nessie 制品全部升级。

```toml
- nessie = "0.101.0"
+ nessie = "0.101.2"
```

## 小结

- **成效**：Nessie 依赖升级到 0.101.2，获得补丁版本的修复与改进；版本集中管理使升级仅需一处改动。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行变更。无源代码逻辑改动，影响的是构建期与测试期依赖。
- **回迁到 1.4.x 的注意事项**：这是依赖版本升级，本身无功能变更。1.4.x 作为维护分支，原则上应保持依赖稳定，仅在安全或必要兼容性问题时才回迁此类补丁升级。**一般情况下无需回迁**；若 1.4.x 的 CI 因 Nessie 旧版本存在已知 bug 而失败，则可考虑回迁此升级，但需注意 Nessie 0.101.x 与 1.4.x 时期 Iceberg 代码的兼容性（Nessie 客户端 API 在补丁版本内应保持兼容）。回迁后应运行 Nessie 相关测试套件确认无回归。
