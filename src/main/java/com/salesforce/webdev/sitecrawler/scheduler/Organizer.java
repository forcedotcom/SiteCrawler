package com.salesforce.webdev.sitecrawler.scheduler;

public class Organizer {

    private final Scheduler scheduler;

    public Organizer(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void submitUrl(String url) {
        scheduler.offerUrl(url);
    }
}
