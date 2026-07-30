# 提交 0589：Build: Align Jackson versions

## 提交信息

- **序号**：0589 / 4088
- **哈希**：43c3397528101859250160f123a0749bae79fb4d
- **短哈希**：43c339752
- **日期**：2024-03-12 17:51:03 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build: Align Jackson versions (#9925)
- **PR/Issue**：#9925

## 总体目的

Apache Iceberg 在构建中使用 Jackson 作为 JSON 处理库，用于表元数据、REST catalog 消息、manifest 序列化等核心场景。Jackson 由多个独立模块组成（`jackson-core` 流式 API、`jackson-databind` 数据绑定、`jackson-annotations` 注解集、`jackson-dataformat-xml` XML 后端等），这些模块之间存在二进制兼容性约束——同一组 Jackson 模块必须使用相同版本，否则 `jackson-databind` 在运行时可能调用 `jackson-core`/`jackson-annotations` 中不存在或不兼容的 API，导致 `NoSuchMethodError`、`AbstractMethodError` 等运行时故障。

本提交要解决的问题是：仓库中存在 Jackson 版本不一致——

- `jackson-bom = "2.14.2"`：BOM（Bill of Materials）版本，通过 `platform()` 机制管理 `jackson-core`、`jackson-databind` 等模块的版本
- `jackson-annotations = "2.16.0"`：单独定义的 `jackson-annotations` 版本，**高于** BOM 版本 2.14.2
- `jackson-dataformat-xml = "2.16.1"`：单独定义的 `jackson-dataformat-xml` 版本，**高于** BOM 版本 2.14.2

这三个版本不在同一条线上：`jackson-annotations` 与 `jackson-dataformat-xml` 被"脱离 BOM 单独升级"到了 2.16.x，而 BOM 仍停留在 2.14.2。这意味着当 `jackson-databind:2.14.2` 运行时，其依赖的 `jackson-annotations` 可能被解析为 2.16.0，二者之间的内部契约不匹配（Jackson 的 minor 版本之间虽力求兼容，但 2.14→2.16 跨了两个 minor，存在新增/变更的内部 SPI 接口）。

本提交的目标是把所有 Jackson 模块统一对齐到 BOM 管理的 2.14.2 版本，消除版本漂移，使 Jackson 模块间保持二进制一致。

## 如何达成设计目的

设计思路是**用 Gradle 的 BOM platform 机制统一管控 Jackson 模块版本**，移除"单独定义版本 + 单独引用别名"的旧模式：

1. **删除脱离 BOM 的版本声明与库别名**：从 `gradle/libs.versions.toml` 中移除 `jackson-annotations = "2.16.0"`、`jackson-dataformat-xml = "2.16.1"` 两个版本变量，以及对应的 `jackson-annotations`、`jackson-dataformat-xml` 两个库别名。这样这些模块不再有"自带版本"，只能通过 BOM 获取版本。

2. **在 build.gradle 中改用 `platform(libs.jackson.bom)` + 无版本依赖声明**：把原先直接引用 `libs.jackson.dataformat.xml`（带版本 2.16.1）或 `libs.jackson.annotations`（带版本 2.16.0）的写法，改为先 `platform(libs.jackson.bom)` 导入 BOM 平台、再以字符串形式声明模块坐标（不带版本），让 BOM 决定版本。这与仓库中其它模块（如 `iceberg-api`、`iceberg-core`、`iceberg-rest`）已有的 `platform(libs.jackson.bom)` + `"com.fasterxml.jackson.core:jackson-databind"` 模式完全一致，是仓库既有的 Jackson 依赖约定。

这种"platform + 无版本声明"的写法是 Gradle 推荐的 BOM 消费方式：BOM 平台会约束所有匹配的 Jackson 依赖到 BOM 声明的版本（2.14.2），即使传递依赖引入了不同版本的 Jackson，也会被 BOM 对齐。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：移除脱离 BOM 的 Jackson 版本声明与对应库别名，使 `jackson-annotations` 与 `jackson-dataformat-xml` 不再有独立版本源。

**工作逻辑**：

`[versions]` 区块删除两行：
```toml
jackson-annotations = "2.16.0"          # 删除
jackson-dataformat-xml = "2.16.1"       # 删除
```

`[libraries]` 区块删除两个别名：
```toml
jackson-annotations = { module = "com.fasterxml.jackson.core:jackson-annotations", version.ref = "jackson-annotations" }       # 删除
jackson-dataformat-xml = { module = "com.fasterxml.jackson.dataformat:jackson-dataformat-xml", version.ref = "jackson-dataformat-xml" }  # 删除
```

删除后，`jackson-annotations` 与 `jackson-dataformat-xml` 在版本目录中不再有入口，使用方必须通过 BOM platform 机制以无版本字符串声明依赖。

注意：仓库中同时维护着供 Spark 模块使用的多版本 Jackson（`jackson211`/`jackson212`/`jackson213`/`jackson214`/`jackson215` 及对应 BOM 别名），这些是给不同 Spark 版本（3.2~3.5）兼容使用的独立 Jackson 版本线，**不受本提交影响**，仍保留各自的 `strictly` rich version 约束。

### `build.gradle`（iceberg-aliyun 模块，测试作用域）

**修改目的**：把 aliyun 模块测试中对 `jackson-dataformat-xml` 的依赖从"别名引用（版本 2.16.1）"改为"BOM platform + 无版本声明"，使其对齐到 2.14.2。

**工作逻辑**：

```groovy
// 旧
testImplementation libs.jackson.dataformat.xml
// 新
testImplementation platform(libs.jackson.bom)
testImplementation "com.fasterxml.jackson.dataformat:jackson-dataformat-xml"
```

`platform(libs.jackson.bom)` 在测试 classpath 上导入 Jackson BOM 2.14.2 作为版本约束平台；随后声明的 `"com.fasterxml.jackson.dataformat:jackson-dataformat-xml"` 不带版本号，Gradle 会从 BOM 平台查找该模块的版本，解析为 2.14.2。这样 aliyun 测试中使用的 XML 处理库与主代码使用的 `jackson-core`/`jackson-databind`（同为 2.14.2）保持一致。

`jackson-dataformat-xml` 在 aliyun 模块的角色：aliyun OSS（对象存储服务）的 API 返回 XML 格式响应，测试中需要用 Jackson 的 XML 后端解析这些响应。

### `hive3/build.gradle`（iceberg-hive3 模块，测试作用域）

**修改目的**：把 hive3 模块测试中对 `jackson-annotations` 的直接依赖替换为仅导入 BOM platform，使传递引入的 Jackson 依赖对齐到 2.14.2。

**工作逻辑**：

```groovy
// 旧
testImplementation libs.jackson.annotations
// 新
testImplementation platform(libs.jackson.bom)
```

注意此处与 aliyun/mr 模块的区别：hive3 删除了对 `jackson-annotations` 的直接依赖声明，仅保留 `platform(libs.jackson.bom)`。这意味着 `jackson-annotations` 不再是 hive3 测试的直接依赖，而是通过 BOM 平台约束传递依赖中的 Jackson 模块版本。如果 hive3 测试代码确实需要 `jackson-annotations` 的注解类，这些类会通过 `jackson-databind` 的传递依赖引入（databind 依赖 annotations），版本被 BOM 对齐为 2.14.2。

`jackson-annotations` 在 Hive3 集成中的角色：Hive 的序列化/元数据操作可能用到 Jackson 注解（如 `@JsonProperty`、`@JsonIgnore`）来控制 JSON 字段映射。Hive3 模块测试中通过 Jackson 注解配置序列化行为。

### `mr/build.gradle`（iceberg-mr 模块，测试作用域）

**修改目的**：把 mr 模块测试中对 `jackson-annotations` 的依赖从"别名引用（版本 2.16.0）"改为"BOM platform + 无版本声明"，使其对齐到 2.14.2。

**工作逻辑**：

```groovy
// 旧
testImplementation libs.jackson.annotations
// 新
testImplementation platform(libs.jackson.bom)
testImplementation "com.fasterxml.jackson.core:jackson-annotations"
```

与 aliyun 模块的改法对称：导入 BOM platform 后以无版本字符串重新声明 `jackson-annotations` 依赖，版本由 BOM 决定为 2.14.2。与 hive3 不同的是 mr 仍保留对 `jackson-annotations` 的直接依赖声明（因为 mr 测试代码直接使用 Jackson 注解，需要显式在 classpath 上）。

mr（MapReduce）模块的角色：为 Hadoop MapReduce 作业提供 Iceberg 输入/输出格式支持，测试中用 Jackson 注解处理序列化配置。

## 小结

本提交通过"删除脱离 BOM 的版本声明 + 统一改用 `platform(libs.jackson.bom)` 无版本声明"的方式，把 `jackson-annotations`（2.16.0→2.14.2）与 `jackson-dataformat-xml`（2.16.1→2.14.2）对齐到与 `jackson-bom`/`jackson-core`/`jackson-databind` 一致的 2.14.2 版本，消除了 Jackson 模块间的版本漂移与潜在二进制不兼容风险。改动涉及 4 个文件、5 行新增 / 7 行删除，影响范围限于 aliyun、hive3、mr 三个模块的测试作用域，不影响主代码的运行时行为。

回迁到 1.4.x 分支的注意事项：需先检查 1.4.x 分支的 `gradle/libs.versions.toml` 中是否存在相同的版本漂移（`jackson-annotations`/`jackson-dataformat-xml` 脱离 BOM 单独定义）。若存在，**建议回迁**以避免潜在的 Jackson 二进制不兼容问题。回迁时需注意 1.4.x 分支的 `jackson-bom` 版本可能与 main（2.14.2）不同，关键是让所有 Jackson 模块跟随 BOM，而非追求与 main 相同的绝对版本号。若 1.4.x 分支已无此漂移（例如已自行修复），则无需回迁。
