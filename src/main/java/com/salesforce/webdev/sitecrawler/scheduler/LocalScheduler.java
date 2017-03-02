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
    private final LocalWcPool localWcPool = new LocalWcPool();

    /**
     * <p>A unique identifier for this particular Scheduler.</p>
     */
    private String id;

    /**
     * <p>The factory we use to name the individual threads for the linkExecutor. We keep this as part of the SiteCrawler class in case we "reset" and, so the number will increase
     * with each reset.</p>
     */
    private ThreadFactory linkExecutorThreadFactory = new NamedThreadFactory("linkExecutor");

    /**
     * <p>The factory we use to name the individual threads for the pageExecutor. We keep this as part of the SiteCrawler class in case we "reset" and, so the number will increase
     * with each reset.</p>
     */
    private ThreadFactory pageExecutorThreadFactory = new NamedThreadFactory("pageExecutor");

    /**
     * <p>We keep track of the linkExecutor thread in case of a reset or shutdown, so we can wait for it do "die" properly.</p>
     */
    private Thread linkServiceConsumer;

    /**
     * <p>We keep track of the pageExecutor thread in case of a reset or shutdown, so we can wait for it do "die" properly.</p>
     */
    private Thread pageServiceConsumer;

    /**
     * <p>This processes the downloaded pages.</p>
     */
    private CompletionService<Collection<String>> pageService;

    /**
     * <p>This Executor determines how many I/O (network) threads to use to crawl the site (thread limit set by {@link #threadLimit}).</p>
     */
    private ExecutorService linkExecutor;

    /**
     * <p>This navigates and downloads the pages.</p>
     */
    private CompletionService<ProcessPage> linkService;

    /**
     * <p>This Executor determines how many downloaded pages we should process in parallel. The number is set by this simple rule:<br /> <code>thread limit = {@link #threadLimit} *
     * {@link #downloadVsProcessRatio}</code></p>
     */
    private ExecutorService pageExecutor;

    public LocalScheduler(SiteCrawler siteCrawler) {
        this.siteCrawler = siteCrawler;
    }

    public void init() {
        localWcPool.init(siteCrawler);
        linkExecutor = Executors.newFixedThreadPool(siteCrawler.getThreadLimit(), linkExecutorThreadFactory);
        linkService = new ExecutorCompletionService<ProcessPage>(linkExecutor);

        int pageExecutorSize = (int) Math.ceil(siteCrawler.getThreadLimit() * siteCrawler.getDownloadVsProcessRatio());
        pageExecutor = Executors.newFixedThreadPool(pageExecutorSize, pageExecutorThreadFactory);
        pageService = new ExecutorCompletionService<Collection<String>>(pageExecutor);

        // in bytes
        long maxHeap = Runtime.getRuntime().maxMemory();
        // to mb
        double gbMaxHeap = maxHeap / (1024.0 * 1024.0);
        // Final result, rounded
        int maxProcessWaiting = (int) (gbMaxHeap * siteCrawler.getMaxProcessWaitingRatio());
        siteCrawler.setMaxProcessWaiting(maxProcessWaiting);

        Object[] args = { pageExecutorSize, maxProcessWaiting, siteCrawler.getThreadLimit() };
        logger.info("Scheduler created with pageExecutor with size {}, maxProcessWaiting={}, , linkExecutor with size {}", args);
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
        linkExecutorThreadFactory = new NamedThreadFactory(id + "-linkExecutor");
        pageExecutorThreadFactory = new NamedThreadFactory(id + "-pageExecutor");
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
        startLinkServiceConsumer();
        startPageServiceConsumer();
    }

    /**
     * <p>Tell the executors ({@link #linkExecutor} and {@link #pageExecutor} to shutdown.</p>
     */
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

        if (null != pageExecutor) {
            pageExecutor.shutdown();
            try {
                pageExecutor.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                logger.error("Something happened while waiting for pageExecutor to be shutdown", e);
                Thread.currentThread().interrupt();
            }
        }

        if (null != linkServiceConsumer) {
            while (linkServiceConsumer.isAlive()) {
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

        if (null != pageServiceConsumer) {
            while (pageServiceConsumer.isAlive()) {
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

    /**
     * <p>The linkService takes the pages that are scheduled to be visited and executes.</p>
     * 
     * <p>After downloading the page, we submit the result to the {@link #pageService} to be processed.</p>
     */
    private void startLinkServiceConsumer() {
        Runnable r = new Runnable() {

            @Override
            public void run() {
                while (siteCrawler.getContinueProcessing()) {

                    try {
                        if (shouldPauseProcessing()) {
                            if (siteCrawler.isPaused()) {
                                logger.trace("[startLinkServiceConsumer] Crawler is hardpaused...");
                            } else {
                                logger.debug("[startLinkServiceConsumer] Analyzing pages (pausing crawling to allow the consumers to catch up)...");
                            }
                            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
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
                        siteCrawler.incrementVisited();
                        ProcessPage processPage = result.get();

                        // This happens AFTER a NavigateThread was successful
                        processPage.setActions(siteCrawler.getCrawlerConfiguration().actions);
                        processPage.setBaseUrl(siteCrawler.getCrawlerConfiguration().baseUrl);
                        processPage.setBaseUrlSecure(siteCrawler.getCrawlerConfiguration().baseUrlSecure);

                        logger.trace("Submitting a new ProcessPage object");
                        pageService.submit(processPage);
                        siteCrawler.incrementScheduled();
                    } catch (InterruptedException e) {
                        logger.error("[startLinkServiceConsumer] Interruped while trying to work with result {}", result, e);
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("[startLinkServiceConsumer] Something went wrong trying to work with result {}", result, e);
                    } catch (RejectedExecutionException e) {
                        logger.warn("[startLinkServiceConsumer] Tried to add a ProcessPage [Future: {}], but this was rejected (shutdown in progress?)", result, e);
                    } finally {
                        if (result != null) {
                            siteCrawler.decrementLinksScheduled();
                        }
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

    public void offerUrl(String url) {
        NavigateThread navigateThread = new NavigateThread(url, localWcPool.getWebClientPool());
        try {
            linkService.submit(navigateThread);
        } catch (RejectedExecutionException e) {
            logger.warn("Tried to add a NavigateThread for {}, but this was rejected (shutdown in progress?)", url, e);
        }

    }

    /**
     * <p>Waits for all the links to be processed, returns when the {@link #linksScheduled} queue is empty.</p>
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
     * <p>Processed the pages after all links are discovered. The new links are added to the queue.</p>
     */
    private void startPageServiceConsumer() {
        Runnable r = new Runnable() {

            @Override
            public void run() {
                while (siteCrawler.getContinueProcessing()) {
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
                            if (siteCrawler.isExcluded(newToVisit)) {
                                logger.trace("NOT adding link since it is excluded: {}", newToVisit);
                                continue;
                            }
                            if (siteCrawler.isScheduled(newToVisit)) {
                                logger.trace("NOT adding link since it is already scheduled: {}", newToVisit);
                                continue;
                            }
                            logger.trace("Adding link to the list: {}", newToVisit);
                            siteCrawler.offerUrl(newToVisit);
                        }
                    } catch (InterruptedException e) {
                        logger.error("[startPageServiceConsumer] Interruped while trying to work with result {}", result, e);
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("[startPageServiceConsumer] Something went wrong trying to work with result {}", result, e);
                    } finally {
                        if (result != null) {
                            siteCrawler.incrementFullyProcessed();
                            siteCrawler.decrementScheduled();
                        }
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

    /**
     * <p>Waits for all the pages to be processed, returns when the {@link #pagesScheduled} queue is empty.</p>
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

    /**
     * <p>If there are too many pages scheduled, this will return true to inform clients to pause the crawling.</p>
     * 
     * @return true if the process queue is too large
     */
    private boolean shouldPauseProcessing() {
        boolean shouldPause = siteCrawler.getPagesScheduled() > siteCrawler.getCrawlerConfiguration().maxProcessWaiting || siteCrawler.isPaused();
        logger.trace("ShouldPause=" + shouldPause + ", pagesScheduled=" + siteCrawler.getPagesScheduled());
        return shouldPause;
    }
}
