# 提交 2819：Build: Bump junit from 5.13.4 to 5.14.1 (#14476)

## 提交信息

- **序号**：2819 / 4088
- **哈希**：6b03be4025c1126d8561dae7645307cf511675e5
- **短哈希**：6b03be402
- **日期**：2025-11-02 12:29:05 +0530
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit from 5.13.4 to 5.14.1 (#14476)
- **PR/Issue**：#14476

## 总体目的

这是 Dependabot 自动生成的依赖版本升级提交。JUnit Jupiter（`org.junit.jupiter:junit-jupiter` 和 `junit-jupiter-engine`）是 JUnit 5 的核心测试 API，Iceberg 项目使用它编写和运行单元测试与集成测试。这是项目最核心的测试框架依赖。

本次从 5.13.4 升至 5.14.1，属于次版本（minor）升级。值得注意的是，提交 2813 已将 JUnit Platform 升至 1.14.1，本提交将 Jupiter API 对齐到 5.14.x 系列，保持平台层与 API 层版本一致。JUnit 5.14 可能引入新的断言 API、测试扩展点或行为变更，需关注测试是否受影响。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的 `junit` 版本属性，从 `5.13.4` 更新为 `5.14.1`。该属性统一管理 `junit-jupiter` 和 `junit-jupiter-engine` 等制品的版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 JUnit Jupiter 版本，与已升级的 JUnit Platform 1.14.1 配套。

**工作逻辑**：将 `junit = "5.13.4"` 改为 `junit = "5.14.1"`。注意此时 `junit-platform` 已在前序提交 2813 中升级为 `1.14.1`，两者版本号对齐确保 JUnit 测试框架各层兼容。该属性被版本目录中 junit-jupiter 和 junit-jupiter-engine 依赖引用。

## 总结

将 JUnit Jupiter 从 5.13.4 升级到 5.14.1，属于次版本升级，与提交 2813（junit-platform 1.14.1）配套保持 JUnit 5 测试框架版本一致。这是 Dependabot 批量依赖升级（2811-2819）的一部分，确保项目使用最新测试框架功能。
