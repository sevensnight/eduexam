"""教师与学生 UI 回归：真实数据只读；考试开始、提交、批改和编辑全部在浏览器内模拟。
截图保存在忽略跟踪的 frontend/artifacts/exam-workflows。需要 Selenium 与本机 Chrome。
"""
import json
import os
from pathlib import Path

from selenium import webdriver
from selenium.common.exceptions import StaleElementReferenceException
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait

base = os.environ.get('EDUEXAM_UI_URL', 'http://localhost:5173')
output = Path(__file__).resolve().parents[1] / 'artifacts/exam-workflows'
output.mkdir(parents=True, exist_ok=True)
options = webdriver.ChromeOptions()
options.add_argument('--headless=new')
options.add_argument('--window-size=1440,1080')
options.set_capability('goog:loggingPrefs', {'browser': 'ALL'})
if os.environ.get('EDUEXAM_BROWSER_BINARY'):
    options.binary_location = os.environ['EDUEXAM_BROWSER_BINARY']
service = Service(executable_path=os.environ.get('EDUEXAM_CHROMEDRIVER'), service_args=['--disable-build-check'] if os.environ.get('EDUEXAM_DRIVER_SKIP_VERSION_CHECK') == '1' else [])
driver = webdriver.Chrome(service=service, options=options)
driver.set_script_timeout(25)
wait = WebDriverWait(driver, 20)
expected_forbidden_urls = set()


def visible(selector):
    result = []
    for item in driver.find_elements(By.CSS_SELECTOR, selector):
        try:
            if item.is_displayed():
                result.append(item)
        except StaleElementReferenceException:
            continue
    return result


def settle():
    driver.execute_async_script('''
      const done=arguments[0];document.fonts.ready.then(()=>requestAnimationFrame(()=>{
        const animations=document.getAnimations().filter(a=>Number.isFinite(a.effect.getComputedTiming().endTime));
        Promise.allSettled(animations.map(a=>a.finished)).then(()=>requestAnimationFrame(done));
      }));
    ''')


def ready(selector='.workflow-page'):
    wait.until(lambda _: visible(selector))
    wait.until(lambda _: not visible('.el-skeleton') and not visible('.el-loading-mask'))
    settle()


def click(element):
    driver.execute_script('arguments[0].scrollIntoView({block:"center"})', element)
    element.click()
    settle()


def button(text, root=None):
    return next(e for e in (root or driver).find_elements(By.XPATH, './/button[normalize-space()="' + text + '"]') if e.is_displayed())


def field(selector, text):
    element = driver.find_element(By.CSS_SELECTOR, selector)
    element.send_keys(Keys.CONTROL, 'a')
    element.send_keys(Keys.BACKSPACE)
    element.send_keys(str(text))
    element.send_keys(Keys.TAB)
    settle()


def viewport(width):
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {'width': width, 'height': 1080 if width > 767 else 900, 'deviceScaleFactor': 1, 'mobile': width < 768})
    driver.execute_script('window.workflowViewportWidth=arguments[0]', width)
    settle()


def api(code):
    result = driver.execute_async_script(r'''
      const [code,done]=arguments;
      const url=performance.getEntriesByType('resource').map(e=>e.name).find(u=>/\/src\/api\/index\.js(?:\?|$)/.test(u));
      import(url || '/src/api/index.js').then(async api=>{
        window.workflowOriginal ||= Object.fromEntries(Object.entries(api).map(([key,value])=>[key,{...value}]));
        const result=await new Function('api','original','return (async()=>{'+code+'})()')(api,window.workflowOriginal);
        done({ok:true,result:result??null});
      }).catch(error=>done({ok:false,error:error.message}));
    ''', code)
    assert result['ok'], result
    return result['result']


def guard_writes():
    # Even an unanticipated UI write is rejected at the HTTP boundary, before reaching the server.
    result = driver.execute_async_script('''
      const done=arguments[0];import('/src/api/request.js').then(({default:request})=>{
        window.blockedNetworkWrites=[];
        request.interceptors.request.use(config=>{
          if(!['get','head','options'].includes((config.method||'get').toLowerCase())){
            window.blockedNetworkWrites.push(config.url);throw new Error('UI test blocked a live write');
          }return config;
        });done(true);
      });
    ''')
    assert result
    api("window.workflowWrites=[];window.workflowReads=[];")


