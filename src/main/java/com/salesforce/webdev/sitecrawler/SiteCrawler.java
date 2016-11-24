/*******************************************************************************
 * Copyright (c) 2014, Salesforce.com, Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.salesforce.webdev.sitecrawler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.salesforce.webdev.sitecrawler.beans.CrawlProgress;
import com.salesforce.webdev.sitecrawler.beans.CrawlerConfiguration;
import com.salesforce.webdev.sitecrawler.navigation.NavigateThread;
import com.salesforce.webdev.sitecrawler.navigation.ProcessPage;
import com.salesforce.webdev.sitecrawler.utils.NamedThreadFactory;
import com.salesforce.webdev.sitecrawler.utils.URLCleaner;
import com.salesforce.webdev.sitecrawler.webclient.WebClientPool;

/**
 * <p>This class is the central hub and referee between our network spider (NavigateThread) and our page (/HTML) parser (ProcessPage).</p>
 * 
 * <p>It controls a pool of WebClients, which are used by the NavigateThread to spider the site.</p>
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public class SiteCrawler {

    /**
     * <p>Logger</p>
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * <p>A unique identifier for this particular Crawler.</p>
     */
    private String id;

    /**
     * <p>The base URL of the site, preferably the "non-https" version.</p>
     */
    private final String baseUrl;

    /**
     * <p>The base URL of the HTTPS version of the site. Can be null if there is none.</p>
     */
    private final String baseUrlSecure;

    /**
     * <p>The actions to be called on every finished {@link NavigateThread}.</p>
     */
    private final List<? extends SiteCrawlerAction> actions;

    /**
     * <p>This restricts what kind of pages are considered "parsable" pages.</p>
     * 
     * <p>By default, this contains a default list (/, JSP, HTM and HTML extensions).</p>
     */
    private Collection<String> allowedSuffixes = new ArrayList<String>();

    /**
     * <p>Some sites don't use suffixes. This allows turning that off. Otherwise, only pages in {@link #allowedSuffixes} are allowed.</p>
     */
    private boolean requireAllowedSuffixes = true;

    /**
     * <p>The collection of URLs we have crawled / check against for uniqueness.</p>
     */
    private Set<String> visited = new ConcurrentSkipListSet<String>();

    /**
     * <p>"visited" is an unreliable source for counting. This aims to correct that.</p>
     */
    private AtomicInteger visitedCounter = new AtomicInteger();

    /**
     * <p>The collection of URLs we have yet to crawl.</p>
     */
    private LinkedBlockingDeque<String> toVisit = new LinkedBlockingDeque<String>();

    /**
     * <p>Collection of URLs (or patterns without URLs) that should NOT be crawled.</p>
     */
    private Collection<String> blocked = new ConcurrentSkipListSet<String>();

    /**
     * <p>Collection of URLs (or patterns without URLs) that should ONLY be crawled.</p>
     */
    private Collection<String> allowed = new ConcurrentSkipListSet<String>();

    /**
     * <p>The amount of I/O threads / webclients to use. Defaults to the # of available processors.</p>
     */
    private int threadLimit = Runtime.getRuntime().availableProcessors();

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
     * <p>This is the ratio of I/O threads vs processing of downloaded pages. Defaults to <code>2.0</code>.</p>
     * 
     * <p>Example, if this is set to "2.0", that means it's X (say, 12) download threads VS 2X (24 in that case) parse threads).</p>
     */
    private double downloadVsProcessRatio = 2;

    /**
     * <p>This determines the amount of heap used for storing unprocessed pages. Should be between 0 and 1.</p>
     * 
     * <p>Example, if the max heap (Xmx) is set to 8Gb, a ratio of 0.4 means roughly 3276 (8 * 1024 * 0.4) Mb of heap taken for storing downloading pages.</p>
     */
    private double maxProcessWaitingRatio = 0.4;

    /**
     * <p>If there are more pages then this waiting to be processing, we pause the crawling to avoid exhausting the memory.</p>
     * 
     * <p>Keep in mind that each page/process waiting costs about 1Mb in memory. So, a value of 500 means a <em>heap requirement of 500Mb</em>.</p>
     * 
     * <p>This is partly controlled by {@link #maxProcessWaitingRatio}.</p>
     */
    private int maxProcessWaiting;

    /**
     * <p>For the regularly updates, this dictates how often we should print that update.</p>
     */
    private final int reportProgressPerDownloadedPages = 2000;

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
    /**
     * <p>This processes the downloaded pages.</p>
     */
    private CompletionService<Collection<String>> pageService;

    /**
     * <p>The pool is used to provide {@link WebClient}s to the {@link #linkService}.</p>
     * 
     * <p>By default, it is initialized to the same amount of clients as the Service has threads ({@link #threadLimit} .</p>
     */
    private WebClientPool wcPool;

    /**
     * <p>Internal counter, keeping track of how pages links still need to be retrieved.</p>
     */
    private AtomicInteger linksScheduled = new AtomicInteger();

    /**
     * <p>Internal counter, keeping track of how many downloaded pages still need to be processed.</p>
     */
    private AtomicInteger pagesScheduled = new AtomicInteger();

    /**
     * <p>Internal counter, keeping track of how many pages we have completely processed (and discarded).</p>
     */
    private AtomicInteger actuallyVisited = new AtomicInteger();

    /**
     * <p>Internal counter to count how many pages have been downloaded an fully processed.</p> (This is different from actuallyVisited, since that decreases too!)
     */
    private AtomicInteger fullyProcessed = new AtomicInteger();

    /**
     * <p>If the crawler is running or not.</p>
     */
    private boolean running = false;
    /**
     * <p>This tells all the other threads to stop processing information!.</p>
     */
    private volatile boolean continueProcessing = true;

    /**
     * <p>If this is set to false, it tells all the {@link NavigateThread}s to stop finding new URLs (basically; to stop crawling new pages).</p>
     */
    private boolean discoverUrls = true;

    /**
     * <p>This is a handy parameter which stops all crawling once the amount of crawls is equal or higher to the value this is set to.</p>
     */
    private int shortCircuitAfter = 0;

    /**
     * <p>visitLogged is used to make sure we don't print the same "visited" messages twice.</p> <p>Basically, it's a counter :-). Used by {@link #updateCrawlProgress()}</p>
     */
    private int visitLogged = -1;

    /**
     * <p>Sitecrawler option.</p>
     */
    private boolean disableRedirects = false;
    /**
     * <p>Sitecrawler option.</p>
     */
    private boolean enabledJavascript = false;
    /**
     * <p>Sitecrawler option.</p>
     */
    private List<Cookie> cookies = new LinkedList<Cookie>();

    /**
     * <p>Force the {@link #shouldContinueCrawling()} method to return "false" if this is set to true.</p>
     */
    private boolean forcePause = false;

    /**
     * <p>Set up the SiteCrawler, initiate the WebClient (default values: no javascript, CSS, use insecure SSL and thrown exceptions of it finds a failing Status Code).</p>
     * 
     * <p>This also sets up the a pool of {@link WebClient}s, based on {@link #threadLimit}.</p>
     * 
     * @param baseUrl The base Url, starting with the protocol, NOT ending with a / (so: "http://www.site.com"). Cannot be null
     * @param baseUrlSecure The base secure Url, starting with the protocol, NOT ending with a / (so: "https://www.site.com"). Can be null
     * @param actions list of {@link SiteCrawlerAction}s, these are the actions that will be called, either when an Exception happens, or when any page is successfully loaded
     */
    public SiteCrawler(String baseUrl, String baseUrlSecure, SiteCrawlerAction... actions) {
        this(baseUrl, baseUrlSecure, Collections.unmodifiableList(Arrays.asList(actions)));
    }

    /**
     * <p>Set up the SiteCrawler, initiate the WebClient (default values: no javascript, CSS, use insecure SSL and thrown exceptions of it finds a failing Status Code).</p>
     * 
     * <p>This also sets up the a pool of {@link WebClient}s, based on {@link #threadLimit}.</p>
     * 
     * @param baseUrl The base Url, starting with the protocol, NOT ending with a / (so: "http://www.site.com"). Cannot be null
     * @param baseUrlSecure The base secure Url, starting with the protocol, NOT ending with a / (so: "https://www.site.com"). Can be null
     * @param actions {@link List} of {@link SiteCrawlerAction}s, these are the actions that will be called, either when an Exception happens, or when any page is successfully
     *            loaded
     */
    public SiteCrawler(String baseUrl, String baseUrlSecure, List<? extends SiteCrawlerAction> actions) {
        this.baseUrl = baseUrl;
        this.baseUrlSecure = baseUrlSecure;
        this.actions = actions;
        parseVMOptions();
        addDefaultAllowedSuffixes();
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

    /**
     * <p>Return the {@link Logger} for this class.</p>
     * 
     * @return {@link Logger} will never be null
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * <p>Sets the threadLimit.</p>
     * 
     * <p>Determines the amount of I/O threads used for crawling) and (based on downloadVsProcessRatio) the amount of threads for processing downloaded pages.</p>
     * 
     * <p><strong>NOTE</strong>: calling this while the crawler is running cause a reset (see {@link #reset()}.</p>
     * 
     * @param threadLimit int positive number (higher then 0).
     */
    public void setThreadLimit(int threadLimit) {
        if (threadLimit < 1) {
            throw new IllegalArgumentException("Cannot have less the 1 thread");
        }
        this.threadLimit = threadLimit;

        if (running) {
            reset();
        }
    }

    /**
     * <p>Returns the threadLimit.</p>
     * 
     * @return threadLimit
     */
    public int getThreadLimit() {
        return threadLimit;
    }

    /**
     * <p>Sets the maxProcessWaitingRatio.</p>
     * 
     * @param maxProcessWaitingRatio has to be between 0 and 1
     */
    public void setMaxProcessWaitingRatio(double maxProcessWaitingRatio) {
        if (maxProcessWaitingRatio <= 0 || maxProcessWaitingRatio > 1) {
            throw new IllegalArgumentException("maxProcessWaitingRatio has to be between 0 and 1");
        }
        this.maxProcessWaitingRatio = maxProcessWaitingRatio;

        if (running) {
            reset();
        }
    }

    public void setDownloadVsProcessRatio(double downloadVsProcessRatio) {
        if (downloadVsProcessRatio < 0 || downloadVsProcessRatio > 1) {
            throw new IllegalArgumentException("maxProcessWaitingRatio has to be between 0 and 1");
        }
        this.downloadVsProcessRatio = downloadVsProcessRatio;

        if (running) {
            reset();
        }
    }

    /**
     * <p>Will cause the crawler to stop adding new pages to the crawler threads.</p>
     */
    public void pause() {
        forcePause = true;
    }

    /**
     * <p>Will cause the crawler to resume adding new pages to the crawler threads.</p>
     */
    public void unpause() {
        forcePause = false;
    }

    /**
     * <p>This will cause a {@link #pause()} and wait until all the queues to be empty. Afterwards, it will shut down all the page and link consumer threads.</p>
     */
    public void hardPause() {
        pause();
        // wait for consumers to be empty
        waitForLinkServiceConsumer();
        waitForPageServiceConsumer();
        shutdown();
    }

    /**
     * <p>This will re-initialize the WebClientPool, wait for all the consumers to be started again and cause an {@link #unpause()} when the system is ready to resume crawling.</p>
     */
    public void hardUnpause() {
        this.continueProcessing = true;
        init();
        startLinkServiceConsumer();
        startPageServiceConsumer();

        unpause();
    }

    /**
     * <p>Add the Collection to the list of pages to be crawled. This will NOT add any links that are either excluded or already scheduled to be visited.</p>
     * 
     * @param paths The collection of pages to be visited (Please make sure they are unique!)
     */
    public void setIncludePath(Collection<String> paths) {
        logger.debug("Setting include path with {} items (currently scheduled: {})", paths.size(), toVisit.size());
        for (String path : paths) {
            String excludePath = prependBaseUrlIfNeeded(path);
            boolean ex = isExcluded(excludePath);
            boolean sc = isScheduled(path);
            if (!ex && !sc) {
                toVisit.add(path);
            }
        }
        logger.debug("DONE Setting include path, currently scheduled: {})", toVisit.size());
    }

    /**
     * <p>Set the limit on amount of pages in the queue to be processed. The crawler pauses to avoid exhausting memory (for example).</p>
     * 
     * @param maxProcessWaiting int
     */
    public void setMaxProcessWaiting(int maxProcessWaiting) {
        if (maxProcessWaiting < 1) {
            throw new IllegalArgumentException("maxProcessWaiting cannot be less then 1");
        }
        this.maxProcessWaiting = maxProcessWaiting;
    }

    /**
     * <p>Return the maxProcessWaiting.</p>
     * 
     * @return int maxProcessWaiting
     */
    public int getMaxProcessWaiting() {
        return this.maxProcessWaiting;
    }

    /**
     * <p>If there is a "shortCircuitAfter" set, we stop all navigation after we have reached that many items. This is basically a way to say "stop after X visits". <br /> This is
     * very useful for debugging or when you don't want to wait for the whole thing to end.</p>
     * 
     * @return int shortCircuitAfter
     */
    public int getShortCircuitAfter() {
        return shortCircuitAfter;
    }

    /**
     * <p>If there is a "shortCircuitAfter" set, we stop all navigation after we have reached that many items. This is basically a way to say "stop after X visits". This is very
     * useful for debugging or when you don't want to wait for the whole thing to end.</p>
     * 
     * <p>A negative number is unsupported, and will likely result in no visits at all!</p>
     * 
     * @param shortCircuitAfter 0 means disabled, a positive integer means "stop after X visits"
     */
    public void setShortCircuitAfter(int shortCircuitAfter) {
        this.shortCircuitAfter = shortCircuitAfter;
    }

    /**
     * <p>If this is called, it tells all the {@link NavigateThread}s to stop finding new URLs (basically; to stop crawling new pages).</p>
     */
    public void disableCrawling() {
        discoverUrls = false;
    }

    /**
     * <p>Enable "redirects" for all {@link WebClient}s in the pool.</p>
     */
    public void enableRedirects() {
        this.disableRedirects = false;
    }

    /**
     * <p>Disable "redirects" for all {@link WebClient}s in the pool.</p>
     */
    public void disableRedirects() {
        this.disableRedirects = true;
    }

    /**
     * <p>Enable "javascript" for all {@link WebClient}s in the pool.</p>
     */
    public void enableJavaScript() {
        this.enabledJavascript = true;
    }

    /**
     * <p>Disabled "javascript" for all {@link WebClient}s in the pool.</p>
     */
    public void disableJavaScript() {
        this.enabledJavascript = false;
    }

    public void setRequireAllowedSuffixes(boolean requireAllowedSuffixes) {
        this.requireAllowedSuffixes = requireAllowedSuffixes;
    }

    private URLCleaner urlCleaner = new URLCleaner();

    public URLCleaner getUrlCleaner() {
        return urlCleaner;
    }

    /**
     * <p>Add a cookie to all {@link WebClient}s in the pool.<p>
     * 
     * @param name name of the cookie
     * @param value value of the cookie
     * @param domain domain this cookie should be restricted to
     */
    public void addCookie(String name, String value, String domain) {
        addCookie(new Cookie(domain, name, value));
    }

    /**
     * <p>Add a cookie to all {@link WebClient}s in the pool.<p>
     * 
     * @param cookie Cookie to add
     */
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    /**
     * <p>Remove all cookies from all {@link WebClient}s in the pool.<p>
     * 
     * @return true if cleared, false if there is no pool (yet?)
     */
    public boolean clearCookies() {
        if (null != wcPool) {
            wcPool.clearCookies();
            return true;
        }
        return false;
    }

    /**
     * <p>Add the collection of patterns to the blocked collection.</p>
     * 
     * @param blocked Collection of patterns
     */
    public void setBlocked(Collection<String> blocked) {
        if (null == blocked) {
            return;
        }
        for (String block : blocked) {
            this.blocked.add(block);
        }
    }

    /**
     * <p>If you set this, only URLs that have one of these patterns will be crawled.</p>
     * 
     * @param allowed Collection of patterns
     */
    public void setAllowed(Collection<String> allowed) {
        if (null == allowed) {
            return;
        }
        for (String allow : allowed) {
            this.allowed.add(allow);
        }
    }

    /**
     * <p>Return the collection of extensions to be parsed.</p>
     * 
     * <p>This collection is backed by the collection used by the crawler, feel free to manipulate.<br /> <strong>NOTE</strong>Please do not manipulate after starting the
     * crawler.</p>
     * 
     * @return {@link Collection} of String.
     */
    public Collection<String> getAllowedSuffixes() {
        return allowedSuffixes;
    }

    public void addAllowedSuffixes(Collection<String> allowed) {
        this.allowedSuffixes.addAll(allowed);
    }

    /**
     * <p>navigate should be called after all setup is completed and the crawl can begin.</p>
     * 
     * <p>Avoid changing parameters after {@link #navigate()} has been called.</p>
     * 
     */
    public void navigate() {
        Object[] args = { toVisit.size(), actions.size(), actions };
        logger.info("Starting crawl with the {} defined endpoints and {} plugins: {}", args);
        this.running = true;
        init();

        if (toVisit.isEmpty()) {
            if (null != baseUrl) {
                toVisit.add(baseUrl);
            } else if (null != baseUrlSecure) {
                toVisit.add(baseUrlSecure);
            }
        }

        startLinkServiceConsumer();
        startPageServiceConsumer();
        startCrawler();

        waitForLinkServiceConsumer();
        waitForPageServiceConsumer();
        shutdown();
    }

    /**
     * <p>Tell the executors ({@link #linkExecutor} and {@link #pageExecutor} to shutdown.</p>
     */
    public void shutdown() {
        // This should stop the consumers! Since we already called the waitFor*Consumer() methods, this should
        // stop the processing cleanly (and allow awaitTermination to end successfully and quickly)
        this.continueProcessing = false;

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

        if (null != wcPool) {
            wcPool.close();
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
     * <p>Returns a user-friendly message of the progress of the crawler.</p>
     * 
     * @return String a user-friendly message of the progress of the crawler
     */
    public String getCrawlProgress() {
        CrawlProgress progress = getCrawlProgressBean();
        StringBuilder sb = new StringBuilder();
        sb.append(progress.crawled).append(" crawled. ");
        sb.append(progress.leftToCrawl).append(" left to crawl. ");
        sb.append(progress.scheduledForDownload).append(" scheduled for download. "); // (submitted a NavigateThread!)
        sb.append(progress.scheduledForProcessing).append(" scheduled for processing. "); // (in LIMBO, downloaded but NOT processed)
        sb.append(progress.fullyProcessed).append(" fully processed. ");
        sb.append(progress.complete).append("% complete.");
        return sb.toString();
    }

    /**
     * <p>Returns a computer-friendly bean of the progress of the crawler.</p>
     * 
     * @return CrawlProgress a computer-friendly bean of the progress of the crawler
     */
    public CrawlProgress getCrawlProgressBean() {
        int leftToCrawl = toVisit.size() + linksScheduled.get() - threadLimit;

        CrawlProgress crawlProgress = new CrawlProgress();
        crawlProgress.crawled = actuallyVisited.get();
        crawlProgress.leftToCrawl = leftToCrawl;
        crawlProgress.scheduledForDownload = linksScheduled.get();
        crawlProgress.scheduledForProcessing = pagesScheduled.get();
        crawlProgress.fullyProcessed = fullyProcessed.get();
        crawlProgress.complete = Math.round((new Double(fullyProcessed.get()) / (fullyProcessed.get() + leftToCrawl)) * 10000) / 100.0;
        return crawlProgress;
    }

    /**
     * <p>Returns a computer-friendly bean of the configuration of the crawler.</p>
     * 
     * @return CrawlerConfiguration a computer-friendly bean of the configuration of the crawler
     */
    public CrawlerConfiguration getCrawlerConfiguration() {
        CrawlerConfiguration crawlerConfiguration = new CrawlerConfiguration();
        crawlerConfiguration.baseUrl = baseUrl;
        crawlerConfiguration.baseUrlSecure = baseUrlSecure;
        crawlerConfiguration.threadLimit = threadLimit;
        crawlerConfiguration.downloadVsProcessRatio = downloadVsProcessRatio;
        crawlerConfiguration.maxProcessWaitingRatio = maxProcessWaitingRatio;
        crawlerConfiguration.maxProcessWaiting = maxProcessWaiting;
        crawlerConfiguration.shortCircuitAfter = shortCircuitAfter;
        crawlerConfiguration.disableRedirects = disableRedirects;
        crawlerConfiguration.enabledJavascript = enabledJavascript;
        return crawlerConfiguration;
    }

    /**
     * <p>Does its best to reset/recreate the WebClient Pool (wcPool) and the link and page consumers.</p>
     */
    private void init() {
        if (null != wcPool) {
            wcPool.close();
        }
        wcPool = new WebClientPool(threadLimit);
        if (disableRedirects) {
            wcPool.disableRedirects();
        }
        if (enabledJavascript) {
            wcPool.enableJavaScript();
        }
        for (Cookie cookie : cookies) {
            wcPool.addCookie(cookie);
        }
        wcPool.setName("Sitecrawler pool");

        linkExecutor = Executors.newFixedThreadPool(threadLimit, linkExecutorThreadFactory);
        linkService = new ExecutorCompletionService<ProcessPage>(linkExecutor);

        int pageExecutorSize = (int) Math.ceil(threadLimit * downloadVsProcessRatio);
        pageExecutor = Executors.newFixedThreadPool(pageExecutorSize, pageExecutorThreadFactory);
        pageService = new ExecutorCompletionService<Collection<String>>(pageExecutor);

        // in bytes
        long maxHeap = Runtime.getRuntime().maxMemory();
        // to mb
        double gbMaxHeap = maxHeap / (1024.0 * 1024.0);
        // Final result, rounded
        int maxProcessWaiting = (int) (gbMaxHeap * maxProcessWaitingRatio);
        setMaxProcessWaiting(maxProcessWaiting);

        Object[] args = { wcPool.getName(), threadLimit, threadLimit, pageExecutorSize, maxProcessWaiting };
        logger.info("WebClientPool {} created with size {}, linkExecutor with size {}, pageExecutor with size {}, maxProcessWaiting={}", args);
    }

    /**
     * <p>This will cause an {@link #hardPause()} followed by an {@link #hardUnpause()}.</p>
     */
    private void reset() {
        hardPause();
        hardUnpause();
    }

    private void parseVMOptions() {
        int threadLimit = NumberUtils.toInt(System.getProperty("sc:threadLimit"));
        if (threadLimit > 0) {
            setThreadLimit(threadLimit);
        }

        int maxProcessWaiting = NumberUtils.toInt(System.getProperty("sc:maxProcessWaiting"));
        if (maxProcessWaiting > 0) {
            setMaxProcessWaiting(maxProcessWaiting);
        }

        int shortCircuitAfter = NumberUtils.toInt(System.getProperty("sc:shortCircuitAfter"));
        if (shortCircuitAfter > 0) {
            setShortCircuitAfter(shortCircuitAfter);
        }

        int downloadVsProcessRatio = NumberUtils.toInt(System.getProperty("sc:downloadVsProcessRatio"));
        if (downloadVsProcessRatio > 0) {
            setDownloadVsProcessRatio(downloadVsProcessRatio);
        }
    }

    /**
     * <p>This is the collection of default suffixes allowed by the crawler on the web.</p>
     * 
     * <p>End-users can add more via {@link #addAllowedSuffixes(Collection)}).</p>
     */
    private void addDefaultAllowedSuffixes() {
        allowedSuffixes.add("/");
        allowedSuffixes.add(".jsp");
        allowedSuffixes.add(".htm");
        allowedSuffixes.add(".html");
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
                while (continueProcessing) {

                    try {
                        if (shouldPauseProcessing()) {
                            if (forcePause) {
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
                        actuallyVisited.getAndIncrement();
                        ProcessPage processPage = result.get();

                        // This happens AFTER a NavigateThread was successful
                        processPage.setActions(actions);
                        processPage.setBaseUrl(baseUrl);
                        processPage.setBaseUrlSecure(baseUrlSecure);

                        logger.trace("Submitting a new ProcessPage object");
                        pageService.submit(processPage);
                        pagesScheduled.getAndIncrement();
                    } catch (InterruptedException e) {
                        logger.error("[startLinkServiceConsumer] Interruped while trying to work with result {}", result, e);
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("[startLinkServiceConsumer] Something went wrong trying to work with result {}", result, e);
                    } catch (RejectedExecutionException e) {
                        logger.warn("[startLinkServiceConsumer] Tried to add a ProcessPage [Future: {}], but this was rejected (shutdown in progress?)", result, e);
                    } finally {
                        if (result != null) {
                            linksScheduled.getAndDecrement();
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

    /**
     * <p>Waits for all the links to be processed, returns when the {@link #linksScheduled} queue is empty.</p>
     */
    private void waitForLinkServiceConsumer() {
        logger.info("Shutting down LinkServiceConsumer");
        final int secondsToWaitBetweenChecks = 5;
        while (linksScheduled.get() > 0) {
            logger.info("Waiting for {} links to be consumed...", linksScheduled.get());

            if (!continueProcessing) {
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
                            if (isExcluded(newToVisit)) {
                                logger.trace("NOT adding link since it is excluded: {}", newToVisit);
                                continue;
                            }
                            if (isScheduled(newToVisit)) {
                                logger.trace("NOT adding link since it is already scheduled: {}", newToVisit);
                                continue;
                            }
                            logger.trace("Adding link to the list: {}", newToVisit);
                            toVisit.put(newToVisit);
                        }
                    } catch (InterruptedException e) {
                        logger.error("[startPageServiceConsumer] Interruped while trying to work with result {}", result, e);
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        logger.error("[startPageServiceConsumer] Something went wrong trying to work with result {}", result, e);
                    } finally {
                        if (result != null) {
                            fullyProcessed.getAndIncrement();
                            pagesScheduled.getAndDecrement();
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
        while (pagesScheduled.get() > 0) {
            logger.info("Waiting for {} pages to be consumed...", pagesScheduled.get());

            if (!continueProcessing) {
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
     * <p>This happens in the "main" thread, and will block the calling code from completing.</p>
     * 
     * <p>As soon as there is nothing to crawl or we should stop, this method returns.</p>
     * 
     */
    private void startCrawler() {
        while (shouldContinueCrawling()) {
            updateCrawlProgress();

            String url;
            try {
                // Cannot be ".take()" since that might block forever.
                // Waiting for 5 seconds max before we're done waiting.
                url = toVisit.poll(5, TimeUnit.SECONDS);
                if (null == url) {
                    continue;
                }
                url = prependBaseUrlIfNeeded(url);
            } catch (InterruptedException e) {
                logger.error("We were interrupted waiting for the next link, exiting...", e);
                Thread.currentThread().interrupt();
                return;
            }

            // What if this URL has been excluded? Well, we simply skip over it :)
            if (isExcluded(url)) {
                logger.trace("This URL is excluded: {}", url);
                continue;
            }

            NavigateThread navigateThread = new NavigateThread(url, this.wcPool);
            try {
                linkService.submit(navigateThread);
            } catch (RejectedExecutionException e) {
                logger.warn("Tried to add a NavigateThread for {}, but this was rejected (shutdown in progress?)", url, e);
            }
            linksScheduled.getAndIncrement();

            visited.add(url);
            String cleanUrl = urlCleaner.getCleanedUrl(url);
            if (null != cleanUrl) {
                visited.add(cleanUrl);
            }
            visitedCounter.getAndIncrement();
        }

        logger.info("Done crawling, {} links visited. (crosscheck: {})", visitedCounter.get(), actuallyVisited.get());
    }

    /**
     * <p>If there are too many pages scheduled, this will return true to inform clients to pause the crawling.</p>
     * 
     * @return true if the process queue is too large
     */
    private boolean shouldPauseProcessing() {

        boolean shouldPause = pagesScheduled.get() > maxProcessWaiting || forcePause;
        logger.trace("ShouldPause=" + shouldPause + ", pagesScheduled=" + pagesScheduled.get());
        return shouldPause;
    }

    /**
     * <p>When the sitecrawler is done and should stop crawling, this returns false.</p>
     * 
     * @return true if we should continue crawling and processing, false if we should stop whenever gracefully possible
     */
    private boolean shouldContinueCrawling() {
        boolean morePagesToVisit = toVisit.size() > 0 || linksScheduled.get() > 0 || pagesScheduled.get() > 0;
        if (!morePagesToVisit) {
            logger.info("No more pages to visit, all pages processed. Stopping this crawl for that reason.");
            return false;
        }

        if (!discoverUrls) {
            logger.info("discoverUrls was set to false. Stopping this crawl for that reason.");
            return false;
        }

        // If there is a "shortCircuitAfter" set, we stop all navigation after
        // we have reached (at least) that many items. This is basically a way to say "stop after X visits"
        // This is very useful for debugging or when you don't want to wait for the whole thing to end
        logger.trace("Current shortcicruit setting: {}, visitedCounter: {}", shortCircuitAfter, visitedCounter.get());
        if (shortCircuitAfter != 0 && visitedCounter.get() > shortCircuitAfter) {
            logger.info("A shortcircuit was set (at {}) and has been triggered after {} visited pages. Stopping this crawl for that reason.", shortCircuitAfter,
                    visitedCounter.get());
            logger.warn("If you see a shortcircuit message (this one) in a production environment/build, "
                    + "it is likely that somebody forgot to remove a debug \".setShortCircuit\" call. " + "Please report this if found.");
            return false;
        }

        if (!forcePause && !continueProcessing) {
            logger.info("This crawler has been shutdown (without pause or reset). Thereforce, stopping the crawl");
            return false;
        }

        return true;
    }

    /**
     * <p>Logs the progress to the log if appropriate.</p>
     */
    private void updateCrawlProgress() {
        // int visited = visitedCounter.get();
        int visited = actuallyVisited.get();
        if (((visited - visitLogged) > reportProgressPerDownloadedPages && visited > visitLogged) || visitLogged == -1) {
            logger.info(getCrawlProgress());
            visitLogged = visited;
        }
    }

    /**
     * <p>If a URL doesn't start with baseUrl of doesn't contain any protocol information, we add it here.</p>
     * 
     * @param url The URL, can be empty (will be baseURL + a /)
     * @return Tries to return a full URL (with protocol and everything)
     */
    private String prependBaseUrlIfNeeded(String url) {
        if (null == url) {
            throw new NullPointerException("url cannot be null");
        }

        if (url.contains("://")) {
            return url;
        }

        if (!url.startsWith("/")) {
            url = "/".concat(url);
        }

        if (null != baseUrlSecure) {
            return baseUrlSecure.concat(url);
        }
        if (null != baseUrl) {
            return baseUrl.concat(url);
        }

        throw new NullPointerException("Cannot have both baseUrl AND baseUrlSecure be null!");
    }

    /**
     * <p>Will return true if the URL is <strong>excluded</strong> from crawling.</p>
     * 
     * <p>Usually this means: <ul> <li>The URL is outside of one of the base URLs.</li> <li>The URL has been visited before by this crawler.</li> <li>The URL doesn't look like a
     * crawlable page (only <code>#hasAllowedSuffix</code> are crawlable).</li> </ul> </p>
     * 
     * @param url A full url (should include the protocol part, eg "http://foo.bar/page/")
     * @return true if it's excluded, false otherwise
     */
    private boolean isExcluded(String url) {
        boolean startsWithBaseUrl = false;
        boolean startsWithBaseUrlSecure = false;
        boolean allGood = false;
        if (null != baseUrl && url.startsWith(baseUrl)) {
            logger.trace("startsWithBaseUrl: {}", url);
            startsWithBaseUrl = true;
        }

        if (null != baseUrlSecure && url.startsWith(baseUrlSecure)) {
            logger.trace("startsWith baseUrlSecure: {}", url);
            startsWithBaseUrlSecure = true;
        }

        // What about relative (from the base) URLs? We can have "/foo.bar", just not "//foo.bar"
        if (url.length() > 1 && url.startsWith("/") && !url.startsWith("//")) {
            logger.trace("This is a relative url, pointing to the BASE: {}", url);
            allGood = true;
        }

        // What about relative (from current path) URL? We can have "foo.bar", just not "//foo.bar"
        // That is not a use-case we currently support (but might need to, for desk.com)
        // if (url.length() > 1 && !url.startsWith("//")) {
        // logger.trace("This is a relative url, pointing RELATIVALlY (starting with c): {}", url);
        // allGood = true;
        // }

        // If it doesn't start with either of the baseUrls (or they are simply not set), we don't allow the URL
        if (!startsWithBaseUrl && !startsWithBaseUrlSecure && !allGood) {
            logger.trace("!startsWithBaseUrl && !startsWithBaseUrlSecure && !allGood: {}", url);
            return true;
        }

        boolean hasAllowedSuffix = false;
        String suffix = url.split("\\?")[0].toLowerCase();
        for (String allowedSuffix : allowedSuffixes) {
            logger.trace("Matching allowed suffix [{}] against URL {}", allowedSuffix, suffix, url);
            if (suffix.endsWith(allowedSuffix)) {
                hasAllowedSuffix = true;
                break;
            }
        }
        if (!requireAllowedSuffixes) {
            logger.trace("requireAllowedSuffixes = false, so setting hasAllowedSuffix to true");
            hasAllowedSuffix = true;
        }

        if (!hasAllowedSuffix) {
            logger.trace("not allowing suffix {} for {}", suffix, url);
            return true;
        }

        if (visited.contains(url)) {
            logger.trace("We already visited [{}], skipping it.", url);
            return true;
        }

        if (listContainsSubstring(blocked, url)) {
            logger.trace("This URL is blocked [{}], skipping it.", url);
            return true;
        }

        if (!allowed.isEmpty() && !listContainsSubstring(allowed, url)) {
            logger.trace("This URL is not allowed [{}], skipping it.", url);
            return true;
        }

        // Also check the cleaned URL
        String cleanUrl = urlCleaner.getCleanedUrl(url);
        if (null != cleanUrl && visited.contains(cleanUrl)) {
            logger.trace("The cleaned URL is blocked [{}], skipping it.", url);
            return true;
        }

        return false;
    }

    /**
     * <p>Returns true if the scheduled link is already on the queue to be processed.</p>
     * 
     * @param url Link to check
     * @return true if the link is already on the queue, false otherwise
     */
    private boolean isScheduled(String url) {
        if (toVisit.contains(url)) {
            return true;
        }
        return false;
    }

    /**
     * <p>Quick and dirty way to check if a string contains any of the provided substrings.</p>
     * 
     * @param list A collection of Strings to check for in the checkStr
     * @param checkStr The String to check
     * @return true if checkStr contains at least one of the items in the provided list, false otherwise
     */
    private boolean listContainsSubstring(Collection<String> list, String checkStr) {
        for (String s : list) {
            logger.trace("CHECKING This URL [{}] for {}", checkStr, s);
            if (checkStr.contains(s)) {
                logger.trace("This URL [{}] matches because of {}, so we're returning true.", checkStr, s);
                return true;
            }
        }
        logger.trace("This URL [{}] did NOT match anything., checked against collection of size: {}", checkStr, list.size());
        return false;
    }
}
