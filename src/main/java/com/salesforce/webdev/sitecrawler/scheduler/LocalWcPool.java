package com.salesforce.webdev.sitecrawler.scheduler;

import com.salesforce.webdev.sitecrawler.beans.CrawlerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.salesforce.webdev.sitecrawler.SiteCrawler;
import com.salesforce.webdev.sitecrawler.webclient.WebClientPool;

public class LocalWcPool {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * <p>The pool is used to provide {@link WebClient}s to the {@link #linkService}.</p>
     * 
     * <p>By default, it is initialized to the same amount of clients as the Service has threads ({@link #threadLimit} .</p>
     */
    private WebClientPool wcPool;

    public WebClientPool getWebClientPool() {
        return this.wcPool;
    }

    /**
     * <p>Remove all cookies from all {@link WebClient}s in the pool.</p>
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
     * <p>Does its best to reset/recreate the WebClient Pool (wcPool) and the link and page consumers.</p>
     *
     * <p>Calls {@link #init(CrawlerConfiguration)} directly.</p>
     *
     * @param siteCrawler An {@link SiteCrawler} to initialize
     */
    public void init(SiteCrawler siteCrawler) {
        init(siteCrawler.getCrawlerConfiguration());
    }

    /**
     * <p>Does its best to reset/recreate the WebClient Pool (wcPool) and the link and page consumers.</p>
     *
     * @param crawlerConfiguration A {@link CrawlerConfiguration} to initialize
     */
    public void init(CrawlerConfiguration crawlerConfiguration) {
        if (null != wcPool) {
            wcPool.close();
        }
        wcPool = new WebClientPool(crawlerConfiguration.threadLimit);
        if (crawlerConfiguration.disableRedirects) {
            wcPool.disableRedirects();
        }
        if (crawlerConfiguration.enabledJavascript) {
            wcPool.enableJavaScript();
        }
        for (Cookie cookie : crawlerConfiguration.cookies) {
            wcPool.addCookie(cookie);
        }
        wcPool.setName("Sitecrawler pool");

        Object[] args = { wcPool.getName(), crawlerConfiguration.threadLimit };
        logger.info("WebClientPool {} created with size {}", args);
    }

    /**
     * <p>Tell the local Webclient pool to shutdown.</p>
     */
    public void shutdown() {
        if (null != wcPool) {
            wcPool.close();
        }

    }
}
