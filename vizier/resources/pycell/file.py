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
from typing import TYPE_CHECKING, Optional, IO, Dict
if TYPE_CHECKING:
    from pycell.client import VizierDBClient


class FileClient(object):
  def __init__(self,
               client: "VizierDBClient",
               name: str,
               mime_type: str = "text/plain",
               filename: Optional[str] = None,
               metadata: Optional[Dict[str, str]] = None,
               open_mode: str = "w"
               ):
    self.client = client
    self.name = name
    self.io: Optional[IO] = None
    self.filename = filename if filename is not None else name
    if metadata is None:
      metadata = client.vizier_request("create_file",
                                       has_response=True,
                                       name=name,
                                       mime=mime_type,
                                       properties={
                                        "filename": self.filename
                                       },
                                      )
    assert(metadata is not None)
    self.identifier = metadata["artifactId"]
    self.file_path = metadata["path"]
    self.meta_url = metadata["url"]
    self.url = metadata["url"] + "/file"
    self.open_mode = open_mode

  def __enter__(self):
    if self.io is not None:
      raise Exception("Already opened file")
    self.io = open(self.file_path, self.open_mode)

    # Add some convenience attributes
    self.io._repr_html_ = self._repr_html_
    self.io.vizier_download_url = self.url
    self.io.vizier_metadata_url = self.meta_url

    return self.io

  def __exit__(self, type, value, tb):
    import traceback
    self.io.flush()
    self.io.close()
    self.io = None
    if tb is not None:
      print(tb)
      traceback.print_tb(tb)
      raise Exception("Error while writing file")
    return self

  def _repr_html_(self) -> str:
    return "<a href='{}'>{}</a>".format(self.url, self.filename)
