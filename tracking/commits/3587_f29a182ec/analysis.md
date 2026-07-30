# 提交 3587：GCP Bundle: Remove JSR 305 (#16106)

## 提交信息

- **序号**：3587 / 4088
- **哈希**：f29a182eccc9287764a511767a79f741eb4135af
- **短哈希**：f29a182ec
- **日期**：2026-04-24 18:06:49 -0600
- **作者**：Ryan Blue
- **提交说明**：GCP Bundle: Remove JSR 305 (#16106)
- **PR/Issue**：#16106

## 总体目的

该提交从 GCP 捆绑包（gcp-bundle）中移除 JSR 305（`com.google.code.findbugs:jsr305`）依赖。JSR 305 是一个已废弃的规范，用于提供 `@Nullable`、`@Nonnull` 等注解，但由于其长期未维护且存在兼容性问题，社区普遍建议移除。Google 自己的库也在逐步移除对 JSR 305 的依赖。

该提交通过在 `build.gradle` 中排除 `com.google.code.findbugs:jsr305` 的传递依赖，从 `runtime-deps.txt` 基线中移除该依赖，并从 `LICENSE` 文件中移除相关的许可声明，确保 GCP 捆绑包不再包含 JSR 305。

## 如何达成设计目的

在 `gcp-bundle/build.gradle` 的 `implementation` 配置中添加 `exclude` 规则排除 JSR 305，同时更新 `runtime-deps.txt` 和 `LICENSE` 文件。

## 修改详情

### `gcp-bundle/build.gradle` (+6/-0 lines)

**修改目的**：排除 JSR 305 传递依赖。

**工作逻辑**：
```gradle
configurations {
  implementation {
    exclude group: 'com.google.code.findbugs', module: 'jsr305'
  }
}
```
在 `implementation` 配置中排除 `com.google.code.findbugs:jsr305`，防止 Google Cloud Storage 等 GCP 依赖传递引入 JSR 305。

### `gcp-bundle/runtime-deps.txt` (+0/-1 lines)

**修改目的**：从依赖基线中移除 JSR 305。

**工作逻辑**：
移除 `com.google.code.findbugs:jsr305:3.0.2` 条目。

### `gcp-bundle/LICENSE` (+0/-7 lines)

**修改目的**：移除 JSR 305 的许可声明。

**工作逻辑**：
移除 LICENSE 文件中关于 Findbugs jsr305 的许可声明段落（Project URL 和 License 信息）。

## 总结

该提交从 GCP 捆绑包中移除了已废弃的 JSR 305 依赖，减少了不必要的依赖。JSR 305 长期未维护且存在兼容性问题，移除它有助于减少依赖冲突和分发包大小。通过构建配置排除、基线更新和许可清理三方面完成移除。
