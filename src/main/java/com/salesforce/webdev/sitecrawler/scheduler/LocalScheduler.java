package com.salesforce.webdev.sitecrawler.scheduler;

import java.util.Collection;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.salesforce.webdev.sitecrawler.scheduler.linkConsumer.LocalLinkConsumer;
import com.salesforce.webdev.sitecrawler.scheduler.pageConsumer.LinkProcessor;
import com.salesforce.webdev.sitecrawler.scheduler.pageConsumer.LocalLinkProcessor;
import com.salesforce.webdev.sitecrawler.scheduler.pageConsumer.LocalPageConsumer;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.salesforce.webdev.sitecrawler.SiteCrawler;
import com.salesforce.webdev.sitecrawler.navigation.NavigateThread;
import com.salesforce.webdev.sitecrawler.navigation.ProcessPage;
import com.salesforce.webdev.sitecrawler.utils.NamedThreadFactory;

public class LocalScheduler implements Scheduler {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SiteCrawler siteCrawler;

    /**
     * <p>A unique identifier for this particular Scheduler.</p>
     */
    private String id;

    private LocalLinkConsumer linkConsumer;
    private LocalPageConsumer pageConsumer;

    public LocalScheduler(SiteCrawler siteCrawler) {
        this.siteCrawler = siteCrawler;
    }

    public void init() {
        linkConsumer = new LocalLinkConsumer(siteCrawler.getCrawlerConfiguration());
        linkConsumer.init();

        pageConsumer = new LocalPageConsumer(siteCrawler.getCrawlerConfiguration());
        pageConsumer.init();

        LinkProcessor linkProcessor = new LocalLinkProcessor(siteCrawler);
        pageConsumer.setLinkProcessor(linkProcessor);

        linkConsumer.setNextInChain(pageConsumer.getPageService());
    }

    /**
     * <p>This allows an end-user to set their own special "ID" for this particular crawl.</p>
     * 
     * <p>This will be reflected in the thread names (the ID will be prefixed for all new threads).</p>
     * 
     * <p>The slf4j framework will get the ID injected via {@link MDC} with name "crawlId".</p>
     * 
     * @param id A non-blank ID (blank IDs will be ignored)
     */
    public void setId(String id) {
        if (StringUtils.isBlank(id)) {
            return;
        }
        this.id = id;
        MDC.put("crawlId", id);
        linkConsumer.setId(id);
        pageConsumer.setId(id);
    }

    /**
     * <p>Return the Id.</p>
     * 
     * @return String Id (null if not set)
     */
    public String getId() {
        return id;
    }

    public void pause() {
        // wait for consumers to be empty
        waitForLinkServiceConsumer();
        waitForPageServiceConsumer();
    }

    public void unpause() {
        linkConsumer.startLinkServiceConsumer();
        pageConsumer.startPageServiceConsumer();
    }

    /**
     * <p>Tell the executors ({@link #linkConsumer} and {@link #pageConsumer} to shutdown.</p>
     */
    public void shutdown() {
        linkConsumer.shutdown();
        pageConsumer.shutdown();

        if (null != linkConsumer) {
            while (linkConsumer.isAlive()) {
                try {
                    logger.info("Waiting for the linkServiceConsumer thread to die...");
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException e) {
                    logger.error("Something happened while waiting for linkServiceConsumer to be shutdown", e);
                    Thread.currentThread().interrupt();
                }
            }
            logger.info("... linkServiceConsumer thread is dead");
        }

        if (null != pageConsumer) {
            while (pageConsumer.isAlive()) {
                try {
                    logger.info("Waiting for the pageServiceConsumer thread to die...");
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException e) {
                    logger.error("Something happened while waiting for pageServiceConsumer to be shutdown", e);
                    Thread.currentThread().interrupt();
                }
            }
            logger.info("... pageServiceConsumer thread is dead");
        }
    }

    public void offerUrl(String url) {
        linkConsumer.offerUrl(url);
    }

    /**
     * <p>Waits for all the links to be processed, returns when the {@link SiteCrawler#getLinksScheduled()} queue is empty.</p>
     */
    private void waitForLinkServiceConsumer() {
        logger.info("Shutting down LinkServiceConsumer");
        final int secondsToWaitBetweenChecks = 5;
        while (siteCrawler.getLinksScheduled() > 0) {
            logger.info("Waiting for {} links to be consumed...", siteCrawler.getLinksScheduled());

            if (!siteCrawler.getContinueProcessing()) {
                logger.warn("waitForLinkServiceConsumer has been told to stop waiting..");
                return;
            }

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(secondsToWaitBetweenChecks));
            } catch (InterruptedException e) {
                logger.error("Interruped while waiting {} seconds for the links to be consumed, stopping :(", secondsToWaitBetweenChecks, e);
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * <p>Waits for all the pages to be processed, returns when the {@link SiteCrawler#getPagesScheduled()} queue is empty.</p>
     */
    private void waitForPageServiceConsumer() {
        logger.info("Shutting down PageServiceConsumer");
        final int secondsToWaitBetweenChecks = 5;
        while (siteCrawler.getPagesScheduled() > 0) {
            logger.info("Waiting for {} pages to be consumed...", siteCrawler.getPagesScheduled());

            if (!siteCrawler.getContinueProcessing()) {
                logger.warn("waitForPageServiceConsumer has been told to stop waiting..");
                return;
            }

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(secondsToWaitBetweenChecks));
            } catch (InterruptedException e) {
                logger.error("Interruped while waiting {} seconds for the links to be consumed, stopping :(", secondsToWaitBetweenChecks, e);
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
