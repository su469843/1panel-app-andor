// 零构建静态自检：XML 良构性 + Android 资源引用 + Kotlin 符号解析/括号平衡
// 用途：不跑 Gradle（本机 1核1G 跑不动）也能挡住大部分低级错误。
// 运行：node tools/check-static.mjs
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const problems = [];
const notes = [];

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

const allFiles = walk(ROOT).filter((f) => !f.includes('/.git/'));
const xmlFiles = allFiles.filter((f) => f.endsWith('.xml'));
const ktFiles = allFiles.filter((f) => f.endsWith('.kt'));
const gradleFiles = allFiles.filter((f) => f.endsWith('.kts'));

/* ---------------- 1. XML 良构性 ---------------- */
function checkXml(file) {
  let src = fs.readFileSync(file, 'utf8');
  src = src.replace(/<!--[\s\S]*?-->/g, '').replace(/<\?[\s\S]*?\?>/g, '').replace(/<![^>]*>/g, '');
  const stack = [];
  const tagRe = /<(\/?)([A-Za-z_][\w:.\-]*)((?:"[^"]*"|'[^']*'|[^>"'])*?)(\/?)>/g;
  let last = 0;
  let m;
  while ((m = tagRe.exec(src)) !== null) {
    const between = src.slice(last, m.index);
    if (between.includes('<')) {
      problems.push(`XML 非法片段 ${path.relative(ROOT, file)}: …${between.trim().slice(0, 60)}`);
    }
    last = tagRe.lastIndex;
    const [, closing, name, , selfClose] = m;
    if (name.startsWith('?') || name.startsWith('!')) continue;
    if (closing) {
      const top = stack.pop();
      if (top !== name) problems.push(`XML 标签不匹配 ${path.relative(ROOT, file)}: </${name}> 对 ${top}`);
    } else if (!selfClose) {
      stack.push(name);
    }
    if (/[<>]/.test(m[3]) === false && /=\s*[^"'\s]/.test(m[3])) {
      // 属性值未加引号（粗略检测）
      const bad = m[3].match(/\s[\w:.\-]+=([^"'\s][^\s]*)/);
      if (bad) problems.push(`XML 属性未加引号 ${path.relative(ROOT, file)}: ${bad[0].trim()}`);
    }
  }
  if (last < src.length && src.slice(last).includes('<')) {
    problems.push(`XML 尾部有未解析内容 ${path.relative(ROOT, file)}`);
  }
  if (stack.length) problems.push(`XML 未闭合标签 ${path.relative(ROOT, file)}: ${stack.join(',')}`);
}
xmlFiles.forEach(checkXml);

/* ---------------- 2. 资源引用 ---------------- */
const resDir = path.join(ROOT, 'app/src/main/res');
const resIndex = { drawable: new Set(), mipmap: new Set(), color: new Set(), string: new Set(), style: new Set() };
for (const f of allFiles) {
  if (!f.startsWith(resDir)) continue;
  const rel = path.relative(resDir, f);
  const [folder, base] = [rel.split('/')[0], path.basename(rel)];
  const name = base.replace(/\.(xml|png|webp|jpg)$/, '');
  if (folder.startsWith('drawable')) resIndex.drawable.add(name);
  if (folder.startsWith('mipmap')) resIndex.mipmap.add(name);
  if (folder.startsWith('values')) {
    const src = fs.readFileSync(f, 'utf8');
    for (const kind of ['color', 'string', 'style']) {
      const re = new RegExp(`<${kind}\\s+name="([^"]+)"`, 'g');
      let mm;
      while ((mm = re.exec(src)) !== null) resIndex[kind].add(mm[1].replace(/^.*\./, (s) => s));
    }
  }
}
// values 里的 style 名带点号，这里单独收集完整名
for (const f of allFiles.filter((x) => x.startsWith(path.join(resDir, 'values')))) {
  const src = fs.readFileSync(f, 'utf8');
  let mm;
  const re = /<style\s+name="([^"]+)"/g;
  while ((mm = re.exec(src)) !== null) resIndex.style.add(mm[1]);
}

const refRe = /@(drawable|mipmap|color|string|style)\/([\w.]+)/g;
for (const f of xmlFiles) {
  const src = fs.readFileSync(f, 'utf8');
  let m;
  while ((m = refRe.exec(src)) !== null) {
    const [, kind, name] = m;
    if (!resIndex[kind].has(name)) {
      problems.push(`资源缺失 ${path.relative(ROOT, f)}: @${kind}/${name}`);
    }
  }
}
const kotlinResRe = /R\.(drawable|mipmap|color|string)\.(\w+)/g;
for (const f of ktFiles) {
  const src = fs.readFileSync(f, 'utf8');
  let m;
  while ((m = kotlinResRe.exec(src)) !== null) {
    const [, kind, name] = m;
    if (!resIndex[kind].has(name)) {
      problems.push(`资源缺失 ${path.relative(ROOT, f)}: R.${kind}.${name}`);
    }
  }
}

/* ---------------- 3. Kotlin 符号解析 ---------------- */
const declarations = new Map(); // 所有顶层符号 name -> Set(package)
const typeDeclarations = new Map(); // 仅类型（class/object/interface/enum）
function addDecl(map, name, pkg) {
  if (!map.has(name)) map.set(name, new Set());
  map.get(name).add(pkg);
}
const fileInfo = [];
for (const f of ktFiles) {
  const src = fs.readFileSync(f, 'utf8');
  const pkg = (src.match(/^package\s+([\w.]+)/m) || [, ''])[1];
  fileInfo.push({ file: f, pkg, src });
  const classRe = /^(?:@\w+(?:\([^)]*\))?\s*)*(?:\w+\s+)*(?:class|interface|object|enum class)\s+(\w+)/gm;
  let m;
  while ((m = classRe.exec(src)) !== null) {
    addDecl(declarations, m[1], pkg);
    addDecl(typeDeclarations, m[1], pkg);
  }
  const funRe = /^(?:@\w+(?:\([^)]*\))?\s*)*(?:\w+\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?,\s]+\.)?(\w+)\s*\(/gm;
  while ((m = funRe.exec(src)) !== null) addDecl(declarations, m[1], pkg);
  const valRe = /^(?:@\w+(?:\([^)]*\))?\s*)*(?:\w+\s+)*(?:val|var)\s+(\w+)/gm;
  while ((m = valRe.exec(src)) !== null) addDecl(declarations, m[1], pkg);
}

