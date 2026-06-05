export const meta = {
  name: 'gui-pattern-analysis',
  description: '深入分析反编译Java代码中的Swing/AWT GUI模式与业务逻辑交互方式',
  phases: [
    { title: 'Discover', detail: '全面搜索所有模块中的Swing/AWT GUI相关代码，按模块统计' },
    { title: 'DeepDive', detail: '深入分析每个模块的GUI-业务交互模式' },
    { title: 'Synthesize', detail: '归类所有交互模式，统计调用点数量，提取共性特征' },
    { title: 'Report', detail: '生成HTML单文件报告，聚焦WEB化重构决策依据' }
  ]
}

const MODEL = 'deepseek-v4-pro[1m]'

phase('Discover')

// 并行搜索所有关键 GUI 符号
const guiDiscoverResults = await parallel([
  () => agent(
    `在 /home/oyealex/project/hktorg/toolbox/original 目录下，使用 codegraph_search 和 codegraph_explore 全面搜索 Swing GUI 相关代码。

    重点搜索以下内容：
    1. 搜索 "javax.swing" 相关的所有 import 和引用
    2. 搜索 "java.awt" 相关的所有 import 和引用（排除 java.awt.event，那是事件处理）
    3. 搜索具体的 Swing 组件类名：JFrame, JPanel, JDialog, JButton, JTable, JTree, JList, JComponent, JLabel, JTextField, JTextArea, JScrollPane, JCheckBox, JComboBox, JTabbedPane, JOptionPane, JMenuBar, JMenu, JToolBar, JSplitPane, JProgressBar, JSpinner, JSlider, JFileChooser
    4. 搜索 GUI 事件监听器：ActionListener, MouseListener, KeyListener, WindowListener, FocusListener, ItemListener, ChangeListener, ListSelectionListener, DocumentListener, ComponentListener
    5. 搜索 Swing 工具类：SwingUtilities, SwingWorker, Timer (javax.swing.Timer), EventQueue

    对每个搜索结果，统计：
    - 所属模块（original/下的第一级子目录）
    - 文件路径
    - 使用的主要 GUI 组件类型
    - 是否包含 JFrame/JDialog（顶层窗口）

    返回：
    - 每个模块的详细 GUI 文件列表
    - 每个文件的 GUI 组件使用概况
    - 总 GUI 文件数量
    - 识别出的顶层窗口类（extends JFrame/JDialog 或包含 main 方法的 GUI 类）`,
    { model: MODEL, schema: {
      type: 'object',
      properties: {
        totalGuiFiles: { type: 'number' },
        moduleDetails: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              module: { type: 'string' },
              guiFileCount: { type: 'number' },
              topLevel: { type: 'array', items: { type: 'string' } },
              components: { type: 'array', items: { type: 'string' } },
              files: { type: 'array', items: {
                type: 'object',
                properties: {
                  path: { type: 'string' },
                  components: { type: 'array', items: { type: 'string' } },
                  hasTopLevel: { type: 'boolean' }
                }
              }}
            }
          }
        },
        topWindows: { type: 'array', items: { type: 'string' } },
        summary: { type: 'string' }
      },
      required: ['totalGuiFiles', 'moduleDetails', 'topWindows', 'summary']
    } }
  ),

  () => agent(
    `在 /home/oyealex/project/hktorg/toolbox/original 目录下，使用 codegraph 搜索以下业务逻辑交互相关的代码模式：

    1. SwingWorker 的使用 —— 搜索 "SwingWorker" 和 "doInBackground"
    2. SwingUtilities.invokeLater / invokeAndWait —— 搜索 "invokeLater" 和 "invokeAndWait"
    3. EventQueue.invokeLater —— 搜索 "EventQueue"
    4. Timer (javax.swing.Timer 和 java.util.Timer) —— 搜索 "Timer" 并区分类型
    5. PropertyChangeListener / PropertyChangeSupport —— 搜索 "PropertyChange"
    6. add*Listener 模式 —— 搜索 "addActionListener", "addMouseListener", "addKeyListener", "addWindowListener", "addFocusListener"
    7. setModel / getModel 模式 —— 搜索 "setModel" 和 "DefaultTableModel", "DefaultListModel"
    8. 自定义 Event 和 Listener 接口 —— 搜索 "EventListener" 和 "EventObject"

    对每个模式，统计：
    - 使用该模式的文件数量
    - 所属模块
    - 典型代码示例的文件路径

    返回每种模式的使用统计和典型示例。`,
    { model: MODEL, schema: {
      type: 'object',
      properties: {
        patterns: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              patternName: { type: 'string' },
              description: { type: 'string' },
              fileCount: { type: 'number' },
              moduleDistribution: { type: 'object', additionalProperties: { type: 'number' } },
              examples: { type: 'array', items: { type: 'string' } }
            }
          }
        },
        summary: { type: 'string' }
      },
      required: ['patterns', 'summary']
    } }
  )
])

