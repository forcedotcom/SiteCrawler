package com.salesforce.webdev.sitecrawler.scheduler;

public interface Scheduler {

    void init();
    void setId(String id);
    String getId();
    void pause();
    void unpause();
    void shutdown();
    void offerUrl(String url);
}
