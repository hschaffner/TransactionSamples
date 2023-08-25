# Java Samples Comparing Confluent Exactly-Once Transactions with JMS Session Transactions

In this Intellij project there are five separate modules that compare the Confluent use of exactly-once transaction processing and JMS transactions. 

The **_"StreamProducerConsumer"_** module
is a KStream sample that consumes a Confluent record and performs some simple stream processing. The stream reads the data and
outputs the data to the console. It also converts the records value Avro data into uppercase, and it also changes the record key to an Avro message from JSON. Both streams write the two changes
to two separate topics. 

The Stream application makes use of Confluent transaction semantics. It includes producers and consumers in the same transaction to show end-to-end exactly once processing.

The second Confluent transaction-based application is the **_"Generate Transaction"_**. This is a simple
Spring Boot applications that makes use of a Rest Controller to receive a message and then use the Confluent API to 
send the message to Confluent. The message is placed in the _"topic.avro.transaction"_ topic. This is
the same topic that the aforementioned Stream application listens for message events. This application is used to create records for consumptions by the stream application.

A sample of the required CURL command and a sample of a JSON record required to generate the Confluent records can be found in the module in the _"PostRestData.sh"_ script.

Finally, there is an application that strictly acts as a transaction-aware Confluent consumer that makes use of the Kafka pub/sub API. This application shows what is required to use
the publication of the consumed records offsets as part of a publisher transaction. The consumer application also listens for message placed in the _"topic.avro.transaction"_ topic by 
the  **_"Generate Transaction"_** application.

All three Confluent applications are Spring applications that are based on Maven. To create the required POJO objects from the required schema you may need to execute:

`mvn generate-sources`

The applications can be run from the shell level with:

`mvn spring-boot:run`

It is required to substitute your own relevant credentials and setup details for these three modules to operate. You need to check the "pom.xml" and "application.yaml" files to update them with your details. 

Finally, there are two JMS Session Transaction applications. The first, is a simple JMS producer found in the project module named: "**_JMS_Transaction_Producer_**".
This application is a simple Spring Boot application that is compiled and run with the same Maven commands as the Confluent transaction applications. In this case the JMS producer only sends a single text messages
to the JMS vendor's queue. The current example is based on Solace's JMS broker. To use another JMS
broker simply replace the Solace dependency in the _POM_ file and change the configuration in the application.yaml file to reflect the JNDI parameters for the targeted broker. 

JMS expects administered objects to be used via JNDI calls. Therefore, all JMS configuration details are based on JNDI lookups. 
The JNDI parameters that are used by the JMS client applications are configured in the "_application.yaml_" files.

The JMS producer only sends a single JMS text message and exits. The message is sent using a JMS session transaction session.

The second JMS session transaction application is found in the **_"JMS_Transaction_Consumer"_** module. It reads the messages from 
the first queue and writes them to a second queue via a single transaction. If the transaction is successful, the message from the 
first queue should be removed and be found in the second queue. 

It is also possible for the JMS consumer application to force a rollback, which is configured in the _"applicaiton.yaml"_ file. In this case the 
message will not be moved to the second queue. When the rollback is again disabled, the messages that are in the
first queue will move to the second and the consumed records from the first queue will show that there is now a "redelivered" flag on the message since
it ws consumed but then rolled back. 

More details for Confluent employees can be found a in a detailed document that describes and contrasts the use
of JMS transactions and Confluent transactions (with exactly-once delivery semantics ). The document can be found with Glean using the following for the search parameter:

`Comparison of Transaction Processing between Confluent versus Message Brokers`



