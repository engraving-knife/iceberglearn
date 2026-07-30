# 提交 1260：Build: Bump com.palantir.baseline:gradle-baseline-java (#11362)

## 提交信息

- **序号**：1260 / 4088
- **哈希**：de48a74263448a191344d411004db67900f528a8
- **短哈希**：de48a7426
- **日期**：2024-10-21（Mon Oct 21 09:06:59 2024 +0200）
- **作者**：dependabot[bot]；共同作者：dependabot[bot]
- **提交说明**：Build: Bump com.palantir.baseline:gradle-baseline-java (#11362)
- **PR/Issue**：#11362

## 总体目的

Iceberg 使用 Palantir 的 gradle-baseline-java 插件作为代码质量基线工具，它集成了 Error Prone、静态分析、代码风格约束等检查规则，在编译期对 Java 代码施加一致的工程规范。dependabot 检测到 build.gradle 的 buildscript classpath 中声明的版本 5.69.0 已落后，发起本次升级至 5.72.0（semver minor 升级，跨 5.70.0/5.71.0/5.72.0 三个次版本）。

升级目的是获取 gradle-baseline 5.7x 系列新增的检查规则、缺陷修复与对依赖（如新版 Error Prone）的兼容性支持，保持代码质量基线工具处于受维护状态，避免因工具版本过旧而漏检新问题或与新依赖不兼容。

## 如何达成设计目的

单点修改 build.gradle 的 buildscript 依赖块：将 classpath 'com.palantir.baseline:gradle-baseline-java:5.69.0' 改为 5.72.0。该插件在 buildscript 阶段加载，影响整个构建的代码检查配置。本次升级未伴随任何源码或测试调整，说明 5.69.0→5.72.0 的规则变化未触发既有代码的新违规（或新规则属可选/非破坏性）。

提交说明保留了 dependabot 生成的标准元信息：依赖类型 direct:production、更新类型 version-update:semver-minor，并附上游 release notes、changelog、commits 对比链接，便于维护者评估升级内容。

## 修改详情

### `build.gradle`

**修改目的**：升级 gradle-baseline-java 插件版本。

**工作逻辑**：在 buildscript { dependencies { ... } } 块中，将

```groovy
classpath 'com.palantir.baseline:gradle-baseline-java:5.69.0'
```

改为

```groovy
classpath 'com.palantir.baseline:gradle-baseline-java:5.72.0'
```

该 classpath 声明使 gradle-baseline-java 插件在构建脚本类路径中可用，后续各子模块通过 apply plugin 应用其检查能力。版本提升后，编译期会按 5.72.0 的规则集对 Iceberg 全部 Java 源码与测试施加静态检查。

## 小结

- **成效**：gradle-baseline-java 从 5.69.0 升级到 5.72.0，获取 5.7x 系列的规则改进与缺陷修复，保持代码质量基线工具受维护；本次升级未引入任何源码改动，表明既有代码在新规则下仍合规。
- **影响范围**：仅 build.gradle 1 个文件、1 行版本号改动。属构建工具链升级，不改变任何产品代码、API 或运行时行为；产物功能不受影响。
- **回迁到 1.4.x 的注意事项**：构建插件升级属低风险变更，对发布产物无功能影响。1.4.x **可回迁**，但需注意：1) 升级后新版 baseline 可能引入更严格的检查规则，1.4.x 的既有代码（与 main 有差异）可能触发新违规导致编译失败，回迁前应在 1.4.x 环境完整构建验证；2) gradle-baseline 5.7x 可能要求更高版本的 Gradle 或 JDK，需与 1.4.x 的构建工具链兼容；3) 若 1.4.x 已有特定的 baseline 配置（如豁免某些规则），升级后需确认配置仍生效。本质上这是开发期工具链维护，与 1.4.x 的运行时功能无关，回迁与否取决于 1.4.x 是否需要继续维护构建质量检查。
