# 提交 0387：Build: Define strict version for Flink / Jackson / Hive2 / Tez 0.8 (#9484)

## 提交信息

- **序号**：0387
- **哈希**：b6cefe5e1444645b2d29010be3167161e2aa093a
- **短哈希**：b6cefe5e1
- **日期**：2024-01-18（Thu Jan 18 19:49:05 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Build: Define strict version for Flink / Jackson / Hive2 / Tez 0.8 (#9484)
- **PR/Issue**：#9484

## 总体目的

这个提交的目的非常聚焦：把 Iceberg 构建依赖中四个家族（Flink 1.16/1.17/1.18、Jackson 2.11–2.15、Hive2、Tez 0.8）从"区间式严格版本 + 偏好版本"的 rich version 写法收紧为"单一严格版本"。原先的写法形如 `flink116 = { strictly = "[1.16, 1.17[", prefer = "1.16.2" }`，表示"接受 1.16.x 任何版本，但优先用 1.16.2"；改成 `flink116 = { strictly = "1.16.2" }` 后，意味着只接受确切的 1.16.2，不再允许向上漂移到 1.16.3 或更高的 patch 版本。

之所以要做这种收紧，是因为此前的区间写法虽然名义上"严格"，但严格的是一个半开区间，Dependabot / Renovate 等自动化依赖管理工具在升级 patch 时仍可能把版本拉到区间内的更高 patch，从而引入未经 CI 充分验证的版本组合。Iceberg 对这些库的兼容性非常敏感：Flink 不同 minor 之间 API 不兼容；Jackson 不同 minor 之间序列化行为可能变化；Hive2 / Tez 0.8 与 Hadoop 之间的依赖链一旦漂移就可能在集成测试里爆出 NoSuchMethod 类错误。把版本钉死到具体 patch，可以让 CI 在每一次构建里跑的都是确定性的版本组合，自动化工具升级时也必须显式提交一个"改这个 patch 号"的 PR，从而被审阅、被测试。

另一层意图是降低社区贡献者的构建不确定性。区间版本在不同机器上可能因为本地缓存或传递依赖而解析到不同版本，导致"我这能跑你那不能跑"的诡异情况；钉死到具体 patch 后所有人构建的都是同一组版本。

## 如何达成设计目的

实现路径极其直接：在 `gradle/libs.versions.toml` 这一 Version Catalog 单一来源文件中，把上述四个家族共 8 条 `strictly = "[a, b[", prefer = "x.y.z"` 形式的 rich version 替换为 `strictly = "x.y.z"`，并保留对 rich version 说明文档的引用（注释里指向 Gradle 官方 rich versions 文档）。同时新增一行注释 `# see https://docs.gradle.org/current/userguide/rich_versions.html on how to configure rich versions.`，为后续维护者提供配置参考。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：把 Flink / Jackson / Hive2 / Tez 0.8 的依赖版本从区间式严格版本收紧为单一严格版本，并在文件顶部追加 rich version 官方文档链接。

**工作逻辑**：
- 顶部注释区新增 `# see https://docs.gradle.org/current/userguide/rich_versions.html on how to configure rich versions.`，作为 rich version 配置的导引。
- Flink 家族三处：
  - `flink116 = { strictly = "[1.16, 1.17[", prefer = "1.16.2"}` → `flink116 = { strictly = "1.16.2"}`
  - `flink117 = { strictly = "[1.17, 1.18[", prefer = "1.17.1"}` → `flink117 = { strictly = "1.17.1"}`
  - `flink118 = { strictly = "[1.18, 1.19[", prefer = "1.18.1"}` → `flink118 = { strictly = "1.18.1"}`
  去掉了 `prefer` 字段，并把 `strictly` 从半开区间改成具体版本号。这意味着如果传递依赖试图把 Flink 拉到 1.16.3 之类的版本，构建会直接失败而非默认接受。
- Hive2：`hive2 = { strictly = "[2, 3[", prefer = "2.3.9"}` → `hive2 = { strictly = "2.3.9"}`，原区间允许任何 2.x，现在钉死到 2.3.9。注释 `# see rich version usage explanation above` 保留。
- Jackson 家族五处（2.11–2.15）：
  - `jackson211 = { strictly = "[2.11, 2.12[", prefer = "2.11.4"}` → `jackson211 = { strictly = "2.11.4"}`
  - `jackson212 = { strictly = "[2.12, 2.13[", prefer = "2.12.3"}` → `jackson212 = { strictly = "2.12.3"}`
  - `jackson213 = { strictly = "[2.13, 2.14[", prefer = "2.13.4"}` → `jackson213 = { strictly = "2.13.4"}`
  - `jackson214 = { strictly = "[2.14, 2.15[", prefer = "2.14.2"}` → `jackson214 = { strictly = "2.14.2"}`
  - `jackson215 = { strictly = "[2.15, 2.16[", prefer = "2.15.2"}` → `jackson215 = { strictly = "2.15.2"}`
  这五个版本号是为了对应 Spark 不同版本各自绑定的 Jackson 版本，Iceberg 必须为每个 Spark 版本提供匹配的 Jackson，否则序列化兼容性会出问题。钉死后每个 Jackson 版本都是确定值。
- Tez 0.8：`tez08 = { strictly = "[0.8, 0.9[", prefer = "0.8.4"}` → `tez08 = { strictly = "0.8.4"}`，注释保留。

整体逻辑就是把"严格但允许 patch 漂移"改成"严格到具体 patch"，配合原有注释说明为何要使用 rich version 语法（即使现在只剩 `strictly`，仍保留 rich version 写法以便未来需要时可再加 `prefer` / `require` 等约束）。

## 小结

这是一个纯构建配置补丁，零产品代码改动，但影响面很大：它把 Iceberg 在 Flink / Jackson / Hive2 / Tez 0.8 四个家族上的依赖版本完全钉死，消除了自动化工具和传递依赖导致的版本漂移风险，提升了构建的可重复性与 CI 的确定性。同时通过保留 rich version 注释和补充官方文档链接，让后续维护者依然能够理解这种写法的来由，并在需要时灵活地重新引入区间约束。这种"收紧到确定 patch + 保留可扩展的 rich version 框架"是大型多模块项目在依赖管理上的常见最佳实践。
