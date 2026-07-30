# 提交 3680：Build: Bump github/codeql-action from 4.35.2 to 4.35.3 (#16275)

## 提交信息

- **序号**：3680 / 4088
- **哈希**：34511542e6764fdbb23e2a60caf34a898b8ad76f
- **短哈希**：34511542e
- **日期**：2026-05-09 23:58:25 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump github/codeql-action from 4.35.2 to 4.35.3 (#16275)
- **PR/Issue**：#16275

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 GitHub CodeQL Action 从 4.35.2 升级到 4.35.3。CodeQL 是 GitHub 提供的语义代码分析工具，用于发现代码中的安全漏洞和编码错误。CodeQL Action 是 GitHub Actions 中用于运行 CodeQL 分析的官方 action，在 Iceberg 项目中用于自动化的安全代码扫描。

此次升级为补丁版本（patch version）升级，主要包含 bug 修复和功能改进。

## 如何达成设计目的

通过修改 `.github/workflows/codeql.yml` GitHub Actions 工作流配置文件，将其中引用的 codeql-action 的 commit hash 和版本号同步更新到 v4.35.3 对应的 commit。

## 修改详情

### `.github/workflows/codeql.yml` (+2/-2 lines)

**修改目的**：升级 CodeQL Action 到 4.35.3 版本。

**工作逻辑**：

```yaml
-      uses: github/codeql-action/init@95e58e9a2cdfd71adc6e0353d5c52f41a045d225 # v4.35.2
+      uses: github/codeql-action/init@e46ed2cbd01164d986452f91f178727624ae40d7 # v4.35.3
```

以及：

```yaml
-      uses: github/codeql-action/analyze@95e58e9a2cdfd71adc6e0353d5c52f41a045d225 # v4.35.2
+      uses: github/codeql-action/analyze@e46ed2cbd01164d986452f91f178727624ae40d7 # v4.35.3
```

修改了两处 action 引用：
1. `Initialize CodeQL` 步骤中的 `codeql-action/init` action
2. `Perform CodeQL Analysis` 步骤中的 `codeql-action/analyze` action

每处都同时更新了 commit hash（用于版本锁定）和注释中的版本号。使用 commit hash 而不是版本标签是 GitHub Actions 的安全最佳实践，可以避免供应链攻击。

## 总结

这是一个常规的 CI/CD 工具依赖维护提交，将 GitHub CodeQL Action 升级到最新的补丁版本。保持 CodeQL 工具的最新版本对于确保安全扫描的有效性、获取最新的漏洞检测规则和修复已知问题具有重要意义。
