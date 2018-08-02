package com.salesforce.webdev.sitecrawler.scheduler.rabbitmq;

import com.rabbitmq.client.*;
import com.salesforce.webdev.sitecrawler.SiteCrawler;
import com.salesforce.webdev.sitecrawler.scheduler.Scheduler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class RabbitMqScheduler implements Scheduler {

    /**
     * <p>Logger</p>
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * <p>A unique identifier for this particular Scheduler.</p>
     */
    private String id;

    private RabbitMqConnection rabbitMqConnection;
    private RabbitMqConnection incomingUnverifiedConnection;

    private boolean initialized;

    private final SiteCrawler siteCrawler;
    public RabbitMqScheduler(SiteCrawler siteCrawler) {
        this.siteCrawler = siteCrawler;
    }

    @Override
    public void init() {
        try {
            rabbitMqConnection = new RabbitMqConnection();
            rabbitMqConnection.connect();
            rabbitMqConnection.createChannel();

            // Users are allowed to set their own Ids up-front
            if (null == id) {
                String uuid = UUID.randomUUID().toString();
                setId(uuid);
            } else {
                setId(id);
            }

            listenForNewUrls("cir-unverified-urls");
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setId(String id) {
        if (StringUtils.isBlank(id)) {
            return;
        }

        // TODO Should be configurable
        try {
            this.id = id;
            if (null == rabbitMqConnection) {
                logger.error("No connection initialized yet");
                initialized = false;
            } else {
                rabbitMqConnection.createExchange(id, id);
                initialized = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

        @Override
    public String getId() {
        return id;
    }

    @Override
    public void pause() {

    }

    @Override
    public void unpause() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void offerUrl(String url) {
        if (!initialized) {
            logger.info("Scheduler was not initialized yet, calling that now");
            init();
        }
        try {
            logger.trace("publishing {} to queue", url);
            rabbitMqConnection.publishMessage(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenForNewUrls(String exchangeName) throws IOException, TimeoutException {
        incomingUnverifiedConnection = new RabbitMqConnection();
        incomingUnverifiedConnection.connect();
        incomingUnverifiedConnection.createChannel();
        incomingUnverifiedConnection.createExchange(exchangeName, exchangeName);

        final Channel channel = incomingUnverifiedConnection.getChannel();
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body)
                    throws IOException {
                String routingKey = envelope.getRoutingKey();
                String contentType = properties.getContentType();
                long deliveryTag = envelope.getDeliveryTag();
                // (process the message components here ...)

                String url = new String(body);
                logger.info("Parsing URL from queue: {}", url);
                siteCrawler.offerUrl(url);

                // TODO Move this to the completion service
                incomingUnverifiedConnection.getChannel().basicAck(deliveryTag, false);
            }
        };

        UUID uuid = UUID.randomUUID();
        String consumerTag = uuid + "-" + exchangeName + "-consumer";

        incomingUnverifiedConnection.createDefaultConsumer(consumer, consumerTag);
    }
}
