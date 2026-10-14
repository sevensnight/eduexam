"""管理列表的只读浏览器回归：真实数据截图、响应式布局、筛选和操作入口。

状态用例仅替换当前测试标签页中的 API 函数，不修改数据库。
运行前启动前后端；可通过 EDUEXAM_CHROMEDRIVER 指定本地 ChromeDriver。
"""
import json
import os
import base64
from pathlib import Path

from selenium import webdriver
from selenium.common.exceptions import StaleElementReferenceException
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait


base = os.environ.get('EDUEXAM_UI_URL', 'http://localhost:5173')
output = Path(__file__).resolve().parents[1] / 'artifacts' / 'lists-redesign'
output.mkdir(parents=True, exist_ok=True)
options = webdriver.ChromeOptions()
options.add_argument('--headless=new')
options.add_argument('--window-size=1440,1080')
options.set_capability('goog:loggingPrefs', {'browser': 'ALL'})
if os.environ.get('EDUEXAM_BROWSER_BINARY'):
    options.binary_location = os.environ['EDUEXAM_BROWSER_BINARY']
service = Service(
    executable_path=os.environ.get('EDUEXAM_CHROMEDRIVER'),
    service_args=['--disable-build-check'] if os.environ.get('EDUEXAM_DRIVER_SKIP_VERSION_CHECK') == '1' else [],
)
driver = webdriver.Chrome(service=service, options=options)
driver.set_script_timeout(20)
driver.set_page_load_timeout(30)
wait = WebDriverWait(driver, 20)
errors = []


def visible(selector):
    result = []
    for item in driver.find_elements(By.CSS_SELECTOR, selector):
        try:
            if item.is_displayed():
                result.append(item)
        except StaleElementReferenceException:
            # Loading overlays and table rows can be removed during this query.
            continue
    return result


def settle():
    driver.execute_async_script('const done=arguments[0];document.fonts.ready.then(()=>requestAnimationFrame(()=>requestAnimationFrame(done)))')


def ready():
    wait.until(lambda _: visible('.management-list'))
    wait.until(lambda _: not visible('.el-loading-mask'))
    settle()


def click(element):
    driver.execute_script('arguments[0].scrollIntoView({block:"center"})', element)
    element.click()
    settle()


def button(text, root=None):
    scope = root or driver
    return next(el for el in scope.find_elements(By.XPATH, './/button[normalize-space()="' + text + '"]') if el.is_displayed())


def viewport(width):
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': width, 'height': 1080 if width > 767 else 900, 'deviceScaleFactor': 1, 'mobile': width < 768,
    })
    driver.execute_script('window.scrollTo(0,0)')
    settle()


def capture(name):
    driver.execute_script('window.scrollTo(0,0)')
    settle()
    layout = driver.execute_script('''
      const table = document.querySelector('.list-table');
      const tableVisible = !!table?.getBoundingClientRect().width;
      const body = table?.querySelector('.el-table__body');
      return {
        viewport:innerWidth,
        pageOverflow:document.documentElement.scrollWidth > innerWidth + 1,
        tableVisible,
        tableOverflow:tableVisible && body.getBoundingClientRect().width > table.getBoundingClientRect().width + 2,
        compactVisible:!!document.querySelector('.compact-list')?.getBoundingClientRect().width,
        buttonOverflow:[...document.querySelectorAll('.header-actions .el-button')].some(button=>{
          const r=button.getBoundingClientRect();
          return [...button.children].some(child=>{
            const c=child.getBoundingClientRect();return c.left<r.left-1 || c.right>r.right+1;
          });
        }),
      };
    ''')
    assert not layout['pageOverflow'], (name, layout)
    assert not layout['tableOverflow'], (name, layout)
    assert not layout['buttonOverflow'], (name, 'header button text is clipped', layout)
    assert layout['tableVisible'] != layout['compactVisible'], (name, 'exactly one layout must be visible', layout)
    driver.save_screenshot(str(output / (name + '.png')))
    errors.extend(entry['message'] for entry in driver.get_log('browser') if entry['level'] == 'SEVERE')
    print(json.dumps({'capture': name, **layout}, ensure_ascii=False), flush=True)


