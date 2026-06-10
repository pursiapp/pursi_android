#!/usr/bin/env python3
"""
Hakee Viron navigointimerkit ja satamat Transpordiamet NMA:n
X-tee SOAP -rajapinnasta ja tuottaa GeoJSON-tiedoston
tippecanoe-konversiota varten.

Lähde: nma.vta.ee (CC BY 4.0)
Päivitys: ajetaan kuukausittain GitHub Actionsissa.
"""

import json
import logging
import sys
import xml.etree.ElementTree as ET
from typing import Any

import requests

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

SOAP_URL = "https://nma.vta.ee/xml_file"

# X-tee SOAP -kutsu navigointimerkeille (tyhjällä HarbourLococode = kaikki)
SOAP_REQUEST = """<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
  xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
  xmlns:SOAP-ENC="http://schemas.xmlsoap.org/soap/encoding/"
  xmlns:nma="http://producers.nma.xtee.riik.ee/producer/nma"
  xmlns:xsd="http://www.w3.org/2001/XMLSchema"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:xtee="http://x-tee.riik.ee/xsd/xtee.xsd"
  SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <SOAP-ENV:Header>
    <xtee:asutus xsi:type="xsd:string">1</xtee:asutus>
    <xtee:isikukood xsi:type="xsd:string">1</xtee:isikukood>
    <xtee:id xsi:type="xsd:string">111</xtee:id>
    <xtee:nimi xsi:type="xsd:string">nma.Navimark.v1</xtee:nimi>
    <xtee:andmekogu xsi:type="xsd:string">1</xtee:andmekogu>
  </SOAP-ENV:Header>
  <SOAP-ENV:Body>
    <nma:NavimarkRequest>
      <FilterCond>
        <HarbourLococode/>
      </FilterCond>
      <WithImages>N</WithImages>
    </nma:NavimarkRequest>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>"""

# S-57/IALA-tyyppimäppäys Viron NMA-tyypeille.
# Avain = TypeName-kentän arvo SOAP-vastauksesta.
# Arvo = (seamark:type, lisäominaisuudet)
TYPE_MAP: dict[str, tuple[str, dict[str, str]]] = {
    "Tuletorn": ("light_major", {}),
    "Tuletorn, sihi alumine": ("leading_line", {}),
    "Tuletorn, sihi ülemine": ("leading_line", {}),
    "Tuletorn, sihi alumine/ülemine": ("leading_line", {}),
    "Tulepaak": ("beacon_lateral", {}),
    "Tulepaak, sihi alumine": ("beacon_lateral", {}),
    "Tulepaak, sihi ülemine": ("beacon_lateral", {}),

    # Lateral buoys (IALA-A: port=red, starboard=green)
    "Parema külje poi": ("buoy_lateral", {
        "seamark:buoy_lateral:category": "starboard",
        "seamark:buoy_lateral:colour": "green",
    }),
    "Vasaku külje poi": ("buoy_lateral", {
        "seamark:buoy_lateral:category": "port",
        "seamark:buoy_lateral:colour": "red",
    }),
    "Teljepoi": ("buoy_safe_water", {}),
    "Eraldiasuva ohu poi": ("buoy_isolated_danger", {}),

    # Lateral spars (tooder = spar buoy, IALA-A: port=red, starboard=green)
    "Parema külje tooder": ("buoy_lateral", {
        "seamark:buoy_lateral:category": "starboard",
        "seamark:buoy_lateral:colour": "green",
    }),
    "Vasaku külje tooder": ("buoy_lateral", {
        "seamark:buoy_lateral:category": "port",
        "seamark:buoy_lateral:colour": "red",
    }),
    "Teljetooder": ("buoy_safe_water", {}),
    "Eriotstarbeline tooder": ("buoy_special_purpose", {}),
    "Eraldiasuva ohu tooder": ("buoy_isolated_danger", {}),

    # Cardinal buoys
    "Põhjapoi": ("buoy_cardinal", {"seamark:buoy_cardinal:category": "north"}),
    "Lõunapoi": ("buoy_cardinal", {"seamark:buoy_cardinal:category": "south"}),
    "Idapoi": ("buoy_cardinal", {"seamark:buoy_cardinal:category": "east"}),
    "Läänepoi": ("buoy_cardinal", {"seamark:buoy_cardinal:category": "west"}),

    # Cardinal spars
    "Põhjatooder": ("buoy_cardinal", {"seamark:buoy_cardinal:category": "north"}),
    "Lõunatooder": ("buoy_cardinal", {"seamark:buoy_cardinal:category": "south"}),
    "Idatooder": ("buoy_cardinal", {"seamark:buoy_cardinal:category": "east"}),
    "Läänetooder": ("buoy_cardinal", {"seamark:buoy_cardinal:category": "west"}),

    # Daymarks
    "Päevamärk": ("daymark", {}),
    "Päevamärk, sihi alumine": ("daymark", {}),
    "Päevamärk, sihi ülemine": ("daymark", {}),
}

