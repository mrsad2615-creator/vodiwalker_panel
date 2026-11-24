// Browser prototype regression checks. Not Android runtime/accessibility acceptance.
// Install pinned tools outside source: see docs/14-design-system.md.
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const {chromium}=await import(path.join(root,'.local/tools/design/node_modules/playwright-core/index.mjs'));
const {default:binary}=await import(path.join(root,'.local/tools/design/node_modules/@sparticuz/chromium/build/index.js'));
const axe=await readFile(path.join(root,'.local/tools/design/node_modules/axe-core/axe.min.js'),'utf8');
const output=path.join(root,'neurox/test-output/design');await mkdir(output,{recursive:true});
const browser=await chromium.launch({executablePath:await binary.executablePath(),args:binary.args,headless:true});
let checks=0;const failures=[],consoleErrors=[],accessibility=[];
try {
 const page=await browser.newPage({viewport:{width:1280,height:1100}});
 page.on('pageerror',e=>consoleErrors.push(String(e)));
 await page.goto((process.env.DESIGN_URL||'http://127.0.0.1:4173')+'/design/prototype/');
 const routes=await page.locator('#screenSelect option').evaluateAll(list=>list.map(x=>x.value));
 if(routes.length!==20)throw Error('Expected all twenty required prototype routes');
 for(const width of [360,430])for(const lang of ['en','fa'])for(const theme of ['dark','light'])for(const large of [false,true]){
  await page.setViewportSize({width,height:960});
  for(const id of routes){
   await page.evaluate(({id,lang,theme,large})=>{language=lang;document.documentElement.dataset.theme=theme;document.body.classList.toggle('large',large);location.hash=id;render();},{id,lang,theme,large});
   await page.waitForSelector(`[data-screen="${id}"]`);
   const result=await page.locator('.device').evaluate(e=>({width:e.clientWidth,scroll:e.scrollWidth,headings:e.querySelectorAll('h1').length,html:e.innerHTML}));
   checks++;
   if(result.scroll>result.width+1||result.headings!==1||result.html.includes('undefined')) failures.push({id,width,lang,theme,large,widths:[result.width,result.scroll],headings:result.headings});
   if(width===430&&!large){
    await page.addScriptTag({content:axe});
    const report=await page.evaluate(async()=>await axe.run(document.querySelector('#device'),{runOnly:{type:'tag',values:['wcag2a','wcag2aa']}}));
    const violations=report.violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.map(n=>n.target)}));
    if(violations.length)accessibility.push({route:id,lang,theme,violations});
   }
  }
 }
 // Exercise real prototype controls. This does not create Android sessions.
 await page.setViewportSize({width:1280,height:1100});
 await page.evaluate(()=>{language='en';location.hash='library';render();});
 await page.locator('#search').fill('Word Sprint');
 if(await page.locator('#screen .list-item').count()!==1)failures.push({interaction:'library search'});
 await page.locator('#search').fill('<img src=x onerror=alert(1)>');
 if(await page.locator('#screen img').count())failures.push({interaction:'search must remain plain text'});
 for(const direction of ['minimal','neuro','editorial','quiet']){
  await page.selectOption('#direction',direction);
  await page.evaluate(()=>{query='';location.hash='home';render();});
  await page.screenshot({path:path.join(output,`direction-${direction}.png`),fullPage:true});
 }
 // Capture every major route in the selected direction for visual critique.
 await page.selectOption('#direction','quiet');
 for(const id of routes){
  await page.evaluate(id=>{language='en';document.documentElement.dataset.theme='dark';document.body.classList.remove('large');location.hash=id;render();},id);
  await page.locator('#device').screenshot({path:path.join(output,`screen-${id}-en-dark.png`)});
 }
 await page.evaluate(()=>{language='fa';document.documentElement.dataset.theme='light';location.hash='vision';render();});
 await page.screenshot({path:path.join(output,'vision-fa-light.png'),fullPage:true});
 const report={scope:'Browser prototype only. Not Android device acceptance.',checks,failures,consoleErrors,accessibility};
 await writeFile(path.join(output,'report.json'),JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify({checks,layoutFailures:failures.length,pageErrors:consoleErrors.length,axeRouteFailures:accessibility.length}));
 if(failures.length||consoleErrors.length||accessibility.length)process.exitCode=1;
} finally {await browser.close();}
