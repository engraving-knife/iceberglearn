# 提交 1178：API: Deprecate ContentFile#path API and add location API which returns String (#11092)

## 提交信息

- **序号**：1178 / 4088
- **哈希**：257bad29d7db09f3f9c89663b7c34f8caca3014b
- **短哈希**：257bad29d
- **日期**：2024-09-23（Mon Sep 23 14:21:38 2024 -0600）
- **作者**：Amogh Jahagirdar <amogh@apache.org>
- **提交说明**：API: Deprecate ContentFile#path API and add location API which returns String (#11092)
- **PR/Issue**：#11092
- **共同作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>

## 总体目的

`ContentFile<F>` 是 Iceberg API 模块中对数据文件（DataFile）、删除文件（DeleteFile）等文件元数据的公共接口。它原本定义了 `CharSequence path()` 方法返回文件的完全限定路径，注释说明该路径"适合构造 Hadoop Path"。

`CharSequence` 作为返回类型存在以下问题：
1. 调用方常需要 `String`，但拿到的是 `CharSequence`，必须再 `.toString()`，繁琐且容易写出低效代码；
2. 实现类历史上曾返回 `UTF8BytesString`（Hadoop 的优化 String 子类）等不同 `CharSequence` 实现，调用方若直接当成 `String` 用会出 `ClassCastException`；
3. `path()` 这个名字语义偏向"路径字符串"，但 Iceberg 内部正在统一用 `location` 来描述文件位置（与 `FileIO.newInputFile(location)` 等其他 API 一致），`path` 一词在 Hadoop 语境里指 `Path` 对象，容易引起混淆。

本提交的目的是**启动 API 迁移**：把 `path()` 标记为 `@Deprecated`（自 1.7.0 起弃用，2.0.0 移除），同时新增 `location()` 方法返回 `String` 类型，作为新的标准 API。`location()` 默认实现调用 `path().toString()`，保证对现有实现类向后兼容——所有已实现 `ContentFile` 的类无需修改即可同时支持新旧两个方法。这是一个标准的"先并行存在、再迁移、最后移除旧 API"的软迁移步骤。

## 如何达成设计目的

在 `ContentFile.java` 接口中：
1. 给 `path()` 方法加上 `@Deprecated` 注解，并在 Javadoc 中写明 `@deprecated since 1.7.0, will be removed in 2.0.0; use {@link #location()} instead.`，引导调用方迁移。
2. 新增 `default String location()` 方法，默认实现为 `return path().toString();`。使用 `default` 方法保证所有现有实现类（`BaseFile`、`GenericDataFile`、`GenericDeleteFile` 等）无需任何改动即可获得 `location()` 能力。
3. 选择 `String` 而非 `CharSequence` 作为返回类型，避免前述 `CharSequence` 类型不统一带来的问题，调用方直接拿到 `String` 即可使用。

由于 `path()` 仍然保留且 `location()` 默认走 `path()`，旧实现类无需改动；新代码可以只实现/调用 `location()`，渐进式完成迁移。后续 PR 会逐步把内部代码与实现类迁移到 `location()`，并在 2.0.0 删除 `path()`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ContentFile.java`

**修改目的**：弃用 `path()`，新增 `String location()` 默认方法。

**改动内容**：

原代码：

```java
/** Returns fully qualified path to the file, suitable for constructing a Hadoop Path. */
CharSequence path();
```

改为：

```java
/**
 * Returns fully qualified path to the file, suitable for constructing a Hadoop Path.
 *
 * @deprecated since 1.7.0, will be removed in 2.0.0; use {@link #location()} instead.
 */
@Deprecated
CharSequence path();

/** Return the fully qualified path to the file. */
default String location() {
  return path().toString();
}
```

**工作逻辑**：
- `@Deprecated` 触发编译器告警，提示所有调用方迁移。
- Javadoc 的 `@deprecated` 与 `@link` 提供迁移指引，IDE 会推荐替换为 `location()`。
- `default location()` 的实现 `path().toString()` 兼容现有实现类：
  - 如果实现类返回的是普通 `String`（`String` 也是 `CharSequence`），`toString()` 返回自身，零开销。
  - 如果实现类返回 `UTF8BytesString` 等子类，`toString()` 也返回等价 `String`。
  - 这样无论底层实现如何，`location()` 总能给出一个标准 `String`，调用方不再需要关心 `CharSequence` 的具体类型。
- 新增方法的注释从 "Returns fully qualified path to the file, suitable for constructing a Hadoop Path" 简化为 "Return the fully qualified path to the file."，不再强调 Hadoop Path，呼应 API 命名统一为 `location` 的方向。

**迁移路径**：
- 阶段 1（本提交）：`path()` 弃用，`location()` 默认走 `path()`，二者并存。
- 阶段 2（后续 PR）：内部代码、实现类逐步改为以 `String` 存储并直接返回 `location()`，`path()` 改为调用 `location()`。
- 阶段 3（2.0.0）：删除 `path()`，仅保留 `String location()`。

## 小结

- **成效**：`ContentFile` 接口新增 `String location()` 作为标准文件位置 API，旧 `CharSequence path()` 标记弃用，启动了向 `String` 类型与 `location` 命名统一的渐进式迁移；现有实现类与调用方因 `default` 方法保持完全向后兼容。
- **影响范围**：仅 `api/src/main/java/org/apache/iceberg/ContentFile.java` 一个接口文件，新增 11 行（含 Javadoc）+ 删除 1 行注释。影响所有 `ContentFile` 的实现类（`BaseFile` 及其子类）与所有调用 `path()` 的代码（会产生 deprecation 告警，但行为不变）。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个 API 层面的软迁移起点，**对 1.4.x 的运行时行为无任何影响**（`location()` 默认实现等价于 `path().toString()`，`path()` 行为不变）。
  - 1.4.x 的 `ContentFile` 接口版本可能与 main 不同。若 1.4.x 仍处于"1.4.x 维护期"，通常**不需要回迁**这类 API 弃用标记——因为 1.4.x 不会演进到 2.0.0，弃用标记对 1.4.x 用户意义不大，且会引入额外的 deprecation 告警噪音。
  - 但如果 1.4.x 希望与 main 的 API 保持一致以方便用户提前迁移，可回迁；回迁是纯增量（新增 `default` 方法 + 注解），不会破坏二进制兼容性，已编译的下游代码仍可正常运行。
  - 注意：本提交声明"since 1.7.0"弃用，说明 main 分支即将发布 1.7.0；1.4.x 是更早版本，回迁后需把弃用版本号改为对应 1.4.x 版本，否则文档语义不符。
