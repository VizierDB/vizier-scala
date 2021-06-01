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
#!/usr/bin/env python3
from subprocess import Popen
from os.path import exists as path_exists
import re

ui_dir = "upstream/ui"
ui_build_dir = "{}/{}".format(ui_dir, "build")
ui_target = "src/main/resources/ui"
ui_target_env     = "{}/env.js".format(ui_target)
ui_target_logo    = "{}/vizier.svg".format(ui_target)
ui_target_favicon = "{}/favicon.svg".format(ui_target)

Popen(
  ["yarn", "build"], 
  cwd="upstream/ui"
).wait()

if path_exists(ui_target):
  print("Removing old UI files")
  Popen(["rm", "-r", ui_target]).wait()

print("Installing new UI files")
Popen(["cp", "-r", ui_build_dir, ui_target]).wait()
Popen(["cp", "-r", ui_target_logo, ui_target_favicon]).wait()


with open(ui_target_env) as f:
  env = [
    re.sub("http://localhost:5000", "", line)
    for line in f.readlines()
  ]
with open(ui_target_env, "w") as f:
  f.write("".join(env))
