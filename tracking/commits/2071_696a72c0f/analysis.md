# 提交 2071：API, Build: Explicitly pass version into Git Properties Plugin

## 提交信息

- **序号**：2071 / 4088
- **哈希**：696a72c0f88c3af1096e716b196f1609da34e50d
- **短哈希**：696a72c0f
- **日期**：2025-05-02 17:49:23 -0600
- **作者**：Russell Spitzer
- **提交说明**：API, Build: Explicitly pass version into Git Properties Plugin (#12949)
- **PR/Issue**：#12949

## 总体目的

Iceberg 在构建时会使用 Git Properties Plugin（`gitProperties`）生成 `git.properties` 文件，该文件包含构建时的 Git 信息（分支、提交 ID、构建版本等），运行时由 `IcebergBuild.version()` 读取以报告当前 Iceberg 的版本号。此前的构建配置中，Git Properties Plugin 会自动推断构建版本，但在某些场景下推断出的版本不正确——例如在源码发布（Source Release）场景中，构建不在完整的 Git 仓库中进行，或者在 CI 环境中 Git 状态不完整时，`IcebergBuild.version()` 可能返回 "unspecified" 或不正确的版本值。

本提交通过在 `gitProperties` 配置中显式传入 `projectVersion`，确保生成的 `git.properties` 中的 `git.build.version` 始终使用项目实际定义的版本号，而非插件自动推断的版本。同时新增了多个测试用例来验证版本在各种场景下的正确性，包括：版本不为 "unspecified"、版本与系统属性 `project.version` 一致、以及版本与源码发布场景下的 `version.txt` 文件一致。

## 如何达成设计目的

设计思路包含两部分：

1. **构建配置**：在 `build.gradle` 的 `gitProperties` 块中添加 `version = projectVersion`，显式将项目版本传递给 Git Properties Plugin；同时在所有子项目的测试任务中注入 `systemProperty 'project.version', project.version`，使测试可以通过系统属性获取项目版本进行比对验证。

2. **测试验证**：在 `TestIcebergBuild` 中新增三个测试：
   - `testVersionNotUnspecified`：确保版本不为 "unspecified"
   - `testVersionMatchesSystemProperty`：确保 `IcebergBuild.version()` 与系统属性 `project.version` 一致（使用 assumeThat 在属性不存在时跳过）
   - `testVersionMatchesFile`：针对源码发布场景，当存在 `../version.txt` 文件时验证版本一致（使用 assumeThat 在文件不存在时跳过）

## 修改详情

### `api/src/test/java/org/apache/iceberg/TestIcebergBuild.java` (修改, +32/-0 lines)

**修改目的**：新增测试用例验证 `IcebergBuild.version()` 在不同构建场景下返回正确的版本值。

**工作逻辑**：
- 新增 `assumeThat`、`IOException`、`Path`、`Paths` 的 import。
- `testVersionNotUnspecified`：断言 `IcebergBuild.version()` 不等于 "unspecified"，确保版本总是被正确设置。
- `testVersionMatchesSystemProperty`：先通过 `assumeThat` 假设系统属性 `project.version` 存在（不存在则跳过测试），然后断言 `IcebergBuild.version()` 等于该系统属性值。
- `testVersionMatchesFile`：针对源码发布场景设计，假设 `../version.txt` 文件存在（不存在则跳过），读取文件内容并断言 `IcebergBuild.version()` 等于文件中的版本字符串。使用 `assumeThat` 确保该测试只在源码发布场景下运行。

### `build.gradle` (修改, +3/-0 lines)

**修改目的**：显式向 Git Properties Plugin 传递版本号，并在测试中注入版本系统属性。

**工作逻辑**：
- 在 `gitProperties` 块中添加 `version = projectVersion`，使插件使用项目定义的版本号而非自动推断。
- 在 `subprojects` 块的测试配置中添加 `systemProperty 'project.version', project.version`，将项目版本作为系统属性注入到所有子项目的测试 JVM 中，供 `testVersionMatchesSystemProperty` 测试使用。

## 总结

本提交通过在 `build.gradle` 中显式向 Git Properties Plugin 传递 `projectVersion`，修复了构建版本在源码发布等场景下可能不正确的问题，并新增三个测试用例覆盖版本验证的不同场景（非 unspecified、与系统属性一致、与 version.txt 文件一致），提升了版本报告的可靠性。
