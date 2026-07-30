# 提交 0401：Arrow, AWS, Core: Remove deprecated code for 1.5.0 release (#9505)

## 提交信息

- **序号**：0401
- **哈希**：556b79893c4cf760f031258f0d8ae657e42b4443
- **短哈希**：556b79893
- **日期**：2024-01-22（AuthorDate: 2024-01-22 13:11:22 +0530；CommitDate: 2024-01-22 08:41:22 +0100）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Arrow, AWS, Core: Remove deprecated code for 1.5.0 release (#9505)
- **PR/Issue**：#9505

## 总体目的

Apache Iceberg 有着一套明确的弃用治理策略：在某个版本（如 1.4.0）中将 API 标记为 `@Deprecated`，并在下一个 minor 版本（如 1.5.0）中真正将其删除。这个提交正是 1.5.0 发布前的一次"清理性大扫除"，把分散在 Arrow、AWS、Core 三个模块中、从 1.4.0 起就被标注"will be removed in 1.5.0"的代码统一移除。

这类清理在开源项目中有几个重要作用：一是真正缩小公共 API 表面，避免长期积累死代码；二是强制下游使用者迁移到新的、更合理的设计上；三是借助 revapi（API 兼容性检查工具）把每一次"破坏性删除"以可追溯的方式登记进 `.palantir/revapi.yml`，让后续版本的兼容性审计有据可查。

值得注意的是，本提交不只是简单删除带 `@Deprecated` 的方法/类，还顺手做了若干"配套清理"：例如 AwsProperties 中删除的若干字段（`httpClientProperties`、`clientRegion`、`allProperties`）是为支持已弃用方法而存在的内部状态，方法被删后这些字段也成了死代码；调用方（如 AwsClientFactories）则被切换到新的 `AwsClientProperties` 上的等价方法。这种"删除 + 迁移调用点"一体的做法保证了删除后的代码仍可编译运行。

## 如何达成设计目的

整体路径很直白：先用 `git grep`/IDE 找到所有标注了 "will be removed in 1.5.0" 的符号，逐个删除其声明；再把仍在调用这些符号的内部代码改到新的替代实现上；最后在 `.palantir/revapi.yml` 的 `1.5.0` 段落中补齐所有因删除而产生的 `java.class.removed` / `java.method.removed` 条目，并附上 "Removing deprecated code" 的统一 justification，使 revapi 在 CI 中不再报错。测试侧则把原本断言弃用行为的用例改写为使用新 API 的等价用例。

## 修改详情

### .palantir/revapi.yml

**修改目的**：登记 1.5.0 因删除弃用代码而引入的 API 破坏，让 revapi 兼容性检查放行。

**工作逻辑**：在 `acceptedBreaks` 的 `"1.4.0"` 段下新增了一系列条目，包括被删除的 `RESTSerializers.UpdateRequirementDeserializer`、`RESTSerializers.UpdateRequirementSerializer`、`UpdateRequirementParser`、`UpdateTableRequest.Builder`、`UpdateTableRequest.UpdateRequirement`，以及 `ClusteredWriter`/`FanoutWriter` 子类中 `newOutputFile` 方法的移除、`UpdateTableRequest` 上 `builderFor/builderForCreate/builderForReplace` 的移除、`PositionDeletesBatchScan` 旧构造器的移除。同时把原本放在 1.4.0 段的 `NameMapping` 默认序列化变更条目挪到了更靠前的位置（这是条目整理，并非新增破坏）。每条都标注 justification 为 "Removing deprecated code"（NameMapping 那条除外，沿用 "Serialization across versions is not guaranteed"）。

### arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorHolder.java

**修改目的**：删除 1.4.0 弃用的、缺少 `icebergField` 信息的常量向量构造入口。

**工作逻辑**：移除静态方法 `constantHolder(int numRows, T constantValue)` 以及内部类 `ConstantVectorHolder` 上 `ConstantVectorHolder(int numRows, T constantValue)` 构造器。两者均自 1.4.0 起标注 `@Deprecated`，注释要求改用携带 `Types.NestedField icebergField` 信息的 typed constant holder 版本。保留的构造器 `ConstantVectorHolder(Types.NestedField, int, T)` 才是推荐路径，因为它能把 Iceberg 字段元数据带进 holder，供向量读取链路正确解析。

### arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java

**修改目的**：删除弃用的 `ConstantVectorReader(T value)` 构造器。

**工作逻辑**：与 VectorHolder 同理，旧的构造器不携带 `icebergField` 信息。删除后只保留 `ConstantVectorReader(Types.NestedField icebergField, T value)`，确保所有常量读取器都关联到具体的 Iceberg 字段。

### aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java

**修改目的**：把对已弃用 `AwsProperties.applyClientCredentialConfigurations` 的调用切到新的 `AwsClientProperties.applyClientCredentialConfigurations` 上。

**工作逻辑**：在 `s3()`、`kms()`、`dynamoDb()` 三个客户端构建方法中，把 `.applyMutation(awsProperties::applyClientCredentialConfigurations)` 改为 `.applyMutation(awsClientProperties::applyClientCredentialConfigurations)`。这是删除前的"先迁移调用点"步骤——`AwsClientProperties` 是 1.4.0 引入的、专门承载客户端级配置（region、credentials provider 等）的新类，把凭证配置职责从臃肿的 `AwsProperties` 中拆出来。

### aws/src/main/java/org/apache/iceberg/aws/AwsProperties.java

**修改目的**：删除 1.4.0 弃用的方法及其依赖的内部字段，让 AwsProperties 更聚焦于 AWS 服务侧配置。

**工作逻辑**：删除了四个 `@Deprecated` 方法：`httpClientProperties()`、`clientRegion()`、`setClientRegion(String)`、`applyClientCredentialConfigurations(AwsClientBuilder)`。同时清理了仅为这些方法服务的内部状态：字段 `httpClientProperties`（及其在两个构造器中的初始化，包括 `PropertyUtil.filterProperties(...)` 调用）、字段 `clientRegion`（及其初始化 `properties.get(AwsClientProperties.CLIENT_REGION)`）、字段 `allProperties`（`SerializableMap.copyOf(properties)`）。相应地移除了不再需要的 import（`Collections`、`Maps`、`SerializableMap`、`AwsClientBuilder`）。这是典型的"删 API + 删背后死字段"联动清理。

### aws/src/test/java/org/apache/iceberg/aws/TestAwsProperties.java

**修改目的**：把原本依赖已删除 `httpClientProperties()` 的 Kryo 序列化测试改写为验证仍然存在的属性。

**工作逻辑**：原测试构造空 `AwsProperties`、带 `"a","b"` 属性的实例和空 Map 实例，分别序列化后断言 `httpClientProperties()` 相等。改写后只构造一个带 `GLUE_CATALOG_ID=foo`、`DYNAMODB_TABLE_NAME=ice` 的实例，序列化往返后断言 `glueCatalogId()` 与 `dynamoDbTableName()` 一致。同时清理了 `Collections` import，新增了对 `DYNAMODB_TABLE_NAME`、`GLUE_CATALOG_ID` 的静态 import。

### aws/src/test/java/org/apache/iceberg/aws/TestHttpClientConfigurations.java

**修改目的**：测试中不再经由 `AwsProperties.httpClientProperties()` 取属性，而是直接传入原始 properties Map。

**工作逻辑**：四个测试方法（`testUrlConnectionConfigurations`、`testUrlConnectionDefaultConfigurations`、`testApacheConfigurations`、`testApacheDefaultConfigurations`）都把 `UrlConnectionHttpClientConfigurations.create(awsProperties.httpClientProperties())` / `ApacheHttpClientConfigurations.create(awsProperties.httpClientProperties())` 改为直接 `create(properties)` 或 `create(Maps.newHashMap())`，删除了中间 `AwsProperties awsProperties = new AwsProperties(properties)` 这一层。这反映了 HttpClient 配置不再寄生于 AwsProperties 的设计意图。

### core/src/main/java/org/apache/iceberg/PositionDeletesTable.java

**修改目的**：删除弃用的 `PositionDeletesBatchScan(Table, Schema, TableScanContext)` 构造器。

**工作逻辑**：该构造器自 1.4.0 起标注 "the API will be removed in v1.5.0"。保留的构造器是带 `Expression baseTableFilter` 参数的四参版本，能支持对 position deletes 表做基于基表的过滤扫描，功能更完整。

### core/src/main/java/org/apache/iceberg/io/ClusteredWriter.java

**修改目的**：删除弃用的 `newOutputFile(OutputFileFactory, PartitionSpec, StructLike)` 模板方法。

**工作逻辑**：原方法封装了"按分区创建 EncryptedOutputFile"的通用逻辑：先校验分区表下 partition 不能为 null，再根据是否分区调用 `fileFactory.newOutputFile()` 或 `newOutputFile(spec, partition)`。该钩子在 1.4.0 被弃用，子类（`ClusteredDataWriter`、`ClusteredEqualityDeleteWriter`、`ClusteredPositionDeleteWriter`）应改为直接使用 `OutputFileFactory` 的 API。删除后同时移除了 `EncryptedOutputFile` 的 import。

### core/src/main/java/org/apache/iceberg/io/FanoutWriter.java

**修改目的**：删除弃用的 `newOutputFile(OutputFileFactory, PartitionSpec, StructLike)` 模板方法。

**工作逻辑**：与 ClusteredWriter 中删除的方法完全一致（同一份代码副本），服务于 `FanoutDataWriter`、`FanoutPositionOnlyDeleteWriter` 等子类。删除后同样移除了 `EncryptedOutputFile` import。

### core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java

**修改目的**：删除弃用的 `UpdateRequirementSerializer` / `UpdateRequirementDeserializer` 内部类及其在 Jackson Module 中的注册。

**工作逻辑**：这两个类自 1.4.0 起标注 `@Deprecated`，注释要求改用 `UpdateReqSerializer` / `UpdateReqDeserializer`（注意是无 "UpdateRequirement" 前缀的简短名）。区别在于：旧类序列化的是 `org.apache.iceberg.rest.requests.UpdateTableRequest.UpdateRequirement`（已弃用的内部接口），新类序列化的是 `org.apache.iceberg.UpdateRequirement`（顶层统一类）。删除后，`RESTSerializers` 不再注册旧接口的 (de)serializer，只保留 `UpdateReqSerializer`/`UpdateReqDeserializer` 处理顶层 `UpdateRequirement`。相应移除了 `UpdateRequirementParser` 与 `UpdateTableRequest.UpdateRequirement` 的 import。

### core/src/main/java/org/apache/iceberg/rest/requests/UpdateRequirementParser.java

**修改目的**：整文件删除——该解析器服务于已弃用的 `UpdateTableRequest.UpdateRequirement` 接口。

**工作逻辑**：被删的 270 行代码是一整套针对旧 `UpdateRequirement` 接口（`AssertTableUUID`、`AssertTableDoesNotExist`、`AssertRefSnapshotID`、`AssertLastAssignedFieldId`、`AssertCurrentSchemaID`、`AssertLastAssignedPartitionId`、`AssertDefaultSpecID`、`AssertDefaultSortOrderID`）的 JSON 序列化/反序列化实现，包括 type 字符串常量、`Class<? extends UpdateRequirement> -> String` 映射表、`toJson`/`fromJson` 的 switch 派发，以及各 assertion 子类型的 read/write 辅助方法。文件级 `@Deprecated` 注释指向替代者 `org.apache.iceberg.UpdateRequirementParser`。删除它是把 REST 层的 requirement 模型统一到 `org.apache.iceberg.UpdateRequirement` 体系的关键一步。

### core/src/main/java/org/apache/iceberg/rest/requests/UpdateTableRequest.java

**修改目的**：删除弃用的 `Builder` 内部类、`UpdateRequirement` 内部接口以及三个 `builderFor*` 静态工厂方法。

**工作逻辑**：这是本提交中删除量最大的文件（363 行）。被删内容包括：
- 静态工厂 `builderForCreate()`、`builderForReplace(TableMetadata)`、`builderFor(TableMetadata)`，注释均指向 `org.apache.iceberg.UpdateRequirements` 上的等价工厂。
- `Builder` 内部类：一个相当复杂的状态机，跟踪 `addedSchema`、`setSchemaId`、`addedSpec`、`setSpecId`、`setOrderId` 等标志位，在 `update(MetadataUpdate)` 时根据 update 类型自动派生对应的 assertion requirement（例如收到 `SetSnapshotRef` 就 `requireRefSnapshotId`、收到 `AddSchema` 就 `requireLastAssignedFieldId` 等）。这套"按 update 自动加 requirement"的逻辑在 1.4.0 被外移到 `org.apache.iceberg.UpdateRequirements` 工具类，旧 Builder 仅作兼容留存。
- `UpdateRequirement` 内部接口及其 8 个 assertion 实现类（`AssertTableDoesNotExist`、`AssertTableUUID`、`AssertRefSnapshotID` 等，每个都带 `validate(TableMetadata)` 实现）。这些同样被外移到顶层 `org.apache.iceberg.UpdateRequirement`。
- 同时清理了仅为这些类服务的 import（`Set`、`TableMetadata`、`SnapshotRef`、`CommitFailedException`、`Preconditions`、`Lists`、`Sets`）。

保留的 `UpdateTableRequest` 仍持有 `requirements`（类型改为 `List<org.apache.iceberg.UpdateRequirement>`）和 `updates`，并通过 `create(...)` 工厂构造，但不再自带 requirement 派生逻辑。

## 小结

本提交是一次教科书式的"按计划移除弃用 API"操作：范围横跨 Arrow/AWS/Core 三模块，删除量 800 行而新增仅 73 行（主要是 revapi 登记）。它体现了 Iceberg 项目几个值得注意的模式：(1) 弃用必须有明确版本号（"will be removed in X.Y.0"）并在到期时果断删除，不让死代码堆积；(2) API 删除前先在新版本提供替代实现并迁移内部调用点，保证删除时无内部引用；(3) 用 revapi 把每一次破坏性变更显式登记，使 API 兼容性可审计；(4) 借清理之机完成架构收敛——本提交把 REST 层散落的 `UpdateRequirement`/`UpdateRequirementParser`/`Builder` 统一并入 `org.apache.iceberg.*` 顶层体系，把 AWS 客户端配置职责从 `AwsProperties` 拆到 `AwsClientProperties`。对 1.4.x 用户而言，升级到含此提交的 1.5.0 时需把所有对上述弃用符号的调用迁移到新 API。
