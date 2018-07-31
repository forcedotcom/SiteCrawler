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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.ProxyConfig;
import com.gargoylesoftware.htmlunit.WebClient;
import com.salesforce.webdev.sitecrawler.utils.EmptyJavascriptEngine;

/**
 * <p>Gets the proxy settings via the system setting called "PROXY_STRING". Format of this String is
 * <code>&lt;host&gt;:&lt;port&gt;</code> (so, <code>localhost:8080</code> for example).</p>
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public final class WebClientFactory {

    private final static String PROXY_CONFIG = "PROXY_STRING";
    private static boolean proxyInitialized;
    private static ProxyConfig proxy;

    /**
     * <p>Private to prevent instantiation.</p>
     */
    private WebClientFactory() {
    }

    /**
     * <p>This is the system variable we will look for to parse the Proxy configuration.</p>
     * <p>TODO: There are default system properties for this? (proxy.host, proxy.port).</p>
     * 
     * <p>Format: &lt;host&gt;:&lt;port&gt; (so, <code>localhost:8080</code> for example).</p>
     */
    public static void setProxyBasedOnSystemProperties() {
        final Logger logger = LoggerFactory.getLogger(WebClientFactory.class);
        String proxyString = System.getProperty(PROXY_CONFIG);
        if (proxyString == null) {
            logger.info("No proxy configured via system property '{}'", PROXY_CONFIG);
        } else {
            logger.info("Using a proxy: {}", proxyString);
            try {
                proxy = new ProxyConfig(proxyString.split(":")[0], Integer.parseInt(proxyString.split(":")[1]));
            } catch (Exception e) {
                logger.error("Error parsing proxyString: {}", proxyString, e);
            }
        }
    }

    /**
     * <p>Get a WebClient initialized to all the default of our QA environment.</p>
     * 
     * <p>In practice that means:</p><ul>
     * <li>The proxy is set if available</li>
     * <li>Javascript &amp; css is disabled</li>
     * <li>You will get an Exception on a failing status code</li>
     * <li>Any certificate will do for SSL (if possible)</li>
     * </ul>
     * 
     * @return A fully initialized {@link WebClient}
     */
    public static WebClient getDefault() {
        if (!proxyInitialized) {
            setProxyBasedOnSystemProperties();
            proxyInitialized = true;
        }

        WebClient wc = new WebClient();
        wc.getOptions().setJavaScriptEnabled(false);
        wc.getOptions().setCssEnabled(false);
        wc.getOptions().setThrowExceptionOnScriptError(false);
        wc.getOptions().setThrowExceptionOnFailingStatusCode(true);

        // Disable the creation of empty javascript threads (which leak memory and threads)
        // if javascript is disabled
        if (!wc.getOptions().isJavaScriptEnabled()) {
            wc.setJavaScriptEngine(new EmptyJavascriptEngine(wc));
        }

        // Stop printing so much crap to the log :-)
        wc.getOptions().setPrintContentOnFailingStatusCode(false);
        wc.getOptions().setUseInsecureSSL(true);

        // Set a custom "bot" user-agent
        wc.getBrowserVersion().setUserAgent(
            "Mozilla/5.0 (compatible; Salesforcebot/1.0; +http://www.salesforce.com/ - wwwbackend@salesforce.com)");

        if (proxy != null) {
            wc.getOptions().setProxyConfig(proxy);
        }

        return wc;
    }
}
