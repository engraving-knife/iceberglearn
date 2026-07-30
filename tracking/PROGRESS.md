# 1.4.x 落后 main 分支提交分析进度追踪

## 概览

- **分析范围**：1.4.x 分支落后于 main 分支的全部提交（`git log --reverse 1.4.x..main`）
- **提交总数**：4088
- **时间范围**：2023-09-28 09:49:10 -0700 ~ 2026-07-24 11:51:34 +0200
- **合并基点**：863f396e (2023-09-28, Docs: Remove spark-3.1 mention #8671)
- **当前进度**：4088/4088（100.0%）已完成 ✅ 全部完成

## 分析方法

按时间顺序（最旧提交在前）针对每个提交在 `commits/NNNN_<短哈希>/` 下创建独立文件夹，其中 `analysis.md` 包含：
1. **提交信息**：哈希、日期、作者、PR/Issue 编号
2. **总体目的**：详细分析该提交要解决的问题与动机
3. **如何达成设计目的**：整体设计思路
4. **修改详情**：针对每一处修改说明目的；较大块修改解释工作逻辑

## 状态图例

- ✅ 已完成：该提交的 `analysis.md` 已生成

- ⏳ 待分析：尚未处理

## 状态汇总

| 状态 | 数量 |
|------|------|
| ✅ 已完成 | 4088 |
| ⏳ 待分析 | 0 |
| 合计 | 4088 |

## 提交清单与状态

| 序号 | 哈希 | 日期 | 作者 | 提交说明 | 状态 | 文件夹 |
|------|------|------|------|----------|------|--------|
| 1 | `eaf7c4f2e` | 2023-09-28 09:49:10 -0700 | Eduard Tudenhoefner | Core: Fix view version ID reassigment and deduplication, start schema ID at 0 (#8664) | ✅ 已完成 | [0001_eaf7c4f2e](commits/0001_eaf7c4f2e/analysis.md) |
| 2 | `6100efc1a` | 2023-09-28 15:24:07 -0700 | Eduard Tudenhoefner | Core: Add remaining View APIs and support for InMemoryCatalog (#7880) | ✅ 已完成 | [0002_6100efc1a](commits/0002_6100efc1a/analysis.md) |
| 3 | `28dd49f5b` | 2023-09-28 15:47:38 -0700 | Fokko Driesprong | Python: Update pre-commit (#8651) | ✅ 已完成 | [0003_28dd49f5b](commits/0003_28dd49f5b/analysis.md) |
| 4 | `6172f5c72` | 2023-09-28 15:48:28 -0700 | Fokko Driesprong | Python: Add more Ruff rules (#8652) | ✅ 已完成 | [0004_6172f5c72](commits/0004_6172f5c72/analysis.md) |
| 5 | `8062aef8b` | 2023-09-29 11:00:29 +0200 | HonahX | Python: ManifestWriter and ManifestListWriter (#8622) | ✅ 已完成 | [0005_8062aef8b](commits/0005_8062aef8b/analysis.md) |
| 6 | `3f2884c6d` | 2023-09-29 12:38:36 -0700 | Anton Okolnychyi | Spark: Fix Decimal value conversion in V2 filters (#8682) | ✅ 已完成 | [0006_3f2884c6d](commits/0006_3f2884c6d/analysis.md) |
| 7 | `c68abfc9f` | 2023-10-01 18:55:31 -0700 | Kristin Cowalcijk | AWS: avoid static global credentials provider which doesn't play well with lifecycle management (#8677) | ✅ 已完成 | [0007_c68abfc9f](commits/0007_c68abfc9f/analysis.md) |
| 8 | `df0f408c0` | 2023-10-02 15:37:41 +0200 | Fokko Driesprong | Docs: Add links to Go, Python and Rust (#8681) | ✅ 已完成 | [0008_df0f408c0](commits/0008_df0f408c0/analysis.md) |
| 9 | `06d420f7c` | 2023-10-02 21:12:10 +0200 | Hongyue/Steve Zhang | Open-API: Add namespaceExist API (#8569) | ✅ 已完成 | [0009_06d420f7c](commits/0009_06d420f7c/analysis.md) |
| 10 | `c862b9177` | 2023-10-02 13:56:58 -0700 | Fokko Driesprong | Labeler: Add Specification label (#8700) | ✅ 已完成 | [0010_c862b9177](commits/0010_c862b9177/analysis.md) |
| 11 | `d2e1094ee` | 2023-10-04 11:17:34 +0200 | Eduard Tudenhoefner | API, Core: Allow setting a View's location (#8648) | ✅ 已完成 | [0011_d2e1094ee](commits/0011_d2e1094ee/analysis.md) |
| 12 | `a960d43a5` | 2023-10-05 08:36:34 +0200 | Naveen Kumar | Docs: Document publish_changes procedure (#8706) | ✅ 已完成 | [0012_a960d43a5](commits/0012_a960d43a5/analysis.md) |
| 13 | `dd26f3630` | 2023-10-05 11:23:10 +0200 | Eduard Tudenhoefner | OpenAPI: Add AssignUUID update to metadata updates (#8716) | ✅ 已完成 | [0013_dd26f3630](commits/0013_dd26f3630/analysis.md) |
| 14 | `036cef946` | 2023-10-05 21:51:37 +0200 | Fokko Driesprong | Spec: Inconsistency around files_count (#5338) | ✅ 已完成 | [0014_036cef946](commits/0014_036cef946/analysis.md) |
| 15 | `dd02085b1` | 2023-10-05 13:05:51 -0700 | Fokko Driesprong | Revert "Spec: Mark added_snapshot_id as optional (#8600)" (#8726) | ✅ 已完成 | [0015_dd02085b1](commits/0015_dd02085b1/analysis.md) |
| 16 | `00a88d0ff` | 2023-10-06 08:11:22 +0200 | Anton Okolnychyi | Build: Let revapi compare against 1.4.0 (#8727) | ✅ 已完成 | [0016_00a88d0ff](commits/0016_00a88d0ff/analysis.md) |
| 17 | `0259918eb` | 2023-10-06 08:11:41 +0200 | Anton Okolnychyi | Build: Add 1.4.0 to issue template (#8728) | ✅ 已完成 | [0017_0259918eb](commits/0017_0259918eb/analysis.md) |
| 18 | `5a98aef61` | 2023-10-06 14:23:46 -0700 | Anton Okolnychyi | Spark: Clean up FileIO instances on executors (#8685) | ✅ 已完成 | [0018_5a98aef61](commits/0018_5a98aef61/analysis.md) |
| 19 | `9bb7de118` | 2023-10-09 08:11:46 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.18.0 to 26.24.0 (#8735) | ✅ 已完成 | [0019_9bb7de118](commits/0019_9bb7de118/analysis.md) |
| 20 | `71cddb8ae` | 2023-10-09 08:13:15 +0200 | Ajantha Bhat | Python: Remove python directory and references (#8695) | ✅ 已完成 | [0020_71cddb8ae](commits/0020_71cddb8ae/analysis.md) |
| 21 | `39239a195` | 2023-10-09 09:38:15 +0200 | Ajantha Bhat | Nessie: Remove dead code in NessieCatalog (#8750) | ✅ 已完成 | [0021_39239a195](commits/0021_39239a195/analysis.md) |
| 22 | `2504c5810` | 2023-10-09 12:32:39 +0200 | dependabot[bot] | Build: Bump org.immutables:value from 2.9.2 to 2.10.0 (#8736) | ✅ 已完成 | [0022_2504c5810](commits/0022_2504c5810/analysis.md) |
| 23 | `f74749eba` | 2023-10-09 16:09:58 +0200 | Johan Henriksson | OpenAPI: uniqueItems is not valid on type object (#8751) | ✅ 已完成 | [0023_f74749eba](commits/0023_f74749eba/analysis.md) |
| 24 | `82e0a5632` | 2023-10-09 08:59:40 -0700 | Eduard Tudenhoefner | Core: Use visibility string instead of enum for Immutable visibility (#8752) | ✅ 已完成 | [0024_82e0a5632](commits/0024_82e0a5632/analysis.md) |
| 25 | `90cf38c50` | 2023-10-10 10:03:16 +0200 | Ashutosh Roy | Dell: Migrate Files using TestRule to Junit5 (#8707) | ✅ 已完成 | [0025_90cf38c50](commits/0025_90cf38c50/analysis.md) |
| 26 | `103038db4` | 2023-10-10 14:17:49 +0200 | Naveen Kumar | Fix minor compilation warnings (#8758) | ✅ 已完成 | [0026_103038db4](commits/0026_103038db4/analysis.md) |
| 27 | `982242ba8` | 2023-10-10 17:00:52 +0200 | Priyansh Agrawal | Docs: Fix missing semicolons in SQL snippets. (#8748) | ✅ 已完成 | [0027_982242ba8](commits/0027_982242ba8/analysis.md) |
| 28 | `d8f29155e` | 2023-10-10 17:03:40 +0200 | Eduard Tudenhoefner | Core: Use more permissive check when registering existing table (#8759) | ✅ 已完成 | [0028_d8f29155e](commits/0028_d8f29155e/analysis.md) |
| 29 | `d8a07ff27` | 2023-10-10 18:07:32 +0200 | Naveen Kumar | Docs: Document all metadata tables (#8709) | ✅ 已完成 | [0029_d8a07ff27](commits/0029_d8a07ff27/analysis.md) |
| 30 | `b3ebccccf` | 2023-10-11 07:29:02 +0200 | JB Onofré | Build: increase open-pull-requests-limit to 50 (#8768) | ✅ 已完成 | [0030_b3ebccccf](commits/0030_b3ebccccf/analysis.md) |
| 31 | `aa43e1f24` | 2023-10-11 08:26:13 +0200 | Eduard Tudenhoefner | OpenAPI: Add description for AssignUUID (#8753) | ✅ 已完成 | [0031_aa43e1f24](commits/0031_aa43e1f24/analysis.md) |
| 32 | `1413984e5` | 2023-10-11 09:01:27 +0200 | Fokko Driesprong | Build: Bump to Avro 1.11.3 (#8587) | ✅ 已完成 | [0032_1413984e5](commits/0032_1413984e5/analysis.md) |
| 33 | `c3b9afe2f` | 2023-10-11 09:02:20 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.20.131 to 2.20.162 (#8773) | ✅ 已完成 | [0033_c3b9afe2f](commits/0033_c3b9afe2f/analysis.md) |
| 34 | `4a08140ad` | 2023-10-11 09:02:40 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.42.0.0 to 3.43.0.0 (#8775) | ✅ 已完成 | [0034_4a08140ad](commits/0034_4a08140ad/analysis.md) |
| 35 | `dbaa92e0b` | 2023-10-11 09:08:04 +0200 | dependabot[bot] | Build: Bump nessie from 0.71.0 to 0.71.1 (#8771) | ✅ 已完成 | [0035_dbaa92e0b](commits/0035_dbaa92e0b/analysis.md) |
| 36 | `9cb22b93a` | 2023-10-11 09:34:06 +0200 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 0.9.47 to 1.0.0 (#8792) | ✅ 已完成 | [0036_9cb22b93a](commits/0036_9cb22b93a/analysis.md) |
| 37 | `d85e7a439` | 2023-10-11 10:09:42 +0200 | dependabot[bot] | Build: Bump guava from 32.1.1-jre to 32.1.3-jre (#8777) | ✅ 已完成 | [0037_d85e7a439](commits/0037_d85e7a439/analysis.md) |
| 38 | `9210a1059` | 2023-10-11 10:11:35 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#8776) | ✅ 已完成 | [0038_9210a1059](commits/0038_9210a1059/analysis.md) |
| 39 | `4b020a41a` | 2023-10-11 10:48:41 +0200 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.17.6 to 1.19.1 (#8780) | ✅ 已完成 | [0039_4b020a41a](commits/0039_4b020a41a/analysis.md) |
| 40 | `b5ea0d5a7` | 2023-10-11 10:50:24 +0200 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.16 to 1.2.17 (#8794) | ✅ 已完成 | [0040_b5ea0d5a7](commits/0040_b5ea0d5a7/analysis.md) |
| 41 | `b7e5d685c` | 2023-10-11 10:28:28 -0700 | Eduard Tudenhoefner | Core: Support view metadata compression (#8552) | ✅ 已完成 | [0041_b7e5d685c](commits/0041_b7e5d685c/analysis.md) |
| 42 | `e6a6cffc7` | 2023-10-12 08:06:33 +0200 | Ajantha Bhat | Nessie: Remove deprecated usage of Operation.Put.of() (#8796) | ✅ 已完成 | [0042_e6a6cffc7](commits/0042_e6a6cffc7/analysis.md) |
| 43 | `4398803cb` | 2023-10-12 11:22:06 +0200 | JB Onofré | Add ASF DOAP rdf file (#8586) | ✅ 已完成 | [0043_4398803cb](commits/0043_4398803cb/analysis.md) |
| 44 | `6530a3e88` | 2023-10-12 15:39:53 +0200 | JB Onofré | Rename master branch to main (#8722) | ✅ 已完成 | [0044_6530a3e88](commits/0044_6530a3e88/analysis.md) |
| 45 | `8463d6623` | 2023-10-13 12:54:50 +0200 | Naveen Kumar | Build: Fix compiler warnings (#8763) | ✅ 已完成 | [0045_8463d6623](commits/0045_8463d6623/analysis.md) |
| 46 | `2268bd8ac` | 2023-10-13 12:59:05 +0200 | Kirill Saied | Replace `.size() > 0` with `.isNotEmpty()` (#8819) | ✅ 已完成 | [0046_2268bd8ac](commits/0046_2268bd8ac/analysis.md) |
| 47 | `287f90a28` | 2023-10-13 05:52:39 -0700 | Kirill Saied | Spark: Replace .size() > 0 with isEmpty() (#8814) | ✅ 已完成 | [0047_287f90a28](commits/0047_287f90a28/analysis.md) |
| 48 | `004d3b11f` | 2023-10-13 19:22:32 +0200 | JB Onofré | Build: Upgrade to spring-web 5.3.30 (#8828) | ✅ 已完成 | [0048_004d3b11f](commits/0048_004d3b11f/analysis.md) |
| 49 | `a339f4091` | 2023-10-13 19:22:59 +0200 | JB Onofré | Build: Upgrade to Jetty 9.4.53.v20231009 (#8830) | ✅ 已完成 | [0049_a339f4091](commits/0049_a339f4091/analysis.md) |
| 50 | `2aac63688` | 2023-10-13 10:45:50 -0700 | Kirill Saied | Core: Replace `.size() > 0` with `!.isEmpty()` (#8813) | ✅ 已完成 | [0050_2aac63688](commits/0050_2aac63688/analysis.md) |
| 51 | `3e522e84f` | 2023-10-15 15:57:07 -0700 | gangy | Flink:backport PR to 1.16 #7360: Implement data statistics coordinator to aggregate data statistics from operator subtasks (#8747) | ✅ 已完成 | [0051_3e522e84f](commits/0051_3e522e84f/analysis.md) |
| 52 | `6d3b0b784` | 2023-10-15 15:59:54 -0700 | gangy | Flink:backport PR to 1.15 #7360: Implement data statistics coordinator to aggregate data statistics from operator subtasks (#8749) | ✅ 已完成 | [0052_6d3b0b784](commits/0052_6d3b0b784/analysis.md) |
| 53 | `b47fb4eb6` | 2023-10-16 07:49:24 +0200 | Fokko Driesprong | Docs: Remove AWS Version (#8842) | ✅ 已完成 | [0053_b47fb4eb6](commits/0053_b47fb4eb6/analysis.md) |
| 54 | `738103fb1` | 2023-10-16 07:50:04 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.20.162 to 2.21.0 (#8838) | ✅ 已完成 | [0054_738103fb1](commits/0054_738103fb1/analysis.md) |
| 55 | `fd1eae815` | 2023-10-16 07:50:18 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.24.0 to 26.25.0 (#8841) | ✅ 已完成 | [0055_fd1eae815](commits/0055_fd1eae815/analysis.md) |
| 56 | `49b1ceb33` | 2023-10-16 07:51:03 +0200 | dependabot[bot] | Build: Bump nessie from 0.71.1 to 0.72.0 (#8835) | ✅ 已完成 | [0056_49b1ceb33](commits/0056_49b1ceb33/analysis.md) |
| 57 | `247e715a2` | 2023-10-16 07:56:44 +0200 | dependabot[bot] | Build: Bump arrow from 12.0.1 to 13.0.0 (#8785) | ✅ 已完成 | [0057_247e715a2](commits/0057_247e715a2/analysis.md) |
| 58 | `676679016` | 2023-10-16 08:33:33 +0200 | dependabot[bot] | Build: Bump com.fasterxml.jackson.core:jackson-annotations (#8836) | ✅ 已完成 | [0058_676679016](commits/0058_676679016/analysis.md) |
| 59 | `96d2fa77f` | 2023-10-16 09:40:35 +0200 | Ajantha Bhat | Infra: Cleanup labeler.yml (#8795) | ✅ 已完成 | [0059_96d2fa77f](commits/0059_96d2fa77f/analysis.md) |
| 60 | `8f86a06c1` | 2023-10-16 09:55:35 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.43.0.0 to 3.43.2.0 (#8837) | ✅ 已完成 | [0060_8f86a06c1](commits/0060_8f86a06c1/analysis.md) |
| 61 | `05c789bee` | 2023-10-16 09:59:56 +0200 | JB Onofré | Build: add gradle configuration to enforce reproducible build (#8826) | ✅ 已完成 | [0061_05c789bee](commits/0061_05c789bee/analysis.md) |
| 62 | `46cad6dda` | 2023-10-16 08:47:02 -0700 | Bryan Keller | Core: Do not use a lazy split offset list in manifests (#8834) | ✅ 已完成 | [0062_46cad6dda](commits/0062_46cad6dda/analysis.md) |
| 63 | `cb20bdbea` | 2023-10-17 11:58:58 +0200 | JB Onofré | Build: Document missing `docker.sock` on OSX (#8766) | ✅ 已完成 | [0063_cb20bdbea](commits/0063_cb20bdbea/analysis.md) |
| 64 | `069d93010` | 2023-10-17 17:14:46 +0200 | Eduard Tudenhoefner | Spark 3.5: Use Awaitility instead of Thread.sleep() (#8853) | ✅ 已完成 | [0064_069d93010](commits/0064_069d93010/analysis.md) |
| 65 | `f7f165446` | 2023-10-17 09:00:42 -0700 | kengtin | Flink: Reverting the default custom partitioner for bucket column (#8848) | ✅ 已完成 | [0065_f7f165446](commits/0065_f7f165446/analysis.md) |
| 66 | `ad602a379` | 2023-10-17 12:13:57 -0700 | Amogh Jahagirdar | Core: Ignore split offsets when the last split offset is past the file length (#8860) | ✅ 已完成 | [0066_ad602a379](commits/0066_ad602a379/analysis.md) |
| 67 | `e837973d1` | 2023-10-18 08:07:08 +0200 | Eduard Tudenhoefner | Flink 1.17: Use awaitility instead of Thread.sleep() (#8852) | ✅ 已完成 | [0067_e837973d1](commits/0067_e837973d1/analysis.md) |
| 68 | `16c2af49a` | 2023-10-18 17:23:10 +0200 | Fokko Driesprong | Open-API: Make error required (#8765) | ✅ 已完成 | [0068_16c2af49a](commits/0068_16c2af49a/analysis.md) |
| 69 | `62662db6e` | 2023-10-19 10:04:28 +0200 | Eduard Tudenhoefner | Add missing license headers (#8875) | ✅ 已完成 | [0069_62662db6e](commits/0069_62662db6e/analysis.md) |
| 70 | `bd66d0f54` | 2023-10-19 13:47:44 +0200 | Naveen Kumar | Nessie: Use custom client builder name (#8798) | ✅ 已完成 | [0070_bd66d0f54](commits/0070_bd66d0f54/analysis.md) |
| 71 | `45da568f1` | 2023-10-19 14:50:12 +0200 | dependabot[bot] | Build: Bump org.apache.pig:pig from 0.14.0 to 0.17.0 (#8774) | ✅ 已完成 | [0071_45da568f1](commits/0071_45da568f1/analysis.md) |
| 72 | `d92be9b8d` | 2023-10-19 07:11:34 -0700 | Amogh Jahagirdar | AWS: Glue catalog strip trailing slash on DB URI (#8870) | ✅ 已完成 | [0072_d92be9b8d](commits/0072_d92be9b8d/analysis.md) |
| 73 | `d3af82f6e` | 2023-10-19 16:52:53 +0200 | Naveen Kumar | Flink 1.15: Use Awaitility instead of Thread.sleep() (#8877) | ✅ 已完成 | [0073_d3af82f6e](commits/0073_d3af82f6e/analysis.md) |
| 74 | `d581e79d3` | 2023-10-19 16:53:41 +0200 | Naveen Kumar | Flink 1.16: Use Awaitility instead of Thread.sleep() (#8880) | ✅ 已完成 | [0074_d581e79d3](commits/0074_d581e79d3/analysis.md) |
| 75 | `d1cb23416` | 2023-10-19 17:04:45 +0200 | Jongwoo Han | Build: Replace deprecated command with environment file (#8666) | ✅ 已完成 | [0075_d1cb23416](commits/0075_d1cb23416/analysis.md) |
| 76 | `046fa74ca` | 2023-10-19 23:01:15 +0200 | Hongyue/Steve Zhang | Doc: Fix Iceberg Javadoc link (#8885) | ✅ 已完成 | [0076_046fa74ca](commits/0076_046fa74ca/analysis.md) |
| 77 | `1a9a7d004` | 2023-10-20 08:48:54 +0200 | Naveen Kumar | Spark 3.4: Use Awaitility instead of Thread.sleep() (#8884) | ✅ 已完成 | [0077_1a9a7d004](commits/0077_1a9a7d004/analysis.md) |
| 78 | `bbfe30a9d` | 2023-10-20 08:49:10 +0200 | Naveen Kumar | Spark 3.3: Use Awaitility instead of Thread.sleep() (#8883) | ✅ 已完成 | [0078_bbfe30a9d](commits/0078_bbfe30a9d/analysis.md) |
| 79 | `dd4139705` | 2023-10-20 08:49:34 +0200 | Naveen Kumar | Spark 3.2: Use Awaitility instead of Thread.sleep() (#8882) | ✅ 已完成 | [0079_dd4139705](commits/0079_dd4139705/analysis.md) |
| 80 | `7a60b00cd` | 2023-10-20 09:06:16 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.13.30 to 3.14.2 (#8790) | ✅ 已完成 | [0080_7a60b00cd](commits/0080_7a60b00cd/analysis.md) |
| 81 | `0cc74f170` | 2023-10-20 10:31:16 +0200 | rice | Core: Add sort_order_id to SCAN_COLUMNS to address null sort order ID after planned data files (#8873) | ✅ 已完成 | [0081_0cc74f170](commits/0081_0cc74f170/analysis.md) |
| 82 | `1e5fcbf15` | 2023-10-20 20:45:09 +0200 | Ajantha Bhat | Infra: Update slack invite link (#8889) | ✅ 已完成 | [0082_1e5fcbf15](commits/0082_1e5fcbf15/analysis.md) |
| 83 | `6f1517546` | 2023-10-20 16:10:05 -0700 | Eduard Tudenhoefner | Core: Derive View operation from version (#8678) | ✅ 已完成 | [0083_6f1517546](commits/0083_6f1517546/analysis.md) |
| 84 | `94edb0e12` | 2023-10-20 16:13:44 -0700 | Eduard Tudenhoefner | Core: Make view metadata properties optional in JSON parser (#8723) | ✅ 已完成 | [0084_94edb0e12](commits/0084_94edb0e12/analysis.md) |
| 85 | `43fce1b56` | 2023-10-20 16:40:08 -0700 | Eduard Tudenhoefner | Core: Improvements around View catalog tests (#8865) | ✅ 已完成 | [0085_43fce1b56](commits/0085_43fce1b56/analysis.md) |
| 86 | `b1f700851` | 2023-10-21 09:27:01 +0200 | Prashant Singh | Build: Avoid Running engine and core CI on template update (#8890) | ✅ 已完成 | [0086_b1f700851](commits/0086_b1f700851/analysis.md) |
| 87 | `e2b56daf3` | 2023-10-22 13:34:00 -0700 | Brian "bits" Olsen | Docs: Add new site deployment (#8659) | ✅ 已完成 | [0087_e2b56daf3](commits/0087_e2b56daf3/analysis.md) |
| 88 | `fce19e120` | 2023-10-23 17:16:56 +0200 | Eduard Tudenhoefner | Infra: Add 1.4.1 to Bug template (#8886) | ✅ 已完成 | [0088_fce19e120](commits/0088_fce19e120/analysis.md) |
| 89 | `2ec938f3f` | 2023-10-25 13:59:16 +0200 | dependabot[bot] | Build: Bump nessie from 0.72.0 to 0.72.1 (#8900) | ✅ 已完成 | [0089_2ec938f3f](commits/0089_2ec938f3f/analysis.md) |
| 90 | `4e05dcf6b` | 2023-10-25 14:00:36 +0200 | Fokko Driesprong | Update release template (#8879) | ✅ 已完成 | [0090_4e05dcf6b](commits/0090_4e05dcf6b/analysis.md) |
| 91 | `9ff517c02` | 2023-10-25 14:00:57 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#8897) | ✅ 已完成 | [0091_9ff517c02](commits/0091_9ff517c02/analysis.md) |
| 92 | `94ae419c5` | 2023-10-25 14:01:17 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.0 to 2.21.5 (#8896) | ✅ 已完成 | [0092_94ae419c5](commits/0092_94ae419c5/analysis.md) |
| 93 | `3bced9337` | 2023-10-25 14:01:35 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.43.2.0 to 3.43.2.1 (#8893) | ✅ 已完成 | [0093_3bced9337](commits/0093_3bced9337/analysis.md) |
| 94 | `c10a4d02a` | 2023-10-25 08:57:36 -0400 | Fokko Driesprong | Infra: Disable merging explicitly in `.asf.yaml` (#8878) | ✅ 已完成 | [0094_c10a4d02a](commits/0094_c10a4d02a/analysis.md) |
| 95 | `9b9b22de3` | 2023-10-25 09:45:49 -0700 | Drew Gallardo | Spec: Fix error response model definition in OpenAPI spec (#8914) | ✅ 已完成 | [0095_9b9b22de3](commits/0095_9b9b22de3/analysis.md) |
| 96 | `aa891acf2` | 2023-10-25 10:30:21 -0700 | bknbkn | Core: Reduce unnecessary add operations in deletedPaths set (#8868) | ✅ 已完成 | [0096_aa891acf2](commits/0096_aa891acf2/analysis.md) |
| 97 | `d7f46b455` | 2023-10-26 17:44:29 -0700 | Anton Okolnychyi | Spark: Clean up FileIO instances on executors for metadata tables (#8924) | ✅ 已完成 | [0097_d7f46b455](commits/0097_d7f46b455/analysis.md) |
| 98 | `385a6bd29` | 2023-10-28 09:43:02 -0700 | Amogh Jahagirdar | Core: Ignore split offsets array when split offset is past file length (#8925) | ✅ 已完成 | [0098_385a6bd29](commits/0098_385a6bd29/analysis.md) |
| 99 | `2f487f448` | 2023-10-30 08:21:45 +0100 | dependabot[bot] | Build: Bump me.champeau.jmh:jmh-gradle-plugin from 0.7.1 to 0.7.2 (#8942) | ✅ 已完成 | [0099_2f487f448](commits/0099_2f487f448/analysis.md) |
| 100 | `884efd4c6` | 2023-10-30 08:22:00 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.5 to 2.21.10 (#8943) | ✅ 已完成 | [0100_884efd4c6](commits/0100_884efd4c6/analysis.md) |
| 101 | `b5aba2ffe` | 2023-10-30 08:22:15 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.25.0 to 26.26.0 (#8940) | ✅ 已完成 | [0101_b5aba2ffe](commits/0101_b5aba2ffe/analysis.md) |
| 102 | `b56ec5ed4` | 2023-10-30 08:22:45 +0100 | dependabot[bot] | Build: Bump nessie from 0.72.1 to 0.73.0 (#8941) | ✅ 已完成 | [0102_b56ec5ed4](commits/0102_b56ec5ed4/analysis.md) |
| 103 | `3c6259193` | 2023-10-30 09:54:39 +0100 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.17 to 1.2.18 (#8939) | ✅ 已完成 | [0103_3c6259193](commits/0103_3c6259193/analysis.md) |
| 104 | `7e9e02c0e` | 2023-10-30 12:33:11 +0100 | Naveen Kumar | AWS, Core: Use Awaitility instead of Thread.sleep() | ✅ 已完成 | [0104_7e9e02c0e](commits/0104_7e9e02c0e/analysis.md) |
| 105 | `8bb52bc39` | 2023-10-30 14:10:40 +0100 | Ashok | AWS: Remove AssertHelpers usage (#8937) | ✅ 已完成 | [0105_8bb52bc39](commits/0105_8bb52bc39/analysis.md) |
| 106 | `64e2deba5` | 2023-10-30 14:33:48 +0100 | wangtaohz | Core: Fix NPE when calling InMemoryLockManager#release using Hadoop catalog (#8494) | ✅ 已完成 | [0106_64e2deba5](commits/0106_64e2deba5/analysis.md) |
| 107 | `a7828dbeb` | 2023-10-30 15:02:16 +0100 | Amogh Jahagirdar | API, Core: Add uuid API to Table (#8800) | ✅ 已完成 | [0107_a7828dbeb](commits/0107_a7828dbeb/analysis.md) |
| 108 | `0f44262a9` | 2023-10-30 16:53:24 +0100 | Hussein Awala | Docs: Fix typos (#8892) | ✅ 已完成 | [0108_0f44262a9](commits/0108_0f44262a9/analysis.md) |
| 109 | `a66166326` | 2023-10-30 16:58:59 +0100 | Fokko Driesprong | Open-API: Refactor TableRequirements (#7710) | ✅ 已完成 | [0109_a66166326](commits/0109_a66166326/analysis.md) |
| 110 | `fceea89cb` | 2023-10-30 10:53:23 -0700 | Anton Okolnychyi | Spark 3.5: Don't cache or reuse manifest entries while rewriting metadata by default (#8935) | ✅ 已完成 | [0110_fceea89cb](commits/0110_fceea89cb/analysis.md) |
| 111 | `c171c5703` | 2023-10-30 17:34:48 -0700 | Anton Okolnychyi | Spark 3.2: Don't cache or reuse manifest entries while rewriting metadata by default (#8956) | ✅ 已完成 | [0111_c171c5703](commits/0111_c171c5703/analysis.md) |
| 112 | `721419db0` | 2023-10-30 17:35:04 -0700 | Anton Okolnychyi | Spark 3.3: Don't cache or reuse manifest entries while rewriting metadata by default (#8955) | ✅ 已完成 | [0112_721419db0](commits/0112_721419db0/analysis.md) |
| 113 | `86bb1c09f` | 2023-10-30 17:35:59 -0700 | Anton Okolnychyi | Spark 3.4: Don't cache or reuse manifest entries while rewriting metadata by default (#8954) | ✅ 已完成 | [0113_86bb1c09f](commits/0113_86bb1c09f/analysis.md) |
| 114 | `4433aa8c6` | 2023-10-31 07:57:49 +0100 | Eduard Tudenhoefner | API, Core: Add uuid() to View (#8851) | ✅ 已完成 | [0114_4433aa8c6](commits/0114_4433aa8c6/analysis.md) |
| 115 | `b9a4478b0` | 2023-10-31 11:03:31 +0100 | Ashok | Spark 3.5:  Remove AssertHelpers usage (#8948) | ✅ 已完成 | [0115_b9a4478b0](commits/0115_b9a4478b0/analysis.md) |
| 116 | `eea554746` | 2023-10-31 14:58:59 +0100 | Ashok | Flink 1.15: Remove usage of AssertHelpers (#8945) | ✅ 已完成 | [0116_eea554746](commits/0116_eea554746/analysis.md) |
| 117 | `d4d747d55` | 2023-10-31 16:02:13 +0100 | Ashok | Flink 1.16: Remove usage of AssertHelpers (#8946) | ✅ 已完成 | [0117_d4d747d55](commits/0117_d4d747d55/analysis.md) |
| 118 | `da555037c` | 2023-10-31 09:15:24 -0700 | Jacob Marble | Spec: add nanosecond timestamp types (#8683) | ✅ 已完成 | [0118_da555037c](commits/0118_da555037c/analysis.md) |
| 119 | `0e09ac15e` | 2023-10-31 17:20:38 +0100 | Ajantha Bhat | Docs: Document UNORDERED for spark write (#8958) | ✅ 已完成 | [0119_0e09ac15e](commits/0119_0e09ac15e/analysis.md) |
| 120 | `50c5f267b` | 2023-10-31 09:42:02 -0700 | Anton Okolnychyi | Core, Spark: Avoid extra copies of manifests while optimizing V2 tables (#8928) | ✅ 已完成 | [0120_50c5f267b](commits/0120_50c5f267b/analysis.md) |
| 121 | `da392f259` | 2023-10-31 10:59:49 -0700 | Anton Okolnychyi | Spark: Use SerializableTableWithSize when optimizing metadata (#8957) | ✅ 已完成 | [0121_da392f259](commits/0121_da392f259/analysis.md) |
| 122 | `8387b508f` | 2023-11-01 14:30:35 +0100 | Ashok | Spark 3.4: Remove usage of AssertHelpers (#8963) | ✅ 已完成 | [0122_8387b508f](commits/0122_8387b508f/analysis.md) |
| 123 | `52e69fb2b` | 2023-11-01 08:15:29 -0700 | Ajantha Bhat | Spec: Add partition stats spec (#7105) | ✅ 已完成 | [0123_52e69fb2b](commits/0123_52e69fb2b/analysis.md) |
| 124 | `0180ef91a` | 2023-11-01 18:48:22 -0700 | Hongyue/Steve Zhang | Core: Scan only live entries in partitions table (#8969) | ✅ 已完成 | [0124_0180ef91a](commits/0124_0180ef91a/analysis.md) |
| 125 | `0b5aacd94` | 2023-11-02 08:50:42 +0100 | Wing Yew Poon | Core: Use ParallelIterable in Deletes::toPositionIndex (6387) (#8805) | ✅ 已完成 | [0125_0b5aacd94](commits/0125_0b5aacd94/analysis.md) |
| 126 | `b95330a25` | 2023-11-02 12:51:32 +0100 | Hussein Awala | Remove outdated `tox` command from doc (#8961) | ✅ 已完成 | [0126_b95330a25](commits/0126_b95330a25/analysis.md) |
| 127 | `94de98555` | 2023-11-02 14:22:17 +0100 | Ajantha Bhat | Parquet: Remove duplicate test code (#8098) | ✅ 已完成 | [0127_94de98555](commits/0127_94de98555/analysis.md) |
| 128 | `4a3d266a6` | 2023-11-02 09:42:11 -0700 | Anton Okolnychyi | Spark 3.5: Use DataFile constants in SparkDataFile (#8936) | ✅ 已完成 | [0128_4a3d266a6](commits/0128_4a3d266a6/analysis.md) |
| 129 | `a44592501` | 2023-11-02 18:19:55 -0700 | Karuppayya | Spark 3.5: Display more read metrics on Spark SQL UI (#8717) | ✅ 已完成 | [0129_a44592501](commits/0129_a44592501/analysis.md) |
| 130 | `2c890109c` | 2023-11-02 20:39:45 -0700 | Anton Okolnychyi | Spark: Fix usage of staging location when optimizing metadata (#8959) | ✅ 已完成 | [0130_2c890109c](commits/0130_2c890109c/analysis.md) |
| 131 | `b0bf62a44` | 2023-11-03 19:19:33 -0700 | Anton Okolnychyi | Spark 3.5: Use rolling manifest writers when optimizing metadata (#8972) | ✅ 已完成 | [0131_b0bf62a44](commits/0131_b0bf62a44/analysis.md) |
| 132 | `0b54f1e00` | 2023-11-06 12:28:57 +0100 | dependabot[bot] | Build: Bump arrow from 13.0.0 to 14.0.0 (#8984) | ✅ 已完成 | [0132_0b54f1e00](commits/0132_0b54f1e00/analysis.md) |
| 133 | `f3e507171` | 2023-11-06 12:29:30 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.10 to 2.21.15 (#8983) | ✅ 已完成 | [0133_f3e507171](commits/0133_f3e507171/analysis.md) |
| 134 | `7c4bdaa3a` | 2023-11-07 07:27:45 +0100 | Thomas | Core: De-dup props in JdbcUtil (#8992) | ✅ 已完成 | [0134_7c4bdaa3a](commits/0134_7c4bdaa3a/analysis.md) |
| 135 | `e8bb8b502` | 2023-11-07 14:55:41 -0600 | roryqi | Test: Add a test utility method to programmatically create expected partition specs (#8467) | ✅ 已完成 | [0135_e8bb8b502](commits/0135_e8bb8b502/analysis.md) |
| 136 | `6105375d4` | 2023-11-08 08:00:44 +0100 | Amogh Jahagirdar | Infra: Add 1.4.2 as latest release to issue template (#9001) | ✅ 已完成 | [0136_6105375d4](commits/0136_6105375d4/analysis.md) |
| 137 | `1fb8e4fbd` | 2023-11-08 10:41:44 +0100 | Wonjae Lee | Core: Add a constructor to StaticTableOperations (#8996) | ✅ 已完成 | [0137_1fb8e4fbd](commits/0137_1fb8e4fbd/analysis.md) |
| 138 | `e8cf33db7` | 2023-11-08 12:40:02 +0100 | Rui Li | Docs: Add note that snapshot expiration and cleanup orphan files could corrupt Flink job state (#9002) | ✅ 已完成 | [0138_e8cf33db7](commits/0138_e8cf33db7/analysis.md) |
| 139 | `af132c7f8` | 2023-11-08 12:51:38 -0800 | Jacob Marble | Spec: Clarify ns timestamps for ORC deserialization (#9007) | ✅ 已完成 | [0139_af132c7f8](commits/0139_af132c7f8/analysis.md) |
| 140 | `f00d3094a` | 2023-11-08 16:31:28 -0800 | Karuppayya | Spark 3.4: Display more read metrics on Spark SQL UI (#9009) | ✅ 已完成 | [0140_f00d3094a](commits/0140_f00d3094a/analysis.md) |
| 141 | `175a7fb7a` | 2023-11-09 15:44:20 +0100 | Eduard Tudenhoefner | Core: Use InMemoryCatalog as backend catalog (#9014) | ✅ 已完成 | [0141_175a7fb7a](commits/0141_175a7fb7a/analysis.md) |
| 142 | `942988744` | 2023-11-09 13:43:24 -0800 | Anton Okolnychyi | Spark 3.5: Fix rewriting manifests for evolved unpartitioned V1 tables (#9015) | ✅ 已完成 | [0142_942988744](commits/0142_942988744/analysis.md) |
| 143 | `8c625dd7d` | 2023-11-09 14:54:32 -0800 | Anton Okolnychyi | Core: Support replacing delete manifests (#9000) | ✅ 已完成 | [0143_8c625dd7d](commits/0143_8c625dd7d/analysis.md) |
| 144 | `255986a8e` | 2023-11-09 15:30:28 -0800 | Anton Okolnychyi | Spark 3.4: Use rolling manifest writers when optimizing metadata (#9019) | ✅ 已完成 | [0144_255986a8e](commits/0144_255986a8e/analysis.md) |
| 145 | `7cec1d979` | 2023-11-09 16:26:11 -0800 | Anton Okolnychyi | Docs: Fix Javadoc for ManifestFile (#9016) | ✅ 已完成 | [0145_7cec1d979](commits/0145_7cec1d979/analysis.md) |
| 146 | `09e6a9f7b` | 2023-11-10 12:31:58 +0100 | Ajantha Bhat | Spec: Fix view example (#8966) | ✅ 已完成 | [0146_09e6a9f7b](commits/0146_09e6a9f7b/analysis.md) |
| 147 | `774d0e8bc` | 2023-11-10 19:51:28 +0100 | zhaoym | Docs: `DataFrameReader` does not take parameters (#9021) | ✅ 已完成 | [0147_774d0e8bc](commits/0147_774d0e8bc/analysis.md) |
| 148 | `6a9c182b4` | 2023-11-10 11:36:01 -0800 | Huaxin Gao | Spark 3.5: Set useCommitCoordinator to false in batch writes (#9017) | ✅ 已完成 | [0148_6a9c182b4](commits/0148_6a9c182b4/analysis.md) |
| 149 | `13fd06d90` | 2023-11-11 10:15:38 -0800 | Huaxin Gao | Spark 3.5: Set useCommitCoordinator to false in streaming writes (#9027) | ✅ 已完成 | [0149_13fd06d90](commits/0149_13fd06d90/analysis.md) |
| 150 | `d7f8e91c0` | 2023-11-11 10:17:24 -0800 | Huaxin Gao | Spark 3.4: Set useCommitCoordinator to false in batch writes (#9028) | ✅ 已完成 | [0150_d7f8e91c0](commits/0150_d7f8e91c0/analysis.md) |
| 151 | `fd4231f5e` | 2023-11-12 19:09:55 -0800 | Anton Okolnychyi | API: Optimize equals in CharSequenceWrapper (#9035) | ✅ 已完成 | [0151_fd4231f5e](commits/0151_fd4231f5e/analysis.md) |
| 152 | `930750d3e` | 2023-11-13 09:59:56 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.15 to 2.21.21 (#9044) | ✅ 已完成 | [0152_930750d3e](commits/0152_930750d3e/analysis.md) |
| 153 | `7ece5faaf` | 2023-11-13 10:00:17 +0100 | dependabot[bot] | Build: Bump orc from 1.9.1 to 1.9.2 (#9045) | ✅ 已完成 | [0153_7ece5faaf](commits/0153_7ece5faaf/analysis.md) |
| 154 | `28a956525` | 2023-11-13 10:00:35 +0100 | dependabot[bot] | Build: Bump arrow from 14.0.0 to 14.0.1 (#9043) | ✅ 已完成 | [0154_28a956525](commits/0154_28a956525/analysis.md) |
| 155 | `7f21b538f` | 2023-11-13 10:00:52 +0100 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.14.2 to 3.14.3 (#9039) | ✅ 已完成 | [0155_7f21b538f](commits/0155_7f21b538f/analysis.md) |
| 156 | `9476f62ff` | 2023-11-13 10:09:10 +0100 | dependabot[bot] | Build: Bump junit from 5.10.0 to 5.10.1 (#9037) | ✅ 已完成 | [0156_9476f62ff](commits/0156_9476f62ff/analysis.md) |
| 157 | `7ff8f2645` | 2023-11-13 10:09:36 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.26.0 to 26.27.0 (#9036) | ✅ 已完成 | [0157_7ff8f2645](commits/0157_7ff8f2645/analysis.md) |
| 158 | `fd00207cd` | 2023-11-13 14:26:25 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.43.2.1 to 3.44.0.0 (#9051) | ✅ 已完成 | [0158_fd00207cd](commits/0158_fd00207cd/analysis.md) |
| 159 | `efa5945f1` | 2023-11-13 23:58:05 +0100 | Fokko Driesprong | Add dependabot to automatically update the site (#9004) | ✅ 已完成 | [0159_efa5945f1](commits/0159_efa5945f1/analysis.md) |
| 160 | `9cfbbdc9e` | 2023-11-14 07:58:28 +0100 | dependabot[bot] | Build: Bump mkdocs-macros-plugin from 1.0.4 to 1.0.5 (#9058) | ✅ 已完成 | [0160_9cfbbdc9e](commits/0160_9cfbbdc9e/analysis.md) |
| 161 | `9c51427f1` | 2023-11-14 07:59:04 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.22.0 to 0.23.0 (#9054) | ✅ 已完成 | [0161_9c51427f1](commits/0161_9c51427f1/analysis.md) |
| 162 | `75ff4a99a` | 2023-11-14 08:09:51 +0100 | dependabot[bot] | Build: Bump mkdocs-material-extensions from 1.1.1 to 1.3 (#9052) | ✅ 已完成 | [0162_75ff4a99a](commits/0162_75ff4a99a/analysis.md) |
| 163 | `69025712b` | 2023-11-14 09:47:44 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.1.21 to 9.4.8 (#9055) | ✅ 已完成 | [0163_69025712b](commits/0163_69025712b/analysis.md) |
| 164 | `188847428` | 2023-11-14 10:36:30 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.21 to 2.21.22 (#9053) | ✅ 已完成 | [0164_188847428](commits/0164_188847428/analysis.md) |
| 165 | `fba7d6125` | 2023-11-14 18:58:25 +0100 | Eduard Tudenhoefner | GCP: Use correct Guava imports (#9067) | ✅ 已完成 | [0165_fba7d6125](commits/0165_fba7d6125/analysis.md) |
| 166 | `6ec3de390` | 2023-11-14 13:09:08 -0800 | pvary | Core: Enable column statistics filtering after planning (#8803) | ✅ 已完成 | [0166_6ec3de390](commits/0166_6ec3de390/analysis.md) |
| 167 | `bfe1d03c7` | 2023-11-15 20:52:47 -0800 | zhen | Spark 3.5: Support metadata columns in staged scan (#8872) | ✅ 已完成 | [0167_bfe1d03c7](commits/0167_bfe1d03c7/analysis.md) |
| 168 | `72da856b3` | 2023-11-16 16:38:24 +0100 | Tom Tanaka | Docs: Fix parquet default compression codec (#9096) | ✅ 已完成 | [0168_72da856b3](commits/0168_72da856b3/analysis.md) |
| 169 | `798f1c8ab` | 2023-11-16 16:39:54 +0100 | Robert Stupp | Azure: Allow shared-key auth for testing purposes (#9068) | ✅ 已完成 | [0169_798f1c8ab](commits/0169_798f1c8ab/analysis.md) |
| 170 | `ccaeb2f4d` | 2023-11-16 08:33:02 -0800 | Anton Okolnychyi | Core: Disallow setting equality field IDs for data (#8970) | ✅ 已完成 | [0170_ccaeb2f4d](commits/0170_ccaeb2f4d/analysis.md) |
| 171 | `2e2ac8dfd` | 2023-11-16 11:31:17 -0600 | Anton Okolnychyi | Core: Fix split size calculations in file rewriters (#9069) | ✅ 已完成 | [0171_2e2ac8dfd](commits/0171_2e2ac8dfd/analysis.md) |
| 172 | `3d6072ad4` | 2023-11-16 15:41:29 -0800 | Anton Okolnychyi | API: Add CharSequenceMap (#9047) | ✅ 已完成 | [0172_3d6072ad4](commits/0172_3d6072ad4/analysis.md) |
| 173 | `1e2a71398` | 2023-11-16 16:16:15 -0800 | Huaxin Gao | Parquet: Add log entry when Bloom filters are used (#9010) | ✅ 已完成 | [0173_1e2a71398](commits/0173_1e2a71398/analysis.md) |
| 174 | `17c7815d4` | 2023-11-17 14:52:17 +0100 | Robert Stupp | GCS: Allow no-auth for testing purposes (#9061) | ✅ 已完成 | [0174_17c7815d4](commits/0174_17c7815d4/analysis.md) |
| 175 | `abbfdae10` | 2023-11-17 17:26:32 -0800 | zhen | Spark 3.4, 3.3: Support metadata columns in staged scans (#9098) | ✅ 已完成 | [0175_abbfdae10](commits/0175_abbfdae10/analysis.md) |
| 176 | `e69418ae8` | 2023-11-17 18:34:25 -0800 | Anton Okolnychyi | Spark 3.5: Extend action for rewriting manifests to support deletes (#9020) | ✅ 已完成 | [0176_e69418ae8](commits/0176_e69418ae8/analysis.md) |
| 177 | `b6b5d4473` | 2023-11-19 07:12:36 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.23.0 to 0.24.2 (#9109) | ✅ 已完成 | [0177_b6b5d4473](commits/0177_b6b5d4473/analysis.md) |
| 178 | `8c7ebeee8` | 2023-11-19 07:57:53 +0100 | dependabot[bot] | Build: Bump openapi-spec-validator from 0.5.2 to 0.7.1 (#9057) | ✅ 已完成 | [0178_8c7ebeee8](commits/0178_8c7ebeee8/analysis.md) |
| 179 | `9e94dc857` | 2023-11-19 10:39:06 +0100 | Fokko Driesprong | Open-API: Remove pydantic pin (#9110) | ✅ 已完成 | [0179_9e94dc857](commits/0179_9e94dc857/analysis.md) |
| 180 | `573f3f30d` | 2023-11-19 10:39:44 +0100 | dependabot[bot] | Build: Bump com.fasterxml.jackson.core:jackson-annotations (#9106) | ✅ 已完成 | [0180_573f3f30d](commits/0180_573f3f30d/analysis.md) |
| 181 | `a3bf0c7e3` | 2023-11-19 10:40:02 +0100 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.19.1 to 1.19.2 (#9103) | ✅ 已完成 | [0181_a3bf0c7e3](commits/0181_a3bf0c7e3/analysis.md) |
| 182 | `04d1a9d85` | 2023-11-19 10:40:46 +0100 | dependabot[bot] | Build: Bump com.fasterxml.jackson.dataformat:jackson-dataformat-xml (#9107) | ✅ 已完成 | [0182_04d1a9d85](commits/0182_04d1a9d85/analysis.md) |
| 183 | `c193de9e8` | 2023-11-19 15:56:55 -0800 | Amogh Jahagirdar | Spark: Fix metadata delete check with branches (#9102) | ✅ 已完成 | [0183_c193de9e8](commits/0183_c193de9e8/analysis.md) |
| 184 | `f8d21116b` | 2023-11-20 08:38:33 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.22 to 2.21.26 (#9105) | ✅ 已完成 | [0184_f8d21116b](commits/0184_f8d21116b/analysis.md) |
| 185 | `3f90a23c1` | 2023-11-20 21:42:08 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.4.8 to 9.4.10 (#9114) | ✅ 已完成 | [0185_3f90a23c1](commits/0185_3f90a23c1/analysis.md) |
| 186 | `7320899de` | 2023-11-20 14:06:54 -0800 | Amogh Jahagirdar | Spark 3.3, 3.4: Backport fix for metadata delete condition check for branches (#9115) | ✅ 已完成 | [0186_7320899de](commits/0186_7320899de/analysis.md) |
| 187 | `506cdbfd5` | 2023-11-20 15:08:59 -0800 | Yujiang Zhong | Spark: Add SQL config to control locality (#9101) | ✅ 已完成 | [0187_506cdbfd5](commits/0187_506cdbfd5/analysis.md) |
| 188 | `42614cc8d` | 2023-11-21 08:25:56 +0100 | Anton Okolnychyi | Core: Remove synchronization from BitmapPositionDeleteIndex (#9119) | ✅ 已完成 | [0188_42614cc8d](commits/0188_42614cc8d/analysis.md) |
| 189 | `c61c3ca01` | 2023-11-21 12:20:41 -0800 | Anton Okolnychyi | Data: Always use delete index for position deletes (#9117) | ✅ 已完成 | [0189_c61c3ca01](commits/0189_c61c3ca01/analysis.md) |
| 190 | `229a243dd` | 2023-11-21 20:53:23 -0800 | przemekd | Core: Lazily create LocationProvider in SerializableTable (#9029) | ✅ 已完成 | [0190_229a243dd](commits/0190_229a243dd/analysis.md) |
| 191 | `0831eb03b` | 2023-11-23 10:40:33 +0100 | pvary | Flink: Emit watermarks from the IcebergSource (#8553) | ✅ 已完成 | [0191_0831eb03b](commits/0191_0831eb03b/analysis.md) |
| 192 | `c11909907` | 2023-11-23 12:05:44 +0100 | Prabhu Joseph | Docs: Remove UNIQUE keyword as it is not supported in Flink (#9046) | ✅ 已完成 | [0192_c11909907](commits/0192_c11909907/analysis.md) |
| 193 | `13fcf62b8` | 2023-11-23 10:12:26 -0800 | Amogh Jahagirdar | Spark: Fix Fast forward before/after snapshot output for non-main branches (#8854) | ✅ 已完成 | [0193_13fcf62b8](commits/0193_13fcf62b8/analysis.md) |
| 194 | `c817c8503` | 2023-11-24 06:46:39 +0100 | Naveen Kumar | Hive: Refactor HiveTableOperations with common code for View. (#9011) | ✅ 已完成 | [0194_c817c8503](commits/0194_c817c8503/analysis.md) |
| 195 | `c427a5628` | 2023-11-24 07:55:52 +0100 | CG | Flink: Create JUnit5 version of FlinkTestBase (#9120) | ✅ 已完成 | [0195_c427a5628](commits/0195_c427a5628/analysis.md) |
| 196 | `b20d30ca0` | 2023-11-24 12:30:04 +0100 | Tom Tanaka | Spark: Create base classes for migration to JUnit5 (#9129) | ✅ 已完成 | [0196_b20d30ca0](commits/0196_b20d30ca0/analysis.md) |
| 197 | `10a856e0f` | 2023-11-24 17:57:04 +0100 | pvary | Flink: Proper backport for #8852 (#9146) | ✅ 已完成 | [0197_10a856e0f](commits/0197_10a856e0f/analysis.md) |
| 198 | `1a073ddc4` | 2023-11-24 11:00:29 -0800 | pvary | Flink: Backport #8803 to v1.16 and v1.15 (#9144) | ✅ 已完成 | [0198_1a073ddc4](commits/0198_1a073ddc4/analysis.md) |
| 199 | `f246614ed` | 2023-11-27 11:16:19 +0100 | CG | Flink: Backport #9078 to v1.16 and v1.15 (#9151) | ✅ 已完成 | [0199_f246614ed](commits/0199_f246614ed/analysis.md) |
| 200 | `6fc5be738` | 2023-11-27 11:24:39 -0800 | Amogh Jahagirdar | API, Core: Fix naming in fastForwardBranch/replaceBranch APIs (#9134) | ✅ 已完成 | [0200_6fc5be738](commits/0200_6fc5be738/analysis.md) |
| 201 | `4e62b58f0` | 2023-11-27 16:41:32 -0800 | Andre Luis Anastacio | AWS, Core, Dell, Spark: Use Strings to verify null and empty string (#9090) | ✅ 已完成 | [0201_4e62b58f0](commits/0201_4e62b58f0/analysis.md) |
| 202 | `b21a8ce24` | 2023-11-28 10:41:38 -0800 | Steven Zhen Wu | API: add StructTransform base class for PartitionKey and SortKey. add SortOrderComparators (#7798) | ✅ 已完成 | [0202_b21a8ce24](commits/0202_b21a8ce24/analysis.md) |
| 203 | `5fb26f839` | 2023-11-28 22:36:43 +0100 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.19.2 to 1.19.3 (#9155) | ✅ 已完成 | [0203_5fb26f839](commits/0203_5fb26f839/analysis.md) |
| 204 | `5e059c1bf` | 2023-11-28 22:59:44 +0100 | pvary | Flink: Backport #8553 to v1.15, v1.16 (#9145) | ✅ 已完成 | [0204_5e059c1bf](commits/0204_5e059c1bf/analysis.md) |
| 205 | `d2ab70927` | 2023-11-29 00:14:28 +0100 | dependabot[bot] | Build: Bump nessie from 0.73.0 to 0.74.0 (#9153) | ✅ 已完成 | [0205_d2ab70927](commits/0205_d2ab70927/analysis.md) |
| 206 | `d247b20f1` | 2023-11-28 15:52:55 -0800 | Anton Okolnychyi | Core: Remove deprecated code in DeleteFileIndex (#9166) | ✅ 已完成 | [0206_d247b20f1](commits/0206_d247b20f1/analysis.md) |
| 207 | `ac71ceaa8` | 2023-11-30 21:04:38 -0800 | dependabot[bot] | Build: Bump mkdocs-material from 9.4.10 to 9.4.12 (#9159) | ✅ 已完成 | [0207_ac71ceaa8](commits/0207_ac71ceaa8/analysis.md) |
| 208 | `de2505027` | 2023-11-30 21:06:09 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.26 to 2.21.29 (#9154) | ✅ 已完成 | [0208_de2505027](commits/0208_de2505027/analysis.md) |
| 209 | `09a8ad5c3` | 2023-12-02 13:16:07 -0800 | Daniel Weeks | Core: REST HttpClient connections config (#9195) | ✅ 已完成 | [0209_09a8ad5c3](commits/0209_09a8ad5c3/analysis.md) |
| 210 | `9bd62f79f` | 2023-12-03 18:15:20 +0100 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9202) | ✅ 已完成 | [0210_9bd62f79f](commits/0210_9bd62f79f/analysis.md) |
| 211 | `1ed1b4ba9` | 2023-12-04 11:39:27 -0800 | emkornfield | Spec: Clarify partition equality (#9125) | ✅ 已完成 | [0211_1ed1b4ba9](commits/0211_1ed1b4ba9/analysis.md) |
| 212 | `99843f03e` | 2023-12-04 14:46:37 -0800 | Andrew Sherman | Core: Expired Snapshot files in a transaction should be deleted. (#9183) | ✅ 已完成 | [0212_99843f03e](commits/0212_99843f03e/analysis.md) |
| 213 | `a4d47567e` | 2023-12-05 12:03:43 +0100 | Eduard Tudenhoefner | Core: Schema for a branch should return table schema (#9131) | ✅ 已完成 | [0213_a4d47567e](commits/0213_a4d47567e/analysis.md) |
| 214 | `cbd33a6f6` | 2023-12-05 13:08:48 +0100 | Steven Zhen Wu | Flink: fix flaky test that might fail due to classloader check (#9216) | ✅ 已完成 | [0214_cbd33a6f6](commits/0214_cbd33a6f6/analysis.md) |
| 215 | `b4c050bc9` | 2023-12-05 13:30:25 +0100 | Li Han | Aliyun: Switch iceberg-aliyun's tests to JUnit5 (#9122) | ✅ 已完成 | [0215_b4c050bc9](commits/0215_b4c050bc9/analysis.md) |
| 216 | `f19643a93` | 2023-12-05 08:03:47 -0800 | Eduard Tudenhoefner | Core: Add View support for REST catalog (#7913) | ✅ 已完成 | [0216_f19643a93](commits/0216_f19643a93/analysis.md) |
| 217 | `afe4aec4d` | 2023-12-05 08:14:09 -0800 | Eduard Tudenhoefner | Spark: Don't allow branch_ usage with VERSION AS OF (#9219) | ✅ 已完成 | [0217_afe4aec4d](commits/0217_afe4aec4d/analysis.md) |
| 218 | `8519224de` | 2023-12-05 08:48:10 -0800 | pvary | Flink: Document watermark generation feature (#9179) | ✅ 已完成 | [0218_8519224de](commits/0218_8519224de/analysis.md) |
| 219 | `68d491e6a` | 2023-12-05 18:27:27 +0100 | Fokko Driesprong | Build: Bump datamodel-code-generator from 0.24.2 to 0.25.0 (#9189) | ✅ 已完成 | [0219_68d491e6a](commits/0219_68d491e6a/analysis.md) |
| 220 | `7b12a4171` | 2023-12-05 18:44:06 +0100 | Steven Zhen Wu | Flink: backport PR #9216 for disabling classloader check (#9226) | ✅ 已完成 | [0220_7b12a4171](commits/0220_7b12a4171/analysis.md) |
| 221 | `8b7a280a9` | 2023-12-05 18:45:02 +0100 | dependabot[bot] | Build: Bump actions/setup-java from 3 to 4 (#9200) | ✅ 已完成 | [0221_8b7a280a9](commits/0221_8b7a280a9/analysis.md) |
| 222 | `d80d7da3d` | 2023-12-05 10:04:47 -0800 | Eduard Tudenhoefner | Core: Handle IAE in default error handler (#9225) | ✅ 已完成 | [0222_d80d7da3d](commits/0222_d80d7da3d/analysis.md) |
| 223 | `faa8b5075` | 2023-12-05 13:13:33 -0800 | Amogh Jahagirdar | Core: Fix logic for determining set of committed files in BaseTransaction when there are no new snapshots (#9221) | ✅ 已完成 | [0223_faa8b5075](commits/0223_faa8b5075/analysis.md) |
| 224 | `8e1900dc9` | 2023-12-05 13:48:30 -0800 | Junhao Liu | Style: Replace Arrays.asList with Collections.singletonList (#9213) | ✅ 已完成 | [0224_8e1900dc9](commits/0224_8e1900dc9/analysis.md) |
| 225 | `367dc8b1b` | 2023-12-05 17:27:42 -0800 | Amogh Jahagirdar | Core: Add comment property to ViewProperties (#9181) | ✅ 已完成 | [0225_367dc8b1b](commits/0225_367dc8b1b/analysis.md) |
| 226 | `a89fc4646` | 2023-12-06 09:07:46 +0100 | emkornfield | Spec: Clarify how column IDs are required (#9162) | ✅ 已完成 | [0226_a89fc4646](commits/0226_a89fc4646/analysis.md) |
| 227 | `70ec4e5ea` | 2023-12-06 09:36:33 -0600 | Ajantha Bhat | Spark: Bump Spark minor versions for 3.3 and 3.4 (#9187) | ✅ 已完成 | [0227_70ec4e5ea](commits/0227_70ec4e5ea/analysis.md) |
| 228 | `d69ba0568` | 2023-12-06 11:22:13 -0800 | Eduard Tudenhoefner | Core: Introduce AssertViewUUID for REST catalog views (#8831) | ✅ 已完成 | [0228_d69ba0568](commits/0228_d69ba0568/analysis.md) |
| 229 | `e27675329` | 2023-12-06 13:20:10 -0800 | Anton Okolnychyi | Core: Fix equality in StructLikeMap (#9236) | ✅ 已完成 | [0229_e27675329](commits/0229_e27675329/analysis.md) |
| 230 | `6a9d3c779` | 2023-12-06 17:31:50 -0800 | Anton Okolnychyi | Core: Add PartitionMap (#9194) | ✅ 已完成 | [0230_6a9d3c779](commits/0230_6a9d3c779/analysis.md) |
| 231 | `af9522ac7` | 2023-12-07 12:11:30 +0100 | Wing Yew Poon | Docs: Document reading in Spark using branch and tag identifiers (#9238) | ✅ 已完成 | [0231_af9522ac7](commits/0231_af9522ac7/analysis.md) |
| 232 | `ea7665e78` | 2023-12-07 12:34:21 +0100 | Alexandre Dutra | Nessie: Reimplement namespace operations (#8857) | ✅ 已完成 | [0232_ea7665e78](commits/0232_ea7665e78/analysis.md) |
| 233 | `d9295903a` | 2023-12-07 14:21:35 +0100 | Yujiang Zhong | Docs: Update default format version to 2. (#9239) | ✅ 已完成 | [0233_d9295903a](commits/0233_d9295903a/analysis.md) |
| 234 | `820fc3ced` | 2023-12-07 11:10:22 -0800 | Rodrigo Meneses | Flink: Move flink/v1.17 to flink/v1.18 | ✅ 已完成 | [0234_820fc3ced](commits/0234_820fc3ced/analysis.md) |
| 235 | `b8ef64a2c` | 2023-12-07 11:10:22 -0800 | Rodrigo Meneses | Recover flink/1.17 files from history | ✅ 已完成 | [0235_b8ef64a2c](commits/0235_b8ef64a2c/analysis.md) |
| 236 | `274390f3d` | 2023-12-07 11:10:22 -0800 | Rodrigo Meneses | Remove Flink 1.15 | ✅ 已完成 | [0236_274390f3d](commits/0236_274390f3d/analysis.md) |
| 237 | `22b95dc70` | 2023-12-07 11:10:22 -0800 | Rodrigo Meneses | Make Flink 1.18 to work | ✅ 已完成 | [0237_22b95dc70](commits/0237_22b95dc70/analysis.md) |
| 238 | `b79a8ffce` | 2023-12-07 11:33:20 -0800 | HonahX | Delta: Fix integration tests and Create DataFile by partition values instead of path (#8398) | ✅ 已完成 | [0238_b79a8ffce](commits/0238_b79a8ffce/analysis.md) |
| 239 | `263b53050` | 2023-12-07 15:34:27 -0600 | Pucheng Yang | Spark 3.5: Support Specifying spec_id in RewriteManifestProcedure (#9242) | ✅ 已完成 | [0239_263b53050](commits/0239_263b53050/analysis.md) |
| 240 | `feeaa8c73` | 2023-12-08 01:22:36 -0800 | Anton Okolnychyi | Spark 3.5: Rework DeleteFileIndexBenchmark (#9165) | ✅ 已完成 | [0240_feeaa8c73](commits/0240_feeaa8c73/analysis.md) |
| 241 | `3f5f4d924` | 2023-12-08 09:20:26 -0600 | Pucheng Yang | Spark 3.2, 3.3, 3.4: Support specifying spec_id in RewriteManifestProcedure (#9243)(#9242) | ✅ 已完成 | [0241_3f5f4d924](commits/0241_3f5f4d924/analysis.md) |
| 242 | `504c13428` | 2023-12-08 09:18:56 -0800 | bknbkn | Spark 3.5: Fix testReplacePartitionField for rewriting manifests (#9250) | ✅ 已完成 | [0242_504c13428](commits/0242_504c13428/analysis.md) |
| 243 | `62a23a377` | 2023-12-08 12:52:29 -0800 | Anton Okolnychyi | Core: Fix null partitions in PartitionSet (#9248) | ✅ 已完成 | [0243_62a23a377](commits/0243_62a23a377/analysis.md) |
| 244 | `beb41b649` | 2023-12-08 12:57:44 -0800 | Steven Zhen Wu | Flink: switch to use SortKey for data statistics (#9212) | ✅ 已完成 | [0244_beb41b649](commits/0244_beb41b649/analysis.md) |
| 245 | `4d0b69beb` | 2023-12-09 09:06:32 +0100 | Mason Chen | Flink: Fix IcebergSource tableloader lifecycle management in batch mode (#9173) | ✅ 已完成 | [0245_4d0b69beb](commits/0245_4d0b69beb/analysis.md) |
| 246 | `21522697c` | 2023-12-09 11:12:33 -0800 | Steven Wu | Flink: backport PR #9212 to 1.16 for switching to SortKey for data statistics | ✅ 已完成 | [0246_21522697c](commits/0246_21522697c/analysis.md) |
| 247 | `2c31acc8a` | 2023-12-09 11:12:33 -0800 | Steven Wu | Flink: backport PR #9212 to 1.18 for switching to SortKey for data statistics | ✅ 已完成 | [0247_2c31acc8a](commits/0247_2c31acc8a/analysis.md) |
| 248 | `5e03d06d2` | 2023-12-10 11:01:05 +0100 | dependabot[bot] | Build: Bump actions/setup-python from 4 to 5 (#9266) | ✅ 已完成 | [0248_5e03d06d2](commits/0248_5e03d06d2/analysis.md) |
| 249 | `1b80537e8` | 2023-12-10 11:01:19 +0100 | dependabot[bot] | Build: Bump actions/labeler from 4 to 5 (#9264) | ✅ 已完成 | [0249_1b80537e8](commits/0249_1b80537e8/analysis.md) |
| 250 | `ec92fa33f` | 2023-12-10 11:01:33 +0100 | dependabot[bot] | Build: Bump actions/stale from 8.0.0 to 9.0.0 (#9265) | ✅ 已完成 | [0250_ec92fa33f](commits/0250_ec92fa33f/analysis.md) |
| 251 | `06894dbc5` | 2023-12-10 11:04:13 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.4.12 to 9.5.1 (#9256) | ✅ 已完成 | [0251_06894dbc5](commits/0251_06894dbc5/analysis.md) |
| 252 | `d3deeecd8` | 2023-12-10 11:04:45 +0100 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.14.3 to 3.14.4 (#9257) | ✅ 已完成 | [0252_d3deeecd8](commits/0252_d3deeecd8/analysis.md) |
| 253 | `ce9186f6b` | 2023-12-10 11:05:03 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.29 to 2.21.42 (#9259) | ✅ 已完成 | [0253_ce9186f6b](commits/0253_ce9186f6b/analysis.md) |
| 254 | `0331aba1a` | 2023-12-10 11:05:20 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.27.0 to 26.28.0 (#9258) | ✅ 已完成 | [0254_0331aba1a](commits/0254_0331aba1a/analysis.md) |
| 255 | `7d06af33f` | 2023-12-10 13:52:07 -0800 | Eduard Tudenhoefner | Core: Improve view/table detection when replacing a table/view (#9012) | ✅ 已完成 | [0255_7d06af33f](commits/0255_7d06af33f/analysis.md) |
| 256 | `4090a8860` | 2023-12-10 13:57:32 -0800 | Eduard Tudenhoefner | Core: Add REST catalog table session cache (#8920) | ✅ 已完成 | [0256_4090a8860](commits/0256_4090a8860/analysis.md) |
| 257 | `61cf766c4` | 2023-12-11 08:49:27 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.24.2 to 0.25.1 (#9199) | ✅ 已完成 | [0257_61cf766c4](commits/0257_61cf766c4/analysis.md) |
| 258 | `1b9530509` | 2023-12-11 08:50:04 +0100 | Ajantha Bhat | Revert "Build: Bump actions/labeler from 4 to 5 (#9264)" (#9271) | ✅ 已完成 | [0258_1b9530509](commits/0258_1b9530509/analysis.md) |
| 259 | `492018902` | 2023-12-11 09:13:52 +0100 | Fokko Driesprong | Open-API: Refactor updates with discriminator (#9240) | ✅ 已完成 | [0259_492018902](commits/0259_492018902/analysis.md) |
| 260 | `f21199d00` | 2023-12-11 09:50:47 +0100 | L S Chetan Rao | MR: Migrate tests to JUnit5 (#9241) | ✅ 已完成 | [0260_f21199d00](commits/0260_f21199d00/analysis.md) |
| 261 | `b309d9bab` | 2023-12-12 09:21:52 +0100 | Ajantha Bhat | Nessie: Support views for NessieCatalog (#8909) | ✅ 已完成 | [0261_b309d9bab](commits/0261_b309d9bab/analysis.md) |
| 262 | `0c5b87a27` | 2023-12-12 10:17:08 +0100 | Anton Okolnychyi | Data: Add GenericFileWriterFactory (#9267) | ✅ 已完成 | [0262_0c5b87a27](commits/0262_0c5b87a27/analysis.md) |
| 263 | `09b44bb66` | 2023-12-12 15:09:14 +0100 | Naveen Kumar | Hive: Introduce HiveMetastoreExtension for Hive tests (#9282) | ✅ 已完成 | [0263_09b44bb66](commits/0263_09b44bb66/analysis.md) |
| 264 | `36ecab460` | 2023-12-12 10:07:46 -0800 | Ryan Blue | Core: Add StandardEncryptionManager (#9277) | ✅ 已完成 | [0264_36ecab460](commits/0264_36ecab460/analysis.md) |
| 265 | `d631e2c38` | 2023-12-13 08:49:15 +0100 | Anton Okolnychyi | Core, Spark: Avoid manifest copies when importing data to V2 tables (#8962) | ✅ 已完成 | [0265_d631e2c38](commits/0265_d631e2c38/analysis.md) |
| 266 | `3112ec916` | 2023-12-13 08:59:25 +0100 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9260) | ✅ 已完成 | [0266_3112ec916](commits/0266_3112ec916/analysis.md) |
| 267 | `60876e4a9` | 2023-12-13 09:41:16 +0100 | Eduard Tudenhoefner | Hive: Make HiveMetastoreExtension configurable (#9288) | ✅ 已完成 | [0267_60876e4a9](commits/0267_60876e4a9/analysis.md) |
| 268 | `7240752e1` | 2023-12-13 09:48:29 +0100 | Pucheng Yang | Docs: Add spec-id for rewrite manifests (#9253) | ✅ 已完成 | [0268_7240752e1](commits/0268_7240752e1/analysis.md) |
| 269 | `11608e102` | 2023-12-13 10:42:24 -0800 | ismail simsek | JDBC Catalog: Fix namespaceExists check with special characters (#8340) | ✅ 已完成 | [0269_11608e102](commits/0269_11608e102/analysis.md) |
| 270 | `46df2ce06` | 2023-12-14 09:17:36 +0100 | Daniel Weeks | API: Restore RuntimeIOException for use (#5640) | ✅ 已完成 | [0270_46df2ce06](commits/0270_46df2ce06/analysis.md) |
| 271 | `c6bbbdbc1` | 2023-12-14 10:56:53 +0100 | Ajantha Bhat | Spark: Remove support for Spark 3.2 (#9295) | ✅ 已完成 | [0271_c6bbbdbc1](commits/0271_c6bbbdbc1/analysis.md) |
| 272 | `5e62e478d` | 2023-12-14 11:32:02 +0100 | Manu Zhang | Spark: Fix flaky tests which concurrently modify HashSet (#9294) | ✅ 已完成 | [0272_5e62e478d](commits/0272_5e62e478d/analysis.md) |
| 273 | `7a421206d` | 2023-12-14 17:02:42 +0100 | Ron Korving | Docs: Update readme status paragraph (#9272) | ✅ 已完成 | [0273_7a421206d](commits/0273_7a421206d/analysis.md) |
| 274 | `09290c58c` | 2023-12-14 17:11:19 +0100 | Ajantha Bhat | Core: Remove deprecated classes related to rewrite data files (#9296) | ✅ 已完成 | [0274_09290c58c](commits/0274_09290c58c/analysis.md) |
| 275 | `5487c1747` | 2023-12-14 17:22:04 +0100 | Naveen Kumar | Hive: Refactor TestHiveCatalog tests to use CatalogTests (#8918) | ✅ 已完成 | [0275_5487c1747](commits/0275_5487c1747/analysis.md) |
| 276 | `8572c56e8` | 2023-12-14 17:38:13 +0100 | Eduard Tudenhoefner | API, Core: Move SQLViewRepresentation to API (#9302) | ✅ 已完成 | [0276_8572c56e8](commits/0276_8572c56e8/analysis.md) |
| 277 | `d56dd63f8` | 2023-12-14 15:32:21 -0800 | Rodrigo | Doc: Adding documentation for flink iceberg connector for version 1.18 (#9304) | ✅ 已完成 | [0277_d56dd63f8](commits/0277_d56dd63f8/analysis.md) |
| 278 | `8181f84c9` | 2023-12-15 12:58:35 -0800 | Amogh Jahagirdar | API, Core: Add sqlFor API to views to handle resolving a representation for a dialect(#9247) | ✅ 已完成 | [0278_8181f84c9](commits/0278_8181f84c9/analysis.md) |
| 279 | `d6a4ca7a3` | 2023-12-16 09:15:35 +0100 | Anton Okolnychyi | API: Fix equals and hashCode in CharSequenceSet (#9245) | ✅ 已完成 | [0279_d6a4ca7a3](commits/0279_d6a4ca7a3/analysis.md) |
| 280 | `395e01e65` | 2023-12-16 10:03:53 -0800 | Amogh Jahagirdar | Core: Make sqlFor case insensitive for dialect check (#9311) | ✅ 已完成 | [0280_395e01e65](commits/0280_395e01e65/analysis.md) |
| 281 | `24578a28f` | 2023-12-16 10:48:55 -0800 | Ajantha Bhat | Core: Fix metadata table uuid to return a consistent UUID for the same reference (#9310) | ✅ 已完成 | [0281_24578a28f](commits/0281_24578a28f/analysis.md) |
| 282 | `9342f64a7` | 2023-12-18 09:13:21 +0100 | pvary | Flink: Fix TestIcebergSourceWithWatermarkExtractor flakiness (#9309) | ✅ 已完成 | [0282_9342f64a7](commits/0282_9342f64a7/analysis.md) |
| 283 | `ad3cf9d81` | 2023-12-18 20:49:47 +0100 | Anton Okolnychyi | Core: Look up targeted position deletes by path (#9251) | ✅ 已完成 | [0283_ad3cf9d81](commits/0283_ad3cf9d81/analysis.md) |
| 284 | `6e21bbf4c` | 2023-12-18 21:05:01 +0100 | Ajantha Bhat | API, Core: Track partition statistics in TableMetadata (#8502) | ✅ 已完成 | [0284_6e21bbf4c](commits/0284_6e21bbf4c/analysis.md) |
| 285 | `d6eba2a2b` | 2023-12-18 14:19:26 -0800 | Jason | Core: Fix missing files from transaction retries with conflicting manifest merges (#9230) | ✅ 已完成 | [0285_d6eba2a2b](commits/0285_d6eba2a2b/analysis.md) |
| 286 | `b1bf4168c` | 2023-12-19 07:19:03 +0100 | Mason Chen | Flink: port #9173 to v1.16 and v1.18 (#9334) | ✅ 已完成 | [0286_b1bf4168c](commits/0286_b1bf4168c/analysis.md) |
| 287 | `97666096e` | 2023-12-19 08:21:24 +0100 | Wing Yew Poon | Spark: Add tests for SELECT using tag/branch prefix identifier (#9286) | ✅ 已完成 | [0287_97666096e](commits/0287_97666096e/analysis.md) |
| 288 | `5fce7ecaa` | 2023-12-19 08:58:12 +0100 | gabry.wu | Core: Shutdown scheduler in Lock manager (#9150) | ✅ 已完成 | [0288_5fce7ecaa](commits/0288_5fce7ecaa/analysis.md) |
| 289 | `892e47cd3` | 2023-12-19 11:18:57 +0100 | Gianluca Principini | API: Support parameterized tests at class-level with JUnit5 (#9161) | ✅ 已完成 | [0289_892e47cd3](commits/0289_892e47cd3/analysis.md) |
| 290 | `838787e29` | 2023-12-19 12:53:04 +0100 | dependabot[bot] | Build: Bump nessie from 0.74.0 to 0.75.0 (#9313) | ✅ 已完成 | [0290_838787e29](commits/0290_838787e29/analysis.md) |
| 291 | `c2018f894` | 2023-12-19 07:13:58 -0800 | Reetika | Forward properties in HadoopCatalog initialization to the default HadoopFileIO (#9283) | ✅ 已完成 | [0291_c2018f894](commits/0291_c2018f894/analysis.md) |
| 292 | `980733c2e` | 2023-12-19 18:05:17 +0100 | Eduard Tudenhoefner | Spark 3.5: Remove UnresolvedIcebergTable (#9338) | ✅ 已完成 | [0292_980733c2e](commits/0292_980733c2e/analysis.md) |
| 293 | `a83bfe72c` | 2023-12-19 12:59:32 -0800 | ggershinsky | Spec: Clarify file length handling for AES GCM streams (#9136) | ✅ 已完成 | [0293_a83bfe72c](commits/0293_a83bfe72c/analysis.md) |
| 294 | `c340915b0` | 2023-12-21 08:27:39 +0100 | Fokko Driesprong | Core: Fix missing delete files from transaction (#9354) | ✅ 已完成 | [0294_c340915b0](commits/0294_c340915b0/analysis.md) |
| 295 | `2eea69729` | 2023-12-21 08:32:33 +0100 | pvary | Flink: Empty implementation for pauseOrResumeSplits to prevent UnsupportedOperationException (#9308) | ✅ 已完成 | [0295_2eea69729](commits/0295_2eea69729/analysis.md) |
| 296 | `a654bf920` | 2023-12-21 08:07:37 -0800 | Sung Yun | Add Description on Using a Separate Authorization Server (#8998) | ✅ 已完成 | [0296_a654bf920](commits/0296_a654bf920/analysis.md) |
| 297 | `bfa8006e5` | 2023-12-21 13:01:35 -0800 | Tom Tanaka | Docs: Fix incorrect set_current_snapshot procedure argument (#9360) | ✅ 已完成 | [0297_bfa8006e5](commits/0297_bfa8006e5/analysis.md) |
| 298 | `3d53060e2` | 2023-12-22 08:37:04 +0100 | Chinmay Bhat | Spark 3.5: Migrate tests to JUnit5 in data directory (#9341) | ✅ 已完成 | [0298_3d53060e2](commits/0298_3d53060e2/analysis.md) |
| 299 | `a077a2be3` | 2023-12-22 14:35:59 +0100 | panbingkun | Spark: Fix AddFilesProcedure error message when no partitions are found (#9357) | ✅ 已完成 | [0299_a077a2be3](commits/0299_a077a2be3/analysis.md) |
| 300 | `58d3ad39d` | 2023-12-22 17:08:40 +0100 | Chinmay Bhat | Spark 3:5 Migrate tests to JUnit5 in source directory (#9342) | ✅ 已完成 | [0300_58d3ad39d](commits/0300_58d3ad39d/analysis.md) |
| 301 | `4f6c5fc64` | 2023-12-22 14:02:39 -0800 | Ryan Blue | Core: Add ApplyNameMapping for Avro (#9347) | ✅ 已完成 | [0301_4f6c5fc64](commits/0301_4f6c5fc64/analysis.md) |
| 302 | `19b24b3f6` | 2023-12-22 15:28:15 -0800 | Walaa Eldin Moustafa | Avro: Add Avro-assisted name mapping (#7392) | ✅ 已完成 | [0302_19b24b3f6](commits/0302_19b24b3f6/analysis.md) |
| 303 | `23fc3a36e` | 2023-12-24 06:43:22 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.1 to 0.25.2 (#9377) | ✅ 已完成 | [0303_23fc3a36e](commits/0303_23fc3a36e/analysis.md) |
| 304 | `1fb47316f` | 2023-12-24 09:17:26 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.1 to 9.5.3 (#9376) | ✅ 已完成 | [0304_1fb47316f](commits/0304_1fb47316f/analysis.md) |
| 305 | `c83308c11` | 2023-12-24 11:08:08 +0100 | dependabot[bot] | Build: Bump guava from 32.1.3-jre to 33.0.0-jre (#9373) | ✅ 已完成 | [0305_c83308c11](commits/0305_c83308c11/analysis.md) |
| 306 | `c8fc591cb` | 2023-12-24 11:08:58 +0100 | dependabot[bot] | Build: Bump arrow from 14.0.1 to 14.0.2 (#9372) | ✅ 已完成 | [0306_c8fc591cb](commits/0306_c8fc591cb/analysis.md) |
| 307 | `226a23f51` | 2023-12-24 15:09:18 +0100 | Eduard Tudenhoefner | Spark 3.5: Remove constructor from parameterized base class (#9368) | ✅ 已完成 | [0307_226a23f51](commits/0307_226a23f51/analysis.md) |
| 308 | `6f4e33ec2` | 2023-12-24 19:56:00 +0100 | Anton Okolnychyi | Core: Use CharSequenceMap for writing unordered deletes (#9365) | ✅ 已完成 | [0308_6f4e33ec2](commits/0308_6f4e33ec2/analysis.md) |
| 309 | `197b61e75` | 2023-12-25 12:52:48 +0100 | Irshad CC | Core: Optimize manifest evaluation for super wide tables (#9147) | ✅ 已完成 | [0309_197b61e75](commits/0309_197b61e75/analysis.md) |
| 310 | `cbb50bfa5` | 2023-12-25 08:26:52 -0800 | Amogh Jahagirdar | Core: Remove unused sourceTransform in private method (#9379) | ✅ 已完成 | [0310_cbb50bfa5](commits/0310_cbb50bfa5/analysis.md) |
| 311 | `6c344dbbb` | 2023-12-26 11:46:47 +0100 | vinitpatni | Flink: Create CatalogTestBase for migration to JUnit5 (#9364) | ✅ 已完成 | [0311_6c344dbbb](commits/0311_6c344dbbb/analysis.md) |
| 312 | `22d4e7836` | 2023-12-28 07:52:21 -0800 | Manu Zhang | Spark 3.5: Parallelize reading files in add_files procedure (#9274) | ✅ 已完成 | [0312_22d4e7836](commits/0312_22d4e7836/analysis.md) |
| 313 | `a8f468d6d` | 2023-12-29 10:52:25 +0100 | Chinmay Bhat | Spark 3.5: Migrate tests to JUnit5 in actions directory (#9367) | ✅ 已完成 | [0313_a8f468d6d](commits/0313_a8f468d6d/analysis.md) |
| 314 | `604422b05` | 2024-01-01 11:03:41 -0800 | Ryan Blue | Core: Refactor internal Avro reader to resolve schemas directly (#9366) | ✅ 已完成 | [0314_604422b05](commits/0314_604422b05/analysis.md) |
| 315 | `7ebb24123` | 2024-01-02 10:24:32 -0600 | Xianyang Liu | Core, Spark: Correct the delete record count for PartitionTable (#9389) | ✅ 已完成 | [0315_7ebb24123](commits/0315_7ebb24123/analysis.md) |
| 316 | `580e7021a` | 2024-01-02 08:41:22 -0800 | Amogh Jahagirdar | Spark 3.5: Fix clobbering of files across streaming epochs when query ID is reused (#9255) | ✅ 已完成 | [0316_580e7021a](commits/0316_580e7021a/analysis.md) |
| 317 | `ad423142c` | 2024-01-02 12:52:59 -0800 | Amogh Jahagirdar | Spark 3.3, 3.4: Fix file clobbering when Spark reuses query IDs (#9255) (#9399) | ✅ 已完成 | [0317_ad423142c](commits/0317_ad423142c/analysis.md) |
| 318 | `e7999a194` | 2024-01-02 22:26:59 +0100 | Anton Okolnychyi | Core, Data, Spark 3.5: Support file and partition delete granularity (#9384) | ✅ 已完成 | [0318_e7999a194](commits/0318_e7999a194/analysis.md) |
| 319 | `38eb1b18b` | 2024-01-03 09:11:46 +0100 | Robert Stupp | Build: Bump Nessie to 0.76.0 (#9398) | ✅ 已完成 | [0319_38eb1b18b](commits/0319_38eb1b18b/analysis.md) |
| 320 | `8c7001eb4` | 2024-01-03 17:53:34 +0100 | pvary | Flink: Backport #9308 to v1.17 and the relevant parts to v1.16 (#9403) | ✅ 已完成 | [0320_8c7001eb4](commits/0320_8c7001eb4/analysis.md) |
| 321 | `c19318e68` | 2024-01-03 15:16:34 -0800 | Amogh Jahagirdar | API: Fix Javadoc on UpdateSchema#updateColumnDoc (#9405) | ✅ 已完成 | [0321_c19318e68](commits/0321_c19318e68/analysis.md) |
| 322 | `3aa0fcd7c` | 2024-01-03 18:12:08 -0800 | Hongyue/Steve Zhang | Core: Remove statistics files in CatalogUtil:dropTableData (#9305) | ✅ 已完成 | [0322_3aa0fcd7c](commits/0322_3aa0fcd7c/analysis.md) |
| 323 | `be4e7d208` | 2024-01-03 18:54:17 -0800 | Fokko Driesprong | JMH: Improvements to `jmh.gradle` (#9390) | ✅ 已完成 | [0323_be4e7d208](commits/0323_be4e7d208/analysis.md) |
| 324 | `27e8c4213` | 2024-01-04 08:40:23 +0100 | Manu Zhang | Docs: CREATE TABLE LIKE is not supported in Spark DDL (#9358) | ✅ 已完成 | [0324_27e8c4213](commits/0324_27e8c4213/analysis.md) |
| 325 | `4ccc29bc2` | 2024-01-04 13:46:44 +0100 | panbingkun | Build: Bump actions/labeler from 4 to 5 (#9331) | ✅ 已完成 | [0325_4ccc29bc2](commits/0325_4ccc29bc2/analysis.md) |
| 326 | `6d75e7a76` | 2024-01-04 15:27:26 +0100 | Manu Zhang | Flink: Disable classloader check in TestIcebergSourceWithWatermarkExtractor to fix flakiness (#9408) | ✅ 已完成 | [0326_6d75e7a76](commits/0326_6d75e7a76/analysis.md) |
| 327 | `1a9c3f78f` | 2024-01-04 08:38:18 -0800 | Eduard Tudenhoefner | Spark: Add support for reading Iceberg views (#9340) | ✅ 已完成 | [0327_1a9c3f78f](commits/0327_1a9c3f78f/analysis.md) |
| 328 | `1288eb8e3` | 2024-01-04 08:55:11 -0800 | ggershinsky | Core, Spark, Flink, Data: Deliver key metadata for encryption of data files (#9359) | ✅ 已完成 | [0328_1288eb8e3](commits/0328_1288eb8e3/analysis.md) |
| 329 | `996cd5b53` | 2024-01-04 11:38:38 -0800 | Adnan Hemani | AWS: Add S3 Access Grants Integration (#9385) | ✅ 已完成 | [0329_996cd5b53](commits/0329_996cd5b53/analysis.md) |
| 330 | `b7e3e21bb` | 2024-01-04 13:27:57 -0800 | Ajantha Bhat | Core: Remove partition statistics files during purge table (#9409) | ✅ 已完成 | [0330_b7e3e21bb](commits/0330_b7e3e21bb/analysis.md) |
| 331 | `12b3ccdd0` | 2024-01-05 12:59:07 +0100 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#9369) | ✅ 已完成 | [0331_12b3ccdd0](commits/0331_12b3ccdd0/analysis.md) |
| 332 | `0bf2602a7` | 2024-01-05 13:04:01 +0100 | CG | Flink 1.17: Create JUnit5 version of TestFlinkScan (#9185) | ✅ 已完成 | [0332_0bf2602a7](commits/0332_0bf2602a7/analysis.md) |
| 333 | `c416c2989` | 2024-01-05 13:12:25 +0100 | Ajantha Bhat | Nessie: Strip trailing slash for warehouse location (#9415) | ✅ 已完成 | [0333_c416c2989](commits/0333_c416c2989/analysis.md) |
| 334 | `460282433` | 2024-01-05 17:18:20 +0100 | Chinmay Bhat | Spark 3.5: Migrate tests to JUnit5  (#9417) | ✅ 已完成 | [0334_460282433](commits/0334_460282433/analysis.md) |
| 335 | `2101ac2e5` | 2024-01-05 08:21:31 -0800 | Eduard Tudenhoefner | Spark 3.4: Add support for reading Iceberg views (#9422) | ✅ 已完成 | [0335_2101ac2e5](commits/0335_2101ac2e5/analysis.md) |
| 336 | `12f6d0d0a` | 2024-01-08 09:51:52 +0100 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.24.2 to 3.25.1 (#9427) | ✅ 已完成 | [0336_12f6d0d0a](commits/0336_12f6d0d0a/analysis.md) |
| 337 | `4a9967836` | 2024-01-08 11:08:07 +0100 | Manu Zhang | Parquet: Support reading INT96 column in row group filter (#8988) | ✅ 已完成 | [0337_4a9967836](commits/0337_4a9967836/analysis.md) |
| 338 | `c87a53aab` | 2024-01-08 04:19:08 -0800 | dependabot[bot] | Build: Bump mkdocs-monorepo-plugin from 1.0.5 to 1.1.0 (#9430) | ✅ 已完成 | [0338_c87a53aab](commits/0338_c87a53aab/analysis.md) |
| 339 | `be155d70c` | 2024-01-08 04:19:53 -0800 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#9429) | ✅ 已完成 | [0339_be155d70c](commits/0339_be155d70c/analysis.md) |
| 340 | `13e1965af` | 2024-01-08 11:40:29 -0800 | Fokko Driesprong | Parquet: Move to ValueReader generation to a visitor (#9063) | ✅ 已完成 | [0340_13e1965af](commits/0340_13e1965af/analysis.md) |
| 341 | `e16bfcffc` | 2024-01-08 21:14:06 +0100 | Eduard Tudenhoefner | Core: Add JUnit5 version of TableTestBase (#9424) | ✅ 已完成 | [0341_e16bfcffc](commits/0341_e16bfcffc/analysis.md) |
| 342 | `3e47855c2` | 2024-01-08 16:39:21 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.21.42 to 2.22.12 (#9426) | ✅ 已完成 | [0342_3e47855c2](commits/0342_3e47855c2/analysis.md) |
| 343 | `bc6b6e65e` | 2024-01-08 16:44:08 -0800 | dependabot[bot] | Build: Bump com.fasterxml.jackson.dataformat:jackson-dataformat-xml (#9395) | ✅ 已完成 | [0343_bc6b6e65e](commits/0343_bc6b6e65e/analysis.md) |
| 344 | `d9498a00d` | 2024-01-09 07:04:59 -0800 | Brian Olsen | Shift site build to use monorepo and gh-pages | ✅ 已完成 | [0344_d9498a00d](commits/0344_d9498a00d/analysis.md) |
| 345 | `9bd5deccd` | 2024-01-09 07:04:59 -0800 | Brian Olsen | Established structured folders to customize MkDocs Material to an Iceberg look and feel | ✅ 已完成 | [0345_9bd5deccd](commits/0345_9bd5deccd/analysis.md) |
| 346 | `4d34398cf` | 2024-01-09 18:22:29 +0100 | Rodrigo | Flink: Watermark read options (#9346) | ✅ 已完成 | [0346_4d34398cf](commits/0346_4d34398cf/analysis.md) |
| 347 | `c6a772618` | 2024-01-10 06:05:03 -0800 | Ajantha Bhat | API, Core: Fix errorprone warnings (#9419) | ✅ 已完成 | [0347_c6a772618](commits/0347_c6a772618/analysis.md) |
| 348 | `d1a3c1045` | 2024-01-10 06:07:38 -0800 | Bryan Keller | Kafka Connect: Initial project setup and event data structures (#8701) | ✅ 已完成 | [0348_d1a3c1045](commits/0348_d1a3c1045/analysis.md) |
| 349 | `53a1c8671` | 2024-01-10 17:05:08 +0100 | Chinmay Bhat | Spark 3.5: Migrate tests in SQL directory to JUnit5 (#9401) | ✅ 已完成 | [0349_53a1c8671](commits/0349_53a1c8671/analysis.md) |
| 350 | `211f5d550` | 2024-01-11 16:20:50 -0800 | ggershinsky | Spark 3.5: Support encrypted output files (#9435) | ✅ 已完成 | [0350_211f5d550](commits/0350_211f5d550/analysis.md) |
| 351 | `8109e420e` | 2024-01-13 07:15:55 +0100 | Rodrigo | Backporting Flink: Watermark Read Options to 1.17 and 1.16 (#9456) | ✅ 已完成 | [0351_8109e420e](commits/0351_8109e420e/analysis.md) |
| 352 | `850cd5c3b` | 2024-01-13 15:04:45 +0100 | vinitpatni | Flink: Migrate subclasses of FlinkCatalogTestBase to JUnit5 (#9381) | ✅ 已完成 | [0352_850cd5c3b](commits/0352_850cd5c3b/analysis.md) |
| 353 | `a3d87e236` | 2024-01-13 15:11:10 +0100 | Chinmay Bhat | Spark 3.5: Migrate remaining tests in source directory to JUnit5 (#9380) | ✅ 已完成 | [0353_a3d87e236](commits/0353_a3d87e236/analysis.md) |
| 354 | `e76988b1a` | 2024-01-14 13:29:59 -0800 | ggershinsky | Core: Minor updates to AES GCM streams (#9453) | ✅ 已完成 | [0354_e76988b1a](commits/0354_e76988b1a/analysis.md) |
| 355 | `16ac3edd2` | 2024-01-15 09:14:10 +0100 | dependabot[bot] | Build: Bump actions/checkout from 3 to 4 (#9474) | ✅ 已完成 | [0355_16ac3edd2](commits/0355_16ac3edd2/analysis.md) |
| 356 | `4b6f5b7b8` | 2024-01-15 09:14:58 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.22.12 to 2.23.2 (#9471) | ✅ 已完成 | [0356_4b6f5b7b8](commits/0356_4b6f5b7b8/analysis.md) |
| 357 | `5a1b0d180` | 2024-01-15 09:15:17 +0100 | dependabot[bot] | Build: Bump nessie from 0.76.0 to 0.76.2 (#9467) | ✅ 已完成 | [0357_5a1b0d180](commits/0357_5a1b0d180/analysis.md) |
| 358 | `23e17ce66` | 2024-01-15 14:49:08 +0100 | Ajantha Bhat | Nessie: Add table() and view() API to NessieIcebergClient (#9477) | ✅ 已完成 | [0358_23e17ce66](commits/0358_23e17ce66/analysis.md) |
| 359 | `bc7e56c2e` | 2024-01-15 14:51:03 +0100 | Chinmay Bhat | Spark, Flink: Migrate DeleteReadTests and its subclasses to JUnit5 (#9382) | ✅ 已完成 | [0359_bc7e56c2e](commits/0359_bc7e56c2e/analysis.md) |
| 360 | `8018ab844` | 2024-01-16 08:51:59 +0100 | Ajantha Bhat | Nessie: Infer default API version from URI (#9459) | ✅ 已完成 | [0360_8018ab844](commits/0360_8018ab844/analysis.md) |
| 361 | `ea30d363e` | 2024-01-16 09:49:43 +0100 | Chinmay Bhat | Core, Spark: Migrate tests that depend on ScanTestBase to JUnit5 (#9416) | ✅ 已完成 | [0361_ea30d363e](commits/0361_ea30d363e/analysis.md) |
| 362 | `13e108b37` | 2024-01-16 09:52:22 +0100 | Robert Stupp | Build: Add `iceberg-bom` artifact (#8065) | ✅ 已完成 | [0362_13e108b37](commits/0362_13e108b37/analysis.md) |
| 363 | `a60ee5d68` | 2024-01-16 11:42:25 +0100 | Eduard Tudenhoefner | Spark: Support renaming views (#9343) | ✅ 已完成 | [0363_a60ee5d68](commits/0363_a60ee5d68/analysis.md) |
| 364 | `581e03713` | 2024-01-16 11:47:12 +0100 | Eduard Tudenhoefner | Flink 1.18: Create JUnit5 version of TestFlinkScan (#9480) | ✅ 已完成 | [0364_581e03713](commits/0364_581e03713/analysis.md) |
| 365 | `2cda2b9a4` | 2024-01-16 12:39:44 +0100 | Eduard Tudenhoefner | Spark: Support dropping Views (#9421) | ✅ 已完成 | [0365_2cda2b9a4](commits/0365_2cda2b9a4/analysis.md) |
| 366 | `8845bf49f` | 2024-01-16 12:54:49 +0100 | dependabot[bot] | Build: Bump actions/setup-python from 4 to 5 (#9473) | ✅ 已完成 | [0366_8845bf49f](commits/0366_8845bf49f/analysis.md) |
| 367 | `fac03ea3c` | 2024-01-16 17:35:07 +0100 | Eduard Tudenhoefner | Flink 1.16: Create JUnit5 version of TestFlinkScan (#9482) | ✅ 已完成 | [0367_fac03ea3c](commits/0367_fac03ea3c/analysis.md) |
| 368 | `7dd01a367` | 2024-01-16 18:02:05 +0100 | Ryan Blue | Parquet: Deprecate readSupport and callInit in ReadBuilder (#9325) | ✅ 已完成 | [0368_7dd01a367](commits/0368_7dd01a367/analysis.md) |
| 369 | `13d2160bd` | 2024-01-16 09:23:54 -0800 | pvary | Flink: Remove reading of the data files to fix flakiness (#9451) | ✅ 已完成 | [0369_13d2160bd](commits/0369_13d2160bd/analysis.md) |
| 370 | `bb50ab97d` | 2024-01-16 09:55:06 -0800 | ggershinsky | Core: Support Avro file encryption with AES GCM streams (#9436) | ✅ 已完成 | [0370_bb50ab97d](commits/0370_bb50ab97d/analysis.md) |
| 371 | `684f7a767` | 2024-01-16 10:21:26 -0800 | Anton Okolnychyi | Core, Spark 3.5: Read deletes in parallel and cache them on executors (#8755) | ✅ 已完成 | [0371_684f7a767](commits/0371_684f7a767/analysis.md) |
| 372 | `368415211` | 2024-01-16 10:26:22 -0800 | Manu Zhang | Docs: Enhance documentation on identifier fields (#9478) | ✅ 已完成 | [0372_368415211](commits/0372_368415211/analysis.md) |
| 373 | `31d18f51b` | 2024-01-16 12:51:50 -0800 | Rodrigo | Flink: Upgrade Flink version from 1.18 to 1.18.1 (#9486) | ✅ 已完成 | [0373_31d18f51b](commits/0373_31d18f51b/analysis.md) |
| 374 | `1da80552c` | 2024-01-17 11:14:21 +0100 | Naveen Kumar | Hive: Unwrap RuntimeException for Hive TException with alter table (#9432) | ✅ 已完成 | [0374_1da80552c](commits/0374_1da80552c/analysis.md) |
| 375 | `5fce05e8c` | 2024-01-17 11:22:21 +0100 | oneonestar | Update iceberg_bug_report.yml to 1.4.3 (#9491) | ✅ 已完成 | [0375_5fce05e8c](commits/0375_5fce05e8c/analysis.md) |
| 376 | `b3273276f` | 2024-01-17 11:37:08 +0100 | Manu Zhang | Infra: Check stale issues in ascending order (#9489) | ✅ 已完成 | [0376_b3273276f](commits/0376_b3273276f/analysis.md) |
| 377 | `99958d96e` | 2024-01-17 13:16:34 +0100 | Ajantha Bhat | Build: Bump minor version for Spark-3.3 (#9492) | ✅ 已完成 | [0377_99958d96e](commits/0377_99958d96e/analysis.md) |
| 378 | `fe004c5bf` | 2024-01-17 14:50:02 +0100 | pvary | Docs: Fix typo in tag reading example (#9496) | ✅ 已完成 | [0378_fe004c5bf](commits/0378_fe004c5bf/analysis.md) |
| 379 | `66b1aa662` | 2024-01-17 14:52:46 +0100 | Fokko Driesprong | Set `ghp_path` to `/` (#9493) | ✅ 已完成 | [0379_66b1aa662](commits/0379_66b1aa662/analysis.md) |
| 380 | `d4056530d` | 2024-01-17 13:02:47 -0800 | N-o-Z | Core: Fix lock acquisition logic in HadoopTableOperations rename (#9498) | ✅ 已完成 | [0380_d4056530d](commits/0380_d4056530d/analysis.md) |
| 381 | `2eafdb5bf` | 2024-01-17 15:47:42 -0800 | Brian "bits" Olsen | Docs: Fix community link (#9500) | ✅ 已完成 | [0381_2eafdb5bf](commits/0381_2eafdb5bf/analysis.md) |
| 382 | `2446cee5c` | 2024-01-18 08:31:40 +0100 | big face cat | Core: Close the MetricsReporter when Catalog is closed (#9353) | ✅ 已完成 | [0382_2446cee5c](commits/0382_2446cee5c/analysis.md) |
| 383 | `97a9a082c` | 2024-01-18 12:25:34 +0100 | JB Onofré | Build: Upgrade to Apache RAT 0.16, scanning hidden directories and adding missing ASF header (#9495) | ✅ 已完成 | [0383_97a9a082c](commits/0383_97a9a082c/analysis.md) |
| 384 | `5963b0a5c` | 2024-01-18 15:06:19 +0100 | Eduard Tudenhoefner | Spark 3.4: Support dropping views (#9508) | ✅ 已完成 | [0384_5963b0a5c](commits/0384_5963b0a5c/analysis.md) |
| 385 | `6e7702dab` | 2024-01-18 08:10:49 -0800 | Ajantha Bhat | Core: Remove deprecated operations method from BaseMetadataTable (#9298) | ✅ 已完成 | [0385_6e7702dab](commits/0385_6e7702dab/analysis.md) |
| 386 | `057f88771` | 2024-01-18 17:40:34 +0100 | Manu Zhang | Docs, Spark: Distribution mode not respected for CTAS/RTAS before 3.5.0 (#9439) | ✅ 已完成 | [0386_057f88771](commits/0386_057f88771/analysis.md) |
| 387 | `b6cefe5e1` | 2024-01-18 10:49:05 -0800 | Eduard Tudenhoefner | Build: Define strict version for Flink / Jackson / Hive2 / Tez 0.8 (#9484) | ✅ 已完成 | [0387_b6cefe5e1](commits/0387_b6cefe5e1/analysis.md) |
| 388 | `02836eaac` | 2024-01-18 11:10:53 -0800 | Ajantha Bhat | Spark: Ensure partition stats files are considered for GC procedures (#9284) | ✅ 已完成 | [0388_02836eaac](commits/0388_02836eaac/analysis.md) |
| 389 | `008d1731c` | 2024-01-18 11:35:48 -0800 | advancedxy | Spark 3.5: Propagate snapshot properties in compaction (#9449) | ✅ 已完成 | [0389_008d1731c](commits/0389_008d1731c/analysis.md) |
| 390 | `1e3b38eb7` | 2024-01-19 08:02:01 +0100 | Ajantha Bhat | Spark: backport #8656 and update docs (#9512) | ✅ 已完成 | [0390_1e3b38eb7](commits/0390_1e3b38eb7/analysis.md) |
| 391 | `781401237` | 2024-01-19 08:03:02 +0100 | Ajantha Bhat | Update doap.rdf (#9507) | ✅ 已完成 | [0391_781401237](commits/0391_781401237/analysis.md) |
| 392 | `fbea70780` | 2024-01-19 08:07:07 +0100 | Manu Zhang | Spark: Fix flaky TestSparkReaderDeletes tests due to metric not found (#9445) | ✅ 已完成 | [0392_fbea70780](commits/0392_fbea70780/analysis.md) |
| 393 | `cd0f30e53` | 2024-01-19 11:05:05 +0100 | Brian "bits" Olsen | Update deploy script and add 1.4.3 updates (#9519) | ✅ 已完成 | [0393_cd0f30e53](commits/0393_cd0f30e53/analysis.md) |
| 394 | `e9f26b13f` | 2024-01-19 12:09:27 +0100 | dependabot[bot] | Build: Bump actions/upload-artifact from 3 to 4 (#9319) | ✅ 已完成 | [0394_e9f26b13f](commits/0394_e9f26b13f/analysis.md) |
| 395 | `b1db17dcf` | 2024-01-19 09:34:01 -0800 | Eduard Tudenhoefner | Core: Mark NoSuchViewException as CleanableFailure (#9516) | ✅ 已完成 | [0395_b1db17dcf](commits/0395_b1db17dcf/analysis.md) |
| 396 | `e32df0ce0` | 2024-01-19 22:14:26 +0100 | Brian "bits" Olsen | Init git credentials in site-ci (#9525) | ✅ 已完成 | [0396_e32df0ce0](commits/0396_e32df0ce0/analysis.md) |
| 397 | `77ee577a8` | 2024-01-21 18:25:43 -0800 | Wang Tao | Docs: Correct spelling of gauge (#9543) | ✅ 已完成 | [0397_77ee577a8](commits/0397_77ee577a8/analysis.md) |
| 398 | `d74034734` | 2024-01-22 08:32:40 +0100 | dependabot[bot] | Build: Bump nessie from 0.76.2 to 0.76.3 (#9537) | ✅ 已完成 | [0398_d74034734](commits/0398_d74034734/analysis.md) |
| 399 | `a84792148` | 2024-01-22 08:35:34 +0100 | Manu Zhang | Infra: Increase operations-per-run in stale action to 100 (#9529) | ✅ 已完成 | [0399_a84792148](commits/0399_a84792148/analysis.md) |
| 400 | `3466ae02e` | 2024-01-22 08:40:03 +0100 | Amogh Jahagirdar | Core: Cleanup assertion messages in partition spec tests (#9528) | ✅ 已完成 | [0400_3466ae02e](commits/0400_3466ae02e/analysis.md) |
| 401 | `556b79893` | 2024-01-22 08:41:22 +0100 | Ajantha Bhat | Arrow, AWS, Core: Remove deprecated code for 1.5.0 release (#9505) | ✅ 已完成 | [0401_556b79893](commits/0401_556b79893/analysis.md) |
| 402 | `26efa7a3e` | 2024-01-22 09:21:57 -0800 | Eduard Tudenhoefner | Revert "Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9260)" (#9544) | ✅ 已完成 | [0402_26efa7a3e](commits/0402_26efa7a3e/analysis.md) |
| 403 | `0f509d2d6` | 2024-01-22 10:00:21 -0800 | Ryan Blue | Parquet: Add system config for unsafe Parquet ID fallback. (#9324) | ✅ 已完成 | [0403_0f509d2d6](commits/0403_0f509d2d6/analysis.md) |
| 404 | `70b7aa534` | 2024-01-22 14:56:43 -0800 | Amogh Jahagirdar | API, Core, Spark: Change behavior of fastForward/replace to create the from branch if it does not exist (#9196) | ✅ 已完成 | [0404_70b7aa534](commits/0404_70b7aa534/analysis.md) |
| 405 | `18a9ca762` | 2024-01-23 07:58:26 +0100 | Ajantha Bhat | Build: Fix errorprone warning (#9531) | ✅ 已完成 | [0405_18a9ca762](commits/0405_18a9ca762/analysis.md) |
| 406 | `20ff1ab33` | 2024-01-23 07:58:53 +0100 | dependabot[bot] | Build: Bump actions/cache from 3 to 4 (#9532) | ✅ 已完成 | [0406_20ff1ab33](commits/0406_20ff1ab33/analysis.md) |
| 407 | `3d66e9dd5` | 2024-01-24 08:20:15 +0100 | Manu Zhang | Spark 3.4, 3.5: Enable drop table with purge in tests (#9548) | ✅ 已完成 | [0407_3d66e9dd5](commits/0407_3d66e9dd5/analysis.md) |
| 408 | `fd1cf4928` | 2024-01-24 08:23:38 +0100 | Fokko Driesprong | Build: Don't run CI's on unrelated changes (#9526) | ✅ 已完成 | [0408_fd1cf4928](commits/0408_fd1cf4928/analysis.md) |
| 409 | `200b9c16b` | 2024-01-26 02:33:41 +0800 | advancedxy | Spec: Add multi-arg transform (#8579) | ✅ 已完成 | [0409_200b9c16b](commits/0409_200b9c16b/analysis.md) |
| 410 | `177572769` | 2024-01-25 10:45:57 -0800 | Alok Thatikunta | AWS: Update S3FileIO test to run when CLIENT_FACTORY is not set (#9541) | ✅ 已完成 | [0410_177572769](commits/0410_177572769/analysis.md) |
| 411 | `83408f888` | 2024-01-25 16:45:40 -0800 | Levani Kokhreidze | AWS: Support setting description for Glue table (#9530) | ✅ 已完成 | [0411_83408f888](commits/0411_83408f888/analysis.md) |
| 412 | `3f6e377a1` | 2024-01-26 07:44:01 -0800 | Eduard Tudenhoefner | Spark: Support creating views via SQL (#9423) | ✅ 已完成 | [0412_3f6e377a1](commits/0412_3f6e377a1/analysis.md) |
| 413 | `6852278d6` | 2024-01-26 21:30:27 +0100 | cccs-jc | Core: `streaming-skip-overwrite-snapshots` only skips (#8980) | ✅ 已完成 | [0413_6852278d6](commits/0413_6852278d6/analysis.md) |
| 414 | `f901ad205` | 2024-01-29 09:07:58 +0100 | JB Onofré | Build/Release: Upgrade to RAT 0.16.1 (#9579) | ✅ 已完成 | [0414_f901ad205](commits/0414_f901ad205/analysis.md) |
| 415 | `fe4229190` | 2024-01-29 09:08:38 +0100 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.25.1 to 3.25.2 (#9576) | ✅ 已完成 | [0415_fe4229190](commits/0415_fe4229190/analysis.md) |
| 416 | `54756b6f5` | 2024-01-29 09:09:51 +0100 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9572) | ✅ 已完成 | [0416_54756b6f5](commits/0416_54756b6f5/analysis.md) |
| 417 | `10ee51630` | 2024-01-29 11:39:20 -0800 | Eduard Tudenhoefner | Spark 3.4: Support creating views via SQL (#9580) | ✅ 已完成 | [0417_10ee51630](commits/0417_10ee51630/analysis.md) |
| 418 | `3be1939af` | 2024-01-29 17:14:34 -0800 | Rodrigo | Flink: Adds the ability to read from a branch on the Flink Iceberg Source (#9547) | ✅ 已完成 | [0418_3be1939af](commits/0418_3be1939af/analysis.md) |
| 419 | `e5dc5ec96` | 2024-01-30 09:22:37 +0100 | Mason Chen | Flink: Implement enumerator metrics for pending splits, pending records, and split discovery (#9524) | ✅ 已完成 | [0419_e5dc5ec96](commits/0419_e5dc5ec96/analysis.md) |
| 420 | `adec50c01` | 2024-01-30 17:57:45 +0100 | Brian "bits" Olsen | Move nightly versioned docs to top-level docs directory (#9578) | ✅ 已完成 | [0420_adec50c01](commits/0420_adec50c01/analysis.md) |
| 421 | `d295a45f0` | 2024-01-30 09:18:03 -0800 | Amogh Jahagirdar | Spark 3.4: Fix writing of default values in CoW for rows with NULL columns which are unmatched (#9556) | ✅ 已完成 | [0421_d295a45f0](commits/0421_d295a45f0/analysis.md) |
| 422 | `2af18b318` | 2024-01-30 09:54:48 -0800 | Eduard Tudenhoefner | Spark: Rewrite identifier when using Subquery expressions in View (#9587) | ✅ 已完成 | [0422_2af18b318](commits/0422_2af18b318/analysis.md) |
| 423 | `7f5e33dd0` | 2024-01-30 09:57:01 -0800 | dependabot[bot] | Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#9575) | ✅ 已完成 | [0423_7f5e33dd0](commits/0423_7f5e33dd0/analysis.md) |
| 424 | `8b429a286` | 2024-01-30 10:01:06 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.23.2 to 2.23.12 (#9573) | ✅ 已完成 | [0424_8b429a286](commits/0424_8b429a286/analysis.md) |
| 425 | `ac46000fc` | 2024-01-30 10:58:01 -0800 | Brian "bits" Olsen | Docs: Update ASF site to deploy from this repository (#9520) | ✅ 已完成 | [0425_ac46000fc](commits/0425_ac46000fc/analysis.md) |
| 426 | `a25f77d1c` | 2024-01-30 12:30:27 -0800 | Brian "bits" Olsen | Revert "Move nightly versioned docs to top-level docs directory (#9578)" (#9589) | ✅ 已完成 | [0426_a25f77d1c](commits/0426_a25f77d1c/analysis.md) |
| 427 | `f8866bf38` | 2024-01-30 19:12:17 -0800 | Anton Okolnychyi | Spark 3.5: Fix flaky TestSparkExecutorCache (#9583) | ✅ 已完成 | [0427_f8866bf38](commits/0427_f8866bf38/analysis.md) |
| 428 | `d5a9d34ed` | 2024-01-31 10:04:08 +0100 | Eduard Tudenhoefner | Spark: Add support for describing/showing views (#9513) | ✅ 已完成 | [0428_d5a9d34ed](commits/0428_d5a9d34ed/analysis.md) |
| 429 | `0536ff398` | 2024-01-31 10:30:02 +0100 | Eduard Tudenhoefner | Spark 3.4: Rewrite identifier when using Subquery expressions in View (#9594) | ✅ 已完成 | [0429_0536ff398](commits/0429_0536ff398/analysis.md) |
| 430 | `8138671ab` | 2024-01-31 12:52:37 +0100 | Eduard Tudenhoefner | Spark 3.4: Add support for describing/showing views (#9595) | ✅ 已完成 | [0430_8138671ab](commits/0430_8138671ab/analysis.md) |
| 431 | `26d62c06b` | 2024-01-31 14:11:20 +0100 | Geoffrey Jacoby | Flink: Added error handling and default logic for Flink version detection (#9452) | ✅ 已完成 | [0431_26d62c06b](commits/0431_26d62c06b/analysis.md) |
| 432 | `9de693f1e` | 2024-01-31 09:29:43 -0800 | Amogh Jahagirdar | API, Spark: Fix aggregation pushdown on struct fields (#9176) | ✅ 已完成 | [0432_9de693f1e](commits/0432_9de693f1e/analysis.md) |
| 433 | `974bde343` | 2024-02-01 08:46:49 +0100 | Marc Cenac | Open-API: Add table updates for statistics (#9564) | ✅ 已完成 | [0433_974bde343](commits/0433_974bde343/analysis.md) |
| 434 | `61532a042` | 2024-02-01 09:07:56 +0100 | Rodrigo | Flink: Backport #9364 to 1.16 and 1.17 for Create CatalogTestBase for migration to JUnit5 (#9601) | ✅ 已完成 | [0434_61532a042](commits/0434_61532a042/analysis.md) |
| 435 | `9c8e9ba67` | 2024-02-01 09:20:33 +0100 | Eduard Tudenhoefner | Spark: Support altering view properties (#9582) | ✅ 已完成 | [0435_9c8e9ba67](commits/0435_9c8e9ba67/analysis.md) |
| 436 | `a05cbcb4b` | 2024-02-01 09:53:10 +0100 | Eduard Tudenhoefner | Core: Add missing @Test to TestRESTCatalog (#9607) | ✅ 已完成 | [0436_a05cbcb4b](commits/0436_a05cbcb4b/analysis.md) |
| 437 | `66d4cf66e` | 2024-02-01 10:11:19 +0100 | Eduard Tudenhoefner | Spark: Throw exception on `ALTER VIEW <viewName> AS <query>` (#9510) | ✅ 已完成 | [0437_66d4cf66e](commits/0437_66d4cf66e/analysis.md) |
| 438 | `187be8531` | 2024-02-01 13:33:21 +0100 | Eduard Tudenhoefner | Spark 3.4: Support altering view properties (#9610) | ✅ 已完成 | [0438_187be8531](commits/0438_187be8531/analysis.md) |
| 439 | `daaf5a1c8` | 2024-02-01 15:29:52 +0100 | Eduard Tudenhoefner | Spark 3.4: Throw exception on `ALTER VIEW <viewName> AS <query>` (#9612) | ✅ 已完成 | [0439_daaf5a1c8](commits/0439_daaf5a1c8/analysis.md) |
| 440 | `e8c197d36` | 2024-02-01 18:34:25 +0100 | Anton Okolnychyi | Spark 3.4: Rework DeleteFileIndexBenchmark (#9600) | ✅ 已完成 | [0440_e8c197d36](commits/0440_e8c197d36/analysis.md) |
| 441 | `9a0191e8d` | 2024-02-01 09:36:42 -0800 | Anton Okolnychyi | Spark 3.4: Fix rewriting manifests for evolved unpartitioned V1 tables (#9599) | ✅ 已完成 | [0441_9a0191e8d](commits/0441_9a0191e8d/analysis.md) |
| 442 | `3547a99d0` | 2024-02-01 09:38:05 -0800 | Anton Okolnychyi | Spark 3.4: Support file and partition delete granularity (#9602) | ✅ 已完成 | [0442_3547a99d0](commits/0442_3547a99d0/analysis.md) |
| 443 | `6bbf70a52` | 2024-02-01 09:55:57 -0800 | Eduard Tudenhoefner | Spark: Bypass Spark's ViewCatalog API when replacing a view (#9596) | ✅ 已完成 | [0443_6bbf70a52](commits/0443_6bbf70a52/analysis.md) |
| 444 | `ed288987c` | 2024-02-01 21:55:49 +0100 | Brian "bits" Olsen | Convert Hugo versioned docs to mkdocs format (#9591) | ✅ 已完成 | [0444_ed288987c](commits/0444_ed288987c/analysis.md) |
| 445 | `f8a4cc225` | 2024-02-01 16:07:43 -0800 | Anton Okolnychyi | Spark 3.4: Extend action for rewriting manifests to support deletes (#9616) | ✅ 已完成 | [0445_f8a4cc225](commits/0445_f8a4cc225/analysis.md) |
| 446 | `2c247501c` | 2024-02-02 09:39:37 +0100 | Rodrigo |  Flink: backport #9381 to 1.17 and 1.16 for Migrate subclasses of FlinkCatalogTestBase to JUnit5 (#9598) | ✅ 已完成 | [0446_2c247501c](commits/0446_2c247501c/analysis.md) |
| 447 | `756fa6894` | 2024-02-02 09:43:50 +0100 | Eduard Tudenhoefner | Spark 3.4: Bypass Spark's ViewCatalog API when replacing a view (#9614) | ✅ 已完成 | [0447_756fa6894](commits/0447_756fa6894/analysis.md) |
| 448 | `d1e24f4c9` | 2024-02-02 11:26:12 +0100 | Tom Tanaka | Spark: Create ExtensionTestBase for migration to JUnit5 (#9613) | ✅ 已完成 | [0448_d1e24f4c9](commits/0448_d1e24f4c9/analysis.md) |
| 449 | `07c4345bb` | 2024-02-02 08:14:42 -0800 | Eduard Tudenhoefner | Spark: Fix CREATE OR REPLACE VIEW when view doesn't exist (#9621) | ✅ 已完成 | [0449_07c4345bb](commits/0449_07c4345bb/analysis.md) |
| 450 | `0f11340bf` | 2024-02-02 19:13:51 +0100 | Rodrigo | Flink: change defaultFlinkVersion back to 1.18 (#9625) | ✅ 已完成 | [0450_0f11340bf](commits/0450_0f11340bf/analysis.md) |
| 451 | `770342cb5` | 2024-02-03 03:01:25 +0800 | Hongyue/Steve Zhang | Spark 3.4: Use ProcedureInput for RewriteDataFiles (#8583) | ✅ 已完成 | [0451_770342cb5](commits/0451_770342cb5/analysis.md) |
| 452 | `aff5b39a7` | 2024-02-02 15:04:40 -0800 | Brian "bits" Olsen | Remove nightly and add .asf.yaml (#9622) | ✅ 已完成 | [0452_aff5b39a7](commits/0452_aff5b39a7/analysis.md) |
| 453 | `65a076ded` | 2024-02-02 15:24:55 -0800 | Anton Okolnychyi | Spark 3.4: Read deletes in parallel and cache them on executors (#9603) | ✅ 已完成 | [0453_65a076ded](commits/0453_65a076ded/analysis.md) |
| 454 | `338c0b83d` | 2024-02-03 08:22:41 -0800 | Gang Wu | Parquet, Arrow: Rename BagePageReader to BasePageReader in VectorizedPageIterator (#9630) | ✅ 已完成 | [0454_338c0b83d](commits/0454_338c0b83d/analysis.md) |
| 455 | `49986b7d6` | 2024-02-03 12:29:14 -0800 | Daniel Weeks | Add REST spec for data access mechanisms (#9628) | ✅ 已完成 | [0455_49986b7d6](commits/0455_49986b7d6/analysis.md) |
| 456 | `f4ba90d64` | 2024-02-03 12:40:45 -0800 | Bryan Keller | Kafka Connect: Sink connector with data writers and converters (#9466) | ✅ 已完成 | [0456_f4ba90d64](commits/0456_f4ba90d64/analysis.md) |
| 457 | `fb02bd2d7` | 2024-02-03 12:42:58 -0800 | Anton Okolnychyi | Core: Fix performance issue when combining tasks by partition (#9629) | ✅ 已完成 | [0457_fb02bd2d7](commits/0457_fb02bd2d7/analysis.md) |
| 458 | `f8a4a7458` | 2024-02-04 09:30:03 -0800 | Manu Zhang | Docs: Enhance Java quickstart example (#9585) | ✅ 已完成 | [0458_f8a4a7458](commits/0458_f8a4a7458/analysis.md) |
| 459 | `9921937d8` | 2024-02-05 03:11:19 +0100 | Ajantha Bhat | Update blogs.md (#9552) | ✅ 已完成 | [0459_9921937d8](commits/0459_9921937d8/analysis.md) |
| 460 | `d5e00f102` | 2024-02-05 10:16:19 +0100 | Ajantha Bhat | Docs: Update Nessie URI to API v2 (#9648) | ✅ 已完成 | [0460_d5e00f102](commits/0460_d5e00f102/analysis.md) |
| 461 | `4a3e06bd9` | 2024-02-05 10:34:23 +0100 | Eduard Tudenhoefner | Spark 3.4: Fix CREATE OR REPLACE VIEW when view doesn't exist (#9646) | ✅ 已完成 | [0461_4a3e06bd9](commits/0461_4a3e06bd9/analysis.md) |
| 462 | `a2c23a762` | 2024-02-05 10:35:38 +0100 | Eduard Tudenhoefner | Docs: Fix listing of catalog implementations (#9649) | ✅ 已完成 | [0462_a2c23a762](commits/0462_a2c23a762/analysis.md) |
| 463 | `c516fef72` | 2024-02-05 12:38:56 +0100 | Fokko Driesprong | Label `site/` as documentation (#9652) | ✅ 已完成 | [0463_c516fef72](commits/0463_c516fef72/analysis.md) |
| 464 | `a1e9e58c0` | 2024-02-05 14:56:07 +0100 | Ajantha Bhat | Core: Add catalog type for glue,jdbc,nessie (#9647) | ✅ 已完成 | [0464_a1e9e58c0](commits/0464_a1e9e58c0/analysis.md) |
| 465 | `c34efa068` | 2024-02-05 17:22:34 +0100 | Rodrigo | Flink: backport #9547 to 1.17 and 1.16 for Adds the ability to read from a branch on the Flink Iceberg Source (#9627) | ✅ 已完成 | [0465_c34efa068](commits/0465_c34efa068/analysis.md) |
| 466 | `67a8f01bf` | 2024-02-05 08:34:21 -0800 | Adnan Hemani | AWS: Add S3 Access Grants Documentation (#9590) | ✅ 已完成 | [0466_67a8f01bf](commits/0466_67a8f01bf/analysis.md) |
| 467 | `c4cb0fb99` | 2024-02-05 10:03:56 -0800 | Brian "bits" Olsen | Docs: Move catalog under concepts folder and add code of conduct page to act as root for ASF. (#9642) | ✅ 已完成 | [0467_c4cb0fb99](commits/0467_c4cb0fb99/analysis.md) |
| 468 | `c745ac3b6` | 2024-02-05 10:33:51 -0800 | Anton Okolnychyi | Spark 3.5: Support executor cache locality (#9563) | ✅ 已完成 | [0468_c745ac3b6](commits/0468_c745ac3b6/analysis.md) |
| 469 | `b6c3f8f2b` | 2024-02-05 17:08:00 -0800 | Anton Okolnychyi | Spark 3.4: Support executor cache locality (#9658) | ✅ 已完成 | [0469_b6c3f8f2b](commits/0469_b6c3f8f2b/analysis.md) |
| 470 | `24a1480f8` | 2024-02-06 08:52:16 +0100 | Eduard Tudenhoefner | Docs: Add newline so that subsection is correctly rendered (#9656) | ✅ 已完成 | [0470_24a1480f8](commits/0470_24a1480f8/analysis.md) |
| 471 | `4835549ac` | 2024-02-06 11:40:08 +0100 | Tom Tanaka | Spark: Migrate tests to JUnit5 (#9624) | ✅ 已完成 | [0471_4835549ac](commits/0471_4835549ac/analysis.md) |
| 472 | `396a8441c` | 2024-02-06 13:21:57 +0100 | Manu Zhang | Docs: Add newline to fix lists (#9664) | ✅ 已完成 | [0472_396a8441c](commits/0472_396a8441c/analysis.md) |
| 473 | `e61b0e51e` | 2024-02-06 07:48:51 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.23.12 to 2.23.17 (#9633) | ✅ 已完成 | [0473_e61b0e51e](commits/0473_e61b0e51e/analysis.md) |
| 474 | `4751a3767` | 2024-02-06 19:55:20 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.2 to 0.25.3 (#9639) | ✅ 已完成 | [0474_4751a3767](commits/0474_4751a3767/analysis.md) |
| 475 | `b89c395ca` | 2024-02-06 19:55:35 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.3 to 9.5.7 (#9638) | ✅ 已完成 | [0475_b89c395ca](commits/0475_b89c395ca/analysis.md) |
| 476 | `dd0ac5bce` | 2024-02-06 19:56:10 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.44.0.0 to 3.45.1.0 (#9634) | ✅ 已完成 | [0476_dd0ac5bce](commits/0476_dd0ac5bce/analysis.md) |
| 477 | `a130f8f1c` | 2024-02-06 20:11:11 +0100 | dependabot[bot] | Build: Bump mkdocs-material-extensions from 1.3 to 1.3.1 (#9160) | ✅ 已完成 | [0477_a130f8f1c](commits/0477_a130f8f1c/analysis.md) |
| 478 | `af4648741` | 2024-02-06 20:11:50 +0100 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.0 to 1.0.1 (#9317) | ✅ 已完成 | [0478_af4648741](commits/0478_af4648741/analysis.md) |
| 479 | `defef48e2` | 2024-02-06 14:57:07 -0800 | Abid Mohammed | Core: Only trim trailing slash when warehouse location is not root path (#9619) | ✅ 已完成 | [0479_defef48e2](commits/0479_defef48e2/analysis.md) |
| 480 | `3348d88d9` | 2024-02-06 17:27:12 -0800 | dependabot[bot] | Build: Bump io.delta:delta-spark_2.12 from 3.0.0 to 3.1.0 (#9631) | ✅ 已完成 | [0480_3348d88d9](commits/0480_3348d88d9/analysis.md) |
| 481 | `9f979a1ff` | 2024-02-06 18:17:40 -0800 | dependabot[bot] | Build: Bump io.delta:delta-standalone_2.12 from 0.6.0 to 3.1.0 (#9636) | ✅ 已完成 | [0481_9f979a1ff](commits/0481_9f979a1ff/analysis.md) |
| 482 | `5d3be12e0` | 2024-02-07 08:13:22 +0100 | Fokko Driesprong | Spark: Move the Writer to a visitor (#9440) | ✅ 已完成 | [0482_5d3be12e0](commits/0482_5d3be12e0/analysis.md) |
| 483 | `3c703ccce` | 2024-02-07 08:17:53 +0100 | Fokko Driesprong | Azure: Bump Azurite container (#9668) | ✅ 已完成 | [0483_3c703ccce](commits/0483_3c703ccce/analysis.md) |
| 484 | `2a39af894` | 2024-02-07 08:32:43 +0100 | Eduard Tudenhoefner | Spark: Handle concurrently dropped view during CREATE OR REPLACE (#9623) | ✅ 已完成 | [0484_2a39af894](commits/0484_2a39af894/analysis.md) |
| 485 | `8a47f7189` | 2024-02-07 09:10:22 +0100 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.18 to 1.2.20 (#9571) | ✅ 已完成 | [0485_8a47f7189](commits/0485_8a47f7189/analysis.md) |
| 486 | `97e9b3e77` | 2024-02-07 09:10:52 +0100 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.19.3 to 1.19.4 (#9577) | ✅ 已完成 | [0486_97e9b3e77](commits/0486_97e9b3e77/analysis.md) |
| 487 | `40fbd8dc5` | 2024-02-07 09:43:41 +0100 | Fokko Driesprong | Spark 3.3: Move the Writer to a visitor (#9672) | ✅ 已完成 | [0487_40fbd8dc5](commits/0487_40fbd8dc5/analysis.md) |
| 488 | `4446e4f0a` | 2024-02-07 09:44:23 +0100 | Fokko Driesprong | Spark 3.4: Move the Writer to a visitor (#9673) | ✅ 已完成 | [0488_4446e4f0a](commits/0488_4446e4f0a/analysis.md) |
| 489 | `0cab58b04` | 2024-02-07 10:35:35 +0100 | Eduard Tudenhoefner | Spark: Avoid NPE when catalog config doesn't have "type" set (#9676) | ✅ 已完成 | [0489_0cab58b04](commits/0489_0cab58b04/analysis.md) |
| 490 | `637a46569` | 2024-02-07 15:18:06 +0100 | Eduard Tudenhoefner | Spark 3.4: Handle concurrently dropped view during CREATE OR REPLACE (#9677) | ✅ 已完成 | [0490_637a46569](commits/0490_637a46569/analysis.md) |
| 491 | `3b8fd904e` | 2024-02-07 16:54:59 -0800 | Scott Teal | Docs: Add/update Snowflake (#9669) | ✅ 已完成 | [0491_3b8fd904e](commits/0491_3b8fd904e/analysis.md) |
| 492 | `44eb00daa` | 2024-02-08 12:35:16 +0100 | Hongyue/Steve Zhang | open-api: Use openapi-generator-gradle-plugin for  validating specification (#9344) | ✅ 已完成 | [0492_44eb00daa](commits/0492_44eb00daa/analysis.md) |
| 493 | `90d1c90b6` | 2024-02-08 15:47:53 +0100 | Muna Bedan | Docs: Fix hidden-partition-animation not showing (#9686) | ✅ 已完成 | [0493_90d1c90b6](commits/0493_90d1c90b6/analysis.md) |
| 494 | `127214568` | 2024-02-08 19:28:44 +0100 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.14.4 to 3.14.5 (#9570) | ✅ 已完成 | [0494_127214568](commits/0494_127214568/analysis.md) |
| 495 | `5f577f1b9` | 2024-02-09 09:33:12 +0100 | Marc Cenac | OpenAPI: Spec updates for statistics (#9690) | ✅ 已完成 | [0495_5f577f1b9](commits/0495_5f577f1b9/analysis.md) |
| 496 | `12bd9d005` | 2024-02-11 20:35:12 +0100 | Muna Bedan | Docs: Fix broken strike-through markup (#9696) | ✅ 已完成 | [0496_12bd9d005](commits/0496_12bd9d005/analysis.md) |
| 497 | `77363ce14` | 2024-02-11 20:52:49 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.7 to 9.5.9 (#9708) | ✅ 已完成 | [0497_77363ce14](commits/0497_77363ce14/analysis.md) |
| 498 | `622d1a09d` | 2024-02-11 20:53:21 +0100 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.19.4 to 1.19.5 (#9704) | ✅ 已完成 | [0498_622d1a09d](commits/0498_622d1a09d/analysis.md) |
| 499 | `053d54172` | 2024-02-11 20:55:48 +0100 | dependabot[bot] | Build: Bump io.airlift:aircompressor from 0.25 to 0.26 (#9700) | ✅ 已完成 | [0499_053d54172](commits/0499_053d54172/analysis.md) |
| 500 | `50fb4004d` | 2024-02-13 12:20:19 +0100 | DongDongLee | MR: Migrate parameterized tests to JUni5 (#9711) | ✅ 已完成 | [0500_50fb4004d](commits/0500_50fb4004d/analysis.md) |
| 501 | `9bac5c490` | 2024-02-14 21:12:22 +0100 | Alexandre Dutra | Upgrade Nessie to 0.77.1 (#9726) | ✅ 已完成 | [0501_9bac5c490](commits/0501_9bac5c490/analysis.md) |
| 502 | `8991c1a91` | 2024-02-14 21:14:26 +0100 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.25.2 to 3.25.3 (#9706) | ✅ 已完成 | [0502_8991c1a91](commits/0502_8991c1a91/analysis.md) |
| 503 | `6d0c5d96d` | 2024-02-14 21:15:32 +0100 | dependabot[bot] | Build: Bump tez010 from 0.10.2 to 0.10.3 (#9702) | ✅ 已完成 | [0503_6d0c5d96d](commits/0503_6d0c5d96d/analysis.md) |
| 504 | `fcd0663ca` | 2024-02-14 21:16:18 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.23.17 to 2.24.0 (#9701) | ✅ 已完成 | [0504_fcd0663ca](commits/0504_fcd0663ca/analysis.md) |
| 505 | `d32abe8aa` | 2024-02-14 21:18:01 +0100 | dependabot[bot] | Build: Bump arrow from 14.0.2 to 15.0.0 (#9574) | ✅ 已完成 | [0505_d32abe8aa](commits/0505_d32abe8aa/analysis.md) |
| 506 | `42a2c19ce` | 2024-02-15 11:19:28 +0100 | Eduard Tudenhoefner | Core: Only write view history when currentVersionId changes (#9725) | ✅ 已完成 | [0506_42a2c19ce](commits/0506_42a2c19ce/analysis.md) |
| 507 | `212355e13` | 2024-02-15 11:18:48 -0800 | Eduard Tudenhoefner | Core: Add strictness flag to prevent loss of view representation when replacing a view (#9620) | ✅ 已完成 | [0507_212355e13](commits/0507_212355e13/analysis.md) |
| 508 | `062704283` | 2024-02-16 12:46:22 +0100 | Drew Gallardo | Core: Make InMemoryFileIO map shared across instances (#9722) | ✅ 已完成 | [0508_062704283](commits/0508_062704283/analysis.md) |
| 509 | `1854d08a6` | 2024-02-16 09:40:01 -0800 | Eduard Tudenhoefner | Core: Properly suppress historical snapshots when building TableMetadata with suppressHistoricalSnapshots() (#9234) | ✅ 已完成 | [0509_1854d08a6](commits/0509_1854d08a6/analysis.md) |
| 510 | `0316be363` | 2024-02-16 16:58:25 -0800 | JB Onofré | Core: Add view support on the JDBC catalog (#9487) | ✅ 已完成 | [0510_0316be363](commits/0510_0316be363/analysis.md) |
| 511 | `9dcf8dbc4` | 2024-02-16 17:35:53 -0800 | Sung Yun | Support usage of Separate OIDC Authorization Server URI (#8976) | ✅ 已完成 | [0511_9dcf8dbc4](commits/0511_9dcf8dbc4/analysis.md) |
| 512 | `5e139f485` | 2024-02-17 08:16:57 -0800 | Eduard Tudenhoefner | Core: Don't reset snapshotLog when replacing Table (#9732) | ✅ 已完成 | [0512_5e139f485](commits/0512_5e139f485/analysis.md) |
| 513 | `bff665278` | 2024-02-19 08:39:11 +0100 | Gang Wu | Aliyun: Add security token to client properties (#9671) | ✅ 已完成 | [0513_bff665278](commits/0513_bff665278/analysis.md) |
| 514 | `f4ee68710` | 2024-02-19 10:24:19 +0100 | dependabot[bot] | Build: Bump org.immutables:value from 2.10.0 to 2.10.1 (#9749) | ✅ 已完成 | [0514_f4ee68710](commits/0514_f4ee68710/analysis.md) |
| 515 | `227553464` | 2024-02-19 10:24:46 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.24.0 to 2.24.5 (#9743) | ✅ 已完成 | [0515_227553464](commits/0515_227553464/analysis.md) |
| 516 | `f1459e214` | 2024-02-19 10:25:20 +0100 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.68.Final to 4.1.107.Final (#9744) | ✅ 已完成 | [0516_f1459e214](commits/0516_f1459e214/analysis.md) |
| 517 | `f5ae0add6` | 2024-02-19 10:26:01 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.3 to 0.25.4 (#9742) | ✅ 已完成 | [0517_f5ae0add6](commits/0517_f5ae0add6/analysis.md) |
| 518 | `598552e13` | 2024-02-19 18:08:18 +0100 | pvary | Allow creating metadata tables based on SerializableTable instances (#9735) | ✅ 已完成 | [0518_598552e13](commits/0518_598552e13/analysis.md) |
| 519 | `4c5208a42` | 2024-02-19 14:42:51 -0800 | Ryan Blue | API: Extend FileIO and add EncryptingFileIO. (#9592) | ✅ 已完成 | [0519_4c5208a42](commits/0519_4c5208a42/analysis.md) |
| 520 | `5b84f34a5` | 2024-02-19 15:14:52 -0800 | Eduard Tudenhoefner | Core: Don't fail if a REST service doesn't support views (#9754) | ✅ 已完成 | [0520_5b84f34a5](commits/0520_5b84f34a5/analysis.md) |
| 521 | `c4d827ef2` | 2024-02-20 09:17:46 +0100 | Tom Tanaka | Spark: Migrate tests to JUnit5 (#9670) | ✅ 已完成 | [0521_c4d827ef2](commits/0521_c4d827ef2/analysis.md) |
| 522 | `66e957bd9` | 2024-02-20 10:51:05 +0100 | Manu Zhang | Infra: Fix issue template labels (#9759) | ✅ 已完成 | [0522_66e957bd9](commits/0522_66e957bd9/analysis.md) |
| 523 | `eab958b62` | 2024-02-20 20:35:07 -0800 | Reo | Flink 1.18: Fix continuous enumerator lost enumeration history state when restore from checkpoint. (#9762) | ✅ 已完成 | [0523_eab958b62](commits/0523_eab958b62/analysis.md) |
| 524 | `1b6826251` | 2024-02-20 20:47:25 -0800 | Reo | Flink 1.18: Fix iceberg source plan parallelism not effective. (#9761) | ✅ 已完成 | [0524_1b6826251](commits/0524_1b6826251/analysis.md) |
| 525 | `53ab0e209` | 2024-02-21 08:07:27 +0100 | Ryan Blue | API: Fix EncryptingFileIO factory method (#9757) | ✅ 已完成 | [0525_53ab0e209](commits/0525_53ab0e209/analysis.md) |
| 526 | `e1f50fd35` | 2024-02-21 12:15:55 +0100 | JB Onofré | Core: Use V0 SQL schema as default /  rename jdbc.add-view-support to jdbc.schema-version (#9765) | ✅ 已完成 | [0526_e1f50fd35](commits/0526_e1f50fd35/analysis.md) |
| 527 | `811c92075` | 2024-02-21 13:53:01 +0100 | Fokko Driesprong | Infra: Add Kafka Connect as a label (#9769) | ✅ 已完成 | [0527_811c92075](commits/0527_811c92075/analysis.md) |
| 528 | `0c8703078` | 2024-02-21 16:57:42 +0100 | JB Onofré | Core: Only test if view exists when using SchemaVersion.V1 during table rename (#9770) | ✅ 已完成 | [0528_0c8703078](commits/0528_0c8703078/analysis.md) |
| 529 | `f17922bd7` | 2024-02-22 16:41:28 +0100 | Eduard Tudenhoefner | docs: Fix listings on Release page / Update Multi-engine support (#9775) | ✅ 已完成 | [0529_f17922bd7](commits/0529_f17922bd7/analysis.md) |
| 530 | `3058e3074` | 2024-02-22 18:03:03 +0100 | Manu Zhang | Infra: Don't run Delta Conversion CI on changes to site folder (#9780) | ✅ 已完成 | [0530_3058e3074](commits/0530_3058e3074/analysis.md) |
| 531 | `d95bd712f` | 2024-02-23 17:17:23 +0100 | Manu Zhang | Docs: Sync specs to site via symlinks (#9779) | ✅ 已完成 | [0531_d95bd712f](commits/0531_d95bd712f/analysis.md) |
| 532 | `00b7d5bec` | 2024-02-23 08:41:01 -0800 | Drew Gallardo | AWS: Deprecate DynamoDB catalog (#9783) | ✅ 已完成 | [0532_00b7d5bec](commits/0532_00b7d5bec/analysis.md) |
| 533 | `569c12d7f` | 2024-02-23 11:29:54 -0800 | Drew Gallardo | AWS: Adjust Deprecation Version for DynamoDB Catalog to 1.5.0 (#9788) | ✅ 已完成 | [0533_569c12d7f](commits/0533_569c12d7f/analysis.md) |
| 534 | `56da99b9f` | 2024-02-24 11:52:39 +0100 | Tom Tanaka | Spark: Migrate procedure tests to JUnit5 (#9760) | ✅ 已完成 | [0534_56da99b9f](commits/0534_56da99b9f/analysis.md) |
| 535 | `b3c68e540` | 2024-02-25 10:22:43 -0800 | Eduard Tudenhoefner | Spark: Fail if temp functions are used in views (#9675) | ✅ 已完成 | [0535_b3c68e540](commits/0535_b3c68e540/analysis.md) |
| 536 | `7a7950e40` | 2024-02-26 10:02:35 +0100 | Eduard Tudenhoefner | Spark 3.4: Fail if temp functions are used in views (#9809) | ✅ 已完成 | [0536_7a7950e40](commits/0536_7a7950e40/analysis.md) |
| 537 | `f14c0e553` | 2024-02-26 10:54:18 +0100 | Eduard Tudenhoefner | Spark: Include catalog name in view errors (#9807) | ✅ 已完成 | [0537_f14c0e553](commits/0537_f14c0e553/analysis.md) |
| 538 | `c64992357` | 2024-02-26 12:07:18 +0100 | Manu Zhang | Build: Ignore major version update in dependabot (#9806) | ✅ 已完成 | [0538_c64992357](commits/0538_c64992357/analysis.md) |
| 539 | `f3817e128` | 2024-02-26 12:38:07 +0100 | Eduard Tudenhoefner | Docs: Sync contributing page / refer to website for contributing (#9776) | ✅ 已完成 | [0539_f3817e128](commits/0539_f3817e128/analysis.md) |
| 540 | `487ff98ce` | 2024-02-26 13:58:15 +0100 | Reo | Flink 1.16, 1.17: Fix continuous enumerator lost enumeration history state when restore from checkpoint (#9812) | ✅ 已完成 | [0540_487ff98ce](commits/0540_487ff98ce/analysis.md) |
| 541 | `2d927b0ec` | 2024-02-26 13:58:52 +0100 | Reo | Flink 1.16, 1.17: Fix iceberg source plan parallelism not effective (#9811) | ✅ 已完成 | [0541_2d927b0ec](commits/0541_2d927b0ec/analysis.md) |
| 542 | `10ec85ffc` | 2024-02-26 15:51:43 +0100 | Eduard Tudenhoefner | Spark 3.4: Include catalog name in view errors (#9810) | ✅ 已完成 | [0542_10ec85ffc](commits/0542_10ec85ffc/analysis.md) |
| 543 | `c18cb969f` | 2024-02-26 15:21:31 -0800 | Szehon Ho | Spec: Clarify multi-arg transform behavior for different versions (#9661) | ✅ 已完成 | [0543_c18cb969f](commits/0543_c18cb969f/analysis.md) |
| 544 | `b788b5f9e` | 2024-02-27 08:32:18 +0100 | Eduard Tudenhoefner | Spark 3.4, 3.5: Use current namespace for SHOW VIEWS cmd (#9787) | ✅ 已完成 | [0544_b788b5f9e](commits/0544_b788b5f9e/analysis.md) |
| 545 | `e39ec185d` | 2024-02-27 09:41:51 +0100 | Ajantha Bhat | AWS: Revert DynamoDb deprecation for 1.5.0 (#9815) | ✅ 已完成 | [0545_e39ec185d](commits/0545_e39ec185d/analysis.md) |
| 546 | `22401546e` | 2024-02-27 15:19:02 +0100 | Tom Tanaka | Spark: Migrate tests to JUnit5 (#9790) | ✅ 已完成 | [0546_22401546e](commits/0546_22401546e/analysis.md) |
| 547 | `8a16a4174` | 2024-02-27 07:20:08 -0800 | dongwang | API: Fix typo in method comment of SortOrder and SortOrderBuilder (#9816) | ✅ 已完成 | [0547_8a16a4174](commits/0547_8a16a4174/analysis.md) |
| 548 | `bd9c615d5` | 2024-02-27 10:54:04 -0800 | Eduard Tudenhoefner | Spark: Improve error msg when function can't be loaded (#9814) | ✅ 已完成 | [0548_bd9c615d5](commits/0548_bd9c615d5/analysis.md) |
| 549 | `6a3b2d7c1` | 2024-02-28 09:28:51 +0100 | Tom Tanaka | Spark: Remove/migrate remaining JUnit4 tests (#9817) | ✅ 已完成 | [0549_6a3b2d7c1](commits/0549_6a3b2d7c1/analysis.md) |
| 550 | `274fc26a7` | 2024-02-28 16:03:12 +0100 | Eduard Tudenhoefner | Spark 3.4: Improve error msg when function can't be loaded (#9821) | ✅ 已完成 | [0550_274fc26a7](commits/0550_274fc26a7/analysis.md) |
| 551 | `c3542e7bd` | 2024-02-28 16:07:59 +0100 | Naveen Kumar | Core, Spark: Fix build warning related to Javadoc link tag (#9823) | ✅ 已完成 | [0551_c3542e7bd](commits/0551_c3542e7bd/analysis.md) |
| 552 | `50af8c070` | 2024-02-28 17:48:48 +0100 | Alok Thatikunta | OpenAPI: Fix URL pointing to catalog properties (#9825) | ✅ 已完成 | [0552_50af8c070](commits/0552_50af8c070/analysis.md) |
| 553 | `bb53c3d4e` | 2024-02-28 08:57:38 -0800 | Drew Gallardo | REST spec: Add ContentFile types to spec for the PreplanTable and PlanTable API (#9717) | ✅ 已完成 | [0553_bb53c3d4e](commits/0553_bb53c3d4e/analysis.md) |
| 554 | `acbf96f4f` | 2024-02-29 10:37:42 -0800 | Rahil C | Add pagination to open api spec for listing of namespaces, tables, views (#9660) | ✅ 已完成 | [0554_acbf96f4f](commits/0554_acbf96f4f/analysis.md) |
| 555 | `08e31ce32` | 2024-02-29 17:02:34 -0600 | Russell Spitzer | Core: FileIO Reflection Error Message is Misleading (#9840) | ✅ 已完成 | [0555_08e31ce32](commits/0555_08e31ce32/analysis.md) |
| 556 | `f9ad8f373` | 2024-03-02 13:03:35 -0800 | Brian "bits" Olsen | Docs: Fix image on spec (#9843) | ✅ 已完成 | [0556_f9ad8f373](commits/0556_f9ad8f373/analysis.md) |
| 557 | `1a4f23bc0` | 2024-03-02 14:05:42 -0800 | Ryan Blue | Core: Fix REST catalog handling when the service has no view support (#9853) | ✅ 已完成 | [0557_1a4f23bc0](commits/0557_1a4f23bc0/analysis.md) |
| 558 | `45a086b3e` | 2024-03-03 12:42:55 -0800 | Brian "bits" Olsen | Site: Remove embedded calendar, replace with links (#9854) | ✅ 已完成 | [0558_45a086b3e](commits/0558_45a086b3e/analysis.md) |
| 559 | `bcbcbb263` | 2024-03-03 12:44:39 -0800 | Brian "bits" Olsen | Site: Update for ASF site guidelines (#9729) | ✅ 已完成 | [0559_bcbcbb263](commits/0559_bcbcbb263/analysis.md) |
| 560 | `05f99b658` | 2024-03-04 18:29:50 -0800 | big face cat | Flink: Supports specifying comment for iceberg fields in create table and addcolumn syntax using flinksql (#9606) | ✅ 已完成 | [0560_05f99b658](commits/0560_05f99b658/analysis.md) |
| 561 | `1ae13b8bf` | 2024-03-05 07:32:50 -0800 | big face cat | Flink:backport PR to 1.17 #9606 : Supports specifying comment for iceberg fields in create table and addcolumn syntax using flinksql (#9868) | ✅ 已完成 | [0561_1ae13b8bf](commits/0561_1ae13b8bf/analysis.md) |
| 562 | `2519ab43d` | 2024-03-05 10:30:48 -0800 | Eduard Tudenhoefner | Build: Don't publish iceberg-open-api module (#9871) | ✅ 已完成 | [0562_2519ab43d](commits/0562_2519ab43d/analysis.md) |
| 563 | `f0b3733e0` | 2024-03-06 11:09:22 +0100 | Brian "bits" Olsen | Docs: Update specs from hugo to mkdocs format (#9861) | ✅ 已完成 | [0563_f0b3733e0](commits/0563_f0b3733e0/analysis.md) |
| 564 | `99b1d0ee4` | 2024-03-06 21:58:54 -0800 | Himadri Pal | Spark 3.5: Add Support for Providing output-spec-id During Rewrite Datafiles | ✅ 已完成 | [0564_99b1d0ee4](commits/0564_99b1d0ee4/analysis.md) |
| 565 | `783dbe2bd` | 2024-03-07 09:22:32 +0100 | Manu Zhang | Docs: Fix links to internal files (#9819) | ✅ 已完成 | [0565_783dbe2bd](commits/0565_783dbe2bd/analysis.md) |
| 566 | `ad9e4e810` | 2024-03-07 09:26:54 +0100 | Tom Tanaka | Core: Migrate tests to JUnit5 (#9849) | ✅ 已完成 | [0566_ad9e4e810](commits/0566_ad9e4e810/analysis.md) |
| 567 | `c0486eef4` | 2024-03-07 09:58:23 +0100 | Ajantha Bhat | Nessie: Gracefully handle empty namespace lookup (#9877) | ✅ 已完成 | [0567_c0486eef4](commits/0567_c0486eef4/analysis.md) |
| 568 | `d90ac0019` | 2024-03-07 14:04:38 +0100 | Eduard Tudenhoefner | Docs: Add DDL docs for Views (#9878) | ✅ 已完成 | [0568_d90ac0019](commits/0568_d90ac0019/analysis.md) |
| 569 | `80c92cd75` | 2024-03-07 16:47:09 +0100 | Robert Stupp | Core: Make constants in CatalogTests protected (#9894) | ✅ 已完成 | [0569_80c92cd75](commits/0569_80c92cd75/analysis.md) |
| 570 | `951d09910` | 2024-03-07 18:54:08 +0100 | Manu Zhang | Build: Free disk space before running action in Spark CI (#9786) | ✅ 已完成 | [0570_951d09910](commits/0570_951d09910/analysis.md) |
| 571 | `afb200041` | 2024-03-08 09:28:59 +0100 | Eduard Tudenhoefner | Core: Add test for renaming table to a non-existing namespace (#9895) | ✅ 已完成 | [0571_afb200041](commits/0571_afb200041/analysis.md) |
| 572 | `e447277b6` | 2024-03-08 17:25:15 -0800 | Amogh Jahagirdar | Core: Mark 502 and 504 failures as retryable to the exponential retry strategy (#9885) | ✅ 已完成 | [0572_e447277b6](commits/0572_e447277b6/analysis.md) |
| 573 | `b261edd0c` | 2024-03-08 17:29:35 -0800 | Alexandre Dutra | Core: Add missing `@Test` annotation in TestRESTCatalog (#9899) | ✅ 已完成 | [0573_b261edd0c](commits/0573_b261edd0c/analysis.md) |
| 574 | `c13c0c94d` | 2024-03-08 23:29:09 -0800 | Himadri Pal | Spark 3.4, 3.3 : Support output-spec-id in rewrite data files(#9901)(Backport #9803) | ✅ 已完成 | [0574_c13c0c94d](commits/0574_c13c0c94d/analysis.md) |
| 575 | `fd7f6b708` | 2024-03-11 11:32:27 +0100 | Manu Zhang | Docs: Enhance Flink pages (#9919) | ✅ 已完成 | [0575_fd7f6b708](commits/0575_fd7f6b708/analysis.md) |
| 576 | `a9d6310b8` | 2024-03-11 11:55:13 +0100 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.1 to 1.0.5 (#9911) | ✅ 已完成 | [0576_a9d6310b8](commits/0576_a9d6310b8/analysis.md) |
| 577 | `4e149ca4d` | 2024-03-11 11:58:07 +0100 | Ajantha Bhat | Site: Update release notes for 1.5.0 (#9835) | ✅ 已完成 | [0577_4e149ca4d](commits/0577_4e149ca4d/analysis.md) |
| 578 | `5ce5c788c` | 2024-03-11 14:53:24 +0100 | Tom Tanaka | Core: Migrate tests to JUnit5 (#9892) | ✅ 已完成 | [0578_5ce5c788c](commits/0578_5ce5c788c/analysis.md) |
| 579 | `8b8907e09` | 2024-03-11 14:56:03 +0100 | Naveen Kumar | Data, Flink, Spark: Migrate TestAppenderFactory and subclasses to JUnit5 (#9862) | ✅ 已完成 | [0579_8b8907e09](commits/0579_8b8907e09/analysis.md) |
| 580 | `790975ffd` | 2024-03-11 15:40:44 +0100 | Ajantha Bhat | Infra: Add 1.5.0 to issue template (#9778) | ✅ 已完成 | [0580_790975ffd](commits/0580_790975ffd/analysis.md) |
| 581 | `1cdb2ebe9` | 2024-03-11 15:43:35 +0100 | Ajantha Bhat | Update ASF DOAP file (#9922) | ✅ 已完成 | [0581_1cdb2ebe9](commits/0581_1cdb2ebe9/analysis.md) |
| 582 | `5c27843d7` | 2024-03-11 15:44:07 +0100 | Ajantha Bhat | Build: Let revapi compare against 1.5.0 (#9777) | ✅ 已完成 | [0582_5c27843d7](commits/0582_5c27843d7/analysis.md) |
| 583 | `dc5cac4ac` | 2024-03-11 22:21:15 +0100 | Brian "bits" Olsen | Update site to 1.5.0 docs (#9931) | ✅ 已完成 | [0583_dc5cac4ac](commits/0583_dc5cac4ac/analysis.md) |
| 584 | `5f655a323` | 2024-03-11 14:22:28 -0700 | Himadri Pal | Make OAuth `audience` and `resource` configurable (#9839) | ✅ 已完成 | [0584_5f655a323](commits/0584_5f655a323/analysis.md) |
| 585 | `043204851` | 2024-03-11 16:52:48 -0700 | Anton Okolnychyi | Spark 3.5: Fix system function pushdown in CoW row-level commands (#9873) | ✅ 已完成 | [0585_043204851](commits/0585_043204851/analysis.md) |
| 586 | `9c2b8f641` | 2024-03-12 05:47:21 +0100 | Ajantha Bhat | Docs: Fix release notes indentation (#9933) | ✅ 已完成 | [0586_9c2b8f641](commits/0586_9c2b8f641/analysis.md) |
| 587 | `3891b4873` | 2024-03-12 08:00:22 +0100 | Ajantha Bhat | Flink: Bump minor versions (#9875) | ✅ 已完成 | [0587_3891b4873](commits/0587_3891b4873/analysis.md) |
| 588 | `71ff8a484` | 2024-03-12 08:44:56 +0100 | Manu Zhang | Docs: Enhance Spark pages (#9920) | ✅ 已完成 | [0588_71ff8a484](commits/0588_71ff8a484/analysis.md) |
| 589 | `43c339752` | 2024-03-12 17:51:03 +0100 | Eduard Tudenhoefner | Build: Align Jackson versions (#9925) | ✅ 已完成 | [0589_43c339752](commits/0589_43c339752/analysis.md) |
| 590 | `732fbfd51` | 2024-03-13 11:43:31 +0100 | Ajantha Bhat | Docs: Update site docs (#9946) | ✅ 已完成 | [0590_732fbfd51](commits/0590_732fbfd51/analysis.md) |
| 591 | `8b5827754` | 2024-03-13 09:54:20 -0700 | Manu Zhang | Docs: Enhance create_changelog_view usage (#9889) | ✅ 已完成 | [0591_8b5827754](commits/0591_8b5827754/analysis.md) |
| 592 | `d6c8358ff` | 2024-03-13 10:48:09 -0700 | ggershinsky | API, Core: Support manifest encryption (#8252) | ✅ 已完成 | [0592_d6c8358ff](commits/0592_d6c8358ff/analysis.md) |
| 593 | `969d4ee60` | 2024-03-14 11:18:09 +0100 | Hongyue/Steve Zhang | Docs: Clarify table property on metrics for inferred column defaults (#9865) | ✅ 已完成 | [0593_969d4ee60](commits/0593_969d4ee60/analysis.md) |
| 594 | `489ec2972` | 2024-03-15 08:26:43 +0100 | Tom Tanaka | Core: Migrate tests to JUnit5 (#9927) | ✅ 已完成 | [0594_489ec2972](commits/0594_489ec2972/analysis.md) |
| 595 | `5e31a0caa` | 2024-03-15 09:41:32 +0100 | Fokko Driesprong | docs: Remove roadmap (#9941) | ✅ 已完成 | [0595_5e31a0caa](commits/0595_5e31a0caa/analysis.md) |
| 596 | `59d79e773` | 2024-03-15 09:50:25 +0100 | Eduard Tudenhoefner | Docs: document view properties (#9961) | ✅ 已完成 | [0596_59d79e773](commits/0596_59d79e773/analysis.md) |
| 597 | `560b72344` | 2024-03-15 15:24:23 +0100 | Gang Wu | Parquet: Refactor BasePageIterator to add initRepetitionLevelsReader (#9751) | ✅ 已完成 | [0597_560b72344](commits/0597_560b72344/analysis.md) |
| 598 | `2dd308778` | 2024-03-18 08:33:55 +0100 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#9972) | ✅ 已完成 | [0598_2dd308778](commits/0598_2dd308778/analysis.md) |
| 599 | `54246a06a` | 2024-03-18 08:35:27 +0100 | dependabot[bot] | Build: Bump org.awaitility:awaitility from 4.2.0 to 4.2.1 (#9970) | ✅ 已完成 | [0599_54246a06a](commits/0599_54246a06a/analysis.md) |
| 600 | `1c5022785` | 2024-03-18 08:37:45 +0100 | Alex Merced | Docs: Add 13 Dremio Blogs + Fix a few incorrect dates (#9967) | ✅ 已完成 | [0600_1c5022785](commits/0600_1c5022785/analysis.md) |
| 601 | `20bd4ca8c` | 2024-03-18 08:39:05 +0100 | Manu Zhang | Build: Fix ignoring major version update in dependabot (#9981) | ✅ 已完成 | [0601_20bd4ca8c](commits/0601_20bd4ca8c/analysis.md) |
| 602 | `82137b9e7` | 2024-03-18 08:40:22 +0100 | dependabot[bot] | Build: Bump nessie from 0.77.1 to 0.79.0 (#9976) | ✅ 已完成 | [0602_82137b9e7](commits/0602_82137b9e7/analysis.md) |
| 603 | `b0a4a907f` | 2024-03-18 08:40:39 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.4 to 0.25.5 (#9979) | ✅ 已完成 | [0603_b0a4a907f](commits/0603_b0a4a907f/analysis.md) |
| 604 | `1bc5c7c35` | 2024-03-18 08:41:20 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.9 to 9.5.14 (#9983) | ✅ 已完成 | [0604_1bc5c7c35](commits/0604_1bc5c7c35/analysis.md) |
| 605 | `f79fb3fc1` | 2024-03-18 09:00:42 +0100 | Tom Tanaka | Core: Migrate tests to JUnit5 (#9964) | ✅ 已完成 | [0605_f79fb3fc1](commits/0605_f79fb3fc1/analysis.md) |
| 606 | `0cdf62f8f` | 2024-03-18 11:02:52 +0100 | dependabot[bot] | Build: Bump spring-boot from 2.5.4 to 2.7.18 (#9985) | ✅ 已完成 | [0606_0cdf62f8f](commits/0606_0cdf62f8f/analysis.md) |
| 607 | `e68795457` | 2024-03-18 11:53:19 +0100 | dependabot[bot] | Build: Bump org.springframework:spring-web from 5.3.30 to 5.3.33 (#9989) | ✅ 已完成 | [0607_e68795457](commits/0607_e68795457/analysis.md) |
| 608 | `a3f887981` | 2024-03-18 11:53:56 +0100 | dependabot[bot] | Build: Bump jetty from 9.4.53.v20231009 to 9.4.54.v20240208 (#9982) | ✅ 已完成 | [0608_a3f887981](commits/0608_a3f887981/analysis.md) |
| 609 | `7a6143abb` | 2024-03-18 14:13:57 +0100 | dependabot[bot] | Build: Bump guava from 33.0.0-jre to 33.1.0-jre (#9977) | ✅ 已完成 | [0609_7a6143abb](commits/0609_7a6143abb/analysis.md) |
| 610 | `f614a3f8b` | 2024-03-18 16:42:14 +0100 | Fokko Driesprong | API: Fix `TestStrictMetricsEvaluator` assertion message (#9992) | ✅ 已完成 | [0610_f614a3f8b](commits/0610_f614a3f8b/analysis.md) |
| 611 | `353e55e24` | 2024-03-19 08:58:10 +0100 | dependabot[bot] | Build: Bump arrow from 15.0.0 to 15.0.1 (#9910) | ✅ 已完成 | [0611_353e55e24](commits/0611_353e55e24/analysis.md) |
| 612 | `f425dc740` | 2024-03-19 17:05:41 +0100 | Eduard Tudenhoefner | AWS, Core: Replace .withFailMessage() usage with .as() (#10000) | ✅ 已完成 | [0612_f425dc740](commits/0612_f425dc740/analysis.md) |
| 613 | `fae0f8140` | 2024-03-20 08:06:38 +0100 | Tom Tanaka | Core: Migrate tests to JUnit5 (#9999) | ✅ 已完成 | [0613_fae0f8140](commits/0613_fae0f8140/analysis.md) |
| 614 | `f8d60ea99` | 2024-03-20 12:01:21 +0100 | Jay Chia | Docs: Add Daft into Iceberg documentation (#9836) | ✅ 已完成 | [0614_f8d60ea99](commits/0614_f8d60ea99/analysis.md) |
| 615 | `aa17c0a30` | 2024-03-20 16:30:19 +0100 | Tom Tanaka | Core: Migrate tests to JUnit5 (#9994) | ✅ 已完成 | [0615_aa17c0a30](commits/0615_aa17c0a30/analysis.md) |
| 616 | `715140113` | 2024-03-20 12:45:58 -0700 | Daniel Weeks | Add issue template and docs for iceberg proposals (#9932) | ✅ 已完成 | [0616_715140113](commits/0616_715140113/analysis.md) |
| 617 | `59ffa33e3` | 2024-03-21 17:50:00 +0100 | Tom Tanaka | Core: Migrate tests to JUnit5 (#10014) | ✅ 已完成 | [0617_59ffa33e3](commits/0617_59ffa33e3/analysis.md) |
| 618 | `e769addf7` | 2024-03-21 14:08:16 -0700 | Bryan Keller | Kafka Connect: Record converters (#9641) | ✅ 已完成 | [0618_e769addf7](commits/0618_e769addf7/analysis.md) |
| 619 | `9cbc2f43c` | 2024-03-22 08:19:29 +0100 | Eduard Tudenhoefner | Core: Use <?> as type parameter instead of raw type for SnapshotUpdate (#10015) | ✅ 已完成 | [0619_9cbc2f43c](commits/0619_9cbc2f43c/analysis.md) |
| 620 | `c9795fda7` | 2024-03-22 08:23:36 +0100 | Brian "bits" Olsen | Docs: Add local nightly build to test current docs changes (#9943) | ✅ 已完成 | [0620_c9795fda7](commits/0620_c9795fda7/analysis.md) |
| 621 | `5d5875084` | 2024-03-22 14:58:14 -0700 | Rahil C | Spec: Fix REST pagination requirements based on new feedback (#9917) | ✅ 已完成 | [0621_5d5875084](commits/0621_5d5875084/analysis.md) |
| 622 | `33838d5a4` | 2024-03-23 11:37:10 +0100 | Fokko Driesprong | docs: Add links checker (#9965) | ✅ 已完成 | [0622_33838d5a4](commits/0622_33838d5a4/analysis.md) |
| 623 | `857590f7f` | 2024-03-24 06:14:58 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.14 to 9.5.15 (#10031) | ✅ 已完成 | [0623_857590f7f](commits/0623_857590f7f/analysis.md) |
| 624 | `8311f052c` | 2024-03-25 09:41:45 +0100 | Alex Merced | Docs: Fix link to blog post (#10028) | ✅ 已完成 | [0624_8311f052c](commits/0624_8311f052c/analysis.md) |
| 625 | `49a66348f` | 2024-03-25 16:23:30 +0100 | Tom Tanaka | Core: Migrate tests to JUnit5 (#10027) | ✅ 已完成 | [0625_49a66348f](commits/0625_49a66348f/analysis.md) |
| 626 | `83bacf501` | 2024-03-25 13:01:42 -0600 | Csenger Geza | Add Iceberg version to UserAgent in S3 requests (#9963) | ✅ 已完成 | [0626_83bacf501](commits/0626_83bacf501/analysis.md) |
| 627 | `602186bed` | 2024-03-25 16:03:33 -0600 | Amogh Jahagirdar | Core, Spark: Fix handling of null binary values when sorting with zorder (#10026) | ✅ 已完成 | [0627_602186bed](commits/0627_602186bed/analysis.md) |
| 628 | `817a5e1be` | 2024-03-26 07:49:22 +0100 | Naveen Kumar | Hive: Extract common code to be re-used for View support (#10001) | ✅ 已完成 | [0628_817a5e1be](commits/0628_817a5e1be/analysis.md) |
| 629 | `2eabd52a8` | 2024-03-26 15:40:56 +0100 | Naveen Kumar | Hive: Add test to make sure iceberg table with same name as hive table can't be created (#9980) | ✅ 已完成 | [0629_2eabd52a8](commits/0629_2eabd52a8/analysis.md) |
| 630 | `b6cbb528e` | 2024-03-26 16:58:48 +0100 | Manu Zhang | Build: Bump Spark from 3.5 to 3.5.1 (#9832) | ✅ 已完成 | [0630_b6cbb528e](commits/0630_b6cbb528e/analysis.md) |
| 631 | `4579b7a1e` | 2024-03-27 00:58:18 -0600 | Eduard Tudenhoefner | Spark: Fail on recursive cycle in view (#9834) | ✅ 已完成 | [0631_4579b7a1e](commits/0631_4579b7a1e/analysis.md) |
| 632 | `fa80c8500` | 2024-03-27 08:27:51 +0100 | Manu Zhang | Build: disable link-check for existing medium blog posts (#10042) | ✅ 已完成 | [0632_fa80c8500](commits/0632_fa80c8500/analysis.md) |
| 633 | `9987314e5` | 2024-03-27 16:59:55 +0100 | Eduard Tudenhoefner | Spark 3.4: Fail on recursive cycle in view (#10048) | ✅ 已完成 | [0633_9987314e5](commits/0633_9987314e5/analysis.md) |
| 634 | `371a6b7ff` | 2024-03-27 17:18:49 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.45.1.0 to 3.45.2.0 (#9974) | ✅ 已完成 | [0634_371a6b7ff](commits/0634_371a6b7ff/analysis.md) |
| 635 | `baaedc6e0` | 2024-03-27 17:19:20 +0100 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.107.Final to 4.1.108.Final (#10032) | ✅ 已完成 | [0635_baaedc6e0](commits/0635_baaedc6e0/analysis.md) |
| 636 | `003cd9477` | 2024-03-27 17:20:00 +0100 | dependabot[bot] | Build: Bump arrow from 15.0.1 to 15.0.2 (#10034) | ✅ 已完成 | [0636_003cd9477](commits/0636_003cd9477/analysis.md) |
| 637 | `15e2a1644` | 2024-03-27 09:33:10 -0700 | dependabot[bot] | Build: Bump kafka from 3.6.1 to 3.7.0 (#9855) | ✅ 已完成 | [0637_15e2a1644](commits/0637_15e2a1644/analysis.md) |
| 638 | `4de819e80` | 2024-03-27 17:35:38 +0100 | dependabot[bot] | Build: Bump orc from 1.9.2 to 1.9.3 (#10033) | ✅ 已完成 | [0638_4de819e80](commits/0638_4de819e80/analysis.md) |
| 639 | `66a0954e4` | 2024-03-27 17:40:26 +0100 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.20 to 1.2.21 (#9857) | ✅ 已完成 | [0639_66a0954e4](commits/0639_66a0954e4/analysis.md) |
| 640 | `bd4603529` | 2024-03-27 20:10:50 +0100 | dependabot[bot] | Build: Bump com.esotericsoftware:kryo from 4.0.2 to 4.0.3 (#9984) | ✅ 已完成 | [0640_bd4603529](commits/0640_bd4603529/analysis.md) |
| 641 | `81b62c78e` | 2024-03-27 13:46:49 -0700 | Steven Zhen Wu | Flink: implement range partitioner for map data statistics (#9321) | ✅ 已完成 | [0641_81b62c78e](commits/0641_81b62c78e/analysis.md) |
| 642 | `8e6c08e35` | 2024-03-28 11:38:26 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.24.5 to 2.25.18 (#10050) | ✅ 已完成 | [0642_8e6c08e35](commits/0642_8e6c08e35/analysis.md) |
| 643 | `783158aca` | 2024-03-28 11:39:25 +0100 | Fokko Driesprong | CI: Run Markdown links checker only when `{docs,site}/**` changes (#10049) | ✅ 已完成 | [0643_783158aca](commits/0643_783158aca/analysis.md) |
| 644 | `4eef2fe82` | 2024-03-28 15:21:31 +0100 | Tom Tanaka | Core, Data: Migrate tests to JUnit5 (#10039) | ✅ 已完成 | [0644_4eef2fe82](commits/0644_4eef2fe82/analysis.md) |
| 645 | `2d76c91d6` | 2024-03-28 16:17:10 +0100 | Manu Zhang | Build: disable link-check for all medium blog posts (#10057) | ✅ 已完成 | [0645_2d76c91d6](commits/0645_2d76c91d6/analysis.md) |
| 646 | `6d6fd0b5e` | 2024-03-29 10:32:01 -0700 | Abid Mohammed | [core] fix #9997 - Handle s3a file upload interrupt which results in table metadata pointing to files that doesn't exist (#9998) | ✅ 已完成 | [0646_6d6fd0b5e](commits/0646_6d6fd0b5e/analysis.md) |
| 647 | `793c8d05c` | 2024-03-30 09:07:45 +0100 | Eduard Tudenhoefner | Spark: Clarify schema behavior when working with branches (#10055) | ✅ 已完成 | [0647_793c8d05c](commits/0647_793c8d05c/analysis.md) |
| 648 | `a86e1b3bb` | 2024-03-30 13:01:28 -0700 | Steven Zhen Wu | Flink: backport PR #9321 for range partitioner on map statistics (#10061) | ✅ 已完成 | [0648_a86e1b3bb](commits/0648_a86e1b3bb/analysis.md) |
| 649 | `d28fcf2f7` | 2024-03-31 12:55:18 +0200 | Eduard Tudenhoefner | Spark: Don't allow branch_ usage with TIMESTAMP AS OF (#10059) | ✅ 已完成 | [0649_d28fcf2f7](commits/0649_d28fcf2f7/analysis.md) |
| 650 | `781282835` | 2024-03-31 23:08:23 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.18 to 2.25.21 (#10072) | ✅ 已完成 | [0650_781282835](commits/0650_781282835/analysis.md) |
| 651 | `a7f87c7e0` | 2024-03-31 23:08:51 +0200 | dependabot[bot] | Build: Bump org.glassfish.jaxb:jaxb-runtime from 2.3.3 to 2.3.9 (#9988) | ✅ 已完成 | [0651_a7f87c7e0](commits/0651_a7f87c7e0/analysis.md) |
| 652 | `ededfcb78` | 2024-04-02 08:42:44 +0200 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.21 to 1.2.22 (#10071) | ✅ 已完成 | [0652_ededfcb78](commits/0652_ededfcb78/analysis.md) |
| 653 | `815b2c649` | 2024-04-02 13:12:29 +0200 | Tom Tanaka | Core, Flink, Spark: Migrate remaining subclasses of TableTestBase to JUnit5 (#10063) | ✅ 已完成 | [0653_815b2c649](commits/0653_815b2c649/analysis.md) |
| 654 | `089c9444c` | 2024-04-03 09:59:13 +0200 | Eduard Tudenhoefner | Build: Ignore link-checking for Blogs / https://search.maven.org/ (#10081) | ✅ 已完成 | [0654_089c9444c](commits/0654_089c9444c/analysis.md) |
| 655 | `ced897ca6` | 2024-04-03 12:28:29 +0200 | Tom Tanaka | Core, Data, Flink: Migrate TableTestBase related classes to JUnit5 (#10080) | ✅ 已完成 | [0655_ced897ca6](commits/0655_ced897ca6/analysis.md) |
| 656 | `c65023b1e` | 2024-04-03 15:27:39 +0200 | lurnagao-dahua | Hive: Avoid NPE on Throwables without error msg (#10069) | ✅ 已完成 | [0656_c65023b1e](commits/0656_c65023b1e/analysis.md) |
| 657 | `319a482aa` | 2024-04-03 16:22:11 +0200 | Alex Merced | Docs: Add 5 dremio blogs (#10067) | ✅ 已完成 | [0657_319a482aa](commits/0657_319a482aa/analysis.md) |
| 658 | `356c6cd30` | 2024-04-04 13:36:24 -0600 | Alexandre Dutra | REST: Fix spurious warning when shutting down refresh executor (#10087) | ✅ 已完成 | [0658_356c6cd30](commits/0658_356c6cd30/analysis.md) |
| 659 | `25c909be9` | 2024-04-04 14:02:50 -0600 | Amogh Jahagirdar | API: Fix default FileIO#newInputFile ManifestFile, DataFile and DeleteFile implementations (#9953) | ✅ 已完成 | [0659_25c909be9](commits/0659_25c909be9/analysis.md) |
| 660 | `07246b10d` | 2024-04-04 22:22:08 +0200 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.19.5 to 1.19.7 (#9912) | ✅ 已完成 | [0660_07246b10d](commits/0660_07246b10d/analysis.md) |
| 661 | `ba1cd36fb` | 2024-04-04 23:32:51 +0200 | Haizhou Zhao | OpenAPI: Fix additionalProperties for SnapshotSummary (#9838) | ✅ 已完成 | [0661_ba1cd36fb](commits/0661_ba1cd36fb/analysis.md) |
| 662 | `fab5e18d0` | 2024-04-05 14:04:03 +0200 | Naveen Kumar | Hive, JDBC: Avoid NPE on Throwables without error msg (#10082) | ✅ 已完成 | [0662_fab5e18d0](commits/0662_fab5e18d0/analysis.md) |
| 663 | `00f46ac09` | 2024-04-05 18:14:05 +0200 | Eduard Tudenhoefner | Core: Introduce ConfigResponseParser (#9952) | ✅ 已完成 | [0663_00f46ac09](commits/0663_00f46ac09/analysis.md) |
| 664 | `abf238abc` | 2024-04-07 13:20:22 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.15 to 9.5.17 (#10092) | ✅ 已完成 | [0664_abf238abc](commits/0664_abf238abc/analysis.md) |
| 665 | `b3261d07f` | 2024-04-08 12:54:50 +0200 | Tom Tanaka | AWS: Migrate tests to JUnit5 (#10086) | ✅ 已完成 | [0665_b3261d07f](commits/0665_b3261d07f/analysis.md) |
| 666 | `cd707394d` | 2024-04-08 15:12:57 -0600 | Brian Hulette | Spec: Document support for binary in truncate transform (#10079) | ✅ 已完成 | [0666_cd707394d](commits/0666_cd707394d/analysis.md) |
| 667 | `a351e22b6` | 2024-04-09 09:33:31 +0200 | Jason | Docs: Add Upsolver to vendor list (#10096) | ✅ 已完成 | [0667_a351e22b6](commits/0667_a351e22b6/analysis.md) |
| 668 | `6f4e9c6a6` | 2024-04-09 09:55:39 +0200 | liko | Docs: Update releases.md for Spark scala versions (#10104) | ✅ 已完成 | [0668_6f4e9c6a6](commits/0668_6f4e9c6a6/analysis.md) |
| 669 | `9bb86fa49` | 2024-04-09 10:16:45 +0200 | bering | Docs: Fix spacing/descriptions on Branching and Tagging DDL (#10091) | ✅ 已完成 | [0669_9bb86fa49](commits/0669_9bb86fa49/analysis.md) |
| 670 | `81bb0d4c9` | 2024-04-09 09:22:32 -0600 | Manu Zhang | Core: Add EnvironmentContext to commit summary (#9273) | ✅ 已完成 | [0670_81bb0d4c9](commits/0670_81bb0d4c9/analysis.md) |
| 671 | `96793bf62` | 2024-04-10 22:35:09 +0200 | Wei Guo | docs: Fix links of `Get Started` and `Community` parts in footer (#10098) | ✅ 已完成 | [0671_96793bf62](commits/0671_96793bf62/analysis.md) |
| 672 | `528b9b336` | 2024-04-11 12:31:41 +0200 | Harish Chandrasekaran | Core: Allow configuring socket/connection timeout in HTTPClient (#10053) | ✅ 已完成 | [0672_528b9b336](commits/0672_528b9b336/analysis.md) |
| 673 | `0bc6dfa1b` | 2024-04-11 14:58:44 -0600 | Harish Chandrasekaran | Core: Extend HTTPClient Builder to allow setting a proxy server (#10052) | ✅ 已完成 | [0673_0bc6dfa1b](commits/0673_0bc6dfa1b/analysis.md) |
| 674 | `290a6a0c5` | 2024-04-11 14:04:13 -0700 | Anton Okolnychyi | Spark 3.4: Fix system function pushdown in CoW row-level commands (#10119) | ✅ 已完成 | [0674_290a6a0c5](commits/0674_290a6a0c5/analysis.md) |
| 675 | `ce7c2c150` | 2024-04-12 17:51:09 +0200 | sullis | API, Core, Kafka, Spark: Reduce enum array allocation (#10126) | ✅ 已完成 | [0675_ce7c2c150](commits/0675_ce7c2c150/analysis.md) |
| 676 | `2025e7990` | 2024-04-12 11:20:22 -0600 | Eduard Tudenhoefner | Spark: Test initialization improvements (#10131) | ✅ 已完成 | [0676_2025e7990](commits/0676_2025e7990/analysis.md) |
| 677 | `1e6665787` | 2024-04-12 11:28:59 -0600 | westse | Spec: Make request bodies required (#10125) | ✅ 已完成 | [0677_1e6665787](commits/0677_1e6665787/analysis.md) |
| 678 | `81b3310ab` | 2024-04-12 14:12:28 -0700 | Yujiang Zhong | Spark 3.5: Support preserving schema nullability in CTAS and RTAS (#10074) | ✅ 已完成 | [0678_81b3310ab](commits/0678_81b3310ab/analysis.md) |
| 679 | `496b32098` | 2024-04-13 21:19:55 -0600 | Eduard Tudenhoefner | Flink, Spark: Replace Boolean.getBoolean() with Boolean.parseBoolean() (#10136) | ✅ 已完成 | [0679_496b32098](commits/0679_496b32098/analysis.md) |
| 680 | `2400aa530` | 2024-04-14 07:16:47 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.14.5 to 3.15.1 (#10095) | ✅ 已完成 | [0680_2400aa530](commits/0680_2400aa530/analysis.md) |
| 681 | `47825ffd8` | 2024-04-14 09:27:53 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.21 to 2.25.31 (#10138) | ✅ 已完成 | [0681_47825ffd8](commits/0681_47825ffd8/analysis.md) |
| 682 | `dd74dd289` | 2024-04-14 09:28:02 +0200 | dependabot[bot] | Build: Bump org.springframework:spring-web from 5.3.33 to 5.3.34 (#10139) | ✅ 已完成 | [0682_dd74dd289](commits/0682_dd74dd289/analysis.md) |
| 683 | `fb657b413` | 2024-04-14 07:44:28 -0600 | Amogh Jahagirdar | Spark: Simplify SparkSchemaUtil#schemaForTable (#10137) | ✅ 已完成 | [0683_fb657b413](commits/0683_fb657b413/analysis.md) |
| 684 | `943321ee6` | 2024-04-15 08:45:45 +0200 | Tom Tanaka | Flink: Migrate tests to JUnit5 (#10130) | ✅ 已完成 | [0684_943321ee6](commits/0684_943321ee6/analysis.md) |
| 685 | `e6a1a4562` | 2024-04-15 13:15:37 +0200 | Manu Zhang | Core: Fix logging table name when scanning metadata table (#10141) | ✅ 已完成 | [0685_e6a1a4562](commits/0685_e6a1a4562/analysis.md) |
| 686 | `d067677df` | 2024-04-15 11:06:10 -0600 | Filipe Regadas | AWS: Close underlying scheduler for DynamoDbLockManager (#10132) | ✅ 已完成 | [0686_d067677df](commits/0686_d067677df/analysis.md) |
| 687 | `78e8204c5` | 2024-04-15 14:06:23 -0700 | Manu Zhang | Spark 3.5: Add threshold for failed commits in data rewrites (#9611) | ✅ 已完成 | [0687_78e8204c5](commits/0687_78e8204c5/analysis.md) |
| 688 | `97c5700ff` | 2024-04-16 07:42:50 +0200 | JB Onofré | Core: Fix JDBC Catalog table commit when migrating from schema V0 to V1 (#10111) | ✅ 已完成 | [0688_97c5700ff](commits/0688_97c5700ff/analysis.md) |
| 689 | `fc5b2b336` | 2024-04-16 09:56:33 +0200 | Eduard Tudenhoefner | Core: Use 'delete' if RowDelta only has delete files (#10123) | ✅ 已完成 | [0689_fc5b2b336](commits/0689_fc5b2b336/analysis.md) |
| 690 | `fbcd142c5` | 2024-04-16 17:37:55 +0200 | Rodrigo Meneses | Flink: Move flink/v1.18 to flink/v1.19 | ✅ 已完成 | [0690_fbcd142c5](commits/0690_fbcd142c5/analysis.md) |
| 691 | `f761d98a1` | 2024-04-16 17:37:55 +0200 | Rodrigo Meneses | Flink: Recover flink/1.18 files from history | ✅ 已完成 | [0691_f761d98a1](commits/0691_f761d98a1/analysis.md) |
| 692 | `b3ebcf109` | 2024-04-16 17:37:55 +0200 | Rodrigo Meneses | Flink: Refactoring code and properties to make Flink 1.19 to work | ✅ 已完成 | [0692_b3ebcf109](commits/0692_b3ebcf109/analysis.md) |
| 693 | `dd194b439` | 2024-04-16 18:10:27 +0200 | Rodrigo | Flink: Removes Flink version 1.16 (#10154) | ✅ 已完成 | [0693_dd194b439](commits/0693_dd194b439/analysis.md) |
| 694 | `0a4e6e6cf` | 2024-04-17 07:11:54 +0200 | Rodrigo | Docs: Updates flink versioning information in our docs (#10155) | ✅ 已完成 | [0694_0a4e6e6cf](commits/0694_0a4e6e6cf/analysis.md) |
| 695 | `c41c599fe` | 2024-04-17 13:43:23 +0200 | Tom Tanaka | Flink: Backport Flink 1.18 JUnit5 migration to Flink 1.17 (#10163) | ✅ 已完成 | [0695_c41c599fe](commits/0695_c41c599fe/analysis.md) |
| 696 | `928888b57` | 2024-04-17 18:32:10 +0200 | c-thiel | OpenAPI: Renaming views should return 204 (#10166) | ✅ 已完成 | [0696_928888b57](commits/0696_928888b57/analysis.md) |
| 697 | `228fc9b41` | 2024-04-17 12:01:24 -0600 | JB Onofré | Core: Fix namespace SQL statement using ESCAPE character that works with MySQL/PostgreSQL (#10167) | ✅ 已完成 | [0697_228fc9b41](commits/0697_228fc9b41/analysis.md) |
| 698 | `8136463bd` | 2024-04-18 11:23:57 +0200 | Ahmet DAL | Flink: Don't fail to serialize IcebergSourceSplit when there is too many delete files (#9464) | ✅ 已完成 | [0698_8136463bd](commits/0698_8136463bd/analysis.md) |
| 699 | `1f8cad3c7` | 2024-04-18 12:46:28 -0700 | Elkhan Dadash | Flink: port #9464 to v1.17 and v1.19 (#10177) | ✅ 已完成 | [0699_1f8cad3c7](commits/0699_1f8cad3c7/analysis.md) |
| 700 | `efa14bfb2` | 2024-04-19 15:20:44 +0200 | Eduard Tudenhoefner | Core: Improve size check in CatalogTests (#10182) | ✅ 已完成 | [0700_efa14bfb2](commits/0700_efa14bfb2/analysis.md) |
| 701 | `ed2d0410c` | 2024-04-19 10:43:46 -0600 | Ajantha Bhat | Kafka-connect: Update iceberg.hadoop-conf-dir config description (#10184) | ✅ 已完成 | [0701_ed2d0410c](commits/0701_ed2d0410c/analysis.md) |
| 702 | `3ed04c16e` | 2024-04-21 17:09:48 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.31 to 2.25.35 (#10192) | ✅ 已完成 | [0702_3ed04c16e](commits/0702_3ed04c16e/analysis.md) |
| 703 | `e468d02e4` | 2024-04-21 17:10:08 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.108.Final to 4.1.109.Final (#10191) | ✅ 已完成 | [0703_e468d02e4](commits/0703_e468d02e4/analysis.md) |
| 704 | `9664940ae` | 2024-04-21 17:10:26 +0200 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.5 to 1.0.6 (#10190) | ✅ 已完成 | [0704_9664940ae](commits/0704_9664940ae/analysis.md) |
| 705 | `2510ef861` | 2024-04-21 17:10:37 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.17 to 9.5.18 (#10189) | ✅ 已完成 | [0705_2510ef861](commits/0705_2510ef861/analysis.md) |
| 706 | `4261e18b7` | 2024-04-21 17:10:52 +0200 | dependabot[bot] | Build: Bump gradle.plugin.io.morethan.jmhreport:gradle-jmh-report (#10193) | ✅ 已完成 | [0706_4261e18b7](commits/0706_4261e18b7/analysis.md) |
| 707 | `a23021d05` | 2024-04-22 09:42:18 +0200 | Eduard Tudenhoefner | Core: Lazily compute & cache hashCode in CharSequenceWrapper (#10023) | ✅ 已完成 | [0707_a23021d05](commits/0707_a23021d05/analysis.md) |
| 708 | `e3b78be9a` | 2024-04-22 08:09:16 -0600 | Eduard Tudenhoefner | AWS: Make sure Signer + User Agent config are both applied (#10198) | ✅ 已完成 | [0708_e3b78be9a](commits/0708_e3b78be9a/analysis.md) |
| 709 | `866021d7d` | 2024-04-23 12:21:20 +0200 | Hanzhi Wang | Hive: turn off the stats gathering when iceberg.hive.keep.stats is false (#10148) | ✅ 已完成 | [0709_866021d7d](commits/0709_866021d7d/analysis.md) |
| 710 | `34e181b28` | 2024-04-24 17:24:52 +0200 | Eduard Tudenhoefner | Docs: Don't check links on Release page (#10212) | ✅ 已完成 | [0710_34e181b28](commits/0710_34e181b28/analysis.md) |
| 711 | `bfe0daadd` | 2024-04-24 10:24:12 -0600 | Fokko Driesprong | Docs: Use `svn mv` when releasing the binaries (#9926) | ✅ 已完成 | [0711_bfe0daadd](commits/0711_bfe0daadd/analysis.md) |
| 712 | `53261312d` | 2024-04-24 21:11:21 +0200 | Amogh Jahagirdar | Infra: Add 1.5.1 to issue template (#10214) | ✅ 已完成 | [0712_53261312d](commits/0712_53261312d/analysis.md) |
| 713 | `0f11f54c4` | 2024-04-24 21:37:03 +0200 | Amogh Jahagirdar | Update site to 1.5.1 docs (#10218) | ✅ 已完成 | [0713_0f11f54c4](commits/0713_0f11f54c4/analysis.md) |
| 714 | `f460964e7` | 2024-04-25 11:21:28 +0200 | Eduard Tudenhoefner | Core: Use 'delete' / 'append' if OverwriteFiles only deletes/appends data files (#10150) | ✅ 已完成 | [0714_f460964e7](commits/0714_f460964e7/analysis.md) |
| 715 | `837a4aab3` | 2024-04-25 13:38:23 +0200 | Akira Ajisaka | AWS: Fix TestGlueCatalogTable#testCreateTable (#10221) | ✅ 已完成 | [0715_837a4aab3](commits/0715_837a4aab3/analysis.md) |
| 716 | `10ffc6062` | 2024-04-25 18:09:29 +0200 | Amogh Jahagirdar | Docs: Add 1.5.1 release notes (#10224) | ✅ 已完成 | [0716_10ffc6062](commits/0716_10ffc6062/analysis.md) |
| 717 | `5821efcdd` | 2024-04-26 08:50:30 +0200 | Fokko Driesprong | Spec: Clarify missing fields when writing (#8672) | ✅ 已完成 | [0717_5821efcdd](commits/0717_5821efcdd/analysis.md) |
| 718 | `c9f775b80` | 2024-04-26 08:50:48 +0200 | Fokko Driesprong | Flink: Move ParquetReader to LogicalTypeAnnotationVisitor (#9719) | ✅ 已完成 | [0718_c9f775b80](commits/0718_c9f775b80/analysis.md) |
| 719 | `b7d3a7f9d` | 2024-04-26 12:30:16 +0200 | pvary | Flink: Fix bounded source state restore record duplication (#10208) | ✅ 已完成 | [0719_b7d3a7f9d](commits/0719_b7d3a7f9d/analysis.md) |
| 720 | `c9eed4381` | 2024-04-26 15:46:34 +0200 | Alexandre Dutra | REST: fix incorrect token refresh thread name (#10223) | ✅ 已完成 | [0720_c9eed4381](commits/0720_c9eed4381/analysis.md) |
| 721 | `21c0ec491` | 2024-04-26 15:51:37 +0200 | pvary | Flink: Backport #10208 to v1.18 and v1.17 (#10230) | ✅ 已完成 | [0721_21c0ec491](commits/0721_21c0ec491/analysis.md) |
| 722 | `646440abf` | 2024-04-26 19:19:13 +0200 | pvary | Flink: Prevent setting endTag/endSnapshotId for streaming source (#10207) | ✅ 已完成 | [0722_646440abf](commits/0722_646440abf/analysis.md) |
| 723 | `1e35bf96e` | 2024-04-27 06:44:39 -0700 | pvary | Flink: Backport #10207 to v1.18 and v1.17 (#10235) | ✅ 已完成 | [0723_1e35bf96e](commits/0723_1e35bf96e/analysis.md) |
| 724 | `01bc864b8` | 2024-04-28 08:04:31 +0200 | dependabot[bot] | Build: Bump nessie from 0.79.0 to 0.80.0 (#10237) | ✅ 已完成 | [0724_01bc864b8](commits/0724_01bc864b8/analysis.md) |
| 725 | `a0a6bcfe5` | 2024-04-29 08:45:08 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.35 to 2.25.40 (#10240) | ✅ 已完成 | [0725_a0a6bcfe5](commits/0725_a0a6bcfe5/analysis.md) |
| 726 | `9310bd482` | 2024-04-29 08:45:27 +0200 | Ajantha Bhat | Spark: Bump minor version for Spark-3.4 (#10243) | ✅ 已完成 | [0726_9310bd482](commits/0726_9310bd482/analysis.md) |
| 727 | `6016110d9` | 2024-04-29 08:45:52 +0200 | dependabot[bot] | Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#10239) | ✅ 已完成 | [0727_6016110d9](commits/0727_6016110d9/analysis.md) |
| 728 | `a55797d4a` | 2024-04-29 08:46:13 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#10236) | ✅ 已完成 | [0728_a55797d4a](commits/0728_a55797d4a/analysis.md) |
| 729 | `426818bfe` | 2024-04-29 12:20:15 +0200 | Marc Cenac | Core: Add property to disable table initialization for JdbcCatalog (#10124) | ✅ 已完成 | [0729_426818bfe](commits/0729_426818bfe/analysis.md) |
| 730 | `6f0d9dd47` | 2024-04-30 12:23:55 +0200 | Tom Tanaka | Flink: Migrate tests to JUnit5 (#10232) | ✅ 已完成 | [0730_6f0d9dd47](commits/0730_6f0d9dd47/analysis.md) |
| 731 | `96268505b` | 2024-04-30 14:45:37 +0200 | JB Onofré | Release: add instruction to update doap.rdf file as part of release process (#9655) | ✅ 已完成 | [0731_96268505b](commits/0731_96268505b/analysis.md) |
| 732 | `5aa0d3bf2` | 2024-04-30 16:14:39 +0200 | JB Onofré | Add stale PRs management (#10134) | ✅ 已完成 | [0732_5aa0d3bf2](commits/0732_5aa0d3bf2/analysis.md) |
| 733 | `e785aa7fa` | 2024-04-30 16:43:04 +0200 | Ajantha Bhat | Docs: Update doap.rdf (#10255) | ✅ 已完成 | [0733_e785aa7fa](commits/0733_e785aa7fa/analysis.md) |
| 734 | `e85884d26` | 2024-04-30 21:35:12 +0200 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.22 to 1.2.23 (#10238) | ✅ 已完成 | [0734_e85884d26](commits/0734_e85884d26/analysis.md) |
| 735 | `839f71c00` | 2024-04-30 21:35:27 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.18 to 9.5.19 (#10241) | ✅ 已完成 | [0735_839f71c00](commits/0735_839f71c00/analysis.md) |
| 736 | `839609771` | 2024-04-30 21:35:35 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.5 to 0.25.6 (#10242) | ✅ 已完成 | [0736_839609771](commits/0736_839609771/analysis.md) |
| 737 | `175757793` | 2024-04-30 22:52:48 +0200 | pvary | Flink: Apply DeleteGranularity for writes (#10200) | ✅ 已完成 | [0737_175757793](commits/0737_175757793/analysis.md) |
| 738 | `aeb26820a` | 2024-05-01 17:11:29 +0200 | Fokko Driesprong | Hive: Remove deprecated `setSchema(TableMetadata, Map<String, String>)` (#10257) | ✅ 已完成 | [0738_aeb26820a](commits/0738_aeb26820a/analysis.md) |
| 739 | `032330856` | 2024-05-01 20:46:38 +0200 | pvary | Flink: Backport #10200 to v1.19 and v1.17 (#10259) | ✅ 已完成 | [0739_032330856](commits/0739_032330856/analysis.md) |
| 740 | `7600ba74f` | 2024-05-03 08:35:38 +0200 | Rahil C | Core: Add pagination when listing namespaces/tables/views (#9782) | ✅ 已完成 | [0740_7600ba74f](commits/0740_7600ba74f/analysis.md) |
| 741 | `51061511e` | 2024-05-03 11:54:24 +0200 | Sourabh Badhya | Docs: Update features for Hive 4.0 (#10162) | ✅ 已完成 | [0741_51061511e](commits/0741_51061511e/analysis.md) |
| 742 | `be305b291` | 2024-05-03 12:27:50 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.45.2.0 to 3.45.3.0 (#10194) | ✅ 已完成 | [0742_be305b291](commits/0742_be305b291/analysis.md) |
| 743 | `9cd5977e4` | 2024-05-05 11:46:16 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.19 to 9.5.21 (#10272) | ✅ 已完成 | [0743_9cd5977e4](commits/0743_9cd5977e4/analysis.md) |
| 744 | `ed84ea004` | 2024-05-06 10:41:09 +0200 | Manu Zhang | docs: Remove link to Flink unit test (#10160) | ✅ 已完成 | [0744_ed84ea004](commits/0744_ed84ea004/analysis.md) |
| 745 | `2857d3a92` | 2024-05-06 10:42:01 +0200 | dependabot[bot] | Build: Bump nessie from 0.80.0 to 0.81.1 (#10267) | ✅ 已完成 | [0745_2857d3a92](commits/0745_2857d3a92/analysis.md) |
| 746 | `ed0959257` | 2024-05-07 15:31:22 +0200 | lurnagao-dahua | MR: Fix using Date type as partition field (#10210) | ✅ 已完成 | [0746_ed0959257](commits/0746_ed0959257/analysis.md) |
| 747 | `a5b85a737` | 2024-05-09 15:01:18 +0200 | Amogh Jahagirdar | Docs: Update site to 1.5.2 docs (#10291) | ✅ 已完成 | [0747_a5b85a737](commits/0747_a5b85a737/analysis.md) |
| 748 | `5d3d647ea` | 2024-05-09 15:21:39 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.40 to 2.25.45 (#10266) | ✅ 已完成 | [0748_5d3d647ea](commits/0748_5d3d647ea/analysis.md) |
| 749 | `e6586e947` | 2024-05-09 11:42:37 -0600 | Amogh Jahagirdar | Infra: Add Iceberg 1.5.2 to issue template (#10296) | ✅ 已完成 | [0749_e6586e947](commits/0749_e6586e947/analysis.md) |
| 750 | `b187b17f3` | 2024-05-09 11:43:35 -0600 | Amogh Jahagirdar | Update doap.rdf for 1.5.2 release (#10297) | ✅ 已完成 | [0750_b187b17f3](commits/0750_b187b17f3/analysis.md) |
| 751 | `e10098b9a` | 2024-05-09 14:06:31 -0600 | Amogh Jahagirdar | Docs: Add release notes for 1.5.2 (#10295) | ✅ 已完成 | [0751_e10098b9a](commits/0751_e10098b9a/analysis.md) |
| 752 | `3c8e04697` | 2024-05-09 18:11:54 -0600 | Dustin Metzgar | Spec: Fix markdown for struct evolution default value rules (#10290) | ✅ 已完成 | [0752_3c8e04697](commits/0752_3c8e04697/analysis.md) |
| 753 | `2b21020ae` | 2024-05-10 11:18:46 -0600 | Amogh Jahagirdar | Core: Retry connections in JDBC catalog with user configured error code list (#10140) | ✅ 已完成 | [0753_2b21020ae](commits/0753_2b21020ae/analysis.md) |
| 754 | `e484f0d71` | 2024-05-11 10:05:23 +0200 | dependabot[bot] | Build: Bump guava from 33.1.0-jre to 33.2.0-jre (#10271) | ✅ 已完成 | [0754_e484f0d71](commits/0754_e484f0d71/analysis.md) |
| 755 | `04792cf99` | 2024-05-11 09:55:09 -0700 | Anton Okolnychyi | Spark 3.5: Remove obsolete conf parsing logic (#10309) | ✅ 已完成 | [0755_04792cf99](commits/0755_04792cf99/analysis.md) |
| 756 | `485ce3470` | 2024-05-13 04:41:07 +0200 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.19.7 to 1.19.8 (#10322) | ✅ 已完成 | [0756_485ce3470](commits/0756_485ce3470/analysis.md) |
| 757 | `b752b742e` | 2024-05-13 08:54:32 +0200 | 911432 | docs: Update Quickstart to Hive 4.0.0 (#10325) | ✅ 已完成 | [0757_b752b742e](commits/0757_b752b742e/analysis.md) |
| 758 | `d0dbc9cba` | 2024-05-13 14:29:06 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.45 to 2.25.50 (#10323) | ✅ 已完成 | [0758_d0dbc9cba](commits/0758_d0dbc9cba/analysis.md) |
| 759 | `b6236302c` | 2024-05-13 12:47:51 -0700 | Huaxin Gao | Parquet: Add Bloom filter FPP config (#10149) | ✅ 已完成 | [0759_b6236302c](commits/0759_b6236302c/analysis.md) |
| 760 | `02b1ff968` | 2024-05-13 14:42:01 -0700 | Huaxin Gao | Spark 3.5: Add support for enums in SparkConfParser (#10311) | ✅ 已完成 | [0760_02b1ff968](commits/0760_02b1ff968/analysis.md) |
| 761 | `d23c4902e` | 2024-05-14 09:10:43 +0200 | Amogh Jahagirdar | Spark: Backport tests for struct aggregation pushdown to 3.3/3.4, cleanup assertion (#10333) | ✅ 已完成 | [0761_d23c4902e](commits/0761_d23c4902e/analysis.md) |
| 762 | `ea916c170` | 2024-05-14 09:40:09 +0200 | Andrew Sherman | Docs: Update vendor information for Cloudera (#10278) | ✅ 已完成 | [0762_ea916c170](commits/0762_ea916c170/analysis.md) |
| 763 | `5e08f886f` | 2024-05-14 10:29:24 -0700 | Yufei Gu | Make proxy endpoint configurable for s3 Http clients (#10332) | ✅ 已完成 | [0763_5e08f886f](commits/0763_5e08f886f/analysis.md) |
| 764 | `a6fb9cd2d` | 2024-05-14 11:00:17 -0700 | Huaxin Gao | Spark 3.4: Add support for enums in SparkConfParser (#10330) | ✅ 已完成 | [0764_a6fb9cd2d](commits/0764_a6fb9cd2d/analysis.md) |
| 765 | `2058053b0` | 2024-05-14 18:08:47 -0600 | Akira Ajisaka | AWS: Retain Glue Catalog table description after updating Iceberg table (#10199) | ✅ 已完成 | [0765_2058053b0](commits/0765_2058053b0/analysis.md) |
| 766 | `4c9f47d20` | 2024-05-15 12:14:20 -0700 | Ajantha Bhat | Kafka-connect: Handle namespace creation for auto table creation (#10186) | ✅ 已完成 | [0766_4c9f47d20](commits/0766_4c9f47d20/analysis.md) |
| 767 | `2cd6d0d47` | 2024-05-15 14:11:53 -0700 | Yufei Gu | Avoid adding a closed client to the pool (#10337) | ✅ 已完成 | [0767_2cd6d0d47](commits/0767_2cd6d0d47/analysis.md) |
| 768 | `090fe2eca` | 2024-05-16 14:05:19 +0200 | dependabot[bot] | Build: Bump nessie from 0.81.1 to 0.82.0 (#10318) | ✅ 已完成 | [0768_090fe2eca](commits/0768_090fe2eca/analysis.md) |
| 769 | `788bea269` | 2024-05-16 14:05:38 +0200 | dongwang | Spark 3.5: Fix the setting of equalAuthorities in RemoveOrphanFilesProcedure (#10334) | ✅ 已完成 | [0769_788bea269](commits/0769_788bea269/analysis.md) |
| 770 | `f31315e93` | 2024-05-16 16:18:59 +0200 | Marcos Vinícius da Silva | Docs: Fix Apache Doris documentation link (#10263) | ✅ 已完成 | [0770_f31315e93](commits/0770_f31315e93/analysis.md) |
| 771 | `f4aaa375e` | 2024-05-16 17:18:00 +0200 | dependabot[bot] | Build: Bump io.delta:delta-spark_2.12 from 3.1.0 to 3.2.0 (#10320) | ✅ 已完成 | [0771_f4aaa375e](commits/0771_f4aaa375e/analysis.md) |
| 772 | `139721fee` | 2024-05-16 10:21:49 -0600 | Amogh Jahagirdar | Remove unused manifest predicate (#10339) | ✅ 已完成 | [0772_139721fee](commits/0772_139721fee/analysis.md) |
| 773 | `bd046f844` | 2024-05-16 18:48:58 +0200 | Eduard Tudenhoefner | Spark: Fix issue when partitioning by UUID (#8250) | ✅ 已完成 | [0773_bd046f844](commits/0773_bd046f844/analysis.md) |
| 774 | `6abb99f0a` | 2024-05-17 09:30:28 +0200 | dongwang | Spark 3.4, 3.3: Fix the setting of equalAuthorities in RemoveOrphanFilesProcedure (#10342) | ✅ 已完成 | [0774_6abb99f0a](commits/0774_6abb99f0a/analysis.md) |
| 775 | `2a68edc04` | 2024-05-17 09:32:00 -0700 | Farooq Qaiser | Use a unique field-id for delete files elements (#10347) | ✅ 已完成 | [0775_2a68edc04](commits/0775_2a68edc04/analysis.md) |
| 776 | `2886ef4bf` | 2024-05-17 10:20:57 -0700 | Anton Okolnychyi | Core, Spark 3.4: Remove redundant output in tests (#10348) | ✅ 已完成 | [0776_2886ef4bf](commits/0776_2886ef4bf/analysis.md) |
| 777 | `236f6255b` | 2024-05-18 10:50:04 -0600 | Amogh Jahagirdar | Core: Replace deprecated Roaring64Bitmap#add call with addRange (#10350) | ✅ 已完成 | [0777_236f6255b](commits/0777_236f6255b/analysis.md) |
| 778 | `8d6bee736` | 2024-05-19 18:17:36 -0700 | Shardul Mahadik | Spark: Coerce shorts and bytes into ints in Parquet Writer (#10349) | ✅ 已完成 | [0778_8d6bee736](commits/0778_8d6bee736/analysis.md) |
| 779 | `fcd07d91a` | 2024-05-23 09:17:55 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.21 to 9.5.23 (#10353) | ✅ 已完成 | [0779_fcd07d91a](commits/0779_fcd07d91a/analysis.md) |
| 780 | `f1a548f97` | 2024-05-23 09:18:11 +0200 | dependabot[bot] | Build: Bump org.springframework:spring-web from 5.3.34 to 5.3.35 (#10354) | ✅ 已完成 | [0780_f1a548f97](commits/0780_f1a548f97/analysis.md) |
| 781 | `b3c25fb76` | 2024-05-23 09:18:20 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.50 to 2.25.57 (#10367) | ✅ 已完成 | [0781_b3c25fb76](commits/0781_b3c25fb76/analysis.md) |
| 782 | `9114cc87a` | 2024-05-23 21:23:24 +0200 | Rui Li | Hive: Use base table metadata to create HiveLock (#10016) | ✅ 已完成 | [0782_9114cc87a](commits/0782_9114cc87a/analysis.md) |
| 783 | `dd2197f83` | 2024-05-23 12:48:33 -0700 | Joshua Kolash | API: Fix aggregate pushdown when optional DataFile stats are null (#10273) | ✅ 已完成 | [0783_dd2197f83](commits/0783_dd2197f83/analysis.md) |
| 784 | `d4c2ef895` | 2024-05-24 08:59:02 -0700 | Anton Okolnychyi | Spark 3.5: Support camel case session configs and options (#10310) | ✅ 已完成 | [0784_d4c2ef895](commits/0784_d4c2ef895/analysis.md) |
| 785 | `311dbbb10` | 2024-05-24 09:23:02 -0700 | Akira Ajisaka | AWS: Support S3 DSSE-KMS encryption (#8370) | ✅ 已完成 | [0785_311dbbb10](commits/0785_311dbbb10/analysis.md) |
| 786 | `af9b9ee8a` | 2024-05-27 09:44:49 +0200 | Manu Zhang | Docs: add metrics-reporting back (#10377) | ✅ 已完成 | [0786_af9b9ee8a](commits/0786_af9b9ee8a/analysis.md) |
| 787 | `2a35e2396` | 2024-05-27 09:48:58 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.57 to 2.25.60 (#10385) | ✅ 已完成 | [0787_2a35e2396](commits/0787_2a35e2396/analysis.md) |
| 788 | `9a3db37d3` | 2024-05-27 12:30:43 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.109.Final to 4.1.110.Final (#10384) | ✅ 已完成 | [0788_9a3db37d3](commits/0788_9a3db37d3/analysis.md) |
| 789 | `ca8af31f7` | 2024-05-27 12:31:07 +0200 | dependabot[bot] | Build: Bump io.airlift:aircompressor from 0.26 to 0.27 (#10383) | ✅ 已完成 | [0789_ca8af31f7](commits/0789_ca8af31f7/analysis.md) |
| 790 | `957cb0d67` | 2024-05-27 12:31:27 +0200 | dependabot[bot] | Build: Bump org.springframework:spring-web from 5.3.35 to 5.3.36 (#10382) | ✅ 已完成 | [0790_957cb0d67](commits/0790_957cb0d67/analysis.md) |
| 791 | `6f4b19516` | 2024-05-27 16:21:56 +0200 | Robert Stupp | Prevent deadlock in Jackson (#10379) | ✅ 已完成 | [0791_6f4b19516](commits/0791_6f4b19516/analysis.md) |
| 792 | `580df62d4` | 2024-05-27 16:22:12 +0200 | dependabot[bot] | Build: Bump nessie from 0.82.0 to 0.83.2 (#10381) | ✅ 已完成 | [0792_580df62d4](commits/0792_580df62d4/analysis.md) |
| 793 | `795fea944` | 2024-05-27 14:52:29 -0700 | Daniel Weeks | Url encode field names for partition paths (#10329) | ✅ 已完成 | [0793_795fea944](commits/0793_795fea944/analysis.md) |
| 794 | `f9cdde251` | 2024-05-28 09:55:57 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.15.1 to 3.16.0 (#10269) | ✅ 已完成 | [0794_f9cdde251](commits/0794_f9cdde251/analysis.md) |
| 795 | `d723f9fc6` | 2024-05-29 23:20:28 -0700 | Manu Zhang | Spark 3.5: Only traverse ancestors of current snapshot when building changelog scan (#10252) | ✅ 已完成 | [0795_d723f9fc6](commits/0795_d723f9fc6/analysis.md) |
| 796 | `2843f3233` | 2024-05-30 10:27:11 +0200 | Manu Zhang | docs: Add archive for documentations older than 1.4.0 (#10374) | ✅ 已完成 | [0796_2843f3233](commits/0796_2843f3233/analysis.md) |
| 797 | `46732b876` | 2024-05-30 17:41:40 +0200 | pvary | Flink 1.19: Fix flaky TestIcebergSourceFailover > testBoundedWithSavepoint (#10393) | ✅ 已完成 | [0797_46732b876](commits/0797_46732b876/analysis.md) |
| 798 | `2722290a7` | 2024-05-30 20:50:47 +0200 | Manu Zhang | docs: deploy on changes in `docs/` (#10394) | ✅ 已完成 | [0798_2722290a7](commits/0798_2722290a7/analysis.md) |
| 799 | `6a594546b` | 2024-06-01 09:12:54 -0700 | Manu Zhang | Spark 3.4: Only traverse ancestors of current snapshot when building changelog scan (#10405) | ✅ 已完成 | [0799_6a594546b](commits/0799_6a594546b/analysis.md) |
| 800 | `23eb5941c` | 2024-06-02 21:44:00 +0200 | Fokko Driesprong | Bump Azurite test-container to `3.30.0` | ✅ 已完成 | [0800_23eb5941c](commits/0800_23eb5941c/analysis.md) |
| 801 | `b4ffbf4f1` | 2024-06-02 21:45:01 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.23 to 9.5.25 (#10413) | ✅ 已完成 | [0801_b4ffbf4f1](commits/0801_b4ffbf4f1/analysis.md) |
| 802 | `1837c8175` | 2024-06-03 08:03:11 +0200 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.25.3 to 3.26.0 (#10416) | ✅ 已完成 | [0802_1837c8175](commits/0802_1837c8175/analysis.md) |
| 803 | `ee11de91c` | 2024-06-03 08:09:13 +0200 | dependabot[bot] | Build: Bump guava from 33.2.0-jre to 33.2.1-jre (#10414) | ✅ 已完成 | [0803_ee11de91c](commits/0803_ee11de91c/analysis.md) |
| 804 | `2dfc0c66f` | 2024-06-03 08:09:39 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.45.3.0 to 3.46.0.0 (#10415) | ✅ 已完成 | [0804_2dfc0c66f](commits/0804_2dfc0c66f/analysis.md) |
| 805 | `252168419` | 2024-06-03 08:11:33 +0200 | Fokko Driesprong | Docs: Refer to the README.md in `site/` for the docs (#10402) | ✅ 已完成 | [0805_252168419](commits/0805_252168419/analysis.md) |
| 806 | `7d75f823a` | 2024-06-03 14:36:06 +0200 | Fokko Driesprong | Build: Require approving review (#10424) | ✅ 已完成 | [0806_7d75f823a](commits/0806_7d75f823a/analysis.md) |
| 807 | `134345dd2` | 2024-06-03 16:49:40 +0200 | advancedxy | Parquet: Remove TestHelpers in parquet module (#10428) | ✅ 已完成 | [0807_134345dd2](commits/0807_134345dd2/analysis.md) |
| 808 | `67e181ea9` | 2024-06-03 08:58:16 -0600 | Eduard Tudenhoefner | Core: Introduce AuthConfig (#10161) | ✅ 已完成 | [0808_67e181ea9](commits/0808_67e181ea9/analysis.md) |
| 809 | `45bdf3fd4` | 2024-06-04 08:22:28 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.60 to 2.25.64 (#10421) | ✅ 已完成 | [0809_45bdf3fd4](commits/0809_45bdf3fd4/analysis.md) |
| 810 | `ab476abfd` | 2024-06-04 08:23:04 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#10418) | ✅ 已完成 | [0810_ab476abfd](commits/0810_ab476abfd/analysis.md) |
| 811 | `40da6f1e4` | 2024-06-04 12:32:07 +0200 | Eduard Tudenhoefner | Core: Use TestTemplate instead of Test annotation in TestPartitionSpecParser/Info (#10435) | ✅ 已完成 | [0811_40da6f1e4](commits/0811_40da6f1e4/analysis.md) |
| 812 | `0a26f0287` | 2024-06-04 12:52:10 +0200 | Manu Zhang | Docs: Point links in metrics-reporting.md to GitHub Java source (#10397) | ✅ 已完成 | [0812_0a26f0287](commits/0812_0a26f0287/analysis.md) |
| 813 | `a642a9350` | 2024-06-05 09:22:22 -0600 | Eduard Tudenhoefner | Build: Clean up Jackson dependency usages (#10448) | ✅ 已完成 | [0813_a642a9350](commits/0813_a642a9350/analysis.md) |
| 814 | `cbe391d1f` | 2024-06-05 10:00:45 -0700 | Steven Zhen Wu | Flink: refactor sink shuffling statistics collection  (#10331) | ✅ 已完成 | [0814_cbe391d1f](commits/0814_cbe391d1f/analysis.md) |
| 815 | `59e937761` | 2024-06-05 10:31:49 -0700 | Huaxin Gao | Spark 3.4, 3.5: SHOW VIEWS failed with AssertionError (#10442) | ✅ 已完成 | [0815_59e937761](commits/0815_59e937761/analysis.md) |
| 816 | `be46d29d4` | 2024-06-05 12:14:00 -0600 | Amogh Jahagirdar | Core, Parquet, Orc: Don't write column sizes when metrics mode is None (#10440) | ✅ 已完成 | [0816_be46d29d4](commits/0816_be46d29d4/analysis.md) |
| 817 | `afc30818b` | 2024-06-05 18:32:45 -0700 | Huaxin Gao | Spark 3.4, 3.5: Follow-up for #10442, Remove static test import (#10451) | ✅ 已完成 | [0817_afc30818b](commits/0817_afc30818b/analysis.md) |
| 818 | `c7d3ef443` | 2024-06-06 09:51:25 +0200 | pvary | Flink: Maintenance - MonitorSource (#10308) | ✅ 已完成 | [0818_c7d3ef443](commits/0818_c7d3ef443/analysis.md) |
| 819 | `e0dc57e33` | 2024-06-06 16:12:05 -0700 | Anurag Mantripragada | Open-API: Use union instead of inheritance for TableRequirements (#10434) | ✅ 已完成 | [0819_e0dc57e33](commits/0819_e0dc57e33/analysis.md) |
| 820 | `c7de6cb34` | 2024-06-06 18:39:06 -0600 | Ajantha Bhat | Core: Reword exception message in RewriteManifests validation (#10446) | ✅ 已完成 | [0820_c7de6cb34](commits/0820_c7de6cb34/analysis.md) |
| 821 | `75b3a052a` | 2024-06-09 19:17:46 +0200 | dependabot[bot] | Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#10468) | ✅ 已完成 | [0821_75b3a052a](commits/0821_75b3a052a/analysis.md) |
| 822 | `5a0372c18` | 2024-06-09 20:56:32 +0200 | Fokko Driesprong | Build: Remove links checker (#10404) | ✅ 已完成 | [0822_5a0372c18](commits/0822_5a0372c18/analysis.md) |
| 823 | `bab547474` | 2024-06-10 07:34:28 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.64 to 2.25.69 (#10466) | ✅ 已完成 | [0823_bab547474](commits/0823_bab547474/analysis.md) |
| 824 | `2a754486f` | 2024-06-11 03:36:47 -0700 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.23 to 1.2.24 (#10420) | ✅ 已完成 | [0824_2a754486f](commits/0824_2a754486f/analysis.md) |
| 825 | `fa95e1277` | 2024-06-11 03:40:06 -0700 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.25 to 9.5.26 (#10464) | ✅ 已完成 | [0825_fa95e1277](commits/0825_fa95e1277/analysis.md) |
| 826 | `282fa73b2` | 2024-06-11 05:04:06 -0700 | Piotr Findeisen | Pin 3rd party CI action version (#10481) | ✅ 已完成 | [0826_282fa73b2](commits/0826_282fa73b2/analysis.md) |
| 827 | `bdd6225b6` | 2024-06-12 06:14:20 -0700 | dependabot[bot] | Build: Bump io.delta:delta-standalone_2.12 from 3.1.0 to 3.2.0 (#10321) | ✅ 已完成 | [0827_bdd6225b6](commits/0827_bdd6225b6/analysis.md) |
| 828 | `b6c949cd8` | 2024-06-12 16:50:17 -0700 | Szehon Ho | Core, Spark: Calling rewrite_position_delete_files fails on tables with more than 1k columns (#10020) | ✅ 已完成 | [0828_b6c949cd8](commits/0828_b6c949cd8/analysis.md) |
| 829 | `74cf6977b` | 2024-06-14 02:55:54 -0700 | Alexandre Dutra | Build: Bump Nessie to 0.90.4 (#10492) | ✅ 已完成 | [0829_74cf6977b](commits/0829_74cf6977b/analysis.md) |
| 830 | `a02b55182` | 2024-06-14 22:04:21 +0200 | GYoung | Core: Simplify `loadCatalog` method call in Iceberg (#10488) | ✅ 已完成 | [0830_a02b55182](commits/0830_a02b55182/analysis.md) |
| 831 | `42c315922` | 2024-06-14 15:03:20 -0700 | GYoung | MR: Optimize schema string retrieval in Iceberg (#10489) | ✅ 已完成 | [0831_42c315922](commits/0831_42c315922/analysis.md) |
| 832 | `b7a0bea6e` | 2024-06-15 14:19:51 +0200 | Piotr Findeisen | Build: Rename allVersions flag to allModules (#10499) | ✅ 已完成 | [0832_b7a0bea6e](commits/0832_b7a0bea6e/analysis.md) |
| 833 | `636f96304` | 2024-06-15 21:10:05 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.16.0 to 3.16.1 (#10419) | ✅ 已完成 | [0833_636f96304](commits/0833_636f96304/analysis.md) |
| 834 | `6c76a3a9a` | 2024-06-15 21:10:25 +0200 | dependabot[bot] | Build: Bump org.scala-lang.modules:scala-collection-compat_2.13 (#10195) | ✅ 已完成 | [0834_6c76a3a9a](commits/0834_6c76a3a9a/analysis.md) |
| 835 | `31654239e` | 2024-06-16 10:06:11 +0200 | dependabot[bot] | Build: Bump org.springframework:spring-web from 5.3.36 to 5.3.37 (#10503) | ✅ 已完成 | [0835_31654239e](commits/0835_31654239e/analysis.md) |
| 836 | `26c298a93` | 2024-06-16 10:06:23 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.110.Final to 4.1.111.Final (#10504) | ✅ 已完成 | [0836_26c298a93](commits/0836_26c298a93/analysis.md) |
| 837 | `26dc338a4` | 2024-06-16 10:06:38 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.25.69 to 2.26.3 (#10505) | ✅ 已完成 | [0837_26dc338a4](commits/0837_26dc338a4/analysis.md) |
| 838 | `a3a2b585b` | 2024-06-16 10:06:53 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.6 to 0.25.7 (#10507) | ✅ 已完成 | [0838_a3a2b585b](commits/0838_a3a2b585b/analysis.md) |
| 839 | `3d3e565a7` | 2024-06-17 08:40:04 +0200 | Piotr Findeisen | Core: Remove deprecated APIs scheduled for removal in 1.6.0 (#10501) | ✅ 已完成 | [0839_3d3e565a7](commits/0839_3d3e565a7/analysis.md) |
| 840 | `5f3809c51` | 2024-06-17 14:59:32 +0200 | Piotr Findeisen | Core, Flink, Spark: Import the right assertThatThrownBy method from AssertJ (#10512) | ✅ 已完成 | [0840_5f3809c51](commits/0840_5f3809c51/analysis.md) |
| 841 | `87810c8f2` | 2024-06-17 15:00:56 +0200 | Piotr Findeisen | AWS: Rename test helper to deconflict with Assertions (#10511) | ✅ 已完成 | [0841_87810c8f2](commits/0841_87810c8f2/analysis.md) |
| 842 | `1928257ce` | 2024-06-17 15:11:11 +0200 | Piotr Findeisen | Build: Merge job definitions in spark-ci.yml (#10513) | ✅ 已完成 | [0842_1928257ce](commits/0842_1928257ce/analysis.md) |
| 843 | `52d82f93e` | 2024-06-17 15:49:42 +0200 | Piotr Findeisen | API, Spark 3.3: Remove all usages of deprecated AssertHelpers (#10500) | ✅ 已完成 | [0843_52d82f93e](commits/0843_52d82f93e/analysis.md) |
| 844 | `d8f26caa9` | 2024-06-17 07:34:23 -0700 | Amogh Jahagirdar | Spark: Use bulk deletes in rewrite manifests action (#10343) | ✅ 已完成 | [0844_d8f26caa9](commits/0844_d8f26caa9/analysis.md) |
| 845 | `4859b552e` | 2024-06-17 10:36:19 -0700 | Cancai Cai | Build: Update NOTICE to include copyright for 2024 (#10471) | ✅ 已完成 | [0845_4859b552e](commits/0845_4859b552e/analysis.md) |
| 846 | `ae6f9066e` | 2024-06-17 21:18:31 +0200 | Piotr Findeisen | Remove redundant `-XX:+IgnoreUnrecognizedVMOptions` (#10475) | ✅ 已完成 | [0846_ae6f9066e](commits/0846_ae6f9066e/analysis.md) |
| 847 | `2289758dc` | 2024-06-18 09:16:20 +0200 | Fokko Driesprong | spec: Fix formatting of Default values (#10525) | ✅ 已完成 | [0847_2289758dc](commits/0847_2289758dc/analysis.md) |
| 848 | `286204777` | 2024-06-18 09:36:11 +0200 | Manu Zhang | docs: Introduce variable for setting the Flink version (#10463) | ✅ 已完成 | [0848_286204777](commits/0848_286204777/analysis.md) |
| 849 | `0b505de41` | 2024-06-18 11:41:48 +0200 | Piotr Findeisen | Flink: Import Assertions statically (#10532) | ✅ 已完成 | [0849_0b505de41](commits/0849_0b505de41/analysis.md) |
| 850 | `316f0a11b` | 2024-06-18 11:52:40 +0200 | Piotr Findeisen | Spark: Import Assertions statically (#10531) | ✅ 已完成 | [0850_316f0a11b](commits/0850_316f0a11b/analysis.md) |
| 851 | `5ea78e3fb` | 2024-06-18 12:56:29 +0200 | Piotr Findeisen | Statically import methods from AssertJ Assertions (#10517) | ✅ 已完成 | [0851_5ea78e3fb](commits/0851_5ea78e3fb/analysis.md) |
| 852 | `3c7144027` | 2024-06-18 13:07:33 +0200 | DaqianLiao | Core: Simplify newTableMetadata method in TableMetadata (#10528) | ✅ 已完成 | [0852_3c7144027](commits/0852_3c7144027/analysis.md) |
| 853 | `08cc776bf` | 2024-06-18 13:54:48 +0200 | Piotr Findeisen | Build: Run revapi workflow on workflow/build system changes (#10485) | ✅ 已完成 | [0853_08cc776bf](commits/0853_08cc776bf/analysis.md) |
| 854 | `9dbfbbb20` | 2024-06-18 08:22:47 -0700 | Piotr Findeisen | API, Core, Flink, Avro, Parquet: Remove dead code and update javadocs (#10530) | ✅ 已完成 | [0854_9dbfbbb20](commits/0854_9dbfbbb20/analysis.md) |
| 855 | `791140652` | 2024-06-18 18:01:44 +0200 | Piotr Findeisen | Fix JVM locale dependent casing (#10521) | ✅ 已完成 | [0855_791140652](commits/0855_791140652/analysis.md) |
| 856 | `8248663a2` | 2024-06-19 09:40:51 +0800 | Piotr Findeisen | Fix code depending on JVM default charset (#10529) | ✅ 已完成 | [0856_8248663a2](commits/0856_8248663a2/analysis.md) |
| 857 | `cbd11d7aa` | 2024-06-19 11:05:29 +0200 | Piotr Findeisen | Run Flink tests on Java 17 (#10477) | ✅ 已完成 | [0857_cbd11d7aa](commits/0857_cbd11d7aa/analysis.md) |
| 858 | `10d7ab11c` | 2024-06-19 18:53:27 +0200 | Piotr Findeisen | Run Hive3 tests on Java 11 and 17 too (#10482) | ✅ 已完成 | [0858_10d7ab11c](commits/0858_10d7ab11c/analysis.md) |
| 859 | `23a578e5c` | 2024-06-20 09:44:13 +0200 | Eduard Tudenhoefner | Core: Prevent duplicate data/delete files (#10007) | ✅ 已完成 | [0859_23a578e5c](commits/0859_23a578e5c/analysis.md) |
| 860 | `601efaf30` | 2024-06-20 12:26:45 +0200 | Qishang Zhong | Flink: Fix the condition of `formatVersion` for skipping test cases (#10541) | ✅ 已完成 | [0860_601efaf30](commits/0860_601efaf30/analysis.md) |
| 861 | `a2a679f7c` | 2024-06-20 15:36:38 +0200 | Piotr Findeisen | Build: Sort error-prone configuration options (#10540) | ✅ 已完成 | [0861_a2a679f7c](commits/0861_a2a679f7c/analysis.md) |
| 862 | `c67c9124d` | 2024-06-20 08:27:59 -0700 | Amogh Jahagirdar | Core, Spark: Spark writes/actions should only perform cleanup if failure is cleanable (#10373) | ✅ 已完成 | [0862_c67c9124d](commits/0862_c67c9124d/analysis.md) |
| 863 | `1ec69d1ce` | 2024-06-20 18:10:14 +0200 | Piotr Findeisen | Docs: Allow Java 17 in contribute.md (#10545) | ✅ 已完成 | [0863_1ec69d1ce](commits/0863_1ec69d1ce/analysis.md) |
| 864 | `e57b9f695` | 2024-06-21 07:33:26 -0700 | Amogh Jahagirdar | Spark: Backport #10373 to Spark 3.3 and 3.4 (#10546) | ✅ 已完成 | [0864_e57b9f695](commits/0864_e57b9f695/analysis.md) |
| 865 | `a47937c0c` | 2024-06-21 10:34:13 -0700 | Huaxin Gao | Spark 3.5: Support Aggregate push down for incremental scan (#10538) | ✅ 已完成 | [0865_a47937c0c](commits/0865_a47937c0c/analysis.md) |
| 866 | `da6268ef1` | 2024-06-24 10:20:54 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.26 to 9.5.27 (#10555) | ✅ 已完成 | [0866_da6268ef1](commits/0866_da6268ef1/analysis.md) |
| 867 | `87d2fdd17` | 2024-06-24 10:24:27 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.26.3 to 2.26.7 (#10554) | ✅ 已完成 | [0867_87d2fdd17](commits/0867_87d2fdd17/analysis.md) |
| 868 | `a75fb1c96` | 2024-06-24 10:28:52 +0200 | dependabot[bot] | Build: Bump nessie from 0.90.4 to 0.91.1 (#10551) | ✅ 已完成 | [0868_a75fb1c96](commits/0868_a75fb1c96/analysis.md) |
| 869 | `29fd2a0cb` | 2024-06-24 10:34:55 +0200 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.6 to 1.1.0 (#10552) | ✅ 已完成 | [0869_29fd2a0cb](commits/0869_29fd2a0cb/analysis.md) |
| 870 | `5733aecd0` | 2024-06-24 10:39:26 -0700 | Hongyue/Steve Zhang | Core: Pushdown data_file.content filter in entries metadata table (#10203) | ✅ 已完成 | [0870_5733aecd0](commits/0870_5733aecd0/analysis.md) |
| 871 | `c85f66759` | 2024-06-25 12:17:04 +0200 | Alexandre Dutra | Build: Bump Nessie to 0.91.2 (#10563) | ✅ 已完成 | [0871_c85f66759](commits/0871_c85f66759/analysis.md) |
| 872 | `4bd9e64f2` | 2024-06-25 12:18:05 +0200 | dongwang | Spark: Remove useless code in `TestRemoveOrphanFilesProcedure` (#10562) | ✅ 已完成 | [0872_4bd9e64f2](commits/0872_4bd9e64f2/analysis.md) |
| 873 | `565352d12` | 2024-06-25 17:29:23 +0200 | JB Onofré | Build: Upgrade to gradle 8.8 (#8486) | ✅ 已完成 | [0873_565352d12](commits/0873_565352d12/analysis.md) |
| 874 | `8af8a4da5` | 2024-06-25 10:21:23 -0600 | Huaxin Gao | Spark: Backport support for Aggregate push down for incremental scan to Spark 3.4 (#10561) | ✅ 已完成 | [0874_8af8a4da5](commits/0874_8af8a4da5/analysis.md) |
| 875 | `10fc04b63` | 2024-06-25 15:42:43 -0600 | Fokko Driesprong | Build: Move to `goooler` shadow plugin (#10568) | ✅ 已完成 | [0875_10fc04b63](commits/0875_10fc04b63/analysis.md) |
| 876 | `29ff08a08` | 2024-06-25 15:40:42 -0700 | Fokko Driesprong | Spec: Fix Typo (#10564) | ✅ 已完成 | [0876_29ff08a08](commits/0876_29ff08a08/analysis.md) |
| 877 | `9ed33839e` | 2024-06-26 10:37:37 +0200 | Steven Zhen Wu | Core, Flink: Add task-type field to JSON serde of scan task / Add JSON serde for StaticDataTask. (#9728) | ✅ 已完成 | [0877_9ed33839e](commits/0877_9ed33839e/analysis.md) |
| 878 | `c88e9422b` | 2024-06-26 15:41:14 +0200 | Robert Stupp | Azure: Make AzureProperties w/ shared-key creds serializable (#10045) | ✅ 已完成 | [0878_c88e9422b](commits/0878_c88e9422b/analysis.md) |
| 879 | `87a988fdd` | 2024-06-26 18:28:00 +0200 | Manu Zhang | Spark 3.5: Parallelize reading files in snapshot and migrate procedures (#10037) | ✅ 已完成 | [0879_87a988fdd](commits/0879_87a988fdd/analysis.md) |
| 880 | `f9f30f6b9` | 2024-06-27 10:37:06 +0800 | edson duarte | Docs: Add BigQuery docs url to sidebar (#10574) | ✅ 已完成 | [0880_f9f30f6b9](commits/0880_f9f30f6b9/analysis.md) |
| 881 | `91fbcaa62` | 2024-06-28 09:04:43 +0200 | Piotr Findeisen | Build: Run CI checks on all supported JDKs (#10473) | ✅ 已完成 | [0881_91fbcaa62](commits/0881_91fbcaa62/analysis.md) |
| 882 | `c469edf6c` | 2024-06-28 11:32:05 +0200 | Piotr Findeisen | Common: DynConstructors cleanup (#10542) | ✅ 已完成 | [0882_c469edf6c](commits/0882_c469edf6c/analysis.md) |
| 883 | `7071dc18e` | 2024-06-28 12:30:13 +0200 | Piotr Findeisen | Fix CI script inclusion of release branches (#10514) | ✅ 已完成 | [0883_7071dc18e](commits/0883_7071dc18e/analysis.md) |
| 884 | `2bdae5e74` | 2024-06-29 08:22:01 -0600 | Tai Le Manh | Fix incorrect double-checked-locking around TestStreamScanSql#tEnv (#10605) | ✅ 已完成 | [0884_2bdae5e74](commits/0884_2bdae5e74/analysis.md) |
| 885 | `f0cb1ec2c` | 2024-06-30 15:15:21 +0200 | dependabot[bot] | Build: Bump nessie from 0.91.2 to 0.91.3 (#10608) | ✅ 已完成 | [0885_f0cb1ec2c](commits/0885_f0cb1ec2c/analysis.md) |
| 886 | `0e7aa84b1` | 2024-06-30 19:12:33 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.26.7 to 2.26.12 (#10611) | ✅ 已完成 | [0886_0e7aa84b1](commits/0886_0e7aa84b1/analysis.md) |
| 887 | `a975a9555` | 2024-07-01 15:24:02 +0200 | Tom Tanaka | Flink: Migrate HadoopCatalog related tests (#10358) | ✅ 已完成 | [0887_a975a9555](commits/0887_a975a9555/analysis.md) |
| 888 | `d3cb1b696` | 2024-07-01 15:33:17 -0600 | Helt | Core: Fix ParallelIterable memory leak where queue continues to be populated even after iterator close (#9402) | ✅ 已完成 | [0888_d3cb1b696](commits/0888_d3cb1b696/analysis.md) |
| 889 | `f4ddaea56` | 2024-07-01 18:23:30 -0600 | Tai Le Manh | Core: Handle potential NPE in RESTSessionCatalog#newSessionCache (#10607) | ✅ 已完成 | [0889_f4ddaea56](commits/0889_f4ddaea56/analysis.md) |
| 890 | `3df3d29a6` | 2024-07-02 09:47:23 +0200 | dependabot[bot] | Build: Bump io.github.goooler.shadow:shadow-gradle-plugin (#10612) | ✅ 已完成 | [0890_3df3d29a6](commits/0890_3df3d29a6/analysis.md) |
| 891 | `0eab91aad` | 2024-07-02 18:26:47 +0200 | Tom Tanaka | Flink 1.17, 1.18: Migrate HadoopCatalog related tests (#10620) | ✅ 已完成 | [0891_0eab91aad](commits/0891_0eab91aad/analysis.md) |
| 892 | `afda8be25` | 2024-07-03 14:36:53 +0200 | Robert Stupp | Address IntelliJ inspection findings (#10583) | ✅ 已完成 | [0892_afda8be25](commits/0892_afda8be25/analysis.md) |
| 893 | `d255c87b0` | 2024-07-03 14:39:08 +0200 | Robert Stupp | Build: Enable the Gradle build cache (#10602) | ✅ 已完成 | [0893_d255c87b0](commits/0893_d255c87b0/analysis.md) |
| 894 | `3825477b0` | 2024-07-03 17:36:49 -0600 | Robert Stupp | API, Flink, ORC: Fix implicit `long` casting issues (#10580) | ✅ 已完成 | [0894_3825477b0](commits/0894_3825477b0/analysis.md) |
| 895 | `d1f9f28f4` | 2024-07-04 08:14:58 +0200 | Alexandre Dutra | REST: disallow overriding "credential" in table sessions (#10345) | ✅ 已完成 | [0895_d1f9f28f4](commits/0895_d1f9f28f4/analysis.md) |
| 896 | `c7eba348d` | 2024-07-04 08:23:54 +0200 | Robert Stupp | Apply IntelliJ inspection findings to older Spark + Flink versions (#10625) | ✅ 已完成 | [0896_c7eba348d](commits/0896_c7eba348d/analysis.md) |
| 897 | `4048987d2` | 2024-07-04 14:06:57 +0200 | Alexandre Dutra | Core: Assume issued_token_type is access_token to fully comply with RFC 6749 (#10314) | ✅ 已完成 | [0897_4048987d2](commits/0897_4048987d2/analysis.md) |
| 898 | `4aee30761` | 2024-07-04 18:02:47 +0200 | Robert Stupp | Flink: Fix `long` casting issues (#10629) | ✅ 已完成 | [0898_4aee30761](commits/0898_4aee30761/analysis.md) |
| 899 | `fc213cc39` | 2024-07-04 11:34:22 -0600 | Tai Le Manh | Core: Handle possible heap data corruption in OAuth2Util.AuthSession#headers (#10615) | ✅ 已完成 | [0899_fc213cc39](commits/0899_fc213cc39/analysis.md) |
| 900 | `9a0fc4948` | 2024-07-05 08:46:24 +0200 | Sotaro Hikita | AWS: Retain Glue Catalog column comment (#10276) | ✅ 已完成 | [0900_9a0fc4948](commits/0900_9a0fc4948/analysis.md) |
| 901 | `83dd59a6f` | 2024-07-05 08:53:38 +0200 | Ajantha Bhat | Build: Use official revapi Gradle plugin (#10631) | ✅ 已完成 | [0901_83dd59a6f](commits/0901_83dd59a6f/analysis.md) |
| 902 | `24d26b6a3` | 2024-07-05 09:08:44 +0200 | dongwang | Core: Fix create v1 table on REST Catalog (#10369) | ✅ 已完成 | [0902_24d26b6a3](commits/0902_24d26b6a3/analysis.md) |
| 903 | `ce1279e3d` | 2024-07-05 12:10:02 +0200 | Tom Tanaka | Flink 1.19: Migrate source package to JUnit5 (#10632) | ✅ 已完成 | [0903_ce1279e3d](commits/0903_ce1279e3d/analysis.md) |
| 904 | `6223708dd` | 2024-07-05 09:49:18 -0700 | Hongyue/Steve Zhang | Spark 3.5: Support read of partition metadata column when table has over 1k columns (#10547) | ✅ 已完成 | [0904_6223708dd](commits/0904_6223708dd/analysis.md) |
| 905 | `1c3476d95` | 2024-07-07 08:10:05 +0200 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.24 to 1.2.25 (#10652) | ✅ 已完成 | [0905_1c3476d95](commits/0905_1c3476d95/analysis.md) |
| 906 | `d06da2dd7` | 2024-07-07 13:51:56 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.26.12 to 2.26.16 (#10650) | ✅ 已完成 | [0906_d06da2dd7](commits/0906_d06da2dd7/analysis.md) |
| 907 | `b911cac63` | 2024-07-07 19:09:17 +0200 | dependabot[bot] | Build: Bump jetty from 9.4.54.v20240208 to 9.4.55.v20240627 (#10654) | ✅ 已完成 | [0907_b911cac63](commits/0907_b911cac63/analysis.md) |
| 908 | `096e507d0` | 2024-07-07 19:35:22 +0200 | dependabot[bot] | Build: Bump kafka from 3.7.0 to 3.7.1 (#10653) | ✅ 已完成 | [0908_096e507d0](commits/0908_096e507d0/analysis.md) |
| 909 | `49e416343` | 2024-07-08 09:19:25 +0200 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.1.0 to 1.2.0 (#10655) | ✅ 已完成 | [0909_49e416343](commits/0909_49e416343/analysis.md) |
| 910 | `bbf350c59` | 2024-07-08 13:27:18 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.7 to 0.25.8 (#10649) | ✅ 已完成 | [0910_bbf350c59](commits/0910_bbf350c59/analysis.md) |
| 911 | `afa913679` | 2024-07-08 14:28:01 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.27 to 9.5.28 (#10648) | ✅ 已完成 | [0911_afa913679](commits/0911_afa913679/analysis.md) |
| 912 | `06b461045` | 2024-07-08 15:12:47 +0200 | Eduard Tudenhoefner | Build: Downgrade Gradle from 8.8 to 8.7 due to bug with older OSX versions (#10637) | ✅ 已完成 | [0912_06b461045](commits/0912_06b461045/analysis.md) |
| 913 | `1f6ff6c70` | 2024-07-09 16:55:37 +0200 | Attila Kreiner | Data: Switch tests to JUnit5 + AssertJ-style assertions (#10657) | ✅ 已完成 | [0913_1f6ff6c70](commits/0913_1f6ff6c70/analysis.md) |
| 914 | `7d796e0b3` | 2024-07-10 08:13:43 +0200 | Tom Tanaka | Flink 1.17, 1.18: Migrate tests to JUnit5 (#10663) | ✅ 已完成 | [0914_7d796e0b3](commits/0914_7d796e0b3/analysis.md) |
| 915 | `05923d6ab` | 2024-07-10 08:38:23 +0200 | fengjiajie | Flink: Pre-create fieldGetters to avoid constructing them for each row (#10565) | ✅ 已完成 | [0915_05923d6ab](commits/0915_05923d6ab/analysis.md) |
| 916 | `4714bbfa2` | 2024-07-10 09:25:12 +0200 | Eduard Tudenhoefner | Dell, Hive3: Convert remaining tests to JUnit5 (#10670) | ✅ 已完成 | [0916_4714bbfa2](commits/0916_4714bbfa2/analysis.md) |
| 917 | `56e6b6f7b` | 2024-07-10 14:40:22 +0200 | Eduard Tudenhoefner | Build: Define JUnit4 dependency only where necessary (#10672) | ✅ 已完成 | [0917_56e6b6f7b](commits/0917_56e6b6f7b/analysis.md) |
| 918 | `6a80b7b61` | 2024-07-10 16:44:14 +0200 | Attila Kreiner | Flink, Spark: Rename constants to be all uppercase (#10675) | ✅ 已完成 | [0918_6a80b7b61](commits/0918_6a80b7b61/analysis.md) |
| 919 | `61c9b6f0b` | 2024-07-10 21:46:38 +0200 | fengjiajie | Flink: Backport #10565 to v1.18 and v1.19 (#10676) | ✅ 已完成 | [0919_61c9b6f0b](commits/0919_61c9b6f0b/analysis.md) |
| 920 | `41e19512b` | 2024-07-11 15:42:38 +0200 | Attila Kreiner | Rename & enforce constants to be all uppercase (#10673) | ✅ 已完成 | [0920_41e19512b](commits/0920_41e19512b/analysis.md) |
| 921 | `048c92d43` | 2024-07-11 06:47:12 -0700 | Devin Smith | Build: don't include slf4j-api in bundled JARs (#10665) | ✅ 已完成 | [0921_048c92d43](commits/0921_048c92d43/analysis.md) |
| 922 | `d8e7642d4` | 2024-07-11 08:22:20 -0700 | Bryan Keller | Kafka Connect: Commit coordination (#10351) | ✅ 已完成 | [0922_d8e7642d4](commits/0922_d8e7642d4/analysis.md) |
| 923 | `89a0b0ffc` | 2024-07-11 21:12:03 +0200 | JB Onofré | Upgrade to Gradle 8.9 (#10686) | ✅ 已完成 | [0923_89a0b0ffc](commits/0923_89a0b0ffc/analysis.md) |
| 924 | `5455d30d9` | 2024-07-11 21:30:35 -0600 | Hongyue/Steve Zhang | Core: Use bulk deletes when removing old metadata files (#10679) | ✅ 已完成 | [0924_5455d30d9](commits/0924_5455d30d9/analysis.md) |
| 925 | `07002fae2` | 2024-07-12 10:06:00 +0200 | Denys Kuzmenko | Core: Expose incremental/changelog scan in SerializableTable (#10682) | ✅ 已完成 | [0925_07002fae2](commits/0925_07002fae2/analysis.md) |
| 926 | `63af974ef` | 2024-07-12 11:05:10 +0200 | Robert Stupp | OpenAPI: Deprecate `oauth/tokens` endpoint (#10603) | ✅ 已完成 | [0926_63af974ef](commits/0926_63af974ef/analysis.md) |
| 927 | `83bb1bf57` | 2024-07-12 11:05:59 +0200 | Robert Stupp | Bump Nessie from 0.91.3 to 0.92.0 (#10689) | ✅ 已完成 | [0927_83bb1bf57](commits/0927_83bb1bf57/analysis.md) |
| 928 | `d9dbb75ed` | 2024-07-12 12:15:24 +0200 | Yuya Ebihara | Core: Exclude unexpected namespaces JdbcCatalog.listNamespaces (#10498) | ✅ 已完成 | [0928_d9dbb75ed](commits/0928_d9dbb75ed/analysis.md) |
| 929 | `ed228f79c` | 2024-07-12 14:25:39 +0200 | boroknagyz | Core: Fix NPE during conflict handling of NULL partitions (#10680) | ✅ 已完成 | [0929_ed228f79c](commits/0929_ed228f79c/analysis.md) |
| 930 | `9724aa2f7` | 2024-07-12 16:32:12 -0700 | Hongyue/Steve Zhang | Spark 3.3, 3.4: Support read of partition metadata column when table is over 1k (#10641) | ✅ 已完成 | [0930_9724aa2f7](commits/0930_9724aa2f7/analysis.md) |
| 931 | `de4262ddc` | 2024-07-14 09:53:12 +0200 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.26.0 to 3.26.3 (#10698) | ✅ 已完成 | [0931_de4262ddc](commits/0931_de4262ddc/analysis.md) |
| 932 | `0acdb3f65` | 2024-07-15 08:42:20 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.28.0 to 26.43.0 (#10699) | ✅ 已完成 | [0932_0acdb3f65](commits/0932_0acdb3f65/analysis.md) |
| 933 | `18c2123cd` | 2024-07-15 08:42:45 +0200 | dependabot[bot] | Build: Bump nessie from 0.92.0 to 0.92.1 (#10697) | ✅ 已完成 | [0933_18c2123cd](commits/0933_18c2123cd/analysis.md) |
| 934 | `96e77596a` | 2024-07-15 08:43:35 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.26.16 to 2.26.20 (#10700) | ✅ 已完成 | [0934_96e77596a](commits/0934_96e77596a/analysis.md) |
| 935 | `6319712b6` | 2024-07-15 11:13:47 +0200 | Eduard Tudenhoefner | OpenAPI: Fix property names for stats/partition stats (#10662) | ✅ 已完成 | [0935_6319712b6](commits/0935_6319712b6/analysis.md) |
| 936 | `5479545e9` | 2024-07-15 14:38:03 -0500 | Robert Stupp | Nit: fix/suppress false-positivie errorprone warning (#10690) | ✅ 已完成 | [0936_5479545e9](commits/0936_5479545e9/analysis.md) |
| 937 | `ab580b995` | 2024-07-15 21:02:35 -0700 | Steven Zhen Wu | Spec: remove the JSON spec for content file and file scan task sections. (#9771) | ✅ 已完成 | [0937_ab580b995](commits/0937_ab580b995/analysis.md) |
| 938 | `404c66580` | 2024-07-16 11:21:42 +0200 | Ajantha Bhat | Infra: Fix stale PR workflow (#10706) | ✅ 已完成 | [0938_404c66580](commits/0938_404c66580/analysis.md) |
| 939 | `12f2e14c3` | 2024-07-16 12:33:45 +0200 | Piotr Findeisen | Update references to `main` branch (#10705) | ✅ 已完成 | [0939_12f2e14c3](commits/0939_12f2e14c3/analysis.md) |
| 940 | `4a0ae2219` | 2024-07-16 13:58:13 -0700 | Szehon Ho | Docs: Clarify defaults for distribution mode (#10575) | ✅ 已完成 | [0940_4a0ae2219](commits/0940_4a0ae2219/analysis.md) |
| 941 | `ff7e833c6` | 2024-07-17 08:29:38 +0200 | Eduard Tudenhoefner | Infra: Improve Bug report template (#10708) | ✅ 已完成 | [0941_ff7e833c6](commits/0941_ff7e833c6/analysis.md) |
| 942 | `cc1a1e495` | 2024-07-17 09:08:01 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.16.1 to 3.17.0 (#10696) | ✅ 已完成 | [0942_cc1a1e495](commits/0942_cc1a1e495/analysis.md) |
| 943 | `f59055a07` | 2024-07-17 09:13:13 +0200 | Manu Zhang | Spark 3.3: Ignore flaky test taking up all device space (#10704) | ✅ 已完成 | [0943_f59055a07](commits/0943_f59055a07/analysis.md) |
| 944 | `206e471de` | 2024-07-17 11:30:45 +0200 | Ajantha Bhat | Infra: Increase operation per limit for stale bot workflow (#10712) | ✅ 已完成 | [0944_206e471de](commits/0944_206e471de/analysis.md) |
| 945 | `5a562bb07` | 2024-07-17 13:49:02 +0200 | Piotr Findeisen | DynFields, DynMethods code cleanup  (#10543) | ✅ 已完成 | [0945_5a562bb07](commits/0945_5a562bb07/analysis.md) |
| 946 | `319f29ea8` | 2024-07-17 11:07:10 -0700 | Anurag Mantripragada | Docs: Add examples for DataFrame branch writes (#10644) | ✅ 已完成 | [0946_319f29ea8](commits/0946_319f29ea8/analysis.md) |
| 947 | `3c4617885` | 2024-07-17 15:14:43 -0500 | Amogh Jahagirdar | Core: Make new TableMetadata.Builder constructor private (#10714) | ✅ 已完成 | [0947_3c4617885](commits/0947_3c4617885/analysis.md) |
| 948 | `229d8f6fc` | 2024-07-17 18:35:21 -0600 | Piotr Findeisen | Common: Update the version in deprecation messages (#10715) | ✅ 已完成 | [0948_229d8f6fc](commits/0948_229d8f6fc/analysis.md) |
| 949 | `bc72b2ee6` | 2024-07-18 16:58:05 +0200 | gaborkaszab | Docs: Fix link on Concepts page (#10718) | ✅ 已完成 | [0949_bc72b2ee6](commits/0949_bc72b2ee6/analysis.md) |
| 950 | `5f970a839` | 2024-07-18 17:27:06 -0600 | Farooq Qaiser | Core: Support appending files with different specs (#9860) | ✅ 已完成 | [0950_5f970a839](commits/0950_5f970a839/analysis.md) |
| 951 | `cb6540c8c` | 2024-07-18 19:30:01 -0600 | Piotr Findeisen | Core: Remove unnecessary class-level synchronized in ManifestFiles (#10544) | ✅ 已完成 | [0951_cb6540c8c](commits/0951_cb6540c8c/analysis.md) |
| 952 | `e02b5c90e` | 2024-07-19 10:23:15 -0700 | emkornfield | Spec: Clarify which columns can be used for equality delete files. (#8981) | ✅ 已完成 | [0952_e02b5c90e](commits/0952_e02b5c90e/analysis.md) |
| 953 | `0e678ce58` | 2024-07-21 19:14:14 +0200 | dependabot[bot] | Build: Bump nessie from 0.92.1 to 0.93.1 (#10727) | ✅ 已完成 | [0953_0e678ce58](commits/0953_0e678ce58/analysis.md) |
| 954 | `fd0d5889d` | 2024-07-22 08:41:28 +0200 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.19.8 to 1.20.0 (#10730) | ✅ 已完成 | [0954_fd0d5889d](commits/0954_fd0d5889d/analysis.md) |
| 955 | `224782fe1` | 2024-07-22 09:16:53 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.28 to 9.5.29 (#10734) | ✅ 已完成 | [0955_224782fe1](commits/0955_224782fe1/analysis.md) |
| 956 | `2a7293343` | 2024-07-22 09:18:17 +0200 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.2.0 to 1.2.1 (#10733) | ✅ 已完成 | [0956_2a7293343](commits/0956_2a7293343/analysis.md) |
| 957 | `5eb9be6fe` | 2024-07-22 09:18:30 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.26.20 to 2.26.21 (#10729) | ✅ 已完成 | [0957_5eb9be6fe](commits/0957_5eb9be6fe/analysis.md) |
| 958 | `8eb75db09` | 2024-07-22 09:18:40 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.111.Final to 4.1.112.Final (#10726) | ✅ 已完成 | [0958_8eb75db09](commits/0958_8eb75db09/analysis.md) |
| 959 | `3067261c9` | 2024-07-22 09:18:52 +0200 | dependabot[bot] | Build: Bump orc from 1.9.3 to 1.9.4 (#10728) | ✅ 已完成 | [0959_3067261c9](commits/0959_3067261c9/analysis.md) |
| 960 | `ad59eb7d6` | 2024-07-22 09:47:13 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#10731) | ✅ 已完成 | [0960_ad59eb7d6](commits/0960_ad59eb7d6/analysis.md) |
| 961 | `fae8f6e73` | 2024-07-22 10:25:00 +0200 | Tom Tanaka | Flink: Migrate remaining classes to JUnit5 (#10684) | ✅ 已完成 | [0961_fae8f6e73](commits/0961_fae8f6e73/analysis.md) |
| 962 | `7477f8bfb` | 2024-07-22 10:36:05 -0600 | dongwang | API, Build: Fix typo in comments in `Table` and `gradlew` (#10744) | ✅ 已完成 | [0962_7477f8bfb](commits/0962_7477f8bfb/analysis.md) |
| 963 | `344bd3e76` | 2024-07-22 10:10:37 -0700 | Steven Zhen Wu | Flink: parameterize Flink table source tests to test both old and FLIP-27 source implementations (#10741) | ✅ 已完成 | [0963_344bd3e76](commits/0963_344bd3e76/analysis.md) |
| 964 | `7831a8dfc` | 2024-07-22 13:08:09 -0700 | Piotr Findeisen | Core: Limit ParallelIterable memory consumption by yielding in tasks (#10691) | ✅ 已完成 | [0964_7831a8dfc](commits/0964_7831a8dfc/analysis.md) |
| 965 | `604b2bb85` | 2024-07-22 13:59:18 -0700 | Steven Zhen Wu | Flink: handle rescale properly and refactor statistics (#10457) | ✅ 已完成 | [0965_604b2bb85](commits/0965_604b2bb85/analysis.md) |
| 966 | `f7b390600` | 2024-07-23 14:35:22 +0200 | Tom Tanaka | Flink 1.17, 1.18: Migrate remaining tests to JUnit5 (#10749) | ✅ 已完成 | [0966_f7b390600](commits/0966_f7b390600/analysis.md) |
| 967 | `b76939128` | 2024-07-23 14:01:47 -0700 | emkornfield | Spec: Clarify time travel implementation in Iceberg (#8982) | ✅ 已完成 | [0967_b76939128](commits/0967_b76939128/analysis.md) |
| 968 | `0cce1198c` | 2024-07-23 15:51:55 -0600 | Ajantha Bhat | Build: Update revapi to compare against 1.6.0 (#10754) | ✅ 已完成 | [0968_0cce1198c](commits/0968_0cce1198c/analysis.md) |
| 969 | `91585e951` | 2024-07-24 07:18:50 +0200 | Venkata krishnan Sowrirajan | Support for Flink's SpeculativeExecution in batch execution mode (#10548) | ✅ 已完成 | [0969_91585e951](commits/0969_91585e951/analysis.md) |
| 970 | `716d5b319` | 2024-07-24 16:40:30 +0200 | rice | API: Update StatisticsFile javadoc (#10769) | ✅ 已完成 | [0970_716d5b319](commits/0970_716d5b319/analysis.md) |
| 971 | `c00635a38` | 2024-07-24 17:07:42 +0200 | Eduard Tudenhoefner | Flink: Remove JUnit4 dependency (#10770) | ✅ 已完成 | [0971_c00635a38](commits/0971_c00635a38/analysis.md) |
| 972 | `4e311cd5b` | 2024-07-24 11:26:29 -0600 | ritwika314 | Docs: Add bodo to iceberg vendors (#10756) | ✅ 已完成 | [0972_4e311cd5b](commits/0972_4e311cd5b/analysis.md) |
| 973 | `24fb7341a` | 2024-07-24 14:00:26 -0600 | Szehon Ho | Core: Implement estimateRowCount for Files and Entries Metadata Tables (#10759) | ✅ 已完成 | [0973_24fb7341a](commits/0973_24fb7341a/analysis.md) |
| 974 | `3495d9eda` | 2024-07-24 15:52:47 -0500 | Attila Kreiner | Build: Updates Checkstyle definition (#10681) | ✅ 已完成 | [0974_3495d9eda](commits/0974_3495d9eda/analysis.md) |
| 975 | `f5635a656` | 2024-07-25 09:02:56 +0200 | Amogh Jahagirdar | API: Fix typo in RewriteManifestFiles java doc (#10778) | ✅ 已完成 | [0975_f5635a656](commits/0975_f5635a656/analysis.md) |
| 976 | `622c127c3` | 2024-07-25 14:20:18 +0200 | Ajantha Bhat | Update .asf.yaml (#10767) | ✅ 已完成 | [0976_622c127c3](commits/0976_622c127c3/analysis.md) |
| 977 | `7bced3313` | 2024-07-25 14:45:48 +0200 | Piotr Findeisen | Build: Support building with Java 21 (#10474) | ✅ 已完成 | [0977_7bced3313](commits/0977_7bced3313/analysis.md) |
| 978 | `a309728e0` | 2024-07-25 08:00:17 -0600 | JB Onofré | Infra, Docs: Publish Apache Iceberg 1.6.0 release (#10752) | ✅ 已完成 | [0978_a309728e0](commits/0978_a309728e0/analysis.md) |
| 979 | `5da64e043` | 2024-07-25 08:54:41 -0600 | JB Onofré | Update iceberg version on site to 1.6.0 (#10783) | ✅ 已完成 | [0979_5da64e043](commits/0979_5da64e043/analysis.md) |
| 980 | `7e2920af4` | 2024-07-25 11:50:19 -0600 | Hussein Awala | Hive: close the fileIO client when closing the hive catalog (#10771) | ✅ 已完成 | [0980_7e2920af4](commits/0980_7e2920af4/analysis.md) |
| 981 | `40d174f61` | 2024-07-25 13:12:55 -0600 | JB Onofré | Infra: Add jbonofre as collaborator on the project (#10782) | ✅ 已完成 | [0981_40d174f61](commits/0981_40d174f61/analysis.md) |
| 982 | `48841a912` | 2024-07-25 19:49:06 -0600 | emkornfield | Docs: Make compatibility example consistent (#10781) | ✅ 已完成 | [0982_48841a912](commits/0982_48841a912/analysis.md) |
| 983 | `ec2c2e978` | 2024-07-26 15:30:00 +0200 | liu yang | mr：Fix ugi not correct in WORKER_POOL (#10661) | ✅ 已完成 | [0983_ec2c2e978](commits/0983_ec2c2e978/analysis.md) |
| 984 | `4dbc7f578` | 2024-07-26 09:13:18 -0700 | Steven Zhen Wu | Flink: backport PR #10331 and PR #10457  (#10757) | ✅ 已完成 | [0984_4dbc7f578](commits/0984_4dbc7f578/analysis.md) |
| 985 | `265bce76f` | 2024-07-29 09:10:42 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.26.21 to 2.26.25 (#10800) | ✅ 已完成 | [0985_265bce76f](commits/0985_265bce76f/analysis.md) |
| 986 | `31d52c0ed` | 2024-07-29 09:11:02 +0200 | dependabot[bot] | Build: Bump nessie from 0.93.1 to 0.94.2 (#10798) | ✅ 已完成 | [0986_31d52c0ed](commits/0986_31d52c0ed/analysis.md) |
| 987 | `349046889` | 2024-07-29 09:30:36 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.17.0 to 3.18.0 (#10801) | ✅ 已完成 | [0987_349046889](commits/0987_349046889/analysis.md) |
| 988 | `b8b57b2ff` | 2024-07-29 09:33:22 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.29 to 9.5.30 (#10796) | ✅ 已完成 | [0988_b8b57b2ff](commits/0988_b8b57b2ff/analysis.md) |
| 989 | `2c42e6737` | 2024-07-29 10:06:22 +0200 | pvary | Flink: Disabling flaky test TestIcebergSourceFailover.testBoundedWithSavepoint (#10802) | ✅ 已完成 | [0989_2c42e6737](commits/0989_2c42e6737/analysis.md) |
| 990 | `94d336ce0` | 2024-07-29 11:05:33 +0200 | dependabot[bot] | Build: Bump mkdocs-awesome-pages-plugin from 2.9.2 to 2.9.3 (#10795) | ✅ 已完成 | [0990_94d336ce0](commits/0990_94d336ce0/analysis.md) |
| 991 | `30e761ebe` | 2024-07-29 07:55:45 -0700 | Bryan Keller | Kafka Connect: Runtime distribution with integration tests (#10739) | ✅ 已完成 | [0991_30e761ebe](commits/0991_30e761ebe/analysis.md) |
| 992 | `e0a464f1c` | 2024-07-29 15:34:32 -0700 | Steven Zhen Wu | Flink: improve snapshot compatibility check by comparing projected sort schema in SortKeySerializer. also add unit tests for serializer snapshot. (#10794) | ✅ 已完成 | [0992_e0a464f1c](commits/0992_e0a464f1c/analysis.md) |
| 993 | `f7585932a` | 2024-07-29 15:35:18 -0700 | Steven Zhen Wu | Flink: support limit pushdown in FLIP-27 source (#10748) | ✅ 已完成 | [0993_f7585932a](commits/0993_f7585932a/analysis.md) |
| 994 | `96587abf7` | 2024-07-30 17:18:39 +0200 | Tom Tanaka | Flink: Remove MiniClusterResource (#10817) | ✅ 已完成 | [0994_96587abf7](commits/0994_96587abf7/analysis.md) |
| 995 | `4d1ceac27` | 2024-07-30 22:25:56 +0100 | liu yang | Docs: Use link addresses instead of descriptions in releases.md (#10815) | ✅ 已完成 | [0995_4d1ceac27](commits/0995_4d1ceac27/analysis.md) |
| 996 | `0ff90e773` | 2024-07-30 23:26:55 +0200 | Devin Smith | Build: Declare avro as an api dependency of iceberg-core (#10573) | ✅ 已完成 | [0996_0ff90e773](commits/0996_0ff90e773/analysis.md) |
| 997 | `72b39ab91` | 2024-07-30 14:49:44 -0700 | Steven Zhen Wu | Flink: backport PR #10748 for limit pushdown (#10813) | ✅ 已完成 | [0997_72b39ab91](commits/0997_72b39ab91/analysis.md) |
| 998 | `76dba8fe8` | 2024-07-31 12:59:13 +0200 | gaborkaszab | Docs: Fix header for entries metadata table (#10826) | ✅ 已完成 | [0998_76dba8fe8](commits/0998_76dba8fe8/analysis.md) |
| 999 | `506fee492` | 2024-07-31 10:41:20 -0500 | Huaxin Gao | Spark 3.5: Support Reporting Column Stats (#10659) | ✅ 已完成 | [0999_506fee492](commits/0999_506fee492/analysis.md) |
| 1000 | `84c912517` | 2024-08-01 09:22:51 +0200 | Venkata krishnan Sowrirajan | Flink: Backport #10548 to v1.18 and v1.17 (#10776) | ✅ 已完成 | [1000_84c912517](commits/1000_84c912517/analysis.md) |
| 1001 | `806da5cfc` | 2024-08-01 09:30:04 +0200 | Eduard Tudenhoefner | Infra: Improve feature request template (#10825) | ✅ 已完成 | [1001_806da5cfc](commits/1001_806da5cfc/analysis.md) |
| 1002 | `99b8e88a8` | 2024-08-01 10:23:05 -0500 | hsiang-c | Core: Replace the duplicated ALL_DATA_FILES with ALL_DELETE_FILES (#10836) | ✅ 已完成 | [1002_99b8e88a8](commits/1002_99b8e88a8/analysis.md) |
| 1003 | `eb9d3951e` | 2024-08-01 10:23:49 -0500 | Russell Spitzer | Core: Adds Basic Classes for Iceberg Table Version 3 (#10760) | ✅ 已完成 | [1003_eb9d3951e](commits/1003_eb9d3951e/analysis.md) |
| 1004 | `39373d09c` | 2024-08-01 12:31:18 -0700 | Grant Nicholas | Core: Allow SnapshotProducer to skip uncommitted manifest cleanup after commit (#10523) | ✅ 已完成 | [1004_39373d09c](commits/1004_39373d09c/analysis.md) |
| 1005 | `6e7113a52` | 2024-08-01 14:10:37 -0700 | Steven Zhen Wu | Flink: a few small fixes or tuning for range partitioner (#10823) | ✅ 已完成 | [1005_6e7113a52](commits/1005_6e7113a52/analysis.md) |
| 1006 | `9a67f0b85` | 2024-08-02 09:23:23 +0200 | Piotr Findeisen | Drop support for Java 8 (#10518) | ✅ 已完成 | [1006_9a67f0b85](commits/1006_9a67f0b85/analysis.md) |
| 1007 | `c2db97ce9` | 2024-08-02 14:02:56 +0200 | Eduard Tudenhoefner | Build: Bump com.adobe.testing:s3mock-junit5 from 2.11.0 to 2.17.0 (#10851) | ✅ 已完成 | [1007_c2db97ce9](commits/1007_c2db97ce9/analysis.md) |
| 1008 | `122176a37` | 2024-08-02 14:08:43 +0200 | Eduard Tudenhoefner | Core: Upgrade Jetty and Servlet API (#10850) | ✅ 已完成 | [1008_122176a37](commits/1008_122176a37/analysis.md) |
| 1009 | `08aed72be` | 2024-08-02 15:14:40 +0200 | Robert Stupp | Build: Configure options.release = 11 / remove com.palantir.baseline-release-compatibility plugin  (#10849) | ✅ 已完成 | [1009_08aed72be](commits/1009_08aed72be/analysis.md) |
| 1010 | `39295753e` | 2024-08-02 15:15:06 +0200 | dependabot[bot] | Build: Bump kafka from 3.7.1 to 3.8.0 (#10797) | ✅ 已完成 | [1010_39295753e](commits/1010_39295753e/analysis.md) |
| 1011 | `674214cb6` | 2024-08-02 15:44:46 +0200 | Piotr Findeisen | Build: Update baseline gradle plugin to 5.58.0 (#10788) | ✅ 已完成 | [1011_674214cb6](commits/1011_674214cb6/analysis.md) |
| 1012 | `dc7ad7190` | 2024-08-02 08:39:10 -0700 | Steven Zhen Wu | Flink: refactor sink tests to reduce the number of combinations with parameterized tests (#10777) | ✅ 已完成 | [1012_dc7ad7190](commits/1012_dc7ad7190/analysis.md) |
| 1013 | `af75440da` | 2024-08-02 08:39:47 -0700 | Steven Zhen Wu | Flink: backport PR #10823 for range partitioner fixup (#10847) | ✅ 已完成 | [1013_af75440da](commits/1013_af75440da/analysis.md) |
| 1014 | `b17d1c9ab` | 2024-08-02 20:44:48 +0200 | Piotr Findeisen | Core: Remove reflection from TestParallelIterable (#10857) | ✅ 已完成 | [1014_b17d1c9ab](commits/1014_b17d1c9ab/analysis.md) |
| 1015 | `479f468c5` | 2024-08-04 14:32:52 -0700 | Ryan Blue | Spec: Deprecate the file system table scheme (#10833) | ✅ 已完成 | [1015_479f468c5](commits/1015_479f468c5/analysis.md) |
| 1016 | `d9aacd24c` | 2024-08-04 21:45:46 -0500 | Shani Elharrar | Core, API: UpdatePartitionSpec: Added ability to create a new Partition Spec but not set it as the Default | ✅ 已完成 | [1016_d9aacd24c](commits/1016_d9aacd24c/analysis.md) |
| 1017 | `4cfa38fbb` | 2024-08-05 01:14:32 -0500 | dependabot[bot] | Build: Bump com.palantir.baseline:gradle-baseline-java (#10864) | ✅ 已完成 | [1017_4cfa38fbb](commits/1017_4cfa38fbb/analysis.md) |
| 1018 | `98ecc9a9e` | 2024-08-05 08:57:45 +0200 | dependabot[bot] | Build: Bump nessie from 0.94.2 to 0.94.4 (#10869) | ✅ 已完成 | [1018_98ecc9a9e](commits/1018_98ecc9a9e/analysis.md) |
| 1019 | `e8582c0f0` | 2024-08-05 09:02:39 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.46.0.0 to 3.46.0.1 (#10871) | ✅ 已完成 | [1019_e8582c0f0](commits/1019_e8582c0f0/analysis.md) |
| 1020 | `1f2198930` | 2024-08-05 09:08:07 +0200 | dependabot[bot] | Build: Bump org.apache.commons:commons-compress from 1.26.0 to 1.26.2 (#10868) | ✅ 已完成 | [1020_1f2198930](commits/1020_1f2198930/analysis.md) |
| 1021 | `9b70fdfd0` | 2024-08-05 09:08:21 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.26.25 to 2.26.29 (#10866) | ✅ 已完成 | [1021_9b70fdfd0](commits/1021_9b70fdfd0/analysis.md) |
| 1022 | `74a9adbc0` | 2024-08-05 09:08:35 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.30 to 9.5.31 (#10863) | ✅ 已完成 | [1022_74a9adbc0](commits/1022_74a9adbc0/analysis.md) |
| 1023 | `722a350af` | 2024-08-05 09:08:58 +0200 | Robert Stupp | Build: Fix Scala compilation (#10860) | ✅ 已完成 | [1023_722a350af](commits/1023_722a350af/analysis.md) |
| 1024 | `87537f995` | 2024-08-05 09:17:42 +0200 | Piotr Findeisen | Build: Enable FormatStringAnnotation error-prone check (#10856) | ✅ 已完成 | [1024_87537f995](commits/1024_87537f995/analysis.md) |
| 1025 | `5fc1413a5` | 2024-08-05 14:35:07 +0200 | Eduard Tudenhoefner | Core: Use encoding/decoding methods for namespaces and deprecate Splitter/Joiner (#10858) | ✅ 已完成 | [1025_5fc1413a5](commits/1025_5fc1413a5/analysis.md) |
| 1026 | `04c2533f1` | 2024-08-05 20:40:26 +0200 | Eduard Tudenhoefner | Aliyun: Replace assert usage with assertThat (#10880) | ✅ 已完成 | [1026_04c2533f1](commits/1026_04c2533f1/analysis.md) |
| 1027 | `b531e97f6` | 2024-08-05 13:43:34 -0700 | Denys Kuzmenko | Core: Extract filePath comparator into it's own class (#10664) | ✅ 已完成 | [1027_b531e97f6](commits/1027_b531e97f6/analysis.md) |
| 1028 | `3d364f6d9` | 2024-08-05 13:50:21 -0700 | k.nakagaki | Docs: Fix SQL in branching docs (#10876) | ✅ 已完成 | [1028_3d364f6d9](commits/1028_3d364f6d9/analysis.md) |
| 1029 | `e9364faab` | 2024-08-05 14:36:20 -0700 | Amogh Jahagirdar | API: Add SupportsRecoveryOperations mixin for FileIO (#10711) | ✅ 已完成 | [1029_e9364faab](commits/1029_e9364faab/analysis.md) |
| 1030 | `525d88781` | 2024-08-05 18:06:36 -0700 | emkornfield | Spec: Clarify identity partition edge cases (#10835) | ✅ 已完成 | [1030_525d88781](commits/1030_525d88781/analysis.md) |
| 1031 | `6ee6d1327` | 2024-08-06 11:16:19 +0200 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.20.0 to 1.20.1 (#10865) | ✅ 已完成 | [1031_6ee6d1327](commits/1031_6ee6d1327/analysis.md) |
| 1032 | `93f7839fa` | 2024-08-06 08:45:56 -0700 | Steven Wu | Flink: move v1.19 to v.120 | ✅ 已完成 | [1032_93f7839fa](commits/1032_93f7839fa/analysis.md) |
| 1033 | `fb60ecde9` | 2024-08-06 08:45:56 -0700 | Steven Wu | Flink: add v1.19 back after coping from 1.20 | ✅ 已完成 | [1033_fb60ecde9](commits/1033_fb60ecde9/analysis.md) |
| 1034 | `0d8f2c42f` | 2024-08-06 08:45:56 -0700 | Steven Wu | Flink: remove v1.17 module | ✅ 已完成 | [1034_0d8f2c42f](commits/1034_0d8f2c42f/analysis.md) |
| 1035 | `38733f8fe` | 2024-08-06 08:45:56 -0700 | Steven Wu | Flink: adjust code for the new 1.20 module. | ✅ 已完成 | [1035_38733f8fe](commits/1035_38733f8fe/analysis.md) |
| 1036 | `257b1d7b1` | 2024-08-06 18:19:12 +0200 | Eduard Tudenhoefner | Build: Add checkstyle rule to ban assert usage (#10886) | ✅ 已完成 | [1036_257b1d7b1](commits/1036_257b1d7b1/analysis.md) |
| 1037 | `86611d94d` | 2024-08-07 11:11:27 +0200 | Fokko Driesprong | Build: Bump Apache Avro to 1.12.0 (#10879) | ✅ 已完成 | [1037_86611d94d](commits/1037_86611d94d/analysis.md) |
| 1038 | `8ec65abdc` | 2024-08-07 13:05:44 +0200 | Piotr Findeisen | Spec: Fix rendering of unified partition struct (#10896) | ✅ 已完成 | [1038_8ec65abdc](commits/1038_8ec65abdc/analysis.md) |
| 1039 | `71b64399d` | 2024-08-07 13:08:23 +0200 | Tom Tanaka | Docs: Fix catalog name for S3 MRAP example (#10897) | ✅ 已完成 | [1039_71b64399d](commits/1039_71b64399d/analysis.md) |
| 1040 | `a3cbdcbae` | 2024-08-07 16:07:38 +0200 | Robert Stupp | Add Flink 1.20 & remove Flink 1.17 in stage-binaries.sh and docs (#10888) | ✅ 已完成 | [1040_a3cbdcbae](commits/1040_a3cbdcbae/analysis.md) |
| 1041 | `97e034b2c` | 2024-08-07 16:08:16 +0200 | Piotr Findeisen | Flink: Remove deprecated RowDataUtil.clone method (#10902) | ✅ 已完成 | [1041_97e034b2c](commits/1041_97e034b2c/analysis.md) |
| 1042 | `3bee806d0` | 2024-08-08 09:59:12 +0200 | Eduard Tudenhoefner | AWS: Fix flaky TestS3RestSigner (#10898) | ✅ 已完成 | [1042_3bee806d0](commits/1042_3bee806d0/analysis.md) |
| 1043 | `70c506eba` | 2024-08-08 14:35:17 -0700 | Amogh Jahagirdar | AWS: Implement SupportsRecoveryOperations mixin for S3FileIO (#10721) | ✅ 已完成 | [1043_70c506eba](commits/1043_70c506eba/analysis.md) |
| 1044 | `d17a7f189` | 2024-08-09 11:34:36 +0200 | Naveen Kumar | Core: Remove deprecated APIs for 1.7.0 (#10818) | ✅ 已完成 | [1044_d17a7f189](commits/1044_d17a7f189/analysis.md) |
| 1045 | `79620e198` | 2024-08-09 16:51:00 +0200 | Naveen Kumar | Core, Flink: Fix build warnings (#10899) | ✅ 已完成 | [1045_79620e198](commits/1045_79620e198/analysis.md) |
| 1046 | `ae08334ca` | 2024-08-12 08:12:20 +0200 | Manu Zhang | Build: Bump Spark 3.5 to 3.5.2 (#10918) | ✅ 已完成 | [1046_ae08334ca](commits/1046_ae08334ca/analysis.md) |
| 1047 | `b4e60e025` | 2024-08-12 15:50:50 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#10915) | ✅ 已完成 | [1047_b4e60e025](commits/1047_b4e60e025/analysis.md) |
| 1048 | `b4fcd4012` | 2024-08-12 15:51:15 +0200 | dependabot[bot] | Build: Bump org.awaitility:awaitility from 4.2.1 to 4.2.2 (#10912) | ✅ 已完成 | [1048_b4fcd4012](commits/1048_b4fcd4012/analysis.md) |
| 1049 | `03c2ce9e3` | 2024-08-12 17:58:48 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.8 to 0.25.9 (#10917) | ✅ 已完成 | [1049_03c2ce9e3](commits/1049_03c2ce9e3/analysis.md) |
| 1050 | `8bc1dde5c` | 2024-08-12 17:59:27 +0200 | dependabot[bot] | Build: Bump nessie from 0.94.4 to 0.95.0 (#10910) | ✅ 已完成 | [1050_8bc1dde5c](commits/1050_8bc1dde5c/analysis.md) |
| 1051 | `8ecaaeba5` | 2024-08-12 13:51:34 -0500 | Fokko Driesprong | Docs: Add Trademark symbol where appropriate (#10921) | ✅ 已完成 | [1051_8ecaaeba5](commits/1051_8ecaaeba5/analysis.md) |
| 1052 | `45bd17294` | 2024-08-12 23:53:48 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.43.0 to 26.44.0 (#10916) | ✅ 已完成 | [1052_45bd17294](commits/1052_45bd17294/analysis.md) |
| 1053 | `994c0fb79` | 2024-08-12 23:54:25 +0200 | dependabot[bot] | Build: Bump org.apache.commons:commons-compress from 1.26.2 to 1.27.0 (#10914) | ✅ 已完成 | [1053_994c0fb79](commits/1053_994c0fb79/analysis.md) |
| 1054 | `33259f946` | 2024-08-12 23:55:59 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.26.29 to 2.27.2 (#10913) | ✅ 已完成 | [1054_33259f946](commits/1054_33259f946/analysis.md) |
| 1055 | `bfab2c334` | 2024-08-13 09:25:34 +0200 | dependabot[bot] | Build: Bump org.xerial.snappy:snappy-java from 1.1.10.5 to 1.1.10.6 (#10911) | ✅ 已完成 | [1055_bfab2c334](commits/1055_bfab2c334/analysis.md) |
| 1056 | `3cd82a7e9` | 2024-08-13 12:58:48 +0200 | gaborkaszab | Docs, Infra: Mount local versioned doc branch for testing (#10838) | ✅ 已完成 | [1056_3cd82a7e9](commits/1056_3cd82a7e9/analysis.md) |
| 1057 | `520e7ffce` | 2024-08-13 08:35:41 -0600 | dongwang | API: Fix JavaDoc typos in Transaction API | ✅ 已完成 | [1057_520e7ffce](commits/1057_520e7ffce/analysis.md) |
| 1058 | `4b57cf8d9` | 2024-08-13 20:32:38 +0200 | hsiang-c | Core: Fix metadata table test to set partition to the right PartitionKey (#10925) | ✅ 已完成 | [1058_4b57cf8d9](commits/1058_4b57cf8d9/analysis.md) |
| 1059 | `cf02ffac4` | 2024-08-13 17:27:32 -0600 | Eduard Tudenhoefner | AWS, Core, Hive: Extract FileIO closing into separate FileIOTracker class (#10893) | ✅ 已完成 | [1059_cf02ffac4](commits/1059_cf02ffac4/analysis.md) |
| 1060 | `8c85a5a7f` | 2024-08-15 14:51:14 +0200 | Naveen Kumar | Build: Suppress various build warnings (#10938) | ✅ 已完成 | [1060_8c85a5a7f](commits/1060_8c85a5a7f/analysis.md) |
| 1061 | `3cd2c528a` | 2024-08-15 09:40:33 -0700 | Steven Zhen Wu |  Core: add JSON serialization for BaseFilesTable.ManifestReadTask, AllManifestsTable.ManifestListReadTask, and BaseEntriesTable.ManifestReadTask (#10735) | ✅ 已完成 | [1061_3cd2c528a](commits/1061_3cd2c528a/analysis.md) |
| 1062 | `9f12cf91d` | 2024-08-15 14:40:43 -0600 | Eduard Tudenhoefner | AWS, Core: Slim down Jetty config for tests (#10945) | ✅ 已完成 | [1062_9f12cf91d](commits/1062_9f12cf91d/analysis.md) |
| 1063 | `a49202773` | 2024-08-16 07:08:49 +0200 | SaketaChalamchala | Docs: Cloudera blog in February 2023 (#10947) | ✅ 已完成 | [1063_a49202773](commits/1063_a49202773/analysis.md) |
| 1064 | `49cf9d988` | 2024-08-16 18:08:25 -0500 | Jonathan Leang | Core: V3 Metadata Upgrade Validation and Testing (#10861) | ✅ 已完成 | [1064_49cf9d988](commits/1064_49cf9d988/analysis.md) |
| 1065 | `10fce2700` | 2024-08-16 17:25:34 -0600 | Amogh Jahagirdar | Spec: Clarify in REST spec that server implementations of commit endpoints must fail with 400 if any unknown updates or requirements are received (#10848) | ✅ 已完成 | [1065_10fce2700](commits/1065_10fce2700/analysis.md) |
| 1066 | `65e7cae51` | 2024-08-18 12:44:58 +0200 | dependabot[bot] | Build: Bump guava from 33.2.1-jre to 33.3.0-jre (#10960) | ✅ 已完成 | [1066_65e7cae51](commits/1066_65e7cae51/analysis.md) |
| 1067 | `d4e0b3f20` | 2024-08-18 12:45:31 +0200 | dependabot[bot] | Build: Bump org.springframework:spring-web from 5.3.37 to 5.3.39 (#10959) | ✅ 已完成 | [1067_d4e0b3f20](commits/1067_d4e0b3f20/analysis.md) |
| 1068 | `b53595b73` | 2024-08-19 16:47:49 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.27.2 to 2.27.7 (#10961) | ✅ 已完成 | [1068_b53595b73](commits/1068_b53595b73/analysis.md) |
| 1069 | `9ddde5077` | 2024-08-19 10:46:20 -0600 | Prashant Singh | Docs: Update MRAP endpoint and add notebook link (#9362) | ✅ 已完成 | [1069_9ddde5077](commits/1069_9ddde5077/analysis.md) |
| 1070 | `ed07fd1cd` | 2024-08-19 14:49:06 -0700 | Steven Zhen Wu | Flink: put everything together for range distribution in Flink sink (#10859) | ✅ 已完成 | [1070_ed07fd1cd](commits/1070_ed07fd1cd/analysis.md) |
| 1071 | `43bbf08ad` | 2024-08-19 15:29:47 -0700 | Steven Zhen Wu | Flink: FLIP-27 IcebergSource builder missed a couple of configs compared to old FlinkSource: expose locality and plan parallelism (#10957) | ✅ 已完成 | [1071_43bbf08ad](commits/1071_43bbf08ad/analysis.md) |
| 1072 | `3028552b4` | 2024-08-20 13:50:00 +0200 | Piotr Findeisen | Prevent implicit default locale/charset usage (#10969) | ✅ 已完成 | [1072_3028552b4](commits/1072_3028552b4/analysis.md) |
| 1073 | `2f2c367b3` | 2024-08-20 15:08:43 +0200 | Fokko Driesprong | Core: Add ManifestWrite benchmark (#8637) | ✅ 已完成 | [1073_2f2c367b3](commits/1073_2f2c367b3/analysis.md) |
| 1074 | `b76d81acf` | 2024-08-20 09:24:08 -0600 | Jason | S3OutputStream: Don't complete multipart upload on finalize (#10874) | ✅ 已完成 | [1074_b76d81acf](commits/1074_b76d81acf/analysis.md) |
| 1075 | `ce3389031` | 2024-08-20 19:21:09 +0200 | Piotr Findeisen | Enable UnusedMethod error-prone check (#10968) | ✅ 已完成 | [1075_ce3389031](commits/1075_ce3389031/analysis.md) |
| 1076 | `24afc1f98` | 2024-08-20 11:54:52 -0600 | JB Onofré | Build: Upgrade to Gradle 8.10 (#10976) | ✅ 已完成 | [1076_24afc1f98](commits/1076_24afc1f98/analysis.md) |
| 1077 | `40d5204fb` | 2024-08-20 10:57:38 -0700 | Steve Lessard | Core: Support case-insensitivity for column names in PartitionSpec (#10678) | ✅ 已完成 | [1077_40d5204fb](commits/1077_40d5204fb/analysis.md) |
| 1078 | `f17c225f6` | 2024-08-21 10:09:23 +0200 | Piotr Findeisen | Core: Remove unused throws declarations (#10974) | ✅ 已完成 | [1078_f17c225f6](commits/1078_f17c225f6/analysis.md) |
| 1079 | `85cf79de0` | 2024-08-21 14:56:32 -0700 | Steven Zhen Wu | Flink: deprecate ReaderFunction with a new Converter interface to simplify user experience (#10956) | ✅ 已完成 | [1079_85cf79de0](commits/1079_85cf79de0/analysis.md) |
| 1080 | `bcb32818d` | 2024-08-22 00:04:57 +0200 | Piotr Findeisen | Drop ParallelIterable's queue low water mark (#10978) | ✅ 已完成 | [1080_bcb32818d](commits/1080_bcb32818d/analysis.md) |
| 1081 | `f1076494c` | 2024-08-22 00:06:05 +0200 | Piotr Findeisen | Check for minimal queue size in ParallelIterable (#10977) | ✅ 已完成 | [1081_f1076494c](commits/1081_f1076494c/analysis.md) |
| 1082 | `2f6e7e637` | 2024-08-21 20:23:03 -0700 | Karuppayya | API, Spark 3.5: Action to compute table stats (#10288) | ✅ 已完成 | [1082_2f6e7e637](commits/1082_2f6e7e637/analysis.md) |
| 1083 | `cbd71ebd1` | 2024-08-21 22:05:23 -0600 | S N Munendra | Core,AWS: Fix NPE in ResolvingFileIO when HadoopConf is not set (#10872) | ✅ 已完成 | [1083_cbd71ebd1](commits/1083_cbd71ebd1/analysis.md) |
| 1084 | `04461781c` | 2024-08-22 15:58:12 +0200 | Manu Zhang | Spark 3.5: Fix incorrect catalog loaded in TestCreateActions (#10952) | ✅ 已完成 | [1084_04461781c](commits/1084_04461781c/analysis.md) |
| 1085 | `b2cd6f38e` | 2024-08-22 16:19:31 +0200 | pvary | Flink: Maintenance - TriggerManager (#10484) | ✅ 已完成 | [1085_b2cd6f38e](commits/1085_b2cd6f38e/analysis.md) |
| 1086 | `7fec19f3f` | 2024-08-22 07:39:07 -0700 | Steven Zhen Wu | Flink: backport PR #10956 for converter interface that deprecates ReaderFunction (#10985) | ✅ 已完成 | [1086_7fec19f3f](commits/1086_7fec19f3f/analysis.md) |
| 1087 | `bf459eed4` | 2024-08-22 13:11:16 -0700 | Steven Zhen Wu | Flink: backport PR #10777 from 1.19 to 1.18 for sink test refactoring. (#10965) | ✅ 已完成 | [1087_bf459eed4](commits/1087_bf459eed4/analysis.md) |
| 1088 | `ac0d20635` | 2024-08-22 13:16:05 -0700 | Ryan Blue | Spec: Minor modifications for v3 (#10948) | ✅ 已完成 | [1088_ac0d20635](commits/1088_ac0d20635/analysis.md) |
| 1089 | `ce772a6ec` | 2024-08-22 14:59:48 -0700 | Steven Zhen Wu | Flink: backport PR #10859 for range distribution (#10990) | ✅ 已完成 | [1089_ce772a6ec](commits/1089_ce772a6ec/analysis.md) |
| 1090 | `f2d62757e` | 2024-08-23 15:42:24 +0200 | pvary | Flink: Port #10484 to v1.20 (#10989) | ✅ 已完成 | [1090_f2d62757e](commits/1090_f2d62757e/analysis.md) |
| 1091 | `aa1ecc817` | 2024-08-23 17:19:58 +0200 | pvary | Flink: Maintenance - TableChange refactor (#10992) | ✅ 已完成 | [1091_aa1ecc817](commits/1091_aa1ecc817/analysis.md) |
| 1092 | `2424e2c31` | 2024-08-23 17:51:00 +0200 | pvary | Flink: Port #10992 to v1.19 (#10994) | ✅ 已完成 | [1092_2424e2c31](commits/1092_2424e2c31/analysis.md) |
| 1093 | `e0596fbba` | 2024-08-23 20:26:29 +0200 | Eduard Tudenhoefner | OpenAPI: Add endpoint field to CatalogConfig (#10928) | ✅ 已完成 | [1093_e0596fbba](commits/1093_e0596fbba/analysis.md) |
| 1094 | `586485008` | 2024-08-23 17:31:20 -0700 | Anton Okolnychyi | Spark 3.5: Add utility to load table state reliably (#10984) | ✅ 已完成 | [1094_586485008](commits/1094_586485008/analysis.md) |
| 1095 | `b9a6645a5` | 2024-08-24 20:47:35 -0600 | Akira Ajisaka | AWS: Include http-auth-aws-crt module into iceberg-aws-bundle (#10972) | ✅ 已完成 | [1095_b9a6645a5](commits/1095_b9a6645a5/analysis.md) |
| 1096 | `5958065b0` | 2024-08-25 13:58:53 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.27.7 to 2.27.12 (#11006) | ✅ 已完成 | [1096_5958065b0](commits/1096_5958065b0/analysis.md) |
| 1097 | `9aa354d22` | 2024-08-25 14:00:52 +0200 | dependabot[bot] | Build: Bump org.apache.commons:commons-compress from 1.27.0 to 1.27.1 (#11005) | ✅ 已完成 | [1097_9aa354d22](commits/1097_9aa354d22/analysis.md) |
| 1098 | `4af2b9e7a` | 2024-08-26 10:47:38 +0200 | dependabot[bot] | Build: Bump jetty from 11.0.22 to 11.0.23 (#11003) | ✅ 已完成 | [1098_4af2b9e7a](commits/1098_4af2b9e7a/analysis.md) |
| 1099 | `244eb1e8d` | 2024-08-26 10:47:57 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.31 to 9.5.33 (#11002) | ✅ 已完成 | [1099_244eb1e8d](commits/1099_244eb1e8d/analysis.md) |
| 1100 | `524fbb895` | 2024-08-26 10:49:36 +0200 | Yuya Ebihara | Docs: `_commit_snapshot_id` instead of `_change_snapshot_id` (#11000) | ✅ 已完成 | [1100_524fbb895](commits/1100_524fbb895/analysis.md) |
| 1101 | `99e4ab711` | 2024-08-26 10:50:17 +0200 | Yuya Ebihara | Docs: Rename Clickhouse to ClickHouse (#10998) | ✅ 已完成 | [1101_99e4ab711](commits/1101_99e4ab711/analysis.md) |
| 1102 | `2ed61a12b` | 2024-08-26 08:01:13 -0700 | Steven Zhen Wu | Flink: infer source parallelism for FLIP-27 source in batch execution mode (#10832) | ✅ 已完成 | [1102_2ed61a12b](commits/1102_2ed61a12b/analysis.md) |
| 1103 | `a7398aba2` | 2024-08-26 10:05:16 -0700 | Qishang Zhong | Flink: Fix duplicate data with upsert writer in case of aborted checkpoints (#10526) | ✅ 已完成 | [1103_a7398aba2](commits/1103_a7398aba2/analysis.md) |
| 1104 | `bea364c36` | 2024-08-26 13:28:46 -0700 | Rodrigo | Introduces the new IcebergSink based on the new V2 Flink Sink Abstraction (#10179) | ✅ 已完成 | [1104_bea364c36](commits/1104_bea364c36/analysis.md) |
| 1105 | `e6f8ab995` | 2024-08-26 15:57:57 -0700 | Rodrigo |  Flink: Backport PR #10179 to Flink 1.20 for v2 sink (#11011) | ✅ 已完成 | [1105_e6f8ab995](commits/1105_e6f8ab995/analysis.md) |
| 1106 | `1898e6216` | 2024-08-26 17:30:47 -0700 | Anton Okolnychyi | Core: Project data file stats only if there are equality deletes (#11013) | ✅ 已完成 | [1106_1898e6216](commits/1106_1898e6216/analysis.md) |
| 1107 | `f1764c689` | 2024-08-26 20:38:25 -0600 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.46.0.1 to 3.46.1.0 (#11007) | ✅ 已完成 | [1107_f1764c689](commits/1107_f1764c689/analysis.md) |
| 1108 | `64b36999d` | 2024-08-26 20:40:20 -0600 | Charles Smith | Docs: Add Druid docs url to sidebar (#10997) | ✅ 已完成 | [1108_64b36999d](commits/1108_64b36999d/analysis.md) |
| 1109 | `bf00d51e4` | 2024-08-27 08:33:42 -0700 | Steven Zhen Wu | Flink: backport PR #10832 of inferring parallelism in FLIP-27 source (#11009) | ✅ 已完成 | [1109_bf00d51e4](commits/1109_bf00d51e4/analysis.md) |
| 1110 | `c95bf5879` | 2024-08-27 09:29:55 -0700 | Daniel Weeks | Add REST Compatibility Kit (#10908) | ✅ 已完成 | [1110_c95bf5879](commits/1110_c95bf5879/analysis.md) |
| 1111 | `f88f128dd` | 2024-08-27 13:47:53 -0700 | Anton Okolnychyi | Core: Generate realistic bounds in benchmarks (#11022) | ✅ 已完成 | [1111_f88f128dd](commits/1111_f88f128dd/analysis.md) |
| 1112 | `8e2eb9ac2` | 2024-08-27 15:23:09 -0600 | Daniel Weeks | OpenAPI, Build: Apply spotless to testFixtures source code (#11024) | ✅ 已完成 | [1112_8e2eb9ac2](commits/1112_8e2eb9ac2/analysis.md) |
| 1113 | `877f63b0b` | 2024-08-28 06:21:28 -0700 | Carl Steinbach | Docs: bump latest version to 1.6.1 (#11036) | ✅ 已完成 | [1113_877f63b0b](commits/1113_877f63b0b/analysis.md) |
| 1114 | `cd32ec76e` | 2024-08-28 08:40:32 -0700 | Amogh Jahagirdar | Spec: Add RemovePartitionSpecsUpdate REST update type (#10846) | ✅ 已完成 | [1114_cd32ec76e](commits/1114_cd32ec76e/analysis.md) |
| 1115 | `3c018333b` | 2024-08-28 16:50:57 -0700 | Anton Okolnychyi | Build: Ignore benchmark output folders across all modules (#11030) | ✅ 已完成 | [1115_3c018333b](commits/1115_3c018333b/analysis.md) |
| 1116 | `6c7964002` | 2024-08-28 16:51:38 -0700 | Anton Okolnychyi | Core: Add benchmark for appending files (#11029) | ✅ 已完成 | [1116_6c7964002](commits/1116_6c7964002/analysis.md) |
| 1117 | `9c344f96c` | 2024-08-29 08:38:32 -0700 | Anton Okolnychyi | Spark 3.5: Use FileGenerationUtil in PlanningBenchmark (#11027) | ✅ 已完成 | [1117_9c344f96c](commits/1117_9c344f96c/analysis.md) |
| 1118 | `4b71d40cc` | 2024-08-29 09:27:23 -0700 | Steven Zhen Wu | Flink: add unit tests for range distribution on bucket partition column (#11033) | ✅ 已完成 | [1118_4b71d40cc](commits/1118_4b71d40cc/analysis.md) |
| 1119 | `a07f8620b` | 2024-08-29 21:48:25 -0600 | Bryan Keller | Kafka Connect: Disable publish tasks in runtime project (#11032) | ✅ 已完成 | [1119_a07f8620b](commits/1119_a07f8620b/analysis.md) |
| 1120 | `e8c614878` | 2024-08-30 21:53:47 +0300 | Qishang Zhong | Flink: Backport PR #10526 to v1.18 and v1.20 (#11018) | ✅ 已完成 | [1120_e8c614878](commits/1120_e8c614878/analysis.md) |
| 1121 | `fa8fbb3d5` | 2024-09-01 06:39:10 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.33 to 9.5.34 (#11062) | ✅ 已完成 | [1121_fa8fbb3d5](commits/1121_fa8fbb3d5/analysis.md) |
| 1122 | `d128a2a98` | 2024-09-01 07:22:59 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#11055) | ✅ 已完成 | [1122_d128a2a98](commits/1122_d128a2a98/analysis.md) |
| 1123 | `113c6e7d6` | 2024-09-03 08:44:43 -0700 | Jacob Marble | API: implement types timestamp_ns and timestamptz_ns (#9008) | ✅ 已完成 | [1123_113c6e7d6](commits/1123_113c6e7d6/analysis.md) |
| 1124 | `5319767ee` | 2024-09-03 13:25:51 -0500 | Ajantha Bhat | Core: Refactor ZOrderByteUtils (#10624) | ✅ 已完成 | [1124_5319767ee](commits/1124_5319767ee/analysis.md) |
| 1125 | `a4461640c` | 2024-09-03 11:49:45 -0700 | emkornfield | Docs: Initial committer guidelines and requirements for merging (#10780) | ✅ 已完成 | [1125_a4461640c](commits/1125_a4461640c/analysis.md) |
| 1126 | `896dcd50f` | 2024-09-03 22:24:32 +0300 | Ajantha Bhat | Flink: Fix compile warning (#11072) | ✅ 已完成 | [1126_896dcd50f](commits/1126_896dcd50f/analysis.md) |
| 1127 | `7830a3b93` | 2024-09-03 14:07:33 -0700 | Manu Zhang | Docs: Fix Flink 1.20 support versions (#11065) | ✅ 已完成 | [1127_7830a3b93](commits/1127_7830a3b93/analysis.md) |
| 1128 | `4f3704161` | 2024-09-05 08:19:36 +0200 | Piotr Findeisen | Build: Enable more error-prone checks (#11078) | ✅ 已完成 | [1128_4f3704161](commits/1128_4f3704161/analysis.md) |
| 1129 | `2391bdddc` | 2024-09-05 09:08:03 -0700 | Ajantha Bhat | open-api: Fix compile warnings for testFixtures (#11071) | ✅ 已完成 | [1129_2391bdddc](commits/1129_2391bdddc/analysis.md) |
| 1130 | `f508a7ea8` | 2024-09-05 11:15:49 -0600 | Manu Zhang | Spark 3.3, 3.4: Parallelize reading files in migrate procedures (#11043) | ✅ 已完成 | [1130_f508a7ea8](commits/1130_f508a7ea8/analysis.md) |
| 1131 | `f7c6d57a0` | 2024-09-05 11:12:24 -0700 | Hongyue/Steve Zhang | Spark 3.5: Mandate identifier fields when create_changelog_view for table contain unsortable columns (#11045) | ✅ 已完成 | [1131_f7c6d57a0](commits/1131_f7c6d57a0/analysis.md) |
| 1132 | `6e05ae022` | 2024-09-06 21:29:47 -0700 | Anton Okolnychyi | Core: Fix setting hasNewDataFile flag in MergingSnapshotProducer (#11088) | ✅ 已完成 | [1132_6e05ae022](commits/1132_6e05ae022/analysis.md) |
| 1133 | `ab2c6f889` | 2024-09-06 21:33:07 -0700 | Anton Okolnychyi | Docs: Document accessing instance variables (#11087) | ✅ 已完成 | [1133_ab2c6f889](commits/1133_ab2c6f889/analysis.md) |
| 1134 | `44eca04b6` | 2024-09-09 07:53:58 +0200 | dependabot[bot] | Build: Bump jetty from 11.0.23 to 11.0.24 (#11096) | ✅ 已完成 | [1134_44eca04b6](commits/1134_44eca04b6/analysis.md) |
| 1135 | `cefb1bbfa` | 2024-09-09 08:58:26 +0200 | Ajantha Bhat | Spec: Fix rendering of partition stats file section (#11068) | ✅ 已完成 | [1135_cefb1bbfa](commits/1135_cefb1bbfa/analysis.md) |
| 1136 | `3fe4f420d` | 2024-09-09 10:44:36 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.27.12 to 2.27.21 (#11098) | ✅ 已完成 | [1136_3fe4f420d](commits/1136_3fe4f420d/analysis.md) |
| 1137 | `ed73ec43d` | 2024-09-09 10:44:56 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.112.Final to 4.1.113.Final (#11097) | ✅ 已完成 | [1137_ed73ec43d](commits/1137_ed73ec43d/analysis.md) |
| 1138 | `153b07069` | 2024-09-09 16:21:32 +0200 | Manu Zhang | Spark 3.3, 3.4: Fix incorrect catalog loaded in TestCreateActions (#11049) | ✅ 已完成 | [1138_153b07069](commits/1138_153b07069/analysis.md) |
| 1139 | `41d00ae64` | 2024-09-09 10:03:05 -0600 | dongwang | Core: Prevent incremental file cleanup when expiring specified snapshots (#10983) | ✅ 已完成 | [1139_41d00ae64](commits/1139_41d00ae64/analysis.md) |
| 1140 | `4873b4b75` | 2024-09-10 08:05:11 +0200 | Eduard Tudenhoefner | Core, Kafka, Spark: Use AssertJ instead of JUnit assertions (#11102) | ✅ 已完成 | [1140_4873b4b75](commits/1140_4873b4b75/analysis.md) |
| 1141 | `026166f22` | 2024-09-10 11:13:06 +0200 | Robin Moffatt | Docs: Add blogs written by rmoff (#11069) | ✅ 已完成 | [1141_026166f22](commits/1141_026166f22/analysis.md) |
| 1142 | `e40fe4cdb` | 2024-09-10 11:13:39 +0200 | JB Onofré | Build: Upgrade to Gradle 8.10.1 (#11104) | ✅ 已完成 | [1142_e40fe4cdb](commits/1142_e40fe4cdb/analysis.md) |
| 1143 | `a5c8f9cd4` | 2024-09-10 11:59:26 +0200 | Piotr Findeisen | Build: Remove unused variables, fields and parameters (#11101) | ✅ 已完成 | [1143_a5c8f9cd4](commits/1143_a5c8f9cd4/analysis.md) |
| 1144 | `8d97d5475` | 2024-09-10 08:34:33 -0600 | Rahil C | OpenAPI: Add Scan Planning Endpoints to REST spec (#9695) | ✅ 已完成 | [1144_8d97d5475](commits/1144_8d97d5475/analysis.md) |
| 1145 | `5439cbdb2` | 2024-09-10 09:42:31 -0700 | Bryan Keller | Kafka Connect: Docs on configuring the sink (#10746) | ✅ 已完成 | [1145_5439cbdb2](commits/1145_5439cbdb2/analysis.md) |
| 1146 | `0747b6044` | 2024-09-10 15:54:20 -0700 | Bryan Keller | Kafka Connect: Terminate commits on coordinator stop (#10814) | ✅ 已完成 | [1146_0747b6044](commits/1146_0747b6044/analysis.md) |
| 1147 | `34cd01ba2` | 2024-09-11 12:08:49 +0200 | Manu Zhang | Build: Upgrade google-java-format to 1.22.0 (#11050) | ✅ 已完成 | [1147_34cd01ba2](commits/1147_34cd01ba2/analysis.md) |
| 1148 | `6ff7a6ec2` | 2024-09-12 07:17:58 +0200 | pvary | Flink: Maintenance - Lock remover (#11010) | ✅ 已完成 | [1148_6ff7a6ec2](commits/1148_6ff7a6ec2/analysis.md) |
| 1149 | `8b4b2c197` | 2024-09-12 08:54:10 +0200 | Daniel Weeks | Docs: Update Project links to includ contributing and REST spec (#11114) | ✅ 已完成 | [1149_8b4b2c197](commits/1149_8b4b2c197/analysis.md) |
| 1150 | `ab0594bf7` | 2024-09-12 15:56:17 +0200 | pvary | Flink: Port #10484 to v1.19 (#11010) (#11117) | ✅ 已完成 | [1150_ab0594bf7](commits/1150_ab0594bf7/analysis.md) |
| 1151 | `e3d3f8845` | 2024-09-12 14:56:26 -0700 | Daniel Weeks | OpenAPI: Fix YAML example and value json formatting (#11119) | ✅ 已完成 | [1151_e3d3f8845](commits/1151_e3d3f8845/analysis.md) |
| 1152 | `d2087a04b` | 2024-09-12 16:41:05 -0700 | Anton Okolnychyi | Core: Parallelize manifest writing for many new files (#11086) | ✅ 已完成 | [1152_d2087a04b](commits/1152_d2087a04b/analysis.md) |
| 1153 | `799120636` | 2024-09-12 22:50:54 -0600 | Amogh Jahagirdar | API, Core: Add manifestLocation API to ContentFile (#11044) | ✅ 已完成 | [1153_799120636](commits/1153_799120636/analysis.md) |
| 1154 | `a2b8008da` | 2024-09-13 18:04:14 +0200 | Eduard Tudenhoefner | Core: Allow servers to express supported endpoints via endpoint field in ConfigResponse (#10929) | ✅ 已完成 | [1154_a2b8008da](commits/1154_a2b8008da/analysis.md) |
| 1155 | `e449d3405` | 2024-09-13 18:15:18 +0200 | Naveen Kumar | Hive: Add View support for HIVE catalog (#9852) | ✅ 已完成 | [1155_e449d3405](commits/1155_e449d3405/analysis.md) |
| 1156 | `5582b0ca6` | 2024-09-13 11:27:18 -0700 | Karuppayya | Spark 3.4: Action to compute table stats (#11106) | ✅ 已完成 | [1156_5582b0ca6](commits/1156_5582b0ca6/analysis.md) |
| 1157 | `2e4d5b5b2` | 2024-09-13 22:56:21 -0600 | Manu Zhang | Docs: Fix missing options for remove_orphan_files procedure (#11080) | ✅ 已完成 | [1157_2e4d5b5b2](commits/1157_2e4d5b5b2/analysis.md) |
| 1158 | `5ce7c3091` | 2024-09-16 09:44:10 -0700 | Steven Zhen Wu | Flink: Increase the number of checkpoints from 4 to 6 to fix flakiness. (#11121) | ✅ 已完成 | [1158_5ce7c3091](commits/1158_5ce7c3091/analysis.md) |
| 1159 | `d5b21d82e` | 2024-09-16 14:46:24 -0700 | Hongyue/Steve Zhang | Spark 3.4: Add utility to load table state reliably (#11115) | ✅ 已完成 | [1159_d5b21d82e](commits/1159_d5b21d82e/analysis.md) |
| 1160 | `06ed235f9` | 2024-09-17 11:13:30 -0500 | Steven Zhen Wu | Build: switch to slf4j-simple 2.x for test implementation dependency because avro 1.12.0 brings in slf4j-api dependency to 2.x (#11001) | ✅ 已完成 | [1160_06ed235f9](commits/1160_06ed235f9/analysis.md) |
| 1161 | `f71c7dfb0` | 2024-09-18 07:42:23 +0200 | Eduard Tudenhoefner | Core: Update metadata location without updating lastUpdatedMillis (#11151) | ✅ 已完成 | [1161_f71c7dfb0](commits/1161_f71c7dfb0/analysis.md) |
| 1162 | `40ffcb9ad` | 2024-09-18 08:52:16 +0200 | Bryan Keller | Kafka Connect: separate CI workflow (#11075) | ✅ 已完成 | [1162_40ffcb9ad](commits/1162_40ffcb9ad/analysis.md) |
| 1163 | `bbeadea75` | 2024-09-18 08:18:41 -0700 | Ryan Blue | Core: Move internal struct projection to SupportsIndexProjection (#11132) | ✅ 已完成 | [1163_bbeadea75](commits/1163_bbeadea75/analysis.md) |
| 1164 | `e3088bc09` | 2024-09-19 07:48:29 +0200 | Eduard Tudenhoefner | Core: Add explicit JSON parser for LoadTableResponse (#11148) | ✅ 已完成 | [1164_e3088bc09](commits/1164_e3088bc09/analysis.md) |
| 1165 | `ffa13b12e` | 2024-09-19 08:33:12 +0200 | Yujiang Zhong | Build: Add .java-version to gitignore (#11167) | ✅ 已完成 | [1165_ffa13b12e](commits/1165_ffa13b12e/analysis.md) |
| 1166 | `e5d9a1594` | 2024-09-19 09:15:16 +0200 | Jason Fehr | Docs: Clarify Partition Transform (#8337) | ✅ 已完成 | [1166_e5d9a1594](commits/1166_e5d9a1594/analysis.md) |
| 1167 | `82cedbbb1` | 2024-09-19 15:38:03 -0500 | Anton Okolnychyi | API, Core: Enable removing rewritten delete files in RowDelta (#11166) | ✅ 已完成 | [1167_82cedbbb1](commits/1167_82cedbbb1/analysis.md) |
| 1168 | `79fd977f6` | 2024-09-19 15:40:12 -0500 | jonaswk | Docs: `field_id` in name serialisation spec should read `field-id` (#11135) | ✅ 已完成 | [1168_79fd977f6](commits/1168_79fd977f6/analysis.md) |
| 1169 | `60f61c3dd` | 2024-09-20 11:03:00 +0200 | sullis | AWS: Bump AWS SDK to version 2.28.5 (#11170) | ✅ 已完成 | [1169_60f61c3dd](commits/1169_60f61c3dd/analysis.md) |
| 1170 | `d4af40c9b` | 2024-09-20 11:03:33 +0200 | dependabot[bot] | Build: Bump org.xerial.snappy:snappy-java from 1.1.10.6 to 1.1.10.7 (#11140) | ✅ 已完成 | [1170_d4af40c9b](commits/1170_d4af40c9b/analysis.md) |
| 1171 | `b2b65df4c` | 2024-09-20 15:17:28 +0200 | dongwang | Spark 3.3, 3.4, 3.5: Supplement test case for `RollbackToTimestampProcedure` (#11171) | ✅ 已完成 | [1171_b2b65df4c](commits/1171_b2b65df4c/analysis.md) |
| 1172 | `4482565d0` | 2024-09-20 13:36:24 -0600 | Yuya Ebihara | Docs: Uppercase SQL keywords in branching docs (#11172) | ✅ 已完成 | [1172_4482565d0](commits/1172_4482565d0/analysis.md) |
| 1173 | `b92ed13d6` | 2024-09-23 14:42:47 +0200 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#11186) | ✅ 已完成 | [1173_b92ed13d6](commits/1173_b92ed13d6/analysis.md) |
| 1174 | `5ed930791` | 2024-09-23 14:43:16 +0200 | dependabot[bot] | Build: Bump tez010 from 0.10.3 to 0.10.4 (#11183) | ✅ 已完成 | [1174_5ed930791](commits/1174_5ed930791/analysis.md) |
| 1175 | `fc6271ca2` | 2024-09-23 14:43:30 +0200 | dependabot[bot] | Build: Bump nessie from 0.95.0 to 0.97.1 (#11184) | ✅ 已完成 | [1175_fc6271ca2](commits/1175_fc6271ca2/analysis.md) |
| 1176 | `ddfe503de` | 2024-09-23 14:43:45 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.44.0 to 26.47.0 (#11185) | ✅ 已完成 | [1176_ddfe503de](commits/1176_ddfe503de/analysis.md) |
| 1177 | `5a2c1c9c8` | 2024-09-23 14:44:07 +0200 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.2.1 to 1.3.0 (#11187) | ✅ 已完成 | [1177_5a2c1c9c8](commits/1177_5a2c1c9c8/analysis.md) |
| 1178 | `257bad29d` | 2024-09-23 14:21:38 -0600 | Amogh Jahagirdar | API: Deprecate ContentFile#path API and add location API which returns String (#11092) | ✅ 已完成 | [1178_257bad29d](commits/1178_257bad29d/analysis.md) |
| 1179 | `72fd9ab9f` | 2024-09-23 18:41:39 -0600 | Prashant Singh | Docs: Document AWS Redshift and Amazon Data Firehose support (#11192) | ✅ 已完成 | [1179_72fd9ab9f](commits/1179_72fd9ab9f/analysis.md) |
| 1180 | `c0d73f4ef` | 2024-09-24 10:26:55 -0600 | Amogh Jahagirdar | API, AWS: Retry S3InputStream reads (#10433) | ✅ 已完成 | [1180_c0d73f4ef](commits/1180_c0d73f4ef/analysis.md) |
| 1181 | `983ede397` | 2024-09-25 11:13:09 +0200 | Laurent Goujon | AWS: Fix AWS doc URL (#11198) | ✅ 已完成 | [1181_983ede397](commits/1181_983ede397/analysis.md) |
| 1182 | `c07de6fff` | 2024-09-25 11:13:39 +0200 | dependabot[bot] | Build: Bump mkdocs-macros-plugin from 1.0.5 to 1.2.0 (#11189) | ✅ 已完成 | [1182_c07de6fff](commits/1182_c07de6fff/analysis.md) |
| 1183 | `2fa8c7d86` | 2024-09-25 08:30:06 -0700 | Anton Okolnychyi | Core: Add rewritten delete files to write results (#11203) | ✅ 已完成 | [1183_2fa8c7d86](commits/1183_2fa8c7d86/analysis.md) |
| 1184 | `474a770aa` | 2024-09-25 08:30:56 -0700 | Anton Okolnychyi | Core: Support iterating over positions in PositionDeleteIndex (#11202) | ✅ 已完成 | [1184_474a770aa](commits/1184_474a770aa/analysis.md) |
| 1185 | `f3c784e16` | 2024-09-25 17:06:21 -0500 | aleenamg21-1 | Spark: Added merge schema as spark configuration (#9640) | ✅ 已完成 | [1185_f3c784e16](commits/1185_f3c784e16/analysis.md) |
| 1186 | `1e5dcb1f6` | 2024-09-25 16:22:42 -0700 | Anton Okolnychyi | Core: Support merging in PositionDeleteIndex (#11208) | ✅ 已完成 | [1186_1e5dcb1f6](commits/1186_1e5dcb1f6/analysis.md) |
| 1187 | `b1d38b3ca` | 2024-09-25 18:34:30 -0600 | Wing Yew Poon | Core: Remove unused code for streaming position deletes (#11175) | ✅ 已完成 | [1187_b1d38b3ca](commits/1187_b1d38b3ca/analysis.md) |
| 1188 | `26648ae20` | 2024-09-26 09:31:36 +0200 | JB Onofré | Build: Upgrade to Gradle 8.10.2 (#11212) | ✅ 已完成 | [1188_26648ae20](commits/1188_26648ae20/analysis.md) |
| 1189 | `7bd13a32f` | 2024-09-26 09:14:49 -0700 | Ajantha Bhat | Core: Add a util to compute partition stats (#11146) | ✅ 已完成 | [1189_7bd13a32f](commits/1189_7bd13a32f/analysis.md) |
| 1190 | `2d9c344b5` | 2024-09-26 11:47:28 -0500 | Aihua Xu | Parquet: update PruneColumns to inherit from TypeWithSchemaVisitor to have Iceberg type (#11179) | ✅ 已完成 | [1190_2d9c344b5](commits/1190_2d9c344b5/analysis.md) |
| 1191 | `95497abe5` | 2024-09-26 11:44:14 -0600 | Amogh Jahagirdar | Core: Replace use of CharSequenceMap in DeleteFileIndex with String (#11199) | ✅ 已完成 | [1191_95497abe5](commits/1191_95497abe5/analysis.md) |
| 1192 | `dddb5f423` | 2024-09-26 18:13:26 -0600 | Anurag Mantripragada | [Core] Fix TestFastAppend.testAddManyFiles() (#11218) | ✅ 已完成 | [1192_dddb5f423](commits/1192_dddb5f423/analysis.md) |
| 1193 | `09370ddbc` | 2024-09-26 21:36:58 -0700 | Ajantha Bhat | Spark: Deprecate SparkAppenderFactory (#11076) | ✅ 已完成 | [1193_09370ddbc](commits/1193_09370ddbc/analysis.md) |
| 1194 | `9601784d6` | 2024-09-30 12:46:29 +0200 | dependabot[bot] | Build: Bump guava from 33.3.0-jre to 33.3.1-jre (#11230) | ✅ 已完成 | [1194_9601784d6](commits/1194_9601784d6/analysis.md) |
| 1195 | `570af254c` | 2024-09-30 12:46:42 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.28.5 to 2.28.11 (#11229) | ✅ 已完成 | [1195_570af254c](commits/1195_570af254c/analysis.md) |
| 1196 | `152f02de5` | 2024-09-30 12:47:12 +0200 | dependabot[bot] | Build: Bump io.delta:delta-standalone_2.12 from 3.2.0 to 3.2.1 (#11228) | ✅ 已完成 | [1196_152f02de5](commits/1196_152f02de5/analysis.md) |
| 1197 | `d00c4938a` | 2024-09-30 12:47:29 +0200 | dependabot[bot] | Build: Bump junit-platform from 1.10.3 to 1.11.1 (#11227) | ✅ 已完成 | [1197_d00c4938a](commits/1197_d00c4938a/analysis.md) |
| 1198 | `3ce09bc5f` | 2024-09-30 15:02:17 +0200 | dependabot[bot] | Build: Bump io.delta:delta-spark_2.12 from 3.2.0 to 3.2.1 (#11225) | ✅ 已完成 | [1198_3ce09bc5f](commits/1198_3ce09bc5f/analysis.md) |
| 1199 | `9454927dd` | 2024-09-30 10:21:56 -0600 | Eduard Tudenhoefner | Core: Improve error handling when parsing view representations (#11236) | ✅ 已完成 | [1199_9454927dd](commits/1199_9454927dd/analysis.md) |
| 1200 | `97c9c535f` | 2024-09-30 16:07:20 -0600 | rcjverhoef | Core: Update REST CatalogHandlers to handle page sizes exceeding number of Namespaces/Tables/Views (#11143) | ✅ 已完成 | [1200_97c9c535f](commits/1200_97c9c535f/analysis.md) |
| 1201 | `c8fe01e71` | 2024-09-30 21:12:56 -0700 | Anton Okolnychyi | Core: Support combining position deletes during writes (#11222) | ✅ 已完成 | [1201_c8fe01e71](commits/1201_c8fe01e71/analysis.md) |
| 1202 | `e4bc593d4` | 2024-10-01 08:14:34 +0200 | Eduard Tudenhoefner | Core: Add DataFileSet / DeleteFileSet (#11195) | ✅ 已完成 | [1202_e4bc593d4](commits/1202_e4bc593d4/analysis.md) |
| 1203 | `e8a11cbf9` | 2024-10-01 08:18:13 +0200 | dependabot[bot] | Build: Bump nessie from 0.97.1 to 0.99.0 (#11224) | ✅ 已完成 | [1203_e8a11cbf9](commits/1203_e8a11cbf9/analysis.md) |
| 1204 | `168a98394` | 2024-10-01 09:38:32 -0700 | fengjiajie | ThreadPools introduce newExitingWorkerPool and newFixedThreadPool for clearer semantics (#11073) | ✅ 已完成 | [1204_168a98394](commits/1204_168a98394/analysis.md) |
| 1205 | `8520b5bc7` | 2024-10-01 10:44:19 -0700 | Anton Okolnychyi | Core: Deprecate legacy ways for loading position deletes (#11242) | ✅ 已完成 | [1205_8520b5bc7](commits/1205_8520b5bc7/analysis.md) |
| 1206 | `09c737656` | 2024-10-01 15:28:52 -0700 | Ozan Okumusoglu | AWS: Add configuration and set defaults for S3 retry behaviour (#11052) | ✅ 已完成 | [1206_09c737656](commits/1206_09c737656/analysis.md) |
| 1207 | `4099a671c` | 2024-10-03 03:49:41 -0700 | Piotr Findeisen | Puffin: Document stats `ndv` value representation (#10793) | ✅ 已完成 | [1207_4099a671c](commits/1207_4099a671c/analysis.md) |
| 1208 | `fd8cb7105` | 2024-10-03 03:59:21 -0700 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.34 to 9.5.38 (#11233) | ✅ 已完成 | [1208_fd8cb7105](commits/1208_fd8cb7105/analysis.md) |
| 1209 | `2b38e0934` | 2024-10-03 04:08:28 -0700 | dependabot[bot] | Build: Bump org.eclipse.microprofile.openapi:microprofile-openapi-api (#11182) | ✅ 已完成 | [1209_2b38e0934](commits/1209_2b38e0934/analysis.md) |
| 1210 | `b7b0a4681` | 2024-10-03 04:52:59 -0700 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.46.1.0 to 3.46.1.3 (#11231) | ✅ 已完成 | [1210_b7b0a4681](commits/1210_b7b0a4681/analysis.md) |
| 1211 | `746e71931` | 2024-10-04 03:51:57 -0700 | Piotr Findeisen | Build: Update baseline-java 5.69.0 (#11252) | ✅ 已完成 | [1211_746e71931](commits/1211_746e71931/analysis.md) |
| 1212 | `f6cdf9409` | 2024-10-04 03:53:06 -0700 | Piotr Findeisen | Build: Forbid implicit case fall-through without a comment and enable couple more recommendable error-prone checks (#11251) | ✅ 已完成 | [1212_f6cdf9409](commits/1212_f6cdf9409/analysis.md) |
| 1213 | `8190ce7e6` | 2024-10-04 14:56:33 -0700 | Walaa Eldin Moustafa | API, Core: Add default value APIs and Avro implementation (#9502) | ✅ 已完成 | [1213_8190ce7e6](commits/1213_8190ce7e6/analysis.md) |
| 1214 | `745e819f3` | 2024-10-07 08:48:53 +0200 | hsiang-c | AWS: Make sure overridden configurations are applied (#11274) | ✅ 已完成 | [1214_745e819f3](commits/1214_745e819f3/analysis.md) |
| 1215 | `5dde68079` | 2024-10-07 09:25:30 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.47.0 to 26.48.0 (#11271) | ✅ 已完成 | [1215_5dde68079](commits/1215_5dde68079/analysis.md) |
| 1216 | `3220fad98` | 2024-10-07 11:05:59 -0500 | Yujiang Zhong | Core: Fix UnicodeUtil#truncateStringMax returns malformed string. (#11161) | ✅ 已完成 | [1216_3220fad98](commits/1216_3220fad98/analysis.md) |
| 1217 | `f0e4fd2f5` | 2024-10-07 16:12:39 -0700 | Ryan Blue | Core: Add internal Avro reader (#11108) | ✅ 已完成 | [1217_f0e4fd2f5](commits/1217_f0e4fd2f5/analysis.md) |
| 1218 | `208ab20dc` | 2024-10-08 09:21:50 +0200 | Wing Yew Poon | Arrow: Remove unused readers (#11276) | ✅ 已完成 | [1218_208ab20dc](commits/1218_208ab20dc/analysis.md) |
| 1219 | `67dc9e58c` | 2024-10-09 14:19:40 -0700 | Ryan Blue | Spec: Add v3 types and type promotion (#10955) | ✅ 已完成 | [1219_67dc9e58c](commits/1219_67dc9e58c/analysis.md) |
| 1220 | `d7f668ab8` | 2024-10-12 21:06:54 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.38 to 9.5.39 (#11272) | ✅ 已完成 | [1220_d7f668ab8](commits/1220_d7f668ab8/analysis.md) |
| 1221 | `5fa3bbeec` | 2024-10-12 21:07:15 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#11270) | ✅ 已完成 | [1221_5fa3bbeec](commits/1221_5fa3bbeec/analysis.md) |
| 1222 | `410477fd9` | 2024-10-12 21:07:58 +0200 | dependabot[bot] | Build: Bump junit-platform from 1.11.1 to 1.11.2 (#11266) | ✅ 已完成 | [1222_410477fd9](commits/1222_410477fd9/analysis.md) |
| 1223 | `28265cd7c` | 2024-10-12 21:08:06 +0200 | dependabot[bot] | Build: Bump org.testcontainers:testcontainers from 1.20.1 to 1.20.2 (#11265) | ✅ 已完成 | [1223_28265cd7c](commits/1223_28265cd7c/analysis.md) |
| 1224 | `d93677a3f` | 2024-10-12 21:09:18 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.113.Final to 4.1.114.Final (#11269) | ✅ 已完成 | [1224_d93677a3f](commits/1224_d93677a3f/analysis.md) |
| 1225 | `7e5caf1c6` | 2024-10-12 21:09:25 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.28.11 to 2.28.16 (#11268) | ✅ 已完成 | [1225_7e5caf1c6](commits/1225_7e5caf1c6/analysis.md) |
| 1226 | `337d05b8d` | 2024-10-12 21:09:36 +0200 | dependabot[bot] | Build: Bump jackson-bom from 2.14.2 to 2.18.0 (#11226) | ✅ 已完成 | [1226_337d05b8d](commits/1226_337d05b8d/analysis.md) |
| 1227 | `d3e015822` | 2024-10-12 21:09:44 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.25.9 to 0.26.1 (#11234) | ✅ 已完成 | [1227_d3e015822](commits/1227_d3e015822/analysis.md) |
| 1228 | `0a1a6665c` | 2024-10-12 21:10:14 +0200 | dependabot[bot] | Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#9705) | ✅ 已完成 | [1228_0a1a6665c](commits/1228_0a1a6665c/analysis.md) |
| 1229 | `7a7d150bb` | 2024-10-12 21:11:54 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.18.0 to 3.19.0 (#11057) | ✅ 已完成 | [1229_7a7d150bb](commits/1229_7a7d150bb/analysis.md) |
| 1230 | `79120354e` | 2024-10-12 21:15:41 +0200 | dependabot[bot] | Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#11061) | ✅ 已完成 | [1230_79120354e](commits/1230_79120354e/analysis.md) |
| 1231 | `12ff959cc` | 2024-10-14 07:53:40 +0200 | Wing Yew Poon | Arrow: Deprecate unused fixed width binary reader classes (#11292) | ✅ 已完成 | [1231_12ff959cc](commits/1231_12ff959cc/analysis.md) |
| 1232 | `ca8a3a4d2` | 2024-10-14 08:47:29 +0200 | Yujiang Zhong | API, Spark: Make StrictMetricsEvaluator not fail on nested column predicates (#11261) | ✅ 已完成 | [1232_ca8a3a4d2](commits/1232_ca8a3a4d2/analysis.md) |
| 1233 | `5832a7adb` | 2024-10-14 12:46:25 +0200 | Ajantha Bhat | Build: Use the active shadow plugin (#11315) | ✅ 已完成 | [1233_5832a7adb](commits/1233_5832a7adb/analysis.md) |
| 1234 | `c55a078fc` | 2024-10-14 12:46:54 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.28.16 to 2.28.21 (#11311) | ✅ 已完成 | [1234_c55a078fc](commits/1234_c55a078fc/analysis.md) |
| 1235 | `919387f12` | 2024-10-14 14:37:24 +0200 | dependabot[bot] | Build: Bump org.apache.datasketches:datasketches-java (#11307) | ✅ 已完成 | [1235_919387f12](commits/1235_919387f12/analysis.md) |
| 1236 | `6a5ae1ae6` | 2024-10-14 09:54:33 -0700 | Eduard Tudenhoefner | Core: Switch usage to DataFileSet / DeleteFileSet (#11158) | ✅ 已完成 | [1236_6a5ae1ae6](commits/1236_6a5ae1ae6/analysis.md) |
| 1237 | `3d9fc1dee` | 2024-10-14 10:07:44 -0700 | S N Munendra | [AWS] S3FileIO - Add Cross-Region Bucket Access (#11259) | ✅ 已完成 | [1237_3d9fc1dee](commits/1237_3d9fc1dee/analysis.md) |
| 1238 | `6fdc69a22` | 2024-10-14 12:57:03 -0700 | Piotr Findeisen | Core: Deprecate ContentCache.invalidateAll (#10494) | ✅ 已完成 | [1238_6fdc69a22](commits/1238_6fdc69a22/analysis.md) |
| 1239 | `6376b447c` | 2024-10-15 11:59:10 +0200 | Huaxin Gao | Spark 3.3, 3.4, 3.5: Remove unnecessary copying of FileScanTask (#11319) | ✅ 已完成 | [1239_6376b447c](commits/1239_6376b447c/analysis.md) |
| 1240 | `33b33f3ec` | 2024-10-15 20:02:49 +0200 | Eduard Tudenhoefner | Core: Rename DeleteFileHolder to PendingDeleteFile / Optimize duplicate data/delete file detection (#11254) | ✅ 已完成 | [1240_33b33f3ec](commits/1240_33b33f3ec/analysis.md) |
| 1241 | `32b1ab6ea` | 2024-10-15 23:46:16 -0700 | Piotr Findeisen | Core: Fix version number in deprecation note for invalidateAll (#11325) | ✅ 已完成 | [1241_32b1ab6ea](commits/1241_32b1ab6ea/analysis.md) |
| 1242 | `5e279c868` | 2024-10-16 14:32:51 +0200 | Tom Tanaka | Build, Spark, Flink: Bump junit from 5.10.1 to 5.11.1 (#11262) | ✅ 已完成 | [1242_5e279c868](commits/1242_5e279c868/analysis.md) |
| 1243 | `11a8a78b9` | 2024-10-16 10:39:50 -0500 | Marc Cenac | Core, Azure: Support wasb[s] paths in ADLSFileIO (#11294) | ✅ 已完成 | [1243_11a8a78b9](commits/1243_11a8a78b9/analysis.md) |
| 1244 | `22a6b19c2` | 2024-10-16 11:04:08 -0700 | SeungwanJo | Make connect compatable with kafka plugin.discovery (#10536) | ✅ 已完成 | [1244_22a6b19c2](commits/1244_22a6b19c2/analysis.md) |
| 1245 | `17f1c4d22` | 2024-10-16 17:18:26 -0500 | Soumya Banerjee | Spark 3.5: Spark Scan should ignore statistics not of type Apache DataSketches (#11035) | ✅ 已完成 | [1245_17f1c4d22](commits/1245_17f1c4d22/analysis.md) |
| 1246 | `3c6c62654` | 2024-10-16 15:51:40 -0700 | RyanJClark | Kafka Connect: Add regex for property file match (#11303) | ✅ 已完成 | [1246_3c6c62654](commits/1246_3c6c62654/analysis.md) |
| 1247 | `9d58865ae` | 2024-10-17 08:44:53 +0200 | Yuya Ebihara | OpenAPI: Remove repeated 'for' (#11338) | ✅ 已完成 | [1247_9d58865ae](commits/1247_9d58865ae/analysis.md) |
| 1248 | `bbbfd1e2f` | 2024-10-17 07:48:54 -0600 | Edgar Rodriguez | AWS: Fix S3InputStream retry policy (#11335) | ✅ 已完成 | [1248_bbbfd1e2f](commits/1248_bbbfd1e2f/analysis.md) |
| 1249 | `3def1f447` | 2024-10-17 14:44:06 -0500 | Aihua Xu | API: (Test Only) Small fix to TestSerializableTypes.java (#11342) | ✅ 已完成 | [1249_3def1f447](commits/1249_3def1f447/analysis.md) |
| 1250 | `f4ffe1389` | 2024-10-17 14:31:02 -0700 | Steven Zhen Wu | Core: lazily load default Hadoop Configuration to avoid NPE with HadoopFileIO because FileIOParser doesn't serialize Hadoop configuration (#10926) | ✅ 已完成 | [1250_f4ffe1389](commits/1250_f4ffe1389/analysis.md) |
| 1251 | `fd064386f` | 2024-10-18 11:31:44 +0200 | Russell Spitzer | Revert "Core, Azure: Support wasb[s] paths in ADLSFileIO (#11294)" (#11344) | ✅ 已完成 | [1251_fd064386f](commits/1251_fd064386f/analysis.md) |
| 1252 | `2ac5c4328` | 2024-10-18 09:06:57 -0700 | Steven Zhen Wu | Flink: make FLIP-27 default in SQL and mark the old FlinkSource as deprecated (#11345) | ✅ 已完成 | [1252_2ac5c4328](commits/1252_2ac5c4328/analysis.md) |
| 1253 | `8a931e89a` | 2024-10-18 09:36:11 -0700 | Steven Zhen Wu | Flink: disable the flaky range distribution bucketing tests for now (#11347) | ✅ 已完成 | [1253_8a931e89a](commits/1253_8a931e89a/analysis.md) |
| 1254 | `44233fa53` | 2024-10-18 19:24:13 +0200 | Eduard Tudenhoefner | OpenAPI: Standardize credentials in loadTable/loadView responses (#10722) | ✅ 已完成 | [1254_44233fa53](commits/1254_44233fa53/analysis.md) |
| 1255 | `8dc9eacd4` | 2024-10-18 19:24:36 +0200 | Eduard Tudenhoefner | Core: Add credentials to loadTable / loadView responses (#11173) | ✅ 已完成 | [1255_8dc9eacd4](commits/1255_8dc9eacd4/analysis.md) |
| 1256 | `d61a98dc5` | 2024-10-18 11:01:44 -0700 | Laith AlZyoud | API: Add RewriteTablePath action interface (#10920) | ✅ 已完成 | [1256_d61a98dc5](commits/1256_d61a98dc5/analysis.md) |
| 1257 | `ea5da1789` | 2024-10-18 16:48:00 -0700 | Ozan Okumusoglu | AWS: Switch to base2 entropy in ObjectStoreLocationProvider for optimized S3 performance (#11112) | ✅ 已完成 | [1257_ea5da1789](commits/1257_ea5da1789/analysis.md) |
| 1258 | `d4f0d7e80` | 2024-10-21 06:35:03 +0200 | Arek Burdach | Flink: Add IcebergSinkBuilder interface allowed unification of most of operations on FlinkSink and IcebergSink Builders (#11305) | ✅ 已完成 | [1258_d4f0d7e80](commits/1258_d4f0d7e80/analysis.md) |
| 1259 | `b8c2b2023` | 2024-10-21 07:34:44 +0200 | dependabot[bot] | Build: Bump parquet from 1.13.1 to 1.14.3 (#11264) | ✅ 已完成 | [1259_b8c2b2023](commits/1259_b8c2b2023/analysis.md) |
| 1260 | `de48a7426` | 2024-10-21 09:06:59 +0200 | dependabot[bot] | Build: Bump com.palantir.baseline:gradle-baseline-java (#11362) | ✅ 已完成 | [1260_de48a7426](commits/1260_de48a7426/analysis.md) |
| 1261 | `181476471` | 2024-10-21 09:07:18 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#11360) | ✅ 已完成 | [1261_181476471](commits/1261_181476471/analysis.md) |
| 1262 | `0640a38b6` | 2024-10-21 09:07:34 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.28.21 to 2.28.26 (#11359) | ✅ 已完成 | [1262_0640a38b6](commits/1262_0640a38b6/analysis.md) |
| 1263 | `ce75f5271` | 2024-10-21 12:00:36 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.48.0 to 26.49.0 (#11363) | ✅ 已完成 | [1263_ce75f5271](commits/1263_ce75f5271/analysis.md) |
| 1264 | `c16cefa50` | 2024-10-21 14:35:03 +0200 | leesf | Core: Move deleteRemovedMetadataFiles(..) to CatalogUtil (#11352) | ✅ 已完成 | [1264_c16cefa50](commits/1264_c16cefa50/analysis.md) |
| 1265 | `d0a7ff915` | 2024-10-21 13:53:13 -0500 | Wing Yew Poon | Arrow: Fix indexing in Parquet dictionary encoded values readers (#11247) | ✅ 已完成 | [1265_d0a7ff915](commits/1265_d0a7ff915/analysis.md) |
| 1266 | `a19896658` | 2024-10-22 09:13:01 -0700 | Ryan Blue | Spark 3.5: Update Spark to use planned Avro reads (#11299) | ✅ 已完成 | [1266_a19896658](commits/1266_a19896658/analysis.md) |
| 1267 | `a8ec43d97` | 2024-10-22 15:28:57 -0700 | Hongyue/Steve Zhang | Core, Spark 3.5: Remove dangling deletes as part of RewriteDataFilesAction (#9724) | ✅ 已完成 | [1267_a8ec43d97](commits/1267_a8ec43d97/analysis.md) |
| 1268 | `97946088a` | 2024-10-23 17:32:30 +0200 | Eduard Tudenhoefner | Spark: Randomize view/function names in testing (#11381) | ✅ 已完成 | [1268_97946088a](commits/1268_97946088a/analysis.md) |
| 1269 | `60181f9a7` | 2024-10-23 11:16:39 -0600 | Eduard Tudenhoefner | Spark 3.4: Randomize view/function names in testing (#11382) | ✅ 已完成 | [1269_60181f9a7](commits/1269_60181f9a7/analysis.md) |
| 1270 | `9c0a80684` | 2024-10-23 11:23:53 -0600 | Hongyue/Steve Zhang | Spark 3.4: Action to remove dangling deletes (#11377) | ✅ 已完成 | [1270_9c0a80684](commits/1270_9c0a80684/analysis.md) |
| 1271 | `043757c0a` | 2024-10-23 14:36:34 -0500 | Huaxin Gao | Spark 3.5: Reset Spark Conf for each test in TestCompressionSettings (#11333) | ✅ 已完成 | [1271_043757c0a](commits/1271_043757c0a/analysis.md) |
| 1272 | `02a988b09` | 2024-10-23 14:59:18 -0700 | Russell Spitzer | Spec: Adds Row Lineage (#11130) | ✅ 已完成 | [1272_02a988b09](commits/1272_02a988b09/analysis.md) |
| 1273 | `4850b622c` | 2024-10-23 20:45:00 -0700 | stubz151 | AWS: Support S3 directory bucket listing (#11021) | ✅ 已完成 | [1273_4850b622c](commits/1273_4850b622c/analysis.md) |
| 1274 | `1cb88a64f` | 2024-10-24 12:33:27 +0200 | Eduard Tudenhoefner | Core: Add LoadCredentialsResponse class/parser (#11339) | ✅ 已完成 | [1274_1cb88a64f](commits/1274_1cb88a64f/analysis.md) |
| 1275 | `35a02d035` | 2024-10-24 16:07:35 +0200 | Eduard Tudenhoefner | OpenAPI: Add endpoint for refreshing vended credentials (#11281) | ✅ 已完成 | [1275_35a02d035](commits/1275_35a02d035/analysis.md) |
| 1276 | `6ba1a1f57` | 2024-10-24 17:37:33 +0200 | sullis | AWS: Use testcontainers-minio instead of S3Mock (#11349) | ✅ 已完成 | [1276_6ba1a1f57](commits/1276_6ba1a1f57/analysis.md) |
| 1277 | `12ac3ee5d` | 2024-10-24 10:58:56 -0700 | Bryan Keller | Kafka Connect: Include third party licenses and notices in distribution (#10829) | ✅ 已完成 | [1277_12ac3ee5d](commits/1277_12ac3ee5d/analysis.md) |
| 1278 | `fdc2c223e` | 2024-10-24 11:55:18 -0700 | JB Onofré | Deprecate iceberg-pig (#11379) | ✅ 已完成 | [1278_fdc2c223e](commits/1278_fdc2c223e/analysis.md) |
| 1279 | `6b04a6d00` | 2024-10-25 08:25:23 -0600 | Eduard Tudenhoefner | Core: Track data files by spec id instead of full PartitionSpec (#11323) | ✅ 已完成 | [1279_6b04a6d00](commits/1279_6b04a6d00/analysis.md) |
| 1280 | `32e9f4046` | 2024-10-25 14:28:51 -0500 | Manu Zhang | Spark 3.5: Don't change table distribution when only altering local order (#10774) | ✅ 已完成 | [1280_32e9f4046](commits/1280_32e9f4046/analysis.md) |
| 1281 | `7738e1d72` | 2024-10-25 15:37:05 -0500 | Ajantha Bhat | Spec: Fix table of content generation (#11067) | ✅ 已完成 | [1281_7738e1d72](commits/1281_7738e1d72/analysis.md) |
| 1282 | `9ecd97bf4` | 2024-10-25 15:39:48 -0500 | Prashant Singh | [KafkaConnect] Fix RecordConverter for UUID and Fixed Types (#11346) | ✅ 已完成 | [1282_9ecd97bf4](commits/1282_9ecd97bf4/analysis.md) |
| 1283 | `7ad11b2df` | 2024-10-25 15:23:44 -0600 | Kevin Liu | Core: Snapshot `summary` map must have `operation` key (#11354) | ✅ 已完成 | [1283_7ad11b2df](commits/1283_7ad11b2df/analysis.md) |
| 1284 | `2b55fef7c` | 2024-10-25 22:57:24 -0700 | erik-grepr | Core: Update TableMetadataParser to ensure all streams closed (#11220) | ✅ 已完成 | [1284_2b55fef7c](commits/1284_2b55fef7c/analysis.md) |
| 1285 | `e3bbcac85` | 2024-10-28 10:04:24 +0100 | Manu Zhang | Build: Bump Spark 3.4 to 3.4.4 (#11366) | ✅ 已完成 | [1285_e3bbcac85](commits/1285_e3bbcac85/analysis.md) |
| 1286 | `b4d178fa0` | 2024-10-28 10:05:32 +0100 | dependabot[bot] | Build: Bump junit from 5.11.1 to 5.11.3 (#11401) | ✅ 已完成 | [1286_b4d178fa0](commits/1286_b4d178fa0/analysis.md) |
| 1287 | `9565b9c40` | 2024-10-28 10:06:12 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.28.26 to 2.29.1 (#11400) | ✅ 已完成 | [1287_9565b9c40](commits/1287_9565b9c40/analysis.md) |
| 1288 | `681b09ddc` | 2024-10-28 10:13:50 +0100 | gaborkaszab | Core: Move Javadoc about commit retries to SnapshotProducer (#10995) | ✅ 已完成 | [1288_681b09ddc](commits/1288_681b09ddc/analysis.md) |
| 1289 | `9fc9c052b` | 2024-10-28 11:23:45 +0100 | dependabot[bot] | Build: Bump junit-platform from 1.11.2 to 1.11.3 (#11402) | ✅ 已完成 | [1289_9fc9c052b](commits/1289_9fc9c052b/analysis.md) |
| 1290 | `68a710207` | 2024-10-28 12:02:34 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.46.1.3 to 3.47.0.0 (#11407) | ✅ 已完成 | [1290_68a710207](commits/1290_68a710207/analysis.md) |
| 1291 | `48acaadcf` | 2024-10-28 12:03:10 +0100 | dependabot[bot] | Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#11405) | ✅ 已完成 | [1291_48acaadcf](commits/1291_48acaadcf/analysis.md) |
| 1292 | `c3191eecd` | 2024-10-28 14:15:42 +0100 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.19.0 to 3.19.1 (#11406) | ✅ 已完成 | [1292_c3191eecd](commits/1292_c3191eecd/analysis.md) |
| 1293 | `6e911e0ea` | 2024-10-28 14:16:03 +0100 | dependabot[bot] | Build: Bump testcontainers from 1.20.2 to 1.20.3 (#11404) | ✅ 已完成 | [1293_6e911e0ea](commits/1293_6e911e0ea/analysis.md) |
| 1294 | `d10e67d42` | 2024-10-28 14:16:44 +0100 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#11403) | ✅ 已完成 | [1294_d10e67d42](commits/1294_d10e67d42/analysis.md) |
| 1295 | `b4b0bdc54` | 2024-10-28 14:18:50 +0100 | dependabot[bot] | Build: Bump mkdocs-macros-plugin from 1.2.0 to 1.3.7 (#11399) | ✅ 已完成 | [1295_b4b0bdc54](commits/1295_b4b0bdc54/analysis.md) |
| 1296 | `696204392` | 2024-10-28 14:20:31 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.39 to 9.5.42 (#11398) | ✅ 已完成 | [1296_696204392](commits/1296_696204392/analysis.md) |
| 1297 | `86a856031` | 2024-10-28 17:42:58 +0100 | Manu Zhang | Flink: Fix disabling flaky range distribution bucketing tests (#11410) | ✅ 已完成 | [1297_86a856031](commits/1297_86a856031/analysis.md) |
| 1298 | `a6503f573` | 2024-10-28 18:12:06 +0100 | Fokko Driesprong | Bump Azurite to the latest version (#11411) | ✅ 已完成 | [1298_a6503f573](commits/1298_a6503f573/analysis.md) |
| 1299 | `a28ebf748` | 2024-10-28 18:12:48 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.26.1 to 0.26.2 (#11356) | ✅ 已完成 | [1299_a28ebf748](commits/1299_a28ebf748/analysis.md) |
| 1300 | `6c58f5bb7` | 2024-10-28 12:32:19 -0500 | Kevin Liu | Revert "Core: Snapshot `summary` map must have `operation` key (#11354)" (#11409) | ✅ 已完成 | [1300_6c58f5bb7](commits/1300_6c58f5bb7/analysis.md) |
| 1301 | `e013c67f2` | 2024-10-28 12:42:08 -0500 | JB Onofré | Aliyun: Remove spring-boot dependency (#11291) | ✅ 已完成 | [1301_e013c67f2](commits/1301_e013c67f2/analysis.md) |
| 1302 | `7ac617a5a` | 2024-10-28 20:55:22 +0100 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.25 to 1.2.28 (#11267) | ✅ 已完成 | [1302_7ac617a5a](commits/1302_7ac617a5a/analysis.md) |
| 1303 | `47eac5221` | 2024-10-28 20:57:39 +0100 | Manu Zhang | Spark: Flaky test due temp directory (#10811) | ✅ 已完成 | [1303_47eac5221](commits/1303_47eac5221/analysis.md) |
| 1304 | `1e3ee1e4e` | 2024-10-28 22:02:55 +0100 | Anton Okolnychyi | Core: Add portable Roaring bitmap for row positions (#11372) | ✅ 已完成 | [1304_1e3ee1e4e](commits/1304_1e3ee1e4e/analysis.md) |
| 1305 | `602c2b2db` | 2024-10-29 11:54:24 -0500 | JB Onofré | Flink 1.20: Update Flink to use planned Avro reads (#11386) | ✅ 已完成 | [1305_602c2b2db](commits/1305_602c2b2db/analysis.md) |
| 1306 | `740d4e7b1` | 2024-10-29 19:19:10 +0100 | Ajantha Bhat | open-api: Fix `testFixtures` dependencies (#11422) | ✅ 已完成 | [1306_740d4e7b1](commits/1306_740d4e7b1/analysis.md) |
| 1307 | `469c5560b` | 2024-10-29 15:03:38 -0500 | Hongyue/Steve Zhang | Core: use ManifestFiles.open when possible (#11414) | ✅ 已完成 | [1307_469c5560b](commits/1307_469c5560b/analysis.md) |
| 1308 | `5359bea71` | 2024-10-30 07:09:42 +0100 | Eduard Tudenhoefner | GCS: Refresh vended credentials (#11282) | ✅ 已完成 | [1308_5359bea71](commits/1308_5359bea71/analysis.md) |
| 1309 | `bedc71167` | 2024-10-30 07:26:10 +0100 | Alex Merced | Docs: Add 21 blogs / fix one broken link (#11424) | ✅ 已完成 | [1309_bedc71167](commits/1309_bedc71167/analysis.md) |
| 1310 | `9e895cb6d` | 2024-10-30 10:29:45 +0100 | Eduard Tudenhoefner | AWS: Refresh vended credentials (#11389) | ✅ 已完成 | [1310_9e895cb6d](commits/1310_9e895cb6d/analysis.md) |
| 1311 | `7c3908619` | 2024-10-30 15:49:37 +0100 | Fokko Driesprong | Build: Bump Hadoop to 3.4.1 (#11428) | ✅ 已完成 | [1311_7c3908619](commits/1311_7c3908619/analysis.md) |
| 1312 | `dec84c054` | 2024-10-30 11:30:55 -0600 | Eduard Tudenhoefner | Core: Remove credentials from LoadViewResponse (#11432) | ✅ 已完成 | [1312_dec84c054](commits/1312_dec84c054/analysis.md) |
| 1313 | `f4b36a5e5` | 2024-10-30 12:52:34 -0500 | Eduard Tudenhoefner | OpenAPI: Remove credentials from LoadViewResult (#11433) | ✅ 已完成 | [1313_f4b36a5e5](commits/1313_f4b36a5e5/analysis.md) |
| 1314 | `91e04c9c8` | 2024-10-30 16:58:25 -0500 | Ryan Blue | API: Add compatibility checks for Schemas with default values (#11434) | ✅ 已完成 | [1314_91e04c9c8](commits/1314_91e04c9c8/analysis.md) |
| 1315 | `57fb6d565` | 2024-10-31 13:31:14 -0700 | Hongyue/Steve Zhang | Doc: Update rewrite data files spark procedure (#11396) | ✅ 已完成 | [1315_57fb6d565](commits/1315_57fb6d565/analysis.md) |
| 1316 | `ea61ee46d` | 2024-10-31 15:48:45 -0500 | Manu Zhang | Docs: warn `parallelism > 1` doesn't work for migration procedures (#11417) | ✅ 已完成 | [1316_ea61ee46d](commits/1316_ea61ee46d/analysis.md) |
| 1317 | `1d4df34d7` | 2024-10-31 16:33:14 -0500 | sullis | Core: Log retry sleep time (#11413) | ✅ 已完成 | [1317_1d4df34d7](commits/1317_1d4df34d7/analysis.md) |
| 1318 | `caf424a37` | 2024-11-01 20:18:34 +0100 | Anton Okolnychyi | Core: Use RoaringPositionBitmap in position index (#11441) | ✅ 已完成 | [1318_caf424a37](commits/1318_caf424a37/analysis.md) |
| 1319 | `fe23584fc` | 2024-11-01 17:22:44 -0700 | Hongyue/Steve Zhang | Core: Add validation for table commit properties (#11437) | ✅ 已完成 | [1319_fe23584fc](commits/1319_fe23584fc/analysis.md) |
| 1320 | `8b4ebc66e` | 2024-11-02 10:06:12 +0100 | Anton Okolnychyi | Core: Add cardinality to PositionDeleteIndex (#11442) | ✅ 已完成 | [1320_8b4ebc66e](commits/1320_8b4ebc66e/analysis.md) |
| 1321 | `b9ebc71fb` | 2024-11-02 11:04:03 +0100 | Ryan Blue | Puffin: Add deletion-vector-v1 blob type (#11238) | ✅ 已完成 | [1321_b9ebc71fb](commits/1321_b9ebc71fb/analysis.md) |
| 1322 | `d368a5f84` | 2024-11-02 11:18:04 +0100 | Ryan Blue | Spec: Add deletion vectors to the table spec (#11240) | ✅ 已完成 | [1322_d368a5f84](commits/1322_d368a5f84/analysis.md) |
| 1323 | `d9b976876` | 2024-11-02 17:23:28 +0100 | Anton Okolnychyi | API, Core: Add data file reference to DeleteFile (#11443) | ✅ 已完成 | [1323_d9b976876](commits/1323_d9b976876/analysis.md) |
| 1324 | `e47fa6a58` | 2024-11-04 08:49:46 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.1 to 2.29.6 (#11454) | ✅ 已完成 | [1324_e47fa6a58](commits/1324_e47fa6a58/analysis.md) |
| 1325 | `29ee90649` | 2024-11-04 08:50:16 +0100 | dependabot[bot] | Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.3 to 8.3.5 (#11452) | ✅ 已完成 | [1325_29ee90649](commits/1325_29ee90649/analysis.md) |
| 1326 | `7ffb6a3de` | 2024-11-04 08:50:40 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.49.0 to 26.50.0 (#11451) | ✅ 已完成 | [1326_7ffb6a3de](commits/1326_7ffb6a3de/analysis.md) |
| 1327 | `aa27d9057` | 2024-11-04 08:50:56 +0100 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#11450) | ✅ 已完成 | [1327_aa27d9057](commits/1327_aa27d9057/analysis.md) |
| 1328 | `c7f0f80e0` | 2024-11-04 11:39:56 +0100 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.28 to 1.2.29 (#11453) | ✅ 已完成 | [1328_c7f0f80e0](commits/1328_c7f0f80e0/analysis.md) |
| 1329 | `0669bcbad` | 2024-11-04 15:06:00 +0100 | pvary | Flink: Maintenance - TableManager + ExpireSnapshots (#11144) | ✅ 已完成 | [1329_0669bcbad](commits/1329_0669bcbad/analysis.md) |
| 1330 | `ec0eef45e` | 2024-11-04 15:13:21 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.42 to 9.5.43 (#11455) | ✅ 已完成 | [1330_ec0eef45e](commits/1330_ec0eef45e/analysis.md) |
| 1331 | `8357f65d2` | 2024-11-04 15:13:37 +0100 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.19.1 to 3.20.0 (#11447) | ✅ 已完成 | [1331_8357f65d2](commits/1331_8357f65d2/analysis.md) |
| 1332 | `329846875` | 2024-11-04 15:15:02 +0100 | dependabot[bot] | Build: Bump kafka from 3.8.0 to 3.8.1 (#11449) | ✅ 已完成 | [1332_329846875](commits/1332_329846875/analysis.md) |
| 1333 | `9dcf0d3d3` | 2024-11-04 15:15:20 +0100 | dependabot[bot] | Build: Bump jackson-bom from 2.18.0 to 2.18.1 (#11448) | ✅ 已完成 | [1333_9dcf0d3d3](commits/1333_9dcf0d3d3/analysis.md) |
| 1334 | `af5be32fb` | 2024-11-04 07:40:21 -0700 | Anton Okolnychyi | Core: Fix generated position delete file spec (#11458) | ✅ 已完成 | [1334_af5be32fb](commits/1334_af5be32fb/analysis.md) |
| 1335 | `ec269ee3e` | 2024-11-04 19:35:08 +0100 | Anton Okolnychyi | API, Core: Add content offset and size to DeleteFile (#11446) | ✅ 已完成 | [1335_ec269ee3e](commits/1335_ec269ee3e/analysis.md) |
| 1336 | `7cc16fa94` | 2024-11-04 13:27:40 -0600 | Russell Spitzer | Revert "Build: Bump parquet from 1.13.1 to 1.14.3 (#11264)" (#11462) | ✅ 已完成 | [1336_7cc16fa94](commits/1336_7cc16fa94/analysis.md) |
| 1337 | `d0cca384a` | 2024-11-04 21:22:50 +0100 | Anton Okolnychyi | Spark 3.5: Preserve data file reference during manifest rewrites (#11457) | ✅ 已完成 | [1337_d0cca384a](commits/1337_d0cca384a/analysis.md) |
| 1338 | `43b2f7d00` | 2024-11-05 08:35:42 +0100 | Anton Okolnychyi | Core: Make PositionDeleteIndex serializable (#11463) | ✅ 已完成 | [1338_43b2f7d00](commits/1338_43b2f7d00/analysis.md) |
| 1339 | `592b3b1c5` | 2024-11-05 08:50:39 +0100 | Anton Okolnychyi | Spark 3.5: Preserve content offset and size during manifest rewrites (#11469) | ✅ 已完成 | [1339_592b3b1c5](commits/1339_592b3b1c5/analysis.md) |
| 1340 | `20e0e3dae` | 2024-11-05 11:04:56 +0100 | Manu Zhang | Spark 3.5: Fix flaky test due to temp directory not empty during delete (#11470) | ✅ 已完成 | [1340_20e0e3dae](commits/1340_20e0e3dae/analysis.md) |
| 1341 | `67ee08259` | 2024-11-05 12:56:02 +0100 | Eduard Tudenhoefner | Core, Data, Flink, Spark: Improve tableDir initialization for tests (#11460) | ✅ 已完成 | [1341_67ee08259](commits/1341_67ee08259/analysis.md) |
| 1342 | `5bd314bdf` | 2024-11-05 15:52:24 +0100 | Anton Okolnychyi | Core: Support DVs in DeleteFileIndex (#11467) | ✅ 已完成 | [1342_5bd314bdf](commits/1342_5bd314bdf/analysis.md) |
| 1343 | `549674b3f` | 2024-11-05 17:02:00 +0100 | Anton Okolnychyi | Core: Adapt commit, scan, and snapshot stats for DVs (#11464) | ✅ 已完成 | [1343_549674b3f](commits/1343_549674b3f/analysis.md) |
| 1344 | `ad24d4bf8` | 2024-11-05 11:15:49 -0700 | Amogh Jahagirdar | Spark: Synchronously merge new position deletes with old deletes (#11273) | ✅ 已完成 | [1344_ad24d4bf8](commits/1344_ad24d4bf8/analysis.md) |
| 1345 | `9be7f00dd` | 2024-11-05 15:55:56 -0800 | Marc Cenac | Fix ADLSLocation file parsing (#11395) | ✅ 已完成 | [1345_9be7f00dd](commits/1345_9be7f00dd/analysis.md) |
| 1346 | `2ffd3b02a` | 2024-11-06 09:17:35 +0100 | Ajantha Bhat | open-api: Build runtime jar for test fixture (#11279) | ✅ 已完成 | [1346_2ffd3b02a](commits/1346_2ffd3b02a/analysis.md) |
| 1347 | `7938403a4` | 2024-11-06 21:32:07 +0100 | Anton Okolnychyi | Core, Puffin: Add DV file writer (#11476) | ✅ 已完成 | [1347_7938403a4](commits/1347_7938403a4/analysis.md) |
| 1348 | `1e82c476d` | 2024-11-06 14:45:41 -0800 | Mingliang Liu | Flink: Fix config key typo in error message of SplitComparators (#11482) | ✅ 已完成 | [1348_1e82c476d](commits/1348_1e82c476d/analysis.md) |
| 1349 | `11e7230b6` | 2024-11-07 09:47:50 -0600 | Russell Spitzer | API: Removes Explicit Parameterization of Schema Tests (#11444) | ✅ 已完成 | [1349_11e7230b6](commits/1349_11e7230b6/analysis.md) |
| 1350 | `5c8a5d68d` | 2024-11-07 17:50:10 +0100 | Manu Zhang | Docs: Fix verifying release candidate with Spark and Flink (#11461) | ✅ 已完成 | [1350_5c8a5d68d](commits/1350_5c8a5d68d/analysis.md) |
| 1351 | `3da64d37d` | 2024-11-08 07:29:44 +0100 | pvary | Flink: Port #11144 to v1.19 (#11473) | ✅ 已完成 | [1351_3da64d37d](commits/1351_3da64d37d/analysis.md) |
| 1352 | `fff9ec3bb` | 2024-11-08 07:36:27 +0100 | Manu Zhang | Docs: Fix format of verifying release candidate with Flink (#11487) | ✅ 已完成 | [1352_fff9ec3bb](commits/1352_fff9ec3bb/analysis.md) |
| 1353 | `166edc729` | 2024-11-08 18:09:49 +0100 | Anton Okolnychyi | Core: Support DVs in DeleteLoader (#11481) | ✅ 已完成 | [1353_166edc729](commits/1353_166edc729/analysis.md) |
| 1354 | `dda62154e` | 2024-11-08 20:05:42 +0100 | Russell Spitzer | Infra: Update DOAP.RDF for Apache Iceberg 1.7.0 (#11492) | ✅ 已完成 | [1354_dda62154e](commits/1354_dda62154e/analysis.md) |
| 1355 | `6a1634094` | 2024-11-08 20:34:08 +0100 | Russell Spitzer | Docs: Site Update for 1.7.0 Release (#11494) | ✅ 已完成 | [1355_6a1634094](commits/1355_6a1634094/analysis.md) |
| 1356 | `bbc0d9aad` | 2024-11-08 20:34:56 +0100 | Russell Spitzer | Infra: Add 1.7.0 to issue template (#11491) | ✅ 已完成 | [1356_bbc0d9aad](commits/1356_bbc0d9aad/analysis.md) |
| 1357 | `df0917d55` | 2024-11-08 20:35:12 +0100 | Russell Spitzer | Build: Let revapi compare against 1.7.0 (#11490) | ✅ 已完成 | [1357_df0917d55](commits/1357_df0917d55/analysis.md) |
| 1358 | `cd25937dc` | 2024-11-08 21:46:15 +0100 | Russell Spitzer | Docs: Adds Release notes for 1.6.1 (#11500) | ✅ 已完成 | [1358_cd25937dc](commits/1358_cd25937dc/analysis.md) |
| 1359 | `dfee4cb35` | 2024-11-08 21:47:21 +0100 | Russell Spitzer | Docs: Fixes Release Formatting for 1.7.0 Release Notes (#11499) | ✅ 已完成 | [1359_dfee4cb35](commits/1359_dfee4cb35/analysis.md) |
| 1360 | `82a2362af` | 2024-11-08 15:39:30 -0600 | Sung Yun | DOCS: Explicitly specify `operation` as a _required_ field of `summary` field (#11355) | ✅ 已完成 | [1360_82a2362af](commits/1360_82a2362af/analysis.md) |
| 1361 | `1c576c595` | 2024-11-08 15:57:37 -0800 | Huaxin Gao | Spark: Exclude reading _pos column if it's not in the scan list (#11390) | ✅ 已完成 | [1361_1c576c595](commits/1361_1c576c595/analysis.md) |
| 1362 | `ea21a533b` | 2024-11-11 09:10:33 +0100 | dependabot[bot] | Build: Bump mkdocs-redirects from 1.2.1 to 1.2.2 (#11511) | ✅ 已完成 | [1362_ea21a533b](commits/1362_ea21a533b/analysis.md) |
| 1363 | `aa0aeb0cf` | 2024-11-11 09:27:40 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.43 to 9.5.44 (#11510) | ✅ 已完成 | [1363_aa0aeb0cf](commits/1363_aa0aeb0cf/analysis.md) |
| 1364 | `981f1ea2f` | 2024-11-11 14:40:46 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.6 to 2.29.9 (#11509) | ✅ 已完成 | [1364_981f1ea2f](commits/1364_981f1ea2f/analysis.md) |
| 1365 | `d1fd492b8` | 2024-11-11 14:41:03 +0100 | Manu Zhang | Docs: Update multi-engine support after 1.7.0 release (#11503) | ✅ 已完成 | [1365_d1fd492b8](commits/1365_d1fd492b8/analysis.md) |
| 1366 | `5530605ff` | 2024-11-11 17:58:41 +0100 | dongwang | Spark: Fix typo in Spark ddl comment (#11517) | ✅ 已完成 | [1366_5530605ff](commits/1366_5530605ff/analysis.md) |
| 1367 | `af3fbfe07` | 2024-11-11 22:08:23 +0100 | Anton Okolnychyi | Core: Support commits with DVs (#11495) | ✅ 已完成 | [1367_af3fbfe07](commits/1367_af3fbfe07/analysis.md) |
| 1368 | `11d21b26e` | 2024-11-11 15:55:42 -0800 | Bryan Keller | Kafka Connect: fix Hadoop dependency exclusion (#11516) | ✅ 已完成 | [1368_11d21b26e](commits/1368_11d21b26e/analysis.md) |
| 1369 | `e3f399728` | 2024-11-12 13:23:32 +0100 | JB Onofré | Build: Upgrade to Gradle 8.11.0 (#11521) | ✅ 已完成 | [1369_e3f399728](commits/1369_e3f399728/analysis.md) |
| 1370 | `50545781d` | 2024-11-12 10:29:08 -0600 | Cheng Pan | Spark 3.5: Iceberg parser should passthrough unsupported procedure to delegate (#11480) | ✅ 已完成 | [1370_50545781d](commits/1370_50545781d/analysis.md) |
| 1371 | `4a3817bc7` | 2024-11-12 20:30:21 +0100 | Kevin Liu | Release: Use `dist/release` KEYS (#11526) | ✅ 已完成 | [1371_4a3817bc7](commits/1371_4a3817bc7/analysis.md) |
| 1372 | `0280885ac` | 2024-11-13 16:00:08 +0100 | JB Onofré | Pig: Remove iceberg-pig (#11380) | ✅ 已完成 | [1372_0280885ac](commits/1372_0280885ac/analysis.md) |
| 1373 | `e06b06952` | 2024-11-13 16:42:06 +0100 | Eduard Tudenhoefner | Core, Flink, Spark: Test DVs with format-version=3 (#11485) | ✅ 已完成 | [1373_e06b06952](commits/1373_e06b06952/analysis.md) |
| 1374 | `3659ded18` | 2024-11-13 14:09:31 -0700 | Huaxin Gao | Spark: Update tests which assume file format to use enum instead of string literal (#11540) | ✅ 已完成 | [1374_3659ded18](commits/1374_3659ded18/analysis.md) |
| 1375 | `9923ac938` | 2024-11-14 10:27:19 +0100 | Sai Tharun | Spark 3.4: Support Spark Column Stats (#11532) | ✅ 已完成 | [1375_9923ac938](commits/1375_9923ac938/analysis.md) |
| 1376 | `daa24f9c3` | 2024-11-14 11:48:48 +0100 | Manu Zhang | Docs: Fix rendering lists (#11546) | ✅ 已完成 | [1376_daa24f9c3](commits/1376_daa24f9c3/analysis.md) |
| 1377 | `071d9e277` | 2024-11-14 06:35:32 -0800 | dependabot[bot] | Build: Bump kafka from 3.8.1 to 3.9.0 (#11508) | ✅ 已完成 | [1377_071d9e277](commits/1377_071d9e277/analysis.md) |
| 1378 | `09634857e` | 2024-11-14 06:38:04 -0800 | Marc Cenac | Support WASB scheme in ADLSFileIO (#11504) | ✅ 已完成 | [1378_09634857e](commits/1378_09634857e/analysis.md) |
| 1379 | `0a705b063` | 2024-11-14 16:04:01 -0600 | Russell Spitzer | Docs: 4 Spaces are Requried for Sublists (#11549) | ✅ 已完成 | [1379_0a705b063](commits/1379_0a705b063/analysis.md) |
| 1380 | `4c0288a4c` | 2024-11-15 07:07:04 +0100 | Rocco Varela | API, Core, Spark: Ignore schema merge updates from long -> int (#11419) | ✅ 已完成 | [1380_4c0288a4c](commits/1380_4c0288a4c/analysis.md) |
| 1381 | `307593ffd` | 2024-11-15 18:19:52 +0800 | Manu Zhang | Docs: Fix level of Deletion Vectors (#11547) | ✅ 已完成 | [1381_307593ffd](commits/1381_307593ffd/analysis.md) |
| 1382 | `50d310aef` | 2024-11-15 08:50:29 -0700 | Amogh Jahagirdar | API, Core: Replace deprecated ContentFile#path usage with location (#11550) | ✅ 已完成 | [1382_50d310aef](commits/1382_50d310aef/analysis.md) |
| 1383 | `821aec32b` | 2024-11-15 12:52:23 -0600 | Aihua Xu | API: Add Variant data type (#11324) | ✅ 已完成 | [1383_821aec32b](commits/1383_821aec32b/analysis.md) |
| 1384 | `315e15437` | 2024-11-15 22:12:45 +0100 | Anton Okolnychyi | Spark 3.5: Adapt DeleteFileIndexBenchmark for DVs (#11529) | ✅ 已完成 | [1384_315e15437](commits/1384_315e15437/analysis.md) |
| 1385 | `7e4fd1ba6` | 2024-11-15 22:14:32 +0100 | Anton Okolnychyi | Spark 3.5: Adapt PlanningBenchmark for DVs (#11531) | ✅ 已完成 | [1385_7e4fd1ba6](commits/1385_7e4fd1ba6/analysis.md) |
| 1386 | `acd7cc112` | 2024-11-15 22:15:08 +0100 | Anton Okolnychyi | Spark 3.5: Add DVReaderBenchmark (#11537) | ✅ 已完成 | [1386_acd7cc112](commits/1386_acd7cc112/analysis.md) |
| 1387 | `8c83fb734` | 2024-11-17 08:29:05 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.26.2 to 0.26.3 (#11572) | ✅ 已完成 | [1387_8c83fb734](commits/1387_8c83fb734/analysis.md) |
| 1388 | `3934b1383` | 2024-11-17 08:29:46 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.9 to 2.29.15 (#11568) | ✅ 已完成 | [1388_3934b1383](commits/1388_3934b1383/analysis.md) |
| 1389 | `fc11dc456` | 2024-11-17 08:29:56 +0100 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.114.Final to 4.1.115.Final (#11569) | ✅ 已完成 | [1389_fc11dc456](commits/1389_fc11dc456/analysis.md) |
| 1390 | `b38951db6` | 2024-11-17 17:20:20 +0100 | Eduard Tudenhoefner | Data, Flink, MR, Spark: Test deletes with format-version=3 (#11538) | ✅ 已完成 | [1390_b38951db6](commits/1390_b38951db6/analysis.md) |
| 1391 | `491718c3e` | 2024-11-18 11:33:42 +0100 | dependabot[bot] | Build: Bump nessie from 0.99.0 to 0.100.0 (#11567) | ✅ 已完成 | [1391_491718c3e](commits/1391_491718c3e/analysis.md) |
| 1392 | `f9256c698` | 2024-11-18 11:37:05 +0100 | dependabot[bot] | Build: Bump orc from 1.9.4 to 1.9.5 (#11571) | ✅ 已完成 | [1392_f9256c698](commits/1392_f9256c698/analysis.md) |
| 1393 | `97542ab67` | 2024-11-18 08:26:58 -0700 | Amogh Jahagirdar | API, Arrow, Core, Data, Spark: Replace usage of deprecated ContentFile#path API with location API (#11563) | ✅ 已完成 | [1393_97542ab67](commits/1393_97542ab67/analysis.md) |
| 1394 | `bf8d25fe1` | 2024-11-18 11:14:35 -0600 | Fokko Driesprong | Core: Serialize `null` when there is no current snapshot (#11560) | ✅ 已完成 | [1394_bf8d25fe1](commits/1394_bf8d25fe1/analysis.md) |
| 1395 | `209781af4` | 2024-11-18 14:11:17 -0600 | Cheng Pan | Spark 3.4: Iceberg parser should passthrough unsupported procedure to delegate (#11579) | ✅ 已完成 | [1395_209781af4](commits/1395_209781af4/analysis.md) |
| 1396 | `568940f5f` | 2024-11-18 14:11:41 -0600 | Cheng Pan | Spark 3.3: Iceberg parser should passthrough unsupported procedure to delegate (#11580) | ✅ 已完成 | [1396_568940f5f](commits/1396_568940f5f/analysis.md) |
| 1397 | `e71e3cb19` | 2024-11-19 06:23:55 -0700 | gaborkaszab | Core: Inherited classes from SnapshotProducer has TableOperations redundantly as member (#11578) | ✅ 已完成 | [1397_e71e3cb19](commits/1397_e71e3cb19/analysis.md) |
| 1398 | `3badfe0c1` | 2024-11-19 06:38:46 -0800 | Eduard Tudenhoefner | Revert "Core: Use encoding/decoding methods for namespaces and deprecate Spli…" (#11574) | ✅ 已完成 | [1398_3badfe0c1](commits/1398_3badfe0c1/analysis.md) |
| 1399 | `f6d02de77` | 2024-11-20 07:05:06 +0100 | leesf | Core: Delete temp metadata file when version already exists (#11350) | ✅ 已完成 | [1399_f6d02de77](commits/1399_f6d02de77/analysis.md) |
| 1400 | `657fa86b4` | 2024-11-20 09:35:34 +0100 | Fokko Driesprong | Build: Bump Apache Parquet 1.14.4 (#11502) | ✅ 已完成 | [1400_657fa86b4](commits/1400_657fa86b4/analysis.md) |
| 1401 | `3c4a710d2` | 2024-11-20 09:48:27 +0100 | Fokko Driesprong | Core: Filter on live entries when reading the manifest (#9996) | ✅ 已完成 | [1401_3c4a710d2](commits/1401_3c4a710d2/analysis.md) |
| 1402 | `918f81f3c` | 2024-11-20 15:06:33 +0100 | Eduard Tudenhoefner | Core: Fix CCE when retrieving TableOps (#11585) | ✅ 已完成 | [1402_918f81f3c](commits/1402_918f81f3c/analysis.md) |
| 1403 | `d19e3ff07` | 2024-11-20 17:32:11 +0100 | Eduard Tudenhoefner | API, Core: Remove unnecessary casts to Iterable<T> (#11601) | ✅ 已完成 | [1403_d19e3ff07](commits/1403_d19e3ff07/analysis.md) |
| 1404 | `799925a4e` | 2024-11-20 10:39:41 -0600 | Manu Zhang | Spark 3.5: Fix NotSerializableException when migrating Spark tables (#11157) | ✅ 已完成 | [1404_799925a4e](commits/1404_799925a4e/analysis.md) |
| 1405 | `c9ece1214` | 2024-11-20 10:40:48 -0800 | Anton Okolnychyi | Spark 3.3: Deprecate support (#11596) | ✅ 已完成 | [1405_c9ece1214](commits/1405_c9ece1214/analysis.md) |
| 1406 | `6e9e07aa0` | 2024-11-20 15:26:19 -0600 | Zhendong Bai | Hive: Bugfix for incorrect Deletion of Snapshot Metadata Due to OutOfMemoryError (#11576) | ✅ 已完成 | [1406_6e9e07aa0](commits/1406_6e9e07aa0/analysis.md) |
| 1407 | `a8f42d1b3` | 2024-11-20 23:08:36 +0100 | Matt Topol | docs: Add `iceberg-go` to doc site (#11607) | ✅ 已完成 | [1407_a8f42d1b3](commits/1407_a8f42d1b3/analysis.md) |
| 1408 | `3b5c9f7f3` | 2024-11-20 15:10:26 -0800 | Karuppayya | Spark 3.5: Procedure to compute table stats (#10986) | ✅ 已完成 | [1408_3b5c9f7f3](commits/1408_3b5c9f7f3/analysis.md) |
| 1409 | `93a063399` | 2024-11-20 17:46:57 -0800 | Wing Yew Poon | Parquet: Use native getRowIndexOffset support instead of calculating it (#11520) | ✅ 已完成 | [1409_93a063399](commits/1409_93a063399/analysis.md) |
| 1410 | `c448a4b87` | 2024-11-20 18:15:44 -0800 | Ace Haidrey | Spark: Fix changelog table bug for start time older than current snapshot (#11564) | ✅ 已完成 | [1410_c448a4b87](commits/1410_c448a4b87/analysis.md) |
| 1411 | `652fcc660` | 2024-11-21 16:14:23 +0100 | Manu Zhang | Spark 3.5: Fix flaky TestRemoveOrphanFilesAction3 (#11616) | ✅ 已完成 | [1411_652fcc660](commits/1411_652fcc660/analysis.md) |
| 1412 | `c1f1f8b18` | 2024-11-21 16:15:42 +0100 | JB Onofré | Build: Upgrade to Gradle 8.11.1 (#11619) | ✅ 已完成 | [1412_c1f1f8b18](commits/1412_c1f1f8b18/analysis.md) |
| 1413 | `90be5d736` | 2024-11-21 08:23:53 -0700 | Amogh Jahagirdar | Core: Optimize MergingSnapshotProducer to use referenced manifests to determine if manifest needs to be rewritten (#11131) | ✅ 已完成 | [1413_90be5d736](commits/1413_90be5d736/analysis.md) |
| 1414 | `12845d4ed` | 2024-11-21 13:09:10 -0700 | Hussein Awala | Revert "Core: Update TableMetadataParser to ensure all streams closed (#11220)" (#11621) | ✅ 已完成 | [1414_12845d4ed](commits/1414_12845d4ed/analysis.md) |
| 1415 | `a52afdc48` | 2024-11-21 15:32:26 -0800 | Haizhou Zhao | Add REST Catalog tests to Spark 3.5 integration test (#11093) | ✅ 已完成 | [1415_a52afdc48](commits/1415_a52afdc48/analysis.md) |
| 1416 | `ce4c44792` | 2024-11-22 15:26:23 +0100 | Cheng Pan | Spark 3.5: Correct the two-stage parsing strategy of antlr parser (#11628) | ✅ 已完成 | [1416_ce4c44792](commits/1416_ce4c44792/analysis.md) |
| 1417 | `f717ebde4` | 2024-11-22 15:26:47 +0100 | Cheng Pan | Spark 3.4: Correct the two-stage parsing strategy of antlr parser (#7734) | ✅ 已完成 | [1417_f717ebde4](commits/1417_f717ebde4/analysis.md) |
| 1418 | `f2b1b91d3` | 2024-11-22 15:49:27 +0100 | ismail simsek | Docs: Add new blog post to Iceberg Blogs (#11627) | ✅ 已完成 | [1418_f2b1b91d3](commits/1418_f2b1b91d3/analysis.md) |
| 1419 | `5851ca6e0` | 2024-11-23 07:28:24 +0100 | Cheng Pan | Docs: Mention HIVE-28121 for MySQL/MariaDB-based HMS users (#11631) | ✅ 已完成 | [1419_5851ca6e0](commits/1419_5851ca6e0/analysis.md) |
| 1420 | `9cc13b11f` | 2024-11-22 22:57:50 -0800 | Cheng Pan | Spark 3.4: IcebergSource extends SessionConfigSupport (#7732) | ✅ 已完成 | [1420_9cc13b11f](commits/1420_9cc13b11f/analysis.md) |
| 1421 | `5e09cdc6b` | 2024-11-22 22:58:39 -0800 | Cheng Pan | Spark 3.5: IcebergSource extends SessionConfigSupport (#11624) | ✅ 已完成 | [1421_5e09cdc6b](commits/1421_5e09cdc6b/analysis.md) |
| 1422 | `eddf9a161` | 2024-11-22 22:59:46 -0800 | Cheng Pan | Spark 3.3: IcebergSource extends SessionConfigSupport (#11625) | ✅ 已完成 | [1422_eddf9a161](commits/1422_eddf9a161/analysis.md) |
| 1423 | `b1fbef7bc` | 2024-11-25 08:43:23 +0100 | dependabot[bot] | Build: Bump testcontainers from 1.20.3 to 1.20.4 (#11640) | ✅ 已完成 | [1423_b1fbef7bc](commits/1423_b1fbef7bc/analysis.md) |
| 1424 | `3aebcfeb8` | 2024-11-25 08:45:53 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.44 to 9.5.45 (#11641) | ✅ 已完成 | [1424_3aebcfeb8](commits/1424_3aebcfeb8/analysis.md) |
| 1425 | `4337040be` | 2024-11-25 08:48:17 +0100 | Cheng Pan | Spark 3.3: Correct the two-stage parsing strategy of antlr parser (#11630) | ✅ 已完成 | [1425_4337040be](commits/1425_4337040be/analysis.md) |
| 1426 | `1f23dcd0e` | 2024-11-25 08:49:38 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.15 to 2.29.20 (#11639) | ✅ 已完成 | [1426_1f23dcd0e](commits/1426_1f23dcd0e/analysis.md) |
| 1427 | `4b52dbd89` | 2024-11-25 10:25:56 +0100 | Fokko Driesprong | Core,Open-API: Don't expose the `last-column-id` (#11514) | ✅ 已完成 | [1427_4b52dbd89](commits/1427_4b52dbd89/analysis.md) |
| 1428 | `cb1ad79ca` | 2024-11-25 10:28:40 +0100 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#11638) | ✅ 已完成 | [1428_cb1ad79ca](commits/1428_cb1ad79ca/analysis.md) |
| 1429 | `fa47f3141` | 2024-11-25 13:23:10 +0100 | Arek Burdach | Flink: Add table.exec.iceberg.use-v2-sink option (#11244) | ✅ 已完成 | [1429_fa47f3141](commits/1429_fa47f3141/analysis.md) |
| 1430 | `cdd944ebb` | 2024-11-25 13:45:27 +0100 | Cheng Pan | Docs: Use DataFrameWriterV2 in example (#11647) | ✅ 已完成 | [1430_cdd944ebb](commits/1430_cdd944ebb/analysis.md) |
| 1431 | `f7ff0dc8c` | 2024-11-25 14:08:54 +0100 | Hussein Awala | Docs: Add `WHEN NOT MATCHED BY SOURCE` to Spark doc (#11636) | ✅ 已完成 | [1431_f7ff0dc8c](commits/1431_f7ff0dc8c/analysis.md) |
| 1432 | `fa00482d3` | 2024-11-25 22:25:31 +0100 | dependabot[bot] | Build: Bump nessie from 0.100.0 to 0.100.2 (#11637) | ✅ 已完成 | [1432_fa00482d3](commits/1432_fa00482d3/analysis.md) |
| 1433 | `430ebff8e` | 2024-11-26 08:37:46 +0100 | Manu Zhang | Build: Delete branch automatically on PR merge (#11635) | ✅ 已完成 | [1433_430ebff8e](commits/1433_430ebff8e/analysis.md) |
| 1434 | `f35608715` | 2024-11-26 16:55:09 +0100 | JB Onofré | Flink: Test both "new" Flink Avro planned reader and "deprecated" Avro reader (#11430) | ✅ 已完成 | [1434_f35608715](commits/1434_f35608715/analysis.md) |
| 1435 | `38d054e4a` | 2024-11-27 08:02:27 +0100 | Soumya Banerjee | Spark 3.4: Add procedure to compute table stats (#11652) | ✅ 已完成 | [1435_38d054e4a](commits/1435_38d054e4a/analysis.md) |
| 1436 | `57527d743` | 2024-11-27 13:43:46 +0100 | Arek Burdach | Flink: Backport #11244 to Flink 1.19 (Add table.exec.iceberg.use-v2-sink option) (#11665) | ✅ 已完成 | [1436_57527d743](commits/1436_57527d743/analysis.md) |
| 1437 | `e9f24f896` | 2024-11-27 13:48:10 +0100 | Yujiang Zhong | Doc: Fix some Javadoc URLs. (#11666) | ✅ 已完成 | [1437_e9f24f896](commits/1437_e9f24f896/analysis.md) |
| 1438 | `2b3cb5b93` | 2024-11-27 14:52:19 +0100 | Arek Burdach | Docs: Add blog post showing Nussknacker with Iceberg integration (#11667) | ✅ 已完成 | [1438_2b3cb5b93](commits/1438_2b3cb5b93/analysis.md) |
| 1439 | `9288d987f` | 2024-11-27 06:47:51 -0800 | Hugo Friant | Kafka Connect: Add config to prefix the control consumer group (#11599) | ✅ 已完成 | [1439_9288d987f](commits/1439_9288d987f/analysis.md) |
| 1440 | `bd7cff175` | 2024-11-27 21:05:46 +0100 | JB Onofré | Flink: Backport Avro planned reader (and corresponding tests) on Flink v1.18 and v1.19 (#11668) | ✅ 已完成 | [1440_bd7cff175](commits/1440_bd7cff175/analysis.md) |
| 1441 | `3a8bf57ed` | 2024-11-28 08:04:04 +0100 | hengm3467 | Docs: Add RisingWave (#11642) | ✅ 已完成 | [1441_3a8bf57ed](commits/1441_3a8bf57ed/analysis.md) |
| 1442 | `163e2068f` | 2024-11-28 10:44:57 +0100 | Ajantha Bhat | REST: Docker file for REST Catalog Fixture (#11283) | ✅ 已完成 | [1442_163e2068f](commits/1442_163e2068f/analysis.md) |
| 1443 | `a95943e55` | 2024-11-28 15:59:42 +0100 | Eduard Tudenhoefner | Core: Propagate custom metrics reporter when table is created/replaced through Transaction (#11671) | ✅ 已完成 | [1443_a95943e55](commits/1443_a95943e55/analysis.md) |
| 1444 | `8e0031c01` | 2024-11-28 11:34:23 -0800 | Huaxin Gao | Spark: remove ROW_POSITION from project schema (#11610) | ✅ 已完成 | [1444_8e0031c01](commits/1444_8e0031c01/analysis.md) |
| 1445 | `3a04257e4` | 2024-11-28 20:44:49 +0100 | Fokko Driesprong | Default to `overwrite` when operation is missing (#11421) | ✅ 已完成 | [1445_3a04257e4](commits/1445_3a04257e4/analysis.md) |
| 1446 | `8fccdec95` | 2024-11-28 23:28:34 +0100 | Fokko Driesprong | Core,API: Set `503: added_snapshot_id` as required (#11626) | ✅ 已完成 | [1446_8fccdec95](commits/1446_8fccdec95/analysis.md) |
| 1447 | `7c7b4bac9` | 2024-11-29 10:39:24 +0100 | Sung Yun | Add GitHub Action to publish the `docker-rest-fixture` container (#11632) | ✅ 已完成 | [1447_7c7b4bac9](commits/1447_7c7b4bac9/analysis.md) |
| 1448 | `e770facc3` | 2024-11-29 16:05:44 +0100 | Yuya Ebihara | Core, GCS, Spark: Replace wrong order of assertion (#11677) | ✅ 已完成 | [1448_e770facc3](commits/1448_e770facc3/analysis.md) |
| 1449 | `f978fe534` | 2024-12-01 08:12:30 +0100 | Ajantha Bhat | REST: Clean up `iceberg-rest-fixture` docker image naming (#11676) | ✅ 已完成 | [1449_f978fe534](commits/1449_f978fe534/analysis.md) |
| 1450 | `233364044` | 2024-12-02 06:22:29 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.20 to 2.29.23 (#11683) | ✅ 已完成 | [1450_233364044](commits/1450_233364044/analysis.md) |
| 1451 | `578dda86d` | 2024-12-02 06:22:49 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.45 to 9.5.46 (#11680) | ✅ 已完成 | [1451_578dda86d](commits/1451_578dda86d/analysis.md) |
| 1452 | `bc36f5e33` | 2024-12-02 06:25:10 +0100 | Yuya Ebihara | REST: Use `HEAD` request to check table existence (#10999) | ✅ 已完成 | [1452_bc36f5e33](commits/1452_bc36f5e33/analysis.md) |
| 1453 | `a993b7998` | 2024-12-02 06:27:25 +0100 | dependabot[bot] | Build: Bump jackson-bom from 2.18.1 to 2.18.2 (#11681) | ✅ 已完成 | [1453_a993b7998](commits/1453_a993b7998/analysis.md) |
| 1454 | `bfeaaeb81` | 2024-12-02 06:27:43 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.47.0.0 to 3.47.1.0 (#11682) | ✅ 已完成 | [1454_bfeaaeb81](commits/1454_bfeaaeb81/analysis.md) |
| 1455 | `d8326d876` | 2024-12-02 13:58:57 -0800 | AGW | Spark 3.5: Make where clause case sensitive in rewrite data files (#11439) | ✅ 已完成 | [1455_d8326d876](commits/1455_d8326d876/analysis.md) |
| 1456 | `af8e3f5a4` | 2024-12-02 17:26:27 -0800 | Huaxin Gao | Spark: Remove extra columns for ColumnarBatch (#11551) | ✅ 已完成 | [1456_af8e3f5a4](commits/1456_af8e3f5a4/analysis.md) |
| 1457 | `6501d29b2` | 2024-12-03 09:54:47 -0800 | Eduard Tudenhoefner | Spark: Add view support to SparkSessionCatalog (#11388) | ✅ 已完成 | [1457_6501d29b2](commits/1457_6501d29b2/analysis.md) |
| 1458 | `15bf9ca54` | 2024-12-04 12:09:22 +0100 | Yuya Ebihara | Core: Fix warning message for deprecated OAuth2 server URI (#11694) | ✅ 已完成 | [1458_15bf9ca54](commits/1458_15bf9ca54/analysis.md) |
| 1459 | `c7cef9bd8` | 2024-12-04 12:10:24 +0100 | Fokko Driesprong | Build: Bump Parquet to 1.15.0 (#11656) | ✅ 已完成 | [1459_c7cef9bd8](commits/1459_c7cef9bd8/analysis.md) |
| 1460 | `3278b6990` | 2024-12-04 15:39:10 +0100 | AGW | Spark 3.3, 3.4: Make where clause case sensitive in rewrite data files (#11696) | ✅ 已完成 | [1460_3278b6990](commits/1460_3278b6990/analysis.md) |
| 1461 | `8c04bcb87` | 2024-12-04 16:41:10 +0100 | Shohei Okumiya | Core: Generalize Util.blockLocations (#11053) | ✅ 已完成 | [1461_8c04bcb87](commits/1461_8c04bcb87/analysis.md) |
| 1462 | `36140b819` | 2024-12-04 14:34:52 -0800 | Karuppayya | Docs: Spark procedure for stats collection (#11606) | ✅ 已完成 | [1462_36140b819](commits/1462_36140b819/analysis.md) |
| 1463 | `38c8daa4e` | 2024-12-05 18:18:10 -0800 | Huaxin Gao | Spark 3.5: Align RewritePositionDeleteFilesSparkAction filter with Spark case sensitivity (#11700) | ✅ 已完成 | [1463_38c8daa4e](commits/1463_38c8daa4e/analysis.md) |
| 1464 | `c91d3b764` | 2024-12-06 08:46:57 -0800 | Amogh Jahagirdar | Spark 3.5: Write DVs in Spark for V3 tables (#11561) | ✅ 已完成 | [1464_c91d3b764](commits/1464_c91d3b764/analysis.md) |
| 1465 | `f931a3dcc` | 2024-12-06 14:09:29 -0600 | Bryan Keller | Infra: Add 1.7.1 to issue template (#11711) | ✅ 已完成 | [1465_f931a3dcc](commits/1465_f931a3dcc/analysis.md) |
| 1466 | `2210e2887` | 2024-12-06 15:49:27 -0600 | Bryan Keller | Update ASF doap.rdf to Release 1.7.1 (#11712) | ✅ 已完成 | [1466_2210e2887](commits/1466_2210e2887/analysis.md) |
| 1467 | `deeb04b2c` | 2024-12-06 18:15:02 -0800 | Huaxin Gao | Spark 3.3,3.4: Align RewritePositionDeleteFilesSparkAction filter with Spark case sensitivity (#11710) | ✅ 已完成 | [1467_deeb04b2c](commits/1467_deeb04b2c/analysis.md) |
| 1468 | `5d1dc1aa5` | 2024-12-09 11:35:58 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.23 to 2.29.29 (#11723) | ✅ 已完成 | [1468_5d1dc1aa5](commits/1468_5d1dc1aa5/analysis.md) |
| 1469 | `b39253fcd` | 2024-12-09 11:48:51 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.50.0 to 26.51.0 (#11724) | ✅ 已完成 | [1469_b39253fcd](commits/1469_b39253fcd/analysis.md) |
| 1470 | `3eed132fd` | 2024-12-09 12:07:45 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.46 to 9.5.47 (#11726) | ✅ 已完成 | [1470_3eed132fd](commits/1470_3eed132fd/analysis.md) |
| 1471 | `0699c8db8` | 2024-12-09 14:07:38 +0100 | Fokko Driesprong | Add C++ to the list of languages in `doap.rdf` (#11714) | ✅ 已完成 | [1471_0699c8db8](commits/1471_0699c8db8/analysis.md) |
| 1472 | `0662373a6` | 2024-12-09 14:11:59 +0100 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.29 to 1.2.30 (#11725) | ✅ 已完成 | [1472_0662373a6](commits/1472_0662373a6/analysis.md) |
| 1473 | `b18ab74c3` | 2024-12-09 14:24:36 +0100 | dominikhei | Add `curl` to the `iceberg-rest-fixture` Docker image (#11705) | ✅ 已完成 | [1473_b18ab74c3](commits/1473_b18ab74c3/analysis.md) |
| 1474 | `70d87f175` | 2024-12-09 14:45:16 +0100 | dependabot[bot] | Build: Bump nessie from 0.100.2 to 0.101.0 (#11722) | ✅ 已完成 | [1474_70d87f175](commits/1474_70d87f175/analysis.md) |
| 1475 | `2b2efd78f` | 2024-12-09 19:35:56 +0100 | Bryan Keller | docs: 1.7.1 Release notes (#11717) | ✅ 已完成 | [1475_2b2efd78f](commits/1475_2b2efd78f/analysis.md) |
| 1476 | `d402f83fc` | 2024-12-09 11:20:45 -0800 | Ryan Blue | Docs: Add guidelines for contributors to become committers (#11670) | ✅ 已完成 | [1476_d402f83fc](commits/1476_d402f83fc/analysis.md) |
| 1477 | `28e81809e` | 2024-12-10 20:36:12 +0100 | Piotr Findeisen | Core, Flink, Spark: Drop deprecated APIs scheduled for removal in 1.8.0 (#11721) | ✅ 已完成 | [1477_28e81809e](commits/1477_28e81809e/analysis.md) |
| 1478 | `ac6509a4e` | 2024-12-10 22:33:55 +0100 | GuoYu | Flink: Fix range distribution npe when value is null (#11662) | ✅ 已完成 | [1478_ac6509a4e](commits/1478_ac6509a4e/analysis.md) |
| 1479 | `ff8134459` | 2024-12-10 18:20:12 -0700 | hsiang-c | AWS: Enable RetryMode for AWS KMS client (#11420) | ✅ 已完成 | [1479_ff8134459](commits/1479_ff8134459/analysis.md) |
| 1480 | `da53495bc` | 2024-12-11 10:04:59 -0700 | Amogh Jahagirdar | Core, Flink, Spark, KafkaConnect: Remove usage of deprecated path API (#11744) | ✅ 已完成 | [1480_da53495bc](commits/1480_da53495bc/analysis.md) |
| 1481 | `fe2f593cd` | 2024-12-11 13:45:52 -0700 | Fokko Driesprong | Infra: Build Iceberg REST fixture docker image for `arm64` architecture (#11753) | ✅ 已完成 | [1481_fe2f593cd](commits/1481_fe2f593cd/analysis.md) |
| 1482 | `af5e156ed` | 2024-12-12 08:50:21 +0100 | xxchan | Docs: fix typos in spec (#11759) | ✅ 已完成 | [1482_af5e156ed](commits/1482_af5e156ed/analysis.md) |
| 1483 | `587620b20` | 2024-12-12 08:52:02 +0100 | Ppei-Wang | Spark 3.4,3.5: Fix issue when views group by an ordinal (#11729) | ✅ 已完成 | [1483_587620b20](commits/1483_587620b20/analysis.md) |
| 1484 | `5c00b29ae` | 2024-12-12 10:01:21 +0100 | Ajantha Bhat | Spark: Remove deprecated SparkAppenderFactory (#11727) | ✅ 已完成 | [1484_5c00b29ae](commits/1484_5c00b29ae/analysis.md) |
| 1485 | `6c05f35e6` | 2024-12-12 11:58:56 +0100 | Manu Zhang | Core: Log where the missing metadata file is located for Hadoop (#11643) | ✅ 已完成 | [1485_6c05f35e6](commits/1485_6c05f35e6/analysis.md) |
| 1486 | `3053540c5` | 2024-12-12 18:07:04 +0100 | Eduard Tudenhoefner | Core: Use HEAD request to check if view exists (#11760) | ✅ 已完成 | [1486_3053540c5](commits/1486_3053540c5/analysis.md) |
| 1487 | `1e126e24e` | 2024-12-12 18:17:14 +0100 | Eduard Tudenhoefner | Core: Use HEAD request to check if namespace exists (#11761) | ✅ 已完成 | [1487_1e126e24e](commits/1487_1e126e24e/analysis.md) |
| 1488 | `a3dcfd19f` | 2024-12-12 10:01:44 -0800 | Hongyue/Steve Zhang | Hive: Optimize tableExists API in hive catalog (#11597) | ✅ 已完成 | [1488_a3dcfd19f](commits/1488_a3dcfd19f/analysis.md) |
| 1489 | `540d6a625` | 2024-12-13 09:05:11 +0100 | Yuya Ebihara | GCS: Suppress JavaUtilDate in OAuth2RefreshCredentialsHandler (#11773) | ✅ 已完成 | [1489_540d6a625](commits/1489_540d6a625/analysis.md) |
| 1490 | `c2fd77a3f` | 2024-12-14 14:49:11 -0800 | abharath9 | Flink: Add RowConverter for Iceberg Source (#11301) | ✅ 已完成 | [1490_c2fd77a3f](commits/1490_c2fd77a3f/analysis.md) |
| 1491 | `bcf7b630e` | 2024-12-14 23:35:05 -0800 | Liurnly | Spark 3.5: Fix assertion mismatch in PartitionedWritesTestBase/TestRewritePositionDeleteFilesAction (#11748) | ✅ 已完成 | [1491_bcf7b630e](commits/1491_bcf7b630e/analysis.md) |
| 1492 | `fd739b32b` | 2024-12-15 15:42:53 +0100 | dependabot[bot] | Build: Bump nessie from 0.101.0 to 0.101.2 (#11791) | ✅ 已完成 | [1492_fd739b32b](commits/1492_fd739b32b/analysis.md) |
| 1493 | `592b60408` | 2024-12-16 07:52:43 +0100 | ajreid21 | Core: Add missing REST endpoint definitions (#11756) | ✅ 已完成 | [1493_592b60408](commits/1493_592b60408/analysis.md) |
| 1494 | `1851ca1b9` | 2024-12-16 08:26:19 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.29 to 2.29.34 (#11793) | ✅ 已完成 | [1494_1851ca1b9](commits/1494_1851ca1b9/analysis.md) |
| 1495 | `2a5b089aa` | 2024-12-16 08:50:49 +0100 | Eduard Tudenhoefner | Spark: Read DVs when reading from .position_deletes table (#11657) | ✅ 已完成 | [1495_2a5b089aa](commits/1495_2a5b089aa/analysis.md) |
| 1496 | `f40ec2096` | 2024-12-16 11:10:03 +0100 | Eduard Tudenhoefner | Core: Add TableUtil to provide access to a table's format version (#11620) | ✅ 已完成 | [1496_f40ec2096](commits/1496_f40ec2096/analysis.md) |
| 1497 | `16cc4e95c` | 2024-12-16 15:49:36 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.47 to 9.5.48 (#11790) | ✅ 已完成 | [1497_16cc4e95c](commits/1497_16cc4e95c/analysis.md) |
| 1498 | `791d0fa6c` | 2024-12-16 10:34:05 -0800 | Eduard Tudenhoefner | Spark 3.4: Add REST catalog to Spark integration tests (#11698) | ✅ 已完成 | [1498_791d0fa6c](commits/1498_791d0fa6c/analysis.md) |
| 1499 | `57ea31047` | 2024-12-16 12:46:05 -0800 | Ryan Blue | Parquet: Implement defaults for generic data (#11785) | ✅ 已完成 | [1499_57ea31047](commits/1499_57ea31047/analysis.md) |
| 1500 | `b9b61b1d7` | 2024-12-16 14:31:01 -0800 | Ryan Blue | Avro: Support default values for generic data (#11786) | ✅ 已完成 | [1500_b9b61b1d7](commits/1500_b9b61b1d7/analysis.md) |
| 1501 | `ac865e334` | 2024-12-17 09:22:36 +0100 | Ajantha Bhat | REST: Use `apache/iceberg-rest-fixture` docker image (#11673) | ✅ 已完成 | [1501_ac865e334](commits/1501_ac865e334/analysis.md) |
| 1502 | `5c170ae91` | 2024-12-17 15:50:58 +0100 | Manu Zhang | docs: Default value of table level distribution-mode should be not set (#11663) | ✅ 已完成 | [1502_5c170ae91](commits/1502_5c170ae91/analysis.md) |
| 1503 | `3adcd8906` | 2024-12-17 16:09:30 +0100 | Manu Zhang | Docs: Fix Spark catalog `table-override` description (#11684) | ✅ 已完成 | [1503_3adcd8906](commits/1503_3adcd8906/analysis.md) |
| 1504 | `ce7a4b42f` | 2024-12-17 18:16:55 +0100 | Fokko Driesprong | API: Add missing deprecations (#11734) | ✅ 已完成 | [1504_ce7a4b42f](commits/1504_ce7a4b42f/analysis.md) |
| 1505 | `ed06c9cad` | 2024-12-17 21:06:54 +0100 | Manu Zhang | Core, Spark 3.5: Fix test failures due to timeout (#11654) | ✅ 已完成 | [1505_ed06c9cad](commits/1505_ed06c9cad/analysis.md) |
| 1506 | `a6cfc12ab` | 2024-12-17 15:11:34 -0800 | Alexandre Dutra | Auth Manager API part 1: HTTPRequest, HTTPHeader (#11769) | ✅ 已完成 | [1506_a6cfc12ab](commits/1506_a6cfc12ab/analysis.md) |
| 1507 | `e3628c18c` | 2024-12-17 22:08:15 -0800 | big face cat | Flink: make `StatisticsOrRecord` to be correctly serialized and deser… (#11557) | ✅ 已完成 | [1507_e3628c18c](commits/1507_e3628c18c/analysis.md) |
| 1508 | `b428fbc59` | 2024-12-18 07:40:48 +0100 | Ppei-Wang | Spark 3.4,3.5: Use correct identifier in view DESCRIBE cmd (#11751) | ✅ 已完成 | [1508_b428fbc59](commits/1508_b428fbc59/analysis.md) |
| 1509 | `204a49ca3` | 2024-12-18 07:35:48 -0800 | Karol Sobczak | Use try-with-resources in TestParallelIterable (#11810) | ✅ 已完成 | [1509_204a49ca3](commits/1509_204a49ca3/analysis.md) |
| 1510 | `7e1a4c9fe` | 2024-12-18 08:10:15 -0800 | Ryan Blue | Spark 3.5: Support default values in Parquet reader (#11803) | ✅ 已完成 | [1510_7e1a4c9fe](commits/1510_7e1a4c9fe/analysis.md) |
| 1511 | `d0effc6d7` | 2024-12-18 13:38:59 -0800 | Ryan Blue | Data: Fix Parquet and Avro defaults date/time representation (#11811) | ✅ 已完成 | [1511_d0effc6d7](commits/1511_d0effc6d7/analysis.md) |
| 1512 | `91a1505d0` | 2024-12-18 14:55:05 -0800 | Marc Cenac | Revert "Support WASB scheme in ADLSFileIO (#11504)" (#11812) | ✅ 已完成 | [1512_91a1505d0](commits/1512_91a1505d0/analysis.md) |
| 1513 | `88a25967e` | 2024-12-19 07:53:41 -0700 | Amogh Jahagirdar | Core, Spark, Flink, Hive: Remove unused failsafe dependency from core and add Failsafe to runtime LICENSE(s) (#11816) | ✅ 已完成 | [1513_88a25967e](commits/1513_88a25967e/analysis.md) |
| 1514 | `3535240c3` | 2024-12-19 10:58:22 -0800 | Mingliang Liu | Docs: Change to Flink directory for instructions (#11031) | ✅ 已完成 | [1514_3535240c3](commits/1514_3535240c3/analysis.md) |
| 1515 | `70336679e` | 2024-12-19 11:36:49 -0800 | Ryan Blue | Spark 3.5: Support default values in vectorized reads (#11815) | ✅ 已完成 | [1515_70336679e](commits/1515_70336679e/analysis.md) |
| 1516 | `ed36a9f9a` | 2024-12-19 23:28:33 -0800 | Tan Qi | Spark 3.5: Remove numbers from assert description in TestRewritePositionDeleteFilesAction (#11827) | ✅ 已完成 | [1516_ed36a9f9a](commits/1516_ed36a9f9a/analysis.md) |
| 1517 | `cdf748e8e` | 2024-12-20 09:51:51 -0800 | Alexandre Dutra | Auth Manager API part 2: AuthManager (#11809) | ✅ 已完成 | [1517_cdf748e8e](commits/1517_cdf748e8e/analysis.md) |
| 1518 | `dea2fd1d9` | 2024-12-20 13:58:43 -0800 | Ryan Blue | Core: Add Variant implementation to read serialized objects (#11415) | ✅ 已完成 | [1518_dea2fd1d9](commits/1518_dea2fd1d9/analysis.md) |
| 1519 | `cd187c571` | 2024-12-20 16:31:48 -0800 | Ryan Blue | Spark: Test reading default values in Spark (#11832) | ✅ 已完成 | [1519_cd187c571](commits/1519_cd187c571/analysis.md) |
| 1520 | `dbf26d7bf` | 2024-12-22 21:45:26 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.26.3 to 0.26.4 (#11856) | ✅ 已完成 | [1520_dbf26d7bf](commits/1520_dbf26d7bf/analysis.md) |
| 1521 | `4ceb96d48` | 2024-12-22 21:55:03 +0100 | dependabot[bot] | Build: Bump mkdocs-awesome-pages-plugin from 2.9.3 to 2.10.0 (#11855) | ✅ 已完成 | [1521_4ceb96d48](commits/1521_4ceb96d48/analysis.md) |
| 1522 | `b0a119c29` | 2024-12-22 21:59:32 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.48 to 9.5.49 (#11854) | ✅ 已完成 | [1522_b0a119c29](commits/1522_b0a119c29/analysis.md) |
| 1523 | `5c5d7c9b7` | 2024-12-22 22:05:03 +0100 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.115.Final to 4.1.116.Final (#11853) | ✅ 已完成 | [1523_5c5d7c9b7](commits/1523_5c5d7c9b7/analysis.md) |
| 1524 | `bbf5d6f84` | 2024-12-22 22:05:32 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.34 to 2.29.39 (#11851) | ✅ 已完成 | [1524_bbf5d6f84](commits/1524_bbf5d6f84/analysis.md) |
| 1525 | `556969ad2` | 2024-12-22 22:05:51 +0100 | dependabot[bot] | Build: Bump guava from 33.3.1-jre to 33.4.0-jre (#11850) | ✅ 已完成 | [1525_556969ad2](commits/1525_556969ad2/analysis.md) |
| 1526 | `e0ccebca5` | 2024-12-22 22:10:07 +0100 | dependabot[bot] | Build: Bump junit from 5.11.3 to 5.11.4 (#11849) | ✅ 已完成 | [1526_e0ccebca5](commits/1526_e0ccebca5/analysis.md) |
| 1527 | `e4d9c1d7a` | 2024-12-22 22:14:47 +0100 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.26.3 to 3.27.0 (#11847) | ✅ 已完成 | [1527_e4d9c1d7a](commits/1527_e4d9c1d7a/analysis.md) |
| 1528 | `12d7ee5c0` | 2024-12-22 23:43:54 +0100 | dependabot[bot] | Build: Bump nessie from 0.101.2 to 0.101.3 (#11852) | ✅ 已完成 | [1528_12d7ee5c0](commits/1528_12d7ee5c0/analysis.md) |
| 1529 | `f7748f20a` | 2024-12-22 23:44:09 +0100 | dependabot[bot] | Build: Bump junit-platform from 1.11.3 to 1.11.4 (#11848) | ✅ 已完成 | [1529_f7748f20a](commits/1529_f7748f20a/analysis.md) |
| 1530 | `55f10ca7c` | 2024-12-23 10:46:01 +0800 | Renjie Liu | Doc: Add status page for different implementations. (#11772) | ✅ 已完成 | [1530_55f10ca7c](commits/1530_55f10ca7c/analysis.md) |
| 1531 | `ca3db931b` | 2024-12-23 08:41:39 +0100 | JB Onofré | Upgrade to Gradle 8.12 (#11861) | ✅ 已完成 | [1531_ca3db931b](commits/1531_ca3db931b/analysis.md) |
| 1532 | `dbd7d1c6c` | 2024-12-23 08:26:32 -0700 | Manu Zhang | Build: Fix ignoring `.asf.yaml` in PR (#11860) | ✅ 已完成 | [1532_dbd7d1c6c](commits/1532_dbd7d1c6c/analysis.md) |
| 1533 | `c6d9e0cdd` | 2024-12-24 11:05:07 +0100 | JB Onofré | Gradle: Update `gradlew` with better `APP_HOME` definition (#11869) | ✅ 已完成 | [1533_c6d9e0cdd](commits/1533_c6d9e0cdd/analysis.md) |
| 1534 | `d6d3cf58a` | 2024-12-24 11:05:55 +0100 | Yuya Ebihara | Core, Spark: Avoid deprecated methods in Guava Files (#11865) | ✅ 已完成 | [1534_d6d3cf58a](commits/1534_d6d3cf58a/analysis.md) |
| 1535 | `1b5886d0e` | 2024-12-24 08:24:22 -0700 | Yuya Ebihara | Core: Don't clear snapshotLog in `TableMetadata.removeRef` (#11779) | ✅ 已完成 | [1535_1b5886d0e](commits/1535_1b5886d0e/analysis.md) |
| 1536 | `4eb9f7ffa` | 2024-12-25 20:06:18 +0100 | Yuya Ebihara | Core: Replace deprecated Schema.toString with SchemaFormatter (#11867) | ✅ 已完成 | [1536_4eb9f7ffa](commits/1536_4eb9f7ffa/analysis.md) |
| 1537 | `bb27030a4` | 2024-12-25 22:26:27 +0100 | Manu Zhang | Build: Fix ignoring `license-check.yml` in PR (#11873) | ✅ 已完成 | [1537_bb27030a4](commits/1537_bb27030a4/analysis.md) |
| 1538 | `fc3f705ab` | 2024-12-26 08:19:16 +0100 | Yuya Ebihara | API: Replace deprecated `asList` with `asInstanceOf` (#11875) | ✅ 已完成 | [1538_fc3f705ab](commits/1538_fc3f705ab/analysis.md) |
| 1539 | `de54a0867` | 2024-12-26 17:28:40 -0800 | big face cat | Flink: Avoid RANGE mode broken chain when write parallelism changes (#11702) | ✅ 已完成 | [1539_de54a0867](commits/1539_de54a0867/analysis.md) |
| 1540 | `607e2fbec` | 2024-12-28 17:19:00 +0100 | Gabriel Igliozzi | Update `README.md` with `iceberg-cpp` (#11882) | ✅ 已完成 | [1540_607e2fbec](commits/1540_607e2fbec/analysis.md) |
| 1541 | `0029d6a7e` | 2024-12-29 21:36:40 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.39 to 2.29.43 (#11886) | ✅ 已完成 | [1541_0029d6a7e](commits/1541_0029d6a7e/analysis.md) |
| 1542 | `3fa8a46bc` | 2024-12-29 21:37:00 +0100 | dependabot[bot] | Build: Bump mkdocs-awesome-pages-plugin from 2.10.0 to 2.10.1 (#11885) | ✅ 已完成 | [1542_3fa8a46bc](commits/1542_3fa8a46bc/analysis.md) |
| 1543 | `7f14032be` | 2024-12-29 21:52:21 +0100 | Shohei Okumiya | Core: Fix typo in HadoopTableOperations (#11880) | ✅ 已完成 | [1543_7f14032be](commits/1543_7f14032be/analysis.md) |
| 1544 | `e3f50e5c6` | 2024-12-30 14:04:36 -0700 | Fokko Driesprong | Revert "Hive: close the fileIO client when closing the hive catalog (#10771)" (#11858) | ✅ 已完成 | [1544_e3f50e5c6](commits/1544_e3f50e5c6/analysis.md) |
| 1545 | `ab6365d42` | 2025-01-02 20:38:24 +0100 | Shohei Okumiya | Docs: Add history to Hive's metadata tables (#11902) | ✅ 已完成 | [1545_ab6365d42](commits/1545_ab6365d42/analysis.md) |
| 1546 | `3b0004343` | 2025-01-03 09:54:38 +0100 | Yuya Ebihara | Doc: Fix format of Hive (#11892) | ✅ 已完成 | [1546_3b0004343](commits/1546_3b0004343/analysis.md) |
| 1547 | `4d3568229` | 2025-01-03 10:00:23 +0100 | GuoYu | Flink: Backport #11662 Fix range distribution npe when value is null to Flink 1.18 and 1.19 (#11745) | ✅ 已完成 | [1547_4d3568229](commits/1547_4d3568229/analysis.md) |
| 1548 | `c0d6d42a5` | 2025-01-03 09:40:04 -0700 | Amogh Jahagirdar | Spark: Change delete file granularity to file in Spark 3.5 (#11478) | ✅ 已完成 | [1548_c0d6d42a5](commits/1548_c0d6d42a5/analysis.md) |
| 1549 | `dbfefb073` | 2025-01-03 21:00:33 +0100 | Cheng Pan | Bump Apache Spark to 3.5.4 (#11731) | ✅ 已完成 | [1549_dbfefb073](commits/1549_dbfefb073/analysis.md) |
| 1550 | `fcd5dd932` | 2025-01-04 10:25:10 -0800 | Vova Kolmakov | Kafka-connect-runtime: remove code duplications in integration tests (#11883) | ✅ 已完成 | [1550_fcd5dd932](commits/1550_fcd5dd932/analysis.md) |
| 1551 | `8e2ffb35d` | 2025-01-06 11:09:13 +0100 | big face cat | Flink: Backport #11557 to Flink 1.19 and 1.18 (#11834) | ✅ 已完成 | [1551_8e2ffb35d](commits/1551_8e2ffb35d/analysis.md) |
| 1552 | `fc923b3af` | 2025-01-06 13:32:06 -0800 | abharath9 | replace legacy converter with new (#11838) | ✅ 已完成 | [1552_fc923b3af](commits/1552_fc923b3af/analysis.md) |
| 1553 | `b9da63885` | 2025-01-07 08:44:16 +0100 | Hector Geraldino | Docs: Improve wording (#11916) | ✅ 已完成 | [1553_b9da63885](commits/1553_b9da63885/analysis.md) |
| 1554 | `9b2a63275` | 2025-01-07 08:44:43 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.43 to 2.29.45 (#11910) | ✅ 已完成 | [1554_9b2a63275](commits/1554_9b2a63275/analysis.md) |
| 1555 | `a6f4160ca` | 2025-01-07 08:45:05 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.47.1.0 to 3.47.2.0 (#11907) | ✅ 已完成 | [1555_a6f4160ca](commits/1555_a6f4160ca/analysis.md) |
| 1556 | `e1d2271ad` | 2025-01-07 11:09:16 +0100 | Hongyue/Steve Zhang | Hive: Optimize viewExists API in hive catalog (#11813) | ✅ 已完成 | [1556_e1d2271ad](commits/1556_e1d2271ad/analysis.md) |
| 1557 | `f12958846` | 2025-01-07 11:19:22 +0100 | Yuya Ebihara | Core: Add support for view-default property in catalog (#11064) | ✅ 已完成 | [1557_f12958846](commits/1557_f12958846/analysis.md) |
| 1558 | `7c4c3793e` | 2025-01-07 11:20:31 +0100 | dependabot[bot] | Build: Bump io.delta:delta-standalone_2.12 from 3.2.1 to 3.3.0 (#11909) | ✅ 已完成 | [1558_7c4c3793e](commits/1558_7c4c3793e/analysis.md) |
| 1559 | `07ab53eed` | 2025-01-07 12:22:57 +0100 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.27.0 to 3.27.2 (#11908) | ✅ 已完成 | [1559_07ab53eed](commits/1559_07ab53eed/analysis.md) |
| 1560 | `1cbc163a1` | 2025-01-07 12:40:27 +0100 | Yuya Ebihara | Doc: Add missing fields to metadata tables in Spark page (#11897) | ✅ 已完成 | [1560_1cbc163a1](commits/1560_1cbc163a1/analysis.md) |
| 1561 | `67e084c8d` | 2025-01-07 11:26:46 -0700 | advancedxy | API: Support removeUnusedSpecs in ExpireSnapshots (#10755) | ✅ 已完成 | [1561_67e084c8d](commits/1561_67e084c8d/analysis.md) |
| 1562 | `39a4cfd6c` | 2025-01-08 22:54:39 +0800 | Szehon Ho | Spark 3.5: Implement RewriteTablePath (#11555) | ✅ 已完成 | [1562_39a4cfd6c](commits/1562_39a4cfd6c/analysis.md) |
| 1563 | `df547908a` | 2025-01-08 10:00:18 -0700 | Manu Zhang | Infra: Add manuzhang to collaborators (#11927) | ✅ 已完成 | [1563_df547908a](commits/1563_df547908a/analysis.md) |
| 1564 | `72dcce95e` | 2025-01-09 14:05:52 -0600 | Russell Spitzer | Site: Put a CFP Banner on the Homepage (#11942) | ✅ 已完成 | [1564_72dcce95e](commits/1564_72dcce95e/analysis.md) |
| 1565 | `3dbb5cc42` | 2025-01-10 09:22:02 -0600 | Huaxin Gao | Parquet: Use compatible column name to set Parquet bloom filter (#11799) | ✅ 已完成 | [1565_3dbb5cc42](commits/1565_3dbb5cc42/analysis.md) |
| 1566 | `97b5b39d3` | 2025-01-10 09:53:26 -0800 | Anurag Mantripragada | Core: Allow adding files to multiple partition specs in FastAppend (#11771) | ✅ 已完成 | [1566_97b5b39d3](commits/1566_97b5b39d3/analysis.md) |
| 1567 | `a100e6a2d` | 2025-01-10 10:39:56 -0800 | Ajantha Bhat | Avro: Add writers for the internal object model (#11919) | ✅ 已完成 | [1567_a100e6a2d](commits/1567_a100e6a2d/analysis.md) |
| 1568 | `c7910bb40` | 2025-01-11 17:13:18 +0100 | Karol Sobczak | Core: Fix possible deadlock in ParallelIterable (#11781) | ✅ 已完成 | [1568_c7910bb40](commits/1568_c7910bb40/analysis.md) |
| 1569 | `7792896d0` | 2025-01-13 07:02:00 +0100 | Manu Zhang | Hive: Remove Hive runtime (#11801) | ✅ 已完成 | [1569_7792896d0](commits/1569_7792896d0/analysis.md) |
| 1570 | `a9d320eb8` | 2025-01-13 07:11:36 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.45 to 2.29.50 (#11949) | ✅ 已完成 | [1570_a9d320eb8](commits/1570_a9d320eb8/analysis.md) |
| 1571 | `f7d40f071` | 2025-01-13 07:36:09 +0100 | S N Munendra | Core,Rest: Read the max connection from properties (#11522) | ✅ 已完成 | [1571_f7d40f071](commits/1571_f7d40f071/analysis.md) |
| 1572 | `dc47b854a` | 2025-01-13 12:42:26 +0100 | Neodon | docs: Use the YAML multi-line indicator (#11552) | ✅ 已完成 | [1572_dc47b854a](commits/1572_dc47b854a/analysis.md) |
| 1573 | `95424abc6` | 2025-01-13 13:10:51 +0100 | feng xiaohang | API: Fix `sizeBytes` parameter of `ScanTask` (#11941) | ✅ 已完成 | [1573_95424abc6](commits/1573_95424abc6/analysis.md) |
| 1574 | `1726565ae` | 2025-01-13 13:17:37 +0100 | dependabot[bot] | Build: Bump io.delta:delta-spark_2.12 from 3.2.1 to 3.3.0 (#11911) | ✅ 已完成 | [1574_1726565ae](commits/1574_1726565ae/analysis.md) |
| 1575 | `af8c9d6b4` | 2025-01-13 13:17:56 +0100 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.20.0 to 3.21.0 (#11792) | ✅ 已完成 | [1575_af8c9d6b4](commits/1575_af8c9d6b4/analysis.md) |
| 1576 | `5fd16b5bf` | 2025-01-13 13:18:18 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.51.0 to 26.52.0 (#11846) | ✅ 已完成 | [1576_5fd16b5bf](commits/1576_5fd16b5bf/analysis.md) |
| 1577 | `472ec6cbd` | 2025-01-13 16:41:07 +0100 | Eduard Tudenhoefner | Core: Add tests for catalogs supporting empty namespaces (#9890) | ✅ 已完成 | [1577_472ec6cbd](commits/1577_472ec6cbd/analysis.md) |
| 1578 | `3247964c8` | 2025-01-13 10:30:02 -0600 | S N Munendra | Spec: Add cross-region bucket access property to config (#11260) | ✅ 已完成 | [1578_3247964c8](commits/1578_3247964c8/analysis.md) |
| 1579 | `c98d0d0d4` | 2025-01-14 10:44:21 +0100 | dongwang | Core: Move namespace/table/view validation into try-catch block (#11960) | ✅ 已完成 | [1579_c98d0d0d4](commits/1579_c98d0d0d4/analysis.md) |
| 1580 | `5b259e297` | 2025-01-14 10:48:13 +0100 | Mingyu Chen (Rayner) | Docs: Update docs link about Apache Doris and update vendors list (#11956) | ✅ 已完成 | [1580_5b259e297](commits/1580_5b259e297/analysis.md) |
| 1581 | `c0bd4bfbc` | 2025-01-14 15:00:24 +0100 | John Bampton | docs: update `README.md` fix brand name `macOS` (#11964) | ✅ 已完成 | [1581_c0bd4bfbc](commits/1581_c0bd4bfbc/analysis.md) |
| 1582 | `cf8b354c4` | 2025-01-14 17:27:48 +0100 | gaborkaszab | Core: Fix loading a table in CachingCatalog with Metadata table name (#11738) | ✅ 已完成 | [1582_cf8b354c4](commits/1582_cf8b354c4/analysis.md) |
| 1583 | `8cd5b1985` | 2025-01-14 20:09:38 +0100 | Fokko Driesprong | Open-API: Bump to OpenAPI 3.1 (#11955) | ✅ 已完成 | [1583_8cd5b1985](commits/1583_8cd5b1985/analysis.md) |
| 1584 | `c31d5d58e` | 2025-01-15 09:41:05 +0100 | Eduard Tudenhoefner | Build: Bump openapi-generator plugin from 6.6.0 to 7.10.0 (#11970) | ✅ 已完成 | [1584_c31d5d58e](commits/1584_c31d5d58e/analysis.md) |
| 1585 | `5d05cd414` | 2025-01-15 10:46:32 +0100 | Fokko Driesprong | Run `java-ci` on changes in `open-api/**` (#11972) | ✅ 已完成 | [1585_5d05cd414](commits/1585_5d05cd414/analysis.md) |
| 1586 | `978189cbe` | 2025-01-15 11:07:07 +0100 | Eduard Tudenhoefner | AWS, Core, GCP: Support relative credential endpoint / pass OAuth2 token to credential provider (#11954) | ✅ 已完成 | [1586_978189cbe](commits/1586_978189cbe/analysis.md) |
| 1587 | `101e65041` | 2025-01-15 12:02:13 +0100 | Yuya Ebihara | Doc: Add Hive DELETE ORPHAN-FILES example (#11896) | ✅ 已完成 | [1587_101e65041](commits/1587_101e65041/analysis.md) |
| 1588 | `a0a1c002f` | 2025-01-15 17:26:08 +0100 | Eduard Tudenhoefner | Spark 3.4: Add view support to SparkSessionCatalog (#11797) | ✅ 已完成 | [1588_a0a1c002f](commits/1588_a0a1c002f/analysis.md) |
| 1589 | `d96901b84` | 2025-01-15 10:57:56 -0700 | Amogh Jahagirdar | Spark 3.4: Backport rewriting historical file-scoped deletes (#11273) to 3.4 (#11975) | ✅ 已完成 | [1589_d96901b84](commits/1589_d96901b84/analysis.md) |
| 1590 | `167d450c7` | 2025-01-16 12:42:38 +0100 | dmgkeke | API: Support sanitizing a `Literal<?>` (#11943) | ✅ 已完成 | [1590_167d450c7](commits/1590_167d450c7/analysis.md) |
| 1591 | `a0777bca7` | 2025-01-16 08:44:27 -0800 | Alexandre Dutra | Auth Manager API part 3: OAuth2 Manager (#11844) | ✅ 已完成 | [1591_a0777bca7](commits/1591_a0777bca7/analysis.md) |
| 1592 | `b128bba57` | 2025-01-16 18:03:55 +0100 | Manu Zhang | Spark: Fix flaky tests `withSnapshotIsolation` (#11974) | ✅ 已完成 | [1592_b128bba57](commits/1592_b128bba57/analysis.md) |
| 1593 | `246439a96` | 2025-01-16 10:47:59 -0700 | Leon Lin | Spark: Fix empty scan issue when start timestamp retrieves root snapshot and end timestamp is missing (#11967) | ✅ 已完成 | [1593_246439a96](commits/1593_246439a96/analysis.md) |
| 1594 | `d97ac3eff` | 2025-01-16 22:27:05 -0700 | Ryan Blue | Spark 3.4: Backport support for default values (#11987) | ✅ 已完成 | [1594_d97ac3eff](commits/1594_d97ac3eff/analysis.md) |
| 1595 | `49f41f5eb` | 2025-01-17 08:13:53 +0100 | Yuya Ebihara | Doc: Add missing content value to manifests table (#11989) | ✅ 已完成 | [1595_49f41f5eb](commits/1595_49f41f5eb/analysis.md) |
| 1596 | `4d0f40c5b` | 2025-01-17 06:59:22 -0700 | Amogh Jahagirdar | Spark: Fix Puffin suffix for DVs (#11986) | ✅ 已完成 | [1596_4d0f40c5b](commits/1596_4d0f40c5b/analysis.md) |
| 1597 | `f895b33dd` | 2025-01-17 15:56:26 -0600 | Russell Spitzer | Spec: Add added-rows field to Snapshot (#11976) | ✅ 已完成 | [1597_f895b33dd](commits/1597_f895b33dd/analysis.md) |
| 1598 | `bed7c3317` | 2025-01-17 14:58:15 -0700 | Liurnly | API: add hashcode cache in StructType (#11764) | ✅ 已完成 | [1598_bed7c3317](commits/1598_bed7c3317/analysis.md) |
| 1599 | `1a69ff8bc` | 2025-01-19 10:24:33 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.49 to 9.5.50 (#12005) | ✅ 已完成 | [1599_1a69ff8bc](commits/1599_1a69ff8bc/analysis.md) |
| 1600 | `4e00a3185` | 2025-01-19 10:24:46 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.26.4 to 0.26.5 (#12004) | ✅ 已完成 | [1600_4e00a3185](commits/1600_4e00a3185/analysis.md) |
| 1601 | `7e0cd3fa1` | 2025-01-19 10:44:15 -0700 | Fokko Driesprong | Revert "API: add hashcode cache in StructType (#11764)" (#12007) | ✅ 已完成 | [1601_7e0cd3fa1](commits/1601_7e0cd3fa1/analysis.md) |
| 1602 | `3203a230d` | 2025-01-19 22:13:08 +0100 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.116.Final to 4.1.117.Final (#11999) | ✅ 已完成 | [1602_3203a230d](commits/1602_3203a230d/analysis.md) |
| 1603 | `894c7cae7` | 2025-01-19 22:13:33 +0100 | dependabot[bot] | Build: Bump org.apache.datasketches:datasketches-java (#12000) | ✅ 已完成 | [1603_894c7cae7](commits/1603_894c7cae7/analysis.md) |
| 1604 | `6a608a5cf` | 2025-01-19 22:14:04 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.47.2.0 to 3.48.0.0 (#12001) | ✅ 已完成 | [1604_6a608a5cf](commits/1604_6a608a5cf/analysis.md) |
| 1605 | `7b95e17b8` | 2025-01-19 22:14:20 +0100 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.27.2 to 3.27.3 (#12002) | ✅ 已完成 | [1605_7b95e17b8](commits/1605_7b95e17b8/analysis.md) |
| 1606 | `c19223d35` | 2025-01-19 22:14:48 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.52.0 to 26.53.0 (#12003) | ✅ 已完成 | [1606_c19223d35](commits/1606_c19223d35/analysis.md) |
| 1607 | `28325437f` | 2025-01-20 08:30:08 +0100 | Manu Zhang | Spark: Don't skip tests in TestSelect for SparkSessionCatalog (#11824) | ✅ 已完成 | [1607_28325437f](commits/1607_28325437f/analysis.md) |
| 1608 | `41b458b70` | 2025-01-20 11:51:12 +0100 | Eduard Tudenhoefner | Core: List namespaces/tables when testing identifier with a dot (#11991) | ✅ 已完成 | [1608_41b458b70](commits/1608_41b458b70/analysis.md) |
| 1609 | `b07455a94` | 2025-01-20 11:13:49 -0700 | Marc Cenac | Azure: Support WASB scheme in ADLSFileIO  (#11830) | ✅ 已完成 | [1609_b07455a94](commits/1609_b07455a94/analysis.md) |
| 1610 | `17432475c` | 2025-01-20 17:43:31 -0700 | Om Kenge | Docs: Update Footer Copyright Year (#12011) | ✅ 已完成 | [1610_17432475c](commits/1610_17432475c/analysis.md) |
| 1611 | `445687d96` | 2025-01-21 09:04:03 -0600 | Fokko Driesprong | Spec: Clarify `next-row-id` (#12018) | ✅ 已完成 | [1611_445687d96](commits/1611_445687d96/analysis.md) |
| 1612 | `8dee55ded` | 2025-01-21 17:54:35 +0100 | Maximilian Michels | Flink: Upgrade Flink version 1.19.0 => 1.19.1 (#12021) | ✅ 已完成 | [1612_8dee55ded](commits/1612_8dee55ded/analysis.md) |
| 1613 | `6eef7809f` | 2025-01-21 10:26:10 -0700 | Amogh Jahagirdar | Update notice files to reference 2025 (#12013) | ✅ 已完成 | [1613_6eef7809f](commits/1613_6eef7809f/analysis.md) |
| 1614 | `e13a87f7d` | 2025-01-21 11:39:30 -0700 | Amogh Jahagirdar | Spark 3.4: Backport writing DVs to Spark 3.4 (#12019) | ✅ 已完成 | [1614_e13a87f7d](commits/1614_e13a87f7d/analysis.md) |
| 1615 | `5b13760c0` | 2025-01-21 15:46:20 -0700 | Ryan Blue | Spark 3.3: Backport support for default values (#11988) | ✅ 已完成 | [1615_5b13760c0](commits/1615_5b13760c0/analysis.md) |
| 1616 | `ab9e05e73` | 2025-01-22 09:04:27 +0100 | Yuya Ebihara | Doc: Fix expired links on vendor page (#12045) | ✅ 已完成 | [1616_ab9e05e73](commits/1616_ab9e05e73/analysis.md) |
| 1617 | `8d7ad4a7c` | 2025-01-22 11:50:33 +0100 | hengm3467 | Docs: Add RisingWave to the Vendors page (#12043) | ✅ 已完成 | [1617_8d7ad4a7c](commits/1617_8d7ad4a7c/analysis.md) |
| 1618 | `9a46890e1` | 2025-01-22 14:57:03 +0100 | Fokko Driesprong | Build: Nightly build for Iceberg REST fixtures (#12008) | ✅ 已完成 | [1618_9a46890e1](commits/1618_9a46890e1/analysis.md) |
| 1619 | `5091e5752` | 2025-01-22 08:14:29 -0700 | Ryan Blue | ORC: Fail when initial default support is required. (#12026) | ✅ 已完成 | [1619_5091e5752](commits/1619_5091e5752/analysis.md) |
| 1620 | `e86d25f28` | 2025-01-22 17:53:07 +0100 | Yuya Ebihara | Core: Add missing default HEAD endpoints and V1_COMMIT_TRANSACTION (#11980) | ✅ 已完成 | [1620_e86d25f28](commits/1620_e86d25f28/analysis.md) |
| 1621 | `be6e9daf8` | 2025-01-22 11:15:44 -0800 | Huaxin Gao | Spark 3.5: Refactor delete logic in batch reading (#11933) | ✅ 已完成 | [1621_be6e9daf8](commits/1621_be6e9daf8/analysis.md) |
| 1622 | `84c8db40f` | 2025-01-23 07:48:22 +0100 | Yuya Ebihara | AWS, Core, Delta: Remove redundant charset lookup (#12057) | ✅ 已完成 | [1622_84c8db40f](commits/1622_84c8db40f/analysis.md) |
| 1623 | `5ce33448e` | 2025-01-23 14:52:52 +0100 | JB Onofré | Remove `slf4j-api` reference in `LICENSE` (#12052) | ✅ 已完成 | [1623_5ce33448e](commits/1623_5ce33448e/analysis.md) |
| 1624 | `ce2af527a` | 2025-01-23 10:58:49 -0600 | Elizabeth Christensen | Adding Crunchy Data to Iceberg Vendors list (#12020) | ✅ 已完成 | [1624_ce2af527a](commits/1624_ce2af527a/analysis.md) |
| 1625 | `908bdc35a` | 2025-01-23 12:42:45 -0700 | JB Onofré | Flink 1.20: Support default values in Parquet reader (#11839) | ✅ 已完成 | [1625_908bdc35a](commits/1625_908bdc35a/analysis.md) |
| 1626 | `17bda20b2` | 2025-01-23 14:02:42 -0700 | JB Onofré | Flink: Backport default values support in Parquet reader on Flink v1.18 and v1.19 (#12072) | ✅ 已完成 | [1626_17bda20b2](commits/1626_17bda20b2/analysis.md) |
| 1627 | `a7ed3ca3b` | 2025-01-23 20:33:08 -0800 | Huaxin Gao | Spark 3.5: Fix Javadoc in ColumnarBatchUtil (#12058) | ✅ 已完成 | [1627_a7ed3ca3b](commits/1627_a7ed3ca3b/analysis.md) |
| 1628 | `2e2b728ff` | 2025-01-24 07:47:01 +0100 | Eduard Tudenhoefner | Core, Spark: Rewrite data files with high delete ratio (#11825) | ✅ 已完成 | [1628_2e2b728ff](commits/1628_2e2b728ff/analysis.md) |
| 1629 | `026a9b004` | 2025-01-24 07:47:34 +0100 | Eduard Tudenhoefner | Core, Spark: Include content offset/size in PositionDeletesTable (#11808) | ✅ 已完成 | [1629_026a9b004](commits/1629_026a9b004/analysis.md) |
| 1630 | `681ff5772` | 2025-01-24 09:36:45 +0100 | Eduard Tudenhoefner | Core: Retain current view version during expiration (#12067) | ✅ 已完成 | [1630_681ff5772](commits/1630_681ff5772/analysis.md) |
| 1631 | `72a165a81` | 2025-01-24 09:52:24 +0100 | Hongyue/Steve Zhang | Spark 3.5: Procedure to rewrite table path (#11931) | ✅ 已完成 | [1631_72a165a81](commits/1631_72a165a81/analysis.md) |
| 1632 | `80a5ef177` | 2025-01-24 10:55:04 +0100 | Eduard Tudenhoefner | Spark: Disable rewriting position deletes for V3 tables (#12048) | ✅ 已完成 | [1632_80a5ef177](commits/1632_80a5ef177/analysis.md) |
| 1633 | `a04220a19` | 2025-01-24 13:35:51 +0100 | Fokko Driesprong | API: Add `UnknownType` (#12012) | ✅ 已完成 | [1633_a04220a19](commits/1633_a04220a19/analysis.md) |
| 1634 | `6e2bc9ac4` | 2025-01-24 15:02:04 +0100 | Bjorn Olsen | Spark: Fix typo in `NoSuchTableException` (#12091) | ✅ 已完成 | [1634_6e2bc9ac4](commits/1634_6e2bc9ac4/analysis.md) |
| 1635 | `da1ea10b5` | 2025-01-24 09:13:41 -0800 | Huaxin Gao | Spark 3.4: Refactor delete logic in batch reading (#12061) | ✅ 已完成 | [1635_da1ea10b5](commits/1635_da1ea10b5/analysis.md) |
| 1636 | `225666390` | 2025-01-24 11:50:02 -0600 | Russell Spitzer | Spec, OpenAPI: Adds EnableRowLineage Metadata Update (#12050) | ✅ 已完成 | [1636_225666390](commits/1636_225666390/analysis.md) |
| 1637 | `c0c1b1507` | 2025-01-24 12:42:53 -0800 | Honah J. | Spec: Document Snapshot Summary Optional Fields for Standardization (#11660) | ✅ 已完成 | [1637_c0c1b1507](commits/1637_c0c1b1507/analysis.md) |
| 1638 | `67c52b55c` | 2025-01-24 13:53:09 -0800 | Ajantha Bhat | Parquet: Add readers and writers for the internal object model (#11904) | ✅ 已完成 | [1638_67c52b55c](commits/1638_67c52b55c/analysis.md) |
| 1639 | `d693f83b6` | 2025-01-24 16:51:46 -0700 | Manu Zhang | Spark 3.5: Fix broadcasting specs in RewriteTablePath (#11982) | ✅ 已完成 | [1639_d693f83b6](commits/1639_d693f83b6/analysis.md) |
| 1640 | `8ce1b32ec` | 2025-01-25 07:21:41 +0100 | JB Onofré | Build: Upgrade to Gradle 8.12.1 (#12093) | ✅ 已完成 | [1640_8ce1b32ec](commits/1640_8ce1b32ec/analysis.md) |
| 1641 | `645ef83ee` | 2025-01-26 17:43:44 -0700 | Hongyue/Steve Zhang | Spark 3.4: Backport Spark actions and procedures for RewriteTablePath (#12111) | ✅ 已完成 | [1641_645ef83ee](commits/1641_645ef83ee/analysis.md) |
| 1642 | `55c2909af` | 2025-01-27 09:18:41 +0100 | Hongyue/Steve Zhang | Core: Add metadataFileLocation in TableUtil (#12082) | ✅ 已完成 | [1642_55c2909af](commits/1642_55c2909af/analysis.md) |
| 1643 | `e3708882d` | 2025-01-27 09:27:16 +0100 | dependabot[bot] | Build: Bump actions/stale from 9.0.0 to 9.1.0 (#12110) | ✅ 已完成 | [1643_e3708882d](commits/1643_e3708882d/analysis.md) |
| 1644 | `b7de28e72` | 2025-01-27 13:03:30 +0100 | Christian | OpenAPI: Deprecate `snapshot-id` of `SetStatisticsUpdate` (#12010) | ✅ 已完成 | [1644_b7de28e72](commits/1644_b7de28e72/analysis.md) |
| 1645 | `2fa6cd855` | 2025-01-27 08:25:15 -0700 | Anton Okolnychyi | Spark 3.5: Make ColumnVectorWithFilter generic and refactor batch load (#12056) | ✅ 已完成 | [1645_2fa6cd855](commits/1645_2fa6cd855/analysis.md) |
| 1646 | `af00d1fb1` | 2025-01-27 14:51:53 -0600 | Russell Spitzer | Spec: Adds in missing ChangeLog Field IDs - Reassigns Row Lineage Field IDs(#12100) | ✅ 已完成 | [1646_af00d1fb1](commits/1646_af00d1fb1/analysis.md) |
| 1647 | `e1d24016f` | 2025-01-28 15:13:49 +0100 | gaborkaszab | OpenAPI: Changes for freshness-aware table loading (#11946) | ✅ 已完成 | [1647_e1d24016f](commits/1647_e1d24016f/analysis.md) |
| 1648 | `491d9068b` | 2025-01-28 16:28:25 +0100 | Yuya Ebihara | Core: Check referencedDataFile existence for DV (#12088) | ✅ 已完成 | [1648_491d9068b](commits/1648_491d9068b/analysis.md) |
| 1649 | `69c7bea3b` | 2025-01-28 16:29:01 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.50 to 2.30.6 (#12109) | ✅ 已完成 | [1649_69c7bea3b](commits/1649_69c7bea3b/analysis.md) |
| 1650 | `fdac0a0fc` | 2025-01-28 16:29:36 +0100 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#12108) | ✅ 已完成 | [1650_fdac0a0fc](commits/1650_fdac0a0fc/analysis.md) |
| 1651 | `d7f5be426` | 2025-01-28 16:34:39 +0100 | Ali LeClerc | Docs: Add IBM to Iceberg vendors list (#12101) | ✅ 已完成 | [1651_d7f5be426](commits/1651_d7f5be426/analysis.md) |
| 1652 | `4f6c711d5` | 2025-01-28 16:41:24 +0100 | dependabot[bot] | Build: Bump nessie from 0.101.3 to 0.102.2 (#12107) | ✅ 已完成 | [1652_4f6c711d5](commits/1652_4f6c711d5/analysis.md) |
| 1653 | `61241ed47` | 2025-01-28 09:46:10 -0700 | Ryan Blue | Core: Update variant class visibility (#12105) | ✅ 已完成 | [1653_61241ed47](commits/1653_61241ed47/analysis.md) |
| 1654 | `8e456aeea` | 2025-01-28 12:13:42 -0700 | Ryan Blue | Parquet: Clean up Parquet generic and internal readers (#12102) | ✅ 已完成 | [1654_8e456aeea](commits/1654_8e456aeea/analysis.md) |
| 1655 | `e89798ea7` | 2025-01-29 00:20:12 -0800 | Manu Zhang | Build: Bump scala-collection-compat from 2.12.0 to 2.13.0 (#12121) | ✅ 已完成 | [1655_e89798ea7](commits/1655_e89798ea7/analysis.md) |
| 1656 | `e9afab434` | 2025-01-29 15:35:48 +0100 | Fokko Driesprong | Core: Fix typo in Javadoc URL for Roaring spec (#12126) | ✅ 已完成 | [1656_e9afab434](commits/1656_e9afab434/analysis.md) |
| 1657 | `53d2aca91` | 2025-01-29 12:49:03 -0600 | Kevin Liu | Docs: Fix spacing in How To Release Vote Section (#12123) | ✅ 已完成 | [1657_53d2aca91](commits/1657_53d2aca91/analysis.md) |
| 1658 | `c7871347b` | 2025-01-29 14:32:42 -0600 | smaheshwar-pltr | Spec: Fix minor typo in `_last_updated_sequence_number` docs (#12128) | ✅ 已完成 | [1658_c7871347b](commits/1658_c7871347b/analysis.md) |
| 1659 | `5da8f5faf` | 2025-01-29 16:49:38 -0600 | Aihua Xu | Spec: Add variant type (#10831) | ✅ 已完成 | [1659_5da8f5faf](commits/1659_5da8f5faf/analysis.md) |
| 1660 | `2a0d5e83a` | 2025-01-30 10:49:28 +0100 | Tom Tanaka | Core, Spark: Make view metadata path configurable by `write.metadata.path` (#12017) | ✅ 已完成 | [1660_2a0d5e83a](commits/1660_2a0d5e83a/analysis.md) |
| 1661 | `02c8b2d45` | 2025-01-30 17:43:29 +0100 | Willi Raschkowski | Core: Support removing keys from EnvironmentContext (#12103) | ✅ 已完成 | [1661_02c8b2d45](commits/1661_02c8b2d45/analysis.md) |
| 1662 | `bdcd9c337` | 2025-01-31 09:54:54 -0800 | Daniel Weeks | OpenAPI: add initial/write defaults to schema (#12094) | ✅ 已完成 | [1662_bdcd9c337](commits/1662_bdcd9c337/analysis.md) |
| 1663 | `d0244cb70` | 2025-01-31 15:22:15 -0700 | JB Onofré | AWS: Fix LICENSE and NOTICE in aws-bundle jar (#12142) | ✅ 已完成 | [1663_d0244cb70](commits/1663_d0244cb70/analysis.md) |
| 1664 | `40334f5f7` | 2025-01-31 15:31:56 -0800 | Huaxin Gao | Spark 3.4: Support Comet Parquet readers (#9841) | ✅ 已完成 | [1664_40334f5f7](commits/1664_40334f5f7/analysis.md) |
| 1665 | `0ec9fa365` | 2025-01-31 18:16:39 -0700 | JB Onofré | Azure: Fix NOTICE and LICENSE in the azure-bundle (#12143) | ✅ 已完成 | [1665_0ec9fa365](commits/1665_0ec9fa365/analysis.md) |
| 1666 | `c5822c408` | 2025-01-31 23:47:00 -0800 | Huaxin Gao | Spark 3.4, 3.5: Iceberg / DataFusion Comet integration (#12147) | ✅ 已完成 | [1666_c5822c408](commits/1666_c5822c408/analysis.md) |
| 1667 | `cae7d1bad` | 2025-02-01 11:15:55 +0100 | Manu Zhang | Spark 3.5: Remove use of File.Separator in RewriteTablePath (#12066) | ✅ 已完成 | [1667_cae7d1bad](commits/1667_cae7d1bad/analysis.md) |
| 1668 | `1fcd6a989` | 2025-02-01 12:00:24 +0100 | Russell Spitzer | API, Core: Metadata Row Lineage (#11948) | ✅ 已完成 | [1668_1fcd6a989](commits/1668_1fcd6a989/analysis.md) |
| 1669 | `9feca0c30` | 2025-02-01 12:07:33 +0100 | ldsantos0911 | Spec: Fix current-version-id in view-spec.md example (#12146) | ✅ 已完成 | [1669_9feca0c30](commits/1669_9feca0c30/analysis.md) |
| 1670 | `1e8519332` | 2025-02-01 19:14:01 -0700 | Bryan Keller | Data: Open file using stats in scan (#12151) | ✅ 已完成 | [1670_1e8519332](commits/1670_1e8519332/analysis.md) |
| 1671 | `1687b26f0` | 2025-02-02 07:47:23 +0100 | dependabot[bot] | Build: Bump nessie from 0.102.2 to 0.102.4 (#12153) | ✅ 已完成 | [1671_1687b26f0](commits/1671_1687b26f0/analysis.md) |
| 1672 | `1c0c7832c` | 2025-02-02 07:48:28 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.5.50 to 9.6.1 (#12157) | ✅ 已完成 | [1672_1c0c7832c](commits/1672_1c0c7832c/analysis.md) |
| 1673 | `63acabc5f` | 2025-02-02 09:39:33 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.30.6 to 2.30.11 (#12156) | ✅ 已完成 | [1673_63acabc5f](commits/1673_63acabc5f/analysis.md) |
| 1674 | `9694c5496` | 2025-02-02 09:39:58 +0100 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.21.0 to 3.22.0 (#12155) | ✅ 已完成 | [1674_9694c5496](commits/1674_9694c5496/analysis.md) |
| 1675 | `46dfd959a` | 2025-02-02 09:40:22 +0100 | dependabot[bot] | Build: Bump me.champeau.jmh:jmh-gradle-plugin from 0.7.2 to 0.7.3 (#12152) | ✅ 已完成 | [1675_46dfd959a](commits/1675_46dfd959a/analysis.md) |
| 1676 | `f6faa58da` | 2025-02-02 20:25:46 +0100 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.30 to 1.2.31 (#12154) | ✅ 已完成 | [1676_f6faa58da](commits/1676_f6faa58da/analysis.md) |
| 1677 | `507e2a9e3` | 2025-02-03 14:13:31 +0100 | SongTao Zhuang | Spark: Make delete file ratio configurable (#12148) | ✅ 已完成 | [1677_507e2a9e3](commits/1677_507e2a9e3/analysis.md) |
| 1678 | `1afa0e739` | 2025-02-03 17:27:50 -0700 | smaheshwar-pltr | Core: Remove `TableMetadata::Builder::resetMainBranch` (#12149) | ✅ 已完成 | [1678_1afa0e739](commits/1678_1afa0e739/analysis.md) |
| 1679 | `da2002960` | 2025-02-04 15:38:13 +0100 | Fokko Driesprong | Spark: Update benchmark instructions (#12171) | ✅ 已完成 | [1679_da2002960](commits/1679_da2002960/analysis.md) |
| 1680 | `e406e3db8` | 2025-02-04 16:33:42 +0100 | Eduard Tudenhoefner | Spark: Test metadata tables with format-version=3 (#12135) | ✅ 已完成 | [1680_e406e3db8](commits/1680_e406e3db8/analysis.md) |
| 1681 | `cac68867c` | 2025-02-04 21:40:39 +0100 | JB Onofré | Docs: Fix latest and nightly link on javadoc (#12023) | ✅ 已完成 | [1681_cac68867c](commits/1681_cac68867c/analysis.md) |
| 1682 | `eb286de18` | 2025-02-04 12:58:02 -0800 | Ryan Blue | Variants: Implement toString (#12138) | ✅ 已完成 | [1682_eb286de18](commits/1682_eb286de18/analysis.md) |
| 1683 | `2510fbab3` | 2025-02-04 16:45:18 -0700 | Ryan Blue | Core: Refactor to enable moving Variant interfaces to API. (#12167) | ✅ 已完成 | [1683_2510fbab3](commits/1683_2510fbab3/analysis.md) |
| 1684 | `d990dbc34` | 2025-02-05 09:36:53 +0100 | Maximilian Michels | Flink: Add null check to writers to prevent resurrecting null values (#12049) | ✅ 已完成 | [1684_d990dbc34](commits/1684_d990dbc34/analysis.md) |
| 1685 | `585a338c1` | 2025-02-05 14:24:26 +0100 | Fokko Driesprong | Bump the versions of `site/mkdocs.yml` (#12180) | ✅ 已完成 | [1685_585a338c1](commits/1685_585a338c1/analysis.md) |
| 1686 | `998102546` | 2025-02-05 17:24:53 -0700 | Amogh Jahagirdar | Bump Nessie to 0.120.5 to include updated License/Notice (#12186) | ✅ 已完成 | [1686_998102546](commits/1686_998102546/analysis.md) |
| 1687 | `3bd04bea6` | 2025-02-06 09:53:28 +0100 | Daniel Weeks | API: Null check for auto-unboxed field-id (#12165) | ✅ 已完成 | [1687_3bd04bea6](commits/1687_3bd04bea6/analysis.md) |
| 1688 | `1f1736394` | 2025-02-06 10:10:02 +0100 | Fokko Driesprong | Build: Remove Jitpack (#12170) | ✅ 已完成 | [1688_1f1736394](commits/1688_1f1736394/analysis.md) |
| 1689 | `a05b2b53b` | 2025-02-06 14:24:08 +0100 | Maria | Hive: Use correct classloader to load SQL script (#12140) | ✅ 已完成 | [1689_a05b2b53b](commits/1689_a05b2b53b/analysis.md) |
| 1690 | `c15374193` | 2025-02-06 09:03:34 -0800 | JB Onofré | Fix NOTICE and LICENSE in the spark-runtime jar (#12160) | ✅ 已完成 | [1690_c15374193](commits/1690_c15374193/analysis.md) |
| 1691 | `be0da5acd` | 2025-02-06 09:05:53 -0800 | JB Onofré | Fix NOTICE and LICENSE in the gcp-bundle jar (#12144) | ✅ 已完成 | [1691_be0da5acd](commits/1691_be0da5acd/analysis.md) |
| 1692 | `9b1d18f72` | 2025-02-06 09:32:13 -0800 | Alexandre Dutra | Auth Manager API part 4: RESTClient, HTTPClient (#11992) | ✅ 已完成 | [1692_9b1d18f72](commits/1692_9b1d18f72/analysis.md) |
| 1693 | `a80364331` | 2025-02-06 09:38:37 -0800 | JB Onofré | Fix NOTICE and LICENSE in the flink-runtime jar (#12145) | ✅ 已完成 | [1693_a80364331](commits/1693_a80364331/analysis.md) |
| 1694 | `c2ea3c270` | 2025-02-06 15:17:46 -0800 | JB Onofré | Flink: Update LICENSE/NOTICE in flink-runtime Jars (#12188) | ✅ 已完成 | [1694_c2ea3c270](commits/1694_c2ea3c270/analysis.md) |
| 1695 | `81c4aee2d` | 2025-02-06 15:18:29 -0800 | JB Onofré | Spark 3.3, 3.4: Update LICENSE/NOTICE for spark-runtime (#12189) | ✅ 已完成 | [1695_81c4aee2d](commits/1695_81c4aee2d/analysis.md) |
| 1696 | `bc106171e` | 2025-02-06 17:27:06 -0800 | smaheshwar-pltr | Docs: Minor improvements to Spark Procedures (#12190) | ✅ 已完成 | [1696_bc106171e](commits/1696_bc106171e/analysis.md) |
| 1697 | `e91655bfa` | 2025-02-06 21:38:27 -0800 | Hongyue/Steve Zhang | Core: Exclude deleted content file in RewriteTablePathUtil copy plan (#12006) | ✅ 已完成 | [1697_e91655bfa](commits/1697_e91655bfa/analysis.md) |
| 1698 | `44aca93d4` | 2025-02-06 21:46:15 -0800 | Manu Zhang |  Spark 3.4: Remove use of File.Separator in RewriteTablePath (#12173) | ✅ 已完成 | [1698_44aca93d4](commits/1698_44aca93d4/analysis.md) |
| 1699 | `a89f1f9aa` | 2025-02-07 17:23:18 +0100 | Alexandre Dutra | AWS, Core: SigV4 Auth Manager (#11995) | ✅ 已完成 | [1699_a89f1f9aa](commits/1699_a89f1f9aa/analysis.md) |
| 1700 | `7a8db16c4` | 2025-02-07 17:07:31 -0800 | barronfuentes | Core: Fix RewriteTablePath Incremental Replication (#12172) | ✅ 已完成 | [1700_7a8db16c4](commits/1700_7a8db16c4/analysis.md) |
| 1701 | `d935460bb` | 2025-02-08 00:49:15 -0800 | Hongyue/Steve Zhang | Spark 3.5: Support Statistics Files in RewriteTablePath (#11929) | ✅ 已完成 | [1701_d935460bb](commits/1701_d935460bb/analysis.md) |
| 1702 | `f29131ebe` | 2025-02-09 08:37:25 +0100 | dependabot[bot] | Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.5 to 8.3.6 (#12210) | ✅ 已完成 | [1702_f29131ebe](commits/1702_f29131ebe/analysis.md) |
| 1703 | `dda964e7c` | 2025-02-09 08:37:48 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.1 to 9.6.3 (#12205) | ✅ 已完成 | [1703_dda964e7c](commits/1703_dda964e7c/analysis.md) |
| 1704 | `1d9fefeb9` | 2025-02-09 08:38:13 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.26.5 to 0.27.2 (#12204) | ✅ 已完成 | [1704_1d9fefeb9](commits/1704_1d9fefeb9/analysis.md) |
| 1705 | `c277c2014` | 2025-02-10 02:58:36 +0530 | JB Onofré | Fix LICENSE and NOTICE for the kafka-connect-runtime distributions (#12195) | ✅ 已完成 | [1705_c277c2014](commits/1705_c277c2014/analysis.md) |
| 1706 | `2af78e213` | 2025-02-10 07:38:14 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.30.11 to 2.30.16 (#12208) | ✅ 已完成 | [1706_2af78e213](commits/1706_2af78e213/analysis.md) |
| 1707 | `8e7508735` | 2025-02-10 07:38:30 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.53.0 to 26.54.0 (#12207) | ✅ 已完成 | [1707_8e7508735](commits/1707_8e7508735/analysis.md) |
| 1708 | `17cbb24b5` | 2025-02-10 07:38:44 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.48.0.0 to 3.49.0.0 (#12206) | ✅ 已完成 | [1708_17cbb24b5](commits/1708_17cbb24b5/analysis.md) |
| 1709 | `964c01bcb` | 2025-02-10 07:39:13 +0100 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#12209) | ✅ 已完成 | [1709_964c01bcb](commits/1709_964c01bcb/analysis.md) |
| 1710 | `6b89fa15a` | 2025-02-10 07:47:11 +0100 | Manu Zhang | Docs: Fix expire_snapshots output (#12213) | ✅ 已完成 | [1710_6b89fa15a](commits/1710_6b89fa15a/analysis.md) |
| 1711 | `7add0f362` | 2025-02-10 08:47:54 +0100 | JB Onofré | Build: Update LICENSE/NOTICE files with last dependency updates (#12214) | ✅ 已完成 | [1711_7add0f362](commits/1711_7add0f362/analysis.md) |
| 1712 | `3e6da2e54` | 2025-02-10 10:05:24 +0100 | xxchan | Docs: Update spark-quickstart note (#11996) | ✅ 已完成 | [1712_3e6da2e54](commits/1712_3e6da2e54/analysis.md) |
| 1713 | `20b18acb7` | 2025-02-10 11:56:15 -0800 | Szehon Ho | Spec: Support geo type (#10981) | ✅ 已完成 | [1713_20b18acb7](commits/1713_20b18acb7/analysis.md) |
| 1714 | `a212e998c` | 2025-02-11 10:38:30 +0100 | Hongyue/Steve Zhang | Build: skip scheduled docker image publish workflows on forks (#12218) | ✅ 已完成 | [1714_a212e998c](commits/1714_a212e998c/analysis.md) |
| 1715 | `31e9dc768` | 2025-02-11 10:46:06 +0100 | Gang Wu | Docs: Add missing types to the spec v3 summary (#12219) | ✅ 已完成 | [1715_31e9dc768](commits/1715_31e9dc768/analysis.md) |
| 1716 | `8839c9bf1` | 2025-02-11 17:13:40 -0600 | Russell Spitzer | Spec: Typo - missing be (#12229) | ✅ 已完成 | [1716_8839c9bf1](commits/1716_8839c9bf1/analysis.md) |
| 1717 | `7216a776c` | 2025-02-13 13:42:51 +0530 | Amogh Jahagirdar | Docs: Site update for 1.8.0 release (#12242) | ✅ 已完成 | [1717_7216a776c](commits/1717_7216a776c/analysis.md) |
| 1718 | `1153c303b` | 2025-02-13 09:28:17 +0100 | Fokko Driesprong | spec: Remove `source-ids` for `V{1,2}` tables (#12161) | ✅ 已完成 | [1718_1153c303b](commits/1718_1153c303b/analysis.md) |
| 1719 | `092ef395c` | 2025-02-13 14:34:10 +0530 | Amogh Jahagirdar | Update revAPI to compare against 1.8.0 (#12244) | ✅ 已完成 | [1719_092ef395c](commits/1719_092ef395c/analysis.md) |
| 1720 | `77316066a` | 2025-02-13 14:34:29 +0530 | Amogh Jahagirdar | Update release version to 1.8.0 in doap.rdf (#12247) | ✅ 已完成 | [1720_77316066a](commits/1720_77316066a/analysis.md) |
| 1721 | `ffbd86379` | 2025-02-13 14:34:54 +0530 | Amogh Jahagirdar | Infra: Update Iceberg bug report template for 1.8.0 (#12248) | ✅ 已完成 | [1721_ffbd86379](commits/1721_ffbd86379/analysis.md) |
| 1722 | `68189d28e` | 2025-02-13 10:42:35 +0100 | Amogh Jahagirdar | Docs: Fix formatting of 1.8.0 release notes (#12249) | ✅ 已完成 | [1722_68189d28e](commits/1722_68189d28e/analysis.md) |
| 1723 | `80a009a45` | 2025-02-13 13:33:15 +0100 | Bryan Keller | Core: Adjust Jackson settings to handle large metadata json (#12224) | ✅ 已完成 | [1723_80a009a45](commits/1723_80a009a45/analysis.md) |
| 1724 | `6604f4790` | 2025-02-13 13:43:32 +0100 | Eduard Tudenhoefner | Build: Bump Hive to 2.3.10 (#12253) | ✅ 已完成 | [1724_6604f4790](commits/1724_6604f4790/analysis.md) |
| 1725 | `6f341ab5b` | 2025-02-13 14:38:06 +0100 | Eduard Tudenhoefner | Build: Clean up dependencies (#12252) | ✅ 已完成 | [1725_6f341ab5b](commits/1725_6f341ab5b/analysis.md) |
| 1726 | `b8fdd847f` | 2025-02-13 11:36:41 -0800 | Ryan Blue | Core: Add InternalData read and write builders (#12060) | ✅ 已完成 | [1726_b8fdd847f](commits/1726_b8fdd847f/analysis.md) |
| 1727 | `602c35a31` | 2025-02-13 11:37:14 -0800 | Ryan Blue | API, Core: Support default values in UpdateSchema (#12211) | ✅ 已完成 | [1727_602c35a31](commits/1727_602c35a31/analysis.md) |
| 1728 | `7876ee151` | 2025-02-13 13:38:37 -0600 | Danica Fine | Site: Update site to include Iceberg Summit link (#12256) | ✅ 已完成 | [1728_7876ee151](commits/1728_7876ee151/analysis.md) |
| 1729 | `e509cfcca` | 2025-02-13 13:46:55 -0600 | dongwang | Core: Validate Arguments when Using  adjustSplitSize (#12201) | ✅ 已完成 | [1729_e509cfcca](commits/1729_e509cfcca/analysis.md) |
| 1730 | `396dc4404` | 2025-02-14 09:53:05 +0530 | Ryan Blue | Spark: Remove unused PruneColumnsWithReordering class. (#12258) | ✅ 已完成 | [1730_396dc4404](commits/1730_396dc4404/analysis.md) |
| 1731 | `8d2b3f4d1` | 2025-02-14 09:02:24 +0100 | Eduard Tudenhoefner | Spark: Fix assertion checks (#12255) | ✅ 已完成 | [1731_8d2b3f4d1](commits/1731_8d2b3f4d1/analysis.md) |
| 1732 | `b78d36a75` | 2025-02-14 11:13:43 +0100 | Ryan Blue | API: Deprecate NestedType.of in favor of builder (#12227) | ✅ 已完成 | [1732_b78d36a75](commits/1732_b78d36a75/analysis.md) |
| 1733 | `75d8e8422` | 2025-02-14 11:40:14 +0100 | Yuya Ebihara | Docker: Pin QEMU version temporarily (#12262) | ✅ 已完成 | [1733_75d8e8422](commits/1733_75d8e8422/analysis.md) |
| 1734 | `f13759328` | 2025-02-14 17:11:01 +0530 | gaborkaszab | OpenAPI: Add RemoveSchemas REST update type (#12022) | ✅ 已完成 | [1734_f13759328](commits/1734_f13759328/analysis.md) |
| 1735 | `abb47830e` | 2025-02-14 12:44:54 -0600 | Danica Fine | Site: Learn More to point to Spark QuickStart Doc (#12272) | ✅ 已完成 | [1735_abb47830e](commits/1735_abb47830e/analysis.md) |
| 1736 | `71493b92d` | 2025-02-16 17:26:10 +0100 | Manu Zhang | Build: Bump datamodel-code-generator from 0.27.2 to 0.28.1 (#12290) | ✅ 已完成 | [1736_71493b92d](commits/1736_71493b92d/analysis.md) |
| 1737 | `0bc2d7029` | 2025-02-17 00:18:53 -0800 | Yuya Ebihara | Spark 3.5: Fix job description of RewriteTablePathSparkAction (#12282) | ✅ 已完成 | [1737_0bc2d7029](commits/1737_0bc2d7029/analysis.md) |
| 1738 | `aed04d0fc` | 2025-02-17 09:35:43 +0100 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.117.Final to 4.1.118.Final (#12287) | ✅ 已完成 | [1738_aed04d0fc](commits/1738_aed04d0fc/analysis.md) |
| 1739 | `aec763a60` | 2025-02-17 09:42:50 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.30.16 to 2.30.21 (#12286) | ✅ 已完成 | [1739_aec763a60](commits/1739_aec763a60/analysis.md) |
| 1740 | `f9b9621e7` | 2025-02-17 10:49:21 +0100 | Hongyue/Steve Zhang | OpenAPI: Add overwrite option when registering a table (#12239) | ✅ 已完成 | [1740_f9b9621e7](commits/1740_f9b9621e7/analysis.md) |
| 1741 | `bcbbd0344` | 2025-02-17 16:44:20 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.3 to 9.6.4 (#12284) | ✅ 已完成 | [1741_bcbbd0344](commits/1741_bcbbd0344/analysis.md) |
| 1742 | `e8d3a06c4` | 2025-02-18 10:03:10 -0600 | Tom Tanaka | Core: Fix Enabling row-lineage during Create Table (#12307) | ✅ 已完成 | [1742_e8d3a06c4](commits/1742_e8d3a06c4/analysis.md) |
| 1743 | `93ef86112` | 2025-02-18 08:21:02 -0800 | Ryan Blue | API: Reject unknown type for required fields and validate defaults (#12302) | ✅ 已完成 | [1743_93ef86112](commits/1743_93ef86112/analysis.md) |
| 1744 | `b62fb42cb` | 2025-02-18 08:21:30 -0800 | Ryan Blue | API: Fix TestInclusiveMetricsEvaluator notStartsWith tests. (#12303) | ✅ 已完成 | [1744_b62fb42cb](commits/1744_b62fb42cb/analysis.md) |
| 1745 | `0b47faaad` | 2025-02-18 08:27:38 -0800 | Aihua Xu | Core: Add variant type support to utils and visitors (#11831) | ✅ 已完成 | [1745_0b47faaad](commits/1745_0b47faaad/analysis.md) |
| 1746 | `e79295b53` | 2025-02-18 13:09:53 -0800 | Fokko Driesprong | Core: Fix CI: Update tests with UnknownType from required to optional (#12316) | ✅ 已完成 | [1746_e79295b53](commits/1746_e79295b53/analysis.md) |
| 1747 | `5e1ce86ec` | 2025-02-18 14:21:17 -0800 | Manu Zhang | Docs: Refactor site navigation bar (#12289) | ✅ 已完成 | [1747_5e1ce86ec](commits/1747_5e1ce86ec/analysis.md) |
| 1748 | `3c8f36963` | 2025-02-18 15:31:48 -0800 | Ryan Blue | Parquet: Implement Variant readers (#12139) | ✅ 已完成 | [1748_3c8f36963](commits/1748_3c8f36963/analysis.md) |
| 1749 | `7e4f0ca6d` | 2025-02-18 23:31:59 -0800 | Hongyue/Steve Zhang | Docs: Add rewrite_table_path Spark Procedure (#12115) | ✅ 已完成 | [1749_7e4f0ca6d](commits/1749_7e4f0ca6d/analysis.md) |
| 1750 | `ea4393e66` | 2025-02-19 08:52:52 +0100 | Yuya Ebihara | Parquet: Fix errorprone warning (#12324) | ✅ 已完成 | [1750_ea4393e66](commits/1750_ea4393e66/analysis.md) |
| 1751 | `1bf55b48e` | 2025-02-19 08:58:04 +0100 | ConradJam | Docs: Add Apache Amoro docs (#11966) | ✅ 已完成 | [1751_1bf55b48e](commits/1751_1bf55b48e/analysis.md) |
| 1752 | `c1d4182b3` | 2025-02-19 08:59:52 +0100 | Bryan Keller | Parquet: Fix performance regression in reader init (#12305) | ✅ 已完成 | [1752_c1d4182b3](commits/1752_c1d4182b3/analysis.md) |
| 1753 | `387d2586a` | 2025-02-19 10:09:16 +0100 | Eduard Tudenhoefner | Core: Fallback to GET requests for namespace/table/view exists checks (#12314) | ✅ 已完成 | [1753_387d2586a](commits/1753_387d2586a/analysis.md) |
| 1754 | `2e6e38cfd` | 2025-02-19 10:18:06 +0100 | ConradJam | Docs: Fix refs in Apache Amoro docs (#12332) | ✅ 已完成 | [1754_2e6e38cfd](commits/1754_2e6e38cfd/analysis.md) |
| 1755 | `3a23f8578` | 2025-02-19 10:19:08 +0100 | Fokko Driesprong | Revert "Core: Serialize `null` when there is no current snapshot (#11560)" (#12312) | ✅ 已完成 | [1755_3a23f8578](commits/1755_3a23f8578/analysis.md) |
| 1756 | `686bae5e9` | 2025-02-19 10:19:39 +0100 | Eduard Tudenhoefner | Parquet: Fix performance regression in reader init (#12305) (#12329) | ✅ 已完成 | [1756_686bae5e9](commits/1756_686bae5e9/analysis.md) |
| 1757 | `fad0c1e6c` | 2025-02-19 11:49:09 +0100 | pvary | Checkstyle: Apply the same generic type naming rules to interfaces and classes (#12333) | ✅ 已完成 | [1757_fad0c1e6c](commits/1757_fad0c1e6c/analysis.md) |
| 1758 | `08d0b50bb` | 2025-02-19 18:47:15 +0100 | Eduard Tudenhoefner | Kafka: Pin Kafka-Connect version to fix integration tests (#12340) | ✅ 已完成 | [1758_08d0b50bb](commits/1758_08d0b50bb/analysis.md) |
| 1759 | `24630c9b8` | 2025-02-19 11:26:52 -0800 | wangyinsheng | Docs: Fix link of catalog in terms.md (#12326) | ✅ 已完成 | [1759_24630c9b8](commits/1759_24630c9b8/analysis.md) |
| 1760 | `25e7897b1` | 2025-02-19 14:49:00 -0600 | Prashant Singh | Docs: Add documentation for Rate limiting in Spark Structured Streaming (#12217) | ✅ 已完成 | [1760_25e7897b1](commits/1760_25e7897b1/analysis.md) |
| 1761 | `6c546fe13` | 2025-02-19 16:19:56 -0600 | Russell Spitzer | Spark 3.5: Fix Incorrect Spec Used With AddFiles Procedure (#12319) | ✅ 已完成 | [1761_6c546fe13](commits/1761_6c546fe13/analysis.md) |
| 1762 | `43ef03bf8` | 2025-02-20 08:19:36 +0100 | Yuya Ebihara | Parquet: Remove deprecated VectorizedReader.setRowGroupInfo and ParquetValueReader.setPageSource (#12321) | ✅ 已完成 | [1762_43ef03bf8](commits/1762_43ef03bf8/analysis.md) |
| 1763 | `4c1dec798` | 2025-02-20 09:09:51 +0100 | Fokko Driesprong | Bump versions in `{LICENSE,NOTICE}` (#12337) | ✅ 已完成 | [1763_4c1dec798](commits/1763_4c1dec798/analysis.md) |
| 1764 | `d6d97d168` | 2025-02-20 10:42:05 +0100 | Manu Zhang | Spark: Remove Spark 3.3 support (#12279) | ✅ 已完成 | [1764_d6d97d168](commits/1764_d6d97d168/analysis.md) |
| 1765 | `3dc4a5498` | 2025-02-20 11:00:19 +0100 | Yuya Ebihara | Core: Remove deprecated Util.blockLocations method and StructCopy class (#12320) | ✅ 已完成 | [1765_3dc4a5498](commits/1765_3dc4a5498/analysis.md) |
| 1766 | `de644158a` | 2025-02-20 12:44:02 +0100 | Denys Kuzmenko | Core: Handle partition evolution case in PartitionStatsUtil#computeStats (#12137) | ✅ 已完成 | [1766_de644158a](commits/1766_de644158a/analysis.md) |
| 1767 | `30fd752f2` | 2025-02-20 15:15:03 +0100 | Eduard Tudenhoefner | Core, Spark: Remove deprecated code for 1.9.0 (#12336) | ✅ 已完成 | [1767_30fd752f2](commits/1767_30fd752f2/analysis.md) |
| 1768 | `d4fe23a15` | 2025-02-20 17:37:56 -0800 | Ryan Blue | API: Move variant to API and add extract expression (#12304) | ✅ 已完成 | [1768_d4fe23a15](commits/1768_d4fe23a15/analysis.md) |
| 1769 | `4958663b4` | 2025-02-21 09:42:14 +0100 | Eduard Tudenhoefner | Core: Remove namespace/table/view HEAD endpoints from defaults (#12351) | ✅ 已完成 | [1769_4958663b4](commits/1769_4958663b4/analysis.md) |
| 1770 | `f186be7c8` | 2025-02-21 17:32:12 +0100 | Tom Tanaka | Core: Remove additional 'Iceberg' in Puffin footer payload (#12369) | ✅ 已完成 | [1770_f186be7c8](commits/1770_f186be7c8/analysis.md) |
| 1771 | `dc1e0b25a` | 2025-02-21 15:12:59 -0800 | Ryan Blue | API: Move Variant interfaces and serialized implementations to API (#12374) | ✅ 已完成 | [1771_dc1e0b25a](commits/1771_dc1e0b25a/analysis.md) |
| 1772 | `ebc9fbc09` | 2025-02-23 19:08:47 -0800 | GuoYu | Flink: Fix the comment error in SketchDataStatistics (#12375) | ✅ 已完成 | [1772_ebc9fbc09](commits/1772_ebc9fbc09/analysis.md) |
| 1773 | `c2461f995` | 2025-02-24 09:03:59 +0100 | Alexandre Dutra | Core: Don't remove trailing slash from absolute paths (#12389) | ✅ 已完成 | [1773_c2461f995](commits/1773_c2461f995/analysis.md) |
| 1774 | `a41d467f6` | 2025-02-24 09:05:24 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.4 to 9.6.5 (#12386) | ✅ 已完成 | [1774_a41d467f6](commits/1774_a41d467f6/analysis.md) |
| 1775 | `e4833dd0d` | 2025-02-24 09:05:45 +0100 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.49.0.0 to 3.49.1.0 (#12385) | ✅ 已完成 | [1775_e4833dd0d](commits/1775_e4833dd0d/analysis.md) |
| 1776 | `c275bd956` | 2025-02-24 12:17:46 +0100 | dependabot[bot] | Build: Bump org.awaitility:awaitility from 4.2.2 to 4.3.0 (#12384) | ✅ 已完成 | [1776_c275bd956](commits/1776_c275bd956/analysis.md) |
| 1777 | `0a4ac6ecd` | 2025-02-24 12:18:09 +0100 | dependabot[bot] | Build: Bump nessie from 0.102.5 to 0.103.0 (#12383) | ✅ 已完成 | [1777_0a4ac6ecd](commits/1777_0a4ac6ecd/analysis.md) |
| 1778 | `ea3fd7fca` | 2025-02-24 12:18:42 +0100 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.54.0 to 26.55.0 (#12382) | ✅ 已完成 | [1778_ea3fd7fca](commits/1778_ea3fd7fca/analysis.md) |
| 1779 | `94983060c` | 2025-02-24 14:17:57 +0100 | dependabot[bot] | Build: Bump testcontainers from 1.20.4 to 1.20.5 (#12380) | ✅ 已完成 | [1779_94983060c](commits/1779_94983060c/analysis.md) |
| 1780 | `7b91c3803` | 2025-02-24 14:33:06 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.30.21 to 2.30.26 (#12379) | ✅ 已完成 | [1780_7b91c3803](commits/1780_7b91c3803/analysis.md) |
| 1781 | `88445086d` | 2025-02-24 12:55:35 -0600 | Russell Spitzer | Spec: Allow Equality Deletes with Row Lineage and Define Behavior (#12230) | ✅ 已完成 | [1781_88445086d](commits/1781_88445086d/analysis.md) |
| 1782 | `1a044adbd` | 2025-02-25 08:01:52 +0100 | Shohei Okumiya | Core: Add "volatile" to HadoopFileIO#hadoopConf (#12388) | ✅ 已完成 | [1782_1a044adbd](commits/1782_1a044adbd/analysis.md) |
| 1783 | `a15f4710b` | 2025-02-25 08:08:43 +0100 | Yuya Ebihara | Arrow, Parquet, Spark 3.5, Flink 1.20: Avoid deprecated method (#11874) | ✅ 已完成 | [1783_a15f4710b](commits/1783_a15f4710b/analysis.md) |
| 1784 | `2473b4a1b` | 2025-02-25 12:29:21 +0100 | Lars Francke | Docs: Add Stackable to the Vendors page (#12344) | ✅ 已完成 | [1784_2473b4a1b](commits/1784_2473b4a1b/analysis.md) |
| 1785 | `0917664cb` | 2025-02-25 17:42:10 +0100 | JB Onofré | Build: Upgrade to Gradle 8.13 (#12398) | ✅ 已完成 | [1785_0917664cb](commits/1785_0917664cb/analysis.md) |
| 1786 | `7d9e96f9f` | 2025-02-25 09:19:08 -0800 | ismail simsek | Kafka Connect: Add SMTs for Debezium and AWS DMS (#11936) | ✅ 已完成 | [1786_7d9e96f9f](commits/1786_7d9e96f9f/analysis.md) |
| 1787 | `2f88ff66d` | 2025-02-25 16:48:12 -0800 | Ryan Blue | API, Core: Update inclusive metrics evaluator for extract and transforms (#12311) | ✅ 已完成 | [1787_2f88ff66d](commits/1787_2f88ff66d/analysis.md) |
| 1788 | `ad5dc14cc` | 2025-02-26 12:53:33 +0100 | Kristin Cowalcijk | Build: Remove Hadoop 2 (#12348) | ✅ 已完成 | [1788_ad5dc14cc](commits/1788_ad5dc14cc/analysis.md) |
| 1789 | `c0f1abd6a` | 2025-02-26 16:30:26 +0100 | Ajantha Bhat | Spec: Fix typo in view spec (#12405) | ✅ 已完成 | [1789_c0f1abd6a](commits/1789_c0f1abd6a/analysis.md) |
| 1790 | `508f2988b` | 2025-02-26 17:19:56 +0100 | winston | Docs: Fix grammar issues in descriptions about Hive environment in hive-quickstart.md (#12402) | ✅ 已完成 | [1790_508f2988b](commits/1790_508f2988b/analysis.md) |
| 1791 | `f6c934b47` | 2025-02-26 17:22:17 +0100 | 码界探索 | Docs: Fix Hive table creation syntax errors (#12394) | ✅ 已完成 | [1791_f6c934b47](commits/1791_f6c934b47/analysis.md) |
| 1792 | `a50ec923f` | 2025-02-26 17:22:56 -0800 | pvary | Core: Interface changes for separating rewrite planner and runner (#12306) | ✅ 已完成 | [1792_a50ec923f](commits/1792_a50ec923f/analysis.md) |
| 1793 | `7553b0f89` | 2025-02-28 10:40:48 +0530 | Eduard Tudenhoefner | Docs: Describe how to handle versioned docs/javadoc during a release (#12413) | ✅ 已完成 | [1793_7553b0f89](commits/1793_7553b0f89/analysis.md) |
| 1794 | `5f33b612c` | 2025-02-28 07:57:36 +0100 | Manu Zhang | Build: Bump Spark from 3.5.4 to 3.5.5 (#12396) | ✅ 已完成 | [1794_5f33b612c](commits/1794_5f33b612c/analysis.md) |
| 1795 | `0f38b5bd1` | 2025-02-28 08:56:00 +0100 | Manu Zhang | Docs: Remove Hive runtime jar link from latest release (#12422) | ✅ 已完成 | [1795_0f38b5bd1](commits/1795_0f38b5bd1/analysis.md) |
| 1796 | `4b592d08e` | 2025-02-28 08:58:39 +0100 | Eduard Tudenhoefner | Docs: Site updates for 1.8.1 (#12410) | ✅ 已完成 | [1796_4b592d08e](commits/1796_4b592d08e/analysis.md) |
| 1797 | `f5e50333a` | 2025-02-28 08:59:36 +0100 | Eduard Tudenhoefner | Infra: Update Bug report template for 1.8.1 (#12409) | ✅ 已完成 | [1797_f5e50333a](commits/1797_f5e50333a/analysis.md) |
| 1798 | `edc32a7f4` | 2025-02-28 08:59:58 +0100 | Eduard Tudenhoefner | Update release version to 1.8.1 in doap.rdf (#12408) | ✅ 已完成 | [1798_edc32a7f4](commits/1798_edc32a7f4/analysis.md) |
| 1799 | `15ed4ca66` | 2025-02-28 09:00:42 +0100 | Manu Zhang | Build: Ignore docker folder in CI (#12417) | ✅ 已完成 | [1799_15ed4ca66](commits/1799_15ed4ca66/analysis.md) |
| 1800 | `6a7999b58` | 2025-02-28 11:29:53 +0100 | Willi Raschkowski | API: Fix IndexOutOfBounds exception in FileFormat#fromFileName (#12301) | ✅ 已完成 | [1800_6a7999b58](commits/1800_6a7999b58/analysis.md) |
| 1801 | `465e063bd` | 2025-02-28 11:32:12 +0100 | Ian Streeter | Core: Print un-pretty metadata files (#12318) | ✅ 已完成 | [1801_465e063bd](commits/1801_465e063bd/analysis.md) |
| 1802 | `b87edbb01` | 2025-02-28 12:39:47 +0100 | gaborkaszab | Core: Code cleanup around TestTable and TestTableOperations (#12419) | ✅ 已完成 | [1802_b87edbb01](commits/1802_b87edbb01/analysis.md) |
| 1803 | `e230f5d79` | 2025-02-28 14:21:53 +0100 | Ajantha Bhat | Data: Add partition stats writer and reader (#11216) | ✅ 已完成 | [1803_e230f5d79](commits/1803_e230f5d79/analysis.md) |
| 1804 | `291a5c9ca` | 2025-02-28 20:05:44 +0100 | Anurag Mantripragada | Azure: Move docker-based tests to integrationTest (#12274) | ✅ 已完成 | [1804_291a5c9ca](commits/1804_291a5c9ca/analysis.md) |
| 1805 | `978430823` | 2025-03-01 13:40:51 -0800 | Jia Yu | Spec: Fix geo type example (#12421) | ✅ 已完成 | [1805_978430823](commits/1805_978430823/analysis.md) |
| 1806 | `adef1ad47` | 2025-03-01 13:42:19 -0800 | wangyinsheng | Docs: Fix link of ndv in spark-procedures.md (#12425) | ✅ 已完成 | [1806_adef1ad47](commits/1806_adef1ad47/analysis.md) |
| 1807 | `925b38251` | 2025-03-02 16:39:28 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.5 to 9.6.6 (#12432) | ✅ 已完成 | [1807_925b38251](commits/1807_925b38251/analysis.md) |
| 1808 | `dffce80ae` | 2025-03-02 16:40:08 +0100 | Manu Zhang | Build: Ignore README.md/LICENSE/NOTICE in all paths on CI (#12429) | ✅ 已完成 | [1808_dffce80ae](commits/1808_dffce80ae/analysis.md) |
| 1809 | `2e128dbf1` | 2025-03-02 20:27:20 +0100 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#12435) | ✅ 已完成 | [1809_2e128dbf1](commits/1809_2e128dbf1/analysis.md) |
| 1810 | `07e168490` | 2025-03-02 20:32:29 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.28.1 to 0.28.2 (#12433) | ✅ 已完成 | [1810_07e168490](commits/1810_07e168490/analysis.md) |
| 1811 | `db65f6e37` | 2025-03-03 12:53:34 +0100 | dependabot[bot] | Build: Bump org.mongodb:bson from 4.11.0 to 4.11.5 (#12438) | ✅ 已完成 | [1811_db65f6e37](commits/1811_db65f6e37/analysis.md) |
| 1812 | `43f1204bb` | 2025-03-03 12:54:20 +0100 | dependabot[bot] | Build: Bump slf4j from 2.0.16 to 2.0.17 (#12436) | ✅ 已完成 | [1812_43f1204bb](commits/1812_43f1204bb/analysis.md) |
| 1813 | `1ab3ef882` | 2025-03-03 12:54:50 +0100 | dependabot[bot] | Build: Bump jackson-bom from 2.18.2 to 2.18.3 (#12434) | ✅ 已完成 | [1813_1ab3ef882](commits/1813_1ab3ef882/analysis.md) |
| 1814 | `0ed01a21d` | 2025-03-03 12:56:09 +0100 | Wenston Xin | Docs: Fix typo in DELETE stmt (#12426) | ✅ 已完成 | [1814_0ed01a21d](commits/1814_0ed01a21d/analysis.md) |
| 1815 | `f14efce43` | 2025-03-03 12:59:00 +0100 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.118.Final to 4.1.119.Final (#12440) | ✅ 已完成 | [1815_f14efce43](commits/1815_f14efce43/analysis.md) |
| 1816 | `6323aa940` | 2025-03-03 16:30:22 +0100 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.22.0 to 3.23.0 (#12437) | ✅ 已完成 | [1816_6323aa940](commits/1816_6323aa940/analysis.md) |
| 1817 | `be9808fb3` | 2025-03-03 22:28:27 +0100 | Jacob Marble | Docs: Deprecate `data_file.distinct_counts` (#12182) | ✅ 已完成 | [1817_be9808fb3](commits/1817_be9808fb3/analysis.md) |
| 1818 | `b810d6411` | 2025-03-04 16:54:50 +0100 | gaborkaszab | Core: Change RemoveSnapshots to remove unused schemas (#12089) | ✅ 已完成 | [1818_b810d6411](commits/1818_b810d6411/analysis.md) |
| 1819 | `1fbf6cd09` | 2025-03-04 13:03:41 -0800 | Aihua Xu | Core: Add Variant logical type for Avro (#12238) | ✅ 已完成 | [1819_1fbf6cd09](commits/1819_1fbf6cd09/analysis.md) |
| 1820 | `fbc587695` | 2025-03-04 18:08:26 -0600 | Bharath Krishna | Spark 3.5: Infer partition spec in ADD_FILES procedure for FileTables than taking latest table spec (#12327) | ✅ 已完成 | [1820_fbc587695](commits/1820_fbc587695/analysis.md) |
| 1821 | `3c8366a43` | 2025-03-04 17:44:25 -0800 | Ryan Blue | Avro: Support timestamp(9) and unknown types (#12455) | ✅ 已完成 | [1821_3c8366a43](commits/1821_3c8366a43/analysis.md) |
| 1822 | `9f99fd7e0` | 2025-03-04 22:47:49 -0800 | Aihua Xu | Core: Wrap variant in PrimitiveLikeHoder so serialization can result same instance (#12317) | ✅ 已完成 | [1822_9f99fd7e0](commits/1822_9f99fd7e0/analysis.md) |
| 1823 | `cc4fe4cc5` | 2025-03-05 09:18:01 -0700 | Leon Lin | Core: Ensure current and newly added view versions are retained in ViewMetadata build (#12401) | ✅ 已完成 | [1823_cc4fe4cc5](commits/1823_cc4fe4cc5/analysis.md) |
| 1824 | `ffe9ad501` | 2025-03-05 11:32:47 -0800 | Swapna Marru | Flink: support create table like in flink catalog (#12199) | ✅ 已完成 | [1824_ffe9ad501](commits/1824_ffe9ad501/analysis.md) |
| 1825 | `58b283e35` | 2025-03-05 13:53:33 -0800 | Ryan Blue | Parquet: Implement Variant writers (#12323) | ✅ 已完成 | [1825_58b283e35](commits/1825_58b283e35/analysis.md) |
| 1826 | `f3b3ee408` | 2025-03-06 08:20:34 +0100 | Fokko Driesprong | Core: Write `null` for `current-snapshot-id` for V3+ (#12335) | ✅ 已完成 | [1826_f3b3ee408](commits/1826_f3b3ee408/analysis.md) |
| 1827 | `41ed45007` | 2025-03-06 08:42:32 +0100 | Fokko Driesprong | Spec: Add implementation note on `current-snapshot-id` (#12334) | ✅ 已完成 | [1827_41ed45007](commits/1827_41ed45007/analysis.md) |
| 1828 | `5994b54de` | 2025-03-06 13:27:09 +0100 | gaborkaszab | Core: Don't create empty RemovePartitionSpecs MetadataUpdate (#12465) | ✅ 已完成 | [1828_5994b54de](commits/1828_5994b54de/analysis.md) |
| 1829 | `55e478bc2` | 2025-03-06 18:28:33 +0100 | Ajantha Bhat | Data: Expose snapshot-id instead of branch for computing partition stats (#12464) | ✅ 已完成 | [1829_55e478bc2](commits/1829_55e478bc2/analysis.md) |
| 1830 | `afe0787b9` | 2025-03-06 11:35:49 -0800 | Ryan Blue | Parquet: Support unknown and timestamp(9) in internal model and generics (#12463) | ✅ 已完成 | [1830_afe0787b9](commits/1830_afe0787b9/analysis.md) |
| 1831 | `19330fa19` | 2025-03-07 07:29:53 +0100 | Eduard Tudenhoefner | Core: Provide access to format-version of metadata table (#12462) | ✅ 已完成 | [1831_19330fa19](commits/1831_19330fa19/analysis.md) |
| 1832 | `9a8466c95` | 2025-03-07 15:21:54 -0600 | Russell Spitzer | Site: Fix Footer Link (#12478) | ✅ 已完成 | [1832_9a8466c95](commits/1832_9a8466c95/analysis.md) |
| 1833 | `8b9bc73ae` | 2025-03-08 20:26:15 +0100 | slfan1989 | Docs: fix typo in `rest-catalog-open-api.yaml` (#12480) | ✅ 已完成 | [1833_8b9bc73ae](commits/1833_8b9bc73ae/analysis.md) |
| 1834 | `cbc3ebfc6` | 2025-03-09 07:44:22 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.30.26 to 2.30.31 (#12439) | ✅ 已完成 | [1834_cbc3ebfc6](commits/1834_cbc3ebfc6/analysis.md) |
| 1835 | `8380e5ecb` | 2025-03-09 14:01:07 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.6 to 9.6.7 (#12483) | ✅ 已完成 | [1835_8380e5ecb](commits/1835_8380e5ecb/analysis.md) |
| 1836 | `456bbe98b` | 2025-03-10 08:57:02 +0100 | dependabot[bot] | Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#12486) | ✅ 已完成 | [1836_456bbe98b](commits/1836_456bbe98b/analysis.md) |
| 1837 | `7539dc504` | 2025-03-10 11:01:29 +0100 | Cheng Pan | Build: Rename versions.spark.hive3* to versions.spark3* (#12489) | ✅ 已完成 | [1837_7539dc504](commits/1837_7539dc504/analysis.md) |
| 1838 | `6e8718113` | 2025-03-10 11:04:53 +0100 | dependabot[bot] | Build: Bump testcontainers from 1.20.5 to 1.20.6 (#12484) | ✅ 已完成 | [1838_6e8718113](commits/1838_6e8718113/analysis.md) |
| 1839 | `f61f97112` | 2025-03-11 15:39:28 +0100 | Xu Bai | Core: Apply correct metric configs in GenericAppenderFactory (#12366) | ✅ 已完成 | [1839_f61f97112](commits/1839_f61f97112/analysis.md) |
| 1840 | `54a62aeb1` | 2025-03-11 09:59:52 -0500 | Bharath Krishna | Spark 3.5: Add unit test for AddFilesProcedure to check invalid column in partition filter (#12456) | ✅ 已完成 | [1840_54a62aeb1](commits/1840_54a62aeb1/analysis.md) |
| 1841 | `020cac508` | 2025-03-11 11:31:03 -0500 | Rich Bowen | Site: Adds AWS to vendors page (#12468) | ✅ 已完成 | [1841_020cac508](commits/1841_020cac508/analysis.md) |
| 1842 | `4e76f70ed` | 2025-03-11 15:28:57 -0700 | Eduard Tudenhoefner | Spark: Rewrite V2 deletes to V3 DVs (#12250) | ✅ 已完成 | [1842_4e76f70ed](commits/1842_4e76f70ed/analysis.md) |
| 1843 | `e080d6fdd` | 2025-03-12 06:24:13 +0100 | sida-shen | Docs: Update recent talks from Iceberg Meetups (#12481) | ✅ 已完成 | [1843_e080d6fdd](commits/1843_e080d6fdd/analysis.md) |
| 1844 | `3dba6afb7` | 2025-03-11 22:47:21 -0700 | Sanjay Marreddi | AWS: Integrate S3 analytics accelerator library  (#12299) | ✅ 已完成 | [1844_3dba6afb7](commits/1844_3dba6afb7/analysis.md) |
| 1845 | `9a98de041` | 2025-03-13 06:23:56 +0100 | Eduard Tudenhoefner | AWS: Don't fetch credential from endpoint if properties contain a valid credential (#12504) | ✅ 已完成 | [1845_9a98de041](commits/1845_9a98de041/analysis.md) |
| 1846 | `fe258463f` | 2025-03-13 16:01:03 +0100 | Eduard Tudenhoefner | OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace | ✅ 已完成 | [1846_fe258463f](commits/1846_fe258463f/analysis.md) |
| 1847 | `d03a5e1db` | 2025-03-13 16:08:38 +0100 | Eduard Tudenhoefner | Revert "OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace" (#12517) | ✅ 已完成 | [1847_d03a5e1db](commits/1847_d03a5e1db/analysis.md) |
| 1848 | `7d0395d4b` | 2025-03-13 14:03:01 -0700 | Sanjay Marreddi | AWS: Update S3 async client configurations and docs for analytics-accelerator-s3 (#12503) | ✅ 已完成 | [1848_7d0395d4b](commits/1848_7d0395d4b/analysis.md) |
| 1849 | `54d7341bd` | 2025-03-14 09:04:07 +0100 | Pucheng Yang | Core: Make reporter() public so that it can be accessed by Trino for BaseTable creation (#12519) | ✅ 已完成 | [1849_54d7341bd](commits/1849_54d7341bd/analysis.md) |
| 1850 | `b3d513334` | 2025-03-14 09:09:52 +0100 | Tom Tanaka | Spark: Migrate Spark 3.4 test base to JUnit5 (#12501) | ✅ 已完成 | [1850_b3d513334](commits/1850_b3d513334/analysis.md) |
| 1851 | `18de78eac` | 2025-03-14 10:19:44 +0100 | Daniel Weeks | Core: Fix support for GenericManifestFile intex projection (#12522) | ✅ 已完成 | [1851_18de78eac](commits/1851_18de78eac/analysis.md) |
| 1852 | `c02ebe474` | 2025-03-14 11:20:33 +0100 | Yuya Ebihara | Core: Set missing table-default property in RESTSessionCatalog (#11646) | ✅ 已完成 | [1852_c02ebe474](commits/1852_c02ebe474/analysis.md) |
| 1853 | `3e3df8ebb` | 2025-03-14 14:20:19 +0100 | pvary | Core: Fix default and initial value handling on table creation (#12520) | ✅ 已完成 | [1853_3e3df8ebb](commits/1853_3e3df8ebb/analysis.md) |
| 1854 | `ab6fc83ec` | 2025-03-14 08:32:43 -0600 | Eduard Tudenhoefner | Core: Don't expose InMemoryViewOperations and RESTViewBuilder outside their visibility scope (#12524) | ✅ 已完成 | [1854_ab6fc83ec](commits/1854_ab6fc83ec/analysis.md) |
| 1855 | `03fc1ae33` | 2025-03-14 08:21:41 -0700 | Thomas Jaeckle | Kafka Connect: Add config for transactional ID prefix (#11780) | ✅ 已完成 | [1855_03fc1ae33](commits/1855_03fc1ae33/analysis.md) |
| 1856 | `51abab10e` | 2025-03-14 10:10:36 -0700 | kumarpritam863 | Kafka Connect: Handle no coordinator and data loss in ICR mode (#12372) | ✅ 已完成 | [1856_51abab10e](commits/1856_51abab10e/analysis.md) |
| 1857 | `7195cb2b6` | 2025-03-14 19:24:47 +0100 | smaheshwar-pltr | Core: Use `buildKeepingLast` for table properties in REST table builder (#12526) | ✅ 已完成 | [1857_7195cb2b6](commits/1857_7195cb2b6/analysis.md) |
| 1858 | `7a572a9eb` | 2025-03-14 15:14:45 -0700 | Ryan Blue | Flink 1.20: Support Avro and Parquet timestamp(9), unknown, and defaults (#12470) | ✅ 已完成 | [1858_7a572a9eb](commits/1858_7a572a9eb/analysis.md) |
| 1859 | `c991e0d92` | 2025-03-14 17:37:06 -0500 | Bharath Krishna | Spark 3.4: Backport partition spec inference in spark ADD_FILES procedure (#12508) (#12319 #12327 #12456) | ✅ 已完成 | [1859_c991e0d92](commits/1859_c991e0d92/analysis.md) |
| 1860 | `a706c348a` | 2025-03-15 13:24:30 -0700 | Ryan Blue | Flink 1.18, 1.19: Implement timestamp(9), unknown, and defaults (#12532) | ✅ 已完成 | [1860_a706c348a](commits/1860_a706c348a/analysis.md) |
| 1861 | `fb5740d3d` | 2025-03-16 07:32:19 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.7 to 9.6.8 (#12542) | ✅ 已完成 | [1861_fb5740d3d](commits/1861_fb5740d3d/analysis.md) |
| 1862 | `fcea78fc3` | 2025-03-16 07:32:48 +0100 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.28.2 to 0.28.4 (#12541) | ✅ 已完成 | [1862_fcea78fc3](commits/1862_fcea78fc3/analysis.md) |
| 1863 | `d35cf23eb` | 2025-03-17 10:55:10 +0100 | Yuya Ebihara | Core: Add missing table-override property to REST catalog (#12548) | ✅ 已完成 | [1863_d35cf23eb](commits/1863_d35cf23eb/analysis.md) |
| 1864 | `57ec405a6` | 2025-03-17 11:02:18 +0100 | drexler-sky | Spark: Call configureTable in ScanTestBase (#12546) | ✅ 已完成 | [1864_57ec405a6](commits/1864_57ec405a6/analysis.md) |
| 1865 | `4816bf3b5` | 2025-03-17 12:20:50 +0100 | Alexandre Dutra | AWS, Core, GCP: Auth Manager API enablement (#12197) | ✅ 已完成 | [1865_4816bf3b5](commits/1865_4816bf3b5/analysis.md) |
| 1866 | `bf2f55272` | 2025-03-17 12:29:08 +0100 | rcjverhoef | Core: Close FileIO instance in JdbcCatalog (#12540) | ✅ 已完成 | [1866_bf2f55272](commits/1866_bf2f55272/analysis.md) |
| 1867 | `15a9cbc7e` | 2025-03-17 15:50:57 +0100 | Tom Tanaka | Spark 3.4: Migrate TestBase related tests in spark and actions to JUnit5 (#12552) | ✅ 已完成 | [1867_15a9cbc7e](commits/1867_15a9cbc7e/analysis.md) |
| 1868 | `c7f3919f8` | 2025-03-17 15:55:19 +0100 | Ryan Blue | API: Implement Variant#toString (#12531) | ✅ 已完成 | [1868_c7f3919f8](commits/1868_c7f3919f8/analysis.md) |
| 1869 | `3cc4b045c` | 2025-03-17 16:33:01 -0700 | Ryan Blue | Avro: Add variant readers and writers (#12457) | ✅ 已完成 | [1869_3cc4b045c](commits/1869_3cc4b045c/analysis.md) |
| 1870 | `8f6ebb5b3` | 2025-03-18 07:31:51 +0100 | Yuya Ebihara | Core: Add `view-override` catalog property (#12534) | ✅ 已完成 | [1870_8f6ebb5b3](commits/1870_8f6ebb5b3/analysis.md) |
| 1871 | `731ff7332` | 2025-03-18 05:33:37 -0700 | Eduard Tudenhoefner | Kafka: Suppress warnings around java.util.Date usage / fix var names (#12561) | ✅ 已完成 | [1871_731ff7332](commits/1871_731ff7332/analysis.md) |
| 1872 | `6417719ad` | 2025-03-18 17:32:15 +0100 | Alexandre Dutra | REST: HTTPRequest.baseUri() should be nullable (#12556) | ✅ 已完成 | [1872_6417719ad](commits/1872_6417719ad/analysis.md) |
| 1873 | `952fcd451` | 2025-03-18 13:24:22 -0700 | Ryan Blue | Parquet, Core: Enable passing Variant tests (#12559) | ✅ 已完成 | [1873_952fcd451](commits/1873_952fcd451/analysis.md) |
| 1874 | `04eecd61b` | 2025-03-18 20:55:00 -0600 | hsiang-c | Core: JDBCCatalog's dropView() should purge metadata files if GC is enabled (#12511) | ✅ 已完成 | [1874_04eecd61b](commits/1874_04eecd61b/analysis.md) |
| 1875 | `4dbcdfc85` | 2025-03-18 22:42:19 -0500 | Russell Spitzer | Core, Spark 3.5: Apply Ignore Residuals to Delete Filtering (#12479) | ✅ 已完成 | [1875_4dbcdfc85](commits/1875_4dbcdfc85/analysis.md) |
| 1876 | `cf980650e` | 2025-03-19 09:17:07 +0100 | Ajantha Bhat | Core: Make totalRecordCount optional in PartitionStats (#12226) | ✅ 已完成 | [1876_cf980650e](commits/1876_cf980650e/analysis.md) |
| 1877 | `5ce86a3e8` | 2025-03-19 09:35:05 +0100 | Eduard Tudenhoefner | Core: Replace withFailMessage() with as() (#12570) | ✅ 已完成 | [1877_5ce86a3e8](commits/1877_5ce86a3e8/analysis.md) |
| 1878 | `29612e80e` | 2025-03-19 10:48:56 +0100 | Eduard Tudenhoefner | Spark: Improve assertions for better debuggability (#12569) | ✅ 已完成 | [1878_29612e80e](commits/1878_29612e80e/analysis.md) |
| 1879 | `82044624d` | 2025-03-19 11:19:13 +0100 | Shohei Okumiya | Docs: Update statements mentioning Hive's alpha/beta versions (#12430) | ✅ 已完成 | [1879_82044624d](commits/1879_82044624d/analysis.md) |
| 1880 | `b821c24c3` | 2025-03-19 16:40:10 +0100 | Eduard Tudenhoefner | Infra: Update Bug report template for 1.7.2 (#12574) | ✅ 已完成 | [1880_b821c24c3](commits/1880_b821c24c3/analysis.md) |
| 1881 | `b6325c499` | 2025-03-19 16:49:16 +0100 | Matt Topol | Docs: update go impl status (#12578) | ✅ 已完成 | [1881_b6325c499](commits/1881_b6325c499/analysis.md) |
| 1882 | `f6a5ba0b4` | 2025-03-19 10:04:12 -0600 | Eduard Tudenhoefner | Core: Use InternalData when reading manifests in FileCleanupStrategy (#12575) | ✅ 已完成 | [1882_f6a5ba0b4](commits/1882_f6a5ba0b4/analysis.md) |
| 1883 | `c8d8b8ca5` | 2025-03-19 17:11:00 +0100 | JB Onofré | Docs: Site updates for 1.7.2 (#12576) | ✅ 已完成 | [1883_c8d8b8ca5](commits/1883_c8d8b8ca5/analysis.md) |
| 1884 | `608345b35` | 2025-03-19 11:40:55 -0700 | Ryan Blue | ORC: Support timestamp(9), variant, and unknown in generics (#12567) | ✅ 已完成 | [1884_608345b35](commits/1884_608345b35/analysis.md) |
| 1885 | `2c746e678` | 2025-03-19 20:29:16 +0100 | Swapna Marru | Flink: Support source watermark for flink sql windows (#12191) | ✅ 已完成 | [1885_2c746e678](commits/1885_2c746e678/analysis.md) |
| 1886 | `017559ecd` | 2025-03-20 07:40:41 +0100 | Eduard Tudenhoefner | Spark: Detect dangling DVs properly (#12270) | ✅ 已完成 | [1886_017559ecd](commits/1886_017559ecd/analysis.md) |
| 1887 | `8ed1c2165` | 2025-03-20 07:49:01 +0100 | Eduard Tudenhoefner | OpenAPI: Handle NamespaceNotEmptyException when dropping a namespace (#12518) | ✅ 已完成 | [1887_8ed1c2165](commits/1887_8ed1c2165/analysis.md) |
| 1888 | `ff5004ef6` | 2025-03-20 06:46:10 -0700 | Wing Yew Poon | Use correct statistics file in SparkScan::estimateStatistics(Snapshot) (#12482) | ✅ 已完成 | [1888_ff5004ef6](commits/1888_ff5004ef6/analysis.md) |
| 1889 | `31e0f19df` | 2025-03-20 14:56:19 +0100 | GuoYu | Flink: fix read config of connector.iceberg.max-allowed-planning-failures (#12585) | ✅ 已完成 | [1889_31e0f19df](commits/1889_31e0f19df/analysis.md) |
| 1890 | `6779a1575` | 2025-03-20 16:02:37 +0100 | GuoYu | Flink: backport for fix read config of connector.iceberg.max-allowed-planning-failures (#12589) | ✅ 已完成 | [1890_6779a1575](commits/1890_6779a1575/analysis.md) |
| 1891 | `21497fd38` | 2025-03-20 11:59:34 -0700 | Daniel Weeks | Use InternalData with Avro for readers. (#12476) | ✅ 已完成 | [1891_21497fd38](commits/1891_21497fd38/analysis.md) |
| 1892 | `721741764` | 2025-03-20 14:42:11 -0700 | Ryan Blue | Core, Parquet, ORC: Fix missing data when writing unknown (#12581) | ✅ 已完成 | [1892_721741764](commits/1892_721741764/analysis.md) |
| 1893 | `e47e99a0d` | 2025-03-21 07:09:27 +0100 | Eduard Tudenhoefner | Core: Handle NamespaceNotEmptyException in NamespaceErrorHandler (#12505) | ✅ 已完成 | [1893_e47e99a0d](commits/1893_e47e99a0d/analysis.md) |
| 1894 | `df27946ba` | 2025-03-21 09:46:32 +0100 | Eduard Tudenhoefner | Spark 3.4: Read DVs when reading from .position_deletes table | ✅ 已完成 | [1894_df27946ba](commits/1894_df27946ba/analysis.md) |
| 1895 | `a4816c1c9` | 2025-03-21 09:46:32 +0100 | Eduard Tudenhoefner | Spark 3.4: Include content offset/size in PositionDeletesTable | ✅ 已完成 | [1895_a4816c1c9](commits/1895_a4816c1c9/analysis.md) |
| 1896 | `2c157cbaf` | 2025-03-21 10:50:50 +0100 | Tom Tanaka | Spark 3.4: Migrate TestBase-related remaining tests in actions (#12579) | ✅ 已完成 | [1896_2c157cbaf](commits/1896_2c157cbaf/analysis.md) |
| 1897 | `d14298fe9` | 2025-03-21 13:18:54 +0100 | Eduard Tudenhoefner | Spark 3.4: Test metadata tables with format-version=v3 / add ExtensionsTestBase (#12600) | ✅ 已完成 | [1897_d14298fe9](commits/1897_d14298fe9/analysis.md) |
| 1898 | `845ef5149` | 2025-03-21 14:21:22 +0100 | gaborkaszab | Core: Bulk deletion in RemoveSnapshots (#11837) | ✅ 已完成 | [1898_845ef5149](commits/1898_845ef5149/analysis.md) |
| 1899 | `74051b44f` | 2025-03-21 18:11:32 +0100 | Tom Tanaka | Spark 3.4: Backport DVs related parts (#12603) | ✅ 已完成 | [1899_74051b44f](commits/1899_74051b44f/analysis.md) |
| 1900 | `ded06702a` | 2025-03-21 13:03:31 -0700 | Ryan Blue | Parquet: Implement Variant metrics (#12496) | ✅ 已完成 | [1900_ded06702a](commits/1900_ded06702a/analysis.md) |
| 1901 | `980212e70` | 2025-03-22 10:41:54 -0600 | Eduard Tudenhoefner | Spark 3.4: Rewrite data files with high delete ratio | ✅ 已完成 | [1901_980212e70](commits/1901_980212e70/analysis.md) |
| 1902 | `263c62ee6` | 2025-03-22 10:53:33 -0600 | Eduard Tudenhoefner | Spark 3.4: Rewrite V2 deletes to V3 DVs | ✅ 已完成 | [1902_263c62ee6](commits/1902_263c62ee6/analysis.md) |
| 1903 | `c2a7d9f0a` | 2025-03-22 10:53:33 -0600 | Eduard Tudenhoefner | Spark 3.4: Detect dangling DVs properly | ✅ 已完成 | [1903_c2a7d9f0a](commits/1903_c2a7d9f0a/analysis.md) |
| 1904 | `49353ff2d` | 2025-03-23 15:22:23 +0100 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.8 to 9.6.9 (#12614) | ✅ 已完成 | [1904_49353ff2d](commits/1904_49353ff2d/analysis.md) |
| 1905 | `c94fae4fa` | 2025-03-23 15:24:21 +0100 | dependabot[bot] | Build: Bump nessie from 0.103.0 to 0.103.2 (#12615) | ✅ 已完成 | [1905_c94fae4fa](commits/1905_c94fae4fa/analysis.md) |
| 1906 | `1a94bc034` | 2025-03-23 17:55:31 -0700 | Szehon Ho | Spec: Geo spec simplifications (#12533) | ✅ 已完成 | [1906_1a94bc034](commits/1906_1a94bc034/analysis.md) |
| 1907 | `cb7d5a827` | 2025-03-24 07:59:57 +0100 | Manu Zhang | Spark 3.5: Reduce repeated logs in SparkWrite and SparkPositionDeltaWrite (#12404) | ✅ 已完成 | [1907_cb7d5a827](commits/1907_cb7d5a827/analysis.md) |
| 1908 | `f12d20010` | 2025-03-24 08:11:12 +0100 | slfan1989 | Spark 3.4: Backport Spark actions changes in Spark rewrite_table_path procedure (#12006 #12172 #11929 #12282 #12569) (#12568) | ✅ 已完成 | [1908_f12d20010](commits/1908_f12d20010/analysis.md) |
| 1909 | `6bd6887db` | 2025-03-24 10:52:21 +0100 | Bryan Keller | Core: Add update event for rewrite manifests (#12627) | ✅ 已完成 | [1909_6bd6887db](commits/1909_6bd6887db/analysis.md) |
| 1910 | `cbf34a6ab` | 2025-03-24 11:09:28 +0100 | Leon Lin | Build: Enforce error message check on Exception assertions (#12624) | ✅ 已完成 | [1910_cbf34a6ab](commits/1910_cbf34a6ab/analysis.md) |
| 1911 | `a908f9201` | 2025-03-24 11:16:21 +0100 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations from 2.36.0 to 2.37.0 (#12622) | ✅ 已完成 | [1911_a908f9201](commits/1911_a908f9201/analysis.md) |
| 1912 | `4f06b0911` | 2025-03-24 13:46:55 +0100 | Eduard Tudenhoefner | Core: Add commit metrics for rewriting manifests (#12630) | ✅ 已完成 | [1912_4f06b0911](commits/1912_4f06b0911/analysis.md) |
| 1913 | `62005e72a` | 2025-03-24 15:40:54 +0100 | Ajantha Bhat | Data: Refactor PartitionStatsHandler (#12550) | ✅ 已完成 | [1913_62005e72a](commits/1913_62005e72a/analysis.md) |
| 1914 | `50c8697f9` | 2025-03-24 09:14:30 -0600 | Eduard Tudenhoefner | Spark 3.4: Propagate snapshot properties / Add max allowed failed commits | ✅ 已完成 | [1914_50c8697f9](commits/1914_50c8697f9/analysis.md) |
| 1915 | `5cb8715c1` | 2025-03-24 19:32:39 +0100 | Manu Zhang | Docs: Fix lifecycle and versions in multi-engine-support (#12370) | ✅ 已完成 | [1915_5cb8715c1](commits/1915_5cb8715c1/analysis.md) |
| 1916 | `6f0dfd976` | 2025-03-24 19:35:27 +0100 | dependabot[bot] | Build: Bump parquet from 1.15.0 to 1.15.1 (#12616) | ✅ 已完成 | [1916_6f0dfd976](commits/1916_6f0dfd976/analysis.md) |
| 1917 | `80f07c02d` | 2025-03-24 19:36:29 +0100 | dependabot[bot] | Build: Bump calcite from 1.10.0 to 1.39.0 (#12617) | ✅ 已完成 | [1917_80f07c02d](commits/1917_80f07c02d/analysis.md) |
| 1918 | `2a0fee50e` | 2025-03-24 17:34:18 -0600 | SourabhEstuary | Add Estuary blog post showing how to load data into Apache Iceberg (#12587) | ✅ 已完成 | [1918_2a0fee50e](commits/1918_2a0fee50e/analysis.md) |
| 1919 | `03ff41c18` | 2025-03-25 06:30:49 +0100 | Bryan Keller | Core: Fallback to thread classloader when loading classes (#12613) | ✅ 已完成 | [1919_03ff41c18](commits/1919_03ff41c18/analysis.md) |
| 1920 | `626329863` | 2025-03-25 11:37:53 -0700 | Alexandre Dutra | Core: child HTTPClient should not close shared resources (#12566) | ✅ 已完成 | [1920_626329863](commits/1920_626329863/analysis.md) |
| 1921 | `e1e0a7404` | 2025-03-25 16:09:02 -0700 | Kristin Cowalcijk | API, Core: Add geometry and geography types support (#12346) | ✅ 已完成 | [1921_e1e0a7404](commits/1921_e1e0a7404/analysis.md) |
| 1922 | `07ad9a635` | 2025-03-26 07:15:51 +0100 | Alexandre Dutra | AWS: Use correct parent session when calling delegate auth manager (#12582) | ✅ 已完成 | [1922_07ad9a635](commits/1922_07ad9a635/analysis.md) |
| 1923 | `af99da6c2` | 2025-03-26 07:43:42 +0100 | Ajantha Bhat | Docs: Update block spacing guideline in contribute.md (#12641) | ✅ 已完成 | [1923_af99da6c2](commits/1923_af99da6c2/analysis.md) |
| 1924 | `695374d8b` | 2025-03-26 20:02:55 +0100 | Manu Zhang | Docs: Fix ASF sponsorship links (#12646) | ✅ 已完成 | [1924_695374d8b](commits/1924_695374d8b/analysis.md) |
| 1925 | `25409c624` | 2025-03-27 10:31:37 +0100 | Tom Tanaka | Spark 3.4: Migrate SparkRowLevelOperationsTestBase related tests to JUnit 5 (#12656) | ✅ 已完成 | [1925_25409c624](commits/1925_25409c624/analysis.md) |
| 1926 | `d54d81ecc` | 2025-03-27 11:37:20 +0100 | Soumya Banerjee | Spark 3.4: Use correct statistics file in SparkScan::estimateStatistics(Snapshot) (#12647) | ✅ 已完成 | [1926_d54d81ecc](commits/1926_d54d81ecc/analysis.md) |
| 1927 | `aa4ed6041` | 2025-03-27 13:02:06 +0100 | Manu Zhang | Core: Enhance TestRemoveSnapshots (#12662) | ✅ 已完成 | [1927_aa4ed6041](commits/1927_aa4ed6041/analysis.md) |
| 1928 | `3385d0534` | 2025-03-27 19:36:47 +0100 | Kevin Liu | Update PyIceberg status page (#12645) | ✅ 已完成 | [1928_3385d0534](commits/1928_3385d0534/analysis.md) |
| 1929 | `8ca7f9bf1` | 2025-03-27 19:44:46 +0100 | dependabot[bot] | Build: Bump jetty from 11.0.24 to 11.0.25 (#12618) | ✅ 已完成 | [1929_8ca7f9bf1](commits/1929_8ca7f9bf1/analysis.md) |
| 1930 | `6e2eaf02f` | 2025-03-27 20:20:13 +0100 | Manu Zhang | Docs: Fix Latest Iceberg Support version of Hive (#12640) | ✅ 已完成 | [1930_6e2eaf02f](commits/1930_6e2eaf02f/analysis.md) |
| 1931 | `68f8053d6` | 2025-03-28 07:32:16 +0100 | ChaladiMohanVamsi | Azure: Support vended credentials refresh in ADLSFileIO. (#11577) | ✅ 已完成 | [1931_68f8053d6](commits/1931_68f8053d6/analysis.md) |
| 1932 | `795f2e4b6` | 2025-03-28 07:53:35 +0100 | Ajantha Bhat | Build: Revert AWS SDK from 2.30.31 to 2.29.52 (#12649) | ✅ 已完成 | [1932_795f2e4b6](commits/1932_795f2e4b6/analysis.md) |
| 1933 | `12f5d0886` | 2025-03-28 09:38:44 +0100 | Eduard Tudenhoefner | AWS: Use assertThat instead of JUnit4 assertions (#12668) | ✅ 已完成 | [1933_12f5d0886](commits/1933_12f5d0886/analysis.md) |
| 1934 | `054eacd5d` | 2025-03-28 08:43:56 -0700 | Eduard Tudenhoefner | GCP: Use catalog endpoint as base when refreshing OAuth2 token (#12638) | ✅ 已完成 | [1934_054eacd5d](commits/1934_054eacd5d/analysis.md) |
| 1935 | `9199ab520` | 2025-03-28 19:08:39 +0100 | sullis | Core: Remove redundant parameters() definition from subclasses of TestBase (#12666) | ✅ 已完成 | [1935_9199ab520](commits/1935_9199ab520/analysis.md) |
| 1936 | `d5971429e` | 2025-03-29 09:15:14 +0100 | pvary | Core: FileRewritePlanner implementation (#12493) | ✅ 已完成 | [1936_d5971429e](commits/1936_d5971429e/analysis.md) |
| 1937 | `d945cde3c` | 2025-03-30 23:22:55 +0200 | Swapna Marru | Flink: Backport support create table like in flink catalog to Flink v1.18 and v1.19 | ✅ 已完成 | [1937_d945cde3c](commits/1937_d945cde3c/analysis.md) |
| 1938 | `d8251755c` | 2025-03-30 22:50:16 -0700 | slfan1989 | Docs: Fix quote for rewrite_table_path example (#12628) | ✅ 已完成 | [1938_d8251755c](commits/1938_d8251755c/analysis.md) |
| 1939 | `6d8653bb2` | 2025-03-31 08:02:49 -0600 | Xiaoxuan | Spark, API: Enhance hashing efficiency by operating on raw UTF-8 bytes (#12657) | ✅ 已完成 | [1939_6d8653bb2](commits/1939_6d8653bb2/analysis.md) |
| 1940 | `12737a128` | 2025-03-31 18:59:03 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.28.4 to 0.28.5 (#12683) | ✅ 已完成 | [1940_12737a128](commits/1940_12737a128/analysis.md) |
| 1941 | `ca2d11eb8` | 2025-04-01 09:04:07 +0200 | Xu Bai | Docs: Update link for User-Defined Tag Restrictions in AWS documentation (#12698) | ✅ 已完成 | [1941_ca2d11eb8](commits/1941_ca2d11eb8/analysis.md) |
| 1942 | `770daff76` | 2025-04-01 09:05:36 +0200 | dependabot[bot] | Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#12687) | ✅ 已完成 | [1942_770daff76](commits/1942_770daff76/analysis.md) |
| 1943 | `9981fe1a2` | 2025-04-01 09:10:34 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.55.0 to 26.58.0 (#12688) | ✅ 已完成 | [1943_9981fe1a2](commits/1943_9981fe1a2/analysis.md) |
| 1944 | `e55f23858` | 2025-04-01 11:55:39 +0200 | Juichang Lu | AWS: Fix Catalog URI within VendedCredentialsProvider (#12612) | ✅ 已完成 | [1944_e55f23858](commits/1944_e55f23858/analysis.md) |
| 1945 | `e1f2cfdf2` | 2025-04-01 16:43:16 +0200 | wangyinsheng | Core: Add MetricsReporter for SnapshotManager (#12665) | ✅ 已完成 | [1945_e1f2cfdf2](commits/1945_e1f2cfdf2/analysis.md) |
| 1946 | `c879eed88` | 2025-04-01 09:48:52 -0500 | Manu Zhang | Spark 3.5: Fix RewriteDataFiles with partial progress enabled and max-failed-commits larger than total-file-group (#12120) | ✅ 已完成 | [1946_c879eed88](commits/1946_c879eed88/analysis.md) |
| 1947 | `9635fb415` | 2025-04-01 10:03:11 -0700 | Andriy Onyshchuk | Spark: Use delimited column names in CreateChangelogViewProcedure (#12418) | ✅ 已完成 | [1947_9635fb415](commits/1947_9635fb415/analysis.md) |
| 1948 | `2e1577e62` | 2025-04-02 00:27:43 +0200 | Swapna Marru | Flink: Backport support source watermark for flink sql windows (#12697) | ✅ 已完成 | [1948_2e1577e62](commits/1948_2e1577e62/analysis.md) |
| 1949 | `ee0190d00` | 2025-04-02 08:12:21 +0200 | Manu Zhang | Spark 3.4: Fix RewriteDataFiles with partial progress enabled and max-failed-commits larger than total-file-group (#12701) | ✅ 已完成 | [1949_ee0190d00](commits/1949_ee0190d00/analysis.md) |
| 1950 | `aada4d2d8` | 2025-04-02 08:13:38 +0200 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 from 5.4.2 to 5.4.3 (#12685) | ✅ 已完成 | [1950_aada4d2d8](commits/1950_aada4d2d8/analysis.md) |
| 1951 | `be2c12075` | 2025-04-02 08:14:23 +0200 | dependabot[bot] | Build: Bump guava from 33.4.0-jre to 33.4.6-jre (#12686) | ✅ 已完成 | [1951_be2c12075](commits/1951_be2c12075/analysis.md) |
| 1952 | `817dc35a9` | 2025-04-02 11:55:12 +0200 | Eduard Tudenhoefner | Core: Pass storage credentials from LoadTableResponse to FileIO (#12591) | ✅ 已完成 | [1952_817dc35a9](commits/1952_817dc35a9/analysis.md) |
| 1953 | `9e65c9d5d` | 2025-04-02 14:27:19 +0200 | Sanjay Marreddi | AWS: Update the bundle `NOTICE` and `LICENSE` (#12553) | ✅ 已完成 | [1953_9e65c9d5d](commits/1953_9e65c9d5d/analysis.md) |
| 1954 | `c661a7109` | 2025-04-02 14:34:32 +0200 | Rui Li | Core, Hive: Double check commit status in case of commit conflict for NoLock (#12637) | ✅ 已完成 | [1954_c661a7109](commits/1954_c661a7109/analysis.md) |
| 1955 | `28c246e2c` | 2025-04-02 16:12:18 +0200 | Manu Zhang | Spark 3.4: Fix NotSerializableException when migrating Spark tables (#12705) | ✅ 已完成 | [1955_28c246e2c](commits/1955_28c246e2c/analysis.md) |
| 1956 | `14122cb73` | 2025-04-02 17:58:55 +0200 | slfan1989 | Doc: Remove warning for issue resolved by #11147. (#12694) | ✅ 已完成 | [1956_14122cb73](commits/1956_14122cb73/analysis.md) |
| 1957 | `cb3f331e4` | 2025-04-03 14:42:45 +0200 | big face cat | Flink: Backport avoid RANGE mode broken chain when write parallelism changes (#12080) | ✅ 已完成 | [1957_cb3f331e4](commits/1957_cb3f331e4/analysis.md) |
| 1958 | `e630e6126` | 2025-04-03 09:00:31 -0700 | Andrew Koller | Updated vendors documentation to add SingleStore (#12708) | ✅ 已完成 | [1958_e630e6126](commits/1958_e630e6126/analysis.md) |
| 1959 | `e9494358c` | 2025-04-03 15:55:50 -0600 | Daniel Weeks | Spec: update to reflect lineage is required (#12580) | ✅ 已完成 | [1959_e9494358c](commits/1959_e9494358c/analysis.md) |
| 1960 | `06f667ada` | 2025-04-03 16:05:54 -0600 | Ricardo Pereira | Core: Enhance remove snapshots efficiency by executing them in bulk (#12670) | ✅ 已完成 | [1960_06f667ada](commits/1960_06f667ada/analysis.md) |
| 1961 | `20508d582` | 2025-04-04 08:34:49 +0200 | Eduard Tudenhoefner | Core: Update deprecation msg (#12720) | ✅ 已完成 | [1961_20508d582](commits/1961_20508d582/analysis.md) |
| 1962 | `db34c1c18` | 2025-04-04 09:12:15 +0200 | Leon Lin | AWS: Add AWS integ tests to check task and enable tests based on required environment variables (#12671) | ✅ 已完成 | [1962_db34c1c18](commits/1962_db34c1c18/analysis.md) |
| 1963 | `015207526` | 2025-04-04 08:37:48 -0600 | Amogh Jahagirdar | Core, Spark: Add row lineage metadata columns, and surface them in SparkTable metadata columns (#12596) | ✅ 已完成 | [1963_015207526](commits/1963_015207526/analysis.md) |
| 1964 | `7b0f17ad7` | 2025-04-06 12:46:58 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.9 to 9.6.11 (#12728) | ✅ 已完成 | [1964_7b0f17ad7](commits/1964_7b0f17ad7/analysis.md) |
| 1965 | `6cfc21b74` | 2025-04-07 07:46:24 +0200 | dependabot[bot] | Build: Bump io.delta:delta-standalone_2.12 from 3.3.0 to 3.3.1 (#12731) | ✅ 已完成 | [1965_6cfc21b74](commits/1965_6cfc21b74/analysis.md) |
| 1966 | `88b012399` | 2025-04-07 07:47:19 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.23.0 to 3.23.2 (#12732) | ✅ 已完成 | [1966_88b012399](commits/1966_88b012399/analysis.md) |
| 1967 | `b99f33dca` | 2025-04-07 10:21:38 +0200 | GuoYu | Flink: Backport RowConverter for Iceberg Source (#12713) | ✅ 已完成 | [1967_b99f33dca](commits/1967_b99f33dca/analysis.md) |
| 1968 | `1c861707f` | 2025-04-07 12:04:08 +0200 | GuoYu | Flink: Fix NPE in SketchUtil when numPartitions bigger than length of samples (#12703) | ✅ 已完成 | [1968_1c861707f](commits/1968_1c861707f/analysis.md) |
| 1969 | `8e473a757` | 2025-04-07 12:08:09 +0200 | Bodor Laszlo | Core: Lazy init workerPool in RemoveSnapshots and SnapshotProducer (#12427) | ✅ 已完成 | [1969_8e473a757](commits/1969_8e473a757/analysis.md) |
| 1970 | `a0194e26a` | 2025-04-07 12:09:54 +0200 | GuoYu | Flink: Backport using ExternalTypeInfo in Rowconverter code instead of deprecated TableSchema.getFieldTypes (#12739) | ✅ 已完成 | [1970_a0194e26a](commits/1970_a0194e26a/analysis.md) |
| 1971 | `369fe8913` | 2025-04-07 12:34:53 +0200 | Zoltan Ratkai | Hive: Refactor HMS table parameter setting to be able to reuse (#12461) | ✅ 已完成 | [1971_369fe8913](commits/1971_369fe8913/analysis.md) |
| 1972 | `64df1afb4` | 2025-04-07 15:21:47 +0200 | GuoYu | Flink: Backport fix NPE in SketchUtil when numPartitions bigger than length of samples (#12741) | ✅ 已完成 | [1972_64df1afb4](commits/1972_64df1afb4/analysis.md) |
| 1973 | `7d7aeb89f` | 2025-04-07 09:52:23 -0700 | Ryan Blue | Core: Enable row lineage for all v3 tables (#12593) | ✅ 已完成 | [1973_7d7aeb89f](commits/1973_7d7aeb89f/analysis.md) |
| 1974 | `68d82aceb` | 2025-04-08 13:56:01 +0200 | Tom Tanaka | Spark 3.4: Migrate Spark ExtensionsTestBase-related tests (#12744) | ✅ 已完成 | [1974_68d82aceb](commits/1974_68d82aceb/analysis.md) |
| 1975 | `f39c1fa6f` | 2025-04-08 15:25:22 +0200 | dependabot[bot] | Build: Bump io.delta:delta-spark_2.12 from 3.3.0 to 3.3.1 (#12729) | ✅ 已完成 | [1975_f39c1fa6f](commits/1975_f39c1fa6f/analysis.md) |
| 1976 | `665baa5ac` | 2025-04-09 09:29:24 +0200 | jackylee | Doc: Remove Hive 2.x/3.x related docs in hive.md (#12700) | ✅ 已完成 | [1976_665baa5ac](commits/1976_665baa5ac/analysis.md) |
| 1977 | `529c0bf28` | 2025-04-09 09:10:09 -0700 | Eduard Tudenhoefner | Core: Return this instead of null in enableRowLineage() (#12747) | ✅ 已完成 | [1977_529c0bf28](commits/1977_529c0bf28/analysis.md) |
| 1978 | `e314e9eaa` | 2025-04-09 19:38:31 +0200 | gaborkaszab | Core: Drop invalid function comment for HTTPClient.isSuccessful (#12742) | ✅ 已完成 | [1978_e314e9eaa](commits/1978_e314e9eaa/analysis.md) |
| 1979 | `e56ec1121` | 2025-04-09 23:12:10 +0200 | Juichang Lu | Core: Allow HTTPClient to parse headers from properties (#12595) | ✅ 已完成 | [1979_e56ec1121](commits/1979_e56ec1121/analysis.md) |
| 1980 | `b15f4ac17` | 2025-04-09 15:27:04 -0700 | Fokko Driesprong | Throw on `{write.folder-storage.path,write.object-storage.path}` properties (#12315) | ✅ 已完成 | [1980_b15f4ac17](commits/1980_b15f4ac17/analysis.md) |
| 1981 | `55b5371e9` | 2025-04-10 17:50:54 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.1.119.Final to 4.2.0.Final (#12730) | ✅ 已完成 | [1981_55b5371e9](commits/1981_55b5371e9/analysis.md) |
| 1982 | `616bee5a5` | 2025-04-11 10:05:50 +0200 | Eduard Tudenhoefner | Api: Deprecate CredentialSupplier (#12763) | ✅ 已完成 | [1982_616bee5a5](commits/1982_616bee5a5/analysis.md) |
| 1983 | `20811c6fb` | 2025-04-11 10:54:47 +0200 | sullis | AWS: Add unit tests for AWS s3Async (#12758) | ✅ 已完成 | [1983_20811c6fb](commits/1983_20811c6fb/analysis.md) |
| 1984 | `dac562233` | 2025-04-11 11:08:28 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.58.0 to 26.59.0 (#12733) | ✅ 已完成 | [1984_dac562233](commits/1984_dac562233/analysis.md) |
| 1985 | `85b8fd359` | 2025-04-11 16:43:24 +0200 | Rodrigo | Flink: Upgrades Flink Minor versions to 1.19.2 and 1.20.1 (#12745) | ✅ 已完成 | [1985_85b8fd359](commits/1985_85b8fd359/analysis.md) |
| 1986 | `13b9391d3` | 2025-04-11 16:54:29 +0200 | Georgi Ivanov | Doc: Update hive-quickstart.md (#12746) | ✅ 已完成 | [1986_13b9391d3](commits/1986_13b9391d3/analysis.md) |
| 1987 | `3d0f080f4` | 2025-04-11 17:08:34 +0200 | GuoYu | Flink: fix rateLimit argument check in TableMaintenance (#12773) | ✅ 已完成 | [1987_3d0f080f4](commits/1987_3d0f080f4/analysis.md) |
| 1988 | `b0c4d91e9` | 2025-04-12 00:04:50 +0200 | GuoYu | Flink: backport fix rateLimit argument check in TableMaintenance (#12776) | ✅ 已完成 | [1988_b0c4d91e9](commits/1988_b0c4d91e9/analysis.md) |
| 1989 | `6cf4fc137` | 2025-04-11 17:43:41 -0500 | aeluce | Docs: Add Estuary to docs and vendors (#12764) | ✅ 已完成 | [1989_6cf4fc137](commits/1989_6cf4fc137/analysis.md) |
| 1990 | `97d1107d6` | 2025-04-12 09:39:34 -0600 | Amogh Jahagirdar | Core: Update RewriteFiles tests to test against V3 (#12777) | ✅ 已完成 | [1990_97d1107d6](commits/1990_97d1107d6/analysis.md) |
| 1991 | `e5308e841` | 2025-04-14 10:54:23 +0200 | Tom Tanaka | Spark 3.4: Migrate ExtensionsTestBase-related tests for Partition, Schema and Branch/Tag (#12766) | ✅ 已完成 | [1991_e5308e841](commits/1991_e5308e841/analysis.md) |
| 1992 | `0ebfd4ce1` | 2025-04-14 15:01:03 +0200 | dependabot[bot] | Build: Bump guava from 33.4.6-jre to 33.4.7-jre (#12789) | ✅ 已完成 | [1992_0ebfd4ce1](commits/1992_0ebfd4ce1/analysis.md) |
| 1993 | `41237e021` | 2025-04-14 15:04:14 +0200 | dependabot[bot] | Build: Bump nessie from 0.103.2 to 0.103.3 (#12786) | ✅ 已完成 | [1993_41237e021](commits/1993_41237e021/analysis.md) |
| 1994 | `1e36faac9` | 2025-04-14 18:04:21 +0200 | Jordano Mark | Core: Use OutputFile.location(), InputFile.location() in Error Messages (#12755) | ✅ 已完成 | [1994_1e36faac9](commits/1994_1e36faac9/analysis.md) |
| 1995 | `6d3b38bc6` | 2025-04-15 09:06:37 +0200 | GuoYu | Flink: Move unlock from MemoryLock open to TestCase Before (#12793) | ✅ 已完成 | [1995_6d3b38bc6](commits/1995_6d3b38bc6/analysis.md) |
| 1996 | `8d1ec91a2` | 2025-04-15 09:57:12 +0200 | GuoYu | Flink: backport move unlock from MemoryLock open to TestCase Before  to Flink 1.19 (#12795) | ✅ 已完成 | [1996_8d1ec91a2](commits/1996_8d1ec91a2/analysis.md) |
| 1997 | `0aeb574ed` | 2025-04-15 12:31:47 +0200 | iProdigy | Build: Bump junit to 5.12.2 (#12391) | ✅ 已完成 | [1997_0aeb574ed](commits/1997_0aeb574ed/analysis.md) |
| 1998 | `4587d0083` | 2025-04-15 14:01:53 +0200 | GuoYu | Flink: Fix TriggerManager to unlock task execution when previous job left an orphaned lock (#12794) | ✅ 已完成 | [1998_4587d0083](commits/1998_4587d0083/analysis.md) |
| 1999 | `d7455390e` | 2025-04-15 14:58:05 +0200 | GuoYu | Flink: Flink: backport fix TriggerManager to unlock task execution when previous job left an orphaned lock for Flink 1.19 (#12801) | ✅ 已完成 | [1999_d7455390e](commits/1999_d7455390e/analysis.md) |
| 2000 | `913e243d1` | 2025-04-15 15:02:38 +0200 | GuoYu | Flink: Fix TriggerManager parameter list for recovery tests (#12800) | ✅ 已完成 | [2000_913e243d1](commits/2000_913e243d1/analysis.md) |
| 2001 | `414b7bc11` | 2025-04-15 17:20:57 -0700 | Eduard Tudenhoefner | Spark 3.4: Migrate integration test to JUnit5 (#12796) | ✅ 已完成 | [2001_414b7bc11](commits/2001_414b7bc11/analysis.md) |
| 2002 | `9c696f0b8` | 2025-04-16 09:32:33 +0200 | Talat UYARER | Core: Improve CatalogTests (#12768) | ✅ 已完成 | [2002_9c696f0b8](commits/2002_9c696f0b8/analysis.md) |
| 2003 | `31f1fca74` | 2025-04-16 10:26:47 +0200 | Dao Thanh Tung | Docs: Update the docs for working with Flink and REST catalog (#12726) | ✅ 已完成 | [2003_31f1fca74](commits/2003_31f1fca74/analysis.md) |
| 2004 | `68d833e9a` | 2025-04-16 10:58:56 +0200 | Tom Tanaka | Spark 3.4: Migrate ExtensionsTestBase-related tests for Snapshot manipulation, ChangeLogView and Distribution/Ordering (#12807) | ✅ 已完成 | [2004_68d833e9a](commits/2004_68d833e9a/analysis.md) |
| 2005 | `2d63ec491` | 2025-04-16 12:02:15 +0200 | Yuya Ebihara | Core: Fix deprecated FileSystem.isDirectory warning and remove redundant test code (#12805) | ✅ 已完成 | [2005_2d63ec491](commits/2005_2d63ec491/analysis.md) |
| 2006 | `c338323b2` | 2025-04-16 13:06:53 +0200 | Eduard Tudenhoefner | Core: Test loading table/view with non-existing namespace (#12812) | ✅ 已完成 | [2006_c338323b2](commits/2006_c338323b2/analysis.md) |
| 2007 | `787b9f0be` | 2025-04-16 16:10:15 +0200 | slfan1989 | docs: Fix pusblished (#12814) | ✅ 已完成 | [2007_787b9f0be](commits/2007_787b9f0be/analysis.md) |
| 2008 | `013d09e47` | 2025-04-16 16:51:46 +0200 | Tom Tanaka | Spark 3.4: Migrate ExtensionsTestBase-related remaining tests (#12813) | ✅ 已完成 | [2008_013d09e47](commits/2008_013d09e47/analysis.md) |
| 2009 | `8e897f1b6` | 2025-04-17 07:54:02 +0200 | Tom Tanaka | Docs: Add the recommended style for ArrayAssertions (#12820) | ✅ 已完成 | [2009_8e897f1b6](commits/2009_8e897f1b6/analysis.md) |
| 2010 | `5d97ad645` | 2025-04-17 08:00:39 +0200 | slfan1989 | Spark 3.5: Use ProcedureInput for SnapshotTableProcedure. (#12783) | ✅ 已完成 | [2010_5d97ad645](commits/2010_5d97ad645/analysis.md) |
| 2011 | `c81dda732` | 2025-04-17 08:03:23 +0200 | slfan1989 | Spark 3.5: Use ProcedureInput for MigrateTableProcedure. (#12782) | ✅ 已完成 | [2011_c81dda732](commits/2011_c81dda732/analysis.md) |
| 2012 | `9fc49e187` | 2025-04-17 16:28:37 +0200 | JB Onofré | AWS, Azure, Kafka: Fix versions in LICENSE and NOTICE (#12831) | ✅ 已完成 | [2012_9fc49e187](commits/2012_9fc49e187/analysis.md) |
| 2013 | `8348decfd` | 2025-04-18 10:06:58 -0700 | slfan1989 | Spark3.4: Backport ProcedureInput for MigrateTableProcedure And SnapshotTableProcedure. (#12837) | ✅ 已完成 | [2013_8348decfd](commits/2013_8348decfd/analysis.md) |
| 2014 | `f25e07db6` | 2025-04-18 10:50:18 -0700 | Ryan Blue | Core: Support first-row-id for manifests and manifest lists (#12672) | ✅ 已完成 | [2014_f25e07db6](commits/2014_f25e07db6/analysis.md) |
| 2015 | `38b7c090b` | 2025-04-18 15:35:17 -0500 | Russell Spitzer | Site: Remove Iceberg Summit Link from the Homepage (#12842) | ✅ 已完成 | [2015_38b7c090b](commits/2015_38b7c090b/analysis.md) |
| 2016 | `3a29199e7` | 2025-04-18 18:28:51 -0500 | sullis | Core: Use ALL_VERSIONS constant in TestBase (#12748) | ✅ 已完成 | [2016_3a29199e7](commits/2016_3a29199e7/analysis.md) |
| 2017 | `edc7f2a54` | 2025-04-19 17:13:46 -0700 | Ryan Blue | Spec: Update row lineage requirements for upgrading tables (#12781) | ✅ 已完成 | [2017_edc7f2a54](commits/2017_edc7f2a54/analysis.md) |
| 2018 | `ad6d6cd47` | 2025-04-21 10:37:32 -0700 | Matyas Orhidi | Flink: Add StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE (#12839) | ✅ 已完成 | [2018_ad6d6cd47](commits/2018_ad6d6cd47/analysis.md) |
| 2019 | `ab92d6e66` | 2025-04-21 11:37:54 -0600 | Ryan Blue | Spark: Add _row_id and _last_updated_sequence_number readers (#12836) | ✅ 已完成 | [2019_ab92d6e66](commits/2019_ab92d6e66/analysis.md) |
| 2020 | `cc2e0ff9e` | 2025-04-21 14:34:24 -0600 | Aihua Xu | Spec: Clarify variant lower/upper bounds (#12658) | ✅ 已完成 | [2020_cc2e0ff9e](commits/2020_cc2e0ff9e/analysis.md) |
| 2021 | `89e499f99` | 2025-04-21 15:16:20 -0600 | Ryan Blue | API: Use normalized JSON path to identify Variant fields. (#12835) | ✅ 已完成 | [2021_89e499f99](commits/2021_89e499f99/analysis.md) |
| 2022 | `5039ad2b4` | 2025-04-21 19:02:45 -0700 | Ryan Blue | Core: Add test cases for row lineage metadata (#12843) | ✅ 已完成 | [2022_5039ad2b4](commits/2022_5039ad2b4/analysis.md) |
| 2023 | `04bb3eed3` | 2025-04-22 08:57:19 +0200 | slfan1989 | Spark 3.5: Add Parallelism Parameter Validation to AddFilesProcedure. (#12784) | ✅ 已完成 | [2023_04bb3eed3](commits/2023_04bb3eed3/analysis.md) |
| 2024 | `12b1f52ea` | 2025-04-22 10:29:44 +0200 | Fokko Driesprong | Spec: Allow the use of `source-id` in V3 (#12644) | ✅ 已完成 | [2024_12b1f52ea](commits/2024_12b1f52ea/analysis.md) |
| 2025 | `038646352` | 2025-04-22 12:02:05 +0200 | slfan1989 | Core: Use assumeThat instead of assumeTrue (#12822) | ✅ 已完成 | [2025_038646352](commits/2025_038646352/analysis.md) |
| 2026 | `3f661d5c6` | 2025-04-22 12:03:14 +0200 | Tom Tanaka | Spark 3.4: Migrate tests in spark, extensions and functions (#12853) | ✅ 已完成 | [2026_3f661d5c6](commits/2026_3f661d5c6/analysis.md) |
| 2027 | `7dbafb438` | 2025-04-22 15:14:07 +0200 | Eduard Tudenhoefner | API: Don't check underlying error msg on AIOOBE (#12867) | ✅ 已完成 | [2027_7dbafb438](commits/2027_7dbafb438/analysis.md) |
| 2028 | `dbe76d477` | 2025-04-22 14:46:44 -0500 | Fokko Driesprong | Site: Add `format/` to site-ci (#12869) | ✅ 已完成 | [2028_dbe76d477](commits/2028_dbe76d477/analysis.md) |
| 2029 | `4b131c5ec` | 2025-04-22 15:09:33 -0500 | ccmao1130 | Site: Update links to daft docs (#12860) | ✅ 已完成 | [2029_4b131c5ec](commits/2029_4b131c5ec/analysis.md) |
| 2030 | `321e66ffc` | 2025-04-23 09:59:40 +0200 | slfan1989 | Spark 3.4: Add Parallelism Parameter Validation to AddFilesProcedure (#12872) | ✅ 已完成 | [2030_321e66ffc](commits/2030_321e66ffc/analysis.md) |
| 2031 | `fc31fd505` | 2025-04-23 16:44:56 +0200 | Eduard Tudenhoefner | Core: Add test when listing namespaces with a non-existing namespace (#12873) | ✅ 已完成 | [2031_fc31fd505](commits/2031_fc31fd505/analysis.md) |
| 2032 | `e996688c5` | 2025-04-23 13:21:24 -0700 | Maximilian Michels | Flink: Move v1.20 to v2.0 directory | ✅ 已完成 | [2032_e996688c5](commits/2032_e996688c5/analysis.md) |
| 2033 | `f5489ae42` | 2025-04-23 13:21:24 -0700 | Maximilian Michels | Flink: Copy back v1.20 directory | ✅ 已完成 | [2033_f5489ae42](commits/2033_f5489ae42/analysis.md) |
| 2034 | `b6477ed6c` | 2025-04-23 13:21:24 -0700 | Maximilian Michels | Flink: Add support for Flink 2.0 | ✅ 已完成 | [2034_b6477ed6c](commits/2034_b6477ed6c/analysis.md) |
| 2035 | `d7f8e5400` | 2025-04-23 13:21:24 -0700 | Maximilian Michels | Flink: Remove Flink 1.18 support | ✅ 已完成 | [2035_d7f8e5400](commits/2035_d7f8e5400/analysis.md) |
| 2036 | `ee2ffb4e9` | 2025-04-24 09:50:42 +0200 | Eduard Tudenhoefner | Core: Fix Kryo ser/de with StorageCredential config (#12882) | ✅ 已完成 | [2036_ee2ffb4e9](commits/2036_ee2ffb4e9/analysis.md) |
| 2037 | `137965d72` | 2025-04-24 15:06:35 -0700 | Aihua Xu | Core, Parquet: Add timestamp(9), time, and UUID types for Variant (#12682) | ✅ 已完成 | [2037_137965d72](commits/2037_137965d72/analysis.md) |
| 2038 | `ee70a53bf` | 2025-04-24 18:20:48 -0500 | Ryan Blue | Spec: Avoid struct field conflicts in default values (#12841) | ✅ 已完成 | [2038_ee70a53bf](commits/2038_ee70a53bf/analysis.md) |
| 2039 | `01fe380d4` | 2025-04-25 09:39:42 +0200 | Leon Lin | Core: Ensure reactivated view version uses correct timestamp (#12821) | ✅ 已完成 | [2039_01fe380d4](commits/2039_01fe380d4/analysis.md) |
| 2040 | `22d194f5d` | 2025-04-25 16:17:10 -0700 | Aihua Xu | Parquet: Add variant array reader in Parquet (#12512) | ✅ 已完成 | [2040_22d194f5d](commits/2040_22d194f5d/analysis.md) |
| 2041 | `63c489c64` | 2025-04-28 07:54:08 +0200 | dependabot[bot] | Build: Bump nessie from 0.103.3 to 0.103.5 (#12902) | ✅ 已完成 | [2041_63c489c64](commits/2041_63c489c64/analysis.md) |
| 2042 | `20b12b733` | 2025-04-28 07:55:18 +0200 | dependabot[bot] | Build: Bump testcontainers from 1.20.6 to 1.21.0 (#12904) | ✅ 已完成 | [2042_20b12b733](commits/2042_20b12b733/analysis.md) |
| 2043 | `423f7034f` | 2025-04-28 08:14:41 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.11 to 9.6.12 (#12848) | ✅ 已完成 | [2043_423f7034f](commits/2043_423f7034f/analysis.md) |
| 2044 | `94240f50e` | 2025-04-28 08:24:25 +0200 | JB Onofré | Build: Upgrade to Gradle 8.14 (#12898) | ✅ 已完成 | [2044_94240f50e](commits/2044_94240f50e/analysis.md) |
| 2045 | `346414c0f` | 2025-04-28 08:25:18 +0200 | jackylee | Spark: Use newArrayListWithExpectedSize in NDVSketchUtil (#12907) | ✅ 已完成 | [2045_346414c0f](commits/2045_346414c0f/analysis.md) |
| 2046 | `ef45c137f` | 2025-04-28 08:25:37 +0200 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#12906) | ✅ 已完成 | [2046_ef45c137f](commits/2046_ef45c137f/analysis.md) |
| 2047 | `dc26b72ad` | 2025-04-28 08:38:25 +0200 | Ajantha Bhat | Docs: Release notes for 1.9.0 (#12911) | ✅ 已完成 | [2047_dc26b72ad](commits/2047_dc26b72ad/analysis.md) |
| 2048 | `b0596267a` | 2025-04-28 09:55:45 +0200 | Ajantha Bhat | Infra: Add 1.9.0 to issue template (#12913) | ✅ 已完成 | [2048_b0596267a](commits/2048_b0596267a/analysis.md) |
| 2049 | `b9effd2e7` | 2025-04-28 14:53:59 +0200 | Ajantha Bhat | Infra: Update doap.rdf file (#12918) | ✅ 已完成 | [2049_b9effd2e7](commits/2049_b9effd2e7/analysis.md) |
| 2050 | `68b016cca` | 2025-04-28 16:39:52 +0200 | Manu Zhang | Core: Increase wait time of flaky test (#12714) | ✅ 已完成 | [2050_68b016cca](commits/2050_68b016cca/analysis.md) |
| 2051 | `eed6dc7f0` | 2025-04-28 17:54:56 +0200 | Matyas Orhidi | Flink: Backport add StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT_EXCLUSIVE to Flink 1.19 (#12899) | ✅ 已完成 | [2051_eed6dc7f0](commits/2051_eed6dc7f0/analysis.md) |
| 2052 | `81f61f4dd` | 2025-04-28 18:21:07 +0200 | Ajantha Bhat | Docs: Site update for 1.9.0 (#12921) | ✅ 已完成 | [2052_81f61f4dd](commits/2052_81f61f4dd/analysis.md) |
| 2053 | `829ae7a11` | 2025-04-28 10:57:32 -0700 | Amogh Jahagirdar | Spark 3.5: Update MERGE and UPDATE for row lineage (#12736) | ✅ 已完成 | [2053_829ae7a11](commits/2053_829ae7a11/analysis.md) |
| 2054 | `e1ab42591` | 2025-04-29 12:03:46 +0200 | GuoYu | Flink: Add lockFactory open in LockRemover for table maintenance (#12900) | ✅ 已完成 | [2054_e1ab42591](commits/2054_e1ab42591/analysis.md) |
| 2055 | `5ed88a5db` | 2025-04-29 15:43:31 +0200 | Ajantha Bhat | Docs: Fix version doc release step (#12922) | ✅ 已完成 | [2055_5ed88a5db](commits/2055_5ed88a5db/analysis.md) |
| 2056 | `242717c8c` | 2025-04-29 13:48:12 -0700 | Aihua Xu | Parquet: Implement Variant array writes (#12847) | ✅ 已完成 | [2056_242717c8c](commits/2056_242717c8c/analysis.md) |
| 2057 | `0580793c4` | 2025-04-30 10:56:06 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.29.52 to 2.31.30 (#12905) | ✅ 已完成 | [2057_0580793c4](commits/2057_0580793c4/analysis.md) |
| 2058 | `3aac0643c` | 2025-04-30 10:56:29 +0200 | dependabot[bot] | Build: Bump jackson-bom from 2.18.3 to 2.19.0 (#12903) | ✅ 已完成 | [2058_3aac0643c](commits/2058_3aac0643c/analysis.md) |
| 2059 | `f7be3a66f` | 2025-04-30 11:34:46 +0200 | Tom Tanaka | Spark 3.4: Migrate tests in sql (#12934) | ✅ 已完成 | [2059_f7be3a66f](commits/2059_f7be3a66f/analysis.md) |
| 2060 | `a0d118724` | 2025-04-30 12:47:10 +0200 | GuoYu | Flink: Backport Add lockFactory open in LockRemover for table maintenance to Flink 1.20 and 1.19 (#12929) | ✅ 已完成 | [2060_a0d118724](commits/2060_a0d118724/analysis.md) |
| 2061 | `767688a33` | 2025-04-30 13:00:08 +0200 | Gabor Szarnyas | Docs: Add DuckDB (#12932) | ✅ 已完成 | [2061_767688a33](commits/2061_767688a33/analysis.md) |
| 2062 | `738336b21` | 2025-04-30 16:02:30 +0200 | Tom Tanaka | Spark 3.4: Migrate Partition Transform tests (#12941) | ✅ 已完成 | [2062_738336b21](commits/2062_738336b21/analysis.md) |
| 2063 | `90273db51` | 2025-04-30 16:03:31 +0200 | GuoYu | Flink: Fix typos in JdbcLockFactory (#12940) | ✅ 已完成 | [2063_90273db51](commits/2063_90273db51/analysis.md) |
| 2064 | `bcda1a339` | 2025-04-30 16:36:43 +0200 | pvary | Spark: Update RewriteDataFilesSparkAction and RewritePositionDeleteFilesSparkAction to use the new APIs (#12692) | ✅ 已完成 | [2064_bcda1a339](commits/2064_bcda1a339/analysis.md) |
| 2065 | `5ac19429f` | 2025-04-30 17:12:54 +0200 | GuoYu | Flink: change Preconditions import from flink util to guava (#12939) | ✅ 已完成 | [2065_5ac19429f](commits/2065_5ac19429f/analysis.md) |
| 2066 | `d0cf7f53c` | 2025-04-30 11:09:03 -0600 | Amogh Jahagirdar | Spark: Update Spark Parquet vectorized read tests to uses Iceberg Record instead of Avro GenericRecord (#12925) | ✅ 已完成 | [2066_d0cf7f53c](commits/2066_d0cf7f53c/analysis.md) |
| 2067 | `e6003454a` | 2025-04-30 11:22:21 -0700 | Aihua Xu | Parquet: Shred variant arrays when element type is uniform (#12933) | ✅ 已完成 | [2067_e6003454a](commits/2067_e6003454a/analysis.md) |
| 2068 | `bd9353ab0` | 2025-04-30 21:26:31 -0400 | Sung Yun | OpenAPI: Use more clear language in recommending error responses (#12376) | ✅ 已完成 | [2068_bd9353ab0](commits/2068_bd9353ab0/analysis.md) |
| 2069 | `bea3f8b58` | 2025-05-01 07:59:46 -0600 | Xiaoxuan | Core: Broaden exception handling in writer clean up logic (#12863) | ✅ 已完成 | [2069_bea3f8b58](commits/2069_bea3f8b58/analysis.md) |
| 2070 | `61e8acecf` | 2025-05-01 15:53:18 -0500 | Wing Yew Poon | Spec: Remove misleading statement about source-ids (#12948) | ✅ 已完成 | [2070_61e8acecf](commits/2070_61e8acecf/analysis.md) |
| 2071 | `696a72c0f` | 2025-05-02 17:49:23 -0600 | Russell Spitzer | API, Build: Explicitly pass version into Git Properties Plugin (#12949) | ✅ 已完成 | [2071_696a72c0f](commits/2071_696a72c0f/analysis.md) |
| 2072 | `cb02daf32` | 2025-05-05 08:13:35 +0200 | dependabot[bot] | Build: Bump parquet from 1.15.1 to 1.15.2 (#12964) | ✅ 已完成 | [2072_cb02daf32](commits/2072_cb02daf32/analysis.md) |
| 2073 | `ed20482ab` | 2025-05-05 08:14:19 +0200 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#12966) | ✅ 已完成 | [2073_ed20482ab](commits/2073_ed20482ab/analysis.md) |
| 2074 | `0db08b7c6` | 2025-05-05 08:14:32 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.31.30 to 2.31.35 (#12967) | ✅ 已完成 | [2074_0db08b7c6](commits/2074_0db08b7c6/analysis.md) |
| 2075 | `13aebe5d3` | 2025-05-05 08:14:59 +0200 | dependabot[bot] | Build: Bump nessie from 0.103.5 to 0.103.6 (#12963) | ✅ 已完成 | [2075_13aebe5d3](commits/2075_13aebe5d3/analysis.md) |
| 2076 | `60def0857` | 2025-05-05 11:59:19 +0200 | Tom Tanaka | Spark 3.4: Migrate sql tests to JUnit5 (#12945) | ✅ 已完成 | [2076_60def0857](commits/2076_60def0857/analysis.md) |
| 2077 | `f9bfec36d` | 2025-05-05 12:19:55 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.28.5 to 0.30.1 (#12961) | ✅ 已完成 | [2077_f9bfec36d](commits/2077_f9bfec36d/analysis.md) |
| 2078 | `d2806b4d8` | 2025-05-05 15:39:49 +0200 | Subhash | Docs: Incorrect property in CREATE CATALOG for Flink (#12894) | ✅ 已完成 | [2078_d2806b4d8](commits/2078_d2806b4d8/analysis.md) |
| 2079 | `af32a07f4` | 2025-05-05 11:22:53 -0500 | Xi Chen | Data, Spark 3.4, 3.5: Add flag to handle missing files for `importSparkTable` (#12212) | ✅ 已完成 | [2079_af32a07f4](commits/2079_af32a07f4/analysis.md) |
| 2080 | `7f3f450bb` | 2025-05-05 10:08:14 -0700 | ggershinsky | Spec additions for encryption (#12162) | ✅ 已完成 | [2080_7f3f450bb](commits/2080_7f3f450bb/analysis.md) |
| 2081 | `a214e9bc2` | 2025-05-05 14:21:58 -0700 | Anton Okolnychyi | Spec: Update partition stats for V3 (#12098) | ✅ 已完成 | [2081_a214e9bc2](commits/2081_a214e9bc2/analysis.md) |
| 2082 | `84741867b` | 2025-05-05 16:29:39 -0600 | Eduard Tudenhoefner | AWS, GCP: Deprecate passing FileIO properties through constructor (#12972) | ✅ 已完成 | [2082_84741867b](commits/2082_84741867b/analysis.md) |
| 2083 | `cbf2695e9` | 2025-05-06 10:05:31 +0200 | pvary | Flink: Maintenance - RewriteDataFiles (#11497) | ✅ 已完成 | [2083_cbf2695e9](commits/2083_cbf2695e9/analysis.md) |
| 2084 | `47f8ae8d7` | 2025-05-06 10:51:15 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.23.2 to 3.24.0 (#12965) | ✅ 已完成 | [2084_47f8ae8d7](commits/2084_47f8ae8d7/analysis.md) |
| 2085 | `1e944650f` | 2025-05-06 11:09:35 -0500 | Russell Spitzer | API, Core: Add deleteFile to RowDelta API (#12861) | ✅ 已完成 | [2085_1e944650f](commits/2085_1e944650f/analysis.md) |
| 2086 | `92d89dc91` | 2025-05-06 11:13:40 -0700 | Ryan Blue | API, Core: Add table metadata keys for encryption (#12927) | ✅ 已完成 | [2086_92d89dc91](commits/2086_92d89dc91/analysis.md) |
| 2087 | `a04c53265` | 2025-05-06 15:53:57 -0700 | Akhil Lawrence | Core: Enable HTTP proxy support for the client used by REST Catalog (#12406) | ✅ 已完成 | [2087_a04c53265](commits/2087_a04c53265/analysis.md) |
| 2088 | `4bb60aa0b` | 2025-05-06 22:52:15 -0700 | Gyula Fora | Flink: Backport Maintenance - RewriteDataFiles to Flink 1.19 | ✅ 已完成 | [2088_4bb60aa0b](commits/2088_4bb60aa0b/analysis.md) |
| 2089 | `27e73fea2` | 2025-05-06 22:52:15 -0700 | Gyula Fora | Flink: Backport Maintenance - RewriteDataFiles to Flink 2.0 | ✅ 已完成 | [2089_27e73fea2](commits/2089_27e73fea2/analysis.md) |
| 2090 | `94ba29596` | 2025-05-07 08:57:45 +0200 | Huaxin Gao | Build: Bump Comet from 0.5.0 to 0.8.1 (#12974) | ✅ 已完成 | [2090_94ba29596](commits/2090_94ba29596/analysis.md) |
| 2091 | `9fb80b716` | 2025-05-07 09:05:42 +0200 | Devin Smith | Core: Disallow creation of invalid PartitionSpec (#12887) | ✅ 已完成 | [2091_9fb80b716](commits/2091_9fb80b716/analysis.md) |
| 2092 | `c4afd783d` | 2025-05-07 09:03:40 -0500 | Ryan Blue | REST Spec: Remove update to enable row lineage (#12986) | ✅ 已完成 | [2092_c4afd783d](commits/2092_c4afd783d/analysis.md) |
| 2093 | `3bb6a25cc` | 2025-05-07 09:16:55 -0500 | Ryan Blue | Spec: Update v3 summary, add row lineage (#12982) | ✅ 已完成 | [2093_3bb6a25cc](commits/2093_3bb6a25cc/analysis.md) |
| 2094 | `a00658b73` | 2025-05-07 08:33:15 -0600 | Manu Zhang | Docs: Mark Spark 3.3 as End-of-Life since 1.9 (#12989) | ✅ 已完成 | [2094_a00658b73](commits/2094_a00658b73/analysis.md) |
| 2095 | `176a6c6b8` | 2025-05-07 16:37:36 +0200 | gaborkaszab | Spark 3.4: Update RewriteDataFilesSparkAction and RewritePositionDeleteFilesSparkAction to use the new APIs (#12980) | ✅ 已完成 | [2095_176a6c6b8](commits/2095_176a6c6b8/analysis.md) |
| 2096 | `c730c0b16` | 2025-05-07 08:09:44 -0700 | Eduard Tudenhoefner | Build, Core: Let RevAPI compare against 1.9.0 / Fix API breakage around StorageCredential (#12930) | ✅ 已完成 | [2096_c730c0b16](commits/2096_c730c0b16/analysis.md) |
| 2097 | `542a4d764` | 2025-05-07 17:35:04 +0200 | Eduard Tudenhoefner | GCP: Support multiple storage credential prefixes (#12881) | ✅ 已完成 | [2097_542a4d764](commits/2097_542a4d764/analysis.md) |
| 2098 | `8cf996188` | 2025-05-07 12:53:39 -0600 | Manu Zhang | AWS, Core, Flink, Parquet: Remove deprecations for 1.10.0 (#12909) | ✅ 已完成 | [2098_8cf996188](commits/2098_8cf996188/analysis.md) |
| 2099 | `ad7f5c439` | 2025-05-07 23:18:25 -0600 | Huaxin Gao | Spark: Spark 4.0 initial support (#12494) | ✅ 已完成 | [2099_ad7f5c439](commits/2099_ad7f5c439/analysis.md) |
| 2100 | `ada3d12b4` | 2025-05-08 07:31:29 +0200 | Tom Tanaka | Spark 3.4: Migrate source and spark tests to JUnit5 (#12998) | ✅ 已完成 | [2100_ada3d12b4](commits/2100_ada3d12b4/analysis.md) |
| 2101 | `df866c51a` | 2025-05-08 07:32:47 +0200 | Dongjoon Hyun | ORC: Upgrade ORC to 1.9.6 (#13003) | ✅ 已完成 | [2101_df866c51a](commits/2101_df866c51a/analysis.md) |
| 2102 | `809a2327f` | 2025-05-08 11:16:45 +0200 | Tom Tanaka | Spark 3.4: Migrate SparkCatalogTestBase related tests to JUnit5 (#13007) | ✅ 已完成 | [2102_809a2327f](commits/2102_809a2327f/analysis.md) |
| 2103 | `bed88a38d` | 2025-05-08 12:03:14 +0200 | Ajantha Bhat | Core: Refactor and use InternalData for partition stats (#12946) | ✅ 已完成 | [2103_bed88a38d](commits/2103_bed88a38d/analysis.md) |
| 2104 | `9c8c431f8` | 2025-05-08 16:10:00 +0200 | sundyli | Docs: Add Databend link (#13002) | ✅ 已完成 | [2104_9c8c431f8](commits/2104_9c8c431f8/analysis.md) |
| 2105 | `a5bcacd97` | 2025-05-09 09:51:33 -0600 | Huaxin Gao | Revert "Spark: Spark 4.0 initial support (#12494)" | ✅ 已完成 | [2105_a5bcacd97](commits/2105_a5bcacd97/analysis.md) |
| 2106 | `582d151de` | 2025-05-09 17:15:55 -0400 | Marc Cenac | Update ADLS implementation status (#13020) | ✅ 已完成 | [2106_582d151de](commits/2106_582d151de/analysis.md) |
| 2107 | `97c0e136b` | 2025-05-09 15:29:04 -0700 | Szehon Ho | Spec: Clarify behavior of special geo objects for lower/upper bounds (#12956) | ✅ 已完成 | [2107_97c0e136b](commits/2107_97c0e136b/analysis.md) |
| 2108 | `8976bc5fd` | 2025-05-12 10:15:26 +0200 | Tom Tanaka | Spark 3.4: Migrate SparkTestBaseWithCatalog related tests to JUnit5 (#13015) | ✅ 已完成 | [2108_8976bc5fd](commits/2108_8976bc5fd/analysis.md) |
| 2109 | `951ba965a` | 2025-05-12 12:33:14 +0200 | Tom Tanaka | Spark 3.4: Migrate SparkTestBase related tests to JUnit5 (#13031) | ✅ 已完成 | [2109_951ba965a](commits/2109_951ba965a/analysis.md) |
| 2110 | `fa1d86060` | 2025-05-12 14:43:36 -0500 | Guy Khazma | Docs: Add Spark SQL Configurations (#12931) | ✅ 已完成 | [2110_fa1d86060](commits/2110_fa1d86060/analysis.md) |
| 2111 | `acf56af52` | 2025-05-12 14:49:44 -0500 | Amogh Jahagirdar | Parquet: Fix to ensure that last updated sequence numbers for V2 and earlier tables are null (#13001) | ✅ 已完成 | [2111_acf56af52](commits/2111_acf56af52/analysis.md) |
| 2112 | `4735142fb` | 2025-05-12 22:10:07 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.12 to 9.6.13 (#13029) | ✅ 已完成 | [2112_4735142fb](commits/2112_4735142fb/analysis.md) |
| 2113 | `7217fb446` | 2025-05-12 13:24:51 -0700 | Ryan Blue | REST spec: Add encryption keys (#12987) | ✅ 已完成 | [2113_7217fb446](commits/2113_7217fb446/analysis.md) |
| 2114 | `d04be9b6d` | 2025-05-12 16:06:22 -0700 | huaxiangsun | Add table property to disable/enable parquet column statistics #12770 (#12771) | ✅ 已完成 | [2114_d04be9b6d](commits/2114_d04be9b6d/analysis.md) |
| 2115 | `6c2950700` | 2025-05-13 09:04:53 +0200 | Manu Zhang | Docs: Fix links to javadoc (#12880) | ✅ 已完成 | [2115_6c2950700](commits/2115_6c2950700/analysis.md) |
| 2116 | `630bf0d33` | 2025-05-13 12:19:54 +0200 | Manu Zhang | OpenAPI: Add retries when finding free port for REST server (#13017) | ✅ 已完成 | [2116_630bf0d33](commits/2116_630bf0d33/analysis.md) |
| 2117 | `5d2230ead` | 2025-05-13 14:42:11 +0200 | Tom Tanaka | Spark 3.4: Migrate tests in spark.data including refactoring Spark 3.5 tests (#13038) | ✅ 已完成 | [2117_5d2230ead](commits/2117_5d2230ead/analysis.md) |
| 2118 | `8facdafc3` | 2025-05-13 09:44:40 -0700 | Talat UYARER | Catalog: Add BigQuery Metastore Catalog Support (#12808) | ✅ 已完成 | [2118_8facdafc3](commits/2118_8facdafc3/analysis.md) |
| 2119 | `ba75a11eb` | 2025-05-13 19:03:15 +0200 | Tom Tanaka | Spark 3.4: Migrate tests in spark.source including refactoring Spark 3.5 tests (#13040) | ✅ 已完成 | [2119_ba75a11eb](commits/2119_ba75a11eb/analysis.md) |
| 2120 | `d2124f277` | 2025-05-13 14:25:05 -0700 | Manu Zhang | Spec: Note position delete files are only deprecated in v3 (#12983) | ✅ 已完成 | [2120_d2124f277](commits/2120_d2124f277/analysis.md) |
| 2121 | `aaba2a334` | 2025-05-14 08:35:56 +0200 | B Vadlamani | Nessie: Throw a NoSuchNamespaceException when listing a non-existing namespace (#12901) | ✅ 已完成 | [2121_aaba2a334](commits/2121_aaba2a334/analysis.md) |
| 2122 | `4ea7f9342` | 2025-05-14 09:27:45 +0200 | Tom Tanaka | Docs: Add Delete Granularity option to write configuration (#13046) | ✅ 已完成 | [2122_4ea7f9342](commits/2122_4ea7f9342/analysis.md) |
| 2123 | `d44cba57c` | 2025-05-14 10:00:07 +0200 | Tom Tanaka | Spark 3.4: Migrate other JUnit 4 dependant tests to JUnit 5 (#13044) | ✅ 已完成 | [2123_d44cba57c](commits/2123_d44cba57c/analysis.md) |
| 2124 | `0aab58191` | 2025-05-14 16:00:38 +0200 | Yuya Ebihara | Arrow: Avoid deprecated ArrowType.Decimal constructor (#13051) | ✅ 已完成 | [2124_0aab58191](commits/2124_0aab58191/analysis.md) |
| 2125 | `d94b09b43` | 2025-05-14 16:12:15 +0200 | Ajantha Bhat | Core: Support incremental compute for partition stats (#12629) | ✅ 已完成 | [2125_d94b09b43](commits/2125_d94b09b43/analysis.md) |
| 2126 | `b050efce8` | 2025-05-14 17:01:45 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.59.0 to 26.60.0 (#13026) | ✅ 已完成 | [2126_b050efce8](commits/2126_b050efce8/analysis.md) |
| 2127 | `c882e3982` | 2025-05-14 17:02:19 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.0.Final to 4.2.1.Final (#13028) | ✅ 已完成 | [2127_c882e3982](commits/2127_c882e3982/analysis.md) |
| 2128 | `268661af2` | 2025-05-14 22:09:02 +0200 | Maximilian Michels | Flink: Add DynamicRecord / DynamicRecordInternal / DynamicRecordInternalSerializer (#12996) | ✅ 已完成 | [2128_268661af2](commits/2128_268661af2/analysis.md) |
| 2129 | `cb33ec16d` | 2025-05-14 15:34:11 -0500 | Ajantha Bhat | Common: Reduce visibility of deprecated method since 1.7.0 (#13053) | ✅ 已完成 | [2129_cb33ec16d](commits/2129_cb33ec16d/analysis.md) |
| 2130 | `71cbe17ce` | 2025-05-14 16:31:44 -0600 | huaxingao | Spark: Move 3.5 as 4.0 | ✅ 已完成 | [2130_71cbe17ce](commits/2130_71cbe17ce/analysis.md) |
| 2131 | `8353ac8f8` | 2025-05-14 16:31:44 -0600 | huaxingao | Spark: Copy back 4.0 as 3.5 | ✅ 已完成 | [2131_8353ac8f8](commits/2131_8353ac8f8/analysis.md) |
| 2132 | `6c98a9e47` | 2025-05-14 16:31:44 -0600 | huaxingao | Spark: initial support for Spark 4.0 | ✅ 已完成 | [2132_6c98a9e47](commits/2132_6c98a9e47/analysis.md) |
| 2133 | `66b60b7b5` | 2025-05-15 08:41:34 +0200 | Eduard Tudenhoefner | Build: Remove JUnit4 dependency (#12938) | ✅ 已完成 | [2133_66b60b7b5](commits/2133_66b60b7b5/analysis.md) |
| 2134 | `31c315f69` | 2025-05-15 10:20:04 +0200 | GuoYu | Flink: Support zookeeper lock in TableMaintenance (#12810) | ✅ 已完成 | [2134_31c315f69](commits/2134_31c315f69/analysis.md) |
| 2135 | `a25f9938c` | 2025-05-15 12:28:52 -0600 | hari | [SPARK] Fix add_files type conversion exception and incorrect partition value when handling null partitions (#12886) | ✅ 已完成 | [2135_a25f9938c](commits/2135_a25f9938c/analysis.md) |
| 2136 | `cbbc8728e` | 2025-05-16 07:47:49 +0200 | Talat UYARER | Core: Use baseTableLocation() instead of hardcoded table location in CatalogTests (#13071) | ✅ 已完成 | [2136_cbbc8728e](commits/2136_cbbc8728e/analysis.md) |
| 2137 | `8511a37ce` | 2025-05-16 20:44:23 +0200 | GuoYu | Flink: Backport support zookeeper lock in TableMaintenance (#13063) | ✅ 已完成 | [2137_8511a37ce](commits/2137_8511a37ce/analysis.md) |
| 2138 | `acffd4dc5` | 2025-05-16 13:49:43 -0500 | Julien Guitton | Kafka Connect: Add BigQuery Metastore catalog (#13041) | ✅ 已完成 | [2138_acffd4dc5](commits/2138_acffd4dc5/analysis.md) |
| 2139 | `b050314a0` | 2025-05-16 21:01:00 +0200 | GuoYu | Flink: Fix watermark no pass in TaskResultAggregator (#13069) | ✅ 已完成 | [2139_b050314a0](commits/2139_b050314a0/analysis.md) |
| 2140 | `c2478968e` | 2025-05-16 21:06:21 +0200 | B Vadlamani | Core: Add max files rewrite option for RewriteAction  (#12824) | ✅ 已完成 | [2140_c2478968e](commits/2140_c2478968e/analysis.md) |
| 2141 | `b08b241aa` | 2025-05-18 16:33:09 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.13 to 9.6.14 (#13090) | ✅ 已完成 | [2141_b08b241aa](commits/2141_b08b241aa/analysis.md) |
| 2142 | `a44ec8d1c` | 2025-05-18 22:14:26 -0700 | Anton Okolnychyi | Spec: Clarify writer requirements to prevent orphan DVs (#13042) | ✅ 已完成 | [2142_a44ec8d1c](commits/2142_a44ec8d1c/analysis.md) |
| 2143 | `1b3021630` | 2025-05-19 08:17:58 +0200 | Russell Spitzer | API: Reorder as() calls in Tests (#13083) | ✅ 已完成 | [2143_1b3021630](commits/2143_1b3021630/analysis.md) |
| 2144 | `a491ce935` | 2025-05-19 08:18:16 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.31.35 to 2.31.45 (#13088) | ✅ 已完成 | [2144_a491ce935](commits/2144_a491ce935/analysis.md) |
| 2145 | `a654f3f91` | 2025-05-19 08:19:16 +0200 | dependabot[bot] | Build: Bump nessie from 0.103.6 to 0.104.1 (#13025) | ✅ 已完成 | [2145_a654f3f91](commits/2145_a654f3f91/analysis.md) |
| 2146 | `4ca3f1083` | 2025-05-19 13:25:31 +0200 | GuoYu | Flink: Backport fix watermark no pass in TaskResultAggregator to Flink 1.19 and 1.20 (#13085) | ✅ 已完成 | [2146_4ca3f1083](commits/2146_4ca3f1083/analysis.md) |
| 2147 | `a7f3dc79a` | 2025-05-19 12:01:40 -0500 | hsingh574 | Core: Copy ByteBuffers when Copying GenericRecord(#12855) | ✅ 已完成 | [2147_a7f3dc79a](commits/2147_a7f3dc79a/analysis.md) |
| 2148 | `dfa5a9794` | 2025-05-19 21:58:15 -0600 | Tom Tanaka | Spark 4: Migrate tests implementing SparkCatalogTestBase to Junit5 (#13096) | ✅ 已完成 | [2148_dfa5a9794](commits/2148_dfa5a9794/analysis.md) |
| 2149 | `09a531740` | 2025-05-20 14:38:17 +0200 | B Vadlamani | Spark, Flink: Backport add max files rewrite option for RewriteAction (#13082) | ✅ 已完成 | [2149_09a531740](commits/2149_09a531740/analysis.md) |
| 2150 | `8a38f5ae2` | 2025-05-20 13:44:06 -0700 | Wing Yew Poon | Spark 3.5: Structured Streaming read limit support follow-up (#12260) | ✅ 已完成 | [2150_8a38f5ae2](commits/2150_8a38f5ae2/analysis.md) |
| 2151 | `018710edd` | 2025-05-21 09:18:13 +0200 | Leon Lin | Parquet: Fix redundant type conversion in parquet schema (#13114) | ✅ 已完成 | [2151_018710edd](commits/2151_018710edd/analysis.md) |
| 2152 | `6550486f0` | 2025-05-21 17:27:38 +0200 | Wing Yew Poon | Spark 3.4: Structured Streaming read limit support follow-up (#13099) | ✅ 已完成 | [2152_6550486f0](commits/2152_6550486f0/analysis.md) |
| 2153 | `e139dbc7e` | 2025-05-21 09:08:03 -0700 | Steven Wu | Build: increase gradle jvm heap size from 1 GB to 1.5 GB. | ✅ 已完成 | [2153_e139dbc7e](commits/2153_e139dbc7e/analysis.md) |
| 2154 | `1911c94ea` | 2025-05-21 09:39:48 -0700 | Wing Yew Poon | Spark 4.0: Structured Streaming read limit support follow-up (#13095) | ✅ 已完成 | [2154_1911c94ea](commits/2154_1911c94ea/analysis.md) |
| 2155 | `91dff9886` | 2025-05-22 13:39:47 +0200 | Dhruv Pratap | Parquet: Log corrupted parquet filenames to trace bad nodes that may have written them. (#13108) | ✅ 已完成 | [2155_91dff9886](commits/2155_91dff9886/analysis.md) |
| 2156 | `14493784a` | 2025-05-22 10:11:45 -0600 | Eduard Tudenhoefner | AWS: Support multiple storage credential prefixes (#12799) | ✅ 已完成 | [2156_14493784a](commits/2156_14493784a/analysis.md) |
| 2157 | `0379c434e` | 2025-05-23 10:40:23 +0200 | emkornfield | Spec: Add details on GZIP compressed metadata files (#12598) | ✅ 已完成 | [2157_0379c434e](commits/2157_0379c434e/analysis.md) |
| 2158 | `d32ba358c` | 2025-05-23 10:58:27 +0200 | JeonDaehong | Flink: Remove the MiniClusterWithClientResource dependency (#13021) | ✅ 已完成 | [2158_d32ba358c](commits/2158_d32ba358c/analysis.md) |
| 2159 | `b47f207d1` | 2025-05-23 14:54:18 +0200 | Ajantha Bhat | Docs: Update spec version for write modes (#13138) | ✅ 已完成 | [2159_b47f207d1](commits/2159_b47f207d1/analysis.md) |
| 2160 | `793f6cd06` | 2025-05-23 14:56:36 +0200 | Devin Smith | AWS: Skip cleanup of analytics accelerator when disabled (#13134) | ✅ 已完成 | [2160_793f6cd06](commits/2160_793f6cd06/analysis.md) |
| 2161 | `419284989` | 2025-05-23 15:43:34 +0200 | GuoYu | Flink: Fix npe in TaskResultAggregator when job recovery (#13086) | ✅ 已完成 | [2161_419284989](commits/2161_419284989/analysis.md) |
| 2162 | `b504f9c51` | 2025-05-23 17:06:06 -0500 | Huaxin Gao | Spark 4.0: Bump to Official Release | ✅ 已完成 | [2162_b504f9c51](commits/2162_b504f9c51/analysis.md) |
| 2163 | `e95d778c7` | 2025-05-26 09:18:36 +0200 | GuoYu | Flink: Backport fix npe in TaskResultAggregator when job recovery (#13140) | ✅ 已完成 | [2163_e95d778c7](commits/2163_e95d778c7/analysis.md) |
| 2164 | `dd7fa01bd` | 2025-05-26 13:10:40 +0200 | Sanjay Marreddi | AWS: Close the S3SeekableInputStreamFactory before removing from cache (#12891) | ✅ 已完成 | [2164_dd7fa01bd](commits/2164_dd7fa01bd/analysis.md) |
| 2165 | `5a096b764` | 2025-05-26 16:56:15 +0200 | Víctor Ramírez | Docs: Add Tinybird to the list of vendors and blog posts (#13128) | ✅ 已完成 | [2165_5a096b764](commits/2165_5a096b764/analysis.md) |
| 2166 | `69b0bb05a` | 2025-05-27 14:25:45 +0200 | JB Onofré | Build: Upgrade to Gradle 8.14.1 (#13149) | ✅ 已完成 | [2166_69b0bb05a](commits/2166_69b0bb05a/analysis.md) |
| 2167 | `d8d9753eb` | 2025-05-27 14:26:12 +0200 | dependabot[bot] | Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#13144) | ✅ 已完成 | [2167_d8d9753eb](commits/2167_d8d9753eb/analysis.md) |
| 2168 | `0e9b364fe` | 2025-05-27 14:26:35 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.31.45 to 2.31.50 (#13148) | ✅ 已完成 | [2168_0e9b364fe](commits/2168_0e9b364fe/analysis.md) |
| 2169 | `2b3559b45` | 2025-05-27 14:27:42 +0200 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#13147) | ✅ 已完成 | [2169_2b3559b45](commits/2169_2b3559b45/analysis.md) |
| 2170 | `d46acf914` | 2025-05-27 14:28:08 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.60.0 to 26.61.0 (#13146) | ✅ 已完成 | [2170_d46acf914](commits/2170_d46acf914/analysis.md) |
| 2171 | `52a72dea8` | 2025-05-27 11:33:01 -0700 | Steven Zhen Wu | Core, AWS, Spark: use loopback address explicitly in tests to work in more restrictive firewall env on dev machines (#13101) | ✅ 已完成 | [2171_52a72dea8](commits/2171_52a72dea8/analysis.md) |
| 2172 | `cab0decbb` | 2025-05-27 14:50:22 -0700 | Prashant Singh | REST Spec: Add row lineage fields (#13010) | ✅ 已完成 | [2172_cab0decbb](commits/2172_cab0decbb/analysis.md) |
| 2173 | `c3eaaf5c8` | 2025-05-28 14:37:13 +0200 | JeonDaehong | Flink: Backport remove the MiniClusterWithClientResource dependency (#13165) | ✅ 已完成 | [2173_c3eaaf5c8](commits/2173_c3eaaf5c8/analysis.md) |
| 2174 | `6be7cd76e` | 2025-05-28 16:03:54 +0200 | Manu Zhang | Docs: Fix Flink upsert doc on equality fields requirement (#13127) | ✅ 已完成 | [2174_6be7cd76e](commits/2174_6be7cd76e/analysis.md) |
| 2175 | `e2de07bac` | 2025-05-28 09:35:14 -0700 | Wing Yew Poon | Spark 3.4: streaming-skip-overwrite-snapshots fix (#13168) | ✅ 已完成 | [2175_e2de07bac](commits/2175_e2de07bac/analysis.md) |
| 2176 | `8eb476f4b` | 2025-05-28 15:18:07 -0500 | Russell Spitzer | Infra: Set Latest Release to  1.9.1 (#13177) | ✅ 已完成 | [2176_8eb476f4b](commits/2176_8eb476f4b/analysis.md) |
| 2177 | `4e0207ad1` | 2025-05-28 15:22:11 -0500 | Russell Spitzer | Site: Updates for 1.9.1 Release (#13176) | ✅ 已完成 | [2177_4e0207ad1](commits/2177_4e0207ad1/analysis.md) |
| 2178 | `2ada62250` | 2025-05-29 13:20:49 +0200 | Ajantha Bhat | Core: Fix incremental compute of partition stats for various edge cases (#13163) | ✅ 已完成 | [2178_2ada62250](commits/2178_2ada62250/analysis.md) |
| 2179 | `4927610d1` | 2025-05-29 08:33:27 -0600 | Prashant Singh | Core: Avoid table corruption from 409 on self conflicts after 5xx retries by throwing CommitStateUnknown (#12818) | ✅ 已完成 | [2179_4927610d1](commits/2179_4927610d1/analysis.md) |
| 2180 | `0ae939407` | 2025-05-29 10:22:37 -0500 | Ajantha Bhat | Spec: Mark version 3 as completed (#13175) | ✅ 已完成 | [2180_0ae939407](commits/2180_0ae939407/analysis.md) |
| 2181 | `0f3257c59` | 2025-05-29 19:34:08 +0200 | dependabot[bot] | Build: Bump kafka from 3.9.0 to 3.9.1 (#13145) | ✅ 已完成 | [2181_0f3257c59](commits/2181_0f3257c59/analysis.md) |
| 2182 | `ff4799afc` | 2025-05-29 12:48:50 -0500 | Aihua Xu | API: Add Variant toString for time/nano timestamps type (#13151) | ✅ 已完成 | [2182_ff4799afc](commits/2182_ff4799afc/analysis.md) |
| 2183 | `a8b42450d` | 2025-05-29 12:51:14 -0500 | Devin Smith | AWS: Configure Default s3Async credentials the same as s3 (#13132) | ✅ 已完成 | [2183_a8b42450d](commits/2183_a8b42450d/analysis.md) |
| 2184 | `3469cf30f` | 2025-05-29 13:24:26 -0500 | Ben Hannel | API: Make PartitionSpec.Builder::identity public (#12975) | ✅ 已完成 | [2184_3469cf30f](commits/2184_3469cf30f/analysis.md) |
| 2185 | `8192a6cd8` | 2025-05-30 15:38:58 -0700 | drexler-sky | Spark 3.5: Update to Spark 3.5.6 (#13142) | ✅ 已完成 | [2185_8192a6cd8](commits/2185_8192a6cd8/analysis.md) |
| 2186 | `a5a0b8af8` | 2025-05-31 09:56:29 -0700 | Steven Zhen Wu | Spark: clean up obsoleted/unused writer classes (#13193) | ✅ 已完成 | [2186_a5a0b8af8](commits/2186_a5a0b8af8/analysis.md) |
| 2187 | `2cdff3669` | 2025-06-01 14:25:36 -0400 | Matt Topol | chore: Update status.md (#13182) | ✅ 已完成 | [2187_2cdff3669](commits/2187_2cdff3669/analysis.md) |
| 2188 | `ee82cdf28` | 2025-06-02 10:51:48 +0200 | Bhargavkonidena | Docs: Reorganize Spark Time Travel Doc (#13113) | ✅ 已完成 | [2188_ee82cdf28](commits/2188_ee82cdf28/analysis.md) |
| 2189 | `4218554f3` | 2025-06-02 14:07:54 +0200 | Jiajia Li | AWS: update test cases to verify credentials for the prefixed S3 client (#13118) | ✅ 已完成 | [2189_4218554f3](commits/2189_4218554f3/analysis.md) |
| 2190 | `a4b2a0dab` | 2025-06-02 14:55:29 +0200 | Russell Spitzer | API, Core: Rename RowDelta deleteFile() to removeRows() (#13184) | ✅ 已完成 | [2190_a4b2a0dab](commits/2190_a4b2a0dab/analysis.md) |
| 2191 | `08bed1303` | 2025-06-02 20:41:10 +0200 | dependabot[bot] | Build: Bump testcontainers from 1.21.0 to 1.21.1 (#13199) | ✅ 已完成 | [2191_08bed1303](commits/2191_08bed1303/analysis.md) |
| 2192 | `7537c3c3a` | 2025-06-02 22:01:40 +0200 | dependabot[bot] | Build: Bump guava from 33.4.7-jre to 33.4.8-jre (#12851) | ✅ 已完成 | [2192_7537c3c3a](commits/2192_7537c3c3a/analysis.md) |
| 2193 | `9fa50f3b8` | 2025-06-02 22:51:03 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#12852) | ✅ 已完成 | [2193_9fa50f3b8](commits/2193_9fa50f3b8/analysis.md) |
| 2194 | `1d8b91a34` | 2025-06-03 07:45:57 +0200 | Prashant Singh | Build, Core: Move assertions to AssertJ / Fix checkstyle rules (#13213) | ✅ 已完成 | [2194_1d8b91a34](commits/2194_1d8b91a34/analysis.md) |
| 2195 | `73758ed9a` | 2025-06-03 07:47:34 +0200 | Ning Kang | Core: Catch IAE when decoding JWT (#13192) | ✅ 已完成 | [2195_73758ed9a](commits/2195_73758ed9a/analysis.md) |
| 2196 | `e5b97d344` | 2025-06-03 07:49:18 +0200 | Jayanth Kumar M J | Hive: Throw NSNE when listing a non-existing namespace (#13130) | ✅ 已完成 | [2196_e5b97d344](commits/2196_e5b97d344/analysis.md) |
| 2197 | `ee1dea0a4` | 2025-06-03 07:52:26 +0200 | Elphas Toringepi | Docs: Add column descriptions for entries metadata table (#13104) | ✅ 已完成 | [2197_ee1dea0a4](commits/2197_ee1dea0a4/analysis.md) |
| 2198 | `f9cc62eb0` | 2025-06-03 13:45:06 +0200 | Wing Yew Poon | Arrow: Reduce code duplication in VectorizedParquetDefinitionLevelReader (#11661) | ✅ 已完成 | [2198_f9cc62eb0](commits/2198_f9cc62eb0/analysis.md) |
| 2199 | `a1a9ba123` | 2025-06-03 15:55:40 +0200 | Tom Tanaka | Docs: Add custom table location description for Flink (#13214) | ✅ 已完成 | [2199_a1a9ba123](commits/2199_a1a9ba123/analysis.md) |
| 2200 | `8e803312c` | 2025-06-03 17:01:11 +0200 | Ziyan | Spark: Remove dependency on hadoop's filesystem class from remove orphan files (#12254) | ✅ 已完成 | [2200_8e803312c](commits/2200_8e803312c/analysis.md) |
| 2201 | `959351677` | 2025-06-03 15:00:49 -0400 | Elphas Toringepi | Core: Improve pagination logic to handle null pageToken (#13129) | ✅ 已完成 | [2201_959351677](commits/2201_959351677/analysis.md) |
| 2202 | `4755b76d2` | 2025-06-03 21:48:42 +0200 | dependabot[bot] | Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#12684) | ✅ 已完成 | [2202_4755b76d2](commits/2202_4755b76d2/analysis.md) |
| 2203 | `931865eca` | 2025-06-03 13:32:46 -0700 | Rodrigo | Flink: port range distribution to v2 iceberg sink (#12071) | ✅ 已完成 | [2203_931865eca](commits/2203_931865eca/analysis.md) |
| 2204 | `126052267` | 2025-06-04 07:35:53 +0200 | Maximilian Michels | Flink: Dynamic Iceberg Sink: Add table update code for schema comparison and evolution  (#13032) | ✅ 已完成 | [2204_126052267](commits/2204_126052267/analysis.md) |
| 2205 | `b38573dcf` | 2025-06-04 10:49:18 +0200 | Eduard Tudenhoefner | Core: Add basic classes for writing table format-version 4 (#13123) | ✅ 已完成 | [2205_b38573dcf](commits/2205_b38573dcf/analysis.md) |
| 2206 | `5b62fecde` | 2025-06-04 12:08:52 +0200 | Manu Zhang | Spark: Fix flaky testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup (#13208) | ✅ 已完成 | [2206_5b62fecde](commits/2206_5b62fecde/analysis.md) |
| 2207 | `73802f63c` | 2025-06-04 14:07:16 +0200 | Rodrigo | Flink: Backport IcerbegSink RANGE distribution to Flink 1.19 and 2.0 (#13228) | ✅ 已完成 | [2207_73802f63c](commits/2207_73802f63c/analysis.md) |
| 2208 | `b8cc8eb84` | 2025-06-04 14:52:29 +0200 | Maximilian Michels | Flink: Dynamic Iceberg Sink: Add dynamic writer and committer (#13080) | ✅ 已完成 | [2208_b8cc8eb84](commits/2208_b8cc8eb84/analysis.md) |
| 2209 | `b3adeb12e` | 2025-06-04 11:17:07 -0700 | Bryan Keller | [REST] Add option to configure TLS settings in REST client (#13190) | ✅ 已完成 | [2209_b3adeb12e](commits/2209_b3adeb12e/analysis.md) |
| 2210 | `3e478d2fe` | 2025-06-05 08:23:37 +0200 | Wing Yew Poon | Docs: Document DataFrame API support for MERGE INTO in Spark 4.0 (#13231) | ✅ 已完成 | [2210_3e478d2fe](commits/2210_3e478d2fe/analysis.md) |
| 2211 | `809e4d8a7` | 2025-06-05 09:12:53 +0200 | Lea Fester | Docs: Add Redpanda to vendor-related documentation (#13242) | ✅ 已完成 | [2211_809e4d8a7](commits/2211_809e4d8a7/analysis.md) |
| 2212 | `4079a4fbb` | 2025-06-05 11:57:48 +0200 | GuoYu | Flink: Support compact in iceberg sink v2 (#12979) | ✅ 已完成 | [2212_4079a4fbb](commits/2212_4079a4fbb/analysis.md) |
| 2213 | `1996ff9f8` | 2025-06-05 14:46:39 +0200 | Maximilian Michels | Flink: Backport add DynamicRecord / DynamicRecordInternal / DynamicRecordInternalSerializer to Flink 1.19 / 1.20 (#13246) | ✅ 已完成 | [2213_1996ff9f8](commits/2213_1996ff9f8/analysis.md) |
| 2214 | `6c9e64196` | 2025-06-05 16:46:25 +0200 | Maximilian Michels | Flink: Backport Dynamic Iceberg Sink: Add table update code for schema comparison and evolution to Flink 1.19 / 1.20 (#13247) | ✅ 已完成 | [2214_6c9e64196](commits/2214_6c9e64196/analysis.md) |
| 2215 | `e631db69a` | 2025-06-05 16:47:18 +0200 | Maximilian Michels | Flink: Backport Dynamic Iceberg Sink: Add dynamic writer and committer to Flink 1.19 / 1.20 (#13248) | ✅ 已完成 | [2215_e631db69a](commits/2215_e631db69a/analysis.md) |
| 2216 | `d3ebea5ad` | 2025-06-05 12:26:59 -0600 | Eduard Tudenhoefner | AWS, GCS: Fix issue with Kryo and empty immutable collections for storage credential (#13216) | ✅ 已完成 | [2216_d3ebea5ad](commits/2216_d3ebea5ad/analysis.md) |
| 2217 | `73b179c3c` | 2025-06-05 16:00:36 -0700 | Amogh Jahagirdar | Spark 3.5, Arrow: Support for Row lineage when using the Parquet Vectorized reader (#12928) | ✅ 已完成 | [2217_73b179c3c](commits/2217_73b179c3c/analysis.md) |
| 2218 | `78156e7f4` | 2025-06-05 18:44:49 -0700 | Wing Yew Poon | Spark 4.0: Add a test for DataFrame API support for MERGE INTO (#13230) | ✅ 已完成 | [2218_78156e7f4](commits/2218_78156e7f4/analysis.md) |
| 2219 | `b4f5da906` | 2025-06-06 12:52:34 +0200 | GuoYu | Flink: Backport support compact in sink v2 to 1.19 and 2.0 (#13250) | ✅ 已完成 | [2219_b4f5da906](commits/2219_b4f5da906/analysis.md) |
| 2220 | `7b510ad48` | 2025-06-06 14:48:47 +0200 | Nándor Kollár | API, AWS, Azure, Core, GCP: Use parametrized tests for Kryo/Java serialization verification (#13244) | ✅ 已完成 | [2220_7b510ad48](commits/2220_7b510ad48/analysis.md) |
| 2221 | `d59ea1ea8` | 2025-06-06 19:25:50 -0400 | Om Kenge | Docs: Remove obsolete version attribute in quick start docker-compose.yml (#13139) | ✅ 已完成 | [2221_d59ea1ea8](commits/2221_d59ea1ea8/analysis.md) |
| 2222 | `e2da23c0c` | 2025-06-06 19:31:12 -0400 | Jerry Chen | Docs: use latest minio client command configuration parameters (#13221) | ✅ 已完成 | [2222_e2da23c0c](commits/2222_e2da23c0c/analysis.md) |
| 2223 | `5b40f9f88` | 2025-06-07 09:19:46 +0200 | JB Onofré | Build: Upgrade to Gradle 8.14.2 (#13259) | ✅ 已完成 | [2223_5b40f9f88](commits/2223_5b40f9f88/analysis.md) |
| 2224 | `aae14bee6` | 2025-06-08 21:51:51 +0200 | dependabot[bot] | Build: Bump openapi-spec-validator from 0.7.1 to 0.7.2 (#13275) | ✅ 已完成 | [2224_aae14bee6](commits/2224_aae14bee6/analysis.md) |
| 2225 | `d1027fc64` | 2025-06-08 23:35:41 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.30.1 to 0.30.2 (#13274) | ✅ 已完成 | [2225_d1027fc64](commits/2225_d1027fc64/analysis.md) |
| 2226 | `77c2f4484` | 2025-06-10 11:00:51 +0200 | GuoYu | Flink, Spark: Backport ThreadPools introduce newExitingWorkerPool and newFixedThreadPool for clearer semantics to Flink 1.19 and Spark 3.4(#13265) | ✅ 已完成 | [2226_77c2f4484](commits/2226_77c2f4484/analysis.md) |
| 2227 | `7b8bd29ce` | 2025-06-10 11:07:18 +0200 | Ziyan | Spark: Port prefix listing option in remove orphan files to Spark 3.4 and Spark 4.0 (#13264) | ✅ 已完成 | [2227_7b8bd29ce](commits/2227_7b8bd29ce/analysis.md) |
| 2228 | `76972ef77` | 2025-06-11 17:52:56 +0200 | Maximilian Michels | Flink: Dynamic Iceberg Sink: Add HashKeyGenerator / RowDataEvolver / TableUpdateOperator (#13277) | ✅ 已完成 | [2228_76972ef77](commits/2228_76972ef77/analysis.md) |
| 2229 | `f5eab59c9` | 2025-06-11 10:15:48 -0700 | hsiang-c | View Spec: Fix engine-version key in the JSON example (#13292) | ✅ 已完成 | [2229_f5eab59c9](commits/2229_f5eab59c9/analysis.md) |
| 2230 | `ee93ffb59` | 2025-06-11 20:09:06 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.1.Final to 4.2.2.Final (#13273) | ✅ 已完成 | [2230_ee93ffb59](commits/2230_ee93ffb59/analysis.md) |
| 2231 | `e141ff73b` | 2025-06-12 12:55:55 +0200 | Maximilian Michels | Flink: Backport dynamic Iceberg Sink: Add HashKeyGenerator / RowDataEvolver / TableUpdateOperator to Flink 1.19 / 1.20 (#13303) | ✅ 已完成 | [2231_e141ff73b](commits/2231_e141ff73b/analysis.md) |
| 2232 | `a8d111eaa` | 2025-06-12 11:39:51 -0400 | Raman Yelianevich | Docs: Fix list rendering and typos (#13214) (#13267) | ✅ 已完成 | [2232_a8d111eaa](commits/2232_a8d111eaa/analysis.md) |
| 2233 | `26d5c1c4f` | 2025-06-12 10:52:00 -0700 | Kyle Lin | Docs: Fix broken links in Flink Configuration documentation (#13288) | ✅ 已完成 | [2233_26d5c1c4f](commits/2233_26d5c1c4f/analysis.md) |
| 2234 | `62d9ff5d0` | 2025-06-12 20:50:07 -0700 | Hongyue/Steve Zhang | Spark: RewriteTablePath should filter returned content files by snapshotId (#12885) | ✅ 已完成 | [2234_62d9ff5d0](commits/2234_62d9ff5d0/analysis.md) |
| 2235 | `17f9a9fd2` | 2025-06-13 12:08:33 -0700 | Prashant Singh | REST: Add property for configuring user agent in http client (#13234) | ✅ 已完成 | [2235_17f9a9fd2](commits/2235_17f9a9fd2/analysis.md) |
| 2236 | `a0d9f0672` | 2025-06-15 08:36:00 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.31.50 to 2.31.63 (#13316) | ✅ 已完成 | [2236_a0d9f0672](commits/2236_a0d9f0672/analysis.md) |
| 2237 | `b196aeec0` | 2025-06-15 09:11:27 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.30.2 to 0.31.0 (#13319) | ✅ 已完成 | [2237_b196aeec0](commits/2237_b196aeec0/analysis.md) |
| 2238 | `24cb139b1` | 2025-06-15 09:12:13 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.49.1.0 to 3.50.1.0 (#13318) | ✅ 已完成 | [2238_24cb139b1](commits/2238_24cb139b1/analysis.md) |
| 2239 | `077af5fec` | 2025-06-15 09:14:38 +0200 | dependabot[bot] | Build: Bump jackson-bom from 2.19.0 to 2.19.1 (#13315) | ✅ 已完成 | [2239_077af5fec](commits/2239_077af5fec/analysis.md) |
| 2240 | `d6b52149d` | 2025-06-16 09:25:36 +0200 | dependabot[bot] | Build: Bump io.delta:delta-spark_2.12 from 3.3.1 to 3.3.2 (#13205) | ✅ 已完成 | [2240_d6b52149d](commits/2240_d6b52149d/analysis.md) |
| 2241 | `d0b90c540` | 2025-06-16 09:30:08 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.61.0 to 26.62.0 (#13317) | ✅ 已完成 | [2241_d0b90c540](commits/2241_d0b90c540/analysis.md) |
| 2242 | `1456c69aa` | 2025-06-16 10:35:02 +0200 | dependabot[bot] | Build: Bump io.delta:delta-standalone_2.12 from 3.3.1 to 3.3.2 (#13202) | ✅ 已完成 | [2242_1456c69aa](commits/2242_1456c69aa/analysis.md) |
| 2243 | `7ceb7402a` | 2025-06-16 11:18:30 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.24.0 to 3.24.2 (#13200) | ✅ 已完成 | [2243_7ceb7402a](commits/2243_7ceb7402a/analysis.md) |
| 2244 | `3ed818438` | 2025-06-16 13:41:03 -0700 | Rodrigo | Flink: If IcebergSink writeParallelism is not specified, defaults to the input source parallelism (#13260) | ✅ 已完成 | [2244_3ed818438](commits/2244_3ed818438/analysis.md) |
| 2245 | `67f550e7e` | 2025-06-16 23:50:31 +0200 | Zach Schumacher | docs: Fix typo in `spark-procedures.md` (#13325) | ✅ 已完成 | [2245_67f550e7e](commits/2245_67f550e7e/analysis.md) |
| 2246 | `7dc4a66b4` | 2025-06-16 14:53:47 -0700 | Rodrigo | Flink: Backports #13260 to Flink 1.19 and 1.20 (#13326) | ✅ 已完成 | [2246_7dc4a66b4](commits/2246_7dc4a66b4/analysis.md) |
| 2247 | `68651a349` | 2025-06-17 17:20:15 +0200 | Maximilian Michels | Flink: Dynamic Iceberg Sink: Add sink / core processing logic / benchmarking (#13304) | ✅ 已完成 | [2247_68651a349](commits/2247_68651a349/analysis.md) |
| 2248 | `0eb9728a4` | 2025-06-17 11:06:14 -0700 | ChaladiMohanVamsi | AWS, GCP: Fix double-checked-locking pattern in S3FileIO, GCSFileIO. (#13276) | ✅ 已完成 | [2248_0eb9728a4](commits/2248_0eb9728a4/analysis.md) |
| 2249 | `5b50afe8f` | 2025-06-17 16:25:58 -0500 | Prashant Singh | Core, REST: Add context aware response parsing (#13191) | ✅ 已完成 | [2249_5b50afe8f](commits/2249_5b50afe8f/analysis.md) |
| 2250 | `81b74fc0b` | 2025-06-18 16:06:19 +0200 | Maximilian Michels | Flink: Backport dynamic Iceberg Sink: Add sink / core processing logic / benchmarking to Flink 1.19 / 1.20 (#13341) | ✅ 已完成 | [2250_81b74fc0b](commits/2250_81b74fc0b/analysis.md) |
| 2251 | `cea7f9c82` | 2025-06-18 16:10:23 +0200 | Maximilian Michels | Flink: Dynamic Iceberg Sink: Rename method in DynamicRecordGenerator (#13342) | ✅ 已完成 | [2251_cea7f9c82](commits/2251_cea7f9c82/analysis.md) |
| 2252 | `fa162de01` | 2025-06-18 20:02:11 +0200 | Denys Kuzmenko | Core: PartitionsTable#partitions returns incomplete list in case of partition evolution and NULL partition values (#12528) | ✅ 已完成 | [2252_fa162de01](commits/2252_fa162de01/analysis.md) |
| 2253 | `e47351980` | 2025-06-18 22:03:29 +0200 | Maximilian Michels | Flink: Backport dynamic Iceberg Sink: Rename method in DynamicRecordGenerator to Flink 1.19 / 1.20 (#13346) | ✅ 已完成 | [2253_e47351980](commits/2253_e47351980/analysis.md) |
| 2254 | `cdd2b5bb3` | 2025-06-18 22:20:57 +0200 | Rui Li | Core: Clean expired metadata even if there is no snapshot to expire (#13322) | ✅ 已完成 | [2254_cdd2b5bb3](commits/2254_cdd2b5bb3/analysis.md) |
| 2255 | `e4dc658c1` | 2025-06-18 20:32:03 -0700 | jingtao-firebolt | Docs: Add Firebolt to vendor-related documentation (#13350) | ✅ 已完成 | [2255_e4dc658c1](commits/2255_e4dc658c1/analysis.md) |
| 2256 | `feeb045a7` | 2025-06-18 21:14:39 -0700 | Drew Gallardo | Spark 3.4: Backport #12836 for row lineage support in spark parquet reader (#13353) | ✅ 已完成 | [2256_feeb045a7](commits/2256_feeb045a7/analysis.md) |
| 2257 | `40c0a73c6` | 2025-06-19 08:12:07 -0700 | Talat UYARER | GCP: Add Google Authentication Support (#13212) | ✅ 已完成 | [2257_40c0a73c6](commits/2257_40c0a73c6/analysis.md) |
| 2258 | `c41fd6457` | 2025-06-19 16:10:57 -0700 | Himadri Pal | spark 4.0: SPJ: add bucket reducer using gcd (#13167) | ✅ 已完成 | [2258_c41fd6457](commits/2258_c41fd6457/analysis.md) |
| 2259 | `56d81e72f` | 2025-06-19 18:51:38 -0700 | Drew Gallardo | Spark 3.4: Backport UPDATE/MERGE logic for row lineage (#13344) | ✅ 已完成 | [2259_56d81e72f](commits/2259_56d81e72f/analysis.md) |
| 2260 | `e5541de0f` | 2025-06-19 21:02:11 -0700 | Fokko Driesprong | Azure: Bump `AzuriteContainer` to 3.34.0 (#13321) | ✅ 已完成 | [2260_e5541de0f](commits/2260_e5541de0f/analysis.md) |
| 2261 | `e86a6b9b5` | 2025-06-20 17:41:33 +0200 | Manu Zhang | Docs: Fix description of min-input-files option of Spark rewrite_data_files procedure (#13355) | ✅ 已完成 | [2261_e86a6b9b5](commits/2261_e86a6b9b5/analysis.md) |
| 2262 | `c07ba83f1` | 2025-06-22 11:45:02 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.31.0 to 0.31.1 (#13362) | ✅ 已完成 | [2262_c07ba83f1](commits/2262_c07ba83f1/analysis.md) |
| 2263 | `444fb9518` | 2025-06-22 11:45:21 +0200 | dependabot[bot] | Build: Bump testcontainers from 1.21.1 to 1.21.2 (#13363) | ✅ 已完成 | [2263_444fb9518](commits/2263_444fb9518/analysis.md) |
| 2264 | `6e432fcb2` | 2025-06-23 11:55:05 +0200 | GuoYu | Flink: Revise the display of the task name in TableMaintenance to show the specific task name. (#13024) | ✅ 已完成 | [2264_6e432fcb2](commits/2264_6e432fcb2/analysis.md) |
| 2265 | `d317ec40b` | 2025-06-24 11:00:43 +0200 | GuoYu | Flink: Backport revise the display of the task name in TableMaintenance to show the specific task name to Flink 1.20, 1.19 (#13372) | ✅ 已完成 | [2265_d317ec40b](commits/2265_d317ec40b/analysis.md) |
| 2266 | `b7154119f` | 2025-06-24 11:46:23 -0700 | Anurag Mantripragada | Spark-3.5, 4.0: Add unit tests for ColumnarBatchUtil (#12275) | ✅ 已完成 | [2266_b7154119f](commits/2266_b7154119f/analysis.md) |
| 2267 | `bcb68800e` | 2025-06-25 08:47:26 +0200 | Ajantha Bhat | Core: Fix filed ids of partition stats file (#13329) | ✅ 已完成 | [2267_bcb68800e](commits/2267_bcb68800e/analysis.md) |
| 2268 | `522de0fe8` | 2025-06-25 10:34:09 +0200 | Liam Bao | Flink: Migrate Flink `TableSchema` to `Schema`/`ResolvedSchema` (#13072) | ✅ 已完成 | [2268_522de0fe8](commits/2268_522de0fe8/analysis.md) |
| 2269 | `460736c18` | 2025-06-25 12:15:19 +0200 | David Phillips | Build: Remove JSpecify annotations from bundled-guava (#13379) | ✅ 已完成 | [2269_460736c18](commits/2269_460736c18/analysis.md) |
| 2270 | `8601bda6d` | 2025-06-25 12:28:44 +0200 | Cheng Pan | Build: Use Java 17 to publish snapshot to Maven (#13369) | ✅ 已完成 | [2270_8601bda6d](commits/2270_8601bda6d/analysis.md) |
| 2271 | `b2e564405` | 2025-06-25 15:42:06 +0200 | Hongze Zhang | Build: Require JDK 17 / 21 for Spark 4.0 support (#13381) | ✅ 已完成 | [2271_b2e564405](commits/2271_b2e564405/analysis.md) |
| 2272 | `ff5d1a1bc` | 2025-06-25 15:45:03 +0200 | dependabot[bot] | Build: Bump calcite from 1.39.0 to 1.40.0 (#13203) | ✅ 已完成 | [2272_ff5d1a1bc](commits/2272_ff5d1a1bc/analysis.md) |
| 2273 | `0dd3c2b63` | 2025-06-25 16:58:47 +0200 | Eduard Tudenhoefner | Build: Run CI actions with JDK 17 (#13385) | ✅ 已完成 | [2273_0dd3c2b63](commits/2273_0dd3c2b63/analysis.md) |
| 2274 | `4278e91dd` | 2025-06-25 13:49:09 -0400 | Eduard Tudenhoefner | Core: Fix API breakage introduced by #13191 (#13386) | ✅ 已完成 | [2274_4278e91dd](commits/2274_4278e91dd/analysis.md) |
| 2275 | `4213a5014` | 2025-06-25 14:19:42 -0500 | Eric Maynard | Arrow: Refactor APIs for Parquet Reading with V2 Encodings (#13290) | ✅ 已完成 | [2275_4213a5014](commits/2275_4213a5014/analysis.md) |
| 2276 | `5845e1e02` | 2025-06-26 09:47:30 +0200 | Alexandre Dutra | Core: Properly close resources when catalog initialization fails (#13384) | ✅ 已完成 | [2276_5845e1e02](commits/2276_5845e1e02/analysis.md) |
| 2277 | `dec1aa5cf` | 2025-06-26 15:54:44 +0200 | Liam Bao | Flink: Backports TableSchema migration to Flink 1.19 and 1.20 (#13392) | ✅ 已完成 | [2277_dec1aa5cf](commits/2277_dec1aa5cf/analysis.md) |
| 2278 | `7443e54bb` | 2025-06-26 21:27:20 +0200 | aiborodin | Flink: Dynamic Iceberg Sink: Optimise RowData evolution (#13340) | ✅ 已完成 | [2278_7443e54bb](commits/2278_7443e54bb/analysis.md) |
| 2279 | `92d931b08` | 2025-06-27 11:43:38 +0200 | Yu-Chuan Hung | Build: Fix errorprone warnings (#13217) | ✅ 已完成 | [2279_92d931b08](commits/2279_92d931b08/analysis.md) |
| 2280 | `fce069f17` | 2025-06-27 11:09:53 -0400 | Amogh Jahagirdar | Spark 3.5: Fix row lineage inheritance for distributed planning (#13061) | ✅ 已完成 | [2280_fce069f17](commits/2280_fce069f17/analysis.md) |
| 2281 | `c151c2dfa` | 2025-06-27 18:04:47 +0200 | aiborodin | Flink: Backport optimised RowData evolution to Flink 1.19 / 1.20 (#13401) | ✅ 已完成 | [2281_c151c2dfa](commits/2281_c151c2dfa/analysis.md) |
| 2282 | `069a6128f` | 2025-06-27 18:40:16 +0200 | Elphas Toringepi | Docs: improve structure for manifest entry fields (#13333) | ✅ 已完成 | [2282_069a6128f](commits/2282_069a6128f/analysis.md) |
| 2283 | `6e8008963` | 2025-06-27 09:44:38 -0700 | Prashant Singh | Spark: Make maxRecordPerMicrobatch a soft limit (#12988) | ✅ 已完成 | [2283_6e8008963](commits/2283_6e8008963/analysis.md) |
| 2284 | `83cc7d874` | 2025-06-27 23:14:23 +0200 | Claude Warren | Fix race condition in `JdbcCatalog` (#13345) | ✅ 已完成 | [2284_83cc7d874](commits/2284_83cc7d874/analysis.md) |
| 2285 | `d4bb2a185` | 2025-06-30 07:45:12 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#13423) | ✅ 已完成 | [2285_d4bb2a185](commits/2285_d4bb2a185/analysis.md) |
| 2286 | `3c993b7fc` | 2025-06-30 07:45:55 +0200 | dependabot[bot] | Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#13422) | ✅ 已完成 | [2286_3c993b7fc](commits/2286_3c993b7fc/analysis.md) |
| 2287 | `cce5a28cf` | 2025-06-30 07:46:21 +0200 | dependabot[bot] | Build: Bump testcontainers from 1.21.2 to 1.21.3 (#13416) | ✅ 已完成 | [2287_cce5a28cf](commits/2287_cce5a28cf/analysis.md) |
| 2288 | `084aa3823` | 2025-06-30 07:46:42 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.31.1 to 0.31.2 (#13413) | ✅ 已完成 | [2288_084aa3823](commits/2288_084aa3823/analysis.md) |
| 2289 | `3f104db71` | 2025-06-30 09:32:31 +0200 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#13421) | ✅ 已完成 | [2289_3f104db71](commits/2289_3f104db71/analysis.md) |
| 2290 | `18822b73c` | 2025-06-30 10:16:24 +0200 | dependabot[bot] | Build: Bump org.immutables:value from 2.10.1 to 2.11.0 (#13420) | ✅ 已完成 | [2290_18822b73c](commits/2290_18822b73c/analysis.md) |
| 2291 | `b526e581f` | 2025-06-30 10:17:21 +0200 | dependabot[bot] | Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.6 to 8.3.7 (#13414) | ✅ 已完成 | [2291_b526e581f](commits/2291_b526e581f/analysis.md) |
| 2292 | `a69af4985` | 2025-06-30 12:11:49 +0200 | Yu-Chuan Hung | Docs: Correct typo in spec.md (#13427) | ✅ 已完成 | [2292_a69af4985](commits/2292_a69af4985/analysis.md) |
| 2293 | `9d8751d50` | 2025-06-30 11:28:30 -0500 | Devin Smith | AWS: Refactor S3FileIOProperties to use common builder interface (#13183) | ✅ 已完成 | [2293_9d8751d50](commits/2293_9d8751d50/analysis.md) |
| 2294 | `e5ba73ec4` | 2025-06-30 23:12:56 +0200 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.31 to 1.2.35 (#13201) | ✅ 已完成 | [2294_e5ba73ec4](commits/2294_e5ba73ec4/analysis.md) |
| 2295 | `ff0904c3f` | 2025-06-30 17:13:20 -0700 | Himadri Pal | spark 4.0 : SPJ : add hour to day reducer (#13166) | ✅ 已完成 | [2295_ff0904c3f](commits/2295_ff0904c3f/analysis.md) |
| 2296 | `28b90ea18` | 2025-06-30 21:20:58 -0700 | Amogh Jahagirdar | Spark 3.4: Backport #13061 fix for row lineage inheritance in distributed planning (#13436) | ✅ 已完成 | [2296_28b90ea18](commits/2296_28b90ea18/analysis.md) |
| 2297 | `dedba4568` | 2025-07-01 08:55:43 +0200 | Shubham Diwakar | GCS: Add Iceberg version to UserAgent in GCS requests (#13428) | ✅ 已完成 | [2297_dedba4568](commits/2297_dedba4568/analysis.md) |
| 2298 | `45e3ae30b` | 2025-07-01 11:51:37 +0200 | Kevin Liu | REST-Fixture: Move `sqlite` backend from memory to file (#13367) | ✅ 已完成 | [2298_45e3ae30b](commits/2298_45e3ae30b/analysis.md) |
| 2299 | `e73344323` | 2025-07-01 16:18:57 +0200 | aiborodin | Flink: Replace Caffeine maxSize cache with LRUCache (#13382) | ✅ 已完成 | [2299_e73344323](commits/2299_e73344323/analysis.md) |
| 2300 | `e64181d99` | 2025-07-01 10:42:16 -0700 | Amogh Jahagirdar | Avro: Support row lineage inheritance for planned avro reader (#13070) | ✅ 已完成 | [2300_e64181d99](commits/2300_e64181d99/analysis.md) |
| 2301 | `be577eeac` | 2025-07-01 10:44:34 -0700 | Russell Spitzer | Core: Maintain passed in ordering of files in Manifest Lists (#13411) | ✅ 已完成 | [2301_be577eeac](commits/2301_be577eeac/analysis.md) |
| 2302 | `b1c8bc589` | 2025-07-01 13:10:30 -0500 | Prashant Singh | REST: Revert #12818 and additionally stop retrying on 502/504 (#13352) | ✅ 已完成 | [2302_b1c8bc589](commits/2302_b1c8bc589/analysis.md) |
| 2303 | `8aa86db08` | 2025-07-01 13:14:34 -0700 | Amogh Jahagirdar | Spark 3.4: Backport tests from #13070 to 3.4 (#13440) | ✅ 已完成 | [2303_8aa86db08](commits/2303_8aa86db08/analysis.md) |
| 2304 | `dfecabc76` | 2025-07-01 16:55:14 -0700 | Prashant Singh | CP 12988 to Spark version (#13412) | ✅ 已完成 | [2304_dfecabc76](commits/2304_dfecabc76/analysis.md) |
| 2305 | `f24f0c093` | 2025-07-01 19:21:19 -0600 | Szehon Ho | Spark 3.5, 4.0: Prevent unnecessary failure when executing DML queries with identifier fields (#13435) | ✅ 已完成 | [2305_f24f0c093](commits/2305_f24f0c093/analysis.md) |
| 2306 | `97a1f3bba` | 2025-07-02 08:28:42 +0200 | aiborodin | Flink: Backport replace Caffeine maxSize cache with LRUCache (#13441) | ✅ 已完成 | [2306_97a1f3bba](commits/2306_97a1f3bba/analysis.md) |
| 2307 | `404c80572` | 2025-07-02 08:46:49 +0200 | Eduard Tudenhoefner | Arrow, AWS, Azure, Core, GCP, Hive, Kafka, Snowflake: Rename test classes to use Test as prefix instead of suffix (#12879) | ✅ 已完成 | [2307_404c80572](commits/2307_404c80572/analysis.md) |
| 2308 | `8134815a3` | 2025-07-02 09:02:34 -0600 | Fokko Driesprong | API, Core, Spark: Ignore partition fields that are dropped from the current-schema (#11868) | ✅ 已完成 | [2308_8134815a3](commits/2308_8134815a3/analysis.md) |
| 2309 | `5634a2b06` | 2025-07-02 18:17:49 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.31.63 to 2.31.73 (#13424) | ✅ 已完成 | [2309_5634a2b06](commits/2309_5634a2b06/analysis.md) |
| 2310 | `21e6e41d8` | 2025-07-02 18:46:30 +0200 | Yu-Chuan Hung | Build: Fix error-prone warning (#13447) | ✅ 已完成 | [2310_21e6e41d8](commits/2310_21e6e41d8/analysis.md) |
| 2311 | `b86dc9e94` | 2025-07-02 16:49:33 -0700 | Szehon Ho | Spark 3.4: Prevent unnecessary failure when executing DML queries with identifier fields (#13448) | ✅ 已完成 | [2311_b86dc9e94](commits/2311_b86dc9e94/analysis.md) |
| 2312 | `8033b32bc` | 2025-07-03 09:05:51 +0200 | Eduard Tudenhoefner | Core, Spark: Propagate orphaned delete files when rewriting data files (#13245) | ✅ 已完成 | [2312_8033b32bc](commits/2312_8033b32bc/analysis.md) |
| 2313 | `fc886142f` | 2025-07-03 11:50:36 +0200 | Claude Warren | Docs: Describe testcontainer failure workaround (#13454) | ✅ 已完成 | [2313_fc886142f](commits/2313_fc886142f/analysis.md) |
| 2314 | `4b6852751` | 2025-07-03 15:39:51 -0600 | Ajantha Bhat | Spark 3.5: Add spark action to compute partition stats (#12450) | ✅ 已完成 | [2314_4b6852751](commits/2314_4b6852751/analysis.md) |
| 2315 | `81062ae83` | 2025-07-04 08:10:22 +0200 | ccmao1130 | Docs: update daft docs (#13463) | ✅ 已完成 | [2315_81062ae83](commits/2315_81062ae83/analysis.md) |
| 2316 | `fb0af7e5f` | 2025-07-04 09:57:39 +0200 | Yuya Ebihara | S3: Add LegacyMd5Plugin to S3 client builder (#12264) | ✅ 已完成 | [2316_fb0af7e5f](commits/2316_fb0af7e5f/analysis.md) |
| 2317 | `f3b198de2` | 2025-07-05 12:14:04 +0200 | JB Onofré | Build: Upgrade to Gradle 8.14.3 (#13465) | ✅ 已完成 | [2317_f3b198de2](commits/2317_f3b198de2/analysis.md) |
| 2318 | `586c02204` | 2025-07-06 18:04:44 -0700 | Jannik Steinmann | docs: Mark Rust ADLS FileIO as implemented in the status page (#13258) | ✅ 已完成 | [2318_586c02204](commits/2318_586c02204/analysis.md) |
| 2319 | `e5274d278` | 2025-07-07 09:52:14 +0200 | Ajantha Bhat | Spark 4.0: Add spark action to compute partition stats (#13478) | ✅ 已完成 | [2319_e5274d278](commits/2319_e5274d278/analysis.md) |
| 2320 | `e0b62e622` | 2025-07-07 09:52:41 +0200 | dependabot[bot] | Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.7 to 8.3.8 (#13476) | ✅ 已完成 | [2320_e0b62e622](commits/2320_e0b62e622/analysis.md) |
| 2321 | `9d917a4d3` | 2025-07-07 09:52:55 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.31.73 to 2.31.77 (#13475) | ✅ 已完成 | [2321_9d917a4d3](commits/2321_9d917a4d3/analysis.md) |
| 2322 | `74acabfe9` | 2025-07-07 09:55:35 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.50.1.0 to 3.50.2.0 (#13468) | ✅ 已完成 | [2322_74acabfe9](commits/2322_74acabfe9/analysis.md) |
| 2323 | `8a747abad` | 2025-07-07 11:09:56 +0200 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.35 to 1.2.36 (#13474) | ✅ 已完成 | [2323_8a747abad](commits/2323_8a747abad/analysis.md) |
| 2324 | `9f91295bf` | 2025-07-07 14:40:21 -0700 | Ajantha Bhat | Core: Support DV for partition stats (#13425) | ✅ 已完成 | [2324_9f91295bf](commits/2324_9f91295bf/analysis.md) |
| 2325 | `401ab27e1` | 2025-07-07 16:04:57 -0600 | Amogh Jahagirdar | Spark: Throw unsupported for ADD COLUMN with default value (#13464) | ✅ 已完成 | [2325_401ab27e1](commits/2325_401ab27e1/analysis.md) |
| 2326 | `30ee7e83c` | 2025-07-07 15:07:49 -0700 | Cheng Pan | Spark 4.0: Migrate Iceberg Stored Procedures to Spark built-in implementations (#13106) | ✅ 已完成 | [2326_30ee7e83c](commits/2326_30ee7e83c/analysis.md) |
| 2327 | `061ae5898` | 2025-07-08 09:04:01 +0200 | Eduard Tudenhoefner | Core: Keep track of data files to be removed for orphaned DV detection (#13222) | ✅ 已完成 | [2327_061ae5898](commits/2327_061ae5898/analysis.md) |
| 2328 | `5d7d8c13c` | 2025-07-08 17:54:55 -0700 | Talat UYARER | Add BigQuery Dependencies for Iceberg GCP Bundle (#13111) | ✅ 已完成 | [2328_5d7d8c13c](commits/2328_5d7d8c13c/analysis.md) |
| 2329 | `9faf4311d` | 2025-07-08 18:48:08 -0700 | Ajantha Bhat | Spec: Add DV information in overview (#13189) | ✅ 已完成 | [2329_9faf4311d](commits/2329_9faf4311d/analysis.md) |
| 2330 | `f60591f9e` | 2025-07-09 08:06:49 -0700 | Alexandre Dutra | AWS: Prevent excessive creation of auth sessions in S3V4RestSignerClient (#13215) | ✅ 已完成 | [2330_f60591f9e](commits/2330_f60591f9e/analysis.md) |
| 2331 | `0d753dc6f` | 2025-07-09 08:34:28 -0700 | Amogh Jahagirdar | Spark 4.0: Port Avro lineage reader test changes from #13070 (#13496) | ✅ 已完成 | [2331_0d753dc6f](commits/2331_0d753dc6f/analysis.md) |
| 2332 | `78cfad95b` | 2025-07-09 18:04:38 +0200 | Manu Zhang | Build: Bump nessie to 0.104.2 skipping tests in JDK 11 (#13490) | ✅ 已完成 | [2332_78cfad95b](commits/2332_78cfad95b/analysis.md) |
| 2333 | `3f2267735` | 2025-07-09 12:01:18 -0700 | NikitaMatskevich | Spark: Use native table FileIO instead of Hadoop to save file list in RewriteTablePath (#13459) | ✅ 已完成 | [2333_3f2267735](commits/2333_3f2267735/analysis.md) |
| 2334 | `c71e3eb4d` | 2025-07-09 13:23:03 -0700 | Laurent Goujon | API, Flink: fix typos in javadoc (#13503) | ✅ 已完成 | [2334_c71e3eb4d](commits/2334_c71e3eb4d/analysis.md) |
| 2335 | `09140e528` | 2025-07-10 00:29:16 +0200 | Fokko Driesprong | Spark: Support Parquet dictionary encoded UUIDs (#13324) | ✅ 已完成 | [2335_09140e528](commits/2335_09140e528/analysis.md) |
| 2336 | `5d9381248` | 2025-07-10 13:53:18 -0600 | Yuval Yogev | Docs: Add Ryft to list of vendors and blog posts (#13504) | ✅ 已完成 | [2336_5d9381248](commits/2336_5d9381248/analysis.md) |
| 2337 | `e9da855b9` | 2025-07-11 08:06:56 +0200 | Ajantha Bhat | Spark 3.5: Add procedure to compute partition stats (#13480) | ✅ 已完成 | [2337_e9da855b9](commits/2337_e9da855b9/analysis.md) |
| 2338 | `26048839c` | 2025-07-11 08:10:26 +0200 | Angelo Genovese | Core: Fix a cast that is too narrow (#12743) | ✅ 已完成 | [2338_26048839c](commits/2338_26048839c/analysis.md) |
| 2339 | `7ba8eeee9` | 2025-07-11 08:10:54 +0200 | Raveendra Pujari | Build: Bump JUnit5 from 5.12.2 to 5.13.2 (#13280) | ✅ 已完成 | [2339_7ba8eeee9](commits/2339_7ba8eeee9/analysis.md) |
| 2340 | `818b2168b` | 2025-07-11 09:02:44 +0200 | Ajantha Bhat | Spark 4.0: Add procedure to compute partition stats (#13523) | ✅ 已完成 | [2340_818b2168b](commits/2340_818b2168b/analysis.md) |
| 2341 | `20b2179bf` | 2025-07-11 21:29:35 +0200 | Anoop Johnson | Core: Make metrics reporting asynchronous (#13507) | ✅ 已完成 | [2341_20b2179bf](commits/2341_20b2179bf/analysis.md) |
| 2342 | `5bda66bbb` | 2025-07-11 21:32:37 +0200 | gaborkaszab | API, Spark: Expose cleanExpiredMetadata in expire_snapshots Spark procedure (#13509) | ✅ 已完成 | [2342_5bda66bbb](commits/2342_5bda66bbb/analysis.md) |
| 2343 | `ae672a270` | 2025-07-11 17:04:08 -0600 | Anoop Johnson | Core: Implement close() method in CompositeMetricsReporter (#13535) | ✅ 已完成 | [2343_ae672a270](commits/2343_ae672a270/analysis.md) |
| 2344 | `17563614b` | 2025-07-14 09:18:18 +0200 | david yuan | Docs: Add missing VIEW keyword in View creation DDL (#13539) | ✅ 已完成 | [2344_17563614b](commits/2344_17563614b/analysis.md) |
| 2345 | `7c4793142` | 2025-07-14 09:19:48 +0200 | dependabot[bot] | Build: Bump nessie from 0.104.2 to 0.104.3 (#13541) | ✅ 已完成 | [2345_7c4793142](commits/2345_7c4793142/analysis.md) |
| 2346 | `ba3a4e8f2` | 2025-07-14 09:20:42 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#13542) | ✅ 已完成 | [2346_ba3a4e8f2](commits/2346_ba3a4e8f2/analysis.md) |
| 2347 | `5440f696a` | 2025-07-14 09:20:59 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.31.77 to 2.31.78 (#13543) | ✅ 已完成 | [2347_5440f696a](commits/2347_5440f696a/analysis.md) |
| 2348 | `f70beba8f` | 2025-07-14 09:21:16 +0200 | dependabot[bot] | Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#13544) | ✅ 已完成 | [2348_f70beba8f](commits/2348_f70beba8f/analysis.md) |
| 2349 | `bcf9c69c0` | 2025-07-14 09:26:26 +0200 | gaborkaszab | Docs: Document clean_expired_metadata parameter in expire_snapshots Spark procedure (#13516) | ✅ 已完成 | [2349_bcf9c69c0](commits/2349_bcf9c69c0/analysis.md) |
| 2350 | `4920a0ca2` | 2025-07-14 19:31:50 +0200 | gaborkaszab | Spark 3.4, 3.5: Expose cleanExpiredMetadata in expire_snapshots Spark procedure (#13553) | ✅ 已完成 | [2350_4920a0ca2](commits/2350_4920a0ca2/analysis.md) |
| 2351 | `d94b0362d` | 2025-07-14 13:31:38 -0700 | Amogh Jahagirdar | Spark 4.0: Row Lineage support (#13310) | ✅ 已完成 | [2351_d94b0362d](commits/2351_d94b0362d/analysis.md) |
| 2352 | `07afd8408` | 2025-07-14 14:58:56 -0600 | ChocZoe | Docs: Add BladePipe to list of vendors and blog posts (#13510) | ✅ 已完成 | [2352_07afd8408](commits/2352_07afd8408/analysis.md) |
| 2353 | `6a6c5b806` | 2025-07-15 08:54:06 +0200 | Yu-Chuan Hung | Enforce that test classes start with "Test" (#13466) | ✅ 已完成 | [2353_6a6c5b806](commits/2353_6a6c5b806/analysis.md) |
| 2354 | `ccb115985` | 2025-07-15 12:11:24 +0200 | Robin Moffatt | [docs] Add two Iceberg blogs (#13493) | ✅ 已完成 | [2354_ccb115985](commits/2354_ccb115985/analysis.md) |
| 2355 | `44c02ad6b` | 2025-07-15 12:11:39 +0200 | Robin Moffatt | Hardcode BSD sed if running on MacOS (#13501) | ✅ 已完成 | [2355_44c02ad6b](commits/2355_44c02ad6b/analysis.md) |
| 2356 | `ed98c6f24` | 2025-07-15 12:12:42 +0200 | Robin Moffatt | Tabular no long exist as a company (#13511) | ✅ 已完成 | [2356_ed98c6f24](commits/2356_ed98c6f24/analysis.md) |
| 2357 | `aea3e2833` | 2025-07-15 12:39:37 +0200 | Hussein Awala | Data: Fix typo in TestDataFileIndexStatsFilters (#13540) | ✅ 已完成 | [2357_aea3e2833](commits/2357_aea3e2833/analysis.md) |
| 2358 | `d3d5662ae` | 2025-07-15 16:26:42 +0200 | Robin Moffatt | [docs] Add Confluent to vendors page (#13512) | ✅ 已完成 | [2358_d3d5662ae](commits/2358_d3d5662ae/analysis.md) |
| 2359 | `31daaedd1` | 2025-07-16 09:42:02 +0200 | Anoop Johnson | Core: Add Schema evolution test with initial defaults (#13537) | ✅ 已完成 | [2359_31daaedd1](commits/2359_31daaedd1/analysis.md) |
| 2360 | `e213258bb` | 2025-07-16 15:02:50 -0700 | Prashant Singh | Site: Updates for 1.9.2 Release (#13578) | ✅ 已完成 | [2360_e213258bb](commits/2360_e213258bb/analysis.md) |
| 2361 | `5ffc529e1` | 2025-07-16 16:42:17 -0600 | Fokko Driesprong | Spark 4: Support Parquet dictionary encoded UUIDs (#13573) | ✅ 已完成 | [2361_5ffc529e1](commits/2361_5ffc529e1/analysis.md) |
| 2362 | `026374b53` | 2025-07-16 16:54:21 -0700 | Prashant Singh | INFRA: Add 1.9.2 to latest (#13577) | ✅ 已完成 | [2362_026374b53](commits/2362_026374b53/analysis.md) |
| 2363 | `04b9033a8` | 2025-07-17 09:12:06 +0200 | Anoop Johnson | Core: Add Schema evolution test with partition transform on a field with default values (#13570) | ✅ 已完成 | [2363_04b9033a8](commits/2363_04b9033a8/analysis.md) |
| 2364 | `cea109359` | 2025-07-17 09:58:30 +0200 | Yuya Ebihara | Docs: Fix indentation of 1.9.2 release note (#13583) | ✅ 已完成 | [2364_cea109359](commits/2364_cea109359/analysis.md) |
| 2365 | `41c0b17a2` | 2025-07-17 08:57:45 -0700 | liko | kafka-connect: resolve CVE-2025-48734  (#13561) | ✅ 已完成 | [2365_41c0b17a2](commits/2365_41c0b17a2/analysis.md) |
| 2366 | `c9154bd55` | 2025-07-17 11:16:27 -0700 | Ajantha Bhat | Docs: Document compute_partition_stats procedure (#13532) | ✅ 已完成 | [2366_c9154bd55](commits/2366_c9154bd55/analysis.md) |
| 2367 | `71629c12d` | 2025-07-17 12:54:23 -0600 | Eduard Tudenhoefner | API, Core: Avoid boxing of integer in evaluators / simplify ManifestReader (#13589) | ✅ 已完成 | [2367_71629c12d](commits/2367_71629c12d/analysis.md) |
| 2368 | `9922e6dc4` | 2025-07-17 11:56:09 -0700 | Tom Tanaka | Docs: Fix the default value of use_caching to false in the rewrite_manifests and add where parameter to rewrite_position_delete_files (#13549) | ✅ 已完成 | [2368_9922e6dc4](commits/2368_9922e6dc4/analysis.md) |
| 2369 | `aca6f2ae5` | 2025-07-17 18:09:32 -1000 | Yuya Ebihara | Core: Add 'google' auth type to auth manager (#13564) | ✅ 已完成 | [2369_aca6f2ae5](commits/2369_aca6f2ae5/analysis.md) |
| 2370 | `2e5a4f2b0` | 2025-07-18 09:28:54 -0700 | Prashant Singh | Docs: Add creating release from passed RC tag (#13595) | ✅ 已完成 | [2370_2e5a4f2b0](commits/2370_2e5a4f2b0/analysis.md) |
| 2371 | `29a87e7a0` | 2025-07-18 09:33:27 -0700 | Robin Moffatt | [docs] Tidy up left-hand navigation (#13491) | ✅ 已完成 | [2371_29a87e7a0](commits/2371_29a87e7a0/analysis.md) |
| 2372 | `77f1f5ba4` | 2025-07-19 06:18:24 -0600 | Adam Szita | AWS: KeyManagementClient implementation that works with AWS KMS (#13136) | ✅ 已完成 | [2372_77f1f5ba4](commits/2372_77f1f5ba4/analysis.md) |
| 2373 | `0cede5752` | 2025-07-21 09:12:09 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.2.Final to 4.2.3.Final (#13605) | ✅ 已完成 | [2373_0cede5752](commits/2373_0cede5752/analysis.md) |
| 2374 | `f7441e414` | 2025-07-21 09:12:44 +0200 | dependabot[bot] | Build: Bump org.immutables:value from 2.11.0 to 2.11.1 (#13603) | ✅ 已完成 | [2374_f7441e414](commits/2374_f7441e414/analysis.md) |
| 2375 | `6bbbbf6f6` | 2025-07-21 09:13:34 +0200 | dependabot[bot] | Build: Bump org.xerial.snappy:snappy-java from 1.1.10.7 to 1.1.10.8 (#13601) | ✅ 已完成 | [2375_6bbbbf6f6](commits/2375_6bbbbf6f6/analysis.md) |
| 2376 | `9a2342059` | 2025-07-21 12:34:35 +0200 | Manu Zhang | Spark: Fix flaky testParallelPartialProgressWithMaxFailedCommitsLargerThanTotalFileGroup (#13598) | ✅ 已完成 | [2376_9a2342059](commits/2376_9a2342059/analysis.md) |
| 2377 | `4c7726429` | 2025-07-21 12:58:09 -0500 | Manikandan R | API, Core: Use short string in Variant when possible (#13284) | ✅ 已完成 | [2377_4c7726429](commits/2377_4c7726429/analysis.md) |
| 2378 | `df66d9b7e` | 2025-07-21 20:51:35 +0200 | Manu Zhang | Spark 4.0: Rename SparkCatalogConfig#SPARK to SparkCatalogConfig#SPARK_SESSION (#13607) | ✅ 已完成 | [2378_df66d9b7e](commits/2378_df66d9b7e/analysis.md) |
| 2379 | `378479bd2` | 2025-07-21 13:46:33 -0600 | Eduard Tudenhoefner | Spark: Unimplement WithAssertions from test classes (#13610) | ✅ 已完成 | [2379_378479bd2](commits/2379_378479bd2/analysis.md) |
| 2380 | `7a9079bee` | 2025-07-22 07:57:51 +0200 | slfan1989 | Docs: Fix spelling errors in v3 spec (#13622) | ✅ 已完成 | [2380_7a9079bee](commits/2380_7a9079bee/analysis.md) |
| 2381 | `e37731dca` | 2025-07-22 12:31:40 +0200 | Manu Zhang | Spec: Add manifest fields to Snapshot Summary metrics (#13238) | ✅ 已完成 | [2381_e37731dca](commits/2381_e37731dca/analysis.md) |
| 2382 | `e986e7d03` | 2025-07-22 12:35:58 +0200 | Nikita Ryanov | Core: Make SnapshotRefType public (#13621) | ✅ 已完成 | [2382_e986e7d03](commits/2382_e986e7d03/analysis.md) |
| 2383 | `85cc58aa8` | 2025-07-22 07:14:06 -0600 | Hongyue/Steve Zhang | Spark: Use bulk deletion for manifests when importing files to an Iceberg table (#13620) | ✅ 已完成 | [2383_85cc58aa8](commits/2383_85cc58aa8/analysis.md) |
| 2384 | `3e0f7a1bf` | 2025-07-22 15:26:25 -0600 | Amogh Jahagirdar | Spark 4.0: Preserve row lineage information when compaction is run (#13555) | ✅ 已完成 | [2384_3e0f7a1bf](commits/2384_3e0f7a1bf/analysis.md) |
| 2385 | `13871b250` | 2025-07-23 07:20:33 +0200 | Yuya Ebihara | Test: Simplify collection and optional assertions (#13613) | ✅ 已完成 | [2385_13871b250](commits/2385_13871b250/analysis.md) |
| 2386 | `398635c39` | 2025-07-23 07:22:23 +0200 | Liam Bao | Build: Bump com.google.cloud:libraries-bom from 26.62.0 to 26.64.0 (#13632) | ✅ 已完成 | [2386_398635c39](commits/2386_398635c39/analysis.md) |
| 2387 | `39e8568b5` | 2025-07-23 12:06:02 +0200 | pvary | Build, Flink: Add timeout and print more logs (#13627) | ✅ 已完成 | [2387_39e8568b5](commits/2387_39e8568b5/analysis.md) |
| 2388 | `8dfd9de79` | 2025-07-23 12:56:23 +0200 | pvary | Core: Implement Map comparator (#13626) | ✅ 已完成 | [2388_8dfd9de79](commits/2388_8dfd9de79/analysis.md) |
| 2389 | `ae7636c40` | 2025-07-23 14:53:23 +0200 | Maximilian Michels | Flink: Dynamic Sink: Ensure parent for newly added struct is resolved from current schema (#13639) | ✅ 已完成 | [2389_ae7636c40](commits/2389_ae7636c40/analysis.md) |
| 2390 | `926142a45` | 2025-07-23 09:16:00 -0600 | Amogh Jahagirdar | Spark 3.5: Backport #13555 for preserving row lineage on compaction (#13637) | ✅ 已完成 | [2390_926142a45](commits/2390_926142a45/analysis.md) |
| 2391 | `6e361a657` | 2025-07-23 18:11:30 +0200 | dependabot[bot] | Build: Bump jackson-bom from 2.19.1 to 2.19.2 (#13600) | ✅ 已完成 | [2391_6e361a657](commits/2391_6e361a657/analysis.md) |
| 2392 | `8e2dc047c` | 2025-07-23 10:29:11 -0600 | Amogh Jahagirdar | Spark 3.4: Backport #13555 for preserving row lineage on compaction (#13641) | ✅ 已完成 | [2392_8e2dc047c](commits/2392_8e2dc047c/analysis.md) |
| 2393 | `a40ab7ad4` | 2025-07-23 13:58:31 -0700 | Steven Zhen Wu | Flink: fail file rewrite for V3 tables as row lineage not supported (#13646) | ✅ 已完成 | [2393_a40ab7ad4](commits/2393_a40ab7ad4/analysis.md) |
| 2394 | `8b694d121` | 2025-07-23 15:21:40 -0600 | Kevin Liu | Spark 4: Port vectorized reader tests for row lineage from #12928 (#13649) | ✅ 已完成 | [2394_8b694d121](commits/2394_8b694d121/analysis.md) |
| 2395 | `badd36d5e` | 2025-07-23 15:22:52 -0600 | Kevin Liu | Spark 4: Port tests to verify that dropped fields referenced in older partition specs are ignored (#13648) | ✅ 已完成 | [2395_badd36d5e](commits/2395_badd36d5e/analysis.md) |
| 2396 | `36ad08c96` | 2025-07-23 15:27:42 -0600 | Kevin Liu | Spark 3.5: Backport rename SparkCatalogConfig#SPARK to SparkCatalogConfig#SPARK_SESSION (#13650) | ✅ 已完成 | [2396_36ad08c96](commits/2396_36ad08c96/analysis.md) |
| 2397 | `5fdc4cf89` | 2025-07-23 21:26:29 -0700 | Manu Zhang | Docs: Move links to other implementations out of Java docs (#13618) | ✅ 已完成 | [2397_5fdc4cf89](commits/2397_5fdc4cf89/analysis.md) |
| 2398 | `494b27e0d` | 2025-07-23 21:50:54 -0700 | NikitaMatskevich | improve error logging in ADLSFileIO (#13517) | ✅ 已完成 | [2398_494b27e0d](commits/2398_494b27e0d/analysis.md) |
| 2399 | `1efd6c988` | 2025-07-24 09:28:46 +0200 | Steven Wu | Flink: backport PR #13646 to 1.20 for fail compaction on V3 tables | ✅ 已完成 | [2399_1efd6c988](commits/2399_1efd6c988/analysis.md) |
| 2400 | `98b7df377` | 2025-07-24 09:28:46 +0200 | Steven Wu | Flink: backport PR #11485 from 1.20 to 1.19 for maintenance actions with DVs | ✅ 已完成 | [2400_98b7df377](commits/2400_98b7df377/analysis.md) |
| 2401 | `53ba1cfba` | 2025-07-24 09:28:46 +0200 | Steven Wu | Flink: backport PR #13646 to 1.19 for fail compaction on V3 tables | ✅ 已完成 | [2401_53ba1cfba](commits/2401_53ba1cfba/analysis.md) |
| 2402 | `7d40d328a` | 2025-07-24 09:33:02 +0200 | Steven Zhen Wu | Core: backport PR #13100 from 1.9 to main branch that partially revert 12670 by sending single snapshot rather than in bulk  (#13647) | ✅ 已完成 | [2402_7d40d328a](commits/2402_7d40d328a/analysis.md) |
| 2403 | `a8c3a591c` | 2025-07-24 10:56:21 +0200 | Maximilian Michels | Flink: Clean up UpdateSchema instantiator method in TestEvolveSchemaVisitor (#13640) | ✅ 已完成 | [2403_a8c3a591c](commits/2403_a8c3a591c/analysis.md) |
| 2404 | `8047c6b84` | 2025-07-24 11:20:11 +0200 | Rodrigo | Flink: Add support for SpeculativeExecution for IcebergSink (#13642) | ✅ 已完成 | [2404_8047c6b84](commits/2404_8047c6b84/analysis.md) |
| 2405 | `bc154539a` | 2025-07-24 12:58:25 +0200 | GuoYu | Flink: Adjust the configuration precedence for the dynamic sink (#13609) | ✅ 已完成 | [2405_bc154539a](commits/2405_bc154539a/analysis.md) |
| 2406 | `60edd9bef` | 2025-07-24 15:14:39 +0200 | Maximilian Michels | Flink: Dynamic Sink: Ensure parent for newly added struct is resolved from current schema (#13656) | ✅ 已完成 | [2406_60edd9bef](commits/2406_60edd9bef/analysis.md) |
| 2407 | `be4db9023` | 2025-07-24 16:14:05 +0200 | Maximilian Michels | Flink: DynamicSink: Convert existing required fields to optional when missing in the data schema (#13659) | ✅ 已完成 | [2407_be4db9023](commits/2407_be4db9023/analysis.md) |
| 2408 | `a5bae2fca` | 2025-07-24 16:14:45 +0200 | Maximilian Michels | Backport: Flink: Clean up UpdateSchema instantiator method in TestEvolveSchemaVisitor (#13658) | ✅ 已完成 | [2408_a5bae2fca](commits/2408_a5bae2fca/analysis.md) |
| 2409 | `036dddd7a` | 2025-07-24 16:16:55 +0200 | Eduard Tudenhoefner | Build: Statically import Assumptions.assumeThat() (#13655) | ✅ 已完成 | [2409_036dddd7a](commits/2409_036dddd7a/analysis.md) |
| 2410 | `16012afb3` | 2025-07-24 17:11:15 +0200 | Maximilian Michels | Flink: Backport: DynamicSink: Convert existing required fields to optional when missing in the data schema (#13660) | ✅ 已完成 | [2410_16012afb3](commits/2410_16012afb3/analysis.md) |
| 2411 | `bfa917252` | 2025-07-24 22:05:10 +0200 | Rodrigo | Flink: Backport: Adds support for SpeculativeExecution for IcebergSink (#13663) | ✅ 已完成 | [2411_bfa917252](commits/2411_bfa917252/analysis.md) |
| 2412 | `43bd9c386` | 2025-07-24 21:38:58 -0700 | Jayce Slesar | REST-Fixture: Ensure strict mode on jdbc catalog for rest fixture (#13599) | ✅ 已完成 | [2412_43bd9c386](commits/2412_43bd9c386/analysis.md) |
| 2413 | `d22cc9c64` | 2025-07-24 22:04:35 -0700 | Kevin Liu | docs: add subpage for REST Catalog Spec in "Specification" (#13521) | ✅ 已完成 | [2413_d22cc9c64](commits/2413_d22cc9c64/analysis.md) |
| 2414 | `c7e367494` | 2025-07-25 09:34:46 +0200 | Hongyue/Steve Zhang | Core: Use Bulk deletion for cleaning up uncommitted files in BaseTransaction (#13653) | ✅ 已完成 | [2414_c7e367494](commits/2414_c7e367494/analysis.md) |
| 2415 | `fb81fcf38` | 2025-07-25 10:19:44 -0600 | Bruno Volpato | Core: Use zero-copy wrapper for equalityFieldIds (#13668) | ✅ 已完成 | [2415_fb81fcf38](commits/2415_fb81fcf38/analysis.md) |
| 2416 | `029781695` | 2025-07-25 13:33:30 -0500 | Manikandan R | API: Improve test coverage for 1-5 byte header string primitive in Variant (#13629) | ✅ 已完成 | [2416_029781695](commits/2416_029781695/analysis.md) |
| 2417 | `e3763f128` | 2025-07-25 13:08:08 -0600 | Drew Gallardo | Core: Prevent empty Puffin file creation in DV writer (#13666) | ✅ 已完成 | [2417_e3763f128](commits/2417_e3763f128/analysis.md) |
| 2418 | `360f87326` | 2025-07-25 15:07:03 -0700 | Russell Spitzer | Core: Allow retries for Idempotent Requests with Certain Codes (#13449) | ✅ 已完成 | [2418_360f87326](commits/2418_360f87326/analysis.md) |
| 2419 | `d365ce3ff` | 2025-07-27 08:59:54 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.31.2 to 0.32.0 (#13688) | ✅ 已完成 | [2419_d365ce3ff](commits/2419_d365ce3ff/analysis.md) |
| 2420 | `dc88aad3d` | 2025-07-27 09:00:19 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.14 to 9.6.16 (#13689) | ✅ 已完成 | [2420_dc88aad3d](commits/2420_dc88aad3d/analysis.md) |
| 2421 | `62cc9525a` | 2025-07-28 09:03:06 +0200 | dependabot[bot] | Build: Bump junit from 5.13.2 to 5.13.4 (#13684) | ✅ 已完成 | [2421_62cc9525a](commits/2421_62cc9525a/analysis.md) |
| 2422 | `7e9a1bc9d` | 2025-07-28 09:03:27 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#13683) | ✅ 已完成 | [2422_7e9a1bc9d](commits/2422_7e9a1bc9d/analysis.md) |
| 2423 | `bb7113a88` | 2025-07-28 09:18:35 +0200 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.50.2.0 to 3.50.3.0 (#13686) | ✅ 已完成 | [2423_bb7113a88](commits/2423_bb7113a88/analysis.md) |
| 2424 | `57dbed557` | 2025-07-28 09:18:49 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.31.78 to 2.32.9 (#13687) | ✅ 已完成 | [2424_57dbed557](commits/2424_57dbed557/analysis.md) |
| 2425 | `ce63292eb` | 2025-07-28 10:17:41 +0200 | dependabot[bot] | Build: Bump junit-platform from 1.13.2 to 1.13.4 (#13682) | ✅ 已完成 | [2425_ce63292eb](commits/2425_ce63292eb/analysis.md) |
| 2426 | `d0361be73` | 2025-07-28 12:32:07 +0200 | Anoop Johnson | Core, Data, Parquet: Cleanup unit tests by delegating file cleanup to JUnit (#13590) | ✅ 已完成 | [2426_d0361be73](commits/2426_d0361be73/analysis.md) |
| 2427 | `f7e6a2775` | 2025-07-28 12:21:20 -0600 | Amogh Jahagirdar | Core: Fix incorrect selection of incremental cleanup in expire snapshots (#13614) | ✅ 已完成 | [2427_f7e6a2775](commits/2427_f7e6a2775/analysis.md) |
| 2428 | `571056929` | 2025-07-28 20:29:23 -0600 | Aihua Xu | Spark: Add Variant read support for Spark Iceberg tables  (#13219) | ✅ 已完成 | [2428_571056929](commits/2428_571056929/analysis.md) |
| 2429 | `d6f22d5a5` | 2025-07-29 17:21:33 +0200 | GuoYu | Flink: Add support for filter in RewriteDataFiles (#13669) | ✅ 已完成 | [2429_d6f22d5a5](commits/2429_d6f22d5a5/analysis.md) |
| 2430 | `83da920b3` | 2025-07-29 17:39:56 +0200 | GuoYu | Flink: Add test for adjust the configuration precedence in the Dynamic Sink (#13662) | ✅ 已完成 | [2430_83da920b3](commits/2430_83da920b3/analysis.md) |
| 2431 | `ffeb7a2a7` | 2025-07-30 08:07:58 +0200 | Aihua Xu | Spark: Use hasSameSizeAs in collection size assertion (#13701) | ✅ 已完成 | [2431_ffeb7a2a7](commits/2431_ffeb7a2a7/analysis.md) |
| 2432 | `a519cb295` | 2025-07-30 16:12:59 +0200 | dependabot[bot] | Build: Bump orc from 1.9.6 to 1.9.7 (#13470) | ✅ 已完成 | [2432_a519cb295](commits/2432_a519cb295/analysis.md) |
| 2433 | `c3d50e177` | 2025-07-30 10:07:25 -0500 | Eric Maynard | Arrow, Parquet: Add support for DELTA_BINARY_PACKED Parquet encoding (#13391) | ✅ 已完成 | [2433_c3d50e177](commits/2433_c3d50e177/analysis.md) |
| 2434 | `a73b48bf5` | 2025-07-30 17:48:05 +0200 | GuoYu | Doc: Flink: Add doc for the dynamic sink (#13608) | ✅ 已完成 | [2434_a73b48bf5](commits/2434_a73b48bf5/analysis.md) |
| 2435 | `09301c149` | 2025-07-30 17:49:08 +0200 | GuoYu | Flink: Backport RewriteDataFiles support filter in plan (#13702) | ✅ 已完成 | [2435_09301c149](commits/2435_09301c149/analysis.md) |
| 2436 | `b7b56fd90` | 2025-07-30 15:08:37 -0700 | Steven Zhen Wu | Flink: disable flaky testRangeDistributionStatisticsMigration() (#13711) | ✅ 已完成 | [2436_b7b56fd90](commits/2436_b7b56fd90/analysis.md) |
| 2437 | `1bd8d5e2d` | 2025-07-31 07:36:33 +0200 | Smith Cruise | Spec: Fix wrong type for snapshot-id in table statistics (#13513) | ✅ 已完成 | [2437_1bd8d5e2d](commits/2437_1bd8d5e2d/analysis.md) |
| 2438 | `15351e6ab` | 2025-07-31 13:28:49 -0700 | Aihua Xu | Core: Fix decimal type for variant (#13692) | ✅ 已完成 | [2438_15351e6ab](commits/2438_15351e6ab/analysis.md) |
| 2439 | `95a578d7a` | 2025-07-31 16:29:30 -0600 | Tobias Riemenschneider | Core: Fix case-insensitive validation of fields in schema evolution (#13697) | ✅ 已完成 | [2439_95a578d7a](commits/2439_95a578d7a/analysis.md) |
| 2440 | `a8fdb2368` | 2025-08-01 13:50:10 -0700 | Hongyue/Steve Zhang | Core: refactor BaseTransaction for extensibility (#13631) | ✅ 已完成 | [2440_a8fdb2368](commits/2440_a8fdb2368/analysis.md) |
| 2441 | `19b9eaebb` | 2025-08-04 10:21:18 +0200 | Nándor Kollár | Arrow: Test FIXED type (#13700) | ✅ 已完成 | [2441_19b9eaebb](commits/2441_19b9eaebb/analysis.md) |
| 2442 | `76b2f875f` | 2025-08-04 10:34:48 +0200 | Alexandre Dutra | Core: introduce shared authentication refresh executor (#12563) | ✅ 已完成 | [2442_76b2f875f](commits/2442_76b2f875f/analysis.md) |
| 2443 | `7fefc46e9` | 2025-08-04 12:56:50 +0200 | Chun Wei Lu | Coerce UUID to String in `readable-metrics` (#13087) | ✅ 已完成 | [2443_7fefc46e9](commits/2443_7fefc46e9/analysis.md) |
| 2444 | `c23b341ea` | 2025-08-04 13:44:40 +0200 | gaborkaszab | Flink: Expose cleanExpiredMetadata for snapshot expiration (#13569) | ✅ 已完成 | [2444_c23b341ea](commits/2444_c23b341ea/analysis.md) |
| 2445 | `d2b5ea636` | 2025-08-04 18:03:51 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.32.9 to 2.32.14 (#13721) | ✅ 已完成 | [2445_d2b5ea636](commits/2445_d2b5ea636/analysis.md) |
| 2446 | `bb7ee6768` | 2025-08-04 18:04:16 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.64.0 to 26.65.0 (#13722) | ✅ 已完成 | [2446_bb7ee6768](commits/2446_bb7ee6768/analysis.md) |
| 2447 | `be55a5d4e` | 2025-08-04 18:16:30 +0200 | gaborkaszab | Flink: Backport expose cleanExpiredMetadata for snapshot expiration (#13729) | ✅ 已完成 | [2447_be55a5d4e](commits/2447_be55a5d4e/analysis.md) |
| 2448 | `e227f330d` | 2025-08-04 09:22:03 -0700 | Steven Wu | Revert "Flink: disable flaky testRangeDistributionStatisticsMigration() (#13711)" | ✅ 已完成 | [2448_e227f330d](commits/2448_e227f330d/analysis.md) |
| 2449 | `5d64360cc` | 2025-08-04 09:22:03 -0700 | Steven Wu | Flink: fix testRangeDistributionStatisticsMigration flakiness by adjusting the setup | ✅ 已完成 | [2449_5d64360cc](commits/2449_5d64360cc/analysis.md) |
| 2450 | `33a6dd13c` | 2025-08-04 18:30:30 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.24.2 to 3.25.1 (#13685) | ✅ 已完成 | [2450_33a6dd13c](commits/2450_33a6dd13c/analysis.md) |
| 2451 | `2876b959d` | 2025-08-04 11:37:34 -0700 | Hongyue/Steve Zhang | Core: make BaseRowDelta public (#13643) | ✅ 已完成 | [2451_2876b959d](commits/2451_2876b959d/analysis.md) |
| 2452 | `de22fd0f9` | 2025-08-04 13:41:08 -0700 | lnc | Doc: Add Databricks to vendors.md (#13734) | ✅ 已完成 | [2452_de22fd0f9](commits/2452_de22fd0f9/analysis.md) |
| 2453 | `32f469b98` | 2025-08-05 08:14:46 +0200 | Eduard Tudenhoefner | API, Core: Preserve original Type for upper/lower bounds in Metrics (#13695) | ✅ 已完成 | [2453_32f469b98](commits/2453_32f469b98/analysis.md) |
| 2454 | `acecf3cb6` | 2025-08-05 09:29:58 +0200 | Manu Zhang | Spark: Print unknown catalog type in exception when configuring validation catalog (#13588) | ✅ 已完成 | [2454_acecf3cb6](commits/2454_acecf3cb6/analysis.md) |
| 2455 | `ded11105f` | 2025-08-05 11:07:16 +0200 | gaborkaszab | Spark: Use default cleanExpiredMetadata from Java API (#13731) | ✅ 已完成 | [2455_ded11105f](commits/2455_ded11105f/analysis.md) |
| 2456 | `34b377e0d` | 2025-08-05 11:42:20 +0200 | dependabot[bot] | Build: Bump org.apache.commons:commons-compress from 1.27.1 to 1.28.0 (#13723) | ✅ 已完成 | [2456_34b377e0d](commits/2456_34b377e0d/analysis.md) |
| 2457 | `c12a724cd` | 2025-08-05 11:51:11 +0200 | Alexandre Dutra | AWS: Fix OAuth2 additional params inclusion (#13718) | ✅ 已完成 | [2457_c12a724cd](commits/2457_c12a724cd/analysis.md) |
| 2458 | `fa80ba787` | 2025-08-05 12:19:10 +0200 | GuoYu | Flink: Fix DynamicCommitter use ThreadPools deprecated method (#13728) | ✅ 已完成 | [2458_fa80ba787](commits/2458_fa80ba787/analysis.md) |
| 2459 | `357020aa6` | 2025-08-05 11:32:29 -0700 | kumarpritam863 | Close resources only if initialised to avoid rare case NPE (#13670) | ✅ 已完成 | [2459_357020aa6](commits/2459_357020aa6/analysis.md) |
| 2460 | `7ffc718d2` | 2025-08-05 11:55:32 -0700 | Mustafa Elbehery | Core, Spark: Preserve the relative path in RewriteTablePathUtil on staging | ✅ 已完成 | [2460_7ffc718d2](commits/2460_7ffc718d2/analysis.md) |
| 2461 | `270e2bc6f` | 2025-08-06 07:54:22 +0200 | hsiang-c | Spark: Use ORC batch for orcBatchReadConf() (#13748) | ✅ 已完成 | [2461_270e2bc6f](commits/2461_270e2bc6f/analysis.md) |
| 2462 | `772c82755` | 2025-08-06 07:59:02 +0200 | suhwan | Build: Bump com.azure:azure-sdk-bom from 1.2.36 to 1.2.37 (#13743) | ✅ 已完成 | [2462_772c82755](commits/2462_772c82755/analysis.md) |
| 2463 | `dd8f5e3f3` | 2025-08-06 10:49:29 +0200 | Robin Moffatt | Doc: Flink can now add/drop/modify columns (#13617) | ✅ 已完成 | [2463_dd8f5e3f3](commits/2463_dd8f5e3f3/analysis.md) |
| 2464 | `5ce63abdb` | 2025-08-06 17:34:10 +0200 | Marcus Sawyer | Azure: Fix concurrency issues in Azure credential refresh. (#13730) | ✅ 已完成 | [2464_5ce63abdb](commits/2464_5ce63abdb/analysis.md) |
| 2465 | `1ea9dbe40` | 2025-08-06 17:37:47 +0200 | Fokko Driesprong | Revert "Coerce UUID to String in `readable-metrics` (#13087)" (#13754) | ✅ 已完成 | [2465_1ea9dbe40](commits/2465_1ea9dbe40/analysis.md) |
| 2466 | `64a7ca518` | 2025-08-06 08:57:32 -0700 | Ryan Blue | API: Fix timestamp(9) with identity partitioning. (#13746) | ✅ 已完成 | [2466_64a7ca518](commits/2466_64a7ca518/analysis.md) |
| 2467 | `9ac8b2bcb` | 2025-08-06 09:04:24 -0700 | Ryan Blue | API: Add expression factory methods for timestamp literals. (#13747) | ✅ 已完成 | [2467_9ac8b2bcb](commits/2467_9ac8b2bcb/analysis.md) |
| 2468 | `05dbcae8c` | 2025-08-06 12:00:05 -0700 | kamijin_fanta | AWS, Aliyun: Fix memory leak by removing deleteOnExit() calls (#13749) | ✅ 已完成 | [2468_05dbcae8c](commits/2468_05dbcae8c/analysis.md) |
| 2469 | `aca97753e` | 2025-08-06 16:23:49 -0700 | Manu Zhang | Docs: Add V3 types to Spark/Flink type conversion table (#13744) | ✅ 已完成 | [2469_aca97753e](commits/2469_aca97753e/analysis.md) |
| 2470 | `6eeb92484` | 2025-08-07 18:30:24 +0200 | Yuya Ebihara | Core: Remove redundant V2TableTestBase (#13757) | ✅ 已完成 | [2470_6eeb92484](commits/2470_6eeb92484/analysis.md) |
| 2471 | `1b0e4b3a5` | 2025-08-07 12:27:02 -0700 | Joshua Kolash | Core: Fix metrics column limit with nested columns (#13039) | ✅ 已完成 | [2471_1b0e4b3a5](commits/2471_1b0e4b3a5/analysis.md) |
| 2472 | `136df9f65` | 2025-08-07 15:15:15 -0600 | Eduard Tudenhoefner | Core: Use ResourcePaths instead of hard-coded resource paths (#13759) | ✅ 已完成 | [2472_136df9f65](commits/2472_136df9f65/analysis.md) |
| 2473 | `c9a245946` | 2025-08-08 12:32:33 +0200 | Yuya Ebihara | Core: Support timestamp nanos in single value parser (#13487) | ✅ 已完成 | [2473_c9a245946](commits/2473_c9a245946/analysis.md) |
| 2474 | `44dff0d85` | 2025-08-08 05:59:01 -0700 | Mickael Maison | Docs: Update the Kafka Connect readme (#13484) | ✅ 已完成 | [2474_44dff0d85](commits/2474_44dff0d85/analysis.md) |
| 2475 | `2b186b80a` | 2025-08-08 15:15:39 +0200 | Blake Smith | OpenAPI: Correct type annotation in TableMetadat#encryption-keys field (#13762) | ✅ 已完成 | [2475_2b186b80a](commits/2475_2b186b80a/analysis.md) |
| 2476 | `252a4d034` | 2025-08-08 08:00:52 -0700 | Mickael Maison | Kafka Connect: Add manifests for the transformations (#13531) | ✅ 已完成 | [2476_252a4d034](commits/2476_252a4d034/analysis.md) |
| 2477 | `de93196ab` | 2025-08-09 08:33:37 -0600 | Eduard Tudenhoefner | Core: Deprecate unused methods in  OAuth2Util (#13767) | ✅ 已完成 | [2477_de93196ab](commits/2477_de93196ab/analysis.md) |
| 2478 | `c8c61a3f6` | 2025-08-09 09:50:00 -0700 | Danica Fine | Docs: Meetup Guidelines (#13520) | ✅ 已完成 | [2478_c8c61a3f6](commits/2478_c8c61a3f6/analysis.md) |
| 2479 | `4ae61621c` | 2025-08-09 12:07:08 -0700 | Huaxin Gao | core: remove duplicate lines (#13770) | ✅ 已完成 | [2479_4ae61621c](commits/2479_4ae61621c/analysis.md) |
| 2480 | `679fa66cd` | 2025-08-10 21:22:13 +0200 | dependabot[bot] | Build: Bump com.gradleup.shadow:shadow-gradle-plugin from 8.3.8 to 8.3.9 (#13776) | ✅ 已完成 | [2480_679fa66cd](commits/2480_679fa66cd/analysis.md) |
| 2481 | `0be91dce7` | 2025-08-10 22:14:11 +0200 | dependabot[bot] | Build: Bump org.immutables:value from 2.11.1 to 2.11.2 (#13779) | ✅ 已完成 | [2481_0be91dce7](commits/2481_0be91dce7/analysis.md) |
| 2482 | `1e4eeecf0` | 2025-08-10 22:57:30 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.32.14 to 2.32.19 (#13778) | ✅ 已完成 | [2482_1e4eeecf0](commits/2482_1e4eeecf0/analysis.md) |
| 2483 | `68a241615` | 2025-08-10 22:57:53 +0200 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.27.3 to 3.27.4 (#13777) | ✅ 已完成 | [2483_68a241615](commits/2483_68a241615/analysis.md) |
| 2484 | `3a4215dbb` | 2025-08-11 19:29:21 +0200 | GuoYu | Spark, Core: Refactor Delete OrphanFiles by moving common code from Spark to core (#13429) | ✅ 已完成 | [2484_3a4215dbb](commits/2484_3a4215dbb/analysis.md) |
| 2485 | `f7b52a9f2` | 2025-08-12 16:44:01 +0200 | Eduard Tudenhoefner | Spark: Remove unused code (#13790) | ✅ 已完成 | [2485_f7b52a9f2](commits/2485_f7b52a9f2/analysis.md) |
| 2486 | `e8c9a110b` | 2025-08-12 08:48:36 -0700 | GuoYu | Spark: Backport #13429 to Spark 4.0 and 3.4 (#13789) | ✅ 已完成 | [2486_e8c9a110b](commits/2486_e8c9a110b/analysis.md) |
| 2487 | `49890ed38` | 2025-08-12 10:25:51 -0700 | JiHo Kim | Parquet: Fix incorrect JavaDoc parameter descriptions in TripleWriter (#13784) | ✅ 已完成 | [2487_49890ed38](commits/2487_49890ed38/analysis.md) |
| 2488 | `b5de305e1` | 2025-08-12 11:12:33 -0700 | Kevin Liu | site: reorganize navbar so that "community" is top level (#13774) | ✅ 已完成 | [2488_b5de305e1](commits/2488_b5de305e1/analysis.md) |
| 2489 | `e6676705f` | 2025-08-12 12:06:07 -0700 | Eric Maynard | Add golden file tests for vectorized Parquet reads (#13450) | ✅ 已完成 | [2489_e6676705f](commits/2489_e6676705f/analysis.md) |
| 2490 | `d38cdf38d` | 2025-08-12 12:07:19 -0700 | Manu Zhang | Spark: Remove unused parameter in RewriteTablePathSparkAction#rewritePositionDeletes (#13792) | ✅ 已完成 | [2490_d38cdf38d](commits/2490_d38cdf38d/analysis.md) |
| 2491 | `159d25353` | 2025-08-12 15:26:20 -0600 | Gabriel Igliozzi | Core: Batch load new files when validating replaced partitions (#13556) | ✅ 已完成 | [2491_159d25353](commits/2491_159d25353/analysis.md) |
| 2492 | `226f9dbea` | 2025-08-13 08:34:06 +0200 | Fokko Driesprong | nit: Make style consistent (#13794) | ✅ 已完成 | [2492_226f9dbea](commits/2492_226f9dbea/analysis.md) |
| 2493 | `86389ba52` | 2025-08-13 09:50:59 +0200 | slfan1989 | Flink: Replace `assertThrows` with `assertThatThrownBy` (#13772) | ✅ 已完成 | [2493_86389ba52](commits/2493_86389ba52/analysis.md) |
| 2494 | `42c740092` | 2025-08-13 08:48:23 -0700 | Manu Zhang | Docs: Fix community links in footer (#13796) | ✅ 已完成 | [2494_42c740092](commits/2494_42c740092/analysis.md) |
| 2495 | `7c1d5af38` | 2025-08-13 10:08:48 -0700 | Kevin Liu | Update community.md (#13806) | ✅ 已完成 | [2495_7c1d5af38](commits/2495_7c1d5af38/analysis.md) |
| 2496 | `efbfb7ef9` | 2025-08-13 13:09:28 -0500 | Anurag Mantripragada | Spark 4.0: Add configuration to disable executor cache for delete files (#12893) | ✅ 已完成 | [2496_efbfb7ef9](commits/2496_efbfb7ef9/analysis.md) |
| 2497 | `3f55cf1f5` | 2025-08-13 21:10:48 -0700 | slfan1989 | Docs: fix typo in JdbcLockFactory Javadoc (#13811) | ✅ 已完成 | [2497_3f55cf1f5](commits/2497_3f55cf1f5/analysis.md) |
| 2498 | `54f8e58fc` | 2025-08-14 09:28:04 +0200 | Fokko Driesprong | Spark: Prune dead branch (#13808) | ✅ 已完成 | [2498_54f8e58fc](commits/2498_54f8e58fc/analysis.md) |
| 2499 | `ed7330b06` | 2025-08-14 12:51:14 -0700 | Anurag Mantripragada | Spark 3.5, 3.4: Add configuration to disable executor cache for delete files (#13817) | ✅ 已完成 | [2499_ed7330b06](commits/2499_ed7330b06/analysis.md) |
| 2500 | `757b8e1fd` | 2025-08-14 14:57:08 -0700 | guixiaowen | Core: Use ResourcePaths instead of hard-coded resource paths in RESTCatalogAdapter #13814 (#13815) | ✅ 已完成 | [2500_757b8e1fd](commits/2500_757b8e1fd/analysis.md) |
| 2501 | `d005d7bf9` | 2025-08-15 12:59:39 +0200 | slfan1989 | Docs: Update testing guidelines to reflect full JUnit 5 migration and AssertJ usage. (#13822) | ✅ 已完成 | [2501_d005d7bf9](commits/2501_d005d7bf9/analysis.md) |
| 2502 | `a01c349f3` | 2025-08-15 13:20:05 +0200 | dependabot[bot] | Build: Bump software.amazon.s3.analyticsaccelerator:analyticsaccelerator-s3 (#13545) | ✅ 已完成 | [2502_a01c349f3](commits/2502_a01c349f3/analysis.md) |
| 2503 | `b1f686c39` | 2025-08-15 14:10:50 +0200 | Eduard Tudenhoefner | Docs: Minor improvements to Variant sections (#13828) | ✅ 已完成 | [2503_b1f686c39](commits/2503_b1f686c39/analysis.md) |
| 2504 | `af82d34e7` | 2025-08-15 08:14:04 -0700 | Daniel Weeks | Core: Allow disabling token exchange as refresh (#13809) | ✅ 已完成 | [2504_af82d34e7](commits/2504_af82d34e7/analysis.md) |
| 2505 | `24447bfbe` | 2025-08-15 14:49:54 -0500 | Anurag Mantripragada | Spark 4.0: Disable executor cache for delete files in RewriteDataFilesSparkAction (#13820) | ✅ 已完成 | [2505_24447bfbe](commits/2505_24447bfbe/analysis.md) |
| 2506 | `d5476cae1` | 2025-08-15 14:11:40 -0600 | Prashant Singh | Core: Request/Response models and parsers for REST Scan Planning  (#13004) | ✅ 已完成 | [2506_d5476cae1](commits/2506_d5476cae1/analysis.md) |
| 2507 | `3685b5558` | 2025-08-15 20:45:09 -0700 | GuoYu | Flink: Fix hash code comparison for requesting global statistics in DataStatisticsCoordinator (#13827) | ✅ 已完成 | [2507_3685b5558](commits/2507_3685b5558/analysis.md) |
| 2508 | `0b599bc37` | 2025-08-16 09:19:08 +0200 | GuoYu | Flink: Backport fix hash code comparison for requesting global statistics in DataStatisticsCoordinator (#13830) | ✅ 已完成 | [2508_0b599bc37](commits/2508_0b599bc37/analysis.md) |
| 2509 | `187c7ad5b` | 2025-08-17 23:53:11 +0200 | dependabot[bot] | Build: Bump mkdocs-macros-plugin from 1.3.7 to 1.3.9 (#13846) | ✅ 已完成 | [2509_187c7ad5b](commits/2509_187c7ad5b/analysis.md) |
| 2510 | `a8e3a2f71` | 2025-08-18 00:01:00 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.16 to 9.6.17 (#13845) | ✅ 已完成 | [2510_a8e3a2f71](commits/2510_a8e3a2f71/analysis.md) |
| 2511 | `a0ef46355` | 2025-08-17 21:02:16 -0700 | slfan1989 | Flink: Fix ResultSet resource leak in JdbcLockFactory.initializeLockTables(). (#13821) | ✅ 已完成 | [2511_a0ef46355](commits/2511_a0ef46355/analysis.md) |
| 2512 | `54c8af1d9` | 2025-08-17 22:06:08 -0700 | GuoYu | Flink: Fix schedule data file size incorrect in RewriteDataFilesConfig (#13848) | ✅ 已完成 | [2512_54c8af1d9](commits/2512_54c8af1d9/analysis.md) |
| 2513 | `090f3ef2c` | 2025-08-17 22:34:22 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.32.19 to 2.32.24 (#13843) | ✅ 已完成 | [2513_090f3ef2c](commits/2513_090f3ef2c/analysis.md) |
| 2514 | `88a719430` | 2025-08-17 22:36:23 -0700 | dependabot[bot] | Build: Bump org.immutables:value from 2.11.2 to 2.11.3 (#13839) | ✅ 已完成 | [2514_88a719430](commits/2514_88a719430/analysis.md) |
| 2515 | `82cad78a9` | 2025-08-17 23:51:04 -0700 | slfan1989 | backport #13821. (#13849) | ✅ 已完成 | [2515_82cad78a9](commits/2515_82cad78a9/analysis.md) |
| 2516 | `3dc3237ae` | 2025-08-18 09:35:23 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.32.0 to 0.33.0 (#13844) | ✅ 已完成 | [2516_3dc3237ae](commits/2516_3dc3237ae/analysis.md) |
| 2517 | `ab9219317` | 2025-08-18 09:35:38 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.25.1 to 3.26.0 (#13842) | ✅ 已完成 | [2517_ab9219317](commits/2517_ab9219317/analysis.md) |
| 2518 | `4b0fecd37` | 2025-08-18 09:35:52 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.65.0 to 26.66.0 (#13841) | ✅ 已完成 | [2518_4b0fecd37](commits/2518_4b0fecd37/analysis.md) |
| 2519 | `43fa85598` | 2025-08-18 09:36:09 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.3.Final to 4.2.4.Final (#13840) | ✅ 已完成 | [2519_43fa85598](commits/2519_43fa85598/analysis.md) |
| 2520 | `389c04cfb` | 2025-08-18 09:36:22 +0200 | dependabot[bot] | Build: Bump jetty from 11.0.25 to 11.0.26 (#13838) | ✅ 已完成 | [2520_389c04cfb](commits/2520_389c04cfb/analysis.md) |
| 2521 | `0d26e1f1e` | 2025-08-18 13:23:49 -0700 | Manu Zhang | Spark 4.0: Add removed_delete_files_count to result of RewriteDataFilesProcedure (#13657) | ✅ 已完成 | [2521_0d26e1f1e](commits/2521_0d26e1f1e/analysis.md) |
| 2522 | `84172252a` | 2025-08-18 14:18:45 -0700 | Filipe Regadas | Add planWith to FindFiles to leverage ParallelIterable (#13836) | ✅ 已完成 | [2522_84172252a](commits/2522_84172252a/analysis.md) |
| 2523 | `856cbf6eb` | 2025-08-18 15:07:28 -0700 | Hongyue/Steve Zhang | Core, Docs: Update write.metadata.metrics.max-inferred-column-defaults documentation and add benchmark (#13785) | ✅ 已完成 | [2523_856cbf6eb](commits/2523_856cbf6eb/analysis.md) |
| 2524 | `6017fec2b` | 2025-08-18 22:14:28 -0700 | Manu Zhang |  Spark 3.5, 3.4: Add removed_delete_files_count to result of RewriteDataFilesProcedure (#13862) | ✅ 已完成 | [2524_6017fec2b](commits/2524_6017fec2b/analysis.md) |
| 2525 | `2c42237a7` | 2025-08-18 22:42:23 -0700 | Itamar Weiss | feat: make RESTCatalogServer catalog name configurable (#13750) | ✅ 已完成 | [2525_2c42237a7](commits/2525_2c42237a7/analysis.md) |
| 2526 | `ede5b5a2f` | 2025-08-19 08:35:41 +0200 | Kevin Liu | Azure: Support access token authentication via the new `adls.token` property (#13825) | ✅ 已完成 | [2526_ede5b5a2f](commits/2526_ede5b5a2f/analysis.md) |
| 2527 | `c588d8d51` | 2025-08-19 11:37:47 +0200 | Yuya Ebihara | Revert "Docker: Pin QEMU version temporarily (#12262)" (#13861) | ✅ 已完成 | [2527_c588d8d51](commits/2527_c588d8d51/analysis.md) |
| 2528 | `0478ff7dc` | 2025-08-19 16:11:53 +0200 | Maximilian Michels | Docs: Fix table of contents in Flink docs (#13864) | ✅ 已完成 | [2528_0478ff7dc](commits/2528_0478ff7dc/analysis.md) |
| 2529 | `653012610` | 2025-08-19 09:48:44 -0700 | Manu Zhang | Docs: Add removed_delete_files_count to rewrite_data_files output (#13865) | ✅ 已完成 | [2529_653012610](commits/2529_653012610/analysis.md) |
| 2530 | `88500ecb4` | 2025-08-19 09:49:57 -0700 | Anoop Johnson | Core: Rewrite the Iceberg Arrow schema translation to use the visitor pattern (#13699) | ✅ 已完成 | [2530_88500ecb4](commits/2530_88500ecb4/analysis.md) |
| 2531 | `3e443d582` | 2025-08-19 22:15:49 +0200 | Gabriel Igliozzi | Hive: Throw `NoSuchViewException` when loading an Iceberg table as a view (#13847) | ✅ 已完成 | [2531_3e443d582](commits/2531_3e443d582/analysis.md) |
| 2532 | `83df4dde3` | 2025-08-20 12:41:34 +0200 | Yujiang Zhong | Core: Deprecate TableMetadataParser#read with unused file io parameter (#13871) | ✅ 已完成 | [2532_83df4dde3](commits/2532_83df4dde3/analysis.md) |
| 2533 | `2e3045282` | 2025-08-20 16:17:03 +0200 | Robin Moffatt | Docs: Add exactly once semantics note to Flink docs (#13875) | ✅ 已完成 | [2533_2e3045282](commits/2533_2e3045282/analysis.md) |
| 2534 | `07c088fce` | 2025-08-20 10:00:22 -0500 | Zach Dischner | API, Spark 3.5: Adding new rewrite manifest spark action to accept custom partition order (#12840) | ✅ 已完成 | [2534_07c088fce](commits/2534_07c088fce/analysis.md) |
| 2535 | `d5e3a56b3` | 2025-08-20 12:20:03 -0700 | Fokko Driesprong | Spark 3.4: Support Parquet dictionary encoded UUIDs (#13877) | ✅ 已完成 | [2535_d5e3a56b3](commits/2535_d5e3a56b3/analysis.md) |
| 2536 | `2012f661a` | 2025-08-20 15:25:23 -0700 | Anurag Mantripragada | Spark 3.5, 3.4: Disable executor cache for delete files in RewriteDataFilesSparkAction (#13868) | ✅ 已完成 | [2536_2012f661a](commits/2536_2012f661a/analysis.md) |
| 2537 | `efc27f9de` | 2025-08-21 14:40:11 +0200 | Nándor Kollár | Arrow: Add nanosec precision timestamp (#13562) | ✅ 已完成 | [2537_efc27f9de](commits/2537_efc27f9de/analysis.md) |
| 2538 | `7d220eb93` | 2025-08-21 16:52:54 +0200 | GuoYu | Flink: Supports delete orphan files in TableMaintenance (#13302) | ✅ 已完成 | [2538_7d220eb93](commits/2538_7d220eb93/analysis.md) |
| 2539 | `0cb18a792` | 2025-08-21 19:01:21 +0200 | GuoYu | Flink: Move state import in SkipOnError from v2 to v1 (#13888) | ✅ 已完成 | [2539_0cb18a792](commits/2539_0cb18a792/analysis.md) |
| 2540 | `7816de0f5` | 2025-08-21 22:03:53 +0200 | GuoYu | Flink: Backport supports delete orphan files in TableMaintenance to 1.19 and 1.20 (#13887) | ✅ 已完成 | [2540_7816de0f5](commits/2540_7816de0f5/analysis.md) |
| 2541 | `8ce6b9529` | 2025-08-21 13:16:37 -0700 | Swapna Marru | Flink: support source parallelism config via property or hint (#13878) | ✅ 已完成 | [2541_8ce6b9529](commits/2541_8ce6b9529/analysis.md) |
| 2542 | `75ef4940d` | 2025-08-21 15:27:43 -0700 | Zach Dischner | Porting custom spark manifest rewrite order to spark 3.4 and 4.0 (#13893) | ✅ 已完成 | [2542_75ef4940d](commits/2542_75ef4940d/analysis.md) |
| 2543 | `c20550386` | 2025-08-21 15:59:30 -0700 | Prashant Singh | Spec, Core: Mark 503 as non retryable error code for Update Table (#13619) | ✅ 已完成 | [2543_c20550386](commits/2543_c20550386/analysis.md) |
| 2544 | `fd5d46982` | 2025-08-21 17:27:57 -0700 | Swapna Marru | Flink: Backport#13878 custom source parallelism (#13894) | ✅ 已完成 | [2544_fd5d46982](commits/2544_fd5d46982/analysis.md) |
| 2545 | `36bb82675` | 2025-08-22 06:46:12 +0200 | Yuya Ebihara | Spark: Fix errorprone warnings (#13896) | ✅ 已完成 | [2545_36bb82675](commits/2545_36bb82675/analysis.md) |
| 2546 | `32b86f34d` | 2025-08-22 11:40:11 +0200 | Yuya Ebihara | Spark 3.4, 3.5: Fix errorprone warnings (#13897) | ✅ 已完成 | [2546_32b86f34d](commits/2546_32b86f34d/analysis.md) |
| 2547 | `86665c9b8` | 2025-08-22 15:59:29 +0200 | slfan1989 | Flink: Refactor ZkLockFactory to improve code readability and maintainability. (#13795) | ✅ 已完成 | [2547_86665c9b8](commits/2547_86665c9b8/analysis.md) |
| 2548 | `b82dac485` | 2025-08-22 11:33:02 -0700 | Anurag Mantripragada | Spark 4.0: Fix source location in stats file copy plan in RewriteTablePathSparkAction (#13881) | ✅ 已完成 | [2548_b82dac485](commits/2548_b82dac485/analysis.md) |
| 2549 | `0e6633bce` | 2025-08-22 14:33:12 -0700 | Anatoly Popov | Fix S3InputStream.readFully connection leak (#13899) | ✅ 已完成 | [2549_0e6633bce](commits/2549_0e6633bce/analysis.md) |
| 2550 | `bfaafa7ee` | 2025-08-22 14:45:58 -0700 | Kevin Liu | spec: be explict about nullability of `LoadTableResult`'s `metadata-location` field (#13904) | ✅ 已完成 | [2550_bfaafa7ee](commits/2550_bfaafa7ee/analysis.md) |
| 2551 | `b7effe8df` | 2025-08-22 14:47:33 -0700 | Kevin Liu | update REST spec to clarify `AssertRefSnapshotId`'s `snapshot-id` field as required (#13902) | ✅ 已完成 | [2551_b7effe8df](commits/2551_b7effe8df/analysis.md) |
| 2552 | `6f0ecfc09` | 2025-08-24 08:58:43 +0200 | slfan1989 | Flink: Backport Refactor ZkLockFactory to improve code readability and maintainability. (#13906) | ✅ 已完成 | [2552_6f0ecfc09](commits/2552_6f0ecfc09/analysis.md) |
| 2553 | `1e9739de2` | 2025-08-24 09:18:20 +0200 | dependabot[bot] | Build: Bump nessie from 0.104.3 to 0.104.5 (#13910) | ✅ 已完成 | [2553_1e9739de2](commits/2553_1e9739de2/analysis.md) |
| 2554 | `adc02fd06` | 2025-08-24 09:19:58 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.32.24 to 2.32.29 (#13911) | ✅ 已完成 | [2554_adc02fd06](commits/2554_adc02fd06/analysis.md) |
| 2555 | `4e71502ef` | 2025-08-24 09:20:24 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.17 to 9.6.18 (#13912) | ✅ 已完成 | [2555_4e71502ef](commits/2555_4e71502ef/analysis.md) |
| 2556 | `59226cac9` | 2025-08-25 09:23:31 +0200 | ayushjariyal | Docs: Update API docs and remove `iceberg-hive3` reference (#13898) | ✅ 已完成 | [2556_59226cac9](commits/2556_59226cac9/analysis.md) |
| 2557 | `9c7ed0584` | 2025-08-25 12:39:29 +0200 | dependabot[bot] | Build: Bump software.amazon.s3.analyticsaccelerator:analyticsaccelerator-s3 (#13909) | ✅ 已完成 | [2557_9c7ed0584](commits/2557_9c7ed0584/analysis.md) |
| 2558 | `56fa9e5eb` | 2025-08-25 15:08:49 +0200 | Adam Szita | GCP: KeyManagementClient implementation that works with Google Cloud KMS (#13334) | ✅ 已完成 | [2558_56fa9e5eb](commits/2558_56fa9e5eb/analysis.md) |
| 2559 | `8f180d62c` | 2025-08-25 07:00:12 -0700 | Anurag Mantripragada | Spark 3.4, 3.5: Backport #13881 to fix source location in stats file copy plan in RewriteTablePathSparkAction | ✅ 已完成 | [2559_8f180d62c](commits/2559_8f180d62c/analysis.md) |
| 2560 | `360fb21d9` | 2025-08-25 08:05:22 -0600 | Anatoly Popov | AWS, Azure: Fix S3InputStream and ADLSInputStream connection leaks (#13905) | ✅ 已完成 | [2560_360fb21d9](commits/2560_360fb21d9/analysis.md) |
| 2561 | `2df7dc760` | 2025-08-25 16:28:23 +0200 | JeonDaehong | Docs: Add docs for Table Maintenance in Flink (#13853) | ✅ 已完成 | [2561_2df7dc760](commits/2561_2df7dc760/analysis.md) |
| 2562 | `ec3052b17` | 2025-08-25 10:58:36 -0500 | Shreyas | Docs: Add dltHub to list of vendors (#13920) | ✅ 已完成 | [2562_ec3052b17](commits/2562_ec3052b17/analysis.md) |
| 2563 | `5d5e0a355` | 2025-08-25 13:01:29 -0700 | Eric Maynard | Test both vectorized and nonvectorized readers in Parquet golden file tests (#13890) | ✅ 已完成 | [2563_5d5e0a355](commits/2563_5d5e0a355/analysis.md) |
| 2564 | `9f266917b` | 2025-08-26 07:19:13 -0700 | Steven Zhen Wu | Spec: clarify the partition-spec metadata for Avro manifest file (#13895) | ✅ 已完成 | [2564_9f266917b](commits/2564_9f266917b/analysis.md) |
| 2565 | `9b5327295` | 2025-08-27 10:11:13 -0700 | hsiang-c | Spark: (unit test) Order query result deterministically (#13891) | ✅ 已完成 | [2565_9b5327295](commits/2565_9b5327295/analysis.md) |
| 2566 | `2b66fc553` | 2025-08-27 13:23:38 -0700 | ismail simsek | Docs: Add Memiiso Debezium to third party integrations (#13773) | ✅ 已完成 | [2566_2b66fc553](commits/2566_2b66fc553/analysis.md) |
| 2567 | `28555ad8f` | 2025-08-27 20:37:25 -0600 | gtrettenero | Core: Parallelize determining of files to cleanup in IncrementalFileCleanup (#13926) | ✅ 已完成 | [2567_28555ad8f](commits/2567_28555ad8f/analysis.md) |
| 2568 | `1128040ef` | 2025-08-28 13:11:56 -0700 | Steven Zhen Wu | Flink: add unit test to check skewness across tasks for range partitioner (#13900) | ✅ 已完成 | [2568_1128040ef](commits/2568_1128040ef/analysis.md) |
| 2569 | `55159939b` | 2025-08-28 14:37:46 -0700 | slfan1989 | Flink: Improve ConfigOption Descriptions for Flink Table Maintenance Configs. (#13832) | ✅ 已完成 | [2569_55159939b](commits/2569_55159939b/analysis.md) |
| 2570 | `4294e9746` | 2025-08-28 18:53:52 -0500 | Badal Prasad Singh | Docs: OLake added to Vendors (#13931) | ✅ 已完成 | [2570_4294e9746](commits/2570_4294e9746/analysis.md) |
| 2571 | `a8726d3e1` | 2025-08-28 18:54:23 -0500 | Russell Spitzer | Arrow, Spark: Fix Direct Memory Leak on Vectorized Parquet Mixed Encoding Pages (#13935) | ✅ 已完成 | [2571_a8726d3e1](commits/2571_a8726d3e1/analysis.md) |
| 2572 | `07982104c` | 2025-08-28 17:01:20 -0700 | Fokko Driesprong | Spark 4: Read and write UnknownType (#13445) | ✅ 已完成 | [2572_07982104c](commits/2572_07982104c/analysis.md) |
| 2573 | `f2e5839e8` | 2025-08-28 20:41:59 -0700 | slfan1989 | Flink: Backport Improve ConfigOption Descriptions for Flink Table Maintenance Configs. (#13944) | ✅ 已完成 | [2573_f2e5839e8](commits/2573_f2e5839e8/analysis.md) |
| 2574 | `87cade9cf` | 2025-08-28 21:14:04 -0700 | Steven Zhen Wu | Flink: backport PR #13900 for adding unit test of skewness for range partitioner (#13943) | ✅ 已完成 | [2574_87cade9cf](commits/2574_87cade9cf/analysis.md) |
| 2575 | `f3e906240` | 2025-08-28 22:59:32 -0600 | JB Onofré | REST Spec: Update the max allowed table format version to 3 (#13505) | ✅ 已完成 | [2575_f3e906240](commits/2575_f3e906240/analysis.md) |
| 2576 | `f82cdd4cc` | 2025-08-29 07:25:49 -0700 | Fokko Driesprong | Build: Release with JDK17 (#13946) | ✅ 已完成 | [2576_f82cdd4cc](commits/2576_f82cdd4cc/analysis.md) |
| 2577 | `10fad0c7a` | 2025-08-29 13:05:55 -0500 | Badal Prasad Singh | Docs: Add OLake (ELT) (#13929) | ✅ 已完成 | [2577_10fad0c7a](commits/2577_10fad0c7a/analysis.md) |
| 2578 | `65dd3de4a` | 2025-08-29 11:22:31 -0700 | Raghav Mahajan | Only warn if OAuth2 server URI is not set (#13741) | ✅ 已完成 | [2578_65dd3de4a](commits/2578_65dd3de4a/analysis.md) |
| 2579 | `ecf0ac759` | 2025-08-29 11:23:39 -0700 | yguy-ryft | Docs: metadata deletion doc fix (#13432) | ✅ 已完成 | [2579_ecf0ac759](commits/2579_ecf0ac759/analysis.md) |
| 2580 | `9ca03da2c` | 2025-08-29 22:37:32 +0300 | GuoYu | Doc: Flink Maintenance add Delete OrphansFiles part (#13923) | ✅ 已完成 | [2580_9ca03da2c](commits/2580_9ca03da2c/analysis.md) |
| 2581 | `256a3a06e` | 2025-08-30 17:02:25 -0700 | Steven Zhen Wu | Build: add spark 4.0 to stage-binaries.sh (#13948) | ✅ 已完成 | [2581_256a3a06e](commits/2581_256a3a06e/analysis.md) |
| 2582 | `80809ce59` | 2025-08-31 10:30:46 -0700 | Steven Zhen Wu | Build: add gradle options --no-parallel and --no-configuration-cache to stage-binaries.sh (#13958) | ✅ 已完成 | [2582_80809ce59](commits/2582_80809ce59/analysis.md) |
| 2583 | `eb36ea62a` | 2025-09-01 05:11:50 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.32.29 to 2.33.0 (#13955) | ✅ 已完成 | [2583_eb36ea62a](commits/2583_eb36ea62a/analysis.md) |
| 2584 | `017744765` | 2025-09-01 05:12:07 +0200 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.26.0 to 3.26.1 (#13950) | ✅ 已完成 | [2584_017744765](commits/2584_017744765/analysis.md) |
| 2585 | `d8767f1b5` | 2025-08-31 22:32:04 -0700 | Kevin Liu | Release: add link to verify release (#13960) | ✅ 已完成 | [2585_d8767f1b5](commits/2585_d8767f1b5/analysis.md) |
| 2586 | `76ff67c65` | 2025-09-01 21:21:29 -0700 | Peter Nguyen | Update nessie version in docs to 0.104.5 (#13969) | ✅ 已完成 | [2586_76ff67c65](commits/2586_76ff67c65/analysis.md) |
| 2587 | `3c8da4f17` | 2025-09-02 12:30:43 -0500 | Russell Spitzer | Test, Spark: Use Single Splits to Improve the Speed of Rewrite Tests (#13947) | ✅ 已完成 | [2587_3c8da4f17](commits/2587_3c8da4f17/analysis.md) |
| 2588 | `8bcfa610a` | 2025-09-02 12:38:05 -0500 | Russell Spitzer | Data, Flink, Spark: Use TestHelpers for FormatVersion (#13880) | ✅ 已完成 | [2588_8bcfa610a](commits/2588_8bcfa610a/analysis.md) |
| 2589 | `fdd04c970` | 2025-09-02 16:41:56 -0700 | Steven Zhen Wu | Infra: update how-to-release.md doc on potential multiple staging repositories in a corp network with floating IPs for outbound requests (#13978) | ✅ 已完成 | [2589_fdd04c970](commits/2589_fdd04c970/analysis.md) |
| 2590 | `12ab7fc3d` | 2025-09-03 06:54:53 -0700 | Fokko Driesprong | Parquet: Bump Parquet-Java to 1.16.0 and use logical annotation for variant type (#13941) | ✅ 已完成 | [2590_12ab7fc3d](commits/2590_12ab7fc3d/analysis.md) |
| 2591 | `170189e17` | 2025-09-03 09:59:05 -0700 | Robin Moffatt | [docs] Add Kafka->Iceberg blog post to blogs.md (#13965) | ✅ 已完成 | [2591_170189e17](commits/2591_170189e17/analysis.md) |
| 2592 | `be03c998d` | 2025-09-03 17:50:03 -0700 | jackylee | Core: Use safeContainsKey to avoid NPE for CountNonNull (#13980) | ✅ 已完成 | [2592_be03c998d](commits/2592_be03c998d/analysis.md) |
| 2593 | `2a3305042` | 2025-09-04 11:07:03 -0500 | Huaxin Gao | Arrow: Add a precondition check in allocateFieldVector (#13949) | ✅ 已完成 | [2593_2a3305042](commits/2593_2a3305042/analysis.md) |
| 2594 | `c64c89fbb` | 2025-09-04 09:31:18 -0700 | emkornfield | [SPEC] Add implementation note about schema evolution (#13936) | ✅ 已完成 | [2594_c64c89fbb](commits/2594_c64c89fbb/analysis.md) |
| 2595 | `8a6b56474` | 2025-09-04 09:58:31 -0700 | Anatoly Popov | Cleanup TestS3OutputStream integration tests by delegating file cleanup to JUnit (#13983) | ✅ 已完成 | [2595_8a6b56474](commits/2595_8a6b56474/analysis.md) |
| 2596 | `a22d08c23` | 2025-09-04 10:38:29 -0700 | Steven Zhen Wu | Spark: use lookback address for spark session in tests to work with more restrictive firewall env on dev machines (#13993) | ✅ 已完成 | [2596_a22d08c23](commits/2596_a22d08c23/analysis.md) |
| 2597 | `173d4c328` | 2025-09-04 12:34:46 -0700 | Steven Zhen Wu | Spark: backport PR #13993 to use loopback address for spark driver in tests (#13994) | ✅ 已完成 | [2597_173d4c328](commits/2597_173d4c328/analysis.md) |
| 2598 | `7c9556b7c` | 2025-09-04 19:32:24 -0700 | jackylee | REST: Fix port binding problem with TIME_WAIT for RESTCatalogServer in test fixture (#13992) | ✅ 已完成 | [2598_7c9556b7c](commits/2598_7c9556b7c/analysis.md) |
| 2599 | `2114bf631` | 2025-09-05 10:53:00 -0700 | JB Onofré | Fix versions in LICENSE and NOTICE files. (#14001) | ✅ 已完成 | [2599_2114bf631](commits/2599_2114bf631/analysis.md) |
| 2600 | `57ec535d0` | 2025-09-05 13:00:07 -0700 | emkornfield | Revert "[SPEC] Add implementation note about schema evolution (#13936)" (#14002) | ✅ 已完成 | [2600_57ec535d0](commits/2600_57ec535d0/analysis.md) |
| 2601 | `7b9ea75eb` | 2025-09-06 15:09:38 -0700 | Anatoly Popov | Fixing AssertJ assertions in TestS3FileIOProperties (#14005) | ✅ 已完成 | [2601_7b9ea75eb](commits/2601_7b9ea75eb/analysis.md) |
| 2602 | `dd35839a7` | 2025-09-07 20:07:48 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.4.Final to 4.2.5.Final (#14008) | ✅ 已完成 | [2602_dd35839a7](commits/2602_dd35839a7/analysis.md) |
| 2603 | `bd15ba551` | 2025-09-07 20:44:28 +0200 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.37 to 1.2.38 (#14010) | ✅ 已完成 | [2603_bd15ba551](commits/2603_bd15ba551/analysis.md) |
| 2604 | `b9014648d` | 2025-09-07 22:14:44 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.66.0 to 26.67.0 (#13952) | ✅ 已完成 | [2604_b9014648d](commits/2604_b9014648d/analysis.md) |
| 2605 | `485c2651e` | 2025-09-07 14:18:30 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.33.0 to 2.33.4 (#14009) | ✅ 已完成 | [2605_485c2651e](commits/2605_485c2651e/analysis.md) |
| 2606 | `c8541db1c` | 2025-09-07 16:12:20 -0700 | drexler-sky | Bump Spark version to 4.0.1 (#14019) | ✅ 已完成 | [2606_c8541db1c](commits/2606_c8541db1c/analysis.md) |
| 2607 | `f0a030a7c` | 2025-09-08 08:39:48 +0200 | Manu Zhang | Site: Bump up mkdocs-monorepo-plugin to 1.1.2 (#14015) | ✅ 已完成 | [2607_f0a030a7c](commits/2607_f0a030a7c/analysis.md) |
| 2608 | `76f21d8ac` | 2025-09-08 13:54:31 +0200 | Robin Moffatt | [docs] Move third-party integrations to root level of left-hand nav, add more catalogs (#13753) | ✅ 已完成 | [2608_76f21d8ac](commits/2608_76f21d8ac/analysis.md) |
| 2609 | `9ea3b136c` | 2025-09-08 14:00:06 +0200 | gaborkaszab | Core: Extended header support for RESTClient implementations (#12194) | ✅ 已完成 | [2609_9ea3b136c](commits/2609_9ea3b136c/analysis.md) |
| 2610 | `f35e88da4` | 2025-09-08 17:14:12 +0200 | Manu Zhang | Revert "Site: Bump up mkdocs-monorepo-plugin to 1.1.2 (#14015)" (#14022) | ✅ 已完成 | [2610_f35e88da4](commits/2610_f35e88da4/analysis.md) |
| 2611 | `300532cd3` | 2025-09-08 17:29:27 +0200 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14011) | ✅ 已完成 | [2611_300532cd3](commits/2611_300532cd3/analysis.md) |
| 2612 | `bc56756f6` | 2025-09-08 08:30:49 -0700 | slfan1989 | Spark 4.0: Add Support for PartitionStatistics Files in RewriteTablePath. (#13956) | ✅ 已完成 | [2612_bc56756f6](commits/2612_bc56756f6/analysis.md) |
| 2613 | `4f48b5166` | 2025-09-08 08:33:25 -0700 | Fokko Driesprong | docs: flink: Fix updated link (#14024) | ✅ 已完成 | [2613_4f48b5166](commits/2613_4f48b5166/analysis.md) |
| 2614 | `8871bbcf4` | 2025-09-08 19:59:18 +0200 | Manu Zhang | Build: Add Docs Build CI (#14025) | ✅ 已完成 | [2614_8871bbcf4](commits/2614_8871bbcf4/analysis.md) |
| 2615 | `d731489b4` | 2025-09-08 13:59:23 -0700 | Alex Prosak | Spark 3.5: Support Trigger AvailableNow in Structured Streaming (#13824) | ✅ 已完成 | [2615_d731489b4](commits/2615_d731489b4/analysis.md) |
| 2616 | `ea2071568` | 2025-09-09 10:47:21 -0500 | Yuya Ebihara | Test: Avoid running redundant tests (#14036) | ✅ 已完成 | [2616_ea2071568](commits/2616_ea2071568/analysis.md) |
| 2617 | `24ca356fb` | 2025-09-09 10:13:25 -0700 | Alex Prosak | Spark 4.0, 3.4: Backport #13824 to Support Trigger AvailableNow in SS (#14026) | ✅ 已完成 | [2617_24ca356fb](commits/2617_24ca356fb/analysis.md) |
| 2618 | `720ef9972` | 2025-09-09 20:16:06 +0200 | Fokko Driesprong | Remove BladePipe due to broken links (#14033) | ✅ 已完成 | [2618_720ef9972](commits/2618_720ef9972/analysis.md) |
| 2619 | `a5f6f449b` | 2025-09-11 11:23:27 +0200 | Maximilian Michels | Flink: Fix flaky tests for Iceberg sink and improve assert description (#14044) | ✅ 已完成 | [2619_a5f6f449b](commits/2619_a5f6f449b/analysis.md) |
| 2620 | `d1771207c` | 2025-09-11 14:10:57 +0200 | Maximilian Michels | Flink: Backport fix flaky tests for Iceberg sink (#14050) | ✅ 已完成 | [2620_d1771207c](commits/2620_d1771207c/analysis.md) |
| 2621 | `46ab802cd` | 2025-09-11 18:18:48 +0200 | gaborkaszab | Core: Rename `resp` to `response` in RESTCatalogAdapter (#14051) | ✅ 已完成 | [2621_46ab802cd](commits/2621_46ab802cd/analysis.md) |
| 2622 | `3448ca228` | 2025-09-11 11:27:10 -0700 | Steven Zhen Wu | Site, Build: finalize 1.10.0 release with notes and update revapi and links (#13996) | ✅ 已完成 | [2622_3448ca228](commits/2622_3448ca228/analysis.md) |
| 2623 | `cced19af0` | 2025-09-11 11:35:02 -0700 | kumarpritam863 | Compulsorily close coordinator if task is stopped by the connect framework. (#13756) | ✅ 已完成 | [2623_cced19af0](commits/2623_cced19af0/analysis.md) |
| 2624 | `4e154bd4e` | 2025-09-11 14:46:09 -0500 | JeonDaehong | Docs: Fix style of row-level deletes in spec | ✅ 已完成 | [2624_4e154bd4e](commits/2624_4e154bd4e/analysis.md) |
| 2625 | `7a62efe3e` | 2025-09-11 22:01:28 +0200 | Fokko Driesprong | docs: Add link to BladePipe (#14047) | ✅ 已完成 | [2625_7a62efe3e](commits/2625_7a62efe3e/analysis.md) |
| 2626 | `b5ca14757` | 2025-09-11 15:45:55 -0700 | Steven Zhen Wu | Site: fix up 1.10.0 release notes (#14055) | ✅ 已完成 | [2626_b5ca14757](commits/2626_b5ca14757/analysis.md) |
| 2627 | `38b5d7a82` | 2025-09-11 17:43:24 -0700 | Raveendra Pujari | Docs: Remove extra `\` in Kafka Connect Configuration docs (#13240) | ✅ 已完成 | [2627_38b5d7a82](commits/2627_38b5d7a82/analysis.md) |
| 2628 | `723d0998d` | 2025-09-12 15:26:48 +0200 | Maximilian Michels | Docs: Update supported Flink versions (#13611) | ✅ 已完成 | [2628_723d0998d](commits/2628_723d0998d/analysis.md) |
| 2629 | `05cd8b52e` | 2025-09-12 19:54:14 +0200 | Christian | fix: Make TableMetadataV3ValidMinimal actually v3 (#14061) | ✅ 已完成 | [2629_05cd8b52e](commits/2629_05cd8b52e/analysis.md) |
| 2630 | `f084616ed` | 2025-09-14 18:43:24 +0200 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.5.Final to 4.2.6.Final (#14075) | ✅ 已完成 | [2630_f084616ed](commits/2630_f084616ed/analysis.md) |
| 2631 | `023fa3e03` | 2025-09-14 18:50:31 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.33.4 to 2.33.9 (#14073) | ✅ 已完成 | [2631_023fa3e03](commits/2631_023fa3e03/analysis.md) |
| 2632 | `b806966ec` | 2025-09-14 19:58:06 +0200 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.18 to 9.6.19 (#14074) | ✅ 已完成 | [2632_b806966ec](commits/2632_b806966ec/analysis.md) |
| 2633 | `dec44392c` | 2025-09-15 08:51:19 +0200 | Nándor Kollár | Arrow: delete outdated comment (#14068) | ✅ 已完成 | [2633_dec44392c](commits/2633_dec44392c/analysis.md) |
| 2634 | `5112ec998` | 2025-09-15 08:56:13 +0200 | Yuya Ebihara | Core: Don't copy stats of delete files in DeleteFileIndex (#13161) | ✅ 已完成 | [2634_5112ec998](commits/2634_5112ec998/analysis.md) |
| 2635 | `4e9f9ccac` | 2025-09-15 09:54:40 +0200 | gaborkaszab | Core: Move properties of REST catalog into RESTCatalogProperties (#13991) | ✅ 已完成 | [2635_4e9f9ccac](commits/2635_4e9f9ccac/analysis.md) |
| 2636 | `e0da08ca5` | 2025-09-15 10:04:41 +0200 | pvary | Spec: Deprecate Position delete files with row data (#14045) | ✅ 已完成 | [2636_e0da08ca5](commits/2636_e0da08ca5/analysis.md) |
| 2637 | `088efad23` | 2025-09-15 10:15:34 +0200 | smaheshwar-pltr | Azure: Don't fetch credential from endpoint if properties contain a valid credential (#13966) | ✅ 已完成 | [2637_088efad23](commits/2637_088efad23/analysis.md) |
| 2638 | `3e1ea48d3` | 2025-09-15 14:38:33 -0500 | Manikandan R | API: Enables sanitizing Variant data type #11479 (#13137) | ✅ 已完成 | [2638_3e1ea48d3](commits/2638_3e1ea48d3/analysis.md) |
| 2639 | `754679ddc` | 2025-09-15 15:31:08 -0700 | Kevin Liu | docs: use * for path expansion instead of hardcoding version (#13989) | ✅ 已完成 | [2639_754679ddc](commits/2639_754679ddc/analysis.md) |
| 2640 | `b38423d61` | 2025-09-15 17:37:51 -0700 | Steven Zhen Wu | Infra: update how-to-release doc and site scripts on releasing versioned doc and javadoc based on learnings from 1.10.0 release (#14066) | ✅ 已完成 | [2640_b38423d61](commits/2640_b38423d61/analysis.md) |
| 2641 | `96040dc60` | 2025-09-15 18:19:16 -0700 | Steven Zhen Wu | Site: change default remote name back to origin. otherwise, site CI could fail. (#14086) | ✅ 已完成 | [2641_96040dc60](commits/2641_96040dc60/analysis.md) |
| 2642 | `9cc4f5b96` | 2025-09-15 18:47:41 -0700 | Steven Zhen Wu | Infra: add .sdkmanrc to .gitignore file (#14085) | ✅ 已完成 | [2642_9cc4f5b96](commits/2642_9cc4f5b96/analysis.md) |
| 2643 | `3bbdee97b` | 2025-09-16 09:08:14 -0700 | slfan1989 | Spark 3.4: Backport: Add procedure and action to compute partition stats. (#14034) | ✅ 已完成 | [2643_3bbdee97b](commits/2643_3bbdee97b/analysis.md) |
| 2644 | `b8c3e2072` | 2025-09-16 09:51:35 -0700 | Alex Prosak | Docs: Update Spark Structured Streaming docs for Rate Limiting & Triggers (#14030) | ✅ 已完成 | [2644_b8c3e2072](commits/2644_b8c3e2072/analysis.md) |
| 2645 | `ee90c10e3` | 2025-09-16 12:35:42 -0500 | fivetran-caseykarst | Site: Updates vendors.md adding Fivetran (#14088) | ✅ 已完成 | [2645_ee90c10e3](commits/2645_ee90c10e3/analysis.md) |
| 2646 | `b7ee7c074` | 2025-09-17 07:29:01 -0700 | Eric Maynard | Backport Parquet encoding tests for Spark 3.5 (#13859) | ✅ 已完成 | [2646_b7ee7c074](commits/2646_b7ee7c074/analysis.md) |
| 2647 | `ade12635b` | 2025-09-17 09:44:24 -0700 | jackylee | Core: Add CountNull Aggregation Support (#13981) | ✅ 已完成 | [2647_ade12635b](commits/2647_ade12635b/analysis.md) |
| 2648 | `87fce5fbe` | 2025-09-17 09:59:15 -0700 | Gabriel Igliozzi | Core: Allow reading metadata table when scanning table with dropped partition source field (#14089) | ✅ 已完成 | [2648_87fce5fbe](commits/2648_87fce5fbe/analysis.md) |
| 2649 | `7eef46da0` | 2025-09-17 10:15:26 -0700 | slfan1989 | Spark 3.4, 3.5: Backport Add Support for PartitionStatistics Files in RewriteTablePath. (#14032) | ✅ 已完成 | [2649_7eef46da0](commits/2649_7eef46da0/analysis.md) |
| 2650 | `7a4b69fff` | 2025-09-17 23:27:06 -0400 | Hang Chen | Add StreamNative to the vendor list (#14097) | ✅ 已完成 | [2650_7a4b69fff](commits/2650_7a4b69fff/analysis.md) |
| 2651 | `1d558a9d3` | 2025-09-17 20:43:32 -0700 | Steven Zhen Wu | Spec: bring back added-rows in snapshot fields (#14048) | ✅ 已完成 | [2651_1d558a9d3](commits/2651_1d558a9d3/analysis.md) |
| 2652 | `345140e9f` | 2025-09-18 16:35:17 -0600 | Doğukan Çağatay | AWS, Core, Data, Spark: Remove deprecations for 1.11.0 (#14059) | ✅ 已完成 | [2652_345140e9f](commits/2652_345140e9f/analysis.md) |
| 2653 | `c05f6c98d` | 2025-09-19 08:53:17 +0200 | Dejan Gvozdenac | API: required nested fields within optional structs can produce null (#13804) | ✅ 已完成 | [2653_c05f6c98d](commits/2653_c05f6c98d/analysis.md) |
| 2654 | `2de7a7a91` | 2025-09-19 11:09:49 +0200 | Yuya Ebihara | Core: Make deprecated methods package-private in PartitionStats (#14119) | ✅ 已完成 | [2654_2de7a7a91](commits/2654_2de7a7a91/analysis.md) |
| 2655 | `8d88846ec` | 2025-09-19 11:11:02 +0200 | Ron Kapoor | Docs: Add Spark 4.0 to lifecycle status (#14116) | ✅ 已完成 | [2655_8d88846ec](commits/2655_8d88846ec/analysis.md) |
| 2656 | `ded2d0a94` | 2025-09-19 07:36:48 -0700 | Maximilian Michels | Flink: Move Flink v2.0 to v2.1 directory | ✅ 已完成 | [2656_ded2d0a94](commits/2656_ded2d0a94/analysis.md) |
| 2657 | `f4d892e2c` | 2025-09-19 07:36:48 -0700 | Maximilian Michels | Flink: Add back v2.0 directory | ✅ 已完成 | [2657_f4d892e2c](commits/2657_f4d892e2c/analysis.md) |
| 2658 | `58da08695` | 2025-09-19 07:36:48 -0700 | Maximilian Michels | Flink: Adjust build scripts for Flink 2.1 | ✅ 已完成 | [2658_58da08695](commits/2658_58da08695/analysis.md) |
| 2659 | `c56f49799` | 2025-09-19 07:36:48 -0700 | Maximilian Michels | Flink: Code changes for Flink 2.1 | ✅ 已完成 | [2659_c56f49799](commits/2659_c56f49799/analysis.md) |
| 2660 | `487ea1ae9` | 2025-09-19 07:36:48 -0700 | Maximilian Michels | Flink: Remove support for Flink 1.19 | ✅ 已完成 | [2660_487ea1ae9](commits/2660_487ea1ae9/analysis.md) |
| 2661 | `804dd865e` | 2025-09-19 09:03:18 -0700 | Robert Stupp | Add "stop signs" for sensitive information/issues to all issue templates (#14103) | ✅ 已完成 | [2661_804dd865e](commits/2661_804dd865e/analysis.md) |
| 2662 | `a73a5c091` | 2025-09-19 09:05:09 -0700 | Robert Stupp | Add "stop signs" for sensitive information/issues to all issue templates (#14103) | ✅ 已完成 | [2662_a73a5c091](commits/2662_a73a5c091/analysis.md) |
| 2663 | `998eb87ee` | 2025-09-19 13:46:19 -0700 | Thomas | BigQuery: Add table validity check for BigQueryMetastoreCatalog (#14113) | ✅ 已完成 | [2663_998eb87ee](commits/2663_998eb87ee/analysis.md) |
| 2664 | `b53d97d3b` | 2025-09-19 14:10:43 -0700 | Hongyue/Steve Zhang | [Core] Add mergeAppendTest to ensure consist distribution of data files in manifests (#14111) | ✅ 已完成 | [2664_b53d97d3b](commits/2664_b53d97d3b/analysis.md) |
| 2665 | `b99caa4aa` | 2025-09-20 10:01:08 -0600 | Prashant Singh | Core: Fix Scan Plan API resource paths (#14120) | ✅ 已完成 | [2665_b99caa4aa](commits/2665_b99caa4aa/analysis.md) |
| 2666 | `29802c3c9` | 2025-09-20 10:36:55 -0700 | slfan1989 | Build: Bump hadoop from 3.4.1 to 3.4.2. (#14125) | ✅ 已完成 | [2666_29802c3c9](commits/2666_29802c3c9/analysis.md) |
| 2667 | `fb63af014` | 2025-09-20 12:09:10 -0600 | Drew Gallardo | Parquet, Data, Spark: Fix variant type filtering in ParquetMetricsRowGroupFilter (#14081) | ✅ 已完成 | [2667_fb63af014](commits/2667_fb63af014/analysis.md) |
| 2668 | `91f457e0c` | 2025-09-20 22:31:58 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.33.9 to 2.34.0 (#14133) | ✅ 已完成 | [2668_91f457e0c](commits/2668_91f457e0c/analysis.md) |
| 2669 | `de8c10632` | 2025-09-20 22:32:19 -0700 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.19 to 9.6.20 (#14131) | ✅ 已完成 | [2669_de8c10632](commits/2669_de8c10632/analysis.md) |
| 2670 | `4cb98f22d` | 2025-09-20 22:33:48 -0700 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.27.4 to 3.27.5 (#14130) | ✅ 已完成 | [2670_4cb98f22d](commits/2670_4cb98f22d/analysis.md) |
| 2671 | `11f3dcb30` | 2025-09-22 08:46:34 -0700 | ggershinsky | Manifest list encryption (#7770) | ✅ 已完成 | [2671_11f3dcb30](commits/2671_11f3dcb30/analysis.md) |
| 2672 | `16fa67378` | 2025-09-22 10:58:12 -0700 | Manu Zhang | Flink: Add support for Flink 2.1.0 follow-up (#14156) | ✅ 已完成 | [2672_16fa67378](commits/2672_16fa67378/analysis.md) |
| 2673 | `6b80e5c42` | 2025-09-23 10:11:20 +0200 | dependabot[bot] | Build: Bump guava from 33.4.8-jre to 33.5.0-jre (#14128) | ✅ 已完成 | [2673_6b80e5c42](commits/2673_6b80e5c42/analysis.md) |
| 2674 | `6829c3e3d` | 2025-09-23 15:11:43 +0200 | GuoYu | Flink: add _row_id and _last_updated_sequence_number readers (#14148) | ✅ 已完成 | [2674_6829c3e3d](commits/2674_6829c3e3d/analysis.md) |
| 2675 | `a71f521d2` | 2025-09-23 09:15:49 -0600 | Fokko Driesprong | Spark 4.0: Pass `format-version` when creating a snapshot in table migration actions (#14163) | ✅ 已完成 | [2675_a71f521d2](commits/2675_a71f521d2/analysis.md) |
| 2676 | `2034b79df` | 2025-09-23 09:34:15 -0600 | GuoYu | Flink: Backport add _row_id and _last_updated_sequence_number readers to 2.1 and 1.20 (#14168) | ✅ 已完成 | [2676_2034b79df](commits/2676_2034b79df/analysis.md) |
| 2677 | `76f7c812a` | 2025-09-23 21:55:01 +0200 | Fokko Driesprong | Spark 3.5: Pass format-version when creating a snapshot in table migration actions (#14169) | ✅ 已完成 | [2677_76f7c812a](commits/2677_76f7c812a/analysis.md) |
| 2678 | `d09815b0e` | 2025-09-23 21:55:12 +0200 | Fokko Driesprong | Spark 3.4: Pass format-version when creating a snapshot (#14170) | ✅ 已完成 | [2678_d09815b0e](commits/2678_d09815b0e/analysis.md) |
| 2679 | `1ed0a755f` | 2025-09-24 00:30:27 +0200 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#14132) | ✅ 已完成 | [2679_1ed0a755f](commits/2679_1ed0a755f/analysis.md) |
| 2680 | `5f30e4fae` | 2025-09-24 17:46:42 +0200 | slfan1989 | API, Spark 4.0: Add create_file_list option to RewriteTablePathProcedure. (#13837) | ✅ 已完成 | [2680_5f30e4fae](commits/2680_5f30e4fae/analysis.md) |
| 2681 | `16c9dd6c0` | 2025-09-24 17:48:10 +0200 | slfan1989 | Spark 4.0: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#13913) | ✅ 已完成 | [2681_16c9dd6c0](commits/2681_16c9dd6c0/analysis.md) |
| 2682 | `ce85b8f74` | 2025-09-25 08:24:27 +0200 | Piyush Dubey | Docs: Add REST catalog authentication properties (#14065) | ✅ 已完成 | [2682_ce85b8f74](commits/2682_ce85b8f74/analysis.md) |
| 2683 | `317ebe53e` | 2025-09-25 08:26:40 +0200 | slfan1989 | Docs: Enhance RewriteTablePath procedure documentation with parameter details. (#14181) | ✅ 已完成 | [2683_317ebe53e](commits/2683_317ebe53e/analysis.md) |
| 2684 | `fbb7136fa` | 2025-09-25 16:27:30 +0200 | slfan1989 | Spark 3.5: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#14179) | ✅ 已完成 | [2684_fbb7136fa](commits/2684_fbb7136fa/analysis.md) |
| 2685 | `df01d88c9` | 2025-09-25 13:44:45 -0600 | Denys Kuzmenko | Parquet: Expose variantShreddingFunc() in Parquet.DataWriteBuilder (#14153) | ✅ 已完成 | [2685_df01d88c9](commits/2685_df01d88c9/analysis.md) |
| 2686 | `ccef3f4f4` | 2025-09-25 17:12:18 -0700 | slfan1989 | Spark 3.4: Refactor Spark procedures to consistently use ProcedureInput for parameter handling. (#14185) | ✅ 已完成 | [2686_ccef3f4f4](commits/2686_ccef3f4f4/analysis.md) |
| 2687 | `e8f0855db` | 2025-09-25 23:55:26 -0700 | Manu Zhang | Spark 3.5: Upgrade to Spark 3.5.7 (#14114) | ✅ 已完成 | [2687_e8f0855db](commits/2687_e8f0855db/analysis.md) |
| 2688 | `85724f89d` | 2025-09-26 14:22:17 +0200 | Rodrigo | Flink: Adds uid-suffix write option to prevent operator UID hash collisions (#14063) | ✅ 已完成 | [2688_85724f89d](commits/2688_85724f89d/analysis.md) |
| 2689 | `d9f096d20` | 2025-09-26 08:45:58 -0700 | slfan1989 | Spark 3.4,3.5: Backport: Add create_file_list option to RewriteTablePathProcedure. (#14180) | ✅ 已完成 | [2689_d9f096d20](commits/2689_d9f096d20/analysis.md) |
| 2690 | `2a49d93e5` | 2025-09-26 13:41:51 -0700 | Christian | REST: Add missing "added-rows" field to snapshot metadata (#14177) | ✅ 已完成 | [2690_2a49d93e5](commits/2690_2a49d93e5/analysis.md) |
| 2691 | `582d63b52` | 2025-09-27 12:19:30 -0700 | Marius Grama | Add reference to the Starburst connector (#14188) | ✅ 已完成 | [2691_582d63b52](commits/2691_582d63b52/analysis.md) |
| 2692 | `69b5caaa9` | 2025-09-28 12:28:40 +0200 | Rodrigo | Flink: Backport add uid-suffix write option to prevent operator UID hash collisions (#14193) | ✅ 已完成 | [2692_69b5caaa9](commits/2692_69b5caaa9/analysis.md) |
| 2693 | `5a05c79a0` | 2025-09-29 08:58:03 +0200 | dependabot[bot] | Build: Bump org.immutables:value from 2.11.3 to 2.11.4 (#14205) | ✅ 已完成 | [2693_5a05c79a0](commits/2693_5a05c79a0/analysis.md) |
| 2694 | `29920daf5` | 2025-09-29 08:58:23 +0200 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#14202) | ✅ 已完成 | [2694_29920daf5](commits/2694_29920daf5/analysis.md) |
| 2695 | `f0b34c0e0` | 2025-09-29 08:58:39 +0200 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.27.5 to 3.27.6 (#14201) | ✅ 已完成 | [2695_f0b34c0e0](commits/2695_f0b34c0e0/analysis.md) |
| 2696 | `124d29ef7` | 2025-09-29 09:00:13 +0200 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.34.0 to 2.34.5 (#14206) | ✅ 已完成 | [2696_124d29ef7](commits/2696_124d29ef7/analysis.md) |
| 2697 | `1f72ec14d` | 2025-09-29 09:00:36 +0200 | dependabot[bot] | Build: Bump nessie from 0.104.5 to 0.105.3 (#14204) | ✅ 已完成 | [2697_1f72ec14d](commits/2697_1f72ec14d/analysis.md) |
| 2698 | `c95dbef9a` | 2025-09-29 14:00:09 +0200 | Alessandro Nori | Docs: Document max-files-to-rewrite in Spark rewrite_data_files (#14211) | ✅ 已完成 | [2698_c95dbef9a](commits/2698_c95dbef9a/analysis.md) |
| 2699 | `3860284b7` | 2025-09-29 14:29:10 +0200 | Maximilian Michels | Flink: Ensure DynamicCommitter Idempotence in the presence of failures (#14182) | ✅ 已完成 | [2699_3860284b7](commits/2699_3860284b7/analysis.md) |
| 2700 | `9281c1b6e` | 2025-09-29 15:29:18 +0200 | Manu Zhang | AWS, Spark, Flink: Remove `org.jetbrains.annotations` (#14192) | ✅ 已完成 | [2700_9281c1b6e](commits/2700_9281c1b6e/analysis.md) |
| 2701 | `6ee8252be` | 2025-09-29 15:41:45 +0200 | gaborkaszab | Core: Implement refs snapshot mode in reference IRC (#14060) | ✅ 已完成 | [2701_6ee8252be](commits/2701_6ee8252be/analysis.md) |
| 2702 | `441597e22` | 2025-09-29 08:55:34 -0700 | Maximilian Michels | Flink: Backport #14182: Ensure DynamicCommitter Idempotence in the presence of failures (#14213) | ✅ 已完成 | [2702_441597e22](commits/2702_441597e22/analysis.md) |
| 2703 | `044233985` | 2025-09-29 13:19:48 -0700 | dependabot[bot] | Build: Bump mkdocs-macros-plugin from 1.3.9 to 1.4.0 (#14203) | ✅ 已完成 | [2703_044233985](commits/2703_044233985/analysis.md) |
| 2704 | `f9a41688e` | 2025-09-29 23:04:01 -0500 | Russell Spitzer | Parquet, Core: Allows Internal Parquet Readers to use Custom Types (#14040) | ✅ 已完成 | [2704_f9a41688e](commits/2704_f9a41688e/analysis.md) |
| 2705 | `8ae75596c` | 2025-09-30 08:24:55 +0200 | S N Munendra | Azure: Add support to specify token credential provider (#14136) | ✅ 已完成 | [2705_8ae75596c](commits/2705_8ae75596c/analysis.md) |
| 2706 | `d1fddd8d6` | 2025-09-30 07:53:42 -0700 | Russell Spitzer | Site: Remove Blogs and Talks From Site (#14110) | ✅ 已完成 | [2706_d1fddd8d6](commits/2706_d1fddd8d6/analysis.md) |
| 2707 | `a2ac14180` | 2025-09-30 08:21:24 -0700 | stubz151 | core: Adding read vector to range readable interface and adding mappe… (#13997) | ✅ 已完成 | [2707_a2ac14180](commits/2707_a2ac14180/analysis.md) |
| 2708 | `00d18e38f` | 2025-09-30 08:50:19 -0700 | Yubo Xu | Core: Handle unpartitioned check for a spec with all void transforms in replace partitions (#14186) | ✅ 已完成 | [2708_00d18e38f](commits/2708_00d18e38f/analysis.md) |
| 2709 | `5c6629ef9` | 2025-09-30 17:03:14 -0600 | Daniel Weeks | AWS: exclude logging classes from bundle (#14225) | ✅ 已完成 | [2709_5c6629ef9](commits/2709_5c6629ef9/analysis.md) |
| 2710 | `2b9a7e3fd` | 2025-10-01 08:34:43 +0200 | Владислав Самороков | BigQuery: Add iceberg-bigquery dependency to spark and flink build scripts (#14221) | ✅ 已完成 | [2710_2b9a7e3fd](commits/2710_2b9a7e3fd/analysis.md) |
| 2711 | `6d5951149` | 2025-10-01 22:39:22 -0600 | Alexandre Dutra | REST, OAuth2: Remove deprecated RefreshingAuthManager (#14229) | ✅ 已完成 | [2711_6d5951149](commits/2711_6d5951149/analysis.md) |
| 2712 | `6fd5e29f3` | 2025-10-02 08:02:58 +0200 | Hugo Wong-Berard | Spark: Log failed catalog load in Spark3Util::catalogAndIdentifier (#14183) | ✅ 已完成 | [2712_6fd5e29f3](commits/2712_6fd5e29f3/analysis.md) |
| 2713 | `a6ea2e38a` | 2025-10-02 09:30:56 -0700 | Kevin Liu | infra: add analytics for iceberg.apache.org (#14158) | ✅ 已完成 | [2713_a6ea2e38a](commits/2713_a6ea2e38a/analysis.md) |
| 2714 | `042f01a24` | 2025-10-02 13:50:53 -0700 | slfan1989 | Build: Bump runner image from Ubuntu 22.04 to 24.04. (#14240) | ✅ 已完成 | [2714_042f01a24](commits/2714_042f01a24/analysis.md) |
| 2715 | `990ceca86` | 2025-10-04 23:10:14 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.34.5 to 2.35.0 (#14252) | ✅ 已完成 | [2715_990ceca86](commits/2715_990ceca86/analysis.md) |
| 2716 | `4f0f9f397` | 2025-10-04 23:11:06 -0700 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14253) | ✅ 已完成 | [2716_4f0f9f397](commits/2716_4f0f9f397/analysis.md) |
| 2717 | `859edb6b2` | 2025-10-04 23:12:02 -0700 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.20 to 9.6.21 (#14255) | ✅ 已完成 | [2717_859edb6b2](commits/2717_859edb6b2/analysis.md) |
| 2718 | `a9a237fd7` | 2025-10-05 09:33:12 -0700 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.2.38 to 1.3.0 (#14257) | ✅ 已完成 | [2718_a9a237fd7](commits/2718_a9a237fd7/analysis.md) |
| 2719 | `fa93604c8` | 2025-10-06 14:27:40 +0200 | Yuya Ebihara | Build: Bump jackson-bom from 2.19.2 to 2.20.0 and jackson-annotations to 2.20 (#13961) | ✅ 已完成 | [2719_fa93604c8](commits/2719_fa93604c8/analysis.md) |
| 2720 | `840497e3d` | 2025-10-07 07:43:59 -0700 | Kevin Liu | Spark 3.4: Deprecate support (#14099) | ✅ 已完成 | [2720_840497e3d](commits/2720_840497e3d/analysis.md) |
| 2721 | `620892cd7` | 2025-10-07 18:41:05 -0700 | Tom Tanaka | Core, Spark: Deprecate `write.metadata.path` and use `location` to customize view location (#14212) | ✅ 已完成 | [2721_620892cd7](commits/2721_620892cd7/analysis.md) |
| 2722 | `8bb66f707` | 2025-10-08 08:20:35 -0700 | Eduard Tudenhoefner | Core: Deprecate Namespace Joiner/Splitter and use separate methods (#14274) | ✅ 已完成 | [2722_8bb66f707](commits/2722_8bb66f707/analysis.md) |
| 2723 | `533a75c8a` | 2025-10-08 08:41:55 -0700 | Huaxin Gao | OpenAPI: Fix inconsistent error example names (#14248) | ✅ 已完成 | [2723_533a75c8a](commits/2723_533a75c8a/analysis.md) |
| 2724 | `e70832cbd` | 2025-10-08 17:46:46 +0200 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.67.0 to 26.68.0 (#14134) | ✅ 已完成 | [2724_e70832cbd](commits/2724_e70832cbd/analysis.md) |
| 2725 | `9be7c1820` | 2025-10-08 23:10:28 -0600 | Aihua Xu | Parquet: Handle NPE for VariantLogicalType in TypeWithSchemaVisitor (#14261) | ✅ 已完成 | [2725_9be7c1820](commits/2725_9be7c1820/analysis.md) |
| 2726 | `ca8fd16c4` | 2025-10-09 00:02:55 -0700 | Shohei Okumiya | Docs: Mention Hive 4.1 (#14282) | ✅ 已完成 | [2726_ca8fd16c4](commits/2726_ca8fd16c4/analysis.md) |
| 2727 | `9ee4d023f` | 2025-10-09 07:16:23 -0700 | Manu Zhang | Build: Upgrade datafusion-comet to 0.10.1 (#14273) | ✅ 已完成 | [2727_9ee4d023f](commits/2727_9ee4d023f/analysis.md) |
| 2728 | `7f4111355` | 2025-10-09 16:20:21 -0700 | André Rosa | Parquet,Docs: Add new table property to configure bloom-filter ndv (#14244) | ✅ 已完成 | [2728_7f4111355](commits/2728_7f4111355/analysis.md) |
| 2729 | `a473b1c6e` | 2025-10-10 16:56:33 -0700 | Eduard Tudenhoefner | API: Detect whether required fields nested within optionals can produce nulls (#14270) | ✅ 已完成 | [2729_a473b1c6e](commits/2729_a473b1c6e/analysis.md) |
| 2730 | `77bc1d08e` | 2025-10-11 10:16:29 -0700 | Thomas Powell | Make KMS endpoint configurable via kms.endpoint AWS property (#14246) | ✅ 已完成 | [2730_77bc1d08e](commits/2730_77bc1d08e/analysis.md) |
| 2731 | `0fc9d7dde` | 2025-10-11 22:20:16 -0700 | dependabot[bot] | Build: Bump org.scala-lang.modules:scala-collection-compat_2.13 (#14303) | ✅ 已完成 | [2731_0fc9d7dde](commits/2731_0fc9d7dde/analysis.md) |
| 2732 | `6ad5dd818` | 2025-10-11 22:21:42 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.35.0 to 2.35.5 (#14302) | ✅ 已完成 | [2732_6ad5dd818](commits/2732_6ad5dd818/analysis.md) |
| 2733 | `765cf8d79` | 2025-10-11 22:23:05 -0700 | dependabot[bot] | Build: Bump org.immutables:value from 2.11.4 to 2.11.6 (#14301) | ✅ 已完成 | [2733_765cf8d79](commits/2733_765cf8d79/analysis.md) |
| 2734 | `06ebf07e6` | 2025-10-11 22:24:02 -0700 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.68.0 to 26.70.0 (#14298) | ✅ 已完成 | [2734_06ebf07e6](commits/2734_06ebf07e6/analysis.md) |
| 2735 | `0e04429c4` | 2025-10-12 08:08:50 -0700 | dependabot[bot] | Build: Bump nessie from 0.105.3 to 0.105.4 (#14299) | ✅ 已完成 | [2735_0e04429c4](commits/2735_0e04429c4/analysis.md) |
| 2736 | `acd3e4657` | 2025-10-12 09:39:52 -0700 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.26.1 to 3.27.0 (#14304) | ✅ 已完成 | [2736_acd3e4657](commits/2736_acd3e4657/analysis.md) |
| 2737 | `70101aad0` | 2025-10-12 16:14:31 -0700 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.33.0 to 0.35.0 (#14300) | ✅ 已完成 | [2737_70101aad0](commits/2737_70101aad0/analysis.md) |
| 2738 | `c67fda1d1` | 2025-10-13 10:18:25 +0200 | Yuya Ebihara | Test: Fix package of TestParquetPartitionStatsHandler and TestOrcPartitionStatsHandler (#14307) | ✅ 已完成 | [2738_c67fda1d1](commits/2738_c67fda1d1/analysis.md) |
| 2739 | `59344e83d` | 2025-10-13 11:51:08 +0200 | GuoYu | Flink: Clear globalStatisticsState in init to avoid duplication (#14294) | ✅ 已完成 | [2739_59344e83d](commits/2739_59344e83d/analysis.md) |
| 2740 | `6ca400929` | 2025-10-13 13:25:24 +0200 | GuoYu | Flink: Backport clear globalStatisticsState in init to avoid duplication (#14315) | ✅ 已完成 | [2740_6ca400929](commits/2740_6ca400929/analysis.md) |
| 2741 | `df563c589` | 2025-10-13 09:20:34 -0600 | gaborkaszab | Core: Refactor: Separate Route from RESTCatalogAdapter (#14313) | ✅ 已完成 | [2741_df563c589](commits/2741_df563c589/analysis.md) |
| 2742 | `88349d239` | 2025-10-13 09:53:29 -0700 | jackylee | [SPARK][MIRROR] Removed the unused imports for scala (#14311) | ✅ 已完成 | [2742_88349d239](commits/2742_88349d239/analysis.md) |
| 2743 | `58940f3ca` | 2025-10-13 09:55:39 -0700 | Yuya Ebihara | Test: Remove unused methods and fix typo (#14305) | ✅ 已完成 | [2743_58940f3ca](commits/2743_58940f3ca/analysis.md) |
| 2744 | `02fdf38ca` | 2025-10-14 09:28:19 +0200 | pvary | Flink: Move write from AppenderFactory to FileWriterFactory (#14271) | ✅ 已完成 | [2744_02fdf38ca](commits/2744_02fdf38ca/analysis.md) |
| 2745 | `a704de7a2` | 2025-10-14 08:23:11 -0700 | pvary | Flink: Backport move write from AppenderFactory to FileWriterFactory (#14325) | ✅ 已完成 | [2745_a704de7a2](commits/2745_a704de7a2/analysis.md) |
| 2746 | `f94fa138a` | 2025-10-14 09:53:58 -0700 | Kevin Liu | add onelake to docs (#14331) | ✅ 已完成 | [2746_f94fa138a](commits/2746_f94fa138a/analysis.md) |
| 2747 | `e9b9ef84b` | 2025-10-14 19:01:19 +0200 | Raghav Mahajan | Spark: Refactor to use ArrayUtil (#14291) | ✅ 已完成 | [2747_e9b9ef84b](commits/2747_e9b9ef84b/analysis.md) |
| 2748 | `8342cbedd` | 2025-10-14 11:27:42 -0700 | Szehon Ho | Spec: Clarify restrictions for geometry types in V3 (#14250) | ✅ 已完成 | [2748_8342cbedd](commits/2748_8342cbedd/analysis.md) |
| 2749 | `34096a7a2` | 2025-10-14 16:03:40 -0700 | Trivedhi | Hive : Fixing the trailing slash issue for the database paths in HMS (#14295) | ✅ 已完成 | [2749_34096a7a2](commits/2749_34096a7a2/analysis.md) |
| 2750 | `c7df5200d` | 2025-10-14 16:27:34 -0700 | guillesd | add DuckDB to vendors.md (#14327) | ✅ 已完成 | [2750_c7df5200d](commits/2750_c7df5200d/analysis.md) |
| 2751 | `ef4007964` | 2025-10-15 17:05:40 +0200 | Andre Luis Anastacio | Data, Parquet: Fix UUID ClassCastException when reading Parquet files with UUIDs (#14027) | ✅ 已完成 | [2751_ef4007964](commits/2751_ef4007964/analysis.md) |
| 2752 | `d831673c2` | 2025-10-15 17:46:41 +0200 | pvary | Kafka Connect: Use GenericFileWriterFactory instead of GenericAppenderFactory (#14328) | ✅ 已完成 | [2752_d831673c2](commits/2752_d831673c2/analysis.md) |
| 2753 | `03ebc8cc9` | 2025-10-15 16:51:04 -0700 | Huaxin Gao | Spark 4.0: Add variant round trip test for Spark (#14276) | ✅ 已完成 | [2753_03ebc8cc9](commits/2753_03ebc8cc9/analysis.md) |
| 2754 | `e34ec2484` | 2025-10-16 07:11:33 +0200 | pvary | Core, Flink, Spark: Further deprecations for the positional deletes with row data (#14210) | ✅ 已完成 | [2754_e34ec2484](commits/2754_e34ec2484/analysis.md) |
| 2755 | `f6b627ca1` | 2025-10-16 08:38:46 -0700 | Yuya Ebihara | Core: Explicitly close SeekableInput in the AvroIterable (#14322) | ✅ 已完成 | [2755_f6b627ca1](commits/2755_f6b627ca1/analysis.md) |
| 2756 | `e667f64f5` | 2025-10-16 11:37:01 -0600 | przemekd |  Core: Fix for respecting custom location providers in SerializableTable #12564  (#14280) | ✅ 已完成 | [2756_e667f64f5](commits/2756_e667f64f5/analysis.md) |
| 2757 | `6d7111b5c` | 2025-10-16 10:49:12 -0700 | Alex Stephen | Add Google Cloud vendor description (#14338) | ✅ 已完成 | [2757_6d7111b5c](commits/2757_6d7111b5c/analysis.md) |
| 2758 | `e8e97636e` | 2025-10-16 16:43:05 -0700 | Yuya Ebihara | Data: Replace LongMath.checkedMultiply with Math.multiplyExact (#14346) | ✅ 已完成 | [2758_e8e97636e](commits/2758_e8e97636e/analysis.md) |
| 2759 | `03f2af8ab` | 2025-10-17 13:52:06 +0200 | pvary | Core: Deprecate and remove GenericAppenderFactory from tests (#14353) | ✅ 已完成 | [2759_03f2af8ab](commits/2759_03f2af8ab/analysis.md) |
| 2760 | `2f947eb30` | 2025-10-17 17:03:55 -0700 | Huaxin Gao | Parquet: Treat VARIANT like nested for eq/in in ParquetMetricsRowGroupFilter (#14279) | ✅ 已完成 | [2760_2f947eb30](commits/2760_2f947eb30/analysis.md) |
| 2761 | `5a5ba0d47` | 2025-10-17 17:04:49 -0700 | Yuya Ebihara | Parquet: Fix UnnecessaryParentheses warning (#14361) | ✅ 已完成 | [2761_5a5ba0d47](commits/2761_5a5ba0d47/analysis.md) |
| 2762 | `e23cf65e9` | 2025-10-18 12:04:36 -0600 | Amogh Jahagirdar | API, Core: Fix byte buffer conversion for Variant in bounds (#14362) | ✅ 已完成 | [2762_e23cf65e9](commits/2762_e23cf65e9/analysis.md) |
| 2763 | `80e59d1a3` | 2025-10-18 16:22:45 -0700 | sullis | Build: bump testcontainers from 1.21.3 to 2.0.1 (#14366) | ✅ 已完成 | [2763_80e59d1a3](commits/2763_80e59d1a3/analysis.md) |
| 2764 | `d0df806c9` | 2025-10-18 21:13:30 -0700 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.21 to 9.6.22 (#14371) | ✅ 已完成 | [2764_d0df806c9](commits/2764_d0df806c9/analysis.md) |
| 2765 | `9eded8add` | 2025-10-18 22:21:45 -0700 | Manu Zhang | Build: Don't override checkstyle version in baseline-checkstyle plugin (#14365) | ✅ 已完成 | [2765_9eded8add](commits/2765_9eded8add/analysis.md) |
| 2766 | `42fcc1412` | 2025-10-18 22:22:48 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.35.5 to 2.35.10 (#14368) | ✅ 已完成 | [2766_42fcc1412](commits/2766_42fcc1412/analysis.md) |
| 2767 | `a539c3f2d` | 2025-10-18 22:45:04 -0700 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.6.Final to 4.2.7.Final (#14372) | ✅ 已完成 | [2767_a539c3f2d](commits/2767_a539c3f2d/analysis.md) |
| 2768 | `4cea66225` | 2025-10-18 22:45:41 -0700 | dependabot[bot] | Build: Bump org.apache.avro:avro from 1.12.0 to 1.12.1 (#14369) | ✅ 已完成 | [2768_4cea66225](commits/2768_4cea66225/analysis.md) |
| 2769 | `b987e60bb` | 2025-10-19 07:26:02 -0700 | dependabot[bot] | Build: Bump nessie from 0.105.4 to 0.105.5 (#14370) | ✅ 已完成 | [2769_b987e60bb](commits/2769_b987e60bb/analysis.md) |
| 2770 | `1a1945be5` | 2025-10-19 19:15:59 -0700 | Huaxin Gao | REST: Reconcile on CommitStateUnknown for simple update (#14320) | ✅ 已完成 | [2770_1a1945be5](commits/2770_1a1945be5/analysis.md) |
| 2771 | `8c44cccf0` | 2025-10-20 08:29:51 +0200 | Tom Tanaka | Spark 3.5,4.0: Fix test parameters (#14376) | ✅ 已完成 | [2771_8c44cccf0](commits/2771_8c44cccf0/analysis.md) |
| 2772 | `c0250c745` | 2025-10-20 12:37:47 +0200 | s-sanjay | Hive: Fix lock selection during table creation to respect table properties (#14236) | ✅ 已完成 | [2772_c0250c745](commits/2772_c0250c745/analysis.md) |
| 2773 | `911a486b0` | 2025-10-20 15:16:39 +0200 | Maximilian Michels | Flink: Prevent recreation of ManifestOutputFileFactory during flushing (#14358) | ✅ 已完成 | [2773_911a486b0](commits/2773_911a486b0/analysis.md) |
| 2774 | `b2ede556d` | 2025-10-20 18:36:34 -0700 | jackylee | Build: Add unused imports check for scala code (#14344) | ✅ 已完成 | [2774_b2ede556d](commits/2774_b2ede556d/analysis.md) |
| 2775 | `30ca573fe` | 2025-10-20 18:39:10 -0700 | ggershinsky | Encryption integration and test (#13066) | ✅ 已完成 | [2775_30ca573fe](commits/2775_30ca573fe/analysis.md) |
| 2776 | `9ffea7f0b` | 2025-10-20 20:12:44 -0700 | Huaxin Gao | Hotfix: adapt HMS tests to HiveTableOperations ctor (add KeyManagementClient) (#14384) | ✅ 已完成 | [2776_9ffea7f0b](commits/2776_9ffea7f0b/analysis.md) |
| 2777 | `fa4890e0f` | 2025-10-21 12:02:19 +0200 | Maximilian Michels | Flink: Backport Prevent recreation of ManifestOutputFileFactory during flushing (#14385) | ✅ 已完成 | [2777_fa4890e0f](commits/2777_fa4890e0f/analysis.md) |
| 2778 | `17cb11b7c` | 2025-10-21 15:04:24 +0200 | slfan1989 | Flink: Add maxSleepTimeMs and retryPolicyName to ZkLockFactory to support multiple retry policies. (#14243) | ✅ 已完成 | [2778_17cb11b7c](commits/2778_17cb11b7c/analysis.md) |
| 2779 | `b6747f8cf` | 2025-10-21 16:25:45 +0200 | GuoYu | Flink: Support writing DVs in IcebergSink (#14197) | ✅ 已完成 | [2779_b6747f8cf](commits/2779_b6747f8cf/analysis.md) |
| 2780 | `e160bb89c` | 2025-10-21 17:49:47 +0200 | GuoYu | Flink: Backport Support writing DVs in IcebergSink to Flink 2.0 and 1.20 (#14390) | ✅ 已完成 | [2780_e160bb89c](commits/2780_e160bb89c/analysis.md) |
| 2781 | `81cd5b5e3` | 2025-10-21 09:12:00 -0700 | Steven Zhen Wu | Flink: add serializer test for StatisticsOrRecord (#14381) | ✅ 已完成 | [2781_81cd5b5e3](commits/2781_81cd5b5e3/analysis.md) |
| 2782 | `c8732b88a` | 2025-10-21 10:07:58 -0700 | Steven Zhen Wu | Flink: backport PR #14381 for TestStatisticsOrRecordSerializer (#14393) | ✅ 已完成 | [2782_c8732b88a](commits/2782_c8732b88a/analysis.md) |
| 2783 | `c0745bd53` | 2025-10-21 11:11:24 -0600 | Amogh Jahagirdar | Core: Remove duplicate test assertion in delete file index tests (#14382) | ✅ 已完成 | [2783_c0745bd53](commits/2783_c0745bd53/analysis.md) |
| 2784 | `6b86a75d3` | 2025-10-21 10:22:22 -0700 | gaborkaszab | Core: Remove usage of deprecated TableProperties.MANIFEST_LISTS_ENABLED (#14347) | ✅ 已完成 | [2784_6b86a75d3](commits/2784_6b86a75d3/analysis.md) |
| 2785 | `f6632e98f` | 2025-10-22 10:25:48 +0200 | slfan1989 | Flink: Backport add maxSleepTimeMs and retryPolicyName to ZkLockFactory to support multiple retry policies. (#14389) | ✅ 已完成 | [2785_f6632e98f](commits/2785_f6632e98f/analysis.md) |
| 2786 | `cd097eb7e` | 2025-10-22 08:05:24 -0700 | Yuya Ebihara | REST: Remove deprecated allowEmptyValue from REST spec (#14364) | ✅ 已完成 | [2786_cd097eb7e](commits/2786_cd097eb7e/analysis.md) |
| 2787 | `29468f073` | 2025-10-23 14:38:14 +0200 | aiborodin | Core, Flink: Remove dependency on Hadoop Sets, Lists and Preconditions classes (#14405) | ✅ 已完成 | [2787_29468f073](commits/2787_29468f073/analysis.md) |
| 2788 | `de213af14` | 2025-10-23 17:12:29 +0200 | wineandcheeze | Docs: Add note about packaging uberjars (#14292) | ✅ 已完成 | [2788_de213af14](commits/2788_de213af14/analysis.md) |
| 2789 | `0b3021742` | 2025-10-23 09:10:11 -0700 | Yuya Ebihara | Docs: Remove expired Tabular links (#14399) | ✅ 已完成 | [2789_0b3021742](commits/2789_0b3021742/analysis.md) |
| 2790 | `e7bd3fa21` | 2025-10-24 12:40:09 -0700 | emkornfield | [SPEC] Removing trailing whitespace (#14416) | ✅ 已完成 | [2790_e7bd3fa21](commits/2790_e7bd3fa21/analysis.md) |
| 2791 | `2f719657a` | 2025-10-24 14:54:09 -0700 | Kurtis Wright | [Doc Update] Iceberg type to Spark type table (#14413) | ✅ 已完成 | [2791_2f719657a](commits/2791_2f719657a/analysis.md) |
| 2792 | `1ed200317` | 2025-10-25 23:35:15 -0700 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#14418) | ✅ 已完成 | [2792_1ed200317](commits/2792_1ed200317/analysis.md) |
| 2793 | `8dc411afd` | 2025-10-25 23:35:36 -0700 | dependabot[bot] | Build: Bump nessie from 0.105.5 to 0.105.6 (#14419) | ✅ 已完成 | [2793_8dc411afd](commits/2793_8dc411afd/analysis.md) |
| 2794 | `6ffc8a965` | 2025-10-26 00:05:14 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.35.10 to 2.36.2 (#14420) | ✅ 已完成 | [2794_6ffc8a965](commits/2794_6ffc8a965/analysis.md) |
| 2795 | `68e555b94` | 2025-10-26 00:05:31 -0700 | dependabot[bot] | Build: Bump mkdocs-macros-plugin from 1.4.0 to 1.4.1 (#14421) | ✅ 已完成 | [2795_68e555b94](commits/2795_68e555b94/analysis.md) |
| 2796 | `867835e28` | 2025-10-27 14:39:14 +0100 | chenjian2664 | Core: Use time-travel schema when resolving partition spec in scan (#13301) | ✅ 已完成 | [2796_867835e28](commits/2796_867835e28/analysis.md) |
| 2797 | `f631298fd` | 2025-10-27 09:30:48 -0600 | gaborkaszab | Core: Return 304 from reference IRC (#14035) | ✅ 已完成 | [2797_f631298fd](commits/2797_f631298fd/analysis.md) |
| 2798 | `7eb13e275` | 2025-10-27 10:24:55 -0700 | Yuya Ebihara | Spark: Deprecate unused methods in SparkTableUtil and SparkSchemaUtil (#14308) | ✅ 已完成 | [2798_7eb13e275](commits/2798_7eb13e275/analysis.md) |
| 2799 | `d5979670c` | 2025-10-27 11:43:02 -0700 | Steven Zhen Wu | Flink: add jmh benchmark for StatisticsOrRecordSerializer (#14394) | ✅ 已完成 | [2799_d5979670c](commits/2799_d5979670c/analysis.md) |
| 2800 | `6a0d4a0f3` | 2025-10-27 12:58:30 -0700 | Manu Zhang | Docs: lint markdown files in site build (#13977) | ✅ 已完成 | [2800_6a0d4a0f3](commits/2800_6a0d4a0f3/analysis.md) |
| 2801 | `fa62ec1df` | 2025-10-28 09:50:23 -0700 | Adam Szita | [Hive] Fix newly added encryption keys getting lost in transactions (#14427) | ✅ 已完成 | [2801_fa62ec1df](commits/2801_fa62ec1df/analysis.md) |
| 2802 | `c4e480da6` | 2025-10-28 13:05:51 -0700 | Eduard Tudenhoefner | Core: Fix view version ID deduplication with schema ID assignment (#14434) | ✅ 已完成 | [2802_c4e480da6](commits/2802_c4e480da6/analysis.md) |
| 2803 | `d1a518f84` | 2025-10-29 09:14:05 -0700 | ggershinsky | Auto rotation of key encryption keys (#14396) | ✅ 已完成 | [2803_d1a518f84](commits/2803_d1a518f84/analysis.md) |
| 2804 | `a99dc4f2f` | 2025-10-29 13:02:52 -0600 | Drew Gallardo | Spark 4.0: Add schema conversion support for default values (#14407) | ✅ 已完成 | [2804_a99dc4f2f](commits/2804_a99dc4f2f/analysis.md) |
| 2805 | `92ae3d9da` | 2025-10-29 23:39:23 +0100 | Yuya Ebihara | Core: Fix failure when not finding a column during time travel (#14438) | ✅ 已完成 | [2805_92ae3d9da](commits/2805_92ae3d9da/analysis.md) |
| 2806 | `57ff27cd1` | 2025-10-30 09:25:26 -0600 | Amogh Jahagirdar | API: Add exception definitions for scan planning (#14442) | ✅ 已完成 | [2806_57ff27cd1](commits/2806_57ff27cd1/analysis.md) |
| 2807 | `bd1b8900a` | 2025-10-30 14:00:44 -0700 | Ron Kapoor | Spark: Fix Z-order UDF to correctly handle DateType  (#14108) | ✅ 已完成 | [2807_bd1b8900a](commits/2807_bd1b8900a/analysis.md) |
| 2808 | `8685985a0` | 2025-10-31 08:39:56 +0100 | gaborkaszab | Core, Spark 4.0, 3.5, 3.4: Remove usage of deprecated avro/DataReader class (#14387) | ✅ 已完成 | [2808_8685985a0](commits/2808_8685985a0/analysis.md) |
| 2809 | `b211c8b82` | 2025-10-31 19:27:20 -0700 | Kevin Liu | site: use jinja comment in header.html override (#14459) | ✅ 已完成 | [2809_b211c8b82](commits/2809_b211c8b82/analysis.md) |
| 2810 | `0ce9b1628` | 2025-11-01 16:35:13 -0700 | Kevin Liu | infra: cleanup remove unused functions from site/ script (#14466) | ✅ 已完成 | [2810_0ce9b1628](commits/2810_0ce9b1628/analysis.md) |
| 2811 | `aa14aae01` | 2025-11-01 16:37:22 -0700 | majian | Spark 4.0: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#13625) | ✅ 已完成 | [2811_aa14aae01](commits/2811_aa14aae01/analysis.md) |
| 2812 | `1875a3001` | 2025-11-01 21:25:04 -0700 | dependabot[bot] | Build: Bump pymarkdownlnt from 0.9.32 to 0.9.33 (#14473) | ✅ 已完成 | [2812_1875a3001](commits/2812_1875a3001/analysis.md) |
| 2813 | `1c3c5c8ea` | 2025-11-01 21:25:42 -0700 | dependabot[bot] | Build: Bump mkdocs-material from 9.6.22 to 9.6.23 (#14474) | ✅ 已完成 | [2813_1c3c5c8ea](commits/2813_1c3c5c8ea/analysis.md) |
| 2814 | `7d7a90538` | 2025-11-01 22:15:20 -0700 | dependabot[bot] | Build: Bump junit-platform from 1.13.4 to 1.14.1 (#14469) | ✅ 已完成 | [2814_7d7a90538](commits/2814_7d7a90538/analysis.md) |
| 2815 | `b68725e47` | 2025-11-01 22:29:55 -0700 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.27.0 to 3.27.1 (#14468) | ✅ 已完成 | [2815_b68725e47](commits/2815_b68725e47/analysis.md) |
| 2816 | `9bbe4cfdf` | 2025-11-01 23:10:09 -0700 | dependabot[bot] | Build: Bump calcite from 1.40.0 to 1.41.0 (#14470) | ✅ 已完成 | [2816_9bbe4cfdf](commits/2816_9bbe4cfdf/analysis.md) |
| 2817 | `e6da0f77d` | 2025-11-01 23:10:30 -0700 | dependabot[bot] | Build: Bump jackson-bom from 2.20.0 to 2.20.1 (#14471) | ✅ 已完成 | [2817_e6da0f77d](commits/2817_e6da0f77d/analysis.md) |
| 2818 | `47a7e20d0` | 2025-11-01 23:11:12 -0700 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.70.0 to 26.71.0 (#14475) | ✅ 已完成 | [2818_47a7e20d0](commits/2818_47a7e20d0/analysis.md) |
| 2819 | `6b03be402` | 2025-11-02 12:29:05 +0530 | dependabot[bot] | Build: Bump junit from 5.13.4 to 5.14.1 (#14476) | ✅ 已完成 | [2819_6b03be402](commits/2819_6b03be402/analysis.md) |
| 2820 | `74e2ea7b1` | 2025-11-02 12:29:24 +0530 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14477) | ✅ 已完成 | [2820_74e2ea7b1](commits/2820_74e2ea7b1/analysis.md) |
| 2821 | `115531403` | 2025-11-02 17:58:26 -0800 | Kevin Liu | site: use virtualenv and add make lint (#14428) | ✅ 已完成 | [2821_115531403](commits/2821_115531403/analysis.md) |
| 2822 | `13ed6670e` | 2025-11-02 21:15:26 -0800 | majian | Spark 3.5: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#14483) | ✅ 已完成 | [2822_13ed6670e](commits/2822_13ed6670e/analysis.md) |
| 2823 | `d6ac00c5a` | 2025-11-03 09:10:32 +0100 | Eduard Tudenhoefner | Core: Prevent dropping namespace when it contains views (#14456) | ✅ 已完成 | [2823_d6ac00c5a](commits/2823_d6ac00c5a/analysis.md) |
| 2824 | `94d9287b7` | 2025-11-03 07:45:18 -0800 | Eduard Tudenhoefner | Core: Minor code improvements (#14489) | ✅ 已完成 | [2824_94d9287b7](commits/2824_94d9287b7/analysis.md) |
| 2825 | `3ccb29624` | 2025-11-03 12:29:41 -0800 | Kevin Liu | faster make lint (#14492) | ✅ 已完成 | [2825_3ccb29624](commits/2825_3ccb29624/analysis.md) |
| 2826 | `de1eab8df` | 2025-11-03 17:43:05 -0800 | Yuya Ebihara | Test: Verify variant logical type annotation of Parquet writer (#14306) | ✅ 已完成 | [2826_de1eab8df](commits/2826_de1eab8df/analysis.md) |
| 2827 | `f17f34bce` | 2025-11-03 17:45:05 -0800 | Yuya Ebihara | Core: Check table UUID in RESTTableOperations (#14363) | ✅ 已完成 | [2827_f17f34bce](commits/2827_f17f34bce/analysis.md) |
| 2828 | `329a90148` | 2025-11-04 08:33:32 +0100 | Prashant Singh | Core: Restrict visibility of spec-by-id API for scan planning responses (#14485) | ✅ 已完成 | [2828_329a90148](commits/2828_329a90148/analysis.md) |
| 2829 | `e268df62f` | 2025-11-04 08:37:09 +0100 | Yuya Ebihara | Core: Fix overflow due to default value on timestamp nanos (#14359) | ✅ 已完成 | [2829_e268df62f](commits/2829_e268df62f/analysis.md) |
| 2830 | `c235305c9` | 2025-11-04 11:27:52 -0800 | Fenil Doshi | Kafka Connect: Don't check that consumer group is stable for coordinator leader election (#14395) | ✅ 已完成 | [2830_c235305c9](commits/2830_c235305c9/analysis.md) |
| 2831 | `34b03f5e6` | 2025-11-04 17:45:12 -0800 | Huaxin Gao | Spark: Implement ArrayData.getVariant for row-based Parquet readers (#14349) | ✅ 已完成 | [2831_34b03f5e6](commits/2831_34b03f5e6/analysis.md) |
| 2832 | `ccc3e14df` | 2025-11-05 16:37:35 +0100 | roryqi | Spark: Improve namespace existence verification logic (#14507) | ✅ 已完成 | [2832_ccc3e14df](commits/2832_ccc3e14df/analysis.md) |
| 2833 | `caa42f617` | 2025-11-05 18:26:59 -0800 | majian | Follow-up: Optimize support recursive delegate unwrapping to find ExtendedParser in parser chains (#14497) | ✅ 已完成 | [2833_caa42f617](commits/2833_caa42f617/analysis.md) |
| 2834 | `99ccfc4b7` | 2025-11-06 12:19:44 +0100 | Arif Azmi | Spark: enable stream-results option for remove orphan files (#14278) | ✅ 已完成 | [2834_99ccfc4b7](commits/2834_99ccfc4b7/analysis.md) |
| 2835 | `4e68ff008` | 2025-11-06 12:24:14 +0100 | GuoYu | Flink: Preserve row lineage in RewriteDataFiles  (#14149) | ✅ 已完成 | [2835_4e68ff008](commits/2835_4e68ff008/analysis.md) |
| 2836 | `4b7286cf0` | 2025-11-06 14:27:51 +0100 | GuoYu | Flink: Backport Preserve row lineage in RewriteDataFiles to Flink 2.1 and 1.20 (#14520) | ✅ 已完成 | [2836_4b7286cf0](commits/2836_4b7286cf0/analysis.md) |
| 2837 | `8cefa386f` | 2025-11-06 07:54:49 -0800 | Talat UYARER | Add Fast Mode for Documentation Builds (#14267) | ✅ 已完成 | [2837_8cefa386f](commits/2837_8cefa386f/analysis.md) |
| 2838 | `ebc6b6692` | 2025-11-06 08:53:06 -0800 | Ron Kapoor | Docs: 2.13 scala runtime addition to multi-engine support page (#14288) | ✅ 已完成 | [2838_ebc6b6692](commits/2838_ebc6b6692/analysis.md) |
| 2839 | `bbf5b8a90` | 2025-11-06 09:58:07 -0800 | Gea Linggar Galih | Docs: add Delta Lake Migration to nav (fix #14309) (#14323) | ✅ 已完成 | [2839_bbf5b8a90](commits/2839_bbf5b8a90/analysis.md) |
| 2840 | `17e76f8f8` | 2025-11-06 13:14:31 -0800 | Arif Azmi | Spark: Backport stream-results for remove orphan files to 3.4 and 4.0 (#14522) | ✅ 已完成 | [2840_17e76f8f8](commits/2840_17e76f8f8/analysis.md) |
| 2841 | `915655171` | 2025-11-06 15:15:35 -0800 | roryqi | Spark: Improve the table, view, and function existence verification logic (#14457) | ✅ 已完成 | [2841_915655171](commits/2841_915655171/analysis.md) |
| 2842 | `de9b8fdc4` | 2025-11-06 16:11:34 -0800 | Bryan Keller | Kafka Connect: add task ID snapshot property (#14493) | ✅ 已完成 | [2842_de9b8fdc4](commits/2842_de9b8fdc4/analysis.md) |
| 2843 | `d2551a667` | 2025-11-06 17:28:27 -0800 | Daniel Weeks | Add SnapshotUpdateValidator to validate snapshots on commit (#14509) | ✅ 已完成 | [2843_d2551a667](commits/2843_d2551a667/analysis.md) |
| 2844 | `843bb46e2` | 2025-11-07 07:47:59 +0100 | James Faulkner | Core: Support reading Avro logical timestamp-millis (#14401) | ✅ 已完成 | [2844_843bb46e2](commits/2844_843bb46e2/analysis.md) |
| 2845 | `8da07dcae` | 2025-11-07 07:55:15 -0800 | Daniel Weeks | Merge control topic and last persisted offests (#14525) | ✅ 已完成 | [2845_8da07dcae](commits/2845_8da07dcae/analysis.md) |
| 2846 | `08d9ee020` | 2025-11-07 14:57:55 -0800 | Alexandre Dutra | AWS, S3 Signing: Fix leaked credentials when contacting multiple catalogs (#14178) | ✅ 已完成 | [2846_08d9ee020](commits/2846_08d9ee020/analysis.md) |
| 2847 | `6b28b0edc` | 2025-11-07 16:14:57 -0800 | Daniel Weeks | Kafka Connect: validate offsets for refreshed table state on commit (#14510) | ✅ 已完成 | [2847_6b28b0edc](commits/2847_6b28b0edc/analysis.md) |
| 2848 | `7e28be434` | 2025-11-08 20:41:33 -0800 | Huaxin Gao | support StructInternalRow.getVariant (#14379) | ✅ 已完成 | [2848_7e28be434](commits/2848_7e28be434/analysis.md) |
| 2849 | `a53811b91` | 2025-11-08 22:15:05 -0800 | dependabot[bot] | Build: Bump nessie from 0.105.6 to 0.105.7 (#14535) | ✅ 已完成 | [2849_a53811b91](commits/2849_a53811b91/analysis.md) |
| 2850 | `2d4c6c6f1` | 2025-11-08 22:17:19 -0800 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.50.3.0 to 3.51.0.0 (#14536) | ✅ 已完成 | [2850_2d4c6c6f1](commits/2850_2d4c6c6f1/analysis.md) |
| 2851 | `72c4d9a02` | 2025-11-08 22:18:04 -0800 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#14538) | ✅ 已完成 | [2851_72c4d9a02](commits/2851_72c4d9a02/analysis.md) |
| 2852 | `25bddb0a4` | 2025-11-08 22:18:46 -0800 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.3.0 to 1.3.2 (#14539) | ✅ 已完成 | [2852_25bddb0a4](commits/2852_25bddb0a4/analysis.md) |
| 2853 | `0d2bee45b` | 2025-11-09 08:05:18 -0800 | Yuya Ebihara | Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#14542) | ✅ 已完成 | [2853_0d2bee45b](commits/2853_0d2bee45b/analysis.md) |
| 2854 | `b3f89215b` | 2025-11-09 15:58:18 -0800 | Yuya Ebihara | Build: Bump software.amazon.awssdk:bom from 2.36.2 to 2.38.2 (#14541) | ✅ 已完成 | [2854_b3f89215b](commits/2854_b3f89215b/analysis.md) |
| 2855 | `ae4f5c1f8` | 2025-11-09 16:12:16 -0800 | Yuya Ebihara | Test: Remove redundant String.format for AssertJ (#14544) | ✅ 已完成 | [2855_ae4f5c1f8](commits/2855_ae4f5c1f8/analysis.md) |
| 2856 | `c22bac8b1` | 2025-11-10 09:57:01 +0100 | Eduard Tudenhoefner | Core, Spark: Handle unknown type during deletes (#14356) | ✅ 已完成 | [2856_c22bac8b1](commits/2856_c22bac8b1/analysis.md) |
| 2857 | `e762cd4e9` | 2025-11-10 09:09:17 -0800 | Enes Yesil | Docs: add missing pages (about, benchmarks, fileio, security) to site (#14481) | ✅ 已完成 | [2857_e762cd4e9](commits/2857_e762cd4e9/analysis.md) |
| 2858 | `54815073b` | 2025-11-10 10:11:43 -0800 | jackylee | Doc: Remove Spark 3 specific wordings in docs (#14357) | ✅ 已完成 | [2858_54815073b](commits/2858_54815073b/analysis.md) |
| 2859 | `f8dff223b` | 2025-11-10 10:31:57 -0800 | Wing Yew Poon | Spec: minor corrections (#14495) | ✅ 已完成 | [2859_f8dff223b](commits/2859_f8dff223b/analysis.md) |
| 2860 | `b81ddb6c5` | 2025-11-10 10:48:38 -0800 | Marc Cenac | Update docs to refer to table format-version in spec definition (#14410) | ✅ 已完成 | [2860_b81ddb6c5](commits/2860_b81ddb6c5/analysis.md) |
| 2861 | `59bc46358` | 2025-11-10 12:53:24 -0800 | Kevin Liu | docs: remove hidden spark-runtime-jar link (#14555) | ✅ 已完成 | [2861_59bc46358](commits/2861_59bc46358/analysis.md) |
| 2862 | `4aca8fda8` | 2025-11-10 12:53:52 -0800 | Kevin Liu | site: remove site/docs/about.md (#14554) | ✅ 已完成 | [2862_4aca8fda8](commits/2862_4aca8fda8/analysis.md) |
| 2863 | `059310ead` | 2025-11-10 15:42:23 -0800 | Manu Zhang | Build: Restore JVM 11 for build-checks and build-javadoc (#14534) | ✅ 已完成 | [2863_059310ead](commits/2863_059310ead/analysis.md) |
| 2864 | `7368e5990` | 2025-11-11 10:15:28 -0800 | Eduard Tudenhoefner | Core: Fix validation of PlanTableScanRequest (#14561) | ✅ 已完成 | [2864_7368e5990](commits/2864_7368e5990/analysis.md) |
| 2865 | `0a92386de` | 2025-11-11 17:45:50 -0800 | hsiang-c | core: Use EncryptionUtil's classloader (#14452) | ✅ 已完成 | [2865_0a92386de](commits/2865_0a92386de/analysis.md) |
| 2866 | `0ce14b7ff` | 2025-11-11 19:45:34 -0800 | Eduard Tudenhoefner | Core: Fix PlanTableScanResponse validation (#14562) | ✅ 已完成 | [2866_0ce14b7ff](commits/2866_0ce14b7ff/analysis.md) |
| 2867 | `2899b5a75` | 2025-11-12 07:47:53 +0100 | Eduard Tudenhoefner | API, Core: Introduce classes for content stats (#13933) | ✅ 已完成 | [2867_2899b5a75](commits/2867_2899b5a75/analysis.md) |
| 2868 | `d2046a7d9` | 2025-11-12 08:46:26 +0100 | adawrapub | Core, Spark: Preserve DV-specific fields for deletion vectors (#14351) | ✅ 已完成 | [2868_d2046a7d9](commits/2868_d2046a7d9/analysis.md) |
| 2869 | `966003c47` | 2025-11-12 04:27:16 -0800 | Amogh Jahagirdar | Core: Add server-side implementation of remote scan planning to RESTCatalogAdapter (#14480) | ✅ 已完成 | [2869_966003c47](commits/2869_966003c47/analysis.md) |
| 2870 | `67e1975ac` | 2025-11-12 14:10:58 +0100 | GuoYu | Flink: Fix writeDataFiles with hardcoded formatVersion (#14570) | ✅ 已完成 | [2870_67e1975ac](commits/2870_67e1975ac/analysis.md) |
| 2871 | `53c046efd` | 2025-11-12 05:52:34 -0800 | Prashant Singh | Core: Increase visibility of ParserContext (#14572) | ✅ 已完成 | [2871_53c046efd](commits/2871_53c046efd/analysis.md) |
| 2872 | `dc217b09d` | 2025-11-12 19:03:12 +0100 | ajreid21 | Core: Fix RESTFileScanTaskParser to handle empty delete file references list (#14568) | ✅ 已完成 | [2872_dc217b09d](commits/2872_dc217b09d/analysis.md) |
| 2873 | `a6c4e6aef` | 2025-11-12 13:51:40 -0800 | Nándor Kollár | Spec: minor clarification, Parquet int type is int32, long is int64 (#14546) | ✅ 已完成 | [2873_a6c4e6aef](commits/2873_a6c4e6aef/analysis.md) |
| 2874 | `571b69610` | 2025-11-13 16:13:54 +0530 | Prashant Singh | REST: Fix serde of tasks with multiple deletes (#14573) | ✅ 已完成 | [2874_571b69610](commits/2874_571b69610/analysis.md) |
| 2875 | `788463b1f` | 2025-11-16 00:00:16 -0800 | dependabot[bot] | Build: Bump testcontainers from 2.0.1 to 2.0.2 (#14595) | ✅ 已完成 | [2875_788463b1f](commits/2875_788463b1f/analysis.md) |
| 2876 | `3873f6e2f` | 2025-11-16 00:00:37 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.38.2 to 2.38.7 (#14596) | ✅ 已完成 | [2876_3873f6e2f](commits/2876_3873f6e2f/analysis.md) |
| 2877 | `de042cf84` | 2025-11-16 00:00:57 -0800 | dependabot[bot] | Build: Bump software.amazon.s3.analyticsaccelerator:analyticsaccelerator-s3 (#14597) | ✅ 已完成 | [2877_de042cf84](commits/2877_de042cf84/analysis.md) |
| 2878 | `700575f6e` | 2025-11-16 00:01:15 -0800 | dependabot[bot] | Build: Bump mkdocs-macros-plugin from 1.4.1 to 1.5.0 (#14598) | ✅ 已完成 | [2878_700575f6e](commits/2878_700575f6e/analysis.md) |
| 2879 | `31fe60b90` | 2025-11-16 22:42:52 -0800 | Owen Zhang | Spark: Custom snapshot property from session configuration (#14545) | ✅ 已完成 | [2879_31fe60b90](commits/2879_31fe60b90/analysis.md) |
| 2880 | `31ce47b8b` | 2025-11-17 14:03:51 +0100 | Jordan Epstein | Flink: Set table properties/location on DynamicIcebergSink table creation (#14578) | ✅ 已完成 | [2880_31ce47b8b](commits/2880_31ce47b8b/analysis.md) |
| 2881 | `8de302bf4` | 2025-11-17 10:31:33 -0800 | Kristin Cowalcijk | API: Add geospatial bounding box types and implement intersects checking (#12667) | ✅ 已完成 | [2881_8de302bf4](commits/2881_8de302bf4/analysis.md) |
| 2882 | `87f0563ff` | 2025-11-17 21:37:29 +0100 | Jordan Epstein | Flink: Backort add TableCreator interface to set table properties/location to 2.0 and 1.20  (#14607) | ✅ 已完成 | [2882_87f0563ff](commits/2882_87f0563ff/analysis.md) |
| 2883 | `ea8eb8600` | 2025-11-17 19:02:10 -0800 | Yuya Ebihara | Core, Flink: Use helper method to filter by prefix (#14610) | ✅ 已完成 | [2883_ea8eb8600](commits/2883_ea8eb8600/analysis.md) |
| 2884 | `8c7ffec48` | 2025-11-17 21:10:46 -0800 | yingjianwu98 | Refactor: populate writeOptions in extraSnapshotMetadata using common util (#14604) | ✅ 已完成 | [2884_8c7ffec48](commits/2884_8c7ffec48/analysis.md) |
| 2885 | `c02d4676f` | 2025-11-18 07:57:54 +0100 | Maximilian Michels | Core: Classify RowDelta with data files only as APPEND (#14581) | ✅ 已完成 | [2885_c02d4676f](commits/2885_c02d4676f/analysis.md) |
| 2886 | `cdf4723e4` | 2025-11-18 17:45:26 +0100 | bezdomniy | Flink: Add test to ensure that append commits are created in dynamic iceberg sink when possible (#14559) | ✅ 已完成 | [2886_cdf4723e4](commits/2886_cdf4723e4/analysis.md) |
| 2887 | `b6b89268d` | 2025-11-18 10:39:14 -0800 | Tamas Mate | Add variant type support to ParquetTypeVisitor (#14588) | ✅ 已完成 | [2887_b6b89268d](commits/2887_b6b89268d/analysis.md) |
| 2888 | `8796f8a2d` | 2025-11-19 07:41:41 +0100 | Eduard Tudenhoefner | OpenAPI: Add planId as query param to /credentials endpoint (#14519) | ✅ 已完成 | [2888_8796f8a2d](commits/2888_8796f8a2d/analysis.md) |
| 2889 | `b2379e5f6` | 2025-11-19 09:38:25 +0100 | Shubham Baldava | Arrow: Fix vectorized reads for Parquet TIMESTAMP_MILLIS types (#14499) | ✅ 已完成 | [2889_b2379e5f6](commits/2889_b2379e5f6/analysis.md) |
| 2890 | `2fe4495e9` | 2025-11-19 09:41:40 +0100 | aiborodin | Flink: Fix commit duplication in DynamicIcebergSink (#14517) | ✅ 已完成 | [2890_2fe4495e9](commits/2890_2fe4495e9/analysis.md) |
| 2891 | `4ee507d57` | 2025-11-19 09:44:00 +0100 | GuoYu | Flink: DynamicSink support dvs (#14414) | ✅ 已完成 | [2891_4ee507d57](commits/2891_4ee507d57/analysis.md) |
| 2892 | `3615fae61` | 2025-11-19 13:47:45 +0100 | GuoYu | Flink:Backport DynamicSink support dvs to Flink 2.0 and 1.20 (#14623) | ✅ 已完成 | [2892_3615fae61](commits/2892_3615fae61/analysis.md) |
| 2893 | `bec6793af` | 2025-11-19 09:38:04 -0800 | ggershinsky | Encryption clean up (#14579) | ✅ 已完成 | [2893_bec6793af](commits/2893_bec6793af/analysis.md) |
| 2894 | `cc38966a8` | 2025-11-19 19:06:06 +0100 | Eduard Tudenhoefner | Build: Upgrade setup-java to v5 (#14616) | ✅ 已完成 | [2894_cc38966a8](commits/2894_cc38966a8/analysis.md) |
| 2895 | `f60b582eb` | 2025-11-19 16:03:40 -0800 | Huaxin Gao | REST Spec: Add Idempotency-Key to OpenAPI (#14196) | ✅ 已完成 | [2895_f60b582eb](commits/2895_f60b582eb/analysis.md) |
| 2896 | `6e873baa7` | 2025-11-19 16:18:27 -0800 | Yuya Ebihara | Test: Avoid deprecated AvroParquetWriter.builder(Path file) (#14620) | ✅ 已完成 | [2896_6e873baa7](commits/2896_6e873baa7/analysis.md) |
| 2897 | `11274f65d` | 2025-11-20 10:00:39 +0100 | aiborodin | Flink: Backport fix commit duplication in DynamicIcebergSink (#14637) | ✅ 已完成 | [2897_11274f65d](commits/2897_11274f65d/analysis.md) |
| 2898 | `51d5b0963` | 2025-11-20 10:02:39 +0100 | Maximilian Michels | Build: Allow overriding the default test parallelism of 1 and update parallelism for Flink test (#13675) | ✅ 已完成 | [2898_51d5b0963](commits/2898_51d5b0963/analysis.md) |
| 2899 | `7fc886ad7` | 2025-11-20 15:55:13 +0100 | ajreid21 | OpenAPI: Add storage-credentials to CompletedPlanningResult (#14563) | ✅ 已完成 | [2899_7fc886ad7](commits/2899_7fc886ad7/analysis.md) |
| 2900 | `f045d5a38` | 2025-11-20 07:09:23 -0800 | Prashant Singh | REST: Make plan status consistent with the SPEC (#14642) | ✅ 已完成 | [2900_f045d5a38](commits/2900_f045d5a38/analysis.md) |
| 2901 | `06c1e0a0b` | 2025-11-20 09:05:15 -0800 | Jordan Epstein | Move deleted files to Hadoop trash if configured (#14501) | ✅ 已完成 | [2901_06c1e0a0b](commits/2901_06c1e0a0b/analysis.md) |
| 2902 | `0d4d3a562` | 2025-11-20 11:47:12 -0700 | Prashant Singh | Core: Fix Async Planning handling in RESTCatalogAdapter for Remote Scan Planning (#14629) | ✅ 已完成 | [2902_0d4d3a562](commits/2902_0d4d3a562/analysis.md) |
| 2903 | `c06ab662d` | 2025-11-21 12:55:45 +0100 | ajreid21 | OpenAPI: Add min-rows-requested field to PlanTableScanRequest (#14565) | ✅ 已完成 | [2903_c06ab662d](commits/2903_c06ab662d/analysis.md) |
| 2904 | `f09dc3c10` | 2025-11-21 12:56:24 +0100 | Eduard Tudenhoefner | Core: Add min-rows-requested to PlanTableScanRequest (#14614) | ✅ 已完成 | [2904_f09dc3c10](commits/2904_f09dc3c10/analysis.md) |
| 2905 | `b6650141d` | 2025-11-21 13:24:26 +0100 | Maximilian Michels | Flink: Update Flink 1.18 status to 'End of Life' (#14152) | ✅ 已完成 | [2905_b6650141d](commits/2905_b6650141d/analysis.md) |
| 2906 | `9d7213db4` | 2025-11-21 04:24:30 -0800 | Prashant Singh | SPEC: Mark plan-id as required for submitted status | ✅ 已完成 | [2906_9d7213db4](commits/2906_9d7213db4/analysis.md) |
| 2907 | `fb52fdef4` | 2025-11-21 04:24:50 -0800 | Prashant Singh | REST: Mark plan-id as required on CompletedPlanningWithIDResult result | ✅ 已完成 | [2907_fb52fdef4](commits/2907_fb52fdef4/analysis.md) |
| 2908 | `ece6b8e77` | 2025-11-21 08:20:55 -0800 | pvary | Core: Deprecate PositionDeleteReaderWriter.writer with rowSchema (#14651) | ✅ 已完成 | [2908_ece6b8e77](commits/2908_ece6b8e77/analysis.md) |
| 2909 | `5cc19891a` | 2025-11-21 14:04:05 -0800 | Hongyue/Steve Zhang | Core, API: Support cleanupMode in snapshot expiration (#14287) | ✅ 已完成 | [2909_5cc19891a](commits/2909_5cc19891a/analysis.md) |
| 2910 | `452221817` | 2025-11-22 12:20:08 +0530 | Drew Gallardo | Align PlanTableScanRequest filter expression with OpenAPI spec (#14657) | ✅ 已完成 | [2910_452221817](commits/2910_452221817/analysis.md) |
| 2911 | `3e45527be` | 2025-11-22 18:57:46 -0800 | Eduard Tudenhoefner | Core: Add storage credentials to PlanTableScanResponse (#14518) | ✅ 已完成 | [2911_3e45527be](commits/2911_3e45527be/analysis.md) |
| 2912 | `fe40f5db6` | 2025-11-22 23:25:17 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.38.7 to 2.39.2 (#14666) | ✅ 已完成 | [2912_fe40f5db6](commits/2912_fe40f5db6/analysis.md) |
| 2913 | `3c7505df7` | 2025-11-22 23:25:47 -0800 | dependabot[bot] | Build: Bump org.immutables:value from 2.11.6 to 2.11.7 (#14665) | ✅ 已完成 | [2913_3c7505df7](commits/2913_3c7505df7/analysis.md) |
| 2914 | `9dfb2c197` | 2025-11-22 23:26:14 -0800 | dependabot[bot] | Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#14664) | ✅ 已完成 | [2914_9dfb2c197](commits/2914_9dfb2c197/analysis.md) |
| 2915 | `8b55ac834` | 2025-11-22 23:26:26 -0800 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.71.0 to 26.72.0 (#14663) | ✅ 已完成 | [2915_8b55ac834](commits/2915_8b55ac834/analysis.md) |
| 2916 | `9c1dd3b3a` | 2025-11-24 15:36:13 +0100 | Adam Szita | Core: Wrong reported length of encrypted Puffin files (#14645) | ✅ 已完成 | [2916_9c1dd3b3a](commits/2916_9c1dd3b3a/analysis.md) |
| 2917 | `f154dafb2` | 2025-11-24 15:59:04 -0800 | Prashant Singh | Core: Support incremental Scan in RESTCatalogAdapter for RemoteScanPlanning (#14661) | ✅ 已完成 | [2917_f154dafb2](commits/2917_f154dafb2/analysis.md) |
| 2918 | `a3c538f64` | 2025-11-24 16:11:04 -0800 | Tamas Mate | Fix NameMapping loss in ParquetUtil.footerMetrics (#14617) | ✅ 已完成 | [2918_a3c538f64](commits/2918_a3c538f64/analysis.md) |
| 2919 | `47d5f5009` | 2025-11-25 10:26:49 -0800 | Yuya Ebihara | Docs: Add gc.enabled table property (#14676) | ✅ 已完成 | [2919_47d5f5009](commits/2919_47d5f5009/analysis.md) |
| 2920 | `6a54bc1c3` | 2025-11-25 15:00:13 -0800 | Huaxin Gao | Core: Add idempotency-key-lifetime to ConfigResponse (#14649) | ✅ 已完成 | [2920_6a54bc1c3](commits/2920_6a54bc1c3/analysis.md) |
| 2921 | `078fbeb10` | 2025-11-26 07:29:40 +0100 | Manu Zhang | Build: Don't ignore major version upgrade for GH actions in dependabot (#14687) | ✅ 已完成 | [2921_078fbeb10](commits/2921_078fbeb10/analysis.md) |
| 2922 | `790a82065` | 2025-11-25 23:33:57 -0800 | dependabot[bot] | Build: Bump actions/labeler from 5 to 6 (#14689) | ✅ 已完成 | [2922_790a82065](commits/2922_790a82065/analysis.md) |
| 2923 | `5e166b5d7` | 2025-11-25 23:34:34 -0800 | dependabot[bot] | Build: Bump actions/setup-python from 5 to 6 (#14690) | ✅ 已完成 | [2923_5e166b5d7](commits/2923_5e166b5d7/analysis.md) |
| 2924 | `b7549da63` | 2025-11-25 23:35:14 -0800 | dependabot[bot] | Build: Bump actions/stale from 9.1.0 to 10.1.0 (#14692) | ✅ 已完成 | [2924_b7549da63](commits/2924_b7549da63/analysis.md) |
| 2925 | `0846ed515` | 2025-11-25 23:42:38 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.39.2 to 2.39.4 (#14693) | ✅ 已完成 | [2925_0846ed515](commits/2925_0846ed515/analysis.md) |
| 2926 | `87174fcbd` | 2025-11-25 23:48:54 -0800 | dependabot[bot] | Build: Bump actions/upload-artifact from 4 to 5 (#14688) | ✅ 已完成 | [2926_87174fcbd](commits/2926_87174fcbd/analysis.md) |
| 2927 | `fe6f78b32` | 2025-11-26 15:39:16 +0100 | Tamas Mate | Core: Allow overriding view location for subclasses (#14653) | ✅ 已完成 | [2927_fe6f78b32](commits/2927_fe6f78b32/analysis.md) |
| 2928 | `cd8d2a334` | 2025-11-26 10:01:29 -0700 | Drew Gallardo | Core: Fix server side planning on empty tables in CatalogHandlers (#14660) | ✅ 已完成 | [2928_cd8d2a334](commits/2928_cd8d2a334/analysis.md) |
| 2929 | `42719ef41` | 2025-11-27 14:57:09 +0100 | dependabot[bot] | Build: Bump actions/checkout from 3 to 6 (#14691) | ✅ 已完成 | [2929_42719ef41](commits/2929_42719ef41/analysis.md) |
| 2930 | `cf2776976` | 2025-11-27 17:41:34 -0800 | Eduard Tudenhoefner | Spark: Fix scala warnings in View code (#14703) | ✅ 已完成 | [2930_cf2776976](commits/2930_cf2776976/analysis.md) |
| 2931 | `d2d413572` | 2025-11-28 14:15:55 -0800 | Kevin Liu | infra: notify on github workflow failure (#14609) | ✅ 已完成 | [2931_d2d413572](commits/2931_d2d413572/analysis.md) |
| 2932 | `784f1f469` | 2025-11-28 22:55:15 -0800 | Yuya Ebihara | Docs: Fix package of iceberg.catalog.io-impl (#14711) | ✅ 已完成 | [2932_784f1f469](commits/2932_784f1f469/analysis.md) |
| 2933 | `8a38d6e38` | 2025-11-29 10:01:58 -0700 | Alessandro Nori | Spark 4.0: expire-snapshots with cleanupLevel=None (#14695) | ✅ 已完成 | [2933_8a38d6e38](commits/2933_8a38d6e38/analysis.md) |
| 2934 | `52c176df6` | 2025-11-29 09:13:21 -0800 | Kevin Liu | Use non-deprecated del_branch_on_merge (#14710) | ✅ 已完成 | [2934_52c176df6](commits/2934_52c176df6/analysis.md) |
| 2935 | `b9a8c31c0` | 2025-11-29 23:41:16 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.39.4 to 2.39.5 (#14718) | ✅ 已完成 | [2935_b9a8c31c0](commits/2935_b9a8c31c0/analysis.md) |
| 2936 | `3a1177645` | 2025-11-29 23:42:05 -0800 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.35.0 to 0.36.0 (#14716) | ✅ 已完成 | [2936_3a1177645](commits/2936_3a1177645/analysis.md) |
| 2937 | `46d766ae4` | 2025-11-29 23:42:29 -0800 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#14715) | ✅ 已完成 | [2937_46d766ae4](commits/2937_46d766ae4/analysis.md) |
| 2938 | `65c667da2` | 2025-11-30 00:04:45 -0800 | Sreesh Maheshwar | Nit: Move unchecked suppression down to violating assignment in `ParquetMetricsRowGroupFilter` (#14013) | ✅ 已完成 | [2938_65c667da2](commits/2938_65c667da2/analysis.md) |
| 2939 | `f3949cea0` | 2025-11-30 09:11:23 -0800 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.3.2 to 1.3.3 (#14717) | ✅ 已完成 | [2939_f3949cea0](commits/2939_f3949cea0/analysis.md) |
| 2940 | `b35c7ec1b` | 2025-12-01 09:45:09 +0100 | Huaxin Gao | Core: Fix NAN_VALUE_COUNTS serialization for ContentFile (#14721) | ✅ 已完成 | [2940_b35c7ec1b](commits/2940_b35c7ec1b/analysis.md) |
| 2941 | `3747965e9` | 2025-12-02 07:11:25 +0100 | Yuya Ebihara | Core: Use `@TempDir` in TestTableMetadataParser (#14732) | ✅ 已完成 | [2941_3747965e9](commits/2941_3747965e9/analysis.md) |
| 2942 | `16e84356d` | 2025-12-02 09:46:37 +0100 | Huaxin Gao | Core: Add UUIDv7 generator (#14700) | ✅ 已完成 | [2942_16e84356d](commits/2942_16e84356d/analysis.md) |
| 2943 | `166a6ebb3` | 2025-12-02 08:49:58 -0800 | Xianyang Liu | Build: Apply spotless for scala code (#8023) | ✅ 已完成 | [2943_166a6ebb3](commits/2943_166a6ebb3/analysis.md) |
| 2944 | `29a144adc` | 2025-12-02 09:41:32 -0800 | aiborodin | Refactor SnapshotAncestryValidator (#14650) | ✅ 已完成 | [2944_29a144adc](commits/2944_29a144adc/analysis.md) |
| 2945 | `9896e8ccc` | 2025-12-02 10:33:02 -0800 | Drew Gallardo | Core: Align ContentFile partition JSON with REST spec (#14702) | ✅ 已完成 | [2945_9896e8ccc](commits/2945_9896e8ccc/analysis.md) |
| 2946 | `da76b873f` | 2025-12-02 19:49:07 +0100 | Kevin Liu | open-api: use uv and python virtual env (#14684) | ✅ 已完成 | [2946_da76b873f](commits/2946_da76b873f/analysis.md) |
| 2947 | `35d66a3fe` | 2025-12-02 13:06:29 -0800 | Rulin Xing | Core: Support Custom Table/View Operations in RESTCatalog (#14465) | ✅ 已完成 | [2947_35d66a3fe](commits/2947_35d66a3fe/analysis.md) |
| 2948 | `fac485c56` | 2025-12-03 07:19:41 +0100 | jbewing | Spark: Analyze but don't optimize view body during creation (#14681) | ✅ 已完成 | [2948_fac485c56](commits/2948_fac485c56/analysis.md) |
| 2949 | `74a116074` | 2025-12-03 07:32:55 +0100 | Eduard Tudenhoefner | Spark 4.0, Core: Add Limit pushdown to Scan (#14615) | ✅ 已完成 | [2949_74a116074](commits/2949_74a116074/analysis.md) |
| 2950 | `fc7cd7db0` | 2025-12-03 11:21:10 +0100 | Eduard Tudenhoefner | Spark 3.4,3.5: Add LIMIT pushdown to Scan (#14741) | ✅ 已完成 | [2950_fc7cd7db0](commits/2950_fc7cd7db0/analysis.md) |
| 2951 | `bfe06a540` | 2025-12-03 14:03:40 +0100 | pvary | Spark: Move DeleteFiltering out from the vectorized reader (#14652) | ✅ 已完成 | [2951_bfe06a540](commits/2951_bfe06a540/analysis.md) |
| 2952 | `8626ef513` | 2025-12-03 14:27:52 -0800 | ggershinsky | Docs: encryption (#14621) | ✅ 已完成 | [2952_8626ef513](commits/2952_8626ef513/analysis.md) |
| 2953 | `52d6a79e9` | 2025-12-03 23:03:46 -0800 | Sreesh Maheshwar | Nit: Prefer `Preconditions` in `StandardEncryptionManager` (#14753) | ✅ 已完成 | [2953_52d6a79e9](commits/2953_52d6a79e9/analysis.md) |
| 2954 | `65280c015` | 2025-12-04 10:14:36 +0100 | pvary | Spark: Backport move DeleteFiltering out from the vectorized reader (#14745) | ✅ 已完成 | [2954_65280c015](commits/2954_65280c015/analysis.md) |
| 2955 | `667bc5954` | 2025-12-04 10:26:02 +0100 | Maximilian Michels | Flink: Dynamic Sink: Document writeParallelism and fail on invalid configuration (#14191) | ✅ 已完成 | [2955_667bc5954](commits/2955_667bc5954/analysis.md) |
| 2956 | `86e53a7e8` | 2025-12-04 11:25:41 +0100 | Maximilian Michels | Flink: Backport: Dynamic Sink: Document writeParallelism and fail on invalid configuration (#14758) | ✅ 已完成 | [2956_86e53a7e8](commits/2956_86e53a7e8/analysis.md) |
| 2957 | `4c0ad4226` | 2025-12-04 09:37:21 -0700 | Drew Gallardo | Core: Align ContentFile enum serialization with REST Spec (#14739) | ✅ 已完成 | [2957_4c0ad4226](commits/2957_4c0ad4226/analysis.md) |
| 2958 | `d7f8950ab` | 2025-12-04 16:30:14 -0800 | ggershinsky | Exception on encryption key altering  (#14723) | ✅ 已完成 | [2958_d7f8950ab](commits/2958_d7f8950ab/analysis.md) |
| 2959 | `ba983470e` | 2025-12-04 17:19:34 -0800 | Huaxin Gao | Docs: fix rendering issues in encryption doc (#14756) | ✅ 已完成 | [2959_ba983470e](commits/2959_ba983470e/analysis.md) |
| 2960 | `8db3d2115` | 2025-12-05 09:07:23 +0100 | aiborodin | Flink: Fix cache refreshing in dynamic sink (#14406) | ✅ 已完成 | [2960_8db3d2115](commits/2960_8db3d2115/analysis.md) |
| 2961 | `c4ba60d27` | 2025-12-05 09:08:30 +0100 | aiborodin | Flink: Backport fix cache refreshing in dynamic sink (#14765) | ✅ 已完成 | [2961_c4ba60d27](commits/2961_c4ba60d27/analysis.md) |
| 2962 | `55bfc7e82` | 2025-12-05 08:39:28 -0800 | Kevin Liu | OpenAPI: use yaml linter  (#14686) | ✅ 已完成 | [2962_55bfc7e82](commits/2962_55bfc7e82/analysis.md) |
| 2963 | `73f8ab814` | 2025-12-05 08:41:53 -0800 | gaborkaszab | Core: Reference IRC to return 204 (#14724) | ✅ 已完成 | [2963_73f8ab814](commits/2963_73f8ab814/analysis.md) |
| 2964 | `7bac8650f` | 2025-12-05 09:23:37 -0800 | Huaxin Gao | Core: Send Idempotency-Key on mutation requests when advertised (#14740) | ✅ 已完成 | [2964_7bac8650f](commits/2964_7bac8650f/analysis.md) |
| 2965 | `4c908314e` | 2025-12-05 19:33:15 +0100 | pvary | Spark: ORC vectorized reader to use the delete filter (#14746) | ✅ 已完成 | [2965_4c908314e](commits/2965_4c908314e/analysis.md) |
| 2966 | `9632a2f76` | 2025-12-05 12:42:44 -0800 | Anurag Mantripragada | AWS: Configure builder for reuse of http connection pool in SDKv2 (#14161) | ✅ 已完成 | [2966_9632a2f76](commits/2966_9632a2f76/analysis.md) |
| 2967 | `885bcbb9f` | 2025-12-05 12:48:45 -0800 | Fokko Driesprong | site: Update Slack link (#14772) | ✅ 已完成 | [2967_885bcbb9f](commits/2967_885bcbb9f/analysis.md) |
| 2968 | `1c024d7c4` | 2025-12-05 13:04:03 -0800 | Kurtis Wright | Update configuration.md (#14771) | ✅ 已完成 | [2968_1c024d7c4](commits/2968_1c024d7c4/analysis.md) |
| 2969 | `fc434997f` | 2025-12-06 05:17:17 -0800 | Manu Zhang | Revert "Update configuration.md (#14771)" (#14780) | ✅ 已完成 | [2969_fc434997f](commits/2969_fc434997f/analysis.md) |
| 2970 | `3b1ec481a` | 2025-12-06 23:32:08 -0800 | dependabot[bot] | Build: Bump actions/stale from 10.1.0 to 10.1.1 (#14784) | ✅ 已完成 | [2970_3b1ec481a](commits/2970_3b1ec481a/analysis.md) |
| 2971 | `faabc46f9` | 2025-12-06 23:32:41 -0800 | dependabot[bot] | Build: Bump nessie from 0.105.7 to 0.106.0 (#14785) | ✅ 已完成 | [2971_faabc46f9](commits/2971_faabc46f9/analysis.md) |
| 2972 | `786d1645c` | 2025-12-06 23:33:39 -0800 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.51.0.0 to 3.51.1.0 (#14786) | ✅ 已完成 | [2972_786d1645c](commits/2972_786d1645c/analysis.md) |
| 2973 | `6735edd84` | 2025-12-06 23:43:51 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.39.5 to 2.40.3 (#14788) | ✅ 已完成 | [2973_6735edd84](commits/2973_6735edd84/analysis.md) |
| 2974 | `19b4bd024` | 2025-12-07 22:01:06 -0700 | Yuya Ebihara | Core: Disallow encryption table properties in v1 and v2 (#14668) | ✅ 已完成 | [2974_19b4bd024](commits/2974_19b4bd024/analysis.md) |
| 2975 | `0c194502b` | 2025-12-08 10:15:03 +0100 | Fokko Driesprong | OpenAPI: Use `PrimitiveTypeValue` rather than `object` (#14184) | ✅ 已完成 | [2975_0c194502b](commits/2975_0c194502b/analysis.md) |
| 2976 | `a739cb3db` | 2025-12-08 12:23:24 +0100 | Maximilian Michels | Flink: Dynamic Sink: Add support for dropping columns (#14728) | ✅ 已完成 | [2976_a739cb3db](commits/2976_a739cb3db/analysis.md) |
| 2977 | `01b29dd64` | 2025-12-08 14:57:20 +0100 | Yuya Ebihara | Build: Bump datamodel-code-generator from 0.36.0 to 0.41.0 (#14791) | ✅ 已完成 | [2977_01b29dd64](commits/2977_01b29dd64/analysis.md) |
| 2978 | `a9482bd6a` | 2025-12-08 16:23:12 +0100 | Maximilian Michels | Flink: Backport: Dynamic Sink: Add support for dropping columns (#14799) | ✅ 已完成 | [2978_a9482bd6a](commits/2978_a9482bd6a/analysis.md) |
| 2979 | `642b85248` | 2025-12-08 16:40:54 +0100 | Eduard Tudenhoefner | OpenAPI: Make namespace separator configurable by server (#14448) | ✅ 已完成 | [2979_642b85248](commits/2979_642b85248/analysis.md) |
| 2980 | `8901269a2` | 2025-12-08 08:37:42 -0800 | Prashant Singh | OpenAPI: Add idempotency key for the mutating plan endpoints (#14730) | ✅ 已完成 | [2980_8901269a2](commits/2980_8901269a2/analysis.md) |
| 2981 | `bd8d28958` | 2025-12-08 09:42:44 -0800 | Adam Szita | Hive: Metadata integrity check for encrypted tables (#14685) | ✅ 已完成 | [2981_bd8d28958](commits/2981_bd8d28958/analysis.md) |
| 2982 | `344adf9d1` | 2025-12-09 10:09:13 +0100 | Eduard Tudenhoefner | Core: Make namespace separator configurable (#10877) | ✅ 已完成 | [2982_344adf9d1](commits/2982_344adf9d1/analysis.md) |
| 2983 | `b2f3a4ce7` | 2025-12-09 13:31:27 +0100 | pvary | Spark: Backport ORC vectorized reader to use the delete filter (#14794) | ✅ 已完成 | [2983_b2f3a4ce7](commits/2983_b2f3a4ce7/analysis.md) |
| 2984 | `0547af078` | 2025-12-09 15:56:28 +0100 | GuoYu | Flink: Fix write unknown type to ORC exception and add ut for unknown type (#14761) | ✅ 已完成 | [2984_0547af078](commits/2984_0547af078/analysis.md) |
| 2985 | `7e4131683` | 2025-12-09 17:02:46 +0100 | GuoYu | Flink: Backport fix write unknown type to ORC exception and add ut for unknown type (#14806) | ✅ 已完成 | [2985_7e4131683](commits/2985_7e4131683/analysis.md) |
| 2986 | `c8f4d5fee` | 2025-12-09 20:30:53 +0100 | pvary | Spark: Add comet reader test (#14807) | ✅ 已完成 | [2986_c8f4d5fee](commits/2986_c8f4d5fee/analysis.md) |
| 2987 | `03415dff6` | 2025-12-09 15:13:05 -0800 | pvary | Spark: Backport add comet reader test (#14809) | ✅ 已完成 | [2987_03415dff6](commits/2987_03415dff6/analysis.md) |
| 2988 | `c6ba7a4c4` | 2025-12-09 15:48:18 -0800 | Beerelly Prudhvi Maharishi | GCS: Integrate GCSAnalyticsCore Library (#14333) | ✅ 已完成 | [2988_c6ba7a4c4](commits/2988_c6ba7a4c4/analysis.md) |
| 2989 | `0cc337ace` | 2025-12-10 07:34:26 +0100 | Prashant Singh | Core: REST Scan Planning Task Implementation (#13400) | ✅ 已完成 | [2989_0cc337ace](commits/2989_0cc337ace/analysis.md) |
| 2990 | `122c4408b` | 2025-12-10 13:26:27 +0100 | Raunaq Morarka | API: Reduce 'Scanning table' log verbosity for long list of strings (#14757) | ✅ 已完成 | [2990_122c4408b](commits/2990_122c4408b/analysis.md) |
| 2991 | `11188387f` | 2025-12-10 14:10:08 +0100 | Maximilian Michels | Flink: Dynamic Sink: Handle NoSuchNamespaceException properly (#14812) | ✅ 已完成 | [2991_11188387f](commits/2991_11188387f/analysis.md) |
| 2992 | `482d850c2` | 2025-12-10 18:02:01 +0100 | Eduard Tudenhoefner | Spark: Test all simple types in TestSelect (#14804) | ✅ 已完成 | [2992_482d850c2](commits/2992_482d850c2/analysis.md) |
| 2993 | `cbd35799d` | 2025-12-10 09:16:23 -0800 | Sreesh Maheshwar | Encryption: Simplify Hive key handling and add transaction tests (#14752) | ✅ 已完成 | [2993_cbd35799d](commits/2993_cbd35799d/analysis.md) |
| 2994 | `361131336` | 2025-12-10 09:47:28 -0800 | Thomas Powell | Handle SupportsWithPrefix in EncryptingFileIO (#14727) | ✅ 已完成 | [2994_361131336](commits/2994_361131336/analysis.md) |
| 2995 | `d894a0283` | 2025-12-10 20:07:58 +0100 | Eduard Tudenhoefner | Core: Align CharSequenceSet impl with Data/DeleteFileSet (#11322) | ✅ 已完成 | [2995_d894a0283](commits/2995_d894a0283/analysis.md) |
| 2996 | `369409529` | 2025-12-10 15:42:18 -0800 | Vladislav Sidorovich | Throw CommitFailedException when BQ returns FAILED_PRECONDITION. (#14801) | ✅ 已完成 | [2996_369409529](commits/2996_369409529/analysis.md) |
| 2997 | `5025d581a` | 2025-12-10 15:51:25 -0800 | Xianyang Liu | Build: Improvements around applying spotless for Scala (#14798) | ✅ 已完成 | [2997_5025d581a](commits/2997_5025d581a/analysis.md) |
| 2998 | `23dc32e5f` | 2025-12-10 23:20:46 -0800 | Prashant Singh | REST: Implement Batch Scan for RESTTableScan (#14776) | ✅ 已完成 | [2998_23dc32e5f](commits/2998_23dc32e5f/analysis.md) |
| 2999 | `f5317674e` | 2025-12-11 09:33:10 -0800 | Manikandan R | Support for TIME, TIMESTAMPNTZ_NANO, UUID types in Inclusive Metrics Evaluator (#13195) | ✅ 已完成 | [2999_f5317674e](commits/2999_f5317674e/analysis.md) |
| 3000 | `fc981b499` | 2025-12-11 19:33:05 +0100 | aiborodin | Flink: Log on cache refresh in dynamic sink (#14792) | ✅ 已完成 | [3000_fc981b499](commits/3000_fc981b499/analysis.md) |
| 3001 | `7418f4986` | 2025-12-11 15:12:14 -0800 | Prashant Singh | Core: disable flaky test for batchScan RemoteScanPlanning (#14826) | ✅ 已完成 | [3001_7418f4986](commits/3001_7418f4986/analysis.md) |
| 3002 | `e90b06cc5` | 2025-12-11 18:41:14 -0800 | Maximilian Michels | Flink: Backport: Dynamic Sink: Handle NoSuchNamespaceException properly (#14812) (#14819) | ✅ 已完成 | [3002_e90b06cc5](commits/3002_e90b06cc5/analysis.md) |
| 3003 | `cc02655c7` | 2025-12-12 08:54:40 +0100 | aiborodin | Flink: Backport: Log on cache refresh in dynamic sink (#14828)a | ✅ 已完成 | [3003_cc02655c7](commits/3003_cc02655c7/analysis.md) |
| 3004 | `c68f04187` | 2025-12-12 12:40:26 +0100 | gaborkaszab | Core: Expose the stats of the manifest file content cache (#13560) | ✅ 已完成 | [3004_c68f04187](commits/3004_c68f04187/analysis.md) |
| 3005 | `849f218d8` | 2025-12-12 12:44:52 +0100 | MehulBatra | Docs: Add Apache Fluss integration link (#14829) | ✅ 已完成 | [3005_849f218d8](commits/3005_849f218d8/analysis.md) |
| 3006 | `41b5af3e8` | 2025-12-12 07:51:16 -0800 | Nándor Kollár | Azure: KeyManagementClient implementation for Azure Key Vault (#13186) | ✅ 已完成 | [3006_41b5af3e8](commits/3006_41b5af3e8/analysis.md) |
| 3007 | `baff19fa9` | 2025-12-12 14:02:58 -0800 | Danica Fine | Docs: Update community meetup guidelines (#14770) | ✅ 已完成 | [3007_baff19fa9](commits/3007_baff19fa9/analysis.md) |
| 3008 | `9a048825f` | 2025-12-12 15:07:03 -0800 | Hongyue/Steve Zhang | Spark, Flink: replace deprecated cleanExpiredFiles in expireSnapshots (#14832) | ✅ 已完成 | [3008_9a048825f](commits/3008_9a048825f/analysis.md) |
| 3009 | `bc23a774a` | 2025-12-13 09:09:08 +0100 | gaborkaszab | Core: Adjust namespace separator in TestRESTCatalog (#14808) | ✅ 已完成 | [3009_bc23a774a](commits/3009_bc23a774a/analysis.md) |
| 3010 | `9daef1715` | 2025-12-13 23:38:45 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.40.3 to 2.40.8 (#14843) | ✅ 已完成 | [3010_9daef1715](commits/3010_9daef1715/analysis.md) |
| 3011 | `83b8afa31` | 2025-12-14 00:02:58 -0800 | Yuya Ebihara | Build: Bump datamodel-code-generator from 0.41.0 to 0.43.1 (#14845) | ✅ 已完成 | [3011_83b8afa31](commits/3011_83b8afa31/analysis.md) |
| 3012 | `837df74d6` | 2025-12-14 06:57:52 -0800 | dependabot[bot] | Build: Bump org.immutables:value from 2.11.7 to 2.12.0 (#14844) | ✅ 已完成 | [3012_837df74d6](commits/3012_837df74d6/analysis.md) |
| 3013 | `abd1d7741` | 2025-12-14 06:58:24 -0800 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.7.Final to 4.2.8.Final (#14841) | ✅ 已完成 | [3013_abd1d7741](commits/3013_abd1d7741/analysis.md) |
| 3014 | `b6e262d14` | 2025-12-14 06:58:39 -0800 | dependabot[bot] | Build: Bump actions/upload-artifact from 5 to 6 (#14840) | ✅ 已完成 | [3014_b6e262d14](commits/3014_b6e262d14/analysis.md) |
| 3015 | `69b4191eb` | 2025-12-14 07:12:48 -0800 | dependabot[bot] | Build: Bump actions/cache from 4 to 5 (#14839) | ✅ 已完成 | [3015_69b4191eb](commits/3015_69b4191eb/analysis.md) |
| 3016 | `831b4ea10` | 2025-12-15 14:02:24 +0100 | gaborkaszab | Core: Change removal of deprecations to 1.12.0 (#14392) | ✅ 已完成 | [3016_831b4ea10](commits/3016_831b4ea10/analysis.md) |
| 3017 | `ba28a3365` | 2025-12-15 09:30:26 -0800 | Amogh Jahagirdar | Core: Deprecate scan response builder deleteFiles API (#14838) | ✅ 已完成 | [3017_ba28a3365](commits/3017_ba28a3365/analysis.md) |
| 3018 | `90bfc3dff` | 2025-12-15 14:07:19 -0800 | Prashant Singh | SPEC: Add NoSuchPlanId to cancel endpoint (#14796) | ✅ 已完成 | [3018_90bfc3dff](commits/3018_90bfc3dff/analysis.md) |
| 3019 | `f2449550c` | 2025-12-16 10:01:26 +0100 | GuoYu | Flink, Core: RewriteDataFiles add max file group count (#14837) | ✅ 已完成 | [3019_f2449550c](commits/3019_f2449550c/analysis.md) |
| 3020 | `d5bfcaf7c` | 2025-12-16 08:33:38 -0800 | Vamsi Krishna | Docs: Add schema selection example for time travel queries (#14825) | ✅ 已完成 | [3020_d5bfcaf7c](commits/3020_d5bfcaf7c/analysis.md) |
| 3021 | `baee88771` | 2025-12-16 10:57:13 -0800 | Huaxin Gao | fix typo in assert message (#14855) | ✅ 已完成 | [3021_baee88771](commits/3021_baee88771/analysis.md) |
| 3022 | `b23f13f71` | 2025-12-16 11:24:11 -0800 | Prashant Singh | Core: Address Race Condition in ScanTaskIterable (#14824) | ✅ 已完成 | [3022_b23f13f71](commits/3022_b23f13f71/analysis.md) |
| 3023 | `60b42ec05` | 2025-12-16 20:54:46 -0800 | Yuya Ebihara | API: Remove redundant } from Transforms javadoc (#14866) | ✅ 已完成 | [3023_60b42ec05](commits/3023_60b42ec05/analysis.md) |
| 3024 | `26cb7cdd2` | 2025-12-17 12:23:21 +0100 | GuoYu | Flink: Backport RewriteDataFiles add max file group count (#14861) | ✅ 已完成 | [3024_26cb7cdd2](commits/3024_26cb7cdd2/analysis.md) |
| 3025 | `9ca8029b4` | 2025-12-17 11:51:33 -0800 | Ajay Yadav | GCS: bump up gcs-analytics-core version from 1.2.1 to 1.2.3 (#14873) | ✅ 已完成 | [3025_9ca8029b4](commits/3025_9ca8029b4/analysis.md) |
| 3026 | `33cab35b6` | 2025-12-18 16:07:06 +0100 | Eduard Tudenhoefner | Spark: Enable remote scan planning with REST catalog (#14822) | ✅ 已完成 | [3026_33cab35b6](commits/3026_33cab35b6/analysis.md) |
| 3027 | `8ea92ff5e` | 2025-12-18 10:32:27 -0700 | Eduard Tudenhoefner | Core: Simplify handling of the current planId in client side of remote planning (#14883) | ✅ 已完成 | [3027_8ea92ff5e](commits/3027_8ea92ff5e/analysis.md) |
| 3028 | `05998edb8` | 2025-12-18 11:22:46 -0800 | Prashant Singh | Fix: Enable metadata tables support for REST scan planning (#14881) | ✅ 已完成 | [3028_05998edb8](commits/3028_05998edb8/analysis.md) |
| 3029 | `00bf964f1` | 2025-12-19 14:53:33 +0100 | Eduard Tudenhoefner | Spark: Order results to fix test flakiness with remote scan planning (#14894) | ✅ 已完成 | [3029_00bf964f1](commits/3029_00bf964f1/analysis.md) |
| 3030 | `2a006ba8d` | 2025-12-19 08:44:00 -0800 | Eduard Tudenhoefner | Core: Close planFiles() iterable in CatalogHandler (#14891) | ✅ 已完成 | [3030_2a006ba8d](commits/3030_2a006ba8d/analysis.md) |
| 3031 | `59280a106` | 2025-12-19 18:02:45 +0100 | wuya | Hive: Update view query in HMS when replacing view (#14831) | ✅ 已完成 | [3031_59280a106](commits/3031_59280a106/analysis.md) |
| 3032 | `554a3c1d2` | 2025-12-19 14:16:55 -0800 | Joy Haldar | GCP: Add service account impersonation support for BigQueryMetastoreCatalog (#14447) | ✅ 已完成 | [3032_554a3c1d2](commits/3032_554a3c1d2/analysis.md) |
| 3033 | `0fb1e3cac` | 2025-12-19 14:44:29 -0800 | Christian | OpenAPI: Etag for CommitTableResponse (#14760) | ✅ 已完成 | [3033_0fb1e3cac](commits/3033_0fb1e3cac/analysis.md) |
| 3034 | `76fcd47ff` | 2025-12-20 21:55:32 -0800 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.8.Final to 4.2.9.Final (#14897) | ✅ 已完成 | [3034_76fcd47ff](commits/3034_76fcd47ff/analysis.md) |
| 3035 | `da67268ee` | 2025-12-20 21:55:52 -0800 | dependabot[bot] | Build: Bump testcontainers from 2.0.2 to 2.0.3 (#14898) | ✅ 已完成 | [3035_da67268ee](commits/3035_da67268ee/analysis.md) |
| 3036 | `73c2aa2d1` | 2025-12-20 21:55:59 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.40.8 to 2.40.13 (#14904) | ✅ 已完成 | [3036_73c2aa2d1](commits/3036_73c2aa2d1/analysis.md) |
| 3037 | `830cbc9b6` | 2025-12-20 21:56:14 -0800 | dependabot[bot] | Build: Bump net.snowflake:snowflake-jdbc from 3.27.1 to 3.28.0 (#14899) | ✅ 已完成 | [3037_830cbc9b6](commits/3037_830cbc9b6/analysis.md) |
| 3038 | `e8f6e90f6` | 2025-12-20 21:56:29 -0800 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.72.0 to 26.73.0 (#14902) | ✅ 已完成 | [3038_e8f6e90f6](commits/3038_e8f6e90f6/analysis.md) |
| 3039 | `d6d44a780` | 2025-12-20 21:56:57 -0800 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#14900) | ✅ 已完成 | [3039_d6d44a780](commits/3039_d6d44a780/analysis.md) |
| 3040 | `1c5bb017c` | 2025-12-22 17:03:51 +0100 | Yuya Ebihara | Build: Bump datamodel-code-generator from 0.43.1 to 0.46.0 (#14905) | ✅ 已完成 | [3040_1c5bb017c](commits/3040_1c5bb017c/analysis.md) |
| 3041 | `f40058689` | 2025-12-22 14:15:46 -0800 | Huaxin Gao | Site: Updates for 1.10.1 Release (#14907) | ✅ 已完成 | [3041_f40058689](commits/3041_f40058689/analysis.md) |
| 3042 | `f1e02730c` | 2025-12-22 16:29:52 -0800 | Huaxin Gao | Issue template: add 1.10.1 to version dropdown (#14916) | ✅ 已完成 | [3042_f1e02730c](commits/3042_f1e02730c/analysis.md) |
| 3043 | `752a2820c` | 2025-12-22 16:52:45 -0800 | Huaxin Gao | Site: correct release time for 1.10.1 (#14918) | ✅ 已完成 | [3043_752a2820c](commits/3043_752a2820c/analysis.md) |
| 3044 | `314989243` | 2025-12-22 19:14:53 -0800 | manuzhang | Spark: Move 4.0 as 4.1 | ✅ 已完成 | [3044_314989243](commits/3044_314989243/analysis.md) |
| 3045 | `ddea5658e` | 2025-12-22 19:14:53 -0800 | manuzhang | Spark: Copy back 4.1 as 4.0 | ✅ 已完成 | [3045_ddea5658e](commits/3045_ddea5658e/analysis.md) |
| 3046 | `317968426` | 2025-12-22 19:14:53 -0800 | manuzhang | Spark: Initial support for 4.1.0 | ✅ 已完成 | [3046_317968426](commits/3046_317968426/analysis.md) |
| 3047 | `ed26fd7ac` | 2025-12-22 21:16:35 -0800 | Huaxin Gao | DOAP: add release 1.10.1 (#14917) | ✅ 已完成 | [3047_ed26fd7ac](commits/3047_ed26fd7ac/analysis.md) |
| 3048 | `0069c5e09` | 2025-12-23 10:15:34 -0800 | Prashant Singh | INFRA: Skip running CI for doap.rdf file (#14919) | ✅ 已完成 | [3048_0069c5e09](commits/3048_0069c5e09/analysis.md) |
| 3049 | `026ec35b7` | 2025-12-23 19:32:01 -0800 | Amogh Jahagirdar | Core: Small cleanup in MergingSnapshotProducer cleanUncommittedAppends (#14923) | ✅ 已完成 | [3049_026ec35b7](commits/3049_026ec35b7/analysis.md) |
| 3050 | `0651b8913` | 2025-12-24 10:02:23 -0800 | nhuantho | [doc] Add highlight note for Hadoop S3A FileSystem (#14913) | ✅ 已完成 | [3050_0651b8913](commits/3050_0651b8913/analysis.md) |
| 3051 | `53044611e` | 2025-12-27 21:01:11 -0800 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.46.0 to 0.49.0 (#14938) | ✅ 已完成 | [3051_53044611e](commits/3051_53044611e/analysis.md) |
| 3052 | `b26009c5c` | 2025-12-27 21:01:19 -0800 | dependabot[bot] | Build: Bump pymarkdownlnt from 0.9.33 to 0.9.34 (#14937) | ✅ 已完成 | [3052_b26009c5c](commits/3052_b26009c5c/analysis.md) |
| 3053 | `63c923e6b` | 2025-12-27 23:24:00 -0800 | Prashant Singh | fix test regex (#14939) | ✅ 已完成 | [3053_63c923e6b](commits/3053_63c923e6b/analysis.md) |
| 3054 | `4db390958` | 2025-12-27 23:25:45 -0800 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#14934) | ✅ 已完成 | [3054_4db390958](commits/3054_4db390958/analysis.md) |
| 3055 | `4632f3133` | 2025-12-27 23:26:04 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.40.13 to 2.40.16 (#14936) | ✅ 已完成 | [3055_4632f3133](commits/3055_4632f3133/analysis.md) |
| 3056 | `9c3bed6a6` | 2025-12-30 12:16:19 -0800 | Varun Lakhyani | Docs: Fix MERGE INTO example in Getting Started (#14943) | ✅ 已完成 | [3056_9c3bed6a6](commits/3056_9c3bed6a6/analysis.md) |
| 3057 | `000460058` | 2026-01-01 20:11:51 -0800 | Dan LaRocque | Spec: fix impl note about snapshot ID generation (#14720) | ✅ 已完成 | [3057_000460058](commits/3057_000460058/analysis.md) |
| 3058 | `e131329a0` | 2026-01-03 08:24:26 -0800 | Huaxin Gao | Spark: Add ordering to TestSelect to remove flakiness (#14956) | ✅ 已完成 | [3058_e131329a0](commits/3058_e131329a0/analysis.md) |
| 3059 | `01b59d37f` | 2026-01-03 23:48:37 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.40.16 to 2.41.1 (#14961) | ✅ 已完成 | [3059_01b59d37f](commits/3059_01b59d37f/analysis.md) |
| 3060 | `64b7b6622` | 2026-01-03 23:50:08 -0800 | Yuya Ebihara | Build: Bump datamodel-code-generator from 0.49.0 to 0.52.1 (#14962) | ✅ 已完成 | [3060_64b7b6622](commits/3060_64b7b6622/analysis.md) |
| 3061 | `3048d772a` | 2026-01-05 14:22:39 +0100 | Maximilian Michels | Flink: Dynamic Sink: Fix serialization issues with schemas larger than 2^16 bytes (#14880) | ✅ 已完成 | [3061_3048d772a](commits/3061_3048d772a/analysis.md) |
| 3062 | `4bd1fb863` | 2026-01-05 14:45:28 +0100 | aiborodin | Flink: DynamicSink: Report writer records/bytes send metrics (#14878) | ✅ 已完成 | [3062_4bd1fb863](commits/3062_4bd1fb863/analysis.md) |
| 3063 | `4bc934b9d` | 2026-01-05 08:18:22 -0800 | Prashant Singh | Spark 3.4 \| 3.5: Enable remote scan planning (#14963) | ✅ 已完成 | [3063_4bc934b9d](commits/3063_4bc934b9d/analysis.md) |
| 3064 | `bc7bfa5de` | 2026-01-05 17:49:48 +0100 | Maximilian Michels | Flink: Backport: Dynamic Sink: Fix serialization issues with schemas larger than 2^16 bytes(#14967) | ✅ 已完成 | [3064_bc7bfa5de](commits/3064_bc7bfa5de/analysis.md) |
| 3065 | `8f2ed2068` | 2026-01-06 10:06:56 +0100 | aiborodin | Flink: Backport: DynamicSink: Report writer records/bytes send metrics (#14971) | ✅ 已完成 | [3065_8f2ed2068](commits/3065_8f2ed2068/analysis.md) |
| 3066 | `42cac92c4` | 2026-01-06 13:26:31 +0100 | GuoYu | Flink: Fix equalityFieldColumns always null in IcebergSink (#14952) | ✅ 已完成 | [3066_42cac92c4](commits/3066_42cac92c4/analysis.md) |
| 3067 | `4fe8ae2d3` | 2026-01-06 09:08:55 -0800 | GuoYu | Flink: Backport fix equalityFieldColumns always null in IcebergSink (#14975) | ✅ 已完成 | [3067_4fe8ae2d3](commits/3067_4fe8ae2d3/analysis.md) |
| 3068 | `d75451833` | 2026-01-06 12:50:13 -0700 | Hongyue/Steve Zhang | Core: Reduce manifest logging noise on drop table (#14969) | ✅ 已完成 | [3068_d75451833](commits/3068_d75451833/analysis.md) |
| 3069 | `bde85b0c3` | 2026-01-06 14:32:51 -0600 | Joy Haldar | API, Spark: Optimize NOT IN and != predicate evaluation for fields containing a single-value (#14593) | ✅ 已完成 | [3069_bde85b0c3](commits/3069_bde85b0c3/analysis.md) |
| 3070 | `7bfe14417` | 2026-01-06 13:02:08 -0800 | Huaxin Gao | Flink: fix VisibleForTesting import in ZkLockFactory (#14977) | ✅ 已完成 | [3070_7bfe14417](commits/3070_7bfe14417/analysis.md) |
| 3071 | `234af35ae` | 2026-01-06 20:17:01 -0500 | Kevin Liu | site: fix live loading in make serve-dev | ✅ 已完成 | [3071_234af35ae](commits/3071_234af35ae/analysis.md) |
| 3072 | `46c871ccb` | 2026-01-07 09:22:14 +0100 | Stas Pak | Spark: Add Spark app name to env context (#14976) | ✅ 已完成 | [3072_46c871ccb](commits/3072_46c871ccb/analysis.md) |
| 3073 | `055a73a92` | 2026-01-07 09:33:45 +0100 | Thomas Powell | AWS: Merge catalog properties with properties prefixed with client.credentials-provider. (#14608) | ✅ 已完成 | [3073_055a73a92](commits/3073_055a73a92/analysis.md) |
| 3074 | `b07c1e570` | 2026-01-07 12:19:22 +0100 | Varun Lakhyani | Spark: Backport: Add Spark app name to env context for Spark v3.4, 3.5, 4.0 (#14981) | ✅ 已完成 | [3074_b07c1e570](commits/3074_b07c1e570/analysis.md) |
| 3075 | `51d548a4f` | 2026-01-07 13:58:23 +0100 | gaborkaszab | API, Core: Scan API for partition stats (#14640) | ✅ 已完成 | [3075_51d548a4f](commits/3075_51d548a4f/analysis.md) |
| 3076 | `1dce77c78` | 2026-01-07 15:35:54 +0100 | Ayush Saxena | Data: Handle TIMESTAMP_NANO in InternalRecordWrapper (#14974) | ✅ 已完成 | [3076_1dce77c78](commits/3076_1dce77c78/analysis.md) |
| 3077 | `b3b665735` | 2026-01-07 11:58:56 -0600 | Russell Spitzer | Site: Add Iceberg Summit 2026 section to homepage (#14988) | ✅ 已完成 | [3077_b3b665735](commits/3077_b3b665735/analysis.md) |
| 3078 | `aee89008a` | 2026-01-07 13:18:25 -0800 | Thomas Powell | Include key metadata in manifest tables (#14750) | ✅ 已完成 | [3078_aee89008a](commits/3078_aee89008a/analysis.md) |
| 3079 | `88d833b06` | 2026-01-07 17:30:01 -0800 | Ashok | Core: Handle NotFound exception for missing metadata file (#13143) | ✅ 已完成 | [3079_88d833b06](commits/3079_88d833b06/analysis.md) |
| 3080 | `99f14e7eb` | 2026-01-07 17:55:48 -0800 | Szehon Ho | Spark 4.1: Initial support for MERGE INTO schema evolution (#14970) | ✅ 已完成 | [3080_99f14e7eb](commits/3080_99f14e7eb/analysis.md) |
| 3081 | `daa3bb257` | 2026-01-07 21:56:26 -0800 | Kevin Liu | manually update spark 3.4 (#14993) | ✅ 已完成 | [3081_daa3bb257](commits/3081_daa3bb257/analysis.md) |
| 3082 | `01e324022` | 2026-01-08 01:20:51 -0500 | Kevin Liu | site infra: when running `make serve`, add a tip on using `make serve-dev` instead | ✅ 已完成 | [3082_01e324022](commits/3082_01e324022/analysis.md) |
| 3083 | `cde5b9f98` | 2026-01-08 07:05:54 -0800 | Robin Moffatt | Kafka Connect: Fix CVE-2025-55163 in grpc-netty-shaded (#14985) | ✅ 已完成 | [3083_cde5b9f98](commits/3083_cde5b9f98/analysis.md) |
| 3084 | `615b5a097` | 2026-01-08 11:45:02 -0600 | Hongyue/Steve Zhang | Core: Unlink table metadata's last-updated timestamp from snapshot timestamp (#14504) | ✅ 已完成 | [3084_615b5a097](commits/3084_615b5a097/analysis.md) |
| 3085 | `0094ccc0a` | 2026-01-08 22:52:23 +0100 | pvary | Use SnapshotRef.MAIN_BRANCH instead of the 'main' string (#14999) | ✅ 已完成 | [3085_0094ccc0a](commits/3085_0094ccc0a/analysis.md) |
| 3086 | `a7b8a08b2` | 2026-01-08 17:17:17 -0800 | Hongyue/Steve Zhang | Spark 4.1: Fix spark 4.1 test for unlink table metadata's last-updated timestamp (#15004) | ✅ 已完成 | [3086_a7b8a08b2](commits/3086_a7b8a08b2/analysis.md) |
| 3087 | `a90848e21` | 2026-01-08 23:18:24 -0500 | Kevin Liu | infra: add gradle cache to github workflows | ✅ 已完成 | [3087_a90848e21](commits/3087_a90848e21/analysis.md) |
| 3088 | `4a4d73408` | 2026-01-09 08:39:51 +0100 | Eduard Tudenhoefner | Core: Add storage credentials to FetchPlanningResultResponse (#14994) | ✅ 已完成 | [3088_4a4d73408](commits/3088_4a4d73408/analysis.md) |
| 3089 | `6f7b5688d` | 2026-01-09 11:33:41 +0100 | aiborodin | Flink: Dynamic Sink: Refactor write result aggregation (#14810) | ✅ 已完成 | [3089_6f7b5688d](commits/3089_6f7b5688d/analysis.md) |
| 3090 | `a1c1c1b5a` | 2026-01-09 12:26:14 +0100 | Maximilian Michels | Core: Support case-insensitive field lookups in SchemaUpdate (#14734) | ✅ 已完成 | [3090_a1c1c1b5a](commits/3090_a1c1c1b5a/analysis.md) |
| 3091 | `d85f8a87a` | 2026-01-09 08:40:24 -0800 | Daniel Weeks | Kafka Connect: validate table uuid on commit (#14979) | ✅ 已完成 | [3091_d85f8a87a](commits/3091_d85f8a87a/analysis.md) |
| 3092 | `b2696b959` | 2026-01-09 13:20:02 -0800 | Bryan Keller | Kafka Connect: fix table UUID check (#15011) | ✅ 已完成 | [3092_b2696b959](commits/3092_b2696b959/analysis.md) |
| 3093 | `2c92500e9` | 2026-01-10 07:18:15 +0100 | Varun Lakhyani | Spark: Add location overlap validation for SnapshotTableAction (#14933) | ✅ 已完成 | [3093_2c92500e9](commits/3093_2c92500e9/analysis.md) |
| 3094 | `b4bb71fc4` | 2026-01-10 09:30:07 -0800 | Varun Lakhyani | Spark: Backport #14933: Snapshot location overlap check to spark v3.4, v3.5, v4.0 (#15016) | ✅ 已完成 | [3094_b4bb71fc4](commits/3094_b4bb71fc4/analysis.md) |
| 3095 | `d67141036` | 2026-01-10 21:12:23 -0800 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.52.1 to 0.52.2 (#15018) | ✅ 已完成 | [3095_d67141036](commits/3095_d67141036/analysis.md) |
| 3096 | `7fd8a2ddc` | 2026-01-10 21:13:43 -0800 | dependabot[bot] | Build: Bump io.grpc:grpc-netty-shaded from 1.76.2 to 1.78.0 (#15024) | ✅ 已完成 | [3096_7fd8a2ddc](commits/3096_7fd8a2ddc/analysis.md) |
| 3097 | `12f935459` | 2026-01-11 00:30:25 -0800 | dependabot[bot] | Build: Bump nessie from 0.106.0 to 0.106.1 (#15019) | ✅ 已完成 | [3097_12f935459](commits/3097_12f935459/analysis.md) |
| 3098 | `a401603a9` | 2026-01-11 00:30:39 -0800 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#15020) | ✅ 已完成 | [3098_a401603a9](commits/3098_a401603a9/analysis.md) |
| 3099 | `95d7405d3` | 2026-01-11 09:05:45 -0800 | dependabot[bot] | Build: Bump junit-platform from 1.14.1 to 1.14.2 (#15021) | ✅ 已完成 | [3099_95d7405d3](commits/3099_95d7405d3/analysis.md) |
| 3100 | `ccf4dfbb6` | 2026-01-11 09:06:11 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.41.1 to 2.41.5 (#15022) | ✅ 已完成 | [3100_ccf4dfbb6](commits/3100_ccf4dfbb6/analysis.md) |
| 3101 | `cc966fc96` | 2026-01-11 09:07:03 -0800 | dependabot[bot] | Build: Bump org.immutables:value from 2.12.0 to 2.12.1 (#15026) | ✅ 已完成 | [3101_cc966fc96](commits/3101_cc966fc96/analysis.md) |
| 3102 | `c73116ab3` | 2026-01-11 09:07:43 -0800 | dependabot[bot] | Build: Bump orc from 1.9.7 to 1.9.8 (#15025) | ✅ 已完成 | [3102_c73116ab3](commits/3102_c73116ab3/analysis.md) |
| 3103 | `b7d9817cc` | 2026-01-11 10:44:30 -0800 | dependabot[bot] | Build: Bump junit from 5.14.1 to 5.14.2 (#15023) | ✅ 已完成 | [3103_b7d9817cc](commits/3103_b7d9817cc/analysis.md) |
| 3104 | `420612246` | 2026-01-11 23:40:36 -0800 | Manu Zhang | Spark 4.1: Upgrade to Spark 4.1.1 (#14946) | ✅ 已完成 | [3104_420612246](commits/3104_420612246/analysis.md) |
| 3105 | `7f81e1e93` | 2026-01-12 14:14:03 +0100 | gaborkaszab | Core: Use scan API to read partition stats (#14989) | ✅ 已完成 | [3105_7f81e1e93](commits/3105_7f81e1e93/analysis.md) |
| 3106 | `f8ee29e6e` | 2026-01-12 08:14:17 -0800 | Manu Zhang | Core: Drop support for Java 11 (#14400) | ✅ 已完成 | [3106_f8ee29e6e](commits/3106_f8ee29e6e/analysis.md) |
| 3107 | `cbf07cba8` | 2026-01-13 13:00:28 +0100 | Eduard Tudenhoefner | AWS, Azure, Core, GCP: Pass planId when refreshing vended credentials (#14767) | ✅ 已完成 | [3107_cbf07cba8](commits/3107_cbf07cba8/analysis.md) |
| 3108 | `42c7f475d` | 2026-01-13 13:59:03 +0100 | gaborkaszab | Core, Data, Spark: Use partition stats scan API in tests (#14996) | ✅ 已完成 | [3108_42c7f475d](commits/3108_42c7f475d/analysis.md) |
| 3109 | `d5b83fc3a` | 2026-01-13 08:15:20 -0800 | Thomas Powell | Include key metadata in manifest tables (Spark 4.1) (#15041) | ✅ 已完成 | [3109_d5b83fc3a](commits/3109_d5b83fc3a/analysis.md) |
| 3110 | `243badb3f` | 2026-01-13 09:00:46 -0800 | Kevin Liu | site: Apache Iceberg Project News and Blog (#15013) | ✅ 已完成 | [3110_243badb3f](commits/3110_243badb3f/analysis.md) |
| 3111 | `779af1231` | 2026-01-13 09:41:01 -0800 | Kevin Liu | add registeristration link closer to the top (#15044) | ✅ 已完成 | [3111_779af1231](commits/3111_779af1231/analysis.md) |
| 3112 | `bd96b79b4` | 2026-01-14 07:41:39 +0100 | Eduard Tudenhoefner | Core, Hive: Detect if a view already exists when registering a table (#15010) | ✅ 已完成 | [3112_bd96b79b4](commits/3112_bd96b79b4/analysis.md) |
| 3113 | `046298f63` | 2026-01-14 11:06:38 +0100 | Fokko Driesprong | Bump to Parquet 1.17.0 (#14924) | ✅ 已完成 | [3113_046298f63](commits/3113_046298f63/analysis.md) |
| 3114 | `b62802faf` | 2026-01-14 09:08:56 -0800 | Eduard Tudenhoefner | Spark: Add test coverage for Hive View catalog (#15048) | ✅ 已完成 | [3114_b62802faf](commits/3114_b62802faf/analysis.md) |
| 3115 | `c1aed477d` | 2026-01-14 13:14:44 -0800 | Eduard Tudenhoefner | Spark 3.5,4.0: Add test coverage for Hive View catalog (#15052) | ✅ 已完成 | [3115_c1aed477d](commits/3115_c1aed477d/analysis.md) |
| 3116 | `035e0fb39` | 2026-01-15 11:44:25 -0800 | Daniel Weeks | REST Spec: clarify uniqueness of ETags for table metadata responses (#15045) | ✅ 已完成 | [3116_035e0fb39](commits/3116_035e0fb39/analysis.md) |
| 3117 | `38cc88136` | 2026-01-15 14:52:53 -0800 | Joy Haldar | BigQuery: Eliminate redundant table load by using ETag for conflict detection (#14940) | ✅ 已完成 | [3117_38cc88136](commits/3117_38cc88136/analysis.md) |
| 3118 | `b4ef17cd1` | 2026-01-16 14:51:35 +0100 | Harsh Sharma | Spark: Add branch support to rewrite_data_files procedure (#14964) | ✅ 已完成 | [3118_b4ef17cd1](commits/3118_b4ef17cd1/analysis.md) |
| 3119 | `53374bde6` | 2026-01-16 15:21:34 +0100 | Ajantha Bhat | API, Core: Support registerView for view catalog (#14868) | ✅ 已完成 | [3119_53374bde6](commits/3119_53374bde6/analysis.md) |
| 3120 | `dd2a5684e` | 2026-01-16 16:46:03 +0100 | Manu Zhang | Spark 3.5: Upgrade to Spark 3.5.8 (#15033) | ✅ 已完成 | [3120_dd2a5684e](commits/3120_dd2a5684e/analysis.md) |
| 3121 | `4f5768738` | 2026-01-16 09:52:55 -0800 | Huaxin Gao | Add idempotency adapter and E2E coverage (#14773) | ✅ 已完成 | [3121_4f5768738](commits/3121_4f5768738/analysis.md) |
| 3122 | `bf5496155` | 2026-01-16 21:05:12 -0800 | Ajantha Bhat | OpenAPI: Add REST endpoint for registering views (#14869) | ✅ 已完成 | [3122_bf5496155](commits/3122_bf5496155/analysis.md) |
| 3123 | `e93eaab33` | 2026-01-17 08:59:18 -0800 | zhaoyunjiong | Aliyun: Add RRSA support for OSS authentication (#14443) | ✅ 已完成 | [3123_e93eaab33](commits/3123_e93eaab33/analysis.md) |
| 3124 | `60010cb12` | 2026-01-17 21:19:05 -0800 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.52.2 to 0.53.0 (#15074) | ✅ 已完成 | [3124_60010cb12](commits/3124_60010cb12/analysis.md) |
| 3125 | `491e00a59` | 2026-01-17 21:41:03 -0800 | dependabot[bot] | Build: Bump yamllint from 1.37.1 to 1.38.0 (#15075) | ✅ 已完成 | [3125_491e00a59](commits/3125_491e00a59/analysis.md) |
| 3126 | `6aa58e4fc` | 2026-01-17 22:08:03 -0800 | dependabot[bot] | Build: Bump com.aliyun:credentials-java from 0.3.2 to 0.3.12 (#15076) | ✅ 已完成 | [3126_6aa58e4fc](commits/3126_6aa58e4fc/analysis.md) |
| 3127 | `bfc76145f` | 2026-01-17 22:08:22 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.41.5 to 2.41.10 (#15077) | ✅ 已完成 | [3127_bfc76145f](commits/3127_bfc76145f/analysis.md) |
| 3128 | `6134dd218` | 2026-01-17 22:08:39 -0800 | dependabot[bot] | Build: Bump com.aliyun:tea from 1.2.1 to 1.4.1 (#15078) | ✅ 已完成 | [3128_6134dd218](commits/3128_6134dd218/analysis.md) |
| 3129 | `72d5fd66c` | 2026-01-18 08:46:08 +0100 | gaborkaszab | Core: Freshness-aware table loading in REST catalog (#14398) | ✅ 已完成 | [3129_72d5fd66c](commits/3129_72d5fd66c/analysis.md) |
| 3130 | `7acc150ac` | 2026-01-19 07:56:39 +0100 | Ajantha Bhat | Core: Add RegisterViewRequest, parser, and serializers (#15068) | ✅ 已完成 | [3130_7acc150ac](commits/3130_7acc150ac/analysis.md) |
| 3131 | `2d6f4bbfb` | 2026-01-19 16:08:15 +0100 | Resort_Annex | Core: Remove unused import in ParquetConversions (#15081) | ✅ 已完成 | [3131_2d6f4bbfb](commits/3131_2d6f4bbfb/analysis.md) |
| 3132 | `504640f78` | 2026-01-19 17:45:29 +0100 | Maximilian Michels | Flink: Dynamic Sink: Add case-insensitive field matching (#14729) | ✅ 已完成 | [3132_504640f78](commits/3132_504640f78/analysis.md) |
| 3133 | `9b573c7b4` | 2026-01-19 10:55:45 -0800 | Raunaq Morarka | API: Use Transform#isIdentity in PartitionSpec#identitySourceIds (#15066) | ✅ 已完成 | [3133_9b573c7b4](commits/3133_9b573c7b4/analysis.md) |
| 3134 | `8967729be` | 2026-01-19 19:00:26 -0800 | Manu Zhang | Build: Bump roaringbitmap from 1.3.0 to 1.6.0 (#14991) | ✅ 已完成 | [3134_8967729be](commits/3134_8967729be/analysis.md) |
| 3135 | `dc708e675` | 2026-01-20 09:07:59 +0100 | aiborodin | Flink: Backport: Dynamic Sink: Refactor write result aggregation (#15054) | ✅ 已完成 | [3135_dc708e675](commits/3135_dc708e675/analysis.md) |
| 3136 | `84be38acc` | 2026-01-20 01:16:25 -0800 | pvary | Flink: Backport: Add test to ensure that append commits are created in dynamic iceberg sink when possible (#15088) | ✅ 已完成 | [3136_84be38acc](commits/3136_84be38acc/analysis.md) |
| 3137 | `8e255cd91` | 2026-01-20 17:20:55 +0100 | Maximilian Michels | Flink: Backport: Dynamic Sink: Add case-insensitive field matching (#15089) | ✅ 已完成 | [3137_8e255cd91](commits/3137_8e255cd91/analysis.md) |
| 3138 | `bfec39f64` | 2026-01-20 11:03:10 -0800 | Thomas Powell | Make StandardEncryptionManager serializable (#14751) | ✅ 已完成 | [3138_bfec39f64](commits/3138_bfec39f64/analysis.md) |
| 3139 | `15485f552` | 2026-01-21 09:01:01 +0100 | Harsh Sharma | Spark: Backport adding branch support to rewrite_data_files procedure (#15067) | ✅ 已完成 | [3139_15485f552](commits/3139_15485f552/analysis.md) |
| 3140 | `135659c2d` | 2026-01-21 16:46:57 -0800 | Varun Lakhyani | Spark 4.1: Add tests for MERGE INTO schema evolution nested case (#15028) | ✅ 已完成 | [3140_135659c2d](commits/3140_135659c2d/analysis.md) |
| 3141 | `15a72dc82` | 2026-01-22 10:04:50 +0100 | Ajantha Bhat | Core: Implement register view for REST catalog (#14870) | ✅ 已完成 | [3141_15a72dc82](commits/3141_15a72dc82/analysis.md) |
| 3142 | `1a4f42339` | 2026-01-22 16:12:20 +0100 | gaborkaszab | API, Core: Move partition stat schema creation to API (#15083) | ✅ 已完成 | [3142_1a4f42339](commits/3142_1a4f42339/analysis.md) |
| 3143 | `157b2488e` | 2026-01-22 13:01:33 -0600 | Joy Haldar | API: Optimize NOT IN and != predicates for single-value partition manifests (#15064) | ✅ 已完成 | [3143_157b2488e](commits/3143_157b2488e/analysis.md) |
| 3144 | `73a26fc1f` | 2026-01-22 17:51:55 -0800 | Huaxin Gao | Docs: Fix publish_changes wap_id parameter type (#15117) | ✅ 已完成 | [3144_73a26fc1f](commits/3144_73a26fc1f/analysis.md) |
| 3145 | `672e8603a` | 2026-01-23 07:59:23 -0800 | Manu Zhang | Site: Add RSS feed for blogs (#15071) | ✅ 已完成 | [3145_672e8603a](commits/3145_672e8603a/analysis.md) |
| 3146 | `7493a158b` | 2026-01-23 17:13:36 +0100 | Maximilian Michels | Flink: Test Parquet writer default handling via core's DataTestBase (#15123) | ✅ 已完成 | [3146_7493a158b](commits/3146_7493a158b/analysis.md) |
| 3147 | `51ae34665` | 2026-01-23 09:13:29 -0800 | Manu Zhang | Build: Bump comet version from 0.10.1 to 0.12.0 (#15105) | ✅ 已完成 | [3147_51ae34665](commits/3147_51ae34665/analysis.md) |
| 3148 | `96a59408b` | 2026-01-23 09:27:03 -0800 | Maximilian Michels | Flink: Backport: Test Parquet writer default handling via core's DataTestBase (#15123) (#15125) | ✅ 已完成 | [3148_96a59408b](commits/3148_96a59408b/analysis.md) |
| 3149 | `829b64146` | 2026-01-23 16:12:19 -0800 | Szehon Ho | Spark 4.1: Add Merge WriteSummary to Snapshot Summary (#15014) | ✅ 已完成 | [3149_829b64146](commits/3149_829b64146/analysis.md) |
| 3150 | `cca746b13` | 2026-01-24 20:51:38 -0800 | dependabot[bot] | Build: Bump pymarkdownlnt from 0.9.34 to 0.9.35 (#15130) | ✅ 已完成 | [3150_cca746b13](commits/3150_cca746b13/analysis.md) |
| 3151 | `a95bb8867` | 2026-01-24 21:09:37 -0800 | dependabot[bot] | Build: Bump mkdocs-rss-plugin from 1.17.4 to 1.17.9 (#15131) | ✅ 已完成 | [3151_a95bb8867](commits/3151_a95bb8867/analysis.md) |
| 3152 | `aa630608f` | 2026-01-25 09:30:44 -0800 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#15129) | ✅ 已完成 | [3152_aa630608f](commits/3152_aa630608f/analysis.md) |
| 3153 | `24522c3f8` | 2026-01-25 09:31:10 -0800 | dependabot[bot] | Build: Bump org.assertj:assertj-core from 3.27.6 to 3.27.7 (#15132) | ✅ 已完成 | [3153_24522c3f8](commits/3153_24522c3f8/analysis.md) |
| 3154 | `755ea35b3` | 2026-01-25 09:32:04 -0800 | dependabot[bot] | Build: Bump jackson-bom from 2.20.1 to 2.21.0 (#15133) | ✅ 已完成 | [3154_755ea35b3](commits/3154_755ea35b3/analysis.md) |
| 3155 | `f8ed0da8b` | 2026-01-25 09:32:37 -0800 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.73.0 to 26.74.0 (#15135) | ✅ 已完成 | [3155_f8ed0da8b](commits/3155_f8ed0da8b/analysis.md) |
| 3156 | `485a4d00c` | 2026-01-25 12:02:03 -0800 | dependabot[bot] | Build: Bump com.fasterxml.jackson.core:jackson-annotations (#15136) | ✅ 已完成 | [3156_485a4d00c](commits/3156_485a4d00c/analysis.md) |
| 3157 | `345ce9187` | 2026-01-26 13:56:40 +0800 | Feiyang Li | Docs: Add C++ library to implementation status (#15107) | ✅ 已完成 | [3157_345ce9187](commits/3157_345ce9187/analysis.md) |
| 3158 | `4368c3a62` | 2026-01-26 08:47:19 +0100 | gaborkaszab | Core: Refactor test suite for freshness-aware loading (#15082) | ✅ 已完成 | [3158_4368c3a62](commits/3158_4368c3a62/analysis.md) |
| 3159 | `7a4d786e7` | 2026-01-26 09:34:21 +0100 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.41.10 to 2.41.14 (#15134) | ✅ 已完成 | [3159_7a4d786e7](commits/3159_7a4d786e7/analysis.md) |
| 3160 | `7bead8ab7` | 2026-01-26 12:56:42 -0600 | Huaxin Gao | Core: Invalidate ShreddedObject serialization cache on remove (#15097) | ✅ 已完成 | [3160_7bead8ab7](commits/3160_7bead8ab7/analysis.md) |
| 3161 | `d75c162c9` | 2026-01-26 11:14:29 -0800 | zengyz | Build: Bump gradle-wrapper to 8.14.4 (#15143) | ✅ 已完成 | [3161_d75c162c9](commits/3161_d75c162c9/analysis.md) |
| 3162 | `2dc306fb6` | 2026-01-26 17:47:51 -0800 | Gang Wu | Docs: add blog post for C++ 0.2.0 release (#15141) | ✅ 已完成 | [3162_2dc306fb6](commits/3162_2dc306fb6/analysis.md) |
| 3163 | `ec93fe231` | 2026-01-26 17:52:41 -0800 | Maximilian Michels | Flink: Fix test assumption which can produce flakiness  (#15147) | ✅ 已完成 | [3163_ec93fe231](commits/3163_ec93fe231/analysis.md) |
| 3164 | `3f7497361` | 2026-01-26 18:12:23 -0800 | Kevin Liu | site: add slug to be explicit about blog url (#15149) | ✅ 已完成 | [3164_3f7497361](commits/3164_3f7497361/analysis.md) |
| 3165 | `0e37d50fe` | 2026-01-27 09:15:38 -0800 | Eduard Tudenhoefner | Build: Bump spotless gradle plugin to 8.2.0 (#15156) | ✅ 已完成 | [3165_0e37d50fe](commits/3165_0e37d50fe/analysis.md) |
| 3166 | `d43031b7d` | 2026-01-27 09:17:23 -0800 | Eduard Tudenhoefner | Build: Bump gradle-git-version to 4.2.0 (#15157) | ✅ 已完成 | [3166_d43031b7d](commits/3166_d43031b7d/analysis.md) |
| 3167 | `1bf44d9fe` | 2026-01-27 09:28:26 -0800 | Resort_Annex | Build: Fix typo in variable name spaceSeparatedPlanId (#15158) | ✅ 已完成 | [3167_1bf44d9fe](commits/3167_1bf44d9fe/analysis.md) |
| 3168 | `696acfaf3` | 2026-01-27 21:31:07 -0700 | Alessandro Nori | API, Spark 4.1: Add `orphanFilesCount` to `DeleteOrphanFiles.Result` (#14886) | ✅ 已完成 | [3168_696acfaf3](commits/3168_696acfaf3/analysis.md) |
| 3169 | `fec9800bc` | 2026-01-28 12:51:48 +0100 | Eduard Tudenhoefner | Build: Bump gradle-baseline-java to 6.90.0 (#15160) | ✅ 已完成 | [3169_fec9800bc](commits/3169_fec9800bc/analysis.md) |
| 3170 | `03ed4ba9a` | 2026-01-28 15:48:31 +0100 | Ayush Saxena | Fix Internal to Generic conversion of TIMESTAMP_NANO (#15099) | ✅ 已完成 | [3170_03ed4ba9a](commits/3170_03ed4ba9a/analysis.md) |
| 3171 | `42a959791` | 2026-01-28 10:17:24 -0800 | Kevin Liu | site: add docs about Requesting Slack Integrations (#15170) | ✅ 已完成 | [3171_42a959791](commits/3171_42a959791/analysis.md) |
| 3172 | `6c7f458d9` | 2026-01-28 13:22:00 -0800 | Kevin Liu | Build/Release: Upgrade to RAT 0.17 (#15145) | ✅ 已完成 | [3172_6c7f458d9](commits/3172_6c7f458d9/analysis.md) |
| 3173 | `b7181e8f7` | 2026-01-28 13:23:11 -0800 | Dong Wang | Fix incorrect partition bounds calculation in manifest on deletion (#15127) | ✅ 已完成 | [3173_b7181e8f7](commits/3173_b7181e8f7/analysis.md) |
| 3174 | `b9f6660be` | 2026-01-28 20:21:17 -0800 | Kevin Liu | Build/Release: use RAT collection for ignore file patterns (#15174) | ✅ 已完成 | [3174_b9f6660be](commits/3174_b9f6660be/analysis.md) |
| 3175 | `83653ba9a` | 2026-01-29 14:58:23 +0800 | Debjani Banerjee | Docs: Fix bullet list formatting in fileio usage section (#15106) | ✅ 已完成 | [3175_83653ba9a](commits/3175_83653ba9a/analysis.md) |
| 3176 | `58d8fd742` | 2026-01-29 07:38:41 -0800 | Junwang Zhao | Docs: fix iceberg-cpp website link (#15177) | ✅ 已完成 | [3176_58d8fd742](commits/3176_58d8fd742/analysis.md) |
| 3177 | `707be1a6a` | 2026-01-29 08:56:47 -0800 | Alessandro Nori | Spark: backport `#14886` to other Spark versions (#15178) | ✅ 已完成 | [3177_707be1a6a](commits/3177_707be1a6a/analysis.md) |
| 3178 | `e24e6e9c0` | 2026-01-29 11:53:07 -0800 | yguy-ryft | Docs: Add Ryft to third-party integrations (#15179) | ✅ 已完成 | [3178_e24e6e9c0](commits/3178_e24e6e9c0/analysis.md) |
| 3179 | `a55d1235d` | 2026-01-29 15:52:23 -0800 | Sam Wheating | Spark 4.1 \| 4.0 \| 3.5 \| 3.4: Fail publish_changes procedure if there's more than one matching snapshot (#14955) | ✅ 已完成 | [3179_a55d1235d](commits/3179_a55d1235d/analysis.md) |
| 3180 | `a0dbed018` | 2026-01-29 15:57:35 -0800 | Yuya Ebihara | GCP: Add gcp.auth.credentials-key property (#14713) | ✅ 已完成 | [3180_a0dbed018](commits/3180_a0dbed018/analysis.md) |
| 3181 | `e40c2d653` | 2026-01-29 20:24:26 -0700 | yan zhang | Core: Fix data loss in partial variant shredding (#15087) | ✅ 已完成 | [3181_e40c2d653](commits/3181_e40c2d653/analysis.md) |
| 3182 | `8072acd66` | 2026-01-29 19:43:33 -0800 | Manu Zhang | API, Hive, Spark: Fix typos in comments and error messages (#15181) | ✅ 已完成 | [3182_8072acd66](commits/3182_8072acd66/analysis.md) |
| 3183 | `b2f312f43` | 2026-01-30 09:26:05 -0700 | Thomas Powell | AWS, Azure, GCP: Configure headers in HTTPClient from properties (#15110) | ✅ 已完成 | [3183_b2f312f43](commits/3183_b2f312f43/analysis.md) |
| 3184 | `f23486ffd` | 2026-01-30 09:27:40 -0700 | Vaibhav Ahuja | AWS: Remove HEAD operation from AnalyticsAcceleratorUtil (#15116) | ✅ 已完成 | [3184_f23486ffd](commits/3184_f23486ffd/analysis.md) |
| 3185 | `738c7a475` | 2026-01-31 09:11:04 -0700 | Edward Gao | AWS: Set retry policy on glue and dynamo clients (#15094) | ✅ 已完成 | [3185_738c7a475](commits/3185_738c7a475/analysis.md) |
| 3186 | `5b5e51104` | 2026-01-31 09:17:21 -0800 | Kevin Liu | Build/Release: fix RAT command (#15194) | ✅ 已完成 | [3186_5b5e51104](commits/3186_5b5e51104/analysis.md) |
| 3187 | `8f3328a76` | 2026-01-31 23:05:15 -0800 | dependabot[bot] | Build: Bump nessie from 0.106.1 to 0.107.0 (#15197) | ✅ 已完成 | [3187_8f3328a76](commits/3187_8f3328a76/analysis.md) |
| 3188 | `3848f1358` | 2026-01-31 23:05:34 -0800 | dependabot[bot] | Build: Bump com.aliyun.oss:aliyun-sdk-oss from 3.18.4 to 3.18.5 (#15198) | ✅ 已完成 | [3188_3848f1358](commits/3188_3848f1358/analysis.md) |
| 3189 | `06569e71d` | 2026-01-31 23:05:59 -0800 | dependabot[bot] | Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#15199) | ✅ 已完成 | [3189_06569e71d](commits/3189_06569e71d/analysis.md) |
| 3190 | `b3da7d176` | 2026-01-31 23:06:19 -0800 | dependabot[bot] | Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#15200) | ✅ 已完成 | [3190_b3da7d176](commits/3190_b3da7d176/analysis.md) |
| 3191 | `85245f74e` | 2026-01-31 23:06:41 -0800 | dependabot[bot] | Build: Bump com.palantir.gradle.gitversion:gradle-git-version (#15201) | ✅ 已完成 | [3191_85245f74e](commits/3191_85245f74e/analysis.md) |
| 3192 | `e7bfab6c5` | 2026-01-31 23:07:05 -0800 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.74.0 to 26.75.0 (#15202) | ✅ 已完成 | [3192_e7bfab6c5](commits/3192_e7bfab6c5/analysis.md) |
| 3193 | `8b4e22fe5` | 2026-01-31 23:07:32 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.41.14 to 2.41.19 (#15203) | ✅ 已完成 | [3193_8b4e22fe5](commits/3193_8b4e22fe5/analysis.md) |
| 3194 | `75f9451d9` | 2026-02-01 15:01:43 -0800 | Prashant Singh | Build: Bump com.azure:azure-sdk-bom from 1.3.3 to 1.3.4  (#15204) | ✅ 已完成 | [3194_75f9451d9](commits/3194_75f9451d9/analysis.md) |
| 3195 | `eb9360909` | 2026-02-01 21:39:57 -0800 | Chihiro | ORC: Fix typos in IdToOrcName and ORC JavaDoc (#15214) | ✅ 已完成 | [3195_eb9360909](commits/3195_eb9360909/analysis.md) |
| 3196 | `84fa33f6e` | 2026-02-02 12:19:23 +0100 | Maximilian Michels | Flink: Dynamic Sink: Fix partition field check in non-immediate update path (#15190) | ✅ 已完成 | [3196_84fa33f6e](commits/3196_84fa33f6e/analysis.md) |
| 3197 | `63d408472` | 2026-02-02 09:28:35 -0700 | Alessandro Nori | Core: Do not cleanup when CREATE transactions fail with 503 (#15051) | ✅ 已完成 | [3197_63d408472](commits/3197_63d408472/analysis.md) |
| 3198 | `c6b252e97` | 2026-02-02 09:50:31 -0700 | gaborkaszab | Core: Skip unnecessary metadata refresh when producing snapshot event after merge append (#14709) | ✅ 已完成 | [3198_c6b252e97](commits/3198_c6b252e97/analysis.md) |
| 3199 | `995101f17` | 2026-02-02 11:14:26 -0800 | Maximilian Michels | Flink: Backport: Dynamic Sink: Fix partition field check in non-immediate update path (#15190) (#15216) | ✅ 已完成 | [3199_995101f17](commits/3199_995101f17/analysis.md) |
| 3200 | `336fc0e48` | 2026-02-03 08:15:35 -0800 | Shawn Chang | doc: Update AWS vendor doc (#15222) | ✅ 已完成 | [3200_336fc0e48](commits/3200_336fc0e48/analysis.md) |
| 3201 | `d0654440a` | 2026-02-03 09:22:30 -0800 | Chihiro | ORC: Fix additional typos in ORCSchemaUtil, OrcMetrics, and others (#15219) | ✅ 已完成 | [3201_d0654440a](commits/3201_d0654440a/analysis.md) |
| 3202 | `45d2ed0e7` | 2026-02-03 12:52:02 -0800 | Kristin Cowalcijk | API: Simplify sanitization of literals in predicates (#15224) | ✅ 已完成 | [3202_45d2ed0e7](commits/3202_45d2ed0e7/analysis.md) |
| 3203 | `e786514c9` | 2026-02-03 15:46:17 -0800 | Kevin Liu | Docs: add blog post for iceberg-rust 0.8.0 release (#15221) | ✅ 已完成 | [3203_e786514c9](commits/3203_e786514c9/analysis.md) |
| 3204 | `5970ddd92` | 2026-02-04 06:58:53 +0100 | Miguel A. Sotomayor | Core: Add code/type in RestException (#14927) | ✅ 已完成 | [3204_5970ddd92](commits/3204_5970ddd92/analysis.md) |
| 3205 | `f03595376` | 2026-02-04 07:43:13 -0700 | Logesh R | REST Spec: Include SetPartitionStatisticsUpdate and RemovePartitionStatisticsUpdate in TableUpdate union (#15115) | ✅ 已完成 | [3205_f03595376](commits/3205_f03595376/analysis.md) |
| 3206 | `ccf4d91e1` | 2026-02-05 16:11:10 +0100 | Maximilian Michels | Flink: Dynamic Sink: Resolve effective write config at runtime (#15237) | ✅ 已完成 | [3206_ccf4d91e1](commits/3206_ccf4d91e1/analysis.md) |
| 3207 | `bfc52d218` | 2026-02-05 10:58:02 -0800 | hemanthboyina | Core: Fix compute_table_stats failures with concurrent writes (#15148) | ✅ 已完成 | [3207_bfc52d218](commits/3207_bfc52d218/analysis.md) |
| 3208 | `b35624e0d` | 2026-02-05 15:54:38 -0800 | Yufei Gu | Spec: Introduce SQL UDF specification (#14117) | ✅ 已完成 | [3208_b35624e0d](commits/3208_b35624e0d/analysis.md) |
| 3209 | `6d217a254` | 2026-02-05 23:50:25 -0800 | Manu Zhang | Spark 4.0: Upgrade to Spark 4.0.2 (#15218) | ✅ 已完成 | [3209_6d217a254](commits/3209_6d217a254/analysis.md) |
| 3210 | `b0c236512` | 2026-02-06 11:14:37 +0100 | Maximilian Michels | Flink: Backport: Dynamic Sink: Resolve effective write config at runtime (#15247) | ✅ 已完成 | [3210_b0c236512](commits/3210_b0c236512/analysis.md) |
| 3211 | `232af5737` | 2026-02-06 09:16:08 -0800 | Ayush Saxena | Core: Avro: Row Lineage Column (ROW_ID) is not populated correctly in case the Data File doesn't have them (#15187) | ✅ 已完成 | [3211_232af5737](commits/3211_232af5737/analysis.md) |
| 3212 | `250d74676` | 2026-02-06 19:39:30 +0100 | gaborkaszab | Core: Remove deprecation from ViewProperties.WRITE_METADATA_LOCATION (#15250) | ✅ 已完成 | [3212_250d74676](commits/3212_250d74676/analysis.md) |
| 3213 | `a8ece055b` | 2026-02-06 14:26:57 -0800 | pvary | Core, Data: File Format API interfaces (#12774) | ✅ 已完成 | [3213_a8ece055b](commits/3213_a8ece055b/analysis.md) |
| 3214 | `48da54897` | 2026-02-06 18:15:40 -0800 | Bryan Keller | AWS: Prefer custom credential provider if specified (#15249) | ✅ 已完成 | [3214_48da54897](commits/3214_48da54897/analysis.md) |
| 3215 | `31fc168c0` | 2026-02-06 18:16:48 -0800 | Anton Okolnychyi | Spark 4.1: Introduce constants for Spark metadata columns (#15245) | ✅ 已完成 | [3215_31fc168c0](commits/3215_31fc168c0/analysis.md) |
| 3216 | `91902a879` | 2026-02-07 09:05:06 -0800 | Manu Zhang | Build: Remove JDK17 target configuration for Spark 4.1 (#15256) | ✅ 已完成 | [3216_91902a879](commits/3216_91902a879/analysis.md) |
| 3217 | `990c7505e` | 2026-02-07 21:04:34 -0800 | dependabot[bot] | Build: Bump io.grpc:grpc-netty-shaded from 1.78.0 to 1.79.0 (#15262) | ✅ 已完成 | [3217_990c7505e](commits/3217_990c7505e/analysis.md) |
| 3218 | `662ff2288` | 2026-02-07 23:05:56 -0800 | dependabot[bot] | Build: Bump nessie from 0.107.0 to 0.107.1 (#15259) | ✅ 已完成 | [3218_662ff2288](commits/3218_662ff2288/analysis.md) |
| 3219 | `3b000aca9` | 2026-02-07 23:06:44 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.41.19 to 2.41.24 (#15261) | ✅ 已完成 | [3219_3b000aca9](commits/3219_3b000aca9/analysis.md) |
| 3220 | `8f7e123f7` | 2026-02-07 23:06:59 -0800 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#15264) | ✅ 已完成 | [3220_8f7e123f7](commits/3220_8f7e123f7/analysis.md) |
| 3221 | `730ce29d5` | 2026-02-07 23:07:59 -0800 | dependabot[bot] | Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#15263) | ✅ 已完成 | [3221_730ce29d5](commits/3221_730ce29d5/analysis.md) |
| 3222 | `af0e7568c` | 2026-02-08 11:19:55 -0800 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.9.Final to 4.2.10.Final (#15260) | ✅ 已完成 | [3222_af0e7568c](commits/3222_af0e7568c/analysis.md) |
| 3223 | `459332131` | 2026-02-09 02:44:42 -0800 | Alexandre Dutra | REST: add support for data access parameter to registerTable (#15231) | ✅ 已完成 | [3223_459332131](commits/3223_459332131/analysis.md) |
| 3224 | `865b60d47` | 2026-02-09 07:31:04 -0800 | Anton Okolnychyi | Core: Prevent exceptions in ExpressionUtil for unpartitioned tables (#15243) | ✅ 已完成 | [3224_865b60d47](commits/3224_865b60d47/analysis.md) |
| 3225 | `e6593a00f` | 2026-02-09 10:52:17 -0600 | Innocent Djiofack | Site: Document v3 types (#14888) | ✅ 已完成 | [3225_e6593a00f](commits/3225_e6593a00f/analysis.md) |
| 3226 | `ee97581bd` | 2026-02-09 09:39:36 -0800 | pvary | Core: FormatModelRegistry javadoc tweaks (#15257) | ✅ 已完成 | [3226_ee97581bd](commits/3226_ee97581bd/analysis.md) |
| 3227 | `854e3b2c8` | 2026-02-09 09:52:10 -0800 | Anton Okolnychyi | Core, Spark 4.1: Fix distributed planning for CoW operations (#15246) | ✅ 已完成 | [3227_854e3b2c8](commits/3227_854e3b2c8/analysis.md) |
| 3228 | `3aec20814` | 2026-02-09 09:53:55 -0800 | Prashant Singh | SPEC: Add AccessDelegation header to planAPI calls (#14781) | ✅ 已完成 | [3228_3aec20814](commits/3228_3aec20814/analysis.md) |
| 3229 | `d95d9f0ad` | 2026-02-09 10:07:04 -0800 | Alexandre Dutra | Core, REST: Add support for overwrite in RegisterTableRequest (#15248) | ✅ 已完成 | [3229_d95d9f0ad](commits/3229_d95d9f0ad/analysis.md) |
| 3230 | `00df4934a` | 2026-02-09 22:07:27 -0800 | Anton Okolnychyi | Core, Spark 4.1: Fix querying equality deletes with schema evolution (#15268) | ✅ 已完成 | [3230_00df4934a](commits/3230_00df4934a/analysis.md) |
| 3231 | `d23b0d39b` | 2026-02-10 11:22:18 +0100 | gaborkaszab | Core: Include query params into ETag calculation in reference IRC (#15057) | ✅ 已完成 | [3231_d23b0d39b](commits/3231_d23b0d39b/analysis.md) |
| 3232 | `c75efa9c3` | 2026-02-10 14:15:24 +0100 | Rui Li | Core: Fix metadata table scans with useRef by preserving metadata schema (#15276) | ✅ 已完成 | [3232_c75efa9c3](commits/3232_c75efa9c3/analysis.md) |
| 3233 | `48e49443f` | 2026-02-10 14:23:28 +0100 | Eduard Tudenhoefner | Core: Convert metrics to Content Stats (#15251) | ✅ 已完成 | [3233_48e49443f](commits/3233_48e49443f/analysis.md) |
| 3234 | `42abc6b3e` | 2026-02-10 15:14:06 +0100 | Sarthak Singh | Add DataLakeFileSystemClient constructor in ADLSFileIO (#14966) | ✅ 已完成 | [3234_42abc6b3e](commits/3234_42abc6b3e/analysis.md) |
| 3235 | `71b05af09` | 2026-02-10 07:06:09 -0800 | Anton Okolnychyi | Spark 4.1: Simplify description and toString in scans (#15281) | ✅ 已完成 | [3235_71b05af09](commits/3235_71b05af09/analysis.md) |
| 3236 | `f49b2fd97` | 2026-02-10 10:45:16 -0800 | Sebastian Baunsgaard | Core: Fix relativize() to handle path equal to prefix (#15173) | ✅ 已完成 | [3236_f49b2fd97](commits/3236_f49b2fd97/analysis.md) |
| 3237 | `473d46a5c` | 2026-02-10 22:30:18 -0800 | Yuya Ebihara | API: Implement properties method in EncryptingFileIO (#15289) | ✅ 已完成 | [3237_473d46a5c](commits/3237_473d46a5c/analysis.md) |
| 3238 | `e5532d6bd` | 2026-02-11 17:30:42 +0100 | Sunwoo Jung | Build: Remove unused jackson versions in libs.versions.toml (#15295) | ✅ 已完成 | [3238_e5532d6bd](commits/3238_e5532d6bd/analysis.md) |
| 3239 | `580e793ef` | 2026-02-11 17:50:59 +0100 | Eduard Tudenhoefner | Core, Spark: Rename RequiresRemoteScanPlanning to SupportsDistributedScanPlanning (#15184) | ✅ 已完成 | [3239_580e793ef](commits/3239_580e793ef/analysis.md) |
| 3240 | `533d2c9ac` | 2026-02-11 16:09:31 -0800 | geruh | Docs: add blog post for iceberg-python 0.11.0 release (#15290) | ✅ 已完成 | [3240_533d2c9ac](commits/3240_533d2c9ac/analysis.md) |
| 3241 | `444e381dc` | 2026-02-12 07:23:43 +0100 | Manu Zhang | OpenAPI: Remove specific table spec versions from description (#15277) | ✅ 已完成 | [3241_444e381dc](commits/3241_444e381dc/analysis.md) |
| 3242 | `0de62585c` | 2026-02-12 10:48:16 -0800 | Prashant Singh | SPEC: Add referenced-by in loadTable API (#13810) | ✅ 已完成 | [3242_0de62585c](commits/3242_0de62585c/analysis.md) |
| 3243 | `ed39cee30` | 2026-02-12 11:51:18 -0700 | Prashant Singh | API, Spark: Support StringLiteral to Fixed and StringLiteral to Binary Conversions  (#14882) | ✅ 已完成 | [3243_ed39cee30](commits/3243_ed39cee30/analysis.md) |
| 3244 | `3e3c17109` | 2026-02-13 13:35:17 +0100 | Raunaq Morarka | Core: Add org.apache.iceberg.DataFiles.Builder#withSortOrderId (#15217) | ✅ 已完成 | [3244_3e3c17109](commits/3244_3e3c17109/analysis.md) |
| 3245 | `6d565b222` | 2026-02-13 18:12:33 +0100 | pvary | Core, Data: Implementation of AvroFormatModel (#15254) | ✅ 已完成 | [3245_6d565b222](commits/3245_6d565b222/analysis.md) |
| 3246 | `e95739e34` | 2026-02-13 18:42:54 -0800 | MJY | docs: Fix uuid type formatting in schemas.md (#15309) | ✅ 已完成 | [3246_e95739e34](commits/3246_e95739e34/analysis.md) |
| 3247 | `b6de7acdb` | 2026-02-14 18:48:51 -0800 | Ruijing Li | Spark 4.1: Refactor SparkMicroBatchStream to SyncPlanner (#15298) | ✅ 已完成 | [3247_b6de7acdb](commits/3247_b6de7acdb/analysis.md) |
| 3248 | `16f87ef7d` | 2026-02-14 23:24:07 -0800 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.51.1.0 to 3.51.2.0 (#15320) | ✅ 已完成 | [3248_16f87ef7d](commits/3248_16f87ef7d/analysis.md) |
| 3249 | `55ff7625b` | 2026-02-14 23:24:39 -0800 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.75.0 to 26.76.0 (#15321) | ✅ 已完成 | [3249_55ff7625b](commits/3249_55ff7625b/analysis.md) |
| 3250 | `d4f3aba2e` | 2026-02-14 23:24:55 -0800 | dependabot[bot] | Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#15325) | ✅ 已完成 | [3250_d4f3aba2e](commits/3250_d4f3aba2e/analysis.md) |
| 3251 | `6bd0e6844` | 2026-02-14 23:25:17 -0800 | dependabot[bot] | Build: Bump com.gorylenko.gradle-git-properties:gradle-git-properties (#15322) | ✅ 已完成 | [3251_6bd0e6844](commits/3251_6bd0e6844/analysis.md) |
| 3252 | `113e71a31` | 2026-02-14 23:25:35 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.41.24 to 2.41.29 (#15324) | ✅ 已完成 | [3252_113e71a31](commits/3252_113e71a31/analysis.md) |
| 3253 | `235ab0f4f` | 2026-02-15 10:53:20 +0100 | pvary | Parquet, Data: Implementation of ParquetFormatModel (#15253) | ✅ 已完成 | [3253_235ab0f4f](commits/3253_235ab0f4f/analysis.md) |
| 3254 | `83fe4ffc5` | 2026-02-15 15:26:51 +0100 | pvary | Orc, Data: Implementation of ORCFormatModel (#15255) | ✅ 已完成 | [3254_83fe4ffc5](commits/3254_83fe4ffc5/analysis.md) |
| 3255 | `8c2ca1d08` | 2026-02-15 06:31:01 -0800 | Yuya Ebihara | Core: Add support for encryption.kms-type with aws/azure/gcp (#15272) | ✅ 已完成 | [3255_8c2ca1d08](commits/3255_8c2ca1d08/analysis.md) |
| 3256 | `cd6814e79` | 2026-02-15 11:29:26 -0800 | dependabot[bot] | Build: Bump nessie from 0.107.1 to 0.107.2 (#15323) | ✅ 已完成 | [3256_cd6814e79](commits/3256_cd6814e79/analysis.md) |
| 3257 | `323ab162f` | 2026-02-15 23:58:32 -0800 | Manu Zhang | Build: Bump datamodel-code-generator from 0.53.0 to 0.54.0 (#15331) | ✅ 已完成 | [3257_323ab162f](commits/3257_323ab162f/analysis.md) |
| 3258 | `b52f8f592` | 2026-02-16 06:05:24 -0800 | Kevin Liu | Remove redundant --watch . flag from serve scripts (#15330) | ✅ 已完成 | [3258_b52f8f592](commits/3258_b52f8f592/analysis.md) |
| 3259 | `57810dc9a` | 2026-02-16 07:25:34 -0800 | Junwang Zhao | Doc: Guidelines for AI-Generated Contributions (#15213) | ✅ 已完成 | [3259_57810dc9a](commits/3259_57810dc9a/analysis.md) |
| 3260 | `4c0db5abf` | 2026-02-16 07:38:44 -0800 | Manu Zhang | Build: Support building site with uv (#15118) | ✅ 已完成 | [3260_4c0db5abf](commits/3260_4c0db5abf/analysis.md) |
| 3261 | `c214f2ed5` | 2026-02-16 09:18:25 -0800 | Kevin Liu | infra: remove explicit GITHUB_TOKEN export from labeler workflow (#15335) | ✅ 已完成 | [3261_c214f2ed5](commits/3261_c214f2ed5/analysis.md) |
| 3262 | `eb8fe538f` | 2026-02-16 10:27:22 -0800 | Robin Moffatt | Add Flink Quickstart docker image (#15124) | ✅ 已完成 | [3262_eb8fe538f](commits/3262_eb8fe538f/analysis.md) |
| 3263 | `6b15becc9` | 2026-02-16 20:22:26 +0100 | pvary | Core, Arrow: Implementation of ArrowFormatModel (#15258) | ✅ 已完成 | [3263_6b15becc9](commits/3263_6b15becc9/analysis.md) |
| 3264 | `a2802c44c` | 2026-02-16 20:23:40 +0100 | pvary | Data, MR: Moving other reader usages to the new FormatModel API (#15333) | ✅ 已完成 | [3264_a2802c44c](commits/3264_a2802c44c/analysis.md) |
| 3265 | `ebaafdeb3` | 2026-02-16 15:07:10 -0800 | Kevin Liu | infra: set github actions max-parallel to 15 (#15339) | ✅ 已完成 | [3265_ebaafdeb3](commits/3265_ebaafdeb3/analysis.md) |
| 3266 | `4c2e60d15` | 2026-02-16 17:20:38 -0800 | Anton Okolnychyi | Spark 4.1: Simplify handling of metadata columns (#15297) | ✅ 已完成 | [3266_4c2e60d15](commits/3266_4c2e60d15/analysis.md) |
| 3267 | `9ce0e6e11` | 2026-02-16 17:21:46 -0800 | Anton Okolnychyi | Spark 4.1: Separate compaction and main operations (#15301) | ✅ 已完成 | [3267_9ce0e6e11](commits/3267_9ce0e6e11/analysis.md) |
| 3268 | `e9aa1c6c7` | 2026-02-17 07:01:47 +0100 | Yuya Ebihara | Build: Enable JavaUtilDate ErrorProne rule (#15346) | ✅ 已完成 | [3268_e9aa1c6c7](commits/3268_e9aa1c6c7/analysis.md) |
| 3269 | `fa7b5c84c` | 2026-02-17 07:02:37 +0100 | slfan1989 | Build: Bump lz4-java to 1.10.3 due to CVE-2025-12183 & CVE-2025-66566 (#14941) | ✅ 已完成 | [3269_fa7b5c84c](commits/3269_fa7b5c84c/analysis.md) |
| 3270 | `c7ed71e97` | 2026-02-16 22:35:36 -0800 | Anton Okolnychyi | Spark 4.1: Align handling of branches in reads and writes (#15288) | ✅ 已完成 | [3270_c7ed71e97](commits/3270_c7ed71e97/analysis.md) |
| 3271 | `bfcb97943` | 2026-02-17 12:40:17 +0100 | pvary | Data: Moving GenericFileWriterFactory to the new FormatModel API (#15334) | ✅ 已完成 | [3271_bfcb97943](commits/3271_bfcb97943/analysis.md) |
| 3272 | `68b7a2a71` | 2026-02-17 12:50:03 +0100 | pvary | Core, Data, Flink: Moving Flink to use the new FormatModel API (#15329) | ✅ 已完成 | [3272_68b7a2a71](commits/3272_68b7a2a71/analysis.md) |
| 3273 | `d06e6c22f` | 2026-02-17 11:33:10 -0800 | Robin Moffatt | Docs: Add documentation pointers in README files and fix typos in Spark quickstart (#15350) | ✅ 已完成 | [3273_d06e6c22f](commits/3273_d06e6c22f/analysis.md) |
| 3274 | `dde0dc233` | 2026-02-18 16:10:25 +0100 | pvary | Core, Spark: Moving Spark to use the new FormatModel API (#15328) | ✅ 已完成 | [3274_dde0dc233](commits/3274_dde0dc233/analysis.md) |
| 3275 | `ccdf23ca2` | 2026-02-18 16:55:00 +0100 | pvary | Flink: Backport moving Flink to use the new FormatModel API (#15354) | ✅ 已完成 | [3275_ccdf23ca2](commits/3275_ccdf23ca2/analysis.md) |
| 3276 | `0f1fa2b6a` | 2026-02-18 17:42:09 +0100 | pvary | Spark: Backport moving Spark to use the new FormatModel API (#15355) | ✅ 已完成 | [3276_0f1fa2b6a](commits/3276_0f1fa2b6a/analysis.md) |
| 3277 | `ad7251692` | 2026-02-18 18:20:58 +0100 | pvary | Spark: Various fixes for SparkFileWriterFactory (#15356) | ✅ 已完成 | [3277_ad7251692](commits/3277_ad7251692/analysis.md) |
| 3278 | `2f170322d` | 2026-02-18 19:02:32 +0100 | pvary | Spark: Backport various fixes for SparkFileWriterFactory (#15357) | ✅ 已完成 | [3278_2f170322d](commits/3278_2f170322d/analysis.md) |
| 3279 | `d35a1f995` | 2026-02-18 21:05:46 -0800 | Anton Okolnychyi | Spark 4.1: Add BaseSparkScanBuilder (#15360) | ✅ 已完成 | [3279_d35a1f995](commits/3279_d35a1f995/analysis.md) |
| 3280 | `d714576c7` | 2026-02-19 06:30:37 +0100 | Amogh Jahagirdar | Revert "Build: Bump roaringbitmap from 1.3.0 to 1.6.0 (#14991)" (#15358) | ✅ 已完成 | [3280_d714576c7](commits/3280_d714576c7/analysis.md) |
| 3281 | `eff136ba8` | 2026-02-18 22:53:59 -0800 | Anton Okolnychyi | Spark 4.1: Remove unnecessary stats reporting from scan builder (#15364) | ✅ 已完成 | [3281_eff136ba8](commits/3281_eff136ba8/analysis.md) |
| 3282 | `c51dda544` | 2026-02-18 22:54:34 -0800 | Anton Okolnychyi | Spark 4.1: Use enum conf parser for isolation level (#15361) | ✅ 已完成 | [3282_c51dda544](commits/3282_c51dda544/analysis.md) |
| 3283 | `3f6d3de04` | 2026-02-18 22:57:17 -0800 | Anton Okolnychyi | Spark 4.1: Use table IDs in scan equals/hashCode (#15363) | ✅ 已完成 | [3283_3f6d3de04](commits/3283_3f6d3de04/analysis.md) |
| 3284 | `881417433` | 2026-02-18 23:19:22 -0800 | Anton Okolnychyi | Spark 4.1: Use scan filter for conflict detection (#15365) | ✅ 已完成 | [3284_881417433](commits/3284_881417433/analysis.md) |
| 3285 | `de3125afe` | 2026-02-18 23:19:57 -0800 | Anton Okolnychyi | Spark 4.1: Fix IcebergSource doc (#15359) | ✅ 已完成 | [3285_de3125afe](commits/3285_de3125afe/analysis.md) |
| 3286 | `f7916f277` | 2026-02-19 16:31:09 +0100 | Robin Moffatt | Docker, Docs, Site: Add Flink quickstart (#15062) | ✅ 已完成 | [3286_f7916f277](commits/3286_f7916f277/analysis.md) |
| 3287 | `c31dd921e` | 2026-02-19 11:09:41 -0800 | Hongyue/Steve Zhang | Core: populate manifest created/replaced/kept count when commit a snapshot (#15003) | ✅ 已完成 | [3287_c31dd921e](commits/3287_c31dd921e/analysis.md) |
| 3288 | `176c72962` | 2026-02-19 19:12:47 -0800 | Anton Okolnychyi | Spark 4.1: Rename expectedSchema to projection for clarity (#15366) | ✅ 已完成 | [3288_176c72962](commits/3288_176c72962/analysis.md) |
| 3289 | `4ed7658bd` | 2026-02-19 22:33:25 -0800 | Anton Okolnychyi | Spark 4.1: Introduce modes in SparkWriteBuilder (#15374) | ✅ 已完成 | [3289_4ed7658bd](commits/3289_4ed7658bd/analysis.md) |
| 3290 | `cb0c9ccd4` | 2026-02-19 22:33:46 -0800 | Anton Okolnychyi | Spark 4.1: Simplify time travel option extraction in IcebergSource (#15375) | ✅ 已完成 | [3290_cb0c9ccd4](commits/3290_cb0c9ccd4/analysis.md) |
| 3291 | `455a82a69` | 2026-02-19 23:50:40 -0800 | Anton Okolnychyi | Spark 4.1: Refactor metadata column references to use asRef() method (#15376) | ✅ 已完成 | [3291_455a82a69](commits/3291_455a82a69/analysis.md) |
| 3292 | `cd50c9d5d` | 2026-02-20 08:57:33 -0800 | Moshe Blumberg | Docs: Fix typos in contribute.md (#15383) | ✅ 已完成 | [3292_cd50c9d5d](commits/3292_cd50c9d5d/analysis.md) |
| 3293 | `6a41168d1` | 2026-02-20 14:33:02 -0800 | Anton Okolnychyi | Core: Avoid exceptions for accessing schema for metadata tables in SnapshotUtil (#15387) | ✅ 已完成 | [3293_6a41168d1](commits/3293_6a41168d1/analysis.md) |
| 3294 | `f1499bde7` | 2026-02-21 08:28:10 +0100 | jbewing | Spark, Arrow, Parquet: Add vectorized parquet read support for `DELTA_LENGTH_BYTE_ARRAY` & `DELTA_BYTE_ARRAY` encodings (#15362) | ✅ 已完成 | [3294_f1499bde7](commits/3294_f1499bde7/analysis.md) |
| 3295 | `723fd4e6f` | 2026-02-21 23:55:37 -0800 | dependabot[bot] | Build: Bump actions/stale from 10.1.1 to 10.2.0 (#15398) | ✅ 已完成 | [3295_723fd4e6f](commits/3295_723fd4e6f/analysis.md) |
| 3296 | `96d8b57d0` | 2026-02-22 00:01:36 -0800 | dependabot[bot] | Build: Bump junit-platform from 1.14.2 to 1.14.3 (#15400) | ✅ 已完成 | [3296_96d8b57d0](commits/3296_96d8b57d0/analysis.md) |
| 3297 | `8e162e6a1` | 2026-02-22 00:01:49 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.41.29 to 2.41.34 (#15402) | ✅ 已完成 | [3297_8e162e6a1](commits/3297_8e162e6a1/analysis.md) |
| 3298 | `903f2e0b4` | 2026-02-22 00:02:23 -0800 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#15403) | ✅ 已完成 | [3298_903f2e0b4](commits/3298_903f2e0b4/analysis.md) |
| 3299 | `e74562d05` | 2026-02-22 00:02:37 -0800 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.3.0 to 1.6.10 (#15404) | ✅ 已完成 | [3299_e74562d05](commits/3299_e74562d05/analysis.md) |
| 3300 | `ed485f928` | 2026-02-22 10:45:11 -0800 | dependabot[bot] | Build: Bump junit from 5.14.2 to 5.14.3 (#15401) | ✅ 已完成 | [3300_ed485f928](commits/3300_ed485f928/analysis.md) |
| 3301 | `3dcacfe9e` | 2026-02-22 11:47:48 -0800 | Kevin Liu | CI: Add CodeQL workflow for GitHub Actions security scanning (#15348) | ✅ 已完成 | [3301_3dcacfe9e](commits/3301_3dcacfe9e/analysis.md) |
| 3302 | `68a347616` | 2026-02-23 08:22:29 +0100 | Manu Zhang | Spark 4.1: Display write metrics on SQL UI (#15104) | ✅ 已完成 | [3302_68a347616](commits/3302_68a347616/analysis.md) |
| 3303 | `86defcebe` | 2026-02-23 13:53:58 +0100 | Swapna Marru | Flink: SQL support for dynamic iceberg sink (#15279) | ✅ 已完成 | [3303_86defcebe](commits/3303_86defcebe/analysis.md) |
| 3304 | `f79932d11` | 2026-02-23 16:49:56 +0100 | gaborkaszab | Core: Add test for freshness-aware table loading with lazy snapshot loading (#15274) | ✅ 已完成 | [3304_f79932d11](commits/3304_f79932d11/analysis.md) |
| 3305 | `ad116d604` | 2026-02-23 10:28:33 -0800 | Anshul Baliga | Docs: Fix several nit issues in docs (#15419) | ✅ 已完成 | [3305_ad116d604](commits/3305_ad116d604/analysis.md) |
| 3306 | `f24e3e388` | 2026-02-23 10:36:29 -0800 | pvary | Docs: Add blog post about File Format API (#15380) | ✅ 已完成 | [3306_f24e3e388](commits/3306_f24e3e388/analysis.md) |
| 3307 | `a97b4ecc4` | 2026-02-23 13:50:23 -0800 | Daniel Weeks | Revert "Move deleted files to Hadoop trash if configured (#14501)" (#15386) | ✅ 已完成 | [3307_a97b4ecc4](commits/3307_a97b4ecc4/analysis.md) |
| 3308 | `39d5e1d59` | 2026-02-23 17:54:51 -0800 | Kevin Liu | chore(ci): add explicit least-privilege workflow permissions (#15409) | ✅ 已完成 | [3308_39d5e1d59](commits/3308_39d5e1d59/analysis.md) |
| 3309 | `501824f0c` | 2026-02-23 18:18:39 -0800 | Manu Zhang | Build: Exclude roaringbitmap dependency from Spark and update LICENSE files (#15405) | ✅ 已完成 | [3309_501824f0c](commits/3309_501824f0c/analysis.md) |
| 3310 | `9534c9b3a` | 2026-02-24 16:03:42 +0100 | GuoYu | Flink: TableMaintenance Support Coordinator Lock (#15151) | ✅ 已完成 | [3310_9534c9b3a](commits/3310_9534c9b3a/analysis.md) |
| 3311 | `791533888` | 2026-02-25 09:16:50 +0100 | feefs | OpenAPI: Add SetPartitionStatisticsUpdate/RemovePartitionStatisticsUpdate to TableUpdate (#14957) | ✅ 已完成 | [3311_791533888](commits/3311_791533888/analysis.md) |
| 3312 | `d2fbe427e` | 2026-02-25 10:26:48 +0100 | GuoYu | Flink: Backport TableMaintenance Support Coordinator Lock to 1.20 and 2.0 (#15438) | ✅ 已完成 | [3312_d2fbe427e](commits/3312_d2fbe427e/analysis.md) |
| 3313 | `08a4a0cde` | 2026-02-25 17:19:57 +0100 | jbewing | Spark, Arrow, Parquet: Add vectorized read support for parquet BYTE_STREAM_SPLIT encoding (#15373) | ✅ 已完成 | [3313_08a4a0cde](commits/3313_08a4a0cde/analysis.md) |
| 3314 | `17891bc4e` | 2026-02-25 11:09:20 -0800 | Eduard Tudenhoefner | API, Core: Align offsets of field stats with Design doc / Spec (#15432) | ✅ 已完成 | [3314_17891bc4e](commits/3314_17891bc4e/analysis.md) |
| 3315 | `d7aa46724` | 2026-02-25 14:22:36 -0800 | yguy-ryft | Docs: Add informational properties section for table comment (#15367) | ✅ 已完成 | [3315_d7aa46724](commits/3315_d7aa46724/analysis.md) |
| 3316 | `7d79fa18b` | 2026-02-26 08:10:52 +0100 | slfan1989 | Build: Bump hadoop from 3.4.2 to 3.4.3. (#15431) | ✅ 已完成 | [3316_7d79fa18b](commits/3316_7d79fa18b/analysis.md) |
| 3317 | `4543e03b4` | 2026-02-26 11:44:45 +0100 | GuoYu | Flink: Support Variant to Flink 2.1 (#15265) | ✅ 已完成 | [3317_4543e03b4](commits/3317_4543e03b4/analysis.md) |
| 3318 | `b7a519a14` | 2026-02-26 09:42:45 -0600 | Han You | JDBC: JDBC Catalog should handle Postgres exception for duplicate keys (#15434) | ✅ 已完成 | [3318_b7a519a14](commits/3318_b7a519a14/analysis.md) |
| 3319 | `2e94af5d1` | 2026-02-26 12:02:10 -0600 | Xiang Li | API: Use correct method name in exception message (#15453) | ✅ 已完成 | [3319_2e94af5d1](commits/3319_2e94af5d1/analysis.md) |
| 3320 | `017e81300` | 2026-02-26 12:08:32 -0800 | Szehon Ho | [Docs] Add Spark MERGE INTO fields in snapshot summary (#15390) | ✅ 已完成 | [3320_017e81300](commits/3320_017e81300/analysis.md) |
| 3321 | `10de21410` | 2026-02-27 10:26:25 +0100 | Swapna Marru | Flink: Backport SQL support for dynamic iceberg sink (#15444) | ✅ 已完成 | [3321_10de21410](commits/3321_10de21410/analysis.md) |
| 3322 | `86e9ebbff` | 2026-02-27 11:43:53 -0600 | Hongyue/Steve Zhang | Core: Support parallel execution when scanning entries in ManifestGroup (#15426) | ✅ 已完成 | [3322_86e9ebbff](commits/3322_86e9ebbff/analysis.md) |
| 3323 | `1a20b546c` | 2026-02-27 10:31:00 -0800 | Eduard Tudenhoefner | Core: Add properties to InMemoryFileIO (#15469) | ✅ 已完成 | [3323_1a20b546c](commits/3323_1a20b546c/analysis.md) |
| 3324 | `de4101118` | 2026-02-28 18:52:32 -0700 | Amogh Jahagirdar | Core: Detect and merge duplicate DVs for a data file and merge them before committing (#15006) | ✅ 已完成 | [3324_de4101118](commits/3324_de4101118/analysis.md) |
| 3325 | `e6af9db74` | 2026-02-28 21:08:19 -0800 | dependabot[bot] | Build: Bump openapi-spec-validator from 0.7.2 to 0.8.3 (#15480) | ✅ 已完成 | [3325_e6af9db74](commits/3325_e6af9db74/analysis.md) |
| 3326 | `dab3f9d8f` | 2026-02-28 22:08:15 -0800 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.10 to 1.6.12 (#15486) | ✅ 已完成 | [3326_dab3f9d8f](commits/3326_dab3f9d8f/analysis.md) |
| 3327 | `6f99c962f` | 2026-02-28 22:08:29 -0800 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.41.34 to 2.42.4 (#15485) | ✅ 已完成 | [3327_6f99c962f](commits/3327_6f99c962f/analysis.md) |
| 3328 | `73c52d18b` | 2026-02-28 22:08:46 -0800 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#15484) | ✅ 已完成 | [3328_73c52d18b](commits/3328_73c52d18b/analysis.md) |
| 3329 | `a6573b0bd` | 2026-02-28 22:09:08 -0800 | dependabot[bot] | Build: Bump com.gradleup.shadow:shadow-gradle-plugin (#15483) | ✅ 已完成 | [3329_a6573b0bd](commits/3329_a6573b0bd/analysis.md) |
| 3330 | `e7a021c9c` | 2026-02-28 22:09:24 -0800 | dependabot[bot] | Build: Bump jackson-bom from 2.21.0 to 2.21.1 (#15482) | ✅ 已完成 | [3330_e7a021c9c](commits/3330_e7a021c9c/analysis.md) |
| 3331 | `9a53e2eda` | 2026-02-28 22:09:34 -0800 | dependabot[bot] | Build: Bump nessie from 0.107.2 to 0.107.3 (#15481) | ✅ 已完成 | [3331_9a53e2eda](commits/3331_9a53e2eda/analysis.md) |
| 3332 | `e79b037b3` | 2026-02-28 22:09:46 -0800 | dependabot[bot] | Build: Bump actions/upload-artifact from 6 to 7 (#15479) | ✅ 已完成 | [3332_e79b037b3](commits/3332_e79b037b3/analysis.md) |
| 3333 | `ba79d8d8c` | 2026-02-28 22:09:56 -0800 | dependabot[bot] | Build: Bump kafka from 3.9.1 to 3.9.2 (#15478) | ✅ 已完成 | [3333_ba79d8d8c](commits/3333_ba79d8d8c/analysis.md) |
| 3334 | `dc4c7840f` | 2026-02-28 22:10:09 -0800 | dependabot[bot] | Build: Bump actions/checkout from 4 to 6 (#15477) | ✅ 已完成 | [3334_dc4c7840f](commits/3334_dc4c7840f/analysis.md) |
| 3335 | `58b3e3673` | 2026-03-02 11:27:59 +0100 | Manu Zhang | Spark 4.0: Display write metrics on SQL UI (#15468) | ✅ 已完成 | [3335_58b3e3673](commits/3335_58b3e3673/analysis.md) |
| 3336 | `6534be116` | 2026-03-02 15:41:02 +0100 | Junwang Zhao | Build: Use ubuntu-slim for lightweight jobs (#15457) | ✅ 已完成 | [3336_6534be116](commits/3336_6534be116/analysis.md) |
| 3337 | `57db6815b` | 2026-03-02 12:23:46 -0600 | Russell Spitzer | Core, Spark: Adds a Table Property for Relying on Identifier Fields (#15372) | ✅ 已完成 | [3337_57db6815b](commits/3337_57db6815b/analysis.md) |
| 3338 | `6b2e80453` | 2026-03-02 19:30:33 +0100 | Ayson Chang | Core: Don't fail when bulk deleting metadata in CatalogUtil (#15464) | ✅ 已完成 | [3338_6b2e80453](commits/3338_6b2e80453/analysis.md) |
| 3339 | `cf25bed6f` | 2026-03-02 11:36:58 -0800 | Steven Zhen Wu | Docs: Add UDF spec to website navigation (#15491) | ✅ 已完成 | [3339_cf25bed6f](commits/3339_cf25bed6f/analysis.md) |
| 3340 | `64e0211e4` | 2026-03-02 15:21:33 -0600 | Russell Spitzer | Core, Spark: Rename read.identifier-fields.rely to identifier-fields.rely (#15495) | ✅ 已完成 | [3340_64e0211e4](commits/3340_64e0211e4/analysis.md) |
| 3341 | `39ed7e445` | 2026-03-03 09:50:17 +0100 | Eduard Tudenhoefner | Core: Track & close FileIO used for remote scan planning (#15439) | ✅ 已完成 | [3341_39ed7e445](commits/3341_39ed7e445/analysis.md) |
| 3342 | `88d460425` | 2026-03-03 09:37:26 -0800 | Hongyue/Steve Zhang | Build, Kafka-Connect: Disable publishing for empty grouping project (#15496) | ✅ 已完成 | [3342_88d460425](commits/3342_88d460425/analysis.md) |
| 3343 | `7064b0979` | 2026-03-04 11:11:12 +0100 | Yuya Ebihara | Docs: Improve json readability in view spec (#15505) | ✅ 已完成 | [3343_7064b0979](commits/3343_7064b0979/analysis.md) |
| 3344 | `6299a2897` | 2026-03-04 14:08:07 -0800 | Ramesh Reddy Adutla | docs: Add CREATE DATABASE step before CREATE TABLE in Spark quickstart (#15513) | ✅ 已完成 | [3344_6299a2897](commits/3344_6299a2897/analysis.md) |
| 3345 | `ed8a16bbe` | 2026-03-04 15:38:12 -0800 | Kevin Liu | docs: udf spec, add newline to properly render lists (#15516) | ✅ 已完成 | [3345_ed8a16bbe](commits/3345_ed8a16bbe/analysis.md) |
| 3346 | `48518ee31` | 2026-03-06 10:31:19 -0800 | Eduard Tudenhoefner | OpenAPI: Include storage credentials for PlanTableScanResponse/FetchPlanningResultResponse on plan completed given include-credentials flag is set (#15524) | ✅ 已完成 | [3346_48518ee31](commits/3346_48518ee31/analysis.md) |
| 3347 | `99436e16c` | 2026-03-06 10:48:06 -0800 | Cheng Pan | Build: Bump lz4-java 1.10.4 (#15518) | ✅ 已完成 | [3347_99436e16c](commits/3347_99436e16c/analysis.md) |
| 3348 | `175e6c241` | 2026-03-06 10:49:25 -0800 | Kevin Liu | infra: restore github.del_branch_on_merge in .asf.yaml (#15517) | ✅ 已完成 | [3348_175e6c241](commits/3348_175e6c241/analysis.md) |
| 3349 | `30232d3e7` | 2026-03-06 20:47:16 +0100 | Anton Okolnychyi | Spark 4.1: Migrate to new version framework in DSv2 (#15240) | ✅ 已完成 | [3349_30232d3e7](commits/3349_30232d3e7/analysis.md) |
| 3350 | `a7f965b94` | 2026-03-07 23:14:45 -0800 | dependabot[bot] | Build: Bump docker/build-push-action from 6 to 7 (#15532) | ✅ 已完成 | [3350_a7f965b94](commits/3350_a7f965b94/analysis.md) |
| 3351 | `0e764073d` | 2026-03-07 23:15:19 -0800 | dependabot[bot] | Build: Bump docker/setup-buildx-action from 3 to 4 (#15533) | ✅ 已完成 | [3351_0e764073d](commits/3351_0e764073d/analysis.md) |
| 3352 | `aaf24f834` | 2026-03-07 23:15:48 -0800 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.54.0 to 0.54.1 (#15535) | ✅ 已完成 | [3352_aaf24f834](commits/3352_aaf24f834/analysis.md) |
| 3353 | `663bbf5af` | 2026-03-07 23:16:07 -0800 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.3.4 to 1.3.5 (#15539) | ✅ 已完成 | [3353_663bbf5af](commits/3353_663bbf5af/analysis.md) |
| 3354 | `26dc1ac1b` | 2026-03-07 23:16:22 -0800 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.76.0 to 26.77.0 (#15538) | ✅ 已完成 | [3354_26dc1ac1b](commits/3354_26dc1ac1b/analysis.md) |
| 3355 | `59ce6126a` | 2026-03-07 23:16:59 -0800 | dependabot[bot] | Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#15537) | ✅ 已完成 | [3355_59ce6126a](commits/3355_59ce6126a/analysis.md) |
| 3356 | `afdcf73b0` | 2026-03-07 23:52:09 -0800 | dependabot[bot] | Build: Bump openapi-spec-validator from 0.8.3 to 0.8.4 (#15536) | ✅ 已完成 | [3356_afdcf73b0](commits/3356_afdcf73b0/analysis.md) |
| 3357 | `80c29134c` | 2026-03-08 08:47:37 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.42.4 to 2.42.8 (#15540) | ✅ 已完成 | [3357_80c29134c](commits/3357_80c29134c/analysis.md) |
| 3358 | `e94775075` | 2026-03-08 08:47:51 -0700 | dependabot[bot] | Build: Bump docker/setup-qemu-action from 3 to 4 (#15534) | ✅ 已完成 | [3358_e94775075](commits/3358_e94775075/analysis.md) |
| 3359 | `9f58ec6c7` | 2026-03-08 14:01:34 -0700 | Anshul Baliga | Spark: Fix javadoc mentioning Spark 3.3 in Spark 3.4 benchmark classes (#15544) | ✅ 已完成 | [3359_9f58ec6c7](commits/3359_9f58ec6c7/analysis.md) |
| 3360 | `d1bf56cc4` | 2026-03-09 09:25:51 +0100 | Eduard Tudenhoefner | Core, Spark: Unify bulk deletion (#15501) | ✅ 已完成 | [3360_d1bf56cc4](commits/3360_d1bf56cc4/analysis.md) |
| 3361 | `32f5ee6c2` | 2026-03-09 09:28:49 +0100 | Manu Zhang | Core: Deprecate unused methods in TableScanUtil (#15543) | ✅ 已完成 | [3361_32f5ee6c2](commits/3361_32f5ee6c2/analysis.md) |
| 3362 | `b310cba27` | 2026-03-09 12:43:28 +0100 | GuoYu | Flink: Add the possibility to use Coordinator Lock when using Flink SQL (#15459) | ✅ 已完成 | [3362_b310cba27](commits/3362_b310cba27/analysis.md) |
| 3363 | `4750cdcd2` | 2026-03-09 10:05:49 -0500 | Russell Spitzer | API, CORE, Flink, Spark: Deprecate Snapshot.changes Methods with SnapshotChange Utility (#15241) | ✅ 已完成 | [3363_4750cdcd2](commits/3363_4750cdcd2/analysis.md) |
| 3364 | `78f7c3dd5` | 2026-03-09 10:05:56 -0500 | Russell Spitzer | API, CORE, Flink, Spark: Deprecate Snapshot.changes Methods with SnapshotChange Utility (#15241) | ✅ 已完成 | [3364_78f7c3dd5](commits/3364_78f7c3dd5/analysis.md) |
| 3365 | `9b7f67d78` | 2026-03-09 16:54:25 +0100 | Maximilian Michels | Spark: Add ExecutorService support to RewriteTablePath (#15381) | ✅ 已完成 | [3365_9b7f67d78](commits/3365_9b7f67d78/analysis.md) |
| 3366 | `dd248a9ac` | 2026-03-09 11:07:37 -0500 | hemanthboyina | Spark: Add sort_by parameter to rewrite_manifests procedure (#15467) | ✅ 已完成 | [3366_dd248a9ac](commits/3366_dd248a9ac/analysis.md) |
| 3367 | `afb58aa34` | 2026-03-09 17:48:55 +0100 | GuoYu | Flink: Backport Add the possibility to use Coordinator Lock when using Flink SQL to 2.0 and 1.20 (#15562) | ✅ 已完成 | [3367_afb58aa34](commits/3367_afb58aa34/analysis.md) |
| 3368 | `1ead9996c` | 2026-03-09 12:36:05 -0700 | Kevin Liu | docs: reformat mailing list (#15527) | ✅ 已完成 | [3368_1ead9996c](commits/3368_1ead9996c/analysis.md) |
| 3369 | `c56757638` | 2026-03-09 19:55:51 -0500 | c2zwdjnlcg | Spark 4.1: Don't Use table FileIO for Spark Checkpoints(#15239) | ✅ 已完成 | [3369_c56757638](commits/3369_c56757638/analysis.md) |
| 3370 | `049d1fe52` | 2026-03-10 12:53:12 +0100 | Maximilian Michels | Spark: Backport: Add ExecutorService support to RewriteTablePath (#15578) | ✅ 已完成 | [3370_049d1fe52](commits/3370_049d1fe52/analysis.md) |
| 3371 | `1d6c685c1` | 2026-03-10 09:00:11 -0700 | Xinyi Lu |  Core: Make sequence number conflicts retryable when there are concurrent commits (#15126) | ✅ 已完成 | [3371_1d6c685c1](commits/3371_1d6c685c1/analysis.md) |
| 3372 | `ba5e54669` | 2026-03-10 10:22:10 -0700 | Steven Zhen Wu | Core, Spark: add the comment table property key (#15531) | ✅ 已完成 | [3372_ba5e54669](commits/3372_ba5e54669/analysis.md) |
| 3373 | `42d01511c` | 2026-03-10 12:40:10 -0500 | c2zwdjnlcg | Spark 3.4, 3.5, 4.0: Don't Use table FileIO for Spark Checkpoints (#15574) | ✅ 已完成 | [3373_42d01511c](commits/3373_42d01511c/analysis.md) |
| 3374 | `4b4eb38cf` | 2026-03-11 09:40:10 +0100 | Eduard Tudenhoefner | Core: Rename tableIo to tableIO (#15582) | ✅ 已完成 | [3374_4b4eb38cf](commits/3374_4b4eb38cf/analysis.md) |
| 3375 | `ace2d0375` | 2026-03-11 15:54:20 -0500 | Russell Spitzer | Core, Flink, Spark: Deprecate Manifest read methods which rely manifest Metadata (#15575) | ✅ 已完成 | [3375_ace2d0375](commits/3375_ace2d0375/analysis.md) |
| 3376 | `f865bac7c` | 2026-03-12 09:02:44 -0700 | Russell Spitzer | Core, Spark: Fix equality deletes non-deterministic schema ordering (#13873) | ✅ 已完成 | [3376_f865bac7c](commits/3376_f865bac7c/analysis.md) |
| 3377 | `fb2c8ac3f` | 2026-03-12 15:05:21 -0500 | JB Onofré | chore: several fixes on the LICENSE/NOTICE (#15449) | ✅ 已完成 | [3377_fb2c8ac3f](commits/3377_fb2c8ac3f/analysis.md) |
| 3378 | `bd54026d7` | 2026-03-12 13:54:33 -0700 | Noritaka Sekiyama | Spark: Delegate temp file deletion to JUnit in TestParquetVectorizedReads (#15557) | ✅ 已完成 | [3378_bd54026d7](commits/3378_bd54026d7/analysis.md) |
| 3379 | `cf6f83550` | 2026-03-13 06:42:50 +0100 | Eduard Tudenhoefner | API, Core: Add FileIO to Scan API (#15561) | ✅ 已完成 | [3379_cf6f83550](commits/3379_cf6f83550/analysis.md) |
| 3380 | `492953233` | 2026-03-13 08:45:18 +0100 | Bhargav Kumar Konidena | Spark 4.0: Fix "AlreadyExistsException: Location already exists" at rewrite_table_path (#14859) | ✅ 已完成 | [3380_492953233](commits/3380_492953233/analysis.md) |
| 3381 | `b4eddbc15` | 2026-03-13 11:58:41 +0100 | Joy Haldar | Data, Spark, Flink: Add TCK for File Format API (#15441) | ✅ 已完成 | [3381_b4eddbc15](commits/3381_b4eddbc15/analysis.md) |
| 3382 | `2cb3cc6d5` | 2026-03-13 13:47:00 +0100 | JB Onofré | Flink CI: Define timeout to avoid CI jobs running indefinitely (#15617) | ✅ 已完成 | [3382_2cb3cc6d5](commits/3382_2cb3cc6d5/analysis.md) |
| 3383 | `9528f85fb` | 2026-03-13 14:08:55 +0100 | Joy Haldar | Spark, Flink: Backport add TCK for File Format API (#15619) | ✅ 已完成 | [3383_9528f85fb](commits/3383_9528f85fb/analysis.md) |
| 3384 | `ba403009d` | 2026-03-13 15:40:14 +0100 | Eduard Tudenhoefner | Core: Move Hadoop conf serialization into SerializableConfiguration (#15583) | ✅ 已完成 | [3384_ba403009d](commits/3384_ba403009d/analysis.md) |
| 3385 | `6e64fb5ba` | 2026-03-13 09:24:15 -0700 | Prashant Singh | Spec: Add scan-planning-mode to LoadTableResult config documentation (#14867) | ✅ 已完成 | [3385_6e64fb5ba](commits/3385_6e64fb5ba/analysis.md) |
| 3386 | `c01c62f98` | 2026-03-13 10:33:06 -0700 | Eduard Tudenhoefner | Infra: Remove legacy github.del_branch_on_merge flag (#15620) | ✅ 已完成 | [3386_c01c62f98](commits/3386_c01c62f98/analysis.md) |
| 3387 | `3452153ed` | 2026-03-13 12:24:23 -0700 | Prashant Singh | Core: Replace Failsafe with Tasks utility in RESTTableScan (#15613) | ✅ 已完成 | [3387_3452153ed](commits/3387_3452153ed/analysis.md) |
| 3388 | `a50c2d920` | 2026-03-13 15:53:03 -0700 | Prashant Singh | Core: Rename MAX_ATTEMPTS to MAX_RETRIES in RESTTableScan (#15627) | ✅ 已完成 | [3388_a50c2d920](commits/3388_a50c2d920/analysis.md) |
| 3389 | `8bca96e65` | 2026-03-14 23:42:27 -0700 | dependabot[bot] | Build: Bump nessie from 0.107.3 to 0.107.4 (#15636) | ✅ 已完成 | [3389_8bca96e65](commits/3389_8bca96e65/analysis.md) |
| 3390 | `09c50f0cf` | 2026-03-14 23:42:48 -0700 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.77.0 to 26.78.0 (#15637) | ✅ 已完成 | [3390_09c50f0cf](commits/3390_09c50f0cf/analysis.md) |
| 3391 | `11dbe2f09` | 2026-03-14 23:43:01 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.42.8 to 2.42.13 (#15638) | ✅ 已完成 | [3391_11dbe2f09](commits/3391_11dbe2f09/analysis.md) |
| 3392 | `3af95aab2` | 2026-03-15 21:47:22 -0700 | Manu Zhang | Build: Bump datamodel-code-generator from 0.54.1 to 0.55.0 (#15641) | ✅ 已完成 | [3392_3af95aab2](commits/3392_3af95aab2/analysis.md) |
| 3393 | `aa5894a13` | 2026-03-16 12:23:32 +0100 | Eduard Tudenhoefner | Core: Load snapshot after it has been committed to prevent accidental cleanup of files (#15511) | ✅ 已完成 | [3393_aa5894a13](commits/3393_aa5894a13/analysis.md) |
| 3394 | `17bdb32cf` | 2026-03-16 12:40:21 +0100 | Manu Zhang | Build: Bump mkdocs-material from 9.6.23 to 9.7.5 (#15643) | ✅ 已完成 | [3394_17bdb32cf](commits/3394_17bdb32cf/analysis.md) |
| 3395 | `063c463fe` | 2026-03-16 12:46:06 +0100 | Denys Kuzmenko | Core: Rename cleanUpOnCommitFailure to cleanUp and make it protected (#15389) | ✅ 已完成 | [3395_063c463fe](commits/3395_063c463fe/analysis.md) |
| 3396 | `bb8b7434b4` | 2026-03-16 13:13:25 +0100 | Yuya Ebihara | API: Fix javadoc of ManageSnapshots.setMaxRefAgeMs (#15642) | ✅ 已完成 | [3396_bb8b7434b4](commits/3396_bb8b7434b4/analysis.md) |
| 3397 | `8b7f6d773f` | 2026-03-16 07:54:58 -0700 | Ruijing Li | Spark 4.1: New Async Spark Micro Batch Planner (#15299) | ✅ 已完成 | [3397_8b7f6d773f](commits/3397_8b7f6d773f/analysis.md) |
| 3398 | `49a9f9946a` | 2026-03-16 19:03:00 +0100 | Ayush Saxena | Avro: Reading files using DataFileStream with ROW LINEAGE if the column isn't projected (#15508) | ✅ 已完成 | [3398_49a9f9946a](commits/3398_49a9f9946a/analysis.md) |
| 3399 | `f5bbb9256c` | 2026-03-16 17:08:33 -0700 | Yuya Ebihara | Spark: Deprecate constructor with branch in SparkReadConf/SparkWriteConf (#15591) | ✅ 已完成 | [3399_f5bbb9256c](commits/3399_f5bbb9256c/analysis.md) |
| 3400 | `bb37293484` | 2026-03-16 17:12:26 -0700 | ZIHAN DAI | Fix JDBC resource leaks in JdbcCatalog and JdbcUtil (#15463) | ✅ 已完成 | [3400_bb37293484](commits/3400_bb37293484/analysis.md) |
| 3401 | `d9749048e3` | 2026-03-16 17:19:40 -0700 | Vrishabh | Spark: Fix aggregate pushdown (#15070) | ✅ 已完成 | [3401_d9749048e3](commits/3401_d9749048e3/analysis.md) |
| 3402 | `e822643ef0` | 2026-03-16 17:23:53 -0700 | wobu | Core: Fix rewriting delete manifests in RewriteTablePathUtil (#15155) | ✅ 已完成 | [3402_e822643ef0](commits/3402_e822643ef0/analysis.md) |
| 3403 | `b99e12adf7` | 2026-03-16 18:24:54 -0700 | Kevin Liu | site: add back privacy plugin (#15657) | ✅ 已完成 | [3403_b99e12adf7](commits/3403_b99e12adf7/analysis.md) |
| 3404 | `06f9a1d1d3` | 2026-03-16 20:17:53 -0700 | hemanthboyina | Core: Fix BinPackRewriteFilePlanner producing incorrect output file count with max-files-to-rewrite (#15576) | ✅ 已完成 | [3404_06f9a1d1d3](commits/3404_06f9a1d1d3/analysis.md) |
| 3405 | `eb460a524f` | 2026-03-16 20:34:12 -0700 | Anshul Baliga | Docs: Add HadoopTables lock configuration to Hadoop configuration section (#15520) | ✅ 已完成 | [3405_eb460a524f](commits/3405_eb460a524f/analysis.md) |
| 3406 | `22c788afb9` | 2026-03-17 13:21:56 +0100 | Maximilian Michels | Flink: Allow arbitrary post-commit maintenance tasks via IcebergSink Builder (#15566) | ✅ 已完成 | [3406_22c788afb9](commits/3406_22c788afb9/analysis.md) |
| 3407 | `f88180a260` | 2026-03-17 07:19:29 -0700 | Russell Spitzer | Add AGENTS.md with project conventions for AI coding agents (#15529) | ✅ 已完成 | [3407_f88180a260](commits/3407_f88180a260/analysis.md) |
| 3408 | `c9f6c8423b` | 2026-03-17 16:57:45 +0100 | Eduard Tudenhoefner | Core: Sent minRowsRequested to REST server (#15661) | ✅ 已完成 | [3408_c9f6c8423b](commits/3408_c9f6c8423b/analysis.md) |
| 3409 | `652691c2ab` | 2026-03-17 10:31:34 -0700 | Maximilian Michels | Flink: Backport: Allow arbitrary post-commit maintenance tasks via IcebergSink Builder (#15566) (#15667) | ✅ 已完成 | [3409_652691c2ab](commits/3409_652691c2ab/analysis.md) |
| 3410 | `67ed9d1165` | 2026-03-17 16:58:37 -0700 | Prashant Singh | CORE: Allow table level override for scan planning (#15572) | ✅ 已完成 | [3410_67ed9d1165](commits/3410_67ed9d1165/analysis.md) |
| 3411 | `0400f5dead` | 2026-03-17 21:45:46 -0500 | Rui Li | Spark: Explicitly disallow migrating bucketed tables (#15429) | ✅ 已完成 | [3411_0400f5dead](commits/3411_0400f5dead/analysis.md) |
| 3412 | `e0fe281360` | 2026-03-18 00:22:54 -0700 | Noritaka Sekiyama | Core: fix propertiesWithPrefix to strip prefix literally, not as regex (#15558) | ✅ 已完成 | [3412_e0fe281360](commits/3412_e0fe281360/analysis.md) |
| 3413 | `69caca0ecf` | 2026-03-18 09:38:02 +0100 | Eduard Tudenhoefner | API, Core: Use Supplier for FileIO on Scan (#15646) | ✅ 已完成 | [3413_69caca0ecf](commits/3413_69caca0ecf/analysis.md) |
| 3414 | `0d3544062e` | 2026-03-18 12:06:36 -0700 | Andy Grove | Spark: Remove Apache DataFusion Comet integration (#15674) | ✅ 已完成 | [3414_0d3544062e](commits/3414_0d3544062e/analysis.md) |
| 3415 | `be25b80f7f` | 2026-03-19 15:18:48 +0100 | Eduard Tudenhoefner | Spark 4.1: Pass FileIO on Spark's read path (#15448) | ✅ 已完成 | [3415_be25b80f7f](commits/3415_be25b80f7f/analysis.md) |
| 3416 | `66c087f241` | 2026-03-19 15:19:05 +0100 | Eduard Tudenhoefner | Spark 4.0: Pass FileIO on Spark's read path (#15682) | ✅ 已完成 | [3416_66c087f241](commits/3416_66c087f241/analysis.md) |
| 3417 | `0c46639e0e` | 2026-03-19 15:19:18 +0100 | Eduard Tudenhoefner | Spark 3.5: Pass FileIO on Spark's read path (#15683) | ✅ 已完成 | [3417_0c46639e0e](commits/3417_0c46639e0e/analysis.md) |
| 3418 | `367a0ba3fc` | 2026-03-19 16:46:51 +0100 | Maximilian Michels | Flink: Add branch support to RewriteDataFiles maintenance task (#15672) | ✅ 已完成 | [3418_367a0ba3fc](commits/3418_367a0ba3fc/analysis.md) |
| 3419 | `0a73da119f` | 2026-03-19 14:56:21 -0700 | Prashant Singh | Core: Fix useSnapshotSchema logic and projection in RESTTableScan (#15609) | ✅ 已完成 | [3419_0a73da119f](commits/3419_0a73da119f/analysis.md) |
| 3420 | `386f0ea6ab` | 2026-03-19 15:49:42 -0700 | Maximilian Michels | Flink: Backport: Add branch support to RewriteDataFiles maintenance task (#15672) (#15690) | ✅ 已完成 | [3420_386f0ea6ab](commits/3420_386f0ea6ab/analysis.md) |
| 3421 | `1009ef41b2` | 2026-03-19 16:50:42 -0700 | Daniel Weeks | AWS: Add scheduled refresh for the S3FileIO held storage credentials (#15678) | ✅ 已完成 | [3421_1009ef41b2](commits/3421_1009ef41b2/analysis.md) |
| 3422 | `9057bf32e9` | 2026-03-20 10:34:02 +0100 | Bhargav Kumar Konidena | Spark 3.4, 3.5, 4.1: Fix "AlreadyExistsException: Location already exists" at rewrite_table_path (#15616) | ✅ 已完成 | [3422_9057bf32e9](commits/3422_9057bf32e9/analysis.md) |
| 3423 | `a16baffcd3` | 2026-03-20 04:28:05 -0700 | jbewing | Spark: Add named constant for `NO_ADVISORY_PARTITION_SIZE` (#15681) | ✅ 已完成 | [3423_a16baffcd3](commits/3423_a16baffcd3/analysis.md) |
| 3424 | `2874fc46e8` | 2026-03-20 12:49:20 +0100 | Joy Haldar | Data, Spark, Flink: Add null engineSchema fallback for format model writers (#15688) | ✅ 已完成 | [3424_2874fc46e8](commits/3424_2874fc46e8/analysis.md) |
| 3425 | `fce4985aa4` | 2026-03-20 16:22:54 +0100 | Maximilian Michels | Flink: Fix non-deterministic operator UIDs in DynamicIcebergSink (#15687) | ✅ 已完成 | [3425_fce4985aa4](commits/3425_fce4985aa4/analysis.md) |
| 3426 | `1139cd441f` | 2026-03-20 16:56:12 +0100 | GuoYu | Data: Add TCK tests for ReadBuilder in BaseFormatModelTests (#15633) | ✅ 已完成 | [3426_1139cd441f](commits/3426_1139cd441f/analysis.md) |
| 3427 | `08ac7844d2` | 2026-03-20 12:04:20 -0500 | Russell Spitzer | Core: Propagate Avro compression settings to manifest writers (#15652) | ✅ 已完成 | [3427_08ac7844d2](commits/3427_08ac7844d2/analysis.md) |
| 3428 | `5caeec64d0` | 2026-03-20 10:39:00 -0700 | Matt Topol | Site: Add Iceberg-Go 0.5.0 release blog post (#15679) | ✅ 已完成 | [3428_5caeec64d0](commits/3428_5caeec64d0/analysis.md) |
| 3429 | `0e5428cdc4` | 2026-03-20 14:30:17 -0700 | Maximilian Michels | Flink: Backport: Fix non-deterministic operator UIDs in DynamicIcebergSink (#15687) (#15702) | ✅ 已完成 | [3429_0e5428cdc4](commits/3429_0e5428cdc4/analysis.md) |
| 3430 | `28bf0f591c` | 2026-03-20 15:40:25 -0700 | Kevin Liu | Build: Stop ignoring gradle directory (#15705) | ✅ 已完成 | [3430_28bf0f591c](commits/3430_28bf0f591c/analysis.md) |
| 3431 | `406016bf2a` | 2026-03-21 12:58:36 +0100 | GuoYu | Data, Orc, Parquet: Throw exception when non-vectorized reader set recordsPerBatch (#15701) | ✅ 已完成 | [3431_406016bf2a](commits/3431_406016bf2a/analysis.md) |
| 3432 | `9429fe02a6` | 2026-03-21 23:40:24 -0700 | dependabot[bot] | Build: Bump pymarkdownlnt from 0.9.35 to 0.9.36 (#15715) | ✅ 已完成 | [3432_9429fe02a6](commits/3432_9429fe02a6/analysis.md) |
| 3433 | `38bd4f1a8e` | 2026-03-21 23:40:49 -0700 | dependabot[bot] | Build: Bump testcontainers from 2.0.3 to 2.0.4 (#15716) | ✅ 已完成 | [3433_38bd4f1a8e](commits/3433_38bd4f1a8e/analysis.md) |
| 3434 | `c46cd4efbc` | 2026-03-21 23:41:09 -0700 | dependabot[bot] | Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#15717) | ✅ 已完成 | [3434_c46cd4efbc](commits/3434_c46cd4efbc/analysis.md) |
| 3435 | `ce3c5e47a6` | 2026-03-21 23:41:29 -0700 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.12 to 1.6.13 (#15718) | ✅ 已完成 | [3435_ce3c5e47a6](commits/3435_ce3c5e47a6/analysis.md) |
| 3436 | `f914c9e9fe` | 2026-03-21 23:41:51 -0700 | dependabot[bot] | Build: Bump org.codehaus.jettison:jettison from 1.5.4 to 1.5.5 (#15719) | ✅ 已完成 | [3436_f914c9e9fe](commits/3436_f914c9e9fe/analysis.md) |
| 3437 | `56ef45f7d6` | 2026-03-21 23:42:10 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.42.13 to 2.42.18 (#15720) | ✅ 已完成 | [3437_56ef45f7d6](commits/3437_56ef45f7d6/analysis.md) |
| 3438 | `52ee8d5b5a` | 2026-03-21 23:42:42 -0700 | dependabot[bot] | Build: Bump jackson-bom from 2.21.1 to 2.21.2 (#15722) | ✅ 已完成 | [3438_52ee8d5b5a](commits/3438_52ee8d5b5a/analysis.md) |
| 3439 | `62f497c062` | 2026-03-21 23:43:03 -0700 | dependabot[bot] | Build: Bump io.grpc:grpc-netty-shaded from 1.79.0 to 1.80.0 (#15723) | ✅ 已完成 | [3439_62f497c062](commits/3439_62f497c062/analysis.md) |
| 3440 | `77f65e1462` | 2026-03-22 09:20:13 -0700 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.51.2.0 to 3.51.3.0 (#15721) | ✅ 已完成 | [3440_77f65e1462](commits/3440_77f65e1462/analysis.md) |
| 3441 | `a8e9ad2ae0` | 2026-03-22 09:22:27 -0700 | jackylee | Docs: Document Spark SQL transform functions (#15697) | ✅ 已完成 | [3441_a8e9ad2ae0](commits/3441_a8e9ad2ae0/analysis.md) |
| 3442 | `65869bfad8` | 2026-03-23 07:45:32 +0100 | Russell Spitzer | Spark 3.4, 3.5: Order results to fix flakiness with remote planning (#15725) | ✅ 已完成 | [3442_65869bfad8](commits/3442_65869bfad8/analysis.md) |
| 3443 | `a114e955e4` | 2026-03-23 10:17:45 +0100 | Kevin Liu | Infra: Remove GitHub Actions updates from dependabot config (#15711) | ✅ 已完成 | [3443_a114e955e4](commits/3443_a114e955e4/analysis.md) |
| 3444 | `7480c3bc2b` | 2026-03-23 16:32:28 +0100 | Eduard Tudenhoefner | AWS: Schedule next credential refresh (#15732) | ✅ 已完成 | [3444_7480c3bc2b](commits/3444_7480c3bc2b/analysis.md) |
| 3445 | `acc2c665bf` | 2026-03-23 09:59:08 -0700 | Manu Zhang | Spark: Remove Spark 4.x Java version skip guards (#15737) | ✅ 已完成 | [3445_acc2c665bf](commits/3445_acc2c665bf/analysis.md) |
| 3446 | `9e5fe8f40f` | 2026-03-23 11:26:34 -0700 | Yuya Ebihara | Infra: Pin versions in publish-iceberg-rest-fixture-docker.yml (#15730) | ✅ 已完成 | [3446_9e5fe8f40f](commits/3446_9e5fe8f40f/analysis.md) |
| 3447 | `72b5f8e1ea` | 2026-03-23 19:34:07 +0100 | Eduard Tudenhoefner | API, Core: Only include required stats fields (#15739) | ✅ 已完成 | [3447_72b5f8e1ea](commits/3447_72b5f8e1ea/analysis.md) |
| 3448 | `19b4189060` | 2026-03-23 11:41:12 -0700 | Eduard Tudenhoefner | GCP: Add scheduled refresh for storage credentials held by GCSFileIO (#15696) | ✅ 已完成 | [3448_19b4189060](commits/3448_19b4189060/analysis.md) |
| 3449 | `60a6e7324c` | 2026-03-23 11:50:56 -0700 | Szehon Ho | Core: Fix rewrite_position_delete_files failure with array/map columns (#15632) | ✅ 已完成 | [3449_60a6e7324c](commits/3449_60a6e7324c/analysis.md) |
| 3450 | `17738b7c5c` | 2026-03-23 12:02:17 -0700 | Kevin Liu | ci: pin third-party actions to Apache-approved SHAs (#15707) | ✅ 已完成 | [3450_17738b7c5c](commits/3450_17738b7c5c/analysis.md) |
| 3451 | `32b2f00125` | 2026-03-23 14:32:39 -0700 | Szehon Ho | Spark: Replicate position delete array/map fix to Spark 3.4, 3.5, and 4.0 (#15743) | ✅ 已完成 | [3451_32b2f00125](commits/3451_32b2f00125/analysis.md) |
| 3452 | `0518651766` | 2026-03-23 17:58:43 -0700 | Maninder | Site: Update Snowflake vendor description (#15745) | ✅ 已完成 | [3452_0518651766](commits/3452_0518651766/analysis.md) |
| 3453 | `7153d0a3fe` | 2026-03-24 08:23:24 -0700 | Matt Butrovich | Docs: Add blog post for Iceberg Rust 0.9.0 release (#15744) | ✅ 已完成 | [3453_7153d0a3fe](commits/3453_7153d0a3fe/analysis.md) |
| 3454 | `84e22676a4` | 2026-03-24 12:25:47 -0500 | Russell Spitzer | Core, Data, Delta, Flink, Kafka, Spark : Migrate callers off deprecated Snapshot file-access methods to Snapshot Changes (#15656) | ✅ 已完成 | [3454_84e22676a4](commits/3454_84e22676a4/analysis.md) |
| 3455 | `4bcd1c16b7` | 2026-03-24 16:02:02 -0700 | Kevin Liu | ci: pin GitHub action to commit hash (#15753) | ✅ 已完成 | [3455_4bcd1c16b7](commits/3455_4bcd1c16b7/analysis.md) |
| 3456 | `050378663b` | 2026-03-24 17:26:23 -0700 | Mukund Thakur | Core: Add VisibleForTesting annotation in SchemaUpdate constructor (#15756) | ✅ 已完成 | [3456_050378663b](commits/3456_050378663b/analysis.md) |
| 3457 | `63ecc7c387` | 2026-03-24 23:06:56 -0700 | antonlin1 | Spark: fix NPE thrown for MAP/LIST columns on DELETE, UPDATE, and MERGE operations (#15726) | ✅ 已完成 | [3457_63ecc7c387](commits/3457_63ecc7c387/analysis.md) |
| 3458 | `5dff6f6d77` | 2026-03-25 07:31:29 +0100 | rkaveti | Core: Pass storage credentials to ioBuilder-created FileIO (#15752) | ✅ 已完成 | [3458_5dff6f6d77](commits/3458_5dff6f6d77/analysis.md) |
| 3459 | `027f088d99` | 2026-03-25 16:07:29 +0100 | Hayoung Lee | Flink: Fix HashKeyGenerator SelectorKey cache ignoring writeParallelism and distributionMode (#15740) | ✅ 已完成 | [3459_027f088d99](commits/3459_027f088d99/analysis.md) |
| 3460 | `8a51a68589` | 2026-03-25 22:35:58 -0700 | Hayoung Lee | Flink: Backport: Fix HashKeyGenerator SelectorKey cache ignoring writeParallelism and distributionMode (#15762) | ✅ 已完成 | [3460_8a51a68589](commits/3460_8a51a68589/analysis.md) |
| 3461 | `8c8c391ed8` | 2026-03-26 08:38:17 -0700 | jackylee | Docs: clarify SparkSessionCatalog function limitations (#15736) | ✅ 已完成 | [3461_8c8c391ed8](commits/3461_8c8c391ed8/analysis.md) |
| 3462 | `f86b8d73c6` | 2026-03-26 13:45:22 -0700 | Russell Spitzer | Docs: Update Slack invite links (#15782) | ✅ 已完成 | [3462_f86b8d73c6](commits/3462_f86b8d73c6/analysis.md) |
| 3463 | `f575d97755` | 2026-03-26 14:06:34 -0700 | Wing Yew Poon | Spark: test cleanup - eliminate unnecessary table refreshes (#15765) | ✅ 已完成 | [3463_f575d97755](commits/3463_f575d97755/analysis.md) |
| 3464 | `9f2bcf582e` | 2026-03-26 19:12:55 -0700 | Wing Yew Poon | Spark 3.4, 3.5, 4.0: test cleanup - eliminate unnecessary table refreshes (#15787) | ✅ 已完成 | [3464_9f2bcf582e](commits/3464_9f2bcf582e/analysis.md) |
| 3465 | `9011eace5e` | 2026-03-27 14:53:35 +0100 | Manu Zhang | Docs: Fix Flink Getting Started page (#15772) | ✅ 已完成 | [3465_9011eace5e](commits/3465_9011eace5e/analysis.md) |
| 3466 | `24cb038903` | 2026-03-27 11:05:05 -0500 | Yaniv Zalach | Spark: Validate Z-order rewrite does not conflict with internal ICEZVALUE column name (#15706) | ✅ 已完成 | [3466_24cb038903](commits/3466_24cb038903/analysis.md) |
| 3467 | `420fb1a688` | 2026-03-27 12:44:23 -0500 | jbewing | Spark 4.1: Set data file sort_order_id in manifest for writes from Spark (#15150) | ✅ 已完成 | [3467_420fb1a688](commits/3467_420fb1a688/analysis.md) |
| 3468 | `a7d2113ac1` | 2026-03-27 11:29:14 -0700 | Kevin Liu | CI: Fix zizmor security findings in PR-triggered workflows (#15788) | ✅ 已完成 | [3468_a7d2113ac1](commits/3468_a7d2113ac1/analysis.md) |
| 3469 | `9fb6a00f3e` | 2026-03-27 11:30:05 -0700 | Kevin Liu | Build: Harden GitHub Actions workflows against zizmor findings (#15790) | ✅ 已完成 | [3469_9fb6a00f3e](commits/3469_9fb6a00f3e/analysis.md) |
| 3470 | `37a0ed6899` | 2026-03-27 14:31:03 -0700 | Kevin Liu | CI: Add ASF allowlist check workflow (#15797) | ✅ 已完成 | [3470_37a0ed6899](commits/3470_37a0ed6899/analysis.md) |
| 3471 | `b9d90533e0` | 2026-03-27 14:31:25 -0700 | Kevin Liu | ci: add cooldown to dependabot (#15796) | ✅ 已完成 | [3471_b9d90533e0](commits/3471_b9d90533e0/analysis.md) |
| 3472 | `79d4fc82cf` | 2026-03-27 16:06:51 -0700 | Anoop Johnson | Core: Add manifest partition pruning to DV validation in MergingSnapshotProducer (#15653) | ✅ 已完成 | [3472_79d4fc82cf](commits/3472_79d4fc82cf/analysis.md) |
| 3473 | `2b212578d4` | 2026-03-27 16:48:20 -0700 | Kevin Liu | CI: Add back Dependabot for GitHub Actions (#15801) | ✅ 已完成 | [3473_2b212578d4](commits/3473_2b212578d4/analysis.md) |
| 3474 | `315620a18f` | 2026-03-27 16:57:53 -0700 | Kevin Liu | CI: Replace `actions/cache` with `gradle/actions/setup-gradle` for Gradle caching (#15799) | ✅ 已完成 | [3474_315620a18f](commits/3474_315620a18f/analysis.md) |
| 3475 | `e0fecc4c77` | 2026-03-27 16:58:23 -0700 | dependabot[bot] | Build: Bump astral-sh/setup-uv from 7.3.1 to 7.6.0 (#15803) | ✅ 已完成 | [3475_e0fecc4c77](commits/3475_e0fecc4c77/analysis.md) |
| 3476 | `31f36619da` | 2026-03-27 18:35:34 -0700 | Russell Spitzer | Core: Fix TestReplacePartitions using wrong table for validation (#15798) | ✅ 已完成 | [3476_31f36619da](commits/3476_31f36619da/analysis.md) |
| 3477 | `99d5bd0b40` | 2026-03-27 19:52:38 -0700 | Kevin Liu | ci: add zizmor github workflow (#15793) | ✅ 已完成 | [3477_99d5bd0b40](commits/3477_99d5bd0b40/analysis.md) |
| 3478 | `5ded8168fe` | 2026-03-27 20:30:39 -0700 | Eunbin Son | Arrow: Tighten VectorHolder constructor visibility to private (#15804) | ✅ 已完成 | [3478_5ded8168fe](commits/3478_5ded8168fe/analysis.md) |
| 3479 | `4eee56c983` | 2026-03-27 20:35:04 -0700 | Kevin Liu | CI: Fix JMH benchmark workflows (#15800) | ✅ 已完成 | [3479_4eee56c983](commits/3479_4eee56c983/analysis.md) |
| 3480 | `0aefafb79a` | 2026-03-27 22:59:32 -0700 | Kevin Liu | add tag as inline comment (#15805) | ✅ 已完成 | [3480_0aefafb79a](commits/3480_0aefafb79a/analysis.md) |
| 3481 | `2abac79fca` | 2026-03-28 22:39:46 -0700 | Rishi | API, Core: Add overwrite-aware table registration (#15525) | ✅ 已完成 | [3481_2abac79fca](commits/3481_2abac79fca/analysis.md) |
| 3482 | `b086b4cbe9` | 2026-03-30 11:26:56 -0700 | Hongyue/Steve Zhang | Core: Fix NPE of generateRandomMetrics in FileGenerationUtil (#15748) | ✅ 已完成 | [3482_b086b4cbe9](commits/3482_b086b4cbe9/analysis.md) |
| 3483 | `857c1abb93` | 2026-03-30 14:04:01 -0600 | Robin Moffatt | Kafka Connect: Fix CVE-2025-67721 in io.airlift:aircompressor by bumping to 2.0.3 (#15440) | ✅ 已完成 | [3483_857c1abb93](commits/3483_857c1abb93/analysis.md) |
| 3484 | `d37ec8b15b` | 2026-03-30 13:32:04 -0700 | Kevin Liu | ci: fix zizmor security alerts (#15820) | ✅ 已完成 | [3484_d37ec8b15b](commits/3484_d37ec8b15b/analysis.md) |
| 3485 | `23b5e1d295` | 2026-03-30 13:42:37 -0700 | Naama Maoz | Fix position delete rewrite option validation (#15828) | ✅ 已完成 | [3485_23b5e1d295](commits/3485_23b5e1d295/analysis.md) |
| 3486 | `3ff0d97b10` | 2026-03-30 23:02:37 -0700 | Ruobing Wang | Spark: Remove Spark 2 test assumptions for write projection (#15823) | ✅ 已完成 | [3486_3ff0d97b10](commits/3486_3ff0d97b10/analysis.md) |
| 3487 | `149cc464f9` | 2026-03-30 23:03:52 -0700 | Eunbin Son | Docs: Update Flink version in contribute.md build example (#15812) | ✅ 已完成 | [3487_149cc464f9](commits/3487_149cc464f9/analysis.md) |
| 3488 | `da0ad6a7cc` | 2026-03-31 14:39:04 -0700 | Alexandre Dutra | OpenAPI: Promote the S3 signing endpoint to the main spec (#15450) | ✅ 已完成 | [3488_da0ad6a7cc](commits/3488_da0ad6a7cc/analysis.md) |
| 3489 | `ee1878f3d8` | 2026-03-31 17:04:19 -0700 | Anoop Johnson | API, Core: Introduce foundational types for V4 manifest support (#15049) | ✅ 已完成 | [3489_ee1878f3d8](commits/3489_ee1878f3d8/analysis.md) |
| 3490 | `850480018e` | 2026-03-31 17:40:06 -0700 | Ruijing Li | Spark 4.1: Fix async microbatch plan bugs (#15670) | ✅ 已完成 | [3490_850480018e](commits/3490_850480018e/analysis.md) |
| 3491 | `fd21d667fb` | 2026-04-01 07:34:46 -0700 | Marius Grama | GCS: Throw NotFoundException for nonexisting input GCS file (#15734) | ✅ 已完成 | [3491_fd21d667fb](commits/3491_fd21d667fb/analysis.md) |
| 3492 | `05d7ece42a` | 2026-04-01 10:19:04 -0600 | Szehon Ho | Spark 4.1: Control merge schema evolution by table property (#15825) | ✅ 已完成 | [3492_05d7ece42a](commits/3492_05d7ece42a/analysis.md) |
| 3493 | `6103dab58f` | 2026-04-01 14:22:45 -0700 | Anoop Johnson | Remove v4 references from javadocs (#15851) | ✅ 已完成 | [3493_6103dab58f](commits/3493_6103dab58f/analysis.md) |
| 3494 | `ff298a676d` | 2026-04-01 14:38:54 -0700 | Alex Stephen | BigQuery: Fix dependency leak into runtime Jars (#15655) | ✅ 已完成 | [3494_ff298a676d](commits/3494_ff298a676d/analysis.md) |
| 3495 | `d204e5e2df` | 2026-04-01 14:51:47 -0700 | Eunbin Son | Spec: Fix typos and stray formatting in gcm-stream-spec and puffin-spec (#15813) | ✅ 已完成 | [3495_d204e5e2df](commits/3495_d204e5e2df/analysis.md) |
| 3496 | `88d553899d` | 2026-04-01 14:54:27 -0700 | Eunbin Son | Docs: Fix stale version label and missing integrations in mkdocs-dev.yml (#15810) | ✅ 已完成 | [3496_88d553899d](commits/3496_88d553899d/analysis.md) |
| 3497 | `245637a62f` | 2026-04-01 16:43:16 -0700 | Russell Spitzer | Build: Add runtime dependency guard for bundled artifacts (#15855) | ✅ 已完成 | [3497_245637a62f](commits/3497_245637a62f/analysis.md) |
| 3498 | `e8b619148b` | 2026-04-01 21:44:39 -0500 | Ryan Blue | Aliyun: Remove leaked transitive dependencies. (#15858) | ✅ 已完成 | [3498_e8b619148b](commits/3498_e8b619148b/analysis.md) |
| 3499 | `3e22f850bd` | 2026-04-01 21:32:29 -0700 | Atsuo Yamaguchi | Docs: Fix missing semicolons in Java API Quickstart imports (#15864) | ✅ 已完成 | [3499_3e22f850bd](commits/3499_3e22f850bd/analysis.md) |
| 3500 | `1a2a8a5ecc` | 2026-04-02 09:54:55 -0700 | jbewing | Spark (4.0, 3.5): Set data file sort_order_id in manifest for writes from Spark (#15832) | ✅ 已完成 | [3500_1a2a8a5ecc](commits/3500_1a2a8a5ecc/analysis.md) |
| 3501 | `3550bcef7a` | 2026-04-03 16:14:19 +0200 | Eduard Tudenhoefner | Core: Upgrade Jetty to 12.1.5 (#10837) | ✅ 已完成 | [3501_3550bcef7a](commits/3501_3550bcef7a/analysis.md) |
| 3502 | `9a939d6835` | 2026-04-03 16:15:34 +0200 | Maksim Konstantinov | Build: bump shadow-gradle-plugin to 9.4.1 (#15835) | ✅ 已完成 | [3502_9a939d6835](commits/3502_9a939d6835/analysis.md) |
| 3503 | `775f55b50d` | 2026-04-04 21:49:13 -0700 | dependabot[bot] | Build: Bump mkdocs-redirects from 1.2.2 to 1.2.3 (#15885) | ✅ 已完成 | [3503_775f55b50d](commits/3503_775f55b50d/analysis.md) |
| 3504 | `4575b6a278` | 2026-04-04 21:51:51 -0700 | dependabot[bot] | Build: Bump astral-sh/setup-uv from 7.6.0 to 8.0.0 (#15888) | ✅ 已完成 | [3504_4575b6a278](commits/3504_4575b6a278/analysis.md) |
| 3505 | `cc90c0e862` | 2026-04-04 22:56:02 -0700 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#15886) | ✅ 已完成 | [3505_cc90c0e862](commits/3505_cc90c0e862/analysis.md) |
| 3506 | `85cd18d316` | 2026-04-04 22:56:17 -0700 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.78.0 to 26.79.0 (#15889) | ✅ 已完成 | [3506_85cd18d316](commits/3506_85cd18d316/analysis.md) |
| 3507 | `c6ff39a69f` | 2026-04-04 22:56:30 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.42.18 to 2.42.23 (#15890) | ✅ 已完成 | [3507_c6ff39a69f](commits/3507_c6ff39a69f/analysis.md) |
| 3508 | `a18ae8a85e` | 2026-04-04 23:34:02 -0700 | dependabot[bot] | Build: Bump jetty from 12.1.5 to 12.1.7 (#15887) | ✅ 已完成 | [3508_a18ae8a85e](commits/3508_a18ae8a85e/analysis.md) |
| 3509 | `b25cb522cc` | 2026-04-04 23:34:18 -0700 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.10.Final to 4.2.12.Final (#15891) | ✅ 已完成 | [3509_b25cb522cc](commits/3509_b25cb522cc/analysis.md) |
| 3510 | `ec2de913bf` | 2026-04-05 13:56:13 -0700 | Jiajia Li | AWS: Add chunked encoding configuration for S3 requests (#15242) | ✅ 已完成 | [3510_ec2de913bf](commits/3510_ec2de913bf/analysis.md) |
| 3511 | `e1a2713d18` | 2026-04-07 08:46:45 -0700 | Rahul Shivu Mahadev | Core : Make REST scan planning poll timeout configurable (#15863) | ✅ 已完成 | [3511_e1a2713d18](commits/3511_e1a2713d18/analysis.md) |
| 3512 | `66870849f2` | 2026-04-10 10:07:38 -0700 | Ryan Blue | Spark 4.1: Add runtime-deps.txt. (#15860) | ✅ 已完成 | [3512_66870849f2](commits/3512_66870849f2/analysis.md) |
| 3513 | `9586899fab` | 2026-04-10 13:45:54 -0700 | Wing Yew Poon | Update documentation on Spark migrate procedure (#15874) | ✅ 已完成 | [3513_9586899fab](commits/3513_9586899fab/analysis.md) |
| 3514 | `1b27a582d3` | 2026-04-10 13:52:39 -0700 | jackylee | Docs: Add Hive Metastore schema validation warnings for schema evolution with Hive catalog (#15814) | ✅ 已完成 | [3514_1b27a582d3](commits/3514_1b27a582d3/analysis.md) |
| 3515 | `4c215926a0` | 2026-04-10 16:35:33 -0700 | Huaxin Gao | Build: Fix zizmor and Spark 4.1 runtime-deps CI failures (#15937) | ✅ 已完成 | [3515_4c215926a0](commits/3515_4c215926a0/analysis.md) |
| 3516 | `9242be6dd` | 2026-04-11 03:08:35 +0200 | Eduard Tudenhoefner | Revert "Build: bump shadow-gradle-plugin to 9.4.1 (#15835)" (#15941) | ✅ 已完成 | [3516_9242be6dd](commits/3516_9242be6dd/analysis.md) |
| 3517 | `7e4aa89d9` | 2026-04-11 16:04:19 +0200 | Eduard Tudenhoefner | AWS, Core: Switch Jetty to use new Compression API for GZIP (#15043) | ✅ 已完成 | [3517_7e4aa89d9](commits/3517_7e4aa89d9/analysis.md) |
| 3518 | `b156f3414` | 2026-04-11 20:06:13 -0700 | Dhruv Arya | pass dockerhub token the safely (#15940) | ✅ 已完成 | [3518_b156f3414](commits/3518_b156f3414/analysis.md) |
| 3519 | `e4d153332` | 2026-04-12 07:46:31 +0200 | Eduard Tudenhoefner | API: Include size unit in avg/max value size fields (#15939) | ✅ 已完成 | [3519_e4d153332](commits/3519_e4d153332/analysis.md) |
| 3520 | `5bfabc307` | 2026-04-11 23:29:59 -0700 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.55.0 to 0.56.0 (#15949) | ✅ 已完成 | [3520_5bfabc307](commits/3520_5bfabc307/analysis.md) |
| 3521 | `3a6863d2f` | 2026-04-11 23:30:14 -0700 | dependabot[bot] | Build: Bump jetty from 12.1.7 to 12.1.8 (#15951) | ✅ 已完成 | [3521_3a6863d2f](commits/3521_3a6863d2f/analysis.md) |
| 3522 | `72c993c7e` | 2026-04-11 23:30:31 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.42.23 to 2.42.28 (#15952) | ✅ 已完成 | [3522_72c993c7e](commits/3522_72c993c7e/analysis.md) |
| 3523 | `beef60d95` | 2026-04-13 16:25:27 +0200 | Govindarajan | API: Fix TableIdentifier.toLowerCase to use Locale.ROOT for namespace levels (#15956) (#15958) | ✅ 已完成 | [3523_beef60d95](commits/3523_beef60d95/analysis.md) |
| 3524 | `0ed7f7791` | 2026-04-13 09:33:16 -0700 | genxiong7 | Flink: Fix checkArgument message for flink streaming (#15907) | ✅ 已完成 | [3524_0ed7f7791](commits/3524_0ed7f7791/analysis.md) |
| 3525 | `56092bc8c` | 2026-04-13 10:44:37 -0700 | Neelesh Salian | Parquet: Fix NPE in ParquetAvroWriter when schema contains variant type (#15934) | ✅ 已完成 | [3525_56092bc8c](commits/3525_56092bc8c/analysis.md) |
| 3526 | `3f18cd4f6` | 2026-04-13 14:02:10 -0700 | kumarpritam863 | Kafka Connect: Fix source offset tracking when SMTs modify the record topic (#15880) | ✅ 已完成 | [3526_3f18cd4f6](commits/3526_3f18cd4f6/analysis.md) |
| 3527 | `2a6f12784` | 2026-04-13 15:11:36 -0700 | Yuya Ebihara | Core: Expose MetricsConfig.from method with 3-parameter version (#15819) | ✅ 已完成 | [3527_2a6f12784](commits/3527_2a6f12784/analysis.md) |
| 3528 | `5d1840cae` | 2026-04-13 18:35:39 -0700 | XL Liang | Docs: Add Sail to integration and vendor (#15920) | ✅ 已完成 | [3528_5d1840cae](commits/3528_5d1840cae/analysis.md) |
| 3529 | `8808f7761` | 2026-04-14 09:16:23 +0200 | Marius Grama | ADLS: Throw NotFoundException for inexistent input file (#15806) | ✅ 已完成 | [3529_8808f7761](commits/3529_8808f7761/analysis.md) |
| 3530 | `87c743463` | 2026-04-14 09:18:09 +0200 | Yuya Ebihara | Build: Ban toLowerCase/toUpperCase without locale (#15960) | ✅ 已完成 | [3530_87c743463](commits/3530_87c743463/analysis.md) |
| 3531 | `ffb095db0` | 2026-04-14 13:05:11 -0700 | Eduard Tudenhoefner | API, Core: Move stats classes to core as package-private (#15971) | ✅ 已完成 | [3531_ffb095db0](commits/3531_ffb095db0/analysis.md) |
| 3532 | `5e3c28438` | 2026-04-14 15:34:12 -0700 | Russell Spitzer | API: Relax partition name check when source column is dropped (#15967) | ✅ 已完成 | [3532_5e3c28438](commits/3532_5e3c28438/analysis.md) |
| 3533 | `dfe16c1ab` | 2026-04-15 08:03:16 -0700 | Anoop Johnson | Core, API, Spark: Add FileContent.fromId (#15953) | ✅ 已完成 | [3533_dfe16c1ab](commits/3533_dfe16c1ab/analysis.md) |
| 3534 | `893528c4d` | 2026-04-15 09:09:59 -0700 | Mukunda Rao Katta | Fix typos in javadoc/comment: 'intialize', 'seperated' (#15978) | ✅ 已完成 | [3534_893528c4d](commits/3534_893528c4d/analysis.md) |
| 3535 | `b710f476c` | 2026-04-15 10:52:33 -0700 | Robin Moffatt | Build: Fix codeql-action version comment to match pinned SHA (#15985) | ✅ 已完成 | [3535_b710f476c](commits/3535_b710f476c/analysis.md) |
| 3536 | `3e56b5287` | 2026-04-15 10:58:17 -0700 | Anoop Johnson | Core: Add fromId to EntryStatus and ManifestEntry.Status (#15983) | ✅ 已完成 | [3536_3e56b5287](commits/3536_3e56b5287/analysis.md) |
| 3537 | `fa4e978ca` | 2026-04-15 12:08:44 -0700 | Kevin Liu | ci: remove zizmor ignore for allowlist-check, pin to main (#15987) | ✅ 已完成 | [3537_fa4e978ca](commits/3537_fa4e978ca/analysis.md) |
| 3538 | `304c00f3c` | 2026-04-15 14:51:10 -0700 | Oguzhan Unlu | Spec: Add 404 response for config endpoint (#15746) | ✅ 已完成 | [3538_304c00f3c](commits/3538_304c00f3c/analysis.md) |
| 3539 | `44145262d` | 2026-04-15 15:26:29 -0700 | Sebastian Baunsgaard | Core: Optimize RoaringPositionBitmap.setRange with native range API (#15791) | ✅ 已完成 | [3539_44145262d](commits/3539_44145262d/analysis.md) |
| 3540 | `8f30d8350` | 2026-04-15 15:54:06 -0700 | gaborkaszab | Core: Introduce default values in RESTCatalogProperties (#15873) | ✅ 已完成 | [3540_8f30d8350](commits/3540_8f30d8350/analysis.md) |
| 3541 | `941884210` | 2026-04-15 16:26:44 -0700 | Sreesh Maheshwar | Hive encryption nits (#14659) | ✅ 已完成 | [3541_941884210](commits/3541_941884210/analysis.md) |
| 3542 | `20ca6c3f8` | 2026-04-16 09:02:01 +0800 | Shawn Chang | Update Rust status on the site (#15709) | ✅ 已完成 | [3542_20ca6c3f8](commits/3542_20ca6c3f8/analysis.md) |
| 3543 | `0babf7d17` | 2026-04-15 20:09:36 -0700 | Yuya Ebihara | Core: Fix StructLikeWrapper.equals exception with mismatched partition types (#15945) | ✅ 已完成 | [3543_0babf7d17](commits/3543_0babf7d17/analysis.md) |
| 3544 | `46c1101ad` | 2026-04-15 20:12:56 -0700 | Barry | API: Fix FileRange validation to reject negative offset/length (#15926) | ✅ 已完成 | [3544_46c1101ad](commits/3544_46c1101ad/analysis.md) |
| 3545 | `74acec7b3` | 2026-04-15 20:14:01 -0700 | yadavay-amzn | Docs: Replace deprecated 'compile' with 'implementation' in Gradle snippet (#15921) | ✅ 已完成 | [3545_74acec7b3](commits/3545_74acec7b3/analysis.md) |
| 3546 | `1a6a48810` | 2026-04-15 20:19:56 -0700 | Manu Zhang | Build: Ignore `.githooks` (#15909) | ✅ 已完成 | [3546_1a6a48810](commits/3546_1a6a48810/analysis.md) |
| 3547 | `f0cf4de76` | 2026-04-15 20:55:32 -0700 | sanshi | Docs: Document that positionDeleteWriteBuilder is for format-version 2 tables only (#15980) | ✅ 已完成 | [3547_f0cf4de76](commits/3547_f0cf4de76/analysis.md) |
| 3548 | `f2ed6a9db` | 2026-04-16 02:35:35 -0700 | Rulin Xing | AWS: Close custom AwsCredentialsProvider in RESTSigV4AuthSession (#15818) | ✅ 已完成 | [3548_f2ed6a9db](commits/3548_f2ed6a9db/analysis.md) |
| 3549 | `dde712ec9` | 2026-04-16 13:28:57 +0200 | GuoYu | Data: Clean engineProjection in  BaseFormatModelTests (#15995) | ✅ 已完成 | [3549_dde712ec9](commits/3549_dde712ec9/analysis.md) |
| 3550 | `7897d57fb` | 2026-04-17 11:37:33 +0200 | Han You | Flink: Add passthroughRecords option to DynamicIcebergSink (#15433) | ✅ 已完成 | [3550_7897d57fb](commits/3550_7897d57fb/analysis.md) |
| 3551 | `ecbe8a84b` | 2026-04-17 13:19:06 +0200 | Kevin Liu | Build: set zizmor min-severity and min-confidence to medium (#16001) | ✅ 已完成 | [3551_ecbe8a84b](commits/3551_ecbe8a84b/analysis.md) |
| 3552 | `53fde56a8` | 2026-04-17 10:02:45 -0700 | Shohei Okumiya | Docs: Add Apache Hive 4.2 to website (#15998) | ✅ 已完成 | [3552_53fde56a8](commits/3552_53fde56a8/analysis.md) |
| 3553 | `8f1f48398` | 2026-04-17 19:58:06 +0200 | Sachin Ranjalkar | Flink: Set generator parallelism to match input in DynamicIcebergSink (#15849) | ✅ 已完成 | [3553_8f1f48398](commits/3553_8f1f48398/analysis.md) |
| 3554 | `69d7bface` | 2026-04-18 15:44:46 -0400 | Tanmay Rauth | Docs: Sync Go implementation status with iceberg-go (#16021) | ✅ 已完成 | [3554_69d7bface](commits/3554_69d7bface/analysis.md) |
| 3555 | `54cdbcdde` | 2026-04-18 22:30:49 -0700 | dependabot[bot] | Build: Bump mkdocs-rss-plugin from 1.17.9 to 1.18.1 (#16036) | ✅ 已完成 | [3555_54cdbcdde](commits/3555_54cdbcdde/analysis.md) |
| 3556 | `f66305aec` | 2026-04-18 22:58:23 -0700 | drexler-sky | Flink 2.1: Fix forward-writer chaining regression in DynamicIcebergSink (#16026) | ✅ 已完成 | [3556_f66305aec](commits/3556_f66305aec/analysis.md) |
| 3557 | `3111ba588` | 2026-04-19 07:15:37 -0700 | dependabot[bot] | Build: Bump com.azure:azure-sdk-bom from 1.3.5 to 1.3.6 (#16037) | ✅ 已完成 | [3557_3111ba588](commits/3557_3111ba588/analysis.md) |
| 3558 | `c66bd68e8` | 2026-04-19 07:15:58 -0700 | dependabot[bot] | Build: Bump at.yawk.lz4:lz4-java from 1.10.4 to 1.11.0 (#16038) | ✅ 已完成 | [3558_c66bd68e8](commits/3558_c66bd68e8/analysis.md) |
| 3559 | `9205427c5` | 2026-04-19 07:16:15 -0700 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#16039) | ✅ 已完成 | [3559_9205427c5](commits/3559_9205427c5/analysis.md) |
| 3560 | `55f892319` | 2026-04-19 07:17:11 -0700 | dependabot[bot] | Build: Bump docker/build-push-action from 7.0.0 to 7.1.0 (#16041) | ✅ 已完成 | [3560_55f892319](commits/3560_55f892319/analysis.md) |
| 3561 | `bfccee96b` | 2026-04-19 07:17:28 -0700 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.13 to 1.6.14 (#16042) | ✅ 已完成 | [3561_bfccee96b](commits/3561_bfccee96b/analysis.md) |
| 3562 | `49839545d` | 2026-04-19 10:21:37 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.42.28 to 2.42.33 (#16040) | ✅ 已完成 | [3562_49839545d](commits/3562_49839545d/analysis.md) |
| 3563 | `f984c28b2` | 2026-04-20 16:07:47 +0200 | Han You | Flink: Backport add passthroughRecords option to DynamicIcebergSink (#16019) | ✅ 已完成 | [3563_f984c28b2](commits/3563_f984c28b2/analysis.md) |
| 3564 | `a8b7ba6d5` | 2026-04-20 08:21:38 -0700 | Alexandre Dutra | Core: Expose HostnameVerificationPolicy in TLSConfigurer (#15500) | ✅ 已完成 | [3564_a8b7ba6d5](commits/3564_a8b7ba6d5/analysis.md) |
| 3565 | `bbd121220` | 2026-04-20 18:46:04 -0700 | Yuming Wang | Add .factorypath to .gitignore (#16067) | ✅ 已完成 | [3565_bbd121220](commits/3565_bbd121220/analysis.md) |
| 3566 | `d4db77be0` | 2026-04-21 12:38:48 +0200 | drexler-sky | Spark: Replace deprecated registerTempTable with createOrReplaceTempView (#16063) | ✅ 已完成 | [3566_d4db77be0](commits/3566_d4db77be0/analysis.md) |
| 3567 | `1f0579fb6` | 2026-04-21 09:00:24 -0700 | Mervyn Lobo | AWS: Add proxy system property and environment variable configuration for HTTP clients (#15506) | ✅ 已完成 | [3567_1f0579fb6](commits/3567_1f0579fb6/analysis.md) |
| 3568 | `a675f88b0` | 2026-04-21 11:08:50 -0700 | kumarpritam863 | Kafka Connect: Do not fail if no partitions assigned (#15955) | ✅ 已完成 | [3568_a675f88b0](commits/3568_a675f88b0/analysis.md) |
| 3569 | `5dae5fc85` | 2026-04-21 12:59:33 -0700 | alpbeysir | Core: Use Stream overload for reading response in HTTPClient (#15648) | ✅ 已完成 | [3569_5dae5fc85](commits/3569_5dae5fc85/analysis.md) |
| 3570 | `017ba2f6f` | 2026-04-22 06:33:48 -0700 | Yuya Ebihara | Spark: Fix RoaringBitmap version in runtime-deps.txt (#16076) | ✅ 已完成 | [3570_017ba2f6f](commits/3570_017ba2f6f/analysis.md) |
| 3571 | `4a31e519b` | 2026-04-22 07:52:29 -0600 | Anupam Yadav | Core: Use Idiomatic ThreadLocal cleanup in CommitMetadata (#15284) (#16031) | ✅ 已完成 | [3571_4a31e519b](commits/3571_4a31e519b/analysis.md) |
| 3572 | `5f80bc3a3` | 2026-04-22 10:13:55 -0700 | Yingjian Wu | Spark: fix delete from branch for canDeleteWhere where it does not resolve to the correct branch (#15512) | ✅ 已完成 | [3572_5f80bc3a3](commits/3572_5f80bc3a3/analysis.md) |
| 3573 | `844a0abc3` | 2026-04-22 11:45:50 -0700 | seokyun.ha | Kafka Connect: Support VARIANT when record convert (#15283) | ✅ 已完成 | [3573_844a0abc3](commits/3573_844a0abc3/analysis.md) |
| 3574 | `c5a09634e` | 2026-04-22 21:56:42 -0700 | Steven Zhen Wu | REST Spec: Clarify identifier uniqueness across tables and views (#15691) | ✅ 已完成 | [3574_c5a09634e](commits/3574_c5a09634e/analysis.md) |
| 3575 | `2e153ca04` | 2026-04-23 11:44:38 -0500 | Bharath Krishna | Spark 3.4, 3.5, 4.0: Include snapshotId and branch in SparkTable equals and hashCode (#15840) | ✅ 已完成 | [3575_2e153ca04](commits/3575_2e153ca04/analysis.md) |
| 3576 | `a4386b969` | 2026-04-23 19:33:09 +0200 | Eduard Tudenhoefner | Core, Spark: Verify that TRUNCATE removes orphaned DVs (#16078) | ✅ 已完成 | [3576_a4386b969](commits/3576_a4386b969/analysis.md) |
| 3577 | `1d5463f68` | 2026-04-23 16:05:26 -0500 | Bharath Krishna | API: Implement notStartsWith bounds check in StrictMetricsEvaluator (#15883) | ✅ 已完成 | [3577_1d5463f68](commits/3577_1d5463f68/analysis.md) |
| 3578 | `52092cd11` | 2026-04-23 14:17:44 -0700 | Anoop Johnson | Core: Add implementations of v4 TrackedFile interfaces (#15854) | ✅ 已完成 | [3578_52092cd11](commits/3578_52092cd11/analysis.md) |
| 3579 | `8f611675e` | 2026-04-23 16:53:45 -0700 | Anoop Johnson | Validate manifest sequence numbers are equal during inheritance (#16091) | ✅ 已完成 | [3579_8f611675e](commits/3579_8f611675e/analysis.md) |
| 3580 | `0e4f447ee` | 2026-04-24 14:14:39 +0200 | GuoYu | Data: Add TCK tests for metrics collection in BaseFormatModelTests (#15906) | ✅ 已完成 | [3580_0e4f447ee](commits/3580_0e4f447ee/analysis.md) |
| 3581 | `f475ccb5c` | 2026-04-24 15:49:05 +0200 | Denys Kuzmenko | ORC: Fix connection leak in OrcIterable (#16086) | ✅ 已完成 | [3581_f475ccb5c](commits/3581_f475ccb5c/analysis.md) |
| 3582 | `57b7c2bd5` | 2026-04-24 11:16:06 -0500 | Bharath Krishna | API: Use column bounds to evaluate startsWith in StrictMetricsEvaluator (#15902) | ✅ 已完成 | [3582_57b7c2bd5](commits/3582_57b7c2bd5/analysis.md) |
| 3583 | `8c217a35b` | 2026-04-24 22:28:41 +0200 | Chase Zhang | Flink: Fix watermark value which should be min timestamp minus one (#15884) | ✅ 已完成 | [3583_8c217a35b](commits/3583_8c217a35b/analysis.md) |
| 3584 | `03347ff6c` | 2026-04-24 22:34:56 +0200 | GuoYu | Data: Add TCK tests for Metadata Columns in BaseFormatModelTests (#15675) | ✅ 已完成 | [3584_03347ff6c](commits/3584_03347ff6c/analysis.md) |
| 3585 | `a287f0907` | 2026-04-24 17:57:32 -0400 | Russell Spitzer | Build: Check runtime deps baseline for all engine versions in CI (#16103) | ✅ 已完成 | [3585_a287f0907](commits/3585_a287f0907/analysis.md) |
| 3586 | `65faa6f35` | 2026-04-24 15:16:07 -0700 | Kevin Liu | Runtimes, Bundles: Add runtime-deps.txt files to track dependencies (#16081) | ✅ 已完成 | [3586_65faa6f35](commits/3586_65faa6f35/analysis.md) |
| 3587 | `f29a182ec` | 2026-04-24 18:06:49 -0600 | Ryan Blue | GCP Bundle: Remove JSR 305 (#16106) | ✅ 已完成 | [3587_f29a182ec](commits/3587_f29a182ec/analysis.md) |
| 3588 | `3d4c6e00d` | 2026-04-24 21:00:01 -0700 | M Alvee | test: add ns1/ns2 to RCK view test namespace purge list (#16050) | ✅ 已完成 | [3588_3d4c6e00d](commits/3588_3d4c6e00d/analysis.md) |
| 3589 | `f7ca134d5` | 2026-04-25 23:48:06 -0700 | dependabot[bot] | Build: Bump zizmorcore/zizmor-action from 0.5.2 to 0.5.3 (#16122) | ✅ 已完成 | [3589_f7ca134d5](commits/3589_f7ca134d5/analysis.md) |
| 3590 | `dd93aacb6` | 2026-04-25 23:48:21 -0700 | dependabot[bot] | Build: Bump astral-sh/setup-uv from 8.0.0 to 8.1.0 (#16121) | ✅ 已完成 | [3590_dd93aacb6](commits/3590_dd93aacb6/analysis.md) |
| 3591 | `180e399e1` | 2026-04-25 23:48:38 -0700 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.51.3.0 to 3.53.0.0 (#16120) | ✅ 已完成 | [3591_180e399e1](commits/3591_180e399e1/analysis.md) |
| 3592 | `22918cf30` | 2026-04-25 23:49:02 -0700 | dependabot[bot] | Build: Bump github/codeql-action from 4.35.1 to 4.35.2 (#16118) | ✅ 已完成 | [3592_22918cf30](commits/3592_22918cf30/analysis.md) |
| 3593 | `c213f5e96` | 2026-04-25 23:49:16 -0700 | dependabot[bot] | Build: Bump bouncycastle from 1.82 to 1.84 (#16117) | ✅ 已完成 | [3593_c213f5e96](commits/3593_c213f5e96/analysis.md) |
| 3594 | `5acbb7a5a` | 2026-04-25 23:49:32 -0700 | dependabot[bot] | Build: Bump guava from 33.5.0-jre to 33.6.0-jre (#16116) | ✅ 已完成 | [3594_5acbb7a5a](commits/3594_5acbb7a5a/analysis.md) |
| 3595 | `bd7096ee0` | 2026-04-25 23:49:56 -0700 | dependabot[bot] | Build: Bump mkdocs-rss-plugin from 1.18.1 to 1.19.0 (#16113) | ✅ 已完成 | [3595_bd7096ee0](commits/3595_bd7096ee0/analysis.md) |
| 3596 | `bac514ab4` | 2026-04-26 10:58:51 -0600 | Ryan Blue | Flink 2.1: Remove flink-metrics-dropwizard from runtime (#16093) | ✅ 已完成 | [3596_bac514ab4](commits/3596_bac514ab4/analysis.md) |
| 3597 | `afb7519eb` | 2026-04-27 00:30:12 -0600 | Ryan Blue | AWS Bundle: Exclude logging dependencies (#16105) | ✅ 已完成 | [3597_afb7519eb](commits/3597_afb7519eb/analysis.md) |
| 3598 | `2a615803e` | 2026-04-27 10:13:52 +0200 | Eduard Tudenhoefner | Spark 4.1: Parameterize TestDeleteFrom with format-version (#16098) | ✅ 已完成 | [3598_2a615803e](commits/3598_2a615803e/analysis.md) |
| 3599 | `1b733ed22` | 2026-04-27 13:53:38 +0200 | Manu Zhang | Core: Fix RejectedExecutionException in InMemoryLockManager when multiple catalogs share default lock manager (#15862) | ✅ 已完成 | [3599_1b733ed22](commits/3599_1b733ed22/analysis.md) |
| 3600 | `b809dcd77` | 2026-04-27 14:57:29 +0200 | Dmitriy Avseitsev | Core, Catalogs: Add support for unique table locations via catalog property (#12892) | ✅ 已完成 | [3600_b809dcd77](commits/3600_b809dcd77/analysis.md) |
| 3601 | `8d0508308` | 2026-04-27 14:59:31 +0200 | Harrison Crosse | Parquet: Add write.parquet.page-version table property (#15700) | ✅ 已完成 | [3601_8d0508308](commits/3601_8d0508308/analysis.md) |
| 3602 | `836bca9c7` | 2026-04-27 17:10:46 +0200 | GuoYu | Flink: RewriteDataFile support dynamic filter (#15865) | ✅ 已完成 | [3602_836bca9c7](commits/3602_836bca9c7/analysis.md) |
| 3603 | `b0f022ff2` | 2026-04-27 13:52:26 -0700 | GuoYu | Flink:Backport RewriteDataFile support dynamic filter (#16132) | ✅ 已完成 | [3603_b0f022ff2](commits/3603_b0f022ff2/analysis.md) |
| 3604 | `4e118e3ca` | 2026-04-27 18:54:17 -0600 | Ryan Blue | Spark 4.1: Update LICENSE and NOTICE for 1.11. (#16104) | ✅ 已完成 | [3604_4e118e3ca](commits/3604_4e118e3ca/analysis.md) |
| 3605 | `9bd214e50` | 2026-04-27 19:33:55 -0700 | drexler-sky | Arrow: Align vectorized reader handling of unsigned Parquet integers with BaseParquetReaders (#16006) | ✅ 已完成 | [3605_9bd214e50](commits/3605_9bd214e50/analysis.md) |
| 3606 | `57409faed` | 2026-04-27 23:49:07 -0700 | Bharath Krishna | Core: Fix child AuthSession inheriting parent's expiresAtMillis (#15999) | ✅ 已完成 | [3606_57409faed](commits/3606_57409faed/analysis.md) |
| 3607 | `d71583f58` | 2026-04-28 15:21:17 +0200 | Neelesh Salian | Spark, Hive: Fix snapshot procedure for tables with Variant columns (#15964) | ✅ 已完成 | [3607_d71583f58](commits/3607_d71583f58/analysis.md) |
| 3608 | `dd45bd926` | 2026-04-28 09:06:08 -0700 | Maximilian Michels | Flink: Bundle flink-metrics-dropwizard in runtime jar (#16126) | ✅ 已完成 | [3608_dd45bd926](commits/3608_dd45bd926/analysis.md) |
| 3609 | `4880f5bc3` | 2026-04-28 11:20:40 -0600 | Ryan Blue | Flink 2.1: Update LICENSE for 1.11. (#16102) | ✅ 已完成 | [3609_4880f5bc3](commits/3609_4880f5bc3/analysis.md) |
| 3610 | `9b139c99d` | 2026-04-28 11:44:07 -0600 | Ryan Blue | Spark: Carry over changes to LICENSE and NOTICE in older Spark versions. (#16142) | ✅ 已完成 | [3610_9b139c99d](commits/3610_9b139c99d/analysis.md) |
| 3611 | `b0df3ca01` | 2026-04-28 19:05:57 -0700 | Yuya Ebihara | Build: Bump software.amazon.awssdk:bom from 2.42.33 to 2.42.36 (#16151) | ✅ 已完成 | [3611_b0df3ca01](commits/3611_b0df3ca01/analysis.md) |
| 3612 | `099ef477c` | 2026-04-28 21:11:36 -0600 | Hongyue/Steve Zhang | Core: Validate v2 deletes against concurrent format upgrade (#16146) | ✅ 已完成 | [3612_099ef477c](commits/3612_099ef477c/analysis.md) |
| 3613 | `8ac703067` | 2026-04-28 22:36:21 -0700 | Yuya Ebihara | Build: Bump com.google.cloud:libraries-bom from 26.79.0 to 26.80.0 (#16152) | ✅ 已完成 | [3613_8ac703067](commits/3613_8ac703067/analysis.md) |
| 3614 | `c81534fba` | 2026-04-29 13:52:42 +0200 | Maximilian Michels | Flink: Backport: Bundle flink-metrics-dropwizard in runtime jar (#16141) | ✅ 已完成 | [3614_c81534fba](commits/3614_c81534fba/analysis.md) |
| 3615 | `f0ba022fc` | 2026-04-29 07:43:50 -0700 | Ruijing Li | Spark 3.5: Backport Async Micro Batch Planner to 3.5 (#15992) | ✅ 已完成 | [3615_f0ba022fc](commits/3615_f0ba022fc/analysis.md) |
| 3616 | `df00c156d` | 2026-04-29 07:44:17 -0700 | Ruijing Li | Spark 4.0: Backport Aync Micro Batch Planner Feature (#15876) | ✅ 已完成 | [3616_df00c156d](commits/3616_df00c156d/analysis.md) |
| 3617 | `b07435ba4` | 2026-04-29 20:45:28 -0500 | Talat UYARER | Site: Remove Iceberg Summit 2026 section as the event has passed (#16166) | ✅ 已完成 | [3617_b07435ba4](commits/3617_b07435ba4/analysis.md) |
| 3618 | `6869adba2` | 2026-04-29 22:31:20 -0600 | Anoop Johnson | Core: Add builders for v4 structs (#16092) | ✅ 已完成 | [3618_6869adba2](commits/3618_6869adba2/analysis.md) |
| 3619 | `54c6433cb` | 2026-04-30 13:55:51 +0200 | Anupam Yadav | Flink: Fix JdbcLockFactory to allow ClientPoolImpl connection retry (#16049) | ✅ 已完成 | [3619_54c6433cb](commits/3619_54c6433cb/analysis.md) |
| 3620 | `1bdbed7a5` | 2026-04-30 14:03:59 +0200 | Swapna Marru | Flink: SQL: Make Dynamic sink options to be configurable in SQL (#15780) | ✅ 已完成 | [3620_1bdbed7a5](commits/3620_1bdbed7a5/analysis.md) |
| 3621 | `0dab08cd9` | 2026-04-30 08:18:14 -0600 | Ryan Blue | Flink: Apply LICENSE changes to older Flink versions. (#16159) | ✅ 已完成 | [3621_0dab08cd9](commits/3621_0dab08cd9/analysis.md) |
| 3622 | `3dd5c1467` | 2026-04-30 18:01:28 +0200 | Talat UYARER | Flink: Add Nanosecond Precision Support for Flink-Iceberg Integration (#15475) | ✅ 已完成 | [3622_3dd5c1467](commits/3622_3dd5c1467/analysis.md) |
| 3623 | `96d556b8b` | 2026-04-30 09:54:23 -0700 | drexler-sky | Spark 4.1: Migrate SparkWriteBuilder to SupportsOverwriteV2 (#16164) | ✅ 已完成 | [3623_96d556b8b](commits/3623_96d556b8b/analysis.md) |
| 3624 | `185da6b29` | 2026-04-30 13:12:15 -0600 | hemanthboyina | Core: Avoid unnecessary manifest scanning during snapshot expiration incremental cleanup (#16077) | ✅ 已完成 | [3624_185da6b29](commits/3624_185da6b29/analysis.md) |
| 3625 | `6ce50265b` | 2026-04-30 14:43:35 -0700 | Kevin Liu | AWS: Fix stale LICENSE entry for Parquet, clarify failsafe attribution (#16179) | ✅ 已完成 | [3625_6ce50265b](commits/3625_6ce50265b/analysis.md) |
| 3626 | `3f14731e2` | 2026-05-01 09:45:57 -0500 | Ryan Blue | Open API: Remove runtime Jar from build and deploy (#16163) | ✅ 已完成 | [3626_3f14731e2](commits/3626_3f14731e2/analysis.md) |
| 3627 | `53f1f1a0a` | 2026-05-01 07:50:18 -0700 | drexler-sky | Spark 3.4, 3.5, 4.0: Migrate SparkWriteBuilder to SupportsOverwriteV2 (#16178) | ✅ 已完成 | [3627_53f1f1a0a](commits/3627_53f1f1a0a/analysis.md) |
| 3628 | `737f043f5` | 2026-05-01 17:35:02 -0700 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.56.0 to 0.56.1 (#16114) | ✅ 已完成 | [3628_737f043f5](commits/3628_737f043f5/analysis.md) |
| 3629 | `128f656c0` | 2026-05-02 11:31:47 -0700 | Kevin Liu | AWS: remove extra/staled LICENSE entry bundled by Parquet (#16180) | ✅ 已完成 | [3629_128f656c0](commits/3629_128f656c0/analysis.md) |
| 3630 | `76283899d` | 2026-05-02 16:34:51 -0600 | Prashant Singh | Core: Propagate server error message in failed remote scan planning responses (#16024) | ✅ 已完成 | [3630_76283899d](commits/3630_76283899d/analysis.md) |
| 3631 | `6d7ab339a` | 2026-05-02 20:49:19 -0700 | Prashant Singh | Core: Surface failed scan planning even when server omits error payload (#16197) | ✅ 已完成 | [3631_6d7ab339a](commits/3631_6d7ab339a/analysis.md) |
| 3632 | `1c1aaf038` | 2026-05-02 23:04:27 -0700 | dependabot[bot] | Build: Bump openapi-spec-validator from 0.8.4 to 0.8.5 (#16200) | ✅ 已完成 | [3632_1c1aaf038](commits/3632_1c1aaf038/analysis.md) |
| 3633 | `bd96c74ae` | 2026-05-02 23:04:38 -0700 | dependabot[bot] | Build: Bump testcontainers from 2.0.4 to 2.0.5 (#16201) | ✅ 已完成 | [3633_bd96c74ae](commits/3633_bd96c74ae/analysis.md) |
| 3634 | `d69d92ce0` | 2026-05-02 23:04:54 -0700 | dependabot[bot] | Build: Bump nessie from 0.107.4 to 0.107.5 (#16202) | ✅ 已完成 | [3634_d69d92ce0](commits/3634_d69d92ce0/analysis.md) |
| 3635 | `8537152cd` | 2026-05-03 08:06:02 -0700 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#16204) | ✅ 已完成 | [3635_8537152cd](commits/3635_8537152cd/analysis.md) |
| 3636 | `33e173c0d` | 2026-05-03 18:59:03 -0700 | Yuya Ebihara | Build: Bump software.amazon.awssdk:bom from 2.42.36 to 2.42.41 (#16206) | ✅ 已完成 | [3636_33e173c0d](commits/3636_33e173c0d/analysis.md) |
| 3637 | `334dd9553` | 2026-05-04 10:30:55 +0200 | Alexandre Dutra | Core, AWS: Adapt code to S3 signing endpoint promotion (#15451) | ✅ 已完成 | [3637_334dd9553](commits/3637_334dd9553/analysis.md) |
| 3638 | `f2e7a6567` | 2026-05-04 10:32:05 +0200 | Akshay Thorat | AWS, GCP: add Kryo round-trip regression test for refreshed storage credentials (#16112) | ✅ 已完成 | [3638_f2e7a6567](commits/3638_f2e7a6567/analysis.md) |
| 3639 | `3a98658ae` | 2026-05-04 16:37:33 +0200 | gaborkaszab | Docs: Move catalog properties to catalog section (#15848) | ✅ 已完成 | [3639_3a98658ae](commits/3639_3a98658ae/analysis.md) |
| 3640 | `54868576c` | 2026-05-04 17:51:24 +0200 | gaborkaszab | Docs: Document general REST catalog properties (#15871) | ✅ 已完成 | [3640_54868576c](commits/3640_54868576c/analysis.md) |
| 3641 | `2d5412573` | 2026-05-04 11:08:17 -0500 | milleniax | Spark: Support TimestampNTZ in SparkZOrderUDF (#15778) | ✅ 已完成 | [3641_2d5412573](commits/3641_2d5412573/analysis.md) |
| 3642 | `7830efec3` | 2026-05-05 15:04:34 +0200 | Kurtis Wright | Spark: Add unknown type support to Spark 3.4 and 3.5 (#16066) | ✅ 已完成 | [3642_7830efec3](commits/3642_7830efec3/analysis.md) |
| 3643 | `0841cdea9` | 2026-05-05 08:41:50 -0700 | Soumyajit Sahu | Sink connector crashes on timestamps with fractional seconds and colon-separated UTC offset (Fixes #15838) (#15839) | ✅ 已完成 | [3643_0841cdea9](commits/3643_0841cdea9/analysis.md) |
| 3644 | `2f6606a24` | 2026-05-05 18:38:22 +0200 | Swapna Marru | Flink: Backport: Dynamic sink options to be configurable in SQL (#16209) | ✅ 已完成 | [3644_2f6606a24](commits/3644_2f6606a24/analysis.md) |
| 3645 | `0011a85e4` | 2026-05-05 18:06:35 -0700 | drexler-sky | Spark: Migrate RollBackStageTable to use SupportsDeleteV2 (#16211) | ✅ 已完成 | [3645_0011a85e4](commits/3645_0011a85e4/analysis.md) |
| 3646 | `da5ffce9a` | 2026-05-05 19:55:43 -0700 | Neelesh Salian | Fix for vectorized builder variant handling (#16087) | ✅ 已完成 | [3646_da5ffce9a](commits/3646_da5ffce9a/analysis.md) |
| 3647 | `dcdeb27ed` | 2026-05-06 07:57:19 +0200 | Talat UYARER | Flink: Define Joda Time in libs.versions.toml file (#16191) | ✅ 已完成 | [3647_dcdeb27ed](commits/3647_dcdeb27ed/analysis.md) |
| 3648 | `680d850e9` | 2026-05-06 11:17:27 +0200 | Maximilian Michels | Flink: Do not ship optional flink-metrics-dropwizard dependency (#16155) | ✅ 已完成 | [3648_680d850e9](commits/3648_680d850e9/analysis.md) |
| 3649 | `0bae0503b` | 2026-05-06 11:22:23 +0200 | Maximilian Michels | Build: Correct actions/labeler version comment to v6.0.1 (#16225) | ✅ 已完成 | [3649_0bae0503b](commits/3649_0bae0503b/analysis.md) |
| 3650 | `ef077f458` | 2026-05-06 07:49:17 -0700 | sagib-sqream | Core: Fix JdbcCatalog & InMemoryCatalog to prevent dropping parent namespaces with children (#16061) | ✅ 已完成 | [3650_ef077f458](commits/3650_ef077f458/analysis.md) |
| 3651 | `b84b37f43` | 2026-05-06 10:09:39 -0600 | Hongyue/Steve Zhang | Core: Replace string-based schema projection with selection on field-id (#16184) | ✅ 已完成 | [3651_b84b37f43](commits/3651_b84b37f43/analysis.md) |
| 3652 | `d7cb79945` | 2026-05-06 09:32:43 -0700 | Kevin Liu | Flink: Backport removal of optional flink-metrics-dropwizard dependency to v2.0 and v1.20 (#16230) | ✅ 已完成 | [3652_d7cb79945](commits/3652_d7cb79945/analysis.md) |
| 3653 | `0d2707eaa` | 2026-05-06 09:41:29 -0700 | Manu Zhang | Docs: Add missing v3 data types to status page (#16228) | ✅ 已完成 | [3653_0d2707eaa](commits/3653_0d2707eaa/analysis.md) |
| 3654 | `b7ef9f1fa` | 2026-05-06 10:02:43 -0700 | Kevin Liu | CI: Use specific patch versions in workflow action comments (#16229) | ✅ 已完成 | [3654_b7ef9f1fa](commits/3654_b7ef9f1fa/analysis.md) |
| 3655 | `e2a119c82` | 2026-05-06 13:14:15 -0700 | Aihua Xu | Spark: Support writing shredded variant in Iceberg-Spark (#14297) | ✅ 已完成 | [3655_e2a119c82](commits/3655_e2a119c82/analysis.md) |
| 3656 | `400ba927d` | 2026-05-06 14:12:20 -0700 | Kevin Liu | AWS: Fix LICENSE/NOTICE compliance for aws-bundle (#16196) | ✅ 已完成 | [3656_400ba927d](commits/3656_400ba927d/analysis.md) |
| 3657 | `334269cd7` | 2026-05-06 14:12:46 -0700 | Kevin Liu | Azure: Fix LICENSE, NOTICE, and runtime-deps for azure-bundle (#16181) | ✅ 已完成 | [3657_334269cd7](commits/3657_334269cd7/analysis.md) |
| 3658 | `e4028bf6a` | 2026-05-06 14:13:07 -0700 | Kevin Liu | GCP: Fix LICENSE, NOTICE, and runtime-deps for gcp-bundle (#16182) | ✅ 已完成 | [3658_e4028bf6a](commits/3658_e4028bf6a/analysis.md) |
| 3659 | `86823e5a5` | 2026-05-06 14:13:29 -0700 | Kevin Liu | Spark: Fix LICENSE/NOTICE compliance for all versions of spark-runtime (v3.4, v3.5, v4.0, v4.1) (#16215) | ✅ 已完成 | [3659_86823e5a5](commits/3659_86823e5a5/analysis.md) |
| 3660 | `0f657edf1` | 2026-05-06 14:13:50 -0700 | Kevin Liu | Flink: Fix LICENSE/NOTICE compliance for all versions of flink-runtime (1.20, 2.0, 2.1) (#16216) | ✅ 已完成 | [3660_0f657edf1](commits/3660_0f657edf1/analysis.md) |
| 3661 | `05b2df1bd` | 2026-05-07 12:02:23 +0200 | Talat UYARER | Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration (#16183) | ✅ 已完成 | [3661_05b2df1bd](commits/3661_05b2df1bd/analysis.md) |
| 3662 | `153237b56` | 2026-05-07 13:30:23 +0200 | pvary | Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration to Flink 2.0 - missing changes (#16239) | ✅ 已完成 | [3662_153237b56](commits/3662_153237b56/analysis.md) |
| 3663 | `9ec1b933e` | 2026-05-07 16:48:58 +0200 | pvary | Spark: Backport support writing shredded variant in Iceberg-Spark (#16241) | ✅ 已完成 | [3663_9ec1b933e](commits/3663_9ec1b933e/analysis.md) |
| 3664 | `f767dad20` | 2026-05-07 16:49:25 +0200 | pvary | Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration to Flink 1.20 (#16240) | ✅ 已完成 | [3664_f767dad20](commits/3664_f767dad20/analysis.md) |
| 3665 | `17fc6da83` | 2026-05-07 13:32:30 -0700 | Oguzhan Unlu | API, Core: Handle 404 from /v1/config for missing warehouses (#16059) | ✅ 已完成 | [3665_17fc6da83](commits/3665_17fc6da83/analysis.md) |
| 3666 | `57b1211b7` | 2026-05-07 17:42:57 -0600 | Steven Zhen Wu | Spark: backport PR #15512 to v3.4, v3.5, v4.0 for WAP branch delete fix (#16245) | ✅ 已完成 | [3666_57b1211b7](commits/3666_57b1211b7/analysis.md) |
| 3667 | `77e7dbb24` | 2026-05-08 15:46:29 +0200 | GuoYu | ORC: Add _row_id and _last_updated_sequence_number raeder in Orc to support lineage (#15776) | ✅ 已完成 | [3667_77e7dbb24](commits/3667_77e7dbb24/analysis.md) |
| 3668 | `b7e65c902` | 2026-05-08 17:16:52 +0200 | Mukund Thakur | Core: Add test to validate we can't delete map value during schema evolution (#15767) | ✅ 已完成 | [3668_b7e65c902](commits/3668_b7e65c902/analysis.md) |
| 3669 | `299f7be39` | 2026-05-08 10:29:29 -0700 | gaborkaszab | OpenAPI, Core: Disambiguate the intent of REFS snapshot mode (#16252) | ✅ 已完成 | [3669_299f7be39](commits/3669_299f7be39/analysis.md) |
| 3670 | `4efbda3e4` | 2026-05-08 10:30:23 -0700 | Alex Miller | Add Oracle as an Iceberg vendor (#16251) | ✅ 已完成 | [3670_4efbda3e4](commits/3670_4efbda3e4/analysis.md) |
| 3671 | `b84d44689` | 2026-05-08 10:32:37 -0700 | Amogh Jahagirdar | Spec: Update formatting in tables to use material content tabs (#14656) | ✅ 已完成 | [3671_b84d44689](commits/3671_b84d44689/analysis.md) |
| 3672 | `9b8bde411` | 2026-05-08 20:29:35 +0200 | pvary | ORC: Backport add _row_id and _last_updated_sequence_number raeder in Orc to support lineage (#16256) | ✅ 已完成 | [3672_9b8bde411](commits/3672_9b8bde411/analysis.md) |
| 3673 | `e7a5a87f2` | 2026-05-08 11:39:59 -0700 | Yuya Ebihara | Azure: Avoid depending on KeyWrapAlgorithm in AzureProperties (#16186) | ✅ 已完成 | [3673_e7a5a87f2](commits/3673_e7a5a87f2/analysis.md) |
| 3674 | `fce250417` | 2026-05-09 10:15:11 -0700 | Manu Zhang | CI: Add PR title check workflow (#16101) | ✅ 已完成 | [3674_fce250417](commits/3674_fce250417/analysis.md) |
| 3675 | `1a8fa1e56` | 2026-05-09 10:19:34 -0700 | Rexwell Minnis | Docs: Document CATALOG_* env vars in iceberg-rest-fixture README (#16007) | ✅ 已完成 | [3675_1a8fa1e56](commits/3675_1a8fa1e56/analysis.md) |
| 3676 | `c14779697` | 2026-05-09 10:25:03 -0700 | Alex Miller | Docs: Update Oracle vendor description (#16261) | ✅ 已完成 | [3676_c14779697](commits/3676_c14779697/analysis.md) |
| 3677 | `1edde6c93` | 2026-05-09 23:56:16 -0700 | dependabot[bot] | Build: Bump jackson-bom from 2.21.2 to 2.21.3 (#16269) | ✅ 已完成 | [3677_1edde6c93](commits/3677_1edde6c93/analysis.md) |
| 3678 | `d9b6f00a9` | 2026-05-09 23:56:37 -0700 | dependabot[bot] | Build: Bump joda-time:joda-time from 2.5 to 2.14.2 (#16270) | ✅ 已完成 | [3678_d9b6f00a9](commits/3678_d9b6f00a9/analysis.md) |
| 3679 | `70ed5d956` | 2026-05-09 23:57:51 -0700 | dependabot[bot] | Build: Bump junit-platform from 1.14.3 to 1.14.4 (#16272) | ✅ 已完成 | [3679_70ed5d956](commits/3679_70ed5d956/analysis.md) |
| 3680 | `34511542e` | 2026-05-09 23:58:25 -0700 | dependabot[bot] | Build: Bump github/codeql-action from 4.35.2 to 4.35.3 (#16275) | ✅ 已完成 | [3680_34511542e](commits/3680_34511542e/analysis.md) |
| 3681 | `a6a0b8131` | 2026-05-10 11:20:09 -0700 | dependabot[bot] | Build: Bump junit from 5.14.3 to 5.14.4 (#16271) | ✅ 已完成 | [3681_a6a0b8131](commits/3681_a6a0b8131/analysis.md) |
| 3682 | `68bab74c7` | 2026-05-10 20:27:50 -0700 | Huaxin Gao | Build: Bump io.grpc:grpc-netty-shaded from 1.80.0 to 1.81.0 (#16277) | ✅ 已完成 | [3682_68bab74c7](commits/3682_68bab74c7/analysis.md) |
| 3683 | `6364aaae2` | 2026-05-11 12:36:36 +0200 | GuoYu | Data: Add TCK tests for Schema Evolution  in BaseFormatModelTests (#15843) | ✅ 已完成 | [3683_6364aaae2](commits/3683_6364aaae2/analysis.md) |
| 3684 | `0e0e79519` | 2026-05-11 13:57:38 +0200 | Huaxin Gao | Build: Bump org.openapitools:openapi-generator-gradle-plugin from 7.21.0 to 7.22.0 (#16278) | ✅ 已完成 | [3684_0e0e79519](commits/3684_0e0e79519/analysis.md) |
| 3685 | `20d5b3100` | 2026-05-11 18:15:58 +0200 | Robin Moffatt | Kafka Connect: Add Trivy CVE scan to CI (#15430) (#15430) | ✅ 已完成 | [3685_20d5b3100](commits/3685_20d5b3100/analysis.md) |
| 3686 | `f86a6727a` | 2026-05-11 10:30:10 -0700 | Huaxin Gao | Build: Bump software.amazon.awssdk:bom from 2.42.41 to 2.44.0 (#16279) | ✅ 已完成 | [3686_f86a6727a](commits/3686_f86a6727a/analysis.md) |
| 3687 | `3d682a3b6` | 2026-05-11 15:05:09 -0700 | Kevin Liu | INFRA: Expand Trivy CVE scan to all bundle and runtime modules (#16291) | ✅ 已完成 | [3687_3d682a3b6](commits/3687_3d682a3b6/analysis.md) |
| 3688 | `64ba246f3` | 2026-05-12 15:05:19 +0200 | Eduard Tudenhoefner | Core: Add partition to TrackedFile (#16253) | ✅ 已完成 | [3688_64ba246f3](commits/3688_64ba246f3/analysis.md) |
| 3689 | `575446ebf` | 2026-05-12 17:25:58 +0200 | Ajay Yadav | Spark: Fix dropped file length in SerializableFileIOWithSize (#16284) | ✅ 已完成 | [3689_575446ebf](commits/3689_575446ebf/analysis.md) |
| 3690 | `e5e2e8b61` | 2026-05-12 09:22:43 -0700 | Manu Zhang | Build: Improve PR title check pattern for multi-word prefixes and reverts (#16301) | ✅ 已完成 | [3690_e5e2e8b61](commits/3690_e5e2e8b61/analysis.md) |
| 3691 | `e1182e2c2` | 2026-05-12 09:32:39 -0700 | drexler-sky | Spark 4.1: Migrate SparkCopyOnWriteScan to SupportsRuntimeV2Filtering (#16295) | ✅ 已完成 | [3691_e1182e2c2](commits/3691_e1182e2c2/analysis.md) |
| 3692 | `b986d7309` | 2026-05-12 09:45:42 -0700 | Kevin Liu | Build: Fix transitive dependency CVEs across all distributions (#16290) | ✅ 已完成 | [3692_b986d7309](commits/3692_b986d7309/analysis.md) |
| 3693 | `4cebcdd6a` | 2026-05-12 13:00:00 -0700 | Fokko Driesprong | Build: Bump to Parquet 1.17.1 (#16257) | ✅ 已完成 | [3693_4cebcdd6a](commits/3693_4cebcdd6a/analysis.md) |
| 3694 | `42f3d01e6` | 2026-05-12 13:00:41 -0700 | Talat UYARER | Move all analyticscore references behind a AnalyticsCoreUtil class (#16258) | ✅ 已完成 | [3694_42f3d01e6](commits/3694_42f3d01e6/analysis.md) |
| 3695 | `947a7b791` | 2026-05-12 13:37:45 -0700 | Mukund Thakur | API: Remove unnecessary EOFException in FileRange constructor. (#15973) | ✅ 已完成 | [3695_947a7b791](commits/3695_947a7b791/analysis.md) |
| 3696 | `1cea23eda` | 2026-05-12 14:57:47 -0600 | Amogh Jahagirdar | Core: Fix row ID assignment for EXISTING entry during a manifest merge (#16263) | ✅ 已完成 | [3696_1cea23eda](commits/3696_1cea23eda/analysis.md) |
| 3697 | `a67655294` | 2026-05-12 19:02:53 -0700 | Kevin Liu | Spark 3.4: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#16306) | ✅ 已完成 | [3697_a67655294](commits/3697_a67655294/analysis.md) |
| 3698 | `062e822df` | 2026-05-12 19:03:32 -0700 | Kevin Liu | Spark 3.4: Pass FileIO on Spark's read path (#16307) | ✅ 已完成 | [3698_062e822df](commits/3698_062e822df/analysis.md) |
| 3699 | `bdbb37555` | 2026-05-12 19:04:00 -0700 | Kevin Liu | Spark 3.4: Set data file sort_order_id in manifest for writes from Spark (#16308) | ✅ 已完成 | [3699_bdbb37555](commits/3699_bdbb37555/analysis.md) |
| 3700 | `e57247b55` | 2026-05-12 20:29:53 -0700 | Kevin Liu | Spark 3.4: Backport Async Micro Batch Planner to 3.4 (#16311) | ✅ 已完成 | [3700_e57247b55](commits/3700_e57247b55/analysis.md) |
| 3701 | `11e582ba0` | 2026-05-13 08:47:57 -0700 | Vrishabh | Spark: Backport aggregate pushdown tests with NaN's (#16316) | ✅ 已完成 | [3701_11e582ba0](commits/3701_11e582ba0/analysis.md) |
| 3702 | `5bbe19f32` | 2026-05-13 18:59:09 +0200 | Talat UYARER | Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor (#16268) | ✅ 已完成 | [3702_5bbe19f32](commits/3702_5bbe19f32/analysis.md) |
| 3703 | `38c78c449` | 2026-05-13 12:19:31 -0700 | Talat UYARER | Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor for v2.0 (#16322) | ✅ 已完成 | [3703_38c78c449](commits/3703_38c78c449/analysis.md) |
| 3704 | `c72c2bdc4` | 2026-05-13 12:20:48 -0700 | Talat UYARER | Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor for 1.20 (#16323) | ✅ 已完成 | [3704_c72c2bdc4](commits/3704_c72c2bdc4/analysis.md) |
| 3705 | `721980dd9` | 2026-05-13 15:12:28 -0700 | Dong Wang | Spark: Disable min/max aggregation push down for string under any mode (#16320) | ✅ 已完成 | [3705_721980dd9](commits/3705_721980dd9/analysis.md) |
| 3706 | `bc8f264be` | 2026-05-13 23:30:33 -0700 | drexler-sky | Spark: Backport migrate SparkCopyOnWriteScan to SupportsRuntimeV2Filtering (#16303) | ✅ 已完成 | [3706_bc8f264be](commits/3706_bc8f264be/analysis.md) |
| 3707 | `1919ae65f` | 2026-05-14 14:11:01 +0200 | Joy Haldar | Flink: Support UUID type in Avro and Parquet readers and writers (#16097) | ✅ 已完成 | [3707_1919ae65f](commits/3707_1919ae65f/analysis.md) |
| 3708 | `6b8b57e2e` | 2026-05-14 14:44:16 +0200 | Anupam Yadav | Flink: Refresh table in ListMetadataFiles to prevent incorrect orphan file deletion (#16324) | ✅ 已完成 | [3708_6b8b57e2e](commits/3708_6b8b57e2e/analysis.md) |
| 3709 | `adfd4a4c8` | 2026-05-14 15:57:48 +0200 | Joy Haldar | Flink: Backport support UUID type in Avro and Parquet readers and writers (#16333) | ✅ 已完成 | [3709_adfd4a4c8](commits/3709_adfd4a4c8/analysis.md) |
| 3710 | `294f80dfd` | 2026-05-14 16:02:12 +0200 | GuoYu | Flink: Use native slot sharing group inheritance for maintenance tasks (#16329) | ✅ 已完成 | [3710_294f80dfd](commits/3710_294f80dfd/analysis.md) |
| 3711 | `87a7e4b13` | 2026-05-14 08:35:00 -0700 | Dong Wang | Spark: Also disable min/max aggregation push down for binary (#16328) | ✅ 已完成 | [3711_87a7e4b13](commits/3711_87a7e4b13/analysis.md) |
| 3712 | `bf340affb` | 2026-05-14 19:01:31 +0200 | GuoYu | Flink: Backport Use native slot sharing group inheritance for maintenance tasks to 2.0 and 1.20 (#16337) | ✅ 已完成 | [3712_bf340affb](commits/3712_bf340affb/analysis.md) |
| 3713 | `22f866687` | 2026-05-14 12:10:55 -0700 | Kevin Liu | Flink: Backport PR #16324 to v2.0 and v1.20 (#16338) | ✅ 已完成 | [3713_22f866687](commits/3713_22f866687/analysis.md) |
| 3714 | `8b45abce0` | 2026-05-14 16:57:07 -0700 | Talat UYARER | Close metrics reporter in RESTSessionCatalog and add test in CatalogTests (#16310) | ✅ 已完成 | [3714_8b45abce0](commits/3714_8b45abce0/analysis.md) |
| 3715 | `62fe817f7` | 2026-05-14 19:30:28 -0500 | Karuppayya | Spark 4.1: Add session configs for adaptive split sizing and parallelism (#16088) | ✅ 已完成 | [3715_62fe817f7](commits/3715_62fe817f7/analysis.md) |
| 3716 | `6976e020b` | 2026-05-14 19:35:48 -0700 | Karuppayya | Spark: Backport #16088 to Spark 3.4, 3.5, 4.0 (#16344) | ✅ 已完成 | [3716_6976e020b](commits/3716_6976e020b/analysis.md) |
| 3717 | `9789f852f` | 2026-05-16 08:37:26 -0700 | Kevin Liu | Build: Designate a single Gradle cache writer across CI workflows (#16356) | ✅ 已完成 | [3717_9789f852f](commits/3717_9789f852f/analysis.md) |
| 3718 | `089434ba2` | 2026-05-16 21:55:51 +0200 | Sachin Ranjalkar | Core: Fix ByteBufferInputStream.read() to return -1 at EOF (#16167) | ✅ 已完成 | [3718_089434ba2](commits/3718_089434ba2/analysis.md) |
| 3719 | `8caf7c2b6` | 2026-05-16 18:54:30 -0700 | Maksim Konstantinov | Website: remove duplicated entries from search (#16368) | ✅ 已完成 | [3719_8caf7c2b6](commits/3719_8caf7c2b6/analysis.md) |
| 3720 | `56ca272ce` | 2026-05-16 23:16:33 -0700 | dependabot[bot] | Build: Bump pymarkdownlnt from 0.9.36 to 0.9.37 (#16372) | ✅ 已完成 | [3720_56ca272ce](commits/3720_56ca272ce/analysis.md) |
| 3721 | `c8b35af9b` | 2026-05-16 23:16:51 -0700 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.56.1 to 0.57.0 (#16373) | ✅ 已完成 | [3721_c8b35af9b](commits/3721_c8b35af9b/analysis.md) |
| 3722 | `4f5033a7f` | 2026-05-16 23:17:05 -0700 | dependabot[bot] | Build: Bump jetty from 12.1.8 to 12.1.9 (#16374) | ✅ 已完成 | [3722_4f5033a7f](commits/3722_4f5033a7f/analysis.md) |
| 3723 | `74634cfab` | 2026-05-16 23:17:49 -0700 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.53.0.0 to 3.53.1.0 (#16379) | ✅ 已完成 | [3723_74634cfab](commits/3723_74634cfab/analysis.md) |
| 3724 | `6dfdcb091` | 2026-05-16 23:18:47 -0700 | dependabot[bot] | Build: Bump github/codeql-action from 4.35.1 to 4.35.4 (#16375) | ✅ 已完成 | [3724_6dfdcb091](commits/3724_6dfdcb091/analysis.md) |
| 3725 | `6a3f7827f` | 2026-05-17 10:03:08 -0700 | dependabot[bot] | Build: Bump actions/labeler from 6.0.1 to 6.1.0 (#16378) | ✅ 已完成 | [3725_6a3f7827f](commits/3725_6a3f7827f/analysis.md) |
| 3726 | `e1705f495` | 2026-05-18 10:13:27 +0200 | Joy Haldar | Core, Flink: Add UUID to DataTestBase SUPPORTED_PRIMITIVES (#16364) | ✅ 已完成 | [3726_e1705f495](commits/3726_e1705f495/analysis.md) |
| 3727 | `886ecab4d` | 2026-05-18 12:12:26 +0200 | liuliquan-marshal | Core: Fix NPE in RemoveOrphanFiles with prefix_listing for root table location(#16351) | ✅ 已完成 | [3727_886ecab4d](commits/3727_886ecab4d/analysis.md) |
| 3728 | `c08a8e4da` | 2026-05-18 12:55:09 +0200 | Manu Zhang | Core: Use ArrayList for manifest list materialization (#15640) | ✅ 已完成 | [3728_c08a8e4da](commits/3728_c08a8e4da/analysis.md) |
| 3729 | `e617d34df` | 2026-05-18 14:13:54 +0200 | Milan Stefanovic | Spec: Clarify non-default CRS conventions (#15834) | ✅ 已完成 | [3729_e617d34df](commits/3729_e617d34df/analysis.md) |
| 3730 | `ae4dfea6e` | 2026-05-18 14:16:09 +0200 | Vova Kolmakov | Flink: Add decimal write/read roundtrip test for FlinkParquetReaders (#16346) | ✅ 已完成 | [3730_ae4dfea6e](commits/3730_ae4dfea6e/analysis.md) |
| 3731 | `3e88117c6` | 2026-05-18 15:16:09 +0200 | Huaxin Gao | Build: Bump gradle-wrapper from 8.14.4 to 8.14.5 (#16381) | ✅ 已完成 | [3731_3e88117c6](commits/3731_3e88117c6/analysis.md) |
| 3732 | `4bdb2c258` | 2026-05-18 10:03:40 -0500 | Varun Lakhyani | Spark: Add compaction only benchmark - rewrite data files (#16219) | ✅ 已完成 | [3732_4bdb2c258](commits/3732_4bdb2c258/analysis.md) |
| 3733 | `1d8de4a96` | 2026-05-18 08:59:02 -0700 | Kevin Liu | Build: Speed up Spark CI with parallel test execution (#16357) | ✅ 已完成 | [3733_1d8de4a96](commits/3733_1d8de4a96/analysis.md) |
| 3734 | `3571629f3` | 2026-05-18 11:01:27 -0700 | Steven Zhen Wu | OpenAPI: Add CatalogObjectIdentifier schema (#16144) | ✅ 已完成 | [3734_3571629f3](commits/3734_3571629f3/analysis.md) |
| 3735 | `fab0d10e2` | 2026-05-18 12:44:17 -0700 | Maksim Konstantinov | Docs: switch default docs version from nightly to latest (#16398) | ✅ 已完成 | [3735_fab0d10e2](commits/3735_fab0d10e2/analysis.md) |
| 3736 | `2fe32aa8f` | 2026-05-18 22:44:30 +0200 | Anupam Yadav | Kafka Connect: Surface commit failures instead of silently swallowing them (#16237) | ✅ 已完成 | [3736_2fe32aa8f](commits/3736_2fe32aa8f/analysis.md) |
| 3737 | `cc9abe8c0` | 2026-05-18 16:50:57 -0700 | Amogh Jahagirdar | infra: add 1.10.2 to issue template (#16404) | ✅ 已完成 | [3737_cc9abe8c0](commits/3737_cc9abe8c0/analysis.md) |
| 3738 | `ce2bb9a5c` | 2026-05-18 16:52:11 -0700 | Amogh Jahagirdar | Doap: Update Doap to reference 1.10.2 (#16405) | ✅ 已完成 | [3738_ce2bb9a5c](commits/3738_ce2bb9a5c/analysis.md) |
| 3739 | `1802dbfad` | 2026-05-18 17:18:32 -0700 | Amogh Jahagirdar | Docs: Add release notes for 1.10.2 (#16406) | ✅ 已完成 | [3739_1802dbfad](commits/3739_1802dbfad/analysis.md) |
| 3740 | `4bece4106` | 2026-05-18 19:27:31 -0700 | Kevin Liu | Docs: Fix duplicate search results by moving versioned docs out of docs_dir (#16371) | ✅ 已完成 | [3740_4bece4106](commits/3740_4bece4106/analysis.md) |
| 3741 | `26a6f535a` | 2026-05-18 19:29:19 -0700 | Huaxin Gao | Build: Bump com.google.cloud:libraries-bom from 26.80.0 to 26.81.0 (#16382) | ✅ 已完成 | [3741_26a6f535a](commits/3741_26a6f535a/analysis.md) |
| 3742 | `7f1d90d30` | 2026-05-18 22:48:12 -0700 | Kevin Liu | Docs: add more to 1.10.2 release notes (#16410) | ✅ 已完成 | [3742_7f1d90d30](commits/3742_7f1d90d30/analysis.md) |
| 3743 | `8e049f02e` | 2026-05-18 23:53:31 -0700 | Yuya Ebihara | Core: Remove redundant string concatenation (#16409) | ✅ 已完成 | [3743_8e049f02e](commits/3743_8e049f02e/analysis.md) |
| 3744 | `0cd9e9b99` | 2026-05-18 23:55:56 -0700 | Aihua Xu | Build: Let revapi compare against 1.11.0 (#16412) | ✅ 已完成 | [3744_0cd9e9b99](commits/3744_0cd9e9b99/analysis.md) |
| 3745 | `5049675b4` | 2026-05-18 23:56:14 -0700 | Aihua Xu | infra: add 1.11.0 to issue template (#16413) | ✅ 已完成 | [3745_5049675b4](commits/3745_5049675b4/analysis.md) |
| 3746 | `5b1cc3c30` | 2026-05-19 00:18:11 -0700 | Aihua Xu | Doap: Update DOAP to reference 1.11.0 (#16415) | ✅ 已完成 | [3746_5b1cc3c30](commits/3746_5b1cc3c30/analysis.md) |
| 3747 | `6d8ebbb10` | 2026-05-19 15:02:05 +0200 | Han You | Flink: Allow setting slot sharing group for fine-grained resource management in DynamicSink (#16065) | ✅ 已完成 | [3747_6d8ebbb10](commits/3747_6d8ebbb10/analysis.md) |
| 3748 | `be94cb0d4` | 2026-05-19 17:07:23 -0700 | Kevin Liu | Build: Use major.minor versions in runtime-deps baselines (#16233) | ✅ 已完成 | [3748_be94cb0d4](commits/3748_be94cb0d4/analysis.md) |
| 3749 | `8e7ab3c88` | 2026-05-19 17:09:09 -0700 | Kevin Liu | CI: Make CVE scan blocking on PRs, informational on main (#16287) | ✅ 已完成 | [3749_8e7ab3c88](commits/3749_8e7ab3c88/analysis.md) |
| 3750 | `f825d09fe` | 2026-05-20 08:30:19 +0200 | Eduard Tudenhoefner | Spec: Add v4 content stats representation (#14234) | ✅ 已完成 | [3750_f825d09fe](commits/3750_f825d09fe/analysis.md) |
| 3751 | `463249a44` | 2026-05-20 10:55:18 +0200 | Kevin Liu | Spark: Remove Spark 3.4 support (#14122) | ✅ 已完成 | [3751_463249a44](commits/3751_463249a44/analysis.md) |
| 3752 | `684a1870d` | 2026-05-20 13:20:20 +0200 | Stepan Stepanishchev | Flink: Fix ALTER TABLE to add column to specific position (#16419) | ✅ 已完成 | [3752_684a1870d](commits/3752_684a1870d/analysis.md) |
| 3753 | `e2569ef89` | 2026-05-20 09:13:52 -0700 | Han You | Backport #16065 to Flink v2.0 and v1.20 (#16429) | ✅ 已完成 | [3753_e2569ef89](commits/3753_e2569ef89/analysis.md) |
| 3754 | `866d50e36` | 2026-05-20 11:53:19 -0700 | Aihua Xu | Docs: Add release notes for 1.11.0 (#16431) | ✅ 已完成 | [3754_866d50e36](commits/3754_866d50e36/analysis.md) |
| 3755 | `e3a4c6422` | 2026-05-20 15:54:20 -0700 | Xiening Dai | Arrow: Fix ClassCastException in vectorized reader on int-to-long pro… (#16343) | ✅ 已完成 | [3755_e3a4c6422](commits/3755_e3a4c6422/analysis.md) |
| 3756 | `90c2c4c53` | 2026-05-20 17:25:07 -0700 | Stepan Stepanishchev | Flink: Backport add column fix to flink v1.20, v2.0 (#16447) | ✅ 已完成 | [3756_90c2c4c53](commits/3756_90c2c4c53/analysis.md) |
| 3757 | `d0a4954d2` | 2026-05-20 18:08:37 -0700 | Kevin Liu | Site: Add version URL alias hook for docs (#16496) | ✅ 已完成 | [3757_d0a4954d2](commits/3757_d0a4954d2/analysis.md) |
| 3758 | `8a90699dc` | 2026-05-20 20:43:04 -0700 | Sreesh Maheshwar | Encrypting IO as a `DelegateFileIO` (#14876) | ✅ 已完成 | [3758_8a90699dc](commits/3758_8a90699dc/analysis.md) |
| 3759 | `693e8a787` | 2026-05-21 07:29:43 +0200 | Stepan Stepanishchev | Flink: Handle table comments in FlinkSQL (#16423) | ✅ 已完成 | [3759_693e8a787](commits/3759_693e8a787/analysis.md) |
| 3760 | `46ae3ad27` | 2026-05-21 07:44:27 +0200 | Vrishabh | Docs: Update information about metrics mode (#16391) | ✅ 已完成 | [3760_46ae3ad27](commits/3760_46ae3ad27/analysis.md) |
| 3761 | `2f05390e0` | 2026-05-21 09:34:00 +0200 | Yuya Ebihara | Build: Ban Preconditions.checkState with %d placeholder (#16407) | ✅ 已完成 | [3761_2f05390e0](commits/3761_2f05390e0/analysis.md) |
| 3762 | `56013b72d` | 2026-05-21 10:15:08 +0200 | Stepan Stepanishchev | Flink: Backport handle table comments in FlinkSQL (#16503) | ✅ 已完成 | [3762_56013b72d](commits/3762_56013b72d/analysis.md) |
| 3763 | `c4f835e7f` | 2026-05-21 13:57:13 +0200 | Yuya Ebihara | CI: Skip spotlessCheck in core-tests (#16505) | ✅ 已完成 | [3763_c4f835e7f](commits/3763_c4f835e7f/analysis.md) |
| 3764 | `2cce97af9` | 2026-05-21 16:16:49 +0200 | gaborkaszab | Build: Fix order in revapi.yml (#16511) | ✅ 已完成 | [3764_2cce97af9](commits/3764_2cce97af9/analysis.md) |
| 3765 | `7bb0fa243` | 2026-05-21 15:10:39 -0700 | Steven Zhen Wu | Docs: Drop manual deploy step from release instructions (#16495) | ✅ 已完成 | [3765_7bb0fa243](commits/3765_7bb0fa243/analysis.md) |
| 3766 | `10ba4eebf` | 2026-05-22 06:38:18 +0200 | GuoYu | Flink: Support writing shredded variant (#15596) | ✅ 已完成 | [3766_10ba4eebf](commits/3766_10ba4eebf/analysis.md) |
| 3767 | `f37a04b53` | 2026-05-22 10:03:42 +0200 | gaborkaszab | Core, Orc: Remove deprecated partition stats read functionality (#14998) | ✅ 已完成 | [3767_f37a04b53](commits/3767_f37a04b53/analysis.md) |
| 3768 | `26169f736` | 2026-05-22 11:39:44 +0200 | Vova Kolmakov | Kafka Connect: Fix ConcurrentModificationException in IcebergSinkConfig.tableConfig (#16438) | ✅ 已完成 | [3768_26169f736](commits/3768_26169f736/analysis.md) |
| 3769 | `ad9af8513` | 2026-05-22 10:46:18 -0700 | Xiening Dai | Add `.claude/` to .gitignore (#16533) | ✅ 已完成 | [3769_ad9af8513](commits/3769_ad9af8513/analysis.md) |
| 3770 | `41a95991a` | 2026-05-22 11:02:24 -0700 | Kevin Liu | ci: only run site-ci deploy job for apache/iceberg (#16535) | ✅ 已完成 | [3770_41a95991a](commits/3770_41a95991a/analysis.md) |
| 3771 | `fca74a08c` | 2026-05-22 11:43:23 -0700 | GuoYu | Spark: Backport Add _row_id and _last_updated_sequence_number raeder in Orc to support lineage (#16534) | ✅ 已完成 | [3771_fca74a08c](commits/3771_fca74a08c/analysis.md) |
| 3772 | `f23da211b` | 2026-05-22 15:52:38 -0500 | Russell Spitzer | Core, Parquet: Allow for Writing Parquet/Avro Manifests in V4 (#15634) | ✅ 已完成 | [3772_f23da211b](commits/3772_f23da211b/analysis.md) |
| 3773 | `4c767c14b` | 2026-05-22 23:59:36 +0200 | Anupam Yadav | Kafka Connect: Add end-to-end test for commit failure propagation (#16432) | ✅ 已完成 | [3773_4c767c14b](commits/3773_4c767c14b/analysis.md) |
| 3774 | `99e451acf` | 2026-05-22 15:14:10 -0700 | Yuya Ebihara | Core: Skip testAddManyFilesWithConsistentOrdering if WORKER_THREAD_POOL_SIZE < 3 (#16506) | ✅ 已完成 | [3774_99e451acf](commits/3774_99e451acf/analysis.md) |
| 3775 | `36ef88722` | 2026-05-22 18:08:30 -0700 | Daniel Weeks | [SPEC] Add relative paths to v4 spec (#15630) | ✅ 已完成 | [3775_36ef88722](commits/3775_36ef88722/analysis.md) |
| 3776 | `29ba0a14d` | 2026-05-22 21:16:44 -0700 | Anoop Johnson | Core: Add V4 location relativization utilities (#16174) | ✅ 已完成 | [3776_29ba0a14d](commits/3776_29ba0a14d/analysis.md) |
| 3777 | `988d6cd6f` | 2026-05-24 06:56:48 -0700 | Yong Zheng | Core: Fix SerializableTable.sortOrders() throwing on historical sort orders with dropped fields (#16519) (#16521) | ✅ 已完成 | [3777_988d6cd6f](commits/3777_988d6cd6f/analysis.md) |
| 3778 | `ffb29d3b1` | 2026-05-24 10:29:36 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.44.4 to 2.44.7 (#16555) | ✅ 已完成 | [3778_ffb29d3b1](commits/3778_ffb29d3b1/analysis.md) |
| 3779 | `95c86f886` | 2026-05-24 10:29:50 -0700 | dependabot[bot] | Build: Bump github/codeql-action from 4.35.4 to 4.35.5 (#16554) | ✅ 已完成 | [3779_95c86f886](commits/3779_95c86f886/analysis.md) |
| 3780 | `d9a12fa9c` | 2026-05-24 10:30:09 -0700 | dependabot[bot] | Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16552) | ✅ 已完成 | [3780_d9a12fa9c](commits/3780_d9a12fa9c/analysis.md) |
| 3781 | `2245a81af` | 2026-05-24 10:30:27 -0700 | dependabot[bot] | Build: Bump slf4j from 2.0.17 to 2.0.18 (#16553) | ✅ 已完成 | [3781_2245a81af](commits/3781_2245a81af/analysis.md) |
| 3782 | `400fff6bd` | 2026-05-24 10:30:42 -0700 | dependabot[bot] | Build: Bump zizmorcore/zizmor-action from 0.5.3 to 0.5.6 (#16550) | ✅ 已完成 | [3782_400fff6bd](commits/3782_400fff6bd/analysis.md) |
| 3783 | `75fcb8d9e` | 2026-05-24 10:31:02 -0700 | dependabot[bot] | Build: Bump com.google.cloud:libraries-bom from 26.81.0 to 26.83.0 (#16551) | ✅ 已完成 | [3783_75fcb8d9e](commits/3783_75fcb8d9e/analysis.md) |
| 3784 | `23c7f231a` | 2026-05-24 17:55:06 -0400 | Sung Yun | fix int96 timestamp offset in arrow dictionary decode (#16435) | ✅ 已完成 | [3784_23c7f231a](commits/3784_23c7f231a/analysis.md) |
| 3785 | `d3cb9e354` | 2026-05-25 07:45:59 +0200 | Yuya Ebihara | Core: Replace deprecated CloseableHttpClient.execute (#16149) | ✅ 已完成 | [3785_d3cb9e354](commits/3785_d3cb9e354/analysis.md) |
| 3786 | `f12e0cfd0` | 2026-05-25 08:37:02 +0200 | GuoYu | Data: Remove Flag FEATURE_META_ROW_LINEAGE in BaseFormatModelTests (#16529) | ✅ 已完成 | [3786_f12e0cfd0](commits/3786_f12e0cfd0/analysis.md) |
| 3787 | `d2cde2950` | 2026-05-26 10:04:15 -0700 | Ryan Blue | REST Spec: Add unregister table endpoint (#16400) | ✅ 已完成 | [3787_d2cde2950](commits/3787_d2cde2950/analysis.md) |
| 3788 | `8629e7c2d` | 2026-05-27 16:20:21 +0200 | Vova Kolmakov | Flink: Fix flaky TestMonitorSource.testStateRestore (#16548) | ✅ 已完成 | [3788_8629e7c2d](commits/3788_8629e7c2d/analysis.md) |
| 3789 | `176c6e30f` | 2026-05-27 11:34:19 -0700 | Steven Zhen Wu | Spark: Trim row-level test parameter rows from 6 to 3 (#16549) | ✅ 已完成 | [3789_176c6e30f](commits/3789_176c6e30f/analysis.md) |
| 3790 | `36d79e74b` | 2026-05-27 11:35:46 -0700 | Steven Zhen Wu | Spark: Trim TestStructuredStreamingRead3 parameter rows from 8 to 2 (#16559) | ✅ 已完成 | [3790_36d79e74b](commits/3790_36d79e74b/analysis.md) |
| 3791 | `ebd0100b3` | 2026-05-27 14:23:02 -0500 | Pratham Manja | Docs: Document adaptive split sizing configurations | ✅ 已完成 | [3791_ebd0100b3](commits/3791_ebd0100b3/analysis.md) |
| 3792 | `141e5be17` | 2026-05-27 21:12:50 -0600 | sanshi | Core: Fix flaky test by ensuring generateContentLength returns positive value (#16539) | ✅ 已完成 | [3792_141e5be17](commits/3792_141e5be17/analysis.md) |
| 3793 | `e88aa5568` | 2026-05-28 07:07:57 +0200 | Jordan Epstein | Flink: Honor schema identifier fields in dynamic-sink record routing (#16243) | ✅ 已完成 | [3793_e88aa5568](commits/3793_e88aa5568/analysis.md) |
| 3794 | `1a5b46cd0` | 2026-05-28 16:27:57 +0200 | SevenJ | Core: Cache PartitionData template in PartitionsTable to avoid rebuilding Avro schema per partition (#16208) | ✅ 已完成 | [3794_1a5b46cd0](commits/3794_1a5b46cd0/analysis.md) |
| 3795 | `e25cfe72d` | 2026-05-28 17:56:32 +0200 | Jordan Epstein | Flink: Backport honor schema identifier fields in dynamic-sink record routing (#16597) | ✅ 已完成 | [3795_e25cfe72d](commits/3795_e25cfe72d/analysis.md) |
| 3796 | `60d3fc01b` | 2026-05-28 14:03:49 -0400 | Sung Yun | Docs: Publish Iceberg security model (#16538) | ✅ 已完成 | [3796_60d3fc01b](commits/3796_60d3fc01b/analysis.md) |
| 3797 | `005294269` | 2026-05-29 14:22:01 +0200 | gaborkaszab | API, Core, Orc: Implement project() for partition statistics scan API (#16569) | ✅ 已完成 | [3797_005294269](commits/3797_005294269/analysis.md) |
| 3798 | `8f28a8691` | 2026-05-29 07:36:05 -0700 | Varun Lakhyani | extends compaction base class in sort compaction (#16593) | ✅ 已完成 | [3798_8f28a8691](commits/3798_8f28a8691/analysis.md) |
| 3799 | `f6740364c` | 2026-05-30 12:30:21 -0700 | Bharath Krishna | Core: Fix optionalOAuthParams dropped during non-exchange token refresh (#16022) (#16023) | ✅ 已完成 | [3799_f6740364c](commits/3799_f6740364c/analysis.md) |
| 3800 | `f6d281122` | 2026-05-30 15:35:58 -0700 | Steven Zhen Wu | Core: Validate non-string elements in JsonUtil.getStringArray (#16586) | ✅ 已完成 | [3800_f6d281122](commits/3800_f6d281122/analysis.md) |
| 3801 | `6a7370045` | 2026-05-30 15:37:46 -0700 | Huaxin Gao | Spark: Deprecate SparkFilters in favor of SparkV2Filters (#16616) | ✅ 已完成 | [3801_6a7370045](commits/3801_6a7370045/analysis.md) |
| 3802 | `05401437c` | 2026-05-30 22:52:02 -0700 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.13.Final to 4.2.14.Final (#16628) | ✅ 已完成 | [3802_05401437c](commits/3802_05401437c/analysis.md) |
| 3803 | `fa9b3d585` | 2026-05-30 22:53:50 -0700 | dependabot[bot] | Build: Bump org.immutables:value from 2.12.1 to 2.12.2 (#16636) | ✅ 已完成 | [3803_fa9b3d585](commits/3803_fa9b3d585/analysis.md) |
| 3804 | `d48629d79` | 2026-05-30 22:54:05 -0700 | dependabot[bot] | Build: Bump github/codeql-action from 4.35.5 to 4.36.0 (#16635) | ✅ 已完成 | [3804_d48629d79](commits/3804_d48629d79/analysis.md) |
| 3805 | `3b7b2d14d` | 2026-05-31 08:41:20 -0700 | dependabot[bot] | Build: Bump openapi-spec-validator from 0.8.5 to 0.9.0 (#16629) | ✅ 已完成 | [3805_3b7b2d14d](commits/3805_3b7b2d14d/analysis.md) |
| 3806 | `dd4d889a1` | 2026-05-31 08:41:47 -0700 | dependabot[bot] | Build: Bump actions/stale from 10.2.0 to 10.3.0 (#16630) | ✅ 已完成 | [3806_dd4d889a1](commits/3806_dd4d889a1/analysis.md) |
| 3807 | `80adb7120` | 2026-05-31 08:41:59 -0700 | dependabot[bot] | Build: Bump docker/build-push-action from 7.1.0 to 7.2.0 (#16631) | ✅ 已完成 | [3807_80adb7120](commits/3807_80adb7120/analysis.md) |
| 3808 | `16566de39` | 2026-05-31 08:42:12 -0700 | dependabot[bot] | Build: Bump docker/setup-buildx-action from 4.0.0 to 4.1.0 (#16632) | ✅ 已完成 | [3808_16566de39](commits/3808_16566de39/analysis.md) |
| 3809 | `7522b02aa` | 2026-05-31 08:42:28 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.44.7 to 2.44.12 (#16634) | ✅ 已完成 | [3809_7522b02aa](commits/3809_7522b02aa/analysis.md) |
| 3810 | `67cbc1aea` | 2026-05-31 18:03:55 -0700 | Yuya Ebihara | Build: Bump com.azure:azure-sdk-bom from 1.3.6 to 1.3.7 (#16637) | ✅ 已完成 | [3810_67cbc1aea](commits/3810_67cbc1aea/analysis.md) |
| 3811 | `c11404cb4` | 2026-06-01 14:03:02 +0200 | lrsb | Flink: Fix duplicate commits in DynamicCommitter when Flink jobId changes on restart (#16011) | ✅ 已完成 | [3811_c11404cb4](commits/3811_c11404cb4/analysis.md) |
| 3812 | `696b93d8d` | 2026-06-01 17:25:44 +0200 | Vova Kolmakov | Arrow: Fix truncation of decimals with precision larger than 18 (#16627) | ✅ 已完成 | [3812_696b93d8d](commits/3812_696b93d8d/analysis.md) |
| 3813 | `785723853` | 2026-06-01 09:26:46 -0700 | lrsb | Flink: Backport DynamicCommitter jobId fix to v1.20 and v2.0 (#16648) | ✅ 已完成 | [3813_785723853](commits/3813_785723853/analysis.md) |
| 3814 | `1172b1072` | 2026-06-01 11:49:53 -0700 | Manu Zhang | Spark 4.1: Upgrade to Spark 4.1.2 (#16365) | ✅ 已完成 | [3814_1172b1072](commits/3814_1172b1072/analysis.md) |
| 3815 | `687c58fc7` | 2026-06-01 13:27:40 -0700 | Alex Stephen | Core: v4 table metadata location should be optional (#16572) | ✅ 已完成 | [3815_687c58fc7](commits/3815_687c58fc7/analysis.md) |
| 3816 | `b88addf85` | 2026-06-01 14:54:49 -0600 | Hongyue/Steve Zhang | Core: Support pluggable executor service for manifest writing on SnapshotUpdate (#16108) | ✅ 已完成 | [3816_b88addf85](commits/3816_b88addf85/analysis.md) |
| 3817 | `26a57711d` | 2026-06-02 12:48:51 +0200 | Vova Kolmakov | Parquet: Fix timestamp_ns and timestamptz_ns predicate pushdown (#16619) | ✅ 已完成 | [3817_26a57711d](commits/3817_26a57711d/analysis.md) |
| 3818 | `f5349db39` | 2026-06-02 13:26:58 -0600 | Hao Jiang | Arrow: Fix vectorized reads of decimal columns with default values (#16501) | ✅ 已完成 | [3818_f5349db39](commits/3818_f5349db39/analysis.md) |
| 3819 | `c958fcf63` | 2026-06-02 14:24:42 -0700 | Neelesh Salian | iceberg go 0.6.0 release blog (#16649) | ✅ 已完成 | [3819_c958fcf63](commits/3819_c958fcf63/analysis.md) |
| 3820 | `c64640400` | 2026-06-03 11:29:41 -0700 | Anoop Johnson | Core: Refactor v4 struct builders to improve validation (#16408) | ✅ 已完成 | [3820_c64640400](commits/3820_c64640400/analysis.md) |
| 3821 | `c91025e13` | 2026-06-03 13:48:25 -0700 | gaborkaszab | ORC: Remove ORC tests for partition statistics (#16676) | ✅ 已完成 | [3821_c91025e13](commits/3821_c91025e13/analysis.md) |
| 3822 | `4b4c5b554` | 2026-06-03 15:27:13 -0700 | Steven Zhen Wu | Infra: Update collaborators list (#16678) | ✅ 已完成 | [3822_4b4c5b554](commits/3822_4b4c5b554/analysis.md) |
| 3823 | `5ec9bd090` | 2026-06-04 09:20:47 -0700 | Aihua Xu | Site: Add 1.11.0 release blog post (#16516) | ✅ 已完成 | [3823_5ec9bd090](commits/3823_5ec9bd090/analysis.md) |
| 3824 | `44de9895e` | 2026-06-04 15:04:09 -0700 | Steven Zhen Wu | API, Core: Add CatalogObjectIdentifier (#16160) | ✅ 已完成 | [3824_44de9895e](commits/3824_44de9895e/analysis.md) |
| 3825 | `c00669fde` | 2026-06-04 19:52:22 -0700 | drexler-sky | API, Spark 4.1: Add ignore_missing_files to migrate procedure (#16643) | ✅ 已完成 | [3825_c00669fde](commits/3825_c00669fde/analysis.md) |
| 3826 | `719776595` | 2026-06-05 12:41:52 +0200 | GuoYu | Data: Add TCK for Writer builder in FileFormat API (#16575) | ✅ 已完成 | [3826_719776595](commits/3826_719776595/analysis.md) |
| 3827 | `bead8dfab` | 2026-06-05 09:42:28 -0700 | drexler-sky | Spark 3.5, 4.0: Add ignore_missing_files to migrate procedure (#16684) | ✅ 已完成 | [3827_bead8dfab](commits/3827_bead8dfab/analysis.md) |
| 3828 | `9bbde2536` | 2026-06-05 16:41:32 -0700 | Vladislav Sidorovich | GCS, S3, ADLS: Handle EOF in inputStreams (#16055) | ✅ 已完成 | [3828_9bbde2536](commits/3828_9bbde2536/analysis.md) |
| 3829 | `4918866c0` | 2026-06-05 16:48:16 -0700 | Jiwon Park | Spark 4.1: Bind parameters in IcebergSparkSqlExtensionsParser (#16626) | ✅ 已完成 | [3829_4918866c0](commits/3829_4918866c0/analysis.md) |
| 3830 | `4057f59db` | 2026-06-05 16:56:12 -0700 | sanshi | Spark: Fix time-travel filter on renamed columns in distributed planning mode (#16523) | ✅ 已完成 | [3830_4057f59db](commits/3830_4057f59db/analysis.md) |
| 3831 | `95540ca16` | 2026-06-06 18:01:11 -0700 | Huaxin Gao | REST spec: add list/load function endpoints to OpenAPI spec (#15180) | ✅ 已完成 | [3831_95540ca16](commits/3831_95540ca16/analysis.md) |
| 3832 | `13182abb1` | 2026-06-06 18:14:39 -0700 | Yujiang Zhong | Core: Skip unnecessary manifest scans during expire snapshots metadata cleanup (#16691) | ✅ 已完成 | [3832_13182abb1](commits/3832_13182abb1/analysis.md) |
| 3833 | `e829aaf8e` | 2026-06-06 21:58:50 -0700 | Neelesh Salian | Parquet: Add opt-in uncompressed row group size tracking (#16327) | ✅ 已完成 | [3833_e829aaf8e](commits/3833_e829aaf8e/analysis.md) |
| 3834 | `44ecffa67` | 2026-06-07 09:40:44 -0700 | dependabot[bot] | Build: Bump jackson-bom from 2.21.3 to 2.21.4 (#16702) | ✅ 已完成 | [3834_44ecffa67](commits/3834_44ecffa67/analysis.md) |
| 3835 | `e8741716a` | 2026-06-07 09:41:01 -0700 | dependabot[bot] | Build: Bump nessie from 0.107.5 to 0.107.6 (#16703) | ✅ 已完成 | [3835_e8741716a](commits/3835_e8741716a/analysis.md) |
| 3836 | `0b042cf8b` | 2026-06-07 09:41:21 -0700 | dependabot[bot] | Build: Bump docker/setup-qemu-action from 4.0.0 to 4.1.0 (#16704) | ✅ 已完成 | [3836_0b042cf8b](commits/3836_0b042cf8b/analysis.md) |
| 3837 | `e94cfba4a` | 2026-06-07 09:41:40 -0700 | dependabot[bot] | Build: Bump com.gradleup.shadow:shadow-gradle-plugin (#16705) | ✅ 已完成 | [3837_e94cfba4a](commits/3837_e94cfba4a/analysis.md) |
| 3838 | `457854fb3` | 2026-06-07 09:42:30 -0700 | dependabot[bot] | Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16706) | ✅ 已完成 | [3838_457854fb3](commits/3838_457854fb3/analysis.md) |
| 3839 | `34e812e18` | 2026-06-08 13:31:16 +0200 | gaborkaszab | API, Core: Implement filter() for partition statistics scan API (#16582) | ✅ 已完成 | [3839_34e812e18](commits/3839_34e812e18/analysis.md) |
| 3840 | `1625f6b80` | 2026-06-08 13:34:54 +0200 | cenotee | Docs: Add Dataddo to the vendor list (#16443) | ✅ 已完成 | [3840_1625f6b80](commits/3840_1625f6b80/analysis.md) |
| 3841 | `d3bb5c9a8` | 2026-06-08 13:50:19 +0200 | Alex Stephen | Data: Add comprehensive data type tests to Format Model TCK (#15795) | ✅ 已完成 | [3841_d3bb5c9a8](commits/3841_d3bb5c9a8/analysis.md) |
| 3842 | `48727c907` | 2026-06-08 13:57:36 +0200 | Vova Kolmakov | Parquet: Pre-size row-group filter maps to the column count (#16723) | ✅ 已完成 | [3842_48727c907](commits/3842_48727c907/analysis.md) |
| 3843 | `d68124d98` | 2026-06-08 10:03:36 -0700 | Yuya Ebihara | Docs: Add Apache Iceberg Summit 2026 Playlist (#16712) | ✅ 已完成 | [3843_d68124d98](commits/3843_d68124d98/analysis.md) |
| 3844 | `d2de29044` | 2026-06-08 14:12:53 -0700 | Eduard Tudenhoefner | Core: Align content stats fields with latest Spec changes (#16439) | ✅ 已完成 | [3844_d2de29044](commits/3844_d2de29044/analysis.md) |
| 3845 | `e7e6256a4` | 2026-06-08 22:42:18 -0600 | Amogh Jahagirdar | Spark: Fix first row ID carry over for manifest rewrite (#16699) | ✅ 已完成 | [3845_e7e6256a4](commits/3845_e7e6256a4/analysis.md) |
| 3846 | `ae6ce1965` | 2026-06-08 22:46:48 -0600 | Manu Zhang | Core: Preserve DV encryption metadata in merges (#15911) | ✅ 已完成 | [3846_ae6ce1965](commits/3846_ae6ce1965/analysis.md) |
| 3847 | `6dca97059` | 2026-06-09 11:01:20 +0200 | Eduard Tudenhoefner | Core: Adjust calculations for reserved field IDs (#16441) | ✅ 已完成 | [3847_6dca97059](commits/3847_6dca97059/analysis.md) |
| 3848 | `4125f26d1` | 2026-06-09 12:29:38 +0200 | Chase Zhang | Flink: implement wakeup method to fix thread/memory leak (#16545) | ✅ 已完成 | [3848_4125f26d1](commits/3848_4125f26d1/analysis.md) |
| 3849 | `56cae8254` | 2026-06-09 09:29:16 -0700 | drexler-sky | API, Spark 4.1: Add ignore_missing_files to snapshot procedure (#16710) | ✅ 已完成 | [3849_56cae8254](commits/3849_56cae8254/analysis.md) |
| 3850 | `ca5368827` | 2026-06-09 18:29:30 +0200 | Vova Kolmakov | Parquet: Avoid intermediate BigInteger in int and long decimal readers (#16722) | ✅ 已完成 | [3850_ca5368827](commits/3850_ca5368827/analysis.md) |
| 3851 | `b299ebdab` | 2026-06-09 14:43:50 -0700 | Ryan Blue | Mumbling: Add draft Mumbling Bitmap spec (#16518) | ✅ 已完成 | [3851_b299ebdab](commits/3851_b299ebdab/analysis.md) |
| 3852 | `8d0aab700` | 2026-06-09 18:42:00 -0700 | Neelesh Salian | Build: Bump Netty pin to 4.2.15.Final (#16749) | ✅ 已完成 | [3852_8d0aab700](commits/3852_8d0aab700/analysis.md) |
| 3853 | `491fc36ad` | 2026-06-10 15:02:28 +0200 | Joy Haldar | Data: Add TCK coverage for reader default values (#16638) | ✅ 已完成 | [3853_491fc36ad](commits/3853_491fc36ad/analysis.md) |
| 3854 | `0440aa683` | 2026-06-10 16:26:37 +0200 | Chase Zhang | Flink: Backport implement wakeup method to fix thread/memory leak (#16745) | ✅ 已完成 | [3854_0440aa683](commits/3854_0440aa683/analysis.md) |
| 3855 | `9cc46bb54` | 2026-06-10 18:17:56 -0600 | Steven Zhen Wu | Docs: Add Javadoc guidance to AGENTS.md (#16764) | ✅ 已完成 | [3855_9cc46bb54](commits/3855_9cc46bb54/analysis.md) |
| 3856 | `d7d3bd5c3` | 2026-06-11 08:47:50 +0200 | Fei Wang | Core: Fix RESTMetricsReporter.report() blocking the calling thread (#16695) | ✅ 已完成 | [3856_d7d3bd5c3](commits/3856_d7d3bd5c3/analysis.md) |
| 3857 | `b471a1a0b` | 2026-06-11 16:12:20 +0200 | jackylee | Flink: Validate missing watermark column in column stats watermark extractor (#16774) | ✅ 已完成 | [3857_b471a1a0b](commits/3857_b471a1a0b/analysis.md) |
| 3858 | `48b4f2add` | 2026-06-11 09:38:39 -0700 | drexler-sky | Spark 3.5, 4.0: Add ignore_missing_files to snapshot procedure (#16754) | ✅ 已完成 | [3858_48b4f2add](commits/3858_48b4f2add/analysis.md) |
| 3859 | `fe91c9e36` | 2026-06-11 09:39:33 -0700 | Cheng Pan | Docs: Clarify Spark `spark.sql.adaptive.advisoryPartitionSizeInBytes` (#16721) | ✅ 已完成 | [3859_fe91c9e36](commits/3859_fe91c9e36/analysis.md) |
| 3860 | `dedb08e64` | 2026-06-11 13:48:45 -0600 | Ryan Blue | API, Core: Reuse VariantUtil methods through ByteBuffers (#16748) | ✅ 已完成 | [3860_dedb08e64](commits/3860_dedb08e64/analysis.md) |
| 3861 | `cc7fec9f2` | 2026-06-11 14:27:11 -0700 | Anoop Johnson | Core: Add EntryStatus.MODIFIED and TrackingBuilder status derivation (#16689) | ✅ 已完成 | [3861_cc7fec9f2](commits/3861_cc7fec9f2/analysis.md) |
| 3862 | `9c57bb543` | 2026-06-11 14:34:32 -0700 | Anoop Johnson | Core: Add adapters from TrackedFile to DataFile, DeleteFile (#16100) | ✅ 已完成 | [3862_9c57bb543](commits/3862_9c57bb543/analysis.md) |
| 3863 | `8ec055340` | 2026-06-11 16:45:14 -0700 | Xin Huang | API: Single-value binary serialization for geometry and geography (#16607) | ✅ 已完成 | [3863_8ec055340](commits/3863_8ec055340/analysis.md) |
| 3864 | `2a21bbc80` | 2026-06-11 19:40:37 -0700 | Manu Zhang | Spark 4.0: Upgrade to Spark 4.0.3 (#16717) | ✅ 已完成 | [3864_2a21bbc80](commits/3864_2a21bbc80/analysis.md) |
| 3865 | `40d3e653f` | 2026-06-11 23:02:14 -0700 | Vova Kolmakov | CI: Retry Trivy scanner image pull to absorb transient Docker Hub timeouts (#16660) | ✅ 已完成 | [3865_40d3e653f](commits/3865_40d3e653f/analysis.md) |
| 3866 | `20cba0dfc` | 2026-06-12 10:54:02 -0500 | Felix Schneider | Spark 3.5: Add  Spark REST_CATALOG_PURGE property to delegate DROP TABLE PURGE to REST catalogs (#15614) | ✅ 已完成 | [3866_20cba0dfc](commits/3866_20cba0dfc/analysis.md) |
| 3867 | `0c9a23e38` | 2026-06-12 10:01:06 -0700 | Huaxin Gao | Build: Bump datamodel-code-generator from 0.57.0 to 0.59.0 (#16708) | ✅ 已完成 | [3867_0c9a23e38](commits/3867_0c9a23e38/analysis.md) |
| 3868 | `70b903305` | 2026-06-12 10:01:58 -0700 | Huaxin Gao | Build: Bump software.amazon.awssdk:bom from 2.44.12 to 2.45.1 (#16709) | ✅ 已完成 | [3868_70b903305](commits/3868_70b903305/analysis.md) |
| 3869 | `40a532bdb` | 2026-06-12 17:15:10 -0700 | Andrei Tserakhau | Spec: clarify Avro encoding for day partition transform in manifests (#16446) | ✅ 已完成 | [3869_40a532bdb](commits/3869_40a532bdb/analysis.md) |
| 3870 | `51ee2cc0d` | 2026-06-12 20:29:33 -0700 | Rahul Shivu Mahadev | Core: Disallow setting main branch ref to a tag (#16753) | ✅ 已完成 | [3870_51ee2cc0d](commits/3870_51ee2cc0d/analysis.md) |
| 3871 | `bc8bf0988` | 2026-06-13 21:46:20 -0700 | dependabot[bot] | Build: Bump astral-sh/setup-uv from 8.1.0 to 8.2.0 (#16803) | ✅ 已完成 | [3871_bc8bf0988](commits/3871_bc8bf0988/analysis.md) |
| 3872 | `ffb7d24f6` | 2026-06-13 21:47:09 -0700 | Vova Kolmakov | Data: Skip equality-delete filter when there are no equality deletes (#16742) | ✅ 已完成 | [3872_ffb7d24f6](commits/3872_ffb7d24f6/analysis.md) |
| 3873 | `45e08836c` | 2026-06-14 00:06:19 -0700 | dependabot[bot] | Build: Bump jetty from 12.1.9 to 12.1.10 (#16813) | ✅ 已完成 | [3873_45e08836c](commits/3873_45e08836c/analysis.md) |
| 3874 | `df8dc25e6` | 2026-06-14 00:06:35 -0700 | dependabot[bot] | Build: Bump github/codeql-action from 4.36.0 to 4.36.2 (#16812) | ✅ 已完成 | [3874_df8dc25e6](commits/3874_df8dc25e6/analysis.md) |
| 3875 | `15118b4c1` | 2026-06-14 00:06:50 -0700 | dependabot[bot] | Build: Bump org.xerial:sqlite-jdbc from 3.53.1.0 to 3.53.2.0 (#16811) | ✅ 已完成 | [3875_15118b4c1](commits/3875_15118b4c1/analysis.md) |
| 3876 | `d48d871c2` | 2026-06-14 00:07:17 -0700 | dependabot[bot] | Build: Bump calcite from 1.41.0 to 1.42.0 (#16809) | ✅ 已完成 | [3876_d48d871c2](commits/3876_d48d871c2/analysis.md) |
| 3877 | `abacb72ed` | 2026-06-14 00:07:41 -0700 | dependabot[bot] | Build: Bump nessie from 0.107.6 to 0.107.9 (#16807) | ✅ 已完成 | [3877_abacb72ed](commits/3877_abacb72ed/analysis.md) |
| 3878 | `7c777f71d` | 2026-06-14 00:07:59 -0700 | dependabot[bot] | Build: Bump actions/checkout from 6.0.2 to 6.0.3 (#16806) | ✅ 已完成 | [3878_7c777f71d](commits/3878_7c777f71d/analysis.md) |
| 3879 | `00905ae0c` | 2026-06-14 00:08:29 -0700 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.59.0 to 0.60.0 (#16805) | ✅ 已完成 | [3879_00905ae0c](commits/3879_00905ae0c/analysis.md) |
| 3880 | `1f985f44e` | 2026-06-14 00:08:46 -0700 | dependabot[bot] | Build: Bump com.fasterxml.jackson.core:jackson-annotations (#16804) | ✅ 已完成 | [3880_1f985f44e](commits/3880_1f985f44e/analysis.md) |
| 3881 | `ba7413b50` | 2026-06-14 00:09:15 -0700 | dependabot[bot] | Build: Bump com.aliyun:tea from 1.4.1 to 1.4.2 (#16802) | ✅ 已完成 | [3881_ba7413b50](commits/3881_ba7413b50/analysis.md) |
| 3882 | `624f14c35` | 2026-06-14 08:53:52 -0700 | Yuya Ebihara | Build: Bump com.google.cloud.gcs.analytics:gcs-analytics-core (#16814) | ✅ 已完成 | [3882_624f14c35](commits/3882_624f14c35/analysis.md) |
| 3883 | `ded726615` | 2026-06-14 08:56:30 -0700 | Yuya Ebihara | Build: Bump jackson-bom from 2.21.4 to 2.22.0 (#16815) | ✅ 已完成 | [3883_ded726615](commits/3883_ded726615/analysis.md) |
| 3884 | `ce4d7d897` | 2026-06-14 09:12:23 -0700 | Yuya Ebihara | Build: Bump software.amazon.awssdk:bom from 2.45.1 to 2.46.5 (#16816) | ✅ 已完成 | [3884_ce4d7d897](commits/3884_ce4d7d897/analysis.md) |
| 3885 | `12b7f7de0` | 2026-06-15 11:06:52 +0800 | Junwang Zhao | Docs: add blog post for C++ 0.3.0 release (#16817) | ✅ 已完成 | [3885_12b7f7de0](commits/3885_12b7f7de0/analysis.md) |
| 3886 | `6ee0a3be3` | 2026-06-15 12:00:04 +0200 | Yuya Ebihara | Docs: Add guidelines for tests (#16784) | ✅ 已完成 | [3886_6ee0a3be3](commits/3886_6ee0a3be3/analysis.md) |
| 3887 | `2f244c0bc` | 2026-06-15 09:38:42 -0700 | Sebastian Baunsgaard | Spark: Cap RandomData collection size to speed up nested tests (#16696) | ✅ 已完成 | [3887_2f244c0bc](commits/3887_2f244c0bc/analysis.md) |
| 3888 | `f5b206167` | 2026-06-15 20:41:50 -0700 | Hongyue/Steve Zhang | Core: extract parallel manifest write logic out of SnapshotProducer (#16730) | ✅ 已完成 | [3888_f5b206167](commits/3888_f5b206167/analysis.md) |
| 3889 | `69063fb16` | 2026-06-16 13:51:39 +0200 | Sebastian Baunsgaard | Spark: Reduce TestSparkDataFile row count to speed up partitioned cases (#16821) | ✅ 已完成 | [3889_69063fb16](commits/3889_69063fb16/analysis.md) |
| 3890 | `85ffa1984` | 2026-06-16 14:00:47 -0700 | Neelesh Salian | Parquet: Variant shredding follow-ups from PR #14297 (#16818) | ✅ 已完成 | [3890_85ffa1984](commits/3890_85ffa1984/analysis.md) |
| 3891 | `bfa2e4867` | 2026-06-16 16:43:08 -0700 | Maximilian Michels | Flink: Add data model and key serialization for equality delete conversion (#16831) | ✅ 已完成 | [3891_bfa2e4867](commits/3891_bfa2e4867/analysis.md) |
| 3892 | `154fc84e0` | 2026-06-17 11:50:56 +0200 | Manu Zhang | Spark: Return session catalog views (#16845) | ✅ 已完成 | [3892_154fc84e0](commits/3892_154fc84e0/analysis.md) |
| 3893 | `e5151f35a` | 2026-06-17 18:45:42 +0200 | Sebastian Baunsgaard | Spark: Spark tests cache rewrite input (#16740) | ✅ 已完成 | [3893_e5151f35a](commits/3893_e5151f35a/analysis.md) |
| 3894 | `f1d8c9bc6` | 2026-06-17 10:02:45 -0700 | Maximilian Michels | Flink: Backport: Add data model and key serialization for equality delete conversion (#16831) (#16842) | ✅ 已完成 | [3894_f1d8c9bc6](commits/3894_f1d8c9bc6/analysis.md) |
| 3895 | `9d9937397` | 2026-06-17 10:17:51 -0700 | Kevin Liu | CI: Limit CVE scan runs to relevant changes (#16513) | ✅ 已完成 | [3895_9d9937397](commits/3895_9d9937397/analysis.md) |
| 3896 | `bbf57bc77` | 2026-06-17 10:40:37 -0700 | GuoYu | Data: Improve format model test coverage and diagnostics (#16832) | ✅ 已完成 | [3896_bbf57bc77](commits/3896_bbf57bc77/analysis.md) |
| 3897 | `de4f1ab1a` | 2026-06-17 19:48:16 +0200 | Anupam Yadav | Kafka Connect: Add metric for partial commit failures (#16433) | ✅ 已完成 | [3897_de4f1ab1a](commits/3897_de4f1ab1a/analysis.md) |
| 3898 | `a165b1c15` | 2026-06-17 19:49:30 +0200 | Henry Haiying Cai | Kafka Connect: Make CommitState.isCommitReady() O(1) (#16453) | ✅ 已完成 | [3898_a165b1c15](commits/3898_a165b1c15/analysis.md) |
| 3899 | `ccfec2b77` | 2026-06-17 13:20:45 -0700 | Maximilian Michels | Flink: Add equality delete conversion operators (#16844) | ✅ 已完成 | [3899_ccfec2b77](commits/3899_ccfec2b77/analysis.md) |
| 3900 | `2f1e3d0de` | 2026-06-17 15:34:32 -0700 | Rahul Shivu Mahadev | Core: Cache manifest list files in manifest content cache (#16762) | ✅ 已完成 | [3900_2f1e3d0de](commits/3900_2f1e3d0de/analysis.md) |
| 3901 | `829e29afe` | 2026-06-17 20:10:57 -0700 | Kevin Liu | docs: add a "AI-Generated PR Disclosure" section to AGENTS.md (#15758) | ✅ 已完成 | [3901_829e29afe](commits/3901_829e29afe/analysis.md) |
| 3902 | `8924046f7` | 2026-06-17 22:09:59 -0600 | gaborkaszab | Core: Add writer_format_version field to TrackedFile (#16688) | ✅ 已完成 | [3902_8924046f7](commits/3902_8924046f7/analysis.md) |
| 3903 | `54ea9a04d` | 2026-06-18 17:25:56 +0200 | Vova Kolmakov | Kafka Connect: Pre-size collections in RecordConverter list/map conversion (#16657) | ✅ 已完成 | [3903_54ea9a04d](commits/3903_54ea9a04d/analysis.md) |
| 3904 | `04728636d` | 2026-06-18 14:54:00 -0700 | Maximilian Michels | Flink: Backport: Add equality delete conversion operators (#16844) (#16857) | ✅ 已完成 | [3904_04728636d](commits/3904_04728636d/analysis.md) |
| 3905 | `a3a755897` | 2026-06-18 19:52:43 -0700 | Maximilian Michels | Flink: Add equality delete conversion DV resolution and writing (#16858) | ✅ 已完成 | [3905_a3a755897](commits/3905_a3a755897/analysis.md) |
| 3906 | `0ddbeead6` | 2026-06-19 07:11:01 +0200 | Mateus Aubin | Core, AWS, GCP, Dell, Hive: Fix FileIO leaks and standardize close() across Catalog implementations (#16862) | ✅ 已完成 | [3906_0ddbeead6](commits/3906_0ddbeead6/analysis.md) |
| 3907 | `e07782e3c` | 2026-06-19 08:59:32 +0200 | Alex Sorokoumov | Core: Parquet per column dictionary encoding (#16713) | ✅ 已完成 | [3907_e07782e3c](commits/3907_e07782e3c/analysis.md) |
| 3908 | `40175bf63` | 2026-06-19 05:53:18 -0700 | Fezinvirtual | Build: Remove duplicate jackson.databind declaration (#16879) | ✅ 已完成 | [3908_40175bf63](commits/3908_40175bf63/analysis.md) |
| 3909 | `722a0735c` | 2026-06-19 08:24:02 -0700 | Maximilian Michels | Flink: Backport: Add equality delete conversion DV resolution and writing (#16858) (#16869) | ✅ 已完成 | [3909_722a0735c](commits/3909_722a0735c/analysis.md) |
| 3910 | `41c8ee43b` | 2026-06-19 10:44:02 -0700 | Kevin Liu | Docs: Clarify geography type serialization (#16799) | ✅ 已完成 | [3910_41c8ee43b](commits/3910_41c8ee43b/analysis.md) |
| 3911 | `8d1f973ba` | 2026-06-19 22:45:17 -0700 | Maximilian Michels | Flink: Add equality delete conversion committer (#16874) | ✅ 已完成 | [3911_8d1f973ba](commits/3911_8d1f973ba/analysis.md) |
| 3912 | `2a2d5c0a6` | 2026-06-20 11:19:25 -0700 | Maximilian Michels | Flink: Backport: Add equality delete conversion committer (#16874) (#16888) | ✅ 已完成 | [3912_2a2d5c0a6](commits/3912_2a2d5c0a6/analysis.md) |
| 3913 | `9bf1b2515` | 2026-06-20 13:15:02 -0700 | Eunbin Son | Aliyun: Pass known file length through OSSFileIO.newInputFile (#16870) | ✅ 已完成 | [3913_9bf1b2515](commits/3913_9bf1b2515/analysis.md) |
| 3914 | `56b1e1970` | 2026-06-20 13:27:26 -0700 | Neelesh Salian | Parquet: Fix variant metrics crash when value column has no stats (#16585) | ✅ 已完成 | [3914_56b1e1970](commits/3914_56b1e1970/analysis.md) |
| 3915 | `55e025f0c` | 2026-06-21 00:05:30 -0700 | dependabot[bot] | Build: Bump com.google.cloud.gcs.analytics:gcs-analytics-core (#16904) | ✅ 已完成 | [3915_55e025f0c](commits/3915_55e025f0c/analysis.md) |
| 3916 | `e4d49808f` | 2026-06-21 00:05:54 -0700 | dependabot[bot] | Build: Bump org.openapitools:openapi-generator-gradle-plugin (#16903) | ✅ 已完成 | [3916_e4d49808f](commits/3916_e4d49808f/analysis.md) |
| 3917 | `d0a05b0f8` | 2026-06-21 00:06:15 -0700 | dependabot[bot] | Build: Bump io.grpc:grpc-netty-shaded from 1.81.0 to 1.82.0 (#16902) | ✅ 已完成 | [3917_d0a05b0f8](commits/3917_d0a05b0f8/analysis.md) |
| 3918 | `926368f15` | 2026-06-21 00:06:38 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.46.5 to 2.46.10 (#16901) | ✅ 已完成 | [3918_926368f15](commits/3918_926368f15/analysis.md) |
| 3919 | `ebcb902f5` | 2026-06-21 00:06:59 -0700 | dependabot[bot] | Build: Bump com.google.errorprone:error_prone_annotations (#16900) | ✅ 已完成 | [3919_ebcb902f5](commits/3919_ebcb902f5/analysis.md) |
| 3920 | `72c226afe` | 2026-06-21 00:07:20 -0700 | dependabot[bot] | Build: Bump gradle/actions from 5.0.2 to 6.2.0 (#16899) | ✅ 已完成 | [3920_72c226afe](commits/3920_72c226afe/analysis.md) |
| 3921 | `afedbb1d1` | 2026-06-21 00:08:09 -0700 | dependabot[bot] | Build: Bump nessie from 0.107.9 to 0.108.0 (#16897) | ✅ 已完成 | [3921_afedbb1d1](commits/3921_afedbb1d1/analysis.md) |
| 3922 | `d5c86d616` | 2026-06-21 00:08:34 -0700 | dependabot[bot] | Build: Bump pymarkdownlnt from 0.9.37 to 0.9.38 (#16895) | ✅ 已完成 | [3922_d5c86d616](commits/3922_d5c86d616/analysis.md) |
| 3923 | `b0977bbfc` | 2026-06-21 10:26:23 -0700 | Yuya Ebihara | Build: Bump datamodel-code-generator from 0.60.0 to 0.63.0 (#16907) | ✅ 已完成 | [3923_b0977bbfc](commits/3923_b0977bbfc/analysis.md) |
| 3924 | `0b3091937` | 2026-06-21 11:09:59 -0700 | Manu Zhang | Docs: Clarify test method naming guidance (#16866) | ✅ 已完成 | [3924_0b3091937](commits/3924_0b3091937/analysis.md) |
| 3925 | `fa072cc6b` | 2026-06-22 10:48:20 +0200 | Eunbin Son | Spark 4.1: Throw on unsupported complex types in SparkValueConverter (#16924) | ✅ 已完成 | [3925_fa072cc6b](commits/3925_fa072cc6b/analysis.md) |
| 3926 | `fb6bb97e3` | 2026-06-22 15:23:27 +0200 | Yuya Ebihara | Spark 3.5, 4.0: Throw on unsupported complex types in SparkValueConverter (#16925) | ✅ 已完成 | [3926_fb6bb97e3](commits/3926_fb6bb97e3/analysis.md) |
| 3927 | `902ed1b80` | 2026-06-22 09:23:51 -0600 | Andrei Tserakhau | Revert "Spark: Spark tests cache rewrite input (#16740)" (#16912) | ✅ 已完成 | [3927_902ed1b80](commits/3927_902ed1b80/analysis.md) |
| 3928 | `02631cdae` | 2026-06-22 13:53:22 -0700 | Manu Zhang | CI: Check ASF action allowlist on every PR (#16926) | ✅ 已完成 | [3928_02631cdae](commits/3928_02631cdae/analysis.md) |
| 3929 | `ac874e80b` | 2026-06-23 00:13:29 +0200 | Thomas Thornton | Kafka Connect: evolve table schema when record schema is updated but value is null (#16826) | ✅ 已完成 | [3929_ac874e80b](commits/3929_ac874e80b/analysis.md) |
| 3930 | `7c13104c8` | 2026-06-22 15:17:15 -0700 | gaborkaszab | Core: Introduce builder for TrackedFile (#16769) | ✅ 已完成 | [3930_7c13104c8](commits/3930_7c13104c8/analysis.md) |
| 3931 | `ef07755dd` | 2026-06-23 17:28:38 +0200 | Eunbin Son | ORC: Fix lower/upper bounds for timestamp_ns columns in OrcMetrics (#16922) | ✅ 已完成 | [3931_ef07755dd](commits/3931_ef07755dd/analysis.md) |
| 3932 | `d7612ae7b` | 2026-06-23 18:10:20 +0200 | GuoYu | Data: Add TCK for Encrypt in FileFormat API (#16724) | ✅ 已完成 | [3932_d7612ae7b](commits/3932_d7612ae7b/analysis.md) |
| 3933 | `df6ea0820` | 2026-06-23 18:24:38 +0200 | Aleksandr Efimov | Data: Add metrics reporter to generic scan builder (#16664) | ✅ 已完成 | [3933_df6ea0820](commits/3933_df6ea0820/analysis.md) |
| 3934 | `f07b404b0` | 2026-06-23 17:01:21 -0700 | Xin Huang | Parquet: Skip geo footer bounds (#16850) | ✅ 已完成 | [3934_f07b404b0](commits/3934_f07b404b0/analysis.md) |
| 3935 | `47148f380` | 2026-06-23 17:43:42 -0700 | Wyatt H | AWS: Handle duplicate column names in IcebergToGlueConverter comment map (#16853) | ✅ 已完成 | [3935_47148f380](commits/3935_47148f380/analysis.md) |
| 3936 | `6bef83a6b` | 2026-06-23 21:49:30 -0700 | Maximilian Michels | Flink: Add equality delete conversion planner (#16889) | ✅ 已完成 | [3936_6bef83a6b](commits/3936_6bef83a6b/analysis.md) |
| 3937 | `aa4abedd2` | 2026-06-23 22:12:45 -0700 | Yuya Ebihara | Build: Bump com.google.cloud:libraries-bom from 26.83.0 to 26.84.0 (#16906) | ✅ 已完成 | [3937_aa4abedd2](commits/3937_aa4abedd2/analysis.md) |
| 3938 | `534c3fd94` | 2026-06-24 08:54:00 +0200 | Neelesh Salian | Core: Migrate switch statements to switch expressions (#16881) | ✅ 已完成 | [3938_534c3fd94](commits/3938_534c3fd94/analysis.md) |
| 3939 | `0474d660b` | 2026-06-24 13:12:24 +0200 | Yujiang Zhong | Flink: Fix file offset mismatch in DataIterator.seek() when files are skipped (#16929) | ✅ 已完成 | [3939_0474d660b](commits/3939_0474d660b/analysis.md) |
| 3940 | `729aead0f` | 2026-06-24 13:49:25 +0200 | Vova Kolmakov | ORC: Remove unused conf field from OrcFileAppender (#16345) | ✅ 已完成 | [3940_729aead0f](commits/3940_729aead0f/analysis.md) |
| 3941 | `d5c427b93` | 2026-06-24 08:21:01 -0700 | Maximilian Michels | Flink: Backport: Add equality delete conversion planner (#16889) (#16944) | ✅ 已完成 | [3941_d5c427b93](commits/3941_d5c427b93/analysis.md) |
| 3942 | `6174dfa1e` | 2026-06-24 10:22:26 -0700 | Eduard Tudenhoefner | Revert "Core: Migrate switch statements to switch expressions (#16881)" (#16953) | ✅ 已完成 | [3942_6174dfa1e](commits/3942_6174dfa1e/analysis.md) |
| 3943 | `1ba42d46e` | 2026-06-24 14:40:46 -0700 | Thomas Powell | Remove encryption key from keysById first to avoid removeIf (#16860) | ✅ 已完成 | [3943_1ba42d46e](commits/3943_1ba42d46e/analysis.md) |
| 3944 | `d3daeef03` | 2026-06-25 00:46:28 +0200 | Eduard Tudenhoefner | Build: Align Jackson versions to fix CVE (#16954) | ✅ 已完成 | [3944_d3daeef03](commits/3944_d3daeef03/analysis.md) |
| 3945 | `2a6c556c0` | 2026-06-24 16:26:58 -0700 | gaborkaszab | Core: Rename TrackedFile writer_format_version to format_version (#16952) | ✅ 已完成 | [3945_2a6c556c0](commits/3945_2a6c556c0/analysis.md) |
| 3946 | `b27930e18` | 2026-06-24 22:06:30 -0700 | Swapna Marru | Flink: SQL: Pass only white-listed Catalog properties for Table LIKE  (#16728) | ✅ 已完成 | [3946_b27930e18](commits/3946_b27930e18/analysis.md) |
| 3947 | `3c604fb60` | 2026-06-25 10:24:34 +0200 | Thomas Thornton | Kafka Connect: Fix avro schema conversion for UUID (#16828) | ✅ 已完成 | [3947_3c604fb60](commits/3947_3c604fb60/analysis.md) |
| 3948 | `33bd1019a` | 2026-06-25 13:27:38 +0200 | Robin Moffatt | Flink: Improve error message for unsupported table kinds in createTable (#16079) | ✅ 已完成 | [3948_33bd1019a](commits/3948_33bd1019a/analysis.md) |
| 3949 | `d1f789e51` | 2026-06-25 14:09:01 +0200 | Yujiang Zhong | Flink: Backport fix file offset mismatch in DataIterator.seek() when files are skipped (#16950) | ✅ 已完成 | [3949_d1f789e51](commits/3949_d1f789e51/analysis.md) |
| 3950 | `b99389ba8` | 2026-06-25 15:34:52 +0200 | gaborkaszab | Core: Set scan planning mode when initializing client (#15903) | ✅ 已完成 | [3950_b99389ba8](commits/3950_b99389ba8/analysis.md) |
| 3951 | `6c5f9f242` | 2026-06-25 13:43:41 -0700 | Maximilian Michels | Flink: Add equality delete conversion API and integration tests (#16948) | ✅ 已完成 | [3951_6c5f9f242](commits/3951_6c5f9f242/analysis.md) |
| 3952 | `cfebc8ed1` | 2026-06-25 13:46:20 -0700 | Robin Moffatt | Flink: Backport improved createTable error message to 1.20 and 2.0 (#16959) | ✅ 已完成 | [3952_cfebc8ed1](commits/3952_cfebc8ed1/analysis.md) |
| 3953 | `66150ef44` | 2026-06-26 07:36:29 +0200 | Swapna Marru | Flink: Backport Pass only white-listed Catalog properties to 1.20,2.0 (#16966) | ✅ 已完成 | [3953_66150ef44](commits/3953_66150ef44/analysis.md) |
| 3954 | `7269fc175` | 2026-06-26 07:58:46 +0200 | Maximilian Michels | Flink: Backport: Add equality delete conversion API and integration tests (#16969) | ✅ 已完成 | [3954_7269fc175](commits/3954_7269fc175/analysis.md) |
| 3955 | `4af3ebee5` | 2026-06-26 13:05:18 -0700 | Sejal Gupta | Build: Support toggling log levels in iceberg-rest-fixture Docker image (#16725) | ✅ 已完成 | [3955_4af3ebee5](commits/3955_4af3ebee5/analysis.md) |
| 3956 | `8e46f4912` | 2026-06-26 16:43:56 -0700 | Xin Huang | API, Parquet: Map geometry and geography to Parquet logical types (#16765) | ✅ 已完成 | [3956_8e46f4912](commits/3956_8e46f4912/analysis.md) |
| 3957 | `874e4096e` | 2026-06-27 14:50:32 -0600 | Matt Butrovich | Core, Spark: Ensure correct delete file sizes in rewrite table action (#15470) | ✅ 已完成 | [3957_874e4096e](commits/3957_874e4096e/analysis.md) |
| 3958 | `9ca2a4b02` | 2026-06-27 21:27:48 -0700 | Maximilian Michels | Flink: Fix monitor source rate limit for sub-second intervals (#16979) | ✅ 已完成 | [3958_9ca2a4b02](commits/3958_9ca2a4b02/analysis.md) |
| 3959 | `ff1108756` | 2026-06-27 22:07:27 -0700 | dependabot[bot] | Build: Bump org.codehaus.jettison:jettison from 1.5.5 to 1.5.6 (#16988) | ✅ 已完成 | [3959_ff1108756](commits/3959_ff1108756/analysis.md) |
| 3960 | `45975ec15` | 2026-06-27 23:48:30 -0700 | dependabot[bot] | Build: Bump actions/checkout from 6.0.3 to 7.0.0 (#16986) | ✅ 已完成 | [3960_45975ec15](commits/3960_45975ec15/analysis.md) |
| 3961 | `ce92c469b` | 2026-06-27 23:48:47 -0700 | dependabot[bot] | Build: Bump actions/setup-java from 5.2.0 to 5.3.0 (#16991) | ✅ 已完成 | [3961_ce92c469b](commits/3961_ce92c469b/analysis.md) |
| 3962 | `839b22647` | 2026-06-27 23:49:07 -0700 | dependabot[bot] | Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#16990) | ✅ 已完成 | [3962_839b22647](commits/3962_839b22647/analysis.md) |
| 3963 | `958864156` | 2026-06-28 08:24:30 -0700 | Maximilian Michels | Flink: Backport: Fix monitor source rate limit for sub-second intervals (#16979) (#16992) | ✅ 已完成 | [3963_958864156](commits/3963_958864156/analysis.md) |
| 3964 | `2dbe96bb4` | 2026-06-29 09:51:24 -0700 | drexler-sky | Build: Bump datamodel-code-generator from 0.63.0 to 0.64.1 (#16993) | ✅ 已完成 | [3964_2dbe96bb4](commits/3964_2dbe96bb4/analysis.md) |
| 3965 | `535d032e9` | 2026-06-29 09:51:47 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.46.10 to 2.46.15 (#16989) | ✅ 已完成 | [3965_535d032e9](commits/3965_535d032e9/analysis.md) |
| 3966 | `a2fb64bff` | 2026-06-29 14:24:00 -0700 | Ryan Blue | Spec: Add spec for expressions (#16652) | ✅ 已完成 | [3966_a2fb64bff](commits/3966_a2fb64bff/analysis.md) |
| 3967 | `8c1ee9d9d` | 2026-06-29 18:43:57 -0700 | Szehon Ho | Core, Spark: Migrate Spark table properties to Spark module (#15875) | ✅ 已完成 | [3967_8c1ee9d9d](commits/3967_8c1ee9d9d/analysis.md) |
| 3968 | `be27af46d` | 2026-06-30 13:28:42 -0700 | Alexandre Dutra | REST: Fix schema of data-access object in REST spec (#16594) | ✅ 已完成 | [3968_be27af46d](commits/3968_be27af46d/analysis.md) |
| 3969 | `c9e61fa55` | 2026-07-01 08:22:22 -0700 | Kevin Liu | CVE Scan: Test PR failure reporting UI (#16962) | ✅ 已完成 | [3969_c9e61fa55](commits/3969_c9e61fa55/analysis.md) |
| 3970 | `f1ed57c17` | 2026-07-01 11:22:50 -0700 | Manu Zhang | Core: Include row lineage and key-id in snapshot value methods (#17015) | ✅ 已完成 | [3970_f1ed57c17](commits/3970_f1ed57c17/analysis.md) |
| 3971 | `e4074709c` | 2026-07-01 12:06:05 -0700 | Ryan Blue | API: Add indexStatsNames to create field names for content stats (#17010) | ✅ 已完成 | [3971_e4074709c](commits/3971_e4074709c/analysis.md) |
| 3972 | `41113b1f6` | 2026-07-01 13:08:13 -0700 | gaborkaszab | Core: Remove unused TestTrackedFileStruct.CONTENT_STATS_ORDINAL (#17031) | ✅ 已完成 | [3972_41113b1f6](commits/3972_41113b1f6/analysis.md) |
| 3973 | `d6aa0bf5c` | 2026-07-01 15:22:22 -0700 | Szehon Ho | Spec: Add optional specific-name to UDF definition model (#16727) | ✅ 已完成 | [3973_d6aa0bf5c](commits/3973_d6aa0bf5c/analysis.md) |
| 3974 | `da8ff447a` | 2026-07-01 23:51:42 -0700 | Vova Kolmakov | Docs: Document nightly snapshots (#16544) | ✅ 已完成 | [3974_da8ff447a](commits/3974_da8ff447a/analysis.md) |
| 3975 | `744e81103` | 2026-07-02 17:36:18 -0700 | Xin Huang | Parquet: Read and write geometry and geography WKB values (#16982) | ✅ 已完成 | [3975_744e81103](commits/3975_744e81103/analysis.md) |
| 3976 | `035fc1e40` | 2026-07-02 17:37:38 -0700 | Xin Huang | Spark 4.1: Map geo Spark types (#16851) | ✅ 已完成 | [3976_035fc1e40](commits/3976_035fc1e40/analysis.md) |
| 3977 | `49b89a8c5` | 2026-07-02 23:33:17 -0700 | Maximilian Michels | Flink: Hold back equality delete converter watermark until completion (#17038) | ✅ 已完成 | [3977_49b89a8c5](commits/3977_49b89a8c5/analysis.md) |
| 3978 | `11706a286` | 2026-07-03 12:13:24 +0200 | Maximilian Michels | Flink: Backport: Hold back equality delete converter watermark until completion (#17038) (#17067) | ✅ 已完成 | [3978_11706a286](commits/3978_11706a286/analysis.md) |
| 3979 | `3038fde68` | 2026-07-03 08:27:28 -0600 | Zehua Zou | Core: Fix thread-unsafe duplicate delete handling in ManifestFilterManager (#16686) | ✅ 已完成 | [3979_3038fde68](commits/3979_3038fde68/analysis.md) |
| 3980 | `c3bc6a468` | 2026-07-05 00:29:30 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.46.15 to 2.46.17 (#17099) | ✅ 已完成 | [3980_c3bc6a468](commits/3980_c3bc6a468/analysis.md) |
| 3981 | `f459f6535` | 2026-07-05 00:29:41 -0700 | dependabot[bot] | Build: Bump io.grpc:grpc-netty-shaded from 1.82.0 to 1.82.1 (#17103) | ✅ 已完成 | [3981_f459f6535](commits/3981_f459f6535/analysis.md) |
| 3982 | `f636995eb` | 2026-07-05 00:29:59 -0700 | dependabot[bot] | Build: Bump nessie from 0.108.0 to 0.108.1 (#17101) | ✅ 已完成 | [3982_f636995eb](commits/3982_f636995eb/analysis.md) |
| 3983 | `05c5d77fd` | 2026-07-06 08:44:57 +0200 | Yuya Ebihara | Core: Extend org.apache.iceberg.hadoop.Configurable in HadoopConfigurable (#16736) | ✅ 已完成 | [3983_05c5d77fd](commits/3983_05c5d77fd/analysis.md) |
| 3984 | `68aa4294e` | 2026-07-06 10:21:32 +0200 | dependabot[bot] | Build: Bump actions/setup-java from 5.3.0 to 5.4.0 (#17104) | ✅ 已完成 | [3984_68aa4294e](commits/3984_68aa4294e/analysis.md) |
| 3985 | `303a538b2` | 2026-07-06 10:21:53 +0200 | dependabot[bot] | Build: Bump actions/setup-python from 6.2.0 to 6.3.0 (#17105) | ✅ 已完成 | [3985_303a538b2](commits/3985_303a538b2/analysis.md) |
| 3986 | `0f485542b` | 2026-07-06 10:29:19 +0200 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.64.1 to 0.66.0 (#17100) | ✅ 已完成 | [3986_0f485542b](commits/3986_0f485542b/analysis.md) |
| 3987 | `2d3f5c24e` | 2026-07-06 11:21:58 +0200 | Eduard Tudenhoefner | Core: Handle ExceptionInInitializerError & NoClassDefFoundError invoke-time failures in DynMethods callers (#16611) | ✅ 已完成 | [3987_2d3f5c24e](commits/3987_2d3f5c24e/analysis.md) |
| 3988 | `04a35e412` | 2026-07-06 09:18:36 -0700 | Yuya Ebihara | Build: Bump com.google.cloud.gcs.analytics:gcs-analytics-core (#17108) | ✅ 已完成 | [3988_04a35e412](commits/3988_04a35e412/analysis.md) |
| 3989 | `79bae78cb` | 2026-07-06 20:20:47 +0200 | Eunbin Son | Docs: Fix reversed Flink binary/varbinary to Iceberg type mapping (#17090) | ✅ 已完成 | [3989_79bae78cb](commits/3989_79bae78cb/analysis.md) |
| 3990 | `d303514b4` | 2026-07-06 20:21:12 +0200 | Eunbin Son | ORC: Fix garbled exception message for invalid timestamp unit attribute (#17098) | ✅ 已完成 | [3990_d303514b4](commits/3990_d303514b4/analysis.md) |
| 3991 | `34b03b771` | 2026-07-07 11:08:49 +0200 | Maximilian Michels | Flink: Resolve unpartitioned equality deletes across all partitions (#17018) | ✅ 已完成 | [3991_34b03b771](commits/3991_34b03b771/analysis.md) |
| 3992 | `773907d28` | 2026-07-07 12:32:18 -0700 | Ryan Blue | Core: Add id tracking to MetricsConfig (#17022) | ✅ 已完成 | [3992_773907d28](commits/3992_773907d28/analysis.md) |
| 3993 | `2780b917e` | 2026-07-07 13:07:34 -0700 | Amogh Jahagirdar | Core: Cleanup some of the TrackedFile tests (#17035) | ✅ 已完成 | [3993_2780b917e](commits/3993_2780b917e/analysis.md) |
| 3994 | `1d64a7c91` | 2026-07-07 13:59:21 -0700 | Maximilian Michels | Flink: Backport: Resolve unpartitioned equality deletes across all partitions (#17018) (#17129) | ✅ 已完成 | [3994_1d64a7c91](commits/3994_1d64a7c91/analysis.md) |
| 3995 | `3cd56a706` | 2026-07-07 22:34:19 -0700 | Xiaoxuan | Parquet: Fix variant shredding of large decimals (precision > 18) (#17002) | ✅ 已完成 | [3995_3cd56a706](commits/3995_3cd56a706/analysis.md) |
| 3996 | `9bb14af0e` | 2026-07-08 08:55:56 -0700 | Ryan Blue | Parquet: Refactor ParquetMetrics to produce field stats (#17116) | ✅ 已完成 | [3996_9bb14af0e](commits/3996_9bb14af0e/analysis.md) |
| 3997 | `2a0f39205` | 2026-07-08 08:57:08 -0700 | Kevin Liu | Docs: Clarify AGENTS comment guidance (#16998) | ✅ 已完成 | [3997_2a0f39205](commits/3997_2a0f39205/analysis.md) |
| 3998 | `34efd94f1` | 2026-07-08 09:00:36 -0700 | gaborkaszab | Core: Make partition field in TrackedFile optional (#17000) | ✅ 已完成 | [3998_34efd94f1](commits/3998_34efd94f1/analysis.md) |
| 3999 | `d7c07d56f` | 2026-07-08 09:21:51 -0700 | Ajantha Bhat | CI: Use one Java version for PR checks (#16945) | ✅ 已完成 | [3999_d7c07d56f](commits/3999_d7c07d56f/analysis.md) |
| 4000 | `20bf72be7` | 2026-07-08 11:13:53 -0700 | gaborkaszab | Core: Clean up TrackingStruct tests (#17041) | ✅ 已完成 | [4000_20bf72be7](commits/4000_20bf72be7/analysis.md) |
| 4001 | `30d137e44` | 2026-07-08 16:57:07 -0700 | Xin Huang | Spark 4.1: Read and write geometry and geography values in Parquet (#17073) | ✅ 已完成 | [4001_30d137e44](commits/4001_30d137e44/analysis.md) |
| 4002 | `dd35b7840` | 2026-07-08 17:38:05 -0700 | Xin Huang | Core: Test geometry and geography metrics keep counts without bounds (#17131) | ✅ 已完成 | [4002_dd35b7840](commits/4002_dd35b7840/analysis.md) |
| 4003 | `a71f95023` | 2026-07-09 09:04:46 +0200 | GuoYu | Docs: Add doc for flink table maintenance ConvertEqualityDeletes (#17113) | ✅ 已完成 | [4003_a71f95023](commits/4003_a71f95023/analysis.md) |
| 4004 | `30f87adc1` | 2026-07-09 10:53:23 +0200 | pvary | Revert "Core: Test geometry and geography metrics keep counts without bounds (#17131)" (#17141) | ✅ 已完成 | [4004_30f87adc1](commits/4004_30f87adc1/analysis.md) |
| 4005 | `5f154712f` | 2026-07-09 10:42:43 -0600 | Gang Wu | Core, Spark: Fix row lineage last updated sequence inheritance (#17039) | ✅ 已完成 | [4005_5f154712f](commits/4005_5f154712f/analysis.md) |
| 4006 | `d41101270` | 2026-07-09 14:20:37 -0700 | Gabriel Baldez | AWS: Use assumed-role credentials for REST SigV4 signing (#16794) | ✅ 已完成 | [4006_d41101270](commits/4006_d41101270/analysis.md) |
| 4007 | `f5770491d` | 2026-07-10 15:12:21 +0200 | Maximilian Michels | Flink: Integrate ConvertEqualityDeletes with IcebergSink (#17142) | ✅ 已完成 | [4007_f5770491d](commits/4007_f5770491d/analysis.md) |
| 4008 | `f07ac96a2` | 2026-07-10 15:56:51 +0200 | Adam Szita | Fix catalog properties links after docs refactor of #15848 (#17153) | ✅ 已完成 | [4008_f07ac96a2](commits/4008_f07ac96a2/analysis.md) |
| 4009 | `9ddc391c1` | 2026-07-10 15:46:28 -0700 | Maximilian Michels | Flink: Backport: Integrate ConvertEqualityDeletes with IcebergSink (#17142) (#17156) | ✅ 已完成 | [4009_9ddc391c1](commits/4009_9ddc391c1/analysis.md) |
| 4010 | `d99b0eefa` | 2026-07-10 23:53:21 -0700 | Xin Huang | Core: Test geometry and geography metrics keep counts without bounds (#17147) | ✅ 已完成 | [4010_d99b0eefa](commits/4010_d99b0eefa/analysis.md) |
| 4011 | `c6fded17a` | 2026-07-11 12:45:14 -0700 | Anas Khan | Core: Fix OAuthTokenResponse.addScope error to show the invalid scope (#17126) | ✅ 已完成 | [4011_c6fded17a](commits/4011_c6fded17a/analysis.md) |
| 4012 | `e8959f23a` | 2026-07-11 12:46:27 -0700 | Neelesh Salian | Parquet: Cache adjacent identical metadata in variant reader (#16852) | ✅ 已完成 | [4012_e8959f23a](commits/4012_e8959f23a/analysis.md) |
| 4013 | `9729c9428` | 2026-07-11 23:55:08 -0700 | dependabot[bot] | Build: Bump github/codeql-action/upload-sarif from 4.36.2 to 4.36.3 (#17172) | ✅ 已完成 | [4013_9729c9428](commits/4013_9729c9428/analysis.md) |
| 4014 | `a9240011f` | 2026-07-11 23:56:31 -0700 | dependabot[bot] | Build: Bump org.apache.httpcomponents.client5:httpclient5 (#17170) | ✅ 已完成 | [4014_a9240011f](commits/4014_a9240011f/analysis.md) |
| 4015 | `a221a146d` | 2026-07-11 23:57:08 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.46.17 to 2.46.21 (#17168) | ✅ 已完成 | [4015_a221a146d](commits/4015_a221a146d/analysis.md) |
| 4016 | `aa64e8ec8` | 2026-07-11 23:57:39 -0700 | dependabot[bot] | Build: Bump com.diffplug.spotless:spotless-plugin-gradle (#17167) | ✅ 已完成 | [4016_aa64e8ec8](commits/4016_aa64e8ec8/analysis.md) |
| 4017 | `2640206b5` | 2026-07-11 23:58:08 -0700 | dependabot[bot] | Build: Bump datamodel-code-generator from 0.66.0 to 0.67.0 (#17166) | ✅ 已完成 | [4017_2640206b5](commits/4017_2640206b5/analysis.md) |
| 4018 | `5926f4fc8` | 2026-07-11 23:58:29 -0700 | dependabot[bot] | Build: Bump docker/setup-qemu-action from 4.1.0 to 4.2.0 (#17165) | ✅ 已完成 | [4018_5926f4fc8](commits/4018_5926f4fc8/analysis.md) |
| 4019 | `187da3ed8` | 2026-07-12 13:08:40 -0600 | Yuya Ebihara | API: Guard against null in IN/NOT_IN predicates (#17014) | ✅ 已完成 | [4019_187da3ed8](commits/4019_187da3ed8/analysis.md) |
| 4020 | `486fd93b4` | 2026-07-13 13:43:51 +0200 | Eunbin Son | Arrow: Fix dict-encoded VARCHAR/VARBINARY read for direct ByteBuffers (#17055) | ✅ 已完成 | [4020_486fd93b4](commits/4020_486fd93b4/analysis.md) |
| 4021 | `04fd0251f` | 2026-07-13 13:45:49 +0200 | Han You | Flink: handle simultaneous schema evolution and data conversion (#17024) | ✅ 已完成 | [4021_04fd0251f](commits/4021_04fd0251f/analysis.md) |
| 4022 | `304d6ffe2` | 2026-07-13 13:47:28 +0200 | Raghvendra Singh | Parquet: add adaptive bloom filter sizing (PARQUET-2254) (#16363) | ✅ 已完成 | [4022_304d6ffe2](commits/4022_304d6ffe2/analysis.md) |
| 4023 | `80ec02432` | 2026-07-13 09:52:22 -0500 | Varun Lakhyani | Core: Add EagerInputFile and EagerInputStream to buffer files below a size threshold  (#16729) | ✅ 已完成 | [4023_80ec02432](commits/4023_80ec02432/analysis.md) |
| 4024 | `9b64b317c` | 2026-07-13 17:12:30 +0200 | Maximilian Michels | Flink: Remove converted equality deletes for same-branch conversion (#17189) | ✅ 已完成 | [4024_9b64b317c](commits/4024_9b64b317c/analysis.md) |
| 4025 | `9484b5769` | 2026-07-13 15:16:35 -0700 | Ryan Blue | Core: Use mocks in TrackedFile tests (#17133) | ✅ 已完成 | [4025_9484b5769](commits/4025_9484b5769/analysis.md) |
| 4026 | `f69664035` | 2026-07-13 17:02:21 -0700 | Maximilian Michels | Flink: Backport: Remove converted equality deletes for same-branch conversion (#17189) (#17190) | ✅ 已完成 | [4026_f69664035](commits/4026_f69664035/analysis.md) |
| 4027 | `9a9474ad1` | 2026-07-13 17:03:37 -0700 | Han You | Flink: Backport: handle simultaneous schema evolution and data conversion (#17024) (#17191) | ✅ 已完成 | [4027_9a9474ad1](commits/4027_9a9474ad1/analysis.md) |
| 4028 | `7a882c2d3` | 2026-07-14 09:41:17 +0200 | William E. Murray | Docs: add Collate to vendors.md (#17123) | ✅ 已完成 | [4028_7a882c2d3](commits/4028_7a882c2d3/analysis.md) |
| 4029 | `d9878d3f2` | 2026-07-14 09:58:44 -0500 | jackylee | Spark 4.0: Add Spark REST_CATALOG_PURGE property to delegate DROP TABLE PURGE to REST catalogs (#17185) | ✅ 已完成 | [4029_d9878d3f2](commits/4029_d9878d3f2/analysis.md) |
| 4030 | `de4505b8c` | 2026-07-14 09:59:52 -0500 | jackylee | Spark 4.1: Add Spark REST_CATALOG_PURGE property to delegate DROP TABLE PURGE to REST catalogs (#17186)(Backport of #15614) | ✅ 已完成 | [4030_de4505b8c](commits/4030_de4505b8c/analysis.md) |
| 4031 | `f1c5c1881` | 2026-07-14 11:26:56 -0700 | Gera Shegalov | Spark: Add session-level split size override (#16154) | ✅ 已完成 | [4031_f1c5c1881](commits/4031_f1c5c1881/analysis.md) |
| 4032 | `8b26043ab` | 2026-07-14 13:54:22 -0700 | Xin Huang | Spark 4.1: Test geometry and geography DML and fall back from vectorized reads (#17149) | ✅ 已完成 | [4032_8b26043ab](commits/4032_8b26043ab/analysis.md) |
| 4033 | `f2875fdcc` | 2026-07-14 14:11:46 -0700 | Gera Shegalov | Spark: Backport split-size session conf to 3.5 and 4.0 (#17199) | ✅ 已完成 | [4033_f2875fdcc](commits/4033_f2875fdcc/analysis.md) |
| 4034 | `4eb37b013` | 2026-07-15 07:37:12 +0200 | Sergei Nikolaev | Flink: Fix timestamp-micros conversion in AvroToRowDataConverters (#17194) | ✅ 已完成 | [4034_4eb37b013](commits/4034_4eb37b013/analysis.md) |
| 4035 | `bb16f7f9b` | 2026-07-15 07:41:37 +0200 | Anas Khan | Flink: Fix watermark extractor error message showing field id instead of file (#17124) | ✅ 已完成 | [4035_bb16f7f9b](commits/4035_bb16f7f9b/analysis.md) |
| 4036 | `98e9e43de` | 2026-07-15 09:23:15 -0700 | Sergei Nikolaev | Flink: Backport timestamp-micros conversion fix to 1.20 and 2.0 (#17212) | ✅ 已完成 | [4036_98e9e43de](commits/4036_98e9e43de/analysis.md) |
| 4037 | `2876f3f57` | 2026-07-15 17:35:06 -0700 | drexler-sky | Spark 4.1: Implement listTableSummaries (#16891) | ✅ 已完成 | [4037_2876f3f57](commits/4037_2876f3f57/analysis.md) |
| 4038 | `b6eaa6adf` | 2026-07-15 17:37:00 -0700 | Yuya Ebihara | Infra: Group github/codeql-action bumps into a single dependabot PR (#17160) | ✅ 已完成 | [4038_b6eaa6adf](commits/4038_b6eaa6adf/analysis.md) |
| 4039 | `57aeb3a58` | 2026-07-15 18:06:45 -0700 | dependabot[bot] | Build: Bump actions/labeler from 6.1.0 to 6.2.0 (#17229) | ✅ 已完成 | [4039_57aeb3a58](commits/4039_57aeb3a58/analysis.md) |
| 4040 | `39b7d1b2f` | 2026-07-15 18:51:11 -0700 | dependabot[bot] | Build: Bump jetty from 12.1.10 to 12.1.11 (#17220) | ✅ 已完成 | [4040_39b7d1b2f](commits/4040_39b7d1b2f/analysis.md) |
| 4041 | `cd2e2a6c2` | 2026-07-15 18:51:31 -0700 | dependabot[bot] | Build: Bump jackson-bom from 2.22.0 to 2.22.1 (#17224) | ✅ 已完成 | [4041_cd2e2a6c2](commits/4041_cd2e2a6c2/analysis.md) |
| 4042 | `91afff3cf` | 2026-07-15 18:51:46 -0700 | dependabot[bot] | Build: Bump orc from 1.9.8 to 1.9.9 (#17225) | ✅ 已完成 | [4042_91afff3cf](commits/4042_91afff3cf/analysis.md) |
| 4043 | `d36c8ac8c` | 2026-07-15 18:52:12 -0700 | dependabot[bot] | Build: Bump at.yawk.lz4:lz4-java from 1.11.0 to 1.11.1 (#17226) | ✅ 已完成 | [4043_d36c8ac8c](commits/4043_d36c8ac8c/analysis.md) |
| 4044 | `14566a17d` | 2026-07-15 18:52:27 -0700 | dependabot[bot] | Build: Bump io.netty:netty-buffer from 4.2.15.Final to 4.2.16.Final (#17227) | ✅ 已完成 | [4044_14566a17d](commits/4044_14566a17d/analysis.md) |
| 4045 | `d2bb155a0` | 2026-07-15 18:52:40 -0700 | dependabot[bot] | Build: Bump the codeql-action group with 3 updates (#17228) | ✅ 已完成 | [4045_d2bb155a0](commits/4045_d2bb155a0/analysis.md) |
| 4046 | `4713badae` | 2026-07-15 18:52:50 -0700 | dependabot[bot] | Build: Bump actions/setup-java from 5.4.0 to 5.5.0 (#17230) | ✅ 已完成 | [4046_4713badae](commits/4046_4713badae/analysis.md) |
| 4047 | `5f3d3c560` | 2026-07-15 18:53:02 -0700 | dependabot[bot] | Build: Bump docker/build-push-action from 7.2.0 to 7.3.0 (#17231) | ✅ 已完成 | [4047_5f3d3c560](commits/4047_5f3d3c560/analysis.md) |
| 4048 | `2660ac0fe` | 2026-07-16 08:13:32 -0600 | gaborkaszab | Core: Clean up TestTrackedFileAdapters and drop TrackedFileBuilder (#17144) | ✅ 已完成 | [4048_2660ac0fe](commits/4048_2660ac0fe/analysis.md) |
| 4049 | `07382fffc` | 2026-07-16 10:38:14 -0700 | Manu Zhang | Spark 4.0: Upgrade to Spark 4.0.4 (#17181) | ✅ 已完成 | [4049_07382fffc](commits/4049_07382fffc/analysis.md) |
| 4050 | `37772e204` | 2026-07-16 10:41:45 -0700 | Yuya Ebihara | Build: Bump datamodel-code-generator from 0.67.0 to 0.68.1 (#17239) | ✅ 已完成 | [4050_37772e204](commits/4050_37772e204/analysis.md) |
| 4051 | `6b6b80fff` | 2026-07-16 10:42:29 -0700 | Yuya Ebihara | Build: Bump software.amazon.awssdk:bom from 2.46.21 to 2.47.2 (#17238) | ✅ 已完成 | [4051_6b6b80fff](commits/4051_6b6b80fff/analysis.md) |
| 4052 | `b4deba5e7` | 2026-07-16 10:43:14 -0700 | Yuya Ebihara | Build: Bump com.google.cloud:libraries-bom from 26.84.0 to 26.85.0 (#17237) | ✅ 已完成 | [4052_b4deba5e7](commits/4052_b4deba5e7/analysis.md) |
| 4053 | `be43e3174` | 2026-07-16 23:26:39 +0200 | Anupam Yadav | Kafka Connect: Add bounded retry for transient commit exceptions (#16434) | ✅ 已完成 | [4053_be43e3174](commits/4053_be43e3174/analysis.md) |
| 4054 | `1522f384b` | 2026-07-16 16:40:05 -0500 | James Dilworth | Site: Add StarTree to vendors documentation (#17197) | ✅ 已完成 | [4054_1522f384b](commits/4054_1522f384b/analysis.md) |
| 4055 | `0d6d2d679` | 2026-07-16 16:41:03 -0500 | Jim Halfpenny | Site: Add stackable to vendors list (#17136) | ✅ 已完成 | [4055_0d6d2d679](commits/4055_0d6d2d679/analysis.md) |
| 4056 | `a9c98970a` | 2026-07-16 16:14:24 -0700 | Eunbin Son | Core: Fix ShreddedObject.put not clearing prior remove marker (#17066) | ✅ 已完成 | [4056_a9c98970a](commits/4056_a9c98970a/analysis.md) |
| 4057 | `6ec1a01cb` | 2026-07-16 16:25:03 -0700 | Matt Butrovich | Site: add Iceberg Rust and DataFusion Comet blog post (#17162) | ✅ 已完成 | [4057_6ec1a01cb](commits/4057_6ec1a01cb/analysis.md) |
| 4058 | `8550723a7` | 2026-07-16 18:46:34 -0700 | Xin Huang | Core: Read and write geometry and geography values in Avro (#17119) | ✅ 已完成 | [4058_8550723a7](commits/4058_8550723a7/analysis.md) |
| 4059 | `30bb110ce` | 2026-07-17 18:51:26 +0200 | GuoYu | Flink: Fix TableMaintenance operator uid instability that breaks savepoint restore (#17210) | ✅ 已完成 | [4059_30bb110ce](commits/4059_30bb110ce/analysis.md) |
| 4060 | `c8521d869` | 2026-07-17 19:39:06 +0200 | Neelesh Salian | API: Harden variant binary parsing against malformed input (#16568) | ✅ 已完成 | [4060_c8521d869](commits/4060_c8521d869/analysis.md) |
| 4061 | `14509b0fb` | 2026-07-17 10:47:41 -0700 | Manu Zhang | Spark 4.1: Upgrade to Spark 4.1.3 (#17182) | ✅ 已完成 | [4061_14509b0fb](commits/4061_14509b0fb/analysis.md) |
| 4062 | `a42396edf` | 2026-07-17 10:48:46 -0700 | Manu Zhang | Spark 3.5: Upgrade to Spark 3.5.9 (#17180) | ✅ 已完成 | [4062_a42396edf](commits/4062_a42396edf/analysis.md) |
| 4063 | `100d0621b` | 2026-07-17 12:31:38 -0700 | Ryan Blue | Core: Refactor ContentStats and FieldStats (#17159) | ✅ 已完成 | [4063_100d0621b](commits/4063_100d0621b/analysis.md) |
| 4064 | `47c121436` | 2026-07-17 12:32:23 -0700 | Ryan Blue | API, Core: Make variant classes serializable. (#17260) | ✅ 已完成 | [4064_47c121436](commits/4064_47c121436/analysis.md) |
| 4065 | `e973bc0fc` | 2026-07-17 15:08:09 -0700 | Anoop Johnson | API, Core: Use fixed transform result types when source type is unknown (#17262) | ✅ 已完成 | [4065_e973bc0fc](commits/4065_e973bc0fc/analysis.md) |
| 4066 | `25654ab4b` | 2026-07-18 08:55:42 +0200 | GuoYu | Flink: BackPort Fix TableMaintenance operator uid instability that breaks savepoint restore (#17283) | ✅ 已完成 | [4066_25654ab4b](commits/4066_25654ab4b/analysis.md) |
| 4067 | `0a1db66e5` | 2026-07-18 10:30:33 -0700 | Eunbin Son | API: Fix incorrect Javadoc on ManageSnapshots tag methods (#17243) | ✅ 已完成 | [4067_0a1db66e5](commits/4067_0a1db66e5/analysis.md) |
| 4068 | `25f53e177` | 2026-07-18 22:26:33 -0700 | dependabot[bot] | Build: Bump pymarkdownlnt from 0.9.38 to 0.9.39 (#17289) | ✅ 已完成 | [4068_25f53e177](commits/4068_25f53e177/analysis.md) |
| 4069 | `7786a264f` | 2026-07-18 22:27:29 -0700 | dependabot[bot] | Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.14 to 1.6.15 (#17290) | ✅ 已完成 | [4069_7786a264f](commits/4069_7786a264f/analysis.md) |
| 4070 | `3322cf01c` | 2026-07-18 22:27:43 -0700 | dependabot[bot] | Build: Bump software.amazon.awssdk:bom from 2.47.2 to 2.47.5 (#17291) | ✅ 已完成 | [4070_3322cf01c](commits/4070_3322cf01c/analysis.md) |
| 4071 | `733e86c55` | 2026-07-18 22:27:55 -0700 | dependabot[bot] | Build: Bump io.grpc:grpc-netty-shaded from 1.82.1 to 1.82.2 (#17292) | ✅ 已完成 | [4071_733e86c55](commits/4071_733e86c55/analysis.md) |
| 4072 | `f9fa14fb8` | 2026-07-19 09:30:51 -0700 | dependabot[bot] | Build: Bump astral-sh/setup-uv from 8.2.0 to 8.3.2 (#17293) | ✅ 已完成 | [4072_f9fa14fb8](commits/4072_f9fa14fb8/analysis.md) |
| 4073 | `e5bc1f15d` | 2026-07-19 09:31:08 -0700 | dependabot[bot] | Build: Bump docker/setup-buildx-action from 4.1.0 to 4.2.0 (#17294) | ✅ 已完成 | [4073_e5bc1f15d](commits/4073_e5bc1f15d/analysis.md) |
| 4074 | `8edec4b4c` | 2026-07-19 09:31:21 -0700 | dependabot[bot] | Build: Bump actions/stale from 10.3.0 to 10.4.0 (#17295) | ✅ 已完成 | [4074_8edec4b4c](commits/4074_8edec4b4c/analysis.md) |
| 4075 | `9821b252e` | 2026-07-19 09:33:29 -0700 | Neelesh Salian | Spark: Add vectorized Parquet reads for variant columns (#16292) | ✅ 已完成 | [4075_9821b252e](commits/4075_9821b252e/analysis.md) |
| 4076 | `1ec15051d` | 2026-07-19 09:35:01 -0700 | Neelesh Salian | Core: Precompute sorted entries in ShreddedObject SerializationState (#17114) | ✅ 已完成 | [4076_1ec15051d](commits/4076_1ec15051d/analysis.md) |
| 4077 | `0950d5216` | 2026-07-20 09:53:38 -0600 | Christian Bush | Parquet: Fix initial-default rows dropped when filtering on the defaulted column (#16692) | ✅ 已完成 | [4077_0950d5216](commits/4077_0950d5216/analysis.md) |
| 4078 | `2f17d9baf` | 2026-07-20 09:46:40 -0700 | Anoop Johnson | API: Shorten dropped source type comment in PartitionSpec (follow-up to #17262) (#17303) | ✅ 已完成 | [4078_2f17d9baf](commits/4078_2f17d9baf/analysis.md) |
| 4079 | `f660519a4` | 2026-07-21 10:28:56 -0600 | ajreid21 | Kafka Connect: Set engine metadata in EnvironmentContext (#17195) | ✅ 已完成 | [4079_f660519a4](commits/4079_f660519a4/analysis.md) |
| 4080 | `a8d1e1464` | 2026-07-22 14:39:37 +0200 | GuoYu | Flink: Add plannedGroups counter metric and log for DataFileRewritePlanner (#17316) | ✅ 已完成 | [4080_a8d1e1464](commits/4080_a8d1e1464/analysis.md) |
| 4081 | `652737e24` | 2026-07-22 14:43:22 +0200 | vishnu prakash | API: Add tests for DateTimeUtil.microsToMillis (#16773) | ✅ 已完成 | [4081_652737e24](commits/4081_652737e24/analysis.md) |
| 4082 | `470f4642b` | 2026-07-22 10:42:57 -0700 | vishnu prakash | Parquet: Fix variant BINARY upper bound to truncate up (#16880) | ✅ 已完成 | [4082_470f4642b](commits/4082_470f4642b/analysis.md) |
| 4083 | `72145bfae` | 2026-07-23 13:03:13 +0200 | pvary | Build: Fix Trivy failures (#17344) | ✅ 已完成 | [4083_72145bfae](commits/4083_72145bfae/analysis.md) |
| 4084 | `cd708ae33` | 2026-07-23 17:38:44 +0200 | GuoYu | Flink: Backport Add plannedGroups counter metric and log for DataFileRewritePlanner to 2.0 and 1.20 (#17335) | ✅ 已完成 | [4084_cd708ae33](commits/4084_cd708ae33/analysis.md) |
| 4085 | `c29264684` | 2026-07-23 17:11:10 -0700 | Ryan Blue | API: Extract superclass from InclusiveMetricsEvaluator (#17201) | ✅ 已完成 | [4085_c29264684](commits/4085_c29264684/analysis.md) |
| 4086 | `8de28da31` | 2026-07-24 08:20:07 +0200 | Swapna Marru | Flink: SQL: Add variant avro dynamic record generator (#16450) | ✅ 已完成 | [4086_8de28da31](commits/4086_8de28da31/analysis.md) |
| 4087 | `8f4843394` | 2026-07-24 11:30:51 +0200 | terrytlu | Hive: Use server-side filter to list Iceberg tables in HiveCatalog (#17317) | ✅ 已完成 | [4087_8f4843394](commits/4087_8f4843394/analysis.md) |
| 4088 | `e42f0dd2d` | 2026-07-24 11:51:34 +0200 | GuoYu | Flink: Avoid unnecessary TableLoader.loadTable() in IcebergCommitter subtasks > 0 (#17251) | ✅ 已完成 | [4088_e42f0dd2d](commits/4088_e42f0dd2d/analysis.md) |
