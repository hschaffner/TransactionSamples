/*
 * Copyright Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.confluent.heinz.transactions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class ConfluentExactlyOnceSession {
//    @Autowired
    private Environment _env;

    public ConfluentExactlyOnceSession(Environment env) {
        this._env = env;
        createKafkaStream(env);
    }

    private void createKafkaStream(Environment env) {
        final Log logger = LogFactory.getLog(ConfluentExactlyOnceSession.class);

        final Properties streamsConfiguration = new Properties();
        //deserialization details
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde.class);
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,"io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde");

        // Schema Registry details
        streamsConfiguration.put("schema.registry.url", env.getProperty("schema.registry.url"));
        streamsConfiguration.put("schema.registry.basic.auth.user.info", env.getProperty("schema.registry.basic.auth.user.info"));
        streamsConfiguration.put("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));

        //Kafka Properties
        streamsConfiguration.put("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
        streamsConfiguration.put("sasl.mechanism", env.getProperty("sasl.mechanism"));
        streamsConfiguration.put("sasl.jaas.config", env.getProperty("sasl.jaas.config"));
        streamsConfiguration.put("security.protocol", env.getProperty("security.protocol"));
        streamsConfiguration.put("client.dns.lookup", env.getProperty("client.dns.lookup"));
        streamsConfiguration.put("acks", "all");
        streamsConfiguration.put("auto.create.topics.enable", "true");
        streamsConfiguration.put("topic.creation.default.partitions", "3");
        streamsConfiguration.put("auto.register.schema", "true");
        streamsConfiguration.put("json.fail.invalid.schema", "true");
        streamsConfiguration.put("enable.idempotence", env.getProperty("enable.idempotence"));
        streamsConfiguration.put("transactional.id", env.getProperty("transactional.id"));
        streamsConfiguration.put("group.id", env.getProperty("consume.group.id"));
        streamsConfiguration.put("enable.auto.commit",env.getProperty("consume.enable.auto.commit"));
        streamsConfiguration.put("isolation.level", env.getProperty("consume.isolation.level"));
        streamsConfiguration.put("application.id", env.getProperty("application.id"));
        streamsConfiguration.put("bootstrap.servers", env.getProperty("bootstrap.servers"));

        streamsConfiguration.put("processing.guarantee", env.getProperty("processing.guarantee"));

        //Value (de)serialization of Avro Value
        final Serde<avroMsg> valueSpecificAvroSerde = new SpecificAvroSerde<>();
        Map<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put("schema.registry.url", env.getProperty("schema.registry.url"));
        serdeConfig.put("schema.registry.basic.auth.user.info", env.getProperty("schema.registry.basic.auth.user.info"));
        serdeConfig.put("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
        valueSpecificAvroSerde.configure(serdeConfig, false); // `false` for record values

        //Value (de)serialization of Avro Key
        final Serde<avroMsgK> keySpecificAvroSerde = new SpecificAvroSerde<>();
        Map<String, String> serdeConfigAvro = new HashMap<>();
        serdeConfigAvro.put("schema.registry.url", env.getProperty("schema.registry.url"));
        serdeConfigAvro.put("schema.registry.basic.auth.user.info", env.getProperty("schema.registry.basic.auth.user.info"));
        serdeConfigAvro.put("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
        keySpecificAvroSerde.configure(serdeConfig, true); // `true` for key values

        //Key (de)serialization of JSON based on class - mostly relevant only for consumer
        final Serde<JsonMsgK> keySpecificJsonSerde = new KafkaJsonSchemaSerde<>(JsonMsgK.class);
        Map<String, Object> serdeConfigJson = new HashMap<>();
        serdeConfigJson.put("schema.registry.url", env.getProperty("schema.registry.url"));
        serdeConfigJson.put("schema.registry.basic.auth.user.info", env.getProperty("schema.registry.basic.auth.user.info"));
        serdeConfigJson.put("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
        keySpecificJsonSerde.configure(serdeConfigJson, true); // true` for record keys

        //Key (de)serialization of JSON based on  JSON ObjectNode -- required if output stream is JSON and requires schema
        final Serde<ObjectNode> keySpecificJsonObjectSerde = new KafkaJsonSchemaSerde<>(ObjectNode.class);
        Map<String, Object> serdeConfigJsonObj = new HashMap<>();
        serdeConfigJsonObj.put("schema.registry.url", env.getProperty("schema.registry.url"));
        serdeConfigJsonObj.put("schema.registry.basic.auth.user.info", env.getProperty("schema.registry.basic.auth.user.info"));
        serdeConfigJsonObj.put("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
        keySpecificJsonObjectSerde.configure(serdeConfigJsonObj, true); // true` for record keys

        //Get details for JSON processing
        GetJsonClassWithSchema classWithSchema = new GetJsonClassWithSchema("JsonMsgK.json");

        //Simple printing of K,V from stream
        StreamsBuilder builder = new StreamsBuilder();
        KStream<JsonMsgK, avroMsg> kStream = builder.stream(_env.getProperty("orig.topic"), Consumed.with(keySpecificJsonSerde, valueSpecificAvroSerde));
        System.out.println("++++++++++++++++++++++++++++++++++++++");
        kStream.foreach((k, v) -> {
            System.out.println("Key = " + k + " Value = " + v);
        } );


        //Same Stream as printing above but converts Value String fields to uppercase
        kStream.filter((Predicate<JsonMsgK, avroMsg>) (key, value) -> {
            //System.out.println(value);
            String client = key.getClient();
            Integer clientID = key.getClientID();
            if (clientID%2 == 0)
                return true;
            else
                System.out.println("******************* Not converting Values to uppercase");
                return false;
        }).map((jsonMsgK, avroMsg) -> {
            //need to create ObjectNode with JSON msg and schema
            ObjectNode jsonKeyObj = classWithSchema.getJsonWithSchema(jsonMsgK);
            avroMsg.setLastName(avroMsg.getLastName().toUpperCase());
            avroMsg.setFirstName(avroMsg.getFirstName().toUpperCase());
            System.out.println("******************* Converting Values to uppercase");
            System.out.println("======================= First Name: " + avroMsg.getFirstName() + " Lastname: " + avroMsg.getLastName());
            return new KeyValue<>(jsonKeyObj,avroMsg);
        }).to(_env.getProperty("dup.topic"), Produced.with(keySpecificJsonObjectSerde, valueSpecificAvroSerde));

        kStream.filter((Predicate<JsonMsgK, avroMsg>) (key, value) -> {
            //System.out.println(value);
            String client = key.getClient();
            Integer clientID = key.getClientID();
            if( clientID % 2 != 0)
                return true;
            else
                System.out.println("******************* Not converting Key to Avro from JSON");
                return false;
        }).map((jsonMsgK, avroMsg) -> {
            System.out.println("******************* Converting Key to Avro from JSON");
            avroMsgK aKey = new avroMsgK();
            aKey.setClient(jsonMsgK.getClient());
            aKey.setClientID(jsonMsgK.getClientID());
            return new KeyValue<>(aKey,avroMsg);
        }).to(_env.getProperty("new.topic"), Produced.with(keySpecificAvroSerde,valueSpecificAvroSerde));



        Topology topology = builder.build();

        TopologyDescription topDesc = topology.describe();
        System.out.println("==================Topology: " + topDesc.toString());

        KafkaStreams streams = new KafkaStreams(topology, streamsConfiguration);
        System.out.println("\n");
        logger.info("Starting the stream");
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Stopping Stream");
            streams.close();
        }));
    }

}