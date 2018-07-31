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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;

/**
 * <p>This is a special implementation (encapsulation, really) of a regular {@link WebClient}.</p>
 * 
 * <p>It provides an option for incremental back-offs in case of recoverable errors.</p>
 * 
 * @author jroel
 * @since v1.0
 */
public class WebClientExtended {

    /**
     * <p>Logger for this class.</p>
     */
    private final Logger logger = LoggerFactory.getLogger(WebClientExtended.class);

    /**
     * <p>How long this process should wait in case of recoverable errors.</p>
     * 
     * <p>Default on Linux hosts: 60 - this matches <code>/proc/sys/net/ipv4/tcp_fin_timeout</code>.</p>
     * <p>Protip: use "ss -s" on a Linux host to see stats while this is running.</p>
     */
    private long recoverableErrorWaitTimeout = TimeUnit.SECONDS.toMillis(10);

    /**
     * <p>Default value, used for {@link #recoverableErrorRetryCounter}. Default is 5.</p>
     */
    private static final int DEFAULT_RECOVERABLE_ERROR_RETRY_COUNTER = 5;
    /**
     * <p>How often this process should retry recoverable errors.</p>
     */
    private int recoverableErrorRetryCounter = DEFAULT_RECOVERABLE_ERROR_RETRY_COUNTER;

    /**
     * <p>Internal counter, used to keep track of the exponential backoff.</p>
     */
    private AtomicInteger exponentialBackoffCounter = new AtomicInteger(1);

    /**
     * <p>Should we try to recover from "known" issues?</p>
     */
    private boolean tryToRecover = true;

    /**
     * <p>The pool to return the borrowed {@link WebClient}s to.</p>
     */
    private final WebClientPool wcPool;

    /**
     * <p>The borrowed {@link WebClient}.</p>
     */
    private WebClient wc;

    /**
     * <p>Internal constructor, used by {@link WebClientPool}.</p>
     * 
     * @param wcPool {@link WebClientPool} (can be null)
     * @param wc {@link WebClient} cannot be null
     */
    protected WebClientExtended(WebClientPool wcPool, WebClient wc) {
        this.wcPool = wcPool;
        this.wc = wc;
    }

    /**
     * <p>Set to true if we should try to recover from errors.</p>
     * 
     * @param tryToRecover true to try and recover from errors.
     */
    public void setTryToRecover(boolean tryToRecover) {
        this.tryToRecover = tryToRecover;
    }

    /**
     * <p>Set the amount of milliseconds to wait between retries.</p>
     * 
     * @param recoverableErrorWaitTimeout See {@link #recoverableErrorWaitTimeout}
     */
    public void setRecoverableErrorWaitTimeout(long recoverableErrorWaitTimeout) {
        this.recoverableErrorWaitTimeout = recoverableErrorWaitTimeout;
    }

    /**
     * <p>Set the amount of times this {@link WebClientExtended} should try to recover from an error.</p>
     * 
     * @param recoverableErrorRetryCounter See {@link #recoverableErrorWaitTimeout}
     */
    public void setRecoverableErrorRetryCounter(int recoverableErrorRetryCounter) {
        this.recoverableErrorRetryCounter = recoverableErrorRetryCounter;
    }

    /**
     * <p>Internal method for {@link WebClientPool}.</p>
     * 
     * @return The borrowed {@link WebClient}
     */
    protected WebClient getWebClient() {
        return wc;
    }

    /**
     * <p>Internal method for {@link WebClientPool}. Resets all the counters for retry attempts to their defaults.</p>
     */
    protected void resetRetryCounters() {
        recoverableErrorRetryCounter = DEFAULT_RECOVERABLE_ERROR_RETRY_COUNTER;
        exponentialBackoffCounter.set(1);
    }

