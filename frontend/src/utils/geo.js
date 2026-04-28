// Helpers for working with GeoJSON polygons on the map.
// Used to derive a map center/bounds before rendering Leaflet, so the map
// never opens on a wrong default location.

function parseGeoJson(input) {
  if (!input) return null
  try {
    return typeof input === 'string' ? JSON.parse(input) : input
  } catch {
    return null
  }
}

function* iterPositions(geometry) {
  if (!geometry) return
  const { type, coordinates } = geometry
  if (type === 'Point') yield coordinates
  else if (type === 'MultiPoint' || type === 'LineString') yield* coordinates
  else if (type === 'Polygon' || type === 'MultiLineString') for (const ring of coordinates) yield* ring
  else if (type === 'MultiPolygon') for (const poly of coordinates) for (const ring of poly) yield* ring
}

function collectPositions(geo) {
  const out = []
  if (!geo) return out
  if (geo.type === 'FeatureCollection') {
    for (const f of geo.features || []) for (const p of iterPositions(f.geometry)) out.push(p)
  } else if (geo.type === 'Feature') {
    for (const p of iterPositions(geo.geometry)) out.push(p)
  } else {
    for (const p of iterPositions(geo)) out.push(p)
  }
  return out
}

/** Returns [lat, lng] centroid of a GeoJSON object, or null if it can't be computed. */
export function centroidOf(geoJson) {
  const geo = parseGeoJson(geoJson)
  const positions = collectPositions(geo)
  if (positions.length === 0) return null
  let sx = 0, sy = 0
  for (const [x, y] of positions) { sx += x; sy += y }
  const lng = sx / positions.length
  const lat = sy / positions.length
  return [lat, lng]
}

/** Returns [[south, west], [north, east]] bounds, or null if it can't be computed. */
export function boundsOf(geoJson) {
  const geo = parseGeoJson(geoJson)
  const positions = collectPositions(geo)
  if (positions.length === 0) return null
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (const [x, y] of positions) {
    if (x < minX) minX = x; if (x > maxX) maxX = x
    if (y < minY) minY = y; if (y > maxY) maxY = y
  }
  return [[minY, minX], [maxY, maxX]]
}
