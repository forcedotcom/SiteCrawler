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
package com.salesforce.webdev.sitecrawler.navigation;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.Page;
import com.salesforce.webdev.sitecrawler.webclient.WebClientExtended;
import com.salesforce.webdev.sitecrawler.webclient.WebClientPool;

/**
 * <p>This is a fairly simple Runnable (actually a Callable, so it can be used as part of an Executor).</p>
 * 
 * <p>It's basic function is to go out to a page, grab its content and return that as part of a {@link ProcessPage}
 * object.</p>
 * 
 * <p>In case of problems, the Exception is grabbed and added to the {@link ProcessPage}. This object does not concern
 * itself with parsing or validating the result.</p>
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public class NavigateThread implements Callable<ProcessPage> {

    /**
     * <p>Logger for this class.</p>
     */
    private final Logger logger = LoggerFactory.getLogger(NavigateThread.class);

    /**
     * <p>The pool we use to draw clients from. We expect the pool to block until a client is available.</p>
     */
    private final WebClientPool wcPool;

    /**
     * <p>The URL we need to visit. Needs to be a fully formed URL.</p>
     */
    private final String location;

    /**
     * 
     * @param location The URL we need to visit. Needs to be a fully formed URL
     * @param wcPool The pool we use to draw clients from. We expect the pool to block until a client is available
     */
    public NavigateThread(String location, WebClientPool wcPool) {
        this.location = location;
        this.wcPool = wcPool;
    }

    /**
     * <p>We retrieve the configured page defined at {@link #location}, store it in a {@link ProcessPage} as a
     * {@link Page}.</p>
     * 
     * <p>In case of an Exception, the Page will NOT be set, but the Exception will be logged in the ProcessPage object
     * for later analysis (this is left to the implementation).</p>
     * 
     * @throws NavigateThreadException In case a problem the requested page has a known problem and cannot be processed.
     *             This is most likely an {@link InterruptedException} (happens when we cannot retrieve a WebClient)
     * @return {@link ProcessPage} Will never be empty, and will contain either a Page (in case of success) or an
     *         Exception (in case of failure)
     */
    @Override
    public ProcessPage call() throws NavigateThreadException {
        ProcessPage result = new ProcessPage(location);
        WebClientExtended wc = null;
        try {
            wc = wcPool.takeClient();
            try {
                logger.debug("Retrieving page {}", location);
                Page page = wc.getPageWithRetry(location);
                result.setPage(page);
                return result;
            } catch (Exception e) {
                result.setException(e);
                return result;
            }
        } catch (InterruptedException e) {
            throw new NavigateThreadException(e);
        } finally {
            if (null != wc) {
                wcPool.returnClient(wc);
            }
        }
    }
}
