# 提交 2202：Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#12684)

## 提交信息

- **序号**：2202 / 4088
- **哈希**：4755b76d2d1edeb727657b0d9941f7df7066f7c9
- **短哈希**：4755b76d2
- **日期**：2025-06-03 21:48:42 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#12684)
- **PR/Issue**：#12684

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 `org.apache.hadoop.thirdparty:hadoop-shaded-guava` 从 1.3.0 升级到 1.4.0。hadoop-shaded-guava 是 Hadoop 第三方依赖中提供的 Guava 重定位（shaded）版本，用于避免 Guava 版本冲突。Iceberg 的 kafka-connect 模块依赖此库。这是一次次版本（minor）升级，可能包含新的重定位版本或修复，通常不涉及破坏性变更。Dependabot 自动检测到新版本可用后提交了此升级。

## 如何达成设计目的

- 在 `kafka-connect/build.gradle` 构建文件中，将 `hadoop-shaded-guava` 的版本号从 `1.3.0` 修改为 `1.4.0`。

## 修改详情

### `kafka-connect/build.gradle` (修改, +1/-1 lines)

**修改目的**：升级 hadoop-shaded-guava 依赖版本。

**工作逻辑**：将 kafka-connect 模块构建文件中 hadoop-shaded-guava 的版本定义从 `1.3.0` 改为 `1.4.0`，这是单个版本号字符串的替换。注意此依赖仅在 kafka-connect 模块的 build.gradle 中声明，而非全局版本目录。

## 总结

这是一次常规的依赖版本升级（1.3.0 → 1.4.0），由 Dependabot 自动完成，属于次版本维护更新，用于获取 hadoop-shaded-guava 上游的改进，仅影响 kafka-connect 模块，对项目核心功能无影响。
