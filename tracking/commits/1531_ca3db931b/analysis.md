# 提交 1531 ca3db931b 分析

## 提交信息
- 哈希：ca3db931b0f024f0412084751ac85dd4ef2da7e7
- 日期：2024-12-23（Mon Dec 23 08:41:39 2024 +0100）
- 作者：JB Onofré <jbonofre@apache.org>
- 消息：Upgrade to Gradle 8.12 (#11861)

## 总体目的

本提交由人工提交（非 Dependabot），目的是将 Apache Iceberg 项目的 Gradle 构建工具版本从 8.11.1 升级到 8.12。Gradle 是 Iceberg 选用的构建系统，负责编译、测试、打包、发布所有 Java 模块（核心库、集成模块、Spark/Flink/Hive/Trino 适配器等）。Gradle Wrapper 机制（`gradlew` 脚本 + `gradle-wrapper.properties`）保证所有开发者与 CI 使用同一 Gradle 版本，避免环境差异导致的构建不一致。

Gradle 8.12 是 2024 年 12 月发布的特性版本。相比 8.11.x，8.12 通常包含构建性能改进、新 DSL 能力、配置缓存与增量编译的稳定性增强、对最新 JDK 的兼容性改进等。升级构建工具版本对大型多模块项目（如 Iceberg 有数十个模块）有实际价值：构建速度、内存占用、配置缓存命中率等关键指标可能得到改善，同时保持对新版 JDK 的兼容。

本提交修改两个文件：`gradle/wrapper/gradle-wrapper.properties`（Wrapper 配置，指定下载哪个版本的 Gradle 发行包）与 `gradlew`（Wrapper 启动脚本，含一个回退下载 `gradle-wrapper.jar` 的逻辑）。这是标准的 Gradle Wrapper 升级流程。

## 如何达成设计目的

通过两个文件修改完成升级：
1. `gradle/wrapper/gradle-wrapper.properties`：更新 Gradle 发行包的下载 URL 与 SHA-256 校验和，指向 8.12 版本。
2. `gradlew`：更新回退下载 `gradle-wrapper.jar` 时的 Gradle 版本标签。

这是 Gradle Wrapper 升级的标准做法。正常运行 `gradle wrapper --gradle-version 8.12` 命令会自动更新这两个文件（以及 `gradlew.bat`，但本提交未涉及，可能因平台差异或已一致）。SHA-256 校验和用于验证下载的发行包完整性，防止传输损坏或中间人篡改。

### 修改详情

#### `gradle/wrapper/gradle-wrapper.properties`

**修改目的**：将 Wrapper 指向的 Gradle 发行包从 8.11.1 升级到 8.12。

**工作逻辑**：该文件包含 Wrapper 的关键配置：
- `distributionBase` / `distributionPath`：发行包解压后的存放位置（GRADLE_USER_HOME/wrapper/dists）。
- `distributionUrl`：Gradle 发行包的下载 URL，本次从 `https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip` 改为 `https\://services.gradle.org/distributions/gradle-8.12-bin.zip`。`-bin` 后缀表示精简版（仅二进制，不含源码与文档），体积更小，适合 CI 与开发者。URL 中的冒号被转义为 `\:`，是 properties 文件的标准转义。
- `distributionSha256Sum`：发行包的 SHA-256 哈希值，本次从 `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`（8.11.1）改为 `7a00d51fb93147819aab76024feece20b6b84e420694101f276be952e08bef03`（8.12）。Wrapper 下载后会校验哈希，不匹配则报错中止，保证完整性。
- `networkTimeout=10000`：下载超时 10 秒。
- `validateDistributionUrl=true`：校验 URL 合法性。
- `zipStoreBase` / `zipStorePath`：ZIP 缓存位置。

执行 `./gradlew` 时，脚本读取该文件，若本地未缓存指定版本则按 `distributionUrl` 下载、校验 SHA-256、解压后执行。这样所有环境自动获得 8.12 版本，无需手动安装。

#### `gradlew`

**修改目的**：更新 `gradlew` 脚本中回退下载 `gradle-wrapper.jar` 的版本引用。

**工作逻辑**：`gradlew` 是 POSIX shell 脚本，负责启动 Wrapper。它在执行前会检查 `gradle/wrapper/gradle-wrapper.jar` 是否存在；若不存在（如用户从源码 zip 解压后未带 jar），则用 `curl` 从 GitHub 上对应版本的 Gradle 仓库下载该 jar：

```sh
if [ ! -e $APP_HOME/gradle/wrapper/gradle-wrapper.jar ]; then
-    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar
+    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.12.0/gradle/wrapper/gradle-wrapper.jar
fi
```

版本标签从 `v8.11.1` 改为 `v8.12.0`（注意 GitHub tag 用 `v8.12.0` 而非 `v8.12`，这是 Gradle 仓库的 tag 命名约定，第三个版本号 `.0` 不能省略）。这一回退路径保证即使 `gradle-wrapper.jar` 丢失，脚本也能自愈。共 1 行变更。

## 小结

- **成效**：Gradle 构建工具升级到 8.12，获得构建性能改进、配置缓存与增量编译稳定性增强、对新 JDK 的兼容性改进；通过 Wrapper 机制保证所有开发者与 CI 环境使用一致的 8.12 版本，避免环境差异。
- **影响范围**：`gradle/wrapper/gradle-wrapper.properties`（2 行：URL 与 SHA-256）+ `gradlew`（1 行：回退下载 URL），共 2 个文件、3 行变更。影响所有模块的构建过程，但不影响构建产物内容（升级构建工具不改变 jar 中的字节码，除非新版 Gradle 改变了编译器默认行为——通常不会）。
- **回迁到 1.4.x 的注意事项**：Gradle 版本升级影响构建过程而非产物。1.4.x 维护分支若需重新构建（如发布 patch 版），可回迁以获得更好的构建工具支持与 JDK 兼容性，但需验证 1.4.x 的构建脚本与插件在 Gradle 8.12 下仍兼容（Gradle 8.x 内部一般向后兼容，风险低）。若 1.4.x 构建已稳定且无 JDK 兼容问题，回迁移非必要，但**通常无害**，建议根据实际构建环境需求决定。
