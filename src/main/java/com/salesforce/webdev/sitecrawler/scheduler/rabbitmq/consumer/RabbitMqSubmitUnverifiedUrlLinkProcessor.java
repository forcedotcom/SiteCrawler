package com.salesforce.webdev.sitecrawler.scheduler.rabbitmq.consumer;

import com.salesforce.webdev.sitecrawler.scheduler.pageConsumer.LinkProcessor;
import com.salesforce.webdev.sitecrawler.scheduler.rabbitmq.RabbitMqConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RabbitMqSubmitUnverifiedUrlLinkProcessor implements LinkProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private RabbitMqConnection rabbitMqConnection;

    private final String exchangeName;


    public RabbitMqSubmitUnverifiedUrlLinkProcessor(String exchangeName) {
        this.exchangeName = exchangeName;
        init();
    }

    public void init() {
        // Create the RabbitMQ connection
        rabbitMqConnection = new RabbitMqConnection();
        try {
            rabbitMqConnection.connect();
            rabbitMqConnection.createChannel();
            rabbitMqConnection.createExchange(exchangeName, exchangeName);
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
    /**
     * Give this back to RabbitMQ in the "unverified repository
     * @param url
     * @return
     */
    @Override
    public boolean processNewUrl(String url) {
        try {
            logger.info("Pushing {} to the {} exchange", url, exchangeName);
            rabbitMqConnection.publishMessage(url);
        } catch (IOException e) {
            logger.error("Unable to publish {} to RabbitMQ", url, e);
            return false;
        }
        return true;
    }
}
