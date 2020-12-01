# Copyright (C) 2017-2020 New York University,
#                         University at Buffalo,
#                         Illinois Institute of Technology.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import base64
import json

class GoogleMapClusterWrapper():
    
    def do_output(self, latlngjson, centerlat, centerlon, zoom, width, height):
        return(("""
        <div style="width:"""+str(width)+"""; height:"""+str(int(height)-30)+"""">
            <style>
              /* Always set the map height explicitly to define the size of the div
               * element that contains the map. */
              #map {
                height: 100%;
              }
              /* Optional: Makes the sample page fill the window. */
              html, body {
                height: 100%;
                margin: 0;
                padding: 0;
              }
            </style>
            <div id="map"></div>
            <img src onerror='{
                
                window.loadjscssfile = function(filename, filetype, async){
                    if (filetype=="js"){ //if filename is a external JavaScript file
                        var fileref=document.createElement("script")
                        fileref.setAttribute("type","text/javascript")
                        fileref.setAttribute("src", filename)
                        if(async){
                            fileref.setAttribute("async", "")
                            fileref.setAttribute("defer", "")
                        }
                    }
                    else if (filetype=="css"){ //if filename is an external CSS file
                        var fileref=document.createElement("link")
                        fileref.setAttribute("rel", "stylesheet")
                        fileref.setAttribute("type", "text/css")
                        fileref.setAttribute("href", filename)
                    }
                    if (typeof fileref!="undefined")
                        document.getElementsByTagName("head")[0].appendChild(fileref)
                }
                
                window.googMapAdded = false;
                
                window.initMap = function(){
                    var map = new google.maps.Map(document.getElementById("map"), {
                      center: {lat: """+str(centerlat)+""", lng: """+str(centerlon)+"""},
                      zoom: """+str(zoom)+"""
                    });
                    var json = """+latlngjson+""";
        
                    var features = [];
        
                    for (var i = 0; i < json.length; i++) {
                      var lat = json[i]["lat"];
                      var lng = json[i]["lng"]; 
        
                      features.push({
                        position: new google.maps.LatLng(lat, lng),
                      });
                    }
        
                    var markers = features.map(function(feature, i) {
                        return new google.maps.Marker({
                            position: feature.position});
                    });
        
                    /*features.forEach(function(feature) {
                      var marker1 = new google.maps.Marker({
                        position: feature.position,
                        map: map
                      });
                    });*/
                    console.log("init map")
                    
                    window.markerCluster = new MarkerClusterer(map, markers,
                    {imagePath: "https://developers.google.com/maps/documentation/javascript/examples/markerclusterer/m"});
              
                }
                
                if(!window.googMapAdded){
                  window.loadjscssfile("https://developers.google.com/maps/documentation/javascript/examples/markerclusterer/markerclusterer.js","js", false)
                  window.loadjscssfile("https://maps.googleapis.com/maps/api/js?key=AIzaSyAKc9sTF-pVezJY8-Dkuvw07v1tdYIKGHk&callback=window.initMap", "js", true)
                }
                else {
                  window.markerCluster.clearMarkers();
                  window.initMap();
                }
            }'>
          </div>
        """))

class LeafletClusterWrapper():

    def do_output(self, addrpts, centerlat, centerlon, zoom, width, height):       
        html = """<!DOCTYPE html>
        <html>
        <head>
            <title>Leaflet debug page</title>
        
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.0.3/dist/leaflet.css" integrity="sha512-07I2e+7D8p6he1SIM+1twR5TIrhUQn9+I6yjqD53JQjFiMf8EtC93ty0/5vJTZGF8aAocvHYNEDJajGdNx1IsQ==" crossorigin="" />
            <script src="https://unpkg.com/leaflet@1.0.3/dist/leaflet-src.js" integrity="sha512-WXoSHqw/t26DszhdMhOXOkI7qCiv5QWXhH9R7CgvgZMHz1ImlkVQ3uNsiQKu5wwbbxtPzFXd1hK4tzno2VqhpA==" crossorigin=""></script>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
            #map {
                width: """+str(width)+"""; 
                height: """+str(int(height)-30)+"""px; 
                border: 1px solid #ccc;
            }
        
            #progress {
                display: none;
                position: absolute;
                z-index: 1000;
                left: 400px;
                top: 300px;
                width: 200px;
                height: 20px;
                margin-top: -20px;
                margin-left: -100px;
                background-color: #fff;
                background-color: rgba(255, 255, 255, 0.7);
                border-radius: 4px;
                padding: 2px;
            }
        
            #progress-bar {
                width: 0;
                height: 100%;
                background-color: #76A6FC;
                border-radius: 4px;
            }
            </style>
        
            <link rel="stylesheet" href="https://leaflet.github.io/Leaflet.markercluster/dist/MarkerCluster.css" />
            <link rel="stylesheet" href="https://leaflet.github.io/Leaflet.markercluster/dist/MarkerCluster.Default.css" />
            <script src="https://leaflet.github.io/Leaflet.markercluster/dist/leaflet.markercluster-src.js"></script>
            <script> var addressPoints = ["""+",\n".join(addrpts)+"""]</script>
        
        </head>
        <body>
        
            <div id="map"></div>
            <script type="text/javascript">
        
                var tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 18
                    }),
                    latlng = L.latLng("""+str(centerlat)+""","""+str(centerlon)+""");
        
                var map = L.map('map', {center: latlng, zoom: """+str(zoom)+""", layers: [tiles]});
        
                var markers = L.markerClusterGroup();
                
                for (var i = 0; i < addressPoints.length; i++) {
                    var a = addressPoints[i];
                    var title = a[2]+" @ ("+a[0]+","+a[1]+")";
                    var marker = L.marker(new L.LatLng(a[0], a[1]), { title: title });
                    marker.bindPopup(title);
                    markers.addLayer(marker);
                }
        
                map.addLayer(markers);
        
            </script>
        </body>
        </html>
        """
        
        iframe_html = '<iframe src="data:text/html;base64,{html}" width="{width}" height="{height}"></iframe>'\
         .format(html = base64.b64encode(html.encode()).decode(),
                    width = width,
                    height= int(height),
                   )
            
        return(iframe_html)
        
        
    
