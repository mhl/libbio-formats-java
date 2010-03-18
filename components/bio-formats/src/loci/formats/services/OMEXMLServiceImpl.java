//
// OMEXMLServiceImpl.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.services;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import ome.xml.OMEXMLFactory;
import ome.xml.OMEXMLNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import loci.common.services.AbstractService;
import loci.common.services.ServiceException;
import loci.common.xml.XMLTools;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataConverter;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.meta.MetadataStore;
import loci.formats.ome.OMEXMLMetadata;

/**
 * @author callan
 *
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/services/OMEXMLServiceImpl.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/services/OMEXMLServiceImpl.java">SVN</a></dd></dl>
 */
public class OMEXMLServiceImpl extends AbstractService
  implements OMEXMLService {

  /** Logger for this class. */
  private static final Logger LOGGER =
    LoggerFactory.getLogger(OMEXMLService.class);

  /** Reorderingstylesheet. */
  private static Templates reorderXSLT =
    XMLTools.getStylesheet("/loci/formats/meta/reorder-2008-09.xsl",
                           OMEXMLServiceImpl.class);
  
  /**
   * Default constructor.
   */
  public OMEXMLServiceImpl() {
    checkClassDependency(ome.xml.OMEXMLFactory.class);
    checkClassDependency(ome.xml.OMEXMLNode.class);
  }
  
  /* (non-Javadoc)
   * @see loci.formats.services.OMEXMLService#getLatestVersion()
   */
  public String getLatestVersion() {
    return OMEXMLFactory.LATEST_VERSION;
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#createOMEXMLMetadata()
   */
  public OMEXMLMetadata createOMEXMLMetadata() throws ServiceException {
    return createOMEXMLMetadata(null);
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#createOMEXMLMetadata(java.lang.String)
   */
  public OMEXMLMetadata createOMEXMLMetadata(String xml)
    throws ServiceException {
    return createOMEXMLMetadata(xml, null);
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#createOMEXMLMetadata(java.lang.String, java.lang.String)
   */
  public OMEXMLMetadata createOMEXMLMetadata(String xml, String version)
    throws ServiceException {
    OMEXMLNode ome = xml == null ? null : createRoot(xml);
    if (version == null) {
      if (ome == null) {
        // default to newest schema version
        version = getLatestVersion();
      }
      else {
        // extract schema version from OME root node
        version = ome.getVersion();
      }
    }

    // create metadata object of the appropriate schema version
    String metaClass = "loci.formats.ome.OMEXML" +
      version.replaceAll("[^\\w]", "") + "Metadata";
    try {
      Class<? extends OMEXMLMetadata> klass = 
        (Class<? extends OMEXMLMetadata>) Class.forName(metaClass);
      OMEXMLMetadata meta = klass.newInstance();
      // attach OME root node to metadata object
      if (ome != null) meta.setRoot(ome);
      else meta.createRoot();
      return meta;
    } catch (Exception e) {
      LOGGER.warn("No IMetadata implementation for: {}", version, e);
      return null;
    }
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#createOMEXMLRoot(java.lang.String)
   */
  public Object createOMEXMLRoot(String xml) throws ServiceException {
    return createRoot(xml);
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#isOMEXMLMetadata(java.lang.Object)
   */
  public boolean isOMEXMLMetadata(Object o) {
    return o instanceof OMEXMLMetadata;
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#isOMEXMLRoot(java.lang.Object)
   */
  public boolean isOMEXMLRoot(Object o) {
    return o instanceof OMEXMLNode;
  }

  /**
   * Constructs an OME root node. <b>NOTE:</b> This method is mostly here to
   * ensure type safety of return values as instances of service dependency
   * classes should not leak out of the interface.
   * @param xml String of XML to create the root node from.
   * @return An ome.xml.OMEXMLNode subclass root node.
   * @throws IOException If there is an error reading from the string.
   * @throws SAXException If there is an error parsing the XML.
   * @throws ParserConfigurationException If there is an error preparing the
   * parsing infrastructure.
   */
  private OMEXMLNode createRoot(String xml) throws ServiceException {
    try {
      return OMEXMLFactory.newOMENodeFromSource(xml);
    }
    catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#getOMEXMLVersion(java.lang.Object)
   */
  public String getOMEXMLVersion(Object o) {
    if (o == null) return null;
    String name = o.getClass().getName();
    if (o instanceof OMEXMLMetadata) {
      final String prefix = "loci.formats.ome.OMEXML";
      final String suffix = "Metadata";
      if (name.startsWith(prefix) && name.endsWith(suffix)) {
        String numbers =
          name.substring(prefix.length(), name.length() - suffix.length());
        if (numbers.length() == 6) {
          return numbers.substring(0, 4) + "-" +
            numbers.substring(4, 6).toUpperCase();
        }
      }
    }
    else if (o instanceof OMEXMLNode) {
      return ((OMEXMLNode) o).getVersion();
    }
    return null;
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#getOMEMetadata(loci.formats.meta.MetadataRetrieve)
   */
  public OMEXMLMetadata getOMEMetadata(MetadataRetrieve src)
    throws ServiceException {
    // check if the metadata is already an OME-XML metadata object
    if (src instanceof OMEXMLMetadata) return (OMEXMLMetadata) src;

    // populate a new OME-XML metadata object with metadata
    // converted from the non-OME-XML metadata object
    OMEXMLMetadata omexmlMeta = createOMEXMLMetadata();
    convertMetadata(src, omexmlMeta);
    return omexmlMeta;
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#getOMEXML(loci.formats.meta.MetadataRetrieve)
   */
  public String getOMEXML(MetadataRetrieve src) throws ServiceException {
    OMEXMLMetadata omexmlMeta = getOMEMetadata(src);
    String xml = omexmlMeta.dumpXML();
    String reordered = null;
    try {
      reordered = XMLTools.transformXML(xml, reorderXSLT);
    }
    catch (IOException exc) {
      LOGGER.warn("Could not transform OME-XML", exc);
    }
    return reordered == null ? xml : reordered;
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#validateOMEXML(java.lang.String)
   */
  public boolean validateOMEXML(String xml) {
    return validateOMEXML(xml, false);
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#validateOMEXML(java.lang.String, boolean)
   */
  public boolean validateOMEXML(String xml, boolean pixelsHack) {
    // HACK: Inject a TiffData element beneath any childless Pixels elements.
    if (pixelsHack) {
      // convert XML string to DOM
      Document doc = null;
      Exception exception = null;
      try {
        doc = XMLTools.parseDOM(xml);
      }
      catch (ParserConfigurationException exc) { exception = exc; }
      catch (SAXException exc) { exception = exc; }
      catch (IOException exc) { exception = exc; }
      if (exception != null) {
        LOGGER.info("Malformed OME-XML", exception);
        return false;
      }

      // inject TiffData elements as needed
      NodeList list = doc.getElementsByTagName("Pixels");
      for (int i=0; i<list.getLength(); i++) {
        Node node = list.item(i);
        NodeList children = node.getChildNodes();
        boolean needsTiffData = true;
        for (int j=0; j<children.getLength(); j++) {
          Node child = children.item(j);
          String name = child.getLocalName();
          if ("TiffData".equals(name) || "BinData".equals(name)) {
            needsTiffData = false;
            break;
          }
        }
        if (needsTiffData) {
          // inject TiffData element
          Node tiffData = doc.createElement("TiffData");
          node.insertBefore(tiffData, node.getFirstChild());
        }
      }

      // convert tweaked DOM back to XML string
      try {
        xml = XMLTools.getXML(doc);
      }
      catch (TransformerConfigurationException exc) { exception = exc; }
      catch (TransformerException exc) { exception = exc; }
      if (exception != null) {
        LOGGER.info("Internal XML conversion error", exception);
        return false;
      }
    }
    return XMLTools.validateXML(xml, "OME-XML");
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#populateOriginalMetadata(loci.formats.ome.OMEXMLMetadata, java.lang.String, java.lang.String)
   */
  public void populateOriginalMetadata(OMEXMLMetadata omexmlMeta,
    String key, String value)
  {
    omexmlMeta.setOriginalMetadata(key, value);
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#convertMetadata(java.lang.String, loci.formats.meta.MetadataStore)
   */
  public void convertMetadata(String xml, MetadataStore dest)
    throws ServiceException {
    OMEXMLNode ome = createRoot(xml);
    String rootVersion = getOMEXMLVersion(ome);
    String storeVersion = getOMEXMLVersion(dest);
    if (rootVersion.equals(storeVersion)) {
      // metadata store is already an OME-XML metadata object of the
      // correct schema version; populate OME-XML string directly
      if (!(dest instanceof OMEXMLMetadata)) {
        throw new IllegalArgumentException(
            "Expecting OMEXMLMetadata instance.");
      }
      // FIXME: What's below was in the ReflectedUniverse, the method no
      // longer exists or has changed.
      //((OMEXMLMetadata) dest).createRoot(xml);
    }
    else {
      // metadata store is incompatible; create an OME-XML
      // metadata object and copy it into the destination
      IMetadata src = createOMEXMLMetadata(xml);
      convertMetadata(src, dest);
    }
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#convertMetadata(loci.formats.meta.MetadataRetrieve, loci.formats.meta.MetadataStore)
   */
  public void convertMetadata(MetadataRetrieve src, MetadataStore dest) {
    MetadataConverter.convertMetadata(src, dest);
  }

  // -- Utility methods - casting --

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#asStore(loci.formats.meta.MetadataRetrieve)
   */
  public MetadataStore asStore(MetadataRetrieve meta) {
    return meta instanceof MetadataStore ? (MetadataStore) meta : null;
  }

  /* (non-Javadoc)
   * @see loci.formats.ome.OMEXMLService#asRetrieve(loci.formats.meta.MetadataStore)
   */
  public MetadataRetrieve asRetrieve(MetadataStore meta) {
    return meta instanceof MetadataRetrieve ? (MetadataRetrieve) meta : null;
  }

}