def navigate(route, selector='.workflow-page'):
    # Unmount old page; dynamic API mocks remain in this isolated tab.
    driver.execute_async_script('''
      const [route,done]=arguments,router=document.querySelector('#app').__vue_app__.config.globalProperties.$router;
      router.push('/profile').then(()=>router.push(route)).then(()=>done(true));
    ''', route)
    ready(selector)


def capture(name, scroll_top=True):
    wait.until(lambda _: not visible('.el-message'))
    if scroll_top:
        driver.execute_script('window.scrollTo(0,0)')
    settle()
    result = driver.execute_script('''
      const shown=e=>!!e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden';
      const rect=e=>e.getBoundingClientRect(), viewportWidth=window.workflowViewportWidth||innerWidth;
      return {width:innerWidth,expectedWidth:viewportWidth,overflow:document.documentElement.scrollWidth>viewportWidth+1,
        clippedButtons:[...document.querySelectorAll('.workflow-page button,.el-dialog button')].filter(shown).filter(e=>{
          const a=rect(e);return [...e.children].some(child=>{const b=rect(child);return b.left<a.left-2||b.right>a.right+2});
        }).map(e=>e.textContent.trim()),
        overflowingDialogs:[...document.querySelectorAll('.el-dialog')].filter(shown).some(e=>e.scrollWidth>e.clientWidth+2||rect(e).right>innerWidth+1),
        overflowingPickers:[...document.querySelectorAll('.el-picker__popper')].filter(shown).some(e=>rect(e).right>viewportWidth+1||rect(e).left < -1),
        overlappingCells:[...document.querySelectorAll('.record-cell')].filter(shown).some(e=>e.scrollWidth>e.clientWidth+2)};
    ''')
    assert not result['overflow'] and not result['clippedButtons'] and not result['overflowingDialogs'] and not result['overlappingCells'] and not result['overflowingPickers'], (name, result)
    driver.save_screenshot(str(output / (name + '.png')))
    print(json.dumps({'capture': name, **result}, ensure_ascii=False), flush=True)


def responsive(name, widths=(1440, 1024, 768, 390, 320)):
    for width in widths:
        viewport(width)
        capture(name + '-' + str(width))
    viewport(1440)


def login(role):
    driver.get(base + '/login')
    logged_in = driver.execute_async_script('''
      const [username,password,done]=arguments;fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})})
        .then(r=>r.json()).then(data=>{if(!data.access_token)return done(false);sessionStorage.setItem('token',data.access_token);sessionStorage.setItem('user',JSON.stringify(data.user));done(true)}).catch(()=>done(false));
    ''', role + '1', os.environ.get('EDUEXAM_' + role.upper() + '_PASSWORD', role + '123'))
    assert logged_in, role
    driver.get(base + '/dashboard')
    ready('.dashboard-view')
    guard_writes()


def real_teacher():
    responsive('teacher-dashboard', (1440, 390))
    exams = api("const r=await api.examApi.list({page:1,size:50});window.realExams=r.items;return r.items.map(e=>({id:e.id,status:e.status}));")
    assert exams
    eid = exams[0]['id']
    navigate('/exams/' + str(eid) + '/edit')
    responsive('exam-editor')
    viewport(320)
    click(visible('.el-date-editor input')[0])
    wait.until(lambda _: visible('.el-picker__popper'))
    assert '确定' in visible('.el-picker__popper')[0].text and '月' in visible('.el-picker__popper')[0].text
    capture('exam-schedule-picker-mobile', scroll_top=False)
    driver.switch_to.active_element.send_keys(Keys.ESCAPE)
    viewport(1440)
    driver.execute_script('document.querySelector(".question-picker").scrollIntoView({block:"center"})')
    settle()
    capture('exam-question-picker', scroll_top=False)
    click(visible('.picker-title')[0])
    wait.until(lambda _: visible('.el-dialog'))
    wait.until(lambda _: visible('.question-preview-dialog .question-details'))
    assert all(label in visible('.question-preview-dialog')[0].text for label in ['完整题干', '正确答案', '解析'])
    capture('full-question-preview')
    click(visible('.el-dialog__headerbtn')[0])

    chosen = api('''
      for(const exam of window.realExams){
        const stats=await api.statsApi.exam(exam.id);
        if(stats.total_participants>0)return exam.id;
      }return window.realExams[0].id;
    ''')
    navigate('/exams/' + str(chosen) + '/stats')
    responsive('exam-statistics')
    for label in ['单题分析', '班级对比', '学生对比']:
        click(next(e for e in visible('[role=tab]') if e.text == label))
        capture('statistics-' + {'单题分析':'questions','班级对比':'classes','学生对比':'students'}[label])
        viewport(320)
        capture('statistics-mobile-' + {'单题分析':'questions','班级对比':'classes','学生对比':'students'}[label])
        viewport(1440)
    if visible('.record-action button'):
        click(visible('.record-action button')[0])
        wait.until(lambda _: visible('.answer-sheet-dialog'))
        ready()
        assert not visible('.answer-sheet-dialog .el-alert--error')
        responsive('teacher-answer-sheet', (1440, 390))
        click(visible('.el-dialog__headerbtn')[0])
    navigate('/exams/' + str(chosen) + '/grade')
    responsive('teacher-grading', (1440, 390))
    navigate('/questions/create')
    responsive('question-editor', (1440, 768, 320))
    navigate('/profile')
    responsive('teacher-profile', (1440, 320))
    assert driver.execute_script('return window.blockedNetworkWrites.length') == 0
    print('PASS: teacher real read-only pages and responsive layouts.', flush=True)