class D3ChartWrapper():
    
    def do_output(self, data, charttype, width, height, title='', subtitle='', legendtitle='', xlabel='', ylabel=''):
        jsData =  json.dumps(data)
        html = """<!DOCTYPE html>
            <html>
            
            <head>
              <title>d3-ez : Showcase</title>
              <script src="https://s3.amazonaws.com/vizier-tmp/html2canvas.min.js"></script>
              <script src="https://s3.amazonaws.com/vizier-tmp/jspdf.min.js"></script>
              <script src="https://d3js.org/d3.v5.min.js"></script>
              <script src="https://s3.amazonaws.com/vizier-tmp/d3-ez.min.js"></script>
              <link rel="stylesheet" type="text/css" href="https://s3.amazonaws.com/vizier-tmp/d3-ez.css" />
            </head>
            
            <body>
              <div style="text-align:center;" id="chartholder"></div>
            
              <script type="text/javascript">
                var width = """+width+""";
                var height = """+height+""";
                var charttype = '"""+charttype+"""';
                var xlabel = '"""+xlabel+"""';
                var ylabel = '"""+ylabel+"""';
                var ctitle = '"""+title+"""';
                var csubtitle = '"""+subtitle+"""';
                var legendtitle = '"""+legendtitle+"""';
                
                function randomNum() {
                  return Math.floor(Math.random() * 10);
                };
                
                function downloadPDF() {
                    var input = document.getElementById('chartholder');
                    html2canvas(input)
                      .then((canvas) => {
                        var imgData = canvas.toDataURL('image/png');
                        var dwnldName = ctitle == '' || ctitle == 'None' ? charttype : ctitle;
                        var pdf = new jsPDF({
                          orientation: 'landscape',
                          unit: 'in'
                        });
                        pdf.addImage(imgData, 'JPEG', 0, 0);
                        pdf.save(dwnldName+".pdf");
                      })
                    ;
                }
                
                // Generate some sample data
                var data = """+jsData+""";
                //console.log(data);
                // Setup chart components
                var chartTypes = {};
                chartTypes.table = function(){
                    return d3.ez.component.htmlTable()
                      .width(width) 
                      .on("customSeriesClick", function(d) {
                        update(d,'bar');
                      });
                  }
                chartTypes.bar = function(){
                    return d3.ez.chart.barChartVertical()
                      .on("customValueMouseOver", function(d) {
                        console.log(d);
                      });
                  }
                chartTypes.bar_stacked = function(){
                    return d3.ez.chart.barChartStacked();
                  }
                chartTypes.bar_horizontal = function(){
                    return d3.ez.chart.barChartHorizontal();
                  }
                chartTypes.bar_circular = function(){
                    return d3.ez.chart.barChartCircular();
                  }
                chartTypes.bar_cluster = function(){
                    return d3.ez.chart.barChartClustered()
                      .on("customSeriesClick", function(d) {
                        update(d, 'bar');
                      });
                  }
                chartTypes.donut = function(){
                    return d3.ez.chart.donutChart()
                      .innerRadius(40);
                  }
                chartTypes.polar = function(){
                    return d3.ez.chart.polarAreaChart();
                  }
                
                chartTypes.heat_rad = function(){
                    return d3.ez.chart.heatMapRadial()
                      .innerRadius(5);
                  }
                chartTypes.heat_table = function(){
                    return d3.ez.chart.heatMapTable();
                  }
                chartTypes.punch = function(){
                    return d3.ez.chart.punchCard()
                      .minRadius(2)
                      .useGlobalScale(true)
                      .maxRadius(18)
                      .on("customValueMouseOver", function(d) {
                        console.log(d);
                      });
                  }
                chartTypes.bubble = function(){
                    return d3.ez.chart.bubbleChart();
                  }
                chartTypes.candle = function(){
                    return d3.ez.chart.candlestickChart();
                  }
                chartTypes.line = function(){
                    return d3.ez.chart.lineChart();
                  }
                chartTypes.radar = function(){
                    return d3.ez.chart.radarChart();
                  }
                chartTypes.rose = function(){
                    return d3.ez.chart.roseChart();
                  }
                // Functions to add charts to page and update charts
                //console.log(Object.keys(chartTypes));
                function update(data,charttype) {
                    var chartObj = charttype == 'table' ? 
                        chartTypes[charttype]() : d3.ez.base()
                            .width(width)
                            .height(height)
                            .chart(chartTypes[charttype]())
                    if(xlabel != '' && xlabel != 'None'){
                      chartObj.xAxisLabel(xlabel);
                    }
                    if(ylabel != '' && ylabel != 'None'){
                      chartObj.yAxisLabel(ylabel);
                    }
                    if(legendtitle != '' && legendtitle != 'None'){
                        var legend = d3.ez.component.legend()
                            .title(legendtitle);
                        chartObj.legend(legend)
                    }
                    if(ctitle != '' && ctitle != 'None'){
                        var title = d3.ez.component.title()
                            .mainText(ctitle)
                            .subText(csubtitle);
                        chartObj.title(title)
                    }
                    d3.select("#chartholder")
                      .datum(data)
                      .call(chartObj);
                    var elem = document.getElementById('creditTag');
                    if(elem)
                        elem.parentNode.removeChild(elem);
                    
      
                }
                update(data, charttype); 
              </script>
              <button style="float:right; margin-left: 5px; color: #fff; background-color: #5cb85c; border-color: #4cae4c; display: inline-block; margin-bottom: 0; font-weight: 400; text-align: center; vertical-align: middle; -ms-touch-action: manipulation; touch-action: manipulation; cursor: pointer; background-image: none; border: 1px solid transparent;     white-space: nowrap; padding: 6px 12px; font-size: 14px; line-height: 1.42857143; border-radius: 4px; -webkit-user-select: none;     -moz-user-select: none; -ms-user-select: none; user-select: none;" onclick="downloadPDF()"><i aria-hidden="true" class="download icon">
              </i> Download PDF</button>
            </body>
            
            </html>""" 
            
        iframe_html = '<iframe src="data:text/html;base64,{html}" width="{width}" height="{height}"></iframe>'\
         .format(html = base64.b64encode(html.encode()).decode(),
                    width = '100%',
                    height= int(height)+60,
                   )
            
        return(iframe_html)
        

