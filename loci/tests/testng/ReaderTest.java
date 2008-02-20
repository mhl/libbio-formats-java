//
// ReaderTest.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan,
Eric Kjellman and Brian Loranger.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.tests.testng;

import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.*;
import loci.formats.*;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.meta.MetadataStore;

/**
 * TestNG tester for Bio-Formats file format readers.
 * Details on failed tests are written to a log file, for easier processing.
 *
 * NB: {@link loci.formats.ome} and ome-java.jar
 * are required for some of the tests.
 *
 * To run tests:
 * ant -Dtestng.directory="/path" -Dtestng.multiplier="1.0" test-all
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/test/ReaderTest.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/test/ReaderTest.java">SVN</a></dd></dl>
 */
public class ReaderTest {

  // -- Fields --

  /** Whether or not this is the first time calling the data provider. */
  private boolean isFirstTime = true;

  /** List of files to test. */
  private Object[][] data;

  /**
   * Multiplier for use adjusting timing values. Slower machines take longer to
   * complete the timing test, and thus need to set a higher (&gt;1) multiplier
   * to avoid triggering false timing test failures. Conversely, faster
   * machines should set a lower (&lt;1) multipler to ensure things finish as
   * quickly as expected.
   */
  private static float timeMultiplier = 1;

  /** List of configuration files. */
  private static Vector configFiles = new Vector();

  /** Configuration file reader. */
  private static ConfigurationFiles config = ConfigurationFiles.newInstance();

  // -- Data provider --

  /**
   * @testng.data-provider name = "provider"
   */
  public Object[][] createData() {
    if (isFirstTime) {
      isFirstTime = false;

      // parse base directory
      final String baseDirProp = "testng.directory";
      String baseDir = System.getProperty(baseDirProp);
      if (!new File(baseDir).isDirectory()) {
        if (baseDir == null || baseDir.equals("${" + baseDirProp + "}")) {
          writeLog("Error: no base directory specified.");
        }
        else writeLog("Error: invalid base directory: " + baseDir);
        writeLog("Please specify a directory containing files to test:");
        writeLog("   ant -D" + baseDirProp + "=\"/path/to/data\" test-all");
        return null;
      }

      // create log file
      createLogFile();
      writeLog("testng.directory = " + baseDir);

      // parse multiplier
      final String multProp = "testng.multiplier";
      String mult = System.getProperty(multProp);
      if (mult != null && !mult.equals("${" + multProp + "}")) {
        try {
          timeMultiplier = Float.parseFloat(mult);
        }
        catch (NumberFormatException exc) {
          writeLog("Warning: invalid multiplier: " + mult);
        }
      }
      writeLog("testng.multiplier = " + timeMultiplier);

      // detect maximum heap size
      long maxMemory = Runtime.getRuntime().maxMemory() >> 20;
      writeLog("Maximum heap size = " + maxMemory + " MB");

      // scan for files
      Vector files = new Vector();
      getFiles(baseDir, files);
      writeLog("Total files: " + files.size());

      // initialize one reader per dataset
      List readers = new LinkedList(); // more efficient than Vector here
      Vector errors = new Vector();
      while (files.size() > 0) {
        String id = (String) files.get(0);
        LogTools.print("Initializing " + id + ": ");
        IFormatReader r = makeReader(true);
        boolean removed = false;
        try {
          r.setId(id);
          // remove used files
          String[] used = r.getUsedFiles();
          for (int i=0; i<used.length; i++) {
            int ndx = files.indexOf(used[i]);
            if (ndx < 0) {
              errors.add("Error: invalid used file #" + i + ": " + used[i]);
            }
            else {
              if (ndx == 0) removed = true;
              files.removeElementAt(ndx);
            }
          }
          if (!removed) {
            errors.add("Error: used files list does not include base file");
          }
          if (used.length == 1 && errors.isEmpty()) LogTools.println("OK");
          else {
            LogTools.println(used.length +
              (used.length == 1 ? " file" : " files"));
          }
          for (int i=0; i<errors.size(); i++) writeLog((String) errors.get(i));
          errors.removeAllElements();
          readers.add(r);
        }
        catch (Throwable t) {
          LogTools.println("error");
          writeLog(t);
        }

        // remove base file, if for some reason it wasn't already
        if (!removed) files.removeElementAt(0);
      }
      writeLog("Total datasets: " + readers.size());

      // package into TestNG array format
      data = new Object[readers.size()][1];
      for (int i=0; i<readers.size(); i++) data[i][0] = readers.get(i);
    }
    return data;
  }

