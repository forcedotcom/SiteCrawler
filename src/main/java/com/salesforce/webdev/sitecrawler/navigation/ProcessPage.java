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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.net.ssl.SSLException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.salesforce.webdev.sitecrawler.SiteCrawlerAction;
import com.salesforce.webdev.sitecrawler.utils.URLNormalizer;

/**
 * <p>ProcessPage takes care of analyzing any retrieved HTML page, extracting any actionable/crawlable links from
 * anchors and iframes.</p>
 * 
 * <p>After processing, the configured actions are all given a change to process the analyzed page.</p>
 * 
 * <p>In case of errors (a non "200 OK" status code), this is handled as well.</p>
 * 
 * <p>TODO A lot of instance variables on this page are unexplained and could probably do with a good rewrite and
 * some javadocs.</p>
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public class ProcessPage implements Callable<Collection<String>> {

    /**
     * <p>Logger for this class.</p>
     */
    private final Logger logger = LoggerFactory.getLogger(ProcessPage.class);

    private Collection<? extends SiteCrawlerAction> actions;

    private final String location;

    // in case of success
    private Page page;

    // in case of failure
    private Exception exception;
    private boolean hasException;

    /**
     * <p>Some URLs are relative, do we want to try and make them absolute?</p>
     */
    private boolean makeRelativeUrlAbsolute = false;

    /**
     * <p>This keeps track of which pages (the value Set) respond with a redirect. The key is the target of the 301
     * (so, if http://foo/bar responds with a 301 to http://foo/bar2, then http://foo/bar2 is the key and http://foo/bar
     * will be in the set as a value.</p>
     * 
     * <p>Note: this is static so we can keep track across multiple processes. This might be rewritten into a different
     * container (factory/singleton?).</p>
     */
    private volatile static Map<String, Set<String>> urlFrom = new ConcurrentHashMap<String, Set<String>>();

    private boolean ignoreQueryParams = false;

    private String baseUrl;
    private String baseUrlSecure;

    private final List<String> toVisit = new LinkedList<String>();

    public ProcessPage(String location) {
        this.location = location;
    }

    public void setIgnoreQueryParameters(Boolean ignore) {
        ignoreQueryParams = ignore;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setBaseUrlSecure(String baseUrlSecure) {
        this.baseUrlSecure = baseUrlSecure;
    }

    // To be called by the SiteCrawler!
    public void setActions(Collection<? extends SiteCrawlerAction> actions) {
        this.actions = actions;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public void setException(Exception exception) {
        this.exception = exception;
        this.hasException = true;
    }

    public Collection<String> getToVisit() {
        return Collections.unmodifiableCollection(this.toVisit);
    }

    /**
     * <p>This analyzes the page and returns all crawlable links.</p>
     * 
     * @return {@link Collection} Collection of crawlable links
     */
    @Override
    public Collection<String> call() throws Exception {
        logger.trace("Processing {}...", location);
        process();

        logger.trace("... DONE processing {}", location);
        return getToVisit();
    }

    /**
     * <p>Process the page based on it's properties. I.e. treat it like an exception, empty page , non-HTML page or (in
     * case of "none of the above) as a normal HTML page).</p>
     */
    public void process() {
        if (this.hasException) {
            handleException();
            return;
        }

        if (page.getWebResponse().getContentAsString().length() == 0) {
            handleEmptyPage();
            return;
        }

        if (page instanceof HtmlPage) {
            processSucces((HtmlPage) page);
        } else {
            handleNonHtmlPage();
        }

    }

    /**
     * <p>Extract links and call all defined {@link #actions}.</p>
     * 
     * <p>All found links are added to the {@link #urlFrom} collection and actionable links are added to
     * {@link #toVisit}.</p>
     * 
     * @param htmlpage The HtmlPage to be processed (cannot be null)
     */
    private void processSucces(HtmlPage htmlpage) {
        List<String> hrefs = new ArrayList<String>();
        List<HtmlAnchor> links = htmlpage.getAnchors();
        DomNodeList<DomElement> iframes = htmlpage.getElementsByTagName("iframe");
        for (DomElement iframe : iframes) {
            logger.trace("iframe added: {}", iframe.getAttribute("src"));
            hrefs.add(iframe.getAttribute("src"));
        }
        for (HtmlAnchor link : links) {
            if (StringUtils.isNotBlank(link.getHrefAttribute())) {
                logger.trace("link added: {}", link.getHrefAttribute());
                hrefs.add(link.getHrefAttribute());
            }
        }
        for (SiteCrawlerAction sca : actions) {
            sca.takeAction(htmlpage, hrefs, urlFrom.get(location));
        }

        for (String href : hrefs) {
            if (StringUtils.isEmpty(href)) {
                continue;
            }

            if (null != baseUrlSecure && location.startsWith(baseUrlSecure)) {
                href = URLNormalizer.normalize(href, baseUrlSecure);
            } else {
                href = URLNormalizer.normalize(href, baseUrl);

                if (makeRelativeUrlAbsolute) {
                    try {
                        // Check for http (and https) or a protocol indicator
                        if (!href.startsWith("http") && !href.contains("://")) {
                            URL baseUrl = new URL(location);
                            URL url = new URL(baseUrl, href);
                            logger.trace("made a new href: {} from {}", url, href);
                            href = url.toString();
                        }
                    } catch (Exception e) {
                        logger.error("Failed to make this relative URL absolute: {}", href, e);
                    }
                } else {
                    logger.trace("makeRelativeUrlAbsolute is disabled");
                }
            }

            if (ignoreQueryParams) {
                href = href.split("\\?")[0];
            }

            if (toVisit.contains(href)) {
                logger.trace("ignoring the URL, it has already been scheduled: {}", href);
                continue;
            }

            if (!urlFrom.containsKey(href)) {
                urlFrom.put(href, new ConcurrentSkipListSet<String>());
            }

            if (!urlFrom.get(href).contains(location)) {
                urlFrom.get(href).add(location);
            }

            toVisit.add(href);
        }
    }

    /**
     * <p>In case of no content, call this method. It will treat the empty page as a "410 Gone" response.</p>
     */
    private void handleEmptyPage() {
        for (SiteCrawlerAction sca : actions) {
            sca.handleError(410, location, urlFrom.get(location), page.getWebResponse());
        }
    }

    /**
     * <p>In case the page isn't a HTML page (which is what we expect), call this to log and process the page.</p>
     */
    private void handleNonHtmlPage() {
        String contentType = page.getWebResponse().getResponseHeaderValue("Content-Type");

        if (contentType != null && contentType.startsWith("application")) {
            logger.error("Unknown page type {} with Content-Type {} for url {}", new Object[] {
                page.getClass().getName(), contentType, location });
            return;
        }

        logger.error("Unknown page type {} with Content-Type {} for url {} with content: {}", new Object[] {
            page.getClass().getName(), contentType, location, page.getWebResponse().getContentAsString() });
    }

    /**
     * <p>Generic Exception handling. If there is a specific handler for the type of Exception, we will defer processing
     * to that.</p>
     */
    private void handleException() {
        if (exception instanceof FailingHttpStatusCodeException) {
            FailingHttpStatusCodeException e = (FailingHttpStatusCodeException) exception;
            handleFailingHttpStatusCodeException(e);

            if (isRedirect(e.getStatusCode())) {
                handleRedirect(e);
            }
        } else if (exception instanceof SSLException) {
            logger.error("SSLException at {}", location, exception);
        } else {
            logger.error("Unhandled exception on page '{}'", location, exception);
        }
    }

    /**
     * <p>Call all configured actions and call their error handlers.</p>
     * 
     * @param e FailingHttpStatusCodeException The exception to be handled
     */
    private void handleFailingHttpStatusCodeException(FailingHttpStatusCodeException e) {
        for (SiteCrawlerAction sca : actions) {
            sca.handleError(e.getStatusCode(), location, urlFrom.get(location), e.getResponse());
        }
    }

    /**
     * <p>Returns true is the statuscode represents a redirect. Currently we support "301 Moved Permanently" and
     * "302 Found".</p>
     * 
     * @param statusCode The status code to check
     * @return boolean true if the statuscode is a redirect code (301 or 302)
     */
    private boolean isRedirect(int statusCode) {
        return (statusCode > 300 && statusCode < 303);
    }

    /**
     * <p>Redirects are simply added to the "to be visited" list to be processed by the crawler later.</p>
     * 
     * @param sce
     */
    private void handleRedirect(FailingHttpStatusCodeException sce) {
        String redirect = URLNormalizer.normalize(sce.getResponse().getResponseHeaderValue("Location"), baseUrl);

        // Adding the redirect target (<code>redirect</code>) to the urlFrom map. If there is a Set in urlFrom for the
        // current page we're visiting, add it as the "source" for the target.
        Set<String> redirectFrom = urlFrom.get(location);
        if (null != redirectFrom) {
            urlFrom.put(redirect, redirectFrom);
        }

        toVisit.add(redirect);
    }
}
