# 提交 3468：CI: Fix zizmor security findings in PR-triggered workflows (#15788)

## 提交信息

- **序号**：3468 / 4088
- **哈希**：a7d2113ac18834dbe24cb59cfb4c6b6d20b7310d
- **短哈希**：a7d2113ac1
- **日期**：2026-03-27 11:29:14 -0700
- **作者**：Kevin Liu
- **提交说明**：CI: Fix zizmor security findings in PR-triggered workflows (#15788)
- **PR/Issue**：#15788

## 总体目的

修复 zizmor（GitHub Actions 安全扫描工具）在 PR 触发的工作流中发现的安全问题。主要修复两类问题：

1. **`persist-credentials` 问题**：`actions/checkout` 默认会在 `.git/config` 中保存 GitHub token，在 PR 触发的工作流中存在安全风险
2. **缓存注入问题**：`actions/cache` 在 PR 触发的工作流中可能被恶意 PR 利用进行缓存投毒

## 如何达成设计目的

- 在所有 PR 触发的工作流中为 `actions/checkout` 添加 `persist-credentials: false`
- 将 `actions/cache` 替换为分离的 `actions/cache/restore`（读取）和 `actions/cache/save`（写入）模式
- 缓存仅在 `push` 事件时保存，PR 事件只读取不写入

## 修改详情

### 11 个工作流文件（共 +93/-8 lines）

涉及以下文件，修改模式类似：

- `api-binary-compatibility.yml`
- `codeql.yml`
- `delta-conversion-ci.yml`
- `docs-ci.yml`
- `flink-ci.yml`
- `hive-ci.yml`
- `java-ci.yml`
- `kafka-connect-ci.yml`
- `license-check.yml`
- `open-api.yml`
- `spark-ci.yml`

**修改模式 1 - persist-credentials**：
```yaml
- uses: actions/checkout@de0fac2e... # v6
  with:
    persist-credentials: false
```

**修改模式 2 - 缓存分离**：
```yaml
# 读取缓存（所有事件）
- uses: actions/cache/restore@668228... # v5
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    ...

# 保存缓存（仅 push 事件）
- uses: actions/cache/save@668228... # v5
  if: github.event_name == 'push'
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
```

## 总结

该提交修复了 zizmor 在 PR 触发工作流中发现的安全问题。通过禁用 checkout 的凭据持久化和将缓存操作分离为只读（restore）和仅 push 时写入（save），防止恶意 PR 通过缓存投毒或凭据泄露进行攻击。
