# 接口契约目录

- [http-api.md](./http-api.md)：浏览器到 Java、Java 到 Python 的 HTTP 契约基线。
- [openapi.yaml](./openapi.yaml)：当前已实现浏览器/管理端接口的 OpenAPI 3.1 描述。
- `backend/java/src/test/java/com/certpractice/contract/OpenApiContractTest.java` 将 OpenAPI 路径与 Spring MVC 路由表双向比对，并校验 operationId、tag、路径参数、安全方案和所有本地 `$ref`。
- 字段裁剪、错误码、幂等和状态转换属于兼容性契约，不能用前端隐藏替代。
- Python 内部接口只允许 Java 调用，不向浏览器公开。

变更规则：

- 新增可选字段视为向后兼容。
- 删除字段、收紧枚举或改变安全字段可见性属于破坏性变更，需同步更新版本和测试。
- 题库答案、正确选项标记、别名的响应结构变化必须经过安全复核。
