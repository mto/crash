package org.crsh.cmdline.matcher.impl;

import org.crsh.cmdline.CommandCompletion;
import org.crsh.cmdline.Delimiter;
import org.crsh.cmdline.completers.EmptyCompleter;
import org.crsh.cmdline.ParameterDescriptor;
import org.crsh.cmdline.matcher.CmdCompletionException;
import org.crsh.cmdline.spi.Completer;
import org.crsh.cmdline.spi.ValueCompletion;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 */
class ParameterCompletion extends Completion {

  /** . */
  private final String prefix;

  /** . */
  private final Delimiter delimiter;

  /** . */
  private final ParameterDescriptor<?> parameter;

  /** . */
  private final Completer completer;

  ParameterCompletion(String prefix, Delimiter delimiter, ParameterDescriptor<?> parameter, Completer completer) {
    this.prefix = prefix;
    this.delimiter = delimiter;
    this.parameter = parameter;
    this.completer = completer;
  }

  CommandCompletion complete() throws CmdCompletionException {

    Class<? extends Completer> completerType = parameter.getCompleterType();
    Completer completer = this.completer;

    // Use the most adapted completer
    if (completerType != EmptyCompleter.class) {
      try {
        completer = completerType.newInstance();
      }
      catch (Exception e) {
        throw new CmdCompletionException(e);
      }
    }

    //
    if (completer != null) {
      try {
        return new CommandCompletion(delimiter, completer.complete(parameter, prefix));
      }
      catch (Exception e) {
        throw new CmdCompletionException(e);
      }
    } else {
      return new CommandCompletion(delimiter, ValueCompletion.create());
    }
  }
}
