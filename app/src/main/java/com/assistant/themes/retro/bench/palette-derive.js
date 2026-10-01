// Every colour of a retro palette, derived from its numbers (palettes.json).
//
// The bench draws with this, and writes what it derives into palettes.json beside the numbers,
// as "#rrggbb": the colours the theme has to derive from the same numbers.
//
// A palette is one register: a ground, the ink on it, and the inside of a frame (a panel, with
// its own ink), each with the two tones of a frame's border. Colours are stated in OKLCH and
// mixed in OKLab; a colour sRGB cannot show loses chroma, never hue.
"use strict";

(function (root) {

  /** The faint tint of the register's hue that every ink carries. */
  const INK_CHROMA = 0.018;
  /** The chroma of a strong ink: a little more than the ink's, so it reads as the same ink, lit. */
  const STRONG_CHROMA = 0.03;
  /** A border's chroma never falls under this, or a frame on a grey ground would be grey too. */
  const DECOR_MIN_CHROMA = 0.045;
  /** The outer tone of a border turns this far from the hue, for the relief of two tones. */
  const OUTER_HUE_TURN = 14;
  /** A grey tag keeps a trace of chroma, so it is grey of this palette. */
  const GREY_CHROMA = 0.015;

  /** Tag colours: one lightness and chroma for all, their hues spread round the circle. */
  const TAG_HUES = { red: 25, orange: 55, yellow: 95, green: 145, teal: 185, blue: 250, purple: 300, pink: 350 };
  /** States, on the hues their meaning expects. Muted is the dim ink. */
  const STATUS_HUES = { success: 145, warning: 70, error: 25, info: 250 };

  const clamp01 = (v) => Math.max(0, Math.min(1, v));

  function oklabToLinear(L, C, H) {
    const hr = H * Math.PI / 180;
    const a = C * Math.cos(hr), b = C * Math.sin(hr);
    const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
    const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
    const s_ = L - 0.0894841775 * a - 1.2914855480 * b;
    const l = l_ * l_ * l_, m = m_ * m_ * m_, s = s_ * s_ * s_;
    return [+4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s];
  }

  const inGamut = (rgb) => rgb.every((v) => v >= -0.0005 && v <= 1.0005);

  /** "#rrggbb" for an OKLCH colour, its chroma reduced until sRGB can show it. */
  function hex(lch) {
    const L = clamp01(lch[0]), C = Math.max(0, lch[1]), H = ((lch[2] % 360) + 360) % 360;
    let chroma = C;
    if (!inGamut(oklabToLinear(L, C, H))) {
      let lo = 0, hi = C;
      for (let i = 0; i < 18; i++) {
        const mid = (lo + hi) / 2;
        if (inGamut(oklabToLinear(L, mid, H))) lo = mid; else hi = mid;
      }
      chroma = lo;
    }
    const enc = (x) => {
      x = clamp01(x);
      x = x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - 0.055;
      return Math.round(x * 255).toString(16).padStart(2, "0");
    };
    return "#" + oklabToLinear(L, chroma, H).map(enc).join("");
  }

  /** How far apart two colours look: their distance in OKLab. */
  function apart(p, q) {
    const ab = (c) => [c[1] * Math.cos(c[2] * Math.PI / 180), c[1] * Math.sin(c[2] * Math.PI / 180)];
    const [a1, b1] = ab(p), [a2, b2] = ab(q);
    return Math.hypot(clamp01(p[0]) - clamp01(q[0]), a1 - a2, b1 - b2);
  }

  /**
   * One surface: a ground, its inks, its border, its states. [up] is the way away from the
   * ground: lighter off a dark ground, darker off a pale one.
   */
  function surface(n, hue, groundL, groundC, inkL, outerStep, innerStep, statusL) {
    const up = groundL < 0.5 ? 1 : -1;
    const decor = Math.max(groundC, DECOR_MIN_CHROMA);
    const dim = [inkL - n.dim_step * up, INK_CHROMA, hue];
    const out = {
      ground: [groundL, groundC, hue],
      ink: [inkL, INK_CHROMA, hue],
      dim: dim,
      strong: [clamp01(inkL + n.strong_step * up), STRONG_CHROMA, hue],
      border_outer: [groundL + outerStep, decor, hue + OUTER_HUE_TURN],
      border_inner: [groundL + innerStep, decor, hue],
      status_muted: dim,
    };
    for (const [name, h] of Object.entries(STATUS_HUES)) out["status_" + name] = [statusL, n.status_chroma, h];
    return out;
  }

  /** Every named colour of a palette's numbers, as OKLCH triples. */
  function derive(n) {
    const h = n.hue;
    const out = {};
    const screen = surface(n, h, n.ground_lightness, n.ground_chroma, n.ink_lightness,
      n.border_outer_step, n.border_inner_step, n.status_lightness);
    const panel = surface(n, h, n.panel_lightness, n.panel_chroma, n.panel_ink_lightness,
      n.panel_border_outer_step, n.panel_border_inner_step, n.panel_status_lightness);
    for (const [k, v] of Object.entries(screen)) out["screen_" + k] = v;
    for (const [k, v] of Object.entries(panel)) out["panel_" + k] = v;
    for (const [name, th] of Object.entries(TAG_HUES)) out["tag_" + name] = [n.tag_lightness, n.tag_chroma, th];
    out.tag_grey = [n.tag_lightness, GREY_CHROMA, h];
    // A tag's name reads on its colour: dark letters on a light tag, light ones on a dark tag.
    out.tag_text = n.tag_lightness >= 0.6 ? [0.25, STRONG_CHROMA, h] : [0.97, INK_CHROMA, h];
    return out;
  }

  /** The same, as "#rrggbb": what palettes.json keeps beside the numbers. */
  function deriveHex(n) {
    const out = {};
    for (const [k, v] of Object.entries(derive(n))) out[k] = hex(v);
    return out;
  }

  const api = { derive, deriveHex, hex, apart, TAG_HUES, STATUS_HUES };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.PaletteDerive = api;
})(this);
