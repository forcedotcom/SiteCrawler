package com.salesforce.webdev.sitecrawler.scheduler.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * See https://www.rabbitmq.com/api-guide.html
 */
public class RabbitMqConnection {

    private final String userName = "eyxcamci";
    private final String password = "yVxBTwtbv6ICXA9sanJ2Ec7XS9430bw_";
    private final String virtualHost = userName;
    private final String hostName = "sheep.rmq.cloudamqp.com";
    //private final int portNumber = 443;

    private Connection connection;
    private Channel channel;
    private String queueName;

    private String exchangeName;
    private String routingKey;

    public void connect() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();

        factory.setUsername(userName);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        factory.setHost(hostName);
        //factory.setPort(portNumber);

        connection = factory.newConnection();
    }

    public void createChannel() throws IOException {
        channel = connection.createChannel();
        channel.basicQos(10);
    }

    public void disconnect() throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }

    public void createExchange(String exchangeName, String routingKey) throws IOException {
        channel.exchangeDeclare(exchangeName, "direct", true);
        queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, exchangeName, routingKey);

        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
    }

    public void publishMessage(String message) throws IOException {
        byte[] messageBytes = message.getBytes();
        channel.basicPublish(exchangeName, routingKey, null, messageBytes);
    }

    public void createDefaultConsumer(Consumer consumer, String consumerTag) throws IOException {
        boolean autoAck = false;
        channel.basicConsume(queueName, autoAck, consumerTag, consumer);
    }

    public Channel getChannel() {
        return this.channel;
    }
}
