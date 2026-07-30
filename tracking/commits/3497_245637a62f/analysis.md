# 提交 3497：Build: Add runtime dependency guard for bundled artifacts (#15855)

## 提交信息

- **序号**：3497 / 4088
- **哈希**：245637a62fd1fac1936c43bcb39cb72872f58768
- **短哈希**：245637a62f
- **日期**：2026-04-01 16:43:16 -0700
- **作者**：Russell Spitzer
- **提交说明**：Build: Add runtime dependency guard for bundled artifacts (#15855)
- **PR/Issue**：#15855

## 总体目的

新增构建时检查机制，防止意外的传递依赖泄漏到打包的 shadow JAR 和分发包中。新增一个 check-in 的 `runtime-deps.txt` 基线文件，列出每个 bundled artifact 解析到的所有依赖。`checkRuntimeDeps` 任务将解析的依赖与基线比较，不匹配时构建失败并显示清晰的 diff。该任务挂载到 check 生命周期，在 CI 中自动运行。

覆盖 11 个 bundled 模块：Spark runtime（3.4, 3.5, 4.0, 4.1）、Flink runtime（1.20, 2.0, 2.1）、cloud bundles（AWS, Azure, GCP）和 Kafka Connect runtime。

## 如何达成设计目的

1. 新建 `runtime-deps.gradle` 脚本，提供两个任务：
   - `generateRuntimeDeps`：解析 runtimeClasspath 并写入排序后的基线文件。
   - `checkRuntimeDeps`：比较解析的依赖与基线，patch 级别版本变化被忽略（不影响 Dependabot 升级）。
2. 在 11 个 bundled 模块的 build.gradle 中 apply 该脚本。
3. 在根 build.gradle 中注册 `checkAllRuntimeDeps` 聚合任务。
4. 在 java-ci.yml 中新增 `check-runtime-deps` job 运行该检查。
5. 在 `.rat-excludes` 中排除 `runtime-deps.txt`。

## 修改详情

### `runtime-deps.gradle` (+130 lines, 新文件)

**修改目的**：核心检查脚本。

**工作逻辑**：
- `resolveRuntimeDeps` 闭包：解析 runtimeClasspath 的 resolvedArtifacts，收集 `group:artifact:version` 坐标，排除 `org.apache.iceberg:` 前缀的依赖，排序去重。
- `generateRuntimeDeps` 任务：将解析结果写入 `runtime-deps.txt`，用于有意的依赖变更后更新基线。
- `checkRuntimeDeps` 任务：
  - 读取基线文件和实际依赖。
  - 按 `group:artifact` 分组比较，忽略 patch 级别版本差异（仅比较 major.minor）。
  - 检测三类差异：新增（added）、移除（removed）、版本变更（versionChanged，major.minor 不同）。
  - 有差异时抛出 GradleException，显示 diff 并提示更新命令。
- 挂载到 `check.dependsOn checkRuntimeDeps`。

### `build.gradle` (+9 lines)

**修改目的**：注册聚合任务。

**工作逻辑**：
```gradle
tasks.register('checkAllRuntimeDeps') {
  description = 'Validates runtime dependency baselines for all subprojects that have them'
  group = 'verification'
  dependsOn subprojects.collect { subproject ->
    subproject.tasks.matching { it.name == 'checkRuntimeDeps' }
  }
}
```

### 11 个 bundled 模块的 build.gradle (各 +2 lines)

**修改目的**：应用 runtime-deps 脚本。

**工作逻辑**：在 aws-bundle、azure-bundle、gcp-bundle、flink v1.20/v2.0/v2.1 runtime、spark v3.4/v3.5/v4.0/v4.1 runtime、kafka-connect runtime 的 build.gradle 中添加 `apply from: "${rootDir}/runtime-deps.gradle"`。

### `.github/workflows/java-ci.yml` (+13 lines)

**修改目的**：在 CI 中运行检查。

**工作逻辑**：新增 `check-runtime-deps` job，运行在 ubuntu-24.04，执行 `./gradlew checkAllRuntimeDeps -q`。

### `dev/.rat-excludes` (+1 line)

**修改目的**：排除基线文件不参与 Apache RAT 许可证检查。

## 总结

构建基础设施增强提交，新增运行时依赖守卫机制防止依赖泄漏。核心是 `runtime-deps.gradle` 脚本，提供基线生成和检查任务，忽略 patch 级版本变更以兼容 Dependabot。覆盖全部 11 个 bundled 模块，集成到 CI 的 check 生命周期中自动运行。这是防止如 #15655（BigQuery 依赖泄漏）和 #15858（Aliyun 依赖泄漏）等问题的关键防护措施。
