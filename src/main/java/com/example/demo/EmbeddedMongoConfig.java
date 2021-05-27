package com.example.demo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongoCmdOptionsBuilder;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class EmbeddedMongoConfig {

    private final MongoProperties mongoProperties;
    private final Logger log = LoggerFactory.getLogger(EmbeddedMongoConfig.class);

    public EmbeddedMongoConfig(MongoProperties mongoProperties) {
        this.mongoProperties = mongoProperties;
    }

    @Bean
    @Primary
    public IMongodConfig mongoConfigTest() throws IOException {
        return new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .withLaunchArgument("--replSet", "rs0")
                .net(new Net(mongoProperties.getHost(), mongoProperties.getPort(), Network.localhostIsIPv6()))
                .cmdOptions(new MongoCmdOptionsBuilder().useNoJournal(false).build())
                .build();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public MongodExecutable embeddedMongoServer() throws IOException {
        return MongodStarter.getDefaultInstance().prepare(mongoConfigTest());
    }

    @Bean
    @Primary
    public MongoClient mongoClient() throws InterruptedException {
        MongoClient client = MongoClients.create();
        initEmbeddedMongoReplica(client);
        awaitReplicaMemberIsRegistered();
        return client;
    }

    private void initEmbeddedMongoReplica(MongoClient client) throws InterruptedException {
        MongoDatabase database = client.getDatabase("admin");
        Document cr = database.runCommand(new Document("isMaster", 1));
        log.info("isMaster: {}", cr);

        // Initialize replica set
        cr = database.runCommand(new Document("replSetInitiate", new Document()));
        log.info("replSetInitiate: {}", cr);

        TimeUnit.SECONDS.sleep(5);
        cr = database.runCommand(new Document("replSetGetStatus", 1));
        log.info("replSetGetStatus: {}", cr);

        // Check replica set status before to proceed
        while (!isReplicaSetStarted(cr)) {
            log.info("Waiting for 1 seconds...");
            TimeUnit.SECONDS.sleep(1);
            cr = database.runCommand(new Document("replSetGetStatus", 1));
            log.info("replSetGetStatus: {}", cr);
        }
    }

    private boolean isReplicaSetStarted(Document setting) {
        if (setting.get("members") == null) {
            return false;
        }

        List members = (List) setting.get("members");
        for (Object m : members) {
            Document member = (Document) m;
            log.info(member.toString());
            int state = member.getInteger("state", 0);
            log.info("state: {}", state);
            // 1 - PRIMARY, 2 - SECONDARY, 7 - ARBITER
            if (state != 1 && state != 2 && state != 7) {
                return false;
            }
        }
        log.info("replica set is started by config");
        return true;
    }

    private void awaitReplicaMemberIsRegistered() throws InterruptedException {
        int replicaMemberRegisterTimeoutSecond = 7;
        TimeUnit.SECONDS.sleep(replicaMemberRegisterTimeoutSecond);
    }
}