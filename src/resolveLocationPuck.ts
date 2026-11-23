import { Image } from 'react-native'

import type {
  LocationPuck2D,
  LocationPuck3D,
  LocationPuckAppearance,
  LocationPuckAssetSource,
  LocationPuckConfig,
  LocationPuckTinted,
} from './MapboxNavigation.types'

/**
 * The flattened, bridge-safe shape handed to the native layer.
 *
 * Every appearance is normalized here rather than natively so that iOS and
 * Android receive identical, already-validated values.
 */
export type NativeLocationPuck = {
  type: '3d' | '2d' | 'tinted' | 'none' | 'default'
  modelUri?: string
  modelScale?: [number, number, number]
  modelRotation?: [number, number, number]
  modelTranslation?: [number, number, number]
  scale?: number
  scaleExpression?: string
  opacity?: number
  topImage?: string
  bearingImage?: string
  shadowImage?: string
  color?: string
  haloColor?: string
  bearingColor?: string
}

/** Navigation states a puck can be bound to, in native key order. */
export const LOCATION_PUCK_STATE_KEYS = [
  'default',
  'freeDrive',
  'destinationPreview',
  'routePreview',
  'activeNavigation',
  'arrival',
  'idle',
] as const

export type NativeLocationPuckOptions = Partial<
  Record<(typeof LOCATION_PUCK_STATE_KEYS)[number], NativeLocationPuck>
>

const APPEARANCE_TYPES = new Set(['3d', '2d', 'tinted', 'none', 'default'])

export function resolveAssetSource(
  source: LocationPuckAssetSource | undefined
): string | undefined {
  if (source == null) {
    return undefined
  }

  if (typeof source === 'number') {
    const resolved = Image.resolveAssetSource(source)
    const uri = resolved?.uri?.trim()
    return uri && uri.length > 0 ? uri : undefined
  }

  if (typeof source === 'string') {
    const trimmed = source.trim()
    return trimmed.length > 0 ? trimmed : undefined
  }

  if (typeof source === 'object' && typeof source.uri === 'string') {
    const trimmed = source.uri.trim()
    return trimmed.length > 0 ? trimmed : undefined
  }

  return undefined
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(value, max))
}

function finiteOrUndefined(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function normalizeOpacity(value: unknown): number | undefined {
  const numeric = finiteOrUndefined(value)
  return numeric == null ? undefined : clamp(numeric, 0, 1)
}

function normalizeVector3(
  value: number | [number, number, number] | undefined,
  fallback?: number
): [number, number, number] | undefined {
  if (Array.isArray(value)) {
    const [x, y, z] = value
    const nx = finiteOrUndefined(x)
    const ny = finiteOrUndefined(y)
    const nz = finiteOrUndefined(z)
    if (nx == null || ny == null || nz == null) {
      return undefined
    }
    return [nx, ny, nz]
  }

  const uniform = finiteOrUndefined(value) ?? fallback
  if (uniform == null) {
    return undefined
  }
  return [uniform, uniform, uniform]
}

function normalizeHexColor(value: unknown): string | undefined {
  if (typeof value !== 'string') {
    return undefined
  }
  const trimmed = value.trim()
  if (!/^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(trimmed)) {
    return undefined
  }
  return trimmed
}

function normalizeScaleExpression(value: unknown): string | undefined {
  if (typeof value !== 'string') {
    return undefined
  }
  const trimmed = value.trim()
  if (trimmed.length === 0) {
    return undefined
  }
  try {
    const parsed = JSON.parse(trimmed)
    return Array.isArray(parsed) ? trimmed : undefined
  } catch {
    return undefined
  }
}

function normalize3D(appearance: LocationPuck3D): NativeLocationPuck | undefined {
  const modelUri = resolveAssetSource(appearance.modelUri)
  if (!modelUri) {
    return undefined
  }

  return {
    type: '3d',
    modelUri,
    modelScale: normalizeVector3(appearance.scale, 1),
    modelRotation: normalizeVector3(appearance.rotation),
    modelTranslation: normalizeVector3(appearance.translation),
    scaleExpression: normalizeScaleExpression(appearance.scaleExpression),
    opacity: normalizeOpacity(appearance.opacity),
  }
}

function normalize2D(appearance: LocationPuck2D): NativeLocationPuck | undefined {
  const topImage = resolveAssetSource(appearance.topImage)
  const bearingImage = resolveAssetSource(appearance.bearingImage)
  const shadowImage = resolveAssetSource(appearance.shadowImage)

  if (!topImage && !bearingImage && !shadowImage) {
    return undefined
  }

  const scale = finiteOrUndefined(appearance.scale)

  return {
    type: '2d',
    topImage,
    bearingImage,
    shadowImage,
    scale: scale == null ? undefined : clamp(scale, 0.05, 20),
    scaleExpression: normalizeScaleExpression(appearance.scaleExpression),
    opacity: normalizeOpacity(appearance.opacity),
  }
}

function normalizeTinted(appearance: LocationPuckTinted): NativeLocationPuck {
  const scale = finiteOrUndefined(appearance.scale)

  return {
    type: 'tinted',
    color: normalizeHexColor(appearance.color),
    haloColor: normalizeHexColor(appearance.haloColor),
    bearingColor: normalizeHexColor(appearance.bearingColor),
    scale: scale == null ? undefined : clamp(scale, 0.05, 20),
    opacity: normalizeOpacity(appearance.opacity),
  }
}

function normalizeAppearance(value: unknown): NativeLocationPuck | undefined {
  if (value == null || typeof value !== 'object') {
    return undefined
  }

  const appearance = value as LocationPuckAppearance
  switch (appearance.type) {
    case '3d':
      return normalize3D(appearance)
    case '2d':
      return normalize2D(appearance)
    case 'tinted':
      return normalizeTinted(appearance)
    case 'none':
      return { type: 'none' }
    case 'default':
      return { type: 'default' }
    default:
      return undefined
  }
}

function isAppearance(value: LocationPuckConfig): value is LocationPuckAppearance {
  const type = (value as LocationPuckAppearance).type
  return typeof type === 'string' && APPEARANCE_TYPES.has(type)
}

/**
 * Normalize a public {@link LocationPuckConfig} into the flat per-state shape
 * consumed by the native views.
 *
 * Returns `undefined` when nothing usable was supplied, which leaves the native
 * layer on the Mapbox SDK defaults.
 */
export function resolveLocationPuck(
  config: LocationPuckConfig | undefined
): NativeLocationPuckOptions | undefined {
  if (config == null || typeof config !== 'object') {
    return undefined
  }

  if (isAppearance(config)) {
    const resolved = normalizeAppearance(config)
    return resolved ? { default: resolved } : undefined
  }

  const states = config
  const output: NativeLocationPuckOptions = {}
  let hasAny = false

  for (const key of LOCATION_PUCK_STATE_KEYS) {
    const resolved = normalizeAppearance(states[key])
    if (resolved) {
      output[key] = resolved
      hasAny = true
    }
  }

  return hasAny ? output : undefined
}

export function resolveAssetScale(source: LocationPuckAssetSource | undefined): number | undefined {
  if (typeof source !== 'number') {
    return undefined
  }
  const scale = Image.resolveAssetSource(source)?.scale
  return typeof scale === 'number' && scale > 0 ? scale : undefined
}
