import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

import com.sun.syndication.feed.synd.*;
import com.sun.syndication.io.*;

public class YouTubeDownload
{
  // config
  private static final String agent = "YouTubeDownloader";
  private static final String logFile = "ytdl.log";
  private static final String rssAddress = "http://gdata.youtube.com/feeds/base/users/DuBistKomisch/uploads?alt=rss&orderby=published";
  private static final int interval = 30 * 60 * 1000;

  // state
  private static PrintWriter log;
  private static long lastUpdate = 0;

  public static void main (String args[])
  {
    // start logging
    try
    {
      log = new PrintWriter(new FileWriter(logFile, true), true);
    }
    catch (Exception e)
    {
      System.err.println("error: " + e.getMessage());
      System.err.println("failed to open log, terminating");
    }

    Calendar cal = new GregorianCalendar();
    cal.set(Calendar.YEAR, 2011);
    lastUpdate = cal.getTime().getTime();

    // continually poll
    while (true)
    {
      try
      {
        poll();
      }
      catch (Exception e)
      {
        System.err.println("error: " + e.getMessage());
        e.printStackTrace(log);
      }
      try
      {
      Thread.sleep(interval);
      }
      catch (InterruptedException e)
      {
        System.err.println("interrupted");
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
        lastUpdate = entry.getPublishedDate().getTime();
        download(getLink((entry.getLink())));
      }
    }
  }

  /**
   * @return Array of id, URL, title and type or null.
   */
  static String[] getLink(String link)
  {
    int i, itag;
    boolean found;
    String id, title, formats, line, response, url, sig, format_lines[], format_pairs[], result[];
    URL address;
    HttpURLConnection connection;
    BufferedReader rd;
    StringBuilder sb;

    log.printf("\n[%s] >> getLink\n", new Date());
    log.printf("link = %s\n", link);

    // send request
    try
    {
      address = new URL(link);
      connection = (HttpURLConnection)address.openConnection();
      connection.setDoOutput(true);
      connection.setReadTimeout(10000);
      connection.setRequestProperty("User-Agent", agent);
      connection.connect();
    }
    catch (Exception e)
    {
      System.err.println("error: " + e.getMessage());
      e.printStackTrace(log);
      return null;
    }

    // get response
    try
    {
      rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      sb = new StringBuilder();
      while ((line = rd.readLine()) != null)
        sb.append(line);
      response = sb.toString();
    }
    catch (Exception e)
    {
      System.err.println("error: " + e.getMessage());
      e.printStackTrace(log);
      return null;
    }

    // get title, ticket and formats
    i = response.indexOf("\"video_id\":") + 13;
    id = response.substring(i, response.indexOf("\"", i)).replaceAll("\\\\/", "/");
    i = response.indexOf("\"title\":") + 10;
    title = response.substring(i, response.indexOf("\"", i)).replaceAll("\\\\/", "/");
    i = response.indexOf("url_encoded_fmt_stream_map") + 30;
    formats = response.substring(i, response.indexOf("\"", i));

    // parse formats
    found = false;
    format_lines = formats.split(",");
    for (String format_line : format_lines)
    {
      format_pairs = format_line.split("\\\\u0026");
      url = "";
      sig = "";
      itag = 0;
      for (String format_pair : format_pairs)
      {
        // get and decode base url
        if (format_pair.indexOf("url") == 0)
          try
          {
            url = URLDecoder.decode(URLDecoder.decode(format_pair.substring(4), "UTF-8"), "UTF-8");
          }
          catch (Exception e)
          {
            System.err.println("error: " + e.getMessage());
          }
        // get auth signature
        if (format_pair.indexOf("sig") == 0)
          sig = format_pair.substring(4);
        // check for which quality we want
        if (format_pair.indexOf("itag") == 0)
          switch (itag = Integer.parseInt(format_pair.substring(5, 7)))
          {
            case 22: // MP4 720p
            case 35: // FLV 480p
            case 18: // MP4 360p
              found = true;
              break;
          }
      }
      // construct full url
      if (found)
      {
        log.println("found result");
        return new String[] {id, url + "&signature=" + sig, title, itag == 35 ? "flv" : "mp4"};
      }
    }

    log.println("no result");
    return null;
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
      (new Thread(new Downloader(params))).start();
    }
    catch (Exception e)
    {
      System.err.println("error: " + e.getMessage());
      e.printStackTrace(log);
    }
  }

  private static class Downloader
    implements Runnable
  {
    String filename;
    String address;

    public Downloader (String params[])
    {
      filename = params[0] + " " + params[2].replaceAll("[^ \\w]", "") + "." + params[3];
      address = params[1];
    }

    public void run ()
    {
      // do stuff
      System.err.println("saving \"" + filename + "\"");
      try (
          InputStream read = new URL(address).openStream();
          ReadableByteChannel in = Channels.newChannel(read);
          FileOutputStream write = new FileOutputStream(filename);
          FileChannel out = write.getChannel())
      {
        out.transferFrom(in, 0, Integer.MAX_VALUE);
      }
      catch (Exception e)
      {
        System.err.println("error: " + e.getMessage());
      }
    }
  }
}
