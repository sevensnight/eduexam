"""分类/标签工作台浏览器回归。真实数据只读；所有写操作在测试标签页中拦截或模拟。"""
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
output = Path(__file__).resolve().parents[1] / 'artifacts/taxonomy-redesign'
output.mkdir(parents=True, exist_ok=True)
options = webdriver.ChromeOptions()
options.add_argument('--headless=new')
options.add_argument('--window-size=1440,1080')
options.set_capability('goog:loggingPrefs', {'browser': 'ALL'})
if os.environ.get('EDUEXAM_BROWSER_BINARY'):
    options.binary_location = os.environ['EDUEXAM_BROWSER_BINARY']
service = Service(executable_path=os.environ.get('EDUEXAM_CHROMEDRIVER'), service_args=['--disable-build-check'] if os.environ.get('EDUEXAM_DRIVER_SKIP_VERSION_CHECK') == '1' else [])
driver = webdriver.Chrome(service=service, options=options)
driver.set_script_timeout(20)
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
    wait.until(lambda _: visible('.taxonomy-workspace'))
    wait.until(lambda _: not visible('.el-loading-mask,.question-skeleton'))
    settle()


def click(element):
    driver.execute_script('arguments[0].scrollIntoView({block:"center"})', element)
    element.click()
    settle()


def button(text, root=None):
    return next(item for item in (root or driver).find_elements(By.XPATH, './/button[normalize-space()="' + text + '"]') if item.is_displayed())


def viewport(width):
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {'width': width, 'height': 1080 if width > 767 else 900, 'deviceScaleFactor': 1, 'mobile': width < 768})
    settle()


def capture(name):
    driver.execute_script('window.scrollTo(0,0)')
    settle()
    result = driver.execute_script('''
      const rect=e=>e.getBoundingClientRect();
      return {width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth+1,
        sidebar:!!document.querySelector('.taxonomy-sidebar'),
        drawerTrigger:!!document.querySelector('.directory-opener'),
        classificationAligned:[...document.querySelectorAll('.question-classification')].every(el=>{
          const a=rect(el.children[0]),b=rect(el.children[1]);
          return a.right+8<=b.left && Math.abs((a.top+a.bottom-b.top-b.bottom)/2)<5;
        }),
        buttonOverflow:[...document.querySelectorAll('.taxonomy-toolbar .el-button')].some(el=>{
          const a=rect(el);return [...el.children].some(child=>{const b=rect(child);return b.left<a.left-1 || b.right>a.right+1});
        })};
    ''')
    assert not result['overflow'] and not result['buttonOverflow'], (name, result)
    assert result['classificationAligned'], (name, result)
    assert result['sidebar'] != result['drawerTrigger'], (name, result)
    driver.save_screenshot(str(output / (name + '.png')))
    print(json.dumps({'capture': name, **result}, ensure_ascii=False), flush=True)


def patch_api(code):
    result = driver.execute_async_script('''
      const [code,done]=arguments;
      const url=performance.getEntriesByType('resource').map(e=>e.name).find(url=>/\\/src\\/api\\/index\\.js(?:\\?|$)/.test(url));
      import(url || '/src/api/index.js').then(({categoryApi,tagApi,questionApi})=>{
        window.taxOriginal ||= {categoryList:categoryApi.list,tagList:tagApi.list,questionList:questionApi.list};
        new Function('categoryApi','tagApi','questionApi','original',code)(categoryApi,tagApi,questionApi,window.taxOriginal);
        done({ok:true});
      }).catch(error=>done({ok:false,message:error.message}));
    ''', code)
    assert result['ok'], result


def guard_writes():
    patch_api('''
      window.taxWrites=[];
      for(const [api,methods] of [[categoryApi,['create','update','remove','reorder']],[tagApi,['create','remove']]])
        for(const method of methods) api[method]=async(...args)=>{window.taxWrites.push({method,args});throw new Error('Unexpected write blocked by visual test')};
    ''')


def remount():
    driver.execute_async_script('''
      const done=arguments[0],router=document.querySelector('#app').__vue_app__.config.globalProperties.$router;
      router.push('/dashboard').then(()=>router.push('/categories')).then(()=>done(true));
    ''')
    ready()


