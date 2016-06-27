# nrtmosaic

Fast mosaic creation from pre-processed source images

## Background

The raison d'être for nrtmosaic is to provide tiles for a newspaper page, visually made up by other newspaper pages.
It is used at a project at statsbiblioteket.dk.

While nrtmosaic is sought to be generally usable, the focus is internal use.

As newspaper pages has an aspect ratio fairly near to 2:3. To create a mosaic from an image, the square pixels from 
the source must be mapped to non-square pages. This can be done by mapping 1x3-pixel from the source to 1x2 pages.
To do this, we need fast access to average color (greyscale intensity really) for the top, middle and bottom third
of all pages.

## Numbers & math

We have about **2.2 million** freely available newspaper pages. They are each 10MP+, for a raw pixel count around
**20 terapixels**.

Image servers likes tiles of 256x256 pixels does not like being hammered with hundreds of requests at the same time.
For a full-screen display os 2048x1600 pixels, about 50 tiles are needed, so as long as we don't ask for smaller 
representations than 256x256 pixels from the image server, everything should work out ok.

To handle the 2:3 aspect ratio, we to cache need 6 tiles (2x3) at all levels below 256x256 pixels, meaning 
`128x128, 64x64,... 2x2, 1x1` . That means `6*128x128 + 6*64x64 + ... 6*2x2 + 6*1x1 pixels` for each image, which 
is about 130 kilopixel). If we store all tiles uncompressed on the file system, that takes up 
`2.2M * 130KB ~= **300GB**`.

For usable performance, we can cache all tiles from 16x16 pixels and below. That takes up
 `2.2M * 2KB ~= **4.5GB**` of memory.

## Mapping

The single pixels from the source image must be mapped to 2:3 aspect ratio destination images. This is done by mapping 1x3 pixels in source to 1x2 images in destination.

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

## Image server notes

Very specific for Statsbiblioteket!


### Get some image UUIDs

`curl "http://prod-search03:56708/aviser/sbsolr/collection1/select?fq=recordBase%3Adoms_aviser&fq=py%3A%5B*+TO+1915%5D&fl=pageUUID&wt=csv&indent=true&q=geder"`

`curl "http://mars:56708/aviser/sbsolr/collection1/select?fq=recordBase%3Adoms_aviser&fq=py%3A[*+TO+1845]&fl=pageUUID&wt=csv&indent=true&q=geder"`
pageUUID
doms_aviser_page:uuid:5926aa0a-f1a8-4899-9ab7-108a1b4ed74e
doms_aviser_page:uuid:9c2f60b0-c314-4a78-9abe-bbf51df69788
doms_aviser_page:uuid:a3f7d94b-79be-4346-956c-8501f35137ff
doms_aviser_page:uuid:09308cbe-de5e-4d5b-a888-45a1ac7d1071
doms_aviser_page:uuid:d03304d0-1346-4089-8e8f-ab6ada63a9c6
doms_aviser_page:uuid:40cd0674-5ba9-4a14-bf8d-43c3664d427a
doms_aviser_page:uuid:a294cd75-ebb6-41f2-a1b1-c62f8d18fbaf
doms_aviser_page:uuid:d91c0309-b2dc-4d17-86b6-31a36ce8fbc1
doms_aviser_page:uuid:c7e2d32d-4a0d-48ec-8796-7ee975397c43
doms_aviser_page:uuid:e791eb1f-8269-4a3e-a0fb-83889abaa812

http://achernar/iipsrv/?GAM=2.0&CNT=1.1&DeepZoom=/avis-show/symlinks/5/9/2/6/5926aa0a-f1a8-4899-9ab7-108a1b4ed74e.jp2.dzi/10/2_1.jpg
http://achernar/iipsrv/?IIIF=/avis-show/symlinks/5/9/2/6/5926aa0a-f1a8-4899-9ab7-108a1b4ed74e.jp2/276,1860,1852,2000/1852,2000/0/default.jpg

Cannot resolve?


