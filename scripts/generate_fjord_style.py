#!/usr/bin/env python3
"""
Generate a dark ('fjord') variant of Pursi's vector style for night mode.
Transforms base map layer colors (land, water, roads, buildings, labels) to dark.
Preserves all seamark layers (source=seamarks) unchanged so navigational
markers remain bright and visible on dark water.

Run: python3 scripts/generate_fjord_style.py
"""
import json, re, sys
from colorsys import rgb_to_hls, hls_to_rgb

def parse_color(s):
    s = s.strip().lower()
    m = re.fullmatch(r'#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})', s)
    if m: return (int(m.group(1),16)/255, int(m.group(2),16)/255, int(m.group(3),16)/255, 1.0)
    m = re.fullmatch(r'#([0-9a-f])([0-9a-f])([0-9a-f])', s)
    if m: return (int(m.group(1)+m.group(1),16)/255, int(m.group(2)+m.group(2),16)/255, int(m.group(3)+m.group(3),16)/255, 1.0)
    m = re.fullmatch(r'rgb\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)', s)
    if m: return (int(m.group(1))/255, int(m.group(2))/255, int(m.group(3))/255, 1.0)
    m = re.fullmatch(r'rgba\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([0-9.]+)\s*\)', s)
    if m: return (int(m.group(1))/255, int(m.group(2))/255, int(m.group(3))/255, float(m.group(4)))
    m = re.fullmatch(r'hsl\s*\(\s*([0-9.]+)\s*,\s*([0-9.]+)%\s*,\s*([0-9.]+)%\s*\)', s)
    if m:
        h, sp, lp = float(m.group(1))/360, float(m.group(2))/100, float(m.group(3))/100
        r,g,b = hls_to_rgb(h, lp, sp)
        return (r,g,b,1.0)
    m = re.fullmatch(r'hsla\s*\(\s*([0-9.]+)\s*,\s*([0-9.]+)%\s*,\s*([0-9.]+)%\s*,\s*([0-9.]+)\s*\)', s)
    if m:
        h, sp, lp, a = float(m.group(1))/360, float(m.group(2))/100, float(m.group(3))/100, float(m.group(4))
        r,g,b = hls_to_rgb(h, lp, sp)
        return (r,g,b,a)
    return None

def fmt_hex(r,g,b,a):
    if a < 1.0:
        return f'rgba({int(r*255)},{int(g*255)},{int(b*255)},{a:.2f})'
    return f'#{int(r*255):02x}{int(g*255):02x}{int(b*255):02x}'

def dark_transform(r,g,b,a):
    h, l, s = rgb_to_hls(r, g, b)
    if l > 0.85 and s < 0.1:
        nl = max(0.08, 0.12 - (l - 0.85))
        nr,ng,nb = hls_to_rgb(h, nl, s * 0.5)
        return (nr,ng,nb,a)
    if l > 0.6:
        nl = 0.08 + (1.0 - l) * 0.25
        nr,ng,nb = hls_to_rgb(h, nl, s * 0.6)
        return (nr,ng,nb,a)
    if l > 0.3:
        nl = l * 0.35
        nr,ng,nb = hls_to_rgb(h, nl, s * 0.7)
        return (nr,ng,nb,a)
    if l < 0.15:
        nl = min(0.25, l + 0.08)
        nr,ng,nb = hls_to_rgb(h, nl, s)
        return (nr,ng,nb,a)
    nl = l * 0.5
    nr,ng,nb = hls_to_rgb(h, nl, s * 0.8)
    return (nr,ng,nb,a)

OVERRIDES = {
    '#d4e7f7': '#0a1628',
    '#a0c8f0': '#0f1f30',
    'rgb(158,189,255)': 'rgb(15,31,48)',
    '#fff': '#1a1a1a',
    '#ffffff': '#1a1a1a',
    '#fff4c6': '#2a2520',
    '#ffdaa6': '#2a2520',
    '#fc8': '#2a2520',
    '#fea': '#2a2520',
    '#e9ac77': '#3a3028',
    '#cfcdca': '#2a2a2a',
    '#f0ede9': '#1a1a1a',
    '#e5e0db': '#1a1a1a',
    '#bbb': '#333333',
    '#666': '#888888',
    '#333': '#555555',
    '#000': '#1a1a1a',
    '#444': '#aaaaaa',
    '#fde': '#2a1a1a',
}

def transform_color_str(s):
    if s in OVERRIDES: return OVERRIDES[s]
    p = parse_color(s)
    if p is None: return s
    nr,ng,nb,na = dark_transform(*p)
    return fmt_hex(nr,ng,nb,na)

def transform_value(v):
    if isinstance(v, str):
        if parse_color(v) is not None: return transform_color_str(v)
        return v
    elif isinstance(v, list): return [transform_value(x) for x in v]
    elif isinstance(v, dict): return {k: transform_value(x) for k, x in v.items()}
    return v

def is_seamark_layer(layer):
    """Return True if this layer should be preserved unchanged."""
    if layer.get('source') == 'seamarks': return True
    lid = layer.get('id', '')
    if lid in ('layer-seamark-bottom',): return True
    return False

def transform_layer(layer):
    if is_seamark_layer(layer): return dict(layer)
    nl = dict(layer)
    for key in list(nl.keys()):
        if key == 'paint':
            paint = dict(nl['paint'])
            for pk, pv in paint.items():
                paint[pk] = transform_value(pv)
            nl['paint'] = paint
        elif key in ('layout', 'filter'):
            nl[key] = transform_value(nl[key])
    return nl

def main():
    src = 'app/src/main/assets/pursi_style_vector.json'
    dst = 'app/src/main/assets/pursi_style_fjord.json'
    with open(src) as f: style = json.load(f)
    style['name'] = 'Pursi Marine Vector — Fjord Night'
    style['layers'] = [transform_layer(l) for l in style['layers']]
    with open(dst, 'w') as f: json.dump(style, f, indent=2, ensure_ascii=False)
    print(f'Wrote {dst}')

if __name__ == '__main__':
    main()
