package com.salesforce.webdev.sitecrawler.scheduler.pageConsumer;

import com.salesforce.webdev.sitecrawler.SiteCrawler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class LocalLinkProcessor implements LinkProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SiteCrawler siteCrawler;

    public LocalLinkProcessor(SiteCrawler siteCrawler) {
        this.siteCrawler = siteCrawler;
    }

    public boolean processNewUrl(String newToVisit) {
        if (siteCrawler.isExcluded(newToVisit)) {
            logger.trace("NOT adding link since it is excluded: {}", newToVisit);
            return false;
        }
        if (siteCrawler.isScheduled(newToVisit)) {
            logger.trace("NOT adding link since it is already scheduled: {}", newToVisit);
            return false;
        }
        logger.trace("Adding link to the list: {}", newToVisit);
        siteCrawler.offerUrl(newToVisit);
        return true;
    }

}
