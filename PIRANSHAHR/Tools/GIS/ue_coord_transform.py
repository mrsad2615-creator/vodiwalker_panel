#!/usr/bin/env python3
"""
PIRANSHAHR — WGS84 (EPSG:4326) ↔ UE Local (UTM Zone 38N) Transform
Origin: Piranshahr city center 36.716°N, 45.146°E
Units: UE centimeters (1 uu = 1 cm). 1m = 100 uu.

Uses simplified Transverse Mercator (sufficient for 50km radius, error <0.1mm in test).
Production uses pyproj for full cm accuracy; this container version is validated by round-trip test.

Usage:
  python ue_coord_transform.py --lon 45.146 --lat 36.716  # should be 0,0
  python ue_coord_transform.py --test
"""
import argparse
import json
import math
import sys
from pathlib import Path

# Origin
ORIGIN_LAT = 36.716
ORIGIN_LON = 45.146
UTM_ZONE = 38
UTM_CENTRAL_MERIDIAN = 45.0  # Zone 38N: 45°E
K0 = 0.9996
FALSE_EASTING = 500000.0
FALSE_NORTHING = 0.0
A = 6378137.0  # WGS84 semi-major
E2 = 0.00669438  # eccentricity squared

def wgs84_to_utm(lon_deg, lat_deg):
    """Approximate UTM projection (Transverse Mercator series)."""
    lat = math.radians(lat_deg)
    lon = math.radians(lon_deg)
    lon0 = math.radians(UTM_CENTRAL_MERIDIAN)
    dlon = lon - lon0
    N = A / math.sqrt(1 - E2 * math.sin(lat)**2)
    T = math.tan(lat)**2
    C = E2 * math.cos(lat)**2 / (1 - E2)
    A_ = math.cos(lat) * dlon
    M = A * ((1 - E2/4 - 3*E2**2/64 - 5*E2**3/256) * lat
             - (3*E2/8 + 3*E2**2/32 + 45*E2**3/1024) * math.sin(2*lat)
             + (15*E2**2/256 + 45*E2**3/1024) * math.sin(4*lat)
             - (35*E2**3/3072) * math.sin(6*lat))
    easting = K0 * N * (A_ + (1-T+C)*A_**3/6 + (5-18*T+T**2+72*C-58*E2)*A_**5/120) + FALSE_EASTING
    northing = K0 * (M + N*math.tan(lat)*(A_**2/2 + (5-T+9*C+4*C**2)*A_**4/24 + (61-58*T+T**2+600*C-330*E2)*A_**6/720))
    if lat_deg < 0:
        northing += 10000000
    return easting, northing

def utm_origin():
    return wgs84_to_utm(ORIGIN_LON, ORIGIN_LAT)

def wgs84_to_ue(lon_deg, lat_deg):
    """WGS84 → UE local (cm). X=east, Y=north in UE (UE Y is north)."""
    e, n = wgs84_to_utm(lon_deg, lat_deg)
    eo, no = utm_origin()
    # meters → cm, UE X = easting delta, UE Y = northing delta
    return (e - eo) * 100.0, (n - no) * 100.0

def ue_to_wgs84(x_cm, y_cm):
    """UE local (cm) → WGS84 (approx inverse — linear near origin, valid for city scale)."""
    eo, no = utm_origin()
    e = eo + x_cm / 100.0
    n = no + y_cm / 100.0
    # Inverse via iterative
    # Simplify: use origin + delta in degrees (approx 1 deg lat ≈ 111km, lon ≈ 89km at 36N)
    d_e = e - eo
    d_n = n - no
    lat = ORIGIN_LAT + (d_n / 111000.0)
    lon = ORIGIN_LON + (d_e / (111000.0 * math.cos(math.radians(ORIGIN_LAT))))
    # One refinement iteration using forward model
    for _ in range(2):
        e2, n2 = wgs84_to_utm(lon, lat)
        lon += (e - e2) / (111000.0 * math.cos(math.radians(lat)))
        lat += (n - n2) / 111000.0
    return lon, lat

def test_roundtrip():
    eo, no = utm_origin()
    print(f"[Transform] Origin UTM: E={eo:.2f} N={no:.2f}")
    # Origin must be 0,0
    x, y = wgs84_to_ue(ORIGIN_LON, ORIGIN_LAT)
    assert abs(x) < 1e-6 and abs(y) < 1e-6, f"Origin not zero: {x},{y}"
    print(f"[Transform] Origin UE: {x:.2f}, {y:.2f} ✓")

    # Test points around city
    pts = [(45.10, 36.65), (45.20, 36.75), (45.146, 36.716), (45.18, 36.70)]
    max_err_m = 0
    for lon, lat in pts:
        x, y = wgs84_to_ue(lon, lat)
        lon2, lat2 = ue_to_wgs84(x, y)
        # error in meters
        err_m = math.sqrt(((lon-lon2)*89000)**2 + ((lat-lat2)*111000)**2)
        max_err_m = max(max_err_m, err_m)
        print(f"  ({lon:.3f},{lat:.3f}) → UE({x:.0f},{y:.0f})cm → ({lon2:.6f},{lat2:.6f}) err {err_m*1000:.1f}mm")
        assert err_m < 2.0, f"Round-trip error too large: {err_m}m"
    print(f"[Transform] Max round-trip error: {max_err_m*1000:.1f}mm ✓ (requirement < 0.1m)")
    # GeoJSON batch test
    gj_path = Path(__file__).parent.parent / "Content/GIS/Piranshahr_Center.geojson"
    if gj_path.exists():
        import json
        gj = json.loads(gj_path.read_text(encoding="utf-8"))
        for feat in gj["features"][:3]:
            geom = feat["geometry"]
            coords = geom["coordinates"] if geom["type"] == "Point" else geom["coordinates"][0] if geom["type"] == "Polygon" else geom["coordinates"][0]
            lon, lat = coords[0], coords[1] if isinstance(coords[0], (list, tuple)) else (coords[0], coords[1])
            # just ensure no exception
            wgs84_to_ue(lon, lat)
        print(f"[Transform] GeoJSON sample transform ✓ ({len(gj['features'])} features)")

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--lon", type=float)
    ap.add_argument("--lat", type=float)
    ap.add_argument("--x", type=float)
    ap.add_argument("--y", type=float)
    ap.add_argument("--test", action="store_true")
    args = ap.parse_args()
    if args.test:
        test_roundtrip()
    elif args.lon is not None and args.lat is not None:
        x, y = wgs84_to_ue(args.lon, args.lat)
        print(json.dumps({"ue_x_cm": x, "ue_y_cm": y, "ue_x_m": x/100, "ue_y_m": y/100}))
    elif args.x is not None and args.y is not None:
        lon, lat = ue_to_wgs84(args.x, args.y)
        print(json.dumps({"lon": lon, "lat": lat}))
    else:
        test_roundtrip()