def editor_fixtures():
    api('''
      window.mockExam={id:99001,title:'界面测试试卷',duration_minutes:120,description:'仅浏览器内模拟，不会写入数据库。',
        allow_retake:true,max_attempts:20,score_public:false,course_id:null,target_class:null,pass_score:4,total_score:7,
        open_time:'2026-10-20T08:30:00',close_time:'2026-10-21T18:30:00',
        exam_questions:[{question_id:99011,question_title:'第一题：测试长题干的完整预览与分值编辑。'.repeat(4),question_type:'single',score:3,order_num:1},{question_id:99012,question_title:'第二题：切换顺序时应保留分值。',question_type:'essay',score:4,order_num:2}]};
      api.examApi.get=async()=>structuredClone(window.mockExam);
      api.questionApi.list=async()=>({items:[{id:99011,title:window.mockExam.exam_questions[0].question_title,question_type:'single'},{id:99012,title:window.mockExam.exam_questions[1].question_title,question_type:'essay'},{id:99013,title:'第三题：标准化会造成数据泄漏吗？',question_type:'truefalse'}],total:3});
      api.examApi.update=async(id,data)=>{window.workflowWrites.push({method:'exam.update',id,data:structuredClone(data)});if(window.rejectExamSave)throw Error('mock failure');return {id}};
      window.rejectExamSave=true;
    ''')
    navigate('/exams/99001/edit')
    assert len(visible('.selected-item')) == 2
    click(driver.find_element(By.CSS_SELECTOR, '[aria-label="上移第 2 题"]'))
    assert visible('.selected-item')[0].get_attribute('data-question-id') == '99012'
    field('.selected-item:nth-child(2) .el-input-number input', '0.5')
    field('.selected-item .el-input-number input', '65')
    click(button('添加', visible('.pool-question')[2]))
    assert len(visible('.selected-item')) == 3
    click(driver.find_element(By.CSS_SELECTOR, '[aria-label="移除第 3 题"]'))
    assert len(visible('.selected-item')) == 2
    click(button('保存考试'))
    wait.until(lambda _: driver.execute_script("return window.workflowWrites.some(w=>w.method==='exam.update')"))
    ready()
    assert len(visible('.selected-item')) == 2
    payload = driver.execute_script("return window.workflowWrites.filter(w=>w.method==='exam.update').at(-1).data")
    assert payload['questions'][0]['question_id'] == 99012
    assert payload['questions'][0]['score'] == 65 and payload['max_attempts'] == 20
    assert payload['questions'][1]['score'] == 0.5
    assert [q['order_num'] for q in payload['questions']] == [1, 2]
    assert payload['open_time'] == '2026-10-20T08:30:00' and payload['close_time'] == '2026-10-21T18:30:00', payload
    api('window.rejectExamSave=false')
    click(button('保存考试'))
    ready('.management-list')
    assert driver.current_url.endswith('/exams')
    api("api.examApi.get=async()=>{throw Error('mock read failure')}")
    navigate('/exams/99001/edit')
    assert not visible('.exam-editor-form') and visible('.el-alert--error')
    api("Object.assign(api.examApi,original.examApi);Object.assign(api.questionApi,original.questionApi)")
    print('PASS: mocked editor reorder/add/remove/score/save failure/retry, unchanged Beijing schedule, and failed-load protection.', flush=True)



