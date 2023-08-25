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

package io.confluent.heinz.transactions.generatetransactionrecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.confluent.heinz.transactions.avroMsg;
import io.confluent.kafka.schemaregistry.json.JsonSchema;

import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.errors.SerializationException;

import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;




public class KafkaSession {

    Environment _env;
    public KafkaSession(Environment env) {
        createKafkaSession(env);
        this._env = env;
    }

    private final Log logger = LogFactory.getLog(KafkaSession.class);
    private int counter = 0;

    private KafkaProducer producer;


    public void createKafkaSession(Environment env) {

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", env.getProperty("bootstrap.servers"));
        properties.setProperty("value.serializer", io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName());
        properties.setProperty("key.serializer", io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer.class.getName());
        properties.setProperty("schema.registry.url", env.getProperty("schema.registry.url"));
        properties.setProperty("schema.registry.basic.auth.user.info", env.getProperty("schema.registry.basic.auth.user.info"));
        properties.setProperty("basic.auth.credentials.source", env.getProperty("basic.auth.credentials.source"));
        properties.setProperty("sasl.mechanism",env.getProperty("sasl.mechanism"));
        properties.setProperty("sasl.jaas.config", env.getProperty("sasl.jaas.config"));
        properties.setProperty("security.protocol", env.getProperty("security.protocol"));
        properties.setProperty("client.dns.lookup", env.getProperty("client.dns.lookup"));
        properties.setProperty("acks", "all");
        properties.setProperty("auto.create.topics.enable", "true");
        properties.setProperty("topic.creation.default.partitions", "3");
        properties.setProperty("auto.register.schema", "true");
        properties.setProperty("json.fail.invalid.schema","true");
        properties.setProperty("enable.idempotence", env.getProperty("enable.idempotence"));
        properties.setProperty("transactional.id", env.getProperty("transactional.id"));


        producer = new KafkaProducer<>(properties);
        producer.initTransactions();

    }

    public void sendAvroMessage(JsonMsg jMsg) throws FileNotFoundException {
        String jsonKey = "";

        //toggle count up and down for 0/1
        if (counter == 0){
            counter++;
        } else {
            counter=0;
        }



        //Simpler for avro and automatically includes the schema as part of the transmission
        avroMsg avroRecord = new avroMsg();
        avroRecord.setFirstName(jMsg.getFirstName());
        avroRecord.setLastName(jMsg.getLastName());
        avroRecord.setCustomerId(jMsg.getCustomerId() + counter);

        //Get JSON Schema from Project -- same schema used to create JSON object
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader("src/main/resources/jsonschema/JsonMsgK.json"))) {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null)
            {
                contentBuilder.append(sCurrentLine).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String jsonSchemaStr = contentBuilder.toString();
        JsonSchema jsonSchema = new JsonSchema(jsonSchemaStr);

        //System.out.println("JSON Schema: " + jsonSchemaStr);

        JsonMsgK jsonMsgK = new JsonMsgK();
        jsonMsgK.setClientID(((Integer) avroRecord.get("customer_id")));
        jsonMsgK.setClient("Heinz57");


        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj = null;

        try {
            jsonKey = mapper.writeValueAsString(jsonMsgK);
            //System.out.println("JSON Key: " + jsonKey);
            actualObj = mapper.readTree(jsonKey);
            //System.out.println("Actual JsonNode: " + actualObj.toString());
        } catch (JsonProcessingException je){
            System.out.println("JSON Error: \n:");
            je.printStackTrace();
        }


        // create producer record that includes JSON schema and payload that is understood by Confluent JSON Schema Serializer
        ProducerRecord producerRecord = new ProducerRecord<Object, avroMsg>(_env.getProperty("publisher.topic"), JsonSchemaUtils.envelope(jsonSchema,actualObj), avroRecord);

        //System.out.println("Schema: " + avroRecord.getSchema().toString(true));
        //System.out.println("First: " + avroRecord.get("first_name"));
        //System.out.println("Last: " + avroRecord.get("last_name"));
        //System.out.println("ID: " + avroRecord.get("customer_id"));

        try {
            producer.beginTransaction();
            producer.send(producerRecord, new MyProducerCallback());
            //System.out.println("\nSent! JSON key: " + JsonSchemaUtils.envelope(jsonSchema,actualObj).toString() + " \nAvro value: " + avroRecord.toString());
            producer.commitTransaction();
            logger.info("\nSent! JSON key: " + JsonSchemaUtils.envelope(jsonSchema,actualObj).toString() + " \nAvro value: " + avroRecord.toString());
        } catch(SerializationException e) {
            System.out.println("Error:");
            e.printStackTrace();
            producer.abortTransaction();
        }



    }

    class MyProducerCallback implements Callback {

        private final Log logger = LogFactory.getLog(MyProducerCallback.class);

        @Override
        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
            if (e != null)
                logger.info("AsynchronousProducer failed with an exception");
            else {
                logger.info("AsynchronousProducer call Success:" + "Sent to partition: " + recordMetadata.partition() + " and offset: " + recordMetadata.offset() + "\n");
            }
        }
    }

}
