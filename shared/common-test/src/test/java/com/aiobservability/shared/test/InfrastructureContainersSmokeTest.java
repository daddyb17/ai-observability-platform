package com.aiobservability.shared.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class InfrastructureContainersSmokeTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("aiobs")
                    .withUsername("aiobs")
                    .withPassword("aiobs");

    @Test
    void postgresContainerStarts() {
        assertTrue(POSTGRES.isRunning());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_KAFKA_TESTCONTAINER", matches = "true")
    void kafkaContainerStartsWhenEnabled() {
        try (KafkaContainer kafka = new KafkaContainer(
                DockerImageName.parse("apache/kafka-native:3.8.0")
                        .asCompatibleSubstituteFor("apache/kafka")
        )) {
            kafka.start();
            assertTrue(kafka.isRunning());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTCONTAINER", matches = "true")
    void redisContainerStartsWhenEnabled() {
        try (GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
                .withExposedPorts(6379)) {
            redis.start();
            assertTrue(redis.isRunning());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_ELASTIC_TESTCONTAINER", matches = "true")
    void elasticsearchContainerStartsWhenEnabled() {
        try (ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.17.22")
                .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("discovery.type", "single-node")) {
            elasticsearch.start();
            assertTrue(elasticsearch.isRunning());
        }
    }
}
