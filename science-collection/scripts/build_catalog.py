#!/usr/bin/env python3
"""Build the offline gallery and catalogue from catalog.json (stdlib only)."""
import argparse
import html
import json
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]


def render():
    data = json.loads((ROOT / 'catalog.json').read_text(encoding='utf-8'))
    items = data['items']
    if len({x['file'] for x in items}) != len(items):
        raise ValueError('Duplicate catalogue entries')
    source_files = {p.name for p in ROOT.glob('*.html') if p.name != 'index.html'}
    if {x['file'] for x in items} != source_files:
        raise ValueError('Catalogue must cover every work exactly once')
    groups = list(dict.fromkeys(x['category'] for x in items))
    esc = html.escape
    options = ''.join(f'<option value="{esc(g)}">{esc(g)}</option>' for g in groups)
    sections, markdown = [], ['# 作品目录', '', '此目录由 `catalog.json` 生成。难度取决于阅读深度；“建议尝试”提供第一步，“模型边界”说明不宜外推的范围。', '']
    for group in groups:
        cards = []
        markdown += [f'## {group}', '', '| 作品 | 核心问题 | 模型与方法 | 建议尝试 | 模型边界 |', '| --- | --- | --- | --- | --- |']
        for item in (x for x in items if x['category'] == group):
            search = ' '.join(item.values())
            href = quote(item['file'])
            cards.append(f'''<article class="card" data-category="{esc(group)}" data-search="{esc(search)}">
<h3><a href="{href}">{esc(item['title'])}<span aria-hidden="true"> ↗</span></a></h3>
<p class="question">{esc(item['question'])}</p><p class="model">{esc(item['model'])}</p>
<p class="try"><b>先试一试</b> {esc(item['try_this'])}</p>
<details><summary>模型边界</summary><p>{esc(item['limit'])}</p></details></article>''')
            markdown.append(f"| [{item['title']}](../{href}) | {item['question']} | {item['model']} | {item['try_this']} | {item['limit']} |")
        sections.append(f'<section class="category"><h2>{esc(group)} <small>{len(cards)} 件</small></h2><div class="grid">'+''.join(cards)+'</div></section>')
        markdown.append('')
    page = '''<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="WORK_COUNT 件独立运行的互动科学装置：从车流、光学和地震，到算法、随机性与证据。按主题浏览，并亲手改变一个条件。">
<title>互动科学装置 · 作品展厅</title>
<style>
:root{color-scheme:light;--paper:#f4f1e9;--ink:#213b37;--soft:#56645e;--line:#d8d8cc;--accent:#a7472b}*{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font:16px/1.75 system-ui,"PingFang SC","Microsoft YaHei",sans-serif}main{max-width:1240px;margin:auto;padding:32px 24px 64px}a{color:inherit;text-underline-offset:4px}a:hover{color:var(--accent)}:focus-visible{outline:3px solid var(--accent);outline-offset:4px}.skip{position:absolute;left:16px;top:-80px;background:white;padding:8px 16px}.skip:focus{top:12px;z-index:2}header{padding:24px 0 28px;border-bottom:1px solid var(--line)}.eyebrow{font-size:12px;letter-spacing:.14em;color:var(--accent)}h1{font-family:Georgia,"Songti SC",serif;font-size:clamp(36px,6vw,66px);line-height:1.2;margin:12px 0 18px;font-weight:500}header p{max-width:740px;color:var(--soft);margin:0}nav{display:flex;flex-wrap:wrap;gap:12px 24px;margin-top:20px;font-size:14px}.filters{display:grid;grid-template-columns:2fr 1fr auto;gap:16px;align-items:end;margin:28px 0 12px}label{display:grid;gap:6px;font-size:13px}input,select,button{font:inherit;min-height:44px;border:1px solid #a9b6ae;background:#fffdf7;color:var(--ink);padding:9px 12px;border-radius:4px;min-width:0}button{cursor:pointer}button:hover{background:#e3e8de}.result{font-size:14px;color:var(--soft)}.category{margin-top:36px}h2{font:500 26px/1.4 Georgia,"Songti SC",serif;margin:0 0 16px}h2 small{font:12px system-ui;color:var(--soft);margin-left:8px}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.card{min-width:0;padding:22px;background:#fffdf7;border:1px solid var(--line);border-top:3px solid #628576;border-radius:4px}h3{font:500 25px/1.35 Georgia,"Songti SC",serif;margin:0 0 12px}h3 a{text-decoration:none}h3 span{font:16px system-ui;color:var(--accent)}.question{margin:0 0 10px}.model{font-size:13px;color:var(--soft);margin:0 0 16px}.try{font-size:13px;border-top:1px solid var(--line);padding-top:12px}.try b{display:block;font-weight:600;margin-bottom:4px;color:var(--accent)}details{font-size:13px;color:var(--soft)}summary{cursor:pointer}details p{margin:8px 0 0}.empty{padding:24px;border:1px dashed var(--line)}footer{border-top:1px solid var(--line);padding-top:20px;margin-top:40px;color:var(--soft);font-size:13px}[hidden]{display:none!important}@media(max-width:900px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:600px){main{padding:16px 16px 40px}.grid{grid-template-columns:1fr}.filters{grid-template-columns:1fr}header{padding-top:18px}.card{padding:20px}nav{gap:8px 20px}}@media print{.filters,.skip{display:none}.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.card{break-inside:avoid}}@media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}}
</style></head><body><a class="skip" href="#works">跳到作品</a><main>
<header><div class="eyebrow">INTERACTIVE SCIENCE / WORK_COUNT WORKS</div><h1>改变一个条件，<br>让模型自己回答。</h1><p>WORK_COUNT 件互动科学装置，THEME_COUNT 个主题。每件作品都能单独打开：先动手，再看解释，最后检查答案成立的边界。</p><nav aria-label="推荐入口"><a href="拥堵之波.html">从车流开始</a><a href="马后炮·新生入口.html">两分钟理解缓存</a><a href="偏振之间.html">试试三片偏振片</a></nav></header>
<form class="filters" role="search"><label>搜索作品、问题或方法<input id="query" type="search" placeholder="例如：随机、光、Wasserstein" autocomplete="off"></label><label>按主题浏览<select id="category"><option value="">全部主题</option>OPTIONS</select></label><button type="reset">清除筛选</button></form>
<p class="result" id="result-count" role="status" aria-live="polite">显示 WORK_COUNT / WORK_COUNT 件作品</p><noscript><p>搜索需要 JavaScript；下方完整目录与所有作品链接仍可使用。</p></noscript>
<div id="works">SECTIONS</div><p id="empty" class="empty" hidden>没有找到匹配的作品。试试更短的关键词，或清除筛选。</p>
<footer>作品在本地计算，无需账户。实验用来解释机制；请结合各页模型说明理解结果。</footer></main>
<script>
(() => {
  const form = document.querySelector('form'), query = document.getElementById('query'), category = document.getElementById('category');
  const cards = [...document.querySelectorAll('.card')], sections = [...document.querySelectorAll('.category')];
  const normalize = text => text.normalize('NFKC').toLocaleLowerCase();
  cards.forEach(card => { card.searchText = normalize(card.dataset.search); });
  function filter() {
    const words = normalize(query.value).trim().split(/\\s+/).filter(Boolean);
    let count = 0;
    cards.forEach(card => {
      card.hidden = Boolean(category.value && card.dataset.category !== category.value) || !words.every(word => card.searchText.includes(word));
      if (!card.hidden) count++;
    });
    sections.forEach(section => { section.hidden = ![...section.querySelectorAll('.card')].some(card => !card.hidden); });
    document.getElementById('result-count').textContent = `显示 ${count} / ${cards.length} 件作品`;
    document.getElementById('empty').hidden = count > 0;
  }
  query.addEventListener('input', filter); category.addEventListener('change', filter);
  form.addEventListener('submit', event => event.preventDefault());
  form.addEventListener('reset', () => { query.value = ''; category.value = ''; filter(); });
})();
</script></body></html>
'''.replace('OPTIONS', options).replace('SECTIONS', ''.join(sections)).replace('WORK_COUNT', str(len(items))).replace('THEME_COUNT', str(len(groups)))
    return {'index.html': page, 'docs/CATALOG.md': '\n'.join(markdown).rstrip() + '\n'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail if generated files are stale')
    args = parser.parse_args()
    for filename, content in render().items():
        target = ROOT / filename
        if args.check:
            if not target.exists() or target.read_text(encoding='utf-8') != content:
                raise SystemExit(f'Out of date: {filename}; run python scripts/build_catalog.py')
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding='utf-8')
    data = json.loads((ROOT / 'catalog.json').read_text(encoding='utf-8'))
    work_count = len(data['items'])
    category_count = len({item['category'] for item in data['items']})
    print(f'Catalogue is current: {work_count} works, {category_count} categories.')


if __name__ == '__main__':
    main()
