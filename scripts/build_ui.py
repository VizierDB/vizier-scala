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
