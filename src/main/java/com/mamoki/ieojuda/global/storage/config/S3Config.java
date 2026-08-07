package com.mamoki.ieojuda.global.storage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties s3Properties) {
        return S3Client.builder()
                .region(Region.of(s3Properties.getRegion()))
                // EC2 배포 시 이 한 줄만 DefaultCredentialsProvider.create()로 교체하면 IAM Role을 쓴다
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())))
                .build();
    }
}
