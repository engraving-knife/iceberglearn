# 提交分析：3874 - Build: Bump github/codeql-action

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 3874 |
| 短哈希 | df8dc25e6 |
| 完整哈希 | df8dc25e6ab192b3cd662ebe3d6200c398f8bc5d |
| 日期 | 2026-06-14 00:06:35 -0700 |
| 作者 | dependabot[bot] |
| 提交说明 | Build: Bump github/codeql-action from 4.36.0 to 4.36.2 (#16812) |

## 总体目的

将 GitHub Actions 中使用的 `github/codeql-action` 从 4.36.0 升级到 4.36.2。

## 修改详情

### 涉及文件及修改

#### 1. `.github/workflows/codeql.yml`

更新 CodeQL 初始化和分析步骤的 action 版本：

```diff
-      uses: github/codeql-action/init@7211b7c8077ea37d8641b6271f6a365a22a5fbfa # v4.36.0
+      uses: github/codeql-action/init@8aad20d150bbac5944a9f9d289da16a4b0d87c1e # v4.36.2
```

```diff
-      uses: github/codeql-action/analyze@7211b7c8077ea37d8641b6271f6a365a22a5fbfa # v4.36.0
+      uses: github/codeql-action/analyze@8aad20d150bbac5944a9f9d289da16a4b0d87c1e # v4.36.2
```

#### 2. `.github/workflows/cve-scan.yml`

更新 Trivy 结果上传到 GitHub Security tab 的 action 版本：

```diff
-      uses: github/codeql-action/upload-sarif@7211b7c8077ea37d8641b6271f6a365a22a5fbfa # v4.36.0
+      uses: github/codeql-action/upload-sarif@8aad20d150bbac5944a9f9d289da16a4b0d87c1e # v4.36.2
```

## CI/CD 类提交说明

此提交属于 CI/CD 依赖升级类，由 Dependabot 自动生成。升级的是 GitHub 官方的 CodeQL action，用于代码安全分析和 SARIF 结果上传。这是一个补丁版本升级（4.36.0 → 4.36.2），影响两个工作流文件中的三个 action 引用点。使用 SHA 引用并附带版本注释，符合安全最佳实践。

## 总结

常规的 CodeQL action 补丁版本升级，同时更新了 CodeQL 分析工作流和 CVE 扫描工作流中的 action 引用。
