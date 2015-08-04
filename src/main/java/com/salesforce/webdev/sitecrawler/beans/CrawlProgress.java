package com.salesforce.webdev.sitecrawler.beans;

/**
 * <p>Simple bean to collect the progress of a crawl.</p>
 * 
 * @author jroel
 *
 */
public class CrawlProgress {
    /**
     * <p>Total pages crawled.</p>
     */
    public long crawled;
    /**
     * <p>Number of pages left to crawl.</p>
     */
    public long leftToCrawl;
    /**
     * <p>Number of pages currently in the queue, waiting to be downloaded.</p>
     */
    public long scheduledForDownload;
    /**
     * <p>Number of pages currently in the queue (already downloaded), waiting to be processed.</p>
     */
    public long scheduledForProcessing;
    /**
     * <p>Number of pages completely processed.</p>
     */
    public long fullyProcessed;
    /**
     * <p>Percentage of crawl complete (estimated).</p>
     */
    public double complete;
}