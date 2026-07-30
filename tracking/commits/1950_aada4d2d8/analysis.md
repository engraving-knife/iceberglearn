# 提交 1950：Build: Bump org.apache.httpcomponents.client5:httpclient5 from 5.4.2 to 5.4.3 (#12685)

## 提交信息

- **序号**：1950 / 4088
- **哈希**：aada4d2d88e60b65c4223da12ae6f5bfab550cba
- **短哈希**：aada4d2d8
- **日期**：2025-04-02 08:13:38 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 from 5.4.2 to 5.4.3 (#12685)
- **PR/Issue**：#12685

## 总体目的

本提交是由 Dependabot 自动生成的依赖升级，将 Apache HttpComponents Client 5（`httpclient5`）从 5.4.2 升级到 5.4.3。这是一次小版本（patch）升级，通常包含 bug 修复和安全补丁，不引入破坏性变更。

## 如何达成设计目的

Dependabot 通过修改版本目录文件 `gradle/libs.versions.toml` 中的版本声明来完成升级。由于该依赖被多个打包模块包含（如 kafka-connect 运行时、open-api 模块），相应模块的 `LICENSE` 文件中引用的版本号也需要同步更新，以保持许可证信息与实际打包依赖一致。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 httpclient5 版本声明。

**工作逻辑**：将 `httpclient5` 的版本从 `5.4.2` 改为 `5.4.3`。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 hive 运行时 LICENSE 中 httpclient5 的版本号。

**工作逻辑**：将许可证文件中记录的 httpclient5 版本由 5.4.2 更新为 5.4.3。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 main 运行时 LICENSE 中 httpclient5 的版本号。

**工作逻辑**：将许可证文件中记录的 httpclient5 版本由 5.4.2 更新为 5.4.3。

### `open-api/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 open-api 模块 LICENSE 中 httpclient5 的版本号。

**工作逻辑**：将许可证文件中记录的 httpclient5 版本由 5.4.2 更新为 5.4.3。

## 总结

本提交是 Dependabot 发起的依赖升级，将 `org.apache.httpcomponents.client5:httpclient5` 从 5.4.2 升级到 5.4.3，并同步更新了 gradle 版本目录与三个受影响模块的 LICENSE 文件中的版本声明。
