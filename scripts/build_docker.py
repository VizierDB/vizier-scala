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
import os
import subprocess
import sys
from datetime import date

DOCKER_DIR = "upstream/docker"

if len(sys.argv) <= 1:
  raise Exception("usage: build_docker.py {tag}")

script, tag = sys.argv

if not os.path.exists(DOCKER_DIR):
  raise Exception("Docker directory doesn't exist.  Run `sbt checkout`.")

with os.popen("which docker") as cmd:
  if len(cmd.readlines()) <= 0:
    raise Exception("Docker is not installed.  See https://docs.docker.com/engine/install/")

os.chdir(DOCKER_DIR)

base_tag, revision = tag.split(":")
latest_tag = f"{base_tag}:latest"

subprocess.run(["docker", "build", ".", "-t", tag, "--build-arg", "CACHE_DATE=" + str(date.today())], check=True)
subprocess.run(["docker", "push", tag], check=True)
subprocess.run(["docker", "tag", tag, latest_tag], check=True)
subprocess.run(["docker", "push", latest_tag], check=True)
