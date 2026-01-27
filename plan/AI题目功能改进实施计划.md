# AI 题目功能改进 - 实施计划

## 项目概述

对 SqlQuiz 项目的 AI 题目功能进行两处改进：
1. **题目规范化功能**：支持文字输入和图片上传（OCR识别），AI整理为MD格式，前端可编辑展示
2. **题目生成功能**：调用知识库（ID: 2013534505419395072）生成题目，AI整理为MD格式，前端可编辑展示

---

## 现有系统架构分析

### 后端关键文件
| 文件 | 路径 | 功能 |
|------|------|------|
| GLMService.java | `src/main/java/com/example/SqlQuiz/service/` | AI服务核心，包含normalizeQuestion()和generateQuestionWithRAG()方法 |
| AIQuestionController.java | `src/main/java/com/example/SqlQuiz/Controller/` | AI题目控制器，提供/normalize、/generate、/confirm接口 |
| SetupSqlExecutorService.java | `src/main/java/com/example/SqlQuiz/service/` | SQL执行服务，处理表名冲突（格式：quiz_q_[timestamp]_[random]） |
| QuizTableMetadata.java | `src/main/java/com/example/SqlQuiz/entity/` | 表元数据实体，记录表前缀与题目的关联 |

### 前端关键文件
| 文件 | 路径 | 功能 |
|------|------|------|
| question-ai-normalize.html | `src/main/resources/templates/teacher/` | 题目规范化页面 |
| question-ai-generate.html | `src/main/resources/templates/teacher/` | AI题目生成页面 |
| custom.css | `src/main/resources/static/css/` | 自定义样式 |

### 现有功能状态
- ✅ 题目文字输入和AI规范化
- ✅ 基于知识库的题目生成
- ✅ Markdown渲染（marked.js）
- ✅ 代码高亮（highlight.js）
- ✅ 表格可视化展示
- ✅ 可编辑表格组件
- ✅ 表名冲突处理机制
- ❌ **图片上传和OCR识别**（需新增）

---

## 实施方案

### 模块一：题目规范化功能 - 图片OCR支持

#### 1.1 后端修改

**文件：`GLMService.java`**
- **新增方法**：`performOCR(String imagePath)` - 使用GLM-4V模型进行图片文字识别
- **输入**：图片文件路径
- **输出**：识别出的文本内容
- **技术要点**：
  - 将图片转为Base64编码
  - 使用多模态API（图片+文本prompt）
  - 调用glm-4v模型

```java
/**
 * 使用 GLM-4V 进行图片 OCR 识别
 * @paramImagePath 图片文件路径
 * @return 识别出的文本内容
 */
public String performOCR(String imagePath) throws JsonProcessingException {
    // 1. 读取图片并转为Base64
    // 2. 构建多模态请求（图片 + OCR提示词）
    // 3. 调用 glm-4v 模型
    // 4. 解析并返回识别结果
}
```

**文件：`AIQuestionController.java`**
- **新增接口**：`POST /teacher/api/question/upload-image`
- **功能**：接收上传的图片，调用OCR识别，返回文本
- **参数**：MultipartFile file
- **响应**：JSON {success: true, text: "识别的文本"}

#### 1.2 前端修改

**文件：`question-ai-normalize.html`**

**新增UI组件**：
1. **输入类型选择器**：文字输入 / 图片上传
2. **图片上传区域**：
   - 拖拽上传支持
   - 点击选择文件
   - 图片预览
   - 删除按钮
3. **OCR进度提示**：识别中的加载状态

**新增JavaScript功能**：
- `handleImageUpload(file)` - 处理图片上传和预览
- `clearImage()` - 清除已上传图片
- 修改`normalizeQuestion()` - 支持图片OCR流程
  - 图片上传 → OCR识别 → 文本规范化

**新增CSS样式**：
```css
.upload-area { /* 拖拽区域样式 */ }
.upload-area.dragover { /* 拖拽悬停状态 */ }
#imagePreview { /* 图片预览容器 */ }
```

---

### 模块二：题目生成功能 - 优化增强

#### 2.1 后端优化

**文件：`GLMService.java`**

**优化方法**：`generateQuestionWithRAG(String questionType, String difficulty)`
- **优化Prompt**：更详细的输出格式要求
- **确保**：Markdown表格格式正确、数据库上下文清晰

