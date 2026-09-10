export function careLabel(value: string) {
  return value.toLowerCase().replaceAll('_', ' ').replace(/^./, char => char.toUpperCase())
}
