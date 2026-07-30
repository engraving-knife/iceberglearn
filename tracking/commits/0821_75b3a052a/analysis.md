# 提交 0821：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#10468)

## 提交信息

- **序号**：0821 / 4088
- **哈希**：75b3a052a739f85f28fa3e43ee29456aa01da066
- **短哈希**：75b3a052a
- **日期**：2024-06-09 19:17:46 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#10468)
- **PR/Issue**：#10468

## 总体目的

本提交由 Dependabot 自动生成，目的是将构建脚本中的 `com.palantir.gradle.gitversion:gradle-git-version` 插件从 `3.0.0` 升级到 `3.1.0`。这是一个 SemVer 次版本（minor）升级，属于直接生产依赖。

该插件（Palantir gradle-git-version）在 Iceberg 项目中用于从 Git 标签（tag）推导出项目的版本号。Iceberg 在 `build.gradle` 中通过 `gitVersion(prefix: 'apache-iceberg-')` 读取最新以 `apache-iceberg-` 为前缀的 Git 标签，并解析出 `MAJOR.MINOR.PATCH`，随后将 MINOR 版本号加 1、PATCH 置 0 并追加 `-SNAPSHOT` 后缀，作为开发期间的项目版本号（例如标签 `apache-iceberg-1.5.0` 推导出 `1.6.0-SNAPSHOT`）。

## 如何达成设计目的

提交仅修改了 `build.gradle` 中 `buildscript` 块的 classpath 依赖声明，将版本号字符串从 `3.0.0` 改为 `3.1.0`。这是一个最小化变更：

1. **buildscript classpath 声明**：在 `build.gradle` 顶部的 `buildscript { dependencies { classpath ... } }` 块中，将 `com.palantir.gradle.gitversion:gradle-git-version:3.0.0` 更新为 `3.1.0`。这一声明让 Gradle 在构建脚本解析阶段加载该插件的新版本。

2. **插件应用处无需改动**：插件通过 `apply plugin: 'com.palantir.git-version'` 应用，并使用 `gitVersion(prefix: 'apache-iceberg-')` API 调用。由于 3.0.0 到 3.1.0 是次版本升级，API 保持向后兼容，因此调用代码无需任何修改。

## 修改详情

### `build.gradle`

**修改目的**：将 gradle-git-version 插件从 3.0.0 升级到 3.1.0，以获取该版本的改进与修复。

**工作逻辑**：
- 修改位于 `buildscript` 块的第 48 行（升级前为第 47 行附近）。
- 该 classpath 声明是构建脚本的依赖，在 Gradle 配置阶段（configuration phase）被解析。更新版本号后，Gradle 会从仓库拉取 3.1.0 版本的插件 JAR。
- 插件被应用后（`apply plugin: 'com.palantir.git-version'`，位于 try-catch 块中以便在无 `.git` 目录时优雅降级），提供了 `gitVersion()` 方法。
- `getProjectVersion()` 方法（约第 958 行）调用 `gitVersion(prefix: 'apache-iceberg-')` 获取版本字符串，用正则提取主次补丁号，生成形如 `1.6.0-SNAPSHOT` 的开发版本号。若存在 `version.txt` 文件则优先从文件读取版本（发布流程使用）。
- 升级后该版本推导逻辑行为不变，仅插件内部实现得到改进。

## 小结

- **成效**：gradle-git-version 插件升级到 3.1.0，获取了 Palantir 在 3.1.0 版本中对 Git 版本推导逻辑的改进与缺陷修复，保持构建工具链的最新状态。
- **影响范围**：仅影响构建脚本的版本号推导环节（`getProjectVersion()`），不影响任何源代码、运行时行为或对外 API。所有通过 Git 标签推导版本号的构建流程会使用新插件版本。
- **回迁注意事项**：回迁到 1.4.x 分支时，需确认 1.4.x 分支的 `build.gradle` 中该 classpath 依赖的当前版本。若 1.4.x 仍使用 3.0.0，可直接应用此升级。由于是次版本升级且 API 兼容，回迁风险极低。需确保构建环境能访问到该依赖的 Maven 仓库。若 1.4.x 分支的 Gradle 版本较旧，应验证 3.1.0 插件是否兼容该 Gradle 版本。