// 3a. 自己包的 import 是否能解析（R 由 AGP 生成，跳过）
const importRe = /^import\s+(com\.panelone\.client[\w.]*)\.(\w+)$/gm;
for (const { file, src } of fileInfo) {
  let m;
  while ((m = importRe.exec(src)) !== null) {
    const [, pkg, name] = m;
    if (name === 'R') continue;
    if (!declarations.has(name) || !declarations.get(name).has(pkg)) {
      problems.push(`import 无法解析 ${path.relative(ROOT, file)}: import ${pkg}.${name}`);
    }
  }
}

// 3b. 跨包使用类型但没 import（只看类型声明，避免局部变量同名误报）
for (const { file, pkg, src } of fileInfo) {
  const imported = new Set();
  let m;
  const impRe = /^import\s+([\w.]+)$/gm;
  while ((m = impRe.exec(src)) !== null) {
    const full = m[1];
    imported.add(full);
    imported.add(full.slice(full.lastIndexOf('.') + 1));
  }
  for (const [name, pkgs] of typeDeclarations) {
    if (pkgs.has(pkg)) continue; // 同包无需 import
    if (imported.has(name)) continue;
    const useRe = new RegExp(`(?<![\\w.])${name}(?![\\w])`, 'g');
    if (useRe.test(src)) {
      problems.push(`可能缺少 import ${path.relative(ROOT, file)}: ${name} (声明于 ${[...pkgs].join(',')})`);
    }
  }
}

