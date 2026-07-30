# 提交 0792：Build: Bump nessie from 0.82.0 to 0.83.2 (#10381)

## 提交信息

- **序号**：0792 / 4088
- **哈希**：580df62d4becd07e18c53bd0df52fe8a094da88c
- **短哈希**：580df62d4
- **日期**：2024-05-27 16:22:12 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.82.0 to 0.83.2 (#10381)
- **PR/Issue**：#10381

## 总体目的

这是一个由 dependabot 自动生成的依赖升级提交，将 Nessie（Projectnessie）版本从 0.82.0 升级到 0.83.2。Nessie 是一个提供 Git 风格版本化数据目录的 Nessie Catalog，Iceberg 通过 Nessie 客户端与之集成，支持多分支/标签的数据湖管理。

本次升级为 semver minor 版本升级（0.82.0 -> 0.83.2），属于功能增强型更新。影响范围包括 4 个 Nessie 组件：

- `org.projectnessie.nessie:nessie-client`：Nessie 客户端库
- `org.projectnessie.nessie:nessie-jaxrs-testextension`：JAX-RS 测试扩展
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`：内存存储测试
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`：版本化存储测试扩展

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `nessie` 的版本号引用，将版本从 `0.82.0` 改为 `0.83.2`。由于所有 Nessie 相关依赖都通过 `version.ref = "nessie"` 引用同一个版本变量，因此只需修改一行即可同步升级所有 4 个 Nessie 组件，保证版本一致性。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 版本从 0.82.0 升级到 0.83.2。

**工作逻辑**：在版本目录的 `[versions]` 段中，将 `nessie = "0.82.0"` 修改为 `nessie = "0.83.2"`。该版本变量被以下 4 个依赖库引用（通过 `version.ref = "nessie"`）：

1. `nessie-client`（line 137）：生产环境使用的 Nessie 客户端库
2. `nessie-jaxrs-testextension`（line 187）：用于测试的 JAX-RS 扩展
3. `nessie-versioned-storage-inmemory`（line 188）：内存存储实现
4. `nessie-versioned-storage-testextension`（line 189）：版本化存储测试扩展

其中 `nessie-client` 是 `direct:production` 类型依赖，其余为测试相关依赖。

## 小结

- **成效**：将 Nessie 依赖升级到 0.83.2，获得最新 bug 修复和功能改进，保持与 Nessie 生态的兼容性。
- **影响范围**：Nessie Catalog 集成模块。`nessie-client` 是生产依赖，影响 Iceberg 通过 Nessie 进行目录操作的功能；其余 3 个组件仅用于测试。由于是 minor 版本升级，API 应保持向后兼容。
- **回迁注意事项**：1.4.x 分支当前 Nessie 版本为 0.71.0，与 0.83.2 之间跨越了多个 minor 版本（0.72-0.83），存在较大版本差距。回迁时需要注意：(1) Nessie 0.83.2 可能引入了新的 API 变更或最低依赖要求；(2) 需要验证与 1.4.x 分支其他依赖（如 Jackson、Guava 等）的兼容性；(3) 建议先在 1.4.x 分支测试 Nessie 集成相关测试是否通过。如果仅需要修复特定 bug，可考虑只升级到必要的中间版本。
