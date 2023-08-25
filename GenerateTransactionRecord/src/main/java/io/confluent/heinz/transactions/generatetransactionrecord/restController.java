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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

//import javax.servlet.http.HttpServletRequest;
import java.io.FileNotFoundException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;


@org.springframework.web.bind.annotation.RestController
@RequestMapping(value = "/")
public class restController {
    @Autowired
    private Environment env;

    private final Log logger = LogFactory.getLog(restController.class);

    private int counter = 0;

    private KafkaSession kafkaSession = null;

    //private KafkaSession kafkaSession = new KafkaSession(env);
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void postMessage(@RequestBody JsonMsg request,
                            HttpServletRequest httpRequest) {


        if(kafkaSession == null) {
            kafkaSession = new KafkaSession(env);
            logger.info("Started new instance of Kafka");
        }

        if (counter == 0){
            counter++;
        } else {
            counter=0;
        }


        String JsonStr = "";
        ObjectMapper mapper = new ObjectMapper();
        //Output the POST message to confirm what was received
        try {
            JsonStr = mapper.writeValueAsString(request);
        } catch (JsonProcessingException je){
            logger.info("++++++++++++++++++++JSON Error: \n:");
            je.printStackTrace();
        }
        //System.out.println("JSON POST Request: " + JsonStr);
        logger.info(String.format("JSON REST POST Data -> %s " , JsonStr));
        System.out.println("JSON REST POST Data -> " + JsonStr);

        try {
            kafkaSession.sendAvroMessage(request);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/logtest")
    public String index() {
        final Log logger = LogFactory.getLog(getClass());

        logger.trace("A TRACE Message");
        logger.debug("A DEBUG Message");
        logger.info("An INFO Message");
        logger.warn("A WARN Message");
        logger.error("An ERROR Message");

        return "Howdy! Check out the Logs to see the output...";
    }
}
