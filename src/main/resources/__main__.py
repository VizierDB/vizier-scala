# -- copyright-header:v2 --
# Copyright (C) 2017-2021 University at Buffalo,
#                         New York University,
#                         Illinois Institute of Technology.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#     http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# -- copyright-header:end --
import json
import sys
from util import IO_Wrapper, format_stack_trace
from pycell.client import VizierDBClient, Artifact
from pycell.plugins import python_cell_preload

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
    cell_id = None
    while script is None:
        cmd = sys.stdin.readline()
        cmd = json.loads(cmd)
        if cmd["event"] == "script":
            script = cmd["script"]
            
            artifacts = cmd["artifacts"]
            project_id = cmd["projectId"]
            cell_id = cmd["cellId"]
        elif cmd["event"] == "ping":
            raw_output.write(json.dumps("pong") + "\n")
            raw_output.flush()
        else:
            print("Unknown event type '{}'".format(cmd["event"]))

    client = VizierDBClient(
            artifacts={
                artifact: Artifact(
                    name=artifact,
                    artifact_type=artifacts[artifact]["type"],
                    artifact_id=artifacts[artifact]["artifactId"],
                    mime_type=artifacts[artifact]["mimeType"]
                )
                for artifact in artifacts
            },
            source=script,
            project_id=project_id,
            raw_output=raw_output,
            cell_id=cell_id
        )

    python_cell_preload(client)
    print("script:",script)
    variables = {
        "vizierdb": client,
        "show": client.show,
        "open": client.pycell_open,
        **client.get_artifact_proxies()
    }
    exec(script, variables, variables)
    sys.stdout.soft_flush()
    sys.stderr.soft_flush()
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
            ((": " + "; ".join(str(arg) for arg in ex.args)) if ex.args is not None else ""),
            format_stack_trace(ex)
        )
    sys.stderr.write(message)
    sys.stderr.flush()


raw_output.flush()
# exit(0)

