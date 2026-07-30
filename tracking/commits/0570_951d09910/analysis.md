# 提交 0570：Build: Free disk space before running action in Spark CI (#9786)

## 提交信息

- **序号**：0570 / 4088
- **哈希**：951d09910c8a1d2db21c70b1698785502db705f9
- **短哈希**：951d09910
- **日期**：2024-03-08 01:54:08 +0800
- **作者**：Manu Zhang
- **提交说明**：Build: Free disk space before running action in Spark CI (#9786)
- **PR/Issue**：#9786

## 总体目的

本提交在 Spark CI 工作流（`.github/workflows/spark-ci.yml`）的三个构建 job 中各插入一个"释放磁盘空间"的步骤，使用第三方 GitHub Action `jlumbroso/free-disk-space@v1.3.1`，目的是在执行实际的 Gradle 构建之前清理 GitHub-hosted runner（`ubuntu-22.04`）上预装但本任务用不到的大型软件包与目录，腾出磁盘空间，避免 Spark CI 因磁盘耗尽而失败。

GitHub-hosted runner（`ubuntu-22.04`）默认提供约 14 GB 可用磁盘空间，但预装了大量软件（Android SDK、.NET、Haskell GHC、Julia、Docker 镜像、多种工具链等），实际可用空间被这些预装内容压缩。Iceberg 的 Spark CI 任务尤其吃磁盘：需要拉取 Spark 二进制包、Iceberg 各子模块源码与依赖、Scala 2.12/2.13 的依赖树、运行集成测试时还会下载 Spark/Hive 的额外资源，加 Gradle 缓存本身，很容易逼近 14 GB 上限。一旦磁盘写满，构建会以"No space left on device"之类的错误失败，且失败点不可预测（可能在下载依赖、编译、测试写日志、上传 artifact 等任意环节）。

本提交的目标就是通过主动删除 runner 上与本任务无关的预装软件，把可用磁盘空间从约 14 GB 提升到约 20+ GB，让 Spark CI 构建有足够空间完成。

## 如何达成设计目的

整体设计思路是"借用社区成熟 Action，最小侵入地插入释放步骤"：

1. **选用 `jlumbroso/free-disk-space` Action**：这是 GitHub Marketplace 上一个成熟的、被广泛使用的 Action（v1.3.1 是当时的稳定版本），专门用于清理 ubuntu runner 上的预装大型软件。它通过 `sudo rm -rf` 删除 Android SDK、.NET、Haskell、Julia、Docker 镜像、工具缓存等目录。比手写 `rm -rf` 脚本更可靠（处理了不同 runner 镜像下路径差异），也避免了工作流里出现一堆清理命令。

2. **插入位置**：在三个 job 的 `actions/cache@v4`（Gradle 缓存恢复）步骤之后、`./gradlew ... check` 构建命令之前插入。这样：
   - 先恢复 Gradle 缓存（`~/.gradle/caches`、`~/.gradle/wrapper`），保证后续构建能命中缓存；
   - 再释放磁盘空间，腾出空间给构建过程产生的临时文件、下载的依赖、测试输出；
   - 释放发生在构建前，确保构建期间空间已就绪。

3. **`tool-cache: false` 参数**：这是关键配置。`jlumbroso/free-disk-space` 默认会删除 `tool-cache` 目录（即 `/opt/hostedtoolcache`，存放 `actions/setup-java` 安装的 JDK 等）。本工作流在前置步骤用 `actions/setup-java@v4` 安装了 JDK 8 或 11（matrix 中 `jvm: [8, 11]`），JDK 装在 tool-cache 中；若被删除，后续 `./gradlew` 会找不到 Java 而失败。因此显式设置 `tool-cache: false`，保留 tool-cache 不删，只删 Android/.NET/Haskell/Julia/Docker 镜像等无关内容。

4. **三个 job 都加**：`spark-ci.yml` 有三个 job——`spark-3x-scala-2-12-tests`、`spark-3x-scala-2-13-tests`、`spark-3x-java-17-tests`，三者都跑 Spark 构建，磁盘压力类似，因此三个 job 都插入此步骤，保证一致。每个 job 的 matrix（JVM 版本 × Spark 版本 × Scala 版本）会展开成多个并行构建实例，每个实例都需要释放空间。

## 修改详情

### `.github/workflows/spark-ci.yml`

**修改目的**：在 Spark CI 三个构建 job 中插入磁盘释放步骤，腾出空间防止构建失败。

**工作逻辑**：在三处（对应三个 job）各插入相同的 3 行步骤：

```yaml
      - uses: jlumbroso/free-disk-space@v1.3.1
        with:
          tool-cache: false
```

插入位置统一在 `actions/cache@v4`（Gradle 缓存）之后、`- run: echo -e ...`（hosts 配置）之前。三处插入点分别是：

1. **`spark-3x-scala-2-12-tests` job**（matrix：jvm × spark，Scala 2.12，约 89 行附近）：跑 `./gradlew ... :iceberg-spark:iceberg-spark-${{ matrix.spark }}_2.12:check ...`。这个 job 同时跑 JVM 8/11 与 Spark 3.3/3.4/3.5 的组合，磁盘压力大。

2. **`spark-3x-scala-2-13-tests` job**（matrix：jvm × spark，Scala 2.13，约 122 行附近）：跑 Scala 2.13 版本的同样构建。Scala 2.13 依赖树与 2.12 不同，需独立构建，磁盘需求同样大。

3. **`spark-3x-java-17-tests` job**（matrix：jvm × spark × scala-version，Java 17，约 155 行附近）：在 Java 17 下跑 Spark 3.3/3.4/3.5 × Scala 2.12/2.13 的组合。Java 17 runner 配置与 8/11 略有差异，但磁盘预装内容一致，同样需要释放。

`tool-cache: false` 是三处共用的关键参数，确保不删除 `actions/setup-java` 安装的 JDK。其余可被清理的内容（Android SDK 约 5GB、.NET 约 2GB、Haskell、Julia、Docker 镜像等）按 Action 默认配置删除，预计可释放约 7-10 GB。

## 小结

本提交通过在 Spark CI 三个构建 job 中各插入一个 `jlumbroso/free-disk-space@v1.3.1` 步骤（带 `tool-cache: false` 参数），在 Gradle 缓存恢复后、构建执行前主动清理 runner 上的预装大型软件，腾出约 7-10 GB 磁盘空间，避免 Spark CI 因磁盘写满而失败。改动最小侵入（每处仅 3 行 YAML），且通过保留 tool-cache 确保 JDK 不被误删。

回迁到 1.4.x 的注意事项：
1. 本提交仅改 GitHub Actions 工作流 YAML，无任何代码变更，回迁零风险。
2. 回迁时需确认 1.4.x 的 `.github/workflows/spark-ci.yml` 中三个 job 结构与 main 一致（job 名、步骤顺序）；若 1.4.x 的 job 数量或名称不同（如未拆分 Java 17 job），需相应调整插入点数量与位置。
3. `jlumbroso/free-disk-space@v1.3.1` 是固定版本引用，回迁时可保持该版本或升级到更新的稳定版本（但升级需测试，避免行为变化破坏构建）。注意 GitHub 有时会对使用 `@master`/`@main` 等浮动引用的 Action 拒绝运行（安全策略），固定 tag 是更安全的做法，本提交已采用。
4. `tool-cache: false` 必须保留——若误改为 `true` 或删除该参数，`actions/setup-java` 安装的 JDK 会被删，构建立即失败。这是本提交最容易在回迁时出错的地方，需特别注意。
5. 释放磁盘空间会略微增加 job 启动时间（删除大量文件耗时约 30-60 秒），但相比磁盘写满导致的构建失败与重试，这点开销值得。
