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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class MongoCongig {

    @Value("${sp.commons.test.replica-member-register-timeout-second:7}")
    private int replicaMemberRegisterTimeoutSecond;
    private final MongoProperties mongoProperties;

    public MongoCongig(MongoProperties mongoProperties) {
        this.mongoProperties = mongoProperties;
    }

    @Bean
    @Primary
    public IMongodConfig mongoConfigTest() throws IOException {
        return (new MongodConfigBuilder()).version(Version.Main.PRODUCTION).withLaunchArgument("--replSet", "rs0")
                .net(new Net(this.mongoProperties.getHost(), this.mongoProperties.getPort(),
                        Network.localhostIsIPv6())).cmdOptions((new MongoCmdOptionsBuilder()).useNoJournal(false).build()).build();
    }

    @Bean(
            initMethod = "start",
            destroyMethod = "stop"
    )
    public MongodExecutable embeddedMongoServer() throws IOException {
        return (MongodExecutable) MongodStarter.getDefaultInstance().prepare(this.mongoConfigTest());
    }

    @Bean
    @Primary
    public MongoClient mongoClient() throws InterruptedException {
        System.out.println("HERE");
        MongoClient client = MongoClients.create();
        this.initEmbeddedMongoReplica(client);
        this.awaitReplicaMemberIsRegistered();
        return client;
    }

    private void initEmbeddedMongoReplica(MongoClient client) throws InterruptedException {
        try {
            MongoDatabase database = client.getDatabase("admin");
            Document cr = database.runCommand(new Document("isMaster", 1));
            cr = database.runCommand(new Document("replSetInitiate", new Document()));
            TimeUnit.SECONDS.sleep(5L);
            cr = database.runCommand(new Document("replSetGetStatus", 1));

            while (!this.isReplicaSetStarted(cr)) {
                TimeUnit.SECONDS.sleep(1L);
                cr = database.runCommand(new Document("replSetGetStatus", 1));
            }

        } catch (Throwable var4) {
            throw var4;
        }
    }

    private boolean isReplicaSetStarted(Document setting) {
        if (setting.get("members") == null) {
            return false;
        } else {
            List members = (List) setting.get("members");
            Iterator var3 = members.iterator();

            int state;
            do {
                if (!var3.hasNext()) {
                    return true;
                }

                Object m = var3.next();
                Document member = (Document) m;
                state = member.getInteger("state", 0);
            } while (state == 1 || state == 2 || state == 7);

            return false;
        }
    }

    private void awaitReplicaMemberIsRegistered() throws InterruptedException {
        try {
            TimeUnit.SECONDS.sleep((long) this.replicaMemberRegisterTimeoutSecond);
        } catch (Throwable var2) {
            throw var2;
        }
    }

}