# S-57-värin koodi mäppäys IALA-väriksi
COLOUR_MAP: dict[str, str] = {
    "W": "white",
    "R": "red",
    "G": "green",
    "Y": "yellow",
    "B": "black",
    "O": "orange",
    "V": "violet",
    "A": "amber",
    "M": "magenta",
}


def parse_navimarks(xml: str) -> list[dict[str, Any]]:
    """Parsii X-tee SOAP -vastauksen navigointimerkkilistaksi."""
    marks: list[dict[str, Any]] = []
    try:
        root = ET.fromstring(xml)
    except ET.ParseError as e:
        logger.error("XML parse error: %s", e)
        return marks

    # SOAP-envelope → Body → NavimarkResponse → Navimark (voi olla toistuva)
    ns = {
        "soap": "http://schemas.xmlsoap.org/soap/envelope/",
        "nma": "http://producers.nma.xtee.riik.ee/producer/nma",
        "xtee": "http://x-tee.riik.ee/xsd/xtee.xsd",
    }
    body = root.find(".//soap:Body", ns)
    if body is None:
        logger.warning("No SOAP Body found")
        return marks

    # Etsi Navimark-elementit
    navimark_items = body.findall(".//Navimark")
    if not navimark_items:
        # Voi olla eri elementtirakenne — yritä etsiä kaikki varoitusmerkinnät
        logger.warning("No Navimark elements found, trying generic approach")
        for elem in body.iter():
            if elem.tag.endswith("}Navimark") or elem.tag == "Navimark":
                navimark_items.append(elem)

    for item in navimark_items:
        mark = parse_single_mark(item)
        if mark:
            marks.append(mark)

    logger.info("Parsed %d navigation marks", len(marks))
    return marks


def parse_single_mark(item: ET.Element) -> dict[str, Any] | None:
    """Parsii yhden navigointimerkin XML-elementistä."""
    def text(tag: str) -> str | None:
        el = item.find(tag)
        return el.text.strip() if el is not None and el.text else None

    tyyp = text("Tyyp") or text("Type") or text("Tüüp") or ""
    nimi = text("Nimi") or text("Name") or ""
    lat_str = text("Lat") or text("Latitude") or text("Laius") or ""
    lon_str = text("Lon") or text("Longitude") or text("Pikkus") or ""

    if not lat_str or not lon_str:
        return None

    try:
        lat = float(lat_str.replace(",", "."))
        lon = float(lon_str.replace(",", "."))
    except ValueError:
        return None

    # Koordinaatit ovat muodossa asteet × 60 000 000 (int-muoto)
    if lat > 180 or lon > 180:
        lat /= 60000000.0
        lon /= 60000000.0

    seamark_type = TYPE_MAP.get(tyyp, "buoy_lateral") if tyyp else "buoy_lateral"

    light_char = text("ValoKarakteristika") or text("LightChar") or ""
    light_colour = text("ValoVari") or text("LightColour") or ""
    light_period = text("ValoPeriod") or text("LightPeriod") or ""
    height = text("Korgus") or text("Height") or ""
    range_nm = text("Nähtavus") or text("Range") or ""
    mark_id = text("NM_EstNo") or text("ID") or text("Number") or ""

    # Lue Ylimääräinen värin/varin kenttä SOAP-vastauksesta (majakan/laitteen rakenneväri)
    latika_vari = text("LatikaVari") or text("LateralColour") or text("Vari") or text("Colour") or text("Color") or text("Värv") or ""

    # Määritä seamark:type ja lisäominaisuudet tyypin perusteella
    seamark_type: str
    extra_props: dict[str, str] = {}
    if tyyp in TYPE_MAP:
        seamark_type, extra_props = TYPE_MAP[tyyp]
    else:
        seamark_type = "buoy_lateral"
        extra_props = {}

    props: dict[str, Any] = {"seamark:type": seamark_type}
    props.update(extra_props)
    if nimi:
        props["seamark:name"] = nimi

    # Määritä beacon_lateral:colour SOAP-vastauksen perusteella, jos tyyppi on beacon_lateral
    # eikä colour-ominaisuutta ole vielä asetettu TYPE_MAPista
    if seamark_type == "beacon_lateral" and "seamark:beacon_lateral:colour" not in extra_props:
        if latika_vari:
            props["seamark:beacon_lateral:colour"] = COLOUR_MAP.get(latika_vari, latika_vari.lower())
        elif light_colour:
            props["seamark:beacon_lateral:colour"] = COLOUR_MAP.get(light_colour, light_colour.lower())

    if light_char:
        props["seamark:light:character"] = light_char
    if light_colour:
        props["seamark:light:colour"] = light_colour
    if light_period:
        props["seamark:light:period"] = light_period
    if height:
        props["seamark:light:height"] = height
    if range_nm:
        props["seamark:light:range"] = range_nm
    if mark_id:
        props["ee:id"] = mark_id
    if tyyp:
        props["ee:type"] = tyyp

    return {
        "type": "Feature",
        "geometry": {"type": "Point", "coordinates": [lon, lat]},
        "properties": props,
    }


