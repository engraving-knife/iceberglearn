# 提交 3593：Build: Bump bouncycastle from 1.82 to 1.84 (#16117)

## 提交信息

- **序号**：3593 / 4088
- **哈希**：c213f5e96e2bd9ae41e5d85e5493ec5a3d74c290
- **短哈希**：c213f5e96
- **日期**：2026-04-25 23:49:16 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump bouncycastle from 1.82 to 1.84 (#16117)
- **PR/Issue**：#16117

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 BouncyCastle 加密库从版本 1.82 升级到 1.84。BouncyCastle 是一个 Java 加密库，提供 TLS/SSL、证书、密钥等加密操作支持。在 Iceberg 中，BouncyCastle 用于 iceberg-core 模块的 TLS 测试（与 MockServer 配合），由提交 3564 引入。这是一个 semver-minor（次版本）升级，同时升级三个 BouncyCastle 组件：`bcpkix-jdk18on`、`bcprov-jdk18on`、`bcutil-jdk18on`。

## 如何达成设计目的

Dependabot 自动检测到版本目录中 `bouncycastle` 版本引用有新版本可用，自动创建 PR 升级版本声明。由于三个 BouncyCastle 库共享同一个版本变量 `bouncycastle`，一次升级即覆盖所有三个组件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 BouncyCastle 版本声明。

**工作逻辑**：
```toml
-bouncycastle = "1.82"
+bouncycastle = "1.84"
```
该版本变量被 `bouncycastle-bcpkix`、`bouncycastle-bcprov`、`bouncycastle-bcutil` 三个库别名引用，升级后所有三个 BouncyCastle 组件统一更新到 1.84 版本。

## 总结

这是一个常规的依赖维护提交，通过次版本升级保持 BouncyCastle 加密库的最新状态。由于 BouncyCastle 用于 TLS 测试场景，1.84 版本可能包含安全修复和加密算法改进。