/* ---------------- 4. 词法扫描：注释与字符串 ---------------- */
// 返回「真正的代码」区间 [from, to, 起始行]，注释与字符串字面量已被剔除。
// 同时校验块注释提前结束 / 注释未闭合 / 字符串未闭合。
//
// 为什么需要这一节：KDoc 正文里只要出现 `*/`（例如中文文档习惯写 **/api/v1**），
// 注释就会在那里提前终止，后面的正文被当成代码 —— 编译器报一堆
// "Expecting a top level declaration"，但括号仍然平衡，光靠括号检查抓不到。
function lexKotlin(file) {
  const src = fs.readFileSync(file, 'utf8');
  const rel = path.relative(ROOT, file);
  const ranges = [];
  let i = 0;
  let line = 1;
  let segStart = 0;
  let segLine = 1;

  const flush = (end) => { if (end > segStart) ranges.push([segStart, end, segLine]); };
  const resume = () => { segStart = i; segLine = line; };

  while (i < src.length) {
    const c = src[i];
    const next = src[i + 1];

    if (c === '\n') { line++; i++; continue; }

    // 行注释
    if (c === '/' && next === '/') {
      flush(i);
      while (i < src.length && src[i] !== '\n') i++;
      resume();
      continue;
    }

    // 块注释（Kotlin 允许嵌套 /* /* */ */）
    if (c === '/' && next === '*') {
      flush(i);
      const startLine = line;
      // 注释是否独占行首（`/*` 之前只有空白）。只有这种注释才要求 `*/` 收尾后本行无内容；
      // `val x = 1 /* 说明 */` 这类行内注释不受影响。
      let ls = i;
      while (ls > 0 && src[ls - 1] !== '\n') ls--;
      const atLineStart = src.slice(ls, i).trim() === '';
      i += 2;
      let depth = 1;
      while (i < src.length && depth > 0) {
        if (src[i] === '\n') { line++; i++; continue; }
        if (src[i] === '/' && src[i + 1] === '*') { depth++; i += 2; continue; }
        if (src[i] === '*' && src[i + 1] === '/') {
          depth--;
          i += 2;
          // 独占行首的块注释，结束时本行不应再有任何内容。
          // 否则说明注释正文里混进了 `*/`，注释被提前截断，后面的正文会被当成代码。
          if (depth === 0 && atLineStart) {
            const eol = src.indexOf('\n', i);
            const tail = src.slice(i, eol === -1 ? src.length : eol);
            if (tail.trim() !== '') {
              problems.push(
                `块注释提前结束 ${rel}:${line} —— '*/' 之后同一行还有内容「${tail.trim().slice(0, 40)}」。` +
                `注释正文里出现 '*/'（如 **/api/v1**）会截断注释，请改写为 \`/api/v1\` 之类。`
              );
            }
          }
          continue;
        }
        i++;
      }
      if (depth > 0) problems.push(`块注释未闭合 ${rel}:${startLine}`);
      resume();
      continue;
    }

    // 原始字符串 """..."""
    if (c === '"' && src.slice(i, i + 3) === '"""') {
      flush(i);
      const startLine = line;
      i += 3;
      while (i < src.length && src.slice(i, i + 3) !== '"""') {
        if (src[i] === '\n') line++;
        i++;
      }
      if (i >= src.length) problems.push(`原始字符串未闭合 ${rel}:${startLine}`);
      else i += 3;
      resume();
      continue;
    }

    // 普通字符串（不能跨行）
    if (c === '"') {
      flush(i);
      const startLine = line;
      i++;
      let closed = false;
      while (i < src.length && src[i] !== '\n') {
        if (src[i] === '\\') { i += 2; continue; }
        if (src[i] === '"') { closed = true; i++; break; }
        i++;
      }
      if (!closed) problems.push(`字符串未闭合 ${rel}:${startLine}`);
      resume();
      continue;
    }

    // 字符字面量
    if (c === "'") {
      flush(i);
      const startLine = line;
      i++;
      let closed = false;
      while (i < src.length && src[i] !== '\n') {
        if (src[i] === '\\') { i += 2; continue; }
        if (src[i] === "'") { closed = true; i++; break; }
        i++;
      }
      if (!closed) problems.push(`字符字面量未闭合 ${rel}:${startLine}`);
      resume();
      continue;
    }

    i++;
  }
  flush(src.length);
  return ranges;
}

/* ---------------- 5. 括号平衡（只在代码区间内计数） ---------------- */
const codeFiles = [...ktFiles, ...gradleFiles];
function balance(file) {
  const src = fs.readFileSync(file, 'utf8');
  const pairs = { ')': '(', ']': '[', '}': '{' };
  const stack = [];
  for (const [from, to, rangeLine] of lexKotlin(file)) {
    let line = rangeLine;
    for (let i = from; i < to; i++) {
      const c = src[i];
      if (c === '\n') { line++; continue; }
      if (c === '{' || c === '(' || c === '[') { stack.push([c, line]); continue; }
      if (c === '}' || c === ')' || c === ']') {
        const top = stack.pop();
        if (!top || top[0] !== pairs[c]) {
          problems.push(`括号不匹配 ${path.relative(ROOT, file)}:${line} 遇到 '${c}'，栈顶 ${top ? top[0] + '@' + top[1] : '空'}`);
          return;
        }
      }
    }
  }
  if (stack.length) {
    problems.push(`括号未闭合 ${path.relative(ROOT, file)}: ${stack.slice(-3).map(([ch, ln]) => ch + '@' + ln).join(', ')}`);
  }
}
codeFiles.forEach(balance);

