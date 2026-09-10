// Exact internal destinations only: never accept protocols, queries or encoded redirects.
export function safeReturnPath(value: unknown): string | null {
  return typeof value === 'string' && (/^\/(staff(?:\/care)?|applications(?:\/[1-9]\d*)?|cats\/[1-9]\d*)$/.test(value)) ? value : null
}
