"""Read-only browser checks for the statistics page (Selenium + Chrome)."""
import argparse
import json
import os
import base64
import math
import re
from pathlib import Path

from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


parser = argparse.ArgumentParser()
parser.add_argument('--before', action='store_true')
parser.add_argument('--states', action='store_true', help='Also simulate empty, failed, and delayed API responses in this browser only.')
parser.add_argument('--only-states', action='store_true', help='Run only the isolated API-state checks.')
parser.add_argument('--only-hover', action='store_true', help='Check actual chart hover, SVG paint order, and donut tooltip stacking at desktop/mobile widths.')
args = parser.parse_args()
base = os.environ.get('EDUEXAM_UI_URL', 'http://localhost:5173')
output = Path(__file__).resolve().parents[1] / 'artifacts' / 'stats-redesign'
output.mkdir(parents=True, exist_ok=True)
options = webdriver.ChromeOptions()
options.add_argument('--headless=new')
options.add_argument('--window-size=1440,1080')
options.set_capability('goog:loggingPrefs', {'browser': 'ALL'})
if os.environ.get('EDUEXAM_BROWSER_BINARY'):
    options.binary_location = os.environ['EDUEXAM_BROWSER_BINARY']
service_args = ['--disable-build-check'] if os.environ.get('EDUEXAM_DRIVER_SKIP_VERSION_CHECK') == '1' else []
service = Service(executable_path=os.environ.get('EDUEXAM_CHROMEDRIVER'), service_args=service_args)
driver = webdriver.Chrome(service=service, options=options)
driver.set_page_load_timeout(30)
driver.set_script_timeout(20)
driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {'width':1440, 'height':1080, 'deviceScaleFactor':1, 'mobile':False})
wait = WebDriverWait(driver, 25)
errors = []


def ready():
    wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, '.stats-view')))
    wait.until(lambda d: not d.find_elements(By.CSS_SELECTOR, '.stats-loading'))
    wait.until(lambda d: not d.find_elements(By.CSS_SELECTOR, '.toolbar-actions .el-button.is-loading'))
    wait.until(lambda d: not any(e.is_displayed() for e in d.find_elements(By.CSS_SELECTOR, '.el-loading-mask')))
    driver.execute_async_script('const done=arguments[0]; document.fonts.ready.then(() => requestAnimationFrame(() => requestAnimationFrame(done)))')


def capture(name):
    overflow = driver.execute_script('return document.documentElement.scrollWidth > innerWidth + 1')
    driver.save_screenshot(str(output / (name + '.png')))
    print(json.dumps({'page': name, 'horizontal_overflow': overflow}, ensure_ascii=False), flush=True)
    locked = '这场考试的统计尚未开放' in driver.find_element(By.CSS_SELECTOR, '.stats-view').text
    for entry in driver.get_log('browser'):
        if entry['level'] != 'SEVERE':
            continue
        # A student's unreleased exam is an expected permission response, not a UI error.
        expected_denial = locked and '403 (Forbidden)' in entry['message'] and re.search(r'/api/stats/exam/\d+(?:/category-distribution)?\s', entry['message'])
        if not expected_denial:
            errors.append(entry['message'])
    if not args.before:
        assert not overflow, name + ': viewport overflow'


def capture_full(name):
    dimensions = driver.execute_cdp_cmd('Page.getLayoutMetrics', {})['cssContentSize']
    shot = driver.execute_cdp_cmd('Page.captureScreenshot', {
        'format': 'png', 'captureBeyondViewport': True,
        'clip': {'x':0, 'y':0, 'width':dimensions['width'], 'height':dimensions['height'], 'scale':1},
    })
    (output / (name + '.png')).write_bytes(base64.b64decode(shot['data']))


def login(username, password):
    driver.get(base + '/login')
    wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, '.auth-page, .layout')))
    # Authenticate real demo accounts without depending on password-manager autofill.
    result = driver.execute_async_script('''
        const [username, password, done] = arguments;
        fetch('/api/auth/login', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({username,password})})
          .then(async response => {
            const data = await response.json();
            if (!response.ok) return done({ok:false, message:data.detail || data.message});
            sessionStorage.setItem('token', data.access_token);
            sessionStorage.setItem('user', JSON.stringify(data.user));
            localStorage.removeItem('token'); localStorage.removeItem('user');
            done({ok:true, role:data.user.role});
          }).catch(error => done({ok:false, message:error.message}));
    ''', username, password)
    assert result.get('ok'), result.get('message', 'Login failed')
    driver.get(base + '/stats')
    ready()


