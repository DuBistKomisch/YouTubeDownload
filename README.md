YouTubeDownload
===============

A service written in Java which downloads videos for later watching from an RSS feed

Makes use of the JDOM and ROME libraries for RSS processing.

Installation
============

Simply dump the files into a folder and run make.

Configuration
=============

To configure a feed, copy `sample.properties` and fill in the values.

Usage
=====

`./quick.sh <link/id>` can be used to download a video from a full link or ID.

Use `./feed.sh conf/myconf.properties` to start the service.

If you're on a different platform, take a look at the script to see how to
invoke Java with the correct classpath.

Hit `ctrl-c` to stop the service.