`curl "http://rhea:56708/aviser/sbsolr/collection1/select?fq=recordBase%3Adoms_aviser&fq=py%3A\[*+TO+1915\]&fl=pageUUID&wt=csv&indent=true&q=geder"`
or better
`curl "http://rhea:56708/aviser/sbsolr/collection1/select?fq=recordBase%3Adoms_aviser&q=py%3A\[*+TO+1915\]&fl=pageUUID&indent=true&rows=20&group=true&group.field=editionUUID" | grep pageUUID | sed 's/.*\(uuid[^<]*\).*/\1/'`
sed 's/uuid://' 
cat pages | sed 's%^\(.\)\(.\)\(.\)\(.\)\(.*\)%\1/\2/\3/\4/\1\2\3\4\5%' > paths
for P in `cat paths`; do ID=`echo $P | grep -o '[^/]*$'` ; curl "http://achernar/iipsrv/?GAM=2.0&CNT=1.1&DeepZoom=/avis-show/symlinks/${P}.jp2_files/8/0_0" > sample_${ID}.jpg ; done

pageUUID
doms_aviser_page:uuid:4921184c-6e5d-4db5-b442-761690b32347
doms_aviser_page:uuid:a7c6c496-a3d0-4b48-9072-8eae4bf3e18c
doms_aviser_page:uuid:07251360-685f-4fce-bba3-fb4ddc72f275
doms_aviser_page:uuid:21526708-8583-41b1-b90c-071251505708
doms_aviser_page:uuid:b47e82e7-1d77-4780-ad77-c522bdb9df92
doms_aviser_page:uuid:30652c95-579f-401b-b40d-65a2ac7f344e
doms_aviser_page:uuid:0bb435ae-60ea-461a-a534-fa1c07632753
doms_aviser_page:uuid:80b48092-938c-4ae8-9f86-20f2e03f0f7b
doms_aviser_page:uuid:fb486afa-5d5b-4d50-80e1-93bded5c40d5
doms_aviser_page:uuid:c67642da-2246-4e5a-93f9-1e810d1a0fc7

http://achernar/iipsrv/?IIIF=/avis-show/symlinks/4/9/2/1/4921184c-6e5d-4db5-b442-761690b32347.jp2/276,160,152,200/152,200/0/default.jpg
http://achernar/iipsrv/?GAM=2.0&CNT=1.1&DeepZoom=/avis-show/symlinks/4/9/2/1/4921184c-6e5d-4db5-b442-761690b32347.jp2_files/9/1_1.jpg

Works fine!


Sample tile URL
http://www2.statsbiblioteket.dk/newspaper-stream/33fbb9c1-ed72-490c-ab53-c800cc2fbe99?GAM=2.0&CNT=1.1&DeepZoom=/avis-show/symlinks/5/3/8/6/5386aa42-92bd-4ffc-b4f4-ad69b0610d00.jp2.dzi/10/2_1.jpg

Sample Image server tile:
http://achernar/iipsrv/?IIIF=/avis-show/symlinks/f/d/f/5/fdf5d350-360a-49db-ada5-2a4b5d51672b.jp2/276,1860,1852,2000/1852,2000/0/default.jpg


### Deploy
Tip: NRT-Mosaic is hungry for file handles. 1024 open files (check with 'ulimit -n') is probably not enough

curl "http://ftp.download-by.net/apache/tomcat/tomcat-8/v8.0.35/bin/apache-tomcat-8.0.35.tar.gz" > apache-tomcat-8.0.35.tar.gz
tar xzovf apache-tomcat-8.0.35.tar.gz
ln -s apache-tomcat-8.0.35 tomcat
echo "export JAVA_OPTS=\"-Xmx1000m $JAVA_OPTS\"" > tomcat/bin/setenv.sh
tomcat/bin/startup.sh
./deployLocalTomcat.sh
cp -r gui/ tomcat/webapps/

Test image:
http://localhost:8080/nrtmosaic/services/image?source=foo&x=1&y=1&z=1

Sample GUI:
http://localhost:8080/gui/

### TIFF
http://iipimage.sourceforge.net/documentation/images/
convert <source> -define tiff:tile-geometry=256x256 -compress jpeg 'ptif:<destination>.tif'
for I in *.jp2; do convert $I -define tiff:tile-geometry=256x256 -quality 80 -compress jpeg "ptif:${I%.*}.tif" ; done
