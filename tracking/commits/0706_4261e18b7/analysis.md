# 提交 0706：Build: Bump gradle-jmh-report 依赖版本

## 提交信息
- **序号**：0706 / 4088
- **哈希**：4261e18b7f739a48b1cc38b728bdbd3ebfebf193
- **短哈希**：4261e18b7
- **日期**：2024-04-21
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump gradle.plugin.io.morethan.jmhreport:gradle-jmh-report (#10193)。Bumps gradle.plugin.io.morethan.jmhreport:gradle-jmh-report from 0.9.0 to 0.9.6.
- **PR/Issue**：#10193

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，目的是将 JMH（Java Microbenchmark Harness）报告生成 Gradle 插件 `gradle-jmh-report` 从 0.9.0 升级到 0.9.6。

JMH 报告插件用于在运行 JMH 基准测试后生成可视化的 HTML 性能报告。Iceberg 项目在 `build.gradle` 的 `buildscript` 依赖中引入该插件，以便为性能基准测试结果生成报告。

Dependabot 会定期扫描项目的依赖文件，发现可升级的版本后自动创建 PR。这是一次 semver-patch（补丁版本）升级，从 0.9.0 到 0.9.6，属于小版本迭代，通常只包含 bug 修复和小的功能改进，理论上不会引入破坏性变更。

## 如何达成设计目的

通过直接修改 `build.gradle` 文件中 `buildscript` 块的 `dependencies` 段，将 `gradle-jmh-report` 插件的版本号字符串从 `0.9.0` 改为 `0.9.6`。这是一行式的最小化改动，符合 Dependabot 自动升级依赖的标准做法。

## 修改详情

### `build.gradle`
**修改目的**：升级 JMH 报告 Gradle 插件版本。

**工作逻辑**：在 `buildscript { dependencies { ... } }` 块中，将 classpath 依赖项 `gradle.plugin.io.morethan.jmhreport:gradle-jmh-report` 的版本从 `0.9.0` 提升到 `0.9.6`。该插件声明在 classpath 中用于在构建脚本运行时加载 JMH 报告生成任务。改动前后对比：

```
- classpath 'gradle.plugin.io.morethan.jmhreport:gradle-jmh-report:0.9.0'
+ classpath 'gradle.plugin.io.morethan.jmhreport:gradle-jmh-report:0.9.6'
```

## 小结
- **成效**：成功将依赖升级到新版本，补丁版本升级风险低。
- **影响范围**：仅影响项目的构建配置（JMH 基准测试报告生成功能），不影响生产代码逻辑。
- **回迁到 1.4.x 的注意事项**：无特殊注意点。可直接应用。若 1.4.x 分支的 `build.gradle` 中该依赖版本仍为 0.9.0，则直接套用即可；若已被升级到其他版本，则需确认版本兼容性。
