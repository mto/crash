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

package org.crsh.ssh;

import org.crsh.plugin.CRaSHPlugin;
import org.crsh.plugin.PropertyDescriptor;
import org.crsh.plugin.ResourceKind;
import org.crsh.ssh.term.SSHLifeCycle;
import org.crsh.vfs.Resource;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import org.apache.sshd.common.util.SecurityUtils;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class SSHPlugin extends CRaSHPlugin<SSHPlugin> {

  /** The SSH port. */
  public static final PropertyDescriptor<Integer> SSH_PORT = PropertyDescriptor.create("ssh.port", 2000, "The SSH port");

  /** The SSH key path. */
  public static final PropertyDescriptor<String> SSH_KEYPATH = PropertyDescriptor.create("ssh.keypath", (String)null, "The path to the key file");

  /** The authentication plugin to use. */
  public static final PropertyDescriptor<String> AUTH = PropertyDescriptor.create("auth", (String)null, "The authentication plugin");

  /** . */
  private SSHLifeCycle lifeCycle;

  @Override
  public SSHPlugin getImplementation() {
    return this;
  }

  @Override
  protected Iterable<PropertyDescriptor<?>> createConfigurationCapabilities() {
    return Arrays.<PropertyDescriptor<?>>asList(SSH_PORT, SSH_KEYPATH, AUTH);
  }

  @Override
  public void init() {

    SecurityUtils.setRegisterBouncyCastle(true);
    //
    Integer port = getContext().getProperty(SSH_PORT);
    if (port == null) {
      log.info("Could not boot SSHD due to missing due to missing port configuration");
      return;
    }

    // Get embedded default key
    URL keyURL = SSHPlugin.class.getResource("hostkey.pem");
    if (keyURL != null) {
      log.debug("Found embedded key url " + keyURL);
    }

    // Override from config if any
    Resource res = getContext().loadResource("hostkey.pem", ResourceKind.CONFIG);
    if (res != null) {
      keyURL = res.getURL();
      log.debug("Found ssh key url " + keyURL);
    }

    // If we have a key path, we convert is as an URL
    String keyPath = getContext().getProperty(SSH_KEYPATH);
    if (keyPath != null) {
      log.debug("Found key path " + keyPath);
      File f = new File(keyPath);
      if (f.exists() && f.isFile()) {
        try {
          keyURL = f.toURI().toURL();
        } catch (MalformedURLException e) {
          log.debug("Ignoring invalid key " + keyPath, e);
        }
      } else {
        log.debug("Ignoring invalid key path " + keyPath);
      }
    }

    //
    if (keyURL == null) {
      log.info("Could not boot SSHD due to missing key");
      return;
    }

    // Get the authentication
    String authentication = getContext().getProperty(AUTH);

    //
    log.info("Booting SSHD");
    SSHLifeCycle lifeCycle = new SSHLifeCycle(getContext());
    lifeCycle.setPort(port);
    lifeCycle.setKeyURL(keyURL);
    lifeCycle.setAuthentication(authentication);
    lifeCycle.init();

    //
    this.lifeCycle = lifeCycle;
  }

  @Override
  public void destroy() {
    if (lifeCycle != null) {
      log.info("Shutting down SSHD");
      lifeCycle.destroy();
      lifeCycle = null;
    }
  }
}