def check_type_difficulty_alignment():
    result = driver.execute_script('''
      const table=document.querySelector('.question-list .list-table');
      if (!table?.getBoundingClientRect().width) return {ok:true};
      const headers=[...table.querySelectorAll('thead th')].map(el=>el.innerText.trim());
      const aligned=[...table.querySelectorAll('.el-table__row')].every(row=>{
        const kind=row.querySelector('.question-kind')?.getBoundingClientRect();
        const difficulty=row.querySelector('.difficulty-tag')?.getBoundingClientRect();
        return kind && difficulty && kind.right+8<=difficulty.left &&
          Math.abs((kind.top+kind.bottom)/2-(difficulty.top+difficulty.bottom)/2)<=6;
      });
      return {ok:headers.includes('题型') && headers.includes('难度') && aligned,headers,aligned};
    ''')
    assert result['ok'], ('type and difficulty must be separate columns on the same line', result)


def capture_judgement_detail(name, trigger, details):
    clip = driver.execute_script('''
      const [trigger,details]=arguments;
      const table=document.querySelector('.question-list .list-table');
      const desktop=!!table.getBoundingClientRect().width;
      const row=desktop?trigger.closest('tr'):trigger.closest('.compact-item');
      const bounds=desktop?table.getBoundingClientRect():row.getBoundingClientRect();
      const start=row.getBoundingClientRect();
      const end=desktop?details.closest('tr').getBoundingClientRect():start;
      return {x:bounds.left+scrollX,y:start.top+scrollY,width:bounds.width,height:end.bottom-start.top,scale:1};
    ''', trigger, details)
    shot = driver.execute_cdp_cmd('Page.captureScreenshot', {'format':'png','captureBeyondViewport':True,'clip':clip})
    (output / (name + '.png')).write_bytes(base64.b64decode(shot['data']))


def login(username, password):
    driver.get(base + '/login')
    result = driver.execute_async_script('''
      const [username,password,done]=arguments;
      fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})})
        .then(async response=>{
          const data=await response.json();
          if(!response.ok) return done({ok:false});
          sessionStorage.setItem('token',data.access_token);
          sessionStorage.setItem('user',JSON.stringify(data.user));
          localStorage.removeItem('token');localStorage.removeItem('user');done({ok:true});
        }).catch(error=>done({ok:false,message:error.message}));
    ''', username, password)
    assert result['ok'], result


def visit(route):
    driver.get(base + route)
    ready()
    wait.until(lambda _: visible('.list-table .el-table__row, .compact-item, .el-empty, .list-error'))


def patch_api(code):
    result = driver.execute_async_script('''
      const [code,done]=arguments;
      const url=performance.getEntriesByType('resource').map(entry=>entry.name).find(url=>/\\/src\\/api\\/index\\.js(?:\\?|$)/.test(url));
      import(url || '/src/api/index.js').then(({questionApi,examApi})=>{
        window.listOriginalApi ||= {questions:{...questionApi},exams:{...examApi}};
        const original=window.listOriginalApi;
        new Function('questionApi','examApi','original',code)(questionApi,examApi,original);
        done({ok:true});
      }).catch(error=>done({ok:false,message:error.message}));
    ''', code)
    assert result['ok'], result


def remount(route):
    result = driver.execute_async_script('''
      const [route,done]=arguments;
      const router=document.querySelector('#app').__vue_app__.config.globalProperties.$router;
      router.push('/dashboard').then(()=>router.push(route)).then(()=>done(true)).catch(error=>done(error.message));
    ''', route)
    assert result is True, result
    ready()


