import json
import sys
from util import IO_Wrapper, format_stack_trace
from pycell.client import VizierDBClient, Artifact

raw_output = sys.stdout
raw_stderr = sys.stderr
sys.stdout = IO_Wrapper("stdout", raw_output)
sys.stderr = IO_Wrapper("stderr", raw_output)


def debug(msg):
    return
    global raw_stderr
    raw_stderr.write(msg + "\n")
    raw_stderr.flush()


try:
    script = None
    artifacts = {}
    project_id = None
    while script is None:
        cmd = sys.stdin.readline()
        cmd = json.loads(cmd)
        if cmd["event"] == "script":
            script = cmd["script"]
            artifacts = cmd["artifacts"]
            project_id = cmd["project_id"]
        else:
            print("Unknown event type '{}'".format(cmd["event"]))
    client = VizierDBClient(
            artifacts={
                artifact: Artifact(
                    name=artifact,
                    artifact_type=artifacts[artifact]["type"],
                    name_in_backend=artifacts[artifact]["nameInBackend"],
                    file=artifacts[artifact]["file"],
                    artifact_id=artifacts[artifact]["artifactId"]
                )
                for artifact in artifacts
            },
            source=script,
            project_id=project_id
        )
    variables = {
        "vizierdb": client,
        "show": client.show,
        "open": client.pycell_open
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
