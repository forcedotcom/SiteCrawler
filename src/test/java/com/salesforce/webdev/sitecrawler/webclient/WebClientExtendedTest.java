package com.salesforce.webdev.sitecrawler.webclient;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.URL;

import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;

public class WebClientExtendedTest {

    /**
     * <p>Integration test, this will reach out!</p>
     * 
     * @throws IOException
     * @throws InterruptedException
     * @throws WebClientPoolClosedException 
     */
    //    @Test
    public void testCache() throws IOException, InterruptedException, WebClientPoolClosedException {
        String url = "http://www.sfdcstatic.com/common/assets/img/logo-company.png";

        // Use "1" to restrict the pool to a single crawler
        WebClientPool pool = new WebClientPool(1);
        WebClientExtended webClient = pool.takeClient();

        WebRequest webRequest = new WebRequest(new URL(url));

        WebResponse response = webClient.loadWebResponseWithRetry(webRequest);
        assertNotNull(response);

        Object cacheResult = webClient.getWebClient().getCache().getCachedObject(webRequest);
        assertNotNull(cacheResult);

    }
}
