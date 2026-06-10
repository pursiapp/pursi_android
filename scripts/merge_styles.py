"""
Merge OpenFreeMap Liberty style with k-yle seamark style
into a single style JSON for Pursi marine navigation app.

Usage: python3 scripts/merge_styles.py
"""
import json

OFM_PATH = '/tmp/ofm_liberty.json'

# Load OFM Liberty style
with open(OFM_PATH) as f:
    ofm = json.load(f)

# Build merged style
merged = {
    "version": 8,
    "name": "Pursi Marine Vector",
    "sources": {
        "openmaptiles": {
            "type": "vector",
            "url": "https://tiles.openfreemap.org/planet"
        },
        "ne2_shaded": {
            "maxzoom": 6,
            "tileSize": 256,
            "tiles": [
                "https://tiles.openfreemap.org/natural_earth/ne2sr/{z}/{x}/{y}.png"
            ],
            "type": "raster"
        },
        "seamarks": {
            "type": "vector",
            "tiles": ["http://127.0.0.1:8080/{z}/{x}/{y}"],
            "minzoom": 0,
            "maxzoom": 14
        }
    },
    "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
    "bearing": 0,
    "pitch": 0,
    "layers": []
}

# Symbol layer IDs that need text-keep-upright in OFM style
OFM_TEXT_KEEP_UPRIGHT = {
    "waterway_line_label", "water_name_point_label", "water_name_line_label",
    "poi_r20", "poi_r7", "poi_r1", "poi_transit",
    "highway-name-path", "highway-name-minor", "highway-name-major",
    "highway-shield-non-us", "highway-shield-us-interstate", "road_shield_us",
    "airport",
    "label_other", "label_village", "label_town", "label_state",
    "label_city", "label_city_capital",
    "label_country_3", "label_country_2", "label_country_1",
}

# Seamark symbol layers that should have icon-keep-upright: true
SEAMARK_NAV_LAYERS = {
    "pilot_boarding[symbol]", "anchor_berth[symbol]", "seaplane_landing_area[symbol]",
    "berth[symbol]", "berth[extra text]", "harbour[symbol]",
    "crane[symbol]", "calling-in_point[symbol]", "calling-in_point-direction[symbol]",
    "{rescue,coastguard}_station[symbol]", "signal_station_*[symbol]",
    "aeroway=windsock[symbol]", "turning_basin[symbol]",
    "waterway=access_point[symbol]", "amenity=charging_station[symbol]",
    "small_craft_facility[symbol]",
    "wreck[symbol]", "rock[symbol]", "marine_farm[symbol]",
    "waterway_gauge[symbol]",
    "DYNAMIC_icon_fixed_rotation", "DYNAMIC_icon_free_rotation",
    "distance_mark[text]", "sea_area[text]",
    "navigation_line[symbol]", "recommended_track[symbol]",
    "separation_lane[line]", "separation_zone[symbol]",
    "rounding_mark_direction[symbol]", "hulk[symbol]",
    "minor_berth[symbol]", "beach[symbol]",
    "anchorage[symbol]", "cable_submarine[symbol]",
    "pipeline_submarine[symbol]", "restricted_area[symbol]",
    "burgee[symbol]", "DYNAMIC_burgee[symbol]",
    "whitewater[text]",
}

# Copy OFM layers (except background — we use our own)
for layer in ofm["layers"]:
    lid = layer.get("id", "")
    ltype = layer.get("type", "")
    if lid == "background":
        # Replace with our marine blue background
        merged["layers"].append({
            "id": "background-water",
            "type": "background",
            "paint": {
                "background-color": "#d4e7f7"
            }
        })
        continue
    if lid in OFM_TEXT_KEEP_UPRIGHT and ltype == "symbol":
        lcopy = json.loads(json.dumps(layer))
        if "layout" not in lcopy:
            lcopy["layout"] = {}
        lcopy["layout"]["text-keep-upright"] = True
        merged["layers"].append(lcopy)
    else:
        merged["layers"].append(json.loads(json.dumps(layer)))

# Add placeholder layer layer-osm (z-order reference for chart insertion)
merged["layers"].append({
    "id": "layer-osm",
    "type": "background",
    "paint": {"background-color": "rgba(0,0,0,0)"}
})

# Add placeholder layer layer-seamark-bottom (chart insertion point below seamarks)
merged["layers"].append({
    "id": "layer-seamark-bottom",
    "type": "background",
    "paint": {"background-color": "rgba(0,0,0,0)"}
})

