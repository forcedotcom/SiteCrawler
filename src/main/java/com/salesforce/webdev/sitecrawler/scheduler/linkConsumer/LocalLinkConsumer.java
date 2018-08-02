package com.salesforce.webdev.sitecrawler.scheduler.linkConsumer;

import com.salesforce.webdev.sitecrawler.SiteCrawler;
import com.salesforce.webdev.sitecrawler.beans.CrawlerConfiguration;
import com.salesforce.webdev.sitecrawler.navigation.NavigateThread;
import com.salesforce.webdev.sitecrawler.navigation.ProcessPage;
import com.salesforce.webdev.sitecrawler.scheduler.LocalWcPool;
import com.salesforce.webdev.sitecrawler.utils.NamedThreadFactory;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;

public class LocalLinkConsumer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * <p>A unique identifier for this particular consumer.</p>
     */
    private String id;

    private final LocalWcPool localWcPool = new LocalWcPool();

    private final CrawlerConfiguration crawlerConfiguration;

    private boolean continueProcessing;
    private boolean paused;
    /**
     * <p>This Executor determines how many I/O (network) threads to use to crawl the site (thread limit set by {@link SiteCrawler#getThreadLimit()}).</p>
     */
    private ExecutorService linkExecutor;
    /**
     * <p>The factory we use to name the individual threads for the linkExecutor. We keep this as part of the SiteCrawler class in case we "reset" and, so the number will increase
     * with each reset.</p>
     */
    private ThreadFactory linkExecutorThreadFactory = new NamedThreadFactory("linkExecutor");


    /**
     * <p>We keep track of the linkExecutor thread in case of a reset or shutdown, so we can wait for it do "die" properly.</p>
     */
    private Thread linkServiceConsumer;

    private CompletionService<Collection<String>> nextInChain;
    /**
     * <p>This navigates and downloads the pages.</p>
     */
    private CompletionService<ProcessPage> linkService;

    public LocalLinkConsumer(CrawlerConfiguration crawlerConfiguration) {
        this.crawlerConfiguration = crawlerConfiguration;
    }

    public void init() {
        localWcPool.init(crawlerConfiguration);
        linkExecutor = Executors.newFixedThreadPool(crawlerConfiguration.threadLimit, linkExecutorThreadFactory);
        linkService = new ExecutorCompletionService<>(linkExecutor);

        continueProcessing = true;
        paused = false;
    }

    public void setNextInChain(CompletionService<Collection<String>> nextInChain) {
        this.nextInChain = nextInChain;
    }

    public void setId(String id) {
        if (StringUtils.isBlank(id)) {
            return;
        }
        this.id = id;
        linkExecutorThreadFactory = new NamedThreadFactory(id + "-linkExecutor");
    }

    public void shutdown() {
        localWcPool.shutdown();

        if (null != linkExecutor) {
            linkExecutor.shutdown();
            try {
                linkExecutor.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                logger.error("Something happened while waiting for linkExecutor to be shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void offerUrl(String url) {
        offerUrl(url, null);
    }

    public void offerUrl(String url, Map<String, String> extraInformation) {
        NavigateThread navigateThread = new NavigateThread(url, localWcPool.getWebClientPool(), extraInformation);
        try {
            linkService.submit(navigateThread);
        } catch (RejectedExecutionException e) {
            logger.warn("Tried to add a NavigateThread for {}, but this was rejected (shutdown in progress?)", url, e);
        }
    }

    public boolean isAlive() {
        if (null != linkServiceConsumer) {
            return linkServiceConsumer.isAlive();
        }
        return false;
    }
    /**
     * <p>The linkService takes the pages that are scheduled to be visited and executes.</p>
     *
     * <p>After downloading the page, we submit the result to the {@link #nextInChain} to be processed.</p>
     */
    public void startLinkServiceConsumer() {
        Runnable r = new Runnable() {

            @Override
            public void run() {
                while (continueProcessing) {

                    try {
                        if (isPaused()) {
                            int secondsToSleep = 5;
                            logger.trace("[startLinkServiceConsumer] Consumer is hardpaused, sleeping for " + secondsToSleep + "seconds...");
                            Thread.sleep(TimeUnit.SECONDS.toMillis(secondsToSleep));
                            continue;
                        }
                    } catch (InterruptedException e) {
                        logger.error("startLinkServiceConsumer got interrupted, stopping...");
                        Thread.currentThread().interrupt();
                        return;
                    }

                    Future<ProcessPage> result = null;
                    try {
                        result = linkService.poll(5, TimeUnit.SECONDS);
                        if (null == result) {
                            continue;
                        }
                        ProcessPage processPage = result.get();

                        // This happens AFTER a NavigateThread was successful
                        processPage.setActions(crawlerConfiguration.actions);
                        processPage.setBaseUrl(crawlerConfiguration.baseUrl);
                        processPage.setBaseUrlSecure(crawlerConfiguration.baseUrlSecure);

                        if (null != nextInChain) {
                            logger.trace("Submitting a new ProcessPage object to nextInChain");
                            nextInChain.submit(processPage);
                        }
                    } catch (InterruptedException e) {
                        logger.error("[startLinkServiceConsumer] Interruped while trying to work with result {}", result, e);
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("[startLinkServiceConsumer] Something went wrong trying to work with result {}", result, e);
                    } catch (RejectedExecutionException e) {
                        logger.warn("[startLinkServiceConsumer] Tried to add a ProcessPage [Future: {}], but this was rejected (shutdown in progress?)", result, e);
                    }
                }
            }
        };
        linkServiceConsumer = new Thread(r);
        linkServiceConsumer.setDaemon(false);
        String name = (StringUtils.isNotBlank(id) ? id + "-" + "linkServiceConsumer" : "linkServiceConsumer");
        linkServiceConsumer.setName(name);
        linkServiceConsumer.start();
    }

    /**
     * <p>If there are too many pages scheduled, this will return true to inform clients to pause the crawling.</p>
     *
     * @return true if the process queue is too large
     */
    private boolean isPaused() {
        logger.trace("paused=" + paused);
        return paused;
    }
}