def patch_api(source):
    """Dev-only fixtures; these replace functions in this tab, never server data."""
    result = driver.execute_async_script(r'''
        const [source, done] = arguments;
        const apiUrl = performance.getEntriesByType('resource').map(entry => entry.name).find(url => /\/src\/api\/index\.js(?:\?|$)/.test(url));
        import(apiUrl || '/src/api/index.js').then(({statsApi, examApi}) => {
          window.__statsUiOriginal ||= {stats:{...statsApi}, exams:{...examApi}};
          new Function('statsApi', 'examApi', 'original', source)(statsApi, examApi, window.__statsUiOriginal);
          done(true);
        }).catch(error => done({error:error.message}));
    ''', source)
    assert result is True, result


def refresh():
    driver.find_element(By.CSS_SELECTOR, '.toolbar-actions .el-button').click()


def move_pointer(x=0, y=0):
    # A real movement generates intermediate events, rather than teleporting the
    # pointer onto the previous segment's tooltip in a single CDP event.
    actions = ActionChains(driver, duration=250)
    actions.w3c_actions.pointer_action.move_to_location(round(x), round(y))
    actions.pause(0.4).perform()


def chart_data(selector):
    """Read the live chart's public API; do not change data or dispatch fake hover."""
    result = driver.execute_async_script(r'''
        const [selector, done] = arguments;
        const moduleUrl = performance.getEntriesByType('resource').map(entry => entry.name)
          .find(url => /\/echarts_core\.js(?:\?|$)/.test(url));
        if (!moduleUrl) return done({error:'ECharts development module not found'});
        import(moduleUrl).then(({getInstanceByDom}) => {
          const option = getInstanceByDom(document.querySelector(selector)).getOption();
          done(option.series[0].data);
        }).catch(error => done({error:error.message}));
    ''', selector)
    assert isinstance(result, list), result
    return result


def scroll_to_chart(selector):
    move_pointer()
    driver.execute_script('document.querySelector(arguments[0]).scrollIntoView({block:"center"})', selector)
    ready()


def check_bar_hover(selector, name):
    scroll_to_chart(selector)
    bars = driver.execute_script('''
        return [...document.querySelector(arguments[0]).querySelectorAll('svg path')]
          .filter(el => ['#0066cc','#8db3df','#a2bfdf'].includes(el.getAttribute('fill')))
          .map(el => { const r=el.getBoundingClientRect(); return {path:el.getAttribute('d'),x:r.x,y:r.y,width:r.width,height:r.height}; });
    ''', selector)
    assert len(bars) == len(chart_data(selector)), name + ': missing bars before hover'
    assert bars, name + ': no data to exercise'
    for index, bar in enumerate(bars):
        if bar['width'] <= 0 or bar['height'] <= 0:
            continue  # A genuine zero-count bucket has no hoverable area.
        move_pointer(bar['x'] + bar['width'] / 2, bar['y'] + bar['height'] / 2)
        state = driver.execute_script('''
            const [selector, path] = arguments;
            const chart = document.querySelector(selector);
            const shapes = [...chart.querySelectorAll('svg path, svg text')];
            const bar = shapes.find(el => el.tagName === 'path' && el.getAttribute('d') === path);
            const shadow = shapes.find(el => el.tagName === 'path' &&
              (el.getAttribute('fill') === '#f1f5fa' || el.getAttribute('fill-opacity') === '0.05'));
            const labels = shapes.filter(el => el.tagName === 'text' && el.getAttribute('fill') === '#454549' && /^\\d[\\d,]*$/.test(el.textContent));
            const popup = chart.querySelector('.analytics-tooltip');
            const tooltip = popup && getComputedStyle(popup).visibility !== 'hidden' ? popup.innerText : '';
            return {
              tooltip, fill:bar?.getAttribute('fill'), shadowFound:!!shadow,
              barAboveShadow:!!bar && !!shadow && shapes.indexOf(bar) > shapes.indexOf(shadow),
              labelsAboveShadow:!!shadow && labels.length > 0 && labels.every(el => shapes.indexOf(el) > shapes.indexOf(shadow)),
              labelCount:labels.length,
            };
        ''', selector, bar['path'])
        assert '占比' in state['tooltip'], (name, index, 'tooltip not triggered', state)
        assert state['fill'] == '#0066cc', (name, index, 'hovered bar lost its fill', state)
        assert state['barAboveShadow'], (name, index, 'highlight covers the bar', state)
        assert state['labelsAboveShadow'], (name, index, 'highlight covers value labels', state)
        assert state['labelCount'] == len(bars), (name, index, 'missing value labels', state)
        if index == 0:
            capture(name)
    move_pointer()
    assert not any(el.is_displayed() for el in driver.find_elements(By.CSS_SELECTOR, selector + ' .analytics-tooltip')), name + ': stale tooltip after leaving'
    print('PASS: ' + name + ' — all bars, value labels, hover highlighting, and mouse leave.', flush=True)


