# 🌀 Vortex - 高性能交易风控撮合系统
Kimiha (基米哈) 小组作品

## 环境依赖 (Environment)

### 后端 (vortex-core)
JDK: 21 (必须，开启虚拟线程支持)

Maven: 3.9.9

Database: SQLite 3


### 前端 (vortex-ui)
Node.js: 22.15.0 (LTS)

Package Manager: npm 10.9.2

Framework: Vue 3.5 + Vite 5

### 模拟器 (vortex-simulators)

Python: 3.12

Locust: 用于压力测试

## 项目结构
vortex-core/: Java 后端核心逻辑

vortex-ui/: Vue 3 前端管理后台

vortex-simulators/: Python 下单模拟与行情脚本

docs/: API 接口文档与设计方案

### 开发协作规范 (Workflow)
见飞书知识库规范文档。

### 快速启动
克隆项目：git clone git@github.com:Aric3/Vortex.git

启动后端：进入 vortex-core 执行 mvn spring-boot:run

启动前端：进入 vortex-ui 执行 npm install && npm run dev