def category(name):
    # Selenium's rendered text can be empty below a scroll container's viewport.
    return next(item for item in driver.find_elements(By.CSS_SELECTOR, '.directory-node .directory-name') if item.get_attribute('title') == name)


def choose(name):
    if visible('.directory-opener'):
        click(visible('.directory-opener')[0])
        wait.until(lambda _: visible('.taxonomy-drawer .directory-name'))
    click(category(name))
    wait.until(lambda _: visible('.selection-copy h2') and visible('.selection-copy h2')[0].text == name)
    ready()


def menu(label, trigger='.selection-more'):
    click(visible(trigger)[0])
    wait.until(lambda _: visible('.el-dropdown-menu__item'))
    click(next(item for item in visible('.el-dropdown-menu__item') if item.text == label))


def real_checks():
    choose('数据结构')
    assert len(visible('.taxonomy-question')) == 7
    assert '本级 7 题' in visible('.selection-meta')[0].text
    capture('categories-desktop')
    trigger = visible('.question-trigger')[0]
    trigger.send_keys(Keys.SPACE)
    wait.until(lambda _: visible('.question-details'))
    assert '正确答案' in visible('.question-details')[0].text
    capture('category-expanded-desktop')
    for width in [1280, 1100, 1024, 820, 768, 390, 320]:
        viewport(width)
        capture('categories-' + str(width))
    click(visible('.directory-opener')[0])
    wait.until(lambda _: visible('.taxonomy-drawer .directory-name'))
    capture('category-drawer-mobile')
    click(category('算法'))
    wait.until(lambda _: not visible('.taxonomy-drawer'))
    ready()
    assert visible('.selection-copy h2')[0].text == '算法'
    viewport(1440)

    # Native keyboard navigation of tabs and tree items.
    driver.find_element(By.ID, 'taxonomy-tab-category').send_keys(Keys.ARROW_RIGHT)
    ready()
    assert driver.find_element(By.ID, 'taxonomy-tab-tag').get_attribute('aria-selected') == 'true'
    click(visible('.selection-copy h2')[0])
    capture('tags-desktop')
    field = driver.find_element(By.CSS_SELECTOR, 'input[aria-label="搜索标签"]')
    field.send_keys('SQL')
    wait.until(lambda _: len(visible('.tag-select')) == 2)
    click(next(item for item in visible('.tag-select') if item.text == 'SQL'))
    ready()
    assert visible('.selection-copy h2')[0].text == 'SQL'
    capture('tags-filtered')
    menu('删除标签')
    wait.until(lambda _: visible('.el-message-box'))
    click(button('取消', visible('.el-message-box')[0]))
    assert driver.execute_script('return window.taxWrites.length') == 0
    click(button('新建标签'))
    wait.until(lambda _: visible('.el-dialog'))
    capture('create-tag-dialog')
    click(button('取消', visible('.el-dialog')[0]))
    driver.find_element(By.ID, 'taxonomy-tab-tag').send_keys(Keys.HOME)
    ready()
    choose('数据结构')
    menu('编辑分类')
    wait.until(lambda _: visible('.el-dialog'))
    assert driver.find_element(By.CSS_SELECTOR, 'input[aria-label="分类名称"]').get_attribute('value') == '数据结构'
    click(button('取消', visible('.el-dialog')[0]))
    menu('添加子分类')
    wait.until(lambda _: visible('.el-dialog'))
    assert '数据结构' in visible('.dialog-context')[0].text
    click(button('取消', visible('.el-dialog')[0]))
    click(button('收起'))
    wait.until(lambda _: not visible('.directory-node[data-category-id="2"]'))
    click(button('展开'))
    treeitem = category('模型评估').find_element(By.XPATH, './ancestor::div[@role="treeitem"][1]')
    driver.execute_script('arguments[0].scrollIntoView({block:"center"});arguments[0].focus()', treeitem)
    treeitem.send_keys(Keys.ENTER)
    ready()
    assert visible('.selection-copy h2')[0].text == '模型评估'
    assert len(visible('.taxonomy-question')) == 10
    click(next(item for item in visible('.el-pager li') if item.text == '2'))
    ready()
    assert len(visible('.taxonomy-question')) == 1
    search = driver.find_element(By.CSS_SELECTOR, 'input[aria-label="搜索关联题目"]')
    search.send_keys('不存在的题目__ui_check', Keys.ENTER)
    ready()
    assert '没有匹配的题目' in visible('.content-state')[0].text
    click(button('清除搜索'))
    ready()
    assert len(visible('.taxonomy-question')) == 10
    assert visible('.el-pager li.is-active')[0].text == '1'
    assert driver.execute_script('return window.taxWrites.length') == 0
    print('PASS: real category/tag navigation, counts, question expansion, responsive drawer, pagination, search, menus and cancellation.', flush=True)


