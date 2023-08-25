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

package io.confluent.heinz.jmstrans.jms_transaction_consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;

@Component
public class JMSConsumerTransSession {


    String QUEUE_JNDI_NAME1 = "";
    String QUEUE_JNDI_NAME2 = "";
    String CONNECTION_FACTORY_JNDI_NAME = "";

    public JMSConsumerTransSession(Environment env) {
        QUEUE_JNDI_NAME1 = env.getProperty("queue_jndi_name_1");
        QUEUE_JNDI_NAME2 = env.getProperty("queue_jndi_name_2");
        CONNECTION_FACTORY_JNDI_NAME = env.getProperty("connection_factory_jndi_name");

        try {
            runJMS_Trans(env);
        } catch (NamingException e) {
            e.printStackTrace();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private void runJMS_Trans(Environment env) throws NamingException, JMSException {
        // setup environment variables for creating of the initial context
        Hashtable<String, Object> envJMS = new Hashtable<String, Object>();

        envJMS.put(InitialContext.INITIAL_CONTEXT_FACTORY, env.getProperty("initial_context_factory"));
        envJMS.put(InitialContext.PROVIDER_URL, env.getProperty("context_provider_url"));
        envJMS.put(Context.SECURITY_PRINCIPAL, env.getProperty("context_security_principal"));
        envJMS.put(Context.SECURITY_CREDENTIALS, env.getProperty("context_security_credentials"));

        InitialContext initialContext = new InitialContext(envJMS);

        // Lookup the connection factory
        ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);

        // Create connection to Solace messaging
        Connection connection = connectionFactory.createConnection();

        //Create a transacted session
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

        System.out.printf("Connected to the Solace Message VPN '%s' with client username '%s'.%n", "heinzvpn",
                "heinz");

        // Lookup the queue.
        javax.jms.Queue queue1 = (javax.jms.Queue) initialContext.lookup(QUEUE_JNDI_NAME1);
        javax.jms.Queue queue2 = (Queue) initialContext.lookup(QUEUE_JNDI_NAME2);

        // Create the message producer for the created queue
        MessageProducer messageProducer = session.createProducer(queue2);

        // Create a text message.
        //TextMessage message = session.createTextMessage("Hello world Queues!");

        connection.start();

        // From the session, create a consumer for the destination
        MessageConsumer messageConsumer = session.createConsumer(queue1);
        int i = 0;

        //while(i != 10) {
        TextMessage msgConsume = (TextMessage) messageConsumer.receive();
        System.out.println("Received message " + msgConsume.getText().toString());
        System.out.println("Message header redelivered: " + msgConsume.getJMSRedelivered());

        TextMessage message2 = session.createTextMessage(msgConsume.getText().toString() + "replay");


        // Send the message
        // NOTE: JMS Message Priority is not supported by the Solace Message Bus
        messageProducer.send(queue2, message2, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);
        System.out.println("Sent message " + message2.getText().toString());


        boolean force_rollback = Boolean.valueOf(env.getProperty("force_rollback"));
        if (force_rollback) {
            session.rollback();
            System.out.println("Was transaction rolled back: " + force_rollback);
        } else {
            session.commit();
            System.out.println("Was transaction rolled back: " + force_rollback);
            System.out.printf("Received message '%s' from queue '%s ...%n' ", msgConsume.getText(), queue1.toString());
            System.out.printf("Sending message '%s' to queue '%s'...%n", message2.getText(), queue2.toString());
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //}

        System.out.println("Exiting...");

        // Close everything in the order reversed from the opening order
        // NOTE: as the interfaces below extend AutoCloseable,
        // with them it's possible to use the "try-with-resources" Java statement
        // see details at https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
        messageProducer.close();
        messageConsumer.close();
        session.close();
        connection.close();
        // The initial context needs to be close; it does not extend AutoCloseable
        initialContext.close();


    }
}
