# 提交分析：Build: Bump com.google.errorprone:error_prone_annotations (#10236)

## 提交信息

| 项目 | 内容 |
| --- | --- |
| 哈希 | `a55797d4ac8724befbfa6e27ff1c8591c572bdb3` |
| 短哈希 | `a55797d4a` |
| 作者 | dependabot[bot] |
| 提交时间 | 2024-04-29 08:46:13 +0200 |
| 提交标题 | Build: Bump com.google.errorprone:error_prone_annotations (#10236) |
| 提交正文 | Bumps com.google.errorprone:error_prone_annotations from 2.26.1 to 2.27.0（含上游 release notes / commits 链接，以及 updated-dependencies 元数据） |
| 变更范围 | 1 个文件，1 行新增，1 行删除 |

## 总体目的

由 dependabot 自动发起的依赖升级，将 `com.google.errorprone:error_prone_annotations` 从 `2.26.1` 升级到 `2.27.0`，跟随上游 error-prone 项目的发布节奏获取修复与改进。这是一次 minor（次版本）级别的升级（`2.26.1 → 2.27.0`）。

## 如何达成设计目的

`error_prone_annotations` 在 Iceberg 的 Gradle 版本目录 `gradle/libs.versions.toml` 中以 `errorprone-annotations` 为键集中声明，供各模块按需引用。本次直接修改该版本号字面量，使所有引用该坐标的模块在解析时统一拉取 `2.27.0`，无需改动任何引用处或业务代码。

`error_prone_annotations` 提供的是编译期/静态分析相关的注解（如 `@CanIgnoreReturnValue`、`@CheckReturnValue` 等），属于纯注解依赖，体积小、运行时副作用低。

## 修改详情

### `gradle/libs.versions.toml`

版本目录中 error-prone 注解依赖的版本号更新：

```toml
-errorprone-annotations = "2.26.1"
+errorprone-annotations = "2.27.0"
```

- 仅此一行变更，把 `errorprone-annotations` 从 `2.26.1` 提升到 `2.27.0`。
- 同文件内未改动任何引用该变量的其它条目，说明本次升级不涉及 API 使用方式的调整。
- `2.26.1 → 2.27.0` 为 minor 升级，error-prone 项目通常保持注解 API 的向后兼容；但 minor 升级相比 patch 升级存在稍高的行为变更可能性（如新增/调整某些诊断规则），需关注上游 release notes。

## 小结

### 成效
- 让 Iceberg 依赖的 error-prone 注解跟随上游最新 minor 版本，纳入 2.27.0 的改进。
- 改动面极小（单行版本号），且该依赖为注解型，对运行时几乎无影响。

### 影响范围
- 影响编译期注解处理与（若启用 error-prone 编译器插件时的）静态分析诊断行为。
- 不影响 Iceberg 自身的公开 API 或运行时逻辑；产物的运行时依赖中会带上 `2.27.0` 版本的注解包。

### 回迁注意事项（1.4.x ← main）
- 单行版本号升级，回迁无冲突风险，直接同步 `gradle/libs.versions.toml` 中 `errorprone-annotations` 一行即可。
- 由于是 minor 升级，回迁后建议执行一次完整编译，确认 1.4.x 分支下未触发新的 error-prone 诊断告警或编译失败。
- 若 1.4.x 分支对 error-prone 注解的使用方式有差异（例如固定使用了某个旧注解语义），需结合上游 2.27.0 release notes 复核。
