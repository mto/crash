import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.crsh.command.ScriptException;
import org.crsh.command.Description;
import org.crsh.command.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.PrintWriter;

@Description("Give info about a logger")
public class loginfo extends org.crsh.command.BaseCommand<Logger, Void> {

  @Argument(required=false,index=0,usage="The logger names")
  def List<String> names;

  /** All levels. */
  private static final List<String> levels = ["trace","debug","info","warn","error"];

  public void execute(CommandContext<Logger, Void> context) throws ScriptException {
    if (context.piped) {
      context.consume().each() {
        info(context.writer, it);
      }
    } else {
      names.each() {
        def logger = LoggerFactory.getLogger(it);
        info(context.writer, logger);
      }
    }
  }

  private void info(PrintWriter writer, Logger logger) {
    if (logger != null) {
      for (String level : levels) {
        if (logger[level + "Enabled"]) {
          writer.println(logger.name + "<" + level.toUpperCase() + ">");
          break;
        }
      }
    }
  }
}
