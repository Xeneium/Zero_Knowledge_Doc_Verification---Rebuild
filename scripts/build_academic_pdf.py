import os
import re
import shutil
import subprocess
import time
from markdown_it import MarkdownIt

def setup_assets():
    assets_dir = 'docs/assets'
    mermaid_dir = os.path.join(assets_dir, 'mermaid')
    katex_dir = os.path.join(assets_dir, 'katex')
    os.makedirs(mermaid_dir, exist_ok=True)
    os.makedirs(katex_dir, exist_ok=True)

    # 1. Mermaid
    mermaid_src = r'C:\Users\sakth\AppData\Local\Programs\Antigravity IDE\resources\app\node_modules\mermaid\dist\mermaid.min.js'
    if os.path.exists(mermaid_src):
        shutil.copy(mermaid_src, os.path.join(mermaid_dir, 'mermaid.min.js'))

    # 2. KaTeX
    katex_src = r'C:\Users\sakth\AppData\Local\Programs\Antigravity IDE\resources\app\node_modules\katex\dist'
    if os.path.exists(katex_src):
        for f in ['katex.min.js', 'katex.min.css']:
            shutil.copy(os.path.join(katex_src, f), os.path.join(katex_dir, f))
        
        fonts_dst = os.path.join(katex_dir, 'fonts')
        if not os.path.exists(fonts_dst):
            shutil.copytree(os.path.join(katex_src, 'fonts'), fonts_dst)
        
        contrib_dst = os.path.join(katex_dir, 'contrib')
        os.makedirs(contrib_dst, exist_ok=True)
        shutil.copy(os.path.join(katex_src, 'contrib', 'auto-render.min.js'), os.path.join(contrib_dst, 'auto-render.min.js'))
    print("Assets successfully provisioned.")

def sanitize_mermaid_code(content):
    lines = content.strip().split('\n')
    cleaned_lines = []
    is_seq = any('sequenceDiagram' in l for l in lines)
    
    for line in lines:
        l = line
        if is_seq:
            if l.strip().startswith('participant') or l.strip().startswith('actor'):
                l = re.sub(r'(\bas\s+[^(\n]+)\s*\([^)]*\)', r'\1', l)
                if ' as ' in l:
                    parts = l.split(' as ', 1)
                    parts[1] = parts[1].replace('/', ' or ').replace('&', 'and')
                    l = ' as '.join(parts)
            else:
                if ':' in l:
                    pre, post = l.split(':', 1)
                    post = post.replace(';', ',')
                    post = post.replace('->', ' to ')
                    post = post.replace('{', '').replace('}', '')
                    post = post.replace('&', 'and')
                    post = post.replace('||', 'concat')
                    l = pre + ':' + post
                if l.strip().startswith('alt ') or l.strip().startswith('else '):
                    l = l.replace('>', 'over ').replace('<', 'under ').replace(';', ',')
        cleaned_lines.append(l)
    return '\n'.join(cleaned_lines)