/* ---------------- 6. JVM 签名冲突（属性 setter vs 同名函数） ---------------- */
// `var keyword` 会自动生成 setKeyword()。若同一个类里又写了 fun setKeyword(...)，
// 前端分析能过，直到 codegen 才报 "Platform declaration clash"。
// 这类错误只能靠编译器发现，所以在自检里提前拦。
function maskNonCode(file) {
  // 把注释与字符串替换成空格，长度与行号保持一一对应
  const src = fs.readFileSync(file, 'utf8');
  const keep = new Uint8Array(src.length);
  for (const [from, to] of lexKotlin(file)) for (let i = from; i < to; i++) keep[i] = 1;
  const chars = src.split('');
  for (let i = 0; i < src.length; i++) if (!keep[i] && chars[i] !== '\n') chars[i] = ' ';
  return chars.join('');
}

function lineAt(text, idx) {
  let n = 1;
  for (let i = 0; i < idx; i++) if (text[i] === '\n') n++;
  return n;
}

function matchBrace(text, open) {
  let depth = 0;
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') depth++;
    else if (text[i] === '}') { depth--; if (depth === 0) return i; }
  }
  return -1;
}

function checkJvmClash(file) {
  const rel = path.relative(ROOT, file);
  const masked = maskNonCode(file);
  const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
  const classRe = /\b(?:class|object|interface)\s+(\w+)/g;
  let m;
  while ((m = classRe.exec(masked)) !== null) {
    // 从类名往后找「括号深度为 0 的 {」，兼容主构造器跨行的情况
    let i = classRe.lastIndex;
    let pd = 0;
    let open = -1;
    while (i < masked.length) {
      const ch = masked[i];
      if (ch === '(') pd++;
      else if (ch === ')') pd--;
      else if (ch === '{' && pd <= 0) { open = i; break; }
      else if (ch === '}' && pd <= 0) break;
      i++;
    }
    if (open < 0) continue;
    const close = matchBrace(masked, open);
    if (close < 0) continue;

    const body = masked.slice(open, close);
    const props = new Set();
    for (const p of body.matchAll(/\bvar\s+(\w+)/g)) props.add(p[1]);
    const fns = new Set();
    for (const f of body.matchAll(/\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?,\s]+\.\s*)?(\w+)\s*\(/g)) fns.add(f[1]);

    for (const p of props) {
      const candidates = [];
      if (/^is[A-Z]/.test(p)) candidates.push('set' + p.slice(2), 'get' + p.slice(2));
      candidates.push('set' + cap(p), 'get' + cap(p));
      for (const fn of candidates) {
        if (!fns.has(fn)) continue;
        const at = open + body.indexOf('fun ' + fn);
        problems.push(
          `JVM 签名冲突 ${rel}:${lineAt(masked, at)} —— 属性 '${p}' 生成的 ${fn}() 与同类中的 fun ${fn}(...)  ` +
          `签名相同，编译报 Platform declaration clash；请把函数改名为 update${cap(p)} 之类。`
        );
      }
    }
  }
}
ktFiles.forEach(checkJvmClash);

/* ---------------- 7. Gradle 配置体检 ---------------- */
const gradleText = gradleFiles.map((f) => fs.readFileSync(f, 'utf8')).join('\n');
for (const dep of ['compose-bom', 'okhttp', 'kotlinx-serialization-json', 'material3', 'activity-compose']) {
  if (!gradleText.includes(dep)) problems.push(`缺少依赖声明: ${dep}`);
}
if (!gradleText.includes('material-icons-core') && !gradleText.includes('material-icons-extended')) {
  notes.push('提示：Icons.Filled.* 依赖 material3 传递引入的 material-icons-core，若编译报找不到 Icons，请显式加 material-icons-core。');
}

/* ---------------- 输出 ---------------- */
console.log(`扫描：${xmlFiles.length} 个 XML，${ktFiles.length} 个 Kotlin，${gradleFiles.length} 个 Gradle 脚本`);
console.log(`资源索引：drawable=${resIndex.drawable.size} mipmap=${resIndex.mipmap.size} color=${resIndex.color.size} string=${resIndex.string.size} style=${resIndex.style.size}`);
console.log(`自定义符号：${declarations.size} 个`);
notes.forEach((n) => console.log('NOTE  ' + n));
if (problems.length === 0) {
  console.log('\n✅ 全部通过：XML 良构、资源引用齐全、符号可解析、括号平衡、注释与字符串闭合、JVM 签名无冲突');
} else {
  console.log(`\n❌ 发现 ${problems.length} 个问题：`);
  problems.forEach((p) => console.log(' - ' + p));
  process.exitCode = 1;
}
