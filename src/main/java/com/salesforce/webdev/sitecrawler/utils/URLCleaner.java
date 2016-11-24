package com.salesforce.webdev.sitecrawler.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class URLCleaner {

    public class URLCleanerOptions {
        /**
         * <p>A URL without an extension should not end in a slash.</p>
         */
        private boolean urlPathShouldNotEndInSlash = true;

        private Collection<String> allowedParameters = new ArrayList<String>();

        public boolean isUrlPathShouldNotEndInSlash() {
            return urlPathShouldNotEndInSlash;
        }

        public void setUrlPathShouldNotEndInSlash(boolean urlPathShouldNotEndInSlash) {
            this.urlPathShouldNotEndInSlash = urlPathShouldNotEndInSlash;
        }


        public void setAllowedParameters(final Collection<String> parameters) {
            this.allowedParameters.clear();
            this.allowedParameters.addAll(parameters);
        }

        public void addAllowedParameters(final String parameter) {
            this.allowedParameters.add(parameter);
        }

        public void removeAllowedParameter(final String parameter) {
            this.allowedParameters.remove(parameter);
        }
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final URLCleanerOptions options = new URLCleanerOptions();

    public URLCleanerOptions getOptions() {
        return options;
    }

    /**
     * <p>Creates a URL that ignores the protocol, port and the params (basically everything except the Host and Path).</p>
     * 
     * @param url Needs to be a full and valid URL (http://www.salesforce.com/foo.html?bar)
     * @return the "clean" URL (www.salesforce.com/foo.html for example) or null if url is empty or an Exception happened
     */
    public String getCleanedUrl(String url) {
        if (null == url) {
            return null;
        }
        try {
            URL cleanUrl = new URL(url);
            // We strip of the ending "/" (if there isn't an extension)
            if (cleanUrl.getPath().endsWith("/") && !cleanUrl.getPath().contains(".") && options.urlPathShouldNotEndInSlash) {
                cleanUrl = new URL(url.substring(0, url.length() - 1));
            }
            StringBuilder sb = new StringBuilder();
            sb.append(cleanUrl.getHost()).append(cleanUrl.getPath());

            String q = cleanUrl.getQuery();
            if (null != q && !q.isEmpty() && !options.allowedParameters.isEmpty()) {
                boolean firstItem = true;
                // Split into parts
                String[] qParts = q.split("&");
                logger.debug("Query Parts: {}", (Object) qParts);
                for (String qPart : qParts) {
                    // Get the key
                    String qKey = qPart;
                    logger.debug("Query Key: {}", qKey);
                    if (qPart.contains("=")) {
                        qKey = qPart.substring(0, qPart.indexOf("="));
                        logger.debug("Query Key (sub'd): {}", qKey);
                    }
                    // Now we check of that key is allowed
                    if (options.allowedParameters.contains(qKey)) {
                        logger.debug("allowedParameters contains {}", qKey);
                        // We do, so attach it to the URL
                        if (firstItem) {
                            sb.append("?");
                            firstItem = false;
                        } else {
                            sb.append("&");
                        }
                        sb.append(qPart);
                    } else {
                        logger.debug("allowedParameters DOES NOT contains {}", qKey);
                    }
                }
            }

            logger.trace("Cleaned up URL [{}] to this: {}", url, sb);
            return sb.toString();
        } catch (MalformedURLException e) {
            logger.error("Could not clean up URL (though this could be perfectly fine) {}", url, e);
            return null;
        }
    }
}
