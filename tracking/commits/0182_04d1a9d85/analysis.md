# 提交 0182：Build: Bump com.fasterxml.jackson.dataformat:jackson-dataformat-xml (#9107)

## 提交信息

- **序号**：0182 / 4088
- **哈希**：04d1a9d85e2732d885c435085e53b7fc7318379a
- **短哈希**：04d1a9d85
- **日期**：2023-11-19 10:40:46 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.fasterxml.jackson.dataformat:jackson-dataformat-xml (#9107)
- **PR/Issue**：#9107

## 总体目的

这是一个由 GitHub Dependabot 自动生成的依赖升级提交，将 [jackson-dataformat-xml](https://github.com/FasterXML/jackson-dataformat-xml) 从 2.9.9 升级到 2.16.0。

jackson-dataformat-xml 是 Jackson 生态中的 XML 数据格式扩展模块，提供 `XmlMapper` 等类，使 Jackson 能够将 Java 对象与 XML 文档之间进行序列化/反序列化。在 Iceberg 项目中，该依赖以 `testImplementation` 方式引入（见 `build.gradle` 第 432 行 `testImplementation libs.jackson.dataformat.xml`），即仅在测试代码中使用，主要用于测试场景下 XML 格式数据的处理与验证。

值得注意的是，此次升级的版本跨度非常大：从 2.9.9 直接跃升到 2.16.0。2.9.9 是 2019 年发布的版本，已经严重落后于项目其余 Jackson 依赖（同一文件中 `jackson-annotations = "2.16.0"`，`jackson-bom = "2.14.2"`）。Dependabot 将其归类为 `version-update:semver-minor`（次要版本升级），依赖类型为 `direct:production`。升级到 2.16.0 使 jackson-dataformat-xml 与项目中已有的 `jackson-annotations 2.16.0` 版本对齐，避免了同一 Jackson 生态中不同组件版本不一致可能导致的 API 兼容性问题和类路径冲突。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 jackson-dataformat-xml 的版本声明，从 `2.9.9` 改为 `2.16.0`。该版本目录中同时定义了版本号（第 35 行 `jackson-dataformat-xml = "2.16.0"`）和库别名（第 174 行 `jackson-dataformat-xml = { module = "com.fasterxml.jackson.dataformat:jackson-dataformat-xml", version.ref = "jackson-dataformat-xml" }`），项目根 `build.gradle` 通过 `libs.jackson.dataformat.xml` 引用该别名，将其作为测试依赖引入。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 jackson-dataformat-xml 依赖版本从 2.9.9 升级到 2.16.0。

**工作逻辑**：该文件是 Gradle 的版本目录，集中定义项目所有依赖的版本号。修改发生在第 35 行附近，将 `jackson-dataformat-xml = "2.9.9"` 改为 `jackson-dataformat-xml = "2.16.0"`。此版本别名在 `build.gradle` 中被引用为 `testImplementation libs.jackson.dataformat.xml`，仅用于测试代码。升级后，jackson-dataformat-xml 的版本与同文件中 `jackson-annotations = "2.16.0"` 保持一致，消除了此前 2.9.9 与 2.16.0 之间的版本断层，使 Jackson XML 扩展模块与项目其余 Jackson 组件处于同一大版本线（2.16.x），减少了潜在的 API 不兼容和依赖冲突风险。

## 小结

此次升级将严重滞后的 jackson-dataformat-xml 测试依赖从 2.9.9 一步升级到 2.16.0，与项目其余 Jackson 组件版本对齐，降低了测试基础设施中的版本碎片化风险。
