package com.salesforce.webdev.sitecrawler.scheduler;

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
     * <p>Does its best to reset/recreate the WebClient Pool (wcPool) and the link and page consumers.</p>
     */
    public void init(SiteCrawler siteCrawler) {
        if (null != wcPool) {
            wcPool.close();
        }
        wcPool = new WebClientPool(siteCrawler.getCrawlerConfiguration().threadLimit);
        if (siteCrawler.getCrawlerConfiguration().disableRedirects) {
            wcPool.disableRedirects();
        }
        if (siteCrawler.getCrawlerConfiguration().enabledJavascript) {
            wcPool.enableJavaScript();
        }
        for (Cookie cookie : siteCrawler.getCookies()) {
            wcPool.addCookie(cookie);
        }
        wcPool.setName("Sitecrawler pool");

        Object[] args = { wcPool.getName(), siteCrawler.getCrawlerConfiguration().threadLimit };
        logger.info("WebClientPool {} created with size {}", args);

    }

    /**
     * <p>Tell the executors ({@link #linkExecutor} and {@link #pageExecutor} to shutdown.</p>
     */
    public void shutdown() {
        if (null != wcPool) {
            wcPool.close();
        }

    }
}