const [guiModules, interactionPatterns] = guiDiscoverResults.filter(Boolean)

log(`Phase 1 完成:`)
log(`  - GUI 文件总数: ${guiModules.totalGuiFiles}`)
log(`  - 顶层窗口: ${guiModules.topWindows.length} 个`)
log(`  - 交互模式类型: ${interactionPatterns.patterns.length} 种`)

phase('DeepDive')

// 深入分析每个重要模块的 GUI-业务交互逻辑
const topModules = guiModules.moduleDetails
  .filter(m => m.guiFileCount >= 3)
  .sort((a, b) => b.guiFileCount - a.guiFileCount)
  .slice(0, 12)
  .map(m => m.module)

log(`深入分析模块: ${topModules.join(', ')}`)

const moduleAnalysis = await pipeline(
  topModules,
  async (module) => {
    const files = guiModules.moduleDetails.find(m => m.module === module)?.files || []
    const fileList = files.map(f => f.path).join(', ')

    return agent(
      `深入分析 /home/oyealex/project/hktorg/toolbox/original/${module} 模块中的 GUI-业务逻辑交互模式。

      该模块包含以下 GUI 文件:
      ${fileList}

      对于每个 GUI 文件，使用 codegraph_explore 和 codegraph_node 详细分析：

      1. **事件处理模式**：
         - ActionListener 的实现方式（匿名内部类、lambda、独立类、本类实现接口）
         - 事件处理方法中如何调用业务逻辑（直接调用、通过Service调用、通过事件总线、通过回调接口）
         - 是否使用了 SwingWorker 在后台执行

      2. **界面更新模式**：
         - 业务逻辑完成后如何更新界面（直接调用Swing组件、SwingUtilities.invokeLater、PropertyChangeListener回调）
         - 是否有轮询更新（Timer定时刷新）
         - 是否有推送更新（观察者模式/事件监听）

      3. **数据绑定模式**：
         - TableModel / ListModel 的使用方式
         - 数据如何从业务层流向界面层（直接setModel、自定义Model、适配器转换）

      4. **窗口管理**：
         - JFrame/JDialog 的创建和生命周期管理
         - 窗口间如何通信
         - 模态vs非模态对话框的使用

      5. **线程交互**：
         - EDT线程和后台线程的交互安全机制
         - 是否存在潜在的线程安全问题

      对每个 GUI 文件，详细描述其交互模式并给出关键代码段的行号引用。

      返回该模块中所有识别出的模式类型、每种模式的特征描述、以及具体的调用点数量。`,
      { model: MODEL, phase: 'DeepDive', schema: {
        type: 'object',
        properties: {
          module: { type: 'string' },
          filesAnalyzed: { type: 'number' },
          patterns: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                patternType: { type: 'string' },
                characteristic: { type: 'string' },
                callSiteCount: { type: 'number' },
                fileLocations: { type: 'array', items: { type: 'string' } },
                codeSnippets: { type: 'array', items: { type: 'string' } }
              }
            }
          },
          threadSafetyIssues: { type: 'array', items: { type: 'string' } },
          summary: { type: 'string' }
        },
        required: ['module', 'filesAnalyzed', 'patterns', 'summary']
      } }
    )
  }
)

log(`Phase 2 完成: 分析了 ${moduleAnalysis.length} 个模块`)

phase('Synthesize')

