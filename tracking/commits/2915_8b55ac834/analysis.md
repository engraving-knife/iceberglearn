# 提交 2915：Build: Bump com.google.cloud:libraries-bom from 26.71.0 to 26.72.0 (#14663)

## 提交信息

- **序号**：2915 / 4088
- **哈希**：8b55ac834015ce664f879ecfe1e80a941a994420
- **短哈希**：8b55ac834
- **日期**：2025-11-22 23:26:26 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.google.cloud:libraries-bom from 26.71.0 to 26.72.0
- **PR/Issue**：#14663

## 总体目的

这是 dependabot 自动生成的依赖升级提交。`com.google.cloud:libraries-bom` 是 Google Cloud Libraries 的 BOM（Bill of Materials），统一管理 Google Cloud 各客户端库（如 GCS 存储 SDK）的版本。Iceberg 项目使用 Google Cloud Storage 作为存储后端之一，该 BOM 控制 GCS 相关依赖的版本。此次从 26.71.0 升级到 26.72.0，属于 semver-minor 级别更新，可能包含新功能和 bug 修复。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `google-libraries-bom` 的版本声明完成升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Google Cloud Libraries BOM 从 26.71.0 升级到 26.72.0。

**工作逻辑**：将 `google-libraries-bom = "26.71.0"` 修改为 `google-libraries-bom = "26.72.0"`，minor 级别升级，预期向后兼容。

## 总结

本提交是 Google Cloud Libraries BOM 的 minor 版本升级（26.71.0 到 26.72.0），预期向后兼容，可能包含新功能和 bug 修复。Iceberg 使用 GCS 作为存储后端，保持该 BOM 最新有助于确保与 Google Cloud 服务的兼容性和稳定性。
