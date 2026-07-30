# 提交 3733：Build: Speed up Spark CI with parallel test execution (#16357)

## 提交信息

- **序号**：3733 / 4088
- **哈希**：1d8de4a961ef12c0bb12886708fe2943d0a2ada6
- **短哈希**：1d8de4a96
- **日期**：2026-05-18 08:59:02 -0700
- **作者**：Kevin Liu
- **提交说明**：Build: Speed up Spark CI with parallel test execution (#16357)
- **PR/Issue**：#16357

## 总体目的

本提交旨在加速 Iceberg 的 Spark CI 工作流。原 Spark CI 在每个矩阵组合（JVM × Spark × Scala）的单一 job 中串行执行三个项目的测试：`iceberg-spark`（core）、`iceberg-spark-extensions`、`iceberg-spark-runtime`。这种串行执行使得每个 job 的耗时较长，整体 CI 周转时间受到限制。此外，原先 `max-parallel: 15` 未能充分利用 Apache 基础设施允许的并发上限（20），且缺乏整体超时保护，可能导致个别异常 job 长时间占用资源。

本提交通过两个层面加速 CI：一是将 core 与 extensions/runtime 拆分为独立的并行 job，使它们能并发执行；二是提升并发上限到 20 并启用 Gradle 的 `testParallelism=auto`，让测试在单个 job 内也能并行执行。

## 如何达成设计目的

1. 在矩阵中新增 `tests: [core, extensions]` 维度，将原先一个 job 中串行执行的三个项目拆分为两类 job：`core` 只跑 `iceberg-spark`，`extensions` 跑 `iceberg-spark-extensions` 和 `iceberg-spark-runtime`。这两类 job 可以并发运行。
2. 将 `max-parallel` 从 15 提升到 20（Apache 基础设施策略上限），并添加注释说明。
3. 在 `Run tests` 步骤中根据 `matrix.tests` 的值动态选择要执行的项目，并添加 `-DtestParallelism=auto` 让 Gradle 自动并行测试。
4. 新增 `timeout-minutes: 90` 防止异常 job 长时间挂起。
5. 移除 `jlumbroso/free-disk-space` 步骤（可能因为拆分后单个 job 资源占用降低）。
6. 改进失败时的 artifact 名称，加入矩阵维度信息避免冲突，并设置 7 天保留期。

## 修改详情

### `.github/workflows/spark-ci.yml` (+17/-10 lines)

**修改目的**：拆分测试项目为并行 job，提升并发上限，启用测试并行，增加超时与日志改进。

**工作逻辑**：
- 新增 `timeout-minutes: 90`：整体 job 超时保护。
- `max-parallel` 从 15 改为 20，添加注释说明 Apache 基础设施策略上限为 20。
- 矩阵新增 `tests: [core, extensions]` 维度，并添加注释解释拆分目的：
```yaml
# Split iceberg-spark (core) from iceberg-spark-extensions/-runtime
# so they run as concurrent jobs rather than serially in one job.
tests: [core, extensions]
```
- 移除 `jlumbroso/free-disk-space` 步骤。
- `Run tests` 步骤改为根据 `matrix.tests` 动态选择项目：
```yaml
- name: Run tests
  run: |
    if [[ "${{ matrix.tests }}" == "core" ]]; then
      projects=":iceberg-spark:iceberg-spark-${{ matrix.spark }}_${{ matrix.scala }}:check"
    else
      projects=":iceberg-spark:iceberg-spark-extensions-${{ matrix.spark }}_${{ matrix.scala }}:check :iceberg-spark:iceberg-spark-runtime-${{ matrix.spark }}_${{ matrix.scala }}:check"
    fi
    ./gradlew -DsparkVersions=${{ matrix.spark }} -DscalaVersion=${{ matrix.scala }} -DflinkVersions= -DkafkaVersions= \
      $projects -Pquick=true -x javadoc -DtestParallelism=auto
```
  新增 `-DtestParallelism=auto` 让 Gradle 自动决定测试并行度。
- 失败 artifact 名称改为包含矩阵维度：`test logs (${{ matrix.jvm }}, ${{ matrix.spark }}, ${{ matrix.scala }}, ${{ matrix.tests }})`，避免不同 job 的 artifact 互相覆盖，并添加 `retention-days: 7` 控制存储成本。

## 总结

本提交通过拆分 Spark CI 的测试项目为并行的 `core` 与 `extensions` 两类 job、提升并发上限到 20、启用 Gradle `testParallelism=auto`、添加 90 分钟超时保护以及改进失败日志 artifact 命名，显著加速了 Spark CI 的整体周转时间。改动均集中在工作流配置，不影响产品代码，是 CI 性能优化的基础设施提交。拆分后单个 job 的资源占用降低（移除了 free-disk-space 步骤），同时更充分的并发利用使 CI 总耗时接近最慢的那一类 job 而非所有项目串行之和。
