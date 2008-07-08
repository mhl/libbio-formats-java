//
// MINCReader.java
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

import java.io.*;
import java.util.*;
import loci.formats.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * MINCReader is the file format reader for MINC MRI files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/MINCReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/MINCReader.java">SVN</a></dd></dl>
 */
public class MINCReader extends FormatReader {

  // -- Fields --

  private NetcdfTools netcdf;
  private byte[][][] pixelData;

  // -- Constructor --

  /** Constructs a new MINC reader. */
  public MINCReader() {
    super("MINC MRI", "mnc");
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    return false;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);
    FormatTools.checkBufferSize(this, buf.length, w, h);

    for (int row=y; row<y+h; row++) {
      System.arraycopy(pixelData[no][row], x, buf, (h - row + y - 1) * w, w);
    }

    return buf;
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    if (netcdf != null) netcdf.close();
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("MINCReader.initFile(" + id + ")");
    super.initFile(id);

    netcdf = new NetcdfTools(id);

    Vector variableList = netcdf.getVariableList();

    for (int i=0; i<variableList.size(); i++) {
      String variable = (String) variableList.get(i);
      Hashtable attributes = netcdf.getVariableAttributes(variable);
      String[] keys = (String[]) attributes.keySet().toArray(new String[0]);
      Arrays.sort(keys);

      for (int j=0; j<keys.length; j++) {
        if (attributes.get(keys[j]) instanceof Object[]) {
          StringBuffer sb = new StringBuffer();
          Object[] o = (Object[]) attributes.get(keys[j]);
          for (int q=0; q<o.length; q++) {
            sb.append(o[q].toString());
          }
          addMeta(variable + " " + keys[j], sb.toString());
        }
      }
    }

    pixelData = (byte[][][]) netcdf.getVariableValue("/image");

    core.sizeX[0] = netcdf.getDimension("/zspace");
    core.sizeY[0] = netcdf.getDimension("/yspace");
    core.sizeZ[0] = netcdf.getDimension("/xspace");

    core.sizeT[0] = 1;
    core.sizeC[0] = 1;
    core.imageCount[0] = core.sizeZ[0];
    core.rgb[0] = false;
    core.indexed[0] = false;
    core.currentOrder[0] = "XYZCT";
    core.pixelType[0] = FormatTools.UINT8;

    addMeta("Comment", netcdf.getAttributeValue("/history"));

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    MetadataTools.setDefaultCreationDate(store, id, 0);
    store.setImageName("", 0);
    store.setImageDescription(netcdf.getAttributeValue("/history"), 0);
    MetadataTools.populatePixels(store, this);
  }

}