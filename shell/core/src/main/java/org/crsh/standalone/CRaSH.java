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

package org.crsh.standalone;

import com.sun.tools.attach.VirtualMachine;
import org.crsh.cmdline.ClassDescriptor;
import org.crsh.cmdline.CommandFactory;
import org.crsh.cmdline.Delimiter;
import org.crsh.cmdline.IntrospectionException;
import org.crsh.cmdline.annotations.Argument;
import org.crsh.cmdline.annotations.Command;
import org.crsh.cmdline.annotations.Option;
import org.crsh.cmdline.annotations.Usage;
import org.crsh.cmdline.matcher.CommandMatch;
import org.crsh.cmdline.matcher.InvocationContext;
import org.crsh.cmdline.matcher.Matcher;
import org.crsh.term.BaseTerm;
import org.crsh.term.Term;
import org.crsh.term.processor.Processor;
import org.crsh.term.spi.jline.JLineIO;
import org.crsh.term.spi.net.TermIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.*;
import java.util.List;
import java.util.Properties;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class CRaSH {

  /** . */
  private static Logger log = LoggerFactory.getLogger(CRaSH.class);

  /** . */
  private final ClassDescriptor<CRaSH> descriptor;

  public CRaSH() throws IntrospectionException {
    this.descriptor = CommandFactory.create(CRaSH.class);
  }

  @Command
  public void main(
    @Option(names = {"h","help"})
    @Usage("display standalone mode help")
    Boolean help,
    @Option(names={"j","jar"})
    @Usage("specify a file system path of a jar added to the class path")
    List<String> jars,
    @Option(names={"c","cmd"})
    @Usage("specify a file system path of a dir added to the command path")
    List<String> cmds,
    @Option(names={"conf"})
    @Usage("specify a file system path of a dir added to the configuration path")
    List<String> confs,
    @Option(names={"p","property"})
    @Usage("specify a configuration property of the form a=b")
    List<String> properties,
    @Argument(name = "pid")
    @Usage("the optional JVM process id to attach to")
    Integer pid) throws Exception {

    //
    if (Boolean.TRUE.equals(help)) {
      descriptor.printUsage(System.out);
    } else if (pid != null) {

      // Standalone
      URL url = CRaSH.class.getProtectionDomain().getCodeSource().getLocation();
      java.io.File f = new java.io.File(url.toURI());
      log.info("Attaching to remote process " + pid);
      VirtualMachine vm = VirtualMachine.attach("" + pid);

      //
      TermIOServer server = new TermIOServer(new JLineIO(), 0);
      int port = server.bind();
      log.info("Callback server set on port " + port);

      // Build the options
      StringBuilder sb = new StringBuilder();

      // Rewrite canonical path
      if (cmds != null) {
        for (String cmd : cmds) {
          File cmdPath = new File(cmd);
          if (cmdPath.exists()) {
            sb.append("--cmd ");
            Delimiter.EMPTY.escape(cmdPath.getCanonicalPath(), sb);
            sb.append(' ');
          }
        }
      }

      // Rewrite canonical path
      if (confs != null) {
        for (String conf : confs) {
          File confPath = new File(conf);
          if (confPath.exists()) {
            sb.append("--conf ");
            Delimiter.EMPTY.escape(confPath.getCanonicalPath(), sb);
            sb.append(' ');
          }
        }
      }

      // Rewrite canonical path
      if (jars != null) {
        for (String jar : jars) {
          File jarPath = new File(jar);
          if (jarPath.exists()) {
            sb.append("--jar ");
            Delimiter.EMPTY.escape(jarPath.getCanonicalPath(), sb);
            sb.append(' ');
          }
        }
      }

      // Propagate canonical config
      if (properties != null) {
        for (String property : properties) {
          sb.append("--property ");
          Delimiter.EMPTY.escape(property, sb);
          sb.append(' ');
        }
      }

      // Append callback port
      sb.append(port);

      //
      String options = sb.toString();
      log.info("Loading agent with command " + options);
      vm.loadAgent(f.getCanonicalPath(), options);

      //
      try {
        server.accept();
        while (server.execute()) {
          //
        }
      } finally {
        vm.detach();
      }
    } else {
      final Bootstrap bootstrap = new Bootstrap(Thread.currentThread().getContextClassLoader());

      //
      if (cmds != null) {
        for (String cmd : cmds) {
          File cmdPath = new File(cmd);
          bootstrap.addCmdPath(cmdPath);
        }
      }

      //
      if (confs != null) {
        for (String conf : confs) {
          File confPath = new File(conf);
          bootstrap.addCmdPath(confPath);
        }
      }

      //
      if (jars != null) {
        for (String jar : jars) {
          File jarPath = new File(jar);
          bootstrap.addJarPath(jarPath);
        }
      }

      //
      if (properties != null) {
        Properties config = new Properties();
        for (String property : properties) {
          int index = property.indexOf('=');
          if (index == -1) {
            config.setProperty(property, "");
          } else {
            config.setProperty(property.substring(0, index), property.substring(index + 1));
          }
        }
        bootstrap.setConfig(config);
      }

      // Register shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          // Should trigger some kind of run interruption
        }
      });

      // Do bootstrap
      bootstrap.bootstrap();

      // Start crash for this command line
      Term term = new BaseTerm(new JLineIO());
      org.crsh.shell.impl.CRaSH crash = new org.crsh.shell.impl.CRaSH(bootstrap.getContext());
      Processor processor = new Processor(term, crash.createSession(null));

      //
      try {
        processor.run();
      }
      finally {
        bootstrap.shutdown();
      }
    }
  }

  public static void main(String[] args) throws Exception {

    StringBuilder line = new StringBuilder();
    for (int i = 0;i < args.length;i++) {
      if (i  > 0) {
        line.append(' ');
      }
      Delimiter.EMPTY.escape(args[i], line);
    }

    //
    CRaSH main = new CRaSH();
    Matcher<CRaSH> matcher = Matcher.createMatcher("main", main.descriptor);
    CommandMatch<CRaSH, ?, ?> match = matcher.match(line.toString());
    match.invoke(new InvocationContext(), new CRaSH());
  }
}
