# 提交 0479：Core: Only trim trailing slash when warehouse location is not root path (#9619)

## 提交信息

- **序号**：0479
- **哈希**：defef48e21936357e0921d1738f211bb32e85e49
- **短哈希**：defef48e2
- **日期**：2024-02-06 14:57:07 -0800
- **作者**：Abid Mohammed <mohamabid+github@gmail.com>
- **提交说明**：Core: Only trim trailing slash when warehouse location is not root path
- **PR/Issue**：#9619
- **协作者**：Abid Mohammed <abid_mohammed@apple.com>、Eduard Tudenhoefner <etudenhoefner@gmail.com>

## 总体目的

本提交修复了 `LocationUtil.stripTrailingSlash` 方法在处理"根路径"（root path）时的一个缺陷。`stripTrailingSlash` 的职责是去除路径末尾的多余斜杠，使表/数据文件路径在拼接时不会出现双斜杠，从而保证路径比较的相等性与存储层路径解析的正确性。然而，原实现使用 `while (result.endsWith("/"))` 无条件地剥离所有尾部斜杠，这会错误地破坏以 `://` 结尾的根路径——例如 `blobstore://`、`s3://bucket/` 这类 warehouse location。

问题的根源在于：当一个 warehouse 的 location 被配置为对象存储的根路径（如 `blobstore://`）时，末尾的 `/` 既是路径分隔符，又是 URI scheme（`://`）的一部分。原循环会把 `://` 末尾的 `/` 也当作普通尾部斜杠剥离，将 `blobstore://` 逐渐削减为 `blobstore:`，这是一个没有 scheme 分隔符的残缺路径。这种残缺路径在后续被用于构造数据文件路径（如 `blobstore:/db/tbl/data/file.parquet`，少了一个斜杠）或与 catalog 元数据比较时，会导致路径解析失败或不匹配，进而引发表加载异常、文件定位失败等运行时错误。

修复方案在剥离循环中增加了一个守卫条件：仅当剩余字符串不以 `://` 结尾时才继续剥离尾部斜杠。即把判定从 `result.endsWith("/")` 改为 `!result.endsWith("://") && result.endsWith("/")`。这样当循环把多余斜杠剥离到 `://` 这一 scheme 分隔符处时即停止，保留 `blobstore://`、`s3://bucket` 等合法根路径的完整性。对于普通非根路径（如 `s3://bucket/db/tbl/`），行为保持不变——因为这类路径在剥离到 `bucket` 或 `tbl` 等路径段后已不以 `/` 结尾，循环自然终止；而对于根路径，新增的 `://` 守卫确保 scheme 分隔符不被误删。

这一修复对支持以对象存储根目录作为 warehouse location 的部署场景至关重要。当用户将 catalog 的 warehouse 指向一个 bucket 的根（而非某个子目录）时，Iceberg 需要在此根路径下拼接出表目录与数据文件路径，而根路径的合法表示（带 `://`）必须被完整保留。

## 如何达成设计目的

修复集中在 `LocationUtil.stripTrailingSlash` 的 while 循环条件上，通过短路求值在前置位置加入 `!result.endsWith("://")` 判断，使循环在遇到 scheme 分隔符时提前退出。由于 `&&` 的短路语义，当 `result.endsWith("://")` 为真时直接跳出循环，不再执行后半段的 `endsWith("/")` 判定与剥离操作。配套地，测试类 `TestLocationUtil` 新增三个测试用例，分别覆盖根路径本身、根路径带单个尾斜杠、根路径带多个尾斜杠三种场景，验证修复后的方法能正确保留 `://` 而仅剥离多余斜杠。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/LocationUtil.java`

**修改目的**：在剥离尾部斜杠时，保护根路径的 scheme 分隔符 `://` 不被误删。

**工作逻辑**：

`stripTrailingSlash` 方法的核心循环由：

```java
while (result.endsWith("/")) {
  result = result.substring(0, result.length() - 1);
}
```

改为：

```java
while (!result.endsWith("://") && result.endsWith("/")) {
  result = result.substring(0, result.length() - 1);
}
```

循环条件的前半段 `!result.endsWith("://")` 是新增的守卫。当 `result` 以 `://` 结尾时（即剩余内容恰好是 `blobstore://`、`s3://` 这样的根路径表示），`!result.endsWith("://")` 为 `false`，由于 `&&` 短路，整个条件为 `false`，循环终止，不再剥离末尾的 `/`。当 `result` 不以 `://` 结尾时，守卫为 `true`，继续由后半段 `result.endsWith("/")` 决定是否剥离，行为与原实现一致。

以输入 `blobstore:///`（根路径带两个多余斜杠）为例：第一次迭代时 `result` 为 `blobstore:///`，不以 `://` 结尾（以 `/` 结尾但末三个字符是 `///`），守卫通过，剥离一个斜杠得 `blobstore://`；第二次迭代时 `result` 为 `blobstore://`，以 `://` 结尾，守卫不通过，循环终止，返回 `blobstore://`。对于普通路径 `s3://bucket/db/tbl//`，剥离到 `s3://bucket/db/tbl` 后不以 `/` 结尾，循环正常终止，行为不变。

### `core/src/test/java/org/apache/iceberg/util/TestLocationUtil.java`

**修改目的**：新增针对根路径场景的测试用例，验证修复后 `://` 分隔符被保留。

**工作逻辑**：

在原有测试基础上新增三个测试方法，均以 `blobstore://` 作为基准根路径：

1. **`testDoNotStripTrailingSlashForRootPath`**：输入为不含多余尾斜杠的根路径 `blobstore://`。断言输出等于输入本身（即 `blobstore://`），验证根路径的 scheme 分隔符在无多余斜杠时被完整保留。

2. **`testStripTrailingSlashForRootPathWithTrailingSlash`**：输入为 `blobstore:///`（根路径 + 1 个多余斜杠）。断言输出等于 `blobstore://`，验证多出的那个 `/` 被剥离，而 `://` 中的 `/` 被保留。

3. **`testStripTrailingSlashForRootPathWithTrailingSlashes`**：输入为 `blobstore://////`（根路径 + 3 个多余斜杠）。断言输出等于 `blobstore://`，验证多个多余斜杠被全部剥离后，循环在 `://` 处正确停止。

三个用例共同覆盖了根路径在零、一、多尾斜杠三种输入下的预期行为，确保守卫条件在边界情形下均正确工作。

## 小结

本提交修复了 `LocationUtil.stripTrailingSlash` 误删根路径 scheme 分隔符 `://` 的缺陷，核心改动是在 while 循环条件前加入 `!result.endsWith("://")` 守卫（1 行修改）。修复使得以对象存储根目录作为 warehouse location（如 `blobstore://`）的配置不再被破坏成残缺路径，对表加载与数据文件路径拼接的正确性有直接影响。配套新增 3 个测试用例（共 26 行新增），覆盖根路径在零/一/多尾斜杠三种场景。文件改动量为 `LocationUtil.java` 1 行改、`TestLocationUtil.java` 26 行增，总计 27 行增、1 行删。