// 综合所有分析结果，归类模式
const synthesis = await agent(
  `综合以下所有模块的 GUI-业务交互分析结果，进行全量归纳总结。

## 各个模块的分析结果：
${JSON.stringify(moduleAnalysis.filter(Boolean).map(m => ({
  module: m.module,
  patternCount: m.patterns.length,
  patterns: m.patterns.map(p => ({ type: p.patternType, calls: p.callSiteCount, char: p.characteristic }))
})), null, 2)}

## 全局交互模式统计：
${JSON.stringify(interactionPatterns, null, 2)}

## 任务要求：

请将所有识别出的 GUI-业务交互模式归入以下分类体系，并统计每种模式的总调用点数量：

### 分类维度一：按交互方向
1. **界面→业务（用户操作触发业务逻辑）**
   - 1a. ActionListener 直接调用业务方法
   - 1b. ActionListener 通过 Service/Manager 间接调用
   - 1c. ActionListener 触发事件总线/回调
   - 1d. 匿名内部类/ActionListener 包装模式

2. **业务→界面（业务逻辑主动更新界面）**
   - 2a. 业务方法直接操作 Swing 组件（耦合最紧）
   - 2b. 通过 SwingUtilities.invokeLater 安全更新
   - 2c. 通过 PropertyChangeListener/观察者模式解耦更新
   - 2d. 通过自定义 EventListener 接口解耦
   - 2e. 通过 SwingWorker done() 回调更新

3. **双向数据绑定**
   - 3a. TableModel/ListModel 数据绑定
   - 3b. 自定义 Model 适配
   - 3c. 组件直接读写业务数据对象

4. **定时/轮询更新**
   - 4a. javax.swing.Timer 定时刷新
   - 4b. java.util.Timer 后台轮询
   - 4c. 自定义轮询线程

### 分类维度二：按耦合程度
- **紧耦合**：界面代码直接持有业务对象引用，互相直接调用
- **松耦合**：通过接口/事件/观察者模式解耦
- **混合**：同一模块中同时存在紧耦合和松耦合

### 分类维度三：按线程安全策略
- EDT 安全（所有 Swing 操作在 EDT 线程）
- 不安全（后台线程直接操作 Swing 组件）
- 混合策略

**关键要求**：
- 统计每种模式在整个项目中的总调用点数量
- 对于每种模式，给出 2-3 个最具代表性的代码示例位置
- 识别出无法归类的特殊代码模式，单独列举
- 按模块维度统计：每个模块分别包含哪些模式、各有多少调用点

返回完整的分类统计结果，用于生成最终报告。`,
  { model: MODEL, schema: {
    type: 'object',
    properties: {
      // 分类一：按交互方向
      directionCategories: {
        type: 'object',
        properties: {
          uiToBusiness: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                subCategory: { type: 'string' },
                description: { type: 'string' },
                totalCallSites: { type: 'number' },
                moduleDistribution: { type: 'object', additionalProperties: { type: 'number' } },
                examples: { type: 'array', items: { type: 'string' } }
              }
            }
          },
          businessToUi: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                subCategory: { type: 'string' },
                description: { type: 'string' },
                totalCallSites: { type: 'number' },
                moduleDistribution: { type: 'object', additionalProperties: { type: 'number' } },
                examples: { type: 'array', items: { type: 'string' } }
              }
            }
          },
          bidirectional: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                subCategory: { type: 'string' },
                description: { type: 'string' },
                totalCallSites: { type: 'number' },
                moduleDistribution: { type: 'object', additionalProperties: { type: 'number' } },
                examples: { type: 'array', items: { type: 'string' } }
              }
            }
          },
          polling: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                subCategory: { type: 'string' },
                description: { type: 'string' },
                totalCallSites: { type: 'number' },
                moduleDistribution: { type: 'object', additionalProperties: { type: 'number' } },
                examples: { type: 'array', items: { type: 'string' } }
              }
            }
          }
        }
      },
      // 分类二：按耦合程度
      couplingAnalysis: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            level: { type: 'string' },
            moduleCount: { type: 'number' },
            modules: { type: 'array', items: { type: 'string' } },
            riskForWebMigration: { type: 'string' }
          }
        }
      },
      // 分类三：线程安全
      threadSafety: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            strategy: { type: 'string' },
            moduleCount: { type: 'number' },
            modules: { type: 'array', items: { type: 'string' } },
            riskForWebMigration: { type: 'string' }
          }
        }
      },
      // 无法归类
      unclassified: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            description: { type: 'string' },
            location: { type: 'string' },
            reason: { type: 'string' }
          }
        }
      },
      // 模块维度汇总
      moduleSummary: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            module: { type: 'string' },
            guiFileCount: { type: 'number' },
            patterns: { type: 'array', items: { type: 'string' } },
            couplingLevel: { type: 'string' },
            threadSafety: { type: 'string' },
            webMigrationComplexity: { type: 'string' }
          }
        }
      },
      // 重构建议
      refactoringInsights: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            insight: { type: 'string' },
            priority: { type: 'string' },
            affectedModules: { type: 'array', items: { type: 'string' } }
          }
        }
      },
      summary: { type: 'string' }
    },
    required: ['directionCategories', 'couplingAnalysis', 'threadSafety', 'unclassified', 'moduleSummary', 'refactoringInsights', 'summary']
  } }
)

