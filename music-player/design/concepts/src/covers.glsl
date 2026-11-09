#version 300 es
precision highp float;
uniform vec2 R;
uniform float uMode;
uniform float uSeed;
out vec4 o;
#include common

vec3 aces(vec3 x) { return clamp((x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14), 0., 1.); }

// Out-of-focus lights with a slightly hexagonal aperture.
float bokeh(vec2 p, vec2 c, float r) {
  vec2 d = abs(p - c);
  float hex = max(d.x * .866 + d.y * .5, d.y);
  float circ = length(p - c);
  float s = mix(circ, hex, .35);
  return smoothstep(r, r * .92, s) * (.75 + .25 * smoothstep(r * .6, r, s));
}

void main() {
  vec2 uv = gl_FragCoord.xy / R;
  vec2 p = uv - .5;
  vec3 c = vec3(0);
  if (uMode < .5) {
    // A rainy night through glass: blurred city lights.
    c = mix(vec3(.010, .016, .022), vec3(.035, .030, .032), uv.y);
    for (int i = 0; i < 46; i++) {
      float fi = float(i);
      vec2 cc = vec2(h11(fi * 3.1 + uSeed), h11(fi * 7.7 + uSeed) * .8 + .1);
      float r = .03 + .09 * pow(h11(fi * 1.3 + uSeed), 2.);
      float k = h11(fi * 5.5 + uSeed);
      vec3 col = k < .5 ? vec3(1., .55, .2) : k < .75 ? vec3(1., .12, .08) : k < .9 ? vec3(.5, .8, .9) : vec3(1., .9, .8);
      c += col * bokeh(uv, cc, r) * mix(.03, .22, h11(fi * 9.1)) * smoothstep(.0, .5, uv.y + .2);
    }
  } else if (uMode < 1.5) {
    // Moon over a dark sea, a path of light on the water.
    float hz = .38;
    c = mix(vec3(.02, .05, .07), vec3(.008, .016, .024), smoothstep(hz, 1., uv.y));
    vec2 mc = vec2(.62, .7);
    float md = length(uv - mc);
    c += vec3(.9, .92, .85) * smoothstep(.085, .08, md);
    c += vec3(.25, .3, .32) * exp(-md * 9.) * .6;
    c += vec3(.02) * fbm(uv * vec2(3., 12.) + 5.) * step(hz, uv.y);
    if (uv.y < hz) {
      float w = fbm(vec2(uv.x * 40., uv.y * 300.));
      float path = exp(-pow((uv.x - mc.x) / (.05 + (hz - uv.y) * .5), 2.));
      c = vec3(.006, .014, .02) + vec3(.8, .85, .8) * path * smoothstep(.55, .75, w) * .9;
      c += vec3(.05, .08, .09) * smoothstep(.6, .8, w) * .3;
    }
  } else if (uMode < 2.5) {
    // Soft dunes of colour, like a slow song.
    float a = fbm(p * 2.2 + uSeed);
    float b = fbm(p * 3. + 9. + uSeed);
    vec3 c1 = vec3(.62, .30, .26), c2 = vec3(.10, .22, .28), c3 = vec3(.86, .62, .44);
    c = mix(c2, c1, smoothstep(.3, .7, a + p.y * .6));
    c = mix(c, c3, smoothstep(.55, .85, b - p.y * .4) * .7);
    c *= .55;
  } else if (uMode < 3.5) {
    // A city from far above at night: streets of sodium light and dark blocks.
    vec2 q = uv * 9. + uSeed;
    vec2 g = fract(q), id = floor(q);
    float street = 1. - step(.08, g.x) * step(.08, g.y);
    c = vec3(.006, .008, .012);
    c += vec3(1., .5, .18) * street * .5 * (.5 + .5 * h21(id));
    vec2 w = fract(q * 6.);
    c += vec3(1., .8, .6) * step(.6, h21(floor(q * 6.))) * step(.3, w.x) * step(.3, w.y) * .25 * (1. - street);
    c *= .4 + .9 * smoothstep(.9, .1, length(p));
  } else {
    // A lighthouse beam sweeping through fog over the water.
    c = mix(vec3(.02, .03, .04), vec3(.05, .06, .07), uv.y);
    vec2 lh = vec2(.3, .42);
    float ang = atan(uv.y - lh.y, uv.x - lh.x);
    float beam = exp(-pow((ang - .25) * 7., 2.)) * step(lh.x, uv.x);
    c += vec3(.9, .85, .7) * beam * .35 * (.6 + .4 * fbm(uv * 6.));
    c += vec3(1., .9, .7) * exp(-length(uv - lh) * 60.) * 1.5;
    if (uv.y < .36) c = mix(c, vec3(.01, .014, .018), .8);
    float rock = step(uv.y, .36 + .03 * sin(uv.x * 9.) * step(uv.x, .45) + step(abs(uv.x - lh.x), .015) * step(uv.y, lh.y) * 1.);
    c = mix(c, vec3(.004), rock * step(uv.x, .5));
  }
  c = aces(c * 1.4);
  c = pow(c, vec3(.4545));
  c *= mix(.7, 1., smoothstep(.8, .2, length(p)));
  c += (h21(gl_FragCoord.xy + uMode) - .5) * .05;
  o = vec4(c, 1.);
}
