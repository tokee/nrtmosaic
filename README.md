# nrtmosaic

Fast mosaic creation from pre-processed source images

Demo with 1 million images in one: http://labs.statsbiblioteket.dk/zoom/

## Background

The raison d'être for nrtmosaic is to provide tiles for a newspaper page, visually made up by other newspaper pages. It is used at the project zoom at statsbiblioteket.dk. While nrtmosaic is sought to be generally usable, the focus is internal use.

As newspaper pages has an aspect ratio fairly near to 2:3. To create a mosaic from an image, the square pixels from the source must be mapped to non-square pages. This can be done by mapping 1x3-pixel from the source to 1x2 pages. To do this, we need fast access to average color (greyscale intensity really) for the top, middle and bottom third of all pages.

## Numbers & math

We have about **2.2 million** freely available newspaper pages. They are each 10MP+, for a raw pixel count around
**20 terapixels**.

Image servers likes tiles of 256x256 pixels and does not like being hammered with hundreds of requests at the same time.
For a full-screen display os 2048x1600 pixels, about 50 tiles are needed, so as long as we don't ask for smaller 
representations than 256x256 pixels from the image server, everything should work out ok.

To handle the 2:3 aspect ratio, we to cache need 6 tiles (2x3) at all levels below 256x256 pixels, meaning 
`128x128, 64x64,... 2x2, 1x1` . That means `6*128x128 + 6*64x64 + ... 6*2x2 + 6*1x1 pixels` for each image, which 
is about 130 kilopixel). If we store all tiles uncompressed on the file system, that takes up 
`2.2M * 130KB ~= **300GB**`.

For usable performance, we can cache all tiles from 16x16 pixels and below. That takes up
 `2.2M * 2KB ~= **4.5GB**` of memory.

## Mapping

The single pixels from the source image must be mapped to 2:3 aspect ratio destination images.
This is done by mapping 1x3 pixels in source to 1x2 images in destination.

To find the top image for 1x1½ pixel, the average of the upper 4 pixels `[0,0][1,0][0,1][1,1]` and the average of
then lower 2 pixels `[0,2][1,2]` is needed. The distance of the upper 1 pixel from source to the first average should
be primary and the distance from the lower ½ pixel to the second average should be secondary. This is done by creating
a lookup structure with key `[primary_average][secondary_average]` and value UUID. As a greyscale average can be
expressed in a single byte, the key range is 0-65535. There will normally be multiple values/key, so some mechanism for
avoiding selection of the same destination image is needed.

Finding the destination image for the bottom 1x1½ pixel is a mirror of the above, where the primary and secondary
average is switched for the lookup.


## Disk format

Each image is uniquely identified by a 128 bit UUID. To avoid a file count explosion we store the tiles in 256 files
 f approximately 1GB. The file is determined by the first byte in the image UUID. The format of each file is

```
[ID1]6*[1x1]6*[2x2]...6*[128x128]
[ID2]6*[1x1]6*[2x2]...6*[128x128]
...
[IDN]6*[1x1]6*[2x2]...6*[128x128]
```

Upon startup the mosaic server scans all tile collections, memory-caching tiles 1x1 to 16x16 and file-offsets for
the rest of the tiles.

## How to generate the tiles

For each image:

1 Request a version as close to 256x384 pixels as possible.
2 Generate the 6*128x128, 6*64x64 etc. tiles
3 Store the raw image data in a single file named after the image-ID in hex, stored in a folder named from the
first 4 hex-digit in the ID. `34fe/...`
 Content is `[IDn][witdh][height]6*[128x128]6*[64x64]...6*[1x1][average]`


### Deploy under Linux (only tested under Ubuntu 14.04)

#### Install apache + iipimage
nrtmosaic requires an image server (and some images), which runs under apache https or similar, using fastcgi.

Under Ubuntu 16.04, this should be possible by calling `sudo apt-get install iipimage-server`.
Possibly it needs explicit installation of apache2 and fastcgi 

```bash
sudo apt-get install apache2 libapache2-mod-fastcgi iipimage-server
sudo a2enmod cgi
sudo service apache2 restart
```

