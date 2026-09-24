/* 产品原型契约回归：仅在内存中执行，不读写浏览器数据，不调用真实模型。
 * 运行：node --test eval-agent/prototype-tests/red-team-prototype.test.cjs
 * 页面函数暴露仅由测试加载器注入，正式 HTML 不提供测试后门。
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const doc = path.resolve(__dirname, '../doc');
const mockSource = fs.readFileSync(path.join(doc, 'red-team-agent-v2.mock.js'), 'utf8');
const html = fs.readFileSync(path.join(doc, 'red-team-agent-frontend-design.html'), 'utf8');
const pageSource = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].at(-1)[1];
const copy = value => JSON.parse(JSON.stringify(value));

function harness(initial) {
  const storage = new Map();
  if (initial) storage.set('redteam-product-v2-demo-20260922', JSON.stringify(initial));
  const events = {}, elements = {};
  for (const id of ['main','sidebar','menuButton','breadcrumb','todoButton','modalRoot','toasts','mobileScrim']) {
    elements['#'+id] = { innerHTML:'',textContent:'',style:{},focus(){},classList:{add(){},remove(){},toggle(){}},appendChild(){},setAttribute(){} };
  }
  const context = vm.createContext({
    window: { dispatchEvent(){},addEventListener(){},scrollTo(){} },
    document: { querySelector: selector => elements[selector] || null, body:{style:{}}, addEventListener:(name,fn)=>{events[name]=fn;},createElement:()=>({remove(){},setAttribute(){}}) },
    localStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value)},
    location:{hash:'#/workspace'},URLSearchParams,CustomEvent:class{},
    setTimeout:fn=>setTimeout(fn,0),clearTimeout,requestAnimationFrame:fn=>fn(),
    FormData:class{constructor(form){this.values=form.values;}entries(){return Object.entries(this.values);}},
  });
  vm.runInContext(mockSource, context);
  const raw = context.window.RedTeamMock;
  const names = ['render','pendingItems','reportSections','reportText','reportPage','redteamRun','standardRun','findingPage','candidateDialog','caseDialog','followupDialog'];
  const exposed = pageSource.replace(/    render\(\);\s*\}\(\)\);\s*$/, '    window.__test = {'+names.join(',')+'};\n    render();\n  }());');
  assert.notEqual(exposed, pageSource, '测试注入点必须存在');
  vm.runInContext(exposed, context);
  return {
    raw, context, elements, ui:context.window.__test,
    state:()=>copy(raw.getState()),
    async request(action,payload){const result=await raw.request(action,payload);context.window.__test.render();return result;},
    render(route){context.location.hash='#/'+route;context.window.__test.render();return elements['#main'].innerHTML;},
    async submit(id,values){await events.submit({preventDefault(){},target:{values,closest:()=>true,reportValidity:()=>true,getAttribute:()=>id}});context.window.__test.render();},
  };
}
const config = {name:'原型回归测试',type:'RED_TEAM_EXPLORATION',targetId:'target-01',risks:['PII','PROMPT'],mode:'ADAPTIVE_SINGLE_TURN',strategyId:'strategy-02',maxTargetCalls:60,maxAgentCalls:100,maxTokens:40000,maxCost:20,maxTurns:2,maxDuration:3600};
async function start(h, cfg = {}) {
  const id = await h.request('createTask',{...config,...cfg});
  await h.request('approve',{id});await h.request('start',{id});return id;
}
async function regression(h, outcome) {
  const id=await h.request('regression',{id:'finding-03',targetId:'target-02'});
  await h.request('approve',{id});await h.request('start',{id});
  await h.request('tick',{id,outcome});return id;
}
const resolution = (id, regressionTaskId) => ({id,regressionTaskId,owner:'陈澈',note:'核对当前目标快照与全部固定用例，证据完整'});

test('所有菜单和初始详情可渲染；未知、报告与候选修改进入待办',()=>{
  const h=harness();
  for(const route of ['workspace','redteam','standard','factory','findings','assets','targets','reports','reports?tab=todos','assets?tab=candidates','assets?tab=experiences'])assert.ok(h.render(route).length>100);
  for(const t of h.state().tasks)assert.ok(h.render('run/'+t.id).includes(t.name));
  for(const f of h.state().findings)assert.ok(h.render('finding/'+f.id).includes(f.title));
  for(const r of h.state().reports)assert.ok(h.render('report/'+r.id).includes(r.title));
  const kinds=h.ui.pendingItems().map(x=>x.kind);
  for(const kind of ['plan','blocked','finding','followup','candidate','experience','report'])assert.ok(kinds.includes(kind),kind);
});

test('多风险红队任务轮换覆盖；预算不足时明确保留未覆盖项',async()=>{
  const h=harness(),id=await start(h);
  for(let i=0;i<8;i++)await h.request('tick',{id,outcome:'safe'});
  await h.request('tick',{id,outcome:'safe'});
  const sessions=h.state().sessions.filter(s=>s.taskId===id);
  assert.deepEqual([...new Set(sessions.map(s=>s.risk))],['PII','PROMPT']);
  assert.equal(h.state().tasks.find(t=>t.id===id).coverage.executed,2);
  const short=await start(h,{maxTargetCalls:1});await h.request('tick',{id:short,outcome:'safe'});await h.request('tick',{id:short,outcome:'safe'});
  assert.equal(h.state().tasks.find(t=>t.id===short).coverage.executed,1);
  assert.match(h.state().tasks.find(t=>t.id===short).stopReason,/预算/);
});

test('静态输入不受上一结果影响；自适应单轮会按同风险反馈变化',async()=>{
  async function inputs(mode,outcome){const h=harness(),id=await start(h,{risks:['PROMPT'],mode,strategyId:mode==='STATIC_SINGLE_TURN'?'strategy-01':'strategy-02'});await h.request('tick',{id,outcome});await h.request('tick',{id,outcome:'safe'});return h.state().sessions.filter(s=>s.taskId===id).flatMap(s=>s.probes.map(p=>p.input));}
  assert.deepEqual(await inputs('STATIC_SINGLE_TURN','risk'),await inputs('STATIC_SINGLE_TURN','safe'));
  assert.notEqual((await inputs('ADAPTIVE_SINGLE_TURN','risk'))[1],(await inputs('ADAPTIVE_SINGLE_TURN','safe'))[1]);
});

test('多轮会话持续上下文；新尝试不误用其他会话最后反馈',async()=>{
  const h=harness(),id=await start(h,{mode:'ADAPTIVE_MULTI_TURN',strategyId:'strategy-03'});
  await h.request('tick',{id,outcome:'safe'});await h.request('tick',{id,outcome:'safe'});await h.request('tick',{id,outcome:'safe'});
  const ss=h.state().sessions.filter(s=>s.taskId===id);
  assert.equal(ss[0].probes.length,2);assert.match(ss[0].probes[1].input,/基于其响应/);assert.doesNotMatch(ss[1].probes[0].input,/上一尝试/);
});

test('Judge 失败、UNKNOWN 和调用失败不会显示或统计为无风险观察',async()=>{
  for(const [outcome,summary] of [['judgeError','INCONCLUSIVE'],['unknown','UNKNOWN'],['executionError','EXECUTION_ERROR']]){
    const h=harness(),id=await start(h);await h.request('tick',{id,outcome});
    const s=h.state().sessions.find(s=>s.taskId===id);assert.equal(h.raw.sessionVerdict(s),summary);assert.equal(h.raw.getCoverage(id).length,0);
    const page=h.render('run/'+id);assert.ok(page.includes({INCONCLUSIVE:'无法判断',UNKNOWN:'结果未知',EXECUTION_ERROR:'执行失败'}[summary]));
    if(outcome==='unknown'){await assert.rejects(()=>h.request('tick',{id,outcome:'safe'}));await h.request('resolveUnknown',{id,note:'已核对远端测试日志，关闭受影响会话'});assert.equal(h.state().sessions.find(x=>x.id===s.id).probes[0].executionStatus,'UNKNOWN');}
  }
});

test('任务、候选和正式用例贯穿保存原规则，不继承目录新判据',async()=>{
  const h=harness();await h.request('reviewFinding',{id:'finding-01',status:'CONFIRMED',note:'身份和检索记录支持'});
  const frozen=h.state().tasks.find(t=>t.id==='RT-260922-01').ruleSnapshots.find(r=>r.id==='PII');
  await h.request('saveRule',{id:'PII',version:'v9.audit',definition:'新定义',criterion:'新判据不应该污染旧记录'});
  await h.request('makeCandidate',{id:'finding-01'});
  const candidate=h.state().candidates.find(c=>c.sourceFinding==='finding-01');assert.equal(candidate.assertion,frozen.criterion);assert.equal(candidate.ruleSnapshot.version,frozen.version);
  await h.request('reviewSample',{id:candidate.id,decision:'approve'});
  const asset=h.state().cases.find(c=>c.sourceFinding==='finding-01');assert.equal(asset.ruleSnapshot.version,frozen.version);assert.equal(asset.assertionRef,'PII@'+frozen.version);
  const id=await h.request('regression',{id:'finding-01',targetId:'target-02'});assert.equal(h.state().tasks.find(t=>t.id===id).ruleSnapshots[0].version,frozen.version);
});

test('标准评测发现沉淀具体固定步骤、条件、断言和结果证据',async()=>{
  const h=harness(),source=h.state().tasks.find(t=>t.id==='EV-260922-01').caseSnapshots.find(c=>c.id==='case-002');
  await h.request('makeCandidate',{id:'finding-02'});const c=h.state().candidates.find(c=>c.sourceFinding==='finding-02');
  assert.equal(c.content,source.steps);assert.equal(c.preconditions,source.preconditions);assert.equal(c.assertion,source.assertion);assert.deepEqual(c.evidenceRefs,['result-1']);assert.ok(c.sourceEvidence[0].response);
});

test('缺少标准评测来源时拒绝生成占位候选',async()=>{
  const initial=harness().state();delete initial.findings.find(f=>f.id==='finding-02').resultId;
  const h=harness(initial);await assert.rejects(()=>h.request('makeCandidate',{id:'finding-02'}),/缺少具体原始/);
});

test('候选退回、修改、批准均保存当时内容和审核依据',async()=>{
  const h=harness(),before=h.state().candidates.find(c=>c.id==='candidate-1');
  await h.request('reviewSample',{id:before.id,decision:'revise',note:'需要补充测试身份说明'});
  await assert.rejects(()=>h.request('reviewSample',{id:before.id,decision:'edit',content:'补充测试身份后的候选内容'}),/修改依据/);
  await h.submit('candidateEdit',{id:before.id,content:'补充明确测试身份 A 的完整候选内容',note:'已按专家意见补充'});
  const c=h.state().candidates.find(c=>c.id===before.id);assert.equal(c.revision,2);assert.equal(c.status,'PENDING');assert.ok(c.history.some(h=>h.content===before.content&&h.feedback==='需要补充测试身份说明'));
  h.ui.candidateDialog(c.id);assert.ok(h.elements['#modalRoot'].innerHTML.includes(before.content));
  await h.request('reviewSample',{id:c.id,decision:'approve'});assert.equal(h.state().cases.find(x=>x.sourceCandidateId===c.id).sourceCandidateRevision,2);
});

test('旧通过不能满足新回归；新命中重开，指定完整通过后才允许修复',async()=>{
  const h=harness();await h.submit('reopenForm',{id:'finding-03',owner:'陈澈',note:'发现新版本存在异常，需要复查'});
  assert.equal(h.raw.getCurrentRegression('finding-03'),null);
  await assert.rejects(()=>h.request('resolveFinding',resolution('finding-03','EV-260921-02')));
  const failed=await regression(h,'risk');assert.equal(h.state().findings.find(f=>f.id==='finding-03').remediation,'REOPENED');
  await assert.rejects(()=>h.request('resolveFinding',resolution('finding-03','EV-260921-02')),/当前指定/);
  await assert.rejects(()=>h.request('resolveFinding',resolution('finding-03',failed)),/不能包含/);
  const safe=await regression(h,'safe');await assert.rejects(()=>h.request('resolveFinding',{id:'finding-03',regressionTaskId:safe}),/责任人和依据/);
  await h.submit('resolveForm',resolution('finding-03',safe));const f=h.state().findings.find(f=>f.id==='finding-03');assert.equal(f.remediation,'RESOLVED');assert.equal(f.resolution.regressionTaskId,safe);
});

test('未完成、待定、执行错误、结果未知都不能确认修复',async()=>{
  for(const outcome of ['judgeError','executionError','unknown']){
    const h=harness();await h.request('reopenFinding',{id:'finding-03',owner:'陈澈',note:'独立复测'});
    const id=await regression(h,outcome);await assert.rejects(()=>h.request('resolveFinding',resolution('finding-03',id)));
  }
});

test('接受风险不计技术修复，必须留依据；人工重开使旧依据失效',async()=>{
  const h=harness();await assert.rejects(()=>h.request('acceptRisk',{id:'finding-02',owner:'陈澈'}));
  await h.submit('acceptRiskForm',{id:'finding-02',owner:'陈澈',note:'测试环境风险，业务限定使用范围'});
  assert.equal(h.state().findings.find(f=>f.id==='finding-02').remediation,'ACCEPTED_RISK');assert.ok(h.render('finding/finding-02').includes('重新打开问题'));
  await h.submit('reopenForm',{id:'finding-02',owner:'陈澈',note:'撤回风险接受并重新验证'});assert.equal(h.state().findings.find(f=>f.id==='finding-02').remediation,'REOPENED');
});

test('样本工厂多风险轮换、目标调用为零、审核前不入库',async()=>{
  const h=harness(),before=h.state().cases.length,id=await start(h,{type:'SAMPLE_PRODUCTION',seed:'仅在隔离测试环境验证授权与内容边界',quantity:3,risks:['PII','PROMPT']});
  for(let i=0;i<3;i++)await h.request('tick',{id});const s=h.state();assert.equal(s.tasks.find(t=>t.id===id).budget.targetCalls,0);assert.equal(s.cases.length,before);assert.deepEqual(s.candidates.filter(c=>c.taskId===id).map(c=>c.risk),['PII','PROMPT','PII']);
});

test('结果跟进只关闭待办，不覆盖原始判定或将错误计作修复',async()=>{
  const h=harness();await h.submit('followupForm',{id:'result-5',status:'HANDLED',note:'已创建后续重测任务，保留原始待定'});
  assert.equal(h.state().results.find(r=>r.id==='result-5').verdict,'INCONCLUSIVE');assert.equal(h.ui.pendingItems().some(x=>x.kind==='followup'&&x.id==='result-5'),false);
});

test('报告页面与导出共用正文，冻结证据和回归，不读取后续数据',async()=>{
  const h=harness(),reportId=await h.request('report',{id:'RT-260922-01'});
  await h.request('publishReport',{id:reportId});const frozen=h.ui.reportText(reportId),sections=copy(h.ui.reportSections(reportId));
  assert.equal(sections.length,7);const rendered=h.render('report/'+reportId);for(const section of sections)assert.ok(rendered.includes(section.title));
  await h.request('reviewFinding',{id:'finding-01',status:'CONFIRMED',note:'报告确认之后的意见不应进入旧报告'});
  await h.request('tick',{id:'RT-260922-01',outcome:'risk'});
  assert.equal(h.ui.reportText(reportId),frozen);
  assert.ok(h.state().reports.find(r=>r.id===reportId).snapshot.sessions.length);
});

test('历史报告缺字段不回填实时数据；缺快照则阻止伪造导出',()=>{
  const initial=harness().state(),report=initial.reports.find(r=>r.id==='report-02');delete report.snapshot.sessions;delete report.snapshot.regressionTasks;
  let h=harness(initial);assert.match(h.ui.reportText('report-02'),/旧版报告没有保存/);
  delete report.snapshot;h=harness(initial);assert.match(h.render('report/report-02'),/缺少确认快照/);assert.throws(()=>h.ui.reportText('report-02'),/缺少确认快照/);
});

test('页面和 Markdown 对不可信响应与链接进行转义',async()=>{
  const initial=harness().state();initial.sessions[0].probes[0].output='<script>alert(1)</script> [诱导](javascript:alert(1)) https://example.invalid';
  const h=harness(initial),id=await h.request('report',{id:'RT-260922-01'});
  const page=h.render('report/'+id),text=h.ui.reportText(id);assert.doesNotMatch(page,/<script>alert/);assert.doesNotMatch(text,/<script>|\]\(javascript:|https:\/\//);assert.match(text,/&lt;script&gt;/);
});

test('目标能力与标准用例快照约束持续生效',async()=>{
  const h=harness();await assert.rejects(()=>start(h,{targetId:'target-03',mode:'ADAPTIVE_MULTI_TURN',strategyId:'strategy-03',risks:['PROMPT']}));
  await assert.rejects(()=>start(h,{targetId:'target-03',risks:['PII']}));
  const id=await start(h,{type:'STANDARD_EVALUATION',setId:'set-01',risks:['PROMPT']}),frozen=h.state().tasks.find(t=>t.id===id).caseSnapshots;
  await h.request('toggleCase',{id:frozen[0].id});assert.deepEqual(h.state().tasks.find(t=>t.id===id).caseSnapshots,frozen);
});

test('文档中的本地 Markdown 链接都指向现存文件',()=>{
  for(const file of fs.readdirSync(doc).filter(f=>f.endsWith('.md'))){
    const text=fs.readFileSync(path.join(doc,file),'utf8');
    for(const m of text.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)){
      const target=m[1].split('#')[0];if(!target||/^[a-z]+:/i.test(target))continue;
      assert.ok(fs.existsSync(path.resolve(doc,target)),file+' -> '+target);
    }
  }
});
