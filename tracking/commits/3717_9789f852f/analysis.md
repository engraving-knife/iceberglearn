# 提交 3717：Build: Designate a single Gradle cache writer across CI workflows (#16356)

## 提交信息

- **序号**：3717 / 4088
- **哈希**：9789f852fe7066a852667619a7ae08fa8a8ad591
- **短哈希**：9789f852f
- **日期**：2026-05-16 08:37:26 -0700
- **作者**：Kevin Liu
- **提交说明**：Build: Designate a single Gradle cache writer across CI workflows (#16356)
- **PR/Issue**：#16356

## 总体目的

本提交旨在解决 Iceberg 项目多个 CI 工作流同时写入 Gradle 构建缓存导致的"缓存竞争"与"缓存抖动"问题。Iceberg 仓库拥有 12 个以上的 CI 工作流（如 java-ci、spark-ci、flink-ci、hive-ci、kafka-connect-ci、delta-conversion-ci、cve-scan、api-binary-compatibility、jmh-benchmarks 等），它们在并行的不同 runner 上执行，都会通过 `gradle/actions/setup-gradle` 操作写入共享的 Gradle 构建缓存。

当多个工作流同时写缓存时，会出现缓存条目被互相覆盖、缓存命中率下降、缓存上传冲突等问题，使得 CI 整体变得不稳定且缓存效率低下。本提交通过指定唯一的"全局写入者"来消除这种竞争：只有 `java-ci` 工作流中的 `build-checks` 任务在 `main` 分支且 JVM 为 17 时允许写缓存，其他所有工作流均设为只读模式。

## 如何达成设计目的

设计思路是利用 `gradle/actions/setup-gradle` action 提供的 `cache-read-only` 参数进行细粒度控制。对 `java-ci.yml` 中的 `build-checks` 任务，使用条件表达式 `cache-read-only: ${{ !(github.ref == 'refs/heads/main' && matrix.jvm == 17) }}`，使得只有 main 分支且 JVM 17 的构建会写入缓存；对该工作流的其他任务（如 quick check、build-javadoc、check-runtime-deps）以及其他所有工作流，统一设置 `cache-read-only: true`。这样所有 CI 共享同一份 Gradle 缓存，但只有一个权威写入点，避免了多写者竞争。

## 修改详情

### `.github/workflows/java-ci.yml` (+12/-0 lines)

**修改目的**：将 `java-ci` 工作流设置为唯一的缓存写入点。

**工作逻辑**：
- 对 `quick-check`、`build-javadoc`、`check-runtime-deps` 三个任务都添加 `cache-read-only: true`，注释说明 "java-ci's build-checks (17) is the global canonical writer"。
- 对 `build-checks` 任务使用条件表达式：
```yaml
- uses: gradle/actions/setup-gradle@0723195856401067f7a2779048b490ace7a47d7c # v5.0.2
  with:
    # Writes cache on main; read-only otherwise.
    cache-read-only: ${{ !(github.ref == 'refs/heads/main' && matrix.jvm == 17) }}
```
该表达式在 `main` 分支且 `matrix.jvm == 17` 时为 `false`（即可写），其余场景为 `true`（只读）。

### `.github/workflows/api-binary-compatibility.yml` (+3/-0 lines)

**修改目的**：将该工作流的 setup-gradle 步骤设为只读。

**工作逻辑**：添加 `cache-read-only: true` 与注释，表明此工作流不写入缓存。

### `.github/workflows/cve-scan.yml` (+3/-0 lines)

**修改目的**：将 CVE 扫描工作流设为只读。同上模式。

### `.github/workflows/delta-conversion-ci.yml` (+6/-0 lines)

**修改目的**：将 Delta Conversion CI 的两个 job（Scala 2.12 与 2.13）均设为只读。

**工作逻辑**：在该工作流的两个并行 job 的 setup-gradle 步骤中分别添加 `cache-read-only: true`。

### `.github/workflows/flink-ci.yml` (+3/-0 lines)

**修改目的**：将 Flink CI 设为只读。同上模式。

### `.github/workflows/hive-ci.yml` (+3/-0 lines)

**修改目的**：将 Hive CI 设为只读。同上模式。

### `.github/workflows/jmh-benchmarks.yml` (+3/-0 lines)

**修改目的**：将 JMH 基准测试工作流设为只读。同上模式。

### `.github/workflows/kafka-connect-ci.yml` (+3/-0 lines)

**修改目的**：将 Kafka Connect CI 设为只读。同上模式。

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+3/-0 lines)

**修改目的**：将 REST fixture Docker 发布工作流设为只读。同上模式。

### `.github/workflows/publish-snapshot.yml` (+3/-0 lines)

**修改目的**：将快照发布工作流设为只读。同上模式。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+3/-0 lines)

**修改目的**：将周期性 JMH 基准测试工作流设为只读。同上模式。

### `.github/workflows/spark-ci.yml` (+3/-0 lines)

**修改目的**：将 Spark CI 设为只读。同上模式。

## 总结

本提交通过在 12 个 CI 工作流中统一设置 `cache-read-only`，将 Gradle 构建缓存的写入权收束到 `java-ci` 工作流的 `build-checks`（main 分支，JVM 17）这一个权威点上，其余工作流均以只读方式共享缓存。这一改动有效消除了多写者竞争导致的缓存抖动，提升了 CI 缓存的稳定性与命中率，是基础设施优化类提交。改动均为配置层，不影响产品代码行为。