def fixture_checks():
    patch_api('''
      window.taxFixture={categories:[{id:9001,name:'测试目录甲',creator_id:null,question_count:0,children:[]},{id:9002,name:'测试目录乙',creator_id:null,question_count:0,children:[]}],tags:[{id:9101,name:'测试标签',color:'#0066cc',creator_id:null}]};
      const clone=data=>JSON.parse(JSON.stringify(data));
      categoryApi.list=async()=>clone(window.taxFixture.categories);
      tagApi.list=async()=>clone(window.taxFixture.tags);
      questionApi.list=async params=>({items:[],total:0});
      categoryApi.create=async data=>{window.taxWrites.push({method:'category-create',data});const item={id:9003,...data,question_count:0,children:[]};window.taxFixture.categories.push(item);return clone(item)};
      categoryApi.update=async(id,data)=>{window.taxWrites.push({method:'category-update',id,data});const item=window.taxFixture.categories.find(i=>i.id===id);Object.assign(item,data);return clone(item)};
      categoryApi.remove=async id=>{window.taxWrites.push({method:'category-delete',id});window.taxFixture.categories=window.taxFixture.categories.filter(i=>i.id!==id)};
      tagApi.create=async(name,color)=>{window.taxWrites.push({method:'tag-create',name,color});const item={id:9102,name,color};window.taxFixture.tags.push(item);return clone(item)};
      tagApi.remove=async id=>{window.taxWrites.push({method:'tag-delete',id});window.taxFixture.tags=window.taxFixture.tags.filter(i=>i.id!==id)};
      categoryApi.reorder=async data=>{window.taxWrites.push({method:'reorder',data});throw new Error('Simulated rejected order')};
    ''')
    remount()
    click(button('新建分类'))
    wait.until(lambda _: visible('.el-dialog'))
    driver.find_element(By.CSS_SELECTOR, 'input[aria-label="分类名称"]').send_keys('  新分类  ')
    click(button('保存分类'))
    wait.until(lambda _: not visible('.el-dialog'))
    ready()
    assert visible('.selection-copy h2')[0].text == '新分类'
    assert driver.execute_script('return window.taxWrites.at(-1).data.name') == '新分类'
    menu('编辑分类')
    wait.until(lambda _: visible('.el-dialog'))
    field = driver.find_element(By.CSS_SELECTOR, 'input[aria-label="分类名称"]')
    field.send_keys(Keys.CONTROL, 'a', Keys.NULL, '重命名分类')
    click(button('保存分类'))
    wait.until(lambda _: not visible('.el-dialog'))
    ready()
    assert visible('.selection-copy h2')[0].text == '重命名分类'
    menu('删除分类')
    wait.until(lambda _: visible('.el-message-box'))
    click(button('确认删除', visible('.el-message-box')[0]))
    wait.until(lambda _: driver.execute_script('return window.taxWrites.at(-1).method') == 'category-delete')
    ready()
    assert visible('.selection-copy h2')[0].text == '测试目录甲'

    # Use HTML5 drag events against the actual Element Plus tree. The write is mocked and rejected.
    driver.execute_script('''
      const source=document.querySelector('[data-category-id="9001"]').closest('[role="treeitem"]');
      const target=document.querySelector('[data-category-id="9002"]').closest('[role="treeitem"]');
      const box=target.querySelector('.el-tree-node__content').getBoundingClientRect(),dataTransfer=new DataTransfer();
      source.dispatchEvent(new DragEvent('dragstart',{bubbles:true,dataTransfer}));
      target.dispatchEvent(new DragEvent('dragover',{bubbles:true,cancelable:true,dataTransfer,clientX:box.x+box.width/2,clientY:box.y+box.height/2}));
      source.dispatchEvent(new DragEvent('dragend',{bubbles:true,dataTransfer}));
    ''')
    wait.until(lambda _: driver.execute_script('return window.taxWrites.some(item=>item.method==="reorder")'))
    ready()
    assert len(visible('.el-tree > .el-tree-node')) == 2
    assert driver.execute_script('return window.taxWrites.find(item=>item.method==="reorder").data.find(item=>item.id===9001).parent_id') == 9002

    click(driver.find_element(By.ID, 'taxonomy-tab-tag'))
    ready()
    click(button('新建标签'))
    wait.until(lambda _: visible('.el-dialog'))
    driver.find_element(By.CSS_SELECTOR, 'input[aria-label="标签名称"]').send_keys('  新标签  ')
    click(button('创建标签'))
    wait.until(lambda _: not visible('.el-dialog'))
    ready()
    assert visible('.selection-copy h2')[0].text == '新标签'
    menu('删除标签')
    wait.until(lambda _: visible('.el-message-box'))
    click(button('确认删除', visible('.el-message-box')[0]))
    wait.until(lambda _: driver.execute_script('return window.taxWrites.at(-1).method') == 'tag-delete')
    ready()
    assert visible('.selection-copy h2')[0].text == '测试标签'
    print('PASS: mocked create/edit/delete, trimmed names, preserved parent selection and rejected drag rollback.', flush=True)

    patch_api('''
      categoryApi.list=original.categoryList;tagApi.list=original.tagList;
      window.taxRaceDone=0;
      questionApi.list=async params=>{
        const keyword=params.keyword || '';
        if(keyword==='fail') throw new Error('Simulated question failure');
        if(keyword==='slow' || keyword==='fast') {
          await new Promise(resolve=>setTimeout(resolve,keyword==='slow'?600:20));window.taxRaceDone++;
          return {items:[{id:9901,title:keyword,question_type:'truefalse',difficulty:'medium',answer:'True',tags:[]}],total:1};
        }
        return original.questionList(params);
      };
    ''')
    remount()
    search = driver.find_element(By.CSS_SELECTOR, 'input[aria-label="搜索关联题目"]')
    search.send_keys('fail', Keys.ENTER)
    ready()
    assert '题目加载失败' in visible('.content-state')[0].text
    patch_api('questionApi.list=original.questionList;')
    click(button('重新加载', visible('.taxonomy-content')[0]))
    ready()
    assert not visible('.taxonomy-results [role="alert"]')
    patch_api('''
      questionApi.list=async params=>{
        const keyword=params.keyword || '';
        await new Promise(resolve=>setTimeout(resolve,keyword==='slow'?600:20));window.taxRaceDone++;
        return {items:[{id:9901,title:keyword,question_type:'truefalse',difficulty:'medium',answer:'True',tags:[]}],total:1};
      };
    ''')
    search.send_keys(Keys.CONTROL, 'a', Keys.NULL, 'slow', Keys.ENTER)
    search.send_keys(Keys.CONTROL, 'a', Keys.NULL, 'fast', Keys.ENTER)
    wait.until(lambda _: driver.execute_script('return window.taxRaceDone>=2'))
    ready()
    assert visible('.question-title')[0].text == 'fast'
    print('PASS: request failure/retry and out-of-order response protection.', flush=True)


