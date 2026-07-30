# 提交 3876：Build: Bump calcite from 1.41.0 to 1.42.0 (#16809)

## 提交信息

- **序号**：3876 / 4088
- **哈希**：d48d871c2220ba04d0e6446f3863e5330f529d1a
- **短哈希**：d48d871c2
- **日期**：2026-06-14 00:07:17 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump calcite from 1.41.0 to 1.42.0 (#16809)
- **PR/Issue**：#16809

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，目的是将 Apache Calcite 库从 1.41.0 升级到 1.42.0。Apache Calcite 是一个动态数据管理框架，Iceberg 在 Spark 等模块中使用它来进行 SQL 解析和查询优化。

Dependabot 会定期扫描项目依赖，并在发现新版本时自动创建 PR 进行升级。此类升级的主要动机是保持依赖的最新状态，获取上游的 bug 修复、性能改进和新特性，同时降低未来升级的技术债务。

Calcite 从 1.41.0 升级到 1.42.0 是一个 semver-minor 版本升级，意味着引入了新功能但应保持向后兼容。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `calcite` 版本变量从 `1.41.0` 改为 `1.42.0`。该版本变量被 `org.apache.calcite:calcite-core` 和 `org.apache.calcite:calcite-druid` 两个依赖共享，因此一处修改即可同步升级两个组件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 calcite 版本变量。

**工作逻辑**：
```toml
-calcite = "1.41.0"
+calcite = "1.42.0"
```
仅修改版本号定义，Gradle 会自动将此版本应用到所有引用该变量的依赖项（calcite-core 和 calcite-druid）。

## 总结

这是一次常规的依赖维护升级，将 Apache Calcite 从 1.41.0 提升到 1.42.0，属于 semver-minor 升级。通过版本目录的集中管理，单点修改即可完成升级，体现了 Iceberg 项目依赖管理的规范性。
