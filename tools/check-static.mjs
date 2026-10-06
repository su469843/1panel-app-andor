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

/* ---------------- 4. 括号平衡（跳过字符串与注释） ---------------- */
function balance(file) {
  const src = fs.readFileSync(file, 'utf8');
  const pairs = { ')': '(', ']': '[', '}': '{' };
  const stack = [];
  let i = 0;
  let line = 1;
  while (i < src.length) {
    const c = src[i];
    const next = src[i + 1];
    if (c === '\n') { line++; i++; continue; }
    if (c === '/' && next === '/') { while (i < src.length && src[i] !== '\n') i++; continue; }
    if (c === '/' && next === '*') { i += 2; while (i < src.length && !(src[i] === '*' && src[i + 1] === '/')) { if (src[i] === '\n') line++; i++; } i += 2; continue; }
    if (c === '"' && src.slice(i, i + 3) === '"""') {
      i += 3;
      while (i < src.length && src.slice(i, i + 3) !== '"""') { if (src[i] === '\n') line++; i++; }
      i += 3;
      continue;
    }
    if (c === '"') {
      i++;
      while (i < src.length && src[i] !== '"') { if (src[i] === '\\') i++; if (src[i] === '\n') line++; i++; }
      i++;
      continue;
    }
    if (c === "'") {
      i++;
      while (i < src.length && src[i] !== "'") { if (src[i] === '\\') i++; i++; }
      i++;
      continue;
    }
    if (c === '{' || c === '(' || c === '[') { stack.push([c, line]); i++; continue; }
    if (c === '}' || c === ')' || c === ']') {
      const top = stack.pop();
      if (!top || top[0] !== pairs[c]) {
        problems.push(`括号不匹配 ${path.relative(ROOT, file)}:${line} 遇到 '${c}'，栈顶 ${top ? top[0] + '@' + top[1] : '空'}`);
        return;
      }
      i++;
      continue;
    }
    i++;
  }
  if (stack.length) {
    problems.push(`括号未闭合 ${path.relative(ROOT, file)}: ${stack.slice(-3).map(([ch, ln]) => ch + '@' + ln).join(', ')}`);
  }
}
ktFiles.forEach(balance);

/* ---------------- 5. Gradle 配置体检 ---------------- */
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
  console.log('\n✅ 全部通过：XML 良构、资源引用齐全、符号可解析、括号平衡');
} else {
  console.log(`\n❌ 发现 ${problems.length} 个问题：`);
  problems.forEach((p) => console.log(' - ' + p));
  process.exitCode = 1;
}
