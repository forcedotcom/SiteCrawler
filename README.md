SiteCrawler
===========


This is a Java library which can be used to crawl the content of some of web properties (www.salesforce.com, blogs.salesforce.com for example). It supports dynamic scaling (depending on available machine power (CPU, RAM) and network capacity) out of the box. It also has a Plugin structure, which allows others to write code (plugins) that act on it.

In short, a no-nonsense sitecrawler, easy-to-use, that adapts to your environment and is easily extendable.

## Features
- Multi-threaded crawler
- Separates the crawling from the page processing
- Easily configurable
- Plugin structure
- Automatic back-off system

## Basic usage

```java
Collection<SiteCrawlerAction> actions = ... some list of cool actions ...

SiteCrawler siteCrawler = new SiteCrawler("http://www.salesforce.com", "https://www.salesforce.com", actions);
siteCrawler.navigate();
```

That's it!

Obviously you can get fancy by instructing it to follow redirects (or not), disable javascript parsing, set cookies (for authenticated areas, for instance), etc.

## Options (before starting the crawler)
```java
/**
 * This defines the starting set of the crawler.
 * You can set it to "/" to start at the root, or give it a collection (your sitemap?) to start from
 */
.setIncludePath(Collection<String> paths)

/**
 * Set the limit on amount of pages in the queue to be processed. The crawler pauses to avoid exhausting memory (for example).
 */
.setMaxProcessWaiting(int maxProcessWaiting)
.getMaxProcessWaiting()

/**
 * If there are patterns you don't want to get crawled, you can define that here.
 * You can set it to "/admin/" for example. It will search for those Strings in the URL.
 * NOTE: That means that "/foo/admin/bar/" will also be blocked by "/admin/".
 */
.setBlocked(Collection<String> blocked)

/**
 * Return the collection of extensions to be parsed.
 * If a found link does not have this extension, it won't be crawled 
 */
.getAllowedSuffixes()

/**
 * By default, the crawler starts with javascript disabled. This will turn the default parser on
 */
.enableJavaScript()
.disableJavaScript()

/**
 * By default, the crawler follows redirects to it's final target. You can manipulate this using these methonds.
 */
.enableRedirects()
.disableRedirects()

/**
 * Add a cookie to use on every request (an authentication token, for example)
 * This is a convenience method for .addCookie(Cookie cookie)
 */
.addCookie(String name, String value, String domain)
 
/**
 * Add a cookie to use on every request (an authentication token, for example)
 */
.addCookie(Cookie cookie)

/**
 * Removes all defined cookies (the crawler starts with no cookies by default) 
 */
.clearCookies()

/**
 * If the crawler navigated to this many pages, it will "short circuit" and stop crawling.
 * The crawler will process the plugins as usual and return as usual (and at a note to the log that it short circuited)
 * NOTE: You can still set this during the "running" phase of the crawler (but you might also want to take a look at .disableCrawling()) 
 */
.setShortCircuitAfter(int shortCircuitAfter)
.getShortCircuitAfter()

/**
 * By default, the crawler sets a "best guess" amount of threads, this will allow you to override that setting and hardcode the number of threads to use.
 * NOTE: You can call this during the "running" phase, but it will cause a reset of all queues and threads. 
 */
.setThreadLimit(int threadLimit)
```

## Options (to start the crawler)
```java
/**
 * This will start the crawler and the above options may no longer be reliable used
 * This is a blocking operation (it will return once the crawling and processing it complete) 
 */
.navigate()
```

## Options (after starting the crawler)
```java
/**
 * The regular pause and unpause allow the crawler to stop processing the queue (but continue the running I/O threads and modules).
 * If you require the I/O threads and modules to be shutdown as well, use the "hard" methods.
 *
 * Unpausing in either case means that the queue will processed again (and the threads/modules to be started again as well).
 */
.pause() / .unpause()
.hardPause() / .hardUnpause()

/**
 * This is simply an alias for .hardPause() followed by a .hardUnpause().
 */
.reset()
/**
 * This will tell the Crawler to stop adding new pages to the list of pages to crawl.
 * It will process the list as it stands and return normally
 */
.disableCrawling()

/**
 * This will tell the Crawler to stop crawling and processing pages.
 * it gives both the crawling and the processing threads 2 minutes to stop before killing them
 */
.shutdown()

/**
 * This return a String with the crawl progress and will look like this:
 * "44132 crawled. 2798 left to crawl. 1868 scheduled for download. 3 scheduled for processing. 94.27% complete."
 */
.getCrawlProgress()
```

## Command line options (VM arguments)
The crawler also supports some quick configuration through VM arguments:

* sc:threadLimit=&lt;int&gt; 
* sc:maxProcessWaiting=&lt;int&gt; 
* sc:shortCircuitAfter=&lt;int&gt; 
* sc:downloadVsProcessRatio&lt;int&gt;

This will allow you to control the initial settings through the command line instead of Java code. An example would be:
```
java -jar siteCrawler.jar -Dsc:threadLimit=4 -Dsc:maxProcessWaiting=100 -Dsc:shortCircuitAfter=1000
```

## Installation
This is a basic maven project, so a simple `mvn install` should suffice to get it locally installed.
 
## Notes
- The crawler does not listen of adhere to "robots.txt".
It should be relatively easy though to parse this separately and pass the results to ".setBlocked()".

- Its exclusion algorithm is currently 2-fold:
  1. If it sees the exact same URL, it'll be ignored
  2. It also has a way less strict "domain and path" check only (which, in short, ignores protocol and parameters)

# License
The BSD 2-Clause License

http://opensource.org/licenses/BSD-2-Clause

See [LICENSE.txt](./LICENSE.txt)