def check_questions():
    visit('/questions')
    wait.until(lambda _: visible('.question-trigger'))
    for width in [1440, 1280, 1100, 1024, 768, 390, 320]:
        viewport(width)
        check_type_difficulty_alignment()
        capture('questions-' + str(width))

    viewport(1440)
    trigger = visible('.question-trigger')[0]
    title = trigger.text
    trigger.send_keys(Keys.SPACE)
    wait.until(lambda _: trigger.get_attribute('aria-expanded') == 'true')
    detail = visible('.question-details')[0]
    assert title in detail.text and '正确答案' in detail.text and '解析' in detail.text
    capture('question-expanded-desktop')
    viewport(390)
    assert visible('.compact-details') and title in visible('.compact-details')[0].text
    capture('question-expanded-mobile')
    click(visible('.question-trigger')[0])
    wait.until(lambda _: not visible('.question-details'))

    viewport(1440)
    judgement_row = next(row for row in visible('.list-table .el-table__row') if row.find_element(By.CSS_SELECTOR, '.question-kind').text == '判断')
    judgement_trigger = judgement_row.find_element(By.CSS_SELECTOR, '.question-trigger')
    judgement_title = judgement_trigger.text
    click(judgement_trigger)
    wait.until(lambda _: visible('.question-details'))
    detail = visible('.question-details')[0]
    assert detail.find_element(By.CSS_SELECTOR, '.detail-answer .detail-copy').text in ['正确', '错误']
    assert driver.execute_script('return getComputedStyle(arguments[0]).backgroundColor', detail.find_element(By.CSS_SELECTOR, '.detail-answer')) == 'rgba(0, 0, 0, 0)'
    capture('judgement-expanded-desktop')
    capture_judgement_detail('judgement-detail-desktop', judgement_trigger, detail)
    viewport(390)
    mobile_trigger = next(el for el in visible('.question-trigger') if el.text == judgement_title)
    detail = visible('.question-details')[0]
    capture_judgement_detail('judgement-detail-mobile', mobile_trigger, detail)
    click(mobile_trigger)
    viewport(1440)
    click(visible('.el-pagination .btn-next')[0])
    ready()
    assert visible('.el-table__row')[0].text.startswith('21')
    search = driver.find_element(By.ID, 'question-search')
    search.send_keys('F1')
    search.send_keys(Keys.ENTER)
    wait.until(lambda _: '搜索：F1' in driver.find_element(By.CSS_SELECTOR, '.active-filters').text)
    ready()
    assert visible('.el-table__row')[0].text.startswith('1')
    assert all('F1' in el.text for el in visible('.question-trigger'))
    click(visible('.active-filters .el-tag__close')[0])
    ready()
    assert not visible('.active-filters')

    click(button('更多筛选'))
    wait.until(lambda _: visible('#question-advanced-filters'))
    click(driver.find_element(By.CSS_SELECTOR, '#question-advanced-filters .filter-field:last-of-type .el-select'))
    wait.until(lambda _: visible('.el-select-dropdown__item'))
    click(next(el for el in visible('.el-select-dropdown__item') if el.text == '困难'))
    ready()
    assert '难度：困难' in driver.find_element(By.CSS_SELECTOR, '.active-filters').text
    click(visible('.more-filters')[0])
    assert not visible('#question-advanced-filters') and visible('.active-filters')
    capture('questions-filtered')
    click(button('重置'))
    ready()

    search = driver.find_element(By.ID, 'question-search')
    search.send_keys('no_match_ui_check_926840')
    search.send_keys(Keys.ENTER)
    ready()
    wait.until(lambda _: not visible('.question-trigger'))
    capture('questions-empty-desktop')
    viewport(390)
    assert visible('.compact-list .el-empty')
    capture('questions-empty-mobile')
    click(button('重置'))
    ready()
    print('PASS: question expansion, keyboard, responsive state, pagination, Enter search, removable/advanced filters, empty state.', flush=True)


def check_exams():
    viewport(1440)
    visit('/exams')
    wait.until(lambda _: visible('.exam-title'))
    for width in [1440, 1280, 1100, 1024, 768, 390, 320]:
        viewport(width)
        capture('exams-' + str(width))
    viewport(1440)
    assert not driver.find_elements(By.CSS_SELECTOR, '.el-table-fixed-column--right')
    times_fit = driver.execute_script('''
      return [...document.querySelectorAll('.list-table .exam-schedule dd > span')].every(span=>{
        const r=span.getBoundingClientRect(), c=span.closest('td').getBoundingClientRect();
        return r.left>=c.left && r.right<=c.right+1;
      });
    ''')
    assert times_fit, 'exam dates or seconds are clipped'
    patch_api('window.listMutationCalls=[];examApi.remove=async id=>{window.listMutationCalls.push(id);return {}};')
    click(visible('.exam-more')[0])
    wait.until(lambda _: visible('.el-dropdown-menu__item'))
    menu = visible('.el-dropdown-menu__item')
    assert any('编辑考试' in el.text for el in menu)
    assert any('批改' in el.text for el in menu)
    click(next(el for el in menu if el.text == '删除考试'))
    wait.until(lambda _: visible('.el-message-box'))
    click(button('取消', visible('.el-message-box')[0]))
    assert driver.execute_script('return window.listMutationCalls.length') == 0
    print('PASS: complete exam dates, compact operations, menu entries, delete confirmation cancellation.', flush=True)


