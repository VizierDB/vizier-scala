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
from io import IOBase
import json
import traceback
import itertools


class IO_Wrapper(IOBase):
  def __init__(self, stream, io):
    self.stream = stream
    self.io = io
    self.buffer = ""

  def write(self, b):
    self.buffer += b
    if "\n" in self.buffer:
      self.flush()

  def soft_flush(self):
    if len(self.buffer) > 0:
      self.io.write(json.dumps({
        "event": "message",
        "stream": self.stream,
        "content": self.buffer
      }) + "\n")
      self.buffer = ""

  def flush(self):
    self.soft_flush()
    self.io.flush()

  def print(self, b: str):
    if not b.endswith("\n"):
      b = b + "\n"
    self.write(b)


def debug_is_on():
    return False


def format_stack_trace(ex, offset_lines=0):
    trace_frames = traceback.extract_tb(ex.__traceback__, limit=30)
    if not debug_is_on():
        trace_frames = itertools.dropwhile(
                          lambda x: x[0] != "<string>",
                          trace_frames
                       )
    trace_text = list([
        "{} {} line {}{}".format(
          # Function Name
          "<Python Cell>" if element[2] == "<module>" else (element[2] + "(...)"),

          # File
          "on" if element[0] == "<string>" else ("in " + element[0] + ", "),

          # Line #
          element[1] + (offset_lines if element[0] == "<string>" else 0),

          # Line Content
          ("\n    " + element[3]) if element[3] != "" else ""
        )
        for element in trace_frames
    ])
    if len(trace_text) > 0:
        trace_text.reverse()
        trace_text = (
            [
              ("  ... caused by " + trace_text[0])
            ] + [
              ("  ... called by " + line)
              for line in trace_text[1:]
            ]
        )
    else:
        return "INTERNAL ERROR\n{}".format(ex)
    return "{}".format("\n".join(trace_text))