    /**
     * <p>Navigates and retrieves a page, using the provided WebClient.</p>
     * 
     * <p>This is an internal method, used so we can reschedule another "pass" in case a recoverable error occurs.
     * (Mostly to avoid the return and take behavior used for WebClient and avoid create a deadlock).</p>
     * 
     * @param location The URL to visit
     * @return Page
     * @throws IOException Thrown when the retries failed
     */
    public Page getPageWithRetry(String location) throws IOException {
        try {
            logger.debug("Retrieving page {}", location);
            Page page = wc.getPage(location);
            // TODO for now, we're going to return 403 as that - 403. Too many external domains that are not allowed
            // holding us up
            // if (tryToRecover) {
            // // Some proxies are known to return a "403" status if that domain isn't allowed. Disabled since that is
            // // unrecoverable.
            // if (page.getWebResponse().getStatusCode() == 403) {
            // logger.warn("Crawling location [" + location + "] caused a "
            // + page.getWebResponse().getStatusCode() + " " + page.getWebResponse().getStatusMessage());
            // while (retry(location)) {
            // return getPageWithRetry(location);
            // }
            // }
            // }
            return page;
        } catch (IOException e) {
            while (retryException(e, location)) {
                return getPageWithRetry(location);
            }
            throw e;
        }
    }

    public WebResponse loadWebResponseWithRetry(WebRequest webRequest) throws IOException {
        String location = "unknown";
        try {
            location = webRequest.getUrl().toExternalForm();
        } catch (Exception e) {
            logger.error("Cannot create get location from webRequest '{}'", webRequest, e);
        }
        try {
            logger.debug("loading web response for {}", location);

            WebResponse response = wc.loadWebResponse(webRequest);
            // if (tryToRecover) {
            // // Some proxies are known to return a "403" status if that domain isn't allowed. Disabled since that is
            // // unrecoverable.
            // if (response.getStatusCode() == 403) {
            // logger.warn("Crawling location [" + location + "] caused a "
            // + response.getStatusCode() + " " + response.getStatusMessage());
            // while (retry(webRequest.getUrl().toExternalForm())) {
            // return loadWebResponseWithRetry(webRequest);
            // }
            // }
            // }
            return response;
        } catch (IOException e) {
            if (tryToRecover) {
                while (retryException(e, location)) {
                    return loadWebResponseWithRetry(webRequest);
                }
            }
            throw e;
        }
    }

    /**
     * <p>This happens when the program runs out of client sockets.</p>
     * 
     * <p>The method will try as best to (re)create the environment so the next call (the retry) can succeed. If there
     * are no more retries available, it will throw the original exception (signaling "no more retries").</p>
     * 
     * @param e IOException The original exception (might be retrown)
     * @param location The URL to visit
     * @return boolean true if we can try recovery again, false if we exhausted the number of recover attempts
     */
    private boolean retryException(final IOException e, final String location) {
        if (recoverableErrorRetryCounter < 0) {
            return false;
        }
        recoverableErrorRetryCounter--;

        if (e.getMessage().contains("Cannot assign requested address") || e instanceof SocketTimeoutException) {
            long timeOut = recoverableErrorWaitTimeout * exponentialBackoffCounter.getAndIncrement();
            // We need to pause the crawling for a while
            logger
                .warn(
                    "Out of client sockets [{}] while trying to open '{}', sleeping for {} ms before retrying (attempts left: {})",
                    e.getClass(), location, timeOut, recoverableErrorRetryCounter, e);
            if (null != wcPool) {
                wc = wcPool.recycleClient(wc);
            }
            try {
                Thread.sleep(timeOut);
            } catch (InterruptedException ie) {
                logger.error("Interrupted while waiting for location '{}'", location, ie);
                Thread.currentThread().interrupt();
                return false;
            }
            logger.info("Done sleeping, rescheduling location '{}'", location);
            return true;
        } else {
            return false;
        }
    }

    // private boolean retry(String location) {
    // if (recoverableErrorRetryCounter < 0) {
    // return false;
    // }
    // recoverableErrorRetryCounter--;
    //
    // long timeOut = recoverableErrorWaitTimeout * exponentialBackoffCounter.getAndIncrement();
    // // We need to pause the crawling for a while
    // logger.warn("Failed while trying to open '" + location
    // + "', sleeping for " + timeOut + " ms before retrying (attempts left: "
    // + recoverableErrorRetryCounter + ")");
    // wc = wcPool.recycleClient(wc);
    // try {
    // Thread.sleep(timeOut);
    // } catch (InterruptedException e) {
    // logger.error("Interrupted while waiting for " + location, e);
    // return false;
    // }
    // return true;
    // }
}
