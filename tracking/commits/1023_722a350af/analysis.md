# 提交 1023：Build: Fix Scala compilation (#10860)

## 提交信息

- **序号**：1023 / 4088
- **哈希**：722a350afa8ef1d6fe3018b290fefb2a836020ec
- **短哈希**：722a350af
- **日期**：2024-08-05 09:08:58 +0200
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：Build: Fix Scala compilation (#10860)
- **PR/Issue**：#10860

## 总体目的

Iceberg 项目在构建配置中通过 Gradle 的 `options.release` 来约束 Java 编译目标版本为 11。但是对于 Scala 编译任务（`ScalaCompile`），`options.release` 选项并不能正确生效——Gradle 的 Scala 编译任务不识别该选项。这导致 Scala 编译使用了不正确的源/目标兼容性版本，可能引发编译失败或产生与目标 JDK 版本不兼容的字节码。

提交说明明确指出：`ScalaCompile` 不遵守 `options.release`，且仅在参数中加 `-release:11` 也不够充分。因此本提交重新为 Scala 编译任务显式设置 `sourceCompatibility` 和 `targetCompatibility` 为 11，确保 Scala 子项目编译产物兼容 JDK 11。

## 如何达成设计目的

在根 `build.gradle` 的 `subprojects` 块中，针对 `ScalaPlugin` 已应用的子项目和 `ScalaCompile` 任务类型，额外添加 `sourceCompatibility = "11"` 和 `targetCompatibility = "11"`，同时保留 `scalaCompileOptions.additionalParameters.add("-release:11")`（即 `-release:11` 参数传递给 Scala 编译器），形成双重保障：既通过 Gradle 层面声明兼容性版本，又通过编译器参数传递 `-release` 标志。

## 修改详情

### `build.gradle`

**修改目的**：修复 Scala 编译任务的 JDK 版本兼容性配置，使 `sourceCompatibility`、`targetCompatibility` 与 `-release:11` 参数共同生效。

**工作逻辑**：在 `plugins.withType(ScalaPlugin.class)` 块内、`tasks.withType(ScalaCompile.class)` 的配置中，原本只有 `scalaCompileOptions.keepAliveMode.set(KeepAliveMode.DAEMON)` 一行。本次新增了：

```groovy
// `options.release` doesn't seem to work for ScalaCompile :(
sourceCompatibility = "11"
targetCompatibility = "11"
scalaCompileOptions.additionalParameters.add("-release:11")
```

注释说明 `options.release` 对 ScalaCompile 不生效，因此需要显式设置源/目标兼容性，并向 Scala 编译器传递 `-release:11` 参数。这确保所有 Scala 子项目（如 Spark 模块中的 Scala 测试代码）编译产物兼容 JDK 11。

## 小结

- **成效**：修复了 Scala 编译任务中 JDK 版本约束不生效的问题，使 Scala 子项目编译正确面向 JDK 11。
- **影响范围**：仅修改 `build.gradle` 根构建脚本，影响所有应用 ScalaPlugin 的子项目的编译行为。
- **回迁到 1.4.x 的注意事项**：构建配置修复，回迁风险低。若 1.4.x 分支的 Scala 编译出现类似问题（如使用较新 JDK 构建时 Scala 编译失败），可回迁此修复。需注意 1.4.x 分支可能已有不同的构建配置基线，应确认该修复不与已有配置冲突。