def question_and_grading_fixtures():
    api('''
      api.questionApi.get=async()=>({id:99020,title:'题型切换与选项重排测试',question_type:'single',difficulty:'medium',answer:'B',explanation:'示例解析',options:JSON.stringify(['A. 第一个选项','B. 第二个选项','C. 第三个选项']),tags:[]});
      api.questionApi.update=async(id,data)=>{window.workflowWrites.push({method:'question.update',data:structuredClone(data)});return {id}};
    ''')
    navigate('/questions/99020/edit')
    assert visible('.el-radio.is-checked')[-1].text == '中等'
    def choose_radio(text):
        click(next(e for e in visible('.el-radio') if e.text == text))
    choose_radio('多选题')
    assert visible('.el-checkbox.is-checked')[0].text == 'B'
    choose_radio('单选题')
    assert any(e.text == 'B' for e in visible('.el-radio.is-checked'))
    click(driver.find_element(By.CSS_SELECTOR, '[aria-label="删除选项 A"]'))
    assert any(e.text == 'A' for e in visible('.el-radio.is-checked'))
    click(button('保存题目'))
    ready('.management-list')
    saved=driver.execute_script("return window.workflowWrites.filter(w=>w.method==='question.update').at(-1).data")
    assert saved['answer']=='A' and len(json.loads(saved['options']))==2
    api('''
      window.mockGrades=[{id:99401,student_id:99,student_name:'浏览器测试学生',total_score:5,essay_graded:false}];
      window.rejectGrade=true;
      api.examApi.get=async()=>({id:99001,title:'模拟主观题批改'});
      api.examApi.records=async(id,params)=>({items:structuredClone(window.mockGrades),total:window.mockGrades.length,page:params.page});
      api.examApi.record=async()=>({id:99401,questions:[
        {answer_id:99501,question_id:99101,question_type:'single',title:'客观题',answer:'A',user_answer:'A',score:5,score_got:5,is_correct:'Y'},
        {answer_id:99502,question_id:99102,question_type:'essay',title:'请说明事务的隔离性，并给出一个业务场景。'.repeat(4),answer:'隔离并发事务，防止异常读写。',user_answer:'银行转账过程中，事务之间应相互隔离。',score:10,score_got:0,is_correct:'N'}]});
      api.examApi.grade=async(id,recordId,data)=>{window.workflowWrites.push({method:'grade.mock',recordId,data:structuredClone(data)});if(window.rejectGrade)throw Error('mock failure');window.mockGrades=[];return {total_score:12,essay_graded:true}};
    ''')
    navigate('/exams/99001/grade')
    click(visible('.el-collapse-item__header')[0])
    wait.until(lambda _: visible('.grade-score-control'))
    assert visible('.answer-index')[0].text == '第 2 题'
    responsive('grading-expanded-mock', (1440, 390, 320))
    field('.grade-score-control input', '7')
    click(visible('.el-collapse-item__header')[0])
    click(visible('.el-collapse-item__header')[0])
    assert visible('.grade-score-control input')[0].get_attribute('value') == '7'
    click(button('保存批改'))
    assert visible('.grade-score-control input')[0].get_attribute('value') == '7'
    api('window.rejectGrade=false')
    click(button('保存批改'))
    ready()
    assert '暂无需要批改' in visible('.el-empty')[0].text
    saved=driver.execute_script("return window.workflowWrites.filter(w=>w.method==='grade.mock').at(-1).data")
    assert saved['grades']==[{'answer_id':99502,'score_got':7,'is_correct':'P'}], saved
    api('Object.assign(api.examApi,original.examApi);Object.assign(api.questionApi,original.questionApi)')
    assert driver.execute_script('return window.blockedNetworkWrites.length')==0
    print('PASS: choice type switching retains answers, removed option remaps answers, essay numbering, grade changes survive collapse/failure and correct payload on retry.', flush=True)

