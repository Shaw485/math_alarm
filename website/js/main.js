document.addEventListener('DOMContentLoaded', function() {
    const appsData = [
        {
            id: 0,
            title: '数学题闹钟',
            shortName: '数学闹钟',
            category: 'Android App · 工具',
            date: '2026',
            rating: '4.9 (2,300 评价)',
            gradient: 'linear-gradient(135deg, #1c1c1e 0%, #000000 50%, #2c2c2e 100%)',
            iconStroke: '#FF9500',
            iconSVG: '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>',
            desc: '一款必须答对数学题才能停止响铃的闹钟 App，专治各种起床困难症。每次响铃随机生成「a × b + c × d」格式的题目，且 a、b、c、d 四个数字严格限定在 3–9 之间，确保需要真正清醒计算才能答出。采用 Kotlin + Jetpack Compose 原生开发，纯黑 #000000 背景 + 高饱和橙 #FF9500 主色调；时间选择器基于 LazyColumn 实现 3 行显示滚轮，支持无限滚动与自动吸附；内置「清晨 / 风来 / 钢琴」三款高品质铃声；严格适配 Android 14 精确闹钟与全屏通知权限，开机重启后自动恢复闹钟；响铃超时（10 分钟）自动兜底结束。',
            features: [
                '🔢 数学题解锁：a×b+c×d 形式题目，a/b/c/d 严格 3–9 整数',
                '🎨 极简深色：纯黑 #000000 背景 + 橙色 #FF9500 主色',
                '🎡 滚轮选时：LazyColumn 3 行显示，无限滚动+自动吸附',
                '🎵 三档铃声：清晨、风来、钢琴精选官方 MP3',
                '🛡️ 全版本适配：精确闹钟 + 全屏通知 + 开机自启 + 锁屏显示',
                '⏱️ 超时兜底：响铃最长 10 分钟，答对题目或超时自动回流未设置'
            ],
            tags: ['Kotlin', 'Jetpack Compose', 'AlarmManager', 'DataStore', 'Android 14', 'Foreground Service'],
            screenshots: [
                'images/alarm-1-home.png',
                'images/alarm-2-ringing.png',
                'images/alarm-3-ringtone.png'
            ]
        },
        {
            id: 1,
            title: 'FitTrack 健康追踪',
            shortName: '健康追踪',
            category: '健康应用',
            date: '2024',
            rating: '4.8 (5,600 评价)',
            gradient: 'linear-gradient(135deg, #34c759 0%, #30d158 50%, #30b0c7 100%)',
            iconStroke: 'white',
            iconSVG: '<path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>',
            desc: '全方位健康管理应用，记录运动、睡眠、饮食数据，提供智能分析和个性化建议。支持连接主流智能手环和体重秤，自动同步健康数据。',
            features: [
                '🏃 运动追踪：跑步、骑行、徒步多种运动模式',
                '😴 睡眠监测：深睡、浅睡、REM 周期分析',
                '🍎 饮食记录：卡路里、营养成分自动计算',
                '📊 智能报告：周月年健康趋势分析',
                '🎯 目标设定：个性化健康目标提醒',
                '⌚ 设备连接：支持主流智能穿戴设备'
            ],
            tags: ['Flutter', 'HealthKit', 'Charts', 'Firebase'],
            screenshots: [
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=fitness%20app%20dashboard%20activity%20rings%20steps%20calories%20green%20health%20ui&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=sleep%20tracking%20app%20screen%20sleep%20stages%20chart%20analysis&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=food%20calorie%20tracker%20app%20meal%20logging%20nutrition%20info&image_size=portrait_4_3'
            ]
        },
        {
            id: 2,
            title: 'SmartAI 智能助手',
            shortName: '智能助手',
            category: 'AI 应用',
            date: '2024',
            rating: '4.9 (8,100 评价)',
            gradient: 'linear-gradient(135deg, #5856d6 0%, #af52de 50%, #0071e3 100%)',
            iconStroke: 'white',
            iconSVG: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>',
            desc: '基于大语言模型的个人智能助手，支持对话、写作、翻译、代码生成等多种场景。内置多轮对话记忆，支持自定义角色和场景模板。',
            features: [
                '💬 智能对话：多轮上下文理解，自然流畅交流',
                '✍️ 写作助手：公文、邮件、文案一键生成',
                '🌐 多语翻译：100+ 语言互译，专业级准确',
                '💻 代码帮助：多语言代码生成、解释、调试',
                '🎭 角色模板：翻译官、文案、导师多种人设',
                '📝 历史记录：云端同步，随时回溯'
            ],
            tags: ['Kotlin', 'LLM API', 'Compose', 'MVVM'],
            screenshots: [
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=ai%20chat%20assistant%20app%20conversation%20purple%20gradient%20clean%20ui&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=ai%20writing%20tool%20app%20interface%20text%20generation%20options&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=ai%20code%20assistant%20app%20screen%20code%20highlight%20syntax&image_size=portrait_4_3'
            ]
        },
        {
            id: 3,
            title: 'SkyView 天气预报',
            shortName: '天气预报',
            category: '工具应用',
            date: '2023',
            rating: '4.7 (12,400 评价)',
            gradient: 'linear-gradient(135deg, #5ac8fa 0%, #0071e3 50%, #64d2ff 100%)',
            iconStroke: 'white',
            iconSVG: '<path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path>',
            desc: '精美的天气应用，实时天气、7天预报、空气质量指数，动态天气背景让查看天气更愉悦。支持全球 200+ 万城市和分钟级降水预报。',
            features: [
                '🌤️ 实时天气：温度、体感、湿度、风速',
                '📅 15日预报：长周期天气趋势预测',
                '🌧️ 降水提醒：分钟级雨雪预警推送',
                '🌫️ 空气质量：AQI、PM2.5 等多指标',
                '🎬 动态背景：根据天气实时变化',
                '📍 多城市：支持无限城市收藏切换'
            ],
            tags: ['Compose', 'OpenWeather', 'Animation', 'Material 3'],
            screenshots: [
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=weather%20app%20sunny%20day%20screen%20temperature%20clouds%20blue%20sky&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=weather%20forecast%20app%207%20day%20weekly%20prediction%20list%20ui&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=air%20quality%20weather%20app%20aqi%20index%20colors%20dashboard&image_size=portrait_4_3'
            ]
        },
        {
            id: 4,
            title: 'MoneyBox 记账本',
            shortName: '记账本',
            category: '财务管理',
            date: '2023',
            rating: '4.8 (3,200 评价)',
            gradient: 'linear-gradient(135deg, #30d158 0%, #34c759 50%, #00c7be 100%)',
            iconStroke: 'white',
            iconSVG: '<line x1="12" y1="1" x2="12" y2="23"></line><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>',
            desc: '简洁易用的个人财务管理 App，支持多账本、预算管理、消费分类统计和云端同步。帮您养成良好的记账习惯，实现财务自由。',
            features: [
                '💰 快速记账：一键录入，支持模板',
                '📚 多账本：日常、旅行、婚礼等独立账本',
                '📈 数据报表：分类、月度趋势可视化',
                '🎯 预算控制：超支智能提醒',
                '☁️ 云端同步：多设备无缝切换',
                '🔒 隐私保护：指纹密码锁保障安全'
            ],
            tags: ['Android', 'Room DB', 'MVVM', 'DataStore'],
            screenshots: [
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=expense%20tracker%20app%20home%20balance%20income%20expense%20summary%20green&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=budgeting%20app%20category%20spending%20pie%20chart%20breakdown&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=add%20expense%20form%20app%20screen%20amount%20category%20input&image_size=portrait_4_3'
            ]
        },
        {
            id: 5,
            title: 'ZenMind 冥想睡眠',
            shortName: '冥想睡眠',
            category: '生活方式',
            date: '2023',
            rating: '4.9 (6,800 评价)',
            gradient: 'linear-gradient(135deg, #5e5ce6 0%, #bf5af2 50%, #32ade6 100%)',
            iconStroke: 'white',
            iconSVG: '<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>',
            desc: '帮助用户冥想放松、改善睡眠的应用。提供白噪音、冥想课程、呼吸练习和睡眠故事。帮助您减轻压力，获得更好的睡眠质量。',
            features: [
                '🧘 冥想课程：初阶到高阶多种主题',
                '🌊 白噪音：雨声、海浪、篝火等 100+ 种',
                '🌙 睡眠故事：助眠有声读物',
                '💨 呼吸练习：多种呼吸法引导',
                '⏰ 智能唤醒：浅睡眠期轻柔叫醒',
                '📊 睡眠统计：时长、质量趋势分析'
            ],
            tags: ['Compose', 'MediaPlayer', 'Audio', 'Foreground Service'],
            screenshots: [
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=meditation%20app%20calm%20breathing%20exercise%20purple%20night%20theme&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=sleep%20sounds%20app%20white%20noise%20ambient%20sound%20selection%20player&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=meditation%20courses%20library%20app%20list%20various%20topics%20calm%20ui&image_size=portrait_4_3'
            ]
        },
        {
            id: 6,
            title: 'WordUp 背单词',
            shortName: '背单词',
            category: '教育学习',
            date: '2024',
            rating: '4.8 (4,500 评价)',
            gradient: 'linear-gradient(135deg, #ff375f 0%, #ff2d55 50%, #ff6b6b 100%)',
            iconStroke: 'white',
            iconSVG: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>',
            desc: '基于艾宾浩斯遗忘曲线的单词记忆 App，支持多种词书、例句发音、学习统计和复习提醒。科学的记忆算法让背单词效率翻倍。',
            features: [
                '📖 海量词书：四六级、考研、雅思、托福等',
                '🧠 科学算法：艾宾浩斯遗忘曲线优化',
                '🔊 真人发音：英音美音自由切换',
                '✏️ 多种模式：选择、拼写、听音等',
                '📊 学习报告：每日、每周进度追踪',
                '🔔 智能提醒：最佳复习时间推送'
            ],
            tags: ['Kotlin', 'DataStore', 'TTS', 'Room'],
            screenshots: [
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=vocabulary%20flashcard%20app%20word%20learning%20card%20english%20definition&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=language%20learning%20app%20daily%20goal%20streak%20progress%20stats&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=word%20book%20library%20app%20selection%20cet4%20cet6%20ielts%20toefl&image_size=portrait_4_3'
            ]
        },
        {
            id: 7,
            title: 'ChefPal 食谱助手',
            shortName: '食谱助手',
            category: '美食生活',
            date: '2022',
            rating: '4.7 (9,200 评价)',
            gradient: 'linear-gradient(135deg, #ff9f0a 0%, #ff6b00 50%, #ff3b30 100%)',
            iconStroke: 'white',
            iconSVG: '<path d="M6 13.87A4 4 0 0 1 7.41 6a5.11 5.11 0 0 1 1.05-1.54 5 5 0 0 1 7.08 0A5.11 5.11 0 0 1 16.59 6 4 4 0 0 1 18 13.87V21H6Z"></path><line x1="6" y1="17" x2="18" y2="17"></line>',
            desc: '美食爱好者的必备 App，海量精选食谱、智能食材推荐、步骤计时器和购物清单功能。让在家做饭变得简单又有趣。',
            features: [
                '🍳 海量食谱：中餐、西餐、烘焙等 10万+',
                '🛒 智能购物：一键生成购物清单',
                '⏱️ 步骤计时：烹饪时间自动提醒',
                '🥗 食材推荐：冰箱有啥做啥推荐',
                '📝 收藏分类：我的食谱本分类管理',
                '👥 社区分享：美食作品交流互动'
            ],
            tags: ['Android', 'Firebase', 'Glide', 'Material Design'],
            screenshots: [
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=recipe%20cooking%20app%20home%20featured%20dishes%20categories%20orange&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=cooking%20recipe%20detail%20app%20ingredients%20list%20steps%20instructions&image_size=portrait_4_3',
                'https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=grocery%20shopping%20list%20app%20screen%20items%20ingredients%20checklist&image_size=portrait_4_3'
            ]
        }
    ];

    const appCardFields = [
        { status: '已上线',  statusType: 'online',  publishDate: '2026/3/14', shortDesc: '必须答对 a×b+c×d 格式数学题才能关闹钟，a/b/c/d 严格限定在 3–9；纯黑 + 橙色极简风，清晨/风来/钢琴三铃声，支持 Android 14+ 精确闹钟。', likes: 2, wants: 8,  comments: 0 },
        { status: '已上线',  statusType: 'online',  publishDate: '2026/3/1',  shortDesc: '全方位健康追踪，运动、睡眠、饮食一站式管理，数据一目了然。', likes: 1, wants: 0,  comments: 0 },
        { status: '已上线',  statusType: 'online',  publishDate: '2026/3/1',  shortDesc: '把多个 AI 模型塞进手机里：对话、写作、翻译、代码随时调用。', likes: 5, wants: 8,  comments: 1 },
        { status: '已上线',  statusType: 'online',  publishDate: '2026/2/26', shortDesc: '不用再打开天气网页啦，自带动态背景和降水提醒的本地天气 App。', likes: 2, wants: 3,  comments: 2 },
        { status: '已上线',  statusType: 'online',  publishDate: '2026/2/20', shortDesc: '告别随手花钱，记账本帮你养成记录习惯，每月财务状况清清楚楚。', likes: 8, wants: 10, comments: 3 },
        { status: '已上线',  statusType: 'online',  publishDate: '2025/12/1', shortDesc: '白噪音、冥想课程、呼吸练习和睡眠故事，给大脑做一次 SPA。', likes: 3, wants: 6,  comments: 1 },
        { status: '开发中',  statusType: 'wip',     publishDate: '2026/2/16', shortDesc: '基于科学记忆曲线的背单词 App，词书覆盖从四六级到 GRE 全阶段（20%）。', likes: 4, wants: 7,  comments: 2 },
        { status: '已上线',  statusType: 'online',  publishDate: '2025/10/8', shortDesc: '把下厨房的经验都装进口袋，10万+ 食谱和一键购物清单随时用。', likes: 6, wants: 12, comments: 4 }
    ];
    appsData.forEach((app, i) => Object.assign(app, appCardFields[i]));

    function renderWorks() {
        const grid = document.getElementById('worksGrid');
        if (!grid) return;
        grid.innerHTML = appsData.map(app => {
            const statusClass = app.statusType === 'online' ? 'status-online' : 'status-wip';
            return `
            <div class="work-card" data-app="${app.id}">
                <div class="work-card-top">
                    <span class="status-tag ${statusClass}">${app.status}${app.statusType === 'wip' ? ' (20%)' : ''}</span>
                    <span class="work-date">${app.publishDate}</span>
                </div>
                <h3 class="work-title">「${app.shortName}」${app.title.replace(app.shortName, '').trim()}</h3>
                <p class="work-desc">${app.shortDesc}</p>
                <div class="work-card-bottom">
                    <div class="work-stats">
                        <span class="stat-item-mini" title="赞">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>
                            ${app.likes}
                        </span>
                        <span class="stat-item-mini" title="想要">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>
                            ${app.wants}
                        </span>
                        <span class="stat-item-mini" title="评论">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
                            ${app.comments}
                        </span>
                    </div>
                    <button class="btn-view-work" type="button">查看作品</button>
                </div>
            </div>`;
        }).join('');
    }
    renderWorks();

    const navbar = document.querySelector('.navbar');
    const navLinks = document.querySelectorAll('.nav-link');
    const navToggle = document.querySelector('.nav-toggle');
    const navLinksContainer = document.querySelector('.nav-links');
    const backToTop = document.getElementById('backToTop');
    const revealElements = document.querySelectorAll('.reveal');
    const statNumbers = document.querySelectorAll('.stat-number');
    const typewriterElement = document.querySelector('.typewriter');
    const contactForm = document.getElementById('contactForm');
    const formSuccess = document.getElementById('formSuccess');

    const appModal = document.getElementById('appModal');
    const modalIcon = document.getElementById('modalIcon');
    const modalTitle = document.getElementById('modalTitle');
    const modalCategory = document.getElementById('modalCategory');
    const modalDate = document.getElementById('modalDate');
    const modalRating = document.getElementById('modalRating');
    const modalDesc = document.getElementById('modalDesc');
    const modalFeatures = document.getElementById('modalFeatures');
    const modalTags = document.getElementById('modalTags');
    const modalScreenshots = document.getElementById('modalScreenshots');

    const worksGrid = document.getElementById('worksGrid');

    const chatWidget = document.getElementById('chatWidget');
    const chatToggle = document.getElementById('chatToggle');
    const chatPanelClose = document.getElementById('chatPanelClose');
    const chatForm = document.getElementById('chatForm');
    const chatInput = document.getElementById('chatInput');
    const chatMessages = document.getElementById('chatMessages');

    const roles = [
        '全栈开发者',
        'Android 工程师',
        'UI/UX 设计爱好者',
        '独立开发者',
        '产品创造者'
    ];

    let roleIndex = 0;
    let charIndex = 0;
    let isDeleting = false;
    let typewriterTimeout;

    function typeWriter() {
        if (!typewriterElement) return;
        const currentRole = roles[roleIndex];
        if (!isDeleting) {
            typewriterElement.textContent = currentRole.substring(0, charIndex + 1);
            charIndex++;
            if (charIndex === currentRole.length) {
                isDeleting = true;
                typewriterTimeout = setTimeout(typeWriter, 2000);
                return;
            }
        } else {
            typewriterElement.textContent = currentRole.substring(0, charIndex - 1);
            charIndex--;
            if (charIndex === 0) {
                isDeleting = false;
                roleIndex = (roleIndex + 1) % roles.length;
            }
        }
        typewriterTimeout = setTimeout(typeWriter, isDeleting ? 50 : 100);
    }

    function handleNavbarScroll() {
        if (window.scrollY > 20) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
        if (window.scrollY > 500) {
            backToTop.classList.add('visible');
        } else {
            backToTop.classList.remove('visible');
        }
        updateActiveNavLink();
    }

    function updateActiveNavLink() {
        const sections = document.querySelectorAll('section[id]');
        const scrollY = window.scrollY + 200;
        sections.forEach(section => {
            const sectionTop = section.offsetTop;
            const sectionHeight = section.offsetHeight;
            const sectionId = section.getAttribute('id');
            if (scrollY >= sectionTop && scrollY < sectionTop + sectionHeight) {
                navLinks.forEach(link => {
                    link.classList.remove('active');
                    if (link.getAttribute('href') === '#' + sectionId) {
                        link.classList.add('active');
                    }
                });
            }
        });
    }

    function revealOnScroll() {
        const triggerBottom = window.innerHeight * 0.88;
        revealElements.forEach(element => {
            const elementTop = element.getBoundingClientRect().top;
            if (elementTop < triggerBottom) {
                element.classList.add('visible');
            }
        });
    }

    function animateStats() {
        const heroSection = document.querySelector('.apps-hero-stats');
        if (!heroSection) return;
        const rect = heroSection.getBoundingClientRect();
        if (rect.top > window.innerHeight) return;
        statNumbers.forEach(stat => {
            if (stat.dataset.animated) return;
            const target = parseInt(stat.dataset.target);
            const duration = 1500;
            const startTime = performance.now();
            stat.dataset.animated = 'true';
            function update(currentTime) {
                const elapsed = currentTime - startTime;
                const progress = Math.min(elapsed / duration, 1);
                const easeOutQuart = 1 - Math.pow(1 - progress, 4);
                const current = Math.floor(easeOutQuart * target);
                if (target >= 1000) {
                    stat.textContent = current.toLocaleString();
                } else {
                    stat.textContent = current;
                }
                if (progress < 1) {
                    requestAnimationFrame(update);
                } else {
                    stat.textContent = target >= 1000 ? target.toLocaleString() : target;
                }
            }
            requestAnimationFrame(update);
        });
    }

    function openAppModal(appId) {
        const app = appsData.find(a => a.id === appId);
        if (!app) return;

        modalIcon.style.background = app.gradient;
        modalIcon.innerHTML = `<svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="${app.iconStroke}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${app.iconSVG}</svg>`;
        modalTitle.textContent = app.title;
        modalCategory.textContent = app.category;
        modalDate.textContent = app.date;
        modalRating.textContent = app.rating;
        modalDesc.textContent = app.desc;

        modalFeatures.innerHTML = app.features.map(f => `<li>${f}</li>`).join('');
        modalTags.innerHTML = app.tags.map(t => `<span>${t}</span>`).join('');
        modalScreenshots.innerHTML = app.screenshots.map((url, i) => `<img src="${url}" alt="截图${i + 1}">`).join('');

        appModal.classList.add('open');
        document.body.style.overflow = 'hidden';
    }

    function closeAppModal() {
        appModal.classList.remove('open');
        document.body.style.overflow = '';
    }

    worksGrid?.addEventListener('click', (e) => {
        const wrapper = e.target.closest('.work-card');
        if (!wrapper) return;
        const appId = parseInt(wrapper.dataset.app);
        openAppModal(appId);
    });

    appModal?.querySelectorAll('[data-close-modal]').forEach(el => {
        el.addEventListener('click', closeAppModal);
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && appModal.classList.contains('open')) {
            closeAppModal();
        }
    });

    function toggleChat(open) {
        const shouldOpen = typeof open === 'boolean' ? open : !chatWidget.classList.contains('open');
        chatWidget.classList.toggle('open', shouldOpen);
        if (shouldOpen) {
            setTimeout(() => chatInput?.focus(), 300);
        }
    }

    chatToggle?.addEventListener('click', () => toggleChat());
    chatPanelClose?.addEventListener('click', () => toggleChat(false));

    function addChatMessage(content, isUser = false) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `chat-message ${isUser ? 'user-message' : 'agent-message'}`;

        const bubble = document.createElement('div');
        bubble.className = 'chat-bubble';

        if (typeof content === 'string') {
            bubble.innerHTML = content.split('\n').map(line => `<p>${line}</p>`).join('');
        } else {
            bubble.appendChild(content);
        }

        msgDiv.appendChild(bubble);
        chatMessages.appendChild(msgDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
        return msgDiv;
    }

    function addTypingIndicator() {
        const bubble = document.createElement('div');
        bubble.className = 'chat-bubble';
        const indicator = document.createElement('div');
        indicator.className = 'typing-indicator';
        indicator.innerHTML = '<span></span><span></span><span></span>';
        bubble.appendChild(indicator);

        const msgDiv = document.createElement('div');
        msgDiv.className = 'chat-message agent-message';
        msgDiv.appendChild(bubble);
        msgDiv.id = 'typingIndicator';
        chatMessages.appendChild(msgDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function removeTypingIndicator() {
        const el = document.getElementById('typingIndicator');
        if (el) el.remove();
    }

    function getAgentReply(userMsg) {
        const msg = userMsg.toLowerCase();
        const appsList = appsData.map(a => `📱 ${a.title}`).join('\n');

        if (msg.includes('app') || msg.includes('作品') || msg.includes('哪些') || msg.includes('有什么')) {
            return `目前已发布 ${appsData.length} 款 App：\n${appsList}\n\n你可以点击页面上的图标查看每款的详情哦～`;
        }
        if (msg.includes('技术') || msg.includes('会什么') || msg.includes('技能') || msg.includes('栈')) {
            return '我主要精通以下技术方向：\n📱 Android：Kotlin、Jetpack Compose、原生开发\n🎨 UI/UX：Figma、交互动效设计\n🧠 架构：MVVM、Clean Architecture、模块化\n☁️ 后端：Firebase、REST API\n🎯 还有产品思维，从设计到发布全流程独立完成';
        }
        if (msg.includes('合作') || msg.includes('接单') || msg.includes('联系') || msg.includes('联系方式')) {
            return '欢迎合作！可以通过以下方式联系我：\n📧 邮箱：hello@example.com\n💬 也可以在页面底部的「联系我」表单留言\n\n合作类型包括：\n• 定制 Android App 开发\n• 技术咨询和架构设计\n• UI/UX 设计服务\n期待与你合作！✨';
        }
        if (msg.includes('价格') || msg.includes('报价') || msg.includes('多少钱')) {
            return '项目报价根据需求复杂度和周期而定，一般范围参考：\n💰 小型工具 App：2 - 5 万\n💰 中等功能 App：5 - 15 万\n💰 大型平台类 App：15 万起\n\n欢迎把你的需求发给我，我会给出详细的方案和报价～';
        }
        if (msg.includes('你好') || msg.includes('hi') || msg.includes('hello') || msg.includes('嗨')) {
            return '你好呀！👋 我是 Shaw 的 AI 助手，个人官网即将上线 shaw.cn。有什么想了解的吗？\n\n比如：\n• 他有哪些 App 作品？\n• 技术能力怎么样？\n• 可以合作吗？\n直接点击下方的快捷回复也可以哦～';
        }
        if (msg.includes('闹钟') || msg.includes('数学')) {
            const app = appsData[0];
            return `关于「${app.title}」：\n${app.desc}\n\n主要特点：\n${app.features.slice(0, 4).map(f => f).join('\n')}\n技术栈：${app.tags.join('、')}`;
        }
        if (msg.includes('是谁') || msg.includes('Shaw') || msg.includes('shaw.cn') || msg.includes('介绍')) {
            return 'Shaw 是一名有 5 年经验的独立 Android 开发者 🧑‍💻\n\n• 累计发布 8 款 App，总用户超 10 万\n• 主打简洁优雅的设计风格，注重细节\n• 从产品设计→开发→发布→运营全流程独立完成\n• 信奉「好产品自己会说话」\n\n个人官网：shaw.cn（即将上线）\n想进一步了解，可以浏览上面的 App 作品集～';
        }

        return '好的，我已记下你的问题～ 📝\n\n建议你可以：\n1️⃣ 浏览上方的 App 图标，查看作品详情\n2️⃣ 使用快捷回复提问更精准的问题\n3️⃣ 或在页面底部的联系表单留言\n\n如果你想和开发者本人直接沟通，欢迎发邮件到 shaw@shaw.cn 📧';
    }

    chatForm?.addEventListener('submit', (e) => {
        e.preventDefault();
        const text = chatInput.value.trim();
        if (!text) return;

        addChatMessage(text, true);
        chatInput.value = '';

        addTypingIndicator();
        setTimeout(() => {
            removeTypingIndicator();
            const reply = getAgentReply(text);
            addChatMessage(reply, false);
        }, 700 + Math.random() * 600);
    });

    document.addEventListener('click', (e) => {
        if (!e.target.classList.contains('quick-reply')) return;
        const text = e.target.textContent;
        addChatMessage(text, true);
        addTypingIndicator();
        setTimeout(() => {
            removeTypingIndicator();
            addChatMessage(getAgentReply(text), false);
        }, 700 + Math.random() * 600);
    });

    navToggle?.addEventListener('click', () => {
        navLinksContainer.classList.toggle('mobile-open');
        const spans = navToggle.querySelectorAll('span');
        if (navLinksContainer.classList.contains('mobile-open')) {
            spans[0].style.transform = 'rotate(45deg) translate(5px, 5px)';
            spans[1].style.opacity = '0';
            spans[2].style.transform = 'rotate(-45deg) translate(5px, -5px)';
        } else {
            spans[0].style.transform = '';
            spans[1].style.opacity = '';
            spans[2].style.transform = '';
        }
    });

    navLinks.forEach(link => {
        link.addEventListener('click', () => {
            navLinksContainer.classList.remove('mobile-open');
            const spans = navToggle?.querySelectorAll('span');
            if (spans) {
                spans[0].style.transform = '';
                spans[1].style.opacity = '';
                spans[2].style.transform = '';
            }
        });
    });

    backToTop?.addEventListener('click', () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });

    contactForm?.addEventListener('submit', (e) => {
        e.preventDefault();
        const submitBtn = contactForm.querySelector('.btn-submit');
        const originalText = submitBtn.innerHTML;
        submitBtn.disabled = true;
        submitBtn.innerHTML = `
            <svg class="loading-spinner" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <circle cx="12" cy="12" r="10" stroke-opacity="0.25"/>
                <path d="M12 2a10 10 0 0 1 10 10" stroke-linecap="round"/>
            </svg>
            发送中...
        `;
        setTimeout(() => {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
            formSuccess.classList.add('show');
            contactForm.reset();
            setTimeout(() => formSuccess.classList.remove('show'), 4000);
        }, 1500);
    });

    const style = document.createElement('style');
    style.textContent = `
        .loading-spinner { animation: spin 0.8s linear infinite; }
        @keyframes spin { to { transform: rotate(360deg); } }
    `;
    document.head.appendChild(style);

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) entry.target.classList.add('visible');
        });
    }, { threshold: 0.1, rootMargin: '0px 0px -50px 0px' });

    revealElements.forEach(el => observer.observe(el));

    window.addEventListener('scroll', handleNavbarScroll, { passive: true });
    window.addEventListener('scroll', animateStats, { passive: true });

    handleNavbarScroll();
    revealOnScroll();
    animateStats();
    setTimeout(typeWriter, 800);

    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            const href = this.getAttribute('href');
            if (href === '#') return;
            const target = document.querySelector(href);
            if (target) {
                e.preventDefault();
                const offsetTop = target.offsetTop - 60;
                window.scrollTo({ top: offsetTop, behavior: 'smooth' });
            }
        });
    });
});