def check_states():
    viewport(1440)
    visit('/questions')
    patch_api('questionApi.list=async()=>{throw new Error("Simulated offline")};')
    click(button('重置'))
    ready()
    assert visible('.list-error')
    patch_api('questionApi.list=original.questions.list;')
    click(button('重新加载'))
    ready()
    assert not visible('.list-error') and visible('.question-trigger')
    patch_api('''
      questionApi.list=params=>new Promise(resolve=>setTimeout(()=>resolve({items:[{
        id:900001,title:params.keyword==='slow'?'过期响应':'最新响应',question_type:'single',difficulty:'easy',
        answer:'A',options:'["A. 测试选项"]',tags:[],usage_count:0,
      }],total:1}),params.keyword==='slow'?900:20));
    ''')
    search = driver.find_element(By.ID, 'question-search')
    search.send_keys('slow', Keys.ENTER)
    search.send_keys(Keys.CONTROL, 'a')
    search.send_keys('fast', Keys.ENTER)
    wait.until(lambda _: visible('.question-trigger') and visible('.question-trigger')[0].text == '最新响应')
    driver.execute_async_script('setTimeout(arguments[0],1100)')
    assert visible('.question-trigger')[0].text == '最新响应'
    patch_api('questionApi.list=original.questions.list;')
    click(button('重置'))
    ready()

    visit('/exams')
    patch_api('examApi.list=async()=>({items:[],total:0});')
    remount('/exams')
    wait.until(lambda _: not visible('.exam-title'))
    viewport(390)
    assert visible('.compact-list .el-empty')
    capture('exams-empty-mobile')
    patch_api('examApi.list=async()=>{throw new Error("Simulated offline")};')
    remount('/exams')
    assert visible('.list-error')
    patch_api('examApi.list=original.exams.list;')
    click(button('重新加载'))
    ready()
    assert not visible('.list-error') and visible('.exam-title')
    print('PASS: failed requests, retry, out-of-order question responses, empty exam state.', flush=True)


