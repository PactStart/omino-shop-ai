# 第 2 层：设计基础与 Token

## 1. 使用原则

设计底座采用 shadcn/ui 的视觉语言和 Tailwind Token 思路。H5 使用 Tailwind，Android Compose 与 iOS SwiftUI 映射相同的语义 Token，不追求逐像素复制浏览器。

默认只实现 V1。V2–V5 只换颜色，不改变布局、信息顺序、组件尺寸、圆角或交互。

## 2. Figma Variables

建立 Collection `App Theme`，Mode 为 `V1` 至 `V5`。圆角不随主题变化：`radius/card=12`，`radius/button=16`。

| Variable | V1 晴空绿 | V2 科技黑 | V3 简约白 | V4 咸鱼黄 | V5 京东红 |
| --- | --- | --- | --- | --- | --- |
| `color/canvas` | `#F7F7F5` | `#0E1116` | `#FFFFFF` | `#FFF8E6` | `#F5F5F5` |
| `color/surface` | `#FFFFFF` | `#171C24` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| `color/primary` | `#0F8F7A` | `#2F6FED` | `#111111` | `#FFD100` | `#E1251B` |
| `color/on-primary` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#1A1A1A` | `#FFFFFF` |
| `color/price` | `#E11D48` | `#FF5C6C` | `#E11D48` | `#F0441C` | `#E1251B` |
| `color/text-primary` | `#171717` | `#F4F6F8` | `#111111` | `#1A1A1A` | `#1A1A1A` |
| `color/text-secondary` | `#737373` | `#9AA3B2` | `#6B6B6B` | `#8A8175` | `#8C8C8C` |
| `color/border` | `#EBEBEA` | `#2A3140` | `#ECECEC` | `#F0E6C8` | `#EEEEEE` |

正文与所在背景、`on-primary` 与 `primary` 至少达到 WCAG AA。京东红的主色和价格色同值，组件仍分别引用两个变量。页面不得再写一套色。

另建与主题无关的语义变量：

- `color/danger`：仅危险操作、失败状态；不得替代价格色。
- `color/success`、`color/warning`、`color/info`：只用于状态反馈，保持克制。
- `color/overlay`：Sheet/Modal 遮罩。
- `opacity/disabled`、`opacity/pressed`。

这四枚状态色先不写死色值。`#10B981`、`#F59E0B`、`#3B82F6`、`#EF4444` 分别贴近晴空绿、咸鱼黄、科技黑、京东红的主色，五套主题共用时，成功、警告、提示、失败会看起来像品牌色。

上表色值已锁定。不要为了补完整再扩展品牌色盘。

## 3. 尺寸、栅格与安全区

基准画板为 `390 x 844`。页面水平 padding 采用 `16`；紧凑消息流内部间距可用 `8/12`；内容最大宽度在平板与桌面 H5 上限制为 `640`，居中显示。

使用 4pt 基础网格：`4, 8, 12, 16, 20, 24, 32, 40, 48`。常规列表行最小高 `48`，点击目标最小 `44 x 44`。顶部栏内容高建议 `56`，底部 Tab 内容高建议 `56`，另加平台安全区。

这些是设计生成基线，不覆盖系统控件的原生尺寸。所有固定头尾必须通过 Auto Layout 和 constraints 表达，不能靠绝对坐标模拟。

## 4. 排版

使用平台系统中文字体。禁止随视口宽度缩放字号，letter spacing 为 `0`。

| Style | 基准 | 用途 |
| --- | --- | --- |
| `display/asset` | 32/40 Semibold | 积分或钱包主数字，仅资产页 |
| `title/page` | 20/28 Semibold | 页面标题 |
| `title/section` | 16/24 Semibold | 分组标题、卡片标题 |
| `body/regular` | 15/22 Regular | 正文与消息 |
| `body/medium` | 15/22 Medium | 按钮、重要行 |
| `label` | 13/18 Medium | 标签、状态、辅助信息 |
| `caption` | 12/16 Regular | 时间、说明、弱信息 |

本地字号设置 `sm/md/lg` 通过 Text Variable 或 Style Mode 统一切换；不能逐页面单独调整。长标题最多两行，金额和重要状态不得截断。

## 5. 图标与影像

- 图标使用 Lucide 同源语言：24 栅格、2px 线、圆角端点。
- 熟悉动作优先使用图标按钮并提供 tooltip/无障碍名称，不用带文字的圆角胶囊代替常用符号。
- 商品图使用稳定比例容器，图片加载失败显示中性占位，不改变卡片尺寸。
- 不使用紫粉渐变、玻璃拟态、霓虹、装饰光球、全屏 Lottie 或双色插画图标。标志不加发光渐变。

## 6. 主题气质

- V1 晴空绿：浅暖灰底、白卡片、青绿主色。默认方案。
- V2 科技黑：近黑画布、抬起一级的深色卡片、单一蓝色主色。实色，不用渐变、霓虹和发光。
- V3 简约白：白底白卡片、黑色主色、细边线。层次靠排版和间距。
- V4 咸鱼黄：暖白画布、白卡片。黄色只用于主色，按钮上用深色字。价格用橙红，避免整页被黄铺满。
- V5 京东红：浅灰画布、白卡片、品牌红主色。红色用于主按钮和价格，其余保持中性。

## 7. 动效与反馈

常规过渡 `150–200ms ease`；页面使用 push；Sheet 自底向上且最高不超过视口 `92%`。流式文本使用细进度或光标，不使用跳动弹簧。Toast 底部居中、单行优先、约 2 秒，不连续堆叠系统 Alert。

## 8. 响应式与内容压力测试

每个核心组件至少测试：320px 窄屏、390px 基准屏、430px 宽屏、字体 `lg`、两行中文标题、无图片、最大金额、空字段和键盘展开。内容变化不能使按钮、计数器、图片或底栏发生布局跳动。

## 9. Figma 生成指令

先建立 Variables 和基础 Text Styles，再生成任何高保真页面。只使用语义变量；图层中出现硬编码主题色、未定义圆角或新字体时，视为验收失败。