  // -- Tests --

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all pixels"
   */
  public void testBufferedImageDimensions(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testBufferedImageDimensions: " + file;
    boolean success = true;
    try {
      BufferedImage b = null;
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);

        int x = reader.getSizeX();
        int y = reader.getSizeY();
        int c = reader.getRGBChannelCount();
        int type = reader.getPixelType();

        for (int j=0; j<reader.getImageCount() && success; j++) {
          b = reader.openImage(j);
          boolean passX = x == b.getWidth();
          boolean passY = y == b.getHeight();
          boolean passC = c < b.getRaster().getNumBands();
          boolean passType = type == ImageTools.getPixelType(b);
          success = passX && passY && passC && passType;
        }
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all pixels"
   */
  public void testByteArrayDimensions(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testByteArrayDimensions: " + file;
    boolean success = true;
    try {
      byte[] b = null;
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        int x = reader.getSizeX();
        int y = reader.getSizeY();
        int c = reader.isIndexed() ? 1 : reader.getRGBChannelCount();
        int bytes = FormatTools.getBytesPerPixel(reader.getPixelType());

        int expected = x * y * c * bytes;

        for (int j=0; j<reader.getImageCount() && success; j++) {
          b = reader.openBytes(j);
          success = b.length == expected;
        }
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all pixels"
   */
  public void testThumbnailImageDimensions(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testThumbnailImageDimensions: " + file;
    boolean success = true;
    try {
      BufferedImage b = null;
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);

        int x = reader.getThumbSizeX();
        int y = reader.getThumbSizeY();
        int c = reader.getRGBChannelCount();
        int type = reader.getPixelType();

        for (int j=0; j<reader.getImageCount() && success; j++) {
          b = reader.openThumbImage(j);
          boolean passX = x == b.getWidth();
          boolean passY = y == b.getHeight();
          boolean passC = c < b.getRaster().getNumBands();
          boolean passType = type == ImageTools.getPixelType(b);

          success = passX && passY && passC && passType;
        }
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all pixels"
   */
  public void testThumbnailByteArrayDimensions(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testThumbnailByteArrayDimensions: " + file;
    boolean success = true;
    try {
      byte[] b = null;
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        int x = reader.getThumbSizeX();
        int y = reader.getThumbSizeY();
        int c = reader.isIndexed() ? 1 : reader.getRGBChannelCount();
        int bytes = FormatTools.getBytesPerPixel(reader.getPixelType());

        int expected = x * y * c * bytes;

        for (int j=0; j<reader.getImageCount() && success; j++) {
          b = reader.openThumbBytes(j);
          success = b.length == expected;
        }
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all fast"
   */
  public void testImageCount(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testImageCount: " + file;
    boolean success = true;
    try {
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        int imageCount = reader.getImageCount();
        int z = reader.getSizeZ();
        int c = reader.getEffectiveSizeC();
        int t = reader.getSizeT();
        success = imageCount == z * c * t;
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all xml fast"
   */
  public void testOMEXML(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testOMEXML: " + file;
    boolean success = true;
    String msg = null;
    try {
      MetadataRetrieve retrieve = (MetadataRetrieve) reader.getMetadataStore();
      success = MetadataTools.isOMEXMLMetadata(retrieve);
      if (!success) msg = retrieve.getClass().getName();

      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);

        String type = FormatTools.getPixelTypeString(reader.getPixelType());

        boolean passX = reader.getSizeX() ==
          retrieve.getPixelsSizeX(i, 0).intValue();
        if (!passX) msg = "SizeX";
        boolean passY = reader.getSizeY() ==
          retrieve.getPixelsSizeY(i, 0).intValue();
        if (!passY) msg = "SizeY";
        boolean passZ = reader.getSizeZ() ==
          retrieve.getPixelsSizeZ(i, 0).intValue();
        if (!passZ) msg = "SizeZ";
        boolean passC = reader.getSizeC() ==
          retrieve.getPixelsSizeC(i, 0).intValue();
        if (!passC) msg = "SizeC";
        boolean passT = reader.getSizeT() ==
          retrieve.getPixelsSizeT(i, 0).intValue();
        if (!passT) msg = "SizeT";
        boolean passBE = reader.isLittleEndian() !=
          retrieve.getPixelsBigEndian(i, 0).booleanValue();
        if (!passBE) msg = "BigEndian";
        boolean passDE = reader.getDimensionOrder().equals(
          retrieve.getPixelsDimensionOrder(i, 0));
        if (!passDE) msg = "DimensionOrder";
        boolean passType = type.equalsIgnoreCase(
          retrieve.getPixelsPixelType(i, 0));
        if (!passType) msg = "PixelType";

        success = passX && passY && passZ &&
          passC && passT && passBE && passDE && passType;
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all fast"
   */
  public void testConsistent(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testConsistent: " + file;
    boolean success = true;
    String msg = null;
    try {
      if (reader.getSeriesCount() != config.getNumSeries(file)) {
        success = false;
        msg = "series counts differ";
      }

      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        config.setSeries(file, i);

        boolean passX = config.getWidth(file) == reader.getSizeX();
        boolean passY = config.getHeight(file) == reader.getSizeY();
        boolean passZ = config.getZ(file) == reader.getSizeZ();
        boolean passC = config.getC(file) == reader.getSizeC();
        boolean passT = config.getT(file) == reader.getSizeT();
        boolean passDim =
          config.getDimOrder(file).equals(reader.getDimensionOrder());
        boolean passInt = config.isInterleaved(file) == reader.isInterleaved();
        boolean passRGB = config.isRGB(file) == reader.isRGB();
        boolean passTX = config.getThumbX(file) == reader.getThumbSizeX();
        boolean passTY = config.getThumbY(file) == reader.getThumbSizeY();
        boolean passType = config.getPixelType(file) == reader.getPixelType();
        boolean passEndian =
          config.isLittleEndian(file) == reader.isLittleEndian();
        //boolean passIndexed = config.isIndexed(file) == reader.isIndexed();
        //boolean passFalseColor =
        //  config.isFalseColor(file) == reader.isFalseColor();

        success = passX && passY && passZ && passC && passT && passDim &&
          passInt && passRGB && passTX && passTY && passType && passEndian;
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all"
   */
  public void testMemoryUsage(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testMemoryUsage: " + file;
    boolean success = true;
    String msg = null;
    try {
      Runtime r = Runtime.getRuntime();
      int maxMemory = (int) ((r.totalMemory() - r.freeMemory()) >> 20);
      int initialMemory = maxMemory;

      int mem = (int) ((r.totalMemory() - r.freeMemory()) >> 20);
      if (mem > maxMemory) maxMemory = mem;

      for (int i=0; i<reader.getImageCount(); i++) {
        BufferedImage b = reader.openImage(i);
        mem = (int) ((r.totalMemory() - r.freeMemory()) >> 20);
        if (mem > maxMemory) maxMemory = mem;
      }
      int finalMemory = (int) ((r.totalMemory() - r.freeMemory()) >> 20);

      // max memory usage should be no more than twice the file size
      if (maxMemory - initialMemory > 2*(config.getFileSize(file) + 1)) {
        success = false;
        msg =  "used " + (maxMemory - initialMemory) + "MB; expected <= " +
          (2*config.getFileSize(file) + 1) + "MB";
      }

      // check that the reader doesn't have any significant memory leaks
      if (finalMemory - initialMemory > 10) {
        success = false;
        String leakMsg = (finalMemory - initialMemory) + "MB leaked";
        msg = msg == null ? leakMsg : msg + "; " + leakMsg;
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all"
   */
  public void testAccessTime(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testAccessTime: " + file;
    boolean success = true;
    String msg = null;
    try {
      long l1 = System.currentTimeMillis();
      for (int i=0; i<reader.getImageCount(); i++) reader.openImage(i);
      long l2 = System.currentTimeMillis();
      double actual = (double) (l2 - l1) / reader.getImageCount();
      double proper = config.getTimePerPlane(file);
      if (actual - timeMultiplier * proper > 20.0) {
        success = false;
        msg = "got " + actual + " ms, expected " + proper + " ms";
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all"
   */
  public void testSaneUsedFiles(IFormatReader reader) {
    String file = reader.getCurrentFile();
    String testName = "testSaneUsedFiles: " + file;
    boolean success = true;
    String msg = null;
    try {
      String[] base = reader.getUsedFiles();
      if (base.length == 1) {
        if (!base[0].equals(file)) success = false;
      }
      else {
        Arrays.sort(base);
        IFormatReader r = makeReader(false);
        for (int i=0; i<base.length && success; i++) {
          r.setId(base[i]);
          String[] comp = r.getUsedFiles();
          Arrays.sort(comp);
          for (int j=0; j<comp.length && success; j++) {
            if (!comp[j].equals(base[j])) {
              success = false;
              msg = base[i];
            }
          }
        }
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all xml fast"
   */
  public void testValidXML(FileStitcher reader) {
    String file = reader.getCurrentFile();
    String testName = "testValidXML: " + file;
    boolean success = true;
    try {
      MetadataRetrieve retrieve = (MetadataRetrieve) reader.getMetadataStore();

      String xml = MetadataTools.getOMEXML(retrieve);
      success = xml != null;
      // TODO call XMLTools.validateXML; somehow get a return value
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "all pixels"
   */
  public void testPixelsHashes(FileStitcher reader) {
    String file = reader.getCurrentFile();
    String testName = "testPixelsHashes: " + file;
    boolean success = true;
    String msg = null;
    try {
      // check the MD5 of the first plane in each series
      for (int i=0; i<reader.getSeriesCount() && success; i++) {
        reader.setSeries(i);
        config.setSeries(file, i);
        String md5 = md5(reader.openBytes(0));
        if (!md5.equals(config.getMD5(file))) {
          success = false;
          msg = "series " + i;
        }
      }
    }
    catch (Throwable t) {
      writeLog(t);
      success = false;
    }
    result(testName, success, msg);
  }

  /**
   * @testng.test dataProvider = "provider"
   *              groups = "config"
   */
  public void writeConfigFile(FileStitcher reader) {
    String file = reader.getCurrentFile();
    writeLog("Generating configuration: " + file);
    Exception exc = null;
    try {
      StringBuffer line = new StringBuffer();
      line.append("\"");
      line.append(new Location(file).getName());
      line.append("\" total_series=");
      line.append(reader.getSeriesCount());
      long start = System.currentTimeMillis();
      int total = 0;
      for (int i=0; i<reader.getSeriesCount(); i++) {
        reader.setSeries(i);
        line.append(" [series=");
        line.append(i);
        line.append(" x=" + reader.getSizeX());
        line.append(" y=" + reader.getSizeY());
        line.append(" z=" + reader.getSizeZ());
        line.append(" c=" + reader.getSizeC());
        line.append(" t=" + reader.getSizeT());
        line.append(" order=" + reader.getDimensionOrder());
        line.append(" interleave=" + reader.isInterleaved());
        line.append(" rgb=" + reader.isRGB());
        line.append(" thumbx=" + reader.getThumbSizeX());
        line.append(" thumby=" + reader.getThumbSizeY());
        line.append(" type=" +
          FormatTools.getPixelTypeString(reader.getPixelType()));
        line.append(" little=" + reader.isLittleEndian());
        line.append(" indexed=" + reader.isIndexed());
        line.append(" falseColor=" + reader.isFalseColor());
        line.append(" md5=" + md5(reader.openBytes(0)));
        line.append("]");

        total += reader.getImageCount();
        for (int j=1; j<reader.getImageCount(); j++) reader.openImage(j);
      }
      long end = System.currentTimeMillis();

      line.append(" access=");
      line.append((end - start) / total);
      line.append(" mem=");
      line.append(new RandomAccessStream(file).length());
      line.append(" test=true\n");

      File f = new File(new Location(file).getParent(), ".bioformats");
      BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
      w.write(line.toString());
      w.close();
    }
    catch (Throwable t) {
      try {
        File f = new File(new Location(file).getParent(), ".bioformats");
        BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
        w.write("\"" + file + "\" test=false\n");
        w.close();
      }
      catch (Throwable t2) {
        writeLog(t2);
        assert false;
      }
    }
  }

  // -- Helper methods --

  /** Calculate the MD5 of a byte array. */
  private static String md5(byte[] b) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.reset();
      md.update(b);
      byte[] digest = md.digest();
      StringBuffer sb = new StringBuffer();
      for (int i=0; i<digest.length; i++) {
        String a = Integer.toHexString(0xff & digest[i]);
        if (a.length() == 1) a = "0" + a;
        sb.append(a);
      }
      return sb.toString();
    }
    catch (NoSuchAlgorithmException e) { writeLog(e); }
    return null;
  }

  /** Writes the given message to the log file. */
  private static void writeLog(String s) {
    LogTools.println(s);
    LogTools.flush();
  }

  /** Writes the given exception's stack trace to the log file. */
  private static void writeLog(Throwable t) {
    LogTools.trace(t);
    LogTools.flush();
  }

  /** Outputs test result and generates appropriate assertion. */
  private static void result(String testName, boolean success) {
    result(testName, success, null);
  }

  /**
   * Outputs test result with optional extra message
   * and generates appropriate assertion.
   */
  private static void result(String testName, boolean success, String msg) {
    writeLog(testName + ": " + (success ? "PASSED" : "FAILED") +
      (msg == null ? "" : " (" + msg + ")"));
    assert success;
  }

  private static IFormatReader makeReader(boolean metadata) {
    FileStitcher fs = new FileStitcher();
    fs.setNormalized(true);
    if (metadata) {
      fs.setOriginalMetadataPopulated(true);
      fs.setMetadataFiltered(true);
      MetadataStore store = MetadataTools.createOMEXMLMetadata();
      fs.setMetadataStore(store);
    }
    return fs;
  }

  /** Creates a new log file. */
  private static void createLogFile() {
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    StringBuffer dateBuf = new StringBuffer();
    fmt.format(new Date(), dateBuf, new FieldPosition(0));
    String logFile = "loci-software-test-" + dateBuf + ".log";
    writeLog("Output logged to " + logFile);
    try {
      LogTools.getLog().setStream(
        new PrintStream(new FileOutputStream(logFile)));
    }
    catch (IOException e) { writeLog(e); }
  }

  /** Recursively generate a list of files to test. */
  private static void getFiles(String root, List files) {
    Location f = new Location(root);
    String[] subs = f.list();
    if (subs == null) subs = new String[0];
    Arrays.sort(subs);

    // make sure that if a config file exists, it is first on the list
    for (int i=0; i<subs.length; i++) {
      if (subs[i].endsWith(".bioformats")) {
        String tmp = subs[0];
        subs[0] = subs[i];
        subs[i] = tmp;
        break;
      }
    }

    ImageReader typeTester = new ImageReader();

    for (int i=0; i<subs.length; i++) {
      subs[i] = new Location(root, subs[i]).getAbsolutePath();
      LogTools.print("Checking " + subs[i] + ": ");
      if (isIgnoredFile(subs[i])) {
        LogTools.println("ignored");
        continue;
      }
      Location file = new Location(subs[i]);

      if (file.isDirectory()) {
        LogTools.println("directory");
        getFiles(subs[i], files);
      }
      else if (file.getName().equals(".bioformats")) {
        // special config file for the test suite
        LogTools.println("config file");
        configFiles.add(file.getAbsolutePath());
      }
      else {
        if (typeTester.isThisType(subs[i])) {
          LogTools.println("OK");
          files.add(file.getAbsolutePath());
        }
        else LogTools.println("invalid type");
      }
      file = null;
    }
  }

  /** Determines if the given file should be ignored by the test suite. */
  private static boolean isIgnoredFile(String file) {
    for (int i=0; i<configFiles.size(); i++) {
      try {
        String s = (String) configFiles.get(i);
        if (!config.isParsed(s)) config.addFile(s);
      }
      catch (IOException exc) {
        LogTools.trace(exc);
      }
    }
    return !config.testFile(file) && !file.endsWith(".bioformats");
  }

}