def check_edges():
    viewport(1440)
    visit('/questions')
    patch_api('''
      window.listQueries=[];window.listExports=[];window.listDownloads=[];
      questionApi.list=params=>{window.listQueries.push(params);return original.questions.list(params)};
      questionApi.export=async params=>{window.listExports.push(params);return new Blob(['UI test'])};
      window.originalAnchorClick=HTMLAnchorElement.prototype.click;
      HTMLAnchorElement.prototype.click=function(){
        if(this.download) window.listDownloads.push(this.download);
        else window.originalAnchorClick.call(this);
      };
    ''')
    type_select = driver.find_elements(By.CSS_SELECTOR, '.filter-primary .el-select')[1]
    click(type_select)
    wait.until(lambda _: visible('.el-select-dropdown__item'))
    click(next(el for el in visible('.el-select-dropdown__item') if el.text == '单选'))
    ready()
    assert driver.execute_script('return window.listQueries.at(-1).question_type') == 'single'
    ActionChains(driver).move_to_element(type_select).perform()
    clear = wait.until(lambda _: visible('.filter-primary .el-select__clear'))[0]
    click(clear)
    ready()
    assert not visible('.active-filters'), 'cleared selects must not leave an undefined filter chip'
    assert driver.execute_script('return window.listQueries.at(-1).question_type == null')
    click(driver.find_element(By.CSS_SELECTOR, '.sort-select'))
    wait.until(lambda _: visible('.el-select-dropdown__item'))
    click(next(el for el in visible('.el-select-dropdown__item') if el.text == '使用次数：从多到少'))
    ready()
    assert driver.execute_script('return window.listQueries.at(-1).sort_by') == 'usage_count'
    assert driver.execute_script('return window.listQueries.at(-1).sort_order') == 'desc'
    driver.find_element(By.ID, 'question-search').send_keys('未提交的搜索条件')
    for label, extension in [('导出 CSV', 'csv'), ('导出 Excel', 'xlsx')]:
        click(button('导出'))
        wait.until(lambda _: visible('.el-dropdown-menu__item'))
        click(next(el for el in visible('.el-dropdown-menu__item') if el.text == label))
        wait.until(lambda _: driver.execute_script('return window.listDownloads.length') > (0 if extension == 'csv' else 1))
        assert driver.execute_script('return window.listExports.at(-1).format') == extension
        assert driver.execute_script('return window.listExports.at(-1).keyword == null'), 'export must use applied filters'
        assert driver.execute_script('return window.listDownloads.at(-1)') == 'questions_export.' + extension
    patch_api('HTMLAnchorElement.prototype.click=window.originalAnchorClick;')

    patch_api('''
      questionApi.list=async()=>({items:[{
        id:900101,title:'长题干边界验证：'+('连续内容'.repeat(100))+'UNBROKEN'.repeat(30),
        question_type:'multiple',difficulty:'hard',usage_count:12,category_name:'多层级分类'.repeat(12),
        tags:[{id:1,name:'长标签'.repeat(20)},{id:2,name:'标签二'},{id:3,name:'标签三'}],
        options:JSON.stringify(['A. '+'长选项'.repeat(60),'B. 另一个选项']),answer:'A,B',
        explanation:'长解析'.repeat(80),updated_at:'2026-10-14T10:30:01',
      }],total:1});
    ''')
    click(button('重置'))
    ready()
    click(visible('.question-trigger')[0])
    assert visible('.detail-options')
    capture('fixture-long-question-desktop')
    viewport(320)
    capture('fixture-long-question-mobile')
    assert '标签三' in visible('.question-details')[0].text

    visit('/exams')
    patch_api('''
      examApi.list=async()=>({items:[{
        id:900102,title:'长考试名称边界验证'.repeat(12),course_name:'COURSE'.repeat(30),target_class:'长班级名称'.repeat(15),
        duration_minutes:120,total_score:100,pass_score:60,question_count:20,max_attempts:3,allow_retake:true,
        status:'draft',has_essay:true,open_time:'2026-10-14T10:00:00',close_time:'2027-01-01T23:59:59',
      }],total:1});
      window.listMutationCalls=[];
      examApi.update=async(...args)=>{window.listMutationCalls.push(args);return {}};
      window.listRouteCalls=[];
      const router=document.querySelector('#app').__vue_app__.config.globalProperties.$router;
      window.originalListRouterPush=router.push;
    ''')
    remount('/exams')
    wait.until(lambda _: visible('.exam-title') and '边界验证' in visible('.exam-title')[0].text)
    capture('fixture-long-exam-mobile')
    viewport(1440)
    capture('fixture-long-exam-desktop')
    click(visible('.exam-more')[0])
    wait.until(lambda _: visible('.el-dropdown-menu__item'))
    click(next(el for el in visible('.el-dropdown-menu__item') if el.text == '发布考试'))
    wait.until(lambda _: visible('.el-message-box'))
    click(button('取消', visible('.el-message-box')[0]))
    assert driver.execute_script('return window.listMutationCalls.length') == 0
    patch_api('''
      const router=document.querySelector('#app').__vue_app__.config.globalProperties.$router;
      router.push=path=>{window.listRouteCalls.push(path);return Promise.resolve()};
    ''')
    click(button('编辑', visible('.list-table')[0]))
    assert driver.execute_script('return window.listRouteCalls.at(-1)') == '/exams/900102/edit'
    for label, route in [('查看统计', 'stats'), ('批改答卷', 'grade')]:
        click(visible('.exam-more')[0])
        wait.until(lambda _: visible('.el-dropdown-menu__item'))
        click(next(el for el in visible('.el-dropdown-menu__item') if el.text == label))
        assert driver.execute_script('return window.listRouteCalls.at(-1)') == '/exams/900102/' + route
    patch_api('document.querySelector("#app").__vue_app__.config.globalProperties.$router.push=window.originalListRouterPush;')
    print('PASS: cleared select values, sorting, both export formats, applied export filters, long content, action routes, cancelled publication.', flush=True)


try:
    viewport(1440)
    login('admin', os.environ.get('EDUEXAM_ADMIN_PASSWORD', 'admin123'))
    check_questions()
    check_exams()
    check_states()
    check_edges()
    login('teacher1', os.environ.get('EDUEXAM_TEACHER_PASSWORD', 'teacher123'))
    viewport(1440)
    visit('/questions')
    assert visible('.question-trigger') and visible('.header-actions')
    visit('/exams')
    assert visible('.exam-more')
    login('student1', os.environ.get('EDUEXAM_STUDENT_PASSWORD', 'student123'))
    visit('/exams')
    assert not visible('.exam-more') and visible('.student-actions')
    capture('student-exams-desktop')
    viewport(390)
    capture('student-exams-mobile')
    assert not errors, '\n'.join(errors)
    print('PASS: teacher/student action permissions; all list checks completed without browser errors.', flush=True)
except Exception:
    driver.save_screenshot(str(output / 'check-failure.png'))
    raise
finally:
    driver.quit()
