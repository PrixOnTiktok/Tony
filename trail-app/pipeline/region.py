"""The app's coverage area: a 100 km circle around Bancroft, Ontario."""
import math

CENTER = (45.0575, -77.8553)  # lat, lon (Bancroft)
RADIUS_KM = 100.0

_dlat = RADIUS_KM / 111.32
_dlon = RADIUS_KM / (111.32 * math.cos(math.radians(CENTER[0])))
BBOX = (round(CENTER[1] - _dlon, 4), round(CENTER[0] - _dlat, 4),
        round(CENTER[1] + _dlon, 4), round(CENTER[0] + _dlat, 4))  # W, S, E, N


def in_region(lat, lon, margin_km=2.0):
    dy = (lat - CENTER[0]) * 111.32
    dx = (lon - CENTER[1]) * 111.32 * math.cos(math.radians(CENTER[0]))
    return dx * dx + dy * dy <= (RADIUS_KM + margin_km) ** 2
