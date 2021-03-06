import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

public class Downloader
    implements Runnable
{
  private static final String agent = "YouTubeDownloader";

  String filename;
  String address;

  /**
   * @param params Paramaters as returned by getParams(String)
   */
  public static void start(String[] params)
    throws IllegalThreadStateException
  {
    (new Thread(new Downloader(params))).start();
  }

  Downloader(String params[])
  {
    filename = params[0] + " " + params[2].replaceAll("[^ \\w]", "") + "." + params[3];
    address = params[1];
  }

  public void run()
  {
    // perform transfer
    System.out.printf("[%s] saving \"%s\"\n", new Date(), filename);
    try (
        InputStream read = new URL(address).openStream();
        ReadableByteChannel in = Channels.newChannel(read);
        FileOutputStream write = new FileOutputStream(filename);
        FileChannel out = write.getChannel())
    {
      long start = (new Date()).getTime();
      long bytes = out.transferFrom(in, 0, Integer.MAX_VALUE);
      long duration = ((new Date()).getTime() - start) / 1000;
      System.out.printf("[%s] done \"%s\", %d kB in %d seconds, %d kB/s\n", new Date(), filename, bytes / 1000, duration, bytes / duration / 1000);
    }
    catch (Exception e)
    {
      System.err.printf("[%s] error: %s\n", new Date(), e.getMessage());
    }
  }

  /**
   * @return Array of id, URL, title and type or null.
   */
  public static String[] getParams(String link)
  {
    int i, itag;
    boolean found;
    String id, title, formats, line, response, url, sig, format_lines[], format_pairs[], result[];
    URL address;
    HttpURLConnection connection;
    BufferedReader rd;
    StringBuilder sb;

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
      System.err.printf("[%s] error: %s\n", new Date(), e.getMessage());
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
      System.err.printf("[%s] error: %s\n", new Date(), e.getMessage());
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
            System.err.printf("[%s] error: %s\n", new Date(), e.getMessage());
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
        return new String[] {id, url + "&signature=" + sig, title, itag == 35 ? "flv" : "mp4"};
      }
    }

    return null;
  }
}