**关键提示词改进**：
```
Requirements:
1. Generate UNIQUE and ORIGINAL question
2. Use real-world scenarios
3. Create 2-3 related tables with realistic data
4. Return in strict JSON format with Markdown content
5. databaseContext must be in Markdown TABLE format
```

#### 2.2 前端优化

**文件：`question-ai-generate.html`**

**优化Markdown渲染**：
```javascript
marked.setOptions({
    breaks: true,
    gfm: true,
    tables: true,
    sanitize: false,
    highlight: function(code, lang) {
        // 代码高亮配置
    }
});
```

**优化表格可视化**：
- 从setupSql解析表格数据
- 渲染为可视化可编辑表格
- 实时同步修改到SQL语句

---

### 模块三：数据处理流程（无需修改）

现有表名冲突处理机制已完善：
- **唯一前缀生成**：`quiz_q_[timestamp]_[random]_[table_name]`
- **自动表名替换**：SetupSqlExecutorService自动处理
- **元数据追踪**：QuizTableMetadata记录关联关系

---

## 需要修改的文件清单

### 后端文件（2个）
| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `GLMService.java` | 新增方法 | 新增performOCR()方法，优化generateQuestionWithRAG() |
| `AIQuestionController.java` | 新增接口 | 新增/upload-image接口处理图片上传 |

### 前端文件（2个）
| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `question-ai-normalize.html` | 新增功能 | 新增图片上传UI、拖拽功能、OCR集成 |
| `question-ai-generate.html` | 优化配置 | 优化Markdown渲染、表格可视化 |

### 配置文件（可能需要）
| 文件 | 说明 |
|------|------|
| `pom.xml` | 可能需要添加commons-io依赖（文件处理） |

---

## 实施步骤

### 阶段1：图片OCR功能（优先级：高）
1. 在GLMService中实现`performOCR()`方法
2. 在AIQuestionController中添加`/upload-image`接口
3. 在question-ai-normalize.html中添加图片上传UI
4. 测试OCR识别效果

### 阶段2：Markdown和表格优化（优先级：中）
1. 配置marked.js选项
2. 优化Markdown渲染
3. 改进表格可视化展示
4. 测试各种Markdown格式

### 阶段3：题目生成Prompt优化（优先级：中）
1. 优化RAG prompt
2. 测试知识库调用
3. 验证生成的题目质量

### 阶段4：整体测试（优先级：高）
1. 端到端测试两个功能
2. 性能优化
3. 用户体验验证

---

## 关键技术点

### 1. 图片OCR
- **模型**：GLM-4V（视觉模型）
- **编码**：Base64
- **输入**：多模态（图片 + 文本prompt）

### 2. 表名冲突处理
- **前缀格式**：`quiz_q_[timestamp]_[random]_[table_name]`
- **自动替换**：SetupSqlExecutorService
- **元数据管理**：QuizTableMetadata

### 3. Markdown渲染
- **库**：marked.js
- **高亮**：highlight.js
- **配置**：支持表格、代码块、GFM

### 4. 可编辑表格
- **实现**：contenteditable
- **同步**：实时更新setupSql
- **功能**：添加/删除行

---

## 注意事项

1. **图片大小限制**：建议<5MB，前后端都验证
2. **OCR准确性**：提供编辑功能，允许人工修正
3. **错误处理**：网络错误、API失败、用户友好提示
4. **安全性**：文件上传验证、SQL注入防护、XSS防护

---

## Critical Files（关键文件）

1. **GLMService.java** - AI服务核心，新增OCR方法
2. **AIQuestionController.java** - 新增图片上传接口
3. **question-ai-normalize.html** - 新增图片上传UI和OCR集成
4. **question-ai-generate.html** - 优化Markdown和表格展示

---

## 验证测试计划

### 功能测试
1. **图片OCR测试**：
   - 上传各种格式的题目截图
   - 验证OCR识别准确性
   - 测试识别后的编辑功能

2. **题目生成测试**：
   - 测试6种题型生成
   - 验证Markdown渲染效果
   - 测试表格可视化

3. **端到端测试**：
   - 完整的规范化流程
   - 完整的生成流程
   - 数据库存储验证

### 性能测试
1. OCR响应时间
2. 题目生成响应时间
3. 大图片上传处理
