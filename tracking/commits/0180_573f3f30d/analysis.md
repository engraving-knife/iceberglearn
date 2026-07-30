# 提交 0180：Build: Bump com.fasterxml.jackson.core:jackson-annotations (#9106)

## 提交信息

- **序号**：0180 / 4088
- **哈希**：573f3f30da1d9739547f8f98c4a0d61613d26412
- **短哈希**：573f3f30d
- **日期**：2023-11-19 10:39:44 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.fasterxml.jackson.core:jackson-annotations (#9106)
- **PR/Issue**：#9106

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，针对 Iceberg Gradle 版本目录（version catalog）中的 `jackson-annotations`。Jackson 是 Java 生态中最主流的 JSON（及多格式）序列化/反序列化库，`jackson-annotations` 提供了 `@JsonProperty`、`@JsonIgnore` 等注解，是 Iceberg 在序列化元数据、REST API 模型、配置对象时广泛依赖的基础注解包。

本次升级把 `jackson-annotations` 从 2.15.3 提升到 2.16.0（semver minor 升级），属于常规依赖维护：跟随 Jackson 2.16 主线获取新注解能力、bug 修复与 JDK 兼容性改进。保持 Jackson 注解包与上游主线同步，有助于 Iceberg 在未来引入依赖 2.16 特性的下游库时减少版本摩擦。

## 如何达成设计目的

Dependabot 通过对 `gradle/libs.versions.toml` 单行修改完成升级：把 `jackson-annotations` 的版本字符串从 `2.15.3` 改为 `2.16.0`。该文件是 Gradle 7+ 引入的版本目录，集中管理所有依赖版本，构建脚本通过 `libs.jackson.annotations` 之类的别名引用此版本。Dependabot 仅更新这一处声明，所有引用该别名的模块在下次构建时自动应用新版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `jackson-annotations` 版本钉从 2.15.3 提升到 2.16.0。

**工作逻辑**：版本目录中 `jackson-annotations = "2.15.3"` 改为 `jackson-annotations = "2.16.0"`。值得注意的上下文：同一文件中其它 Jackson 相关版本未受影响，例如 `jackson-bom = "2.14.2"`、`jackson-dataformat-xml = "2.9.9"` 以及为 Spark 兼容性保留的 rich version `jackson211 = { strictly = "[2.11, 2.12[", prefer = "2.11.4" }` 等保持原样。这说明本次升级仅作用于直接由 `jackson-annotations` 别名引用的注解包，不会改变 BOM 控制下的其它 Jackson 组件版本，也不影响为适配 Spark 而特别钉扎的 Jackson 2.11 版本线。

## 小结

一次常规的 Dependabot 依赖升级，把 Gradle 版本目录中独立钉扎的 `jackson-annotations` 升级到 2.16.0，保持 Iceberg 注解依赖与 Jackson 上游主线同步。
