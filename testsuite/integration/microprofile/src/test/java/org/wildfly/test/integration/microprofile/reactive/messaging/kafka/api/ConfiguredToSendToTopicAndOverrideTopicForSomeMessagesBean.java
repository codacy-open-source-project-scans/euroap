/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.microprofile.reactive.messaging.kafka.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;

import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.kafka.api.KafkaMetadataUtil;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@ApplicationScoped
public class ConfiguredToSendToTopicAndOverrideTopicForSomeMessagesBean {


    private final CountDownLatch latch = new CountDownLatch(4);
    private final Map<Integer, IncomingKafkaRecordMetadata<String, Integer>> testing2Metadatas = Collections.synchronizedMap(new HashMap<>());
    private final Map<Integer, IncomingKafkaRecordMetadata<String, Integer>> testing3Metadatas = Collections.synchronizedMap(new HashMap<>());

    public CountDownLatch getLatch() {
        return latch;
    }

    public Map<Integer, IncomingKafkaRecordMetadata<String, Integer>> getTesting2Metadatas() {
        return testing2Metadatas;
    }

    public Map<Integer, IncomingKafkaRecordMetadata<String, Integer>> getTesting3Metadatas() {
        return testing3Metadatas;
    }

    @Outgoing("invm2")
    public Publisher<Integer> source() {
        return ReactiveStreams.of(1, 2, 3, 4).buildRs();
    }

    @Incoming("invm2")
    @Outgoing("to-kafka2or3-default-to-2")
    public Message<Integer> sendToKafka2or3(Integer i) {
        Message<Integer> msg = Message.of(i);

        OutgoingKafkaRecordMetadata.OutgoingKafkaRecordMetadataBuilder<String> mb = OutgoingKafkaRecordMetadata.<String>builder();
        if (i % 2 == 0) {
            // Only set the key for half the messages
            mb.withKey("KEY-" + i);
        }
        if (i >= 3) {
            mb.withTopic("testing3");
        }
        msg = KafkaMetadataUtil.writeOutgoingKafkaMetadata(msg, mb.build());
        return msg;
    }

    // Messages which did not have the topic set in the metadata should end up here
    // (as this is the topic set up in the MP Config)
    @Incoming("from-kafka2")
    public CompletionStage<Void> receiveFromKafka2(Message<Integer> msg) {
        IncomingKafkaRecordMetadata<String, Integer> metadata = KafkaMetadataUtil.readIncomingKafkaMetadata(msg).get();
        testing2Metadatas.put(msg.getPayload(), metadata);
        latch.countDown();
        return msg.ack();
    }


    // Messages which had the topic set in the metadata should end up here
    @Incoming("from-kafka3")
    public CompletionStage<Void> receiveFromKafka3(Message<Integer> msg) {
        IncomingKafkaRecordMetadata<String, Integer> metadata = KafkaMetadataUtil.readIncomingKafkaMetadata(msg).get();
        testing3Metadatas.put(msg.getPayload(), metadata);
        latch.countDown();
        return msg.ack();
    }
}
