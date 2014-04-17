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
package com.salesforce.webdev.sitecrawler.navigation;

/**
 * <p>In case there is a problem retrieving a page this Exception is thrown.</p>
 * 
 * <p>It can be an known issue, a network error or a parsing problem.</p>
 * 
 * @author jroel
 * @since v1.0
 * 
 */
public class NavigateThreadException extends Exception {

    /**
     * <p>Serialization entry.</p>
     */
    private static final long serialVersionUID = 7287177219326843471L;

    /**
     * <p>Constructor with a single message explaining the issue.</p>
     * 
     * @param msg A description of why this exception is raised (can be null).
     */
    public NavigateThreadException(String msg) {
        super(msg);
    }

    /**
     * <p>Constructor which takes a root cause.</p>
     * 
     * @param e The root cause used (cannot be null).
     */
    public NavigateThreadException(Exception e) {
        super(e);
    }
}
