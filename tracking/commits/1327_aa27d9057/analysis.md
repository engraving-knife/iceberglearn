# 提交 1327：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#11450)

## 提交信息

- **序号**：1327 / 4088
- **哈希**：aa27d9057d7471e4ca9d1e03e20d9cbbf0c87890
- **短哈希**：aa27d9057
- **日期**：2024-11-04（Mon Nov 4 08:50:56 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#11450)
- **PR/Issue**：#11450

## 总体目的

由 Dependabot 自动发起的依赖版本升级：将 Apache HttpComponents Client 5（`org.apache.httpcomponents.client5:httpclient5`）从 `5.4` 升级到 `5.4.1`，属 patch 升级。Iceberg 在部分需要 HTTP 客户端的集成场景（如通过 AWS SDK 的 Apache HTTP 客户端、部分 catalog 通信）中使用该依赖。升级目的是获取 5.4.1 中 bug 修复与安全补丁。

Dependabot 标注 `update-type: version-update:semver-patch`，属低风险升级。

## 如何达成设计目的

只修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `httpcomponents-httpclient5` 这一个版本键的值。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 HttpComponents httpclient5 版本号。

**工作逻辑**：将第 51 行附近的版本声明由

```toml
httpcomponents-httpclient5 = "5.4"
```

改为

```toml
httpcomponents-httpclient5 = "5.4.1"
```

其他版本键保持不变。所有引用 `httpcomponents-httpclient5` 的模块在依赖解析时使用 5.4.1 版本。

## 小结

- **成效**：Apache HttpComponents httpclient5 升级至 5.4.1，获取 patch 修复（通常包含 bug 修复与安全补丁）。属依赖维护性升级。
- **影响范围**：仅 1 个文件、1 行版本号变更。运行时影响取决于 5.4→5.4.1 之间的具体改动；patch 升级理论上向后兼容。
- **回迁到 1.4.x 的注意事项**：**视情况可选回迁**。1.4.x 同样使用 `libs.versions.toml` 管理该依赖。patch 级升级风险低，若 1.4.x 当前 httpclient5 版本为 5.4 或更低且回迁能获取相关修复，则可回迁。回迁前应确认 1.4.x 当前版本是否已高于 5.4.1，并验证相关集成测试通过。若 1.4.x 无 HTTP 客户端相关 bug，可不必回迁。
