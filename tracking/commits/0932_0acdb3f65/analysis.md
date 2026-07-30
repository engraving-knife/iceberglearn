# 提交 0932：Build: Bump com.google.cloud:libraries-bom from 26.28.0 to 26.43.0 (#10699)

## 提交信息

- **序号**：0932 / 4088
- **哈希**：0acdb3f6541605d6f17c0415e6f736060deb15d3
- **短哈希**：0acdb3f65
- **日期**：2024-07-15（Mon Jul 15 08:42:20 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.28.0 to 26.43.0 (#10699)
- **PR/Issue**：#10699

## 总体目的

这是由 Dependabot 自动生成的依赖升级 PR，目的是将 Iceberg 项目所依赖的 Google Cloud Java 客户端库 BOM（Bill of Materials）`com.google.cloud:libraries-bom` 从 `26.28.0` 升级到 `26.43.0`。

`libraries-bom` 是 Google 官方维护的依赖版本清单（BOM），用于统一管理 Google Cloud 各客户端库（如 GCS、BigQuery、Pub/Sub 等的 Java SDK）的版本与传递依赖，避免多个 Google 库之间出现版本冲突。Iceberg 在 `gcp` 模块（Google Cloud Storage 集成）中依赖该 BOM 来拉取 GCS 相关客户端。定期升级 BOM 可以获得 bug 修复、安全补丁以及与新 Google Cloud API 的兼容性。本次升级为 minor 版本升级（26.28.0 → 26.43.0），跨越多个 minor 版本，属于常规的依赖维护。

## 如何达成设计目的

实现方式极简：仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 这一项的版本字符串，由 `26.28.0` 改为 `26.43.0`。所有引用该版本的模块会通过 Gradle 的版本目录机制（version catalog）自动继承新版本，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `google-libraries-bom` 的版本从 26.28.0 升级到 26.43.0，使所有 Google Cloud 客户端库统一使用新版本。

**工作逻辑**：

```diff
-google-libraries-bom = "26.28.0"
+google-libraries-bom = "26.43.0"
```

这是版本目录（TOML 格式）中的一个变量定义。`libs.versions.toml` 中以 `xxx = "version"` 形式定义的条目是版本别名，后续在 `[libraries]`、`[bundles]` 等段落通过 `{ module = "...", version.ref = "google-libraries-bom" }` 引用。改这一行即可让所有依赖此 BOM 的位置（如 `gcs` 模块的 `platform("com.google.cloud:libraries-bom")`）统一升级。

## 小结

- **成效**：将 Google Cloud libraries-bom 由 26.28.0 升级至 26.43.0，引入 Google Cloud 客户端库的 bug 修复与安全更新。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动；影响 `gcp` 模块的 Google Cloud 依赖版本。
- **回迁到 1.4.x 的注意事项**：属于纯依赖升级，技术上可以回迁，但需评估 1.4.x 分支当前 `google-libraries-bom` 版本及与现有 AWS SDK、Hadoop 等依赖的兼容性。1.4.x 维护分支通常也应定期同步依赖升级以获取安全补丁，建议在 1.4.x 上单独验证 GCS 集成测试通过后再合入。若 1.4.x 已有独立的依赖升级策略或版本锁，则按分支自身节奏处理。
