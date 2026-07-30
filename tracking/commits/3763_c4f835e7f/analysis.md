# 提交 3763：CI: Skip spotlessCheck in core-tests (#16505)

## 提交信息

- **序号**：3763 / 4088
- **哈希**：c4f835e7f12e2dcbc037053c1e6f94d83b80dba1
- **短哈希**：c4f835e7f
- **日期**：2026-05-21 13:57:13 +0200
- **作者**：Yuya Ebihara
- **提交说明**：CI: Skip spotlessCheck in core-tests (#16505)
- **PR/Issue**：#16505

## 总体目的

这个提交在 CI 的 core-tests 作业中跳过 `spotlessCheck` 任务。`spotlessCheck` 是代码格式化检查工具，在 `java-ci.yml` 的 `build-checks (17)` 作业中已经作为"全局规范写入者"（global canonical writer）运行。core-tests 作业的构建缓存配置为只读（`cache-read-only: true`），不应该重复执行格式化检查，避免在只读缓存场景下可能出现的冲突或冗余工作。

## 如何达成设计目的

在 core-tests 作业的 Gradlew 命令中添加 `-x spotlessCheck` 参数，排除 spotless 检查任务。

## 修改详情

### `.github/workflows/java-ci.yml` (+1/-1 lines)

**修改目的**：在 core-tests 作业中跳过 spotlessCheck。

**工作逻辑**：
将原来的命令：
```yaml
- run: ./gradlew check -DsparkVersions= -DflinkVersions= -DkafkaVersions= -Pquick=true -x javadoc
```
改为：
```yaml
- run: ./gradlew check -DsparkVersions= -DflinkVersions= -DkafkaVersions= -Pquick=true -x javadoc -x spotlessCheck
```

添加 `-x spotlessCheck` 排除代码格式化检查任务，因为该检查已在 `build-checks` 作业中执行，core-tests 作业无需重复。

## 总结

这是一个 CI 优化提交，通过在 core-tests 作业中跳过 `spotlessCheck` 避免重复执行代码格式化检查。由于 `build-checks` 作业已经负责全局的格式化检查，core-tests 只需要专注于测试，这样可以减少 CI 运行时间并避免只读缓存场景下的潜在问题。