# Copy k-yle seamark layers (skip the "basemap" raster layer, modify others)
# We don't have the full k-yle file saved, so define them inline
SEAMARK_LAYERS = [
    {
        "id": "no_entry[fill]",
        "type": "fill",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "leisure", "swimming_area"], ["==", "seamark:restricted_area:restriction", "no_entry"]],
        "paint": {"fill-pattern": "no_entry", "fill-opacity": 0.5},
        "minzoom": 11
    },
    {
        "id": "no_entry[line]",
        "type": "line",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "leisure", "swimming_area"], ["==", "seamark:restricted_area:restriction", "no_entry"]],
        "paint": {"line-color": "#f00", "line-opacity": 0.5},
        "minzoom": 11
    },
    {
        "id": "burgee[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:small_craft_facility:category", "nautical_club"], ["==", "club", "boat"], ["==", "club", "boating"], ["==", "club", "yachting"], ["==", "club", "sailing"], ["all", ["==", "club", "sport"], ["any", ["==", "sport", "sailing"], ["==", "sport", "rowing"]]], ["==", "amenity", "sailing_school"], ["==", "education", "sailing_school"], ["==", "training", "sailing"], ["==", "training", "maritime"], ["==", "scout", "sea"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "symbol-spacing": 5, "icon-image": "small_craft_facility/yacht_club", "icon-size": 0.3, "icon-keep-upright": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "text-size": 10, "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1.4, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "DYNAMIC_burgee[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "club", "boat"], ["==", "club", "boating"], ["==", "club", "yachting"], ["==", "club", "sailing"], ["all", ["==", "club", "sport"], ["any", ["==", "sport", "sailing"], ["==", "sport", "rowing"]]], ["==", "scout", "sea"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "symbol-spacing": 5, "icon-image": ["concat", "&_burgee_=", ["coalesce", ["get", "wikidata"], ["get", "network:wikidata"], ["get", "operator:wikidata"], ["get", "brand:wikidata"]]], "icon-size": 0.3, "icon-keep-upright": True, "icon-offset": [100, 0], "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "whitewater[line]",
        "type": "line",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "waterway", "canoe_pass"], ["has", "whitewater:section_grade"], ["has", "whitewater:rapid_grade"]],
        "paint": {"line-color": ["let", "scale", ["concat", ["get", "whitewater:section_grade"], ["get", "whitewater:rapid_grade"]], ["case", ["in", "6", ["var", "scale"]], "#f00", ["in", "5", ["var", "scale"]], "#f00", ["in", "4", ["var", "scale"]], "#f70", ["in", "3", ["var", "scale"]], "#da3", ["in", "2", ["var", "scale"]], "#ff0", ["in", "1", ["var", "scale"]], "#061", ["==", "waterway", "canoe_pass"], "#f00", "transparent"]], "line-width": 2},
        "layout": {"line-cap": "round"},
        "minzoom": 11
    },
    {
        "id": "whitewater[text]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["coalesce", ["get", "whitewater:section_name"], ["get", "name"]], "symbol-placement": "line-center", "text-size": 10, "text-justify": "center", "text-anchor": "bottom", "text-keep-upright": True},
        "filter": ["any", ["has", "whitewater:section_grade"], ["has", "whitewater:rapid_grade"]],
        "minzoom": 11
    },
    {
        "id": "pilot_boarding[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "icon-image": "pilot_boarding", "icon-size": 0.2, "symbol-placement": "point", "text-size": 10, "icon-anchor": "center", "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1.4, "text-keep-upright": True, "icon-keep-upright": True},
        "minzoom": 11,
        "filter": ["any", ["==", "seamark:type", "pilot_boarding"]],
        "paint": {"text-color": "#a30075"}
    },
    {
        "id": "anchor_berth[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "icon-image": "line-style/anchorage", "icon-size": 0.2, "symbol-placement": "point", "text-size": 10, "icon-anchor": "center", "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1.7, "text-keep-upright": True, "icon-keep-upright": True},
        "minzoom": 11,
        "filter": ["any", ["==", "seamark:type", "anchor_berth"]],
        "paint": {"text-color": "#a30075"}
    },
    {
        "id": "seaplane_landing_area[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "icon-image": "seaplane", "icon-size": 0.2, "symbol-placement": "point", "text-size": 10, "icon-anchor": "center", "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1.4, "text-keep-upright": True, "icon-keep-upright": True},
        "minzoom": 11,
        "filter": ["any", ["==", "seamark:type", "seaplane_landing_area"]],
        "paint": {"text-color": "#a30075"}
    },
    {
        "id": "waterway_gauge[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "waterway_gauge", "icon-size": 0.2, "symbol-placement": "point", "icon-anchor": "center", "icon-keep-upright": True, "text-keep-upright": True},
        "minzoom": 11,
        "filter": ["any", ["==", "seamark:type", "waterway_gauge"], ["==", "seamark:signal_station_warning:category", "tide_gauge"], ["==", "seamark:signal_station_warning:category", "depth"], ["==", "seamark:signal_station_warning:category", "water_level_gauge"]]
    },
    {
        "id": "marine_farm[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "marine_farm", "icon-size": 0.4, "symbol-placement": "point", "icon-anchor": "center", "icon-keep-upright": True, "text-keep-upright": True},
        "minzoom": 11,
        "filter": ["any", ["==", "seamark:type", "marine_farm"]]
    },
    {
        "id": "rock[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "rock_dangerous", "icon-size": 0.15, "symbol-placement": "point", "icon-anchor": "center", "icon-keep-upright": True, "text-keep-upright": True},
        "minzoom": 11,
        "filter": ["any", ["==", "seamark:type", "rock"]]
    },
    {
        "id": "wreck[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "wreck_surface", "icon-size": 0.15, "symbol-placement": "point", "icon-anchor": "center", "text-field": ["concat", ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], ["case", ["has", "wreck:date_sunk"], ["concat", " (", ["get", "wreck:date_sunk"], ")"], ""]], "text-size": 10, "text-justify": "center", "text-anchor": "top", "text-radial-offset": 0.7, "text-keep-upright": True, "icon-keep-upright": True},
        "minzoom": 11,
        "filter": ["any", ["==", "seamark:type", "wreck"], ["==", "historic", "wreck"]]
    },
    {
        "id": "minor_berth[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"], "?"], "symbol-placement": "point", "text-size": 8, "text-anchor": "center", "text-keep-upright": True},
        "minzoom": 16,
        "filter": ["any", ["==", "seamark:type", "minor_berth"]],
        "paint": {"text-color": "#333"}
    },
    {
        "id": "berth[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": True, "text-field": ["let", "name", ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"], ["case", ["==", ["get", "noref"], "yes"], "", "?"]], ["case", [">", ["length", ["var", "name"]], 5], ["concat", ["slice", ["var", "name"], 0, 4], "…"], ["var", "name"]]], "icon-image": ["let", "name", ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"], ""], ["case", [">=", ["length", ["var", "name"]], 5], "berth5", [">=", ["length", ["var", "name"]], 4], "berth4", [">=", ["length", ["var", "name"]], 3], "berth3", "berth2"]], "icon-size": 0.2, "symbol-placement": "point", "icon-keep-upright": True, "text-size": 9, "icon-anchor": "center", "text-anchor": "center", "text-keep-upright": True},
        "minzoom": 11,
        "filter": ["any", ["==", "seamark:type", "berth"]],
        "paint": {"text-color": "#a30075"}
    },
    {
        "id": "berth[extra text]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "berth"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-size": 0.3, "icon-keep-upright": True, "text-field": ["concat", ["case", ["all", ["has", "name"], ["!=", ["get", "name"], ["get", "seamark:name"]], ["!=", ["get", "name"], ["get", "ref"]]], ["concat", ["get", "name"], "\n"], ["has", "seamark:berth:name"], ["concat", ["get", "seamark:berth:name"], "\n"], ""], ["case", ["any", ["has", "maxstay"], ["has", "maxdraft"], ["has", "maxlength"], ["has", "maxwidth"], ["has", "maxweight"]], ["concat", "max", ["case", ["has", "maxstay"], ["concat", ". ", ["get", "maxstay"]], ""], ["case", ["has", "maxweight"], ["concat", ". ", ["get", "maxweight"], "t"], ""], ["case", ["has", "maxdraft"], ["concat", ". ", ["get", "maxdraft"], "m draft"], ""], ["case", ["has", "maxlength"], ["concat", ". ", ["get", "maxlength"], "m LOA"], ""], ["case", ["has", "maxwidth"], ["concat", ". ", ["get", "maxwidth"], "m wide"], ""]], ""]], "text-size": 10, "text-max-width": 20, "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1, "text-keep-upright": True},
        "minzoom": 15
    },
    {
        "id": "anchorage[line]",
        "type": "line",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "anchorage"]],
        "paint": {"line-color": "#a30075", "line-dasharray": [6, 6]},
        "minzoom": 11
    },
    {
        "id": "anchorage[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "anchorage"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "symbol-placement": "line", "symbol-spacing": 5, "icon-image": "line-style/anchorage", "icon-size": 0.1, "icon-rotation-alignment": "map", "icon-keep-upright": True, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "cable_submarine[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "cable_submarine"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "symbol-placement": "line", "icon-image": "line-style/cable_submarine", "icon-size": 0.2, "symbol-spacing": 1, "icon-rotate": 90, "icon-rotation-alignment": "map", "icon-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "pipeline_submarine[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "pipeline_submarine"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "symbol-placement": "line", "symbol-spacing": 1, "icon-image": "line-style/pipeline_submarine", "icon-rotate": 90, "icon-size": 0.2, "icon-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "restricted_area[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["any", ["==", "seamark:type", "restricted_area"], ["==", "seamark:type", "cable_area"], ["==", "seamark:type", "pipeline_area"], ["==", "seamark:type", "dumping_ground"], ["==", "seamark:type", "seaplane_landing_area"]], ["none", ["==", "seamark:restricted_area:restriction", "no_entry"]]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "symbol-placement": "line", "symbol-spacing": 5, "icon-image": "line-style/restricted_area", "icon-rotate": 90, "icon-size": 0.4, "icon-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "harbour[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "harbour"], ["==", "leisure", "marina"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": ["case", ["has", "leisure"], "harbour_marina", ["match", ["get", "seamark:harbour:category"], "fishing", "harbour_fishing", "marina", "harbour_marina", "marina_no_facilities", "harbour_marina", "harbour_generic"]], "icon-size": 0.2, "icon-keep-upright": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "symbol-placement": "point", "text-size": 10, "icon-anchor": "center", "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1.5, "text-keep-upright": True},
        "paint": {"text-color": "#a30075"},
        "minzoom": 11
    },
    {
        "id": "sea_area[text]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "sea_area"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "text-size": 10, "icon-optional": False, "text-keep-upright": True},
        "paint": {"text-color": "#333", "text-opacity": 0.9},
        "minzoom": 9
    },
    {
        "id": "distance_mark[circle]",
        "type": "circle",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["==", "seamark:type", "distance_mark"],
        "paint": {"circle-radius": 2, "circle-opacity": 0, "circle-stroke-color": ["case", ["==", "seamark:distance_mark:category", "not_installed"], "#a30075", "#333"], "circle-stroke-width": 1},
        "minzoom": 14
    },
    {
        "id": "distance_mark[text]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "distance_mark"], ["==", "waterway", "milestone"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["case", ["has", "distance"], ["concat", ["get", "distance"], "km"], ["concat", ["case", ["has", "seamark:distance_mark:distance"], ["get", "seamark:distance_mark:distance"], "?"], ["match", ["get", "seamark:distance_mark:units"], "metres", "m", "feet", "ft", "kilometres", "km", "hectometres", "hm", "statute_miles", "mi", "nautical_miles", "nm", "m"]]], "text-size": 10, "text-radial-offset": 0.5, "text-anchor": "top", "text-keep-upright": True},
        "paint": {"text-color": "#333", "text-opacity": 0.9},
        "minzoom": 9
    },
    {
        "id": "crane[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "crane"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "small_craft_facility/boat_hoist", "icon-size": 0.3, "icon-keep-upright": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "symbol-placement": "point", "text-size": 10, "icon-anchor": "center", "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1.4, "text-keep-upright": True},
        "paint": {"text-color": "#a30075"},
        "minzoom": 11
    },
    {
        "id": "calling-in_point[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "calling-in_point"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "calling-in_point", "icon-size": 0.4, "icon-keep-upright": True, "text-field": ["case", ["has", "seamark:calling-in_point:channel"], ["concat", "Ch.", ["get", "seamark:calling-in_point:channel"]], ""], "symbol-placement": "point", "text-size": 10, "icon-anchor": "center", "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1.4, "text-keep-upright": True},
        "paint": {"text-color": "#a30075"},
        "minzoom": 11
    },
    {
        "id": "calling-in_point-direction[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["==", "seamark:type", "calling-in_point"], ["any", ["has", "direction"], ["has", "seamark:calling-in_point:orientation"]]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "icon-image": "direction-arrow", "icon-size": 1.4, "icon-offset": [0, -15], "icon-rotation-alignment": "map", "icon-rotate": ["case", ["has", "seamark:calling-in_point:orientation"], ["to-number", ["get", "seamark:calling-in_point:orientation"]], ["has", "direction"], ["+", 180, ["to-number", ["at", 0, ["split", ["get", "direction"], ";"]]]], 0], "icon-keep-upright": True, "symbol-placement": "point", "icon-anchor": "center", "text-keep-upright": True},
        "minzoom": 13
    },
    {
        "id": "{rescue,coastguard}_station[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "emergency", "water_rescue"], ["==", "police", "naval_base"], ["==", "seamark:type", "rescue_station"], ["==", "seamark:type", "coastguard_station"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "text-size": 10, "text-justify": "left", "text-anchor": "left", "icon-image": "rescue_station", "icon-size": 0.2, "text-radial-offset": 0.6, "text-keep-upright": True, "icon-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "signal_station_*[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "signal_station_traffic"], ["all", ["==", "seamark:type", "signal_station_warning"], ["!=", "seamark:signal_station_warning:category", "tide_gauge"], ["!=", "seamark:signal_station_warning:category", "depth"], ["!=", "seamark:signal_station_warning:category", "water_level_gauge"]]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["format", ["case", ["any", ["has", "seamark:signal_station_traffic:category"], ["has", "seamark:signal_station_warning:category"]], ["concat", "SS(", ["match", ["get", "seamark:signal_station_traffic:category"], "bridge_passage", "Bridge", "port_entry_departure", "Port", ["coalesce", ["get", "seamark:signal_station_traffic:category"], ["get", "seamark:signal_station_warning:category"]]], ")"], ""], {}, "\n", {}, ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"], ["get", "operator"]], {}, "\n", {}, ["case", ["has", "seamark:signal_station_traffic:channel"], ["concat", "Ch.", ["get", "seamark:signal_station_traffic:channel"]], ["has", "seamark:signal_station_warning:channel"], ["concat", "Ch.", ["get", "seamark:signal_station_warning:channel"]], ""], {}], "text-size": 10, "text-justify": "left", "text-anchor": "left", "icon-image": "signal_station", "icon-size": 0.4, "text-radial-offset": 1, "text-keep-upright": True, "icon-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "aeroway=windsock[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["==", "aeroway", "windsock"], ["!=", "seamark:topmark:shape", "flag"], ["!=", "seamark:daymark:shape", "flag"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "windsock", "icon-size": 0.8, "icon-keep-upright": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "text-size": 10, "text-max-width": 20, "text-justify": "left", "text-anchor": "left", "text-radial-offset": 1.1, "text-keep-upright": True},
        "minzoom": 15
    },
    {
        "id": "mooring-point",
        "type": "circle",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["==", ["geometry-type"], "Point"], ["any", ["all", ["==", ["get", "seamark:type"], "mooring"], ["any", ["==", ["get", "seamark:mooring:category"], "dolphin"], ["==", ["get", "seamark:mooring:category"], "bollard"]]], ["==", ["get", "man_made"], "dolphin"]]],
        "paint": {"circle-radius": 3, "circle-color": ["case", ["==", ["get", "seamark:mooring:category"], "bollard"], "#fff", "#fc0"], "circle-stroke-color": "#333", "circle-stroke-width": 1},
        "minzoom": 14
    },
    {
        "id": "turning_basin[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "turning_basin"], ["==", "waterway", "turning_point"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "turning_basin", "icon-size": 0.3, "icon-keep-upright": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "text-size": 10, "text-max-width": 20, "text-justify": "left", "text-anchor": "left", "text-radial-offset": 1.1, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "beach[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["==", "natural", "beach"], ["!=", "access", "no"], ["!=", "access", "private"], ["!=", "access", "permit"], ["!=", "access", "customers"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "symbol-spacing": 5, "icon-image": "small_craft_facility/beach", "icon-size": 0.3, "icon-keep-upright": True, "text-field": ["let", "name", ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"], ["get", "operator"], ""], ["concat", ["case", ["!=", ["var", "name"], ""], ["concat", ["var", "name"], "\n"], ""], ["case", ["has", "opening_hours"], ["concat", ["get", "opening_hours"], "\n"], ""]]], "text-size": 10, "text-justify": "center", "text-anchor": "top", "text-radial-offset": 1.4, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "waterway=access_point[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["any", ["==", "waterway", "access_point"], ["==", "canoe", "egress"], ["==", "canoe", "put_in"], ["==", "canoe", "put_in;egress"], ["==", "whitewater", "egress"], ["==", "whitewater", "put_in"], ["==", "whitewater", "put_in;egress"], ["==", "whitewater", "put_in_out"]], ["!=", "access", "private"], ["!=", "access", "permit"], ["!=", "access", "no"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "small_craft_facility/kayak", "icon-size": 0.3, "icon-keep-upright": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"], ["get", "operator"]], "text-size": 10, "text-max-width": 20, "text-justify": "left", "text-anchor": "left", "text-radial-offset": 1.1, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "amenity=charging_station[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["==", "amenity", "charging_station"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "small_craft_facility/ev_charger", "icon-size": 0.3, "icon-keep-upright": True, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"], ["get", "operator"]], "text-size": 10, "text-max-width": 20, "text-justify": "left", "text-anchor": "left", "text-radial-offset": 1.1, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "small_craft_facility[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["any", ["==", "seamark:type", "small_craft_facility"], ["==", "amenity", "boat_storage"], ["==", "leisure", "fishing"], ["==", "leisure", "slipway"], ["==", "waterway", "boat_lift"], ["==", "waterway", "fuel"], ["==", "waterway", "water_point"], ["==", "waterway", "sanitary_dump_station"]], ["!=", "seamark:small_craft_facility:category", "nautical_club"], ["!=", "access", "private"], ["!=", "access", "permit"], ["!=", "access", "no"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": ["case", ["==", ["get", "amenity"], "boat_storage"], "small_craft_facility/boat_storage", ["==", ["get", "leisure"], "fishing"], "small_craft_facility/fishing_spot", ["==", ["get", "leisure"], "slipway"], "small_craft_facility/slipway", ["==", ["get", "waterway"], "boat_lift"], "small_craft_facility/elevator", ["==", ["get", "waterway"], "fuel"], "small_craft_facility/fuel_station", ["==", ["get", "waterway"], "water_point"], "small_craft_facility/freshwater_tap", ["==", ["get", "waterway"], "sanitary_dump_station"], "small_craft_facility/pump_out", ["match", ["get", "seamark:small_craft_facility:category"], "boat_hoist", "small_craft_facility/boat_hoist", "boatyard", "small_craft_facility/boatyard", "fuel_station", "small_craft_facility/fuel_station", "pump-out", "small_craft_facility/pump_out", "water_tap", "small_craft_facility/freshwater_tap", "slipway", "small_craft_facility/slipway", "visitor_berth", "small_craft_facility/visitor_berth", "visitors_mooring", "small_craft_facility/visitors_mooring", ""]], "icon-size": 0.3, "icon-keep-upright": True, "text-field": ["let", "name", ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"], ["get", "operator"], ""], ["concat", ["case", ["any", ["all", ["has", "charge"], ["!=", ["get", "charge"], "no"]], ["all", ["has", "toll"], ["!=", ["get", "toll"], "no"]], ["all", ["has", "fee"], ["!=", ["get", "fee"], "no"]]], "$$\n", ""], ["case", ["!=", ["var", "name"], ""], ["concat", ["var", "name"], "\n"], ""], ["case", ["has", "opening_hours"], ["concat", ["get", "opening_hours"], "\n"], ""], ["case", ["any", ["has", "maxstay"], ["has", "maxdraft"], ["has", "maxlength"], ["has", "maxwidth"], ["has", "maxweight"]], ["concat", "max", ["case", ["has", "maxstay"], ["concat", ". ", ["get", "maxstay"]], ""], ["case", ["has", "maxweight"], ["concat", ". ", ["get", "maxweight"], "t"], ""], ["case", ["has", "maxdraft"], ["concat", ". ", ["get", "maxdraft"], "m draft"], ""], ["case", ["has", "maxlength"], ["concat", ". ", ["get", "maxlength"], "m LOA"], ""], ["case", ["has", "maxwidth"], ["concat", ". ", ["get", "maxwidth"], "m wide"], ""]], ["has", "_fuel"], ["get", "_fuel"], ""]]], "text-size": 10, "text-max-width": 20, "text-justify": "left", "text-anchor": "left", "text-radial-offset": 1, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "rounding_mark_direction[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["any", ["==", "seamark:type", "beacon_cardinal"], ["==", "seamark:type", "beacon_isolated_danger"], ["==", "seamark:type", "beacon_lateral"], ["==", "seamark:type", "beacon_safe_water"], ["==", "seamark:type", "beacon_special_purpose"], ["==", "seamark:type", "buoy_cardinal"], ["==", "seamark:type", "buoy_installation"], ["==", "seamark:type", "buoy_isolated_danger"], ["==", "seamark:type", "buoy_lateral"], ["==", "seamark:type", "buoy_safe_water"], ["==", "seamark:type", "buoy_special_purpose"]], ["any", ["==", "direction", "clockwise"], ["==", "direction", "anticlockwise"]]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "icon-size": 0.5, "icon-rotate": -90, "icon-image": ["case", ["==", ["get", "direction"], "clockwise"], "round_to_starboard", "round_to_port"], "icon-keep-upright": True, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "DYNAMIC_icon_fixed_rotation",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["any", ["==", "seamark:type", "beacon_cardinal"], ["==", "seamark:type", "beacon_isolated_danger"], ["==", "seamark:type", "beacon_lateral"], ["==", "seamark:type", "beacon_safe_water"], ["==", "seamark:type", "beacon_special_purpose"], ["==", "seamark:type", "buoy_cardinal"], ["==", "seamark:type", "buoy_installation"], ["==", "seamark:type", "buoy_isolated_danger"], ["==", "seamark:type", "buoy_lateral"], ["==", "seamark:type", "buoy_safe_water"], ["==", "seamark:type", "buoy_special_purpose"], ["==", "seamark:type", "daymark"], ["==", "seamark:type", "fog_signal"], ["==", "seamark:type", "notice"], ["==", "seamark:type", "light"], ["==", "seamark:type", "light_minor"], ["==", "seamark:type", "light_major"], ["==", "seamark:type", "light_float"], ["==", "seamark:type", "light_vessel"], ["==", "seamark:type", "virtual_aton"], ["==", "seamark:type", "mooring"], ["==", "seamark:type", "platform"], ["==", "seamark:type", "topmark"], ["==", "man_made", "lighthouse"], ["==", "man_made", "offshore_platform"]], ["any", ["has", "direction"], ["has", "seamark:notice:orientation"], ["has", "seamark:notice:1:orientation"]]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "placeholder", "icon-rotation-alignment": "map", "icon-rotate": ["case", ["has", "direction"], ["+", 180, ["to-number", ["at", 0, ["split", ["get", "direction"], ";"]]]], ["has", "seamark:notice:orientation"], ["to-number", ["get", "seamark:notice:orientation"]], ["has", "seamark:notice:1:orientation"], ["to-number", ["get", "seamark:notice:1:orientation"]], 0], "text-field": ["format", ["case", ["==", ["get", "seamark:type"], "virtual_aton"], "V-AIS\n", ""], {}, ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], {}, "\n", {}, ["get", "_lx"], {}], "text-anchor": "left", "text-justify": "left", "text-radial-offset": 1.4, "text-size": 10, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "DYNAMIC_icon_free_rotation",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["any", ["==", "seamark:type", "beacon_cardinal"], ["==", "seamark:type", "beacon_isolated_danger"], ["==", "seamark:type", "beacon_lateral"], ["==", "seamark:type", "beacon_safe_water"], ["==", "seamark:type", "beacon_special_purpose"], ["==", "seamark:type", "buoy_cardinal"], ["==", "seamark:type", "buoy_installation"], ["==", "seamark:type", "buoy_isolated_danger"], ["==", "seamark:type", "buoy_lateral"], ["==", "seamark:type", "buoy_safe_water"], ["==", "seamark:type", "buoy_special_purpose"], ["==", "seamark:type", "daymark"], ["==", "seamark:type", "fog_signal"], ["==", "seamark:type", "notice"], ["==", "seamark:type", "light"], ["==", "seamark:type", "light_minor"], ["==", "seamark:type", "light_major"], ["==", "seamark:type", "light_float"], ["==", "seamark:type", "light_vessel"], ["==", "seamark:type", "virtual_aton"], ["==", "seamark:type", "mooring"], ["==", "seamark:type", "platform"], ["==", "seamark:type", "topmark"], ["==", "man_made", "lighthouse"], ["==", "man_made", "offshore_platform"]], ["none", ["has", "direction"], ["has", "seamark:notice:orientation"], ["has", "seamark:notice:1:orientation"]]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "icon-image": "placeholder", "text-field": ["format", ["case", ["==", ["get", "seamark:type"], "virtual_aton"], "V-AIS\n", ""], {}, ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], {}, "\n", {}, ["get", "_lx"], {}], "text-anchor": "left", "text-justify": "left", "text-radial-offset": 1.4, "text-size": 10, "text-keep-upright": True},
        "minzoom": 11
    },
    {
        "id": "production_area[line]",
        "type": "line",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "production_area"], ["==", "seamark:type", "marine_farm"]],
        "paint": {"line-width": 1.5, "line-dasharray": [4, 4]},
        "minzoom": 11
    },
    {
        "id": "navigation_line[line]",
        "type": "line",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "navigation_line"]],
        "paint": {"line-opacity": 0.5, "line-width": 1.5, "line-dasharray": [2, 2]},
        "minzoom": 11
    },
    {
        "id": "navigation_line[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["case", ["has", "seamark:recommended_track:orientation"], ["concat", ["get", "seamark:recommended_track:orientation"], "°"], ["has", "seamark:navigation_line:orientation"], ["concat", ["get", "seamark:navigation_line:orientation"], "°"], ""], "symbol-placement": "line-center", "text-size": 10, "text-justify": "center", "text-anchor": "bottom", "text-keep-upright": True},
        "filter": ["any", ["==", "seamark:type", "navigation_line"]],
        "minzoom": 11
    },
    {
        "id": "recommended_track[line]",
        "type": "line",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "recommended_track"]],
        "paint": {"line-opacity": 0.5, "line-width": 1.5},
        "minzoom": 11
    },
    {
        "id": "separation_lane[line]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "separation_lane"]],
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "symbol-placement": "line", "symbol-spacing": 50, "icon-image": "line-style/separation_lane", "icon-rotate": 90, "icon-size": 0.4, "icon-keep-upright": True},
        "minzoom": 0
    },
    {
        "id": "separation_line[line]",
        "type": "line",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "separation_line"]],
        "paint": {"line-width": 3, "line-color": "#be94f7"},
        "minzoom": 0
    },
    {
        "id": "separation_boundary[line]",
        "type": "line",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "separation_boundary"]],
        "paint": {"line-width": 3, "line-color": "#be94f7", "line-dasharray": [4, 4]},
        "minzoom": 0
    },
    {
        "id": "recommended_track[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["case", ["has", "seamark:recommended_track:orientation"], ["concat", ["get", "seamark:recommended_track:orientation"], "°"], ""], "symbol-placement": "line-center", "text-size": 10, "text-justify": "center", "text-anchor": "bottom", "text-keep-upright": True},
        "filter": ["any", ["==", "seamark:type", "recommended_track"]],
        "minzoom": 11
    },
    {
        "id": "hulk[fill]",
        "type": "fill",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", ["get", "building"], "ship"], ["==", ["get", "historic"], "ship"], ["==", ["get", "seamark:type"], "hulk"], ["==", ["get", "seamark:type"], "pontoon"], ["all", ["!=", ["geometry-type"], "Point"], ["any", ["all", ["==", ["get", "seamark:type"], "mooring"], ["==", ["get", "seamark:mooring:category"], "dolphin"]], ["==", ["get", "man_made"], "dolphin"]]]],
        "paint": {"fill-color": "#fc0", "fill-outline-color": "#000"},
        "minzoom": 11
    },
    {
        "id": "restricted_area[fill]",
        "type": "fill",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["all", ["any", ["==", "seamark:type", "restricted_area"], ["==", "seamark:type", "cable_area"], ["==", "seamark:type", "pipeline_area"], ["==", "seamark:type", "dumping_ground"], ["==", "seamark:type", "seaplane_landing_area"]], ["none", ["==", "seamark:restricted_area:restriction", "no_entry"]]],
        "paint": {"fill-color": "#fff", "fill-opacity": 0.1},
        "minzoom": 11
    },
    {
        "id": "fairway",
        "type": "fill",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "fairway"], ["==", "seamark:type", "turning_basin"]],
        "paint": {"fill-color": "#fff", "fill-opacity": 0.2},
        "minzoom": 11
    },
    {
        "id": "separation_zone + separation_roundabout",
        "type": "fill",
        "source": "seamarks",
        "source-layer": "seamarks",
        "filter": ["any", ["==", "seamark:type", "separation_zone"], ["==", "seamark:type", "separation_roundabout"]],
        "paint": {"fill-color": "#be94f7", "fill-opacity": 0.9},
        "minzoom": 0
    },
    {
        "id": "separation_zone[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "symbol-placement": "point", "text-keep-upright": True},
        "filter": ["all", ["==", "seamark:type", "separation_zone"]],
        "paint": {"text-color": "#a30075"},
        "minzoom": 0
    },
    {
        "id": "hulk[symbol]",
        "type": "symbol",
        "source": "seamarks",
        "source-layer": "seamarks",
        "layout": {"icon-allow-overlap": True, "icon-ignore-placement": True, "text-optional": True, "text-allow-overlap": False, "text-field": ["coalesce", ["get", "seamark:name"], ["get", "ref"], ["get", "name"]], "symbol-placement": "point", "icon-image": "anchor", "icon-size": 0.2, "icon-keep-upright": True, "text-keep-upright": True},
        "filter": ["any", ["==", ["get", "seamark:type"], "hulk"], ["==", ["get", "seamark:type"], "pontoon"], ["all", ["!=", ["geometry-type"], "Point"], ["any", ["all", ["==", ["get", "seamark:type"], "mooring"], ["==", ["get", "seamark:mooring:category"], "dolphin"]], ["==", ["get", "man_made"], "dolphin"]]]],
        "minzoom": 11
    },
]

