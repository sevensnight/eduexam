"""课程/用户管理回归：真实数据只读，写操作仅在独立浏览器标签页中模拟。"""
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
output = Path(__file__).resolve().parents[1] / 'artifacts/directory-redesign'
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
driver.set_page_load_timeout(30)
wait = WebDriverWait(driver, 20)


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
      const done=arguments[0];
      document.fonts.ready.then(()=>requestAnimationFrame(()=>{
        const animations=document.getAnimations().filter(a=>Number.isFinite(a.effect.getComputedTiming().endTime));
        Promise.allSettled(animations.map(a=>a.finished)).then(()=>requestAnimationFrame(done));
      }));
    ''')


def ready():
    wait.until(lambda _: visible('.directory-management'))
    wait.until(lambda _: not visible('.el-loading-mask'))
    settle()


def click(item):
    driver.execute_script('arguments[0].scrollIntoView({block:"center"})', item)
    item.click()
    settle()


def button(text, root=None):
    return next(item for item in (root or driver).find_elements(By.XPATH, './/button[normalize-space()="' + text + '"]') if item.is_displayed())


def field(selector, text):
    item = driver.find_element(By.CSS_SELECTOR, selector)
    item.send_keys(Keys.CONTROL, 'a')
    item.send_keys(Keys.BACKSPACE)
    item.send_keys(text)
    settle()


def select(selector, text):
    item = driver.find_element(By.CSS_SELECTOR, selector)
    click(item.find_element(By.XPATH, './ancestor::*[contains(concat(" ", normalize-space(@class), " "), " el-select ")][1]'))
    wait.until(lambda _: visible('.el-select-dropdown__item'))
    target = next(item for item in visible('.el-select-dropdown__item') if item.get_attribute('textContent').strip() == text)
    click(target)


def menu(text, index=0):
    click(visible('.management-more')[index])
    wait.until(lambda _: visible('.el-dropdown-menu__item'))
    click(next(item for item in visible('.el-dropdown-menu__item') if item.text == text))


def viewport(width):
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {'width': width, 'height': 1080 if width > 767 else 900, 'deviceScaleFactor': 1, 'mobile': width < 768})
    settle()


def capture(name):
    driver.execute_script('window.scrollTo(0,0)')
    settle()
    result = driver.execute_script('''
      const shown=e=>!!e.getClientRects().length && getComputedStyle(e).visibility!=='hidden';
      const rect=e=>e.getBoundingClientRect();
      return {width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth+1,
        table:[...document.querySelectorAll('.list-table')].some(shown),
        compact:[...document.querySelectorAll('.compact-list')].some(shown),
        clippedButtons:[...document.querySelectorAll('.directory-management button,.el-dialog button')].filter(shown).filter(el=>{
          const a=rect(el); return [...el.children].some(child=>{const b=rect(child);return b.left<a.left-1 || b.right>a.right+1});
        }).map(el=>el.textContent),
        clippedCells:[...document.querySelectorAll('.directory-management .el-table__cell')].filter(shown).some(el=>el.scrollWidth>el.clientWidth+2),
        overflowingDialogs:[...document.querySelectorAll('.el-dialog')].filter(shown).some(el=>el.scrollWidth>el.clientWidth+1 || rect(el).right>innerWidth+1)};
    ''')
    assert not result['overflow'] and not result['clippedCells'] and not result['clippedButtons'] and not result['overflowingDialogs'], (name, result)
    assert result['table'] != result['compact'], (name, result)
    driver.save_screenshot(str(output / (name + '.png')))
    print(json.dumps({'capture': name, **result}, ensure_ascii=False), flush=True)


def patch_api(code):
    result = driver.execute_async_script('''
      const [code,done]=arguments;
      const url=performance.getEntriesByType('resource').map(e=>e.name).find(url=>/\\/src\\/api\\/index\\.js(?:\\?|$)/.test(url));
      import(url || '/src/api/index.js').then(({courseApi,userApi})=>{
        window.directoryOriginal ||= {course:{...courseApi},user:{...userApi}};
        new Function('courseApi','userApi','original',code)(courseApi,userApi,window.directoryOriginal);
        done({ok:true});
      }).catch(error=>done({ok:false,message:error.message}));
    ''', code)
    assert result['ok'], result


def guard_writes():
    patch_api('''
      window.directoryWrites=[];window.directoryReads=[];
      for(const [api,methods] of [[courseApi,['create','update','remove','enroll']],[userApi,['setRole','toggleActive','importStudents']]])
        for(const method of methods) api[method]=async(...args)=>{window.directoryWrites.push({method,args});throw new Error('Unexpected write blocked by visual test')};
      courseApi.students=params=>{window.directoryReads.push({method:'students',params});return original.course.students(params)};
      userApi.list=params=>{window.directoryReads.push({method:'users',params});return original.user.list(params)};
    ''')


def navigate(route):
    driver.execute_async_script('''
      const [route,done]=arguments,router=document.querySelector('#app').__vue_app__.config.globalProperties.$router;
      router.push('/dashboard').then(()=>router.push(route)).then(()=>done(true));
    ''', route)
    ready()


def real_checks():
    capture('courses-desktop')
    for width in [1280, 1100, 1024, 768, 390, 320]:
        viewport(width)
        capture('courses-' + str(width))
    viewport(1440)
    first_name = visible('.course-name')[0].text
    click(visible('.course-name')[0])
    wait.until(lambda _: visible('.directory-detail-dialog'))
    assert first_name in visible('.detail-list')[0].text
    capture('course-details')
    click(button('关闭', visible('.el-dialog')[0]))
    click(button('选课学生'))
    wait.until(lambda _: visible('.enrollment-dialog'))
    ready()
    assert not visible('.enrollment-dialog .el-alert--error')
    assert driver.execute_script("return window.directoryReads.some(r=>r.method==='students' && r.params.courseId && !('course_id' in r.params))")
    capture('course-enrollment')
    viewport(320)
    capture('course-enrollment-mobile')
    click(button('取消', visible('.el-dialog')[0]))
    viewport(1440)
    field('#course-search', '不存在的课程__ui_check')
    driver.find_element(By.ID, 'course-search').send_keys(Keys.ENTER)
    ready()
    assert '没有匹配的课程' in visible('.el-empty')[0].text
    click(button('重置'))
    ready()
    menu('删除课程')
    wait.until(lambda _: visible('.el-message-box'))
    click(button('取消', visible('.el-message-box')[0]))
    click(button('新增课程'))
    wait.until(lambda _: visible('.course-dialog'))
    ready()
    capture('course-form')
    click(button('取消', visible('.el-dialog')[0]))

    navigate('/admin/users')
    capture('users-desktop')
    for width in [1280, 1100, 1024, 768, 390, 320]:
        viewport(width)
        capture('users-' + str(width))
    viewport(1440)
    # A real read-only class filter verifies the Spring query parameter contract.
    click(driver.find_element(By.ID, 'user-class').find_element(By.XPATH, './ancestor::div[contains(@class,"el-select")][last()]'))
    wait.until(lambda _: visible('.el-select-dropdown__item'))
    classes = [item.get_attribute('textContent').strip() for item in visible('.el-select-dropdown__item')]
    assert classes
    click(visible('.el-select-dropdown__item')[0])
    click(button('查询'))
    ready()
    assert driver.execute_script("return window.directoryReads.some(r=>r.method==='users' && r.params?.className && !('class_name' in r.params))")
    assert all(classes[0] in row.text for row in visible('.list-table .el-table__row'))
    assert '筛选结果' in visible('.list-heading')[0].text
    click(driver.find_element(By.ID, 'user-class').find_element(By.XPATH, './ancestor::div[contains(@class,"el-select")][last()]'))
    assert len(visible('.el-select-dropdown__item')) == len(classes)
    driver.switch_to.active_element.send_keys(Keys.ESCAPE)
    click(button('重置'))
    ready()
    select('#user-role', '教师')
    click(button('查询'))
    ready()
    assert all(item.text == '教师' for item in visible('.list-table .user-role'))
    assert not button('导出学生').is_enabled()
    click(button('详情'))
    wait.until(lambda _: visible('.directory-detail-dialog'))
    capture('user-details')
    click(button('关闭', visible('.el-dialog')[0]))
    click(button('重置'))
    ready()
    assert driver.execute_script('return window.directoryWrites.length') == 0
    print('PASS: real course/user data, responsive lists, detail/form dialogs, class/role filtering, enrollment query and cancellation.', flush=True)


def fixture_checks():
    # All fixture actions write only to these in-memory arrays, never the database.
    patch_api('''
      window.testUsers=[
        {id:9101,username:'u_alpha',real_name:'测试甲',class_name:'甲班',email:'alpha@example.test',role:'student',is_active:true,created_at:'2026-10-14T08:12:34'},
        {id:9102,username:'u_beta',real_name:'测试乙',class_name:'乙班',email:'beta@example.test',role:'student',is_active:true,created_at:'2026-10-14T08:12:34'},
        {id:9103,username:'u_disabled',real_name:'停用学生',class_name:'乙班',email:'disabled@example.test',role:'student',is_active:false,created_at:'2026-10-14T08:12:34'},
        {id:9201,username:'teacher_test',real_name:'测试教师',role:'teacher',is_active:true,created_at:'2026-10-14T08:12:34'},
        {id:9202,username:'teacher_disabled',real_name:'停用教师',role:'teacher',is_active:false,created_at:'2026-10-14T08:12:34'},
        {...document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('user').user,created_at:'2026-10-14T08:12:34',is_active:true}];
      window.testCourses=[{id:9301,name:'浏览器测试课程',description:'测试说明，不写入数据库。',teacher_id:9201,teacher_name:'测试教师',student_ids:[9101,9103],student_count:2,created_at:'2026-10-14T08:12:34'}];
      courseApi.list=async()=>structuredClone(window.testCourses);
      courseApi.classes=async()=>['甲班','乙班'];
      courseApi.get=async id=>({course:structuredClone(window.testCourses.find(c=>c.id===id)),students:structuredClone(window.testUsers.filter(u=>[9101,9103].includes(u.id)))});
      courseApi.students=async params=>{window.directoryReads.push({method:'students',params});return structuredClone(window.testUsers.filter(u=>u.role==='student'&&u.is_active))};
      userApi.list=async params=>{window.directoryReads.push({method:'users',params});return structuredClone(window.testUsers.filter(u=>(!params?.role||u.role===params.role)&&(!params?.className||u.class_name===params.className)&&(!params?.keyword||[u.real_name,u.username,u.email,u.class_name].join(' ').includes(params.keyword))))};
      window.rejectRole=true;window.rejectStatus=true;
      userApi.setRole=async(id,role)=>{window.directoryWrites.push({method:'setRole',id,role});if(window.rejectRole)throw Error('fixture role rejection');window.testUsers.find(u=>u.id===id).role=role};
      userApi.toggleActive=async id=>{window.directoryWrites.push({method:'toggleActive',id});if(window.rejectStatus)throw Error('fixture status rejection');const user=window.testUsers.find(u=>u.id===id);user.is_active=!user.is_active;return structuredClone(user)};
      courseApi.enroll=async(id,ids)=>{window.directoryWrites.push({method:'enroll',id,ids});const course=window.testCourses.find(c=>c.id===id);course.student_ids=[...ids];course.student_count=ids.length};
      userApi.exportStudents=async params=>{window.directoryWrites.push({method:'export',params});return new Blob(['fixture'])};
      HTMLAnchorElement.prototype.click=function(){window.downloadedName=this.download};
    ''')
    navigate('/courses')
    click(button('选课学生'))
    ready()
    assert '1 个已选账号' in visible('.roster-warning')[0].text
    assert not button('保存选课').is_enabled()
    click(button('移除不可用学生'))
    assert '已选 1 人' in visible('.roster-footer')[0].text
    select('input[aria-label="筛选学生班级"]', '乙班')
    assert len(visible('.roster-student')) == 2
    active_checkbox = next(item for item in visible('.roster-student .el-checkbox') if 'is-disabled' not in item.get_attribute('class'))
    click(active_checkbox)
    assert '已选 2 人' in visible('.roster-footer')[0].text
    select('input[aria-label="筛选学生班级"]', '甲班')
    assert 'is-checked' in visible('.roster-student .el-checkbox')[0].get_attribute('class')
    click(button('保存选课'))
    wait.until(lambda _: not visible('.enrollment-dialog'))
    ready()
    assert driver.execute_script("return JSON.stringify(window.directoryWrites.find(w=>w.method==='enroll').ids.slice().sort())") == '[9101,9102]'
    print('PASS: unavailable enrollment warning, explicit removal, class filters and cross-filter selection preservation (mocked save).', flush=True)

    navigate('/admin/users')
    menu('修改角色')
    wait.until(lambda _: visible('.role-dialog'))
    select('.role-dialog input', '教师')
    click(button('确认修改'))
    wait.until(lambda _: not visible('.role-dialog .is-loading'))
    assert visible('.role-dialog')
    assert visible('.list-table .user-role')[0].text == '学生'
    assert driver.execute_script("return window.directoryWrites.filter(w=>w.method==='setRole').length") == 1
    driver.execute_script('window.rejectRole=false')
    click(button('确认修改'))
    wait.until(lambda _: not visible('.role-dialog'))
    ready()
    assert visible('.list-table .user-role')[0].text == '教师'
    menu('禁用账号')
    wait.until(lambda _: visible('.el-message-box'))
    click(button('取消', visible('.el-message-box')[0]))
    assert driver.execute_script("return window.directoryWrites.filter(w=>w.method==='toggleActive').length") == 0
    menu('禁用账号')
    click(button('确认禁用', visible('.el-message-box')[0]))
    wait.until(lambda _: not visible('.el-message-box'))
    ready()
    assert visible('.list-table .user-state')[0].text == '正常'
    driver.execute_script('window.rejectStatus=false')
    menu('禁用账号')
    click(button('确认禁用', visible('.el-message-box')[0]))
    wait.until(lambda _: not visible('.el-message-box'))
    ready()
    assert visible('.list-table .user-state')[0].text == '禁用'
    self_row = next(item for item in visible('.list-table .el-table__row') if '本人' in item.text)
    click(self_row.find_element(By.CSS_SELECTOR, '.management-more'))
    assert all('is-disabled' in item.get_attribute('class') for item in visible('.el-dropdown-menu__item'))
    driver.switch_to.active_element.send_keys(Keys.ESCAPE)

    # Draft filters must not accidentally change an export of the applied result set.
    select('#user-class', '乙班')
    click(button('查询'))
    ready()
    field('#user-search', '未提交的搜索词')
    click(button('导出学生'))
    click(next(item for item in visible('.el-dropdown-menu__item') if item.text == '导出 CSV'))
    wait.until(lambda _: driver.execute_script('return window.downloadedName'))
    assert driver.execute_script("return window.directoryWrites.find(w=>w.method==='export').params") == {'className': '乙班', 'role': 'student', 'format': 'csv'}
    print('PASS: explicit role/status confirmations, rejected-write rollback, self-account protection and applied-filter export (all writes mocked).', flush=True)


def edge_checks():
    patch_api('''
      window.fixtureListUsers=userApi.list;
      window.fixtureListCourses=courseApi.list;
      window.fixtureGetCourse=courseApi.get;
      window.rejectList=true;
      userApi.list=async params=>{if(window.rejectList)throw Error('fixture list failure');return window.fixtureListUsers(params)};
    ''')
    navigate('/admin/users')
    assert visible('.list-error')
    assert not visible('.list-table,.compact-list')
    driver.execute_script('window.rejectList=false')
    click(button('重新加载'))
    ready()
    assert visible('.list-table')

    # Resolve an earlier request after a more recent search to exercise stale-response guards.
    patch_api('''
      userApi.list=params=>params?.keyword==='slow'?new Promise(resolve=>{window.resolveSlow=resolve}):window.fixtureListUsers(params);
    ''')
    field('#user-search', 'slow')
    click(button('查询'))
    wait.until(lambda _: driver.execute_script('return !!window.resolveSlow'))
    field('#user-search', '测试乙')
    click(button('查询'))
    ready()
    assert len(visible('.list-table .el-table__row')) == 1
    driver.execute_async_script('''const done=arguments[0];window.resolveSlow([window.testUsers[0]]);requestAnimationFrame(()=>requestAnimationFrame(done));''')
    assert '测试乙' in visible('.list-table .el-table__row')[0].text
    patch_api('userApi.list=window.fixtureListUsers;')
    click(button('重置'))
    ready()

    # Import runs through Element Plus upload callbacks, with no real request or disk fixture.
    patch_api('''
      userApi.importStudents=async form=>{window.directoryWrites.push({method:'import',name:form.get('file').name});return {created:1,updated:0,errors:[]}};
    ''')
    driver.execute_script('''
      const transfer=new DataTransfer();transfer.items.add(new File(['username,email,real_name'], 'students_fixture.csv', {type:'text/csv'}));
      const input=document.querySelector('input[type="file"]');input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));
    ''')
    wait.until(lambda _: driver.execute_script("return window.directoryWrites.some(w=>w.method==='import')"))
    ready()
    assert '导入完成' in ' '.join(item.text for item in visible('.el-message'))

    # Pagination and long text must remain usable at both desktop and mobile sizes.
    patch_api('''
      window.longName='课程名称与说明需要保持完整可读'.repeat(7);
      window.testCourses=Array.from({length:13},(_,index)=>({id:9400+index,name:index===0?window.longName:'分页课程 '+(index+1),description:'LongDescriptionWithoutSpaces'.repeat(12),teacher_id:9201,teacher_name:'测试教师',student_ids:[],student_count:0,created_at:'2026-10-14T08:12:34'}));
      window.testUsers=Array.from({length:25},(_,index)=>({id:9500+index,username:'long_username_without_spaces'.repeat(index===0?4:1)+index,real_name:index===0?'测试姓名很长'.repeat(12):'测试用户 '+(index+1),email:'very_long_email_address'.repeat(index===0?5:1)+'@example.test',class_name:'班级名称较长的布局检查'.repeat(index===0?5:1),role:'student',is_active:true,created_at:'2026-10-14T08:12:34'}));
    ''')
    navigate('/admin/users')
    assert len(visible('.list-table .el-table__row')) == 20
    capture('users-long-content')
    click(next(item for item in visible('.el-pager li') if item.text == '2'))
    ready()
    assert len(visible('.list-table .el-table__row')) == 5
    click(next(item for item in visible('.el-pager li') if item.text == '1'))
    viewport(320)
    capture('users-long-content-mobile')
    click(button('详情'))
    capture('user-long-details-mobile')
    click(button('关闭', visible('.el-dialog')[0]))
    viewport(1440)
    navigate('/courses')
    assert len(visible('.list-table .el-table__row')) == 10
    capture('courses-long-content')
    click(next(item for item in visible('.el-pager li') if item.text == '2'))
    ready()
    assert len(visible('.list-table .el-table__row')) == 3
    click(next(item for item in visible('.el-pager li') if item.text == '1'))
    viewport(320)
    capture('courses-long-content-mobile')
    click(visible('.course-name')[0])
    assert driver.execute_script('return window.longName') in visible('.detail-list')[0].text
    capture('course-long-details-mobile')
    click(button('关闭', visible('.el-dialog')[0]))
    viewport(1440)

    # Roster failure must never look like an empty, savable selection.
    patch_api('''
      window.rejectRoster=true;
      courseApi.get=async id=>{if(window.rejectRoster)throw Error('fixture roster failure');return window.fixtureGetCourse(id)};
    ''')
    click(button('选课学生'))
    ready()
    assert visible('.enrollment-dialog .el-alert--error')
    assert not button('保存选课').is_enabled()
    driver.execute_script('window.rejectRoster=false')
    click(button('重新加载', visible('.el-dialog')[0]))
    ready()
    assert not visible('.enrollment-dialog .el-alert--error')
    click(button('取消', visible('.el-dialog')[0]))

    # Course editing and deletion stay behind explicit actions; only fixtures are modified.
    patch_api('''
      userApi.list=async()=>[{id:9201,username:'teacher_test',real_name:'测试教师',role:'teacher',is_active:true},{id:9202,username:'teacher_disabled',real_name:'停用教师',role:'teacher',is_active:false}];
      courseApi.update=async(id,payload)=>{window.directoryWrites.push({method:'update',id,payload});Object.assign(window.testCourses.find(c=>c.id===id),payload)};
      courseApi.remove=async id=>{window.directoryWrites.push({method:'remove',id});window.testCourses=window.testCourses.filter(c=>c.id!==id)};
    ''')
    menu('编辑课程')
    wait.until(lambda _: visible('.course-dialog'))
    field('.course-dialog input', '更新后的测试课程')
    click(visible('.course-dialog .el-select')[0])
    wait.until(lambda _: visible('.el-select-dropdown__item'))
    assert any('is-disabled' in item.get_attribute('class') and '停用教师' in item.text for item in visible('.el-select-dropdown__item'))
    driver.switch_to.active_element.send_keys(Keys.ESCAPE)
    click(button('保存课程'))
    wait.until(lambda _: not visible('.course-dialog'))
    ready()
    assert visible('.course-name')[0].text == '更新后的测试课程'
    menu('删除课程')
    click(button('确认删除', visible('.el-message-box')[0]))
    ready()
    assert '12' in visible('.list-count-badge')[0].text

    # UI role switch is confined to this isolated tab; ownership stays enforced by the server.
    driver.execute_script('''document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('user').user={id:9201,username:'teacher_test',role:'teacher'};''')
    navigate('/courses')
    assert not visible('#course-teacher')
    click(button('新增课程'))
    assert not visible('.course-dialog .el-select')
    capture('teacher-course-form')
    click(button('取消', visible('.el-dialog')[0]))
    print('PASS: retry/error states, stale search response, mock import, pagination, long text, unavailable teacher, mock course edit/delete and teacher-specific controls.', flush=True)


try:
    viewport(1440)
    driver.get(base + '/login')
    logged_in = driver.execute_async_script('''
      const [password,done]=arguments;fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password})})
        .then(r=>r.json()).then(data=>{sessionStorage.setItem('token',data.access_token);sessionStorage.setItem('user',JSON.stringify(data.user));done(!!data.access_token)}).catch(()=>done(false));
    ''', os.environ.get('EDUEXAM_ADMIN_PASSWORD', 'admin123'))
    assert logged_in
    driver.get(base + '/courses')
    ready()
    guard_writes()
    real_checks()
    fixture_checks()
    edge_checks()
    errors = [entry['message'] for entry in driver.get_log('browser') if entry['level'] == 'SEVERE']
    assert not errors, '\n'.join(errors)
    print('PASS: all course/user UI checks completed; no live business-data mutations or browser errors.', flush=True)
except Exception:
    driver.save_screenshot(str(output / 'check-failure.png'))
    raise
finally:
    driver.quit()
