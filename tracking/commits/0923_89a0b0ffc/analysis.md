# 提交 0923：Upgrade to Gradle 8.9 (#10686)

## 提交信息

- **序号**：0923 / 4088
- **哈希**：89a0b0ffcebaf640d7df394b97404cb9505817c7
- **短哈希**：89a0b0ffc
- **日期**：2024-07-11（Thu Jul 11 21:12:03 2024 +0200）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Upgrade to Gradle 8.9
- **PR/Issue**：#10686

## 总体目的

将 Iceberg 项目使用的 Gradle 包装器（Gradle Wrapper）版本从 8.7 升级到 8.9。Gradle 会定期发布新版本，包含 bug 修复、性能改进、安全补丁以及对新 JDK 版本的更好支持。保持构建工具版本与时俱进是项目维护的常规工作，可以避免在较新 JDK 上构建时遇到已知的兼容性问题，同时让开发者享受新版 Gradle 在构建速度、依赖解析等方面的改进。

Gradle Wrapper 是 Gradle 推荐的分发方式：仓库里只提交 `gradle-wrapper.properties`、`gradlew`、`gradlew.bat` 和 `gradle-wrapper.jar`，开发者首次执行 `./gradlew` 时会自动下载指定版本的 Gradle。因此升级 Gradle 版本只需更新 wrapper 相关文件即可，无需修改项目源码或构建脚本逻辑。

## 如何达成设计目的

通过修改 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 与 `distributionSha256Sum`，把目标 Gradle 发行版从 `gradle-8.7-bin.zip` 改为 `gradle-8.9-bin.zip`，并同步更新对应的 SHA-256 校验和（用于验证下载完整性）。同时更新 `gradlew` 脚本中"当 `gradle-wrapper.jar` 缺失时从 GitHub 下载"的备用 URL，把版本号从 `v8.7.0` 改为 `v8.9.0`，保证备用路径与主发行版版本一致。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`

**修改目的**：将 Gradle Wrapper 指向 8.9 发行版。

**工作逻辑**：

```diff
-distributionSha256Sum=544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d
-distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
+distributionSha256Sum=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
+distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

`distributionSha256Sum` 是 Gradle 8.9 bin 压缩包的官方校验和；`distributionUrl` 指向 8.9 下载地址。下次执行 `./gradlew` 时，wrapper 会按此 URL 下载并校验。

### `gradlew`

**修改目的**：同步备用下载路径到 8.9.0。

**工作逻辑**：

```diff
-    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar
+    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar
```

当本地缺少 `gradle-wrapper.jar`（仓库有时不提交该二进制）时，从 Gradle 官方 GitHub 仓库对应 tag 下载 jar，保证版本一致。

## 小结

- **成效**：项目构建工具升级到 Gradle 8.9，跟随上游最新稳定版。
- **影响范围**：仅 2 个构建相关文件（`gradle-wrapper.properties`、`gradlew`），共 3 行改动，无源码或构建脚本逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是纯粹的构建工具版本升级，不涉及任何业务逻辑，**适合回迁到 1.4.x**，且通常风险很低。回迁时只需 cherry-pick 这两个文件改动即可；但需确认 1.4.x 当前使用的 Gradle 版本（若 1.4.x 已基于更早的 8.x），且 1.4.x 的构建脚本（`build.gradle` 等）与 Gradle 8.9 兼容。如果 1.4.x 已经冻结 Gradle 版本以保持发布稳定性，也可不回迁，保持 1.4.x 原有 Gradle 版本即可，不影响功能。
