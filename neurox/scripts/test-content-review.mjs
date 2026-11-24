// Browser review of actual draft content. Not Android or psychometric acceptance.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const {chromium}=await import(path.join(root,'.local/tools/design/node_modules/playwright-core/index.mjs'));
const {default:binary}=await import(path.join(root,'.local/tools/design/node_modules/@sparticuz/chromium/build/index.js'));
const axe=await readFile(path.join(root,'.local/tools/design/node_modules/axe-core/axe.min.js'),'utf8');
const origin=process.env.CONTENT_REVIEW_URL||'http://127.0.0.1:4174';
const bank=JSON.parse(await readFile(path.join(root,'neurox/test-output/learning-content.json'),'utf8'));
const output=path.join(root,'neurox/test-output/content-review');await mkdir(output,{recursive:true});
const browser=await chromium.launch({executablePath:await binary.executablePath(),args:binary.args,headless:true});
const errors=[],violations=[],failures=[];let layoutChecks=0,audits=0,answered=0;
function assert(value,message){if(!value)throw Error(message);}
try{
 const page=await browser.newPage({viewport:{width:1280,height:1000}});page.on('pageerror',e=>errors.push(String(e)));
 const responses=[];page.on('request',r=>{if(new URL(r.url()).origin!==new URL(origin).origin)responses.push(r.url());});
 await page.goto(origin+'/design/content-review/');
 await page.waitForSelector('[data-text]');
 // Direct links must not invent a session or render an incomplete summary.
 await page.evaluate(()=>go('question'));await page.waitForURL('**/#library');await page.waitForSelector('[data-text]');
 await page.locator('[data-text]').first().click();await page.waitForURL('**/#reader');
 await page.evaluate(()=>go('summary'));await page.waitForURL('**/#reader');
 assert(await page.locator('[data-action="questions"]').count()===1,'Incomplete summary returns to the text');
 await page.evaluate(()=>go('library'));await page.waitForURL('**/#library');
 async function check(name){
  await page.evaluate(()=>document.fonts.ready);layoutChecks++;
  const metrics=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth+1,headings:document.querySelectorAll('main h1').length,lang:document.documentElement.lang,dir:document.documentElement.dir}));
  if(metrics.overflow||metrics.headings!==1||metrics.lang!=='fa'||metrics.dir!=='rtl')failures.push({name,...metrics});
  await page.addScriptTag({content:axe});audits++;
  const result=await page.evaluate(()=>axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa']}}));
  if(result.violations.length)violations.push({name,issues:result.violations.map(v=>({id:v.id,nodes:v.nodes.map(n=>n.target)}))});
 }
 for(const width of [360,430,768,1280])for(const large of [false,true]){
  await page.setViewportSize({width,height:1000});
  await page.evaluate(large=>{state.large=large;go('library');render(false);},large);
  await check(`library-${width}-${large}`);
  await page.locator('[data-text]').first().click();await page.waitForURL('**/#reader');await check(`reader-${width}-${large}`);
  await page.locator('[data-action="questions"]').click();await page.waitForURL('**/#question');await check(`question-${width}-${large}`);
  assert(await page.locator('[data-action="submit"]').isDisabled(),'No empty submission');
  await page.locator('[data-choice]').first().click();await page.locator('[data-action="submit"]').click();await check(`feedback-${width}-${large}`);
  await page.evaluate(()=>go('assessment'));await page.waitForURL('**/#assessment');await check(`assessment-${width}-${large}`);
 }
 // Complete every question; alternate correct and incorrect choices. Compare all feedback.
 await page.setViewportSize({width:430,height:1000});
 for(const t of bank.texts){
  await page.evaluate(({id,level})=>{state.large=false;state.level=level;go('library');render(false);},{id:t.id,level:t.level});
  await page.locator(`[data-text="${t.id}"]`).click();await page.locator('[data-action="questions"]').click();
  let correct=0;
  for(let i=0;i<t.questions.length;i++){
   const q=t.questions[i],choice=i%2===0?q.choices.find(c=>c.id===q.correctChoiceId):q.choices.find(c=>c.id!==q.correctChoiceId);
   await page.locator(`[data-choice="${choice.id}"]`).click();await page.locator('[data-action="submit"]').click();answered++;
   assert((await page.locator('#feedback').textContent()).includes(choice.explanation),'Selected choice has its own rationale');
   assert((await page.locator('#feedback blockquote').textContent())===q.evidence,'Feedback quotes source evidence');
   if(choice.id===q.correctChoiceId)correct++;
   assert(await page.locator('[data-choice]:disabled').count()===4,'Submitted choices cannot be changed silently');
   await page.locator('[data-action="next"]').click();
  }
  await page.waitForURL('**/#summary');
  assert(await page.evaluate(()=>state.answers.filter(a=>a.correct).length)===correct,'Summary counts actual submitted choices');
  await check(`summary-${t.id}`);
 }
 const downloads=page.waitForEvent('download');await page.locator('#reviewNote').fill('ابهام سؤال باید بازبینی شود. <script>');await page.locator('[data-action="export"]').click();
 const download=await downloads;const downloadPath=path.join(output,'review-export.json');await download.saveAs(downloadPath);
 const record=JSON.parse(await readFile(downloadPath,'utf8'));assert(record.scope==='editorial-review-only'&&record.note.includes('<script>'),'Review export preserves plain text without execution');
 await page.goto(origin+'/design/content-review/');await page.waitForSelector('[data-text]');
 assert(await page.evaluate(()=>state.answers.length)===0,'Reload clears unpersisted answers');
 await page.screenshot({path:path.join(output,'library-fa-mobile.png'),fullPage:true});
 await page.setViewportSize({width:1280,height:1050});await page.screenshot({path:path.join(output,'library-fa-desktop.png'),fullPage:true});
 await page.locator('[data-level="3"]').click();await page.locator('[data-text]').first().click();await page.screenshot({path:path.join(output,'reader-fa.png'),fullPage:true});
 await page.locator('[data-action="questions"]').click();await page.locator('[data-choice]').last().click();await page.locator('[data-action="submit"]').click();await page.screenshot({path:path.join(output,'feedback-fa.png'),fullPage:true});
 assert(responses.length===0,'No third-party requests');
 const report={scope:'Draft content reviewer only. Not Android acceptance or editorial/psychometric validation.',layoutChecks,audits,answered,errors,failures,violations,thirdPartyRequests:responses};
 await writeFile(path.join(output,'report.json'),JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify({layoutChecks,audits,answered,pageErrors:errors.length,layoutFailures:failures.length,axeFailures:violations.length}));
 if(errors.length||failures.length||violations.length)process.exitCode=1;
}finally{await browser.close();}
