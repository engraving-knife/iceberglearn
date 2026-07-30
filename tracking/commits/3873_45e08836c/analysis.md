# 提交分析：3873 - Build: Bump jetty

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 3873 |
| 短哈希 | 45e08836c |
| 完整哈希 | 45e08836c12a00ec8c4f7d6a32f6489f7574e6d8 |
| 日期 | 2026-06-14 00:06:19 -0700 |
| 作者 | dependabot[bot] |
| 提交说明 | Build: Bump jetty from 12.1.9 to 12.1.10 (#16813) |

## 总体目的

将 Jetty 从 12.1.9 升级到 12.1.10（补丁版本升级）。

## 修改详情

### 文件路径: `gradle/libs.versions.toml`

```diff
-jetty = "12.1.9"
+jetty = "12.1.10"
```

此版本变量控制以下三个 Jetty 组件的版本：
- `org.eclipse.jetty.compression:jetty-compression-server`
- `org.eclipse.jetty.compression:jetty-compression-gzip`
- `org.eclipse.jetty.ee10:jetty-ee10-servlet`

## 依赖升级类提交说明

此提交属于依赖升级类，由 Dependabot 自动生成。升级的是 Jetty HTTP 服务器及相关组件，从 12.1.9 升级到 12.1.10，这是一个补丁版本升级（semver-patch），通常包含 bug 修复和安全补丁。

## 总结

常规的 Jetty 补丁版本升级，通过 Gradle 版本目录统一管理版本号。