def check_donut_hover(name):
    selector = '.donut-wrap .analytics-chart'
    scroll_to_chart(selector)
    data = chart_data(selector)
    total = sum(item['value'] for item in data)
    label_before = driver.find_element(By.CSS_SELECTOR, '.donut-label strong').text
    # Mouse coordinates are viewport-relative, unlike Selenium's element.rect.
    dimensions = driver.execute_script('const r=document.querySelector(arguments[0]).getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height}', selector)
    radius = min(dimensions['width'], dimensions['height']) * 0.405
    consumed = 0
    overlaps_checked = 0
    for item in data:
        angle = -math.pi / 2 + 2 * math.pi * (consumed + item['value'] / 2) / total
        consumed += item['value']
        move_pointer(dimensions['x'] + dimensions['width'] / 2 + radius * math.cos(angle),
                     dimensions['y'] + dimensions['height'] / 2 + radius * math.sin(angle))
        state = driver.execute_script('''
            const chart = document.querySelector('.donut-wrap .analytics-chart');
            const total = document.querySelector('.donut-label strong');
            const tooltip = chart.querySelector('.analytics-tooltip');
            const texts = tooltip?.innerText || '';
            if (!tooltip || getComputedStyle(tooltip).visibility === 'hidden') return {tooltipFound:false, texts};
            const t=tooltip.getBoundingClientRect(), n=total.getBoundingClientRect();
            const left=Math.max(t.left+3,n.left), right=Math.min(t.right-3,n.right);
            const top=Math.max(t.top+3,n.top), bottom=Math.min(t.bottom-3,n.bottom);
            const overlaps=left < right && top < bottom;
            let tooltipAboveTotal = null;
            if (overlaps) {
              // Restore pointer-events immediately after a paint-order hit-test.
              // Normal users still interact through the non-interactive center label.
              const previous=total.style.pointerEvents;
              const tooltipPrevious=tooltip.style.pointerEvents;
              try {
                total.style.pointerEvents='auto';
                tooltip.style.pointerEvents='auto';
                const layers=document.elementsFromPoint((left+right)/2,(top+bottom)/2);
                const tooltipIndex=layers.indexOf(tooltip);
                tooltipAboveTotal=tooltipIndex >= 0 && layers.indexOf(total) > tooltipIndex;
              } finally {
                total.style.pointerEvents=previous;
                tooltip.style.pointerEvents=tooltipPrevious;
              }
            }
            const c=chart.getBoundingClientRect();
            return {tooltipFound:true, texts, overlaps, tooltipAboveTotal, pointerEvents:getComputedStyle(tooltip).pointerEvents,
              confined:t.left >= c.left-2 && t.right <= c.right+2 && t.top >= c.top-2 && t.bottom <= c.bottom+2};
        ''')
        assert state['tooltipFound'] and item['name'] in state['texts'], (name, item, state)
        assert state['pointerEvents'] == 'none', (name, 'tooltip intercepts the mouse', state)
        assert state['confined'], (name, 'tooltip clipped', state)
        if state['overlaps']:
            assert state['tooltipAboveTotal'], (name, 'center number paints over the tooltip', state)
            if overlaps_checked == 0:
                capture(name)
            overlaps_checked += 1
    assert overlaps_checked, name + ': did not exercise the overlapping-tooltip case'
    move_pointer()
    assert not any(el.is_displayed() for el in driver.find_elements(By.CSS_SELECTOR, selector + ' .analytics-tooltip')), name + ': stale tooltip after leaving'
    assert driver.find_element(By.CSS_SELECTOR, '.donut-label strong').text == label_before
    assert driver.find_element(By.CSS_SELECTOR, '.donut-label strong').is_displayed()
    print('PASS: ' + name + ' — all segments, tooltip paint order/confinement, and center total after mouse leave.', flush=True)


