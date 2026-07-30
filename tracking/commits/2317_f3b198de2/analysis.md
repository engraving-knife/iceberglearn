# 提交 2317：Build: Upgrade to Gradle 8.14.3 (#13465)

## 提交信息

- **序号**：2317 / 4088
- **哈希**：f3b198de2304c01af9beedf47fcc6edfe8f4f1c7
- **短哈希**：f3b198de2
- **日期**：2025-07-05 12:14:04 +0200
- **作者**：JB Onofré
- **提交说明**：Build: Upgrade to Gradle 8.14.3 (#13465)
- **PR/Issue**：#13465

## 总体目的

这个提交将项目的 Gradle 构建工具从版本 8.14.2 升级到 8.14.3。Gradle 是 Iceberg 项目使用的主要构建系统，定期升级可以获取 bug 修复、性能改进和安全补丁。

8.14.3 是 8.14.x 系列的 patch 版本，通常包含错误修复和稳定性改进，不引入破坏性变更。

## 如何达成设计目的

通过更新 Gradle Wrapper 配置文件来升级版本。Gradle Wrapper 是 Gradle 的版本管理机制，它确保所有开发者使用相同版本的 Gradle。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties` (+2/-2 lines)

**修改目的**：更新 Gradle 发行版本和校验和。

**工作逻辑**：
- `distributionSha256Sum` 更新为 8.14.3 版本的校验和
- `distributionUrl` 从 `gradle-8.14.2-bin.zip` 改为 `gradle-8.14.3-bin.zip`

### `gradlew` (+1/-1 lines)

**修改目的**：更新 gradlew 脚本中引用的 Gradle 版本。

**工作逻辑**：更新下载 `gradle-wrapper.jar` 的 URL 从 `v8.14.2` 改为 `v8.14.3`。

## 总结

这是一个常规的构建工具升级提交，将 Gradle 从 8.14.2 升级到 8.14.3。变更仅涉及 Wrapper 配置文件，不影响项目代码逻辑。
