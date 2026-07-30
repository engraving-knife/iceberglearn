# 提交 3882：Build: Bump com.google.cloud.gcs.analytics:gcs-analytics-core (#16814)

## 提交信息

- **序号**：3882 / 4088
- **哈希**：624f14c35e5e4106db6cb4df5424b7db5f950a0b
- **短哈希**：624f14c35
- **日期**：2026-06-14 08:53:52 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump com.google.cloud.gcs.analytics:gcs-analytics-core (#16814)
- **PR/Issue**：#16814

## 总体目的

由开发者 Yuya Ebihara 手动触发的依赖升级提交（虽然提交信息格式类似 Dependabot，但作者为真人），将 Google Cloud Storage 分析核心库 `gcs-analytics-core` 从 1.2.3 升级到 1.3.0。该库用于 GCS 存储的访问分析，Iceberg 的 GCP 模块依赖它进行存储分析。

这是 semver-minor 级别的升级，引入了新功能。由于 1.2.x 到 1.3.x 涉及到模块结构变化，升级不仅需要修改版本号，还需要更新运行时依赖清单。

## 如何达成设计目的

通过两处修改完成升级：一是修改 `gradle/libs.versions.toml` 中的版本变量，二是更新 `gcp-bundle/runtime-deps.txt` 运行时依赖清单以反映新版本引入的传递依赖变化。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 gcs-analytics-core 版本变量。

**工作逻辑**：
```diff
-gcs-analytics-core = "1.2.3"
+gcs-analytics-core = "1.3.0"
```

### `gcp-bundle/runtime-deps.txt` (+6/-3 lines)

**修改目的**：更新 GCP bundle 运行时依赖清单以反映 1.3.0 版本的传递依赖变化。

**工作逻辑**：
1. 新增 `com.github.ben-manes.caffeine:caffeine:3.1` 依赖
2. 将 `com.google.cloud.gcs.analytics:client` 从 `1.2` 升级到 `1.3`
3. 新增 `com.google.cloud.gcs.analytics:common:1.3`（1.3 版本拆分出了 common 模块）
4. 将 `com.google.cloud.gcs.analytics:gcs-analytics-core` 从 `1.2` 升级到 `1.3`
5. 新增 `io.opentelemetry:opentelemetry-exporter-logging:1.52` 依赖

这表明 1.3.0 版本重构了模块结构，新增了 `common` 子模块，并引入了 caffeine 缓存和 opentelemetry logging exporter 作为新的传递依赖。

## 总结

将 GCS 分析核心库从 1.2.3 升级到 1.3.0，属于 minor 版本升级。此次升级伴随模块结构变化（新增 common 子模块）和新的传递依赖（caffeine、opentelemetry-exporter-logging），因此需要同时更新版本目录和运行时依赖清单，确保 GCP bundle 包含所有必要的运行时依赖。
