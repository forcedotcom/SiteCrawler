package com.salesforce.webdev.sitecrawler.scheduler.pageConsumer;

public interface LinkProcessor {
    boolean processNewUrl(String url);
}