def edge_checks():
    patch_api('''
      window.taxFailDirectory=true;
      categoryApi.list=async()=>{if(window.taxFailDirectory) throw new Error('Simulated directory failure');return original.categoryList()};
      tagApi.list=original.tagList;questionApi.list=original.questionList;
    ''')
    remount()
    assert '分类加载失败' in visible('.initial-state')[0].text
    patch_api('window.taxFailDirectory=false;')
    click(button('重新加载', visible('.initial-state')[0]))
    ready()
    field = driver.find_element(By.CSS_SELECTOR, 'input[aria-label="搜索分类"]')
    field.send_keys('模型评估')
    wait.until(lambda _: category('模型评估').is_displayed())
    choose('模型评估')
    assert '人工智能' in visible('.selection-path')[0].text

    patch_api('''
      const name='非常长的分类名称与技术术语'.repeat(8);
      categoryApi.list=async()=>[{id:9301,name,description:'用于验证长文本自动换行和目录省略显示。'.repeat(4),question_count:1,children:[]}];
      tagApi.list=async()=>[];
      questionApi.list=async()=>({items:[{id:9302,title:'LongUnbrokenQuestionTitle'.repeat(25),question_type:'truefalse',difficulty:'medium',answer:'True',explanation:'很长的解析内容。'.repeat(40),category_name:name,tags:[{id:9303,name:'超长标签'.repeat(15)}]}],total:1});
    ''')
    remount()
    click(visible('.question-trigger')[0])
    capture('long-content-desktop')
    viewport(320)
    capture('long-content-mobile')
    click(driver.find_element(By.ID, 'taxonomy-tab-tag'))
    ready()
    assert '从一个标签开始' in visible('.initial-state')[0].text
    capture('tags-empty-mobile')
    viewport(1440)

    # Permission checks are isolated to this test tab; no role or database is changed.
    driver.execute_script('''
      const app=document.querySelector('#app').__vue_app__;
      app.config.globalProperties.$pinia._s.get('user').user={id:8001,role:'teacher',username:'visual-teacher'};
    ''')
    patch_api('''
      categoryApi.list=async()=>[
        {id:9401,name:'共享分类',creator_id:null,question_count:0,children:[]},
        {id:9402,name:'本人分类',creator_id:8001,question_count:0,children:[]},
        {id:9403,name:'其他教师分类',creator_id:8002,question_count:0,children:[]}];
      tagApi.list=async()=>[{id:9501,name:'共享标签',creator_id:null,color:'#0066cc'}];
      questionApi.list=async()=>({items:[],total:0});
    ''')
    remount()
    click(visible('.selection-more')[0])
    wait.until(lambda _: visible('.el-dropdown-menu__item'))
    assert [item.text for item in visible('.el-dropdown-menu__item')] == ['添加子分类']
    driver.switch_to.active_element.send_keys(Keys.ESCAPE)
    choose('本人分类')
    click(visible('.selection-more')[0])
    wait.until(lambda _: visible('.el-dropdown-menu__item'))
    assert [item.text for item in visible('.el-dropdown-menu__item')] == ['编辑分类', '添加子分类', '删除分类']
    driver.switch_to.active_element.send_keys(Keys.ESCAPE)
    choose('其他教师分类')
    assert not visible('.selection-more')
    click(driver.find_element(By.ID, 'taxonomy-tab-tag'))
    ready()
    assert not visible('.selection-more,.directory-more')
    print('PASS: directory retry, nested category search, long content, empty tags and teacher ownership controls.', flush=True)


try:
    viewport(1440)
    driver.get(base + '/login')
    result = driver.execute_async_script('''
      const [password,done]=arguments;fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password})})
        .then(r=>r.json()).then(data=>{sessionStorage.setItem('token',data.access_token);sessionStorage.setItem('user',JSON.stringify(data.user));done(!!data.access_token)}).catch(()=>done(false));
    ''', os.environ.get('EDUEXAM_ADMIN_PASSWORD', 'admin123'))
    assert result
    driver.get(base + '/categories')
    ready()
    guard_writes()
    real_checks()
    fixture_checks()
    edge_checks()
    errors = [entry['message'] for entry in driver.get_log('browser') if entry['level'] == 'SEVERE']
    assert not errors, '\n'.join(errors)
    print('PASS: all taxonomy UI checks completed; no live taxonomy mutations or browser errors.', flush=True)
except Exception:
    driver.save_screenshot(str(output / 'check-failure.png'))
    raise
finally:
    driver.quit()
