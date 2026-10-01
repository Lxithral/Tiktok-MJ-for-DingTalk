# 从 MiSans VF 实例化 500/700 静态字重并按 App 字符集子集化。
# 用法: 在 fonts-src 目录下执行  python make_misans.py
# 产物: app/src/main/res/font/misans_500.ttf / misans_700.ttf (各约 140KB)
# 依赖: pip install fonttools
import re, pathlib, subprocess, string

HERE = pathlib.Path(__file__).resolve().parent
MAIN = HERE.parent / 'app' / 'src' / 'main'
VF = HERE / 'misans_vf.ttf'
OUT = MAIN / 'res' / 'font'
PY = r'C:/Users/Lxithral/AppData/Local/Programs/Python/Python314/python.exe'

# 1) 字符集 = 全部 .kt 字符串字面量 + strings.xml 文本 + ASCII + 常用符号兜底。
#    子集外字符(如诊断页显示的任意聊天文本)由平台回落系统字体渲染。
chars = set()
for p in MAIN.rglob('*.kt'):
    t = p.read_text(encoding='utf-8', errors='ignore')
    for m in re.findall(r'"((?:[^"\\\n]|\\.)*)"', t):
        chars.update(m.replace('\\n', '\n').replace('\\"', '"'))
for p in MAIN.rglob('strings.xml'):
    t = p.read_text(encoding='utf-8', errors='ignore')
    for m in re.findall(r'>([^<]+)<', t):
        chars.update(m)
chars.update(string.printable)
chars.update('，。、「」·（）…：；！？—～×→←↑↓％')
chars = {c for c in chars if c == ' ' or (c.isprintable() and c != '\x0b')}
text = ''.join(sorted(chars))
charset_file = HERE / 'charset.txt'
charset_file.write_text(text, encoding='utf-8')
print('charset glyphs:', len(text))

# 2) VF -> 静态字重实例 -> 子集化
for w in (500, 700):
    inst = HERE / f'misans_inst_{w}.ttf'
    subprocess.run([PY, '-m', 'fontTools.varLib.instancer', str(VF), f'wght={w}',
                    '-o', str(inst), '--quiet'], check=True)
    out = OUT / f'misans_{w}.ttf'
    subprocess.run([PY, '-m', 'fontTools.subset', str(inst),
                    f'--text-file={charset_file}',
                    f'--output-file={out}',
                    '--layout-features=*', '--name-IDs=*'], check=True)
    inst.unlink()
    print(out.name, out.stat().st_size, 'bytes')
print('DONE')
