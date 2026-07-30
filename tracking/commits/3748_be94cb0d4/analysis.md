# 提交 3748：Build: Use major.minor versions in runtime-deps baselines (#16233)

## 提交信息

- **序号**：3748 / 4088
- **哈希**：be94cb0d4cbdb317aeeaafba4302afeeaa50fa5d
- **短哈希**：be94cb0d4
- **日期**：2026-05-19 17:07:23 -0700
- **作者**：Kevin Liu
- **提交说明**：Build: Use major.minor versions in runtime-deps baselines (#16233)
- **PR/Issue**：#16233

## 总体目的

本提交改进了 Iceberg 的运行时依赖基线（runtime-deps baseline）机制，将基线文件中记录的依赖版本从完整版本号（major.minor.patch）改为仅保留 major.minor 版本号，从而消除日常 Dependabot patch 版本升级导致的基线漂移问题。

Iceberg 为多个 bundle/runtime 模块（aws-bundle、azure-bundle、gcp-bundle、flink-runtime、spark-runtime、kafka-connect-runtime）维护了 `runtime-deps.txt` 基线文件，记录该模块运行时类路径上所有传递依赖的 `group:artifact:version` 坐标。CI 中的 `checkRuntimeDeps` 任务会将实际解析的依赖与基线对比，检测意外的依赖变更。

原先基线文件记录完整版本号（如 `2.9.3`），而 `checkRuntimeDeps` 在比较时忽略 patch 级别差异。这意味着每次 Dependabot 升级某个依赖的 patch 版本时，基线文件中的版本号会变得过时，虽然检查不会失败（因为比较时忽略 patch），但基线文件本身会与实际版本不一致，造成混淆。更糟糕的是，如果 `generateRuntimeDeps` 重新生成基线，会产生大量仅 patch 版本不同的 diff，增加 PR 噪音。

本提交将基线文件本身直接记录 major.minor 版本（如 `2.9`），这样 Dependabot 的 patch 升级不会导致基线文件变化，基线始终与实际保持一致，减少维护负担和 PR 噪音。

## 如何达成设计目的

修改 `runtime-deps.gradle` 中的 `resolveRuntimeDeps` 闭包，在生成坐标时通过新增的 `majorMinor` 函数将版本号截断为 `major.minor` 形式。同时简化 `checkRuntimeDeps` 任务的比较逻辑：由于基线已经是 major.minor 形式，比较时直接对比完整坐标字符串，不再需要原先的 `majorMinor` 转换。然后重新生成所有 11 个模块的 `runtime-deps.txt` 基线文件，将版本号从完整形式改为 major.minor 形式。

## 修改详情

### `runtime-deps.gradle` (+14/-12 lines)

**修改目的**：在生成基线时将版本号截断为 major.minor，简化比较逻辑。

**工作逻辑**：
- 新增 `majorMinor` 闭包，将版本字符串按 `.` 分割取前两部分：
```groovy
def majorMinor = { version ->
  def parts = version.split('\\.')
  parts.length >= 2 ? "${parts[0]}.${parts[1]}" : version
}
```
- `resolveRuntimeDeps` 闭包中，生成坐标时应用 `majorMinor`：
```groovy
.collect {
  def id = it.moduleVersion.id
  "${id.group}:${id.name}:${majorMinor(id.version)}"
}
```
  原先生成 `group:artifact:fullVersion`，现在生成 `group:artifact:major.minor`。
- 更新文件头注释，说明基线现在使用 major.minor 坐标，patch 版本被省略以避免 Dependabot 升级导致基线漂移。
- `checkRuntimeDeps` 任务中，移除原先用于比较时转换的 `majorMinor` 闭包，因为基线已是 major.minor 形式。`versionChanged` 比较改为直接对比完整坐标字符串：
```groovy
def versionChanged = shared.findAll {
  actualByModule[it] != expectedByModule[it]
}
```
  原先为 `majorMinor(actualByModule[it]) != majorMinor(expectedByModule[it])`。

### 各模块 `runtime-deps.txt` 基线文件（11 个文件，共 +680/-680 lines）

**修改目的**：重新生成基线，将版本号从完整形式改为 major.minor 形式。

**工作逻辑**：
对所有 11 个模块的 `runtime-deps.txt` 重新生成，将每个依赖坐标的版本号从 `major.minor.patch`（或更长）截断为 `major.minor`。例如：
- `com.github.ben-manes.caffeine:caffeine:2.9.3` → `com.github.ben-manes.caffeine:caffeine:2.9`
- `commons-codec:commons-codec:1.17.1` → `commons-codec:commons-codec:1.17`
- `io.netty:netty-buffer:4.2.13.Final` → `io.netty:netty-buffer:4.2`
- `software.amazon.awssdk:annotations:2.44.4` → `software.amazon.awssdk:annotations:2.44`

涉及文件：
- `aws-bundle/runtime-deps.txt`
- `azure-bundle/runtime-deps.txt`
- `gcp-bundle/runtime-deps.txt`
- `flink/v1.20/flink-runtime/runtime-deps.txt`
- `flink/v2.0/flink-runtime/runtime-deps.txt`
- `flink/v2.1/flink-runtime/runtime-deps.txt`
- `kafka-connect/kafka-connect-runtime/runtime-deps.txt`
- `spark/v3.4/spark-runtime/runtime-deps.txt`
- `spark/v3.5/spark-runtime/runtime-deps.txt`
- `spark/v4.0/spark-runtime/runtime-deps.txt`
- `spark/v4.1/spark-runtime/runtime-deps.txt`

## 总结

本提交改进了运行时依赖基线机制，将基线文件记录的版本号从完整版本（major.minor.patch）改为 major.minor 形式，使 Dependabot 的 patch 版本升级不再导致基线文件变化，消除基线漂移和 PR 噪音。通过在生成时截断版本号并简化比较逻辑，基线文件始终与实际依赖保持一致，减少了维护负担。改动涉及 Gradle 脚本逻辑调整和 11 个基线文件的重新生成。这是 CI/构建基础设施的优化提交。
