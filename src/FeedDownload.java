import java.io.*;
import java.net.*;
import java.util.*;

import com.sun.syndication.feed.synd.*;
import com.sun.syndication.io.*;

public class FeedDownload
{
  // config
  private static String logFile;
  private static String rssAddress;
  private static long interval;

  // state
  private static PrintWriter log;
  private static long lastUpdate = (new Date()).getTime();

  public static void main (String args[])
  {
    // get configuration
    try
    {
      // load properties file
      Properties prop = new Properties();
      prop.load(new FileReader(args[0]));

      // take the properties we want, see sample for explanations
      rssAddress = prop.getProperty("feed");
      logFile = prop.getProperty("logfile");
      interval = 1000 * Long.parseLong(prop.getProperty("interval"));
    }
    catch (Exception e)
    {
      System.err.println("error: " + e.getMessage());
      System.out.println("invalid or missing configuration file");
      System.out.println("Usage: java YouTubeDownload <config>");
      return;
    }

    // start logging
    try
    {
      log = new PrintWriter(new FileWriter(logFile, true), true);
    }
    catch (Exception e)
    {
      System.err.println("error: " + e.getMessage());
      System.out.println("failed to open log, terminating");
    }

    // continually poll
    while (true)
    {
      try
      {
        poll();
        Thread.sleep(interval);
      }
      catch (Exception e)
      {
        System.err.printf("[%s] error: %s\n", new Date(), e.getMessage());
        e.printStackTrace(log);
      }
    }
  }

  private static void poll()
    throws Exception
  {
    log.printf("\n[%s] >> poll\n", new Date());

    // fetch
    SyndFeed feed = (new SyndFeedInput()).build(new XmlReader(new URL(rssAddress)));

    // iterate
    List entries = feed.getEntries();
    for (int i = entries.size() - 1; i >= 0; i--)
    {
      SyndEntry entry = (SyndEntry)entries.get(i);
      if (entry.getPublishedDate().getTime() > lastUpdate)
      {
        // found a new video
        lastUpdate = entry.getPublishedDate().getTime();

        log.printf("\n[%s] >> getParams\n", new Date());
        log.printf("link = %s\n", entry.getLink());
        String params[] = Downloader.getParams(entry.getLink());
        if (params != null)
          download(params);
      }
    }
  }

  static void download(String[] params)
  {
    log.printf("\n[%s] >> download\n", new Date());
    log.printf("id = %s\n", params[0]);
    log.printf("url = %s\n", params[1]);
    log.printf("title = %s\n", params[2]);
    log.printf("type = %s\n", params[3]);

    try
    {
      Downloader.start(params);
    }
    catch (Exception e)
    {
      System.err.printf("[%s] error: %s\n", new Date(), e.getMessage());
      e.printStackTrace(log);
    }
  }
}