def real_student():
    login('student')
    responsive('student-dashboard', (1440, 390))
    navigate('/exams', '.management-list')
    responsive('student-exams', (1440, 390))
    navigate('/my-records')
    responsive('student-records')
    records = api("const r=await api.studentApi.myRecords();return r.filter(v=>v.status!=='in_progress').slice(0,1).map(v=>({id:v.id,exam_id:v.exam_id}));")
    if records:
        item=records[0]
        navigate('/exams/' + str(item['exam_id']) + '/result?recordId=' + str(item['id']))
        responsive('student-result', (1440, 390))
        navigate('/exams/' + str(item['exam_id']) + '/leaderboard')
        if visible('.el-result') and '排行榜暂未公开' in visible('.el-result')[0].text:
            expected_forbidden_urls.add(base + '/api/exams/' + str(item['exam_id']) + '/leaderboard')
        responsive('student-leaderboard', (1440, 390))
    navigate('/wrong-book')
    responsive('student-wrong-book', (1440, 390, 320))
    navigate('/profile')
    responsive('student-profile', (1440, 320))
    assert driver.execute_script('return window.blockedNetworkWrites.length') == 0
    print('PASS: student real read-only pages and responsive layouts.', flush=True)


def student_fixtures():
    api('''
      window.resultReleased=false;
      window.reviewQuestions=[{question_id:99101,question_type:'truefalse',title:'验证判断题答案与解析的可见性。',answer:'False',user_answer:'True',score:5,score_got:0,is_correct:'N',explanation:'未公布时不得显示的解析标记'},
        {question_id:99102,question_type:'essay',title:'请简要说明数据质量检查的方法。',answer:'未公布时不得显示的参考答案',user_answer:'检查缺失值、重复值与异常值。',score:10,score_got:8,is_correct:'P'}];
      api.examApi.get=async()=>({id:99001,title:'学生界面回归试卷',total_score:15,pass_score:9});
      api.examApi.record=async(id,recordId)=>{window.workflowReads.push({method:'record',id,recordId});return {id:Number(recordId),status:'graded',essay_graded:true,result_released:window.resultReleased,total_score:8,submitted_at:'2026-10-14T13:20:01',questions:structuredClone(window.reviewQuestions)}};
      api.studentApi.wrongBookIds=async()=>({ids:[]});
      api.studentApi.myRecords=async()=>[{id:99201,exam_id:99001,exam_title:'保密成绩测试',total_score:9999,pass_score:9,status:'graded',essay_graded:true,result_released:false,submitted_at:'2026-10-14T13:20:01'}];
    ''')
    navigate('/my-records')
    assert '9999' not in driver.find_element(By.CSS_SELECTOR, '.student-records').text
    click(button('查看详情'))
    ready()
    assert 'recordId=99201' in driver.current_url
    text = driver.find_element(By.CSS_SELECTOR, '.exam-result').text
    assert '暂未公布' in text and '未公布时不得显示' not in text and '参考答案' not in text
    capture('result-unreleased')
    api('window.resultReleased=true')
    navigate('/exams/99001/result?recordId=99201')
    assert '未公布时不得显示的参考答案' in driver.find_element(By.CSS_SELECTOR, '.exam-result').text
    assert '错误' in visible('.reference-answer')[0].text
    api('''
      api.studentApi.wrongBook=async()=>structuredClone(window.reviewQuestions.map(q=>({...q,exam_title:'模拟来源考试'})));
      window.rejectWrongDelete=true;
      api.studentApi.removeWrong=async id=>{window.workflowWrites.push({method:'wrong.remove',id});if(window.rejectWrongDelete)throw Error('mock failure')};
    ''')
    navigate('/wrong-book')
    click(button('移出错题本'))
    click(button('确认移出'))
    assert len(visible('.answer-review')) == 2
    api('window.rejectWrongDelete=false')
    click(button('移出错题本'))
    click(button('确认移出'))
    wait.until(lambda _: len(visible('.answer-review')) == 1)
    api("api.examApi.leaderboard=async()=>{throw {response:{status:403}}}")
    navigate('/exams/99001/leaderboard')
    assert '排行榜暂未公开' in driver.find_element(By.CSS_SELECTOR, '.workflow-page').text
    print('PASS: hidden scores/answers, precise attempt navigation, Chinese boolean answers, wrong-book failed deletion and private leaderboard.', flush=True)