def check_hover():
    login('admin', os.environ.get('EDUEXAM_ADMIN_PASSWORD', 'admin123'))
    for width, height, viewport in [(1440,1080,'desktop'), (390,844,'narrow')]:
        driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {'width':width,'height':height,'deviceScaleFactor':1,'mobile':False})
        driver.execute_script('window.scrollTo({top:0,behavior:"instant"})')
        ready()
        driver.find_element(By.CSS_SELECTOR, '[data-view="bank"]').click()
        ready()
        check_bar_hover('.ranking-chart', 'hover-bank-ranking-' + viewport)
        check_donut_hover('hover-bank-donut-' + viewport)
        driver.execute_script('window.scrollTo({top:0,behavior:"instant"})')
        ready()
        driver.find_element(By.CSS_SELECTOR, '[data-view="exam"]').click()
        ready()
        check_bar_hover('.score-panel .analytics-chart', 'hover-exam-scores-' + viewport)
        check_bar_hover('.ranking-chart', 'hover-exam-ranking-' + viewport)
        check_donut_hover('hover-exam-donut-' + viewport)


def check_states():
    login('admin', os.environ.get('EDUEXAM_ADMIN_PASSWORD', 'admin123'))
    patch_api('statsApi.categories = async () => [];')
    refresh()
    ready()
    assert '暂无分类数据' in driver.find_element(By.CSS_SELECTOR, '.stats-view').text
    assert not driver.find_elements(By.CSS_SELECTOR, '.analytics-chart')
    capture('state-empty-categories')

    patch_api('statsApi.categories = async () => { throw new Error("Simulated offline response"); };')
    refresh()
    ready()
    assert driver.find_elements(By.CSS_SELECTOR, '.error-state')
    assert not driver.find_elements(By.CSS_SELECTOR, '.overview-metrics')
    capture('state-error-categories')

    patch_api('statsApi.categories = () => new Promise(resolve => setTimeout(() => resolve(original.stats.categories()), 900));')
    driver.find_element(By.CSS_SELECTOR, '.error-state .el-button').click()
    wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR, '.stats-loading')))
    capture('state-loading')
    ready()
    assert driver.find_elements(By.CSS_SELECTOR, '.overview-metrics')
    patch_api('statsApi.categories = original.stats.categories;')

    patch_api('''
      const exam = (id, title) => ({id, title, status:'closed', score_public:true, total_score:100});
      examApi.list = async ({page}) => ({items:page === 1 ? [exam(90001,'界面验证考试 A'), exam(90002,'界面验证考试 B')] : [exam(90003,'界面验证考试 C')], total:51});
      const summary = (id) => ({
        total_participants:id === 90003 ? 0 : 2,
        avg_score:id === 90001 ? 40 : id === 90002 ? 86 : 0,
        max_score:id === 90001 ? 45 : id === 90002 ? 90 : 0,
        pass_rate:id === 90002 ? 1 : 0, good_rate:id === 90002 ? 1 : 0, excellent_rate:id === 90002 ? 0.5 : 0,
        score_distribution:['<60%','60%-69%','70%-79%','80%-89%','≥90%'].map((range,index) => ({range,count:id === 90002 ? (index > 2 ? 1 : 0) : id === 90001 && index === 0 ? 2 : 0})),
      });
      statsApi.exam = id => new Promise(resolve => setTimeout(() => resolve(summary(id)), id === 90001 ? 900 : 40));
      statsApi.examCategoryDist = async id => id === 90003 ? [] : [{category_name:'界面验证分类',question_count:id === 90001 ? 2 : 4}];
      statsApi.insights = async () => ({weak_points:[{name:'界面验证知识点',point_type:'category',avg_score_rate:0.45}]});
    ''')
    refresh()
    ready()
    driver.find_element(By.CSS_SELECTOR, '[data-view="exam"]').click()
    driver.find_element(By.CSS_SELECTOR, '.exam-selection .el-select').click()
    option = wait.until(EC.element_to_be_clickable((By.XPATH, '//li[contains(@class,"el-select-dropdown__item")][contains(.,"界面验证考试 B")]')))
    option.click()
    ready()
    driver.execute_async_script('setTimeout(arguments[0], 1000)')
    assert '86' in driver.find_elements(By.CSS_SELECTOR, '.exam-metrics .metric-number')[1].text
    assert '4' in driver.find_element(By.CSS_SELECTOR, '.donut-label strong').text
    capture('state-latest-exam-wins')

    driver.find_element(By.CSS_SELECTOR, '.exam-pages .btn-next').click()
    ready()
    assert '等待第一份批改结果' in driver.find_element(By.CSS_SELECTOR, '.score-panel').text
    assert driver.find_elements(By.CSS_SELECTOR, '.exam-metrics .metric-number')[2].text == '-'
    capture('state-ungraded-exam')

    patch_api('statsApi.exam = async () => { throw new Error("Simulated exam service failure"); };')
    refresh()
    ready()
    assert driver.find_elements(By.CSS_SELECTOR, '.error-state')
    assert not driver.find_elements(By.CSS_SELECTOR, '.exam-metrics')
    capture('state-error-exam')

    driver.find_element(By.CSS_SELECTOR, '[data-view="bank"]').click()
    patch_api('examApi.list = async () => ({items:[],total:0});')
    refresh()
    ready()
    driver.find_element(By.CSS_SELECTOR, '[data-view="exam"]').click()
    assert '暂无可查看的考试' in driver.find_element(By.CSS_SELECTOR, '.stats-state').text
    capture('state-no-exams')
    print('PASS: isolated empty/error/loading states, retry, exam pagination, ungraded results, and out-of-order responses.', flush=True)


