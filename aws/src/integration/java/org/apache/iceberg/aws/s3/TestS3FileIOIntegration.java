/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.aws.s3;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.apache.iceberg.TestHelpers;
import org.apache.iceberg.aws.AwsClientFactories;
import org.apache.iceberg.aws.AwsClientFactory;
import org.apache.iceberg.aws.AwsIntegTestUtil;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.PartitionMetadata;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectAclRequest;
import software.amazon.awssdk.services.s3.model.GetObjectAclResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.utils.ImmutableMap;
import software.amazon.awssdk.utils.IoUtils;

/**
 * 文件级说明：TestS3FileIOIntegration 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 s3文件io集成 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class TestS3FileIOIntegration {

  private final Random random = new Random(1);
  private static AwsClientFactory clientFactory;
  private static S3Client s3;
  private static S3ControlClient s3Control;
  private static S3ControlClient crossRegionS3Control;
  private static KmsClient kms;
  private static String bucketName;
  private static String crossRegionBucketName;
  private static String accessPointName;
  private static String crossRegionAccessPointName;
  private static String prefix;
  private static byte[] contentBytes;
  private static String content;
  private static String kmsKeyArn;
  private static int deletionBatchSize;
  private String objectKey;
  private String objectUri;

  /** 初始化：beforeClass，在测试类加载时准备共享的测试环境与数据。 */
  @BeforeClass
  public static void beforeClass() {
    clientFactory = AwsClientFactories.defaultFactory();
    s3 = clientFactory.s3();
    kms = clientFactory.kms();
    s3Control = AwsIntegTestUtil.createS3ControlClient(AwsIntegTestUtil.testRegion());
    crossRegionS3Control =
        AwsIntegTestUtil.createS3ControlClient(AwsIntegTestUtil.testCrossRegion());
    bucketName = AwsIntegTestUtil.testBucketName();
    crossRegionBucketName = AwsIntegTestUtil.testCrossRegionBucketName();
    accessPointName = UUID.randomUUID().toString();
    crossRegionAccessPointName = UUID.randomUUID().toString();
    prefix = UUID.randomUUID().toString();
    contentBytes = new byte[1024 * 1024 * 10];
    deletionBatchSize = 3;
    content = new String(contentBytes, StandardCharsets.UTF_8);
    kmsKeyArn = kms.createKey().keyMetadata().arn();

    AwsIntegTestUtil.createAccessPoint(s3Control, accessPointName, bucketName);
    AwsIntegTestUtil.createAccessPoint(
        crossRegionS3Control, crossRegionAccessPointName, crossRegionBucketName);
  }

  /** 清理：afterClass，在所有测试方法执行完毕后释放共享资源。 */
  @AfterClass
  public static void afterClass() {
    AwsIntegTestUtil.cleanS3Bucket(s3, bucketName, prefix);
    AwsIntegTestUtil.deleteAccessPoint(s3Control, accessPointName);
    AwsIntegTestUtil.deleteAccessPoint(crossRegionS3Control, crossRegionAccessPointName);
    kms.scheduleKeyDeletion(
        ScheduleKeyDeletionRequest.builder().keyId(kmsKeyArn).pendingWindowInDays(7).build());
  }

  /** 初始化：before，在每个测试方法执行前准备测试环境与数据。 */
  @Before
  public void before() {
    objectKey = String.format("%s/%s", prefix, UUID.randomUUID().toString());
    objectUri = String.format("s3://%s/%s", bucketName, objectKey);
  }

  /** 初始化：beforeEach，在每个测试方法执行前准备测试环境与数据。 */
  @BeforeEach
  public void beforeEach() {
    clientFactory.initialize(Maps.newHashMap());
  }

  /**
   * 测试场景：新建input流。
   *
   * <p>验证该方法在 新建input流 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNewInputStream() throws Exception {
    s3.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(objectKey).build(),
        RequestBody.fromBytes(contentBytes));
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3);
    validateRead(s3FileIO);
  }

  /**
   * 测试场景：s3文件io带s3文件ioaws客户端工厂impl。
   *
   * <p>验证该方法在 s3文件io带s3文件ioaws客户端工厂impl 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testS3FileIOWithS3FileIOAwsClientFactoryImpl() throws Exception {
    s3.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(objectKey).build(),
        RequestBody.fromBytes(contentBytes));
    S3FileIO s3FileIO = new S3FileIO();
    Map<String, String> properties = Maps.newHashMap();
    properties.put(
        S3FileIOProperties.CLIENT_FACTORY,
        "org.apache.iceberg.aws.s3.DefaultS3FileIOAwsClientFactory");
    s3FileIO.initialize(properties);
    validateRead(s3FileIO);
  }

  /**
   * 测试场景：s3文件io带默认aws客户端工厂impl。
   *
   * <p>验证该方法在 s3文件io带默认aws客户端工厂impl 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testS3FileIOWithDefaultAwsClientFactoryImpl() throws Exception {
    s3.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(objectKey).build(),
        RequestBody.fromBytes(contentBytes));
    S3FileIO s3FileIO = new S3FileIO();
    Map<String, String> properties = Maps.newHashMap();
    properties.put(
        S3FileIOProperties.CLIENT_FACTORY,
        "org.apache.iceberg.aws.s3.DefaultS3FileIOAwsClientFactory");
    s3FileIO.initialize(properties);
    validateRead(s3FileIO);
  }

  /**
   * 测试场景：新建input流带accesspoint。
   *
   * <p>验证该方法在 新建input流带accesspoint 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNewInputStreamWithAccessPoint() throws Exception {
    s3.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(objectKey).build(),
        RequestBody.fromBytes(contentBytes));
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3);
    s3FileIO.initialize(
        ImmutableMap.of(
            S3FileIOProperties.ACCESS_POINTS_PREFIX + bucketName,
            testAccessPointARN(AwsIntegTestUtil.testRegion(), accessPointName)));
    validateRead(s3FileIO);
  }

  /**
   * 测试场景：新建input流带交叉regionaccesspoint。
   *
   * <p>验证该方法在 新建input流带交叉regionaccesspoint 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNewInputStreamWithCrossRegionAccessPoint() throws Exception {
    clientFactory.initialize(ImmutableMap.of(S3FileIOProperties.USE_ARN_REGION_ENABLED, "true"));
    S3Client s3Client = clientFactory.s3();
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(objectKey).build(),
        RequestBody.fromBytes(contentBytes));
    // make a copy in cross-region bucket
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(
                testAccessPointARN(AwsIntegTestUtil.testCrossRegion(), crossRegionAccessPointName))
            .key(objectKey)
            .build(),
        RequestBody.fromBytes(contentBytes));
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3);
    s3FileIO.initialize(
        ImmutableMap.of(
            S3FileIOProperties.ACCESS_POINTS_PREFIX + bucketName,
            testAccessPointARN(AwsIntegTestUtil.testCrossRegion(), crossRegionAccessPointName)));
    validateRead(s3FileIO);
  }

  /**
   * 测试场景：新建output流。
   *
   * <p>验证该方法在 新建output流 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNewOutputStream() throws Exception {
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3);
    write(s3FileIO);
    InputStream stream =
        s3.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build());
    String result = IoUtils.toUtf8String(stream);
    stream.close();
    Assert.assertEquals(content, result);
  }

  /**
   * 测试场景：新建output流带accesspoint。
   *
   * <p>验证该方法在 新建output流带accesspoint 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNewOutputStreamWithAccessPoint() throws Exception {
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3);
    s3FileIO.initialize(
        ImmutableMap.of(
            S3FileIOProperties.ACCESS_POINTS_PREFIX + bucketName,
            testAccessPointARN(AwsIntegTestUtil.testRegion(), accessPointName)));
    write(s3FileIO);
    InputStream stream =
        s3.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build());
    String result = IoUtils.toUtf8String(stream);
    stream.close();
    Assert.assertEquals(content, result);
  }

  /**
   * 测试场景：新建output流带交叉regionaccesspoint。
   *
   * <p>验证该方法在 新建output流带交叉regionaccesspoint 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNewOutputStreamWithCrossRegionAccessPoint() throws Exception {
    clientFactory.initialize(ImmutableMap.of(S3FileIOProperties.USE_ARN_REGION_ENABLED, "true"));
    S3Client s3Client = clientFactory.s3();
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3);
    s3FileIO.initialize(
        ImmutableMap.of(
            S3FileIOProperties.ACCESS_POINTS_PREFIX + bucketName,
            testAccessPointARN(AwsIntegTestUtil.testCrossRegion(), crossRegionAccessPointName)));
    write(s3FileIO);
    InputStream stream =
        s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(
                    testAccessPointARN(
                        AwsIntegTestUtil.testCrossRegion(), crossRegionAccessPointName))
                .key(objectKey)
                .build());
    String result = IoUtils.toUtf8String(stream);
    stream.close();
    Assert.assertEquals(content, result);
  }

  /**
   * 测试场景：测试serversides3encryption。
   *
   * <p>验证该方法在对应输入下的行为与断言结果是否符合预期。
   */
  @Test
  public void testServerSideS3Encryption() throws Exception {
    S3FileIOProperties properties = new S3FileIOProperties();
    properties.setSseType(S3FileIOProperties.SSE_TYPE_S3);
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, properties);
    write(s3FileIO);
    validateRead(s3FileIO);
    GetObjectResponse response =
        s3.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build())
            .response();
    Assert.assertEquals(ServerSideEncryption.AES256, response.serverSideEncryption());
  }

  /**
   * 测试场景：测试serversidekmsencryption。
   *
   * <p>验证该方法在对应输入下的行为与断言结果是否符合预期。
   */
  @Test
  public void testServerSideKmsEncryption() throws Exception {
    S3FileIOProperties properties = new S3FileIOProperties();
    properties.setSseType(S3FileIOProperties.SSE_TYPE_KMS);
    properties.setSseKey(kmsKeyArn);
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, properties);
    write(s3FileIO);
    validateRead(s3FileIO);
    GetObjectResponse response =
        s3.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build())
            .response();
    Assert.assertEquals(ServerSideEncryption.AWS_KMS, response.serverSideEncryption());
    Assert.assertEquals(response.ssekmsKeyId(), kmsKeyArn);
  }

  /**
   * 测试场景：serversidekmsencryption带默认key。
   *
   * <p>验证该方法在 serversidekmsencryption带默认key 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testServerSideKmsEncryptionWithDefaultKey() throws Exception {
    S3FileIOProperties properties = new S3FileIOProperties();
    properties.setSseType(S3FileIOProperties.SSE_TYPE_KMS);
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, properties);
    write(s3FileIO);
    validateRead(s3FileIO);
    GetObjectResponse response =
        s3.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build())
            .response();
    Assert.assertEquals(ServerSideEncryption.AWS_KMS, response.serverSideEncryption());
    ListAliasesResponse listAliasesResponse =
        kms.listAliases(ListAliasesRequest.builder().keyId(response.ssekmsKeyId()).build());
    Assert.assertTrue(listAliasesResponse.hasAliases());
    Assert.assertEquals(1, listAliasesResponse.aliases().size());
    Assert.assertEquals("alias/aws/s3", listAliasesResponse.aliases().get(0).aliasName());
  }

  /**
   * 测试场景：serverside自定义encryption。
   *
   * <p>验证该方法在 serverside自定义encryption 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testServerSideCustomEncryption() throws Exception {
    // generate key
    KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
    keyGenerator.init(256, new SecureRandom());
    SecretKey secretKey = keyGenerator.generateKey();
    Base64.Encoder encoder = Base64.getEncoder();
    String encodedKey = new String(encoder.encode(secretKey.getEncoded()), StandardCharsets.UTF_8);
    // generate md5
    MessageDigest digest = MessageDigest.getInstance("MD5");
    String md5 =
        new String(encoder.encode(digest.digest(secretKey.getEncoded())), StandardCharsets.UTF_8);

    S3FileIOProperties properties = new S3FileIOProperties();
    properties.setSseType(S3FileIOProperties.SSE_TYPE_CUSTOM);
    properties.setSseKey(encodedKey);
    properties.setSseMd5(md5);
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, properties);
    write(s3FileIO);
    validateRead(s3FileIO);
    GetObjectResponse response =
        s3.getObject(
                GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .sseCustomerAlgorithm(ServerSideEncryption.AES256.name())
                    .sseCustomerKey(encodedKey)
                    .sseCustomerKeyMD5(md5)
                    .build())
            .response();
    Assert.assertNull(response.serverSideEncryption());
    Assert.assertEquals(ServerSideEncryption.AES256.name(), response.sseCustomerAlgorithm());
    Assert.assertEquals(md5, response.sseCustomerKeyMD5());
  }

  /**
   * 测试场景：测试acl。
   *
   * <p>验证该方法在对应输入下的行为与断言结果是否符合预期。
   */
  @Test
  public void testACL() throws Exception {
    S3FileIOProperties properties = new S3FileIOProperties();
    properties.setAcl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL);
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, properties);
    write(s3FileIO);
    validateRead(s3FileIO);
    GetObjectAclResponse response =
        s3.getObjectAcl(GetObjectAclRequest.builder().bucket(bucketName).key(objectKey).build());
    Assert.assertTrue(response.hasGrants());
    Assert.assertEquals(1, response.grants().size());
    Assert.assertEquals(Permission.FULL_CONTROL, response.grants().get(0).permission());
  }

  /**
   * 测试场景：客户端工厂序列化。
   *
   * <p>验证该方法在 客户端工厂序列化 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testClientFactorySerialization() throws Exception {
    S3FileIO fileIO = new S3FileIO(clientFactory::s3);
    write(fileIO);
    byte[] data = TestHelpers.serialize(fileIO);
    S3FileIO fileIO2 = TestHelpers.deserialize(data);
    validateRead(fileIO2);
  }

  /**
   * 测试场景：删除文件多个batches。
   *
   * <p>验证该方法在 删除文件多个batches 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDeleteFilesMultipleBatches() throws Exception {
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, getDeletionTestProperties());
    testDeleteFiles(deletionBatchSize * 2, s3FileIO);
  }

  /**
   * 测试场景：删除文件多个batches带accesspoints。
   *
   * <p>验证该方法在 删除文件多个batches带accesspoints 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDeleteFilesMultipleBatchesWithAccessPoints() throws Exception {
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, getDeletionTestProperties());
    s3FileIO.initialize(
        ImmutableMap.of(
            S3FileIOProperties.ACCESS_POINTS_PREFIX + bucketName,
            testAccessPointARN(AwsIntegTestUtil.testRegion(), accessPointName)));
    testDeleteFiles(deletionBatchSize * 2, s3FileIO);
  }

  /**
   * 测试场景：删除文件多个batches带交叉regionaccesspoints。
   *
   * <p>验证该方法在 删除文件多个batches带交叉regionaccesspoints 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDeleteFilesMultipleBatchesWithCrossRegionAccessPoints() throws Exception {
    clientFactory.initialize(ImmutableMap.of(S3FileIOProperties.USE_ARN_REGION_ENABLED, "true"));
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, getDeletionTestProperties());
    s3FileIO.initialize(
        ImmutableMap.of(
            S3FileIOProperties.ACCESS_POINTS_PREFIX + bucketName,
            testAccessPointARN(AwsIntegTestUtil.testCrossRegion(), crossRegionAccessPointName)));
    testDeleteFiles(deletionBatchSize * 2, s3FileIO);
  }

  /**
   * 测试场景：删除文件lessthan批量size。
   *
   * <p>验证该方法在 删除文件lessthan批量size 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDeleteFilesLessThanBatchSize() throws Exception {
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, getDeletionTestProperties());
    testDeleteFiles(deletionBatchSize - 1, s3FileIO);
  }

  /**
   * 测试场景：删除文件单个批量带remainder。
   *
   * <p>验证该方法在 删除文件单个批量带remainder 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDeleteFilesSingleBatchWithRemainder() throws Exception {
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, getDeletionTestProperties());
    testDeleteFiles(5, s3FileIO);
  }

  /**
   * 测试场景：prefix列表。
   *
   * <p>验证该方法在 prefix列表 条件下的行为与断言结果是否符合预期。
   */
  @SuppressWarnings("DangerousParallelStreamUsage")
  @Test
  public void testPrefixList() {
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3);
    List<Integer> scaleSizes = Lists.newArrayList(1, 1000, 2500);
    String listPrefix = String.format("s3://%s/%s/%s", bucketName, prefix, "prefix-list-test");

    scaleSizes
        .parallelStream()
        .forEach(
            scale -> {
              String scalePrefix = String.format("%s/%s/", listPrefix, scale);
              createRandomObjects(scalePrefix, scale);
              assertEquals((long) scale, Streams.stream(s3FileIO.listPrefix(scalePrefix)).count());
            });

    long totalFiles = scaleSizes.stream().mapToLong(Integer::longValue).sum();
    Assertions.assertEquals(totalFiles, Streams.stream(s3FileIO.listPrefix(listPrefix)).count());
  }

  /**
   * 测试场景：prefix删除。
   *
   * <p>验证该方法在 prefix删除 条件下的行为与断言结果是否符合预期。
   */
  @SuppressWarnings("DangerousParallelStreamUsage")
  @Test
  public void testPrefixDelete() {
    S3FileIOProperties properties = new S3FileIOProperties();
    properties.setDeleteBatchSize(100);
    S3FileIO s3FileIO = new S3FileIO(clientFactory::s3, properties);
    String deletePrefix = String.format("s3://%s/%s/%s", bucketName, prefix, "prefix-delete-test");

    List<Integer> scaleSizes = Lists.newArrayList(0, 5, 1000, 2500);
    scaleSizes
        .parallelStream()
        .forEach(
            scale -> {
              String scalePrefix = String.format("%s/%s/", deletePrefix, scale);
              createRandomObjects(scalePrefix, scale);
              s3FileIO.deletePrefix(scalePrefix);
              assertEquals(0L, Streams.stream(s3FileIO.listPrefix(scalePrefix)).count());
            });
  }

  /** 辅助方法：获取deletion测试属性。 */
  private S3FileIOProperties getDeletionTestProperties() {
    S3FileIOProperties properties = new S3FileIOProperties();
    properties.setDeleteBatchSize(deletionBatchSize);
    return properties;
  }

  /** 辅助方法：测试删除文件。 */
  private void testDeleteFiles(int numObjects, S3FileIO s3FileIO) throws Exception {
    List<String> paths = Lists.newArrayList();
    for (int i = 1; i <= numObjects; i++) {
      String deletionKey = objectKey + "-deletion-" + i;
      write(s3FileIO, String.format("s3://%s/%s/%s", bucketName, prefix, deletionKey));
      paths.add(String.format("s3://%s/%s/%s", bucketName, prefix, deletionKey));
    }
    s3FileIO.deleteFiles(paths);
    for (String path : paths) {
      Assert.assertFalse(s3FileIO.newInputFile(path).exists());
    }
  }

  /** 辅助方法：写入。 */
  private void write(S3FileIO s3FileIO) throws Exception {
    write(s3FileIO, objectUri);
  }

  /** 辅助方法：写入。 */
  private void write(S3FileIO s3FileIO, String uri) throws Exception {
    OutputFile outputFile = s3FileIO.newOutputFile(uri);
    OutputStream outputStream = outputFile.create();
    IoUtils.copy(new ByteArrayInputStream(contentBytes), outputStream);
    outputStream.close();
  }

  /** 辅助方法：校验读取。 */
  private void validateRead(S3FileIO s3FileIO) throws Exception {
    InputFile file = s3FileIO.newInputFile(objectUri);
    Assert.assertEquals(contentBytes.length, file.getLength());
    InputStream stream = file.newStream();
    String result = IoUtils.toUtf8String(stream);
    stream.close();
    Assert.assertEquals(content, result);
  }

  /** 辅助方法：测试accesspointarn。 */
  private String testAccessPointARN(String region, String accessPoint) {
    // format: arn:aws:s3:region:account-id:accesspoint/resource
    return String.format(
        "arn:%s:s3:%s:%s:accesspoint/%s",
        PartitionMetadata.of(Region.of(region)).id(),
        region,
        AwsIntegTestUtil.testAccountId(),
        accessPoint);
  }

  /** 辅助方法：创建randomobjects。 */
  private void createRandomObjects(String objectPrefix, int count) {
    S3URI s3URI = new S3URI(objectPrefix);
    random
        .ints(count)
        .parallel()
        .forEach(
            i ->
                s3.putObject(
                    builder -> builder.bucket(s3URI.bucket()).key(s3URI.key() + i).build(),
                    RequestBody.empty()));
  }
}
