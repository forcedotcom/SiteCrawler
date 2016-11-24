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
package com.salesforce.webdev.sitecrawler.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A helper class to normalize a URL.</p>
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public final class URLNormalizer {

    private static Logger logger = LoggerFactory.getLogger(URLNormalizer.class);

    /**
     * <p>Private to prevent instantiation.</p>
     */
    private URLNormalizer() {
    }

    /**
     * <p><strong> Use {@link #normalize(String, String, String)} for a better resolving experience.</strong></p>
     * 
     * <p>If #url starts with "/", #base will be prepended.<br />
     * So, if url = <code>"/x"</code> and base is <code>"base.com"</code>, the return value will be
     * <code>"base.com/x"</code>.</p>
     * 
     * <p>Then again, if the #url does NOT start with /, #base will be completely ignored.</p>
     * 
     * <p>If the #url ends with "index.jsp", this will be stripped from the result.</p>
     * 
     * @param url A regular URL. Cannot be null
     * @param base A String that gets prepended to the result if #url starts with "/"
     * @return A normalized string with base prepended (if needed) and /index.jsp stripped if present at the end of #url
     */
    public static String normalize(String url, String base) {
        url = url.trim();
        if (url.startsWith("/") && !url.startsWith("//")) {
            url = base + url;
        }

        if (url.endsWith("/index.jsp")) {
            url = url.replace("/index.jsp", "/");
        }

        return url;
    }

    /**
     * <p>If this is already a "normal" URL (starts with an HTTP protocol or a /", we call {@link #normalize(String, String)}.</p>
     * 
     * <p>This normalizer does a small effort to deal with relative URLs as well (hence, the pageOrigin parameter).</p>
     * 
     * @param url A regular URL. Cannot be null
     * @param base A String that gets prepended to the result if #url starts with "/"
     * @param pageOrigin The page this url appears on. Can be null
     * @return A normalized string with base prepended (if needed) and /index.jsp stripped if present at the end of #url
     */
    public static String normalize(String url, String base, String pageOrigin) {
        url = url.trim();
        if ((url.startsWith("/") && !url.startsWith("//")) || url.startsWith("http://") || url.startsWith("https://")) {
            return normalize(url, base);
        }

        if (url.isEmpty() || url.startsWith("tel:") || url.startsWith("#") || url.startsWith("???") || url.startsWith("mailto:")
            || url.startsWith("javascript:")) {
            return normalize(url, base);
        }

        // We also need to deal with "//" URLs
        if (url.startsWith("//")) {
            // Prepend the protocol of the pageOrigin
            // This gets the "https:" (or "http:") part and prepends it to create a "https://..." string
            String pageOriginProtocol = pageOrigin.substring(0, pageOrigin.indexOf("/"));
            url = pageOriginProtocol + url;
            return normalize(url, base);
        }

        if (null != pageOrigin && !pageOrigin.endsWith("/") && pageOrigin.contains("/")) {
            pageOrigin = pageOrigin.substring(0, pageOrigin.lastIndexOf("/") + 1);

            // Assume that it's a relative URL, create a full URL
            url = pageOrigin + url;
            return normalize(url, base);
        }

        // "assets/foo.png" && "https://www.salesforce.com/bar/"
        if (null != pageOrigin && pageOrigin.endsWith("/")) {
            logger.trace("[1/2] found url: {} and pageOrigin: {}...", url, pageOrigin);
            url = pageOrigin + url;
            logger.trace("[2/2] ...created: {}", url);
            return normalize(url, base);
        }
        return normalize(url, base);
    }
}
