// Local replacement for jslib's randomString.
//
// The three load scripts used to import it from https://jslib.k6.io/..., which made every load run
// depend on a CDN being reachable: a TLS handshake timeout there turned into "NO DATA / could not
// parse k6 summary" and looked like a platform failure (observed twice on 2026-09-14). The helper is
// four lines, so the gate now runs with no network dependency beyond the stack under test.
const ALPHABET = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';

export function randomString(length) {
    let value = '';
    for (let i = 0; i < length; i += 1) {
        value += ALPHABET.charAt(Math.floor(Math.random() * ALPHABET.length));
    }
    return value;
}