def build_html():
    with open('docs/ZERO_KNOWLEDGE_DOCUMENT_VERIFICATION_FINAL_REPORT.md', 'r', encoding='utf-8') as f:
        md_text = f.read()

    # Pre-process math
    block_math = []
    def save_block_math(match):
        block_math.append(match.group(1))
        return f"<!--BLOCK_MATH_{len(block_math)-1}-->"
    md_text = re.sub(r'\$\$(.*?)\$\$', save_block_math, md_text, flags=re.DOTALL)

    inline_math = []
    def save_inline_math(match):
        inline_math.append(match.group(1))
        return f"<!--INLINE_MATH_{len(inline_math)-1}-->"
    md_text = re.sub(r'(?<!\$)\$(?!\$)([^\n\$]+?)\$', save_inline_math, md_text)

    # Sanitize mermaid blocks
    def clean_mermaid_block(match):
        block = match.group(1)
        cleaned = sanitize_mermaid_code(block)
        return f"```mermaid\n{cleaned}\n```"
    md_text = re.sub(r'```mermaid(.*?)```', clean_mermaid_block, md_text, flags=re.DOTALL)

    # Configure MarkdownIt
    md = MarkdownIt('gfm-like', {'linkify': False, 'html': True})
    html_rendered = md.render(md_text)

    # Restore block math
    for i, m in enumerate(block_math):
        safe_m = m.strip().replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
        html_rendered = html_rendered.replace(
            f"&lt;!--BLOCK_MATH_{i}--&gt;",
            f'<div class="math-display">$$\n{safe_m}\n$$</div>'
        ).replace(
            f"<!--BLOCK_MATH_{i}-->",
            f'<div class="math-display">$$\n{safe_m}\n$$</div>'
        )

    # Restore inline math
    for i, m in enumerate(inline_math):
        safe_m = m.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
        html_rendered = html_rendered.replace(
            f"&lt;!--INLINE_MATH_{i}--&gt;",
            f'<span class="math-inline">${safe_m}$</span>'
        ).replace(
            f"<!--INLINE_MATH_{i}-->",
            f'<span class="math-inline">${safe_m}$</span>'
        )

    # Convert mermaid code blocks into <pre class="mermaid">
    html_rendered = re.sub(
        r'<pre><code class="language-mermaid">(.*?)</code></pre>',
        r'<div class="mermaid-container"><div class="mermaid">\1</div></div>',
        html_rendered,
        flags=re.DOTALL
    )

    # Decode HTML entities inside mermaid blocks so Mermaid can parse graph syntax
    def clean_mermaid_html(match):
        m_content = match.group(1)
        m_content = m_content.replace('&gt;', '>').replace('&lt;', '<').replace('&amp;', '&').replace('&quot;', '"')
        return f'<div class="mermaid-container"><div class="mermaid">{m_content.strip()}</div></div>'
    
    html_rendered = re.sub(
        r'<div class="mermaid-container"><div class="mermaid">(.*?)</div></div>',
        clean_mermaid_html,
        html_rendered,
        flags=re.DOTALL
    )

    # Convert Chapters to styled headers with page-breaks
    html_rendered = re.sub(
        r'<h1>CHAPTER\s+(\d+):\s+(.*?)</h1>',
        r'<div class="chapter-break"></div><div class="chapter-header"><div class="chapter-number">CHAPTER \1</div><h1 class="chapter-title">\2</h1></div>',
        html_rendered
    )

    html_rendered = re.sub(
        r'<h1>(APPENDICES|REFERENCES)</h1>',
        r'<div class="chapter-break"></div><div class="chapter-header"><h1 class="chapter-title">\1</h1></div>',
        html_rendered
    )

    html_rendered = re.sub(
        r'<h2>(DECLARATION|CERTIFICATE|ACKNOWLEDGEMENT|ABSTRACT|TABLE OF CONTENTS|LIST OF FIGURES|LIST OF TABLES|LIST OF ABBREVIATIONS)</h2>',
        r'<div class="page-break"></div><div class="prelim-header"><h2 class="prelim-title">\1</h2></div>',
        html_rendered
    )

    full_html = f'''<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>ZERO-KNOWLEDGE DOCUMENT VERIFICATION PLATFORM - FINAL PROJECT REPORT</title>
<link rel="stylesheet" href="assets/katex/katex.min.css">
<script src="assets/katex/katex.min.js"></script>
<script src="assets/katex/contrib/auto-render.min.js"></script>
<script src="assets/mermaid/mermaid.min.js"></script>
<style>
  @page {{
    size: A4;
    margin: 17mm 16mm 15mm 19mm;
  }}

  *, *:before, *:after {{
    box-sizing: border-box;
  }}

  body {{
    font-family: "Times New Roman", Times, "Liberation Serif", serif;
    font-size: 10.3pt;
    line-height: 1.38;
    color: #000;
    background: #fff;
    margin: 0;
    padding: 0;
    text-align: justify;
    text-justify: inter-word;
  }}

  /* Page breaks */
  .page-break, .chapter-break {{
    page-break-before: always;
    break-before: page;
  }}

  .avoid-break {{
    page-break-inside: avoid;
    break-inside: avoid;
  }}

  /* Typography */
  h1, h2, h3, h4, h5, h6 {{
    font-family: "Times New Roman", Times, serif;
    color: #000;
    page-break-after: avoid;
    break-after: avoid;
  }}

  .chapter-header {{
    text-align: center;
    margin-top: 25px;
    margin-bottom: 25px;
    padding-bottom: 0;
    border-bottom: none;
  }}

  .chapter-number {{
    font-size: 14pt;
    font-weight: bold;
    letter-spacing: 2px;
    color: #000;
    text-transform: uppercase;
    margin-bottom: 6px;
  }}

  .chapter-title {{
    font-size: 17pt;
    font-weight: bold;
    text-transform: uppercase;
    margin: 0;
    line-height: 1.3;
  }}

  .prelim-header {{
    text-align: center;
    margin-top: 25px;
    margin-bottom: 26px;
  }}

  .prelim-title {{
    font-size: 16pt;
    font-weight: bold;
    text-transform: uppercase;
    letter-spacing: 1.5px;
    margin: 0;
    padding-bottom: 8px;
    border-bottom: none;
  }}

  h2 {{
    font-size: 14pt;
    font-weight: bold;
    margin-top: 20px;
    margin-bottom: 8px;
    border-bottom: none;
    padding-bottom: 3px;
    text-transform: uppercase;
  }}

  h3 {{
    font-size: 12pt;
    font-weight: bold;
    margin-top: 15px;
    margin-bottom: 6px;
  }}

  h4 {{
    font-size: 10.5pt;
    font-weight: bold;
    font-style: italic;
    margin-top: 10px;
    margin-bottom: 4px;
  }}

  /* Cover page follows the centered academic title-page composition. */
  body > h1 {{
    text-align: center;
    margin-top: 48px;
    margin-bottom: 18px;
    font-size: 17pt;
  }}

  body > h2 {{
    text-align: center;
    font-size: 13pt;
    margin: 0 0 18px;
  }}

  body > h3, body > p {{
    text-align: center;
  }}

  p {{
    margin-top: 0;
    margin-bottom: 8px;
  }}

  /* Tables */
  table {{
    width: 100%;
    border-collapse: collapse;
    margin: 10px 0;
    font-size: 8.8pt;
    line-height: 1.35;
    page-break-inside: auto;
  }}

  tr {{
    page-break-inside: avoid;
    break-inside: avoid;
  }}

  th, td {{
    border: 1px solid #000;
    padding: 5px 7px;
    vertical-align: top;
  }}

  th {{
    background-color: #fff;
    font-weight: bold;
    color: #000;
    text-align: left;
    border-bottom: 1.5px solid #000;
  }}

  tbody tr:nth-child(even) {{
    background-color: #fff;
  }}

  /* Code Blocks */
  pre {{
    background-color: #fff;
    border: 1px solid #000;
    border-left: 2px solid #000;
    padding: 8px 12px;
    font-family: "Consolas", "Courier New", monospace;
    font-size: 8.2pt;
    line-height: 1.38;
    overflow-x: auto;
    page-break-inside: avoid;
    break-inside: avoid;
    border-radius: 2px;
    margin: 10px 0;
  }}

  code {{
    font-family: "Consolas", "Courier New", monospace;
    font-size: 8.5pt;
    background-color: #f1f5f9;
    padding: 1px 3px;
    border-radius: 2px;
    color: #000;
  }}

  pre code {{
    background: none;
    padding: 0;
    border-radius: 0;
  }}

  /* Lists */
  ul, ol {{
    margin-top: 4px;
    margin-bottom: 10px;
    padding-left: 22px;
  }}

  li {{
    margin-bottom: 4px;
  }}

  /* Math display */
  .math-display {{
    text-align: center;
    margin: 12px 0;
    font-size: 11pt;
    page-break-inside: avoid;
  }}

  .math-inline {{
    font-size: 10.5pt;
  }}

  /* Mermaid Diagrams */
  .mermaid-container {{
    text-align: center;
    margin: 16px auto;
    padding: 8px;
    background: #fff;
    border: none;
    border-radius: 0;
    page-break-inside: avoid;
    break-inside: avoid;
  }}

  .mermaid svg {{
    max-width: 95% !important;
    height: auto !important;
  }}

  /* Blockquotes & Callouts */
  blockquote {{
    margin: 10px 0;
    padding: 6px 12px;
    border-left: 2px solid #000;
    background-color: #fff;
    font-style: italic;
    page-break-inside: avoid;
  }}

  /* Horizontal Rules */
  hr {{
    border: none;
    border-top: 1px solid #000;
    margin: 16px 0;
  }}
</style>
</head>
<body>

{html_rendered}

<script>
  window.addEventListener('DOMContentLoaded', async function() {{
    // 1. Render KaTeX
    if (typeof renderMathInElement !== 'undefined') {{
      renderMathInElement(document.body, {{
        delimiters: [
          {{left: '$$', right: '$$', display: true}},
          {{left: '$', right: '$', display: false}}
        ],
        throwOnError: false
      }});
    }}

    // 2. Initialize Mermaid
    if (typeof mermaid !== 'undefined') {{
      mermaid.initialize({{
        startOnLoad: false,
        theme: 'neutral',
        flowchart: {{ useMaxWidth: true, htmlLabels: true }},
        sequence: {{ useMaxWidth: true }},
        er: {{ useMaxWidth: true }}
      }});
      try {{
        await mermaid.run({{ querySelector: '.mermaid' }});
      }} catch (err) {{
        console.error('Mermaid render error:', err);
      }}
    }}

    window.__PRINT_READY__ = true;
  }});
</script>
</body>
</html>
'''

    out_path = 'docs/report_preview.html'
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(full_html)
    print(f"Generated {out_path} ({len(full_html)} chars)")
    return out_path

