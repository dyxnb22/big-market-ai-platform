import { chromium } from 'playwright';

const BASE = process.env.WEB_BASE || 'http://127.0.0.1:5173';
const issues = [];

function note(msg) {
  issues.push(msg);
  console.log('ISSUE:', msg);
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  page.on('dialog', async (d) => { await d.accept(); });

  console.log('1. Landing page');
  await page.goto(`${BASE}/index.html`);
  await page.waitForTimeout(800);
  if (!(await page.locator('#landingView').isVisible())) note('landingView not visible for guest');
  const landingOverflow = await page.evaluate(() => getComputedStyle(document.body).overflowY);
  if (landingOverflow === 'hidden') note('body overflow still hidden on landing');

  console.log('2. Login');
  await page.goto(`${BASE}/login.html`);
  await page.fill('#userIdInput', 'xiaofuge');
  await page.fill('#passwordInput', 'demo');
  await page.click('#loginBtn');
  await page.waitForURL(/index\.html/, { timeout: 15000 });

  console.log('3. App shell');
  await page.waitForSelector('#appView', { state: 'visible', timeout: 20000 });
  await page.waitForTimeout(2000);
  const credit = await page.locator('#creditDisplay').textContent();
  if (!credit || credit.includes('-')) note('credit not loaded: ' + credit);
  const activity = await page.locator('#activityLabel').textContent();
  if (!activity || activity === '加载中...') note('activity label stuck: ' + activity);

  console.log('4. Lottery drawer');
  await page.click('#openLotteryBtn');
  await page.waitForSelector('#lotteryDrawer.open');
  await page.waitForFunction(() => {
    const t = document.querySelector('#surplusMetric')?.textContent || '';
    return t && t !== '加载中' && t !== '-';
  }, { timeout: 20000 });
  const surplus = await page.locator('#surplusMetric').textContent();
  if (surplus === '加载中' || surplus === '-') note('surplus metric: ' + surplus);
  await page.click('#closeDrawerBtn');
  await page.waitForTimeout(400);

  console.log('5. User center + history');
  await page.click('#userCenterBtn');
  await page.waitForSelector('#userCenterDrawer.open');
  const hist = await page.locator('#drawHistoryList').textContent();
  if (!hist) note('draw history empty element missing');
  await page.click('#closeUserCenterBtn');
  await page.waitForTimeout(400);

  console.log('6. Chat suggestion');
  const sug = page.locator('.suggestion-row button').first();
  if (await sug.count()) {
    await sug.click();
    await page.click('#sendBtn');
    await page.waitForFunction(() => document.querySelectorAll('.message.assistant .bubble').length >= 1, { timeout: 30000 });
    const msgs = await page.locator('.message.assistant .bubble').count();
    if (msgs < 1) note('no assistant reply after chat');
  }

  console.log('7. New chat');
  await page.click('#newChatBtn');
  await page.waitForTimeout(300);

  console.log('8. Admin login');
  await page.goto(`${BASE}/admin-login.html`);
  await page.fill('#adminUserIdInput', 'admin');
  await page.fill('#adminPasswordInput', 'admin');
  await page.click('#adminLoginBtn');
  await page.waitForURL(/admin\.html/, { timeout: 15000 });
  await page.waitForTimeout(3000);
  const segActive = await page.locator('#chatbotSwitch button.active').count();
  if (segActive < 1) note('admin segmented switch has no active state');

  await browser.close();
  if (issues.length) {
    console.log('\nFound', issues.length, 'issue(s)');
    process.exit(1);
  }
  console.log('\nE2E smoke passed');
}

main().catch((e) => { console.error(e); process.exit(1); });
