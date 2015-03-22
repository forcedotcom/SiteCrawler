package com.salesforce.webdev.sitecrawler;

import java.util.Collection;
import java.util.LinkedList;

import org.junit.Assert;
import org.junit.Test;

public class SiteCrawlerTest {

    @Test
    public void testGetCleanedUrl() {
        SiteCrawler s = new SiteCrawler("", "", new LinkedList<SiteCrawlerAction>());
        String url = "https://www.salesforce.com?a=b&c=d&e=f";
        String clean = s.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com", clean);

        Collection<String> parameters = new LinkedList<String>();
        parameters.add("a");
        s.setAllowedParameters(parameters);

        clean = s.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com?a=b", clean);

        clean = s.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com?a=b", clean);

        parameters.add("c");
        s.setAllowedParameters(parameters);

        clean = s.getCleanedUrl(url);
        Assert.assertEquals("www.salesforce.com?a=b&c=d", clean);
    }
}
