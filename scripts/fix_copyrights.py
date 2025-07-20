# -- copyright-header:v4 --
# Copyright (C) 2017-2025 University at Buffalo,
#                         New York University,
#                         Illinois Institute of Technology,
#                         Breadcrumb Analytics.
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

from glob import glob
import re

LICENSE_VERSION = 4

with open("LICENSE.txt") as f:
  LICENSE = [
    line if line.endswith("\n") else line + "\n"
    for line in f.readlines()
  ]


def strip_old_licenses(lines):
  i = 0
  mode = 1
  while(i < len(lines)):
    if mode == 1:
      if(re.search("-- copyright-header:v[0-9]+ --", lines[i])):
        mode = 2
        del(lines[i])
      else:
        i += 1
    elif mode == 2:
      if(re.search("-- copyright-header:end --", lines[i])):
        mode = 1
      del(lines[i])


def fix_license_if_needed(path: str, license, skip_check=False):
  # skip upstream repositories
  if path.startswith("./upstream"):
    return
  with open(path) as f:
    lines = f.readlines()
  if len(lines) > 0:
    if skip_check or (lines[0] != license[0]):
      print("FIXING {}".format(path))
      strip_old_licenses(lines)
      lines = license + lines
      with open(path, "w") as f:
        f.write("".join(lines))


# ##################################
# ###### Fix Python Licenses #######
# ##################################

PYTHON_LICENSE = [
  "# -- copyright-header:v{} --\n".format(LICENSE_VERSION)
] + list(("# " + line) for line in LICENSE) + [
  "# -- copyright-header:end --\n"
]

for path in glob("./**/*.py", recursive=True):
  fix_license_if_needed(path, PYTHON_LICENSE)

# #################################
# ###### Fix Scala Licenses #######
# #################################

SCALA_LICENSE = [
  "/* -- copyright-header:v{} --\n".format(LICENSE_VERSION)
] + list((" * " + line) for line in LICENSE) + [
  " * -- copyright-header:end -- */\n"
]

for path in glob("./**/*.scala", recursive=True):
  fix_license_if_needed(path, SCALA_LICENSE)
