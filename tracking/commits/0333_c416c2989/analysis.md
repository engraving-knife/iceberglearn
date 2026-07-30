# 提交 0333：Nessie: Strip trailing slash for warehouse location (#9415)

## 提交信息

- **序号**：0333 / 4088
- **哈希**：c416c298943c9c21928e71f37662b8ffbe11b56b
- **短哈希**：c416c2989
- **日期**：2024-01-05 13:12:25 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Nessie: Strip trailing slash for warehouse location (#9415)
- **PR/Issue**：#9415

## 总体目的

本提交修复 NessieCatalog 在初始化时对 warehouse location 末尾斜杠处理不一致的问题：当用户在 catalog 配置中传入的 warehouse 路径以 `/` 结尾时（如 `s3://bucket/warehouse/`），NessieCatalog 原样保留该斜杠，导致后续拼接出来的表/数据文件路径出现双斜杠（如 `s3://bucket/warehouse//default/table`），从而引发路径不一致、缓存未命中、跨 catalog 比对失败等一系列下游问题。

Iceberg 的路径管理期望仓库位置以"无尾斜杠"的规范形式存储，其它 catalog 实现（如 HiveCatalog、HadoopCatalog、JdbcCatalog 等）普遍在内部对 warehouse 做了去尾斜杠的规范化处理。NessieCatalog 此前遗漏了这一步，使其与其余 catalog 在同一配置输入下产生不同的 location 字符串。这种不一致在多 catalog 共享同一物理存储、或通过 `location` 属性比对表身份的场景下尤为致命——同样指向 `s3://bucket/warehouse/`，Nessie 算出的表 location 是 `s3://bucket/warehouse//ns/tbl`，其它 catalog 是 `s3://bucket/warehouse/ns/tbl`，二者字符串不相等就会被误判为不同的表。

此外，部分对象存储（如 S3 的某些实现）对双斜杠路径的处理也存在歧义，可能在签名计算、key 解析上出现非预期行为，进一步放大了这一 bug 的影响面。该问题由 Ajantha Bhat（Nessie 维护者之一）提交修复，属 Nessie 与 Iceberg 集成路径上的边界规范化改进。

## 如何达成设计目的

修复方案非常聚焦：在 `NessieCatalog.initialize` 解析 warehouse location 的最后一步，统一调用 `LocationUtil.stripTrailingSlash(...)` 去除可能的尾斜杠，使其与项目其它 catalog 的规范化策略对齐。同时把原方法名 `validateWarehouseLocation`（语义只强调"校验"）改名为 `warehouseLocation`（语义包含"返回规范化值"），更准确地反映方法职责，并新增一个测试用例覆盖带尾斜杠的输入场景。

## 修改详情

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieCatalog.java`

**修改目的**：在 warehouse location 解析流程的返回处加入去尾斜杠处理，并对方法重命名以反映新的职责。

**工作逻辑**：
`NessieCatalog.initialize(String name, Map<String,String> catalogOptions)` 在构造实例时调用一个私有方法取得 warehouse location 并赋值给 `this.warehouseLocation` 字段。修改点有三处：

1. 新增 import：
   ```java
   import org.apache.iceberg.util.LocationUtil;
   ```
   `LocationUtil` 是 Iceberg core 模块的工具类，提供 `stripTrailingSlash` 等路径规范化静态方法，是项目内复用的统一入口。

2. 调用处改名：
   ```java
   - this.warehouseLocation = validateWarehouseLocation(name, catalogOptions);
   + this.warehouseLocation = warehouseLocation(name, catalogOptions);
   ```
   把对私有方法的调用从 `validateWarehouseLocation` 改为 `warehouseLocation`。

3. 方法定义重命名 + 返回值规范化：
   ```java
   @SuppressWarnings("checkstyle:HiddenField")
   - private String validateWarehouseLocation(String name, Map<String, String> catalogOptions) {
   + private String warehouseLocation(String name, Map<String, String> catalogOptions) {
       String warehouseLocation = catalogOptions.get(CatalogProperties.WAREHOUSE_LOCATION);
       if (warehouseLocation == null) {
         // ... 原有的告警与抛 IllegalStateException 逻辑保持不变 ...
       }
   -   return warehouseLocation;
   +
   +   return LocationUtil.stripTrailingSlash(warehouseLocation);
     }
   ```
   方法体内已有的「为空时记 warning + 抛 `IllegalStateException`」逻辑保留不变，仅把最后的 `return warehouseLocation;` 替换为 `return LocationUtil.stripTrailingSlash(warehouseLocation);`，并删除原 return 与上方空行。这样无论用户配置 `s3://bucket/warehouse/` 还是 `s3://bucket/warehouse`，最终 `this.warehouseLocation` 都规范化为 `s3://bucket/warehouse`。

   方法重命名为 `warehouseLocation` 的合理性在于：原方法不仅做"校验"（为空判断 + 抛异常），还负责"返回规范化值"，新名 `warehouseLocation` 既能覆盖校验语义，又能体现返回职责，避免误导调用方以为它只是个 boolean 校验器。`@SuppressWarnings("checkstyle:HiddenField")` 保留是因为方法参数 `catalogOptions` 与字段同名遮蔽，与方法名变更无关。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java`

**修改目的**：新增针对"带尾斜杠的 warehouse"场景的回归测试，确保 `defaultWarehouseLocation` 返回的路径前缀是去尾斜杠后的规范形式。

**工作逻辑**：
新增 imports：
```java
import org.apache.iceberg.util.LocationUtil;
import org.assertj.core.api.Assertions;
```
然后追加一个 `@Test` 方法：
```java
@Test
public void testWarehouseLocationWithTrailingSlash() {
  Assertions.assertThat(catalog.defaultWarehouseLocation(TABLE))
      .startsWith(
          LocationUtil.stripTrailingSlash(temp.toUri().toString())
              + "/"
              + TABLE.namespace()
              + "/"
              + TABLE.name());
}
```

关键设计：`temp` 是测试基类提供的临时目录（其 URI 通常以 `/` 结尾，如 `file:///tmp/xxx/`）。测试断言 `catalog.defaultWarehouseLocation(TABLE)` 返回的字符串以「`temp` URI 去尾斜杠 + "/" + 命名空间 + "/" + 表名」开头。这正好验证了两点：(1) catalog 内部对 warehouse 做了 `stripTrailingSlash`，否则路径会出现 `...xxx//ns/tbl` 形式的双斜杠导致 `startsWith` 断言失败；(2) 拼接出的表路径前缀符合规范形式。使用 `startsWith` 而非 `isEqualTo` 是因为完整路径还可能含分区的随机后缀，这里只关心前缀规范。

注意该测试类本身已是 JUnit5（`org.junit.jupiter.api.Test`），说明 Nessie 模块此前已完成 JUnit5 迁移，新测试自然沿用 JUnit5 + AssertJ 风格。

## 小结

本提交以最小改动修复了 NessieCatalog 与其它 catalog 在 warehouse location 规范化上的不一致：通过在私有方法返回处统一调用 `LocationUtil.stripTrailingSlash`，把用户配置中可能存在的尾斜杠去除，避免后续表/文件路径出现双斜杠及由此引发的缓存失效、路径比对失败等问题；同时重命名方法以更准确反映职责，并补上针对性的回归测试，整体改动仅 18 行但消除了一个跨 catalog 行为差异的潜在 bug。
