import java.util.*;

public class QuickDownload
{
  public static void main (String args[])
  {
    if (args.length < 1)
    {
      System.out.println("missing link or id");
      System.out.println("Usage: java QuickDownload <link/id>");
      return;
    }

    String params[] = Downloader.getParams(args[0].length() == 11 ? "http://youtube.com/watch?v=" + args[0] : args[0]);
    if (params == null)
    {
      return;
    }

    try
    {
      Downloader.start(params);
    }
    catch (Exception e)
    {
      System.err.printf("[%s] error: %s\n", new Date(), e.getMessage());
    }
  }
}
