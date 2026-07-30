# 提交 1369：Build: Upgrade to Gradle 8.11.0 (#11521)

## 提交信息

- **序号**：1369 / 4088
- **哈希**：e3f39972863f891481ad9f5a559ffef093976bd7
- **短哈希**：e3f399728
- **日期**：2024-11-12（Tue Nov 12 13:23:32 2024 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Build: Upgrade to Gradle 8.11.0 (#11521)
- **PR/Issue**：#11521

## 总体目的

Iceberg 项目使用 Gradle Wrapper（`gradlew` 脚本 + `gradle-wrapper.properties`）锁定构建时使用的 Gradle 版本，确保所有开发者与 CI 环境使用同一版本。本提交将 Gradle Wrapper 从 8.10.2 升级到 8.11.0（8.11 系列的首个稳定版）。

升级动机通常包括：
1. **获取新特性与改进**：Gradle 8.11 引入了若干构建性能改进、配置缓存（configuration cache）稳定性提升、新版 JVM 兼容性支持等；
2. **安全补丁**：Gradle 各版本会修复依赖包（如 `gradle-wrapper.jar` 中嵌入的第三方库）的已知 CVE；
3. **跟上生态**：Iceberg 依赖的 Gradle 插件（如 `nebula-publishing-plugin`、`com.palantir.revapi`、`com.github.spotbugs` 等）逐步要求较新的 Gradle 版本；
4. **CI 环境一致性**：升级后 `distributionSha256Sum` 同步更新，确保下载的 Gradle 发行版完整无损。

## 如何达成设计目的

通过 Gradle Wrapper 标准升级流程：修改 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 与 `distributionSha256Sum`，并同步更新 `gradlew` 脚本中"自动下载 gradle-wrapper.jar"的 fallback URL（指向 GitHub 上对应 Gradle 版本 tag 的 jar 文件）。

注意：本提交未修改 `gradlew.bat`（Windows 版本）或 `gradle-wrapper.jar` 本身，因为：
- `gradle-wrapper.jar` 在已检出到仓库时无需变更（它是引导脚本，跨 Gradle 版本通用）；
- `gradlew.bat` 不含版本特定 URL（其 fallback 逻辑与 Unix 版本不同），无需改动。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`（修改，+2 -2 行）

**修改目的**：将 Gradle Wrapper 锁定的 Gradle 版本从 8.10.2 升级到 8.11.0。

**工作逻辑**：

```properties
# 旧
distributionSha256Sum=31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip

# 新
distributionSha256Sum=57dafb5c2622c6cc08b993c85b7c06956a2f53536432a30ead46166dbca0f1e9
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip
```

- `distributionUrl` 改为 `gradle-8.11-bin.zip`（注意是 `8.11` 而非 `8.11.0`，Gradle 官方对 minor 版本不带 `.0` 后缀）；
- `distributionSha256Sum` 更新为 8.11 发行版的官方 SHA-256 校验值（`57dafb5c...f1e9`），用于验证下载的 Gradle 发行版完整性，防止中间人篡改。

### `gradlew`（修改，+1 -1 行）

**修改目的**：同步更新 Unix `gradlew` 脚本中"wrapper jar 缺失时从 GitHub 下载"的 fallback URL。

**工作逻辑**：

```bash
# 旧
if [ ! -e $APP_HOME/gradle/wrapper/gradle-wrapper.jar ]; then
    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar
fi

# 新
if [ ! -e $APP_HOME/gradle/wrapper/gradle-wrapper.jar ]; then
    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.11.0/gradle/wrapper/gradle-wrapper.jar
fi
```

该 fallback 仅在 `gradle-wrapper.jar` 不存在时触发（如源码分发未带 jar）。URL 指向 Gradle 仓库 `v8.11.0` tag 下的 `gradle-wrapper.jar`。注意这里 tag 名是 `v8.11.0`（带 `.0` 与 `v` 前缀），与 `distributionUrl` 中的 `gradle-8.11-bin.zip` 命名规则不同——前者是 Git tag，后者是发行版 artifact 名。

## 小结

- **成效**：Iceberg 构建工具链升级到 Gradle 8.11.0，获得新版本的改进与安全修复；wrapper 校验和同步更新保证发行版完整性。改动极小（+3/-3 行），纯构建工具链升级，不触碰任何项目源代码或构建脚本逻辑。
- **影响范围**：仅 `gradlew` 与 `gradle/wrapper/gradle-wrapper.properties` 两个 wrapper 文件。所有使用 `./gradlew` 的开发者与 CI 在下次构建时会自动下载 Gradle 8.11.0 发行版（约 130MB）。
- **回迁到 1.4.x 的注意事项**：构建工具链升级类变更，回迁安全。需注意：
  1. 1.4.x 上的 `build.gradle` 与各插件版本需与 Gradle 8.11 兼容（Gradle 8.11 通常向后兼容 8.10.x 的构建脚本，但少数插件可能对新版 Gradle 内部 API 敏感）；
  2. CI 环境需能访问 `services.gradle.org` 下载新版 Gradle，且网络环境允许下载约 130MB 的 zip；
  3. `distributionSha256Sum` 必须与 `distributionUrl` 严格匹配，否则 wrapper 会拒绝启动；若 1.4.x 上有人手工改过 wrapper，回迁时需以本提交的 SHA 为准；
  4. `gradlew.bat`（Windows）未在本提交修改，1.4.x 上的 Windows 用户若依赖 fallback 下载逻辑需自行确认 URL（实际上 `gradlew.bat` 不会做 fallback 下载，它要求 `gradle-wrapper.jar` 已存在）。
