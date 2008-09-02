//
// MinimalTiffReader.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.IOException;
import java.util.*;
import loci.formats.*;

/**
 * MinimalTiffReader is the superclass for file format readers compatible with
 * or derived from the TIFF 6.0 file format.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/MinimalTiffReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/MinimalTiffReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class MinimalTiffReader extends FormatReader {

  // -- Fields --

  /** List of IFDs for the current TIFF. */
  protected Hashtable[] ifds;

  // -- Constructors --

  /** Constructs a new MinimalTiffReader. */
  public MinimalTiffReader() {
    super("Minimal TIFF", new String[] {"tif", "tiff"});
  }

  /** Constructs a new MinimalTiffReader. */
  public MinimalTiffReader(String name, String suffix) { super(name, suffix); }

  /** Constructs a new MinimalTiffReader. */
  public MinimalTiffReader(String name, String[] suffixes) {
    super(name, suffixes);
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessStream) */
  public boolean isThisType(RandomAccessStream stream) throws IOException {
    return TiffTools.isValidHeader(stream);
  }

  /* @see loci.formats.IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);
    int[] bits = TiffTools.getBitsPerSample(ifds[0]);
    if (bits[0] <= 8) {
      int[] colorMap =
        TiffTools.getIFDIntArray(ifds[0], TiffTools.COLOR_MAP, false);
      if (colorMap == null) return null;

      byte[][] table = new byte[3][colorMap.length / 3];
      int next = 0;
      for (int j=0; j<table.length; j++) {
        for (int i=0; i<table[0].length; i++) {
          table[j][i] = (byte) ((colorMap[next++] >> 8) & 0xff);
        }
      }

      return table;
    }
    return null;
  }

  /* @see loci.formats.IFormatReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);
    int[] bits = TiffTools.getBitsPerSample(ifds[0]);
    if (bits[0] <= 16 && bits[0] > 8) {
      int[] colorMap =
        TiffTools.getIFDIntArray(ifds[0], TiffTools.COLOR_MAP, false);
      if (colorMap == null || colorMap.length < 65536 * 3) return null;
      short[][] table = new short[3][colorMap.length / 3];
      int next = 0;
      for (int i=0; i<table.length; i++) {
        for (int j=0; j<table[0].length; j++) {
          if (isLittleEndian()) {
            table[i][j] = (short) (colorMap[next++] & 0xffff);
          }
          else {
            int n = colorMap[next++];
            table[i][j] =
              (short) (((n & 0xff0000) >> 8) | ((n & 0xff000000) >> 24));
          }
        }
      }
      return table;
    }
    return null;
  }

  /**
   * @see loci.formats.FormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int width,
    int height) throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);
    FormatTools.checkBufferSize(this, buf.length, width, height);

    TiffTools.getSamples(ifds[no], in, buf, x, y, width, height);
    return buf;
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    ifds = null;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("BaseTiffReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessStream(id);
    boolean little = in.readShort() == 0x4949;
    in.order(little);

    status("Reading IFDs");

    ifds = TiffTools.getIFDs(in);
    if (ifds == null) throw new FormatException("No IFDs found");

    status("Populating metadata");

    core[0].imageCount = ifds.length;

    int photo = TiffTools.getPhotometricInterpretation(ifds[0]);
    int samples = TiffTools.getSamplesPerPixel(ifds[0]);
    core[0].rgb = samples > 1 || photo == TiffTools.RGB;
    core[0].interleaved = false;
    core[0].littleEndian = TiffTools.isLittleEndian(ifds[0]);

    core[0].sizeX = (int) TiffTools.getImageWidth(ifds[0]);
    core[0].sizeY = (int) TiffTools.getImageLength(ifds[0]);
    core[0].sizeZ = 1;
    core[0].sizeC = isRGB() ? samples : 1;
    core[0].sizeT = ifds.length;
    core[0].pixelType = getPixelType(ifds[0]);
    core[0].metadataComplete = true;
    core[0].indexed = photo == TiffTools.RGB_PALETTE &&
      (get8BitLookupTable() != null || get16BitLookupTable() != null);
    if (isIndexed()) {
      core[0].sizeC = 1;
      core[0].rgb = false;
    }
    if (getSizeC() == 1 && !isIndexed()) core[0].rgb = false;
    core[0].falseColor = false;
    core[0].dimensionOrder = "XYCZT";
  }

  // -- Helper methods --

  protected int getPixelType(Hashtable ifd) throws FormatException {
    int bps = TiffTools.getBitsPerSample(ifd)[0];
    int bitFormat = TiffTools.getIFDIntValue(ifd, TiffTools.SAMPLE_FORMAT);

    while (bps % 8 != 0) bps++;
    if (bps == 24) bps = 32;

    if (bitFormat == 3) return FormatTools.FLOAT;
    else if (bitFormat == 2) {
      switch (bps) {
        case 16:
          return FormatTools.INT16;
        case 32:
          return FormatTools.INT32;
        default:
          return FormatTools.INT8;
      }
    }
    else {
      switch (bps) {
        case 16:
          return FormatTools.UINT16;
        case 32:
          return FormatTools.UINT32;
        default:
          return FormatTools.UINT8;
      }
    }
  }

}