There seems to be a problem: http://iipimage.sourceforge.net/2012/05/new-debian-and-ubuntu-packages-released/
That can be solved by
```bash
sudo cp -r /usr/lib/iipimage-server /usr/share/iipimage-server
```
and adjusting of the path in `/etc/apache2/mods-available/iipsrv.conf` accordingly. Remember to restart apache2 with
`sudo service apache2 restart`.

Moreover, maybe `libapache2-mod-fcgid` should be used instead of `libapache2-mod-fastcgi`? 

If more problems arises, try debugging with `sudo apache2ctl -t`.  
   
To verify installation, visit http://localhost/iipsrv/iipsrv.fcgi which should show a status page for IIPImage.


If that does not work, more steps are needed: 
```bash
sudo apt-get install apache2
sudo apt-get install libapache2-mod-fastcgi
sudo a2enmod fastcgi
sudo apache2ctl graceful
...More to follow
```

### Generate some pyramid TIFFs
http://iipimage.sourceforge.net/documentation/images/

Single image:
```bash
convert <source> -define tiff:tile-geometry=256x256 -compress jpeg 'ptif:<destination>.tif'
```

Multiple images:
```bash
for I in *.jp2; do convert $I -define tiff:tile-geometry=256x256 -quality 80 -compress jpeg "ptif:${I%.*}.tif" ; done
```
or
```bash
ls *.jp2 | parallel -j 4 'I={} ; convert $I -define tiff:tile-geometry=256x256 -quality 80 -compress jpeg "ptif:${I%.*}.tif"'
```

Verify DeepZoom image server + pyramid TIFF with something like
http://localhost/iipsrv/iipsrv.fcgi?DeepZoom=/tmp/0010a611-4af0-405c-a60d-977314627740.tif_files/8/0_0.jpg

Common problems: File permissions, too small image (try /9/0_0.jpg instead of /8/0_0.jpg).


### Setup a local sample
In the project folder, create a sub-folder `nrtmosaic` and add a file `sources.dat`.
The sources.dat should contain a list of the available Pyramid TIFFs i size <= 256x384, such as
```
http://localhost/iipsrv/iipsrv.fcgi?DeepZoom=/home/te/projects/nrtmosaic/sample/0010a611-4af0-405c-a60d-977314627740.tif_files/8/0_0.jpg
http://localhost/iipsrv/iipsrv.fcgi?DeepZoom=/home/te/projects/nrtmosaic/sample/0013ca9d-26ae-49c4-95d4-6ba0fa4ef407.tif_files/8/0_0.jpg
http://localhost/iipsrv/iipsrv.fcgi?DeepZoom=/home/te/projects/nrtmosaic/sample/0019f8ff-fd0f-42d8-a449-6444c5b40777.tif_files/8/0_0.jpg
http://localhost/iipsrv/iipsrv.fcgi?DeepZoom=/home/te/projects/nrtmosaic/sample/0020b25c-ea64-4e31-83cb-4c5031b3bd79.tif_files/8/0_0.jpg
...
```


#### Download and install tomcat
```bash
./setupTomcat.sh
```

#### Start tomcat and deploy nrtmosaic
Really ugly hack here, with the link to the configuration.
```bash
tomcat/bin/startup.sh
./deployLocalTomcat.sh
sleep 5
pushd tomcat/webapps/nrtmosaic/WEB-INF/
ln -s ../../../../nrtmosaic .
popd
tomcat/bin/shutdown.sh ; tomcat/bin/startup.sh
```

Test image:
http://localhost:8080/nrtmosaic/services/image?source=foo&x=1&y=1&z=1

Sample GUI:
http://localhost:8080/gui/

If the gui is not served from the tomcat, either CORS must be enabled or the tomcat must be mounted
under the front end server. For apache httpd that is done with
mod_jk: https://tomcat.apache.org/connectors-doc/webserver_howto/apache.html
http://thetechnocratnotebook.blogspot.dk/2012/05/installing-tomcat-7-and-apache2-with.html

mod_proxy_ajp seems simplest to use:
http://httpd.apache.org/docs/2.2/mod/mod_proxy_ajp.html
http://stackoverflow.com/questions/2001886/how-to-enable-mod-proxy-ajp-for-apache-2-2-14
   sudo a2enmod proxy_ajp

