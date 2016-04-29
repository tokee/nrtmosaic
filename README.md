# nrtmosaic

Fast mosaic creation from pre-processed source images

## Background

The raison d'être for nrtmosaic it to provide tiles for a newspaper page made up by other newspaper pages, used at a project at statsbiblioteket.dk. While nrtmosaic is sought to be generally usable, the focus is internal use.

As newspaper pages has an aspect ratio fairly near to 2:3. To create a mosaic from an image, the square pixels from the source must be mapped to non-square pages. This can be done by mapping 1x3-pixel from the source to 1x2 pages. To do this, we need fast access to average color (greyscale intensity really) for the top, middle and bottom third of all pages.

## Numbers & math

We have about **2.2 million** freely available newspaper pages. They are each 10MP+, for a raw pixel count around **20 terapixels**.

Image servers likes tiles of 256x256 pixels does not like being hammered with hundreds of requests at the same time. For a full-screen display os 2048x1600 pixels, about 50 tiles are needed, so as long as we don't ask for smaller representations than 256x256 pixels from the image server, everything should work out ok.

To handle the 2:3 aspect ratio, we to cache need 6 tiles (2x3) at all levels below 256x256 pixels, meaning 128x128, 64x64,... 2x2, 1x1. That means 6*128x128 + 6*64x64 + ... 6*2x2 + 6*1x1 pixels for each image, which is about 130 kilopixel). If we store all tiles uncompressed on the file system, that takes up 2.2M * 130KB ~= **300GB**.

For usable performance, we can cache all tiles from 16x16 pixels and below. That takes up 2.2M * 2KB ~= **4.5GB** of memory.

## Disk format

Each image is uniquely identified by a 128 bit UUID. To avoid a file count explosion we store the tiles in 256 files of approximately 1GB. The file is determined by the first byte in the image UUID. The format of each file is

```
[ID1]6*[1x1]
...
[IDn]6*[1x1]
[ID2]6*[2x2]
...
[IDn]6*[2x2]
...
[ID1]6*[64x64]
...
[IDn]6*[64x64]
[ID1]6*[128x128]
...
[IDn]6*[128x128]
```
Upon startup the mosaic server scans all tile collections, memory-caching tiles 1x1 to 16x16 and file-offsets for the rest of the tiles.

## How to generate the tiles

For each image:

1 Request a version as close to 256x384 pixels as possible.
2 Generate the 6*128x128, 6*64x64 etc. tiles
3 Store the raw image data in a single file named after the image-ID in hex, stored in a folder named from the first 4 hex-digit in the ID. `34fe/...`
 Content is [IDn][witdh][height]6*[128x128]6*[64x64]...6*[1x1][average]

