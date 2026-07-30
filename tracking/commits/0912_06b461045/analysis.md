# 提交 0912：Build: Downgrade Gradle from 8.8 to 8.7 due to bug with older OSX versions (#10637)

## 提交信息

- **序号**：0912 / 4088
- **哈希**：06b4610458469c344bb6ecd5207467202b5dc231
- **短哈希**：06b461045
- **日期**：2024-07-08 15:12:47 +0200
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build: Downgrade Gradle from 8.8 to 8.7 due to bug with older OSX versions (#10637)
- **PR/Issue**：#10637

## 总体目的

Iceberg 项目此前将 Gradle 构建工具升级到了 8.8 版本，但随后发现 Gradle 8.8 在较旧版本的 macOS（OSX）系统上存在 bug，会导致构建或测试出现问题。为了保证项目在 macOS 旧版本系统上的可构建性以及维护构建的稳定性，需要将 Gradle 版本从 8.8 回退（降级）到上一个稳定版本 8.7。

这是一个构建工具链层面的紧急回退改动，目的是消除 8.8 版本引入的兼容性问题，避免贡献者和 CI 环境在旧版 macOS 上因 Gradle 自身缺陷而构建失败。通过回退到 8.7，可以恢复一个已知可靠的构建基线。

## 如何达成设计目的

实现方式是修改 Gradle Wrapper 的配置文件和脚本，让 `./gradlew` 启动时下载并使用 8.7 版本的 Gradle 发行包而非 8.8。具体改动包括：

- 修改 `gradle/wrapper/gradle-wrapper.properties`，将 `distributionUrl` 指向 `gradle-8.7-bin.zip`，同时更新对应的 `distributionSha256Sum` 校验和以保证下载完整性，并新增 `validateDistributionUrl=true` 选项。
- 修改 `gradlew` 脚本，将下载 `gradle-wrapper.jar` 时引用的 GitHub 标签从 `v8.8.0` 改为 `v8.7.0`，并附带一处将 `cd` 标准输出重定向到 `/dev/null` 的小修正（这是 Gradle 8.7 wrapper 脚本本身的写法）。

这些都是 `gradle wrapper --gradle-version 8.7` 命令生成的标准产物，没有引入额外的自定义逻辑。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`

**修改目的**：将 Gradle Wrapper 指向的发行版版本从 8.8 回退到 8.7。

**工作逻辑**：
- `distributionUrl` 由 `gradle-8.8-bin.zip` 改为 `gradle-8.7-bin.zip`；
- `distributionSha256Sum` 替换为 8.7 发行包对应的 SHA-256 校验和（`544c35d6...`），用于校验下载的发行包完整性；
- 新增 `validateDistributionUrl=true`，启用对发行版 URL 的校验。

### `gradlew`

**修改目的**：同步更新 wrapper 脚本中下载 `gradle-wrapper.jar` 时引用的 Gradle 版本标签，使其与 8.7 一致。

**工作逻辑**：
- 当 `gradle-wrapper.jar` 不存在时，脚本会通过 `curl` 从 GitHub 下载该 jar，原 URL 中的 `v8.8.0` 改为 `v8.7.0`；
- 同时一处 `APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P )` 将 `cd` 的标准输出重定向到 `/dev/null`，避免 `$CDPATH` 干扰输出，这是 8.7 wrapper 脚本的写法（8.8 版本移除了该重定向）。

## 小结

- **成效**：成功将 Gradle 构建版本从 8.8 回退到 8.7，规避了 8.8 在旧版 macOS 上的 bug，恢复了稳定的构建基线。
- **影响范围**：仅影响 `gradle/wrapper/gradle-wrapper.properties` 和 `gradlew` 两个构建相关文件，不涉及任何业务代码。
- **回迁到 1.4.x 的注意事项**：属于构建工具链回退，可以回迁到 1.4.x，但需先确认 1.4.x 分支当前使用的 Gradle 版本。如果 1.4.x 本身未升级到 8.8，则无需回迁；如果已升级到 8.8 且同样遇到旧 macOS 问题，则适合回迁。风险点很低，仅为 wrapper 配置变更。
