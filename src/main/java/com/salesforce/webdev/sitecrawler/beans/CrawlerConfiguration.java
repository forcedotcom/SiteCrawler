package com.salesforce.webdev.sitecrawler.beans;

import java.util.LinkedList;
import java.util.List;

import com.gargoylesoftware.htmlunit.util.Cookie;
import com.salesforce.webdev.sitecrawler.SiteCrawler;
import com.salesforce.webdev.sitecrawler.SiteCrawlerAction;

/**
 * <p>Simple bean to collect the configuration of the SiteCrawler.</p>
 * 
 * @author jroel
 *
 */
public class CrawlerConfiguration {
    public String baseUrl;
    public String baseUrlSecure;

    /**
     * @see SiteCrawler#getThreadLimit()
     */
    public int threadLimit;

    public double downloadVsProcessRatio;
    public double maxProcessWaitingRatio;

    public int maxProcessWaiting;

    public int shortCircuitAfter;

    public boolean disableRedirects;
    public boolean enabledJavascript;
    public List<? extends SiteCrawlerAction> actions;

    public List<Cookie> cookies = new LinkedList<>();
}
