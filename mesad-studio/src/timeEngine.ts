export type Jalali = {year:number;month:number;day:number};
const breaks=[-61,9,38,199,426,686,756,818,1111,1181,1210,1635,2060,2097,2192,2262,2324,2394,2456,3178];
const div=(a:number,b:number)=>Math.trunc(a/b);const mod=(a:number,b:number)=>a-div(a,b)*b;
function jalCal(jy:number){const gy=jy+621;let leapJ=-14,jp=breaks[0],jm=0,jump=0;for(let i=1;i<breaks.length;i++){jm=breaks[i];jump=jm-jp;if(jy<jm)break;leapJ+=div(jump,33)*8+div(mod(jump,33),4);jp=jm;}const n=jy-jp;leapJ+=div(n,33)*8+div(mod(n,33)+3,4);if(mod(jump,33)===4&&jump-n===4)leapJ++;const leapG=div(gy,4)-div((div(gy,100)+1)*3,4)-150;const march=20+leapJ-leapG;let adjusted=n;if(jump-n<6)adjusted=n-jump+div(jump+4,33)*33;let leap=mod(mod(adjusted+1,33)-1,4);if(leap===-1)leap=4;return{leap,gy,march};}
function g2d(gy:number,gm:number,gd:number){let d=div((gy+div(gm-8,6)+100100)*1461,4)+div(153*mod(gm+9,12)+2,5)+gd-34840408;d=d-div(div(gy+100100+div(gm-8,6),100)*3,4)+752;return d;}
function d2g(jdn:number){let j=4*jdn+139361631;j=j+div(div(4*jdn+183187720,146097)*3,4)*4-3908;const i=div(mod(j,1461),4)*5+308;const day=div(mod(i,153),5)+1;const month=mod(div(i,153),12)+1;const year=div(j,1461)-100100+div(8-month,6);return{year,month,day};}
function j2d(jy:number,jm:number,jd:number){const r=jalCal(jy);return g2d(r.gy,3,r.march)+(jm-1)*31-div(jm,7)*(jm-7)+jd-1;}
function d2j(jdn:number){const gy=d2g(jdn).year;const jy=Math.min(gy-621,3177);const r=jalCal(jy);let k=jdn-g2d(r.gy,3,r.march);if(k>=0){if(k<=185)return{year:jy,month:1+div(k,31),day:mod(k,31)+1};k-=186;}else{const prev=jy-1;k+=179;if(r.leap===1)k++;return{year:prev,month:7+div(k,30),day:mod(k,30)+1};}return{year:jy,month:7+div(k,30),day:mod(k,30)+1};}
export function gregorianToJalali(date:Date):Jalali{return d2j(g2d(date.getUTCFullYear(),date.getUTCMonth()+1,date.getUTCDate()));}
export function jalaliToGregorian(j:Jalali):Date{const g=d2g(j2d(j.year,j.month,j.day));return new Date(Date.UTC(g.year,g.month-1,g.day));}
export function jalaliMonthDays(year:number,month:number){if(month<1||month>12)throw new RangeError('Month must be 1–12');return month<=6?31:month<=11?30:(jalCal(year).leap===0?30:29);}
export function formatDuration(ms:number){const s=Math.max(0,Math.floor(ms/1000));const h=Math.floor(s/3600),m=Math.floor(s%3600/60),sec=s%60;return h>0?`${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`:`${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`;}
export function timerRemaining(deadline:number|undefined,remaining:number,now:number){return deadline===undefined?Math.max(0,remaining):Math.max(0,deadline-now);}
export type AlarmSpec={hour:number;minute:number;weekdays:number[]};
export function nextAlarmDate(alarm:AlarmSpec,now:Date){const result=new Date(now);result.setHours(alarm.hour,alarm.minute,0,0);if(!alarm.weekdays.length){if(result<=now)result.setDate(result.getDate()+1);return result;}for(let n=0;n<=7;n++){const d=new Date(result);d.setDate(result.getDate()+n);if(alarm.weekdays.includes(d.getDay())&&d>now)return d;}return null;}
export function cityClock(zone:string,instant:Date){return new Intl.DateTimeFormat('en-US',{timeZone:zone,hour:'numeric',minute:'2-digit',hour12:false}).format(instant);}
export function offsetLabel(zone:string,instant=new Date()){const parts=new Intl.DateTimeFormat('en-US',{timeZone:zone,timeZoneName:'shortOffset'}).formatToParts(instant);return parts.find(x=>x.type==='timeZoneName')?.value??'';}
export function lapDelta(previous:number,total:number){return Math.max(0,total-previous);}
