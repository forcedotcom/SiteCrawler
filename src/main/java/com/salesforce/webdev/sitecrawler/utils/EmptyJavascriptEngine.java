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

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;

/**
 * <p>Empty JavaScriptEngine to use for the WebClient in case javascript is disabled.</p>
 * 
 * <p>This is an attempt to stop the javascript engine from leaking threads and memory is javascript is disabled.</p>
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public class EmptyJavascriptEngine extends JavaScriptEngine {

    /**
     * Creates an instance for the specified {@link WebClient}.
     * 
     * @param webClient the client that will own this engine
     */
    public EmptyJavascriptEngine(final WebClient webClient) {
        super(webClient);
    }

    /**
     * <p>This is turned into a NOOP if javascript is disabled, to stop the parent implementation from leaking threads
     * and memory.</p>
     * 
     * {@inheritDoc}
     */
    @Override
    public void registerWindowAndMaybeStartEventLoop(final WebWindow webWindow) {
        if (getWebClient().getOptions().isJavaScriptEnabled()) {
            super.registerWindowAndMaybeStartEventLoop(webWindow);
        }
    }
}
