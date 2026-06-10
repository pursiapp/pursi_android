import { promises as fs } from 'node:fs';
import { join } from 'node:path';
import 'tsx/esm';

const { BuoyBeaconLxComponent } = await import('./src/buoy-beacon-lx.tsx');
const { svgToString } = await import('./src/util/svgToString.ts');

const ICONS = JSON.parse(await fs.readFile(join(import.meta.dirname, 'icons.json'), 'utf-8'));
const SCALE = 3;
const OUT_DIR = '/tmp/pursi-navmarks';

const mode = process.argv[2] || 'both'; // 'day', 'night', 'both'

function nightify(svg) {
  const glowFilter = `<filter id="night-glow" x="-100%" y="-100%" width="300%" height="300%">
    <feDropShadow dx="0" dy="0" stdDeviation="0.6" flood-color="white" flood-opacity="0.65"/>
  </filter>`;

  let out = svg;

  // convert dark strokes to white
  out = out
    .replace(/stroke="#000"/g, 'stroke="white"')
    .replace(/stroke="#000407"/g, 'stroke="white"')
    .replace(/stroke="#333"/g, 'stroke="white"');

  // slightly thicker strokes for night visibility
  out = out.replace(/stroke-width="0\.3"/g, 'stroke-width="0.5"');

  // keep mast slightly lighter
  out = out.replace(/fill="#999"/g, 'fill="#bbb"');

  // inject glow filter into defs
  if (out.includes('<defs>')) {
    out = out.replace('<defs>', `<defs>\n${glowFilter}`);
  } else {
    out = out.replace(/<svg([^>]*)>/, `<svg$1>\n  <defs>\n${glowFilter}\n  </defs>`);
  }

  // wrap all content after defs in a glow group
  out = out.replace('</defs>', '</defs>\n<g filter="url(#night-glow)">');
  out = out.replace('</svg>', '</g>\n</svg>');

  return out;
}

function buildSvg(comp) {
  const w = comp.width * SCALE;
  const h = comp.height * SCALE;
  let xml = svgToString(comp.svg).replace(/^<\?xml[^?]*\?>/, '').trim();
  let defs = '';
  if (comp.defs) {
    defs = Object.values(comp.defs)
      .map(d => svgToString(d).replace(/^<\?xml[^?]*\?>/, '').trim())
      .join('\n');
  }
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${comp.width} ${comp.height}" width="${w}" height="${h}">`,
    defs ? `  <defs>\n${defs}\n  </defs>` : '',
    xml,
    '</svg>',
  ].join('\n');
}

async function writeIcons(dirName, transform) {
  const outDir = join(OUT_DIR, dirName);
  await fs.mkdir(outDir, { recursive: true });
  let count = 0;
  for (const { name, tags } of ICONS) {
    try {
      const svg = buildSvg(BuoyBeaconLxComponent(tags));
      await fs.writeFile(join(outDir, `${name}.svg`), transform ? transform(svg) : svg);
      count++;
    } catch (e) {
      process.stdout.write('x');
    }
  }
  return count;
}

async function main() {
  console.log(`Mode: ${mode}`);
  if (mode === 'day' || mode === 'both') {
    const c = await writeIcons('navmarks');
    console.log(`Day: ${c} icons`);
  }
  if (mode === 'night' || mode === 'both') {
    const c = await writeIcons('navmarks-night', nightify);
    console.log(`Night: ${c} icons`);
  }
}

main().catch(console.error);
