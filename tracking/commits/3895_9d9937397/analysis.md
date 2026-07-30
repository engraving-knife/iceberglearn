# 提交 3895：CI: Limit CVE scan runs to relevant changes (#16513)

## 提交信息

- **序号**：3895 / 4088
- **哈希**：9d9937397e40ac060ca4037e2028354eaf707870
- **短哈希**：9d9937397
- **日期**：2026-06-17 10:17:51 -0700
- **作者**：Kevin Liu
- **提交说明**：CI: Limit CVE scan runs to relevant changes (#16513)
- **PR/Issue**：#16513

## 总体目的

优化 CVE（安全漏洞）扫描 CI 工作流的触发条件，使其只在相关代码变更时运行，而非对所有 PR 和分支推送都运行。CVE 扫描使用 Trivy 工具扫描项目依赖中的已知漏洞，是一个资源密集型的工作流。当 PR 只修改文档、CI 配置、示例等非生产代码时，运行完整的 CVE 扫描是浪费 CI 资源。

通过添加 `paths` 过滤器，排除非生产代码路径，只在可能影响依赖安全性的变更时触发扫描，可以显著减少 CI 运行次数和资源消耗，同时加快 PR 反馈速度。

## 如何达成设计目的

在 `.github/workflows/cve-scan.yml` 工作流配置的 `push` 和 `pull_request` 触发器上添加 `paths` 过滤器，使用否定模式（`!`）排除不影响依赖安全的路径。同时移除了一个不再需要的 `trivyignores` 参数。

## 修改详情

### `.github/workflows/cve-scan.yml` (+44/-1 lines)

**修改目的**：限制 CVE 扫描只在相关变更时触发。

**工作逻辑**：

1. **push 触发器的 paths 过滤**：
```yaml
push:
  branches:
    - main
    - '2.*'
  tags:
    - 'apache-iceberg-**'
  paths:
    - '**'                          # 匹配所有文件
    - '!.github/**'                 # 排除 .github 目录（但保留下面的工作流自身）
    - '.github/workflows/cve-scan.yml'  # 但保留 CVE 扫描工作流自身的变更
    - '!.baseline/**'               # 排除 baseline 配置
    - '!.palantir/**'               # 排除 palantir 配置
    - '!.gitignore'
    - '!.asf.yaml'
    - '!dev/**'                     # 排除开发工具脚本
    - '!docker/**'                  # 排除 docker 配置
    - '!docs/**'                    # 排除文档
    - '!examples/**'                # 排除示例
    - '!site/**'                    # 排除网站
    - '!format/**'                  # 排除格式定义
    - '!.gitattributes'
    - '!**/README.md'
    - '!AGENTS.md'
    - '!CONTRIBUTING.md'
    - '!SECURITY-THREAT-MODEL.md'
    - '!**/LICENSE'
    - '!**/NOTICE'
    - '!doap.rdf'
```

2. **pull_request 触发器添加相同的 paths 过滤**（之前 pull_request 没有 paths 限制）

3. **移除 trivyignores 参数**：
```yaml
-        trivyignores: ${{ matrix.trivyignores || '' }}
```
移除了矩阵中不再使用的 trivyignores 配置。

排除的路径主要分为几类：
- **CI/构建配置**：`.github/`、`.baseline/`、`.palantir/`
- **文档和网站**：`docs/`、`site/`、`README.md`、`CONTRIBUTING.md`
- **开发工具**：`dev/`、`docker/`、`examples/`
- **元数据文件**：`LICENSE`、`NOTICE`、`.gitignore`、`doap.rdf`
- **格式定义**：`format/`

保留了 CVE 扫描工作流自身的变更触发，确保工作流配置修改时仍能验证。

## 总结

通过为 CVE 扫描工作流添加 paths 过滤器，将扫描限制在可能影响依赖安全性的代码变更上，排除了文档、CI 配置、开发工具等非生产代码路径。这能显著减少不必要的 CI 运行，节约资源并加快 PR 反馈速度。同时移除了不再使用的 trivyignores 参数，保持配置整洁。
