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

import java.util.List;
import java.util.Set;

import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * <p>Any implementation of this class can be added to a {@link SiteCrawler}, where it will be called when a page is
 * fully retrieved.</p>
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public interface SiteCrawlerAction {

    /**
     * <p>When a page has been successfully retrieved, this method will take any necessary action to parse the
     * results.</p>
     * 
     * <p>A general rule of thumb is that the provided page and other parameters should not be modified!</p>
     * 
     * @param page The parsed {@link HtmlPage}
     * @param hrefs A List of links (anchors with href="..." elements , iframes) found in the page
     * @param referrer TODO <em>Need documentation</em><br>
     *            (Currently, all implementing class ignore the referrer parameter)
     */
    void takeAction(HtmlPage page, List<String> hrefs, Set<String> referrer);

    /**
     * <p>When a page fails to be successfully retrieved, this method will be called. Usually this is due to a
     * {@link com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException} or an empty page response (in which case the error code might be a 410
     * [410 Gone]!).
     * 
     * @param errorcode The statuscode as returned by the server (usually a non-200 code)
     * @param url The URL that was being visited when the errorcode was returned
     * @param from the "referrer"(s) if it/they exist
     * @param response The content of the error page
     */
    void handleError(int errorcode, String url, Set<String> from, WebResponse response);
}
