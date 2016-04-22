package com.salesforce.webdev.sitecrawler.webclient;

/**
 * <p>Exception to indicate that a pool is already closed.</p>
 * 
 * @author jroel
 *
 */
public class WebClientPoolClosedException extends Exception {

    private static final long serialVersionUID = 8037793523887890949L;

    public WebClientPoolClosedException(String message) {
        super(message);
    }
}
