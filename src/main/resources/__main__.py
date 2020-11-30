import json
import sys
from util import IO_Wrapper, format_stack_trace

raw_output = sys.stdout
raw_stderr = sys.stderr
sys.stdout = IO_Wrapper("stdout", raw_output)
sys.stderr = IO_Wrapper("stderr", raw_output)


def debug(msg):
    return
    global raw_stderr
    raw_stderr.write(msg+"\n")
    raw_stderr.flush()


try:
    script = None
    artifacts = None
    while script is None:
        cmd = sys.stdin.readline()
        cmd = json.loads(cmd)
        if cmd["event"] == "script":
            script = cmd["script"]
            artifacts = cmd["artifacts"]
        else:
            print("Unknown event type '{}'".format(cmd["event"]))
    variables = {
        "vizierdb": "The Princess is in another castle"
    }
    exec(script, variables, variables)
except Exception as ex:
    if type(ex) is SyntaxError:
        context, line, pos, content = ex.args[1]
        message = "Syntax error (line {}:{})\n{}{}^-- {}".format(
            line, pos,
            content,
            " " * pos,
            ex.args[0]
        )
    elif type(ex) is NameError:
        message = "{}\n{}".format(
                        ex.args[0],
                        format_stack_trace(ex)
                    )
    else:
        message = "{}{}\n{}".format(
            type(ex).__name__, 
            ((": " + "; ".join(str(arg) for arg in ex.args)) if ex.args is not None else "" ), 
            format_stack_trace(ex)
        )
    sys.stderr.write(message)
    sys.stderr.flush()


raw_output.flush()
# exit(0)
