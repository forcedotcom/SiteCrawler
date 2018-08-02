package com.salesforce.webdev.sitecrawler.scheduler.rabbitmq.consumer;

import com.rabbitmq.client.*;
import com.salesforce.webdev.sitecrawler.beans.CrawlerConfiguration;
import com.salesforce.webdev.sitecrawler.scheduler.linkConsumer.LocalLinkConsumer;
import com.salesforce.webdev.sitecrawler.scheduler.pageConsumer.LinkProcessor;
import com.salesforce.webdev.sitecrawler.scheduler.pageConsumer.LocalPageConsumer;
import com.salesforce.webdev.sitecrawler.scheduler.rabbitmq.RabbitMqConnection;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * This is the main entrypoint for a stand-alone Link crawler
 */
public class RabbitUrlCrawler {

    public static void main(String[] args) throws Exception {
        // Start a RabbitMqConsumer

        String exchangeName = System.getenv("exchangeName");
        String returnExchangeName = System.getenv("returnExchangeName");

        if (StringUtils.isBlank(exchangeName)) {
            throw new Exception("set exchangeName as an env variable");
        }

        if (StringUtils.isBlank(returnExchangeName)) {
            throw new Exception("set returnExchangeName as an env variable");
        }


        RabbitUrlCrawler crawler = new RabbitUrlCrawler();
        crawler.init(exchangeName, returnExchangeName);

        // Actually start waiting for URLs to crawl
        UUID uuid = UUID.randomUUID();
        String consumerTag = uuid + "-" + exchangeName + "-consumer";
        crawler.start(consumerTag);

    }

    /**
     * <p>Logger</p>
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private RabbitMqConnection rabbitMqConnection;


    private LocalLinkConsumer linkConsumer;
    private LocalPageConsumer pageConsumer;

    public void init(String exchangeName, String unverifiedUrlExchange) throws IOException, TimeoutException {
        // Create the RabbitMQ connection
        rabbitMqConnection = new RabbitMqConnection();
        rabbitMqConnection.connect();
        rabbitMqConnection.createChannel();
        rabbitMqConnection.createExchange(exchangeName, exchangeName);

        // Create a way to actually parse the URL
        CrawlerConfiguration crawlerConfiguration = new CrawlerConfiguration();
        crawlerConfiguration.threadLimit = 5;
        crawlerConfiguration.downloadVsProcessRatio = 2;
        crawlerConfiguration.maxProcessWaitingRatio = 0.4;

        linkConsumer = new LocalLinkConsumer(crawlerConfiguration);
        linkConsumer.init();
        pageConsumer = new LocalPageConsumer(crawlerConfiguration);
        pageConsumer.init();
        linkConsumer.setNextInChain(pageConsumer.getPageService());

        LinkProcessor linkProcessor = new RabbitMqSubmitUnverifiedUrlLinkProcessor(unverifiedUrlExchange);
        pageConsumer.setLinkProcessor(linkProcessor);

        linkConsumer.startLinkServiceConsumer();
        pageConsumer.startPageServiceConsumer();
    }

    public void start(String consumerTag) throws IOException {
        linkConsumer.init();

        final Channel channel = rabbitMqConnection.getChannel();
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
                linkConsumer.offerUrl(url);

                // TODO Move this to the completion service
                rabbitMqConnection.getChannel().basicAck(deliveryTag, false);
            }
        };

        rabbitMqConnection.createDefaultConsumer(consumer, consumerTag);

    }
}
