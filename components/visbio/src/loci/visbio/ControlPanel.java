//
// ControlPanel.java
//

/*
VisBio application for visualization of multidimensional biological
image data. Copyright (C) 2002-@year@ Curtis Rueden and Abraham Sorber.

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

package loci.visbio;

import java.awt.BorderLayout;
import javax.swing.JPanel;

/**
 * ControlPanel is the superclass of all control panel types.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/visbio/src/loci/visbio/ControlPanel.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/visbio/src/loci/visbio/ControlPanel.java">SVN</a></dd></dl>
 */
public class ControlPanel extends JPanel {

  // -- Fields --

  /** Associated logic manager. */
  protected LogicManager lm;

  /** Name of this control panel. */
  protected String name;

  /** Tip for this control panel. */
  protected String tip;

  // -- Constructor --

  /** Constructs a control panel. */
  public ControlPanel(LogicManager logic, String name, String tip) {
    super();
    lm = logic;
    this.name = name;
    this.tip = tip;
    setLayout(new BorderLayout());
  }

  // -- ControlPanel API methods --

  /** Gets control panel's logic manager. */
  public LogicManager getManager() { return lm; }

  /** Gets the name of this control panel. */
  public String getName() { return name; }

  /** Gets the tip for this control panel. */
  public String getTip() { return tip; }

}