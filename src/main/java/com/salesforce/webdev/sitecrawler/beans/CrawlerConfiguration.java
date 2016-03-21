package com.salesforce.webdev.sitecrawler.beans;

import com.salesforce.webdev.sitecrawler.SiteCrawler;

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
}
