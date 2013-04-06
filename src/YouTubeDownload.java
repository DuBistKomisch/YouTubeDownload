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
        System.err.println("error: " + e.getMessage());
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
        String link[] = getLink(entry.getLink());
        if (link != null)
          download(link);
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

    // get id, title and formats
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
            e.printStackTrace(log);
          }
        // get auth signature
        if (format_pair.indexOf("sig") == 0)
          sig = format_pair.substring(4);
        // check for which quality we want
        if (format_pair.indexOf("itag") == 0)
          switch (itag = Integer.parseInt(format_pair.substring(5, 7)))
          {
            // TODO config
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
      // perform transfer
      System.out.println("saving \"" + filename + "\"");
      try (
          InputStream read = new URL(address).openStream();
          ReadableByteChannel in = Channels.newChannel(read);
          FileOutputStream write = new FileOutputStream(filename);
          FileChannel out = write.getChannel())
      {
        long start = (new Date()).getTime();
        long bytes = out.transferFrom(in, 0, Integer.MAX_VALUE);
        long duration = ((new Date()).getTime() - start) / 1000;
        System.out.printf("done \"%s\", %d kB in %d seconds, %d kB/s\n", filename, bytes / 1000, duration, bytes / duration / 1000);
      }
      catch (Exception e)
      {
        System.err.println("error: " + e.getMessage());
      }
    }
  }
}
