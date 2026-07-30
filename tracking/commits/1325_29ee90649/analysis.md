# 提交 1325：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.3 to 8.3.5 (#11452)

## 提交信息

- **序号**：1325 / 4088
- **哈希**：29ee906496d840785f814f1aee99eb0e0767f0ae
- **短哈希**：29ee90649
- **日期**：2024-11-04（Mon Nov 4 08:50:16 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.3 to 8.3.5 (#11452)
- **PR/Issue**：#11452

## 总体目的

由 Dependabot 自动发起的 Gradle 插件版本升级：将 Shadow 插件（`com.gradleup.shadow:shadow-gradle-plugin`）从 `8.3.3` 升级到 `8.3.5`，跨越 2 个 patch 版本。Shadow 是 Gradle 生态中用于生成 fat/uber JAR（将依赖打入同一 JAR）的主流插件，Iceberg 在 `aws-bundle`、`gcp-bundle`、`azure-bundle`、`aliyun-bundle`、`delta-storage` 等需要发布"打包依赖"模块的场景使用它来生成 shaded JAR。升级目的是获取 8.3.4 与 8.3.5 中可能的 bug 修复与改进。

Dependabot 标注 `update-type: version-update:semver-patch`，属低风险升级。

## 如何达成设计目的

只修改根 `build.gradle` 的 `buildscript.dependencies` 块中 Shadow 插件的 classpath 依赖版本字符串。Shadow 插件通过 `buildscript` classpath 加载（而非通过版本目录），因此直接改 `build.gradle` 即可。

## 修改详情

### `build.gradle`

**修改目的**：升级 Shadow 插件版本号。

**工作逻辑**：在 `buildscript { dependencies { ... } }` 块中，将

```groovy
classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.3'
```

改为

```groovy
classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.5'
```

其他 classpath 依赖（`gradle-baseline-java`、`spotless-plugin-gradle`、`gradle-processors` 等）保持不变。升级后所有应用 `com.gradleup.shadow` 插件的子模块在执行 `shadowJar` 任务时使用 8.3.5 版本的插件逻辑。

## 小结

- **成效**：Shadow 插件升级至 8.3.5，获取 8.3.4/8.3.5 的 patch 修复。属构建工具链维护性升级，不影响 Iceberg 运行时行为，仅影响 shaded JAR 的打包过程。
- **影响范围**：仅 1 个文件、1 行版本号变更。下游影响限于 `shadowJar` 任务的执行，可能影响 shaded JAR 的内部结构（如依赖 relocation、META-INF 合并等），但 patch 升级通常保持兼容。
- **回迁到 1.4.x 的注意事项**：**视情况可选回迁**。1.4.x 同样使用 Shadow 插件生成 bundled JAR（如 `aws-bundle`），若 1.4.x 当前 Shadow 版本为 8.3.3 或更低且回迁能修复打包相关问题，则可回迁。若 1.4.x 已使用更高版本或无打包问题，则无需回迁。回迁前应确认 1.4.x 的 `build.gradle` 该行格式与 main 一致（直接改版本字符串即可），并执行 `shadowJar` 任务验证产物正常。
