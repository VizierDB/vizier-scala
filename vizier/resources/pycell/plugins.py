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
"""Extensions to preload prior to executing a python cell (e.g., visualization tools)"""

from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from pycell.client import VizierDBClient

import json
import io
from bokeh.resources import Resources  # type: ignore[import]
from bokeh.io import output_notebook  # type: ignore[import]
from bokeh.io.notebook import install_notebook_hook  # type: ignore[import]
from bokeh.embed import json_item  # type: ignore[import]


target_client: Optional["VizierDBClient"] = None

bokeh_resources = Resources()


def python_cell_preload(client: "VizierDBClient"):
    global target_client
    """Convenient place to hook extension code that needs to run before a python cell"""

    # Set up Bokeh
    target_client = client
    output_notebook(notebook_type='vizier')


def python_cell_close():
    global target_client
    target_client = None


# ################### Bokeh Support ####################
# https://bokeh.pydata.org/en/latest/docs/reference.html

def vizier_bokeh_load(resources, verbose, hide_banner, load_timeout):
    """Hook called by Bokeh before the first show operation."""
    # We reset the execution environment before every call. No use
    # in doing anything specific here.
    pass


def vizier_bokeh_show(obj, state, notebook_handle):
    """Hook called by Bokeh when show() is called"""
    # r = "bokeh_plot_"+str([ random.choice(range(0, 10)) for x in range(0, 20) ])
    global target_client
    global bokeh_resources

    if(target_client is None):
        raise Exception("Internal error: Bokeh not set up properly")

    plot_object_id = "bokeh_plot_{}".format(target_client.cell_id)
    json_graph = json_item(obj, target=plot_object_id)

    target_client.show_javascript(
        # JSON is/should be valid javascript
        code="Bokeh.embed.embed_item({})".format(json.dumps(json_graph)),
        # Dump out a placeholder for the json
        html='<div id="{}"></div>'.format(plot_object_id),
        # Include dependencies based on current version of bokeh
        dependencies=bokeh_resources.js_files
    )


def vizier_bokeh_app(app, state, notebook_url, **kwargs):
    """Hook called by Bokeh when an app is started."""
    # Apps are presently unsupported.
    raise


install_notebook_hook('vizier', vizier_bokeh_load, vizier_bokeh_show, vizier_bokeh_app)


def vizier_matplotlib_render(figure):
    with io.BytesIO() as imgbytes:
        figure.savefig(imgbytes, format="svg")
        return imgbytes.getvalue().decode("utf-8")
