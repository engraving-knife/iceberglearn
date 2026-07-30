# 提交 3474：CI: Replace `actions/cache` with `gradle/actions/setup-gradle` for Gradle caching (#15799)

## 提交信息

- **序号**：3474 / 4088
- **哈希**：315620a18f709ef2c9ed255a7d03e65ff1501775
- **短哈希**：315620a18f
- **日期**：2026-03-27 16:57:53 -0700
- **作者**：Kevin Liu
- **提交说明**：CI: Replace `actions/cache` with `gradle/actions/setup-gradle` for Gradle caching (#15799)
- **PR/Issue**：#15799

## 总体目的

将 CI 工作流中的 `actions/cache`（以及 `actions/cache/restore` + `actions/cache/save`）替换为 `gradle/actions/setup-gradle`。`setup-gradle` 是 Gradle 官方提供的 GitHub Action，专门用于 Gradle 缓存管理，相比通用的 `actions/cache` 有以下优势：

1. 自动管理缓存键和恢复策略
2. 内置 Gradle 缓存最佳实践
3. 简化工作流配置（一行替代多行缓存配置）
4. 自动处理缓存保存和恢复的权限安全

这也简化了在提交 #15788 中引入的缓存分离（restore/save）方案。

## 如何达成设计目的

- 在 11 个工作流文件中，将 `actions/cache`、`actions/cache/restore`、`actions/cache/save` 替换为 `gradle/actions/setup-gradle`
- 使用固定 commit SHA：`gradle/actions/setup-gradle@0723195856401067f7a2779048b490ace7a47d7c # v5`
- 移除缓存路径、键和恢复键配置

## 修改详情

### 11 个工作流文件（共 +14/-140 lines）

涉及以下文件，修改模式类似：

- `api-binary-compatibility.yml`：移除 cache/restore 和 cache/save，添加 setup-gradle
- `delta-conversion-ci.yml`：2 个 job 的缓存替换
- `flink-ci.yml`：缓存替换
- `hive-ci.yml`：缓存替换
- `java-ci.yml`：3 个 job 的缓存替换
- `jmh-benchmarks.yml`：缓存替换
- `kafka-connect-ci.yml`：缓存替换
- `publish-iceberg-rest-fixture-docker.yml`：缓存替换
- `publish-snapshot.yml`：缓存替换
- `recurring-jmh-benchmarks.yml`：缓存替换
- `spark-ci.yml`：缓存替换

**修改模式**：
```yaml
# 旧代码（多行）
- uses: actions/cache/restore@668228... # v5
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
    restore-keys: ${{ runner.os }}-gradle-
...
- uses: actions/cache/save@668228... # v5
  if: github.event_name == 'push'
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

# 新代码（一行）
- uses: gradle/actions/setup-gradle@072319... # v5
```

## 总结

该提交将 11 个 CI 工作流中的 `actions/cache` 缓存方案替换为 Gradle 官方的 `gradle/actions/setup-gradle` action。这大幅简化了工作流配置（移除 140 行缓存配置，添加 14 行 setup-gradle），同时利用 Gradle 官方的缓存最佳实践，并解决了之前缓存分离方案的复杂性。
