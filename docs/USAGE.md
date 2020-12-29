## Running Vizier

Make sure the vizier bootstrap is in your path and then run

```
$> vizier
```

Navigate your browser of choice to http://localhost:5000 and you're good to go


#### Configuration Options

```
$> vizier --help
  -g, --google-api-key  <arg>   Your Google API Key (for Geocoding)
  -o, --osm-server  <arg>       Your Open Street Maps server (for Geocoding)
      --project  <arg>          Path to the project (e.g., vizier.db)
  -p, --python  <arg>           Path to python binary
```

The python path may be relative to your current path (e.g., `python3` works).

Vizier will also look for configuration options at: `~/.vizierdb` or `~/.config/vizierdb.conf` in standard Java Properties format.  For example:
```
google-api-key=[MY KEY]
python=python3.8
```
