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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class GetJsonClassWithSchema {

    ObjectMapper mapper = new ObjectMapper();
    JsonNode actualObj = null;
    JsonSchema schema = null;

    String jsonKey = "";

    public GetJsonClassWithSchema(String jsonSchema) {
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader("src/main/resources/jsonschema/" + jsonSchema))) {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null)
            {
                contentBuilder.append(sCurrentLine).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String jsonSchemaStr = contentBuilder.toString();
        schema = new JsonSchema(jsonSchemaStr);

    }

    public ObjectNode getJsonWithSchema(Object jsonMsg) {
        try {
            jsonKey = mapper.writeValueAsString(jsonMsg);
            //System.out.println("JSON Key: " + jsonKey);
            actualObj = mapper.readTree(jsonKey);
            //System.out.println("Actual JsonNode: " + actualObj.toString());
        } catch (JsonProcessingException je){
            System.out.println("JSON Error: \n:");
            je.printStackTrace();
        }
        return (ObjectNode) JsonSchemaUtils.envelope(schema,actualObj);
    }
}