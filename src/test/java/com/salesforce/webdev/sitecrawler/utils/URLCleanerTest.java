package com.salesforce.webdev.sitecrawler.utils;

import java.util.Collection;
import java.util.LinkedList;

import org.junit.Assert;
import org.junit.Test;

public class URLCleanerTest {

    @Test
    public void testGetCleanedUrl() {
        URLCleaner urlCleaner = new URLCleaner();
        String url = "https://www.salesforce.com?a=b&c=d&e=f";
        String clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com", clean);

        Collection<String> parameters = new LinkedList<String>();
        parameters.add("a");
        urlCleaner.getOptions().setAllowedParameters(parameters);

        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com?a=b", clean);

        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com?a=b", clean);

        parameters.add("c");
        urlCleaner.getOptions().setAllowedParameters(parameters);

        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com?a=b&c=d", clean);
    }

    @Test
    public void testStripLastSlashFromPath() {
        URLCleaner urlCleaner = new URLCleaner();

        urlCleaner.getOptions().setUrlPathShouldNotEndInSlash(true);
        String url = "https://www.salesforce.com";
        String clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com", clean);

        url = "https://www.salesforce.com/";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com", clean);

        url = "https://www.salesforce.com/crm";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com/crm", clean);

        url = "https://www.salesforce.com/crm/";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com/crm", clean);

        url = "https://www.salesforce.com/crm.html";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com/crm.html", clean);

        // Set it to false
        urlCleaner.getOptions().setUrlPathShouldNotEndInSlash(false);
        url = "https://www.salesforce.com";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com", clean);

        url = "https://www.salesforce.com/";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com/", clean);

        url = "https://www.salesforce.com/crm";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com/crm", clean);

        url = "https://www.salesforce.com/crm/";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com/crm/", clean);

        url = "https://www.salesforce.com/crm.html";
        clean = urlCleaner.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com/crm.html", clean);

    }
}