class TetrisWrapper():
    def do_output(self):
        print("""
            <div style="width:480px; height:340px;">
            <style> 
              #viz {
                background-image: url('data:image/png;base64,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'); 
                background-position: bottom left; black; 
                width:480px; 
                height:320px; 
                margin: -5px 0 0 0; 
                padding: 0; 
                border: 0; 
                outline: 0;
              }
            </style>
            <div id="viz">
            </div>
            <img src onerror='{
            var Stc;
            (function (Stc) {
                // Clear resources used by platform
                // Process events and notify game
                // Render the state of the game
                // Return the current system time in milliseconds
                // Return a random positive integer number
                // Data structure that holds information about our tetromino blocks.
                var StcTetromino = (function () {
                    function StcTetromino() {
                        this.cells = [];
                        for(var k = 0; k < Game.TETROMINO_SIZE; ++k) {
                            this.cells[k] = [];
                        }
                    }
                    return StcTetromino;
                })();
                Stc.StcTetromino = StcTetromino;    
                // Datas structure for statistical data
                var StcStatics = (function () {
                    function StcStatics() {
                        this.pieces = [];
                    }
                    return StcStatics;
                })();
                Stc.StcStatics = StcStatics;    
                var Game = (function () {
                    function Game() {
                        this.m_map = [];
                        for(var k = 0; k < Game.BOARD_TILEMAP_WIDTH; ++k) {
                            this.m_map[k] = [];
                        }
                        this.m_fallingBlock = new StcTetromino();
                        this.m_nextBlock = new StcTetromino();
                    }
                    // Initialize the game. The error code (if any) is saved in [m_errorCode].
                            Game.BOARD_TILEMAP_WIDTH = 10;
                    Game.BOARD_TILEMAP_HEIGHT = 22;
                    Game.INIT_DELAY_FALL = 1000;
                    Game.SCORE_1_FILLED_ROW = 400;
                    Game.SCORE_2_FILLED_ROW = 1000;
                    Game.SCORE_3_FILLED_ROW = 3000;
                    Game.SCORE_4_FILLED_ROW = 12000;
                    Game.SCORE_MOVE_DOWN_DIVISOR = 1000;
                    Game.SCORE_DROP_DIVISOR = 20;
                    Game.SCORE_DROP_WITH_SHADOW_DIVISOR = 100;
                    Game.FILLED_ROWS_FOR_LEVEL_UP = 10;
                    Game.DELAY_FACTOR_FOR_LEVEL_UP = 9;
                    Game.DELAY_DIVISOR_FOR_LEVEL_UP = 10;
                    Game.DAS_DELAY_TIMER = 200;
                    Game.DAS_MOVE_TIMER = 40;
                    Game.ROTATION_AUTOREPEAT_DELAY = 375;
                    Game.ROTATION_AUTOREPEAT_TIMER = 200;
                    Game.ERROR_NONE = 0;
                    Game.ERROR_PLAYER_QUITS = 1;
                    Game.ERROR_NO_MEMORY = -1;
                    Game.ERROR_NO_VIDEO = -2;
                    Game.ERROR_NO_IMAGES = -3;
                    Game.ERROR_PLATFORM = -4;
                    Game.ERROR_ASSERT = -100;
                    Game.EVENT_NONE = 0;
                    Game.EVENT_MOVE_DOWN = 1;
                    Game.EVENT_MOVE_LEFT = 1 << 1;
                    Game.EVENT_MOVE_RIGHT = 1 << 2;
                    Game.EVENT_ROTATE_CW = 1 << 3;
                    Game.EVENT_ROTATE_CCW = 1 << 4;
                    Game.EVENT_DROP = 1 << 5;
                    Game.EVENT_PAUSE = 1 << 6;
                    Game.EVENT_RESTART = 1 << 7;
                    Game.EVENT_SHOW_NEXT = 1 << 8;
                    Game.EVENT_SHOW_SHADOW = 1 << 9;
                    Game.EVENT_QUIT = 1 << 10;
                    Game.TETROMINO_SIZE = 4;
                    Game.TETROMINO_TYPES = 7;
                    Game.TETROMINO_I = 0;
                    Game.TETROMINO_O = 1;
                    Game.TETROMINO_T = 2;
                    Game.TETROMINO_S = 3;
                    Game.TETROMINO_Z = 4;
                    Game.TETROMINO_J = 5;
                    Game.TETROMINO_L = 6;
                    Game.COLOR_CYAN = 1;
                    Game.COLOR_RED = 2;
                    Game.COLOR_BLUE = 3;
                    Game.COLOR_ORANGE = 4;
                    Game.COLOR_GREEN = 5;
                    Game.COLOR_YELLOW = 6;
                    Game.COLOR_PURPLE = 7;
                    Game.COLOR_WHITE = 0;
                    Game.EMPTY_CELL = -1;
                    Game.prototype.onChangeProcessed = 
                    // The platform must call this method after processing a changed state
                    function () {
                        this.m_stateChanged = false;
                    }// Return true if the game state has changed, false otherwise
                    ;
                    Game.prototype.hasChanged = function () {
                        return this.m_stateChanged;
                    }// Return true if the game state has changed, false otherwise
                    ;
                    Game.prototype.setChanged = function (changed) {
                        this.m_stateChanged = changed;
                    }// Return the cell at the specified position
                    ;
                    Game.prototype.getCell = function (column, row) {
                        return this.m_map[column][row];
                    }// Return a reference to the game statistic data
                    ;
                    Game.prototype.stats = function () {
                        return this.m_stats;
                    }// Return current falling tetromino
                    ;
                    Game.prototype.fallingBlock = function () {
                        return this.m_fallingBlock;
                    }// Return next tetromino
                    ;
                    Game.prototype.nextBlock = function () {
                        return this.m_nextBlock;
                    }// Return current error code
                    ;
                    Game.prototype.errorCode = function () {
                        return this.m_errorCode;
                    }// Return true if the game is paused, false otherwise
                    ;
                    Game.prototype.isPaused = function () {
                        return this.m_isPaused;
                    }// Return true if the game has finished, false otherwise
                    ;
                    Game.prototype.isOver = function () {
                        return this.m_isOver;
                    }// Return true if we must show preview tetromino
                    ;
                    Game.prototype.showPreview = function () {
                        return this.m_showPreview;
                    }// Return true if we must show ghost shadow
                    ;
                    Game.prototype.showShadow = function () {
                        return this.m_showShadow;
                    }// Return height gap between shadow and falling tetromino
                    ;
                    Game.prototype.shadowGap = function () {
                        return this.m_shadowGap;
                    };
                    Game.prototype.init = function (targetPlatform) {
                        // Store platform reference and start it
                        this.m_platform = targetPlatform;
                        // Initialize platform
                        this.m_errorCode = this.m_platform.init(this);
                        if(this.m_errorCode == Game.ERROR_NONE) {
                            // If everything is OK start the game
                            this.start();
                        }
                    };
                    Game.prototype.end = // Free used resources
                    function () {
                        this.m_platform.end();
                    }// Main function game called every frame
                    ;
                    Game.prototype.update = function () {
                        // Read player input
                        this.m_platform.processEvents();
                        // Update game state
                        if(this.m_isOver) {
                            if((this.m_events & Game.EVENT_RESTART) != 0) {
                                this.m_isOver = false;
                                this.start();
                            }
                        } else {
                            // Always handle restart event
                            if((this.m_events & Game.EVENT_RESTART) != 0) {
                                this.start();
                                return;
                            }
                            var currentTime = this.m_platform.getSystemTime();
                            // Process delayed autoshift
                            var timeDelta = currentTime - this.m_systemTime;
                            if(this.m_delayDown > 0) {
                                this.m_delayDown -= timeDelta;
                                if(this.m_delayDown <= 0) {
                                    this.m_delayDown = Game.DAS_MOVE_TIMER;
                                    this.m_events |= Game.EVENT_MOVE_DOWN;
                                }
                            }
                            if(this.m_delayLeft > 0) {
                                this.m_delayLeft -= timeDelta;
                                if(this.m_delayLeft <= 0) {
                                    this.m_delayLeft = Game.DAS_MOVE_TIMER;
                                    this.m_events |= Game.EVENT_MOVE_LEFT;
                                }
                            } else {
                                if(this.m_delayRight > 0) {
                                    this.m_delayRight -= timeDelta;
                                    if(this.m_delayRight <= 0) {
                                        this.m_delayRight = Game.DAS_MOVE_TIMER;
                                        this.m_events |= Game.EVENT_MOVE_RIGHT;
                                    }
                                }
                            }
                            if(this.m_delayRotation > 0) {
                                this.m_delayRotation -= timeDelta;
                                if(this.m_delayRotation <= 0) {
                                    this.m_delayRotation = Game.ROTATION_AUTOREPEAT_TIMER;
                                    this.m_events |= Game.EVENT_ROTATE_CW;
                                }
                            }
                            // Always handle pause event
                            if((this.m_events & Game.EVENT_PAUSE) != 0) {
                                this.m_isPaused = !this.m_isPaused;
                                this.m_events = Game.EVENT_NONE;
                            }
                            // Check if the game is paused
                            if(this.m_isPaused) {
                                // We achieve the effect of pausing the game
                                // adding the last frame duration to lastFallTime
                                this.m_lastFallTime += (currentTime - this.m_systemTime);
                            } else {
                                if(this.m_events != Game.EVENT_NONE) {
                                    if((this.m_events & Game.EVENT_SHOW_NEXT) != 0) {
                                        this.m_showPreview = !this.m_showPreview;
                                        this.m_stateChanged = true;
                                    }
                                    if((this.m_events & Game.EVENT_SHOW_SHADOW) != 0) {
                                        this.m_showShadow = !this.m_showShadow;
                                        this.m_stateChanged = true;
                                    }
                                    if((this.m_events & Game.EVENT_DROP) != 0) {
                                        this.dropTetromino();
                                    }
                                    if((this.m_events & Game.EVENT_ROTATE_CW) != 0) {
                                        this.rotateTetromino(true);
                                    }
                                    if((this.m_events & Game.EVENT_MOVE_RIGHT) != 0) {
                                        this.moveTetromino(1, 0);
                                    } else {
                                        if((this.m_events & Game.EVENT_MOVE_LEFT) != 0) {
                                            this.moveTetromino(-1, 0);
                                        }
                                    }
                                    if((this.m_events & Game.EVENT_MOVE_DOWN) != 0) {
                                        // Update score if the player accelerates downfall
                                        this.m_stats.score += Math.floor(Game.SCORE_2_FILLED_ROW * 
                                        (this.m_stats.level + 1) / Game.SCORE_MOVE_DOWN_DIVISOR);
                                        this.moveTetromino(0, 1);
                                    }
                                    this.m_events = Game.EVENT_NONE;
                                }
                                // Check if its time to move downwards the falling tetromino
                                if(currentTime - this.m_lastFallTime >= this.m_fallingDelay) {
                                    this.moveTetromino(0, 1);
                                    this.m_lastFallTime = currentTime;
                                }
                            }
                            // Save current time for next game update
                            this.m_systemTime = currentTime;
                        }
                        // Draw game state
                        this.m_platform.renderGame();
                    }// Process a key down event
                    ;
                    Game.prototype.onEventStart = function (command) {
                        switch(command) {
                            case Game.EVENT_QUIT: {
                                this.m_errorCode = Game.ERROR_PLAYER_QUITS;
                                break;
            
                            }
                            case Game.EVENT_MOVE_DOWN: {
                                this.m_events |= Game.EVENT_MOVE_DOWN;
                                this.m_delayDown = Game.DAS_DELAY_TIMER;
                                break;
            
                            }
                            case Game.EVENT_ROTATE_CW: {
                                this.m_events |= Game.EVENT_ROTATE_CW;
                                this.m_delayRotation = Game.ROTATION_AUTOREPEAT_DELAY;
                                break;
            
                            }
                            case Game.EVENT_MOVE_LEFT: {
                                this.m_events |= Game.EVENT_MOVE_LEFT;
                                this.m_delayLeft = Game.DAS_DELAY_TIMER;
                                break;
            
                            }
                            case Game.EVENT_MOVE_RIGHT: {
                                this.m_events |= Game.EVENT_MOVE_RIGHT;
                                this.m_delayRight = Game.DAS_DELAY_TIMER;
                                break;
            
                            }
                            case Game.EVENT_DROP:
                            case Game.EVENT_RESTART:
                            case Game.EVENT_PAUSE:
                            case Game.EVENT_SHOW_NEXT:
                            case Game.EVENT_SHOW_SHADOW: {
                                this.m_events |= command;
                                break;
            
                            }
                        }
                    }// Process a key up event
                    ;
                    Game.prototype.onEventEnd = function (command) {
                        switch(command) {
                            case Game.EVENT_MOVE_DOWN: {
                                this.m_delayDown = -1;
                                break;
            
                            }
                            case Game.EVENT_MOVE_LEFT: {
                                this.m_delayLeft = -1;
                                break;
            
                            }
                            case Game.EVENT_MOVE_RIGHT: {
                                this.m_delayRight = -1;
                                break;
            
                            }
                            case Game.EVENT_ROTATE_CW: {
                                this.m_delayRotation = -1;
                                break;
            
                            }
                        }
                    }// Set matrix elements to indicated value
                    ;
                    Game.prototype.setMatrixCells = function (matrix, width, height, value) {
                        for(var i = 0; i < width; ++i) {
                            for(var j = 0; j < height; ++j) {
                                matrix[i][j] = value;
                            }
                        }
                    }// Initialize tetromino cells for every type of tetromino
                    ;
                    Game.prototype.setTetromino = function (indexTetromino, tetromino) {
                        // Initialize tetromino cells to empty cells
                        this.setMatrixCells(tetromino.cells, Game.TETROMINO_SIZE, Game.TETROMINO_SIZE, Game.EMPTY_CELL);
                        // Almost all the blocks have size 3
                        tetromino.size = Game.TETROMINO_SIZE - 1;
                        // Initial configuration from:http://tetris.wikia.com/wiki/SRS
                        switch(indexTetromino) {
                            case Game.TETROMINO_I: {
                                tetromino.cells[0][1] = Game.COLOR_CYAN;
                                tetromino.cells[1][1] = Game.COLOR_CYAN;
                                tetromino.cells[2][1] = Game.COLOR_CYAN;
                                tetromino.cells[3][1] = Game.COLOR_CYAN;
                                tetromino.size = Game.TETROMINO_SIZE;
                                break;
            
                            }
                            case Game.TETROMINO_O: {
                                tetromino.cells[0][0] = Game.COLOR_YELLOW;
                                tetromino.cells[0][1] = Game.COLOR_YELLOW;
                                tetromino.cells[1][0] = Game.COLOR_YELLOW;
                                tetromino.cells[1][1] = Game.COLOR_YELLOW;
                                tetromino.size = Game.TETROMINO_SIZE - 2;
                                break;
            
                            }
                            case Game.TETROMINO_T: {
                                tetromino.cells[0][1] = Game.COLOR_PURPLE;
                                tetromino.cells[1][0] = Game.COLOR_PURPLE;
                                tetromino.cells[1][1] = Game.COLOR_PURPLE;
                                tetromino.cells[2][1] = Game.COLOR_PURPLE;
                                break;
            
                            }
                            case Game.TETROMINO_S: {
                                tetromino.cells[0][1] = Game.COLOR_GREEN;
                                tetromino.cells[1][0] = Game.COLOR_GREEN;
                                tetromino.cells[1][1] = Game.COLOR_GREEN;
                                tetromino.cells[2][0] = Game.COLOR_GREEN;
                                break;
            
                            }
                            case Game.TETROMINO_Z: {
                                tetromino.cells[0][0] = Game.COLOR_RED;
                                tetromino.cells[1][0] = Game.COLOR_RED;
                                tetromino.cells[1][1] = Game.COLOR_RED;
                                tetromino.cells[2][1] = Game.COLOR_RED;
                                break;
            
                            }
                            case Game.TETROMINO_J: {
                                tetromino.cells[0][0] = Game.COLOR_BLUE;
                                tetromino.cells[0][1] = Game.COLOR_BLUE;
                                tetromino.cells[1][1] = Game.COLOR_BLUE;
                                tetromino.cells[2][1] = Game.COLOR_BLUE;
                                break;
            
                            }
                            case Game.TETROMINO_L: {
                                tetromino.cells[0][1] = Game.COLOR_ORANGE;
                                tetromino.cells[1][1] = Game.COLOR_ORANGE;
                                tetromino.cells[2][0] = Game.COLOR_ORANGE;
                                tetromino.cells[2][1] = Game.COLOR_ORANGE;
                                break;
            
                            }
                        }
                        tetromino.type = indexTetromino;
                    }// Start a new game
                    ;
                    Game.prototype.start = function () {
                        // Initialize game data
                        this.m_errorCode = Game.ERROR_NONE;
                        this.m_systemTime = this.m_platform.getSystemTime();
                        this.m_lastFallTime = this.m_systemTime;
                        this.m_isOver = false;
                        this.m_isPaused = false;
                        this.m_showPreview = true;
                        this.m_events = Game.EVENT_NONE;
                        this.m_fallingDelay = Game.INIT_DELAY_FALL;
                        this.m_showShadow = true;
                        // Initialize game statistics
                        this.m_stats = new StcStatics();
                        this.m_stats.score = 0;
                        this.m_stats.lines = 0;
                        this.m_stats.totalPieces = 0;
                        this.m_stats.level = 0;
                        for(var i = 0; i < Game.TETROMINO_TYPES; ++i) {
                            this.m_stats.pieces[i] = 0;
                        }
                        // Initialize game tile map
                        this.setMatrixCells(this.m_map, Game.BOARD_TILEMAP_WIDTH, Game.BOARD_TILEMAP_HEIGHT, Game.EMPTY_CELL);
                        // Initialize falling tetromino
                        this.setTetromino(this.m_platform.random() % Game.TETROMINO_TYPES, this.m_fallingBlock);
                        this.m_fallingBlock.x = Math.floor((Game.BOARD_TILEMAP_WIDTH - this.m_fallingBlock.size) / 2);
                        this.m_fallingBlock.y = 0;
                        // Initialize preview tetromino
                        this.setTetromino(this.m_platform.random() % Game.TETROMINO_TYPES, this.m_nextBlock);
                        // Initialize events
                        this.onTetrominoMoved();
                        // Initialize delayed autoshift
                        this.m_delayLeft = -1;
                        this.m_delayRight = -1;
                        this.m_delayDown = -1;
                        this.m_delayRotation = -1;
                    }// Rotate falling tetromino. If there are no collisions when the
                    // tetromino is rotated this modifies the tetrominos cell buffer.
                    ;
                    Game.prototype.rotateTetromino = function (clockwise) {
                        var i, j;
                        var rotated = [];// temporary array to hold rotated cells
                        
                        for(var k = 0; k < Game.TETROMINO_SIZE; ++k) {
                            rotated[k] = [];
                        }
                        // If TETROMINO_O is falling return immediately
                        if(this.m_fallingBlock.type == Game.TETROMINO_O) {
                            return;// rotation doesnt require any changes
                            
                        }
                        // Initialize rotated cells to blank
                        this.setMatrixCells(rotated, Game.TETROMINO_SIZE, Game.TETROMINO_SIZE, Game.EMPTY_CELL);
                        // Copy rotated cells to the temporary array
                        for(i = 0; i < this.m_fallingBlock.size; ++i) {
                            for(j = 0; j < this.m_fallingBlock.size; ++j) {
                                if(clockwise) {
                                    rotated[this.m_fallingBlock.size - j - 1][i] = this.m_fallingBlock.cells[i][j];
                                } else {
                                    rotated[j][this.m_fallingBlock.size - i - 1] = this.m_fallingBlock.cells[i][j];
                                }
                            }
                        }
                        var wallDisplace = 0;
                        // Check collision with left wall
                        if(this.m_fallingBlock.x < 0) {
                            for(i = 0; (wallDisplace == 0) && (i < -this.m_fallingBlock.x); ++i) {
                                for(j = 0; j < this.m_fallingBlock.size; ++j) {
                                    if(rotated[i][j] != Game.EMPTY_CELL) {
                                        wallDisplace = i - this.m_fallingBlock.x;
                                        break;
                                    }
                                }
                            }
                        } else {
                            // Or check collision with right wall
                            if(this.m_fallingBlock.x > Game.BOARD_TILEMAP_WIDTH - this.m_fallingBlock.size) {
                                i = this.m_fallingBlock.size - 1;
                                for(; (wallDisplace == 0) && (i >= Game.BOARD_TILEMAP_WIDTH - this.m_fallingBlock.x); --i) {
                                    for(j = 0; j < this.m_fallingBlock.size; ++j) {
                                        if(rotated[i][j] != Game.EMPTY_CELL) {
                                            wallDisplace = -this.m_fallingBlock.x - i + Game.BOARD_TILEMAP_WIDTH - 1;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        // Check collision with board floor and other cells on board
                        for(i = 0; i < this.m_fallingBlock.size; ++i) {
                            for(j = 0; j < this.m_fallingBlock.size; ++j) {
                                if(rotated[i][j] != Game.EMPTY_CELL) {
                                    // Check collision with bottom border of the map
                                    if(this.m_fallingBlock.y + j >= Game.BOARD_TILEMAP_HEIGHT) {
                                        return;// there was collision therefore return
                                        
                                    }
                                    // Check collision with existing cells in the map
                                    if(this.m_map[i + this.m_fallingBlock.x + wallDisplace][j + this.m_fallingBlock.y] 
                                      != Game.EMPTY_CELL) {
                                        return;// there was collision therefore return
                                        
                                    }
                                }
                            }
                        }
                        // Move the falling piece if there was wall collision and its a legal move
                        if(wallDisplace != 0) {
                            this.m_fallingBlock.x += wallDisplace;
                        }
                        // There are no collisions, replace tetromino cells with rotated cells
                        for(i = 0; i < Game.TETROMINO_SIZE; ++i) {
                            for(j = 0; j < Game.TETROMINO_SIZE; ++j) {
                                this.m_fallingBlock.cells[i][j] = rotated[i][j];
                            }
                        }
                        this.onTetrominoMoved();
                    }// Check if tetromino will collide with something if it is moved in the requested direction.
                    // If there are collisions returns 1 else returns 0.
                    ;
                    Game.prototype.checkCollision = function (dx, dy) {
                        var newx = this.m_fallingBlock.x + dx;
                        var newy = this.m_fallingBlock.y + dy;
                        for(var i = 0; i < this.m_fallingBlock.size; ++i) {
                            for(var j = 0; j < this.m_fallingBlock.size; ++j) {
                                if(this.m_fallingBlock.cells[i][j] != Game.EMPTY_CELL) {
                                    // Check the tetromino would be inside the left, right and bottom borders
                                    if((newx + i < 0) || (newx + i >= Game.BOARD_TILEMAP_WIDTH) || (newy + j 
                                      >= Game.BOARD_TILEMAP_HEIGHT)) {
                                        return true;
                                    }
                                    // Check the tetromino wont collide with existing cells in the map
                                    if(this.m_map[newx + i][newy + j] != Game.EMPTY_CELL) {
                                        return true;
                                    }
                                }
                            }
                        }
                        return false;
                    }// Game scoring:http://tetris.wikia.com/wiki/Scoring
                    ;
                    Game.prototype.onFilledRows = function (filledRows) {
                        // Update total number of filled rows
                        this.m_stats.lines += filledRows;
                        // Increase score accordingly to the number of filled rows
                        switch(filledRows) {
                            case 1: {
                                this.m_stats.score += (Game.SCORE_1_FILLED_ROW * (this.m_stats.level + 1));
                                break;
            
                            }
                            case 2: {
                                this.m_stats.score += (Game.SCORE_2_FILLED_ROW * (this.m_stats.level + 1));
                                break;
            
                            }
                            case 3: {
                                this.m_stats.score += (Game.SCORE_3_FILLED_ROW * (this.m_stats.level + 1));
                                break;
            
                            }
                            case 4: {
                                this.m_stats.score += (Game.SCORE_4_FILLED_ROW * (this.m_stats.level + 1));
                                break;
            
                            }
                            default: {
                                // This shouldnt happen, but if happens kill the game
                                this.m_errorCode = Game.ERROR_ASSERT;
            
                            }
                        }
                        // Check if we need to update the level
                        if(this.m_stats.lines >= Game.FILLED_ROWS_FOR_LEVEL_UP * (this.m_stats.level + 1)) {
                            this.m_stats.level++;
                            // Increase speed for falling tetrominoes
                            this.m_fallingDelay = Math.floor(Game.DELAY_FACTOR_FOR_LEVEL_UP * 
                            this.m_fallingDelay / Game.DELAY_DIVISOR_FOR_LEVEL_UP);
                        }
                    }// Move tetromino in the direction specified by (x, y) (in tile units)
                    // This function detects if there are filled rows or if the move
                    // lands a falling tetromino, also checks for game over condition.
                    ;
                    Game.prototype.moveTetromino = function (x, y) {
                        var i, j;
                        // Check if the move would create a collision
                        if(this.checkCollision(x, y)) {
                            // In case of collision check if move was downwards (y == 1)
                            if(y == 1) {
                                // Check if collision occurs when the falling
                                // tetromino is on the 1st or 2nd row
                                if(this.m_fallingBlock.y <= 1) {
                                    this.m_isOver = true// if this happens the game is over
                                    ;
                                } else {
                                    // The falling tetromino has reached the bottom,
                                    // so we copy their cells to the board map
                                    for(i = 0; i < this.m_fallingBlock.size; ++i) {
                                        for(j = 0; j < this.m_fallingBlock.size; ++j) {
                                            if(this.m_fallingBlock.cells[i][j] != Game.EMPTY_CELL) {
                                                this.m_map[this.m_fallingBlock.x + i][this.m_fallingBlock.y + j] = 
                                                this.m_fallingBlock.cells[i][j];
                                            }
                                        }
                                    }
                                    // Check if the landing tetromino has created full rows
                                    var numFilledRows = 0;
                                    for(j = 1; j < Game.BOARD_TILEMAP_HEIGHT; ++j) {
                                        var hasFullRow = true;
                                        for(i = 0; i < Game.BOARD_TILEMAP_WIDTH; ++i) {
                                            if(this.m_map[i][j] == Game.EMPTY_CELL) {
                                                hasFullRow = false;
                                                break;
                                            }
                                        }
                                        // If we found a full row we need to remove that row from the map
                                        // we do that by just moving all the above rows one row below
                                        if(hasFullRow) {
                                            for(x = 0; x < Game.BOARD_TILEMAP_WIDTH; ++x) {
                                                for(y = j; y > 0; --y) {
                                                    this.m_map[x][y] = this.m_map[x][y - 1];
                                                }
                                            }
                                            numFilledRows++// increase filled row counter
                                            ;
                                        }
                                    }
                                    // Update game statistics
                                    if(numFilledRows > 0) {
                                        this.onFilledRows(numFilledRows);
                                    }
                                    this.m_stats.totalPieces++;
                                    this.m_stats.pieces[this.m_fallingBlock.type]++;
                                    // Use preview tetromino as falling tetromino.
                                    // Copy preview tetromino for falling tetromino
                                    for(i = 0; i < Game.TETROMINO_SIZE; ++i) {
                                        for(j = 0; j < Game.TETROMINO_SIZE; ++j) {
                                            this.m_fallingBlock.cells[i][j] = this.m_nextBlock.cells[i][j];
                                        }
                                    }
                                    this.m_fallingBlock.size = this.m_nextBlock.size;
                                    this.m_fallingBlock.type = this.m_nextBlock.type;
                                    // Reset position
                                    this.m_fallingBlock.y = 0;
                                    this.m_fallingBlock.x = Math.floor((Game.BOARD_TILEMAP_WIDTH - this.m_fallingBlock.size) / 2);
                                    this.onTetrominoMoved();
                                    // Create next preview tetromino
                                    this.setTetromino(this.m_platform.random() % Game.TETROMINO_TYPES, this.m_nextBlock);
                                }
                            }
                        } else {
                            // There are no collisions, just move the tetromino
                            this.m_fallingBlock.x += x;
                            this.m_fallingBlock.y += y;
                        }
                        this.onTetrominoMoved();
                    }// Hard drop
                    ;
                    Game.prototype.dropTetromino = function () {
                        // Shadow has already calculated the landing position.
                        this.m_fallingBlock.y += this.m_shadowGap;
                        // Force lock.
                        this.moveTetromino(0, 1);
                        // Update score
                        if(this.m_showShadow) {
                            this.m_stats.score += Math.floor(Game.SCORE_2_FILLED_ROW * 
                            (this.m_stats.level + 1) / Game.SCORE_DROP_WITH_SHADOW_DIVISOR);
                        } else {
                            this.m_stats.score += Math.floor(Game.SCORE_2_FILLED_ROW * 
                            (this.m_stats.level + 1) / Game.SCORE_DROP_DIVISOR);
                        }
                    }// This event is called when the falling tetromino is moved
                    ;
                    Game.prototype.onTetrominoMoved = function () {
                        var y = 0;
                        // Calculate number of cells where shadow tetromino would be
                        while(!this.checkCollision(0, ++y)) {
                            ; ;
                        }
                        this.m_shadowGap = y - 1;
                        this.m_stateChanged = true;
                    }// Game events are stored in bits in this variable.
                    // It must be cleared to EVENT_NONE after being used.
                    ;
                    return Game;
                })();
                Stc.Game = Game;    
                var PlatformHTML5 = (function () {
                    function PlatformHTML5(image) {
                        this.m_image = image;
                        // Create background layer.
                        var canvasBack = document.createElement("canvas");
                        canvasBack.width = 480;
                        canvasBack.height = 320;
                        canvasBack.style.position = "absolute";
                        canvasBack.style.left = "0";
                        canvasBack.style.top = "0";
                        canvasBack.style.zIndex = "0";
                        document.getElementById("viz").appendChild(canvasBack);
                        // Draw background
                        var context = canvasBack.getContext("2d");
                        context.drawImage(this.m_image, 0, PlatformHTML5.TEXTURE_SIZE - 
                        PlatformHTML5.SCREEN_HEIGHT, PlatformHTML5.SCREEN_WIDTH, PlatformHTML5.SCREEN_HEIGHT, 
                        0, 0, PlatformHTML5.SCREEN_WIDTH, PlatformHTML5.SCREEN_HEIGHT);
                        // Create stats layer.
                        var canvasStats = document.createElement("canvas");
                        canvasStats.width = 480;
                        canvasStats.height = 320;
                        canvasStats.style.position = "absolute";
                        canvasStats.style.left = "0";
                        canvasStats.style.top = "0";
                        canvasStats.style.zIndex = "1";
                        document.getElementById("viz").appendChild(canvasStats);
                        this.m_canvasStats = canvasStats.getContext("2d");
                        // Create game layer.
                        var canvas = document.createElement("canvas");
                        canvas.width = 480;
                        canvas.height = 320;
                        canvas.style.position = "absolute";
                        canvas.style.left = "0";
                        canvas.style.top = "0";
                        canvas.style.zIndex = "2";
                        document.getElementById("viz").appendChild(canvas);
                        this.m_canvas = canvas.getContext("2d");
                        // Register events.
                        var myself = this;
            function handlerKeyDown(event) {
                            myself.onKeyDown(event);
                        }
                        window.addEventListener("keydown", handlerKeyDown, false);
            function handlerKeyUp(event) {
                            myself.onKeyUp(event);
                        }
                        window.addEventListener("keyup", handlerKeyUp, false);
            function handlerTouchDown(event) {
                            myself.onTouchStart(event);
                        }
                        canvas.onmousedown = handlerTouchDown;
            function handlerTouchEnd(event) {
                            myself.onTouchEnd(event);
                        }
                        canvas.onmouseup = handlerTouchEnd;
                    }
                    PlatformHTML5.SCREEN_WIDTH = 480;
                    PlatformHTML5.SCREEN_HEIGHT = 320;
                    PlatformHTML5.TILE_SIZE = 12;
                    PlatformHTML5.BOARD_X = 180;
                    PlatformHTML5.BOARD_Y = 28;
                    PlatformHTML5.PREVIEW_X = 112;
                    PlatformHTML5.PREVIEW_Y = 232;
                    PlatformHTML5.SCORE_X = 72;
                    PlatformHTML5.SCORE_Y = 86;
                    PlatformHTML5.SCORE_LENGTH = 10;
                    PlatformHTML5.LINES_X = 108;
                    PlatformHTML5.LINES_Y = 68;
                    PlatformHTML5.LINES_LENGTH = 5;
                    PlatformHTML5.LEVEL_X = 108;
                    PlatformHTML5.LEVEL_Y = 50;
                    PlatformHTML5.LEVEL_LENGTH = 5;
                    PlatformHTML5.TETROMINO_X = 425;
                    PlatformHTML5.TETROMINO_L_Y = 79;
                    PlatformHTML5.TETROMINO_I_Y = 102;
                    PlatformHTML5.TETROMINO_T_Y = 126;
                    PlatformHTML5.TETROMINO_S_Y = 150;
                    PlatformHTML5.TETROMINO_Z_Y = 174;
                    PlatformHTML5.TETROMINO_O_Y = 198;
                    PlatformHTML5.TETROMINO_J_Y = 222;
                    PlatformHTML5.TETROMINO_LENGTH = 5;
                    PlatformHTML5.PIECES_X = 418;
                    PlatformHTML5.PIECES_Y = 246;
                    PlatformHTML5.PIECES_LENGTH = 6;
                    PlatformHTML5.NUMBER_WIDTH = 7;
                    PlatformHTML5.NUMBER_HEIGHT = 9;
                    PlatformHTML5.TEXTURE_SIZE = 512;
                    PlatformHTML5.FPS = 35;
                    PlatformHTML5.TY_1 = 50;
                    PlatformHTML5.TY_2 = 270;
                    PlatformHTML5.TY_DOWN = 70;
                    PlatformHTML5.TY_DROP = 250;
                    PlatformHTML5.TX_1 = 160;
                    PlatformHTML5.TX_2 = 320;
                    PlatformHTML5.KEY_A = 65;
                    PlatformHTML5.KEY_W = 87;
                    PlatformHTML5.KEY_S = 83;
                    PlatformHTML5.KEY_D = 68;
                    PlatformHTML5.KEY_SPACE = 32;
                    PlatformHTML5.KEY_LEFT = 37;
                    PlatformHTML5.KEY_RIGHT = 39;
                    PlatformHTML5.KEY_UP = 38;
                    PlatformHTML5.KEY_DOWN = 40;
                    PlatformHTML5.prototype.showOverlay = function (text) {
                        this.m_canvas.globalAlpha = 0.4;
                        this.m_canvas.fillStyle = "rgb(0, 0, 0)";
                        this.m_canvas.fillRect(0, 0, PlatformHTML5.SCREEN_WIDTH, PlatformHTML5.SCREEN_HEIGHT);
                        this.m_canvas.globalAlpha = 1;
                        this.m_canvas.fillStyle = "white";
                        this.m_canvas.font = "20px monospace";
                        var textWidth = this.m_canvas.measureText(text).width;
                        this.m_canvas.fillText(text, (PlatformHTML5.SCREEN_WIDTH - textWidth) / 2, 
                        PlatformHTML5.SCREEN_HEIGHT / 2);
                    };
                    PlatformHTML5.prototype.onTouchStart = function (event) {
                        var tx = event.layerX;
                        var ty = event.layerY;
                        if(tx < PlatformHTML5.TX_1) {
                            if(ty < PlatformHTML5.TY_1) {
                                this.m_game.onEventStart(Game.EVENT_RESTART);
                            } else {
                                if(ty < PlatformHTML5.TY_2) {
                                    this.m_game.onEventStart(Game.EVENT_MOVE_LEFT);
                                } else {
                                    this.m_game.onEventStart(Game.EVENT_SHOW_NEXT);
                                }
                            }
                        } else {
                            if(tx < PlatformHTML5.TX_2) {
                                if(ty > PlatformHTML5.TY_DROP) {
                                    this.m_game.onEventStart(Game.EVENT_DROP);
                                } else {
                                    if(ty > PlatformHTML5.TY_DOWN) {
                                        this.m_game.onEventStart(Game.EVENT_MOVE_DOWN);
                                    } else {
                                        this.m_game.onEventStart(Game.EVENT_ROTATE_CW);
                                    }
                                }
                            } else {
                                if(ty < PlatformHTML5.TY_1) {
                                    if(!this.m_game.isOver()) {
                                        if(!this.m_game.isPaused()) {
                                            this.showOverlay("Game is paused");
                                        } else {
                                            // Force redraw.
                                            this.m_game.setChanged(true);
                                            this.renderGame();
                                        }
                                        this.m_game.onEventStart(Game.EVENT_PAUSE);
                                    }
                                } else {
                                    if(ty < PlatformHTML5.TY_2) {
                                        this.m_game.onEventStart(Game.EVENT_MOVE_RIGHT);
                                    } else {
                                        this.m_game.onEventStart(Game.EVENT_SHOW_SHADOW);
                                    }
                                }
                            }
                        }
                        console.info("-- touchStart:" + tx + " " + ty);
                    };
                    PlatformHTML5.prototype.onTouchEnd = function (event) {
                        this.m_game.onEventEnd(Game.EVENT_MOVE_LEFT);
                        this.m_game.onEventEnd(Game.EVENT_MOVE_RIGHT);
                        this.m_game.onEventEnd(Game.EVENT_MOVE_DOWN);
                        this.m_game.onEventEnd(Game.EVENT_ROTATE_CW);
                    };
                    PlatformHTML5.prototype.onKeyDown = function (event) {
                        var key = (event.which) ? event.which : event.keyCode;
                        switch(key) {
                            case PlatformHTML5.KEY_A:
                            case PlatformHTML5.KEY_LEFT: {
                                this.m_game.onEventStart(Game.EVENT_MOVE_LEFT);
                                break;
            
                            }
                            case PlatformHTML5.KEY_D:
                            case PlatformHTML5.KEY_RIGHT: {
                                this.m_game.onEventStart(Game.EVENT_MOVE_RIGHT);
                                break;
            
                            }
                            case PlatformHTML5.KEY_W:
                            case PlatformHTML5.KEY_UP: {
                                this.m_game.onEventStart(Game.EVENT_ROTATE_CW);
                                break;
            
                            }
                            case PlatformHTML5.KEY_S:
                            case PlatformHTML5.KEY_DOWN: {
                                this.m_game.onEventStart(Game.EVENT_MOVE_DOWN);
                                break;
            
                            }
                            case PlatformHTML5.KEY_SPACE: {
                                this.m_game.onEventStart(Game.EVENT_DROP);
                                break;
            
                            }
                        }
                    };
                    PlatformHTML5.prototype.onKeyUp = function (event) {
                        var key = (event.which) ? event.which : event.keyCode;
                        switch(key) {
                            case PlatformHTML5.KEY_LEFT: {
                                this.m_game.onEventEnd(Game.EVENT_MOVE_LEFT);
                                break;
            
                            }
                            case PlatformHTML5.KEY_RIGHT: {
                                this.m_game.onEventEnd(Game.EVENT_MOVE_RIGHT);
                                break;
            
                            }
                            case PlatformHTML5.KEY_UP: {
                                this.m_game.onEventEnd(Game.EVENT_ROTATE_CW);
                                break;
            
                            }
                            case PlatformHTML5.KEY_DOWN: {
                                this.m_game.onEventEnd(Game.EVENT_MOVE_DOWN);
                                break;
            
                            }
                        }
                    }// Initializes platform
                    ;
                    PlatformHTML5.prototype.init = function (game) {
                        this.m_game = game;
                        return Game.ERROR_NONE;
                    }// Clear resources used by platform
                    ;
                    PlatformHTML5.prototype.end = function () {
                        this.m_game = null;
                    }// Process events and notify game
                    ;
                    PlatformHTML5.prototype.processEvents = function () {
                        // Events are handled by document handlers, nothing to do here.
                                }// Render the state of the game
                    ;
                    PlatformHTML5.prototype.renderGame = function () {
                        var i, j;
                        // Check if the game state has changed, if so redraw
                        if(this.m_game.hasChanged()) {
                            // Clear canvas.
                            this.m_canvas.clearRect(0, 0, PlatformHTML5.SCREEN_WIDTH, PlatformHTML5.SCREEN_HEIGHT);
                            // Draw preview block
                            if(this.m_game.showPreview()) {
                                for(i = 0; i < Game.TETROMINO_SIZE; ++i) {
                                    for(j = 0; j < Game.TETROMINO_SIZE; ++j) {
                                        if(this.m_game.nextBlock().cells[i][j] != Game.EMPTY_CELL) {
                                            this.drawTile(PlatformHTML5.PREVIEW_X + (PlatformHTML5.TILE_SIZE * i), 
                                            PlatformHTML5.PREVIEW_Y + (PlatformHTML5.TILE_SIZE * j), 
                                            this.m_game.nextBlock().cells[i][j], false);
                                        }
                                    }
                                }
                            }
                            // Draw shadow tetromino
                            if(this.m_game.showShadow() && this.m_game.shadowGap() > 0) {
                                for(i = 0; i < Game.TETROMINO_SIZE; ++i) {
                                    for(j = 0; j < Game.TETROMINO_SIZE; ++j) {
                                        if(this.m_game.fallingBlock().cells[i][j] != Game.EMPTY_CELL) {
                                            this.drawTile(PlatformHTML5.BOARD_X + (PlatformHTML5.TILE_SIZE * 
                                            (this.m_game.fallingBlock().x + i)), PlatformHTML5.BOARD_Y + 
                                            (PlatformHTML5.TILE_SIZE * 
                                            (this.m_game.fallingBlock().y + this.m_game.shadowGap() + j)), 
                                            this.m_game.fallingBlock().cells[i][j], true);
                                        }
                                    }
                                }
                            }
                            // Draw the cells in the board
                            for(i = 0; i < Game.BOARD_TILEMAP_WIDTH; ++i) {
                                for(j = 0; j < Game.BOARD_TILEMAP_HEIGHT; ++j) {
                                    if(this.m_game.getCell(i, j) != Game.EMPTY_CELL) {
                                        this.drawTile(PlatformHTML5.BOARD_X + (PlatformHTML5.TILE_SIZE * i), 
                                        PlatformHTML5.BOARD_Y + 
                                        (PlatformHTML5.TILE_SIZE * j), this.m_game.getCell(i, j), false);
                                    }
                                }
                            }
                            // Draw falling tetromino
                            for(i = 0; i < Game.TETROMINO_SIZE; ++i) {
                                for(j = 0; j < Game.TETROMINO_SIZE; ++j) {
                                    if(this.m_game.fallingBlock().cells[i][j] != Game.EMPTY_CELL) {
                                        this.drawTile(PlatformHTML5.BOARD_X + (PlatformHTML5.TILE_SIZE * 
                                        (this.m_game.fallingBlock().x + i)), 
                                        PlatformHTML5.BOARD_Y + (PlatformHTML5.TILE_SIZE * (this.m_game.fallingBlock().y + j)), 
                                        this.m_game.fallingBlock().cells[i][j], false);
                                    }
                                }
                            }
                            // Draw game statistic data
                            if(!this.m_game.isPaused()) {
                                // Clear stats canvas.
                                this.m_canvasStats.clearRect(0, 0, PlatformHTML5.SCREEN_WIDTH, PlatformHTML5.SCREEN_HEIGHT);
                                this.drawNumber(PlatformHTML5.LEVEL_X, PlatformHTML5.LEVEL_Y, this.m_game.stats().level, 
                                PlatformHTML5.LEVEL_LENGTH, Game.COLOR_WHITE);
                                this.drawNumber(PlatformHTML5.LINES_X, PlatformHTML5.LINES_Y, this.m_game.stats().lines, 
                                PlatformHTML5.LINES_LENGTH, Game.COLOR_WHITE);
                                this.drawNumber(PlatformHTML5.SCORE_X, PlatformHTML5.SCORE_Y, this.m_game.stats().score, 
                                PlatformHTML5.SCORE_LENGTH, Game.COLOR_WHITE);
                                this.drawNumber(PlatformHTML5.TETROMINO_X, PlatformHTML5.TETROMINO_L_Y, this.m_game.stats()
                                .pieces[Game.TETROMINO_L], PlatformHTML5.TETROMINO_LENGTH, Game.COLOR_ORANGE);
                                this.drawNumber(PlatformHTML5.TETROMINO_X, PlatformHTML5.TETROMINO_I_Y, this.m_game.stats()
                                .pieces[Game.TETROMINO_I], PlatformHTML5.TETROMINO_LENGTH, Game.COLOR_CYAN);
                                this.drawNumber(PlatformHTML5.TETROMINO_X, PlatformHTML5.TETROMINO_T_Y, this.m_game.stats()
                                .pieces[Game.TETROMINO_T], PlatformHTML5.TETROMINO_LENGTH, Game.COLOR_PURPLE);
                                this.drawNumber(PlatformHTML5.TETROMINO_X, PlatformHTML5.TETROMINO_S_Y, this.m_game.stats()
                                .pieces[Game.TETROMINO_S], PlatformHTML5.TETROMINO_LENGTH, Game.COLOR_GREEN);
                                this.drawNumber(PlatformHTML5.TETROMINO_X, PlatformHTML5.TETROMINO_Z_Y, this.m_game.stats()
                                .pieces[Game.TETROMINO_Z], PlatformHTML5.TETROMINO_LENGTH, Game.COLOR_RED);
                                this.drawNumber(PlatformHTML5.TETROMINO_X, PlatformHTML5.TETROMINO_O_Y, this.m_game.stats()
                                .pieces[Game.TETROMINO_O], PlatformHTML5.TETROMINO_LENGTH, Game.COLOR_YELLOW);
                                this.drawNumber(PlatformHTML5.TETROMINO_X, PlatformHTML5.TETROMINO_J_Y, this.m_game.stats()
                                .pieces[Game.TETROMINO_J], PlatformHTML5.TETROMINO_LENGTH, Game.COLOR_BLUE);
                                this.drawNumber(PlatformHTML5.PIECES_X, PlatformHTML5.PIECES_Y, this.m_game.stats().totalPieces, 
                                PlatformHTML5.PIECES_LENGTH, Game.COLOR_WHITE);
                            }
                            if(this.m_game.isOver()) {
                                this.showOverlay("Game is over");
                            }
                            // Inform the game that we are done with the changed state
                            this.m_game.onChangeProcessed();
                        }
                    }// Return the current system time in milliseconds
                    ;
                    PlatformHTML5.prototype.getSystemTime = function () {
                        return Date.now();
                    }// Return a random positive integer number
                    ;
                    PlatformHTML5.prototype.random = function () {
                        // JavaScript maximum integer number is 2^53 = 9007199254740992.
                        return Math.floor(9007199254740992 * Math.random());
                    };
                    PlatformHTML5.prototype.drawTile = function (x, y, tile, shadow) {
                        this.m_canvas.drawImage(this.m_image, PlatformHTML5.TILE_SIZE * (shadow ? Game.TETROMINO_TYPES + 
                        tile + 1 : tile), 0, PlatformHTML5.TILE_SIZE, PlatformHTML5.TILE_SIZE, x, y, 
                        PlatformHTML5.TILE_SIZE, PlatformHTML5.TILE_SIZE);
                    };
                    PlatformHTML5.prototype.drawNumber = function (x, y, value, length, color) {
                        var pos = 0;
                        do {
                            this.m_canvasStats.drawImage(this.m_image, PlatformHTML5.NUMBER_WIDTH * (value % 10), 1 + 
                            PlatformHTML5.TILE_SIZE + PlatformHTML5.NUMBER_HEIGHT * color, PlatformHTML5.NUMBER_WIDTH, 
                            PlatformHTML5.NUMBER_HEIGHT, x + PlatformHTML5.NUMBER_WIDTH * (length - pos), y, 
                            PlatformHTML5.NUMBER_WIDTH, PlatformHTML5.NUMBER_HEIGHT);
                            value = Math.floor(value / 10);
                        }while(++pos < length)
                    };
                    return PlatformHTML5;
                })();
                Stc.PlatformHTML5 = PlatformHTML5;    
            })(Stc || (Stc = {}));
            
            //------------------------------------------------
            var image = document.getElementById("stc_sprites");
            var platform = new Stc.PlatformHTML5(image);
            var game = new Stc.Game();
            game.init(platform);
            function update() {
                game.update();
            }
            setInterval(update, 1000 / Stc.PlatformHTML5.FPS);
            }'/>
            <div style="position: relative;">
            <img id="stc_sprites" src="data:image/png;base64,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" width="0" height="0"/>
            </div>
            
            </div>
            """)
