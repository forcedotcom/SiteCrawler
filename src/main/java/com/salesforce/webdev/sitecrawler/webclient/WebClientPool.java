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
package com.salesforce.webdev.sitecrawler.webclient;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.Cache;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.util.Cookie;

/**
 * <p>This class encapsulates the intricacies of managing WebClients.</p>
 * 
 * <p>It supports setting the initial thread size and has some convenience methods for enabling and disabling certain
 * settings for the entire pool.</p>.
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public class WebClientPool {

    private final Logger logger = LoggerFactory.getLogger(WebClientPool.class);

    private String name = "Anonymous pool";
    /**
     * <p>The max numbers of WebClients to be created for the pool.</p>
     */
    private final int poolLimit;

    /**
     * <p>The Queue we use to hold the WebClients.</p>
     */
    private final BlockingDeque<WebClientExtended> wcPool;

    /**
     * <p>Since we visit the same site over and over, we share the cache between the clients.</p>
     */
    private final Cache globalCache = new Cache();
    /**
     * <p>Number of objects allowed in the cache.</p>
     */
    private final int maxSize = 10240;

    /**
     * <p>After every 5000 visits, we flush the cache to make room for newer items.</p>
     * 
     * <p>NOTE: This might not be necessary, but was done to empty out stale items and (what seemed like) an ever
     * growing cache that broke the memory limit.</p>
     */
    private final int flushAfterXTimes = 5000;
    private volatile AtomicInteger cacheFlushCounter = new AtomicInteger();
    private boolean flushCache = false;

    private volatile AtomicLong takeCounter = new AtomicLong();
    private volatile AtomicLong returnCounter = new AtomicLong();
    private volatile AtomicLong recycleCounter = new AtomicLong();
    private volatile boolean closed = false;;

    /**
     * <p>Start a new Pool, using the amount of available processors as the threadLimit.</p>
     */
    public WebClientPool() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * <p>Start a new pool, with a fixed max amount of clients.</p>
     * 
     * @param poolLimit The maximum number of WebClients to queue
     * @throws IllegalArgumentException if threadLimit is below 1
     */
    public WebClientPool(final int poolLimit) {
        if (poolLimit < 1) {
            throw new IllegalArgumentException("poolLimit needs to be a positive int (got: " + poolLimit + ")");
        }

        this.poolLimit = poolLimit;
        this.wcPool = new LinkedBlockingDeque<WebClientExtended>(poolLimit);

        this.globalCache.setMaxSize(maxSize);

        for (int i = 0; i < poolLimit; i++) {
            WebClientExtended wc = new WebClientExtended(this, WebClientFactory.getDefault());
            wc.getWebClient().setCache(globalCache);
            wcPool.add(wc);
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    /**
     * <p>Returns the maximum size of this pool.</p>
     * 
     * @return int max size of the pool
     */
    public int getSize() {
        return poolLimit;
    }

    /**
     * <p>Disable "redirects" for all {@link WebClient}s in the pool.</p>
     */
    public void disableRedirects() {
        for (WebClientExtended wc : wcPool) {
            wc.getWebClient().getOptions().setRedirectEnabled(false);
        }
    }

    /**
     * <p>Enable "javascript" for all {@link WebClient}s in the pool.</p>
     */
    public void enableJavaScript() {
        for (WebClientExtended wc : wcPool) {
            wc.getWebClient().getOptions().setJavaScriptEnabled(true);
            // Reset the engine to the normal engine
            wc.getWebClient().setJavaScriptEngine(new JavaScriptEngine(wc.getWebClient()));
        }
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
        for (WebClientExtended wc : wcPool) {
            wc.getWebClient().getCookieManager().addCookie(cookie);
        }
    }

    /**
     * <p>Remove all cookies from all {@link WebClient}s in the pool.<p>
     */
    public void clearCookies() {
        for (WebClientExtended wc : wcPool) {
            wc.getWebClient().getCookieManager().clearCookies();
        }
    }

    /**
     * <p>Take (borrow) a client.</p>
     * 
     * <p>Please return it after usage using {@link #returnClient(WebClient)}.</p>
     * 
     * <p>Will block if there are no clients currently.</p>
     * 
     * @return A WebClient (will not be null)
     * @throws InterruptedException
     */
    public WebClientExtended takeClient() throws InterruptedException {
        if (closed) {
            throw new RuntimeException("pool " + name + " is closed, cannot take new clients!");
        }
        takeCounter.incrementAndGet();
        return wcPool.take();
    }

    /**
     * <p>Returns a client back to the pool (and cleans it up).</p>
     * 
     * <p>Only call with a client that was borrowed (via {@link #takeClient()}).</p>
     * 
     * @param client The WebClient to return (cannot be null)
     * @throws NullPointerException if the client is null
     */
    public void returnClient(WebClientExtended client) {
        if (null == client) {
            throw new NullPointerException("client cannot be null");
        }
        returnCounter.incrementAndGet();

        closeClient(client.getWebClient());
        client.resetRetryCounters();

        if (!closed) {
            wcPool.add(client);
        }

        // attempt to get the cache under control..
        int count = cacheFlushCounter.incrementAndGet();
        if (count > flushAfterXTimes) {
            report();
            if (flushCache) {
                globalCache.clear();
            }
            cacheFlushCounter.set(0);
        }
    }

    protected WebClient recycleClient(WebClient client) {
        recycleCounter.incrementAndGet();
        closeClient(client);
        return WebClientFactory.getDefault();
    }

    /**
     * <p>Returns a client back to the pool (and cleans it up).</p>
     * 
     * <p>Only call with a client that was borrowed (via {@link #takeClient()}).</p>
     * 
     * @param client The WebClient to return (cannot be null)
     * @throws NullPointerException if the client is null
     */
    protected void closeClient(WebClient client) {
        if (null == client) {
            throw new NullPointerException("client cannot be null");
        }

        client.close();
    }

    public void close() {
        synchronized (this) {
            if (closed) {
                logger.trace("Pool '{}' already closed", name);
                return;
            }
            closed = true;
        }
        logger.info("Closing pool '{}' after having crawled {} pages...", name, takeCounter.get());
        for (WebClientExtended wc : wcPool) {
            closeClient(wc.getWebClient());
        }
        // Report just before we actually clear the whole pool
        report();

        globalCache.clear();
        cacheFlushCounter.set(0);

        wcPool.clear();
        logger.info("... pool closed: '{}'", name);
    }

    /**
     * <p>Report on the state of the pool (debug level if still running, INFO when closed (for reporting).</p>
     */
    private void report() {
        long taken = takeCounter.get();
        long returned = returnCounter.get();
        long inPool = wcPool.size();
        long unaccountedFor = taken - returned - (poolLimit - inPool);

        Object[] args = { name, taken, returned, inPool, recycleCounter.get(), unaccountedFor };
        logger.debug("[{}] taken: {}, returned: {}, inPool: {}, recycled: {}, unaccountedFor: {}", args);

        reportOnCache();
    }

    protected void reportOnCache() {
        int maxCacheSize = globalCache.getMaxSize();
        int currentCacheSize = globalCache.getSize();
        Object[] cacheArgs = { name, maxCacheSize, currentCacheSize };
        logger.debug("[{}], cache MAX: {}, cache CURRENT: {}", cacheArgs);
    }
}
