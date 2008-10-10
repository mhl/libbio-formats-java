//
// FV1000Reader.java
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
import java.text.*;
import java.util.*;
import loci.formats.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * FV1000Reader is the file format reader for Fluoview FV 1000 OIB and
 * Fluoview FV 1000 OIF files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/FV1000Reader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/FV1000Reader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class FV1000Reader extends FormatReader {

  // -- Constants --

  public static final String[] OIB_SUFFIX = {"oib"};
  public static final String[] OIF_SUFFIX = {"oif"};
  public static final String[] FV1000_SUFFIXES = {"oib", "oif"};

  private static final int NUM_DIMENSIONS = 9;

  // -- Fields --

  /** Names of every TIFF file to open. */
  private Vector tiffs;

  /** Name of thumbnail file. */
  private String thumbId;

  /** Helper reader for thumbnail. */
  private BMPReader thumbReader;

  /** Used file list. */
  private Vector usedFiles;

  /** Flag indicating this is an OIB dataset. */
  private boolean isOIB;

  /** File mappings for OIB file. */
  private Hashtable oibMapping;

  private String[] code, size, pixelSize;
  private int imageDepth;
  private Vector previewNames;

  private String pixelSizeX, pixelSizeY;
  private Vector channelNames, emWaves, exWaves;
  private String gain, offset, voltage, pinholeSize;
  private String magnification, lensNA, objectiveName, workingDistance;
  private String creationDate;

  private POITools poi;

  private short[][][] lut;
  private int lastChannel;
  private int[] channelIndexes;

  private Vector pinholeSizes;
  private Vector dyeNames;
  private Vector wavelengths;
  private Vector illuminations;

  // -- Constructor --

  /** Constructs a new FV1000 reader. */
  public FV1000Reader() {
    super("Olympus FV1000", new String[] {"oib", "oif", "pty", "lut"});
    blockCheckLen = 1024;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    if (checkSuffix(name, FV1000_SUFFIXES)) return true;

    if (!open) return false; // not allowed to touch the file system

    Location parent = new Location(name).getAbsoluteFile().getParentFile();
    String path = parent.getPath();
    path = path.substring(path.lastIndexOf(File.separator) + 1);
    if (path.indexOf(".") != -1) {
      path = path.substring(0, path.lastIndexOf("."));
    }

    Location oif = new Location(parent.getParentFile(), path);
    return oif.exists() && !oif.isDirectory();
  }

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessStream) */
  public boolean isThisType(RandomAccessStream stream) throws IOException {
    if (!FormatTools.validStream(stream, blockCheckLen, false)) return false;
    String s = DataTools.stripString(stream.readString(blockCheckLen));
    return s.indexOf("FileInformation") != -1 ||
      s.indexOf("Acquisition Parameters") != -1;
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    String name = id.toLowerCase();
    if (name.endsWith(".oib") || name.endsWith(".oif")) {
      return FormatTools.CANNOT_GROUP;
    }
    return FormatTools.MUST_GROUP;
  }

  /* @see loci.formats.IFormatReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() {
    FormatTools.assertId(currentId, true, 1);
    return lut == null ? null : lut[lastChannel];
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);

    int[] coords = getZCTCoords(no);
    if (coords[1] < channelIndexes.length) {
      coords[1] = channelIndexes[coords[1]];
      lastChannel = coords[1];
    }

    int planeNum = FormatTools.getIndex(getDimensionOrder(), getSizeZ(),
      getEffectiveSizeC(), getSizeT(), getImageCount(), coords[0],
      coords[1], coords[2]);

    String file =
      (String) (series == 0 ? tiffs.get(planeNum) : previewNames.get(planeNum));
    RandomAccessStream plane = getFile(file);
    TiffTools.getSamples(TiffTools.getFirstIFD(plane), plane, buf, x, y, w, h);
    plane.close();
    plane = null;
    return buf;
  }

  /* @see loci.formats.IFormatReader#openThumbImage(int) */
  /*
  public BufferedImage openThumbImage(int no)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);

    RandomAccessStream thumb = getFile(thumbId);
    byte[] b = new byte[(int) thumb.length()];
    thumb.read(b);
    thumb.close();
    Location.mapFile("thumbnail.bmp", new RABytes(b));
    thumbReader.setId("thumbnail.bmp");
    return thumbReader.openImage(0);
  }
  */

  /* @see loci.formats.IFormatReader#getUsedFiles() */
  public String[] getUsedFiles() {
    FormatTools.assertId(currentId, true, 1);
    if (usedFiles == null) return new String[] {currentId};
    return (String[]) usedFiles.toArray(new String[0]);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    if (in != null) in.close();
    if (thumbReader != null) thumbReader.close(fileOnly);

    if (!fileOnly) {
      super.close();
      tiffs = usedFiles = null;
      thumbReader = null;
      thumbId = null;
      previewNames = null;
      if (poi != null) poi.close();
      poi = null;
      lastChannel = 0;
      pinholeSizes = null;
      dyeNames = null;
      wavelengths = null;
      illuminations = null;
    }
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    close(false);
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("FV1000Reader.initFile(" + id + ")");

    super.initFile(id);

    isOIB = checkSuffix(id, OIB_SUFFIX);

    in = new RandomAccessStream(id);
    if (isOIB) poi = new POITools(Location.getMappedId(id));

    // mappedOIF is used to distinguish between datasets that are being read
    // directly (e.g. using ImageJ or showinf), and datasets that are being
    // imported through omebf. In the latter case, the necessary directory
    // structure is not preserved (only relative file names are stored in
    // OMEIS), so we will need to use slightly different logic to build the
    // list of associated files.
    boolean mappedOIF = !isOIB && !new File(id).getAbsoluteFile().exists();

    channelNames = new Vector();
    emWaves = new Vector();
    exWaves = new Vector();
    dyeNames = new Vector();
    wavelengths = new Vector();
    illuminations = new Vector();

    String line = null, key = null, value = null, oifName = null;

    if (isOIB) {
      String infoFile = null;
      Vector list = poi.getDocumentList();
      for (int i=0; i<list.size(); i++) {
        String name = (String) list.get(i);
        if (name.endsWith("OibInfo.txt")) {
          infoFile = name;
          break;
        }
      }
      if (infoFile == null) {
        throw new FormatException("OibInfo.txt not found in " + id);
      }
      RandomAccessStream ras = poi.getDocumentStream(infoFile);

      oibMapping = new Hashtable();

      // set up file name mappings

      String s = DataTools.stripString(ras.readString((int) ras.length()));
      ras.close();
      StringTokenizer lines = new StringTokenizer(s, "\n");
      String directoryKey = null, directoryValue = null;
      while (lines.hasMoreTokens()) {
        line = lines.nextToken().trim();
        if (line.indexOf("=") != -1) {
          key = line.substring(0, line.indexOf("="));
          value = line.substring(line.indexOf("=") + 1);

          if (key.startsWith("Stream")) {
            if (directoryKey != null && directoryValue != null) {
              value = value.replaceAll(directoryKey, directoryValue);
            }
            if (value.indexOf("GST") != -1) {
              String first = value.substring(0, value.indexOf("GST"));
              String last = value.substring(value.lastIndexOf("=") + 1);
              value = first + last;
            }
            if (checkSuffix(value, OIF_SUFFIX)) oifName = value;
            if (directoryKey != null) {
              oibMapping.put(value, "Root Entry" + File.separator +
                directoryKey + File.separator + key);
            }
            else oibMapping.put(value, "Root Entry" + File.separator + key);
          }
          else if (key.startsWith("Storage")) {
            if (value.indexOf("GST") != -1) {
              String first = value.substring(0, value.indexOf("GST"));
              String last = value.substring(value.lastIndexOf("=") + 1);
              value = first + last;
            }
            directoryKey = key;
            directoryValue = value;
          }
        }
      }
      s = null;
    }
    else {
      // make sure we have the OIF file, not a TIFF
      if (!checkSuffix(id, OIF_SUFFIX)) {
        Location current = new Location(id).getAbsoluteFile();
        String parent = current.getParent();
        Location tmp = new Location(parent);
        parent = tmp.getParent();

        id = current.getPath();
        String oifFile = id.substring(id.lastIndexOf(File.separator));
        oifFile =
          parent + oifFile.substring(0, oifFile.lastIndexOf("_")) + ".oif";

        tmp = new Location(oifFile);
        if (!tmp.exists()) {
          oifFile = oifFile.substring(0, oifFile.lastIndexOf(".")) + ".OIF";
          tmp = new Location(oifFile);
          if (!tmp.exists()) {
            // check in parent directory
            if (parent.endsWith(File.separator)) {
              parent = parent.substring(0, parent.length() - 1);
            }
            String dir = parent.substring(parent.lastIndexOf(File.separator));
            tmp = new Location(parent);
            parent = tmp.getParent();
            oifFile = parent + dir.substring(0, dir.lastIndexOf("."));
            if (!new Location(oifFile).exists()) {
              throw new FormatException("OIF file not found");
            }
          }
          currentId = oifFile;
        }
        else currentId = oifFile;
        super.initFile(currentId);
        in = new RandomAccessStream(currentId);
        oifName = currentId;
      }
      else oifName = currentId;
    }

    String f = new Location(oifName).getAbsoluteFile().getAbsolutePath();
    String path = (isOIB || !f.endsWith(oifName) || mappedOIF) ? "" :
      f.substring(0, f.lastIndexOf(File.separator) + 1);

    RandomAccessStream oif = null;
    try {
      oif = getFile(oifName);
    }
    catch (IOException e) {
      oif = getFile(oifName.replaceAll(".oif", ".OIF"));
    }

    // parse key/value pairs from the OIF file

    String s = oif.readString((int) oif.length());
    oif.close();

    code = new String[NUM_DIMENSIONS];
    size = new String[NUM_DIMENSIONS];
    pixelSize = new String[NUM_DIMENSIONS];

    StringTokenizer st = new StringTokenizer(s, "\r\n");

    previewNames = new Vector();
    boolean laserEnabled = true;

    Vector channels = new Vector();
    Vector lutNames = new Vector();
    Hashtable filenames = new Hashtable();
    String prefix = "";
    while (st.hasMoreTokens()) {
      line = DataTools.stripString(st.nextToken().trim());
      if (!line.startsWith("[") && (line.indexOf("=") > 0)) {
        key = line.substring(0, line.indexOf("=")).trim();
        value = line.substring(line.indexOf("=") + 1).trim();
        if (value.startsWith("\"")) {
          value = value.substring(1, value.length() - 1);
        }

        if (key.startsWith("IniFileName") && key.indexOf("Thumb") == -1 &&
          !isPreviewName(value))
        {
          value = value.replaceAll("/", File.separator);
          value = value.replace('\\', File.separatorChar);
          while (value.indexOf("GST") != -1) {
            String first = value.substring(0, value.indexOf("GST"));
            int ndx = value.indexOf(File.separator) < value.indexOf("GST") ?
              value.length() : value.indexOf(File.separator);
            String last = value.substring(value.lastIndexOf("=", ndx) + 1);
            value = first + last;
          }
          if (mappedOIF) {
            value = value.substring(value.lastIndexOf(File.separator) + 1);
          }
          filenames.put(new Integer(key.substring(11)), value.trim());
        }
        else if (key.indexOf("Thumb") != -1) {
          value = value.replaceAll("/", File.separator);
          value = value.replace('\\', File.separatorChar);
          while (value.indexOf("GST") != -1) {
            String first = value.substring(0, value.indexOf("GST"));
            int ndx = value.indexOf(File.separator) < value.indexOf("GST") ?
              value.length() : value.indexOf(File.separator);
            String last = value.substring(value.lastIndexOf("=", ndx) + 1);
            value = first + last;
          }
          if (mappedOIF) {
            value = value.substring(value.lastIndexOf(File.separator) + 1);
          }
          if (thumbId == null) thumbId = value.trim();
        }
        else if (key.startsWith("LutFileName")) {
          if (!isPreviewName(value)) {
            value = value.replaceAll("/", File.separator);
            value = value.replace('\\', File.separatorChar);
            while (value.indexOf("GST") != -1) {
              String first = value.substring(0, value.indexOf("GST"));
              int ndx = value.indexOf(File.separator) < value.indexOf("GST") ?
                value.length() : value.indexOf(File.separator);
              String last = value.substring(value.lastIndexOf("=", ndx) + 1);
              value = first + last;
            }
          }
          if (mappedOIF) {
            value = value.substring(value.lastIndexOf(File.separator) + 1);
          }
          lutNames.add(path + value);
        }
        else if (isPreviewName(value)) {
          value = value.replaceAll("/", File.separator);
          value = value.replace('\\', File.separatorChar);
          while (value.indexOf("GST") != -1) {
            String first = value.substring(0, value.indexOf("GST"));
            int ndx = value.indexOf(File.separator) < value.indexOf("GST") ?
              value.length() : value.indexOf(File.separator);
            String last = value.substring(value.lastIndexOf("=", ndx) + 1);
            value = first + last;
          }
          if (mappedOIF) {
            value = value.substring(value.lastIndexOf(File.separator) + 1);
          }
          previewNames.add(path + value.trim());
        }
        addMeta(prefix + key, value);

        if (prefix.startsWith("[Axis ") &&
          prefix.endsWith("Parameters Common] - "))
        {
          int ndx =
            Integer.parseInt(prefix.substring(6, prefix.indexOf("P")).trim());
          if (key.equals("AxisCode")) code[ndx] = value;
          else if (key.equals("MaxSize")) size[ndx] = value;
	        else if (key.equals("Interval")) pixelSize[ndx] = value;
	      }
        else if ((prefix + key).equals(
          "[Reference Image Parameter] - ImageDepth"))
        {
          imageDepth = Integer.parseInt(value);
        }
        else if ((prefix + key).equals(
          "[Reference Image Parameter] - WidthConvertValue"))
        {
          pixelSizeX = value;
        }
        else if ((prefix + key).equals(
          "[Reference Image Parameter] - HeightConvertValue"))
        {
          pixelSizeY = value;
        }
        else if (prefix.indexOf("[Channel ") != -1 &&
          prefix.indexOf("Parameters] - ") != -1)
        {
          if (key.equals("CH Name")) channelNames.add(value);
          else if (key.equals("DyeName")) dyeNames.add(value);
          else if (key.equals("EmissionWavelength")) {
            emWaves.add(new Integer(value));
          }
          else if (key.equals("ExcitationWavelength")) {
            exWaves.add(new Integer(value));
          }
          else if (key.equals("SequentialNumber")) channels.add(value);
          else if (key.equals("LightType")) {
            String illumination = value.toLowerCase();
            if (illumination.indexOf("fluorescence") != -1) {
              illumination = "Epifluorescence";
            }
            else if (illumination.indexOf("transmitted") != -1) {
              illumination = "Transmitted";
            }
            else illumination = null;
            illuminations.add(illumination);
          }
        }
        else if (prefix.startsWith("[Laser ") && key.equals("Laser Enable")) {
          laserEnabled = value.equals("1");
        }
        else if (prefix.startsWith("[Laser ") && key.equals("LaserWavelength"))
        {
          if (laserEnabled) wavelengths.add(new Integer(value));
        }
        else if (key.equals("ImageCaputreDate") ||
          key.equals("ImageCaptureDate"))
        {
          creationDate = value;
        }
      }
      else if (line.length() > 0) {
        if (line.indexOf("[") == 2) {
          line = line.substring(2, line.length());
        }
        prefix = line + " - ";
      }
    }

    channelIndexes = new int[channels.size()];
    for (int i=0; i<channelIndexes.length; i++) {
      channelIndexes[i] = Integer.parseInt((String) channels.get(i));
    }

    // check that no two indexes are equal
    for (int i=0; i<channelIndexes.length; i++) {
      for (int q=0; q<channelIndexes.length; q++) {
        if (i != q && channelIndexes[i] == channelIndexes[q]) {
          for (int n=0; n<channelIndexes.length; n++) {
            if (channelIndexes[n] > channelIndexes[q]) {
              channelIndexes[q] = channelIndexes[n];
            }
          }
          channelIndexes[q]++;
        }
      }
    }

    // normalize channel indexes to [0, sizeC-1]

    int nextIndex = 0;
    for (int i=0; i<channelIndexes.length; i++) {
      int min = Integer.MAX_VALUE;
      int minIndex = -1;
      for (int q=0; q<channelIndexes.length; q++) {
        if (channelIndexes[q] < min && channelIndexes[q] >= nextIndex) {
          min = channelIndexes[q];
          minIndex = q;
        }
      }
      channelIndexes[minIndex] = nextIndex++;
    }

    int reference = ((String) filenames.get(new Integer(0))).length();
    int numFiles = filenames.size();
    for (int i=0; i<numFiles; i++) {
      Integer ii = new Integer(i);
      value = (String) filenames.get(ii);
      if (value != null) {
        if (value.length() > reference) filenames.remove(ii);
      }
    }

    status("Initializing helper readers");

    // populate core metadata for preview series

    if (previewNames.size() > 0) {
      Vector v = new Vector();
      for (int i=0; i<previewNames.size(); i++) {
        String ss = (String) previewNames.get(i);
        ss = ss.replaceAll("pty", "tif");
        if (ss.endsWith(".tif")) v.add(ss);
      }
      previewNames = v;
      if (previewNames.size() > 0) {
        String previewName = (String) previewNames.get(0);
        core = new CoreMetadata[2];
        core[0] = new CoreMetadata();
        core[1] = new CoreMetadata();
        Hashtable[] ifds = TiffTools.getIFDs(getFile(previewName));
        core[1].imageCount = ifds.length * previewNames.size();
        core[1].sizeX = (int) TiffTools.getImageWidth(ifds[0]);
        core[1].sizeY = (int) TiffTools.getImageLength(ifds[0]);
        core[1].sizeZ = 1;
        core[1].sizeT = 1;
        core[1].sizeC = core[1].imageCount;
        core[1].rgb = false;
        int bits = TiffTools.getBitsPerSample(ifds[0])[0];
        while ((bits % 8) != 0) bits++;
        switch (bits) {
          case 8:
            core[1].pixelType = FormatTools.UINT8;
            break;
          case 16:
            core[1].pixelType = FormatTools.UINT16;
            break;
          case 32:
            core[1].pixelType = FormatTools.UINT32;
        }
        core[1].dimensionOrder = "XYCZT";
        core[1].indexed = false;
      }
    }

    core[0].imageCount = filenames.size();
    tiffs = new Vector(core[0].imageCount);

    thumbReader = new BMPReader();
    thumbId = thumbId.replaceAll("pty", "bmp");
    thumbId = sanitizeFile(thumbId, (isOIB || mappedOIF) ? "" : path);
    //if (isOIB) thumbId = thumbId.substring(1);

    status("Reading additional metadata");

    // open each INI file (.pty extension) and build list of TIFF files

    String tiffPath = null;

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    pinholeSizes = new Vector();

    for (int i=0, ii=0; ii<core[0].imageCount; i++, ii++) {
      String file = (String) filenames.get(new Integer(i));
      while (file == null) file = (String) filenames.get(new Integer(++i));
      file = sanitizeFile(file, (isOIB || mappedOIF) ? "" : path);

      if (file.indexOf(File.separator) != -1) {
        tiffPath = file.substring(0, file.lastIndexOf(File.separator));
      }
      else tiffPath = file;
      RandomAccessStream ptyReader = getFile(file);
      s = ptyReader.readString((int) ptyReader.length());
      ptyReader.close();
      st = new StringTokenizer(s, "\n");

      while (st.hasMoreTokens()) {
        line = st.nextToken().trim();
        if (!line.startsWith("[") && (line.indexOf("=") > 0)) {
          key = line.substring(0, line.indexOf("=") - 1).trim();
          value = line.substring(line.indexOf("=") + 1).trim();
          key = DataTools.stripString(key);
          value = DataTools.stripString(value);
          if (key.equals("DataName")) {
            value = value.substring(1, value.length() - 1);
            if (!isPreviewName(value)) {
              value = value.replaceAll("/", File.separator);
              value = value.replace('\\', File.separatorChar);
              while (value.indexOf("GST") != -1) {
                String first = value.substring(0, value.indexOf("GST"));
                int ndx = value.indexOf(File.separator) < value.indexOf("GST") ?
                  value.length() : value.indexOf(File.separator);
                String last = value.substring(value.lastIndexOf("=", ndx) + 1);
                value = first + last;
              }
              if (mappedOIF) tiffs.add(ii, value);
              else tiffs.add(ii, tiffPath + File.separator + value);
            }
          }
          value = value.replaceAll("\"", "");
          addMeta("Image " + ii + " : " + key, value);

          if (key.equals("AnalogPMTGain") || key.equals("CountingPMTGain")) {
            gain = value;
          }
          else if (key.equals("AnalogPMTOffset") ||
            key.equals("CountingPMTOffset"))
          {
            offset = value;
          }
          else if (key.equals("Magnification")) {
            magnification = value;
          }
          else if (key.equals("ObjectiveLens NAValue")) {
            lensNA = value;
          }
          else if (key.equals("ObjectiveLens Name")) {
            objectiveName = value;
          }
          else if (key.equals("ObjectiveLens WDValue")) {
            workingDistance = value;
          }
          else if (key.equals("PMTVoltage")) {
            voltage = value;
          }
          else if (key.equals("PinholeDiameter")) {
            pinholeSize = value;
          }
        }
      }
    }

    usedFiles = new Vector();

    if (tiffPath != null) {
      usedFiles.add(id);
      if (!isOIB) {
        Location dir = new Location(tiffPath);
        String[] list = mappedOIF ?
          (String[]) Location.getIdMap().keySet().toArray(new String[0]) :
          dir.list();
        for (int i=0; i<list.length; i++) {
          if (mappedOIF) usedFiles.add(list[i]);
          else {
            String p = new Location(tiffPath, list[i]).getAbsolutePath();
            String check = p.toLowerCase();
            if (!check.endsWith(".tif") && !check.endsWith(".pty") &&
              !check.endsWith(".roi") && !check.endsWith(".lut") &&
              !check.endsWith(".bmp"))
            {
              continue;
            }
            usedFiles.add(p);
          }
        }
      }
    }

    status("Populating metadata");

    // calculate axis sizes

    int realChannels = 0;
    for (int i=0; i<9; i++) {
      int ss = Integer.parseInt(size[i]);
      if (pixelSize[i] == null) pixelSize[i] = "1.0";
      pixelSize[i] = pixelSize[i].replaceAll("\"", "");
      Float pixel = new Float(pixelSize[i]);
      if (code[i].equals("X")) core[0].sizeX = ss;
      else if (code[i].equals("Y")) core[0].sizeY = ss;
      else if (code[i].equals("Z")) {
        core[0].sizeZ = ss;
        // Z size stored in nm
        store.setDimensionsPhysicalSizeZ(new Float(pixel.floatValue() * 0.001),
          0, 0);
      }
      else if (code[i].equals("T")) {
        core[0].sizeT = ss;
        pixel = new Float(pixel.floatValue() / 1000);
        store.setDimensionsTimeIncrement(pixel, 0, 0);
      }
      else if (ss > 0) {
        if (core[0].sizeC == 0) core[0].sizeC = ss;
        else core[0].sizeC *= ss;
        if (code[i].equals("C")) realChannels = ss;
      }
    }

    if (core[0].sizeZ == 0) core[0].sizeZ = 1;
    if (core[0].sizeC == 0) core[0].sizeC = 1;
    if (core[0].sizeT == 0) core[0].sizeT = 1;

    if (core[0].imageCount == core[0].sizeC) {
      core[0].sizeZ = 1;
      core[0].sizeT = 1;
    }

    if (core[0].sizeZ * core[0].sizeT * core[0].sizeC > core[0].imageCount) {
      int diff =
        (core[0].sizeZ * core[0].sizeC * core[0].sizeT) - core[0].imageCount;
      if (diff == previewNames.size()) {
        if (core[0].sizeT > 1 && core[0].sizeZ == 1) core[0].sizeT -= diff;
        else if (core[0].sizeZ > 1 && core[0].sizeT == 1) core[0].sizeZ -= diff;
      }
      else core[0].imageCount += diff;
    }

    core[0].dimensionOrder = "XYCZT";

    switch (imageDepth) {
      case 1:
        core[0].pixelType = FormatTools.UINT8;
        break;
      case 2:
        core[0].pixelType = FormatTools.UINT16;
        break;
      case 4:
        core[0].pixelType = FormatTools.UINT32;
        break;
      default:
        throw new RuntimeException("Unsupported pixel depth: " + imageDepth);
    }

    in.close();
    in = null;

    // set up thumbnail file mapping

    try {
      RandomAccessStream thumb = getFile(thumbId);
      byte[] b = new byte[(int) thumb.length()];
      thumb.read(b);
      thumb.close();
      Location.mapFile("thumbnail.bmp", new RABytes(b));
      thumbReader.setId("thumbnail.bmp");
      for (int i=0; i<core.length; i++) {
        core[i].thumbSizeX = thumbReader.getSizeX();
        core[i].thumbSizeY = thumbReader.getSizeY();
      }
    }
    catch (IOException e) {
      if (debug) LogTools.trace(e);
    }

    // initialize lookup table

    lut = new short[core[0].sizeC][3][65536];
    byte[] buffer = new byte[65536 * 4];
    int count = (int) Math.min(core[0].sizeC, lutNames.size());
    for (int c=0; c<count; c++) {
      try {
        RandomAccessStream stream = getFile((String) lutNames.get(c));
        stream.seek(stream.length() - 65536 * 4);
        stream.read(buffer);
        for (int q=0; q<buffer.length; q+=4) {
          lut[c][0][q / 4] = buffer[q + 1];
          lut[c][1][q / 4] = buffer[q + 2];
          lut[c][2][q / 4] = buffer[q + 3];
        }
      }
      catch (IOException e) {
        if (debug) LogTools.trace(e);
        lut = null;
        break;
      }
    }

    for (int i=0; i<core.length; i++) {
      core[i].rgb = false;
      core[i].littleEndian = true;
      core[i].interleaved = false;
      core[i].metadataComplete = true;
      core[i].indexed = false;
      core[i].falseColor = false;
    }

    // populate MetadataStore

    if (creationDate != null) {
      creationDate = creationDate.replaceAll("'", "");
      SimpleDateFormat parse = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      Date date = parse.parse(creationDate, new ParsePosition(0));
      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
      creationDate = fmt.format(date);
    }

    for (int i=0; i<core.length; i++) {
      store.setImageName("Series " + i, i);
      if (creationDate != null) store.setImageCreationDate(creationDate, i);
      else MetadataTools.setDefaultCreationDate(store, id, i);

      if (pixelSizeX != null) {
        store.setDimensionsPhysicalSizeX(new Float(pixelSizeX), i, 0);
      }
      if (pixelSizeY != null) {
        store.setDimensionsPhysicalSizeY(new Float(pixelSizeY), i, 0);
      }
    }

    MetadataTools.populatePixels(store, this);

    for (int i=0; i<core.length; i++) {
      for (int c=0; c<core[i].sizeC; c++) {
        if (c < channelNames.size()) {
          store.setLogicalChannelName((String) channelNames.get(c), i, c);
        }
        if (c < emWaves.size()) {
          store.setLogicalChannelEmWave((Integer) emWaves.get(c), i, c);
        }
        if (c < exWaves.size()) {
          store.setLogicalChannelExWave((Integer) exWaves.get(c), i, c);
        }
        if (c < illuminations.size()) {
          store.setLogicalChannelIlluminationType(
            (String) illuminations.get(c), i, c);
        }
      }
    }

    int nLasers = (int) Math.min(dyeNames.size(), wavelengths.size());
    for (int i=0; i<nLasers; i++) {
      store.setLaserLaserMedium((String) dyeNames.get(i), 0, i);
      store.setLaserWavelength((Integer) wavelengths.get(i), 0, i);
    }

    store.setDetectorGain(new Float(gain), 0, 0);
    store.setDetectorOffset(new Float(offset), 0, 0);
    store.setDetectorVoltage(new Float(voltage), 0, 0);

    store.setObjectiveLensNA(new Float(lensNA), 0, 0);
    store.setObjectiveModel(objectiveName, 0, 0);
    store.setObjectiveNominalMagnification(
      new Integer((int) Float.parseFloat(magnification)), 0, 0);
    store.setObjectiveWorkingDistance(new Float(workingDistance), 0, 0);
  }

  // -- Helper methods --

  private String sanitizeFile(String file, String path) {
    String f = file;
    f = f.replaceAll("\"", "");
    f = f.replace('\\', File.separatorChar);
    f = f.replace('/', File.separatorChar);
    if (!isOIB && path.equals("")) return f;
    return path + File.separator + f;
  }

  private RandomAccessStream getFile(String name)
    throws FormatException, IOException
  {
    if (isOIB) {
      if (name.startsWith("/") || name.startsWith("\\")) {
        name = name.substring(1);
      }
      name = name.replace('\\', '/');
      return poi.getDocumentStream((String) oibMapping.get(name));
    }
    else return new RandomAccessStream(name);
  }

  private boolean isPreviewName(String name) {
    // "-R" in the file name indicates that this is a preview image
    int index = name.indexOf("-R");
    return index == name.length() - 9;
  }

}