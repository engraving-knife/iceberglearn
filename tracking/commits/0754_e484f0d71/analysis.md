# 提交 0754：Build: Bump guava from 33.1.0-jre to 33.2.0-jre (#10271)

## 提交信息

- **序号**：0754 / 4088
- **哈希**：e484f0d71b8ef7164df4f8e6ed16142dfe346fbb
- **短哈希**：e484f0d71
- **日期**：2024-05-11 10:05:23 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump guava from 33.1.0-jre to 33.2.0-jre (#10271)
- **PR/Issue**：#10271

## 总体目的

本提交由 dependabot 自动生成，将项目依赖的 Google Guava 库版本从 33.1.0-jre 升级到 33.2.0-jre。Guava 是 Iceberg 广泛使用的核心工具库（集合、缓存、并发原语、IO 辅助等），Iceberg 同时通过 relocated（重定位打包）方式将 Guava 内嵌到发布构件中以避免与用户环境的 Guava 版本冲突。本次为 semver-minor（次版本）升级，由 dependabot 根据 Guava 官方发布说明与提交历史触发。升级的目的通常是获取 Guava 33.2.0 中包含的缺陷修复、性能改进与小功能增强，同时保持 API 兼容性（minor 版本升级不破坏 API）。

## 如何达成设计目的

Iceberg 使用 Gradle 进行构建，依赖版本统一通过版本目录（version catalog）文件 `gradle/libs.versions.toml` 集中管理。该文件中 `guava = "33.1.0-jre"` 这一行定义了 Guava 的版本别名，所有模块（core、spark、flink 等）及 `guava-testlib` 测试依赖均引用此别名。因此，仅需将该行的版本号字符串从 `33.1.0-jre` 改为 `33.2.0-jre`，即可使整个项目的 Guava 及 guava-testlib 同步升级到 33.2.0-jre。dependabot 的 PR 描述中也明确指出本次同时更新了 `com.google.guava:guava` 与 `com.google.guava:guava-testlib` 两个构件，二者共享同一版本别名。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Guava 版本别名从 33.1.0-jre 升级到 33.2.0-jre。

**工作逻辑**：在 `gradle/libs.versions.toml` 第 45 行附近，将：

```toml
guava = "33.1.0-jre"
```

改为：

```toml
guava = "33.2.0-jre"
```

该别名被项目中所有引用 `libs.guava`（及 `libs.guava.testlib`）的模块共享，因此一处修改即可全局生效。其余依赖（`google-libraries-bom`、`hadoop2`、`hadoop3-client`、`httpcomponents-httpclient5` 等）版本未变。

## 小结

- **成效**：将 Guava 依赖升级到 33.2.0-jre，获取上游 minor 版本的缺陷修复与改进。由于是 semver-minor 升级，API 保持兼容，预期不需要修改任何调用方代码。
- **影响范围**：影响所有模块的 Guava 依赖版本，包括 core 模块的 relocated Guava 打包产物。由于 Iceberg 将 Guava relocate 后内嵌发布，最终用户（依赖 Iceberg 的下游应用）不受 Iceberg 内部 Guava 版本变化的直接影响。但 Iceberg 自身构建与运行时行为可能受 Guava 33.2.0 内部改进影响。
- **回迁注意事项**：此为依赖版本号单行修改，回迁到 1.4.x 分支非常简单。但需注意：
  1. 1.4.x 分支的 `libs.versions.toml` 中 Guava 版本可能本身就是 33.1.0-jre 或更早版本，cherry-pick 时可能无冲突直接应用。
  2. 回迁后需确认 1.4.x 分支的 CI 环境能正常解析并下载 Guava 33.2.0-jre 构件（该版本已于 2024 年发布，Maven Central 可用）。
  3. 若 1.4.x 分支已有其他提交调整了 `libs.versions.toml` 的相邻行，cherry-pick 时可能产生上下文冲突，需手动解决。
  4. Guava 33.2.0 相对 33.1.0 若有 Iceberg 代码依赖的行为微调（如集合工具的边界行为），理论上应跑全量测试验证，但作为 minor 升级风险较低。
