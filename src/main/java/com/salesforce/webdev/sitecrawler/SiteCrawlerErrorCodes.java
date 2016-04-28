package com.salesforce.webdev.sitecrawler;

import com.salesforce.webdev.sitecrawler.navigation.ProcessPage;

/**
 * <p>This is a collection of error codes used throughout the SiteCrawler (although mostly by {@link ProcessPage}.</p>
 * 
 * <p>It contains the basic HTTP codes (and the code maps to the natural error code) as well as some crawler specific errors.</p>
 * <p>The special codes are in the &gt;=1000 range.</p>
 * 
 * @author jroel
 * @since 0.0.7-SNAPSHOT
 *
 */
public enum SiteCrawlerErrorCodes {

    HTTP_PERMANENT_REDIRECT(301),
    HTTP_TEMPORARY_REDIRECT(302),
    HTTP_FORBIDDEN(403),
    HTTP_NOT_FOUND(404),
    HTTP_INTERNAL_SERVER_ERROR(500),
    HTTP_GONE(410),
    HTTP_GENERIC_ERROR(900),

    PAGEOBJECT_COULD_NOT_BE_FOUND(1000),
    WEBRESPONSE_COULD_NOT_BE_FOUND(1001),
    CONTENTSTRING_COULD_NOT_BE_FOUND(1002),

    SOCKET_EXCEPTION(2000),
    SOCKET_EXCEPTION_CONNECTION_RESET(2001),

    UNKNOWN_HOST_EXCEPTION(3000);

    /**
     * <p>The actual internal error code.</p>
     */
    private final int errorCode;

    private SiteCrawlerErrorCodes(int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * <p>Return the errorcode. For most HTTP_* results, this maps to the natural errorcode (404 for Not Found, etc).</p>
     * 
     * @return int the error code
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * <p>Returns the {@link SiteCrawlerErrorCodes} associated with the code.</p>
     * 
     * @param code any integer (error code)
     * @return the {@link SiteCrawlerErrorCodes} for that code, or null if no match is found.
     */
    public static SiteCrawlerErrorCodes getByCode(int code) {
        for (SiteCrawlerErrorCodes siteCrawlerErrorCode : SiteCrawlerErrorCodes.values()) {
            if (code == siteCrawlerErrorCode.getErrorCode()) {
                return siteCrawlerErrorCode;
            }
        }
        return null;
    }
}