if args.only_states or args.only_hover:
    try:
        if args.only_hover:
            check_hover()
        else:
            check_states()
        assert not errors, '\n'.join(errors)
    except Exception:
        driver.save_screenshot(str(output / 'check-failure.png'))
        raise
    finally:
        driver.quit()
    raise SystemExit(0)


try:
    login('admin', os.environ.get('EDUEXAM_ADMIN_PASSWORD', 'admin123'))
    capture('before-desktop' if args.before else 'overview-desktop')
    if not args.before:
        capture_full('overview-desktop-full')
        wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, '.analytics-chart svg')))
        assert len(driver.find_elements(By.CSS_SELECTOR, '.overview-metrics .metric-item')) == 4
        distribution = driver.find_element(By.CSS_SELECTOR, '.category-distribution')
        distribution.find_element(By.XPATH, './/button[normalize-space()="明细"]').click()
        search = distribution.find_element(By.CSS_SELECTOR, 'input')
        search.send_keys('no_matching_category_ui_check')
        wait.until(lambda d: '没有匹配的分类' in distribution.text)
        capture('category-search-empty')
        search.clear()
        search.send_keys(' ')
        search.clear()
        distribution.find_element(By.XPATH, './/button[normalize-space()="图表"]').click()
        wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, '.ranking-chart svg')))
        driver.find_element(By.CSS_SELECTOR, '[data-view="exam"]').click()
        ready()
        wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, '.exam-metrics, .stats-state')))
        capture('exam-desktop')
        capture_full('exam-desktop-full')
        driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {'width':390, 'height':844, 'deviceScaleFactor':1, 'mobile':True})
        driver.execute_script('window.scrollTo(0, 0)')
        ready()
        capture('exam-mobile')
        driver.find_element(By.CSS_SELECTOR, '[data-view="bank"]').click()
        ready()
        capture('overview-mobile')
        capture_full('overview-mobile-full')
        login('teacher1', os.environ.get('EDUEXAM_TEACHER_PASSWORD', 'teacher123'))
        assert '我的题库' in driver.find_element(By.CSS_SELECTOR, '.stats-view').text
        capture('teacher-mobile')
        login('student1', os.environ.get('EDUEXAM_STUDENT_PASSWORD', 'student123'))
        assert not driver.find_elements(By.CSS_SELECTOR, '[data-view="bank"]')
        assert not driver.find_elements(By.CSS_SELECTOR, '.knowledge-panel')
        capture('student-mobile')
        capture_full('student-mobile-full')
        if args.states:
            check_states()
        check_hover()
        assert not errors, '\n'.join(errors)
        print('PASS: real-account statistics, chart/table switch, category search, mobile layout, role separation; no unexpected browser errors.', flush=True)
except Exception:
    driver.save_screenshot(str(output / 'check-failure.png'))
    print(json.dumps({'url': driver.current_url, 'messages': [e.text for e in driver.find_elements(By.CSS_SELECTOR, '.el-message, .el-form-item__error')]}, ensure_ascii=False), flush=True)
    raise
finally:
    driver.quit()
