package com.salesforce.webdev.sitecrawler.scheduler.pageConsumer;

import com.salesforce.webdev.sitecrawler.SiteCrawler;
import com.salesforce.webdev.sitecrawler.beans.CrawlerConfiguration;
import com.salesforce.webdev.sitecrawler.utils.NamedThreadFactory;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.*;

/**
 * The PageConsumer adds the found URLs back to the SiteCrawler for further consideration
 */
public class LocalPageConsumer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * <p>The factory we use to name the individual threads for the pageExecutor. We keep this as part of the SiteCrawler class in case we "reset" and, so the number will increase
     * with each reset.</p>
     */
    private ThreadFactory pageExecutorThreadFactory = new NamedThreadFactory("pageExecutor");

    /**
     * <p>We keep track of the pageExecutor thread in case of a reset or shutdown, so we can wait for it do "die" properly.</p>
     */
    private Thread pageServiceConsumer;

    /**
     * <p>This Executor determines how many downloaded pages we should process in parallel. The number is set by this simple rule:<br /> <code>thread limit = {@link SiteCrawler#getThreadLimit()} *
     * {@link SiteCrawler#getDownloadVsProcessRatio()}</code></p>
     */
    private ExecutorService pageExecutor;

    private CrawlerConfiguration crawlerConfiguration;

    private String id;

    private boolean continueProcessing;

    private LinkProcessor linkProcessor;

    public LocalPageConsumer(CrawlerConfiguration crawlerConfiguration) {
        this.crawlerConfiguration = crawlerConfiguration;
    }

    /**
     * <p>This processes the downloaded pages.</p>
     */
    private CompletionService<Collection<String>> pageService;

    public void init() {
        int pageExecutorSize = (int) Math.ceil(crawlerConfiguration.threadLimit * crawlerConfiguration.downloadVsProcessRatio);
        pageExecutor = Executors.newFixedThreadPool(pageExecutorSize, pageExecutorThreadFactory);
        pageService = new ExecutorCompletionService<>(pageExecutor);

        continueProcessing = true;

        // in bytes
        long maxHeap = Runtime.getRuntime().maxMemory();
        // to mb
        double gbMaxHeap = maxHeap / (1024.0 * 1024.0);
        // Final result, rounded
        int maxProcessWaiting = (int) (gbMaxHeap * crawlerConfiguration.maxProcessWaitingRatio);
        // TODO Not sure if this translates into any meaningful action
        crawlerConfiguration.maxProcessWaiting = maxProcessWaiting;

        Object[] args = { pageExecutorSize, maxProcessWaiting, crawlerConfiguration.threadLimit };
        logger.info("Scheduler created with pageExecutor with size {}, maxProcessWaiting={}, , linkExecutor with size {}", args);
    }

    public void setId(String id) {
        this.id = id;
        pageExecutorThreadFactory = new NamedThreadFactory(id + "-pageExecutor");
    }

    public void setLinkProcessor(LinkProcessor linkProcessor) {
        this.linkProcessor = linkProcessor;
    }

    public void shutdown() {
        if (null != pageExecutor) {
            pageExecutor.shutdown();
            try {
                pageExecutor.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                logger.error("Something happened while waiting for pageExecutor to be shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isAlive() {
        if (null != pageServiceConsumer) {
            return pageServiceConsumer.isAlive();
        }
        return false;
    }

    /**
     * <p>Processed the pages after all links are discovered. The new links are added to the queue.</p>
     */
    public void startPageServiceConsumer() {
        Runnable r = new Runnable() {

            @Override
            public void run() {
                while (continueProcessing) {
                    Future<Collection<String>> result = null;
                    try {
                        result = pageService.poll(5, TimeUnit.SECONDS);
                        if (null == result) {
                            continue;
                        }
                        Collection<String> newToVisits = result.get();
                        logger.trace("Retrieved a collection of links of size: {}...", newToVisits.size());
                        for (String newToVisit : newToVisits) {
                            logger.trace("Processing a new link {}", newToVisit);
                            logger.info("linkProcessor OUTSIDE (1) -> {}", newToVisit);
                            if (null != linkProcessor) {
                                logger.info("linkProcessor INSIDE (2) -> {}", newToVisit);
                                linkProcessor.processNewUrl(newToVisit);
                            }
                        }
                    } catch (InterruptedException e) {
                        logger.error("[startPageServiceConsumer] Interruped while trying to work with result {}", result, e);
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("[startPageServiceConsumer] Something went wrong trying to work with result {}", result, e);
                    } finally {
                        //TODO Restore
//                        if (result != null) {
//                            siteCrawler.incrementFullyProcessed();
//                            siteCrawler.decrementScheduled();
//                        }
                    }
                }
            }
        };
        pageServiceConsumer = new Thread(r);
        pageServiceConsumer.setDaemon(false);
        String name = (StringUtils.isNotBlank(id) ? id + "-" + "pageServiceConsumer" : "pageServiceConsumer");
        pageServiceConsumer.setName(name);
        pageServiceConsumer.start();
    }

    public CompletionService<Collection<String>> getPageService() {
        return pageService;
    }
}
