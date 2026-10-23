import json
from pathlib import Path

base = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets"
geo = json.loads((base / "prefectures.geojson").read_text(encoding="utf-8"))
salts = json.loads((base / "salts.json").read_text(encoding="utf-8"))
assert geo["type"] == "FeatureCollection"
features = geo["features"]
assert len(features) == 47
codes = {f["properties"]["PREF_CODE"] for f in features}
names = {f["properties"]["N03_001"] for f in features}
assert len(codes) == 47
assert len(names) == 47
valid_types = {"Polygon", "MultiPolygon"}
vertices = 0
for feature in features:
    geometry = feature["geometry"]
    assert geometry["type"] in valid_types
    polygons = [geometry["coordinates"]] if geometry["type"] == "Polygon" else geometry["coordinates"]
    assert polygons
    for polygon in polygons:
        assert polygon and len(polygon[0]) >= 4
        for point in polygon[0]:
            lon, lat = point[:2]
            assert -180 <= lon <= 180 and -90 <= lat <= 90
            vertices += 1
items = salts["items"]
assert len({x["id"] for x in items}) == len(items)
for item in items:
    assert item.get("sourceUrl") and item.get("lastChecked")
    assert (item.get("latitude") is None) == (item.get("longitude") is None)
    if item.get("latitude") is not None:
        assert -90 <= item["latitude"] <= 90 and -180 <= item["longitude"] <= 180
print(f"GeoJSON OK: 47 prefectures, 47 stable codes, {vertices:,} outer-ring vertices")
print(f"Catalog OK: {len(items)} sourced records; {sum(x.get('latitude') is not None for x in items)} coordinate-backed markers")
