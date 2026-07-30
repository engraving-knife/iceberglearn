# 提交 0416：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9572)

## 提交信息

- **序号**：0416
- **哈希**：54756b6f5c653be2ab271bfbe262e77109bf9608
- **短哈希**：54756b6f5
- **日期**：2024-01-29 09:09:51 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9572)
- **PR/Issue**：#9572

## 总体目的

本提交由 GitHub Dependabot 自动生成，将 Iceberg 项目依赖 `org.apache.httpcomponents.client5:httpclient5` 从 5.2.3 升级到 5.3.1。Apache HttpComponents Client 5.x 是 Java 生态中主流的 HTTP 客户端库，Iceberg 在与云存储（如 S3、Azure Blob、Google Cloud Storage）交互的模块中使用它来处理 HTTP 请求与连接管理。

5.2.3 到 5.3.1 属于次要版本（semver-minor）升级，按照语义化版本规范，次要版本升级应保持向后兼容，主要新增特性与改进。Dependabot 在提交说明中标注 `update-type: version-update:semver-minor`、`dependency-type: direct:production`，表明这是一个直接生产依赖的次要版本升级。次要版本升级相比补丁升级引入的变更更多，可能包含新功能、性能改进与 bug 修复，但仍承诺 API 兼容。对于涉及网络通信的库，及时升级有助于获取安全修复与连接稳定性改进，对 Iceberg 与云存储交互的可靠性有积极意义。

## 如何达成设计目的

实现路径与 assertj-core 升级一致：通过 Gradle 版本目录集中管理依赖版本。本提交将 `gradle/libs.versions.toml` 中 `httpcomponents-httpclient5 = "5.2.3"` 改为 `httpcomponents-httpclient5 = "5.3.1"`。该版本变量在第 143 行被依赖库声明 `httpcomponents-httpclient5 = { module = "org.apache.httpcomponents.client5:httpclient5", version.ref = "httpcomponents-httpclient5" }` 通过 `version.ref` 引用，而 `build.gradle` 中三处（第 352 行 `implementation`、第 492 行 `compileOnly`、第 507 行 `testImplementation`）通过 `libs.httpcomponents.httpclient5` 引用该库，因此一处版本号变更即可让所有相关模块同步升级，无需修改构建脚本或源代码。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：将 Apache HttpComponents Client 5 依赖从 5.2.3 升级到 5.3.1，获取上游次要版本的改进与修复。

**工作逻辑**：`gradle/libs.versions.toml` 是项目的 Gradle 版本目录文件，集中声明所有依赖版本。第 48 行的 `httpcomponents-httpclient5 = "5.3.1"` 是版本变量，第 143 行通过 `version.ref` 引用该变量定义依赖库坐标。`build.gradle` 在多个模块配置中引用 `libs.httpcomponents.httpclient5`：作为 `implementation` 用于运行时依赖、作为 `compileOnly` 用于编译期、作为 `testImplementation` 用于测试。由于版本目录的引用机制，所有引用点会自动解析到新版本 5.3.1。HttpComponents Client 5.3.x 系列保持 API 兼容，因此无需调整调用代码。

## 小结

这是一个典型的 Dependabot 次要版本依赖升级提交，属于项目持续维护的常规动作。与补丁版本升级相比，次要版本升级引入的变更范围更大，但 HttpComponents Client 项目严格遵守语义化版本，5.3.1 对 5.2.3 保持 API 兼容，因此升级风险可控。及时升级 HTTP 客户端库对 Iceberg 这类重度依赖云存储交互的项目尤为重要，能获取连接管理、TLS 处理、重试逻辑等方面的改进与潜在安全修复。提交作者为 dependabot[bot]，体现了项目对自动化依赖管理的重视。