log(`Phase 3 完成: 模式分类和统计完成`)
log(`  - 界面→业务 子类: ${synthesis.directionCategories.uiToBusiness?.length || 0}`)
log(`  - 业务→界面 子类: ${synthesis.directionCategories.businessToUi?.length || 0}`)
log(`  - 双向绑定 子类: ${synthesis.directionCategories.bidirectional?.length || 0}`)
log(`  - 定时轮询 子类: ${synthesis.directionCategories.polling?.length || 0}`)
log(`  - 无法归类: ${synthesis.unclassified?.length || 0}`)

phase('Report')

// 生成 HTML 报告
const report = await agent(
  `基于以下完整的 GUI-业务交互模式分析数据，生成一个专业的 HTML 单文件报告。

## 分析数据：
${JSON.stringify(synthesis, null, 2)}

## 全局数据：
- 总 GUI 文件数: ${guiModules.totalGuiFiles}
- 总模块数: ${guiModules.moduleDetails.length}
- 顶层窗口数: ${guiModules.topWindows.length}

## HTML 报告要求：

### 报告标题
"SmartKit 存储设备运维工具箱 — GUI交互模式分析报告（WEB化重构决策依据）"

### 必须包含的章节：

1. **概述**（Executive Summary）
   - 分析范围、方法
   - 关键数字（GUI文件数、模块数、识别出的模式种类数）
   - 核心结论（一句话总结重构难度）

2. **全局统计面板**
   - 使用卡片式布局展示关键指标
   - 使用 Chart.js 或其他方式展示模块分布图（用纯 CSS/SVG 实现即可，不依赖外部资源）

3. **交互方向分类详解**
   对每种模式子类：
   - 模式名称和描述
   - 总调用点数量（显眼的数字）
   - 模块分布（表格）
   - 代表性代码示例（带语法高亮的代码块）
   - 对WEB化重构的影响评估

4. **耦合度分析**
   - 紧耦合/松耦合/混合的模块分布
   - 可视化展示
   - 每种耦合方式的重构难度评估

5. **线程安全分析**
   - EDT安全策略分布
   - 对WEB化重构的影响

6. **模块维度总览**
   - 每个模块的GUI文件数、模式、耦合度、重构难度
   - 可排序的表格

7. **无法归类的特殊模式**
   - 列举所有无法归类的代码
   - 说明无法归类的原因

8. **WEB化重构建议**
   - 按优先级排列的建议
   - 每个建议的受影响模块
   - 建议的重构策略

9. **附录**
   - 完整的方法论说明
   - 术语定义

### 设计风格：
- 深色主题（dark mode），专业的技术报告风格
- 使用现代化的 CSS（flexbox/grid）
- 响应式布局
- 代码块使用暗色语法高亮（内联CSS实现）
- 使用 CSS 打印友好的样式
- 卡片、表格、数据可视化元素
- 字体使用系统默认等宽/无衬线字体
- 颜色方案：深色背景 #1a1a2e, 卡片 #16213e, 强调色 #e94560 或 #0f3460
- 添加平滑过渡动画和 hover 效果

### 关键原则：
- 这是决策依据文档，不是纯技术参考手册
- 每个分析点都要关联到 WEB 化重构的影响
- 数据要具体、可追溯
- 聚焦在业务无关的界面代码和逻辑代码的交互形式
- 忽略纯界面的逻辑包装（如如何绘制表格）`,

  { model: MODEL, schema: {
    type: 'object',
    properties: {
      htmlReport: { type: 'string' },
      reportPath: { type: 'string' }
    },
    required: ['htmlReport']
  } }
)

log(`Phase 4 完成: HTML 报告已生成`)

return {
  report: report.htmlReport,
  synthesis,
  guiModules,
  interactionPatterns,
  moduleAnalysis
}