def generate_pdf(html_path):
    pdf_out = os.path.abspath('docs/ZERO_KNOWLEDGE_DOCUMENT_VERIFICATION_FINAL_REPORT.pdf')
    html_uri = 'file:///' + os.path.abspath(html_path).replace('\\', '/')

    cmd = [
        r'C:\Program Files\Google\Chrome\Application\chrome.exe',
        '--headless=new',
        '--disable-gpu',
        '--no-sandbox',
        '--run-all-compositor-stages-before-draw',
        '--virtual-time-budget=20000',
        '--no-pdf-header-footer',
        f'--print-to-pdf={pdf_out}',
        html_uri
    ]

    print("Running Google Chrome to generate PDF...")
    start_t = time.time()
    res = subprocess.run(cmd, capture_output=True, text=True)
    duration = time.time() - start_t
    print(f"Completed in {duration:.2f}s with returncode {res.returncode}")

    if os.path.exists(pdf_out):
        size_bytes = os.path.getsize(pdf_out)
        print(f"PDF generated successfully at {pdf_out}")
        print(f"File size: {size_bytes:,} bytes ({size_bytes / (1024*1024):.2f} MB)")
        
        # Copy to root directory for easy user access as well
        root_pdf = os.path.abspath('ZERO_KNOWLEDGE_DOCUMENT_VERIFICATION_FINAL_REPORT.pdf')
        shutil.copy(pdf_out, root_pdf)
        print(f"Copied PDF to workspace root: {root_pdf}")
        return pdf_out
    else:
        print("Error: PDF output file was not created!")
        print("Chrome stdout:", res.stdout)
        print("Chrome stderr:", res.stderr)
        return None

if __name__ == '__main__':
    setup_assets()
    html_file = build_html()
    generate_pdf(html_file)