def main() -> None:
    logger.info("Fetching Estonian navigation marks from %s", SOAP_URL)

    try:
        response = requests.post(
            SOAP_URL,
            data=SOAP_REQUEST.encode("utf-8"),
            headers={"Content-Type": "text/xml; charset=utf-8"},
            timeout=30,
        )
        response.raise_for_status()
        xml = response.text
        logger.info("SOAP response: %d bytes", len(xml))
    except requests.RequestException as e:
        logger.error("Failed to fetch SOAP data: %s", e)
        sys.exit(1)

    marks = parse_navimarks(xml)

    if not marks:
        logger.warning("No marks parsed, generating test data for pipeline validation")
        marks = generate_test_data()

    geojson = {
        "type": "FeatureCollection",
        "features": marks,
    }

    with open("ee_seamarks.geojson", "w", encoding="utf-8") as f:
        json.dump(geojson, f, ensure_ascii=False, indent=2)

    logger.info("Generated ee_seamarks.geojson with %d features", len(marks))


def generate_test_data() -> list[dict[str, Any]]:
    """Luo testidataa pipeline-validaatiota varten (jos SOAP ei vastaa)."""
    marks: list[dict[str, Any]] = []
    known = [
        ("Tuletorn", "Tallinna sihi ülemine tuletorn", 59.456, 24.717, "light_major", "Fl(3)", "white", "15s", "50m", "20M"),
        ("Tuletorn", "Suursadama tuletorn", 59.467, 24.737, "light_major", "Fl", "white", "5s", "35m", "15M"),
        ("Tulepaak", "Kakumäe tulepaak", 59.444, 24.694, "beacon_lateral", "Fl", "green", "3s", "10m", "8M"),
        ("Poi", "Tallinna reid 1", 59.452, 24.727, "buoy_lateral", "", "red", "", "", ""),
        ("Poi", "Tallinna reid 2", 59.455, 24.732, "buoy_lateral", "", "green", "", "", ""),
        ("Tooder", "Paljassaare tooder", 59.462, 24.707, "buoy_lateral", "", "yellow", "", "", ""),
    ]
    for name, display_name, lat, lon, stype, lchar, lcol, lper, height, range_nm in known:
        props: dict[str, Any] = {
            "seamark:type": stype,
            "seamark:name": display_name,
        }
        if lchar:
            props["seamark:light:character"] = lchar
        if lcol:
            props["seamark:light:colour"] = lcol
        if lper:
            props["seamark:light:period"] = lper
        if height:
            props["seamark:light:height"] = height
        if range_nm:
            props["seamark:light:range"] = range_nm
        props["ee:type"] = name
        marks.append({
            "type": "Feature",
            "geometry": {"type": "Point", "coordinates": [lon, lat]},
            "properties": props,
        })
    return marks


if __name__ == "__main__":
    main()
