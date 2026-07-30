# 提交 3545：Docs: Replace deprecated 'compile' with 'implementation' in Gradle snippet (#15921)

## 提交信息

- **序号**：3545 / 4088
- **哈希**：74acec7b3e108f6676243ad5eb8461f24a64c882
- **短哈希**：74acec7b3
- **日期**：2026-04-15 20:14:01 -0700
- **作者**：yadavay-amzn
- **提交说明**：Docs: Replace deprecated 'compile' with 'implementation' in Gradle snippet (#15921)
- **PR/Issue**：#15921（关闭 #15811）

## 总体目的

Iceberg 官方网站「Releases」页面提供了在 Gradle 中添加 Iceberg 依赖的示例代码片段。该片段使用的是 `compile` 配置：
```groovy
dependencies {
  compile 'org.apache.iceberg:iceberg-core:{{ icebergVersion }}'
}
```

`compile` 配置在 Gradle 7 中已被移除（Gradle 4.10 起废弃），使用该配置会导致用户构建失败。正确的现代写法是 `implementation`。Iceberg 项目自身的 `build.gradle` 也已经使用 `implementation`，文档示例与项目实际做法不一致。

本提交将文档中的 `compile` 替换为 `implementation`，与当前 Gradle 约定和 Iceberg 自身构建脚本保持一致。关闭 #15811。

## 如何达成设计目的

直接修改 `site/docs/releases.md` 中 Gradle 代码块的 `compile` 关键字为 `implementation`。

## 修改详情

### `site/docs/releases.md` (+1/-1 lines)

**修改目的**：将 Gradle 依赖示例从废弃的 `compile` 改为 `implementation`。

**工作逻辑**：
```
-  compile 'org.apache.iceberg:iceberg-core:{{ icebergVersion }}'
+  implementation 'org.apache.iceberg:iceberg-core:{{ icebergVersion }}'
```
`{{ icebergVersion }}` 是 mkdocs 模板变量，构建时替换为当前版本号。

## 总结

本提交将 Iceberg 官网 Releases 页面的 Gradle 依赖示例从 Gradle 7 已移除的 `compile` 配置改为现代的 `implementation` 配置，与当前 Gradle 约定和 Iceberg 自身 `build.gradle` 保持一致，避免用户照搬示例导致构建失败。关闭 #15811。