# Add seamark vector layers (source added programmatically by tile server)
last_seamark_id = SEAMARK_LAYERS[-1]["id"]
for layer in SEAMARK_LAYERS:
    if layer["id"] == last_seamark_id:
        layer = {**layer, "id": "layer-openseamap"}
    merged["layers"].append(layer)

# Write merged style
output_path = 'app/src/main/assets/pursi_style_vector.json'
with open(output_path, 'w') as f:
    json.dump(merged, f, indent=2, ensure_ascii=False)

# Verify
with open(output_path) as f:
    data = json.load(f)
print(f"Merged style saved to {output_path}")
print(f"Total layers: {len(data['layers'])}")
print(f"Sources: {list(data['sources'].keys())}")
print(f"Sprite: {data.get('sprite', 'none')}")
print(f"Glyphs: {data['glyphs']}")
print(f"Layer names: {[l['id'] for l in data['layers'][:5]]} ... {[l['id'] for l in data['layers'][-5:]]}")
# Check for text-keep-upright on symbol layers
symbol_layers = [l for l in data['layers'] if l.get('type') == 'symbol']
with_keep = [l for l in symbol_layers if l.get('layout', {}).get('text-keep-upright')]
without_keep = [l for l in symbol_layers if not l.get('layout', {}).get('text-keep-upright')]
print(f"\nSymbol layers: {len(symbol_layers)}")
print(f"With text-keep-upright: {len(with_keep)}")
print(f"Without text-keep-upright: {len(without_keep)}")
if without_keep:
    print(f"MISSING keep-upright on: {[l['id'] for l in without_keep]}")
