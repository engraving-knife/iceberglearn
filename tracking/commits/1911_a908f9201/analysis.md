# 提交 1911：Build: Bump com.google.errorprone:error_prone_annotations from 2.36.0 to 2.37.0 (#12622)

## 提交信息

- **序号**：1911 / 4088
- **哈希**：a908f920178f6a621f9418ffd455fa101cba92ab
- **短哈希**：a908f9201
- **日期**：2025-03-24 11:16:21 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.errorprone:error_prone_annotations from 2.36.0 to 2.37.0 (#12622)
- **PR/Issue**：#12622

## 总体目的

这是由 dependabot 自动生成的依赖升级提交，把 `com.google.errorprone:error_prone_annotations` 从 2.36.0 升到 2.37.0。该 artifact 提供 Error Prone 静态分析工具使用的注解（如 `@CanIgnoreReturnValue`、`@CheckReturnValue`、`@CompatibleWith` 等），Iceberg 在编译期通过它来增强空指针/资源管理等检查。

升级为 semver minor（次版本号）升级，通常包含新检查规则与缺陷修复，API 兼容。除了更新版本目录与若干 LICENSE 文件中记录的依赖版本号外，不涉及代码逻辑变更。

## 如何达成设计目的

1. 在 `gradle/libs.versions.toml` 中把 `errorprone-annotations` 从 `"2.36.0"` 改为 `"2.37.0"`，作为版本单一来源。
2. 同步更新打包到分发包里的 LICENSE 文件中登记的依赖版本号（`gcp-bundle/LICENSE`、`kafka-connect-runtime/hive/LICENSE`、`kafka-connect-runtime/main/LICENSE`），保持许可证清单与实际依赖版本一致。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 error_prone_annotations 版本声明。

**工作逻辑**：

```toml
-errorprone-annotations = "2.36.0"
+errorprone-annotations = "2.37.0"
```

### `gcp-bundle/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 GCP bundle 中登记的 error_prone_annotations 版本号到 2.37.0。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 kafka-connect hive runtime bundle 中登记的版本号到 2.37.0。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 kafka-connect main runtime bundle 中登记的版本号到 2.37.0。

## 总结

dependabot 自动把 `error_prone_annotations` 从 2.36.0 升级到 2.37.0，仅修改版本目录与 LICENSE 文件中的版本登记，无业务代码改动。
