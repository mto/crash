/*
 * Copyright (C) 2010 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.crsh.jcr;

import javax.jcr.Node;
import java.util.Iterator;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class SelectTestCase extends AbstractJCRCommandTestCase {

  public void testQuery() throws Exception {
    assertLogin();
    groovyShell.evaluate("session.rootNode.addNode('foo').setProperty('bar','juu');");
    groovyShell.evaluate("session.save();");
    Iterator<?> produced = assertOk("select * from nt:base where bar = 'juu'").getProduced().iterator();
    assertTrue(produced.hasNext());
    assertEquals("/foo", ((Node) produced.next()).getPath());
    assertFalse(produced.hasNext());
  }
}
