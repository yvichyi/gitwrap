// Renders a multi-pass WebGL2 pipeline in headless Chromium and saves the result as PNG.
// Usage: node render.js <pipeline.json> <out.png>
// pipeline.json: { "w":780, "h":960, "uniforms":{...}, "passes":[{ "frag":"file.glsl", "scale":1, "inputs":["passname",...], "name":"..." }] }
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const [cfgPath, out] = process.argv.slice(2);
  const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
  const dir = path.dirname(cfgPath);
  const common = fs.existsSync(path.join(dir, 'common.glsl')) ? fs.readFileSync(path.join(dir, 'common.glsl'), 'utf8') : '';
  for (const p of cfg.passes) p.src = fs.readFileSync(path.join(dir, p.frag), 'utf8').replace('#include common', common);
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const pg = await b.newPage();
  pg.on('console', m => console.log('page:', m.text()));
  await pg.setContent('<canvas id=c></canvas>');
  const data = await pg.evaluate((cfg) => {
    const c = document.getElementById('c'); c.width = cfg.w; c.height = cfg.h;
    const gl = c.getContext('webgl2', { preserveDrawingBuffer: true });
    gl.getExtension('EXT_color_buffer_float'); gl.getExtension('OES_texture_float_linear');
    const vs = `#version 300 es
in vec2 p; void main(){ gl_Position = vec4(p,0.,1.); }`;
    const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 3,-1, -1,3]), gl.STATIC_DRAW);
    const targets = {};
    for (const pass of cfg.passes) {
      const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o); if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(pass.frag + ': ' + gl.getShaderInfoLog(o)); return o; };
      const prog = gl.createProgram(); gl.attachShader(prog, sh(gl.VERTEX_SHADER, vs)); gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, pass.src)); gl.linkProgram(prog);
      if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
      gl.useProgram(prog);
      const w = Math.round(cfg.w * (pass.scale || 1)), h = Math.round(cfg.h * (pass.scale || 1));
      let fb = null, tex = null;
      if (!pass.final) {
        tex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, tex);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA16F, w, h, 0, gl.RGBA, gl.HALF_FLOAT, null);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
        fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
        gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
      } else gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      gl.viewport(0, 0, w, h);
      const loc = gl.getAttribLocation(prog, 'p'); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
      gl.uniform2f(gl.getUniformLocation(prog, 'R'), w, h);
      for (const [k, v] of Object.entries(Object.assign({}, cfg.uniforms, pass.uniforms || {}))) {
        const l = gl.getUniformLocation(prog, k); if (!l) continue;
        if (Array.isArray(v)) gl['uniform' + v.length + 'fv'](l, v); else gl.uniform1f(l, v);
      }
      (pass.inputs || []).forEach((n, i) => { gl.activeTexture(gl.TEXTURE0 + i); gl.bindTexture(gl.TEXTURE_2D, targets[n]); gl.uniform1i(gl.getUniformLocation(prog, 'iChannel' + i), i); });
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      if (!pass.final) targets[pass.name] = tex;
    }
    gl.finish();
    return c.toDataURL('image/png');
  }, cfg);
  fs.writeFileSync(out, Buffer.from(data.split(',')[1], 'base64'));
  await b.close();
})().catch(e => { console.error(e.message); process.exit(1); });
