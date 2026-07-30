# 提交 0061：Build: add gradle configuration to enforce reproducible build (#8826)

## 提交信息

- **序号**：0061 / 4088
- **哈希**：05c789beee1afd3f043188fcaf02e11960920fc6
- **短哈希**：05c789bee
- **日期**：2023-10-16
- **作者**：JB Onofré
- **提交说明**：Build: add gradle configuration to enforce reproducible build (#8826)
- **PR/Issue**：#8826

## 总体目的

该提交为 Iceberg 的 Gradle 构建脚本增加"可复现构建（reproducible build）"配置，确保所有归档类任务（`AbstractArchiveTask`，例如 `jar`、`zip`、`tar`、源码包、分发包等）产出的产物在字节层面是确定的——同一份源码在任何时间、任何机器上构建都得到相同的产物。

可复现构建是 Apache 项目发布流程中越来越被重视的一环。如果归档文件里包含构建时的时间戳、或文件条目顺序取决于文件系统遍历顺序（不同 OS/文件系统顺序不同），那么即使源码完全一致，两次构建产生的 jar/zip 也不同。这会带来三个问题：(1) 无法通过对比 hash 验证发布产物确实来自公开源码，削弱社区对发布产物的信任；(2) CI 缓存命中率下降，因为任何变化都会让缓存失效；(3) 下游依赖方反复下载相同版本但内容不同的产物。本提交通过两个 Gradle 属性一次性消除这两类不确定性来源。

## 如何达成设计目的

设计上非常简洁：在根 `build.gradle` 中、JDK 版本校验之后、`git-properties` 插件应用之前，插入一个对全部 `AbstractArchiveTask` 子类型任务的 `configureEach` 配置块，设置两个标准 Gradle 属性：

- `preserveFileTimestamps = false`：归档中不保留文件时间戳，避免同一源码在不同时间构建产生不同产物；
- `reproducibleFileOrder = true`：归档内文件按确定性顺序排列，避免因文件系统遍历顺序差异导致的条目顺序不同。

这两行配置会作用于项目中所有模块的 jar、源码 jar、javadoc jar、distribution zip/tar 等所有归档任务，无需在每个子模块单独配置。

## 修改详情

### [build.gradle](file:///Users/fengxiaohang/trae/iceberglearn/build.gradle)

**修改目的**：在根构建脚本中为所有归档任务统一启用可复现构建。

**工作逻辑**：在 JDK 版本检查块（JDK 8/11/17 之外的版本抛 `GradleException`）之后、`apply plugin: 'com.gorylenko.gradle-git-properties'` 之前，新增 5 行：

```groovy
tasks.withType(AbstractArchiveTask).configureEach {
  preserveFileTimestamps = false
  reproducibleFileOrder = true
}
```

`tasks.withType(AbstractArchiveTask).configureEach { ... }` 是 Gradle 的延迟配置惯用法：`configureEach` 会在每个匹配任务创建时（包括子项目中的任务）应用配置块，即便该任务在此刻尚未创建也能正确生效，避免使用 `all` 在配置时立即遍历可能造成的性能问题或顺序依赖。`AbstractArchiveTask` 是 `Jar`、`Zip`、`Tar` 等归档任务的公共父类，覆盖面足够广。两个属性都是 Gradle 内置的标准可复现构建开关，无需额外插件。

## 小结

该提交以最小代价（5 行 Groovy）让 Iceberg 所有归档产物满足可复现构建要求，是项目发布合规性与产物可信度的基础设施改进。