def take_fixture():
    api('''
      window.fixtureDraftVersion=0;window.rejectSubmit=true;
      const startedAt=new Date().toISOString();
      api.examApi.get=async()=>({id:99001,title:'模拟作答 · 所有写操作已隔离',description:'验证导航、草稿与提交，不影响真实考试。'});
      api.examApi.start=async()=>{window.workflowWrites.push({method:'exam.start.mock'});return {
        record_id:99301,status:'in_progress',started_at:startedAt,duration_minutes:60,draft_version:0,draft_answers:[],
        exam_questions:[
          {question_id:99101,question_type:'single',question_title:'以下哪项有助于提高数据质量？',score:5,options:JSON.stringify(['A. 检查缺失与异常数据','B. 忽略重复记录'])},
          {question_id:99102,question_type:'multiple',question_title:'请选择常见的数据质量问题。',score:5,options:JSON.stringify(['A. 缺失值','B. 重复值','C. 合法且唯一的数据'])},
          {question_id:99103,question_type:'truefalse',question_title:'模型评估应使用训练集之外的数据。',score:5},
          {question_id:99104,question_type:'essay',question_title:'说明你会如何处理重复数据。'.repeat(5),score:10}
        ]}};
      api.examApi.saveDraft=async(id,data)=>{window.workflowWrites.push({method:'draft.mock',data:structuredClone(data)});return {draft_version:++window.fixtureDraftVersion}};
      api.examApi.submit=async(id,data)=>{window.workflowWrites.push({method:'submit.mock',data:structuredClone(data)});if(window.rejectSubmit)throw Error('mock submit failure');return {id:99301,status:'submitted',result_released:false,essay_graded:false}};
    ''')
    navigate('/exams/99001/take')
    responsive('student-taking-mock', (1440, 1024, 768, 390, 320))
    click(visible('#q-0 .el-radio')[0])
    click(visible('#q-1 .el-checkbox')[0])
    click(visible('#q-1 .el-checkbox')[1])
    click(visible('#q-2 .el-radio')[0])
    field('#q-3 textarea', '先确定业务主键，再核验并合并重复记录。')
    wait.until(lambda _: driver.execute_script("return window.workflowWrites.some(w=>w.method==='draft.mock')"))
    assert '4 / 4' in visible('.nav-title')[0].text
    viewport(390)
    click(button('展开答题卡'))
    click(driver.find_element(By.CSS_SELECTOR, '[aria-label="跳到第 4 题"]'))
    wait.until(lambda _: not visible('.nav-grid'))
    capture('student-taking-mobile-answered')
    viewport(1440)
    click(button('提交答卷'))
    click(button('确认提交', visible('.el-message-box')[0]))
    wait.until(lambda _: driver.execute_script("return window.workflowWrites.some(w=>w.method==='submit.mock')"))
    assert visible('#q-3 textarea')[0].get_attribute('value').startswith('先确定')
    api('window.rejectSubmit=false')
    click(button('提交答卷'))
    click(button('确认提交', visible('.el-message-box')[0]))
    wait.until(lambda _: visible('.el-result'))
    assert '答卷已提交' in visible('.el-result')[0].text
    payload=driver.execute_script("return window.workflowWrites.filter(w=>w.method==='submit.mock').at(-1).data")
    assert payload['record_id']==99301
    assert [a['user_answer'] for a in payload['answers']][:3] == ['A','A,B','True']
    assert payload['draft_version'] > 0
    capture('student-submit-mock')
    assert driver.execute_script('return window.blockedNetworkWrites.length') == 0
    print('PASS: mock-only exam start, all four question types, mobile navigation, autosave, failed submit preserves answers and successful retry.', flush=True)


try:
    viewport(1440)
    login('teacher')
    real_teacher()
    editor_fixtures()
    question_and_grading_fixtures()
    real_student()
    student_fixtures()
    take_fixture()
    errors=[e['message'] for e in driver.get_log('browser') if e['level']=='SEVERE' and not ('403 (Forbidden)' in e['message'] and any(url in e['message'] for url in expected_forbidden_urls))]
    assert not errors, '\n'.join(errors)
    print('PASS: all workflow UI checks; no live business-data writes or unexpected browser errors. Private leaderboard 403 responses are expected.', flush=True)
except Exception:
    driver.save_screenshot(str(output / 'check-failure.png'))
    raise
finally:
    driver.quit()
