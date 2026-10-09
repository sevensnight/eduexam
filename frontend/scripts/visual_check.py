"""Browser smoke checks for the frontend. Uses installed Python Selenium + Chrome."""
import argparse
import json
from pathlib import Path
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

parser = argparse.ArgumentParser()
parser.add_argument('--before', action='store_true')
args = parser.parse_args()
output = Path(__file__).resolve().parents[1] / 'artifacts' / ('before' if args.before else 'after')
output.mkdir(parents=True, exist_ok=True)
options = webdriver.ChromeOptions()
options.add_argument('--headless=new')
options.add_argument('--window-size=1440,1000')
options.set_capability('goog:loggingPrefs', {'browser': 'ALL'})
driver = webdriver.Chrome(options=options)
wait = WebDriverWait(driver, 20)
base = 'http://localhost:5173'
errors = []

def ready():
    wait.until(lambda d: d.find_elements(By.CSS_SELECTOR, 'input,.el-main'))
    wait.until(lambda d: not d.find_elements(By.CSS_SELECTOR, '.el-loading-mask:not([style*="display: none"])'))
    wait.until(lambda d: not d.find_elements(By.CSS_SELECTOR, '.dashboard-skeleton'))
    driver.execute_async_script('const done=arguments[0]; document.fonts.ready.then(() => requestAnimationFrame(() => requestAnimationFrame(done)))')

def capture(name):
    if name == 'exams-mobile':
        assert driver.execute_script("return [...document.querySelectorAll('.el-table-fixed-column--right')].every(e=>getComputedStyle(e).position!=='sticky')"), 'Fixed actions must not cover mobile table'
    driver.save_screenshot(str(output / (name + '.png')))
    overflow = driver.execute_script('return document.documentElement.scrollWidth > window.innerWidth + 1')
    print(json.dumps({'page': name, 'horizontal_overflow': overflow}, ensure_ascii=False), flush=True)
    if not args.before:
        assert not overflow, name + ': viewport overflow'
    errors.extend(entry['message'] for entry in driver.get_log('browser') if entry['level'] == 'SEVERE')

def login(username, password):
    driver.execute_script('sessionStorage.clear()')
    driver.get(base + '/login')
    ready()
    driver.find_element(By.CSS_SELECTOR, 'input').send_keys(username)
    driver.find_element(By.CSS_SELECTOR, 'input[type=password]').send_keys(password)
    driver.find_element(By.CSS_SELECTOR, '.auth-submit').click()
    wait.until(lambda d: '/dashboard' in d.current_url)
    ready()

try:
    driver.get(base + '/login')
    ready()
    capture('login-desktop')
    driver.find_element(By.CSS_SELECTOR, 'input').send_keys('admin')
    driver.find_element(By.CSS_SELECTOR, 'input[type=password]').send_keys('admin123')
    driver.find_element(By.CSS_SELECTOR, '.el-button--primary').click()
    wait.until(lambda d: '/dashboard' in d.current_url)
    ready()
    capture('dashboard-desktop')
    if not args.before:
        for route in ['/questions', '/categories', '/courses', '/exams', '/stats', '/admin/users', '/profile', '/questions/create', '/exams/create']:
            driver.get(base + route)
            ready()
            capture(route.strip('/').replace('/', '-') + '-desktop')
        driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {'width':390, 'height':844, 'deviceScaleFactor':1, 'mobile':True})
        for route in ['/dashboard', '/questions', '/exams', '/categories', '/questions/create', '/exams/create']:
            driver.get(base + route)
            ready()
            capture(route.strip('/').replace('/', '-') + '-mobile')
        driver.execute_script('sessionStorage.clear()')
        driver.get(base + '/register')
        ready()
        capture('register-mobile')
        driver.get(base + '/login')
        ready()
        capture('login-mobile')
        driver.find_element(By.CSS_SELECTOR, '.auth-submit').click()
        wait.until(lambda d: len(d.find_elements(By.CSS_SELECTOR, '.el-form-item__error')) == 2)
        assert driver.current_url.endswith('/login')
        capture('login-validation-mobile')
        login('teacher1', 'teacher123')
        capture('teacher-dashboard-mobile')
        driver.find_element(By.CSS_SELECTOR, '.mobile-menu').click()
        wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR, '.el-drawer')))
        drawer = driver.find_element(By.CSS_SELECTOR, '.el-drawer')
        wait.until(lambda d: '题库管理' in drawer.text)
        assert '题库管理' in drawer.text and '用户管理' not in drawer.text
        capture('teacher-navigation-mobile')
        drawer.find_element(By.XPATH, ".//li[contains(., '题库管理')]").click()
        wait.until(lambda d: d.current_url.endswith('/questions'))
        ready()
        search = driver.find_element(By.CSS_SELECTOR, '.el-form--inline input')
        search.send_keys('no_matching_question_20260916')
        driver.find_element(By.XPATH, "//button[span[normalize-space()='查询']]").click()
        wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR, '.el-table__empty-block')))
        capture('questions-empty-mobile')
        login('student1', 'student123')
        capture('student-dashboard-mobile')
        for route in ['/my-records', '/wrong-book', '/exams', '/stats', '/profile']:
            driver.get(base + route)
            ready()
            capture('student-' + route.strip('/') + '-mobile')
        driver.get(base + '/admin/users')
        wait.until(lambda d: d.current_url.endswith('/dashboard'))
        ready()
        assert not errors, '\n'.join(errors)
        print('PASS: role navigation, mobile drawer, login validation, question search, no browser errors', flush=True)
finally:
    driver.quit()
